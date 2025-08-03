/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static android.service.controls.flags.Flags.FLAG_HOME_PANEL_DREAM;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.DreamCoordinator;
import android.server.wm.WindowManagerState.Task;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@ApiTest(apis = {"com.android.server.dreams.DreamManagerService#setSystemDreamComponent"})
@RunWith(AndroidJUnit4.class)
@SmallTest
@Ignore("b/325686454")
public class SystemDreamTest extends ActivityManagerTestBase {
    private static final ComponentName USER_DREAM_COMPONENT =
            ComponentName.unflattenFromString("android.app.dream.cts.app/.TestDreamService");
    private static final ComponentName SYSTEM_DREAM_COMPONENT =
            ComponentName.unflattenFromString("android.app.dream.cts.app/.TestSystemDreamService");
    private static final String ACTION_DREAM_SHOWN =
            "android.app.dream.cts.app.action.dream_shown";
    private static final String ACTION_SYSTEM_DREAM_SHOWN =
            "android.app.dream.cts.app.action.system_dream_shown";
    private static final ComponentName DREAM_ACTIVITY =
            ComponentName.unflattenFromString(
                    "android.app.dream.cts.app/android.service.dreams.DreamActivity");

    private final DreamCoordinator mDreamCoordinator = new DreamCoordinator(mContext);

    /**
     * A simple {@link BroadcastReceiver} implementation that counts down a
     * {@link CountDownLatch} when a matching message is received
     */
    private static final class DreamShownReceiver extends BroadcastReceiver {
        private static final int TIMEOUT_SECONDS = 5;

        private final CountDownLatch mLatch;

        DreamShownReceiver() {
            mLatch = new CountDownLatch(1);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mLatch.countDown();
        }

        Boolean waitUntilShown() {
            try {
                return mLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }


    @Before
    public void setup() {
        mDreamCoordinator.setup();
        mDreamCoordinator.setActiveDream(USER_DREAM_COMPONENT);
    }

    @After
    public void reset() {
        mDreamCoordinator.stopDream();
        mDreamCoordinator.setSystemDream(null);
        mDreamCoordinator.restoreDefaults();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HOME_PANEL_DREAM)
    public void startDream_systemDreamNotSet_startUserDream() {
        final DreamShownReceiver receiver = registerReceiver(ACTION_DREAM_SHOWN);
        mDreamCoordinator.startDream();
        assertDreamShown(receiver);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HOME_PANEL_DREAM)
    public void startDream_systemDreamSet_startSystemDream() {
        final DreamShownReceiver receiver = registerReceiver(ACTION_SYSTEM_DREAM_SHOWN);
        mDreamCoordinator.setSystemDream(SYSTEM_DREAM_COMPONENT);
        mDreamCoordinator.startDream();
        assertDreamShown(receiver);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HOME_PANEL_DREAM)
    public void switchDream_systemDreamSet_switchToSystemDream() {
        final DreamShownReceiver userDreamReceiver = registerReceiver(ACTION_DREAM_SHOWN);
        mDreamCoordinator.startDream();
        assertDreamShown(userDreamReceiver);

        final DreamShownReceiver systemDreamReceiver = registerReceiver(ACTION_SYSTEM_DREAM_SHOWN);
        mDreamCoordinator.setSystemDream(SYSTEM_DREAM_COMPONENT);
        assertDreamShown(systemDreamReceiver);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HOME_PANEL_DREAM)
    public void switchDream_systemDreamCleared_switchToUserDream() {
        final DreamShownReceiver systemDreamReceiver = registerReceiver(ACTION_SYSTEM_DREAM_SHOWN);
        mDreamCoordinator.setSystemDream(SYSTEM_DREAM_COMPONENT);
        mDreamCoordinator.startDream();
        assertDreamShown(systemDreamReceiver);

        final DreamShownReceiver userDreamReceiver = registerReceiver(ACTION_DREAM_SHOWN);
        // Clearing system dream should switch back to the user dream.
        mDreamCoordinator.setSystemDream(null);
        assertDreamShown(userDreamReceiver);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HOME_PANEL_DREAM)
    public void switchDream_noDreamActivityWhenDreamStopped() {
        final DreamShownReceiver systemDreamReceiver = registerReceiver(ACTION_SYSTEM_DREAM_SHOWN);
        mDreamCoordinator.setSystemDream(SYSTEM_DREAM_COMPONENT);
        mDreamCoordinator.startDream();
        assertDreamShown(systemDreamReceiver);

        verifyOnlyOneDreamActivity();

        final DreamShownReceiver userDreamReceiver = registerReceiver(ACTION_DREAM_SHOWN);
        mDreamCoordinator.setSystemDream(null);
        assertDreamShown(userDreamReceiver);

        verifyOnlyOneDreamActivity();

        mDreamCoordinator.stopDream();
        mWmState.waitAndAssertActivityRemoved(DREAM_ACTIVITY);
    }

    private DreamShownReceiver registerReceiver(String action) {
        final DreamShownReceiver receiver = new DreamShownReceiver();
        mContext.registerReceiver(
                receiver,
                new IntentFilter(action),
                Context.RECEIVER_EXPORTED
        );
        return receiver;
    }

    private void assertDreamShown(DreamShownReceiver receiver) {
        try {
            assertThat(receiver.waitUntilShown()).isTrue();
            waitAndAssertTopResumedActivity(DREAM_ACTIVITY, Display.DEFAULT_DISPLAY,
                    "Dream activity not resumed");
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    private void verifyOnlyOneDreamActivity() {
        // Only one dream task should exist with one activity.
        assertThat(mWmState.getRootTaskCountWithActivityType(ACTIVITY_TYPE_DREAM)).isEqualTo(1);
        mWmState.waitFor(state -> {
            final Task dreamTask = state.getRootTaskByActivityType(ACTIVITY_TYPE_DREAM);
            return dreamTask.getActivityCount() == 1;
        }, "more than one dream activity");
    }
}
