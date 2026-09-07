package com.atmaca.files;

import static org.junit.Assert.assertEquals;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;

public class CsvCatalogParserTest {
    @Test public void quotedNamesParse() throws Exception {
        String csv = "Tur,Ad,Yol,Boyut,Tarih,Uzanti\nDOSYA,\"a,b.mp4\",/V/a-b.mp4,12,2026-08-30 01:00:00,.mp4\n";
        List<CatalogEntry> rows = CsvCatalogParser.parse(new StringReader(csv));
        assertEquals(1, rows.size());
        assertEquals("a,b.mp4", rows.get(0).name);
    }
}
