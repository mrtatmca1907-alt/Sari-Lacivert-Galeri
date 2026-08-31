package com.atmaca.imagemover;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileMoveEngineContractTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Object engine() throws Exception {
        Class<?> type = Class.forName("com.atmaca.imagemover.FileMoveEngine");
        return type.getDeclaredConstructor().newInstance();
    }

    private boolean move(Object engine, Path source, Path targetDir) throws Exception {
        Method method = engine.getClass().getMethod("move", Path.class, Path.class);
        return (Boolean) method.invoke(engine, source, targetDir);
    }

    @Test
    public void movesSourceIntoTargetDirectory() throws Exception {
        Path root = temporaryFolder.newFolder("basic").toPath();
        Path source = root.resolve("foto.jpg");
        Path target = Files.createDirectory(root.resolve("1907"));
        Files.write(source, "yeni-goruntu".getBytes(StandardCharsets.UTF_8));

        assertTrue(move(engine(), source, target));
        assertFalse(Files.exists(source));
        assertEquals("yeni-goruntu", Files.readString(target.resolve("foto.jpg")));
    }

    @Test
    public void replacesExistingSameNameDestination() throws Exception {
        Path root = temporaryFolder.newFolder("replace").toPath();
        Path source = root.resolve("aynı.jpg");
        Path target = Files.createDirectory(root.resolve("1907"));
        Files.write(source, "kaynak".getBytes(StandardCharsets.UTF_8));
        Files.write(target.resolve("aynı.jpg"), "eski-hedef".getBytes(StandardCharsets.UTF_8));

        assertTrue(move(engine(), source, target));
        assertFalse(Files.exists(source));
        assertEquals("kaynak", Files.readString(target.resolve("aynı.jpg")));
    }

    @Test
    public void failedMoveKeepsSourceUntouched() throws Exception {
        Path root = temporaryFolder.newFolder("failure").toPath();
        Path source = root.resolve("koru.png");
        Path notADirectory = root.resolve("hedef-dosya");
        Files.write(source, "dokunma".getBytes(StandardCharsets.UTF_8));
        Files.write(notADirectory, "normal-dosya".getBytes(StandardCharsets.UTF_8));

        assertFalse(move(engine(), source, notADirectory));
        assertTrue(Files.exists(source));
        assertEquals("dokunma", Files.readString(source));
    }
}
