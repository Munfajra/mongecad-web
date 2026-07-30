package gl3d.scene

import androidx.compose.ui.graphics.Color
import gl3d.api.Gl
import gl3d.api.GlBlend
import gl3d.api.GlCap
import gl3d.api.GlCompare
import gl3d.api.GlStencilOp
import gl3d.api.gl3dLog
import gl3d.camera.Camera3D
import gl3d.camera.buildCameraMatrices
import gl3d.math.Vec3
import gl3d.math.toVec3
import gl3d.render.ConeRenderer
import gl3d.render.CylinderRenderer
import gl3d.render.LineBatch
import gl3d.render.MeshRenderer
import gl3d.render.OitPipeline
import gl3d.render.LinePattern
import gl3d.render.LineStyle3D
import gl3d.render.ScreenProjector
import gl3d.render.SphereRenderer
import gl3d.render.ThickLineRenderer
import gl3d.render.TriangleBatch
import gl3d.render.TriangleRenderer
import model.LineStyle
import model.Offset3D
import model.Point3D
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.Line3D
import model.classes.isAxis
import model.classes.normalized
import model.classes.isX12Line3D
import model.classes.pointAtParam
import serialization.SettingsManager
import state.MongeState
import kotlin.math.abs
import model.gl3dLineColor
import model.gl3dSurfaceColor

/**
 * Vykreslení 3D scény – webový protějšek `renderScene3D` z `opengl/RenderScene.kt`.
 *
 * Pokrývá čárovou část scény (osy s ryskami, přímky, úsečky, kuželosečky,
 * křížky bodů), referenční průmětny míchané přes OIT a uživatelské roviny
 * i s jejich stopami. Kvadriky přibývají v další etapě – pořadí průchodů
 * z desktopu se přitom nemění, jen se doplňuje.
 */
class SceneRenderer(private val gl: Gl) {

    private val lineRenderer = ThickLineRenderer(gl)
    private val triangleRenderer = TriangleRenderer(gl)
    private val sphereRenderer = SphereRenderer(gl)
    private val cylinderRenderer = CylinderRenderer(gl)
    private val coneRenderer = ConeRenderer(gl)
    private val meshRenderer = MeshRenderer(gl)
    private val oit = OitPipeline(gl)

    /** Běžná geometrie – kreslí se pod roviny, aby ji roviny tónovaly. */
    private val batch = LineBatch()

    /** Osy a rysky. Desktop je kreslí až za rovinami, aby se do nich nezanořily. */
    private val axisBatch = LineBatch()

    /** Plné hroty os – trojúhelníkové kužely, viz `drawConeMesh3D` na desktopu. */
    private val axisConeBatch = TriangleBatch()

    /** Referenční průmětny – jdou přes OIT. */
    private val planeBatch = TriangleBatch()

    /** Uživatelské roviny, viditelná a zakrytá část (viz `collectUserPlanes`). */
    private val userPlaneBatch = TriangleBatch()
    private val userPlaneHiddenBatch = TriangleBatch()

    /** Zvýraznění výběru – kreslí se úplně nakonec, i skrz tělesa. */
    private val highlightBatch = LineBatch()

    val isReady: Boolean get() = lineRenderer.isReady && triangleRenderer.isReady

    fun initialize(): Boolean {
        val lines = lineRenderer.initialize()
        val triangles = triangleRenderer.initialize()
        sphereRenderer.initialize()
        cylinderRenderer.initialize()
        coneRenderer.initialize()
        meshRenderer.initialize()
        // OIT je volitelné – když ho GPU nedá, průhledné plochy se míchají
        // přímo a scéna se kreslí rovnou na plátno.
        if (triangles && triangleRenderer.supportsOit) oit.initialize()
        return lines && triangles
    }

    /** Zahození GPU zdrojů po ztrátě kontextu; volající pak zavolá [initialize]. */
    fun invalidateResources() {
        lineRenderer.dispose()
        triangleRenderer.dispose()
        sphereRenderer.dispose()
        cylinderRenderer.dispose()
        coneRenderer.dispose()
        meshRenderer.dispose()
        oit.dispose()
    }

