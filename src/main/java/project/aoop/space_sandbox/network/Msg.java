package project.aoop.space_sandbox.network;

import java.util.Arrays;
import java.util.Locale;

/** One network message = one line of text: TYPE|field|field|... */
public final class Msg {

    public final MessageType type;
    private final String[] f;

    private Msg(MessageType type, String[] f) {
        this.type = type;
        this.f = f;
    }

    public static Msg of(MessageType type, Object... fields) {
        String[] s = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Object o = fields[i];
            // Locale.ROOT: always a dot as the decimal separator, whatever the PC's language
            s[i] = (o instanceof Double d) ? String.format(Locale.ROOT, "%.4f", d) : String.valueOf(o);
        }
        return new Msg(type, s);
    }

    public String encode() {
        return f.length == 0 ? type.name() : type.name() + "|" + String.join("|", f);
    }

    /** Returns null for anything that isn't a valid message. */
    public static Msg decode(String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.split("\\|", -1);
        try {
            return new Msg(MessageType.valueOf(parts[0]),
                    Arrays.copyOfRange(parts, 1, parts.length));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int     size()       { return f.length; }
    public String  s(int index) { return f[index]; }
    public double  d(int index) { return Double.parseDouble(f[index]); }
    public int     i(int index) { return Integer.parseInt(f[index]); }
    public boolean b(int index) { return Boolean.parseBoolean(f[index]); }
}