from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
source = path.read_text()
old = '''@Composable
private fun VideoPage(item: GalleryMedia, rotation: Float) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(rotationZ = rotation)
    )
}
'''
new = '''@Composable
private fun VideoPage(item: GalleryMedia, rotation: Float) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(activity, player) {
        val oldOrientation = activity?.requestedOrientation
        if (activity != null) {
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            player.release()
            if (activity != null && oldOrientation != null) {
                activity.requestedOrientation = oldOrientation
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(rotationZ = rotation)
    )
}
'''
if old not in source:
    if "SCREEN_ORIENTATION_SENSOR_LANDSCAPE" in source and "requestedOrientation = oldOrientation" in source:
        print("Video landscape already wired")
    else:
        raise SystemExit("VideoPage block not found")
else:
    path.write_text(source.replace(old, new, 1))
    print("Video landscape wired")