    /**
     * @param background barva pozadí scény. Bere se z motivu aplikace, ne
     *   z `SettingsManager.bcolor` jako na desktopu – ten má jednu barvu bez
     *   ohledu na světlý/tmavý režim, což by ve webovém panelu vedle 2D plátna
     *   bilo do očí. Alfa se ignoruje: plátno musí být neprůhledné, jinak by
     *   skrz vyříznutou díru prosvítala prázdná stránka.
     * @param onLabels popisky os s polohou na plátně; kreslí je Compose
     *   ve vrstvě nad plátnem, renderer sám žádný text neumí.
     * @param captureRgba přečíst hotový snímek zpátky jako RGBA8 (export).
     *   Čte se z offscreen cíle ještě před překlopením na plátno.
     * @return pixely snímku, jen když je [captureRgba] zapnuté.
     */
    fun render(
        state: MongeState,
        camera: Camera3D,
        width: Int,
        height: Int,
        pixelScale: Float,
        background: Color,
        onLabels: (List<Scene3DLabel>) -> Unit = {},
        captureRgba: Boolean = false,
    ): ByteArray? {
        if (!isReady) {
            gl.clearColor(background.red, background.green, background.blue, 1f)
            gl.viewport(0, 0, width, height)
            gl.clear(color = true, depth = true, stencil = true)
            return null
        }

        // S OIT jde celý snímek do offscreen cíle a na plátno se překlopí až
        // hotový; bez něj se kreslí rovnou na plátno a průhledné roviny se
        // míchají přímo, seřazené podle hloubky.
        val useOit = oit.beginScene(
            width = width,
            height = height,
            red = background.red,
            green = background.green,
            blue = background.blue,
        )
        if (!useOit) {
            gl.clearColor(background.red, background.green, background.blue, 1f)
            gl.viewport(0, 0, width, height)
            gl.clear(color = true, depth = true, stencil = true)
        }

        // Volba směru os na 2D plátně se do 3D promítá jen mimo AXO; v AXO
        // orientaci určuje axo báze (viz `useScreenAxisFlip` v RenderScene).
        val useScreenAxisFlip = state.projectionMode != ProjectionMode.AXO
        val matrices = buildCameraMatrices(
            camera = camera,
            width = width,
            height = height,
            flipX = useScreenAxisFlip && state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            flipY = useScreenAxisFlip && state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP,
        )

        val projector = ScreenProjector(matrices.viewProjection, width, height)

        gl.enable(GlCap.DEPTH_TEST)
        gl.depthFunc(GlCompare.LESS)
        gl.depthMask(true)
        gl.enable(GlCap.BLEND)
        gl.blendFuncSeparate(
            GlBlend.SRC_ALPHA, GlBlend.ONE_MINUS_SRC_ALPHA,
            GlBlend.ONE, GlBlend.ONE_MINUS_SRC_ALPHA,
        )

        batch.clear()
        axisBatch.clear()
        axisConeBatch.clear()
        planeBatch.clear()
        userPlaneBatch.clear()
        userPlaneHiddenBatch.clear()
        highlightBatch.clear()

        val visibleLines = state.lines3D.filter { it.show }
        val referenceLines = visibleLines.filter(::isReferenceLine3D)
        val regularLines = visibleLines.filterNot(::isReferenceLine3D)

        val referencePlaneSize = referencePlaneRenderSize(state, camera)
        val axisRenderLength = (referencePlaneSize * 1.08f).coerceIn(90f, 2600f)

        // Když ve scéně stojí něco, co může čáry zakrýt, kreslí se čáry až
        // nakonec proti přestavěné hloubce a jejich zakrytá část se čárkuje.
        // Koplanární posun hloubky je pak nutný, aby čára ležící v rovině
        // (typicky stopa nebo řez) nespadla do její vlastní hloubky a
        // nezačala se čárkovat sama proti sobě.
        val hasOccluders = hasVisibleOccluders(state)
        val lineDepthBias = if (hasOccluders) -COPLANAR_LINE_DEPTH_BIAS else 0f

        val labels = ArrayList<Scene3DLabel>(referenceLines.size)
        collectRegularLines(regularLines, projector, lineDepthBias)
        collectSegments(state, projector, lineDepthBias)
        collectConics(state, batch, projector, lineDepthBias)
        collectCurves(state, batch, projector, lineDepthBias)
        collectPointCrosses(state)
        collectPointLabels(state, projector, pixelScale, labels)
        collectLineLabels(regularLines, projector, labels)
        collectReferenceAxes(
            lines = referenceLines,
            axisRenderLength = axisRenderLength,
            projector = projector,
            viewDirection = matrices.viewDirection,
            pixelScale = pixelScale,
            labels = labels,
        )
        collectReferencePlanes(
            state = state,
            batch = planeBatch,
            size = referencePlaneSize,
            viewDirection = matrices.viewDirection,
            eye = matrices.eye,
        )
        // Uživatelské roviny jsou o kus větší než průmětny, stejný poměr
        // jako `planeRenderSize` v `renderScene3D`.
        val userPlaneSize = maxOf(state.refPlaneSize * 0.7f, camera.distance * 0.74f)
            .coerceIn(180f, 2800f)
        collectUserPlanes(
            planes = state.planes3D,
            front = userPlaneBatch,
            hidden = userPlaneHiddenBatch,
            size = userPlaneSize,
            viewDirection = matrices.viewDirection,
        )
        collectSelectionHighlight(
            state = state,
            batch = highlightBatch,
            projector = projector,
            view = matrices,
            planeSize = userPlaneSize,
        )
        if (state.showTraces) {
            collectPlaneTraces(
                state = state,
                planes = state.planes3D,
                batch = axisBatch,
                projector = projector,
                size = userPlaneSize,
                labels = labels,
            )
        }
        onLabels(labels)

        if (!loggedFirstFrame) {
            loggedFirstFrame = true
            var segments = 0
            batch.forEachGroup { _, _, _, count -> segments += count }
            gl3dLog(
                "první snímek: ${width}×$height, přímek=${visibleLines.size}, " +
                        "úseček=${state.segments3D.size}, bodů=${state.sharedPoints3D.size}, " +
                        "úseků v batchi=$segments"
            )
        }

        // Pořadí průchodů podle desktopu: neprůhledné plochy se zápisem do
        // hloubky → uživatelské roviny → referenční průmětny přes OIT → osy,
        // aby se do rovin nezanořily → a úplně nakonec čárová geometrie proti
        // přestavěné hloubce (`renderScene3D`, bloky od ř. 904 po 1560).
        drawSurfaces(state, matrices, width, height)

        drawUserPlanes(state, matrices, width, height)

        if (useOit) {
            oit.beginTransparent()
            triangleRenderer.draw(planeBatch, matrices.viewProjection, oit = true)
            oit.endTransparent()
        } else {
            gl.depthMask(false)
            triangleRenderer.draw(planeBatch, matrices.viewProjection)
            gl.depthMask(true)
        }

        lineRenderer.draw(
            batch = axisBatch,
            viewProjection = matrices.viewProjection,
            viewportWidth = width,
            viewportHeight = height,
            pixelScale = pixelScale,
        )
        // Hroty os jsou plné kužely, ne drátěné – jdou po tělech os a se
        // zápisem do hloubky, aby se navzájem správně překrývaly.
        triangleRenderer.draw(axisConeBatch, matrices.viewProjection)

        drawLines(state, matrices, width, height, pixelScale, hasOccluders)
        drawSelectionHighlight(matrices, width, height, pixelScale)

        // S OIT je právě navázaný offscreen cíl scény, bez něj výchozí
        // framebuffer plátna – v obou případech je v něm hotový snímek.
        val capture = if (captureRgba) gl.readPixels(width, height) else null

        if (useOit) oit.endScene()
        return capture
    }

