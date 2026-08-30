package com.atmaca.video50;

import java.util.Set;

public final class MoveJournal {
    private MoveJournal() {}

    public static final class State {
        private final Set<String> done;
        public State(Set<String> done) { this.done = done; }
        public boolean isDone(String key) { return done.contains(key); }
        public void markDone(String key) { done.add(key); }
        public int size() { return done.size(); }
        public Set<String> snapshot() { return new java.util.HashSet<>(done); }
    }
}
