package com.atmaca.video50;

import static org.junit.Assert.*;
import java.util.HashSet;
import org.junit.Test;

public class MoveJournalTest {
    @Test public void completedKeysAreRemembered() {
        MoveJournal.State state = new MoveJournal.State(new HashSet<>());
        assertFalse(state.isDone("content://video/1"));
        state.markDone("content://video/1");
        assertTrue(state.isDone("content://video/1"));
        assertEquals(1, state.size());
    }
}
