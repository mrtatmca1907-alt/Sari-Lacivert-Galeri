package com.atmaca.videokareleri;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameRulesTest {
    @Test public void tenSecondsProducesTenFrames() {
        assertEquals(10, FrameRules.frameCount(10_000));
    }

    @Test public void partialFinalSecondStillProducesOneFrame() {
        assertEquals(11, FrameRules.frameCount(10_001));
    }

    @Test public void zeroDurationProducesZeroFrames() {
        assertEquals(0, FrameRules.frameCount(0));
    }

    @Test public void stripsOnlyLastExtension() {
        assertEquals("tatil.video", FrameRules.baseName("tatil.video.mp4"));
    }

    @Test public void frameNamesAreStableAndSortable() {
        assertEquals("tatil_000001.jpg", FrameRules.frameName("tatil", 1));
        assertEquals("tatil_001220.jpg", FrameRules.frameName("tatil", 1220));
    }
}
