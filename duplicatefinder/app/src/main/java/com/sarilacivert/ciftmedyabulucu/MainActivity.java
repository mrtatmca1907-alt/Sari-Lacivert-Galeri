package com.sarilacivert.ciftmedyabulucu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int PICK_FOLDER = 7001;

    private TextView statusText;
    private ProgressBar progressBar;
    private Button chooseButton;
    private Uri selectedTree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(42), dp(22), dp(22));
        root.setBackgroundColor(Color.rgb(8, 20, 38));

        TextView title = new TextView(this);
        title.setText("ATMACA\nHIZLI KLASÖR SİLİCİ");
        title.setTextColor(Color.rgb(244, 196, 48));
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);

        TextView info = new TextView(this);
        info.setText("Telefonu taramaz. Dosya dizini oluşturmaz.\nSadece seçtiğin klasörü doğrudan siler.");
        info.setTextColor(Color.WHITE);
        info.setTextSize(17);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(20), 0, dp(32));

        chooseButton = new Button(this);
        chooseButton.setText("KLASÖR SEÇ");
        chooseButton.setTextSize(20);
        chooseButton.setTextColor(Color.rgb(8, 20, 38));
        chooseButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(244, 196, 48)));
        chooseButton.setOnClickListener(v -> openFolderPicker());

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);

        statusText = new TextView(this);
        statusText.setText("Hazır");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(26), 0, 0);

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));
        root.addView(chooseButton, new LinearLayout.LayoutParams(-1, dp(64)));
        root.addView(progressBar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void openFolderPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        selectedTree = data.getData();

        try {
            int flags = data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(selectedTree, flags);
        } catch (Exception ignored) {
        }

        String treeId;
        try {
            treeId = DocumentsContract.getTreeDocumentId(selectedTree);
        } catch (Exception e) {
            treeId = "";
        }

        // Dahili depolamanın tamamını yanlışlıkla silmeyi engelle.
        if ("primary:".equalsIgnoreCase(treeId) || treeId.endsWith(":")) {
            new AlertDialog.Builder(this)
                    .setTitle("Depolama kökü seçildi")
                    .setMessage("Telefonun depolama kökünün tamamını silmeye izin vermiyorum. Silmek istediğin klasörün içine girip o klasörü seç.")
                    .setPositiveButton("Tamam", null)
                    .show();
            return;
        }

        String name = getDisplayName(selectedTree);
        new AlertDialog.Builder(this)
                .setTitle("Kalıcı olarak sil")
                .setMessage("Seçilen klasör:\n" + name + "\n\nKlasör ve içindeki her şey doğrudan silinecek. Çöp kutusuna gitmez.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("SİL", (d, w) -> deleteSelectedFolder())
                .show();
    }

    private void deleteSelectedFolder() {
        if (selectedTree == null) return;

        chooseButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Siliniyor...");

        new Thread(() -> {
            boolean ok = false;
            String error = null;

            try {
                String id = DocumentsContract.getTreeDocumentId(selectedTree);
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(selectedTree, id);
                ok = DocumentsContract.deleteDocument(getContentResolver(), doc);
            } catch (Exception e) {
                error = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            }

            final boolean result = ok;
            final String err = error;

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                chooseButton.setEnabled(true);

                if (result) {
                    statusText.setText("Klasör silindi.");
                    Toast.makeText(this, "Silme tamamlandı", Toast.LENGTH_LONG).show();
                } else {
                    statusText.setText("Klasör silinemedi.");
                    new AlertDialog.Builder(this)
                            .setTitle("Silinemedi")
                            .setMessage(err == null
                                    ? "Android dosya seçicisi bu klasörde doğrudan silmeye izin vermedi."
                                    : err)
                            .setPositiveButton("Tamam", null)
                            .show();
                }
            });
        }, "folder-delete").start();
    }

    private String getDisplayName(Uri treeUri) {
        try {
            String id = DocumentsContract.getTreeDocumentId(treeUri);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
            try (Cursor c = getContentResolver().query(
                    doc,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            }
        } catch (Exception ignored) {
        }
        return "Seçilen klasör";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
