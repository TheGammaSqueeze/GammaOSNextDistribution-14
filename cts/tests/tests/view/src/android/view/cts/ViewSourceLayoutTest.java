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

package android.view.cts;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.content.res.Resources;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ViewSourceLayoutTest {
    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<ViewSourceLayoutTestActivity> mActivityRule =
            new ActivityTestRule<>(ViewSourceLayoutTestActivity.class);

    @Test
    public void testGetSourceLayout() {
        ViewSourceLayoutTestActivity activity = mActivityRule.getActivity();
        View rootView = activity.findViewById(R.id.view_root);
        assertEquals(R.layout.view_source_layout_test_layout, rootView.getSourceLayoutResId());

        View view1 = activity.findViewById(R.id.view1);
        assertEquals(R.layout.view_source_layout_test_layout, view1.getSourceLayoutResId());

        View view2 = activity.findViewById(R.id.view2);
        assertEquals(R.layout.view_source_layout_test_include_layout, view2.getSourceLayoutResId());

        View view3 = new View(activity);
        assertEquals(Resources.ID_NULL, view3.getSourceLayoutResId());
    }
}
