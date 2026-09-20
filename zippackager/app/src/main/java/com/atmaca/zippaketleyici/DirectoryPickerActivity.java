package com.atmaca.zippaketleyici;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class DirectoryPickerActivity extends Activity {
    public static final String EXTRA_PATH = "path";

    private File root;
    private File current;
    private TextView pathView;
    private ArrayAdapter<String> adapter;
    private final List<File> dirs = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            root = Environment.getExternalStorageDirectory().getCanonicalFile();
            current = root;
        } catch (IOException e) {
            root = Environment.getExternalStorageDirectory();
            current = root;
        }
        buildUi();
        refresh();
    }

    private void buildUi() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(14), dp(18), dp(14), dp(14));
        rootLayout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("KLASÖR SEÇ");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(0,42,92));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));
        rootLayout.addView(title, new LinearLayout.LayoutParams(-1, -2));

        pathView = new TextView(this);
        pathView.setTextSize(15);
        pathView.setTextColor(Color.DKGRAY);
        pathView.setPadding(0, 0, 0, dp(10));
        rootLayout.addView(pathView, new LinearLayout.LayoutParams(-1, -2));

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            current = dirs.get(position);
            refresh();
        });
        rootLayout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button use = new Button(this);
        use.setText("BU KLASÖRÜ KULLAN");
        use.setTextSize(17);
        use.setOnClickListener(v -> {
            if (current == null || !current.isDirectory()) return;
            Intent result = new Intent();
            result.putExtra(EXTRA_PATH, current.getAbsolutePath());
            setResult(RESULT_OK, result);
            finish();
        });
        rootLayout.addView(use, new LinearLayout.LayoutParams(-1, dp(58)));

        setContentView(rootLayout);
    }

    private void refresh() {
        pathView.setText(current.equals(root) ? "Dahili depolama kökü\n" + current.getAbsolutePath()
                : current.getAbsolutePath());

        dirs.clear();
        File[] all = current.listFiles(File::isDirectory);
        if (all != null) {
            Arrays.sort(all, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            dirs.addAll(Arrays.asList(all));
        }

        List<String> labels = new ArrayList<>(dirs.size());
        for (File f : dirs) labels.add("📁  " + f.getName());
        adapter.clear();
        adapter.addAll(labels);
        adapter.notifyDataSetChanged();

        if (all == null) {
            Toast.makeText(this, "Bu klasör okunamıyor.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onBackPressed() {
        if (current != null && !current.equals(root)) {
            try {
                File parent = current.getParentFile();
                if (parent != null && parent.getCanonicalPath().startsWith(root.getCanonicalPath())) {
                    current = parent;
                    refresh();
                    return;
                }
            } catch (IOException ignored) {}
        }
        super.onBackPressed();
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }
}
