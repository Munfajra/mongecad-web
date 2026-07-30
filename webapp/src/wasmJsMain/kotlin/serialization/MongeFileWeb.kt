package serialization

import kotlinx.browser.document
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import state.MongeState
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

/**
 * Otevření `.monge` přes souborový dialog prohlížeče.
 *
 * Desktopové soubory jsou gzipované, takže se podle magic bytes (1f 8b)
 * rozbalí přes `DecompressionStream`. Nekomprimovaný JSON se přečte přímo.
 */
actual suspend fun openMongeFile(): MongeState? {
    val file = pickFile(".monge,.json") ?: return null
    val bytes = readFileBytes(file)
    val text = if (isGzip(bytes)) gunzip(file) else bytes.decodeToString()
    return runCatching { loadMongeStateFromText(text, file.name) }.getOrNull()
}

/**
 * Uložení stažením souboru.
 *
 * Ukládá se gzipovaně, stejně jako na desktopu – historie snapshotů dělá
 * z nekomprimovaného JSONu obrovský soubor. Kompresi zajišťuje prohlížeč
 * (`CompressionStream`); pokud by ji neuměl, spadne se na čistý JSON,
 * který desktopový loader taky přečte (pozná gzip podle magic bytes).
 */
actual fun saveMongeFile(state: MongeState) {
    val text = serializeMongeState(state)
    val name = state.displayName.ifBlank { "Vykres" } + ".monge"

    val blob = runCatching { gzipToBlob(text.toJsString()) }.getOrNull()
    if (blob != null) {
        downloadBlobAsync(blob, name)
    } else {
        val parts = JsArray<JsAny?>()
        parts[0] = text.toJsString()
        // Taky octet-stream, ať Android nepřipíše `.json` – viz [gzipToBlob].
        downloadBlob(Blob(parts, BlobPropertyBag(type = "application/octet-stream")), name)
    }

    state.isDirty = false
}

private fun downloadBlob(blob: Blob, name: String) {
    val url = URL.createObjectURL(blob)
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = url
    a.download = name
    a.style.display = "none"
    document.body?.appendChild(a)
    a.click()
    document.body?.removeChild(a)
    URL.revokeObjectURL(url)
}

private suspend fun pickFile(accept: String): File? = suspendCoroutine { cont ->
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = accept
    input.style.display = "none"
    var resumed = false
    input.onchange = {
        if (!resumed) {
            resumed = true
            val f = input.files?.item(0)
            document.body?.removeChild(input)
            cont.resume(f)
        }
    }
    // Zrušení dialogu prohlížeč hlásí jen na některých platformách – bez toho
    // by korutina zůstala viset navždy.
    input.oncancel = {
        if (!resumed) {
            resumed = true
            document.body?.removeChild(input)
            cont.resume(null)
        }
    }
    document.body?.appendChild(input)
    input.click()
}

private suspend fun readFileBytes(file: File): ByteArray {
    val buffer = fileArrayBuffer(file).await<JsAny>()
    val view = Uint8Array(buffer as org.khronos.webgl.ArrayBuffer)
    return ByteArray(view.length) { view[it].toByte() }
}

private fun isGzip(bytes: ByteArray): Boolean =
    bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

private suspend fun gunzip(file: File): String =
    gunzipToText(file).await<JsString>().toString()

@JsFun("(file) => file.arrayBuffer()")
private external fun fileArrayBuffer(file: File): Promise<JsAny>

/** Rozbalení gzipu prohlížečem – DecompressionStream + Response.text(). */
@JsFun(
    "(file) => new Response(file.stream().pipeThrough(new DecompressionStream('gzip'))).text()"
)
private external fun gunzipToText(file: File): Promise<JsString>

/**
 * Zabalení textu do gzip Blobu prohlížečem.
 *
 * Blob z `Response.blob()` nemá MIME typ (Response nemá Content-Type), a to
 * Androidu nestačí: jeho správce stahování si typ domyslí jako textový a
 * k názvu připíše `.txt` (`vykres.monge.txt`). `application/octet-stream`
 * znamená „neznámá binárka", u které se přípona nedoplňuje.
 */
@JsFun(
    """(text) => new Response(
        new Blob([text]).stream().pipeThrough(new CompressionStream('gzip'))
    ).blob().then((blob) => new Blob([blob], { type: 'application/octet-stream' }))"""
)
private external fun gzipToBlob(text: JsString): Promise<JsAny>

/**
 * Stažení Blobu, který teprve dorazí (gzip komprese je asynchronní).
 * Řeší se v JS, aby nebylo nutné tahat celý download přes suspend funkci.
 */
@JsFun(
    """(blobPromise, name) => blobPromise.then((blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    })"""
)
private external fun downloadBlobAsync(blobPromise: Promise<JsAny>, name: String)
