package com.atmaca.files;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CloudHddPathPolicyTest {
    @Test public void cloudRootMapsToMovies() {
        assertEquals("/MOVIES", CloudHddPathPolicy.remoteDir(""));
    }

    @Test public void cloudSubfoldersStayUnderMovies() {
        assertEquals("/MOVIES/Fener/2026", CloudHddPathPolicy.remoteDir("Fener/2026/"));
    }

    @Test public void windowsSeparatorsAreNormalized() {
        assertEquals("/MOVIES/A/B", CloudHddPathPolicy.remoteDir("A\\B"));
    }
}
