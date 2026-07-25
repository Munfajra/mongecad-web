package state

import model.classes.Plane3D
import serialization.setAll

/*
 * Přepojení stop roviny na aktualizovaný Plane3D.
 * Dřív v `ui/mongeui/toolbar/rightDescriptionBar/Planes.kt`, i když je to
 * operace nad stavem – volá ji i dialog přejmenování a OpenGL panel.
 */
fun relinkPlaneToTraces(state: MongeState, updated: Plane3D, clearLocal: Boolean = true) {
    state.lineTracesPudorys.setAll(
        state.lineTracesPudorys.map { t ->
            if (t.parent?.id == updated.id || t.parentId == updated.id) {
                t.copy(
                    parent = updated,
                    parentId = updated.id,
                    localColor = if (clearLocal) null else t.localColor,
                    localLineStyle = if (clearLocal) null else t.localLineStyle,
                    localStrokeWidth = if (clearLocal) null else t.localStrokeWidth,
                    localName = if (clearLocal) null else t.localName
                )
            } else t
        }
    )
    state.lineTracesNarys.setAll(
        state.lineTracesNarys.map { t ->
            if (t.parent?.id == updated.id || t.parentId == updated.id) {
                t.copy(
                    parent = updated,
                    parentId = updated.id,
                    localColor = if (clearLocal) null else t.localColor,
                    localLineStyle = if (clearLocal) null else t.localLineStyle,
                    localStrokeWidth = if (clearLocal) null else t.localStrokeWidth,
                    localName = if (clearLocal) null else t.localName
                )
            } else t
        }
    )
    state.lineTracesBokorys.setAll(
        state.lineTracesBokorys.map { t ->
            if (t.parent?.id == updated.id || t.parentId == updated.id) {
                t.copy(
                    parent = updated,
                    parentId = updated.id,
                    localColor = if (clearLocal) null else t.localColor,
                    localLineStyle = if (clearLocal) null else t.localLineStyle,
                    localStrokeWidth = if (clearLocal) null else t.localStrokeWidth,
                    localName = if (clearLocal) null else t.localName
                )
            } else t
        }
    )
}

