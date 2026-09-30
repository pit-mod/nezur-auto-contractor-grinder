package com.nezurstandalone.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

public final class PitMartService {

    private static final String SALT = "pittrader-hub-client-salt-2024";
    private static volatile long rateLimitedUntil;
    private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final Map<String, CachedHistory> CACHE = new ConcurrentHashMap<>();

    public enum FetchStatus {
        OK,
        NOT_INDEXED,
        BLOCKED,
        ERROR
    }

    private static final Object[] LOCKS = new Object[32];
    static { for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new Object(); }

    private PitMartService() {
    }

    public static final class OwnerRecord {
        public final String uuid;
        public final String username;
        public final long seenAtMs;
        public final boolean currentHolder;

        public OwnerRecord(String uuid, String username, long seenAtMs) {
            this(uuid, username, seenAtMs, false);
        }

        public OwnerRecord(String uuid, String username, long seenAtMs, boolean currentHolder) {
            this.currentHolder = currentHolder;
            this.uuid = uuid;
            this.username = username;
            this.seenAtMs = seenAtMs;
        }
    }

    public static final class OwnerHistoryResult {
        public final String itemId;
        public final int nonce;
        public final String itemName;
        public final List<OwnerRecord> owners;
        public final int totalOwners;
        public final FetchStatus status;
        public final boolean duped;

        public OwnerHistoryResult(String itemId, int nonce, String itemName, List<OwnerRecord> owners,
                                  FetchStatus status) {
            this(itemId, nonce, itemName, owners, owners != null ? owners.size() : 0, status);
        }

        public OwnerHistoryResult(String itemId, int nonce, String itemName, List<OwnerRecord> owners,
                                  int totalOwners, FetchStatus status) {
            this(itemId, nonce, itemName, owners, totalOwners, status, false);
        }

        public OwnerHistoryResult(String itemId, int nonce, String itemName, List<OwnerRecord> owners,
                                  int totalOwners, FetchStatus status, boolean duped) {
            this.duped = duped;
            this.itemId = itemId;
            this.nonce = nonce;
            this.itemName = itemName;
            this.owners = owners;
            this.totalOwners = totalOwners;
            this.status = status;
        }
    }

    private static final class CachedHistory {
        final OwnerHistoryResult result;
        final long fetchedAt;

        CachedHistory(OwnerHistoryResult result, long fetchedAt) {
            this.result = result;
            this.fetchedAt = fetchedAt;
        }
    }

    public static OwnerHistoryResult peekCachedByNonce(int nonce) {
        if (nonce == 0) {
            return null;
        }
        return getCached("nonce:" + nonce);
    }

    public static OwnerHistoryResult fetchByNonce(int nonce) {
        synchronized (LOCKS[(nonce & Integer.MAX_VALUE) % LOCKS.length]) {
            return fetchLocked(nonce);
        }
    }

    private static OwnerHistoryResult fetchLocked(int nonce) {
        if (nonce == 0) {
            return null;
        }
        String cacheKey = "nonce:" + nonce;
        OwnerHistoryResult cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (System.currentTimeMillis() < rateLimitedUntil) {
            return new OwnerHistoryResult(null, nonce, "Mystic Item", Collections.emptyList(), FetchStatus.BLOCKED);
        }
        OwnerHistoryResult result = fetchFromPitPalOwners(nonce);
        if (result != null) {
            putCache(cacheKey, result);
        }
        return result;
    }

    public static OwnerHistoryResult fetchByNonceFast(int nonce) {
        return fetchByNonce(nonce);
    }

    public static OwnerHistoryResult withResolvedUsernames(OwnerHistoryResult result) {
        return result;
    }

