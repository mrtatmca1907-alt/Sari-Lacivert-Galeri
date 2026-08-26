package com.sarilacivert.ciftmedyabulucu;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_PERMISSION = 100;
    private static final int REQ_DELETE = 101;
    private static final int NAVY = Color.rgb(8, 20, 38);
    private static final int NAVY_2 = Color.rgb(11, 29, 58);
    private static final int YELLOW = Color.rgb(244, 196, 48);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<ResultEntry> results = new ArrayList<>();
    private final ArrayList<String> displayRows = new ArrayList<>();

    private TextView statusText;
    private ProgressBar progressBar;
    private ListView listView;
    private Button scanButton;
    private Button autoSelectButton;
    private Button deleteButton;
    private ArrayAdapter<String> adapter;
    private boolean scanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (hasMediaPermissions()) {
            scanDuplicates();
        } else {
            requestMediaPermissions();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);

        TextView title = new TextView(this);
        title.setText("ÇİFT FOTO + VİDEO BULUCU");
        title.setTextColor(YELLOW);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(18), dp(14), dp(18), dp(14));
        title.setBackgroundColor(NAVY_2);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        statusText = new TextView(this);
        statusText.setText("Medya izni bekleniyor…");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setPadding(dp(16), dp(14), dp(16), dp(10));
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(42), dp(42));
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(progressBar, pp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(dp(8), dp(8), dp(8), dp(8));

        scanButton = makeButton("TARA");
        autoSelectButton = makeButton("ÇİFTLERİ SEÇ");
        deleteButton = makeButton("SEÇİLENİ SİL");

        buttons.addView(scanButton, weightedButtonParams());
        buttons.addView(autoSelectButton, weightedButtonParams());
        buttons.addView(deleteButton, weightedButtonParams());
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        listView = new ListView(this);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setDividerHeight(1);
        listView.setBackgroundColor(NAVY);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, displayRows) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t = (TextView) v;
                t.setTextColor(Color.WHITE);
                t.setTextSize(14);
                t.setPadding(dp(10), dp(8), dp(8), dp(8));
                t.setBackgroundColor(NAVY);
                return v;
            }
        };
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1f));

        scanButton.setOnClickListener(v -> scanDuplicates());
        autoSelectButton.setOnClickListener(v -> selectDuplicateCopies());
        deleteButton.setOnClickListener(v -> deleteSelected());
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < results.size() && results.get(position).keeper) {
                listView.setItemChecked(position, false);
                Toast.makeText(this, "Her gruptaki ilk dosya korunuyor.", Toast.LENGTH_SHORT).show();
            }
        });

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(NAVY);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(YELLOW));
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean hasMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, REQ_PERMISSION);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) {
            if (hasMediaPermissions()) {
                scanDuplicates();
            } else {
                statusText.setText("Fotoğraf ve video izni verilmedi. Taramak için TARA'ya basıp izin ver.");
            }
        }
    }

    private void scanDuplicates() {
        if (scanning) return;
        if (!hasMediaPermissions()) {
            requestMediaPermissions();
            return;
        }

        scanning = true;
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        autoSelectButton.setEnabled(false);
        deleteButton.setEnabled(false);
        statusText.setText("Fotoğraf ve videolar listeleniyor…");
        listView.clearChoices();
        results.clear();
        displayRows.clear();
        adapter.notifyDataSetChanged();

        executor.execute(() -> {
            try {
                ArrayList<MediaItem> all = new ArrayList<>();
                queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "FOTO", all);
                queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "VİDEO", all);

                mainHandler.post(() -> statusText.setText(all.size() + " medya bulundu. Aynı boyuttakiler doğrulanıyor…"));

                Map<String, ArrayList<MediaItem>> bySize = new HashMap<>();
                for (MediaItem item : all) {
                    if (item.size <= 0) continue;
                    String key = item.type + ":" + item.size;
                    ArrayList<MediaItem> bucket = bySize.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        bySize.put(key, bucket);
                    }
                    bucket.add(item);
                }

                ArrayList<ArrayList<MediaItem>> candidates = new ArrayList<>();
                int candidateFiles = 0;
                for (ArrayList<MediaItem> bucket : bySize.values()) {
                    if (bucket.size() > 1) {
                        candidates.add(bucket);
                        candidateFiles += bucket.size();
                    }
                }

                final int totalCandidates = candidateFiles;
                int hashed = 0;
                int groupNo = 0;
                long recoverable = 0L;
                ArrayList<ResultEntry> found = new ArrayList<>();

                for (ArrayList<MediaItem> bucket : candidates) {
                    Map<String, ArrayList<MediaItem>> hashes = new HashMap<>();
                    for (MediaItem item : bucket) {
                        String hash = sha256(item.uri);
                        hashed++;
                        if (hash != null) {
                            ArrayList<MediaItem> same = hashes.get(hash);
                            if (same == null) {
                                same = new ArrayList<>();
                                hashes.put(hash, same);
                            }
                            same.add(item);
                        }
                        if (hashed % 10 == 0 || hashed == totalCandidates) {
                            final int h = hashed;
                            mainHandler.post(() -> statusText.setText("Doğrulanıyor: " + h + " / " + totalCandidates));
                        }
                    }

                    for (ArrayList<MediaItem> same : hashes.values()) {
                        if (same.size() > 1) {
                            groupNo++;
                            Collections.sort(same, Comparator.comparingLong(a -> a.dateAdded));
                            for (int i = 0; i < same.size(); i++) {
                                boolean keeper = i == 0;
                                found.add(new ResultEntry(groupNo, same.get(i), keeper));
                                if (!keeper) recoverable += same.get(i).size;
                            }
                        }
                    }
                }

                final int groups = groupNo;
                final long bytes = recoverable;
                mainHandler.post(() -> showResults(found, groups, bytes, all.size()));
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    scanning = false;
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);
                    statusText.setText("Tarama hatası: " + t.getClass().getSimpleName() + " - " + String.valueOf(t.getMessage()));
                });
            }
        });
    }

    private void queryMedia(Uri baseUri, String type, ArrayList<MediaItem> out) {
        String[] projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };

        try (Cursor c = getContentResolver().query(baseUri, projection, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (c == null) return;
            int idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int bucketCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
            while (c.moveToNext()) {
                long id = c.getLong(idCol);
                String name = safe(c.getString(nameCol));
                long size = c.getLong(sizeCol);
                long date = c.getLong(dateCol);
                String bucket = bucketCol >= 0 ? safe(c.getString(bucketCol)) : "";
                Uri uri = ContentUris.withAppendedId(baseUri, id);
                out.add(new MediaItem(uri, name, bucket, type, size, date));
            }
        }
    }

    private String sha256(Uri uri) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ContentResolver resolver = getContentResolver();
            try (InputStream raw = resolver.openInputStream(uri)) {
                if (raw == null) return null;
                try (BufferedInputStream in = new BufferedInputStream(raw, 256 * 1024)) {
                    byte[] buffer = new byte[256 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        md.update(buffer, 0, n);
                    }
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void showResults(ArrayList<ResultEntry> found, int groups, long recoverable, int totalMedia) {
        scanning = false;
        progressBar.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        autoSelectButton.setEnabled(true);
        deleteButton.setEnabled(true);
        results.clear();
        results.addAll(found);
        displayRows.clear();

        int removableCopies = 0;
        for (ResultEntry e : results) {
            if (!e.keeper) removableCopies++;
            String mark = e.keeper ? "KORU" : "ÇİFT";
            displayRows.add("Grup " + e.group + "  [" + mark + "]  " + e.item.type + "\n"
                    + e.item.name + "\n"
                    + (e.item.bucket.isEmpty() ? "(klasör bilinmiyor)" : e.item.bucket)
                    + "  •  " + humanBytes(e.item.size));
        }
        adapter.notifyDataSetChanged();
        listView.clearChoices();

        if (groups == 0) {
            statusText.setText(totalMedia + " medya tarandı. Birebir aynı foto/video bulunmadı.");
        } else {
            statusText.setText(groups + " çift grup • " + removableCopies + " silinebilir kopya • kazanılabilir " + humanBytes(recoverable));
        }
    }

    private void selectDuplicateCopies() {
        if (results.isEmpty()) return;
        for (int i = 0; i < results.size(); i++) {
            listView.setItemChecked(i, !results.get(i).keeper);
        }
        Toast.makeText(this, "Her grubun ilk kopyası korunarak diğerleri seçildi.", Toast.LENGTH_SHORT).show();
    }

    private void deleteSelected() {
        if (scanning) return;
        ArrayList<Uri> selected = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).keeper && listView.isItemChecked(i)) {
                selected.add(results.get(i).item.uri);
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Silmek için en az bir çift kopya seç.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 30) {
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), selected);
                startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE, null, 0, 0, 0);
            } else {
                int deleted = 0;
                for (Uri uri : selected) {
                    deleted += getContentResolver().delete(uri, null, null);
                }
                Toast.makeText(this, deleted + " dosya silindi.", Toast.LENGTH_SHORT).show();
                scanDuplicates();
            }
        } catch (IntentSender.SendIntentException | SecurityException e) {
            Toast.makeText(this, "Silme başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DELETE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Seçilen çiftler silindi.", Toast.LENGTH_SHORT).show();
                scanDuplicates();
            } else {
                Toast.makeText(this, "Silme iptal edildi.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private static final class MediaItem {
        final Uri uri;
        final String name;
        final String bucket;
        final String type;
        final long size;
        final long dateAdded;

        MediaItem(Uri uri, String name, String bucket, String type, long size, long dateAdded) {
            this.uri = uri;
            this.name = name;
            this.bucket = bucket;
            this.type = type;
            this.size = size;
            this.dateAdded = dateAdded;
        }
    }

    private static final class ResultEntry {
        final int group;
        final MediaItem item;
        final boolean keeper;

        ResultEntry(int group, MediaItem item, boolean keeper) {
            this.group = group;
            this.item = item;
            this.keeper = keeper;
        }
    }
}
