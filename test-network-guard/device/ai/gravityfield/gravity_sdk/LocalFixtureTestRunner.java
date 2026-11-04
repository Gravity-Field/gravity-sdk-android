package ai.gravityfield.gravity_sdk;

import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;
import ai.gravityfield.testguard.NetworkGuard;
import java.io.File;

/** All device tests use a loopback deny proxy and disabled automatic diagnostics. */
public final class LocalFixtureTestRunner extends AndroidJUnitRunner {
    @Override public void onCreate(Bundle arguments) {
        System.setProperty("xreview.guard.file", new File(getTargetContext().getFilesDir(), "test-network-guard.jsonl").getAbsolutePath());
        NetworkGuard.install(); NetworkGuard.disableLogger();
        Bundle guarded = arguments == null ? new Bundle() : new Bundle(arguments);
        String previous = guarded.getString("listener", "");
        guarded.putString("listener", NetworkGuardRunListener.class.getName() + (previous.isEmpty() ? "" : "," + previous));
        super.onCreate(guarded);
    }
}
