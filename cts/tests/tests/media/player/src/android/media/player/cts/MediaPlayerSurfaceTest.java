/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.media.player.cts;

import static org.junit.Assert.assertNotNull;

import android.os.ConditionVariable;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 */
@Presubmit
@SmallTest
@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class MediaPlayerSurfaceTest {

    private ActivityScenario<MediaPlayerSurfaceStubActivity> mActivityScenario;
    private MediaPlayerSurfaceStubActivity mActivity;

    @Before
    public void setUp() {
        mActivityScenario = ActivityScenario.launch(MediaPlayerSurfaceStubActivity.class);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(/* timeoutMs= */ 10000);
        assertNotNull("Failed to acquire activity reference.", mActivity);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @After
    public void tearDown() {
        mActivity = null;
    }

    @Test
    public void testSetSurface() throws Exception {
        mActivity.playVideo();
        mActivity.finish();
    }
}
