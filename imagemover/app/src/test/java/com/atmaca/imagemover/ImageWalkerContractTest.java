package com.atmaca.imagemover;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageWalkerContractTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Object walker() throws Exception {
        Class<?> type = Class.forName("com.atmaca.imagemover.ImageWalker");
        return type.getDeclaredConstructor().newInstance();
    }

    @SuppressWarnings("unchecked")
    private void walk(Object walker, Path root, Path target, Consumer<Path> consumer) throws Exception {
        Method method = walker.getClass().getMethod("walk", Path.class, Path.class, Consumer.class);
        method.invoke(walker, root, target, consumer);
    }

    @Test
    public void emitsImagesCaseInsensitivelyAndSkipsNonImages() throws Exception {
        Path root = temporaryFolder.newFolder("scan").toPath();
        Path nested = Files.createDirectories(root.resolve("alt/derin"));
        Path jpg = Files.write(root.resolve("bir.jpg"), "1".getBytes(StandardCharsets.UTF_8));
        Path png = Files.write(nested.resolve("iki.PNG"), "2".getBytes(StandardCharsets.UTF_8));
        Path heic = Files.write(nested.resolve("uc.HEIC"), "3".getBytes(StandardCharsets.UTF_8));
        Path txt = Files.write(root.resolve("not.txt"), "x".getBytes(StandardCharsets.UTF_8));
        Path mp4 = Files.write(nested.resolve("video.mp4"), "v".getBytes(StandardCharsets.UTF_8));
        Path target = Files.createDirectories(root.resolve("Pictures/1907"));

        List<Path> found = new ArrayList<>();
        walk(walker(), root, target, found::add);

        assertTrue(found.contains(jpg));
        assertTrue(found.contains(png));
        assertTrue(found.contains(heic));
        assertFalse(found.contains(txt));
        assertFalse(found.contains(mp4));
    }

    @Test
    public void neverReentersDestinationTree() throws Exception {
        Path root = temporaryFolder.newFolder("skip-target").toPath();
        Path target = Files.createDirectories(root.resolve("Pictures/1907"));
        Path outside = Files.write(root.resolve("disarida.webp"), "o".getBytes(StandardCharsets.UTF_8));
        Path inside = Files.write(target.resolve("zaten-hedefte.jpg"), "i".getBytes(StandardCharsets.UTF_8));

        List<Path> found = new ArrayList<>();
        walk(walker(), root, target, found::add);

        assertTrue(found.contains(outside));
        assertFalse(found.contains(inside));
    }
}
