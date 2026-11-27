package com.nezurstandalone.module.impl.render;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.DraggableHud;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.ButtonSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.utils.EventUtils;
import com.nezurstandalone.utils.Utils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Events extends Module implements DraggableHud {
    private static final long REFRESH_MS = 30_000;
    private static final int MAX_EVENTS = 4096;
    private final NumberSetting count = new NumberSetting("Number of Events", 5, 1, 10, 0);
    private final Map<String, BooleanSetting> filters = new LinkedHashMap<>();
    private final Map<String, Integer> colors = new LinkedHashMap<>();
    private ExecutorService executor;
    private Future<List<Entry>> request;
    private List<Entry> schedule = Collections.emptyList();
    private final List<Entry> displayed = new ArrayList<>();
    private long nextRefresh;
    private long shownAt;
    private boolean inPit;
    private boolean fetchFailed;

    public Events() {
        super("Events", "Shows upcoming Hypixel Pit events and countdowns.", Category.RENDER);
        addSettings(count, new ButtonSetting("Refresh", () -> nextRefresh = 0));
        event("Blockhead", 0xFFAA00); event("Pizza", 0xFF5555); event("Beast", 0x55FF55);
        event("Robbery", 0xFFAA00); event("Spire", 0xAA00AA); event("Squads", 0x55FFFF);
        event("Team Deathmatch", 0xAA00AA); event("Raffle", 0xFFAA00); event("Rage Pit", 0xFF5555);
        event("2x Rewards", 0x00AA00); event("Giant Cake", 0xFF55FF); event("KOTL", 0x55FF55);
        event("Dragon Egg", 0xAA00AA); event("Auction", 0xFFFF55); event("Quick Maths", 0xAA00AA);
        event("KOTH", 0x55FFFF); event("Care Package", 0xFFAA00); event("All Bounty", 0xFFAA00);
    }

    private void event(String name, int color) {
        BooleanSetting setting = new BooleanSetting(name, true);
        String key = name.toLowerCase(Locale.ROOT);
        filters.put(key, setting);
        colors.put(key, color);
        addSetting(setting);
    }

    @Override protected void onDisable() {
        if (request != null) request.cancel(true);
        if (executor != null) executor.shutdownNow();
        request = null;
        executor = null;
        schedule = Collections.emptyList();
        displayed.clear();
        inPit = fetchFailed = false;
        nextRefresh = 0;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isToggled()) return;
        inPit = mc.thePlayer != null && mc.theWorld != null
                && "THE HYPIXEL PIT".equalsIgnoreCase(Utils.getScoreboardTitle().trim());
        if (!inPit) return;
        long now = System.currentTimeMillis();
        if (request != null && request.isDone()) finishFetch();
        if (request == null && now >= nextRefresh) {
            if (executor == null) executor = Executors.newSingleThreadExecutor(task -> {
                Thread worker = new Thread(task, "nezur-events");
                worker.setDaemon(true);
                return worker;
            });
            nextRefresh = now + REFRESH_MS;
            request = executor.submit(() -> readSchedule(EventUtils.fetchEvents()));
        }
        shownAt = now;
        displayed.clear();
        int limit = Math.max(1, Math.min(10, (int) count.value));
        for (Entry entry : schedule) {
            BooleanSetting filter = filters.get(entry.key);
            if (entry.timestamp < now || (filter != null && !filter.enabled)) continue;
            displayed.add(entry);
            if (displayed.size() >= limit) break;
        }
    }

    private void finishFetch() {
        try {
            schedule = request.get();
            fetchFailed = false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fetchFailed = true;
        } catch (ExecutionException failed) {
            fetchFailed = true;
        } finally {
            request = null;
        }
    }

    private static List<Entry> readSchedule(String response) throws IOException {
        if (response == null) throw new IOException("Event schedule unavailable");
        List<Entry> entries = new ArrayList<>();
        try (JsonReader json = new JsonReader(new StringReader(response))) {
            json.beginArray();
            for (int count = 0; json.hasNext(); count++) {
                if (count >= MAX_EVENTS) throw new IOException("Too many events");
                if (json.peek() != JsonToken.BEGIN_OBJECT) {
                    json.skipValue();
                    continue;
                }
                json.beginObject();
                String name = "";
                long timestamp = 0;
                while (json.hasNext()) {
                    String field = json.nextName();
                    if ("event".equals(field) && json.peek() == JsonToken.STRING) name = json.nextString().trim();
                    else if ("timestamp".equals(field) && json.peek() == JsonToken.NUMBER) {
                        try { timestamp = Long.parseLong(json.nextString()); }
                        catch (NumberFormatException malformed) { timestamp = 0; }
                    } else json.skipValue();
                }
                json.endObject();
                if (!name.isEmpty() && name.length() <= 64 && timestamp > 0) entries.add(new Entry(name, timestamp));
            }
            json.endArray();
            if (json.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing event data");
        }
        entries.sort(Comparator.comparingLong(entry -> entry.timestamp));
        return entries;
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT || !isHudVisible()) return;
        FontRenderer font = mc.fontRendererObj;
        int x = getRenderX(), y = getRenderY();
        if (displayed.isEmpty()) {
            font.drawStringWithShadow(status(), x, y, 0xAAAAAA);
            return;
        }
        int timerX = x + nameWidth(font) + 2;
        for (int index = 0; index < displayed.size(); index++) {
            Entry entry = displayed.get(index);
            int timerColor = index == 0 ? 0xFF5555 : index == 1 ? 0xFFAA00 : 0x55FF55;
            font.drawStringWithShadow(entry.name, x, y, colors.getOrDefault(entry.key, 0xFFFFFF));
            font.drawStringWithShadow(timer(entry), timerX, y, timerColor);
            y += font.FONT_HEIGHT + 2;
        }
    }

    private String status() {
        if (request != null && schedule.isEmpty()) return "Loading events...";
        return fetchFailed ? "Event schedule unavailable" : "No upcoming events";
    }

    private String timer(Entry entry) {
        long seconds = Math.max(0, (entry.timestamp - shownAt) / 1000);
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return "[" + (minutes < 10 ? "0" : "") + minutes + ":" + (remainder < 10 ? "0" : "") + remainder + "]";
    }

    private int nameWidth(FontRenderer font) {
        int width = 0;
        for (Entry entry : displayed) width = Math.max(width, font.getStringWidth(entry.name));
        return width;
    }

    @Override public String getHudKey() { return getName(); }
    @Override public boolean isHudVisible() { return isToggled() && inPit && mc.fontRendererObj != null; }
    @Override public double getDefaultHudX() { return 5; }
    @Override public double getDefaultHudY() { return 50; }
    @Override public int getHudWidth() {
        FontRenderer font = mc == null ? null : mc.fontRendererObj;
        if (font == null) return getHudEditorWidth();
        if (displayed.isEmpty()) return font.getStringWidth(status());
        int timerWidth = 0;
        for (Entry entry : displayed) timerWidth = Math.max(timerWidth, font.getStringWidth(timer(entry)));
        return nameWidth(font) + 2 + timerWidth;
    }
    @Override public int getHudHeight() {
        FontRenderer font = mc == null ? null : mc.fontRendererObj;
        return Math.max(1, displayed.size()) * (font == null ? 11 : font.FONT_HEIGHT + 2);
    }

    private static final class Entry {
        final String name, key;
        final long timestamp;

        Entry(String name, long timestamp) {
            this.name = name;
            this.key = name.toLowerCase(Locale.ROOT);
            this.timestamp = timestamp;
        }
    }
}
