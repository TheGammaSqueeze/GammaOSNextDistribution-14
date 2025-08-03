/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.contentcaptureservice.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.contentcaptureservice.cts.CtsContentCaptureService.CONTENT_CAPTURE_SERVICE_COMPONENT_NAME;
import static android.contentcaptureservice.cts.Helper.MY_PACKAGE;
import static android.contentcaptureservice.cts.Helper.MY_SECOND_PACKAGE;
import static android.contentcaptureservice.cts.Helper.NO_ACTIVITIES;
import static android.contentcaptureservice.cts.Helper.resetService;
import static android.contentcaptureservice.cts.Helper.sContext;
import static android.contentcaptureservice.cts.Helper.toSet;

import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ActivitiesWatcher.ActivityWatcher;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class BlankActivityTest
        extends AbstractContentCaptureIntegrationAutoActivityLaunchTest<BlankActivity> {

    private static final String TAG = BlankActivityTest.class.getSimpleName();

    private static final ActivityTestRule<BlankActivity> sActivityRule = new ActivityTestRule<>(
            BlankActivity.class, false, false);

    private UiDevice mDevice;

    public BlankActivityTest() {
        super(BlankActivity.class);
    }

    @Override
    protected ActivityTestRule<BlankActivity> getActivityTestRule() {
        return sActivityRule;
    }

    @Before
    public void setup() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(instrumentation);
    }

    @Test
    public void testSimpleSessionLifecycle() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final BlankActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        activity.assertDefaultEvents(session);
    }

    @Test
    public void testGetServiceComponentName() throws Exception {
        final CtsContentCaptureService service = enableService();
        service.waitUntilConnected();

        final ActivityWatcher watcher = startWatcher();

        final BlankActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        try {
            assertThat(activity.getContentCaptureManager().getServiceComponentName())
                    .isEqualTo(CONTENT_CAPTURE_SERVICE_COMPONENT_NAME);

            resetService();
            service.waitUntilDisconnected();

            assertThat(activity.getContentCaptureManager().getServiceComponentName())
                    .isNotEqualTo(CONTENT_CAPTURE_SERVICE_COMPONENT_NAME);
        } finally {
            activity.finish();
            watcher.waitFor(DESTROYED);
        }
    }

    @Test
    public void testGetServiceComponentName_onUiThread() throws Exception {
        final CtsContentCaptureService service = enableService();
        service.waitUntilConnected();

        final ActivityWatcher watcher = startWatcher();

        final BlankActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final AtomicReference<ComponentName> ref = new AtomicReference<>();
        activity.syncRunOnUiThread(
                () -> ref.set(activity.getContentCaptureManager().getServiceComponentName()));

        activity.finish();
        watcher.waitFor(DESTROYED);

        assertThat(ref.get()).isEqualTo(CONTENT_CAPTURE_SERVICE_COMPONENT_NAME);
    }

    @Test
    public void testIsContentCaptureFeatureEnabled_onUiThread() throws Exception {
        final CtsContentCaptureService service = enableService();
        service.waitUntilConnected();

        final ActivityWatcher watcher = startWatcher();

        final BlankActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final AtomicBoolean ref = new AtomicBoolean();
        activity.syncRunOnUiThread(() -> ref
                .set(activity.getContentCaptureManager().isContentCaptureFeatureEnabled()));

        activity.finish();
        watcher.waitFor(DESTROYED);

        assertThat(ref.get()).isTrue();
    }

    @Test
    public void testDisableContentCaptureService_onUiThread() throws Exception {
        final CtsContentCaptureService service = enableService();
        service.waitUntilConnected();

        final ActivityWatcher watcher = startWatcher();

        final BlankActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        service.disableSelf();

        activity.finish();
        watcher.waitFor(DESTROYED);
    }

    @Test
    public void testOnConnectionEvents() throws Exception {
        final CtsContentCaptureService service = enableService();
        service.waitUntilConnected();

        resetService();
        service.waitUntilDisconnected();
    }

    @Test
    public void testOutsideOfPackageActivity_noSessionCreated() throws Exception {
        final CtsContentCaptureService service = enableService();

        startOutsideActivity(/* finishActivity= */ true);

        mDevice.waitForIdle();

        assertThat(service.getAllSessionIds()).isEmpty();
    }

    @Ignore("b/267743222")
    @Test
    public void testOutsideOfPackageContentCaptureEnable_updateAllowList() throws Exception {
        // Enable Service but not allowlist OutsideOfPackage
        final CtsContentCaptureService service = enableService();

        // Because the OutsideOfPackage isn't allow-listed so no session will be started finally.
        // We need to finish OutsideOfPackage activity so the next MainContentCaptureSession will
        // be updated for the OutsideOfPackage's ContentCaptureManager
        Log.d(TAG, "startActivity 1st time");
        final String action = "ACTION_ACTIVITY_CC_STATUS_TEST";
        // register a receiver to receive result
        BlockingBroadcastReceiver receiver = registerResultReceiver();
        startOutsideActivity(/* finishActivity= */ true);

        try {
            // Verify the finish broadcast is received.
            assertContentCaptureEnableStatus(receiver.awaitForBroadcast(),
                    /* expectedStatus= */ false, /* defaultValue= */ true);
        } finally {
            // unregister receiver
            receiver.unregisterQuietly();
        }
        // Allow-lists the OutsideOfPackage
        service.setContentCaptureWhitelist(
                toSet(MY_PACKAGE, MY_SECOND_PACKAGE), NO_ACTIVITIES);
        // Wait for the IPC to take effect.
        Thread.sleep(2_000);
        // would like to verify removing the package from allowlist, the enabled status should
        // be updated. We need to keep session, so test doesn't finish the OutsideOfPackage Activity
        Log.d(TAG, "startActivity 2nd time");
        BlockingBroadcastReceiver receiver2 = registerResultReceiver();
        startOutsideActivity(/* finishActivity= */ false);

        try {
            // Verify the cc enable status broadcast is received.
            assertContentCaptureEnableStatus(receiver2.awaitForBroadcast(),
                    /* expectedStatus= */ true, /* defaultValue= */ false);
        } finally {
            receiver2.unregisterQuietly();
        }
        // Remove OutsideOfPackage from allowlist
        service.setContentCaptureWhitelist(toSet(MY_PACKAGE), NO_ACTIVITIES);
        // Wait for the IPC to take effect.
        Thread.sleep(2_000);
        BlockingBroadcastReceiver receiver3 = registerResultReceiver();
        Log.d(TAG, "startActivity 3rd time");
        startOutsideActivity(/* finishActivity= */ true);

        try {
            // Verify the cc enable status broadcast is received
            assertContentCaptureEnableStatus(receiver3.awaitForBroadcast(),
                    /* expectedStatus= */ false, /* defaultValue= */ true);
        } finally {
            receiver3.unregisterQuietly();
        }
    }

    private BlockingBroadcastReceiver registerResultReceiver() {
        final String action = "ACTION_ACTIVITY_CC_STATUS_TEST";
        BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(sContext, action);
        receiver.register();
        return receiver;
    }

    private void startOutsideActivity(boolean finishActivity) {
        Intent outsideActivity = new Intent();
        outsideActivity.setComponent(new ComponentName("android.contentcaptureservice.cts2",
                "android.contentcaptureservice.cts2.OutsideOfPackageActivity"));
        outsideActivity.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
        outsideActivity.putExtra("finishActivity", finishActivity);
        sContext.startActivity(outsideActivity);
    }

    private void assertContentCaptureEnableStatus(Intent intent, boolean expectedStatus,
            boolean defaultValue) {
        assertThat(intent).isNotNull();
        boolean isEnabled = intent.getBooleanExtra("cc_enable", defaultValue);
        Log.d(TAG, "assertContentCaptureEnableStatus, isEnabled=" + isEnabled + " expectedStatus="
                + expectedStatus);
        assertThat(isEnabled).isEqualTo(expectedStatus);
    }
}
