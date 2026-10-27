package ai.gravityfield.gravity_sdk.models;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The six-field constructor that existed before raw variables keeps working from Java. */
public class VariablesJavaCompatTest {
    @Test
    public void legacyConstructorAndCopyStillExist() {
        Variables variables = new Variables(null, null, null, null, null, null);
        Variables copy = variables.copy(null, null, null, null, null, null);
        assertTrue(copy.getRaw().isEmpty());
        assertNull(variables.get("anything"));
    }
}
