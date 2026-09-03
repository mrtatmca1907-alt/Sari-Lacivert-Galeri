from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import kotlin.math.abs\n",
    "import kotlin.math.abs\nimport kotlin.math.hypot\n",
    "hypot import",
)

replace_once(
    '''    var viewportWidth by remember(item.id) { mutableIntStateOf(0) }
    var viewportHeight by remember(item.id) { mutableIntStateOf(0) }
''',
    '''    var viewportWidth by remember(item.id) { mutableIntStateOf(0) }
    var viewportHeight by remember(item.id) { mutableIntStateOf(0) }
    val lastTapTime = remember(item.id) { longArrayOf(0L) }
    val lastTapPosition = remember(item.id) { floatArrayOf(0f, 0f) }
''',
    "tap state",
)

replace_once(
    '''    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = loadHighResolutionBitmap(context, item)
    }
''',
    '''    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        item.uri,
        viewportWidth,
        viewportHeight
    ) {
        if (viewportWidth > 0 && viewportHeight > 0) {
            value = loadHighResolutionBitmap(
                context = context,
                item = item,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight
            )
        }
    }
''',
    "viewport decode",
)

replace_once(
    '''                        awaitFirstDown(requireUnconsumed = false)
                        var transformed = false
                        var moved = false
                        do {
''',
    '''                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val downX = firstDown.position.x
                        val downY = firstDown.position.y
                        var transformed = false
                        var moved = false
                        var lastEventTime = firstDown.uptimeMillis
                        do {
''',
    "gesture down state",
)

replace_once(
    '''                            val rotationDelta = if (pressed >= 2) event.calculateRotation() else 0f
                            val owns = shouldPhotoConsumeGesture(pressed, scale, localRotation)

                            if (owns) {
''',
    '''                            val rotationDelta = if (pressed >= 2) event.calculateRotation() else 0f
                            val owns = shouldPhotoConsumeGesture(pressed, scale, localRotation)
                            event.changes.firstOrNull()?.let { change ->
                                lastEventTime = change.uptimeMillis
                                if (hypot(change.position.x - downX, change.position.y - downY) > 12f) moved = true
                            }
                            val transformFrame = owns && (
                                pressed >= 2 || abs(pan.x) > 0.01f || abs(pan.y) > 0.01f
                            )

                            if (transformFrame) {
''',
    "transform frame ownership",
)

replace_once(
    '''                        } else if (!moved) {
                            if (scale > 1.001f || abs(localRotation) > 0.5f) resetTransform() else onFitTap()
                        }
''',
    '''                        } else if (!moved) {
                            val distanceFromLast = hypot(
                                downX - lastTapPosition[0],
                                downY - lastTapPosition[1]
                            )
                            val doubleTap = isViewerDoubleTap(
                                previousUpMs = lastTapTime[0],
                                currentUpMs = lastEventTime,
                                distancePx = distanceFromLast
                            )
                            if (doubleTap) {
                                lastTapTime[0] = 0L
                                if (scale > 1.1f || abs(localRotation) > 0.5f) {
                                    resetTransform()
                                } else {
                                    val oldScale = scale
                                    val nextScale = nextDoubleTapScale(scale)
                                    offsetX = zoomOffsetAroundFocus(
                                        offsetX,
                                        downX - viewportWidth / 2f,
                                        oldScale,
                                        nextScale
                                    )
                                    offsetY = zoomOffsetAroundFocus(
                                        offsetY,
                                        downY - viewportHeight / 2f,
                                        oldScale,
                                        nextScale
                                    )
                                    scale = nextScale
                                    clampOffsets(source)
                                    onScaleChanged(scale)
                                    onRotationChanged(localRotation)
                                }
                            } else {
                                lastTapTime[0] = lastEventTime
                                lastTapPosition[0] = downX
                                lastTapPosition[1] = downY
                                if (scale <= 1.001f && abs(localRotation) <= 0.5f) onFitTap()
                            }
                        }
''',
    "double tap behavior",
)

path.write_text(text, encoding="utf-8")
print("Stable photo performance patch applied")
