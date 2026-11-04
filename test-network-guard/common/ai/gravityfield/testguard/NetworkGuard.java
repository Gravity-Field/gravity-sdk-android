package ai.gravityfield.testguard;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Test transport boundary: external HTTP is sent only to a loopback deny proxy. */
public final class NetworkGuard {
    private static volatile ServerSocket proxy;
    private static volatile String currentTest = "unknown";
    private NetworkGuard() {}

    public static boolean loopback(String host) {
        if (host == null) return false;
        String value = host.toLowerCase(Locale.ROOT);
        if (value.equals("localhost") || value.equals("::1") || value.equals("[::1]") || value.equals("0:0:0:0:0:0:0:1")) return true;
        String[] parts = value.split("\\.");
        if (parts.length != 4 || !parts[0].equals("127")) return false;
        for (String part : parts) {
            if (part.isEmpty() || !part.matches("[0-9]{1,3}")) return false;
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }
    public static synchronized void install() {
        if (proxy == null) {
            try {
                proxy = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            } catch (IOException error) { throw new IllegalStateException("Cannot start test deny proxy", error); }
            Thread listener = new Thread(() -> {
                while (!proxy.isClosed()) {
                    try {
                        Socket socket = proxy.accept();
                        Thread worker = new Thread(() -> handle(socket), "test-deny-proxy-request");
                        worker.setDaemon(true); worker.start();
                    } catch (IOException error) { if (!proxy.isClosed()) record("proxy-error", "", error.getClass().getName()); }
                }
            }, "test-deny-proxy");
            listener.setDaemon(true); listener.start();
        }
        final int port = proxy.getLocalPort();
        System.setProperty("xreview.guard.proxyPort", String.valueOf(port));
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) {
                if (loopback(uri.getHost())) return Collections.singletonList(Proxy.NO_PROXY);
                bump("denied");
                record("external-http", uri.getHost() == null ? "[unparsed-network-host]" : uri.getHost(), uri.getScheme() + ":" + uri.getPort());
                return Collections.singletonList(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress("127.0.0.1", port)));
            }
            @Override public void connectFailed(URI uri, SocketAddress address, IOException error) {
                record("proxy-connect-failed", uri.getHost(), error.getClass().getName());
            }
        });
        System.setProperty("xreview.guard.installed", "true");
    }
    public static String fixtureUrl() { return "http://127.0.0.1:" + System.getProperty("xreview.guard.proxyPort"); }
    private static void bump(String kind) {
        synchronized (System.getProperties()) {
            String key = "xreview.guard.count." + kind;
            System.setProperty(key, String.valueOf(Integer.parseInt(System.getProperty(key, "0")) + 1));
        }
    }
    public static int deniedCount() { return Integer.parseInt(System.getProperty("xreview.guard.count.denied", "0")); }
    public static int fixtureCount() { return Integer.parseInt(System.getProperty("xreview.guard.count.fixture", "0")); }
    public static void currentTest(String name) { System.setProperty("xreview.guard.currentTest", name); }
    public static void noteTestClass(String name) {
        synchronized (System.getProperties()) {
            if (System.getProperty("xreview.guard.currentTest") == null) currentTest(name);
        }
    }
    public static boolean audit() { return "audit".equals(System.getProperty("xreview.guard.mode")); }
    public static void beforeTest(Class<?> testClass) {
        currentTest(testClass.getName());
        if (audit()) return;
        try {
            Class<?> logger = Class.forName("ai.gravityfield.gravity_sdk.utils.Logger", true, testClass.getClassLoader());
            Field flag = logger.getDeclaredField("errorReportingEnabled");
            flag.setAccessible(true); flag.setBoolean(null, false);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot disable automatic reports for test", error);
        }
    }
    public static void disableLogger() {
        try {
            Class<?> logger = Class.forName("ai.gravityfield.gravity_sdk.utils.Logger");
            Field flag = logger.getDeclaredField("errorReportingEnabled");
            flag.setAccessible(true); flag.setBoolean(null, false);
        } catch (ReflectiveOperationException error) {
            record("logger-guard-unavailable", "", error.getClass().getName());
        }
    }
    public static void beforeLogger(Object logger, String prefix, String message, Throwable error, boolean backend) {
        if (!audit()) return; // Tests may deliberately enable reports against their mock/local transport.
        try {
            Field flag = logger.getClass().getDeclaredField("errorReportingEnabled"); flag.setAccessible(true);
            if (backend && flag.getBoolean(null)) {
                String shape = prefix + ":" + (error == null ? "message-only" : error.getClass().getName());
                record("eligible-report", "sdk-sentry.gravityfield.ai", shape + ";messageSha256=" + hash(message));
            }
        } catch (ReflectiveOperationException failure) { record("audit-logger-unavailable", "", failure.getClass().getName()); }
    }
    public static void directDenied(String host, int port) {
        bump("denied"); record(port == -1 ? "denied-dns" : "denied-connect", host, String.valueOf(port));
    }
    public static void record(String kind, String host, String detail) {
        String path = System.getProperty("xreview.guard.file");
        if (path == null) return;
        String test = System.getProperty("xreview.guard.testClass", System.getProperty("xreview.guard.currentTest", currentTest));
        String stack = Arrays.toString(Thread.currentThread().getStackTrace());
        if (stack.length() > 6000) stack = stack.substring(0, 6000);
        String line = "{\"event\":" + quote(kind) + ",\"host\":" + quote(host) + ",\"testClass\":" + quote(test)
            + ",\"detail\":" + quote(detail) + ",\"thread\":" + quote(Thread.currentThread().getName())
            + ",\"stack\":" + quote(stack) + "}\n";
        synchronized (System.getProperties()) {
            try {
                File file = new File(path); File parent = file.getParentFile(); if (parent != null) parent.mkdirs();
                try (FileOutputStream out = new FileOutputStream(file, true)) { out.write(line.getBytes(StandardCharsets.UTF_8)); }
            } catch (IOException error) { throw new IllegalStateException("Cannot record test network denial", error); }
        }
    }
    private static String quote(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '\\' || c == '"') out.append('\\').append(c);
            else if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
    private static String hash(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (Exception error) { return "unavailable"; }
    }
    private static void handle(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            String first = reader.readLine(); if (first == null) return;
            String line; int length = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
            }
            boolean fixture = first.startsWith("POST /choose ");
            if (fixture) { for (int i = 0; i < length && reader.read() != -1; i++) {} bump("fixture"); }
            else {
                String[] parts = first.split(" ");
                String target = parts.length > 1 ? parts[1] : "";
                String host;
                try { host = URI.create((first.startsWith("CONNECT ") ? "https://" : "") + target).getHost(); }
                catch (IllegalArgumentException invalid) { host = "unparseable"; }
                record("proxy-denied", host, "deny-only; no upstream connection");
            }
            byte[] body = (fixture ? "{\"data\":[]}" : "test network denied").getBytes(StandardCharsets.UTF_8);
            OutputStream out = client.getOutputStream();
            out.write(((fixture ? "HTTP/1.1 200 OK" : "HTTP/1.1 503 Test Network Denied")
                + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body); out.flush();
        } catch (Exception error) { record("proxy-handler", "", error.getClass().getName()); }
    }
}
