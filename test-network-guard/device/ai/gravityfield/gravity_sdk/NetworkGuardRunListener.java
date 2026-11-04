package ai.gravityfield.gravity_sdk;

import ai.gravityfield.testguard.NetworkGuard;
import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

public final class NetworkGuardRunListener extends RunListener {
    @Override public void testStarted(Description description) {
        NetworkGuard.currentTest(description.getClassName());
        NetworkGuard.install(); NetworkGuard.disableLogger();
    }
}
