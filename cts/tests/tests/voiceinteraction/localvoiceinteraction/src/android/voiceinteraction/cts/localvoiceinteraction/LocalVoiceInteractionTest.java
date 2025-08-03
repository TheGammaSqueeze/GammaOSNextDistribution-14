/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.voiceinteraction.cts.localvoiceinteraction;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.voiceinteraction.common.Utils;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SettingsStateChangerRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class LocalVoiceInteractionTest {

    private static final String TAG = LocalVoiceInteractionTest.class.getSimpleName();
    private static final int TIMEOUT_MS = 20 * 1000;

    private final Context mContext = getInstrumentation().getTargetContext();
    private final CountDownLatch mLatchStart = new CountDownLatch(1);
    private final CountDownLatch mLatchStop = new CountDownLatch(1);

    @Rule
    public final SettingsStateChangerRule mServiceSetterRule = new SettingsStateChangerRule(
            mContext, Settings.Secure.VOICE_INTERACTION_SERVICE, Utils.SERVICE_NAME);

    @Rule
    public final ActivityScenarioRule<TestLocalInteractionActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(TestLocalInteractionActivity.class);

    @Before
    public void prepareDevice() throws Exception {
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");

        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");
    }

    @Test
    public void testLifecycle() throws Exception {
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            assertWithMessage("Doesn't support LocalVoiceInteraction")
                    .that(activity.isLocalVoiceInteractionSupported()).isTrue();
            activity.startLocalInteraction(mLatchStart);
        });

        if (!mLatchStart.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to start voice interaction in " + TIMEOUT_MS + "msec");
            return;
        }

        mActivityScenarioRule.getScenario().onActivity(activity -> {
            activity.stopLocalInteraction(mLatchStop);
        });

        if (!mLatchStop.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to stop voice interaction in " + TIMEOUT_MS + "msec");
            return;
        }
    }

    @Test
    @AppModeFull(reason = "CtsVoiceInteractionService is installed as non-instant mode")
    public void testGrantVisibilityOnStartLocalInteraction() throws Exception {
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            assertWithMessage("Doesn't support LocalVoiceInteraction")
                    .that(activity.isLocalVoiceInteractionSupported()).isTrue();
            assertNoDefaultPackageVisibility(activity);
            activity.startLocalInteraction(mLatchStart);
        });

        if (!mLatchStart.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to start voice interaction in " + TIMEOUT_MS + "msec");
            return;
        }

        mActivityScenarioRule.getScenario().onActivity(activity -> {
            assertWithMessage("Visibility is not granted to the client app")
                    .that(activity.getVoiceInteractionPackageInfo()).isNotNull();
            activity.stopLocalInteraction(mLatchStop);
        });

        if (!mLatchStop.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to stop voice interaction in " + TIMEOUT_MS + "msec");
            return;
        }
    }

    private void assertNoDefaultPackageVisibility(Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo(Utils.TEST_VOICE_INTERACTION_SERVICE_PACKAGE_NAME,
                    PackageManager.GET_META_DATA | PackageManager.GET_SERVICES);
            fail("Visibility is granted to the client app in default");
        } catch (PackageManager.NameNotFoundException expected) {
            // no visibility for the service. It is expected.
        }
    }
}
