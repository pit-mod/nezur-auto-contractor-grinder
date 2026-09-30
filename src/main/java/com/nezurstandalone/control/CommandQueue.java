package com.nezurstandalone.control;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
/** Ordered sequences with observable cancellation; deferred commands remain queued. */
public final class CommandQueue {
    public enum Status { QUEUED,DEFERRED,DISPATCHED,CANCELLED,REJECTED }
    public static final class Sequence {
        public final Object owner;public final long id,session;private final String[] commands;
        private int cursor;private Status status=Status.QUEUED;private Consumer<Status> listener;
        private Sequence(Object owner,long id,long session,String[] commands){this.owner=owner;this.id=id;this.session=session;this.commands=commands;}
        public Status status(){return status;}
        public int dispatched(){return cursor;}
        public Sequence watch(Consumer<Status> listener){this.listener=listener;notifyListener();return this;}
        private void status(Status next){if(status==next)return;status=next;notifyListener();}
        private void notifyListener(){
            if(listener==null)return;
            try{listener.accept(status);}catch(Throwable failure){
                java.util.logging.Logger.getLogger(CommandQueue.class.getName()).log(
                        java.util.logging.Level.WARNING,"Command sequence observer failed",failure);
            }
        }
    }
    private static void cancelAll(List<Sequence> cancelled){
        // Publish every terminal state before invoking reentrant observers.
        for(Sequence s:cancelled)s.status=Status.CANCELLED;
        for(Sequence s:cancelled)s.notifyListener();
    }
    private final Deque<Sequence> queue=new ArrayDeque<>();private long serial;
    public Sequence submit(Object owner,long session,String... commands){
        if(owner==null||commands.length==0)throw new IllegalArgumentException("sequence owner/commands");
        for(String c:commands)if(c==null||c.trim().isEmpty())throw new IllegalArgumentException("empty command");
        Sequence s=new Sequence(owner,++serial,session,commands.clone());
        if(queue.size()>=256){s.status(Status.REJECTED);return s;}queue.addLast(s);return s;
    }
    public boolean empty(){return queue.isEmpty();}
    public void cancel(Object owner){java.util.List<Sequence> cancelled=new ArrayList<>();for(Iterator<Sequence> i=queue.iterator();i.hasNext();){Sequence s=i.next();if(s.owner==owner){i.remove();cancelled.add(s);}}cancelAll(cancelled);}
    public void session(long token){java.util.List<Sequence> cancelled=new ArrayList<>();for(Iterator<Sequence> i=queue.iterator();i.hasNext();){Sequence s=i.next();if(s.session!=token){i.remove();cancelled.add(s);}}cancelAll(cancelled);}
    public void tick(long token,BiPredicate<Object,String> permit,Consumer<String> dispatch){
        session(token);Sequence s=queue.peekFirst();if(s==null)return;
        if(!permit.test(s.owner,s.commands[s.cursor])){s.status(Status.DEFERRED);return;}
        dispatch.accept(s.commands[s.cursor++]);
        if(s.cursor==s.commands.length){queue.removeFirst();s.status(Status.DISPATCHED);}else s.status(Status.QUEUED);
    }
}
