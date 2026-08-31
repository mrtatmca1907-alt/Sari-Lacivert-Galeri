package com.atmaca.imagemover;

public final class MovePolicy {
    public String targetRelativePath() {
        return "Pictures/1907/";
    }

    public boolean replaceSameNameDestination() {
        return true;
    }

    public boolean deleteSourceOnlyAfterSuccessfulCopy() {
        return true;
    }
}
