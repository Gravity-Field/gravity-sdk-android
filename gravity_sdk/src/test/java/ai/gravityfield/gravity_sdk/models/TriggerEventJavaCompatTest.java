package ai.gravityfield.gravity_sdk.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;

import org.junit.Test;

/** Constructor calls that compiled from Java before customProps/eventTime were added. */
public class TriggerEventJavaCompatTest {
    @Test
    public void legacyJavaConstructorsStillExist() {
        new AddToCartEvent(1.0, "p", 1, null, null);
        new RemoveFromCartEvent(1.0, "p", 1, null, null);
        new SyncCartEvent(1.0, null, null);
        new PurchaseEvent("t", 1.0, Collections.emptyList(), null);
        new AddToWishlistEvent(1.0, "p");
        new SignUpEvent(null, "c", null);
        new LoginEvent(null, "c", null);
        CustomEvent custom = new CustomEvent("x", "X", Collections.singletonMap("a", "b"));
        assertEquals("b", custom.getCustomProps().get("a"));
        assertNull(custom.getEventTime());
    }
}
