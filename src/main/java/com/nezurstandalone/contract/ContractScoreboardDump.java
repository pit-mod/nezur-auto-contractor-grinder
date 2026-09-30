package com.nezurstandalone.contract;

import com.google.gson.Gson;
import com.nezurstandalone.utils.ScoreboardHelper;
import com.nezurstandalone.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local-only change journal. Captures server text and parser evidence, never packets or credentials. */
public final class ContractScoreboardDump {
    private static final Gson JSON=new Gson();
    private static String previous="";
    private static Object previousWorld;
    private static long retryAfter;
    private ContractScoreboardDump() {}
    public static void capture(Minecraft mc,List<String> lines,ContractScoreboard.Snapshot live,
                               ContractScoreboard.Snapshot tracked,long now) {
        if(now<retryAfter || mc.theWorld==null)return;
        try {
            Scoreboard board=mc.theWorld.getScoreboard();
            ScoreObjective objective=Utils.displayedSidebar(board);
            Map<String,Object> data=new LinkedHashMap<String,Object>();
            data.put("title",objective==null?"":objective.getDisplayName());
            data.put("objective",objective==null?"":objective.getName());
            List<Object> slots=new ArrayList<Object>();
            for(int i=0;i<19;i++) {
                ScoreObjective o=board.getObjectiveInDisplaySlot(i);
                if(o!=null)slots.add(Arrays.asList(i,o.getName(),o.getDisplayName()));
            }
            data.put("displaySlots",slots);
            List<Object> entries=new ArrayList<Object>();
            if(objective!=null)for(Score score:board.getSortedScores(objective)) {
                Map<String,Object> row=new LinkedHashMap<String,Object>();
                String name=score.getPlayerName();ScorePlayerTeam team=board.getPlayersTeam(name);
                String[] parts=ScoreboardHelper.canonicalParts(team);
                row.put("score",score.getScorePoints());row.put("entry",name);
                row.put("team",team==null?null:team.getRegisteredName());
                row.put("prefix",parts[0]);row.put("suffix",parts[1]);
                row.put("formatted",ScoreboardHelper.canonicalName(team,name));entries.add(row);
            }
            data.put("rawEntries",entries);data.put("parserLines",lines);
            data.put("parsed",live);data.put("tracked",tracked);
            data.put("normalSidebar",ContractScoreboard.normalSidebar(lines));
            data.put("majorEvent",ContractScoreboard.majorEvent(lines));
            String fingerprint=JSON.toJson(data);
            if(mc.theWorld==previousWorld && fingerprint.equals(previous))return;
            data.put("worldChanged",mc.theWorld!=previousWorld);
            data.put("capturedAtEpochMs",System.currentTimeMillis());
            File dir=new File(mc.mcDataDir,"nezur-acg/diagnostics");
            Files.createDirectories(dir.toPath());
            Path journal=new File(dir,"scoreboard-live.jsonl").toPath();
            if(Files.exists(journal) && Files.size(journal)>4*1024*1024)
                Files.move(journal,new File(dir,"scoreboard-live.previous.jsonl").toPath(),StandardCopyOption.REPLACE_EXISTING);
            byte[] bytes=(JSON.toJson(data)+"\n").getBytes(StandardCharsets.UTF_8);
            Files.write(journal,bytes,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            Files.write(new File(dir,"scoreboard-latest.json").toPath(),bytes);
            previous=fingerprint;previousWorld=mc.theWorld;
        } catch(Exception error) {
            retryAfter=now+30000;
            System.err.println("[AutoContractor] Scoreboard dump unavailable: "+error.getClass().getSimpleName());
        }
    }
}