    /**
     * Zvýraznění výběru. Kreslí se úplně nakonec s `depthFunc = ALWAYS` a bez
     * zápisu do hloubky, takže je vidět i skrz tělesa – přesně jako
     * `drawSelectionHighlight3D` na desktopu. Bez toho by vybraná hrana zmizela
     * pod plochou, na které leží, a výběr by nebyl poznat.
     */
    private fun drawSelectionHighlight(
        matrices: gl3d.camera.CameraMatrices,
        width: Int,
        height: Int,
        pixelScale: Float,
    ) {
        if (highlightBatch.isEmpty()) return
        gl.enable(GlCap.DEPTH_TEST)
        gl.depthFunc(GlCompare.ALWAYS)
        gl.depthMask(false)
        lineRenderer.draw(
            batch = highlightBatch,
            viewProjection = matrices.viewProjection,
            viewportWidth = width,
            viewportHeight = height,
            pixelScale = pixelScale,
        )
        gl.depthFunc(GlCompare.LESS)
        gl.depthMask(true)
    }

    /**
     * Čárová geometrie s rozlišením viditelné a zakryté části – port bloku
     * „PLANE TRACES / hidden lines“ z `renderScene3D` (ř. 1195–1560).
     *
     * Bez překážek ve scéně se prostě nakreslí všechno plnou barvou. Jinak se
     * hloubka i stencil postaví **znovu, jen z překážek**: hloubka řekne, co je
     * před čím, stencil je obrazovkové sjednocení všech překážek. Čáry pak jdou
     * třikrát:
     *
     * 1. mimo překážky (`stencil ≠ 1`) – bez hloubkového testu, plnou barvou,
     * 2. uvnitř překážek před nimi (`stencil = 1`, `LEQUAL`) – plnou barvou,
     * 3. uvnitř překážek za nimi (`stencil = 1`, `GREATER`) – čárkovaně
     *    s [HIDDEN_LINE_ALPHA].
     *
     * Stencil se schválně nestaví z hloubkového prepassu, ale zvlášť s
     * `depthFunc = ALWAYS`: jinak by druhá překážka přestala čáry klasifikovat
     * všude, kde je před ní jiná.
     */
    private fun drawLines(
        state: MongeState,
        matrices: gl3d.camera.CameraMatrices,
        width: Int,
        height: Int,
        pixelScale: Float,
        hasOccluders: Boolean,
    ) {
        fun drawBatch(alpha: Float? = null, pattern: Float? = null) = lineRenderer.draw(
            batch = batch,
            viewProjection = matrices.viewProjection,
            viewportWidth = width,
            viewportHeight = height,
            pixelScale = pixelScale,
            alphaOverride = alpha,
            patternOverride = pattern,
        )

        if (!hasOccluders) {
            gl.disable(GlCap.DEPTH_TEST)
            drawBatch()
            gl.enable(GlCap.DEPTH_TEST)
            gl.depthFunc(GlCompare.LESS)
            return
        }

        gl.clear(depth = true, stencil = true)
        gl.colorMask(false, false, false, false)

        // 1) hloubka překážek
        gl.enable(GlCap.DEPTH_TEST)
        gl.depthFunc(GlCompare.LESS)
        gl.depthMask(true)
        triangleRenderer.draw(userPlaneBatch, matrices.viewProjection)
        drawSurfaces(state, matrices, width, height)

        // 2) stencil = obrazovkové sjednocení překážek
        gl.enable(GlCap.STENCIL_TEST)
        gl.stencilMask(0xFF)
        gl.stencilFunc(GlCompare.ALWAYS, 1, 0xFF)
        gl.stencilOp(GlStencilOp.KEEP, GlStencilOp.KEEP, GlStencilOp.REPLACE)
        gl.depthFunc(GlCompare.ALWAYS)
        gl.depthMask(false)
        triangleRenderer.draw(userPlaneBatch, matrices.viewProjection)
        drawSurfaces(state, matrices, width, height)
        gl.colorMask(true, true, true, true)
        gl.stencilMask(0x00)
        gl.stencilOp(GlStencilOp.KEEP, GlStencilOp.KEEP, GlStencilOp.KEEP)

        // 3) mimo překážky
        gl.stencilFunc(GlCompare.NOTEQUAL, 1, 0xFF)
        gl.disable(GlCap.DEPTH_TEST)
        drawBatch()

        // 4) uvnitř překážek – viditelná část
        gl.stencilFunc(GlCompare.EQUAL, 1, 0xFF)
        gl.enable(GlCap.DEPTH_TEST)
        gl.depthFunc(GlCompare.LEQUAL)
        gl.depthMask(false)
        drawBatch()

        // 5) uvnitř překážek – zakrytá část
        gl.depthFunc(GlCompare.GREATER)
        drawBatch(alpha = HIDDEN_LINE_ALPHA, pattern = LinePattern.DASHED)

        gl.disable(GlCap.STENCIL_TEST)
        gl.stencilMask(0xFF)
        gl.depthFunc(GlCompare.LESS)
        gl.depthMask(true)
    }

    /**
     * Uživatelské roviny s hloubkovým klíčem – port dvojprůchodu
     * z `renderPlanesInteractionStyle`.
     *
     * Nejdřív hloubkový prepass všech rovin (jen do hloubky, bez barvy), pak
     * se každá plocha kreslí dvakrát: `GREATER` vykreslí to, co je za jinou
     * rovinou (ztmavené a průhlednější), `LEQUAL` část vpředu plnou barvou.
     * Bez toho se protínající se roviny slijí do jedné barvy a není poznat,
     * která je vpředu.
     *
     * Prepass ale nesmí zůstat v hloubkovém bufferu. Rovina je průhledná,
     * takže nemá právo cokoli zakrývat – a kdyby si svou hloubku ponechala,
     * všechno kreslené později (referenční průmětny, osy, stopy) by za ní
     * zmizelo úplně, místo aby skrz ni prosvítalo. Hloubka se proto na konci
     * vrátí do stavu „jen čárová geometrie“.
     */
    private fun drawUserPlanes(
        state: MongeState,
        matrices: gl3d.camera.CameraMatrices,
        width: Int,
        height: Int,
    ) {
        if (userPlaneBatch.isEmpty) return
        val viewProjection = matrices.viewProjection

        gl.colorMask(false, false, false, false)
        gl.depthMask(true)
        gl.depthFunc(GlCompare.LESS)
        triangleRenderer.draw(userPlaneBatch, viewProjection)

        gl.colorMask(true, true, true, true)
        gl.depthMask(false)

        gl.depthFunc(GlCompare.GREATER)
        triangleRenderer.draw(userPlaneHiddenBatch, viewProjection)

        gl.depthFunc(GlCompare.LEQUAL)
        triangleRenderer.draw(userPlaneBatch, viewProjection)

        // Zahodit hloubku rovin a nechat v bufferu zpátky jen neprůhlednou
        // geometrii. Levnější než jakékoli zálohování: překreslí se bez barvy.
        gl.depthFunc(GlCompare.LESS)
        gl.depthMask(true)
        gl.clear(depth = true)
        gl.colorMask(false, false, false, false)
        drawSurfaces(state, matrices, width, height)
        gl.colorMask(true, true, true, true)
    }

