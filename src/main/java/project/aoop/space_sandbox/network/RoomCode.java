package project.aoop.space_sandbox.network;

import java.util.Locale;

/**
 * Turns the host's LAN address into a short room code and back, e.g. 192.168.1.5 -> "ZAQD-C1H".
 * The alphabet has no 0/O/1/I so codes are easy to read aloud.
 */
public final class RoomCode {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // 32 symbols

    private RoomCode() {}

    public static String fromIp(String ipv4) {
        String[] p = ipv4.split("\\.");
        if (p.length != 4) return null;
        long v = 0;
        for (String part : p) v = (v << 8) | (Integer.parseInt(part) & 0xFF);

        char[] c = new char[7];                       // 7 x 5 bits = 35 bits >= 32
        for (int i = 6; i >= 0; i--) {
            c[i] = ALPHABET.charAt((int) (v & 31));
            v >>= 5;
        }
        return new String(c, 0, 4) + "-" + new String(c, 4, 3);
    }

    /** Returns the IPv4 address, or null if the code is not valid. */
    public static String toIp(String code) {
        if (code == null) return null;
        String s = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (s.length() != 7) return null;

        long v = 0;
        for (int i = 0; i < 7; i++) {
            int idx = ALPHABET.indexOf(s.charAt(i));
            if (idx < 0) return null;
            v = (v << 5) | idx;
        }
        if (v > 0xFFFFFFFFL) return null;
        return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }
}