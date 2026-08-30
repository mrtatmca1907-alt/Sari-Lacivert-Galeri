package com.atmaca.files;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public final class SyncEngine {
    public static final class Result {
        public final int uploaded;
        public final int metadataOps;
        public final int catalogCount;

        public Result(int uploaded, int metadataOps, int catalogCount) {
            this.uploaded = uploaded;
            this.metadataOps = metadataOps;
            this.catalogCount = catalogCount;
        }
    }

    private SyncEngine() {}

    public static Result run(Context context, CatalogDb db, String host) throws Exception {
        AtmacaApi api = new AtmacaApi(host);
        if (!api.health()) throw new IllegalStateException("PC servisi yanıt vermedi");

        int uploaded = uploadPending(db, api);

        List<String> pending = db.pendingQueue();
        int metadataOps = pending.size();
        if (!pending.isEmpty()) {
            api.sendQueue(pending);
            db.clearQueue();
        }

        List<CatalogEntry> rows = api.fetchCatalog();
        db.replaceAll(rows);
        return new Result(uploaded, metadataOps, rows.size());
    }

    static int uploadPending(CatalogDb db, AtmacaApi api) throws Exception {
        int uploaded = 0;
        List<PendingUpload> uploads = db.pendingUploads();
        for (PendingUpload item : uploads) {
            File file = new File(item.localPath);
            if (!file.isFile()) {
                throw new IllegalStateException("Bekleyen dosya bulunamadı: " + item.name);
            }
            try (FileInputStream in = new FileInputStream(file)) {
                api.upload(item.remoteDir, item.name, in);
            }
            db.deleteUpload(item.id);
            if (!file.delete() && file.exists()) {
                // HDD upload was confirmed. A leftover local payload can be cleaned later.
            }
            uploaded++;
        }
        return uploaded;
    }
}
