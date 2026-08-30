package com.atmaca.video50;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class VideoScanner {
    private VideoScanner() {}

    public static List<VideoItem> scanMediaStore(Context context) {
        LinkedHashMap<String, VideoItem> out = new LinkedHashMap<>();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.RELATIVE_PATH
        };
        try (Cursor c = context.getContentResolver().query(collection, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " ASC")) {
            if (c == null) return new ArrayList<>();
            int idCol = c.getColumnIndex(MediaStore.Video.Media._ID);
            int nameCol = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
            int sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE);
            int dataCol = c.getColumnIndex(MediaStore.Video.Media.DATA);
            int relCol = c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH);
            while (c.moveToNext()) {
                long id = idCol >= 0 ? c.getLong(idCol) : -1;
                String name = nameCol >= 0 ? c.getString(nameCol) : "video";
                long size = sizeCol >= 0 ? c.getLong(sizeCol) : 0L;
                String filePath = dataCol >= 0 ? c.getString(dataCol) : null;
                String rel = relCol >= 0 ? c.getString(relCol) : null;
                if (DedupePolicy.isGeneratedMoviesPath(rel == null ? "" : rel)) continue;
                Uri uri = id >= 0 ? ContentUris.withAppendedId(collection, id) : collection;
                VideoItem item = new VideoItem(uri, name, size, filePath);
                out.putIfAbsent(item.key(), item);
            }
        }
        return new ArrayList<>(out.values());
    }

    public static List<VideoItem> scanTree(Context context, Uri treeUri) {
        LinkedHashMap<String, VideoItem> out = new LinkedHashMap<>();
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root != null) walk(root, out);
        return new ArrayList<>(out.values());
    }

    private static void walk(DocumentFile node, LinkedHashMap<String, VideoItem> out) {
        if (node.isDirectory()) {
            String name = node.getName();
            if (name != null && name.matches("(?i)Video \\d+")) return;
            for (DocumentFile child : node.listFiles()) walk(child, out);
            return;
        }
        String type = node.getType();
        String name = node.getName();
        boolean video = type != null && type.startsWith("video/");
        if (!video && name != null) {
            String n = name.toLowerCase();
            video = n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov") || n.endsWith(".webm") || n.endsWith(".3gp") || n.endsWith(".m4v") || n.endsWith(".ts");
        }
        if (!video) return;
        VideoItem item = new VideoItem(node.getUri(), name, node.length(), null);
        out.putIfAbsent(item.key(), item);
    }
}
