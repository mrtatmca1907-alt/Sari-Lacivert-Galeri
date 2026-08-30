package com.atmaca.video50;

import static org.junit.Assert.assertArrayEquals;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DedupePolicyTest {
    @Test public void keepsFirstOccurrenceOfEachKey() {
        List<String> out = DedupePolicy.unique(Arrays.asList("a", "b", "a", "c", "b"));
        assertArrayEquals(new String[]{"a", "b", "c"}, out.toArray(new String[0]));
    }

    @Test public void generatedMoviesFoldersAreExcluded() {
        org.junit.Assert.assertTrue(DedupePolicy.isGeneratedMoviesPath("Movies/Video 1"));
        org.junit.Assert.assertTrue(DedupePolicy.isGeneratedMoviesPath("Movies/Video 27/"));
        org.junit.Assert.assertFalse(DedupePolicy.isGeneratedMoviesPath("Movies/Camera"));
    }
}
