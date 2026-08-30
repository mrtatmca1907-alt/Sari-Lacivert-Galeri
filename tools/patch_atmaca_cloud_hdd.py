from pathlib import Path

path = Path("ATMACA-WIFI/app/src/main/java/com/atmaca/files/MainActivity.java")
s = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    s = s.replace(old, new, 1)

replace_once(
'''    private static final int REQ_VIDEO_PERMISSION = 4108;\n    private static final String PREF_CLOUD_TREE = "cloud_tree_uri";''',
'''    private static final int REQ_VIDEO_PERMISSION = 4108;\n    private static final int REQ_CLOUD_HDD_SOURCE = 4109;\n    private static final String PREF_CLOUD_TREE = "cloud_tree_uri";''',
"request code")

replace_once(
'''    @Override protected void onResume() {\n        super.onResume();\n        if (db != null && (db.pendingUploadCount() > 0 || !db.pendingQueue().isEmpty())) SyncScheduler.scheduleNow(this);\n    }''',
'''    @Override protected void onResume() {\n        super.onResume();\n        if (db != null) autoSyncNow();\n    }''',
"onResume")

replace_once(
'''        Button send = new Button(this);\n        send.setText("Kuyruğu Gönder");\n        send.setOnClickListener(v -> syncNow());''',
'''        Button send = new Button(this);\n        send.setText("Kuyruk / Gönder");\n        send.setOnClickListener(v -> showQueueDialog());''',
"queue button")

replace_once(
'''        cloudRow.addView(folderBackup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));\n        cloudRow.addView(cloudDownload, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));\n        root.addView(cloudRow);\n\n        pathView = new TextView(this);''',
'''        cloudRow.addView(folderBackup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));\n        cloudRow.addView(cloudDownload, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));\n        root.addView(cloudRow);\n\n        Button cloudToHdd = new Button(this);\n        cloudToHdd.setText("Bulut → Telefon → HDD");\n        cloudToHdd.setOnClickListener(v -> startCloudToHdd());\n        LinearLayout.LayoutParams cloudHddParams = new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n        cloudHddParams.setMargins(12, 0, 12, 6);\n        root.addView(cloudToHdd, cloudHddParams);\n\n        pathView = new TextView(this);''',
"cloud HDD button")

replace_once(
'''        if (requestCode == REQ_PHONE_DEST) {\n            Uri dest = data.getData();\n            if (dest == null || cloudDownloadItems.isEmpty()) return;\n            persistTreePermission(data, dest);\n            copyCloudFilesToPhone(dest);\n        }\n    }''',
'''        if (requestCode == REQ_PHONE_DEST) {\n            Uri dest = data.getData();\n            if (dest == null || cloudDownloadItems.isEmpty()) return;\n            persistTreePermission(data, dest);\n            copyCloudFilesToPhone(dest);\n            return;\n        }\n\n        if (requestCode == REQ_CLOUD_HDD_SOURCE) {\n            Uri tree = data.getData();\n            if (tree == null) return;\n            persistTreePermission(data, tree);\n            prefs.edit().putString(PREF_CLOUD_TREE, tree.toString()).apply();\n            runCloudToHdd(tree);\n        }\n    }''',
"cloud HDD activity result")

