package com.atmaca.files;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public final class CsvCatalogParser {
    private CsvCatalogParser() {}

    public static List<CatalogEntry> parse(Reader reader) throws IOException {
        ArrayList<CatalogEntry> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(reader, 64 * 1024)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                List<String> c = split(line);
                if (c.size() < 6) continue;
                long size = 0L;
                try { size = Long.parseLong(c.get(3).trim()); } catch (Exception ignored) {}
                out.add(new CatalogEntry(c.get(0), c.get(1), c.get(2), size, c.get(4), c.get(5)));
            }
        }
        return out;
    }

    static List<String> split(String line) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                out.add(cell.toString()); cell.setLength(0);
            } else cell.append(ch);
        }
        out.add(cell.toString());
        return out;
    }
}
