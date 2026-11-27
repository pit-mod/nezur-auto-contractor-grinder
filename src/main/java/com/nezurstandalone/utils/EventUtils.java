package com.nezurstandalone.utils;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class EventUtils {
    static final int MAX_BODY_BYTES = 262144;
    public static String fetchEvents() {
        try {
            String response=readResponse((HttpURLConnection)new URL("https://raw.githubusercontent.com/BrookeAFK/brookeafk-api/main/events.js").openConnection());
            return response;
        } catch (IOException failure) {
            java.util.logging.Logger.getLogger(EventUtils.class.getName()).warning("Event refresh failed: " + failure.getClass().getSimpleName());
            return null;
        }
    }
    static String readResponse(HttpURLConnection connection) throws IOException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Event HTTP status rejected");
            if (connection.getContentLengthLong() > MAX_BODY_BYTES) throw new IOException("Event body too large");
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while (true) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new java.net.SocketTimeoutException("Event request deadline");
                    connection.setReadTimeout((int)Math.max(1, Math.min(5000, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining))));
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    count = stream.read(buffer);
                    if (count == -1) break;
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    if (System.nanoTime() > deadline) throw new java.net.SocketTimeoutException("Event request deadline");
                    if (out.size() + count > MAX_BODY_BYTES) throw new IOException("Event body too large");
                    out.write(buffer, 0, count);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
