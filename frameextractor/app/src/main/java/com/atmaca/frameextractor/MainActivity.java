package com.atmaca.frameextractor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int VIDEO = 1, FOLDER = 2;
    private Uri video, folder;
    private TextView status;
    private Button start;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 48, 32, 32);
        box.setBackgroundColor(Color.rgb(18, 42, 89));
        TextView heading = new TextView(this);
        heading.setText("ATMACA\nKARE ÇIKARICI");
        heading.setTextSize(26);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(Color.rgb(247, 213, 72));
        box.addView(heading);
        TextView info = new TextView(this);
        info.setText("Her saniyeden 1 kare • Tam çözünürlükte PNG • 50'şerli klasörler");
        info.setTextColor(Color.WHITE);
        info.setTextSize(16);
        info.setPadding(0, 22, 0, 24);
        box.addView(info);
        addButton(box, "1  Video seç", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, VIDEO);
        });
        addButton(box, "2  Çıktı klasörü seç", v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), FOLDER));
        start = addButton(box, "KARELERİ ÇIKAR", v -> {
            Intent i = new Intent(this, ExtractService.class);
            i.putExtra("video", video.toString());
            i.putExtra("folder", folder.toString());
            try {
                startForegroundService(i);
                status.setText("İşlem başladı. İlerlemeyi bildirimden izleyebilirsin.");
            } catch (Exception e) { status.setText("Başlatılamadı: " + e.getMessage()); }
        });
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setPadding(0, 28, 0, 0);
        box.addView(status);
        setContentView(box);
        update();
    }

    private Button addButton(LinearLayout box, String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = 18;
        box.addView(b, p);
        return b;
    }

    private void update() {
        start.setEnabled(video != null && folder != null);
        status.setText((video == null ? "Video bekleniyor" : "Video seçildi") + "  •  " +
                (folder == null ? "Klasör bekleniyor" : "Klasör seçildi"));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == VIDEO) {
            video = data.getData();
            getContentResolver().takePersistableUriPermission(video, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (request == FOLDER) {
            folder = data.getData();
            getContentResolver().takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        update();
    }
}
