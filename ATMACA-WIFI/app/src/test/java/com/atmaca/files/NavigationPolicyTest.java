package com.atmaca.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class NavigationPolicyTest {
    @Test public void backFromNestedFolderReturnsParent() {
        assertEquals("/VIDEOLAR", NavigationPolicy.backTarget("/VIDEOLAR/VIDEO_000064"));
    }

    @Test public void backFromTopLevelFolderReturnsRoot() {
        assertEquals("/", NavigationPolicy.backTarget("/VIDEOLAR"));
    }

    @Test public void backFromRootAllowsAppExit() {
        assertNull(NavigationPolicy.backTarget("/"));
    }
}
