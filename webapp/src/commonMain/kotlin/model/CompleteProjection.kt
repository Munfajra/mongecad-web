package model

sealed class CompletionRequest {
    data class Line3DFromProjections(
        val existing: Any,
        val expect: DrawingModeMonge
    ) : CompletionRequest()

    data class Point3DFromProjections(
        val existing: Any,
        val expect: DrawingModeMonge
    ) : CompletionRequest()

}
