package com.atmaca.files;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PathUtilTest {
    @Test public void normalizesPaths() {
        assertEquals("/Videos/a.mp4", PathUtil.normalize("\\Videos\\a.mp4/"));
        assertEquals("/", PathUtil.normalize("/"));
    }
    @Test public void parentAndChildAreStable() {
        assertEquals("/Videos", PathUtil.parent("/Videos/a.mp4"));
        assertEquals("/Videos/a.mp4", PathUtil.child("/Videos", "a.mp4"));
    }
}
