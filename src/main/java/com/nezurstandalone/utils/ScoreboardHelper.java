package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which scoreboard line carries the animated branding.
 *
 * <p>Lives outside the mixin so it can hold static state.
 *
 * <h2>Why this is harder than it looks</h2>
 * The mixin can only replace the prefix of a {@link ScorePlayerTeam}, but the sidebar renders
 * one line per <em>score</em>, and a server is free to put several lines in the same team -
 * blank spacer lines especially, since they all have the same empty prefix. So "the bottom
 * line's team" is not the same thing as "the bottom line", and returning true for a shared team
 * paints the branding onto every line that team owns. That is what produced two copies of the
 * logo stacked on top of each other.
 *
 * <p>The team is therefore only accepted when it owns exactly one visible line. If the server
 * shares it, the branding is skipped: showing nothing is correct, showing it twice is not.
 */
public class ScoreboardHelper {

    private static final ThreadLocal<Boolean> CHECKING = ThreadLocal.withInitial(() -> false);

    /** Registered name of the team that currently owns the footer line, or null for none. */
    private static String footerTeamName;
    private static long lastResolveMs;
    private static Object resolvedWorld;

    /**
     * The sidebar is re-read at most this often.
     *
     * <p>{@code getColorPrefix} is called for every line on every frame, and resolving this
     * involves sorting the scoreboard and allocating lists - so the old code ran a sort per
     * line per frame, which at fifteen lines and a high frame rate is thousands of sorts a
     * second for an answer that changes at most a few times a second.
     */
    private static final long RESOLVE_INTERVAL_MS = 250L;

    public static String canonicalName(ScorePlayerTeam team, String name) {
        boolean previous = CHECKING.get();
        CHECKING.set(true);
        try { return ScorePlayerTeam.formatPlayerName(team, name); }
        finally { CHECKING.set(previous); }
    }

    public static String[] canonicalParts(ScorePlayerTeam team) {
        boolean previous=CHECKING.get();CHECKING.set(true);
        try { return team==null?new String[]{"", ""}:new String[]{team.getColorPrefix(),team.getColorSuffix()}; }
        finally { CHECKING.set(previous); }
    }

    public static boolean isChecking() {
        return CHECKING.get();
    }

    public static boolean isFooterTeam(ScorePlayerTeam team) {
        if (team == null) return false;
        if (CHECKING.get()) return false;

        String footer = resolveFooterTeam();
        return footer != null && footer.equals(team.getRegisteredName());
    }

    /** Cached lookup of the footer team, refreshed on a short interval. */
    private static String resolveFooterTeam() {
        Object world=Minecraft.getMinecraft().theWorld;
        if(world!=resolvedWorld){resolvedWorld=world;lastResolveMs=0;footerTeamName=null;}
        long now = com.nezurstandalone.control.Clock.millis();
        if (now - lastResolveMs < RESOLVE_INTERVAL_MS) {
            return footerTeamName;
        }
        lastResolveMs = now;

        // getPlayersTeam can re-enter prefix lookups, so the guard has to span the whole scan.
        CHECKING.set(true);
        try {
            footerTeamName = findFooterTeam();
        } catch (Exception e) {
            footerTeamName = null;
        } finally {
            CHECKING.set(false);
        }
        return footerTeamName;
    }

    private static String findFooterTeam() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return null;

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return null;

        ScoreObjective sidebarObj = scoreboard.getObjectiveInDisplaySlot(1);
        if (sidebarObj == null) return null;

        Collection<Score> scores = scoreboard.getSortedScores(sidebarObj);
        if (scores == null || scores.isEmpty()) return null;

        List<Score> visible = new ArrayList<Score>();
        for (Score s : scores) {
            if (s.getPlayerName() != null && !s.getPlayerName().startsWith("#")) {
                visible.add(s);
            }
        }
        if (visible.isEmpty()) return null;

        // How many visible lines each team owns. A team owning more than one line cannot be
        // used, because the prefix replacement would apply to all of them.
        Map<String, Integer> lineCount = new HashMap<String, Integer>();
        for (Score s : visible) {
            ScorePlayerTeam t = scoreboard.getPlayersTeam(s.getPlayerName());
            if (t == null) continue;
            String name = t.getRegisteredName();
            Integer prev = lineCount.get(name);
            lineCount.put(name, prev == null ? 1 : prev + 1);
        }

        // getSortedScores is ascending by score, so index 0 is the line drawn at the bottom.
        String candidate = exclusiveTeamOf(scoreboard, lineCount, visible.get(0));
        if (candidate != null) return candidate;

        // The bottom line is often a blank spacer. Fall through to the one above it, but only
        // when the bottom really is blank - otherwise this would brand an actual stat line.
        if (visible.size() > 1) {
            String bottomText = StringUtils.stripControlCodes(visible.get(0).getPlayerName()).trim();
            if (bottomText.isEmpty()) {
                return exclusiveTeamOf(scoreboard, lineCount, visible.get(1));
            }
        }
        return null;
    }

    /** The score's team, but only if that team owns no other visible line. */
    private static String exclusiveTeamOf(Scoreboard scoreboard, Map<String, Integer> lineCount, Score score) {
        ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
        if (team == null) return null;
        String name = team.getRegisteredName();
        Integer count = lineCount.get(name);
        return (count != null && count == 1) ? name : null;
    }

    /** Drops the cached lookup - call when the world or scoreboard changes out from under us. */
    public static void invalidate() {
        footerTeamName = null;
        lastResolveMs = 0L;
    }

    /**
     * The branding, padded so it slides left and right.
     *
     * <p>The leading spaces sit before the bold code and the {@code §r} ends it, so every space
     * in the string is a plain 4px one and the total rendered width never changes as the text
     * moves - otherwise the line would visibly breathe.
     */
    public static String getAnimatedText() {
        long time = System.currentTimeMillis() / 120;
        int maxSpaces = 10;
        int cycle = maxSpaces * 2;
        int step = (int) (time % cycle);
        int spaces = (step <= maxSpaces) ? step : (cycle - step);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
        sb.append("§e§lnezur§r");
        for (int i = 0; i < (maxSpaces - spaces); i++) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
