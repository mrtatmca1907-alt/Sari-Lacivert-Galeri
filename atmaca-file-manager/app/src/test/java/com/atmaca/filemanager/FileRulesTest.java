package com.atmaca.filemanager;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class FileRulesTest {
    @Test public void collisionNameGetsIncremented() throws Exception {
        File dir = Files.createTempDirectory("atmaca").toFile();
        assertTrue(new File(dir, "photo.jpg").createNewFile());
        assertTrue(new File(dir, "photo (2).jpg").createNewFile());
        assertEquals("photo (3).jpg", FileRules.uniqueTarget(dir, "photo.jpg").getName());
    }

    @Test public void directoriesSortBeforeFilesThenByName() {
        List<FileEntry> items = new ArrayList<>();
        items.add(new FileEntry("/z.txt", "z.txt", false, 1, 0));
        items.add(new FileEntry("/B", "B", true, 0, 0));
        items.add(new FileEntry("/a", "a", true, 0, 0));
        items.sort(FileRules.ENTRY_COMPARATOR);
        assertEquals("a", items.get(0).name);
        assertEquals("B", items.get(1).name);
        assertEquals("z.txt", items.get(2).name);
    }

    @Test public void childCannotEscapeParent() throws Exception {
        File root = Files.createTempDirectory("root").toFile();
        assertFalse(FileRules.isSafeChild(root, new File(root, "../escape")));
        assertTrue(FileRules.isSafeChild(root, new File(root, "ok/file.txt")));
    }
}
