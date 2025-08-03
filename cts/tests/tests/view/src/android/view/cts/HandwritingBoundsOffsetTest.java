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

package android.view.cts;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Activity;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class HandwritingBoundsOffsetTest {

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<HandwritingActivity> mActivityRule =
            new ActivityTestRule<>(HandwritingActivity.class);

    @Test
    public void handwritingBoundsOffset_viewDefaultValue() {
        final Activity activity = mActivityRule.getActivity();
        final View view = activity.findViewById(R.id.default_view);

        // The default value for handwriting bounds offset is: 10dp for the left and right edge and
        // 40dp for the top and bottom edge.
        assertThat(view.getHandwritingBoundsOffsetLeft()).isZero();
        assertThat(view.getHandwritingBoundsOffsetRight()).isZero();
        assertThat(view.getHandwritingBoundsOffsetTop()).isZero();
        assertThat(view.getHandwritingBoundsOffsetBottom()).isZero();
    }

    @Test
    public void handwritingBoundsOffset_textViewDefaultValue() {
        final Activity activity = mActivityRule.getActivity();
        final View view = activity.findViewById(R.id.default_textview);

        final DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        // The default value for handwriting bounds offset is: 10dp for the left and right edge and
        // 40dp for the top and bottom edge.
        assertThat(view.getHandwritingBoundsOffsetLeft()).isEqualTo(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, displayMetrics));
        assertThat(view.getHandwritingBoundsOffsetRight()).isEqualTo(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, displayMetrics));

        assertThat(view.getHandwritingBoundsOffsetTop()).isEqualTo(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, displayMetrics));
        assertThat(view.getHandwritingBoundsOffsetBottom()).isEqualTo(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, displayMetrics));
    }

    @Test
    public void handwritingBoundsOffset_editTextDefaultValue() {
        final Activity activity = mActivityRule.getActivity();
        final View view = activity.findViewById(R.id.default_edittext);

        final DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        // The default value for handwriting bounds offset is: 10dp for the left and right edge and
        // 40dp for the top and bottom edge.
        assertThat(view.getHandwritingBoundsOffsetLeft()).isEqualTo(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, displayMetrics));
        assertThat(view.getHandwritingBoundsOffsetRight()).isEqualTo(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, displayMetrics));

        assertThat(view.getHandwritingBoundsOffsetTop()).isEqualTo(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, displayMetrics));
        assertThat(view.getHandwritingBoundsOffsetBottom()).isEqualTo(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, displayMetrics));
    }

    @Test
    public void handwritingBoundsOffset_setThoughXml() {
        final Activity activity = mActivityRule.getActivity();
        final View view = activity.findViewById(R.id.handwriting_bounds_offset);

        assertThat(view.getHandwritingBoundsOffsetLeft()).isEqualTo(5);
        assertThat(view.getHandwritingBoundsOffsetTop()).isEqualTo(10);
        assertThat(view.getHandwritingBoundsOffsetRight()).isEqualTo(15);
        assertThat(view.getHandwritingBoundsOffsetBottom()).isEqualTo(20);
    }

    @Test
    public void setHandwritingBoundsOffsets() {
        final Activity activity = mActivityRule.getActivity();
        final View view = activity.findViewById(R.id.default_view);

        view.setHandwritingBoundsOffsets(1, 2, 3, 4);
        assertThat(view.getHandwritingBoundsOffsetLeft()).isEqualTo(1);
        assertThat(view.getHandwritingBoundsOffsetTop()).isEqualTo(2);
        assertThat(view.getHandwritingBoundsOffsetRight()).isEqualTo(3);
        assertThat(view.getHandwritingBoundsOffsetBottom()).isEqualTo(4);
    }
}
