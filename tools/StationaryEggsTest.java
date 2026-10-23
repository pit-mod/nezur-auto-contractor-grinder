import com.nezurstandalone.control.StationaryEggs;
import com.nezurstandalone.control.EggLocations;

public final class StationaryEggsTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        StationaryEggs<String> eggs = new StationaryEggs<>();
        check(eggs.observe("a", true, 100));
        check(eggs.observe("a", true, 25099));
        check(!eggs.observe("a", true, 25100));
        check(!eggs.observe("a", true, 100000)); // repeated scans never renew a stuck egg
        check(eggs.observe("b", true, 100000)); // movement/new position has its own timer
        check(!eggs.observe("a", true, 100001)); // other egg activity cannot renew a
        check(!eggs.observe("a", false, 100002));
        check(eggs.observe("a", true, 100003)); // removal then replacement resets that position
        eggs.retain(p -> p.equals("a"));
        check(eggs.observe("b", true, 200000)); // unloaded/disappeared positions can return
        check(!eggs.observe("a", true, 200000));
        eggs.clear();
        check(eggs.observe("a", true, 200001)); // new world

        EggLocations index = new EggLocations();
        StationaryEggs<EggLocations.Position> ages = new StationaryEggs<>();
        EggLocations.Position near = new EggLocations.Position(0, 64, 0);
        EggLocations.Position far = new EggLocations.Position(8, 64, 0);
        index.update(near.x, near.y, near.z, true);
        index.update(far.x, far.y, far.z, true);
        ages.observe(near, true, 0);
        ages.observe(far, true, 20000);
        check(far.equals(index.nearest(0,64,0, p -> ages.observe(p,true,25000))));
        check(index.nearest(0,64,0, p -> ages.observe(p,true,45000)) == null);
        check(near.equals(index.nearest(0,64,0))); // exclusion doesn't corrupt the location index
        System.out.println("StationaryEggsTest PASS checks=" + checks);
    }
}
