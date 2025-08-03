/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.cts.builtin.view;

import static android.car.feature.Flags.FLAG_CLUSTER_HEALTH_MONITORING;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.car.builtin.view.SurfaceControlHelper;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.SurfaceControl;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class SurfaceControlHelperTest {
    private static final long TIMEOUT_MS = 5_000;

    private final Instrumentation mInstrumentation = getInstrumentation();
    private final Context mContext = getInstrumentation().getContext();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private TestActivity mTestActivity;

    @After
    public void tearDown() throws Exception {
        if (mTestActivity != null) {
            mTestActivity.finishAndRemoveTask();
            mTestActivity.waitForDestroyed();
            mTestActivity = null;
        }
    }

    @Test
    public void testMirrorSurface() {
        int width = 16;
        int hegiht = 8;
        SurfaceControl source = new SurfaceControl.Builder().setBufferSize(width, hegiht)
                .setFormat(PixelFormat.RGBA_8888)
                .setName("Test Surface")
                .setHidden(false)
                .build();
        SurfaceControl mirror = SurfaceControlHelper.mirrorSurface(source);
        // Just checks SurfaceControlHelper.mirrorSurface() returns the valid SurfaceControl.
        assertThat(mirror).isNotNull();
        assertThat(mirror.isValid()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CLUSTER_HEALTH_MONITORING)
    public void testGetSurfaceControl() throws Exception {
        mTestActivity = launchTestActivity(TestActivity.class);
        SurfaceControl surface = SurfaceControlHelper.getSurfaceControl(mTestActivity);
        assertThat(surface).isNotNull();
    }

    private <T> T launchTestActivity(Class<T> type) {
        Intent startIntent = new Intent(mContext, type).addFlags(FLAG_ACTIVITY_NEW_TASK);
        Activity testActivity = (Activity) mInstrumentation
                .startActivitySync(startIntent, /* options = */ null);
        return type.cast(testActivity);
    }

    public static final class TestActivity extends Activity {
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mDestroyed.countDown();
        }

        private boolean waitForDestroyed() throws InterruptedException {
            return mDestroyed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
