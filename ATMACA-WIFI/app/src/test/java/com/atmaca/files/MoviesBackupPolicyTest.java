package com.atmaca.files;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MoviesBackupPolicyTest {
    @Test public void acceptsOnlyMoviesPaths() {
        assertTrue(MoviesBackupPolicy.isMoviesPath("Movies/"));
        assertTrue(MoviesBackupPolicy.isMoviesPath("Movies/Video 1/"));
        assertFalse(MoviesBackupPolicy.isMoviesPath("Download/"));
        assertFalse(MoviesBackupPolicy.isMoviesPath("DCIM/Camera/"));
    }

    @Test public void preservesSubfolderBelowMovies() {
        assertEquals("", MoviesBackupPolicy.subdirectory("Movies/"));
        assertEquals("Video 1", MoviesBackupPolicy.subdirectory("Movies/Video 1/"));
        assertEquals("Video 1/Alt", MoviesBackupPolicy.subdirectory("Movies/Video 1/Alt/"));
    }
}
