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
package android.contentcaptureservice.cts;

import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import android.platform.test.annotations.AppModeFull;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ActivitiesWatcher.ActivityWatcher;

import org.junit.Test;

@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class NullTextViewActivityTest extends
        AbstractContentCaptureIntegrationAutoActivityLaunchTest<NullTextViewActivity> {
    private static final ActivityTestRule<NullTextViewActivity> sActivityRule =
            new ActivityTestRule<>(NullTextViewActivity.class, false, false);

    public NullTextViewActivityTest() {
        super(NullTextViewActivity.class);
    }

    @Override
    protected ActivityTestRule<NullTextViewActivity> getActivityTestRule() {
        return sActivityRule;
    }

    /**
     * Test that returning null from a TextView's getText() method will not cause an exception.
     */
    @Test
    public void testNullTextView() throws Exception {
        enableService();
        final ActivityWatcher watcher = startWatcher();
        final NullTextViewActivity activity = launchActivity();

        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);
    }
}
