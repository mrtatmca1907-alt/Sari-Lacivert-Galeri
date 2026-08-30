package com.atmaca.files;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MoviesSsdPolicyTest {
    @Test public void mapsMoviesRootToSsdMoviesFolder() {
        assertEquals("/MOVIES", MoviesSsdPolicy.remoteDir("Movies/"));
    }

    @Test public void preservesMoviesSubfolders() {
        assertEquals("/MOVIES/Video 1", MoviesSsdPolicy.remoteDir("Movies/Video 1/"));
    }
}
