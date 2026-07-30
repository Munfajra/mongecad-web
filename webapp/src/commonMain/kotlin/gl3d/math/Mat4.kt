package gl3d.math

/**
 * Sloupcová 4×4 matice – náhrada JOML `Matrix4f`, které je JVM-only.
 *
 * Rozložení je stejné jako v OpenGL: `data[col * 4 + row]`, takže pole jde
 * poslat rovnou do `uniformMatrix4fv` bez transpozice.
 *
 * Desktop matice vůbec nestaví – používá fixní pipeline (`glMatrixMode`,
 * `glOrtho`, `glMultMatrixf`) a hotový výsledek si pak čte zpátky přes
 * `glGetFloatv(GL_MODELVIEW_MATRIX)`. Ve WebGL2 fixní pipeline neexistuje,
 * takže tytéž matice sestavujeme přímo tady (viz [gl3d.camera.buildCamera]).
 */
class Mat4(val data: FloatArray) {

    init {
        require(data.size == 16) { "Mat4 potřebuje 16 prvků, dostal ${data.size}" }
    }

    operator fun get(row: Int, col: Int): Float = data[col * 4 + row]

    operator fun times(other: Mat4): Mat4 {
        val a = data
        val b = other.data
        val out = FloatArray(16)
        for (col in 0 until 4) {
            val b0 = b[col * 4]
            val b1 = b[col * 4 + 1]
            val b2 = b[col * 4 + 2]
            val b3 = b[col * 4 + 3]
            for (row in 0 until 4) {
                out[col * 4 + row] =
                    a[row] * b0 + a[4 + row] * b1 + a[8 + row] * b2 + a[12 + row] * b3
            }
        }
        return Mat4(out)
    }

    /** Transformace bodu v homogenních souřadnicích; vrací (x, y, z, w). */
    fun transform(x: Float, y: Float, z: Float, w: Float): FloatArray {
        val m = data
        return floatArrayOf(
            m[0] * x + m[4] * y + m[8] * z + m[12] * w,
            m[1] * x + m[5] * y + m[9] * z + m[13] * w,
            m[2] * x + m[6] * y + m[10] * z + m[14] * w,
            m[3] * x + m[7] * y + m[11] * z + m[15] * w,
        )
    }

    fun transformPoint(p: Vec3): FloatArray = transform(p.x, p.y, p.z, 1f)

    /**
     * Obecná inverze 4×4 (kofaktorová, varianta z MESA `gluInvertMatrix`).
     * Vrací `null` pro singulární matici – volající se pak jen nepokusí
     * kreslit kvadriky, které inverzi ViewProj potřebují pro ray-cast.
     */
    fun inverted(): Mat4? {
        val m = data
        val inv = FloatArray(16)

        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
                m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
                m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
                m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
                m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
                m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
                m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
                m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
                m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
                m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
                m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
                m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
                m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
                m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
                m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
                m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
                m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f || !det.isFinite()) return null

        val invDet = 1f / det
        for (i in 0 until 16) inv[i] *= invDet
        return Mat4(inv)
    }

    companion object {
        fun identity(): Mat4 = Mat4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            )
        )

        /** Ekvivalent `glOrtho(left, right, bottom, top, near, far)`. */
        fun ortho(
            left: Float, right: Float,
            bottom: Float, top: Float,
            near: Float, far: Float,
        ): Mat4 {
            val rl = right - left
            val tb = top - bottom
            val fn = far - near
            return Mat4(
                floatArrayOf(
                    2f / rl, 0f, 0f, 0f,
                    0f, 2f / tb, 0f, 0f,
                    0f, 0f, -2f / fn, 0f,
                    -(right + left) / rl, -(top + bottom) / tb, -(far + near) / fn, 1f,
                )
            )
        }

        fun translation(tx: Float, ty: Float, tz: Float): Mat4 = Mat4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                tx, ty, tz, 1f,
            )
        )

        fun scale(sx: Float, sy: Float, sz: Float): Mat4 = Mat4(
            floatArrayOf(
                sx, 0f, 0f, 0f,
                0f, sy, 0f, 0f,
                0f, 0f, sz, 0f,
                0f, 0f, 0f, 1f,
            )
        )

        /**
         * Rotační matice zadaná **řádky** – tedy třemi směry obrazovkové báze
         * (vpravo, nahoru, do hloubky). Přesně to, co desktop skládá ručně do
         * `glMultMatrixf` v `RenderScene.kt`.
         */
        fun fromBasisRows(right: Vec3, up: Vec3, forward: Vec3): Mat4 = Mat4(
            floatArrayOf(
                right.x, up.x, forward.x, 0f,
                right.y, up.y, forward.y, 0f,
                right.z, up.z, forward.z, 0f,
                0f, 0f, 0f, 1f,
            )
        )
    }
}
