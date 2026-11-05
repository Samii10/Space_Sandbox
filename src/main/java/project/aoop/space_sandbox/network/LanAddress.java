package project.aoop.space_sandbox.network;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/** Finds this computer's address on the local network (Wi-Fi / hotspot / Ethernet). */
public final class LanAddress {

    private static final String[] VIRTUAL_HINTS = {
            "virtual", "vmware", "vbox", "docker", "hyper-v", "vethernet",
            "wsl", "loopback", "pseudo", "tailscale", "zerotier", "bluetooth"
    };

    private LanAddress() {}

    /** Returns e.g. "192.168.1.5", or null if there is no network. */
    public static String find() {
        // 1) ask the OS which address it would use to reach another network (nothing is sent)
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress a = s.getLocalAddress();
            if (isUsable(a)) return a.getHostAddress();
        } catch (Exception ignored) {
        }

        // 2) scan the network cards (works with no internet, e.g. a phone hotspot without data)
        String fallback = null;
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            if (en == null) return null;
            for (NetworkInterface ni : Collections.list(en)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || looksVirtual(ni)) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!isUsable(a)) continue;
                    if (a.isSiteLocalAddress()) return a.getHostAddress();
                    if (fallback == null) fallback = a.getHostAddress();
                }
            }
        } catch (SocketException ignored) {
        }
        return fallback;
    }

    private static boolean isUsable(InetAddress a) {
        return a instanceof Inet4Address
                && !a.isLoopbackAddress()
                && !a.isAnyLocalAddress()
                && !a.isLinkLocalAddress();
    }

    private static boolean looksVirtual(NetworkInterface ni) {
        String name = (ni.getName() + " " + ni.getDisplayName()).toLowerCase(Locale.ROOT);
        for (String hint : VIRTUAL_HINTS) if (name.contains(hint)) return true;
        return false;
    }
}