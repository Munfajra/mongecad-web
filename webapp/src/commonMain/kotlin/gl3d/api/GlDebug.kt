package gl3d.api

/**
 * Diagnostika 3D rendereru.
 *
 * Chyby překladu shaderů a stav inicializace se jinak nikde neprojeví –
 * scéna prostě zůstane prázdná a není poznat, jestli selhal kontext, program,
 * nebo je jen prázdný výkres.
 */
expect fun gl3dLog(message: String)
