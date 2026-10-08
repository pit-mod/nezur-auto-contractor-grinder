import com.nezurstandalone.utils.PitLocationTracker;
import com.nezurstandalone.control.ManualCameraPriority;
import com.nezurstandalone.contract.ContractConnectionFlow.Location;
import java.util.Arrays;
import java.util.Collections;

public final class RecoveryPolicyTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        PitLocationTracker tracker = new PitLocationTracker();
        Object world = new Object();
        check(PitLocationTracker.supportedHost("mc.hypixel.net:25565"), "Hypixel host");
        check(PitLocationTracker.supportedHost("mc.pitclassic.net"), "Classic host");
        check(!PitLocationTracker.supportedHost("hypixel.net.example.org"), "Exact domain boundary");
        check(tracker.observe(world, true, "", Collections.emptyList(), 0) == Location.UNKNOWN, "Transfer grace");
        check(tracker.observe(world, true, "", Collections.emptyList(), 4999) == Location.UNKNOWN, "Grace still active");
        check(tracker.observe(world, true, "", Collections.emptyList(), 5000) == Location.LIMBO, "Persistent empty sidebar");
        check(tracker.observe(world, true, "THE HYPIXEL PIT", Collections.emptyList(), 6000) == Location.PIT, "Pit resumes");
        check(tracker.observe(world, true, "", Collections.emptyList(), 7000) == Location.UNKNOWN, "New transfer resets grace");
        check(tracker.observe(new Object(), true, "", Collections.emptyList(), 15000) == Location.UNKNOWN, "World change resets grace");
        check(tracker.observe(world, false, "THE HYPIXEL PIT", Collections.emptyList(), 16000) == Location.UNKNOWN, "Unsupported server ignored");
        check(tracker.observe(world, true, "PIT CLASSIC", Arrays.asList("NETWORK LOBBY"), 17000) == Location.LOBBY, "Classic lobby");
        check(tracker.observe(world, true, "SPIRE FLOOR 3", Collections.emptyList(), 18000) == Location.PIT, "Spire remains connected Pit");
        ManualCameraPriority camera = new ManualCameraPriority();
        check(!camera.sample(1000, 0, 0), "Idle automation allowed");
        check(camera.sample(1001, 4, 0), "Physical look takes priority");
        check(camera.sample(1199, 0, 0), "Quiet period retains manual priority");
        check(camera.sample(1200, 0, -2), "More mouse input extends priority");
        check(!camera.sample(1400, 0, 0), "Automation resumes after quiet period");
        camera.sample(1500, 1, 1);
        camera.reset();
        check(!camera.blocked(1500), "Reset clears stale manual priority");
        System.out.println("RECOVERY_CAMERA_POLICY=PASS");
    }
}