    /**
     * Neprůhledné plochy scény: kvadriky a sítě.
     *
     * Sama nemění `colorMask` ani hloubkový stav – volá se jednak v barevném
     * průchodu, jednak s vypnutou barvou dvakrát jen kvůli hloubce (obnova
     * hloubky po rovinách a stavba hloubky/stencilu překážek pro skryté čáry).
     */
    private fun drawSurfaces(
        state: MongeState,
        matrices: gl3d.camera.CameraMatrices,
        width: Int,
        height: Int,
    ) {
        drawSpheres(state, matrices, width, height)
        drawCylinders(state, matrices, width, height)
        drawCones(state, matrices, width, height)
        drawMeshSurfaces(state, matrices)
    }

    /**
     * Plochy, které se kreslí skutečnou sítí trojúhelníků: rotační plochy,
     * přímkové plochy a tělesa z úseček.
     *
     * Jdou stejným neprůhledným průchodem jako kvadriky – desktopová
     * `QUADRIC_FRONT_ALPHA` je 1, takže obyčejný hloubkový test dá správné
     * zakrytí čar za nimi a do OIT nepatří. Sítě se drží nacachované v
     * [MeshRenderer]; přenáší se jen při změně geometrie.
     */
    private fun drawMeshSurfaces(state: MongeState, matrices: gl3d.camera.CameraMatrices) {
        if (!meshRenderer.isReady) return

        val sorNarys = state.solidsOfRevolutionNarys.filter { it.show && it.fillFaces }
        val sorPudorys = state.solidsOfRevolutionPudorys.filter { it.show && it.fillFaces }
        val ruled = state.ruledSurfaces.filter { it.show && it.fillFaces }
        val solids = state.segmentSolids3D.filter { it.show && it.fillFaces }
        if (sorNarys.isEmpty() && sorPudorys.isEmpty() && ruled.isEmpty() && solids.isEmpty()) {
            meshRenderer.clearCache()
            return
        }

        val axesById = state.lines3D.associateBy { it.id }
        meshRenderer.begin(matrices.viewProjection, matrices.invViewProjection)

        for (sor in sorNarys) {
            val axis = axesById[sor.axisLine3DId] ?: continue
            val meridian = sor.sampledMeridianPolylineXZ
            if (meridian.isEmpty()) continue
            val axisStart = axis.start.toVec3()
            meshRenderer.draw(
                key = sor.id,
                signature = sorMeshSignature(axisStart, meridian),
                color = sor.color.gl3dSurfaceColor(),
                alpha = 1f,
                tint = 1f,
                ambient = SOR_AMBIENT,
            ) {
                // Osa nárysové rotační plochy je kanonicky +z bez ohledu na
                // orientaci přímky osy (přímka nakreslená „dolů“ by jinak celé
                // těleso zrcadlila pod půdorysnu) – stejná konvence jako
                // v `opengl/model/SoR.kt` a ve 2D výplních.
                buildSoRMesh(
                    axisPoint = axisStart,
                    axisDir = Vec3(0f, 0f, 1f),
                    axisX0InMeridian = axis.start.x,
                    meridianXZ = meridian,
                )
            }
        }

        for (sor in sorPudorys) {
            val axis = axesById[sor.axisLine3DId] ?: continue
            val meridian = sor.sampledMeridianPolylineXY
            if (meridian.isEmpty()) continue
            val axisStart = axis.start.toVec3()
            meshRenderer.draw(
                key = sor.id,
                signature = sorMeshSignature(axisStart, meridian),
                color = sor.color.gl3dSurfaceColor(),
                alpha = 1f,
                tint = 1f,
                ambient = SOR_AMBIENT,
            ) {
                // Osa půdorysové rotační plochy je kanonicky +y, viz výše.
                buildSoRMesh(
                    axisPoint = axisStart,
                    axisDir = Vec3(0f, 1f, 0f),
                    axisX0InMeridian = axis.start.x,
                    meridianXZ = meridian,
                )
            }
        }

        for (surface in ruled) {
            meshRenderer.draw(
                key = surface.id,
                signature = ruledSurfaceMeshSignature(state, surface),
                color = surface.color.takeIf { it != Color.Black } ?: FALLBACK_SURFACE_COLOR,
                alpha = 1f,
                tint = 1f,
                ambient = SURFACE_AMBIENT,
            ) { buildRuledSurfaceMesh(state, surface) }
        }

        if (solids.isNotEmpty()) {
            // Podstava hranolu bývá koplanární s rovinou, ve které vznikla, a
            // ta se často kreslí taky; stejné hloubky by se praly o pixely.
            // Desktop to řeší týmž záporným posunem (`SegmentSolids.kt`).
            gl.enable(GlCap.POLYGON_OFFSET_FILL)
            gl.polygonOffset(-1f, -1f)
            for (solid in solids) {
                val color = solid.color
                val opaque = color.red == 0f && color.green == 0f && color.blue == 0f
                meshRenderer.draw(
                    key = solid.id,
                    signature = segmentSolidMeshSignature(state, solid),
                    color = if (opaque) FALLBACK_SURFACE_COLOR else color,
                    alpha = color.alpha.coerceIn(0.85f, 1f),
                    tint = 1f,
                    ambient = SURFACE_AMBIENT,
                ) { buildSegmentSolidMesh(state, solid) }
            }
            gl.polygonOffset(0f, 0f)
            gl.disable(GlCap.POLYGON_OFFSET_FILL)
        }

        meshRenderer.end()
        meshRenderer.retain()
    }

