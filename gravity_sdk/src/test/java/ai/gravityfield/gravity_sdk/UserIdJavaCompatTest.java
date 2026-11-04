package ai.gravityfield.gravity_sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.robolectric.RuntimeEnvironment;

import android.os.Looper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Existing Companion/instance access and continuation signatures remain callable from Java. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28})
@LooperMode(LooperMode.Mode.PAUSED)
public class UserIdJavaCompatTest {
    @Test
    public void userIdMethodsKeepTheirJavaSignaturesBeforeInitialize() throws Exception {
        UserIdTestProcess.start();
        assertFalse(GravitySDK.Companion.isInitialized());
        GravitySDK sdk = GravitySDK.Companion.getInstance();
        List<String> heard = new ArrayList<>();
        sdk.setUserIdListener(uid -> {
            heard.add(uid);
            return Unit.INSTANCE;
        });
        android.app.Application context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(context.getPackageName() + "_preferences", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch waiting = new CountDownLatch(2);
        Future<Unit> restored = callers.submit(() -> BuildersKt.<Unit>runBlocking(EmptyCoroutineContext.INSTANCE,
            (scope, continuation) -> {
                Object result = sdk.restoreUserId("java-user", continuation);
                waiting.countDown();
                return result;
            }));
        Future<String> getter = callers.submit(() -> BuildersKt.<String>runBlocking(EmptyCoroutineContext.INSTANCE,
            (scope, continuation) -> {
                Object result = sdk.getUserId(continuation);
                waiting.countDown();
                return result;
            }));
        try {
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            assertFalse(restored.isDone());
            assertFalse(getter.isDone());
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Arrays.asList("java-user"), heard);
            GravitySDK.Companion.initialize(context, "java-key", "java-section",
                event -> Unit.INSTANCE, null, null, null, ai.gravityfield.gravity_sdk.utils.LogLevel.NONE);
            restored.get(5, TimeUnit.SECONDS);
            assertEquals("java-user", getter.get(5, TimeUnit.SECONDS));
            String uid = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> sdk.getUserId(continuation));
            assertEquals("java-user", uid);
            BuildersKt.<Unit>runBlocking(EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> sdk.resetUser(continuation));
            String cleared = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> sdk.getUserId(continuation));
            assertNull(cleared);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Arrays.asList("java-user", null), heard);
        } finally {
            if (!GravitySDK.Companion.isInitialized()) {
                GravitySDK.Companion.initialize(context, "java-key", "java-section",
                    event -> Unit.INSTANCE, null, null, null, ai.gravityfield.gravity_sdk.utils.LogLevel.NONE);
            }
            restored.cancel(true);
            getter.cancel(true);
            callers.shutdownNow();
            sdk.setUserIdListener(null);
            GravitySDK.Companion.getInstance().dispose();
            UserIdTestProcess.stop(GravitySDK.Companion.getInstance());
        }
    }
}
