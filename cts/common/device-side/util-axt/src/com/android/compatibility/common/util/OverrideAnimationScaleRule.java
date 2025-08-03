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

package com.android.compatibility.common.util;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import android.animation.ValueAnimator;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;


public class OverrideAnimationScaleRule extends BeforeAfterRule {

    // From javadoc definition, the default animator duration scale is 1
    private static final String DEFAULT_ANIMATOR_DURATION_SCALE = "1";

    @NonNull
    private final GlobalSetting mWindowAnimationScaleSetting = new GlobalSetting(
            "window_animation_scale");
    @NonNull
    private final GlobalSetting mTransitionAnimationScaleSetting = new GlobalSetting(
            "transition_animation_scale");
    @NonNull
    private final GlobalSetting mAnimatorDurationScaleSetting = new GlobalSetting(
            "animator_duration_scale", DEFAULT_ANIMATOR_DURATION_SCALE);

    private final float mAnimationScale;

    public OverrideAnimationScaleRule(float animationScale) {
        mAnimationScale = animationScale;
    }

    @Override
    protected void onBefore(Statement base, Description description) {
        var value = Float.toString(mAnimationScale);
        mWindowAnimationScaleSetting.put(value);
        mTransitionAnimationScaleSetting.put(value);
        mAnimatorDurationScaleSetting.put(value);
        if (mAnimationScale > 0) {
            PollingCheck.waitFor(() ->
                    Math.abs(ValueAnimator.getDurationScale() - mAnimationScale) < 0.001);
        }
    }

    @Override
    protected void onAfter(Statement base, Description description) {
        mWindowAnimationScaleSetting.restore();
        mTransitionAnimationScaleSetting.restore();
        mAnimatorDurationScaleSetting.restore();
    }

    private static class GlobalSetting {
        @NonNull
        private final String mName;

        private String mInitialValue;
        private String mDefaultValue;

        public GlobalSetting(@NonNull String name) {
            this(name, /* defaultValue= */ null);
        }

        GlobalSetting(@NonNull String name, @Nullable String defaultValue) {
            mName = name;
            mDefaultValue = defaultValue;
        }

        public void put(@NonNull String value) {
            mInitialValue = runShellCommand("settings get global " + mName);
            runShellCommand("settings put global " + mName + " " + value);
        }

        public void restore() {
            String restoreValue =
                    !isEmptyOrNullString(mInitialValue.trim()) ? mInitialValue : mDefaultValue;
            runShellCommand("settings put global " + mName + " " + restoreValue);
        }

        private boolean isEmptyOrNullString(String value) {
            return TextUtils.isEmpty(value) || TextUtils.equals(value.toLowerCase(), "null");
        }
    }
}
