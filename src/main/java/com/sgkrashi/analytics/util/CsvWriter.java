package com.sgkrashi.analytics.util;

import java.util.List;

/**
 * Hand-rolled, deliberately minimal — no OpenCSV dependency for what's just
 * a header row plus N data rows of already-formatted strings. Only quotes a
 * field when it actually needs it (contains a comma, quote, or newline),
 * per RFC 4180's escaping rule (double any embedded quote).
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static String write(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(toLine(headers));
        for (List<String> row : rows) {
            sb.append(toLine(row));
        }
        return sb.toString();
    }

    private static String toLine(List<String> fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(fields.get(i)));
        }
        line.append("\r\n");
        return line.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
