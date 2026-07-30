package ui.mongeui.toolbar
import monge.input.ruledsurface.ruledSurfaceSelectionInfo

import monge.input.axo.lines.linecomplete.ProjectionKind
import model.ConstructionModifier
import model.Mongeobjects
import model.ProjectionMode
import model.ProjectionType
import model.axo.AxoMode
import monge.input.selection.CylinderPhase
import state.MongeState
import ui.resetStavu

// Zpráva pro PLATONIC_SOLID podle rozestavěné konstrukce (viz monge/input/solids/Platonic.kt).
// Počítá se tu, ne přímo v handleru, protože updateConstructionInfo se volá i při každém
// pohybu myši (computeSnappedPoint) a přepsal by tak jednorázově nastavený text v handleru.
private fun platonicSolidInfo(state: MongeState): String {
    val polygon = state.pendingPlatonicPolygonId?.let { pid -> state.polygons3D.firstOrNull { it.id == pid } }
    return when {
        polygon == null -> "Vyberte podstavu (pravidelný 3- nebo 4-úhelník)."
        polygon.n == 4 -> "Krychle: pravým tlačítkem překlopíte stranu, levým potvrdíte."
        else -> "Čtyřstěn: pravým tlačítkem překlopíte stranu, levým potvrdíte."
    }
}

// Zprávy pro doplňování průmětů přímek v AXO (viz monge/input/axo/lines/linecomplete/CompleteLine.kt).
// Počítají se tu ze stejného důvodu jako platonicSolidInfo výše - updateConstructionInfo se volá
// i při každém pohybu myši a přepsal by tak text nastavený jednorázově přímo v handleru.
// Desktop tu rozlišuje, ve kterém AxoMode uživatel právě je (projectionKindFromAxoMode),
// a podle toho volí kratší text. Web AXO režim nemá, takže se vybírá vždy varianta
// "přepněte do ...".
private fun axoLineCompletionWaitingInfo(state: MongeState): String {
    val pending = state.pendingAxoLineCompletion ?: return ""
    val action = if (state.reusingExistingProjection) "vyberte existující průmět přímky" else "zadejte druhý průmět přímky"

    return when (pending.firstKind) {
        ProjectionKind.PUDORYS, ProjectionKind.BOKORYS, ProjectionKind.NARYS -> "Přepněte do jiné průmětny a $action."
        ProjectionKind.AXO -> "Přepněte do půdorysu, nárysu nebo bokorysu a $action."
    }
}

private fun axoLineCompletionSecondProjectionInfo(state: MongeState): String {
    val pending = state.pendingAxoLineCompletion ?: return ""
    val currentKind = null
        ?: return "Přepněte do půdorysu, nárysu, bokorysu nebo axo průmětu."

    if (currentKind == pending.firstKind) {
        return "Přepněte do jiného průmětu než je původní."
    }

    if (pending.secondKind != null && pending.secondKind != currentKind) {
        return "Dokončujete přímku v jiném průmětu, než ve kterém jste začal."
    }

    if (state.reusingExistingProjection) {
        return "Vyberte existující druhý průmět přímky."
    }

    return when (state.constructionModifier) {
        ConstructionModifier.PARALLEL ->
            if (!false) "Zvolte rovnoběžný směr kliknutím na přímku či úsečku."
            else "Umístěte druhý průmět přímky."
        ConstructionModifier.ORTHOGONAL ->
            if (!false) "Zvolte kolmý směr kliknutím na přímku či úsečku."
            else "Umístěte druhý průmět přímky."
        ConstructionModifier.NONE ->
            if (state.completingLineSecondStart == null) "Umístěte první bod druhého průmětu přímky."
            else "Umístěte druhý bod druhého průmětu přímky."
    }
}

// Analogická zpráva pro doplňování bodů v AXO (monge/input/axo/points/pointcomplete/*).
// I tady desktop rozlišuje aktuální AxoMode, který na webu neexistuje.
private fun axoPointCompletionInfo(firstKind: ProjectionKind): String =
    when (firstKind) {
        ProjectionKind.PUDORYS, ProjectionKind.BOKORYS, ProjectionKind.NARYS -> "Přepněte do jiné průmětny a umístěte druhý průmět bodu."
        ProjectionKind.AXO -> "Přepněte do půdorysu, nárysu nebo bokorysu a umístěte druhý průmět bodu."
    }

