package com.nezurstandalone.utils;

/** One outstanding menu action; time passing alone never acknowledges it. */
public final class MenuTransition {
    public enum Result { IDLE, WAITING, CHANGED, TIMED_OUT, UNKNOWN }
    private boolean tracked, acknowledged, rejected, responseOnly,strict;
    private Object authoritativeContainer;
    private int sourceSlot;
    public void beginAuthoritative(Object source,String kind,String contents,long now,int window,short action,int slot,String... expected){
        beginTracked(source,kind,contents,now,window,action,expected);strict=true;sourceSlot=slot;
    }
    public void contents(int window,Object container){if(strict&&isPending()&&window!=this.window&&container!=source)authoritativeContainer=container;}
    public int sourceSlot(){return sourceSlot;}
    public void beginResponse(Object source,long now,int window,short action) {
        beginTracked(source,"","",now,window,action,"response"); responseOnly=true;
    }
    public boolean awaitingResponse(long now) { return isPending() && responseOnly && !rejected && now-started<TIMEOUT_NANOS; }
    public boolean completeResponse(long now) { if(!awaitingResponse(now))return false;clear();return true; }
    private int window;
    private short action;
    public void beginTracked(Object source, String kind, String contents, long now, int window, short action, String... expected) {
        begin(source,kind,contents,now,expected);
        tracked=true; acknowledged=false; rejected=false; this.window=window; this.action=action;
    }
    public void confirm(int window, short action, boolean accepted) {
        if (isPending() && tracked && this.window==window && this.action==action) {
            acknowledged=accepted; rejected=!accepted;
        }
    }
    public boolean isAcknowledged() { return isPending() && acknowledged && !rejected; }
    private Object source;
    private String sourceKind;
    private String sourceContents;
    private String[] expected;
    private long started;
    private static final long TIMEOUT_NANOS = 15_000_000_000L;

    public boolean failed(long now){Result result=observe(null,"","",now);return result==Result.TIMED_OUT||result==Result.UNKNOWN;}
    public boolean isPending() { return source != null; }

    public void begin(Object source, String kind, String contents, long now, String... expected) {
        if (isPending()) throw new IllegalStateException("A menu action is already pending");
        if (source == null || expected.length == 0) throw new IllegalArgumentException();
        this.source = source;
        this.sourceKind = kind;
        this.sourceContents = contents;
        this.expected = expected.clone();
        this.started = now;
    }

    public Result observe(Object container, String kind, String contents, long now) {
        if (!isPending()) return Result.IDLE;
        if (rejected || now - started >= TIMEOUT_NANOS) return strict?Result.UNKNOWN:Result.TIMED_OUT;
        boolean changed = container != source || !sourceKind.equals(kind)
                || !sourceContents.equals(contents);
        if (!responseOnly && container != null && changed && (!tracked || acknowledged) && (!strict || container!=source&&container==authoritativeContainer)) {
            for (String next : expected) {
                if (next.equals(kind)) {
                    clear();
                    return Result.CHANGED;
                }
            }
        }
        return Result.WAITING;
    }

    public void clear() {
        tracked = acknowledged = rejected = responseOnly = strict = false;authoritativeContainer=null;sourceSlot=-1;
        source = null;
        sourceKind = null;
        sourceContents = null;
        expected = null;
        started = 0L;
    }
}
