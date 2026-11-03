import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrigFormTest {

    @Test
    void bothFormsAreTheSameFunction() {
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(7L);
        for (int i = 0; i < 1_000; i++) {
            double x = random.nextDouble(-50.0, 50.0);
            double y = random.nextDouble(-50.0, 50.0);
            assertEquals(TrigForm.SUM.reference(x, y), TrigForm.PRODUCT.reference(x, y), 1e-9,
                    () -> "at " + x + ", " + y);
        }
    }

    @Test
    void everyProgramEndsWithAReturnAndNothingElse() {
        for (TrigForm form : TrigForm.values()) {
            var program = form.program();
            assertEquals(Insn.RET, program.getLast());
            assertEquals(1, program.stream().filter(Insn.RET::equals).count(), form.name());
        }
    }

    @Test
    void loadArgOnlyAcceptsTheParameterSlots() {
        assertEquals(1, new Insn.LoadArg(1).slot());
        assertEquals(3, new Insn.LoadArg(3).slot());
        assertThrows(IllegalArgumentException.class, () -> new Insn.LoadArg(0));
        assertThrows(IllegalArgumentException.class, () -> new Insn.LoadArg(2));
    }

    @Test
    void parseIsCaseInsensitiveAndStrict() {
        assertEquals(TrigForm.SUM, TrigForm.parse("Sum"));
        assertEquals(TrigForm.PRODUCT, TrigForm.parse("PRODUCT"));
        assertThrows(IllegalArgumentException.class, () -> TrigForm.parse("tangent"));
    }
}