    /**
     * Koule. Kreslí se jako neprůhledné – desktopová `QUADRIC_FRONT_ALPHA`
     * je 1, takže do OIT nepatří a normální hloubkový test dá správné
     * zakrytí čar za nimi.
     */
    private fun drawSpheres(
        state: MongeState,
        matrices: gl3d.camera.CameraMatrices,
        width: Int,
        height: Int,
    ) {
        if (state.spheres3D.isEmpty() || !sphereRenderer.isReady) return
        // Ray-cast potřebuje inverzní ViewProj; u singulární matice se koule
        // prostě nekreslí, což je lepší než kreslit je špatně.
        val inverse = matrices.invViewProjection ?: return

        val centersById = state.sharedPoints3D.associateBy { it.id }
        sphereRenderer.begin(matrices.viewProjection, inverse, width, height)
        for (sphere in state.spheres3D) {
            if (!sphere.show || !sphere.fillFaces) continue
            val center = centersById[sphere.centerPoint3DId] ?: continue
            sphereRenderer.draw(
                center = center.toVec3(),
                radius = sphere.radius,
                color = sphere.color.gl3dSurfaceColor(),
            )
        }
        sphereRenderer.end()
    }

    /**
     * Válcové plochy. Plocha je zadaná řídicí kuželosečkou, směrem tvořic
     * a horní omezující rovinou; dolní rovina je rovina té kuželosečky.
     */
    private fun drawCylinders(
        state: MongeState,
        matrices: gl3d.camera.CameraMatrices,
        width: Int,
        height: Int,
    ) {
        if (state.cylindricalSurfaces.isEmpty() || !cylinderRenderer.isReady) return
        val inverse = matrices.invViewProjection ?: return

        val conicsById = state.conics3D.associateBy { it.id }
        var started = false
        for (surface in state.cylindricalSurfaces) {
            if (!surface.show || !surface.fillFaces) continue
            val directrix = conicsById[surface.directrixId] ?: continue
            val ellipse = ellipseParams(directrix) ?: continue
            val topPlane = surface.equation?.normalized() ?: continue
            val direction = surface.direction.normalized().toVec3()
            if (direction.length() < 1e-6f) continue
            val (baseNormal, baseD) = conicPlane(directrix)

            if (!started) {
                cylinderRenderer.begin(matrices.viewProjection, inverse, width, height)
                started = true
            }
            cylinderRenderer.draw(
                color = surface.color.takeIf { it != Color.Black } ?: FALLBACK_SURFACE_COLOR,
                center = ellipse.center,
                u = ellipse.u,
                v = ellipse.v,
                invA2 = ellipse.invA2,
                invB2 = ellipse.invB2,
                direction = direction,
                basePlaneNormal = baseNormal,
                basePlaneD = baseD,
                topPlaneNormal = Vec3(topPlane.a, topPlane.b, topPlane.c),
                topPlaneD = topPlane.d,
            )
        }
        if (started) cylinderRenderer.end()
    }

    /**
     * Kuželové plochy. Plocha je zadaná vrcholem a řídicí kuželosečkou;
     * kvadratickou formu v souřadnicích vrcholu počítá [coneQuadricLocal].
     */
    private fun drawCones(
        state: MongeState,
        matrices: gl3d.camera.CameraMatrices,
        width: Int,
        height: Int,
    ) {
        if (state.conicalSurfaces.isEmpty() || !coneRenderer.isReady) return
        val inverse = matrices.invViewProjection ?: return

        val conicsById = state.conics3D.associateBy { it.id }
        val pointsById = state.sharedPoints3D.associateBy { it.id }
        var started = false
        for (surface in state.conicalSurfaces) {
            if (!surface.show || !surface.fillFaces) continue
            val directrix = conicsById[surface.directrixId] ?: continue
            val apexPoint = pointsById[surface.apexId] ?: continue
            val apex = apexPoint.toVec3()
            val ellipse = ellipseParams(directrix) ?: continue
            val quadric = coneQuadricLocal(directrix, apex) ?: continue

            val (rawNormal, _) = conicPlane(directrix)
            if (rawNormal.length() < 1e-8f) continue
            val normal = rawNormal.normalized()
            // k je vzdálenost roviny řídicí kuželosečky od vrcholu po normále.
            val planeK = normal dot (directrix.p0.toVec3() - apex)

            if (!started) {
                coneRenderer.begin(matrices.viewProjection, inverse, width, height)
                started = true
            }
            coneRenderer.draw(
                color = surface.color.takeIf { it != Color.Black } ?: FALLBACK_SURFACE_COLOR,
                apex = apex,
                quadric = quadric,
                planeNormal = normal,
                planeK = planeK,
                directrixCenter = ellipse.center,
                directrixU = ellipse.u,
                directrixV = ellipse.v,
                directrixInvA2 = ellipse.invA2,
                directrixInvB2 = ellipse.invB2,
            )
        }
        if (started) coneRenderer.end()
    }


    /** Jednorázový záznam do konzole – prázdná scéna je jinak k nerozeznání od chyby. */
    private var loggedFirstFrame = false

    fun dispose() {
        lineRenderer.dispose()
        triangleRenderer.dispose()
        sphereRenderer.dispose()
        cylinderRenderer.dispose()
        coneRenderer.dispose()
        meshRenderer.dispose()
        oit.dispose()
    }

    // --- sběr geometrie ----------------------------------------------------

