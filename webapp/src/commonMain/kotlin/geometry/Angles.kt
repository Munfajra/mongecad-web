package geometry

/*
 * Drobná úhlová matematika. `positiveMod` byla v `objects/axo/drawLines.kt`,
 * i když ji volá i ortogonální kreslení, náhledy a kuželosečky.
 */
fun positiveMod(value: Float, mod: Float): Float {
    if (mod == 0f) return 0f
    return ((value % mod) + mod) % mod
}
