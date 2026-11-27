package com.nezurstandalone.engine;

import java.util.List;
import java.util.Locale;

/** Keep verified equipment across respawns in an identified profile, never across profile resets. */
public final class LoadoutContext {
    private String scope="",profile="",prestige="";
    private Object connection;

    private static String prestigeToken(String value) {
        String token=value.trim().replace("[", "").replace("]", "").toUpperCase(Locale.ROOT);
        return token.matches("(?:[IVXLCDM]+|[0-9]+)")?token:"";
    }
    public boolean identified(){return !profile.isEmpty()||!prestige.isEmpty();}

    public void observe(LoadoutPlanner planner,Object currentConnection,String account,String title,List<String> lines) {
        String nextScope=account+"|"+title;
        if(!scope.equals(nextScope)){scope=nextScope;profile=prestige="";connection=null;}
        // Lobby transfers can replace the World and briefly remove the sidebar on the same connection.
        if(currentConnection!=null){
            if(connection!=null&&connection!=currentConnection&&!identified())planner.resetEvidence();
            connection=currentConnection;
        }
        String nextProfile="",nextPrestige="",bracketPrestige="";
        for(String line:lines) {
            String text=LoadoutPlanner.clean(line).trim();
            if(text.startsWith("Profile:")&&!text.substring(8).trim().isEmpty())nextProfile=text;
            if(text.startsWith("Prestige:"))nextPrestige=prestigeToken(text.substring(9));
            if(text.startsWith("Level:")&&text.contains("[")&&text.contains("]")) {
                String bracket=text.substring(text.indexOf('[')+1,text.indexOf(']'));
                int dash=bracket.lastIndexOf('-');if(dash>=0)bracketPrestige=prestigeToken(bracket.substring(0,dash));
            }
        }
        // Incomplete streaming updates never erase the last proven metadata.
        if(!nextProfile.isEmpty())profile=nextProfile;
        if(!nextPrestige.isEmpty())prestige=nextPrestige;
        else if(!bracketPrestige.isEmpty())prestige=bracketPrestige;
        planner.context(scope+"|"+profile+"|"+prestige);
    }

}
