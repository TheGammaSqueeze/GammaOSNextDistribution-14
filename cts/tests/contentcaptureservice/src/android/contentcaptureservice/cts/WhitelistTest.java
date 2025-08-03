/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.contentcaptureservice.cts.Helper.EXTRA_VERIFY_RESULT;
import static android.contentcaptureservice.cts.Helper.MY_PACKAGE;
import static android.contentcaptureservice.cts.Helper.NO_ACTIVITIES;
import static android.contentcaptureservice.cts.Helper.NO_PACKAGES;
import static android.contentcaptureservice.cts.Helper.sContext;
import static android.contentcaptureservice.cts.Helper.toSet;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.contentcapture.ContentCaptureService;
import android.support.test.uiautomator.UiDevice;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Set;

/**
 * Tests for {@link ContentCaptureService#setContentCaptureWhitelist(Set, Set)}.
 *
 * <p><b>NOTE</b>: not all cases are supported because in some of them the test package is
 * allowlisted in 'lite' mode as it's the same as the service's.
 */
@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class WhitelistTest extends AbstractContentCaptureIntegrationActivityLessTest {
    public final ServiceTestRule mServiceRule = new ServiceTestRule();
    private UiDevice mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Ignore("will be whitelisted 'lite'")
    @Test
    public void testNothingWhitelisted() throws Exception {
        final CtsContentCaptureService service = enableService(NO_PACKAGES, NO_ACTIVITIES);
        assertNotAllowlisted(service);
    }

    @Ignore("will be whitelisted 'lite'")
    @Test
    public void testNotWhitelisted_byPackage() throws Exception {
        final CtsContentCaptureService service = enableService(toSet("not.me"), NO_ACTIVITIES);
        assertNotAllowlisted(service);
    }

    @Test
    public void testNotWhitelisted_byActivity() throws Exception {
        final CtsContentCaptureService service = enableService(NO_PACKAGES,
                toSet(ComponentName.createRelative(MY_PACKAGE, ".who.cares")));
        assertNotAllowlisted(service);
    }

    @Test
    public void testWhitelisted_byPackage() throws Exception {
        final CtsContentCaptureService service = enableService(toSet(MY_PACKAGE), NO_ACTIVITIES);
        assertAllowlisted(service);
    }

    @Test
    public void testWhitelisted_byActivity() throws Exception {
        final CtsContentCaptureService service = enableService(NO_PACKAGES,
                toSet(OutOfProcessActivity.COMPONENT_NAME));
        assertAllowlisted(service);
    }

    @Test
    public void testWhitelisted_byService_alreadyRunning() throws Exception {
        IOutOfProcessDataSharingService service = getDataShareService();

        // enable service and set allowlist package
        enableService(toSet(MY_PACKAGE), NO_ACTIVITIES);

        // Wait for update to propagate. We can only get the manager if the service allowlist
        // the activity or package. Because we don't have a signal to know if the allowlist is set.
        // So try to check ContentCaptureManager available within a given timeout.
        Helper.eventually("Can not get ContentCaptureManager",
                () -> service.isApplicationContentCaptureManagerAvailable()
                    ? "can get ContentCaptureManager" : null);

        assertContentCaptureManagerAvailable(service, true);
    }

    @Test
    public void testRinseAndRepeat() throws Exception {
        // Right package
        final CtsContentCaptureService service = enableService(toSet(MY_PACKAGE), NO_ACTIVITIES);
        assertAllowlisted(service);

        // Wrong activity
        service.setContentCaptureWhitelist(NO_PACKAGES,
                toSet(ComponentName.createRelative(MY_PACKAGE, ".who.cares")));
        assertNotAllowlisted(service);

        // Right activity
        service.setContentCaptureWhitelist(NO_PACKAGES, toSet(OutOfProcessActivity.COMPONENT_NAME));
        assertAllowlisted(service);
    }

    private void assertAllowlisted(@NonNull CtsContentCaptureService service) throws Exception {
        launchActivityAndAssert(service, /* expectHasManager= */ true);
    }

    private void assertNotAllowlisted(@NonNull CtsContentCaptureService service) throws Exception {
        launchActivityAndAssert(service, /* expectHasManager= */ false);
    }

    private void assertContentCaptureManagerAvailable(IOutOfProcessDataSharingService service,
            boolean isAvailable) throws Exception {
        try {
            assertThat(service.isContentCaptureManagerAvailable()).isEqualTo(isAvailable);
        } finally {
            OutOfProcessActivity.killOutOfProcessActivity();
        }
    }

    private void launchActivityAndAssert(@NonNull CtsContentCaptureService service,
            boolean expectHasManager) {
        final String actionActivityFinish =
                "ACTION_ACTIVITY_FINISH_" + SystemClock.uptimeMillis();
        final BlockingBroadcastReceiver receiver =
                new BlockingBroadcastReceiver(sContext, actionActivityFinish);
        receiver.register();

        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        sContext,
                        0,
                        new Intent(actionActivityFinish).setPackage(sContext.getPackageName()),
                        PendingIntent.FLAG_MUTABLE);
        OutOfProcessActivity.launchOutOfProcessActivity(pendingIntent,
                OutOfProcessActivity.ACTION_CHECK_MANAGER_AND_FINISH,
                OutOfProcessActivity.EXTRA_FINISH_BROADCAST);

        try {
            // Wait received result
            final Intent intent = receiver.awaitForBroadcast();
            assertThat(intent).isNotNull();

            String result = intent.getStringExtra(EXTRA_VERIFY_RESULT);
            if (expectHasManager) {
                assertThat(result).startsWith(OutOfProcessActivity.HAS_MANAGER);
            } else {
                assertThat(result).isEqualTo(OutOfProcessActivity.NO_MANAGER);
            }
        } finally {
            // Must kill process, otherwise next call might fail because of the cached
            // CaptureOptions state in the application context
            service.setIgnoreOrphanSessionEvents(true); //
            OutOfProcessActivity.killOutOfProcessActivity();
            // unregister receiver
            receiver.unregisterQuietly();
        }
    }

    private IOutOfProcessDataSharingService getDataShareService() throws Exception {
        Intent outsideService = new Intent();
        outsideService.setComponent(new ComponentName(
                "android.contentcaptureservice.cts",
                "android.contentcaptureservice.cts.OutOfProcessDataSharingService"
        ));
        IBinder service = mServiceRule.bindService(outsideService);
        return IOutOfProcessDataSharingService.Stub.asInterface(service);
    }
}
