package akasha.queststats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;

/** Dependency-free tests (run by build.sh): ranking rules and the HolographicDisplays reflection bridge. */
public final class PluginTests {

    private static int failures;

    public static void main(String[] args) {
        rankingOrderAndTies();
        rankingHiddenAndTotals();
        rankingEmptyAndUnknownNames();
        hdBridge();
        if (failures > 0) {
            System.err.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("PluginTests: all checks passed");
    }

    private static UUID id(int n) {
        return new UUID(0, n);
    }

    private static void rankingOrderAndTies() {
        Map<UUID, Integer> c = new HashMap<>();
        c.put(id(1), 10);
        c.put(id(2), 30);
        c.put(id(3), 10);
        c.put(id(4), 20);
        Map<UUID, String> names = new HashMap<>();
        names.put(id(1), "zeca");
        names.put(id(2), "Ana");
        names.put(id(3), "Bruno");
        names.put(id(4), "carla");
        Ranking r = Ranking.of(c, 100, names::get, e -> false);
        check("order", r.at(1).name.equals("Ana") && r.at(2).name.equals("carla"));
        // tie at 10: by name ignoring case -> Bruno before zeca
        check("tie by name", r.at(3).name.equals("Bruno") && r.at(4).name.equals("zeca"));
        check("past end", r.at(5) == null && r.at(0) == null);
        check("position", r.positionOf(id(4)) == 2 && r.positionOf(id(9)) == 0);
    }

    private static void rankingHiddenAndTotals() {
        Map<UUID, Integer> c = new HashMap<>();
        c.put(id(1), 5);
        c.put(id(2), 50);
        c.put(id(3), 0);
        Map<UUID, String> names = new HashMap<>();
        names.put(id(1), "Jogador");
        names.put(id(2), "Staff");
        Ranking r = Ranking.of(c, 8939, names::get, e -> e.name.equals("Staff"));
        check("hidden left out", r.entries.size() == 1 && r.at(1).name.equals("Jogador"));
        check("hidden still counted", r.players == 2 && r.totalCompleted == 55 && r.totalQuests == 8939);
        check("zero ignored", r.positionOf(id(3)) == 0);
    }

    private static void rankingEmptyAndUnknownNames() {
        check("empty", Ranking.EMPTY.at(1) == null && Ranking.EMPTY.players == 0);
        Map<UUID, Integer> c = new HashMap<>();
        UUID u = UUID.fromString("12345678-aaaa-bbbb-cccc-1234567890ab");
        c.put(u, 3);
        Ranking r = Ranking.of(c, 10, x -> null, e -> false);
        check("unknown name -> short id", r.at(1).name.equals("12345678"));
    }

    private static void hdBridge() {
        HdPlaceholders hd = HdPlaceholders.load(null, PluginTests.class.getClassLoader());
        check("api found", hd != null);
        if (hd == null) return;
        String[] value = { "a" };
        check("register", hd.register("{aq_top1}", 5.0, () -> value[0]));
        check("duplicate refused", !hd.register("{aq_top1}", 5.0, () -> "x"));
        check("replacer reads supplier", HologramsAPI.REGISTERED.get("{aq_top1}").update().equals("a"));
        value[0] = "b";
        check("replacer is live", HologramsAPI.REGISTERED.get("{aq_top1}").update().equals("b"));
        check("rate passed", HologramsAPI.RATES.get("{aq_top1}") == 5.0);
        check("proxy toString/hashCode", HologramsAPI.REGISTERED.get("{aq_top1}").toString().contains("aq_top1")
            && HologramsAPI.REGISTERED.get("{aq_top1}").hashCode() == HologramsAPI.REGISTERED.get("{aq_top1}").hashCode());
        hd.unregisterAll();
        check("unregister", HologramsAPI.REGISTERED.isEmpty());
        check("missing api -> null", HdPlaceholders.load(null, new ClassLoader(null) {}) == null);
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failures++;
            System.err.println("FAIL " + what);
        }
    }
}
