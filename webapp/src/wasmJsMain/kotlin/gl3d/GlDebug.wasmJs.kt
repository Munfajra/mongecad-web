package gl3d.api

@JsFun("(message) => { console.info('[gl3d] ' + message); }")
private external fun gl3dLogJs(message: String)

actual fun gl3dLog(message: String) {
    gl3dLogJs(message)
}
