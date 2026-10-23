package com.nezurstandalone.control;

/** Frame aim, frozen tick aim, and vanilla-reported orientation are separate states. */
public final class ServerRotationState {
    private float yaw, pitch, tickYaw, tickPitch, sentYaw, sentPitch, previousSentYaw, previousSentPitch;
    private boolean tickActive, confirmed, reported;
    public void seed(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; tickActive = confirmed = false; }
    public void apply(float yawDelta, float pitchDelta) { yaw += yawDelta; pitch = Math.max(-90, Math.min(90, pitch + pitchDelta)); }
    public void beginTick(boolean active) { tickActive = active; tickYaw = yaw; tickPitch = pitch; confirmed = false; }
    public void report(float yaw, float pitch) {
        previousSentYaw = reported ? sentYaw : yaw;
        previousSentPitch = reported ? sentPitch : pitch;
        sentYaw = yaw; sentPitch = pitch;
        reported = true;
        confirmed = tickActive && Math.abs(wrap(yaw - tickYaw)) < 0.0001F && Math.abs(pitch - tickPitch) < 0.0001F;
    }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float tickYaw() { return tickYaw; }
    public float tickPitch() { return tickPitch; }
    public float sentYaw() { return sentYaw; }
    public float sentPitch() { return sentPitch; }
    public float previousSentYaw() { return previousSentYaw; }
    public float previousSentPitch() { return previousSentPitch; }
    public boolean tickActive() { return tickActive; }
    public boolean confirmed() { return confirmed; }
    public void reset() { tickActive = confirmed = reported = false; }
    public static float wrap(float value) { return ((value % 360) + 540) % 360 - 180; }
}
