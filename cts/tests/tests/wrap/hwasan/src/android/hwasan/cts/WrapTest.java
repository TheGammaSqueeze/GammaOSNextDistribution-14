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
package android.wrap.hwasan.cts;


import android.hwasan.WrapActivity;
import android.test.ActivityInstrumentationTestCase2;

import com.android.compatibility.common.util.CpuFeatures;

public class WrapTest extends ActivityInstrumentationTestCase2<WrapActivity> {
    static {
        System.loadLibrary("cts_wrap_hwasan_jni");
    }

    private WrapActivity mActivity;

    public WrapTest() {
        super(WrapActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Start the activity.
        mActivity = getActivity();
        // Wait for the UI Thread to become idle.
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        // Nothing to do here.
        super.tearDown();
    }

    public void testProperty() throws Exception {
        if (!CpuFeatures.isArm64Cpu()) return;
        assertTrue(System.getenv("LD_HWASAN") != null);
    }

    public static native boolean runningWithHwasan();

    public void testRunningWithHwasan() throws Exception {
        if (!CpuFeatures.isArm64Cpu()) return;
        assertTrue(runningWithHwasan());
    }
}
