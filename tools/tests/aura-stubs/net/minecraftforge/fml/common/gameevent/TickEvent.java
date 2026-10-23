package net.minecraftforge.fml.common.gameevent;
public class TickEvent { public enum Phase { START,END } public static class ClientTickEvent { public final Phase phase; public ClientTickEvent(Phase phase){this.phase=phase;} } public static class RenderTickEvent { public final Phase phase; public RenderTickEvent(Phase phase){this.phase=phase;} } }
