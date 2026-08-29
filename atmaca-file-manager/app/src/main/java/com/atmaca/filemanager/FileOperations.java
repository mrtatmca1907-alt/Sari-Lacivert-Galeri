package com.atmaca.filemanager;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FileOperations {
    public interface Callback { void onComplete(Result result); }

    public static final class Result {
        public final int successCount;
        public final int failureCount;
        public final List<String> failures;

        Result(int successCount, int failureCount, List<String> failures) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        public boolean allSucceeded() { return failureCount == 0; }
    }

    private interface Operation { Result run(); }

    private static final int BUFFER_SIZE = 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AtmacaFileWorker");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());

    public void copy(List<File> sources, File destinationDir, Callback callback) {
        submit(() -> batchCopy(sources, destinationDir, false), callback);
    }

    public void move(List<File> sources, File destinationDir, Callback callback) {
        submit(() -> batchCopy(sources, destinationDir, true), callback);
    }

    public void delete(List<File> sources, Callback callback) {
        submit(() -> {
            int ok = 0;
            List<String> failures = new ArrayList<>();
            for (File source : safeList(sources)) {
                try {
                    if (deleteTree(source)) ok++;
                    else failures.add(source.getAbsolutePath());
                } catch (Throwable t) {
                    failures.add(source.getAbsolutePath() + " — " + message(t));
                }
            }
            return new Result(ok, failures.size(), failures);
        }, callback);
    }

    public void rename(File source, String newName, Callback callback) {
        submit(() -> {
            List<String> failures = new ArrayList<>();
            if (source == null || newName == null || newName.trim().isEmpty() || newName.contains(File.separator)) {
                failures.add("Geçersiz ad");
                return new Result(0, 1, failures);
            }
            File parent = source.getParentFile();
            if (parent == null) {
                failures.add("Üst klasör bulunamadı");
                return new Result(0, 1, failures);
            }
            File target = new File(parent, newName.trim());
            if (target.exists()) {
                failures.add("Aynı isim zaten var");
                return new Result(0, 1, failures);
            }
            try {
                if (source.renameTo(target)) return new Result(1, 0, failures);
                failures.add("Yeniden adlandırma başarısız");
            } catch (Throwable t) {
                failures.add(message(t));
            }
            return new Result(0, 1, failures);
        }, callback);
    }

    public void mkdir(File parent, String name, Callback callback) {
        submit(() -> {
            List<String> failures = new ArrayList<>();
            if (parent == null || name == null || name.trim().isEmpty() || name.contains(File.separator)) {
                failures.add("Geçersiz klasör adı");
                return new Result(0, 1, failures);
            }
            File dir = new File(parent, name.trim());
            try {
                if (!dir.exists() && dir.mkdir()) return new Result(1, 0, failures);
                failures.add(dir.exists() ? "Klasör zaten var" : "Klasör oluşturulamadı");
            } catch (Throwable t) {
                failures.add(message(t));
            }
            return new Result(0, 1, failures);
        }, callback);
    }

    private Result batchCopy(List<File> sources, File destinationDir, boolean move) {
        int ok = 0;
        List<String> failures = new ArrayList<>();
        if (destinationDir == null || !destinationDir.isDirectory()) {
            return new Result(0, safeList(sources).size(), Collections.singletonList("Hedef klasör geçersiz"));
        }
        for (File source : safeList(sources)) {
            try {
                if (source == null || !source.exists()) {
                    failures.add("Kaynak bulunamadı");
                    continue;
                }
                if (source.isDirectory() && FileRules.isSafeChild(source, destinationDir)) {
                    failures.add(source.getName() + " kendi içine taşınamaz/kopyalanamaz");
                    continue;
                }
                File target = FileRules.uniqueTarget(destinationDir, source.getName());
                boolean done;
                if (move && tryRename(source, target)) {
                    done = true;
                } else {
                    done = copyTree(source, target);
                    if (done && move) done = deleteTree(source);
                }
                if (done) ok++; else failures.add(source.getAbsolutePath());
            } catch (Throwable t) {
                failures.add((source == null ? "Kaynak" : source.getAbsolutePath()) + " — " + message(t));
            }
        }
        return new Result(ok, failures.size(), failures);
    }

    private static boolean tryRename(File source, File target) {
        try { return source.renameTo(target); } catch (Throwable t) { return false; }
    }

    private static boolean copyTree(File source, File target) throws IOException {
        if (source.isFile()) {
            copyFile(source, target);
            return true;
        }
        if (!source.isDirectory()) return false;
        if (!target.exists() && !target.mkdirs()) return false;

        final class Pair {
            final File src, dst;
            Pair(File src, File dst) { this.src = src; this.dst = dst; }
        }
        Deque<Pair> stack = new ArrayDeque<>();
        stack.push(new Pair(source, target));
        while (!stack.isEmpty()) {
            Pair pair = stack.pop();
            File[] children;
            try { children = pair.src.listFiles(); } catch (Throwable t) { return false; }
            if (children == null) return false;
            for (File child : children) {
                File dst = new File(pair.dst, child.getName());
                if (child.isDirectory()) {
                    if (!dst.exists() && !dst.mkdir()) return false;
                    stack.push(new Pair(child, dst));
                } else if (child.isFile()) {
                    copyFile(child, dst);
                }
            }
        }
        return true;
    }

    private static void copyFile(File source, File target) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target, false)) {
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            try { target.delete(); } catch (Throwable ignored) { }
            throw e;
        }
        if (source.length() != target.length()) {
            try { target.delete(); } catch (Throwable ignored) { }
            throw new IOException("Kopya boyutu doğrulanamadı");
        }
        try { target.setLastModified(source.lastModified()); } catch (Throwable ignored) { }
    }

    private static boolean deleteTree(File root) {
        if (root == null || !root.exists()) return true;
        if (root.isFile()) return root.delete();
        Deque<File> first = new ArrayDeque<>();
        Deque<File> post = new ArrayDeque<>();
        first.push(root);
        while (!first.isEmpty()) {
            File f = first.pop();
            post.push(f);
            File[] children;
            try { children = f.listFiles(); } catch (Throwable t) { return false; }
            if (children == null) return false;
            for (File c : children) {
                if (c.isDirectory()) first.push(c);
                else if (!c.delete()) return false;
            }
        }
        while (!post.isEmpty()) if (!post.pop().delete()) return false;
        return true;
    }

    private void submit(Operation op, Callback callback) {
        worker.execute(() -> {
            Result result;
            try { result = op.run(); }
            catch (Throwable t) { result = new Result(0, 1, Collections.singletonList(message(t))); }
            Result finalResult = result;
            main.post(() -> { if (callback != null) callback.onComplete(finalResult); });
        });
    }

    private static List<File> safeList(List<File> list) {
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    private static String message(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    public void shutdown() { worker.shutdownNow(); }
}
