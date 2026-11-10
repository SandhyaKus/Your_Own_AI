package yourownai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tiny JSON helpers (no external libraries needed). */
public final class Json {
    private Json() {}

    /** Escape (without surrounding quotes). */
    public static String esc(String s) {
        StringBuilder o = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> o.append("\\\"");
                case '\\' -> o.append("\\\\");
                case '\n' -> o.append("\\n");
                case '\r' -> o.append("\\r");
                case '\t' -> o.append("\\t");
                default -> {
                    if (c < 0x20) o.append(String.format("\\u%04x", (int) c));
                    else o.append(c);
                }
            }
        }
        return o.toString();
    }

    /** JSON string literal. */
    public static String str(String s) { return "\"" + esc(s) + "\""; }

    public static String fixed(float v, int prec) { return String.format(Locale.ROOT, "%." + prec + "f", v); }

    public static String vec(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(fixed(v[i], 4));
        }
        return sb.append(']').toString();
    }

    /** Parse "0.1,0.2,0.3" - silently skips bad tokens. */
    public static float[] parseVec(String s) {
        List<Float> out = new ArrayList<>();
        if (s != null) {
            for (String t : s.split(",", -1)) {
                try { out.add(Float.parseFloat(t.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        float[] r = new float[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    /** Extract a string field from a JSON body (handles escape sequences, incl. \\uXXXX). */
    public static String extractStr(String body, String key) {
        int p = body.indexOf("\"" + key + "\"");
        if (p < 0) return "";
        int colon = body.indexOf(':', p);
        if (colon < 0) return "";
        p = colon + 1;
        while (p < body.length() && (body.charAt(p) == ' ' || body.charAt(p) == '\t'
                || body.charAt(p) == '\n' || body.charAt(p) == '\r')) p++;
        if (p >= body.length() || body.charAt(p) != '"') return "";
        p++;
        StringBuilder r = new StringBuilder();
        while (p < body.length()) {
            char c = body.charAt(p);
            if (c == '"') break;
            if (c == '\\' && p + 1 < body.length()) {
                p++;
                char e = body.charAt(p);
                switch (e) {
                    case '"'  -> r.append('"');
                    case '\\' -> r.append('\\');
                    case '/'  -> r.append('/');
                    case 'n'  -> r.append('\n');
                    case 'r'  -> r.append('\r');
                    case 't'  -> r.append('\t');
                    case 'b'  -> r.append('\b');
                    case 'f'  -> r.append('\f');
                    case 'u'  -> {
                        if (p + 4 < body.length()) {
                            try {
                                r.append((char) Integer.parseInt(body.substring(p + 1, p + 5), 16));
                                p += 4;
                            } catch (NumberFormatException ex) { r.append('u'); }
                        } else r.append('u');
                    }
                    default -> r.append(e);
                }
            } else {
                r.append(c);
            }
            p++;
        }
        return r.toString();
    }

    /** Extract an integer field from a JSON body. */
    public static int extractInt(String body, String key, int def) {
        int p = body.indexOf("\"" + key + "\"");
        if (p < 0) return def;
        int colon = body.indexOf(':', p);
        if (colon < 0) return def;
        p = colon + 1;
        while (p < body.length() && Character.isWhitespace(body.charAt(p))) p++;
        int s = p;
        if (p < body.length() && (body.charAt(p) == '-' || body.charAt(p) == '+')) p++;
        while (p < body.length() && Character.isDigit(body.charAt(p))) p++;
        try { return Integer.parseInt(body.substring(s, p)); } catch (NumberFormatException e) { return def; }
    }

    /** Extract the first [ ... ] array of floats following the given key. */
    public static float[] extractArr(String body, String key) {
        int p = body.indexOf("\"" + key + "\"");
        if (p < 0) return new float[0];
        p = body.indexOf('[', p);
        if (p < 0) return new float[0];
        int e = body.indexOf(']', p);
        if (e < 0) return new float[0];
        return parseVec(body.substring(p + 1, e));
    }
}
