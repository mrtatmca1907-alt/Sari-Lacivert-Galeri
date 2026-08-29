package com.sarilacivert.galeri.ui

sealed interface ViewerPlaybackState {
    data object Idle : ViewerPlaybackState
    data object Loading : ViewerPlaybackState
    data object Ready : ViewerPlaybackState
    data class Error(val message: String) : ViewerPlaybackState
}

sealed interface PlaybackEvent {
    data object Prepare : PlaybackEvent
    data object Ready : PlaybackEvent
    data class Failed(val message: String) : PlaybackEvent
    data object Dispose : PlaybackEvent
}

fun reducePlayback(state: ViewerPlaybackState, event: PlaybackEvent): ViewerPlaybackState = when (event) {
    PlaybackEvent.Prepare -> ViewerPlaybackState.Loading
    PlaybackEvent.Ready -> ViewerPlaybackState.Ready
    is PlaybackEvent.Failed -> ViewerPlaybackState.Error(event.message.ifBlank { "Video oynatılamadı" })
    PlaybackEvent.Dispose -> ViewerPlaybackState.Idle
}
