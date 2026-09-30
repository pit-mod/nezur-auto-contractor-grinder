package com.nezurstandalone.control;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
public final class CommandCoordinator {
    private static final CommandGate GATE=new CommandGate(Clock.SYSTEM);
    private static final CommandQueue QUEUE=new CommandQueue();private static boolean registered;
    private static void init(){if(!registered){net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new CommandCoordinator());registered=true;}}
    /** Synchronous callers advance their state only on true; a refusal does not submit hidden work. */
    public static boolean send(Object owner,String command){
        init();Minecraft mc=Minecraft.getMinecraft();QUEUE.session(ClientSession.current());
        if(mc.thePlayer==null||mc.theWorld==null||!QUEUE.empty()||!GATE.acquire(owner,command,ClientSession.current()))return false;
        mc.thePlayer.sendChatMessage(command);return true;
    }
    /** Sequence callers receive explicit terminal and deferred states. */
    public static CommandQueue.Sequence enqueue(Object owner,String... commands){
        init();CommandQueue.Sequence s=QUEUE.submit(owner,ClientSession.current(),commands);
        if(s.status()==CommandQueue.Status.REJECTED)throw new IllegalStateException("Automated command queue full; sequence rejected");
        return s;
    }
    public static void tick(){Minecraft mc=Minecraft.getMinecraft();long session=ClientSession.current();QUEUE.session(session);
        if(mc.thePlayer!=null&&mc.theWorld!=null)QUEUE.tick(session,(owner,command)->GATE.acquire(owner,command,session),command->mc.thePlayer.sendChatMessage(command));
    }
    public static boolean reconnect(Object owner){QUEUE.session(ClientSession.current());return QUEUE.empty()&&GATE.acquire(owner,"<reconnect>",ClientSession.current());}
    public static void cancel(Object owner){QUEUE.cancel(owner);GATE.cancel(owner);}
    @SubscribeEvent public void chat(ClientChatReceivedEvent event){String text=net.minecraft.util.StringUtils.stripControlCodes(event.message.getUnformattedText());
        if(event.type==0&&ServerMessages.throttle(text))GATE.throttle(15_000_000_000L);
    }
}
