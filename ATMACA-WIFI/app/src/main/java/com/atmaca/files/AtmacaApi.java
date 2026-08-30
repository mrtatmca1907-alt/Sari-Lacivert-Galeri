package com.atmaca.files;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class AtmacaApi {
    private final String base;
    public AtmacaApi(String host) {
        String h = host == null ? "" : host.trim();
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://" + h;
        if (!h.matches(".*:\\d+$")) h += ":8765";
        base = h;
    }

    public boolean health() throws Exception {
        HttpURLConnection c = open("/health", "GET");
        try { return c.getResponseCode() == 200; } finally { c.disconnect(); }
    }

    public List<CatalogEntry> fetchCatalog() throws Exception {
        HttpURLConnection c = open("/catalog.csv", "GET");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            return CsvCatalogParser.parse(new InputStreamReader(new BufferedInputStream(c.getInputStream()), StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }

    public void download(String remotePath, OutputStream out) throws Exception {
        String encoded = URLEncoder.encode(PathUtil.normalize(remotePath), StandardCharsets.UTF_8.toString());
        HttpURLConnection c = open("/download?path=" + encoded, "GET");
        c.setReadTimeout(120000);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            try (BufferedInputStream in = new BufferedInputStream(c.getInputStream())) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                out.flush();
            }
        } finally { c.disconnect(); }
    }

    public void upload(String remoteDir, String fileName, InputStream in) throws Exception {
        String dir = URLEncoder.encode(PathUtil.normalize(remoteDir), StandardCharsets.UTF_8.toString());
        String name = URLEncoder.encode(fileName == null ? "video" : fileName, StandardCharsets.UTF_8.toString());
        HttpURLConnection c = open("/upload?dir=" + dir + "&name=" + name, "POST");
        c.setReadTimeout(120000);
        c.setRequestProperty("Content-Type", "application/octet-stream");
        c.setChunkedStreamingMode(1024 * 1024);
        try {
            try (OutputStream out = c.getOutputStream()) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                out.flush();
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        } finally { c.disconnect(); }
    }

    public void sendQueue(List<String> items) throws Exception {
        JSONArray arr = new JSONArray();
        for (String s : items) arr.put(new JSONObject(s));
        byte[] body = arr.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = open("/queue", "POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = c.getOutputStream()) { os.write(body); }
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                String msg = "";
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = br.readLine()) != null) msg += line;
                } catch (Exception ignored) {}
                throw new IllegalStateException("HTTP " + code + " " + msg);
            }
        } finally { c.disconnect(); }
    }

    private HttpURLConnection open(String path, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(3500);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        if ("POST".equals(method)) c.setDoOutput(true);
        return c;
    }
}
