package android.server.wm;

import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.DreamManager;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.Context;
import android.os.PowerManager;

import org.junit.rules.ExternalResource;

/**
 * TestRule to ensure the device is awake and unlocked (if possible) before running a test.
 * <p>
 * Note: This adopts/drops Shell permission identity. Make sure this runs before any other instances
 * of adopting Shell permissions as this might otherwise interfere with permissions in the test.
 */
public class WakeUpAndUnlockRule extends ExternalResource {

    @Override
    protected void before() throws Throwable {
        super.before();
        UiDeviceUtils.wakeUpAndUnlock(getApplicationContext());
    }
}
