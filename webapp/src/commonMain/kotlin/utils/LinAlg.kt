package utils

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Hustá lineární algebra pro wasm.
 *
 * Desktop tady volá EJML (`SimpleMatrix.solve/eig/svd`), který je jen pro JVM.
 * Web potřebuje jen tři konkrétní operace z konstrukce přímkových ploch, takže
 * místo portu celé knihovny stačí tyhle tři rutiny. Všechny počítají v Double,
 * protože jde o proložení kvadriky – ve Floatu se rozdíly vlastních čísel ztratí.
 */

/** Řešení soustavy 3×3 přes Cramerovo pravidlo. Vrací null pro singulární matici. */
fun solve3x3(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
    fun det3(
        m00: Double, m01: Double, m02: Double,
        m10: Double, m11: Double, m12: Double,
        m20: Double, m21: Double, m22: Double,
    ): Double = m00 * (m11 * m22 - m12 * m21) -
        m01 * (m10 * m22 - m12 * m20) +
        m02 * (m10 * m21 - m11 * m20)

    val det = det3(
        a[0][0], a[0][1], a[0][2],
        a[1][0], a[1][1], a[1][2],
        a[2][0], a[2][1], a[2][2],
    )
    if (!det.isFinite() || abs(det) < 1e-18) return null

    val x = det3(
        b[0], a[0][1], a[0][2],
        b[1], a[1][1], a[1][2],
        b[2], a[2][1], a[2][2],
    ) / det
    val y = det3(
        a[0][0], b[0], a[0][2],
        a[1][0], b[1], a[1][2],
        a[2][0], b[2], a[2][2],
    ) / det
    val z = det3(
        a[0][0], a[0][1], b[0],
        a[1][0], a[1][1], b[1],
        a[2][0], a[2][1], b[2],
    ) / det

    val result = doubleArrayOf(x, y, z)
    return if (result.all { it.isFinite() }) result else null
}

/**
 * Vlastní čísla a vektory symetrické matice n×n cyklickou Jacobiho metodou.
 *
 * Náhrada za `SimpleMatrix.eig()`. Matice ze zadání jsou symetrické, takže
 * vlastní čísla vyjdou reálná a Jacobi je pro malé rozměry přesný i rychlý.
 * Vrací dvojice (vlastní číslo, vlastní vektor); vektory jsou ortonormální.
 */
fun symmetricEigen(matrix: Array<DoubleArray>): List<Pair<Double, DoubleArray>>? {
    val n = matrix.size
    if (n == 0 || matrix.any { it.size != n }) return null
    if (matrix.any { row -> row.any { !it.isFinite() } }) return null

    val a = Array(n) { r -> DoubleArray(n) { c -> matrix[r][c] } }
    val v = Array(n) { r -> DoubleArray(n) { c -> if (r == c) 1.0 else 0.0 } }

    repeat(100) { sweep ->
        var offDiagonal = 0.0
        for (p in 0 until n) for (q in p + 1 until n) offDiagonal += a[p][q] * a[p][q]
        if (offDiagonal < 1e-30) return@repeat

        for (p in 0 until n) for (q in p + 1 until n) {
            val apq = a[p][q]
            if (abs(apq) < 1e-300) continue

            // rotace, která vynuluje prvek (p,q)
            val theta = (a[q][q] - a[p][p]) / (2.0 * apq)
            val t = if (theta >= 0.0) 1.0 / (theta + sqrt(1.0 + theta * theta))
            else -1.0 / (-theta + sqrt(1.0 + theta * theta))
            val c = 1.0 / sqrt(1.0 + t * t)
            val s = t * c

            for (k in 0 until n) {
                val akp = a[k][p]
                val akq = a[k][q]
                a[k][p] = c * akp - s * akq
                a[k][q] = s * akp + c * akq
            }
            for (k in 0 until n) {
                val apk = a[p][k]
                val aqk = a[q][k]
                a[p][k] = c * apk - s * aqk
                a[q][k] = s * apk + c * aqk
            }
            for (k in 0 until n) {
                val vkp = v[k][p]
                val vkq = v[k][q]
                v[k][p] = c * vkp - s * vkq
                v[k][q] = s * vkp + c * vkq
            }
        }
    }

    val out = (0 until n).map { i -> a[i][i] to DoubleArray(n) { k -> v[k][i] } }
    return if (out.all { (value, vec) -> value.isFinite() && vec.all { it.isFinite() } }) out else null
}

/**
 * Jednostranná Jacobiho SVD – vrací pravý singulární vektor odpovídající
 * nejmenšímu singulárnímu číslu, tedy vektor jádra matice [rows].
 *
 * Náhrada za `SimpleMatrix.svd().nullSpace()`. Používá se na soustavu 9×10
 * (devět podmínek, deset koeficientů implicitní kvadriky), kde je jádro
 * genericky jednorozměrné. Jednostranný Jacobi pracuje přímo se sloupci
 * matice, takže se – na rozdíl od cesty přes AᵀA – nezhoršuje podmíněnost.
 *
 * Výsledek je normalizovaný na jednotkovou délku, nebo null, pokud výpočet
 * nedal konečná čísla.
 */
fun nullSpaceVector(rows: Array<DoubleArray>): DoubleArray? {
    val m = rows.size
    if (m == 0) return null
    val n = rows[0].size
    if (n == 0 || rows.any { it.size != n }) return null
    if (rows.any { row -> row.any { !it.isFinite() } }) return null

    // sloupce matice, se kterými Jacobi rotuje
    val a = Array(n) { col -> DoubleArray(m) { row -> rows[row][col] } }
    val v = Array(n) { r -> DoubleArray(n) { c -> if (r == c) 1.0 else 0.0 } }

    repeat(60) {
        var rotated = false
        for (p in 0 until n) for (q in p + 1 until n) {
            var alpha = 0.0
            var beta = 0.0
            var gamma = 0.0
            for (k in 0 until m) {
                alpha += a[p][k] * a[p][k]
                beta += a[q][k] * a[q][k]
                gamma += a[p][k] * a[q][k]
            }
            if (abs(gamma) <= 1e-15 * sqrt(alpha * beta) || gamma == 0.0) continue

            val zeta = (beta - alpha) / (2.0 * gamma)
            val t = if (zeta >= 0.0) 1.0 / (zeta + sqrt(1.0 + zeta * zeta))
            else -1.0 / (-zeta + sqrt(1.0 + zeta * zeta))
            val c = 1.0 / sqrt(1.0 + t * t)
            val s = c * t

            for (k in 0 until m) {
                val akp = a[p][k]
                val akq = a[q][k]
                a[p][k] = c * akp - s * akq
                a[q][k] = s * akp + c * akq
            }
            for (k in 0 until n) {
                val vkp = v[k][p]
                val vkq = v[k][q]
                v[k][p] = c * vkp - s * vkq
                v[k][q] = s * vkp + c * vkq
            }
            rotated = true
        }
        if (!rotated) return@repeat
    }

    // singulární čísla = normy sloupců; jádro = sloupec V s nejmenší normou
    var bestIndex = 0
    var bestNorm = Double.MAX_VALUE
    for (col in 0 until n) {
        var norm = 0.0
        for (k in 0 until m) norm += a[col][k] * a[col][k]
        if (norm < bestNorm) {
            bestNorm = norm
            bestIndex = col
        }
    }

    val result = DoubleArray(n) { k -> v[k][bestIndex] }
    val length = sqrt(result.sumOf { it * it })
    if (!length.isFinite() || length < 1e-12) return null
    return DoubleArray(n) { result[it] / length }
}
