package app.pinimage.float

import kotlinx.serialization.Serializable

/**
 * Core floating item state. Frame and content transforms are kept completely
 * separate by design - see docs/requirements.md sections 3 and 28.
 */
@Serializable
data class FloatingItem(
    val id: String,
    val imageUri: String,
    val frame: FrameTransform,
    val mediaKind: MediaKind = MediaKind.Image,
    val content: ContentTransform = ContentTransform(),
    val display: DisplayProps = DisplayProps(),
    val state: LockState = LockState.Unlocked,
)

@Serializable
enum class MediaKind {
    Image,
    Pdf,
    Epub,
}

@Serializable
data class FrameTransform(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

@Serializable
data class ContentTransform(
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
)

@Serializable
data class DisplayProps(
    val opacity: Float = 1f,
    val zIndex: Int = 0,
    val borderStyle: PinBorderStyle = PinBorderStyle.Hairline,
)

@Serializable
enum class PinBorderStyle {
    None,
    Hairline,
    Outline,
    SoftShadow,
}

@Serializable
enum class LockState {
    Unlocked,
    FrameLocked,
    FullyLocked,
}
