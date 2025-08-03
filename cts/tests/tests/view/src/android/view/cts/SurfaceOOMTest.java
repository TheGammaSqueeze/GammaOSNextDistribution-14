/*
 * Copyright 2023 The Android Open Source Project
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
package android.view.cts;

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;

import static java.util.Objects.requireNonNull;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.flags.Flags;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests covering Surface memory management. These tests can take a long time to run.
 */
public class SurfaceOOMTest {
    public static final int BIG_WIDTH = 1000;
    public static final int BIG_HEIGHT = 1000;
    public static final int BYTES_PER_PIXEL = 4;
    public static final int MEMORY_MULTIPLIER = 2;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public ActivityScenarioRule<SurfaceOOMTestActivity> mActivityRule =
            createFullscreenActivityScenarioRule(SurfaceOOMTestActivity.class);

    private SurfaceOOMTestActivity mActivity;

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    @Test(timeout = 5 * 60 * 1000)
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SURFACE_NATIVE_ALLOC_REGISTRATION_RO)
    public void testSurfaceGarbageCollection() throws Throwable {
        long imageSizeBytes = BIG_WIDTH * BIG_HEIGHT * BYTES_PER_PIXEL;
        long numSurfacesToCreate = MEMORY_MULTIPLIER * (getMemoryInfo().totalMem / imageSizeBytes);

        mActivity.verifyCreatingManySurfaces(BIG_WIDTH, BIG_HEIGHT, numSurfacesToCreate);
    }

    private MemoryInfo getMemoryInfo() {
        MemoryInfo memoryInfo = new MemoryInfo();
        requireNonNull(mActivity.getSystemService(ActivityManager.class)).getMemoryInfo(memoryInfo);
        return memoryInfo;
    }
}
