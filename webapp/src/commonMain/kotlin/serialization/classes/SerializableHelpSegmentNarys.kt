package serialization.classes

import kotlinx.serialization.Serializable
import model.classes.HelpSegmentNarys
import model.classes.Segment3D
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable

@Serializable
data class SerializableHelpSegmentNarys(
    val start: SerializablePoint3DNarys,
    val end: SerializablePoint3DNarys,
    val parentId: String? = null,
    val name: String? = null,
    val color: SerializableColor? = null,
    val lineStyle: SerializableLineStyle? = null,
    val strokeWidth: Float? = null,
    val id: String,
    val creationIndex: Long

)

fun HelpSegmentNarys.toSerializable(): SerializableHelpSegmentNarys {
    return SerializableHelpSegmentNarys(
        start = this.start.toSerializable(),
        end = this.end.toSerializable(),
        parentId = this.parent?.id,
        name = this.name,
        color = this.localColor?.let { SerializableColor.from(it) },
        lineStyle = this.localLineStyle?.toSerializable(),
        strokeWidth = this.localStrokeWidth,
        id = this.id,
        creationIndex = this.creationIndex)
}

fun SerializableHelpSegmentNarys.toRuntime(
    parentsById: Map<String, Segment3D>
): HelpSegmentNarys {
    return HelpSegmentNarys(
        start = this.start.toRuntime(emptyMap()),
        end = this.end.toRuntime(emptyMap()),
        name = this.name,
        parent = this.parentId?.let { parentsById[it] },
        localColor = this.color?.toColor(),
        localLineStyle = this.lineStyle?.toLineStyle(),
        localStrokeWidth = this.strokeWidth,
        id = this.id,
        creationIndex = this.creationIndex
    )
}

