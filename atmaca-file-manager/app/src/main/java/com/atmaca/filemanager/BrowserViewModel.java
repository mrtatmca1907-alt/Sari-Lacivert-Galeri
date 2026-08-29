package com.atmaca.filemanager;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class BrowserViewModel extends ViewModel {
    public static final class BrowserState {
        public final File currentDir;
        public final List<FileEntry> items;
        public final boolean loading;
        public final String error;
        public final String filter;

        BrowserState(File currentDir, List<FileEntry> items, boolean loading, String error, String filter) {
            this.currentDir = currentDir;
            this.items = items;
            this.loading = loading;
            this.error = error;
            this.filter = filter;
        }
    }

    private final MutableLiveData<BrowserState> state = new MutableLiveData<>(
            new BrowserState(null, Collections.emptyList(), false, null, ""));
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AtmacaDirLoader");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final AtomicLong generation = new AtomicLong();
    private volatile File currentDir;
    private volatile List<FileEntry> completeItems = Collections.emptyList();
    private volatile String currentFilter = "";

    public LiveData<BrowserState> state() { return state; }

    public void open(File dir) {
        if (dir == null) return;
        currentDir = dir;
        currentFilter = "";
        load(dir);
    }

    public void refresh() {
        File dir = currentDir;
        if (dir != null) load(dir);
    }

    public void goUp() {
        File dir = currentDir;
        if (dir == null) return;
        File parent = dir.getParentFile();
        if (parent != null) open(parent);
    }

    public void search(String query) {
        currentFilter = query == null ? "" : query.trim();
        publishFiltered(currentFilter);
    }

    private void publishFiltered(String filter) {
        List<FileEntry> source = completeItems;
        if (filter == null || filter.isEmpty()) {
            state.postValue(new BrowserState(currentDir, source, false, null, ""));
            return;
        }
        String q = filter.toLowerCase(Locale.ROOT);
        List<FileEntry> out = new ArrayList<>();
        for (FileEntry e : source) {
            if (e.name.toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        }
        state.postValue(new BrowserState(currentDir,
                Collections.unmodifiableList(out), false, null, filter));
    }

    private void load(File dir) {
        long token = generation.incrementAndGet();
        state.postValue(new BrowserState(dir, Collections.emptyList(), true, null, currentFilter));
        loader.execute(() -> {
            List<FileEntry> found = new ArrayList<>();
            String error = null;
            try {
                if (!dir.exists()) {
                    error = "Klasör bulunamadı";
                } else if (!dir.isDirectory()) {
                    error = "Bu bir klasör değil";
                } else {
                    File[] children = null;
                    try { children = dir.listFiles(); } catch (Throwable ignored) { }
                    if (children == null) {
                        error = "Klasör okunamadı";
                    } else {
                        for (File f : children) {
                            if (token != generation.get()) return;
                            try {
                                String name = f.getName();
                                if (name == null || name.isEmpty()) continue;
                                boolean isDir = f.isDirectory();
                                long size = isDir ? 0L : Math.max(0L, f.length());
                                found.add(new FileEntry(f.getAbsolutePath(), name, isDir, size, f.lastModified()));
                            } catch (Throwable ignored) {
                                // A single corrupt/inaccessible entry must not kill the whole folder listing.
                            }
                        }
                        found.sort(FileRules.ENTRY_COMPARATOR);
                    }
                }
            } catch (Throwable t) {
                error = t.getClass().getSimpleName() + ": " + safeMessage(t);
            }
            if (token != generation.get()) return;
            List<FileEntry> immutable = Collections.unmodifiableList(new ArrayList<>(found));
            completeItems = immutable;
            String filter = currentFilter;
            if (filter != null && !filter.isEmpty()) {
                publishFiltered(filter);
            } else {
                state.postValue(new BrowserState(dir, immutable, false, error, ""));
            }
        });
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? "bilinmeyen hata" : m;
    }

    public void shutdown() {
        generation.incrementAndGet();
        loader.shutdownNow();
    }

    @Override protected void onCleared() {
        shutdown();
    }
}