fun updateConstructionInfo(state: MongeState) {
    val info = when (state.projectionPhase) {

        "ruled_surface_directrix_select" -> ruledSurfaceSelectionInfo(state)

        "pudorys_start" -> {
            if (  state.showPlaneNamingDialog) {"Pojmenujte rovinu."}
            else
            when (state.drawobjects){
                Mongeobjects.TRANSORTH -> "Vyberte přímku."
                Mongeobjects.NONE -> ""
                Mongeobjects.RULED_SURFACE -> ruledSurfaceSelectionInfo(state)
                Mongeobjects.POINTS ->
                    when (state.projectionMode)
                    {
                        ProjectionMode.MONGE ->    when(state.projekcnityp) {
                            ProjectionType.SINGLE -> "Umístěte půdorys bodu."
                            ProjectionType.ASSOCIATED -> "Umístěte půdorys bodu."
                            ProjectionType.AUXILIARY -> "Umístěte pomocný bod."
                        }
                        ProjectionMode.AXO -> when(state.axoMode) {
                            AxoMode.NORMAL_2D -> if (state.projekcnityp == ProjectionType.AUXILIARY) "Umístěte pomocný bod." else "Umístěte axonometrický průmět bodu."
                            AxoMode.AXO_PUDORYS -> "Umístěte půdorys bodu."
                            AxoMode.AXO_NARYS -> "Umístěte nárys bodu."
                            AxoMode.AXO_BOKORYS -> "Umístěte bokorys bodu."
                        }
                        ProjectionMode.PLANE -> "Umístěte bod"
                        ProjectionMode.KOTO ->    when(state.projekcnityp) {
                            ProjectionType.SINGLE -> "Umístěte průmět bodu (s kótou či bez kóty)"
                            ProjectionType.ASSOCIATED -> "Vyberte rovinu nebo přímku, na které leží bod"
                            ProjectionType.AUXILIARY -> "Umístěte pomocný bod."
                        }
                    }

                Mongeobjects.SEGMENT_ON_LINE-> "Umístěte první bod přímky, na které bude ležet úsečka"
                Mongeobjects.LINES -> when (state.constructionModifier){
                    ConstructionModifier.NONE ->when (state.projectionMode){
                        ProjectionMode.MONGE ->  if (state.lineStartPoint3DPudorys == null){"Kliknutím umístěte první bod přímky."}
                        else {"Umístěte druhý bod přímky"}
                        ProjectionMode.AXO -> when(state.axoMode) {
                            AxoMode.NORMAL_2D -> if (state.pendingPoint1==null && state.pendingAxoPoint1 == null) {if (state.projekcnityp == ProjectionType.AUXILIARY) "Umístěte první bod pomocné přímky."
                            else "Umístěte první bod axonometrického průmětu přímky."} else {if (state.projekcnityp == ProjectionType.AUXILIARY) "Dokončete pomocnou přímku" else "Dokončete axonometrický průmět přímky"}
                            AxoMode.AXO_PUDORYS ->if (state.lineStartPoint3DPudorys == null) {"Umístěte první bod půdorysné přímky"} else {"Dokončete půdorysnou přímku"}
                            AxoMode.AXO_NARYS ->if (state.lineStartPoint3DNarys == null) {"Umístěte první bod nárysné přímky"} else {"Dokončete nárysnou přímku"}
                            AxoMode.AXO_BOKORYS -> if (state.lineStartPoint3DBokorys == null) {"Umístěte první bod bokorysné přímky"} else {"Dokončete bokorysnou přímku"}
                        }
                        ProjectionMode.PLANE -> if (state.lineStartPoint3DPudorys == null){"Kliknutím umístěte první bod přímky."}
                        else {"Umístěte druhý bod přímky"}
                        ProjectionMode.KOTO -> {when (state.projekcnityp) {
                            ProjectionType.SINGLE ->  if (state.lineStartPoint3DPudorys == null){"Kliknutím umístěte první bod přímky."}
                            else {"Umístěte druhý bod přímky"}
                            ProjectionType.ASSOCIATED -> {
                                if (state.kotoLinePickAId == null) "Vyberte první (okótovaný) bod přímky"
                                else "Vyberte druhý (okótovaný) bod přímky"
                            }
                            ProjectionType.AUXILIARY ->if (state.lineStartPoint3DPudorys == null){"Kliknutím umístěte první bod přímky."}
                            else {"Umístěte druhý bod přímky"}
                        }
                        }

                    }
                    ConstructionModifier.PARALLEL -> {
                        when (state.projectionMode) {
                            ProjectionMode.MONGE, ProjectionMode.KOTO, ProjectionMode.PLANE -> {
                                if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) { "Vyberte přímku nebo úsečku a poté zvolte umístění." } else { "Umístěte přímku."}}
                            ProjectionMode.AXO -> when(state.axoMode) {
                                AxoMode.NORMAL_2D ->   if (false) {if (state.projekcnityp == ProjectionType.AUXILIARY) "Umístěte pomocnou přímku."
                                else "Umístěte axonometrický průmět přímky."} else { "Zvolte rovnoběžný směr kliknutím na přímku či úsečku."}
                                AxoMode.AXO_PUDORYS ->if (false) {"Umístěte půdorysný průmět přímky"} else { "Zvolte rovnoběžný směr kliknutím na přímku či úsečku."}
                                AxoMode.AXO_NARYS ->if (false) {"Umístěte nárysný průmět přímky"} else { "Zvolte rovnoběžný směr kliknutím na přímku či úsečku."}
                                AxoMode.AXO_BOKORYS -> if (false) {"Umístěte bokorysný průmět přímky"} else { "Zvolte rovnoběžný směr kliknutím na přímku či úsečku."}
                            }
                        }
                    }

                    ConstructionModifier.ORTHOGONAL -> {
                        when (state.projectionMode) {
                            ProjectionMode.MONGE, ProjectionMode.KOTO, ProjectionMode.PLANE -> {
                                if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) { "Vyberte přímku nebo úsečku a poté zvolte umístění." } else { "Umístěte přímku."}}
                            ProjectionMode.AXO -> when(state.axoMode) {
                                AxoMode.NORMAL_2D ->   if (false) {if (state.projekcnityp == ProjectionType.AUXILIARY) "Umístěte pomocnou přímku."
                                else "Umístěte axonometrický průmět přímky."} else { "Zvolte kolmý směr kliknutím na přímku či úsečku."}
                                AxoMode.AXO_PUDORYS ->if (false) {"Umístěte půdorysný průmět přímky."} else { "Zvolte kolmý směr kliknutím na přímku či úsečku."}
                                AxoMode.AXO_NARYS ->if (false) {"Umístěte nárysný průmět přímky."} else { "Zvolte kolmý směr kliknutím na přímku či úsečku."}
                                AxoMode.AXO_BOKORYS -> if (false) {"Umístěte bokorysný průmět přímky."} else { "Zvolte kolmý směr kliknutím na přímku či úsečku."}
                            }
                        }
                    }
                }

                Mongeobjects.PLANE ->
                    when (state.projectionMode) {
                        ProjectionMode.MONGE -> when (state.constructionModifier) {
                            ConstructionModifier.NONE -> "Umístěte první bod půdorysné stopy."
                            ConstructionModifier.PARALLEL -> "Vyberte přímku nebo úsečku rovnoběžnou s půdorysnou stopou."
                            ConstructionModifier.ORTHOGONAL -> "Vyberte přímku nebo úsečku kolmou k půdorysné stopě."
                        }

                        ProjectionMode.AXO -> ""
                        ProjectionMode.PLANE -> ""
                        ProjectionMode.KOTO -> when (state.projekcnityp){
                            ProjectionType.SINGLE -> "Umístěte první bod půdorysné stopy."
                            ProjectionType.ASSOCIATED -> "Vyberte postupně (nekolineární) trojici bodů, které určují rovinu."
                            ProjectionType.AUXILIARY -> ""
                        }
                    }

                Mongeobjects.SEGMENTS -> when (state.constructionModifier){
                    ConstructionModifier.NONE ->
                        when (state.projectionMode){
                            ProjectionMode.MONGE -> when (state.projekcnityp){
                                ProjectionType.SINGLE -> if (state.segmentStartPudorys==null) {"Umístěte první bod úsečky."}
                                else {"Umístěte druhý bod úsečky"}
                                ProjectionType.ASSOCIATED -> "Umístěte půdorysný průmět prvního bodu úsečky."
                                ProjectionType.AUXILIARY -> if (state.segmentStartPudorys==null ) {"Umístěte první bod úsečky."}
                                else {"Umístěte druhý bod úsečky"}
                            }
                            ProjectionMode.AXO ->   when (state.projekcnityp){
                                ProjectionType.SINGLE -> if (state.segmentStartPudorys==null) {"Umístěte první bod úsečky."}
                            else {"Umístěte druhý bod úsečky"}
                                ProjectionType.AUXILIARY -> if (state.segmentStartPudorys==null ) {"Umístěte první bod úsečky."}
                            else {"Umístěte druhý bod úsečky"}
                                else -> ""
                        }
                            ProjectionMode.PLANE -> if (state.segmentStartPudorys==null ) {"Umístěte první bod úsečky."}
                            else {"Umístěte druhý bod úsečky"}
                            ProjectionMode.KOTO -> when (state.projekcnityp) {
                                ProjectionType.SINGLE -> if (state.segmentStartPudorys == null) {
                                    "Umístěte první bod úsečky."
                                } else {
                                    "Umístěte druhý bod úsečky"
                                }

                                ProjectionType.ASSOCIATED -> {
                                    if (state.kotoSegPickAId == null) "Vyberte první bod úsečky"
                                    else "Vyberte druhý bod úsečky"
                                }

                                ProjectionType.AUXILIARY -> if (state.segmentStartPudorys == null) {
                                    "Umístěte první bod úsečky."
                                } else {
                                    "Umístěte druhý bod úsečky"
                                }
                            }
                        }
                    ConstructionModifier.PARALLEL -> { if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null){
                        "Vyberte přímku nebo úsečku a poté zvolte umístění."
                    }
                      else{    "Umístěte úsečku."}}
                    ConstructionModifier.ORTHOGONAL -> {if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null)
                    {
                        "Vyberte přímku nebo úsečku a poté zvolte umístění."}
                    else {"Umístěte úsečku."}}
                }

                Mongeobjects.AID_MIDPOINT ->   if (state.midpointPoint1 == null) {"Vyberte první bod."}
                else {"Vybráním druhého bodu přidáte střed."}
                Mongeobjects.ARC -> when {
                   ( state.arc.arcCenterPudorys==null && state.pendingPoint1 == null) -> {
                        "Zvolte střed oblouku."
                    }
                   ( state.arc.arcRadiusPointPudorys == null && state.pendingPoint2 == null)-> {
                        "Zvolte poloměr oblouku."
                    }
                   ( state.arc.arcStartPointPudorys == null && state.pendingPoint3 == null) -> {
                        "Zvolte počáteční bod oblouku."
                    }
                    else -> "Zvolte koncový bod oblouku, pro změnu směru klikněte pravým tlačítkem myši."}
                Mongeobjects.GETDISTANCE -> "Vyberte první bod."
                Mongeobjects.TYPEDISTANCE -> "Zadejte vzdálenost."
                Mongeobjects.TYPEANGLE -> "Zadejte úhel."
                Mongeobjects.GETANGLE -> if (state.helixPitchAngleTransferActive) {
                    "Přeneste úhel stoupání: vyberte vrchol úhlu."
                } else "Vyberte vrchol úhlu."
                Mongeobjects.TRANSPARALLEL -> "Vyberte přímku."
                Mongeobjects.ERASE -> "Kliknutím na objekt v půdorysu jej smažete."
                Mongeobjects.CIRCLE -> when (state.projekcnityp){
                    ProjectionType.SINGLE -> "Umístěte střed kružnice."
                    ProjectionType.ASSOCIATED -> "Vyberte rovinu kružnice."
                    ProjectionType.AUXILIARY -> "Umístěte střed pomocné kružnice."
                }

                Mongeobjects.ELLIPSE ->  when (state.projekcnityp){
                    ProjectionType.SINGLE -> "Umístěte střed elipsy."
                    ProjectionType.ASSOCIATED -> "Vyberte rovinu elipsy."
                    ProjectionType.AUXILIARY -> ""
                }

                Mongeobjects.PARABOLA -> when (state.projekcnityp){
                    ProjectionType.SINGLE -> "Umístěte vrchol paraboly."
                    ProjectionType.ASSOCIATED -> "Vyberte rovinu paraboly."
                    ProjectionType.AUXILIARY -> ""
                }

                Mongeobjects.HYPERBOLA -> when (state.projekcnityp){
                    ProjectionType.SINGLE -> "Vyberte první asymptotu hyperboly v půdorysu."
                    ProjectionType.ASSOCIATED -> "Vyberte rovinu hyperboly."
                    ProjectionType.AUXILIARY -> ""
                }
                Mongeobjects.CONICARC -> "Označte kuželosečku"
                Mongeobjects.CONE -> "Vyberte řídicí kuželosečku a vrchol."
                Mongeobjects.CYLINDER -> when (state.cylinderPhase) {
                    CylinderPhase.IDLE -> ""
                    CylinderPhase.PICK_CONIC -> "Zvolte podstavnou elipsu válce."
                    CylinderPhase.PICK_DIRECTION -> "Zvolte směr tvořicích přímek válce kliknutím na prostorovou přímku či úsečku."
                    CylinderPhase.PICK_PLANE -> "Zvolte rovinu omezující válcovou plochu."
                    CylinderPhase.PICK_CONIC_PERP -> "Zvolte podstavnou elipsu kolmého válce."
                    CylinderPhase.PICK_CENTER_PERP -> "Kliknutím umístěte průmět středu horní podstavy."
                }
                Mongeobjects.SPHERE -> "Zvolte půdorysný průmět středu kulové plochy."
                Mongeobjects.CONICARCAS -> "Označte jeden z průmětů kuželosečky."
                Mongeobjects.REGULAR_POLYGON_IN_PLANE -> if (state.projectionMode == ProjectionMode.PLANE)"Zvolte střed obrazce" else "Zvolte rovinu obrazce."
                Mongeobjects.PLANE_LIFT -> "Zvolte rovinu objektu"
                Mongeobjects.CURVE -> when (state.projekcnityp){
                    ProjectionType.SINGLE ->if (state.projectionMode == ProjectionMode.PLANE) {
                        "Zvolte postupně body, kterámi bude procházet křivka"
                    }
                    else {"Zvolte postupně body půdorysu, kterými bude procházet křivka, poté potvrďte klávesou Enter."}+
                            "Počet vybraných bodů:${state.pudorysCurvePickPointIds.size}"
                    ProjectionType.ASSOCIATED -> "Zvolte postupně body (s oběma průměty), kterými bude procházet 3D křivka, poté potvrďte klávesou Enter.  "+
                            "Počet vybraných bodů:${state.curve3DPickPointIds.size}"
                    ProjectionType.AUXILIARY -> if (state.projectionMode == ProjectionMode.PLANE) {
                        "Zvolte postupně body, kterámi bude procházet křivka"
                    }
                    else {"Zvolte postupně body půdorysu, kterými bude procházet křivka, poté potvrďte klávesou Enter."}+
                            "Počet vybraných bodů:${state.pudorysCurvePickPointIds.size}"
                }
                Mongeobjects.HELIX -> "Zvolte osu šroubovice kolmou na některou z průměten."
                Mongeobjects.SOLID_OF_REVOLUTION -> "Zvolte osu rotace kolmou na půdorysnu"
                Mongeobjects.INTERSECTION -> if (state.intersectionPicks.isEmpty())
                    "Průnik: vyberte první objekt."
                else "Průnik: vyberte druhý objekt (1. = ${state.intersectionPicks.first().label})."
                Mongeobjects.PYRAMID -> "Vyberte podstavu (n-úhelník) a vrchol jehlanu."
                Mongeobjects.PRISM -> "Vyberte podstavu a výšku hranolu."
                Mongeobjects.PLATONIC_SOLID -> platonicSolidInfo(state)
            }
        }
        "narys_start" -> {
            when (state.drawobjects){
                Mongeobjects.TRANSORTH -> "Vyberte přímku."
                Mongeobjects.NONE -> ""
                Mongeobjects.POINTS -> when(state.projekcnityp) {
                    ProjectionType.SINGLE -> "Umístěte nárys bodu."
                    ProjectionType.ASSOCIATED -> "Umístěte nárys bodu."
                    ProjectionType.AUXILIARY -> "Umístěte pomocný bod."
                }
                Mongeobjects.LINES -> when (state.constructionModifier){
                    ConstructionModifier.NONE ->  if (state.lineStartPoint3DNarys == null){"Kliknutím umístěte první bod přímky."}
                    else {"Umístěte druhý bod přímky"}
                    ConstructionModifier.PARALLEL -> "Vyberte rovnoběžnou přímku nebo úsečku."
                    ConstructionModifier.ORTHOGONAL -> "Vyberte kolmou přímku nebo úsečku."
                }
                Mongeobjects.SEGMENT_ON_LINE-> "Umístěte první bod přímky, na které bude ležet úsečka"
                Mongeobjects.PLANE -> when (state.constructionModifier){
                    ConstructionModifier.NONE -> "Umístěte první bod nárysné stopy."
                    ConstructionModifier.PARALLEL -> "Vyberte přímku nebo úsečku rovnoběžnou s nárysnou stopou."
                    ConstructionModifier.ORTHOGONAL -> "Vyberte přímku nebo úsečku kolmou k nárysné stopě."
                }

                Mongeobjects.SEGMENTS -> when (state.constructionModifier){
                    ConstructionModifier.NONE -> when (state.projekcnityp){
                        ProjectionType.SINGLE -> if (state.segmentStartPudorys==null) {"Umístěte první bod úsečky."}
                        else {"Umístěte druhý bod úsečky"}
                        ProjectionType.ASSOCIATED -> "Umístěte nárysný průmět prvního bodu úsečky."
                        ProjectionType.AUXILIARY -> if (state.segmentStartPudorys==null) {"Umístěte první bod úsečky."}
                        else {"Umístěte druhý bod úsečky"}
                    }
                    ConstructionModifier.PARALLEL -> {if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null)
                    {
                        "Vyberte přímku nebo úsečku a poté zvolte umístění."}
                    else {"Umístěte úsečku."}}
                    ConstructionModifier.ORTHOGONAL -> {if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null)
                    {
                        "Vyberte přímku nebo úsečku a poté zvolte umístění."}
                    else {"Umístěte úsečku."}}
                }

                Mongeobjects.AID_MIDPOINT ->   if (state.midpointPoint1 == null) {"Vyberte první bod."}
                else {"Vybráním druhého bodu přidáte střed."}
                Mongeobjects.ARC -> when {
                    state.arc.arcCenterNarys == null -> {
                        "Zvolte střed oblouku."
                    }
                    state.arc.arcRadiusPointNarys == null -> {
                        "Zvolte poloměr oblouku."
                    }
                    state.arc.arcStartPointNarys == null -> {
                        "Zvolte počáteční bod oblouku."
                    }
                    else -> "Zvolte koncový bod oblouku, pro změnu směru klikněte pravým tlačítkem myši."}
                Mongeobjects.GETDISTANCE -> "Vyberte první bod."
                Mongeobjects.TYPEDISTANCE -> "Zadejte vzdálenost."
                Mongeobjects.TYPEANGLE -> "Zadejte úhel."
                Mongeobjects.GETANGLE -> if (state.helixPitchAngleTransferActive) {
                    "Přeneste úhel stoupání: vyberte vrchol úhlu."
                } else "Vyberte vrchol úhlu."
                Mongeobjects.TRANSPARALLEL -> "Vyberte přímku."
                Mongeobjects.ERASE -> "Kliknutím na objekt v nárysu jej smažete."
                Mongeobjects.CIRCLE -> when (state.projekcnityp){
                    ProjectionType.SINGLE -> "Umístěte střed kružnice."
                    ProjectionType.ASSOCIATED -> "Vyberte rovinu kružnice."
                    ProjectionType.AUXILIARY -> "Umístěte střed pomocné."
                }

                Mongeobjects.ELLIPSE ->  when (state.projekcnityp){
                    ProjectionType.SINGLE -> "Umístěte střed elipsy."
                    ProjectionType.ASSOCIATED -> "Vyberte rovinu elipsy."
                    ProjectionType.AUXILIARY -> ""
                }

                Mongeobjects.PARABOLA -> when (state.projekcnityp){
                    ProjectionType.SINGLE -> "Umístěte vrchol paraboly."
                    ProjectionType.ASSOCIATED -> "Vyberte rovinu paraboly."
                    ProjectionType.AUXILIARY -> ""
                }

                Mongeobjects.HYPERBOLA -> when (state.projekcnityp){
                    ProjectionType.SINGLE -> "Vyberte první asymptotu hyperboly v nárysu."
                    ProjectionType.ASSOCIATED -> "Vyberte rovinu hyperboly."
                    ProjectionType.AUXILIARY -> ""
                }
                Mongeobjects.PLANE_LIFT -> "Zvolte rovinu objektu"
                Mongeobjects.CONICARC -> "Označte kuželosečku"
                Mongeobjects.CONE -> "Vyberte řídicí kuželosečku a vrchol."
                Mongeobjects.CYLINDER -> when (state.cylinderPhase) {
                    CylinderPhase.IDLE -> ""
                    CylinderPhase.PICK_CONIC -> "Zvolte podstavnou elipsu válce."
                    CylinderPhase.PICK_DIRECTION -> "Zvolte směr tvořicích přímek válce kliknutím na prostorovou přímku či úsečku."
                    CylinderPhase.PICK_PLANE -> "Zvolte rovinu omezující válcovou plochu."
                    CylinderPhase.PICK_CONIC_PERP -> "Zvolte podstavnou elipsu kolmého válce."
                    CylinderPhase.PICK_CENTER_PERP -> "Kliknutím umístěte průmět středu horní podstavy."
                }
                Mongeobjects.SPHERE -> "Zvolte nárysný průmět středu kulové plochy."
                Mongeobjects.CONICARCAS -> "Označte jeden z průmětů kuželosečky."
                Mongeobjects.REGULAR_POLYGON_IN_PLANE ->  "Zvolte rovinu obrazce."
                Mongeobjects.CURVE -> when (state.projekcnityp){
                    ProjectionType.SINGLE ->"Zvolte postupně body půdorysu, kterými bude procházet křivka, poté potvrďte klávesou Enter.  "+
                            "Počet vybraných bodů:${state.pudorysCurvePickPointIds.size}"
                    ProjectionType.ASSOCIATED -> "Zvolte postupně body (s oběma průměty), kterými bude procházet 3D křivka, poté potvrďte klávesou Enter.  "+
                            "Počet vybraných bodů:${state.curve3DPickPointIds.size}"
                    ProjectionType.AUXILIARY -> "Zvolte postupně body půdorysu, kterými bude procházet křivka, poté potvrďte klávesou Enter.  "+
                            "Počet vybraných bodů:${state.pudorysCurvePickPointIds.size}"
                }
                Mongeobjects.HELIX -> "Zvolte osu šroubovice kolmou na některou z průměten."
                Mongeobjects.SOLID_OF_REVOLUTION -> "Zvolte osu rotace (podporována je pouze kolmice na jednu z průměten)"
                Mongeobjects.INTERSECTION -> if (state.intersectionPicks.isEmpty())
                    "Průnik: vyberte první objekt."
                else "Průnik: vyberte druhý objekt (1. = ${state.intersectionPicks.first().label})."
                Mongeobjects.PYRAMID -> "Vyberte podstavu (n-úhelník) a vrchol jehlanu."
                Mongeobjects.PRISM -> "Vyberte podstavu a výšku hranolu."
                Mongeobjects.PLATONIC_SOLID -> platonicSolidInfo(state)
                else -> {""}
            }
        }
        "ellipse_point2" -> {
            "Umístěte krajní bod prvního sdruženého poloměru elipsy."
        }
        "ellipse_point3" -> {
            "Umístěte krajní bod druhého sdruženého poloměru elipsy."
        }
        "pudorys_vertex" -> {
            when (state.drawobjects) {
                Mongeobjects.PARABOLA -> { when (state.projekcnityp)
                {
                  ProjectionType.SINGLE ->   "Umístěte vrchol paraboly."
                  ProjectionType.ASSOCIATED -> "Umístěte půdorysný průmět vrcholu paraboly."
                  else -> ""
                }}
                Mongeobjects.HYPERBOLA -> "Umístěte půdorysný průmět vrcholu hyperboly v rovině ${state.selectedPlaneForCircle?.name}."
                else -> ""

            }

        }
        "narys_vertex" -> {
            when (state.drawobjects) {
               Mongeobjects.PARABOLA -> { when (state.projekcnityp)
               {
                   ProjectionType.SINGLE ->   "Umístěte vrchol paraboly."
                   ProjectionType.ASSOCIATED -> "Umístěte nárysný průmět vrcholu paraboly."
                   else -> ""
               }}
               Mongeobjects.HYPERBOLA -> "Umístěte nárysný průmět vrcholu hyperboly v rovině ${state.selectedPlaneForCircle?.name}."
                else -> ""
            }

        }
        "parabola_focus"-> {
            "Umístěte ohnisko paraboly."
        }
        "parabola_focus_narys"-> {
            "Umístěte ohnisko paraboly."
        }
        "hyperbola_asymptote2" -> {
            "Vyberte druhou asymptotu hyperboly v půdorysu."
        }
        "hyperbola_vertex" -> {
            "Umístěte jeden z vrcholů hyperboly v půdorysu."
        }
        "hyperbola_asymptote2_narys" ->{
            "Vyberte druhou asymptotu hyperboly v nárysu."
        }
        "hyperbola_vertex_narys"-> {
            "Umístěte jeden z vrcholů hyperboly v nárysu."
        }
        "projection_line_dir_pudorys" -> {
            "Umístěte druhý bod půdorysné projekce přímky."
        }
        "projection_line_start_narys" -> {
            when (state.constructionModifier){
                ConstructionModifier.NONE -> "Umístěte první bod nárysné projekce přímky."
                ConstructionModifier.PARALLEL -> "Vyberte rovnoběžnou přímku nebo úsečku."
                ConstructionModifier.ORTHOGONAL -> "Vyberte kolmou přímku nebo úsečku."
            }
        }
        "projection_line_narys_dir" -> {
            "Umístěte druhý bod nárysné projekce přímky."
        }
        "special_case_point_in_narys" -> {
            "Umístěte nárys přímky (bod)."
        }
        "projection_line_dir_narys_start"-> {
            "Umístěte druhý bod nárysné projekce přímky."
        }
        "projection_line_start_pudorys" -> {
            when (state.constructionModifier){
                ConstructionModifier.NONE -> "Umístěte první bod půdorysné projekce přímky."
                ConstructionModifier.PARALLEL -> "Vyberte rovnoběžnou přímku nebo úsečku."
                ConstructionModifier.ORTHOGONAL -> "Vyberte kolmou přímku nebo úsečku."
            }
        }
        "narys_dir_finalize_auto" -> {
            "Umístěte druhý bod nárysné projekce přímky."
        }
        "projection_line_start_pudorys_dir" -> {
            "Umístěte první bod půdorysné projekce přímky."
        }
        "special_case_point_in_pudorys" -> {
            "Umístěte půdorys přímky (bod)."
        }
        "pudorys_ready" -> {
            "Umístěte půdorysný průmět kružnice v rovině."
        }
        "circle_plane_radius" -> {
            "Umístěte půdorysný průmět bodu na kružnici v rovině."
        }
        "narys_ready" -> {
            "Umístěte nárysný průmět kružnice v rovině."
        }
        "circle_plane_radius_narys" -> {
            "Umístěte nárysný průmět bodu na kružnici v rovině."
        }
        "ellipse_plane_point1" -> {
            "Umístěte půdorysný průmět středu elipsy."
        }
        "ellipse_plane_point2" -> {
            "Umístěte půdorysný průmět krajního bodu prvního sdruženého poloměru elipsy."
        }
        "ellipse_plane_point3" -> {
            "Umístěte půdorysný průmět krajního bodu druhého sdruženého poloměru elipsy."
        }
        "ellipse_plane_point1_narys" -> {
            "Umístěte nárysný průmět středu elipsy."
        }
        "ellipse_plane_point2_narys" -> {
            "Umístěte nárysný průmět krajního bodu prvního sdruženého poloměru elipsy."
        }
        "ellipse_plane_point3_narys" -> {
            "Umístěte nárysný průmět krajního bodu druhého sdruženého poloměru elipsy."
        }
        "pudorys_asymptote1" -> {
            "Vyberte půdorysný průmět první asymptoty hyperboly."
        }
        "pudorys_asymptote2" -> {
            "Vyberte půdorysný průmět druhé asymptoty hyperboly."
        }
        "narys_asymptote1" -> {
            "Vyberte nárysný průmět první asymptoty hyperboly."
        }
        "narys_asymptote2" -> {
            "Vyberte nárysný průmět druhé asymptoty hyperboly."
        }
        "pudorys_focus","narys_focus"  -> {
            "Umístěte ohnisko paraboly v rovině ${state.selectedPlaneForCircle?.name}."
        }
        "plane_trace_pudorys_start" -> {
            "Umístěte druhý bod půdorysné stopy."
        }
        "plane_trace_narys_direction" -> {
            when (state.constructionModifier){
                ConstructionModifier.ORTHOGONAL ->"Vyberte přímku kolmou na stopu."
                ConstructionModifier.NONE -> "Umístěte bod nárysné stopy."
                ConstructionModifier.PARALLEL -> "Vyberte přímku rovnoběžnou se stopou."
            }
        }
        "plane_trace_single_narys_start" -> "Umístěte první bod nárysné stopy."
        "plane_trace_narys_start" -> "Umístěte první bod nárysné stopy."
        "plane_trace_pudorys_direction" -> {
            when (state.constructionModifier){
                ConstructionModifier.ORTHOGONAL ->"Vyberte přímku kolmou na stopu."
                ConstructionModifier.NONE -> "Umístěte bod půdorysné stopy."
                ConstructionModifier.PARALLEL -> "Vyberte přímku rovnoběžnou se stopou."
            }
        }
        "single_pudorys" -> "Pojmenujte půdorys bodu."
        "single_narys" -> "Pojmenujte nárys bodu."
        "pudorys_to_narys_point" -> "Umístěte nárys bodu."
        "narys_finalize_auto" -> "Umístěte nárys bodu."
        "narys_to_pudorys_point" -> "Umístěte půdorys bodu."
        "pudorys_finalize_auto" -> "Umístěte půdorys bodu."
        "pudorys_finalize" -> "Pojmenujte půdorysný průmět bodu."
        "narys_segment_associated_B_narys_start" -> "Umístěte nárysný průmět druhého bodu úsečky."
        "pudorys_segment_associated_A_narys_start" -> {when (state.constructionModifier) {
            ConstructionModifier.NONE -> "Umístěte půdorysný průmět prvního bodu úsečky."
            ConstructionModifier.PARALLEL ->  "Vyberte přímku nebo úsečku a poté zvolte umístění."
            ConstructionModifier.ORTHOGONAL ->  "Vyberte přímku nebo úsečku a poté zvolte umístění."
        }}
        "pudorys_segment_associated_B_narys_start" -> "Umístěte půdorysný průmět druhého bodu úsečky."
        "pudorys_segment_associated_B_pudorys_start"-> "Umístěte půdorysný průmět druhého bodu úsečky."
        "narys_segment_associated_A_pudorys_start"-> {when (state.constructionModifier) {
            ConstructionModifier.NONE -> "Umístěte nárysný průmět prvního bodu úsečky."
            ConstructionModifier.PARALLEL ->  "Vyberte přímku nebo úsečku a poté zvolte umístění."
            ConstructionModifier.ORTHOGONAL ->  "Vyberte přímku nebo úsečku a poté zvolte umístění."
        }}
        "narys_segment_associated_B_pudorys_start"->"Umístěte nárysný průmět druhého bodu úsečky."
        "angle_point2","angle_point2_axo" -> if (state.helixPitchAngleTransferActive) {
            "Přeneste úhel stoupání: vyberte bod na prvním ramenu."
        } else "Vyberte bod na prvním ramenu úhlu."
        "angle_point3","angle_point3_axo" -> if (state.helixPitchAngleTransferActive) {
            "Přeneste úhel stoupání: vyberte bod na druhém ramenu."
        } else "Vyberte bod na druhém ramenu úhlu."
        "angle_new_vertex","angle_new_vertex_axo"->"Vyberte vrchol přeneseného úhlu."
        "angle_new_ray","angle_new_ray_axo"->"Kliknutím umístíte pomocný bod, pro změnu ramene klikněte pravým tlačítkem myši."
        "distance_point2_select","distance_point2_select_axo" -> "Určete přenášenou vzdálenost."
        "distance_point3_select","distance_point3_select_axo"-> "Umístěte první bod přenesené úsečky."
        "distance_target_place", "distance_target_place_axo",-> "Kliknutím umístíte pomocný bod ve správne vzdálenosti. Pro nanesení kolmo zvolte ⟂."
        "trans_parallel_temp_point_pudorys","trans_parallel_temp_point_narys","axo_trans_parallel_place_line"->"Vyberte přenášený bod."
        "trans_parallel_final_point_narys","trans_parallel_final_point_pudorys","axo_trans_parallel_final"->"Umístěte přenášený bod."
        "narys_finalize"->"Pojmenujte nárysný průmět bodu."
        "rename_point_pudorys"-> "Přejmenujte bod."
        "naming_3d_line"->"Pojmenujte přímku."
        "single_narys_line" -> "Pojmenujte průmět přímky."
        "single_pudorys_line"-> "Pojmenujte průmět přímky."
        "rename_plane"-> "Přejmenujte rovinu."
        "plane_trace_single_pudorys_start"->"Umístěte druhý bod půdorysné stopy."
        "parallel_line_point_selection_pudorys_start" -> "Vyberte přímku nebo úsečku a poté zvolte umístění."
        "parallel_line_point_selection_pudorys_narys_start" -> "Vyberte přímku nebo úsečku a poté zvolte umístění."
        "parallel_line_point_selection_narys_start"-> "Vyberte přímku nebo úsečku a poté zvolte umístění."
        "parallel_line_point_selection_narys_pudorys_start" -> "Vyberte přímku nebo úsečku a poté zvolte umístění."
        "orthogonal_line_point_selection_pudorys_start" -> "Vyberte přímku nebo úsečku a poté zvolte umístění."
        "orthogonal_line_point_selection_pudorys_narys_start" -> "Vyberte přímku nebo úsečku a poté zvolte umístění."
        "orthogonal_line_point_selection_narys_start" -> "Vyberte přímku nebo úsečku a poté zvolte umístění."
        "orthogonal_line_point_selection_narys_pudorys_start" -> "Vyberte přímku nebo úsečku a poté zvolte umístění."
        "segment_parallel_first_point_narys" -> "Umístěte počáteční bod úsečky."
        "segment_parallel_second_point_narys" -> "Umístěte koncový bod úsečky."
        "segment_orthogonal_first_point_narys_pudorys" -> "Umístěte počáteční bod úsečky."
        "narys_segment_associated_B_narys_start_orthogonal" -> "Umístěte koncový bod úsečky."
        "segment_orthogonal_first_point_pudorys_narys" -> "Umístěte počáteční bod úsečky."
        "pudorys_segment_associated_B_pudorys_start_orthogonal" -> "Umístěte koncový bod úsečky."
        "segment_parallel_place_line_narys" -> "Umístěte přímku, na které leží úsečka."
        "segment_parallel_place_line_pudorys" -> "Umístěte přímku, na které leží úsečka."
        "segment_orthogonal_place_line_narys" -> "Umístěte přímku, na které leží úsečka."
        "segment_orthogonal_place_line_pudorys" -> "Umístěte přímku, na které leží úsečka."
        "segment_parallel_first_point_pudorys" -> "Umístěte počáteční bod úsečky."
        "segment_parallel_second_point_pudorys" -> "Umístěte koncový bod úsečky."
        "segment_orthogonal_first_point_pudorys" -> "Umístěte počáteční bod úsečky."
        "segment_orthogonal_second_point_pudorys" -> "Umístěte koncový bod úsečky."
        "segment_orthogonal_first_point_narys" -> "Umístěte počáteční bod úsečky."
        "segment_orthogonal_second_point_narys" -> "Umístěte koncový bod úsečky."
        "narys_segment_associated_A_narys_start" -> "Dokončete úsečku."
        "lift_conic_select_plane_narys" -> "Vyberte rovinu, ve které leží nárys elipsy."
        "lift_conic_select_plane" -> "Vyberte rovinu, ve které leží půdorys elipsy."
        "lift_parabola_select_plane_pudorys" -> "Vyberte rovinu, ve které leží půdorys paraboly."
        "lift_parabola_select_plane_narys" -> "Vyberte rovinu, ve které leží nárys paraboly."
        "lift_hyperbola_select_plane_pudorys" -> "Vyberte rovinu, ve které leží půdorys hyperboly."
        "lift_hyperbola_select_plane_narys" -> "Vyberte rovinu, ve které leží nárys hyperboly."
        "plane_trace_pudorys_special_direction"->"Umístěte druhou stopu roviny rovnoběžné s x₁₂ nebo stiskněte klávesu Enter pro rovinu kolmou na nárysnu."
        "plane_trace_narys_special_direction"->"Umístěte druhou stopu roviny rovnoběžné s x₁₂ nebo stiskněte klávesu Enter pro rovinu kolmou na půdorysnu."
        "rename_aid" -> "Přejmenujte pomocný bod."
        "pudorys_ellipse_arc_hold" -> "Vyberte začátek úseku elipsy."
        "pudorys_ellipse_arc" -> "Vyberte začátek úseku elipsy. Pro otočení směru použijte pravé tlačítko."
        "narys_ellipse_arc_hold" -> "Vyberte začátek úseku elipsy."
        "narys_ellipse_arc" -> "Vyberte začátek úseku elipsy. Pro otočení směru použijte pravé tlačítko."
        "pudorys_hyp_second_hold" -> "Vyznačte úsek na první větvi hyperboly."
        "pudorys_hyp_second" -> "Vyznačte úsek na druhé větvi hyperboly, klávesou ENTER jej přeskočíte."
        "narys_hyp_second_hold" -> "Vyznačte úsek na první větvi hyperboly."
        "narys_hyp_second" -> "Vyznačte úsek na druhé větvi hyperboly, klávesou ENTER jej přeskočíte."
        "pudorys_par_arc" -> "Vyznačte konec úseku na parabole."
        "pudorys_par_arc_hold" -> "Vyznačte začátek úseku na parabole."
        "narys_par_arc" -> "Vyznačte konec úseku na parabole."
        "narys_par_arc_hold" -> "Vyznačte začátek úseku na parabole."
        "narys_elip3d_arc_hold" -> "Vyznačte začátek úseku na prostorové elipse."
        "narys_elip3d_arc" -> "Vyznačte konec úseku na prostorové elipse. Pro otočení směru použijte pravé tlačítko."
        "pudorys_elip3d_arc_hold" -> "Vyznačte začátek úseku na prostorové elipse."
        "pudorys_elip3d_arc" -> "Vyznačte konec úseku na prostorové elipse. Pro otočení směru použijte pravé tlačítko."
        "narys_hyp3d_b1_hold" -> "Vyznačte začátek úseku na první větvi prostorové hyperboly."
        "narys_hyp3d_b1" -> "Vyznačte konec úseku na první větvi prostorové hyperboly."
        "narys_hyp3d_a2" -> "Vyznačte úsek na druhé větvi prostorové hyperboly, klávesou ENTER jej přeskočíte."
        "narys_hyp3d_b2" -> "Vyznačte úsek na druhé větvi prostorové hyperboly, klávesou ENTER jej přeskočíte."
        "pudorys_hyp3d_b1" -> "Vyznačte konec úseku na první větvi prostorové hyperboly."
        "pudorys_hyp3d_b1_hold" -> "Vyznačte začátek úseku na první větvi prostorové hyperboly."
        "pudorys_hyp3d_a2" -> "Vyznačte úsek na druhé větvi prostorové hyperboly, klávesou ENTER jej přeskočíte."
        "pudorys_hyp3d_b2" -> "Vyznačte úsek na druhé větvi prostorové hyperboly, klávesou ENTER jej přeskočíte."
        "pudorys_par3d_arc_hold"-> "Vyznačte začátek úseku na prostorové parabole."
        "pudorys_par3d_arc"-> "Vyznačte konec úseku na prostorové parabole."
        "narys_par3d_arc_hold" -> "Vyznačte začátek úseku na prostorové parabole."
        "narys_par3d_arc" -> "Vyznačte konec úseku na prostorové parabole."
        "rp_center_pud" -> "Zvolte půdorysný průmět středu obrazce."
        "rp_vertex_pud" ->  if (state.projectionMode == ProjectionMode.PLANE)"Zvolte vrchol obrazce" else "Zvolte půdorysný průmět vrcholu obrazce."
        "rp_center_nar" -> "Zvolte nárysný průmět středu obrazce."
        "rp_vertex_nar" -> "Zvolte nárysný průmět vrcholu obrazce."
        "sphere_radius_pick_pudorys"-> "Zvolte poloměr kulové plochy."
        "sphere_radius_pick_narys"-> "Zvolte poloměr kulové plochy."
        "sol_pudorys_second_point_dir" -> "Umístěte druhý bod přímky, na které bude ležet úsečka"
        "sol_narys_second_point_dir" -> "Umístěte druhý bod přímky, na které bude ležet úsečka"
        "sol_begin_segment_pud" -> "Umístěte na přímce první bod úsečky"
        "sol_second_point_pudorys" -> "Umístěte na přímce druhý bod úsečky"
        "sol_begin_segment_nar" -> "Umístěte na přímce první bod úsečky"
        "sol_second_point_narys" -> "Umístěte na přímce druhý bod úsečky"
        "picking_line_points" -> if (state.kotoLinePickAId==null){"Zvolte body (s kótou) ležící na přímce"}
        else {"Vyberte druhý bod ležící na přímce"}
        "plane_trace_pick_point" -> "Vyberte (okótovaný) bod roviny"
        "ready_planelift" -> "Vyberte objekt, který chcete zvednout do roviny (bod, přímka nebo kuželosečka)"
        "plane_point_pick_xy" -> "Umístěte průmět bodu v rovině"
        "line_point_pick_xy" -> "Umístěte průmět bodu na přímku"
        "curve3d_pick_points" -> "Zvolte postupně body (s oběma průměty), kterými bude procházet 3D křivka, poté potvrďte klávesou Enter.  "+
        "Počet vybraných bodů:${state.curve3DPickPointIds.size}"
        "pudorys_curve_pick" -> "Zvolte postupně body půdorysu, kterými bude procházet křivka, poté potvrďte klávesou Enter.  "+
                "Počet vybraných bodů:${state.pudorysCurvePickRefs.size}"
        "narys_curve_pick" -> "Zvolte postupně body půdorysu, kterými bude procházet křivka, poté potvrďte klávesou Enter.  "+
                "Počet vybraných bodů:${state.narysCurvePickPointIds.size}"
        "bokorys_curve_pick" -> "Zvolte postupně body bokorvsu, kterými bude procházet křivka, poté potvrďte klávesou Enter.  "+
                "Počet vybraných bodů:${state.bokorysCurvePickPointIds.size}"
        "rev_axis_select" -> "Zvolte osu rotace (podporována je pouze kolmice na jednu z průměten)"
        "helix_axis_select_tangent", "helix_axis_select_pitch" -> "Zvolte prostorovou osu kolmou na půdorysnu, nárysnu nebo bokorysnu."
        "helix_tangent_select" -> "Zvolte prostorovou tečnou přímku šroubovice."
        "helix_start_point_select" -> "Zvolte prostorový počáteční bod ležící mimo osu šroubovice."
        "helix_turns_dialog" -> "Zadejte počet závitů šroubovice."
        "helix_pitch_dialog" -> "Zadejte redukovaný úhel stoupání šroubovice."
        "helix_height_pick" -> null
        "rev_meridian_select" -> "Vyberte části meridiánu (obouk, úsečka) nebo celou křivku či kružnici v nárysu."
        "axo_complete_point_from_pudorys" -> axoPointCompletionInfo(ProjectionKind.PUDORYS)
        "axo_complete_point_from_narys" -> axoPointCompletionInfo(ProjectionKind.NARYS)
        "axo_complete_point_from_bokorys" -> axoPointCompletionInfo(ProjectionKind.BOKORYS)
        "axo_complete_point_from_axo" -> axoPointCompletionInfo(ProjectionKind.AXO)
        "axo_complete_line_waiting_for_second_projection" -> axoLineCompletionWaitingInfo(state)
        "axo_complete_line_second_projection" -> axoLineCompletionSecondProjectionInfo(state)
        "rev_axis_select_pudorys" -> "Vyberte přímku kolmou na nárysnu jako osu"
        "rev_axis_select_narys" -> "Vyberte přímku kolmou na půdorysnu jako osu"

        "rev_meridian_select_pudorys", "rev_meridian_select_narys" -> "Zvolte části meridiánu v dané průmětně - kružnice, kuželosečka, obecná křivka nebo křivka složená z jednotlivých segmentů a oblouků. Poté potvrďte klávesou ENTER"
        else -> {
        ""
        }
    }

    state.consInfo.value = info ?: ""
}
fun setProjectionPhase(phase: String, state: MongeState) {
    state.projectionPhase = phase
    updateConstructionInfo(state)
}
fun repeatCons(state: MongeState){
    if (state.suspendRepeatCons) return
    resetStavu(state)
    if (state.repeatCons.value) {
        state.drawobjects = Mongeobjects.NONE
    }
}
