package com.nezurstandalone.control;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.*;

/** One requested Classic list at a time; rows never authorize arbitrary command text. */
public final class LowestClassicPit {
    private enum Phase { IDLE, HEADER, ROWS, JOIN, TRANSFER, DONE, FALLBACK }
    private static final String ID="(?:[a-z]{2,5}-)?pit-[1-9][0-9]{0,2}";
    private static final Pattern ROW=Pattern.compile("^[•*-]\\s+("+ID+")\\s+[—–-]\\s+([0-9]{1,5})\\s+players?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENT=Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+("+ID+")$",Pattern.CASE_INSENSITIVE);
    private final Map<String,Integer> servers=new LinkedHashMap<>();
    private Object connection;
    private Phase phase=Phase.IDLE;
    private String selected;
    private long deadline,lastRow,nextAt;
    private int attempts;
    private boolean wasPit;

    public static boolean classicHost(String address) {
        if(address==null)return false;
        String host=address.trim().toLowerCase(Locale.ROOT).split(":",2)[0];
        return host.equals("pitclassic.net")||host.endsWith(".pitclassic.net");
    }
    private static String clean(String text){return text==null?"":text.replaceAll("§[0-9a-fk-orA-FK-OR]","").replace('\u00a0',' ').trim();}
    public static String currentServer(List<String> lines) {
        String result=null;
        if(lines!=null)for(String line:lines){Matcher m=CURRENT.matcher(clean(line));if(m.matches()){
            if(result!=null)return null;result=m.group(1).toLowerCase(Locale.ROOT);
        }}
        return result;
    }
    public void reset(){connection=null;phase=Phase.IDLE;servers.clear();selected=null;deadline=lastRow=nextAt=0;attempts=0;wasPit=false;}
    public void context(Object current,boolean enabled) {
        if(!enabled||current==null){reset();return;}
        if(connection!=current){reset();connection=current;}
    }
    public boolean belongsTo(Object current){return connection!=null&&connection==current;}
    /** Only messages after our accepted list request, with its expected header, are candidates. */
    public void message(String message,long now) {
        if(message==null||connection==null||(phase!=Phase.HEADER&&phase!=Phase.ROWS)||now>=deadline)return;
        for(String raw:message.split("\\r?\\n")) {
            String text=clean(raw);
            if(text.equalsIgnoreCase("Available Pit lobbies:")) {
                if(phase==Phase.HEADER){phase=Phase.ROWS;lastRow=now;}
                continue;
            }
            if(phase!=Phase.ROWS)continue;
            Matcher m=ROW.matcher(text);if(!m.matches())continue;
            int count=Integer.parseInt(m.group(2));if(count>10000)continue;
            String id=m.group(1).toLowerCase(Locale.ROOT);
            if(servers.size()>=64&&!servers.containsKey(id))continue;
            servers.put(id,count);lastRow=now;
        }
    }
    private String least(String current) {
        String best=null;int minimum=Integer.MAX_VALUE;
        for(Map.Entry<String,Integer> s:servers.entrySet())if(s.getValue()<minimum){minimum=s.getValue();best=s.getKey();}
        if(current!=null&&servers.containsKey(current)&&servers.get(current)==minimum)return current;
        return best;
    }
    /** True while this flow owns the join; send refusal never advances a phase. */
    public boolean tick(boolean eligible,boolean safe,boolean inPit,boolean inLobby,
                        String current,long now,Predicate<String> send) {
        if(connection==null)return false;
        if(wasPit&&inLobby&&(phase==Phase.DONE||phase==Phase.FALLBACK)) {
            phase=Phase.IDLE;attempts=0;nextAt=now;servers.clear();selected=null;
        }
        if(inPit)wasPit=true;else if(inLobby)wasPit=false;
        if(phase==Phase.DONE||phase==Phase.FALLBACK)return false;
        if(phase==Phase.TRANSFER) {
            if(inPit&&selected.equals(current)){phase=Phase.DONE;return false;}
            if(now>=deadline){phase=attempts>=3?Phase.FALLBACK:Phase.IDLE;nextAt=now+100;}
            return phase!=Phase.FALLBACK;
        }
        if((phase==Phase.HEADER||phase==Phase.ROWS)&&now>=deadline){phase=Phase.FALLBACK;return false;}
        if(phase==Phase.JOIN&&now>=deadline){phase=Phase.FALLBACK;return false;}
        if(phase==Phase.ROWS&&!servers.isEmpty()&&now-lastRow>=100) {
            selected=least(current);
            if(inPit&&selected.equals(current)){phase=Phase.DONE;return false;}
            phase=Phase.JOIN;deadline=now+8000;
        }
        if(!safe)return phase!=Phase.IDLE||eligible;
        if(phase==Phase.IDLE) {
            if(!eligible||now<nextAt)return eligible;
            if(send.test("/play pit list")){attempts++;phase=Phase.HEADER;deadline=now+3000;servers.clear();selected=null;}
            return true;
        }
        if(phase==Phase.JOIN&&send.test("/play pit "+selected)){phase=Phase.TRANSFER;deadline=now+5000;}
        return true;
    }
}