marker = '''    private void refreshList() {\n'''
insert = r'''    private void startCloudToHdd() {
        String saved = prefs.getString(PREF_CLOUD_TREE, "");
        if (saved == null || saved.trim().isEmpty()) {
            status.setText("Bulut yedek klasörünü seç");
            toast("Yedeklenen Bulut klasörünü seç");
            pickDestinationTree(REQ_CLOUD_HDD_SOURCE);
            return;
        }
        runCloudToHdd(Uri.parse(saved));
    }

    private void runCloudToHdd(Uri cloudTree) {
        String host = hostInput.getText().toString().trim();
        prefs.edit().putString("host", host).apply();
        status.setText("Bulut taranıyor • Telefon → HDD hazırlanıyor...");
        io.execute(() -> {
            try {
                CloudHddTransfer.Result result = CloudHddTransfer.transfer(
                        this, db, cloudTree, host,
                        (done, total, uploaded, queued, failed, currentName) -> runOnUiThread(() -> {
                            String text = "Bulut → Telefon → HDD: " + done + " / " + total
                                    + " • HDD " + uploaded + " • bekleyen " + queued;
                            if (failed > 0) text += " • hata " + failed;
                            if (currentName != null && !currentName.isEmpty()) text += " • " + currentName;
                            status.setText(text);
                        }));

                if (result.queued > 0) SyncScheduler.scheduleNow(this);
                runOnUiThread(() -> {
                    refreshList();
                    String text = "Bulut → HDD: " + result.processed + " / " + result.total
                            + " • HDD " + result.uploaded + " • bekleyen " + result.queued;
                    if (result.failed > 0) text += " • hata " + result.failed;
                    status.setText(text);
                    if (result.failed == 0 && result.queued == 0) {
                        toast("Bulut yedekleri HDD'ye aktarıldı");
                    } else if (result.queued > 0) {
                        toast(result.queued + " dosya telefonda güvenli HDD kuyruğunda");
                    } else {
                        toast(result.failed + " dosya alınamadı; Bulut'taki kaynaklar silinmedi");
                    }
                });
            } catch (SecurityException e) {
                prefs.edit().remove(PREF_CLOUD_TREE).apply();
                runOnUiThread(() -> {
                    status.setText("Bulut klasör izni yenilenmeli");
                    toast("Yedeklenen Bulut klasörünü tekrar seç");
                    pickDestinationTree(REQ_CLOUD_HDD_SOURCE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Bulut → HDD hatası: " + safe(e.getMessage()));
                    toast("Alınmış dosyalar kuyrukta korunuyor; Bulut kaynakları silinmedi");
                    refreshList();
                });
            }
        });
    }

    private void showQueueDialog() {
        io.execute(() -> {
            List<PendingUpload> uploads = db.pendingUploads();
            List<String> operations = db.pendingQueue();
            StringBuilder text = new StringBuilder();
            int shown = Math.min(uploads.size(), 100);
            for (int i = 0; i < shown; i++) {
                PendingUpload p = uploads.get(i);
                text.append("• ").append(p.name).append("\n  → ").append(p.remotePath()).append("\n");
            }
            if (uploads.size() > shown) {
                text.append("\n+").append(uploads.size() - shown).append(" dosya daha");
            }
            if (!operations.isEmpty()) {
                text.append("\n\n").append(operations.size()).append(" dosya işlemi de bekliyor.");
            }
            String body = text.length() == 0 ? "Kuyruk boş." : text.toString();
            String title = "HDD Kuyruğu • " + uploads.size() + " dosya • " + operations.size() + " işlem";
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(body)
                    .setNegativeButton("Kapat", null)
                    .setPositiveButton("Şimdi Gönder", (d, which) -> syncNow())
                    .show());
        });
    }

    private void autoSyncNow() {
        String host = hostInput.getText().toString().trim();
        prefs.edit().putString("host", host).apply();
        io.execute(() -> {
            try {
                SyncEngine.run(this, db, host);
            } catch (Exception ignored) {
                if (db.pendingUploadCount() > 0 || !db.pendingQueue().isEmpty()) {
                    SyncScheduler.scheduleNow(this);
                }
            }
            runOnUiThread(this::refreshList);
        });
    }

'''
if s.count(marker) != 1:
    raise SystemExit(f"refresh marker: expected exactly 1 match, found {s.count(marker)}")
s = s.replace(marker, insert + marker, 1)

path.write_text(s, encoding="utf-8")
print("MainActivity patched for visible queue, auto HDD catalog and Cloud→Phone→HDD transfer")
