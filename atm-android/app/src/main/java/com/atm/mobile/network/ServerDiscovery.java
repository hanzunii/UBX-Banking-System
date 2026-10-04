package com.atm.mobile.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zero-configuration discovery for the UBX API Gateway.
 *
 * <p>The gateway runs on a laptop whose LAN address is handed out by DHCP, so
 * any address baked into the app goes stale the next time the laptop rejoins
 * the network. Instead of compiling an IP in, the app finds the gateway at
 * runtime with three strategies, cheapest first:
 *
 * <ol>
 *   <li><b>UDP broadcast</b> - probe {@code 255.255.255.255:45560}; the gateway
 *       replies with the URL it is reachable on at this instant. Works in
 *       under a second.</li>
 *   <li><b>Emulator loopback</b> - {@code 10.0.2.2} when running on a stock
 *       emulator, which reaches the host machine's localhost.</li>
 *   <li><b>Subnet sweep</b> - HTTP probe of every host on the phone's own
 *       /24. Covers Wi-Fi networks that drop broadcast traffic, at the cost of
 *       a few seconds.</li>
 * </ol>
 *
 * <p>Every candidate is confirmed with {@code GET /api/v1/health} before it is
 * accepted, so a reply from an unrelated device on the same network is ignored.
 * See {@link ApiClient#ensureServer} for the cached-URL fast path.
 */
public final class ServerDiscovery {

    public static final int DISCOVERY_PORT = 45560;
    public static final int DEFAULT_HTTP_PORT = 8080;
    public static final String API_PATH = "/api/v1";
    private static final String MAGIC = "UBX_DISCOVER";

    private static final int BROADCAST_TIMEOUT_MS = 2500;
    private static final int PROBE_CONNECT_TIMEOUT_MS = 1200;
    private static final int PROBE_READ_TIMEOUT_MS = 2000;
    private static final int SUBNET_SWEEP_TIMEOUT_S = 6;
    private static final int SUBNET_THREADS = 32;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKERS = Executors.newCachedThreadPool();

    public interface Callback {
        void onFound(String baseUrl);

        void onFailed(String reason);
    }

    private ServerDiscovery() {
    }

    /**
     * Runs the discovery chain off the calling thread and reports back on the
     * main thread.
     *
     * @param context used to read the active network's own address; may be null
     * @param httpPort gateway port to try when falling back to a subnet sweep
     */
    public static void discover(Context context, int httpPort, Callback callback) {
        WORKERS.execute(() -> {
            String found = null;
            String failure = "no candidate answered";
            try {
                found = probeBroadcast();
            } catch (Exception e) {
                failure = "broadcast failed: " + describe(e);
            }
            if (found == null) {
                try {
                    found = probeEmulatorLoopback(httpPort);
                } catch (Exception e) {
                    failure = "emulator probe failed: " + describe(e);
                }
            }
            if (found == null) {
                try {
                    found = sweepSubnet(context, httpPort);
                } catch (Exception e) {
                    failure = "subnet sweep failed: " + describe(e);
                }
            }
            if (found == null) {
                String reason = failure;
                MAIN.post(() -> callback.onFailed(reason));
            } else {
                String baseUrl = found;
                MAIN.post(() -> callback.onFound(baseUrl));
            }
        });
    }

    // ---------- Strategy 1: UDP broadcast ----------

    private static String probeBroadcast() throws Exception {
        List<InetAddress> targets = broadcastTargets();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(BROADCAST_TIMEOUT_MS);
            byte[] probe = MAGIC.getBytes(StandardCharsets.UTF_8);
            for (InetAddress target : targets) {
                try {
                    socket.send(new DatagramPacket(probe, probe.length, target, DISCOVERY_PORT));
                } catch (Exception ignored) {
                    // One unreachable broadcast address must not abort the probe.
                }
            }
            long deadline = System.currentTimeMillis() + BROADCAST_TIMEOUT_MS;
            byte[] buffer = new byte[1024];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(response);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String baseUrl = baseUrlFrom(new String(
                        response.getData(), 0, response.getLength(), StandardCharsets.UTF_8));
                if (baseUrl != null && isReachable(baseUrl)) {
                    return baseUrl;
                }
            }
        }
        return null;
    }

    private static List<InetAddress> broadcastTargets() {
        Set<InetAddress> targets = new LinkedHashSet<>();
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {
            // Keep going - the directed broadcasts below may still work.
        }
        for (String ip : interfaceIpv4Addresses()) {
            String[] octets = ip.split("\\.");
            if (octets.length != 4) {
                continue;
            }
            try {
                targets.add(InetAddress.getByName(
                        octets[0] + "." + octets[1] + "." + octets[2] + ".255"));
            } catch (Exception ignored) {
                // Malformed address - skip it.
            }
        }
        return new ArrayList<>(targets);
    }

    // ---------- Strategy 2: emulator host loopback ----------

    private static String probeEmulatorLoopback(int httpPort) {
        if (!isEmulator()) {
            return null;
        }
        String baseUrl = "http://10.0.2.2:" + httpPort + API_PATH;
        return isReachable(baseUrl) ? baseUrl : null;
    }

    private static boolean isEmulator() {
        return Build.FINGERPRINT != null
                && (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.contains("emulator")
                || Build.FINGERPRINT.contains("vbox")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for")
                || "google_sdk".equals(Build.PRODUCT));
    }

    // ---------- Strategy 3: subnet sweep ----------

    private static String sweepSubnet(Context context, int httpPort) throws InterruptedException {
        List<String> local = localIpv4Addresses(context);
        if (local.isEmpty()) {
            return null;
        }
        Set<String> prefixes = new LinkedHashSet<>();
        for (String ip : local) {
            String[] octets = ip.split("\\.");
            if (octets.length == 4) {
                prefixes.add(octets[0] + "." + octets[1] + "." + octets[2]);
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(SUBNET_THREADS);
        try {
            AtomicReference<String> found = new AtomicReference<>(null);
            CountDownLatch latch = new CountDownLatch(prefixes.size() * 254);
            for (String prefix : prefixes) {
                for (int host = 1; host <= 254; host++) {
                    String candidate = "http://" + prefix + "." + host + ":" + httpPort + API_PATH;
                    pool.execute(() -> {
                        try {
                            if (found.get() == null && isReachable(candidate)) {
                                found.compareAndSet(null, candidate);
                            }
                        } catch (Exception ignored) {
                            // Unreachable host - the common case during a sweep.
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }
            latch.await(SUBNET_SWEEP_TIMEOUT_S, TimeUnit.SECONDS);
            return found.get();
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- Validation ----------

    /**
     * Confirms a base URL really is the API gateway by requiring
     * {@code GET <baseUrl>/health} to answer 200 with a status document. A 404
     * means something else is listening on that port and is rejected.
     */
    public static boolean isReachable(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return false;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + "/health").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(PROBE_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(PROBE_READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                return false;
            }
            try (InputStream in = conn.getInputStream()) {
                return new JSONObject(readAll(in)).has("status");
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Extracts a normalised base URL from a discovery reply, or null when the
     * payload is not a gateway announcement.
     */
    private static String baseUrlFrom(String payload) {
        try {
            String baseUrl = new JSONObject(payload).optString("baseUrl", "");
            if (baseUrl.isEmpty()) {
                return null;
            }
            return baseUrl.replaceAll("/+$", "");
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Address helpers ----------

    /**
     * The phone's own IPv4 addresses, taken from the active network so the
     * sweep targets the subnet the phone is actually on. Neither lookup needs
     * a location permission.
     */
    public static List<String> localIpv4Addresses(Context context) {
        Set<String> found = new LinkedHashSet<>();
        if (context != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager)
                        context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    Network active = cm.getActiveNetwork();
                    LinkProperties properties = active != null ? cm.getLinkProperties(active) : null;
                    if (properties != null) {
                        for (LinkAddress link : properties.getLinkAddresses()) {
                            InetAddress address = link.getAddress();
                            if (isUsableIpv4(address)) {
                                found.add(address.getHostAddress());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fall through to interface enumeration.
            }
        }
        if (found.isEmpty()) {
            found.addAll(interfaceIpv4Addresses());
        }
        return new ArrayList<>(found);
    }

    private static List<String> interfaceIpv4Addresses() {
        List<String> found = new ArrayList<>();
        try {
            Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return found;
            }
            for (java.net.NetworkInterface nic : Collections.list(interfaces)) {
                try {
                    if (!nic.isUp() || nic.isLoopback()) {
                        continue;
                    }
                } catch (Exception e) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (isUsableIpv4(address) && !found.contains(address.getHostAddress())) {
                        found.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            // No usable addresses - the sweep is skipped.
        }
        return found;
    }

    private static boolean isUsableIpv4(InetAddress address) {
        return address instanceof Inet4Address
                && !address.isLoopbackAddress()
                && !address.isAnyLocalAddress()
                && !address.isLinkLocalAddress();
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
