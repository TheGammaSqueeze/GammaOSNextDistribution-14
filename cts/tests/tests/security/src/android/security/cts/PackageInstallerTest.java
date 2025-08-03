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

package android.security.cts;

import static android.content.Intent.EXTRA_REMOTE_CALLBACK;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallback;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class PackageInstallerTest extends StsExtraBusinessLogicTestCase {

    private static final String TEST_APP_NAME = "android.security.cts.packageinstallertestapp";

    private static final String KEY_ERROR = "key_error";
    private static final String ACTION_COMMIT_WITH_ACTIVITY_INTENT_SENDER = TEST_APP_NAME
            + ".action.COMMIT_WITH_ACTIVITY_INTENT_SENDER";
    private static final String ACTION_COMMIT_WITH_FG_SERVICE_INTENT_SENDER = TEST_APP_NAME
            + ".action.COMMIT_WITH_FG_SERVICE_INTENT_SENDER";

    static final long DEFAULT_TIMEOUT_MS = isLowRamDevice()
            ? TimeUnit.SECONDS.toMillis(60) : TimeUnit.SECONDS.toMillis(15);

    private static final TestApp TEST_APP = new TestApp(
            "PackageInstallerTestApp", TEST_APP_NAME, 1, /*isApex*/ false,
            "PackageInstallerTestApp.apk");

    private static Context sContext = InstrumentationRegistry.getInstrumentation().getContext();
    private static HandlerThread sResponseThread;
    private static Handler sHandler;

    private static final ComponentName BACKGROUND_RECEIVER_COMPONENT_NAME =
            ComponentName.createRelative(TEST_APP_NAME, ".BackgroundReceiver");
    private static final ComponentName BACKGROUND_LAUNCH_ACTIVITY_COMPONENT_NAME =
            new ComponentName(sContext, BackgroundLaunchActivity.class);
    private static final ComponentName FOREGROUND_SERVICE_COMPONENT_NAME =
            new ComponentName(sContext, TestForegroundService.class);

    @BeforeClass
    public static void onBeforeClass() {
        sResponseThread = new HandlerThread("response");
        sResponseThread.start();
        sHandler = new Handler(sResponseThread.getLooper());
    }

    @AfterClass
    public static void onAfterClass() {
        sResponseThread.quit();
    }

    @Before
    public void setUp() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.DELETE_PACKAGES,
                        Manifest.permission.PACKAGE_VERIFICATION_AGENT,
                        Manifest.permission.BIND_PACKAGE_VERIFIER);
    }

    @After
    public void tearDown() throws Exception {
        Uninstall.packages(TestApp.A, TEST_APP_NAME);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    @AsbSecurityTest(cveBugId = 138650665)
    public void verificationCanNotBeDisabledByInstaller() throws Exception {
        // Enable the verifier, and check that INSTALL_DISABLE_VERIFICATION flag takes no effect
        Install.single(TEST_APP).enableVerifier().addInstallFlags(
                0x00080000 /* PackageManager.INSTALL_DISABLE_VERIFICATION */).commit();
        String packageName = PackageVerificationsBroadcastReceiver.packages.poll(30,
                TimeUnit.SECONDS);
        Assert.assertNotNull("Did not receive broadcast", packageName);
        Assert.assertEquals(TEST_APP_NAME, packageName);
    }

    @Test
    @AsbSecurityTest(cveBugId = 230492955)
    public void commitSessionInBackground_withActivityIntentSender_doesNotLaunchActivity()
            throws Exception {
        Install.single(TEST_APP).commit();
        // An activity with the system uid in the foreground is necessary to this test.
        goToSettings();
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final ActivityMonitor monitor = instrumentation.addMonitor(
                BackgroundLaunchActivity.class.getName(), null /* result */, false /* block */);
        try {
            sendActionToBackgroundReceiver(
                    ACTION_COMMIT_WITH_ACTIVITY_INTENT_SENDER,
                    BACKGROUND_LAUNCH_ACTIVITY_COMPONENT_NAME);

            final Activity activity = monitor.waitForActivityWithTimeout(DEFAULT_TIMEOUT_MS);
            if (activity != null) {
                instrumentation.runOnMainSync(() -> activity.finish());
            }
            Assert.assertNull(activity);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 243377226)
    public void commitSessionInBackground_withForegroundServiceIntentSender_doesNotStartService()
            throws Exception {
        Install.single(TEST_APP).commit();
        // An activity with the system uid in the foreground is necessary to this test.
        goToSettings();

        sendActionToBackgroundReceiver(
                ACTION_COMMIT_WITH_FG_SERVICE_INTENT_SENDER, FOREGROUND_SERVICE_COMPONENT_NAME);

        final Service service =
                TestForegroundService.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (service != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> service.stopSelf());
        }
        Assert.assertNull(service);
    }

    private void goToSettings() {
        SystemUtil.runShellCommand(
                "am start -W --user current -a " + Settings.ACTION_SETTINGS);
    }

    private Bundle sendActionToBackgroundReceiver(String action, ComponentName statusReceiver)
            throws Exception {
        final Intent intent = new Intent(action)
                .setComponent(BACKGROUND_RECEIVER_COMPONENT_NAME);
        if (statusReceiver != null) {
            intent.putExtra(Intent.EXTRA_COMPONENT_NAME, statusReceiver);
        }
        final ConditionVariable latch = new ConditionVariable();
        final AtomicReference<Bundle> resultReference = new AtomicReference<>();
        final RemoteCallback remoteCallback = new RemoteCallback(
                bundle -> {
                    resultReference.set(bundle);
                    latch.open();
                },
                sHandler);
        intent.putExtra(EXTRA_REMOTE_CALLBACK, remoteCallback);
        sContext.sendBroadcast(intent);

        if (!latch.block(DEFAULT_TIMEOUT_MS)) {
            throw new TimeoutException(
                    "Latch timed out while awaiting a response from background receiver");
        }
        final Bundle bundle = resultReference.get();
        if (bundle != null && bundle.containsKey(KEY_ERROR)) {
            throw Objects.requireNonNull(bundle.getSerializable(KEY_ERROR, Exception.class));
        }
        return bundle;
    }

    private static boolean isLowRamDevice() {
        return InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(ActivityManager.class).isLowRamDevice();
    }

    // An activity to receive status of a committed session
    public static class BackgroundLaunchActivity extends Activity {
    }
}
