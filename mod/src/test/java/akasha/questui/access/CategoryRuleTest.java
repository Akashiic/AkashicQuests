package akasha.questui.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;

import org.junit.Test;

public class CategoryRuleTest {

    @Test
    public void parsesDimensionLists() {
        assertEquals(new LinkedHashSet<>(Arrays.asList(7, -1, 12)), CategoryRule.parseDims(" 7, -1;12 x"));
        assertTrue(
            CategoryRule.parseDims("")
                .isEmpty());
        assertTrue(
            CategoryRule.parseDims(null)
                .isEmpty());
    }

    @Test
    public void roundTripsThroughNbt() {
        CategoryRule r = new CategoryRule(
            "Skyblock",
            CategoryRule.parseDims("7, 8"),
            CategoryRule.parseDims("8"),
            "akasha.skyblock",
            CategoryRule.BANNER,
            "Só no Skyblock",
            0x336699);
        CategoryRule back = CategoryRule.read(r.write());
        assertEquals("Skyblock", back.name);
        assertEquals(r.dims, back.dims);
        assertEquals(r.exclude, back.exclude);
        assertEquals("akasha.skyblock", back.perm);
        assertEquals(CategoryRule.BANNER, back.outside);
        assertEquals("Só no Skyblock", back.banner);
        assertEquals(0xFF336699, back.color);
    }

    @Test
    public void defaultsAndOpenRules() {
        CategoryRule e = CategoryRule.empty("A");
        assertTrue(e.isOpen());
        assertTrue(e.dimensionOk(-1));
        assertEquals(CategoryRule.HIDE, e.outside);
        assertEquals(CategoryRule.DEFAULT_COLOR, e.color);
        CategoryRule d = e.withDims(CategoryRule.parseDims("1"));
        assertFalse(d.isOpen());
        assertTrue(d.dimensionOk(1));
        assertFalse(d.dimensionOk(0));
        assertEquals(CategoryRule.HIDE, e.withOutside("weird").outside);
        // blacklist: everywhere except 7 (a main campaign that must not count in the Skyblock dimension)
        CategoryRule x = e.withExclude(CategoryRule.parseDims("7"));
        assertFalse(x.isOpen());
        assertTrue(x.dimensionOk(0));
        assertTrue(x.dimensionOk(-1));
        assertFalse(x.dimensionOk(7));
        // both: whitelist first, then blacklist
        CategoryRule both = x.withDims(CategoryRule.parseDims("7, 8"));
        assertTrue(both.dimensionOk(8));
        assertFalse(both.dimensionOk(7));
        assertFalse(both.dimensionOk(0));
        assertFalse(
            e.withPerm("a.b")
                .isOpen());
    }
}
