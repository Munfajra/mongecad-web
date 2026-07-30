package gl3d.api

/**
 * Kreslicí plocha 3D scény.
 *
 * Na desktopu je to samostatné GLFW okno (`opengl/openglWindow.kt`); na webu
 * druhý `<canvas>` uvnitř `#webApp`, absolutně polohovaný přesně na obdélník
 * 3D panelu. Compose o něm neví – jen mu přes [setRect] hlásí, kam patří.
 *
 * Plátno má v CSS `pointer-events: none`, takže všechny události propadnou
 * skrz na Compose plátno pod ním. Orbit, pan i zoom tím pádem řeší normální
 * Compose `pointerInput`, ne DOM listenery.
 */
interface GlSurface {

    val gl: Gl

    /** Rozměr backing storu v device pixelech (CSS rozměr × devicePixelRatio). */
    val pixelWidth: Int
    val pixelHeight: Int

    /**
     * Umístění plátna v **CSS pixelech** relativně ke kontejneru aplikace.
     * Volá se při každé změně layoutu; implementace si sama poradí s DPR.
     */
    fun setRect(x: Float, y: Float, width: Float, height: Float)

    /**
     * Dočasné skrytí. Používá se, když je nad viewportem otevřený Compose
     * dialog nebo menu – to by se jinak vykreslilo *pod* GL plátnem.
     */
    fun setVisible(visible: Boolean)

    /**
     * Byl mezitím ztracen a znovu získán grafický kontext? Po `true` musí
     * renderer zahodit všechny GPU zdroje a postavit je znovu.
     */
    fun consumeContextRestored(): Boolean

    /** Je kontext právě ztracený? Během toho nemá smysl nic kreslit. */
    fun isContextLost(): Boolean

    fun dispose()
}

/**
 * Vytvoří kreslicí plochu, nebo `null`, když prostředí 3D nepodporuje
 * (chybějící WebGL2, zakázaná akcelerace). Volající pak místo viewportu
 * ukáže srozumitelnou hlášku.
 */
expect fun createGlSurface(): GlSurface?
