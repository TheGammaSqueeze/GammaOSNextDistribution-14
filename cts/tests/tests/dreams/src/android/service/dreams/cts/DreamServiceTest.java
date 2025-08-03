/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.service.dreams.cts;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.server.wm.CliIntentExtra.extraString;
import static android.server.wm.app.Components.PIP_ACTIVITY;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP_ON_PAUSE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.DreamCoordinator;
import android.service.dreams.DreamService;
import android.view.ActionMode;
import android.view.Display;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DreamServiceTest extends ActivityManagerTestBase {
    private static final int TIMEOUT_SECONDS = 2;
    private static final String DREAM_SERVICE_COMPONENT =
            "android.app.dream.cts.app/.SeparateProcessDreamService";

    private final DreamCoordinator mDreamCoordinator = new DreamCoordinator(mContext);

    /**
     * A simple {@link BroadcastReceiver} implementation that counts down a
     * {@link CountDownLatch} when a matching message is received
     */
    static final class DreamBroadcastReceiver extends BroadcastReceiver {
        final CountDownLatch mLatch;

        DreamBroadcastReceiver(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mLatch.countDown();
        }
    }

    @Before
    public void setup() {
        mDreamCoordinator.setup();
    }

    @After
    public void reset() {
        mDreamCoordinator.restoreDefaults();
    }

    @Test
    public void testOnWindowStartingActionMode() {
        DreamService dreamService = new DreamService();

        ActionMode actionMode = dreamService.onWindowStartingActionMode(null);

        assertEquals(actionMode, null);
    }

    @Test
    public void testOnWindowStartingActionModeTyped() {
        DreamService dreamService = new DreamService();

        ActionMode actionMode = dreamService.onWindowStartingActionMode(
                null, ActionMode.TYPE_FLOATING);

        assertEquals(actionMode, null);
    }

    @Test
    public void testDreamInSeparateProcess() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));

        final ComponentName dreamService =
                ComponentName.unflattenFromString(DREAM_SERVICE_COMPONENT);
        final ComponentName dreamActivity = mDreamCoordinator.setActiveDream(dreamService);

        mDreamCoordinator.startDream();
        waitAndAssertTopResumedActivity(dreamActivity, Display.DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        mDreamCoordinator.stopDream();
    }

    @Test
    public void testMetadataParsing() throws PackageManager.NameNotFoundException {
        final String dreamComponent = "android.app.dream.cts.app/.TestDreamService";
        final String testSettingsActivity =
                "android.app.dream.cts.app/.TestDreamSettingsActivity";
        final DreamService.DreamMetadata metadata = getDreamMetadata(dreamComponent);

        assertThat(metadata.settingsActivity).isEqualTo(
                ComponentName.unflattenFromString(testSettingsActivity));
        assertThat(metadata.showComplications).isFalse();
    }

    @Test
    public void testMetadataParsing_invalidSettingsActivity()
            throws PackageManager.NameNotFoundException {
        final String dreamComponent =
                "android.app.dream.cts.app/.TestDreamServiceWithInvalidSettings";
        final DreamService.DreamMetadata metadata = getDreamMetadata(dreamComponent);

        assertThat(metadata.settingsActivity).isNull();
    }

    private DreamService.DreamMetadata getDreamMetadata(String dreamComponent)
            throws PackageManager.NameNotFoundException {
        final ServiceInfo si = mContext.getPackageManager().getServiceInfo(
                ComponentName.unflattenFromString(dreamComponent),
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA));
        return DreamService.getDreamMetadata(mContext, si);
    }

    @Test
    public void testDreamServiceOnDestroyCallback() throws InterruptedException {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));

        final ComponentName dreamService =
                ComponentName.unflattenFromString(DREAM_SERVICE_COMPONENT);
        final ComponentName dreamActivity = mDreamCoordinator.setActiveDream(dreamService);

        mDreamCoordinator.startDream();
        waitAndAssertTopResumedActivity(dreamActivity, Display.DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");

        removeRootTasksWithActivityTypes(ACTIVITY_TYPE_DREAM);

        // Listen for the dream to end
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mContext.registerReceiver(
                new DreamBroadcastReceiver(countDownLatch),
                new IntentFilter(Intent.ACTION_DREAMING_STOPPED));
        assertThat(countDownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertFalse("DreamService is still dreaming", mDreamCoordinator.isDreaming());
        mDreamCoordinator.stopDream();
    }

    @Test
    public void testDreamDoesNotForcePictureInPicture() {
        assumeTrue(supportsPip());

        // Launch a PIP activity
        launchActivity(PIP_ACTIVITY, extraString(EXTRA_ENTER_PIP_ON_PAUSE, "true"));

        // Asserts that the pinned stack does not exist.
        mWmState.assertDoesNotContainStack("Must not contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);

        final ComponentName dreamService =
                ComponentName.unflattenFromString(DREAM_SERVICE_COMPONENT);
        final ComponentName dreamActivity = mDreamCoordinator.setActiveDream(dreamService);
        mDreamCoordinator.startDream();
        waitAndAssertTopResumedActivity(dreamActivity, Display.DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        mDreamCoordinator.stopDream();

        // Asserts that the pinned stack does not exist.
        mWmState.assertDoesNotContainStack("Must not contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
    }
}