    private fun collectRegularLines(
        lines: List<Line3D>,
        projector: ScreenProjector,
        depthBias: Float,
    ) {
        for (line in lines) {
            val trim = line.customTrimRange
            val a: Vec3
            val b: Vec3
            if (trim != null) {
                a = line.pointAtParam(trim.min).toVec3()
                b = line.pointAtParam(trim.max).toVec3()
            } else {
                val dir = line.direction.normalized().toVec3()
                val start = line.start.toVec3()
                a = start - dir * FREE_LINE_HALF_LENGTH
                b = start + dir * FREE_LINE_HALF_LENGTH
            }
            batch.addSegment(
                a, b,
                LineStyle3D.of(
                    color = line.color.gl3dLineColor(),
                    width = line.strokeWidth,
                    pattern = line.lineStyle.toPatternValue(),
                    depthBias = depthBias,
                ),
                projector,
            )
        }
    }

    private fun collectSegments(
        state: MongeState,
        projector: ScreenProjector,
        depthBias: Float,
    ) {
        for (segment in state.segments3D) {
            if (!segment.show) continue
            batch.addSegment(
                segment.start.toVec3(),
                segment.end.toVec3(),
                LineStyle3D.of(
                    color = segment.color.gl3dLineColor(),
                    width = segment.strokeWidth,
                    pattern = segment.lineStyle.toPatternValue(),
                    depthBias = depthBias,
                ),
                projector,
            )
        }
    }

    /**
     * Body se kreslí jako trojice os křížku – přesně jako
     * `drawCrosses3DUsingLineShader` na desktopu, včetně vyřazení bodů, které
     * jsou jen koncem úsečky nebo vrcholem tělesa (ty by jinak zdvojily
     * značku už nakreslenou tělesem).
     */
    private fun collectPointCrosses(state: MongeState) {
        val excluded = excludedCrossPointIds(state)
        val length = 2f * CROSS_SIZE

        for (point in state.sharedPoints3D) {
            if (!point.show || point.id in excluded) continue
            val style = LineStyle3D.of(color = point.color.gl3dLineColor(), width = CROSS_WIDTH)
            addCrossArm(point, CROSS_SIZE, 0f, 0f, style, length)
            addCrossArm(point, 0f, CROSS_SIZE, 0f, style, length)
            addCrossArm(point, 0f, 0f, CROSS_SIZE, style, length)
        }
    }

    private fun addCrossArm(
        point: Point3D,
        dx: Float,
        dy: Float,
        dz: Float,
        style: LineStyle3D,
        length: Float,
    ) {
        batch.addSegment(
            Vec3(point.x - dx, point.y - dy, point.z - dz),
            Vec3(point.x + dx, point.y + dy, point.z + dz),
            style,
            distA = 0f,
            distB = length,
        )
    }

    /**
     * Osy a základnice x₁₂. Na rozdíl od běžných přímek se nekreslí do
     * „nekonečna“, ale na délku odvozenou od vzdálenosti kamery, aby zůstaly
     * čitelné při každém zoomu; hrot je drátěný kužel.
     */
    private fun collectReferenceAxes(
        lines: List<Line3D>,
        axisRenderLength: Float,
        projector: ScreenProjector,
        viewDirection: Vec3,
        pixelScale: Float,
        labels: MutableList<Scene3DLabel>,
    ) {
        val arrowTrim = (axisRenderLength * 0.047f).coerceIn(16f, 60f)
        val coneRadius = (axisRenderLength * 0.013f).coerceIn(4f, 18f)
        val uiScale = SettingsManager.current.UIscale / 75f

        for (line in lines) {
            val negativeEnd = axisEnd(line, -axisRenderLength)
            val bodyEnd = axisEnd(line, (axisRenderLength - arrowTrim).coerceAtLeast(1f))
            val tip = axisEnd(line, axisRenderLength)

            val style = LineStyle3D.of(
                color = line.color.gl3dLineColor(),
                width = line.strokeWidth + 4f,
                alpha = if (isAxis(line)) 0.88f else 0.72f,
                depthBias = -COPLANAR_LINE_DEPTH_BIAS,
            )
            axisBatch.addSegment(negativeEnd, bodyEnd, style, projector)
            addArrowHead(
                tip = tip,
                direction = line.direction,
                length = arrowTrim,
                radius = coneRadius,
                color = line.color.gl3dLineColor(),
                alpha = if (isAxis(line)) 0.95f else 0.82f,
            )
            collectAxisTicks(
                line = line,
                axisRenderLength = axisRenderLength - arrowTrim,
                viewDirection = viewDirection,
                projector = projector,
                style = style.copy(width = line.strokeWidth + 2f),
                halfLengthPx = AXIS_TICK_HALF_LEN_PX * uiScale * pixelScale,
            )
            collectAxisLabel(line, axisRenderLength, arrowTrim, projector, uiScale, pixelScale, labels)
        }
    }

    /**
     * Měřítkové rysky na ose. Jsou kolmé na osu i na směr pohledu, takže
     * zůstávají čitelné z každého úhlu, a mají konstantní délku v pixelech –
     * proto se pro každou počítá, kolik světových jednotek ta délka zabere.
     */
    private fun collectAxisTicks(
        line: Line3D,
        axisRenderLength: Float,
        viewDirection: Vec3,
        projector: ScreenProjector,
        style: LineStyle3D,
        halfLengthPx: Float,
    ) {
        if (axisRenderLength <= AXIS_TICK_SPACING || halfLengthPx <= 0f) return

        val axis = line.direction.normalized().toVec3()
        if (axis.length() < 1e-6f) return

        var tickDir = axis cross viewDirection
        if (tickDir.length() < 1e-5f) {
            // Pohled skoro podél osy – vezmi libovolný jiný kolmý směr.
            val helper = if (abs(axis.z) < 0.9f) Vec3.UNIT_Z else Vec3(0f, 1f, 0f)
            tickDir = axis cross helper
        }
        if (tickDir.length() < 1e-6f) return
        tickDir = tickDir.normalized()

        var offset = AXIS_TICK_SPACING
        while (offset <= axisRenderLength) {
            addTick(axisEnd(line, offset), tickDir, projector, style, halfLengthPx)
            addTick(axisEnd(line, -offset), tickDir, projector, style, halfLengthPx)
            offset += AXIS_TICK_SPACING
        }
    }

