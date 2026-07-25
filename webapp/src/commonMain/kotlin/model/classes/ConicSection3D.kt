package model.classes
import utils.format
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.Offset3D
import model.UNASSIGNED_INDEX
import monge.input.axo.AxoRenderBasis
import utils.UUID
import kotlin.math.abs
data class ConicSection3D(
    val p0: Offset3D,               // bod v rovině
    val u: Offset3D,                // jednotkový vektor v rovině
    val v: Offset3D,                // ortogonální jednotkový vektor v rovině
    val matrix: Matrix3x3,          // koeficienty A-F v rovině, uložené jako symetrická matice
    val rawName: String = "ρ",
    var color: Color = Color.Black,
    var strokeWidth: Float = 1.5f,
    val lineStyle: LineStyle = LineStyle.Solid,
    val id: String = UUID.randomUUID().toString(),
    val a: Float? = null,
    val b: Float? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true
    ) {
    val name: String get() = rawName
    var directrixOfSurfaceIds by mutableStateOf<Set<String>>(emptySet())
    val isDirectrixOfAnyCone get() = directrixOfSurfaceIds.isNotEmpty()
}
data class Matrix3x3(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float,
    val m20: Float, val m21: Float, val m22: Float
) {
    companion object {
        fun fromCoefficients(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): Matrix3x3 {
            return Matrix3x3(
                a, b / 2f, d / 2f,
                b / 2f, c, e / 2f,
                d / 2f, e / 2f, f
            )
        }
        fun toCoefficients(m: Matrix3x3): List<Float> {
            return listOf(
                m.m00,             // A
                2 * m.m01,         // B
                m.m11,             // C
                2 * m.m02,         // D
                2 * m.m12,         // E
                m.m22              // F
            )
        }
    }
}
fun Matrix3x3.mul(v: Offset): Offset {
    return Offset(
        this.m00 * v.x + this.m01 * v.y + this.m02,
        this.m10 * v.x + this.m11 * v.y + this.m12
    )
}
fun Matrix3x3.prettyFormat(): String {
    return "[[%.4f, %.4f, %.4f],\n [%.4f, %.4f, %.4f],\n [%.4f, %.4f, %.4f]]".format(
        m00, m01, m02,
        m10, m11, m12,
        m20, m21, m22
    )
}
fun ConicSection3D.projectToXY(): Matrix3x3 {
    val a = u.x; val b = v.x
    val c = u.y; val d = v.y
    val det = det2(a, b, c, d)
    if (abs(det) < EPS_DET) {
        return degenerateLineConic2D(
            point = Offset(p0.x, p0.y),
            direction = Offset(a, c)
        )
    }
    val H = Matrix3x3(
        a, b, p0.x,
        c, d, p0.y,
        0f, 0f, 1f
    )
    return transformConic(matrix, H) ?: Matrix3x3.zero()
}
fun ConicSection3D.projectToYZ(): Matrix3x3 {
    val a = u.y
    val b = v.y
    val c = u.z
    val d = v.z
    val det = a * d - b * c
    if (abs(det) < EPS_DET) {
        return degenerateLineConic2D(
            point = Offset(p0.y, p0.z),
            direction = Offset(a, c)
        )
    }
    val h = Matrix3x3(
        a, b, p0.y,
        c, d, p0.z,
        0f, 0f, 1f
    )
    return transformConic(matrix, h) ?: Matrix3x3.zero()
}
private const val EPS_DET = 1e-7f
private fun det2(a: Float, b: Float, c: Float, d: Float) = a*d - b*c
fun ConicSection3D.projectToXZ(): Matrix3x3 {
    val a = u.x; val b = v.x
    val c = u.z; val d = v.z
    val det = det2(a, b, c, d)
    // ✅ degenerace: projekce roviny do XZ kolabuje na přímku
    if (abs(det) < EPS_DET) {
        // Nejčastější případ u rovnoběžek: u.z ≈ v.z ≈ 0 → z = konst.
        return degenerateLineConic2D(
            point = Offset(p0.x, p0.z),
            direction = Offset(a, c)
        )
    }
    val H = Matrix3x3(
        a, b, p0.x,
        c, d, p0.z,
        0f, 0f, 1f
    )
    return transformConic(matrix, H) ?: Matrix3x3.zero()
}
fun ConicSection3D.projectToAxo(basis: AxoRenderBasis): Matrix3x3 {
    val p = projectPoint3DToAxoLocal(p0, basis)
    val u2 = projectVector3DToAxoLocal(u, basis)
    val v2 = projectVector3DToAxoLocal(v, basis)
    val det = det2(u2.x, v2.x, u2.y, v2.y)
    if (abs(det) < EPS_DET) {
        return degenerateLineConic2D(
            point = p,
            direction = u2
        )
    }
    val H = Matrix3x3(
        u2.x, v2.x, p.x,
        u2.y, v2.y, p.y,
        0f,  0f,  1f
    )
    return transformConic(matrix, H) ?: Matrix3x3.zero()
}
fun projectPoint3DToAxoLocal(p: Offset3D, basis: AxoRenderBasis): Offset {
    return basis.ex * p.x + basis.ey * p.y + basis.ez * p.z
}
fun projectVector3DToAxoLocal(v: Offset3D, basis: AxoRenderBasis): Offset {
    return basis.ex * v.x + basis.ey * v.y + basis.ez * v.z
}
fun Matrix3x3.Companion.zero() = Matrix3x3(
    0f, 0f, 0f,
    0f, 0f, 0f,
    0f, 0f, 0f
)
fun transformConic(C: Matrix3x3, H: Matrix3x3): Matrix3x3? {
    val Hinv = H.invert()
    if (Hinv == null) {
        println("❌ Inverze matice H selhala – nelze provést transformaci kuželosečky.")
        return null
    }
    val HinvT = Hinv.transpose()
    return HinvT * C * Hinv
}
operator fun Matrix3x3.times(other: Matrix3x3): Matrix3x3 {
    fun get(r: Int, c: Int): Float {
        val row = when (r) {
            0 -> listOf(m00, m01, m02)
            1 -> listOf(m10, m11, m12)
            2 -> listOf(m20, m21, m22)
            else -> error("Invalid row")
        }
        val col = when (c) {
            0 -> listOf(other.m00, other.m10, other.m20)
            1 -> listOf(other.m01, other.m11, other.m21)
            2 -> listOf(other.m02, other.m12, other.m22)
            else -> error("Invalid column")
        }
        return row.zip(col).sumOf { (a, b) -> (a * b).toDouble() }.toFloat()
    }
    return Matrix3x3(
        get(0, 0), get(0, 1), get(0, 2),
        get(1, 0), get(1, 1), get(1, 2),
        get(2, 0), get(2, 1), get(2, 2),
    )
}
fun Matrix3x3.transpose(): Matrix3x3 = Matrix3x3(
    m00, m10, m20,
    m01, m11, m21,
    m02, m12, m22
)
// Inverze 3x3 přes adjungovanou matici. Desktop tu volá EJML, na wasm ho nemáme
// a pro fixní rozměr 3x3 je uzavřený vzorec stejně přesnější i rychlejší.
fun Matrix3x3.invert(): Matrix3x3? {
    val a = m00.toDouble(); val b = m01.toDouble(); val c = m02.toDouble()
    val d = m10.toDouble(); val e = m11.toDouble(); val f = m12.toDouble()
    val g = m20.toDouble(); val h = m21.toDouble(); val i = m22.toDouble()
    val cof00 = e * i - f * h
    val cof01 = -(d * i - f * g)
    val cof02 = d * h - e * g
    val det = a * cof00 + b * cof01 + c * cof02
    if (det == 0.0 || !det.isFinite()) {
        println("❌ Inverze selhala: singulární matice.")
        return null
    }
    val invDet = 1.0 / det
    // adj = transpozice kofaktorové matice
    val r = doubleArrayOf(
        cof00 * invDet, (c * h - b * i) * invDet, (b * f - c * e) * invDet,
        cof01 * invDet, (a * i - c * g) * invDet, (c * d - a * f) * invDet,
        cof02 * invDet, (b * g - a * h) * invDet, (a * e - b * d) * invDet
    )
    if (r.any { !it.isFinite() }) {
        println("❌ Inverze selhala: obsahuje nečíselné hodnoty.")
        return null
    }
    return Matrix3x3(
        r[0].toFloat(), r[1].toFloat(), r[2].toFloat(),
        r[3].toFloat(), r[4].toFloat(), r[5].toFloat(),
        r[6].toFloat(), r[7].toFloat(), r[8].toFloat()
    )
}
infix fun FloatArray.dot(other: FloatArray): Float =
    this.zip(other).sumOf { (a, b) -> (a * b).toDouble() }.toFloat()
fun degenerateLineConic2D(point: Offset, direction: Offset): Matrix3x3 {
    val len = direction.getDistance()
    val normal = if (len < EPS_DET) Offset(1f, 0f) else Offset(-direction.y / len, direction.x / len)
    val q = -(normal.x * point.x + normal.y * point.y)
    return Matrix3x3.fromCoefficients(
        a = normal.x * normal.x,
        b = 2f * normal.x * normal.y,
        c = normal.y * normal.y,
        d = 2f * normal.x * q,
        e = 2f * normal.y * q,
        f = q * q
    )
}
data class EllipseParam(
    val center3D: Offset3D,  // Pc = p0 + u*α0 + v*β0
    val uRot: Offset3D,      // 3D hlavní osa 1
    val vRot: Offset3D,      // 3D hlavní osa 2 (kolmá v rovině)
    val a: Float,            // velká/menší poloosa
    val b: Float
)
