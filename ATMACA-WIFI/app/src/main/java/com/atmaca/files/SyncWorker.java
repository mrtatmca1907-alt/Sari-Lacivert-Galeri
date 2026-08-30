package com.atmaca.files;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull @Override public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("atmaca", Context.MODE_PRIVATE);
        String host = prefs.getString("host", "192.168.1.104");
        CatalogDb db = new CatalogDb(getApplicationContext());
        try {
            SyncEngine.run(getApplicationContext(), db, host);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        } finally {
            db.close();
        }
    }
}
