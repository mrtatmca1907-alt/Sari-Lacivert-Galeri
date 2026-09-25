package com.sarilacivert.ciftmedyabulucu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity {

    private final ArrayList<File> folders = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ListView listView;
    private TextView pathText;
    private TextView statusText;
    private Button deleteButton;
    private File currentDir;
    private File selectedDir;

    private static final int NAVY = Color.rgb(8, 20, 38);
    private static final int YELLOW = Color.rgb(244, 196, 48);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        ensureAllFilesAccess();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);
        root.setPadding(dp(12), dp(18), dp(12), dp(12));

        TextView title = new TextView(this);
        title.setText("ATMACA HIZLI KLASÖR SİLİCİ V2");
        title.setTextColor(YELLOW);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        pathText = new TextView(this);
        pathText.setTextColor(Color.WHITE);
        pathText.setTextSize(14);
        pathText.setPadding(dp(8), dp(10), dp(8), dp(10));
        root.addView(pathText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout topButtons = new LinearLayout(this);
        topButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button up = makeButton("YUKARI");
        Button storage = makeButton("DAHİLİ DEPOLAMA");
        topButtons.addView(up, new LinearLayout.LayoutParams(0, dp(48), 1f));
        topButtons.addView(storage, new LinearLayout.LayoutParams(0, dp(48), 2f));
        root.addView(topButtons, new LinearLayout.LayoutParams(-1, -2));

        listView = new ListView(this);
        listView.setBackgroundColor(NAVY);
        listView.setDividerHeight(1);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(Color.WHITE);
                t.setTextSize(16);
                t.setPadding(dp(14), dp(12), dp(8), dp(12));
                t.setBackgroundColor(NAVY);
                return t;
            }
        };
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1f));

        deleteButton = makeButton("BU KLASÖRÜ SİL");
        deleteButton.setEnabled(false);
        root.addView(deleteButton, new LinearLayout.LayoutParams(-1, dp(58)));

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(10), 0, 0);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        storage.setOnClickListener(v -> openDirectory(Environment.getExternalStorageDirectory()));
        up.setOnClickListener(v -> {
            if (currentDir != null && currentDir.getParentFile() != null) {
                File parent = currentDir.getParentFile();
                if (parent.getAbsolutePath().startsWith("/storage")) openDirectory(parent);
            }
        });

        listView.setOnItemClickListener((p, v, position, id) -> {
            if (position >= 0 && position < folders.size()) {
                openDirectory(folders.get(position));
            }
        });

        deleteButton.setOnClickListener(v -> confirmDelete());
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(NAVY);
        b.setTextSize(15);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(YELLOW));
        b.setAllCaps(false);
        return b;
    }

    private void ensureAllFilesAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                    .setTitle("Tüm dosyalara erişim gerekli")
                    .setMessage("Hızlı silme için Android dosya seçicisini kullanmayacağım. Bir kez tüm dosyalara erişim izni ver.")
                    .setNegativeButton("Kapat", (d,w) -> finish())
                    .setPositiveButton("İzin ver", (d,w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    }).show();
        } else {
            openDirectory(Environment.getExternalStorageDirectory());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) {
            if (currentDir == null) openDirectory(Environment.getExternalStorageDirectory());
        }
    }

    private void openDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, "Klasör açılamadı", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDir = dir;
        selectedDir = dir;
        pathText.setText(dir.getAbsolutePath());
        deleteButton.setEnabled(!isProtectedRoot(dir));

        File[] children = dir.listFiles(File::isDirectory);
        folders.clear();
        ArrayList<String> names = new ArrayList<>();

        if (children != null) {
            Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File f : children) {
                folders.add(f);
                names.add("📁 " + f.getName());
            }
        }

        adapter.clear();
        adapter.addAll(names);
        adapter.notifyDataSetChanged();

        statusText.setText(folders.size() + " klasör");
    }

    private boolean isProtectedRoot(File dir) {
        String p = dir.getAbsolutePath();
        return "/storage".equals(p)
                || "/storage/emulated".equals(p)
                || "/storage/emulated/0".equals(p)
                || Environment.getExternalStorageDirectory().getAbsolutePath().equals(p);
    }

    private void confirmDelete() {
        if (selectedDir == null || isProtectedRoot(selectedDir)) {
            Toast.makeText(this, "Depolama kökü silinemez", Toast.LENGTH_SHORT).show();
            return;
        }

        final File target = selectedDir;
        new AlertDialog.Builder(this)
                .setTitle("Kalıcı sil")
                .setMessage(target.getAbsolutePath() + "\n\nKlasör ve içindeki her şey doğrudan silinecek.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("SİL", (d,w) -> fastDelete(target))
                .show();
    }

    private void fastDelete(File target) {
        deleteButton.setEnabled(false);
        statusText.setText("Siliniyor...");

        new Thread(() -> {
            long[] result = deleteTreeIterative(target);

            runOnUiThread(() -> {
                statusText.setText("Silinen: " + result[0] + "  |  Silinemeyen: " + result[1]);
                Toast.makeText(this, "Bitti", Toast.LENGTH_LONG).show();
                File parent = target.getParentFile();
                if (parent != null && parent.exists()) openDirectory(parent);
            });
        }, "atmaca-fast-delete").start();
    }

    private long[] deleteTreeIterative(File root) {
        long deleted = 0;
        long failed = 0;

        ArrayList<File> stack = new ArrayList<>();
        ArrayList<File> post = new ArrayList<>();
        stack.add(root);

        while (!stack.isEmpty()) {
            File f = stack.remove(stack.size() - 1);
            post.add(f);

            if (f.isDirectory()) {
                File[] list = f.listFiles();
                if (list != null) {
                    for (File child : list) stack.add(child);
                }
            }
        }

        for (int i = post.size() - 1; i >= 0; i--) {
            File f = post.get(i);
            try {
                if (f.delete()) deleted++;
                else failed++;
            } catch (Exception e) {
                failed++;
            }
        }

        return new long[]{deleted, failed};
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
