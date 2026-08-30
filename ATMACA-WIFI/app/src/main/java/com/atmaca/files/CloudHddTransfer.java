package com.atmaca.files;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.Collections;

public final class CloudHddTransfer {
    private CloudHddTransfer() {}

    public interface ProgressListener {
        void onProgress(int processed, int total, int uploaded, int queued, int failed, String currentName);
    }

    public static final class Result {
        public final int total;
        public final int processed;
        public final int uploaded;
        public final int queued;
        public final int failed;
        public final boolean pcReachable;

        Result(int total, int processed, int uploaded, int queued, int failed, boolean pcReachable) {
            this.total = total;
            this.processed = processed;
            this.uploaded = uploaded;
            this.queued = queued;
            this.failed = failed;
            this.pcReachable = pcReachable;
        }
    }

    private static final class State {
        int processed;
        int uploaded;
        int failed;
        boolean online;
    }

    public static Result transfer(Context context, CatalogDb db, Uri cloudTree, String host,
                                  ProgressListener listener) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        Uri root = DocumentsContract.buildDocumentUriUsingTree(
                cloudTree, DocumentsContract.getTreeDocumentId(cloudTree));
        int total = countFiles(resolver, cloudTree, root);

        AtmacaApi api = new AtmacaApi(host);
        State state = new State();
        try {
            state.online = api.health();
        } catch (Exception ignored) {
            state.online = false;
        }

        // If there is an older safe queue, clear it first while the PC is reachable.
        // A failure simply switches this transfer to offline staging; nothing is deleted.
        if (state.online) {
            try {
                SyncEngine.uploadPending(db, api);
            } catch (Exception ignored) {
                state.online = false;
            }
        }

        transferChildren(context, db, resolver, cloudTree, root, "", api, state, total, listener);

        if (state.online) {
            try {
                SyncEngine.run(context, db, host);
            } catch (Exception ignored) {
                state.online = false;
            }
        }

        return new Result(total, state.processed, state.uploaded, db.pendingUploadCount(), state.failed, state.online);
    }

    private static int countFiles(ContentResolver resolver, Uri tree, Uri parent) throws Exception {
        int total = 0;
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] cols = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor c = resolver.query(children, cols, null, null, null)) {
            if (c == null) throw new IllegalStateException("Bulut klasörü okunamadı");
            while (c.moveToNext()) {
                String id = c.getString(0);
                String mime = c.getString(1);
                Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    total += countFiles(resolver, tree, child);
                } else {
                    total++;
                }
            }
        }
        return total;
    }

    private static void transferChildren(Context context, CatalogDb db, ContentResolver resolver,
                                         Uri tree, Uri parent, String relativeDir,
                                         AtmacaApi api, State state, int total,
                                         ProgressListener listener) throws Exception {
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] cols = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor c = resolver.query(children, cols, null, null, null)) {
            if (c == null) throw new IllegalStateException("Bulut klasörü okunamadı");
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = safeName(c.getString(1));
                String mime = c.getString(2);
                Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, id);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    String next = relativeDir.isEmpty() ? name : relativeDir + "/" + name;
                    transferChildren(context, db, resolver, tree, child, next, api, state, total, listener);
                    continue;
                }

                if (listener != null) {
                    listener.onProgress(state.processed, total, state.uploaded,
                            db.pendingUploadCount(), state.failed, name);
                }

                try {
                    HddStager.stage(context, db, Collections.singletonList(child),
                            CloudHddPathPolicy.remoteDir(relativeDir));

                    if (state.online) {
                        try {
                            state.uploaded += SyncEngine.uploadPending(db, api);
                        } catch (Exception ignored) {
                            // The durable phone copy and DB queue entry remain intact.
                            state.online = false;
                        }
                    }
                } catch (Exception e) {
                    state.failed++;
                }

                state.processed++;
                if (listener != null) {
                    listener.onProgress(state.processed, total, state.uploaded,
                            db.pendingUploadCount(), state.failed, name);
                }
            }
        }
    }

    private static String safeName(String value) {
        if (value == null || value.trim().isEmpty()) return "dosya";
        return value.replace('/', '_').replace('\\', '_').trim();
    }
}