    private fun addTick(
        center: Vec3,
        tickDir: Vec3,
        projector: ScreenProjector,
        style: LineStyle3D,
        halfLengthPx: Float,
    ) {
        val pixelsPerWorldUnit = projector.distancePx(center, center + tickDir)
        if (pixelsPerWorldUnit < 1e-4f) return
        val halfWorld = halfLengthPx / pixelsPerWorldUnit
        axisBatch.addSegment(
            center - tickDir * halfWorld,
            center + tickDir * halfWorld,
            style,
            projector,
        )
    }

    /**
     * Popisek osy. Renderer spočítá jen polohu na plátně, samotný text kreslí
     * Compose nad plátnem – NanoVG, které na to má desktop, ve webu není a
     * Compose navíc umí rovnou nabundlované matematické fonty.
     */
    private fun collectAxisLabel(
        line: Line3D,
        axisRenderLength: Float,
        arrowTrim: Float,
        projector: ScreenProjector,
        uiScale: Float,
        pixelScale: Float,
        labels: MutableList<Scene3DLabel>,
    ) {
        val text = axisLabel(line)
        if (text.isBlank()) return

        val tip = projector.screenPoint(axisEnd(line, axisRenderLength)) ?: return
        val beforeTip = projector.screenPoint(
            axisEnd(line, (axisRenderLength - arrowTrim).coerceAtLeast(1f))
        ) ?: return

        // Popisek sedí kousek za hrotem ve směru osy, aby ho nepřekrýval.
        val dx = tip.x - beforeTip.x
        val dy = tip.y - beforeTip.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val push = LABEL_PUSH_PX * uiScale * pixelScale

        labels += Scene3DLabel(
            text = text,
            x = tip.x + dx / length * push,
            y = tip.y + dy / length * push,
            color = line.color.gl3dLineColor(),
            centered = true,
        )
    }

    /**
     * Popisky bodů – port bloku nad `state.sharedPoints3D` z `renderScene3D`.
     * Sedí kousek vpravo dolů od bodu, horní index se sází zvlášť. Body, které
     * jsou jen koncem sdružené úsečky, popisek nedostávají (jinak by se zdvojil
     * s popiskem z 2D plátna).
     */
    private fun collectPointLabels(
        state: MongeState,
        projector: ScreenProjector,
        pixelScale: Float,
        labels: MutableList<Scene3DLabel>,
    ) {
        val excluded = mutableSetOf<String>()
        state.pointsPudorys.filter { it.isSegmentEndpoint }.forEach { p -> p.parent?.let { excluded += it.id } }
        state.pointsNarys.filter { it.isSegmentEndpoint }.forEach { p -> p.parent?.let { excluded += it.id } }

        val offset = LABEL_POINT_OFFSET_PX * pixelScale
        for (point in state.sharedPoints3D) {
            if (!point.show || point.id in excluded) continue
            if (point.name.isBlank()) continue
            val screen = projector.screenPoint(point.toVec3()) ?: continue
            labels += Scene3DLabel(
                text = point.name,
                superscript = point.superscript.orEmpty(),
                x = screen.x + offset,
                y = screen.y + offset,
                color = point.color.gl3dLineColor(),
            )
        }
    }

    /**
     * Popisky přímek. Desktop je umisťuje kolizním algoritmem
     * (`placeLabelNearLine3D`); tady sedí na pevném podílu vykresleného úseku,
     * stejně jako popisky stop rovin.
     */
    private fun collectLineLabels(
        lines: List<Line3D>,
        projector: ScreenProjector,
        labels: MutableList<Scene3DLabel>,
    ) {
        for (line in lines) {
            if (line.name.isBlank()) continue
            val trim = line.customTrimRange
            val anchor = if (trim != null) {
                val a = line.pointAtParam(trim.min).toVec3()
                val b = line.pointAtParam(trim.max).toVec3()
                a + (b - a) * LABEL_POSITION_ALONG_LINE
            } else {
                // Volná přímka nemá konce, popisek proto sedí kousek od bodu,
                // kterým je zadaná – jinak by uletěl tisíce jednotek daleko.
                line.start.toVec3() +
                        line.direction.normalized().toVec3() * FREE_LINE_LABEL_DISTANCE
            }
            val screen = projector.screenPoint(anchor) ?: continue
            labels += Scene3DLabel(
                text = line.name,
                superscript = line.superscript.orEmpty(),
                x = screen.x,
                y = screen.y,
                color = line.color.gl3dLineColor(),
            )
        }
    }

    /**
     * Hrot osy jako **plný kužel** – port `drawConeMesh3D` z
     * `opengl/model/LineTypes.kt`. Plášť se skládá z trojúhelníků do vrcholu,
     * podstava z vějíře do středu; stínování žádné, kužel má plnou barvu osy
     * stejně jako na desktopu.
     */
    private fun addArrowHead(
        tip: Vec3,
        direction: Offset3D,
        length: Float,
        radius: Float,
        color: Color,
        alpha: Float,
    ) {
        val axis = direction.normalized().toVec3()
        if (axis.length() < 1e-6f || length <= 0f || radius <= 0f) return
        val base = tip - axis * length
        val (u, v) = perpendicularBasis(axis)

        fun rim(angle: Float): Vec3 =
            base + u * (radius * kotlin.math.cos(angle)) + v * (radius * kotlin.math.sin(angle))

        for (i in 0 until ARROW_SEGMENTS) {
            val p0 = rim((i.toFloat() / ARROW_SEGMENTS) * TWO_PI)
            val p1 = rim(((i + 1).toFloat() / ARROW_SEGMENTS) * TWO_PI)
            axisConeBatch.addTriangle(tip, p0, p1, color.red, color.green, color.blue, alpha)
            axisConeBatch.addTriangle(base, p1, p0, color.red, color.green, color.blue, alpha)
        }
    }

    private fun axisEnd(line: Line3D, distance: Float): Vec3 {
        val dir = line.direction.normalized().toVec3()
        // x₁₂ vede počátkem bez ohledu na to, kde má uložený `start`.
        val start = if (isX12Line3D(line)) Vec3.ZERO else line.start.toVec3()
        return start + dir * distance
    }

