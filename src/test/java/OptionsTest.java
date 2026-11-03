import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsTest {

    @Test
    void defaultsMatchTheOriginalDemo() {
        var options = SinusCalculation.Options.parse("1.0", "2.0");

        assertEquals(1.0, options.x());
        assertEquals(2.0, options.y());
        assertEquals(SinusCalculation.Mode.REWRITE, options.mode());
        assertEquals("asm", options.engine().id());
        assertEquals(TrigForm.Choice.Flip.INSTANCE, options.form());
        assertEquals(4, options.rounds());
        assertFalse(options.dump());
    }

    @Test
    void everyOptionIsParsed() {
        var options = SinusCalculation.Options.parse("0.5", "-3",
                "--mode", "live", "--engine", "classfile", "--form", "product", "--rounds", "7", "--dump");

        assertEquals(0.5, options.x());
        assertEquals(-3.0, options.y());
        assertEquals(SinusCalculation.Mode.LIVE, options.mode());
        assertEquals("classfile", options.engine().id());
        assertEquals(new TrigForm.Choice.Fixed(TrigForm.PRODUCT), options.form());
        assertEquals(7, options.rounds());
        assertTrue(options.dump());
    }

    @Test
    void formChoicesResolveAgainstTheCurrentForm() {
        assertEquals(TrigForm.SUM, TrigForm.Choice.parse("sum").next(TrigForm.SUM));
        assertEquals(TrigForm.SUM, TrigForm.Choice.parse("SUM").next(TrigForm.PRODUCT));
        assertEquals(TrigForm.PRODUCT, TrigForm.Choice.parse("flip").next(TrigForm.SUM));
        assertEquals(TrigForm.SUM, TrigForm.Choice.parse("flip").next(TrigForm.PRODUCT));
        assertNotNull(TrigForm.Choice.parse("random").next(TrigForm.SUM));
    }

    @Test
    void badInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("one", "2.0"));
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("1", "2", "--mode", "sideways"));
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("1", "2", "--engine", "javassist"));
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("1", "2", "--form", "cosine"));
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("1", "2", "--rounds", "0"));
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("1", "2", "--rounds", "many"));
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("1", "2", "--rounds"));
        assertThrows(IllegalArgumentException.class, () -> SinusCalculation.Options.parse("1", "2", "--verbose"));
    }
}
