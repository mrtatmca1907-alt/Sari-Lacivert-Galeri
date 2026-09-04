package com.atmaca.gallery

import android.content.Context
import android.net.Uri
import java.util.UUID

const val VIDEO_FRAME_WORK_TAG = MEDIA_TOOL_WORK_TAG

@Deprecated("Use enqueueMediaToolWork")
fun enqueueVideoFrameWork(context: Context, uris: List<Uri>, framesPerSecond: Int): UUID? =
    enqueueMediaToolWork(context, AtmacaToolPage.VIDEO_FRAMES, uris, framesPerSecond)
