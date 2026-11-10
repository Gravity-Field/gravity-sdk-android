package ai.gravityfield.gravity_sdk.models;

import ai.gravityfield.gravity_sdk.network.Campaign;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GravityDataResponseJavaCompatTest {
    @Test public void delayRemainsFinalWithNoPublicSetter() throws Exception {
        assertTrue(java.lang.reflect.Modifier.isFinal(
            GravityDataResponse.class.getDeclaredField("delayTime").getModifiers()));
        for (java.lang.reflect.Method method : GravityDataResponse.class.getMethods()) {
            assertFalse("delayTime must be immutable", method.getName().equals("setDelayTime"));
        }
    }

    @Test public void legacyConstructorsKeepWorkingAndNewDelayIsReadable() {
        GravityDataResponse<String> legacy = new GravityDataResponse<>("content", Collections.emptyMap());
        assertEquals(0, legacy.getDelayTime());
        GravityDataResponse<String> delayed = new GravityDataResponse<>("content", Collections.emptyMap(), 1500);
        assertEquals(1500, delayed.getDelayTime());
        assertEquals(-1500, new GravityDataResponse<>("content", Collections.emptyMap(), -1500).getDelayTime());
        ai.gravityfield.gravity_sdk.network.CampaignId publicCampaign =
            new ai.gravityfield.gravity_sdk.network.CampaignId("c", "", Integer.MAX_VALUE, 0);
        Integer publicPriority = publicCampaign.getPriority();
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), publicPriority);
        // Resolving both constructors also checks Java source compatibility of load callbacks.
        try {
            ContentLoadEvent.class.getConstructor(CampaignContent.class, Campaign.class);
            ContentLoadEvent.class.getConstructor(CampaignContent.class, Campaign.class, int.class);
        } catch (NoSuchMethodException missingConstructor) {
            throw new AssertionError(missingConstructor);
        }
    }
}