    private static String generateClientToken() {
        try {
            long timeWindow = System.currentTimeMillis() / 120000L;
            String message = String.valueOf(timeWindow);

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(SALT.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secretKey);

            byte[] bytes = sha256_HMAC.doFinal(message.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static OwnerHistoryResult fetchFromPitPalOwners(int nonce) {
        try {
            String token = generateClientToken();
            URL url = new URL(VpsConfig.getPitPalEndpoint("item-history/nonce/" + nonce + "/owners"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Client-Token", token);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            try {
                int status = conn.getResponseCode();
                if (status == 429) {
                    long seconds = 30;
                    try { seconds = Math.max(1, Math.min(300, Long.parseLong(conn.getHeaderField("Retry-After")))); }
                    catch (RuntimeException ignored) { }
                    rateLimitedUntil = System.currentTimeMillis() + seconds * 1000L;
                }
                if (status != 200) {
                    return new OwnerHistoryResult(null, nonce, "Mystic Item", Collections.emptyList(),
                            status == 404 ? FetchStatus.NOT_INDEXED :
                            (status == 401 || status == 403 || status == 429) ? FetchStatus.BLOCKED : FetchStatus.ERROR);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                return parseOwners(nonce, new JsonParser().parse(sb.toString()));
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return new OwnerHistoryResult(null, nonce, "Mystic Item", Collections.emptyList(), FetchStatus.ERROR);
        }
    }

    static OwnerHistoryResult parseOwners(int nonce, JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) {
            return new OwnerHistoryResult(null, nonce, "Mystic Item", Collections.emptyList(), FetchStatus.ERROR);
        }
        JsonObject root = payload.getAsJsonObject();
        if (root.has("data") && root.get("data").isJsonObject()) root = root.getAsJsonObject("data");
        if (!root.has("owners") || !root.get("owners").isJsonArray()) {
            return new OwnerHistoryResult(null, nonce, "Mystic Item", Collections.emptyList(), FetchStatus.ERROR);
        }
        List<OwnerRecord> records = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("owners")) {
            if (!element.isJsonObject()) continue;
            JsonObject owner = element.getAsJsonObject();
            String uuid = string(owner, "ownerUuid", "uuid");
            String name = string(owner, "username", "ign", "name");
            if (uuid == null && name == null) continue;
            long seen = parseTime(string(owner, "lastSeen", "timestamp"));
            boolean current = owner.has("isCurrentHolder") && owner.get("isCurrentHolder").isJsonPrimitive()
                    && "true".equalsIgnoreCase(owner.get("isCurrentHolder").getAsString());
            records.add(new OwnerRecord(uuid, name, seen, current));
        }
        records.sort((a, b) -> Long.compare(b.seenAtMs, a.seenAtMs));
        return new OwnerHistoryResult(String.valueOf(nonce), nonce, "Mystic Item",
                Collections.unmodifiableList(records), records.size(), records.isEmpty() ? FetchStatus.NOT_INDEXED : FetchStatus.OK,
                root.has("duped") && root.get("duped").isJsonPrimitive() && "true".equalsIgnoreCase(root.get("duped").getAsString()));
    }

    private static String string(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && !value.getAsString().trim().isEmpty()) return value.getAsString();
        }
        return null;
    }

    private static long parseTime(String value) {
        if (value == null) return 0L;
        try {
            double numeric = Double.parseDouble(value);
            return (long) (numeric < 10000000000L ? numeric * 1000 : numeric);
        } catch (NumberFormatException ignored) { }
        try { return java.time.Instant.parse(value).toEpochMilli(); }
        catch (java.time.format.DateTimeParseException ignored) { return 0L; }
    }

    /** Historical owners alone are not evidence of the current player's identity. */
    public static String currentOwner(OwnerHistoryResult result) {
        if (result == null || result.status != FetchStatus.OK || result.duped) return null;
        String candidate = null;
        for (OwnerRecord owner : result.owners) {
            if (!owner.currentHolder) continue;
            if (owner.username == null || !owner.username.matches("[A-Za-z0-9_]{1,16}")) return null;
            if (candidate != null && !candidate.equalsIgnoreCase(owner.username)) return null;
            candidate = owner.username;
        }
        return candidate;
    }

    public static String formatOwnerLine(OwnerRecord record, int index) {
        String name = record.username != null ? record.username : record.uuid;
        if (record.seenAtMs > 0L) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            return "\u00a77" + (index + 1) + ". \u00a7f" + name + " \u00a78(" + fmt.format(new Date(record.seenAtMs)) + ")";
        }
        return "\u00a77" + (index + 1) + ". \u00a7f" + name;
    }

    public static void appendOwnerHistoryTooltip(List<String> tooltip, OwnerHistoryResult result, boolean loading) {
        tooltip.add("");
        tooltip.add("\u00a78\u00a7m--------------------");
        tooltip.add("\u00a7bOwner History \u00a77(PitPal)");
        if (loading && result == null) {
            tooltip.add("\u00a77Loading...");
            return;
        }
        if (result == null || result.owners.isEmpty()) {
            if (result != null && result.status == FetchStatus.BLOCKED) {
                tooltip.add("\u00a7cPitPal denied or rate-limited the request; try later");
            } else if (result != null && result.status == FetchStatus.ERROR) {
                tooltip.add("\u00a7cCould not reach PitPal database");
            } else {
                tooltip.add("\u00a7cItem not indexed on PitPal yet");
            }
            return;
        }
        List<OwnerRecord> owners = result.owners;
        int limit = Math.min(20, owners.size());
        for (int i = 0; i < limit; i++) {
            tooltip.add(formatOwnerLine(owners.get(i), i));
        }
        if (owners.size() > 20) {
            tooltip.add("\u00a78... and " + (owners.size() - 20) + " older owners");
        }
    }

    public static int getDisplayStartIndex(OwnerHistoryResult result) {
        return 0;
    }

    private static OwnerHistoryResult getCached(String key) {
        CachedHistory cached = CACHE.get(key);
        if (cached == null) {
            return null;
        }
        if (System.currentTimeMillis() - cached.fetchedAt > (cached.result.status == FetchStatus.OK ? CACHE_TTL_MS : 15000L)) {
            CACHE.remove(key, cached);
            return null;
        }
        return cached.result;
    }

    private static void putCache(String key, OwnerHistoryResult result) {
        if (CACHE.size() >= 2048) CACHE.clear();
        CACHE.put(key, new CachedHistory(result, System.currentTimeMillis()));
    }
}
