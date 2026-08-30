package com.atmaca.video50;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class BatchPolicyTest {
    @Test public void groupsEveryFifty() {
        assertEquals("Video 1", BatchPolicy.folderNameForIndex(0));
        assertEquals("Video 1", BatchPolicy.folderNameForIndex(49));
        assertEquals("Video 2", BatchPolicy.folderNameForIndex(50));
        assertEquals("Video 7", BatchPolicy.folderNameForIndex(326));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeIndex() {
        BatchPolicy.folderNameForIndex(-1);
    }
}
