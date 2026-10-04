package com.atm.gateway.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Lets mobile clients find this gateway without ever hard-coding an IP address.
 *
 * <p>The gateway's LAN address is assigned by DHCP, so it changes whenever the
 * laptop rejoins the network. Instead of asking users to edit and rebuild the
 * app every time, the gateway answers a UDP probe on
 * {@code discovery.port} with the base URL it is reachable on <em>right now</em>.
 *
 * <p>The advertised host is resolved on every request (never cached) and is
 * picked from the server interface that shares a subnet with the requester, so
 * multi-homed machines advertise the address the phone can actually reach.
 *
 * <p>Protocol: client sends the ASCII token {@code UBX_DISCOVER} to
 * {@code 255.255.255.255:45560}; the gateway replies with a JSON document
 * <pre>{"service":"ubx-api-gateway","version":"...","host":"...","port":8080,"baseUrl":"http://.../api/v1"}</pre>
 */
@Component
public class DiscoveryResponder implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryResponder.class);
    private static final String MAGIC = "UBX_DISCOVER";
    private static final int BUFFER_SIZE = 1024;
    private static final int RECEIVE_TIMEOUT_MS = 1000;

    private final boolean enabled;
    private final int discoveryPort;
    private final int httpPort;
    private final String apiPath;
    private final String explicitHost;
    private final String version;

    private volatile DatagramSocket socket;
    private volatile Thread listener;
    private volatile boolean running;

    public DiscoveryResponder(@Value("${discovery.enabled:true}") boolean enabled,
                              @Value("${discovery.port:45560}") int discoveryPort,
                              @Value("${server.port:8080}") int httpPort,
                              @Value("${server.servlet.context-path:}") String contextPath,
                              @Value("${discovery.advertise-host:}") String explicitHost,
                              @Value("${app.version:2.0.0}") String version) {
        this.enabled = enabled;
        this.discoveryPort = discoveryPort;
        this.httpPort = httpPort;
        this.apiPath = normalizeContextPath(contextPath) + "/api/v1";
        this.explicitHost = explicitHost;
        this.version = version;
    }

    // ---------- SmartLifecycle ----------

    @Override
    public void start() {
        if (!enabled || running) {
            return;
        }
        try {
            DatagramSocket s = new DatagramSocket(new InetSocketAddress(discoveryPort));
            s.setBroadcast(true);
            s.setSoTimeout(RECEIVE_TIMEOUT_MS);
            socket = s;
            running = true;
            listener = new Thread(this::listen, "gateway-discovery-responder");
            listener.setDaemon(true);
            listener.start();
            log.info("=== Discovery responder on UDP {} advertising {} ===",
                    discoveryPort, buildBaseUrl(preferredAdvertisedHost(null)));
        } catch (SocketException e) {
            log.warn("Discovery responder disabled - cannot bind UDP {}: {}", discoveryPort, e.getMessage());
        }
    }

    @Override
    public void stop() {
        running = false;
        DatagramSocket s = socket;
        if (s != null) {
            s.close();
        }
        Thread t = listener;
        if (t != null) {
            t.interrupt();
        }
        socket = null;
        listener = null;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Start after Tomcat, stop before it - the advertised port must be live.
        return Integer.MAX_VALUE - 100;
    }

    // ---------- Request handling ----------

    private void listen() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket probe = new DatagramPacket(buffer, buffer.length);
                socket.receive(probe);
                handle(probe);
            } catch (SocketTimeoutException ignored) {
                // Idle tick - loop back for the next probe.
            } catch (Exception e) {
                if (running) {
                    log.debug("Discovery probe failed: {}", e.toString());
                }
            }
        }
    }

    private void handle(DatagramPacket probe) {
        String request = new String(probe.getData(), 0, probe.getLength(), StandardCharsets.UTF_8).trim();
        if (!request.startsWith(MAGIC)) {
            return;
        }
        InetAddress requester = probe.getAddress();
        String host = advertisedHostFor(requester);
        String body = "{"
                + "\"service\":\"ubx-api-gateway\""
                + ",\"version\":\"" + version + "\""
                + ",\"host\":\"" + host + "\""
                + ",\"port\":" + httpPort
                + ",\"baseUrl\":\"" + buildBaseUrl(host) + "\""
                + "}";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(payload, payload.length, requester, probe.getPort()));
            log.debug("Discovery probe from {} answered with {}", requester, buildBaseUrl(host));
        } catch (Exception e) {
            log.debug("Failed to answer discovery probe from {}: {}", requester, e.toString());
        }
    }

    // ---------- Address selection ----------

    /**
     * Prefers an explicit override, then a LAN address on the requester's
     * subnet, then any LAN address. Resolved per call so DHCP changes are
     * picked up without a restart.
     */
    private String advertisedHostFor(InetAddress requester) {
        if (explicitHost != null && !explicitHost.isBlank()) {
            return explicitHost.trim();
        }
        List<String> candidates = lanIpv4Addresses();
        if (requester != null && candidates.size() > 1) {
            String requesterPrefix = subnetPrefix(requester.getHostAddress());
            for (String candidate : candidates) {
                if (requesterPrefix.equals(subnetPrefix(candidate))) {
                    return candidate;
                }
            }
        }
        return preferredAdvertisedHost(requester);
    }

    private String preferredAdvertisedHost(InetAddress requester) {
        List<String> candidates = lanIpv4Addresses();
        return candidates.isEmpty() ? "127.0.0.1" : candidates.get(0);
    }

    private String buildBaseUrl(String host) {
        return "http://" + host + ":" + httpPort + apiPath;
    }

    private List<String> lanIpv4Addresses() {
        List<String> found = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return found;
            }
            for (NetworkInterface nic : Collections.list(interfaces)) {
                try {
                    if (!nic.isUp() || nic.isLoopback()) {
                        continue;
                    }
                } catch (SocketException e) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.isAnyLocalAddress()
                            && address.isSiteLocalAddress()
                            && !found.contains(address.getHostAddress())) {
                        found.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.debug("Cannot enumerate network interfaces: {}", e.toString());
        }
        return found;
    }

    private static String subnetPrefix(String ipv4) {
        String[] octets = ipv4.split("\\.");
        return octets.length == 4 ? octets[0] + "." + octets[1] + "." + octets[2] : ipv4;
    }

    private static String normalizeContextPath(String contextPath) {
        if (contextPath == null) {
            return "";
        }
        String trimmed = contextPath.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return "";
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
