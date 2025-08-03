/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.DreamCoordinator;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import static com.google.common.truth.Truth.assertThat;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DreamOverlayTest extends ActivityManagerTestBase {
    private static final String DREAM_OVERLAY_SERVICE_COMPONENT =
            "android.app.dream.cts.app/.DreamOverlayService";
    private static final String DREAM_SERVICE_COMPONENT =
            "android.app.dream.cts.app/.TestDreamService";
    private static final String ACTION_DREAM_OVERLAY_SHOWN =
            "android.app.dream.cts.app.action.overlay_shown";
    private static final String ACTION_DREAM_OVERLAY_REMOVED =
            "android.app.dream.cts.app.action.overlay_removed";

    private static final int TIMEOUT_SECONDS = 5;

    private DreamCoordinator mDreamCoordinator = new DreamCoordinator(mContext);

    /**
     * A simple {@link BroadcastReceiver} implementation that counts down a
     * {@link CountDownLatch} when a matching message is received
     */
    static final class OverlayVisibilityReceiver extends BroadcastReceiver {
        final CountDownLatch mLatch;

        OverlayVisibilityReceiver(CountDownLatch latch) {
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
        mDreamCoordinator.setDreamOverlay(ComponentName.unflattenFromString(
                DREAM_OVERLAY_SERVICE_COMPONENT));
    }

    @After
    public void reset()  {
        mDreamCoordinator.setDreamOverlay(null);
        mDreamCoordinator.restoreDefaults();
    }

    @Test
    public void testDreamOverlayAppearance() throws InterruptedException {
        Assume.assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));

        // Listen for the overlay to be shown
        final CountDownLatch shownCountDownLatch = new CountDownLatch(1);
        mContext.registerReceiver(
                new OverlayVisibilityReceiver(shownCountDownLatch),
                new IntentFilter(ACTION_DREAM_OVERLAY_SHOWN),
                Context.RECEIVER_EXPORTED);

        final ComponentName dreamService =
                ComponentName.unflattenFromString(DREAM_SERVICE_COMPONENT);
        final ComponentName dreamActivity = mDreamCoordinator.setActiveDream(dreamService);

        mDreamCoordinator.startDream();
        waitAndAssertTopResumedActivity(dreamActivity, Display.DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        // Wait on count down latch for overlay being added.
        assertThat(shownCountDownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Listen for the overlay to be removed
        final CountDownLatch removedCountDownLatch = new CountDownLatch(1);
        mContext.registerReceiver(
                new OverlayVisibilityReceiver(removedCountDownLatch),
                new IntentFilter(ACTION_DREAM_OVERLAY_REMOVED),
                Context.RECEIVER_EXPORTED);

        mDreamCoordinator.stopDream();
        // Wait on count down latch for overlay being removed.
        assertThat(removedCountDownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
}
