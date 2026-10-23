package com.nezurstandalone.utils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared player streak reader. Missing data is distinct from a confirmed zero. */
public final class PitStreak {
    private static final Pattern VALUE=Pattern.compile("(?i)^streak\\s*:\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*$");
    private PitStreak() { }
    public static int read(List<String> lines) {
        if(lines==null)return -1;
        for(String raw:lines){
            if(raw==null)continue;
            Matcher m=VALUE.matcher(raw.replaceAll("(?i)\u00a7[0-9a-fk-or]", "").replace('\u00a0',' ').trim());
            if(m.matches())try {
                double n=Double.parseDouble(m.group(1).replace(",",""));
                if(n<=Integer.MAX_VALUE)return (int)n;
            }catch(NumberFormatException ignored){ }
        }
        return -1;
    }
}