    private companion object {
        /** Volná přímka se kreslí na tuto délku na obě strany, stejně jako na desktopu. */
        const val FREE_LINE_HALF_LENGTH = 3000f
        const val CROSS_SIZE = 5f
        const val CROSS_WIDTH = 3f
        const val COPLANAR_LINE_DEPTH_BIAS = 2e-5f

        /** Průhlednost zakryté (čárkované) části čar, jako na desktopu. */
        const val HIDDEN_LINE_ALPHA = 0.28f

        /** Stejná hustota pláště hrotu jako `drawConeMesh3D` na desktopu. */
        const val ARROW_SEGMENTS = 18
        const val TWO_PI = 6.2831855f

        /** Rozteč rysek v logických jednotkách modelu, jako na desktopu. */
        /** Náhradní barva plochy – černá plocha by ve 3D splynula s čarami. */
        val FALLBACK_SURFACE_COLOR = Color(0.5f, 0.5f, 0.5f)

        /** Ambientní složka sítí; hodnoty z desktopových rendererů. */
        const val SOR_AMBIENT = 0.32f
        const val SURFACE_AMBIENT = 0.38f

        const val AXIS_TICK_SPACING = 10f
        const val AXIS_TICK_HALF_LEN_PX = 5f
        const val LABEL_PUSH_PX = 18f

        /** Odsazení popisku bodu od jeho značky, jako `screenPos + 5` na desktopu. */
        const val LABEL_POINT_OFFSET_PX = 5f

        /** Podíl vykresleného úseku přímky, kde sedí její popisek. */
        const val LABEL_POSITION_ALONG_LINE = 0.72f

        /** Jak daleko od zadaného bodu sedí popisek neomezené přímky. */
        const val FREE_LINE_LABEL_DISTANCE = 120f
    }
}

/**
 * Popisek scény v souřadnicích plátna (počátek vlevo nahoře, v pixelech).
 *
 * Renderer sám žádný text neumí – dodá jen polohu a sazbu obstará Compose
 * (`ui/gl3d/Gl3DViewport.kt`) stejným Skia kódem jako popisky na 2D plátně.
 */
data class Scene3DLabel(
    val text: String,
    /** Horní index; u rovin je to jejich název, u bodů a přímek `superscript`. */
    val superscript: String = "",
    val x: Float,
    val y: Float,
    val color: Color,
    /** Osy sedí kotvou na střed, ostatní popisky vlevo nahoře jako na desktopu. */
    val centered: Boolean = false,
)

private fun axisLabel(line: Line3D): String = when (line.id) {
    "x_axis" -> "x"
    "y_axis" -> "y"
    "z_axis" -> "z"
    else -> if (isX12Line3D(line)) "x₁₂" else line.name
}

private fun isReferenceLine3D(line: Line3D): Boolean = isAxis(line) || isX12Line3D(line)

/**
 * Stojí ve scéně něco, co může zakrýt čáry? Port `splitLinesByOccluders`
 * z `renderScene3D`: uživatelské roviny a všechny vyplněné plochy. Referenční
 * průmětny mezi ně schválně nepatří – ty by čárkovaly celou konstrukci.
 */
private fun hasVisibleOccluders(state: MongeState): Boolean =
    state.planes3D.any { it.show && it.equation != null } ||
        state.conicalSurfaces.any { it.show && it.fillFaces } ||
        state.cylindricalSurfaces.any { it.show && it.fillFaces } ||
        state.spheres3D.any { it.show && it.fillFaces } ||
        state.solidsOfRevolutionNarys.any { it.show && it.fillFaces } ||
        state.solidsOfRevolutionPudorys.any { it.show && it.fillFaces } ||
        state.ruledSurfaces.any { it.show && it.fillFaces } ||
        state.segmentSolids3D.any { it.show && it.fillFaces }

/**
 * Velikost vykreslených referenčních rovin. Kopíruje výpočet z `renderScene3D` –
 * roste se vzdáleností kamery, takže scéna nikdy nezmizí v bodě ani nepřeteče.
 * Délka os se z ní odvozuje.
 */
private fun referencePlaneRenderSize(state: MongeState, camera: Camera3D): Float = maxOf(
    state.refPlaneSize * 0.6f,
    camera.distance * 0.68f,
).coerceIn(150f, 2400f)

/** Dvojice jednotkových vektorů kolmých na `axis` a na sebe navzájem. */
private fun perpendicularBasis(axis: Vec3): Pair<Vec3, Vec3> {
    val helper = if (abs(axis.z) < 0.9f) Vec3.UNIT_Z else Vec3(1f, 0f, 0f)
    val u = (axis cross helper).normalized()
    return u to (axis cross u).normalized()
}

internal fun LineStyle.toPatternValue(): Float = when (this) {
    LineStyle.Solid -> LinePattern.SOLID
    LineStyle.Dashed -> LinePattern.DASHED
    LineStyle.Dotted -> LinePattern.DOTTED
    LineStyle.DashDot -> LinePattern.DASH_DOT
}

/**
 * Port `segmentSolidVertexPointIds3D` a filtru z `opengl/model/Points.kt`:
 * body, které jsou koncem sdružené úsečky nebo vrcholem tělesa, křížek
 * nedostávají.
 */
private fun excludedCrossPointIds(state: MongeState): Set<String> {
    val ids = mutableSetOf<String>()

    state.pointsPudorys.filter { it.isSegmentEndpoint }.forEach { p -> p.parent?.let { ids += it.id } }
    state.pointsNarys.filter { it.isSegmentEndpoint }.forEach { p -> p.parent?.let { ids += it.id } }

    if (state.segmentSolids3D.isNotEmpty()) {
        val segmentsById = state.segments3D.associateBy { it.id }
        for (solid in state.segmentSolids3D) {
            ids += solid.vertexPointIds
            for (segmentId in solid.segmentIds3D) {
                val segment = segmentsById[segmentId] ?: continue
                ids += segment.start.id
                ids += segment.end.id
            }
        }
    }

    return ids
}
