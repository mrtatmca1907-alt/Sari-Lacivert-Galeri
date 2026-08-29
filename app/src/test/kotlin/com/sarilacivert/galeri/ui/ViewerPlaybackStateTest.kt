package com.sarilacivert.galeri.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerPlaybackStateTest {
    @Test
    fun prepareTransitionsIdleToLoading() {
        assertEquals(ViewerPlaybackState.Loading, reducePlayback(ViewerPlaybackState.Idle, PlaybackEvent.Prepare))
    }

    @Test
    fun readyTransitionsLoadingToReady() {
        assertEquals(ViewerPlaybackState.Ready, reducePlayback(ViewerPlaybackState.Loading, PlaybackEvent.Ready))
    }

    @Test
    fun errorStoresReadableMessage() {
        assertEquals(
            ViewerPlaybackState.Error("Video oynatılamadı"),
            reducePlayback(ViewerPlaybackState.Loading, PlaybackEvent.Failed("Video oynatılamadı"))
        )
    }

    @Test
    fun disposeAlwaysReturnsIdle() {
        assertEquals(ViewerPlaybackState.Idle, reducePlayback(ViewerPlaybackState.Ready, PlaybackEvent.Dispose))
    }
}
