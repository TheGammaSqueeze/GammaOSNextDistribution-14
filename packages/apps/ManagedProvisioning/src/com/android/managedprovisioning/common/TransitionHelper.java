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

package com.android.managedprovisioning.common;

import static com.android.managedprovisioning.provisioning.Constants.ENABLE_CUSTOM_TRANSITIONS;

import static com.google.android.setupdesign.transition.TransitionHelper.EXTRA_ACTIVITY_OPTIONS;
import static com.google.android.setupdesign.transition.TransitionHelper.TRANSITION_FADE;
import static com.google.android.setupdesign.transition.TransitionHelper.makeActivityOptions;

import static java.util.Objects.requireNonNull;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;

/**
 * Wrapper for {@link com.google.android.setupdesign.transition.TransitionHelper}.
 *
 * <p>Each {@link Activity#onCreate(Bundle)} is expected to call {@link
 * TransitionHelper#applyContentScreenTransitions(Activity)}. For starting an {@link Activity}, use
 * any of the {@code startActivity*WithTransition} methods provided in this class, in order to
 * apply the appropriate transition. When finishing an {@link Activity}, {@link
 * TransitionHelper#finishActivity(Activity)} must be used to apply the relevant transition.
 *
 * @see #startActivityWithTransition(Activity, Intent)
 * @see #startActivityForResultWithTransition(Activity, Intent, int)
 * @see #startActivityAsUserWithTransition(Activity, Intent, UserHandle)
 * @see #startActivityForResultAsUserWithTransition(Activity, Intent, int, UserHandle)
 */
// TODO(b/183631247): Add tests once infrastructure is fixed
public class TransitionHelper {
    private final Handler mHandler = new Handler(Looper.myLooper());

    /**
     * Applies the relevant {@link Activity} transitions for this content screen.
     * Must be called in {@link Activity#onCreate(Bundle)}.
     *
     * <p>A content screen is a screen that shows content, not a spinner.
     *
     * <p>Also enables the {@link Window#FEATURE_ACTIVITY_TRANSITIONS} feature so that transitions
     * are supported.
     *
     * @see #applyWaitingScreenTransitions(Activity)
     */
    public void applyContentScreenTransitions(Activity activity) {
        requireNonNull(activity);
        if (!ENABLE_CUSTOM_TRANSITIONS) {
            return;
        }
        activity.getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        com.google.android.setupdesign.transition.TransitionHelper.applyForwardTransition(activity);
        com.google.android.setupdesign.transition.TransitionHelper
                .applyBackwardTransition(activity);
    }

    /**
     * Applies the relevant {@link Activity} transitions for this waiting screen.
     * Must be called in {@link Activity#onResume()}.
     *
     * <p>A waiting screen is a screen that shows a spinner, not content.
     *
     * @see #applyContentScreenTransitions(Activity)
     */
    public void applyWaitingScreenTransitions(Activity activity) {
        requireNonNull(activity);
        com.google.android.setupdesign.transition.TransitionHelper
                .applyForwardTransition(activity, TRANSITION_FADE);
    }

    /**
     * Calls {@link Activity#startActivity(Intent)} with an appropriate transition.
     */
    public void startActivityWithTransition(Activity activity, Intent intent) {
        requireNonNull(activity);
        requireNonNull(intent);
        if (!ENABLE_CUSTOM_TRANSITIONS) {
            activity.startActivity(intent);
            return;
        }
        // TODO(b/183537077): Remove hack to start UI-less activities using the previous activity's
        //  options bundle
        Bundle previousBundle = getActivityOptionsForActivity(activity);
        if (!hasContentView(activity) && previousBundle != null) {
            mHandler.post(() -> com.google.android.setupdesign.transition.TransitionHelper
                    .startActivityWithTransition(activity, intent, previousBundle));
        } else {
            mHandler.post(() -> com.google.android.setupdesign.transition.TransitionHelper
                    .startActivityWithTransition(activity, intent));
        }
    }

    /**
     * Calls {@link Activity#startActivityForResult(Intent, int)} with an appropriate transition.
     */
    public void startActivityForResultWithTransition(
            Activity activity, Intent intent, int requestCode) {
        requireNonNull(activity);
        requireNonNull(intent);
        if (!ENABLE_CUSTOM_TRANSITIONS) {
            activity.startActivityForResult(intent, requestCode);
            return;
        }
        // TODO(b/183537077): Remove hack to start UI-less activities using the previous activity's
        //  options bundle
        Bundle previousBundle = getActivityOptionsForActivity(activity);
        if (!hasContentView(activity) && previousBundle != null) {
            mHandler.post(() -> com.google.android.setupdesign.transition.TransitionHelper
                    .startActivityForResultWithTransition(
                            activity, intent, requestCode, previousBundle));
        } else {
            mHandler.post(() -> com.google.android.setupdesign.transition.TransitionHelper
                    .startActivityForResultWithTransition(activity, intent, requestCode));
        }
    }

    /**
     * Calls {@link Activity#startActivityAsUser(Intent, UserHandle)} with an appropriate
     * transition.
     */
    public void startActivityAsUserWithTransition(
            Activity activity, Intent intent, UserHandle userHandle) {
        requireNonNull(activity);
        requireNonNull(intent);
        requireNonNull(userHandle);
        if (!ENABLE_CUSTOM_TRANSITIONS) {
            activity.startActivityAsUser(intent, userHandle);
            return;
        }
        Bundle options = getRelevantActivityOptions(activity, intent);
        mHandler.post(() -> activity.startActivityAsUser(intent, options, userHandle));
    }

    /**
     * Calls {@link Activity#startActivityForResultAsUser(Intent, int, UserHandle)} with an
     * appropriate transition.
     */
    public void startActivityForResultAsUserWithTransition(
            Activity activity, Intent intent, int requestCode, UserHandle userHandle) {
        requireNonNull(activity);
        requireNonNull(intent);
        requireNonNull(userHandle);
        if (!ENABLE_CUSTOM_TRANSITIONS) {
            activity.startActivityForResultAsUser(intent, requestCode, userHandle);
            return;
        }
        Bundle options = getRelevantActivityOptions(activity, intent);
        mHandler.post(() ->
                activity.startActivityForResultAsUser(intent, requestCode, options, userHandle));
    }

    /**
     * Calls {@link Activity#finish()} with an appropriate transition.
     */
    public void finishActivity(Activity activity) {
        requireNonNull(activity);
        if (!ENABLE_CUSTOM_TRANSITIONS) {
            activity.finish();
            return;
        }
        com.google.android.setupdesign.transition.TransitionHelper.finishActivity(activity);
    }

    @Nullable
    private Bundle getActivityOptionsForActivity(Activity activity) {
        if (activity.getIntent() == null) {
            return null;
        }
        return activity.getIntent().getBundleExtra(EXTRA_ACTIVITY_OPTIONS);
    }

    private boolean hasContentView(Activity activity) {
        if (activity.getWindow() == null) {
            return false;
        }
        final ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView().getRootView();
        return rootView.getChildCount() > 0;
    }

    private Bundle getRelevantActivityOptions(Activity activity, Intent intent) {
        // TODO(b/183537077): Remove hack to start UI-less activities using the previous activity's
        //  options bundle
        Bundle options;
        Bundle previousBundle = getActivityOptionsForActivity(activity);
        if (!hasContentView(activity) && previousBundle != null) {
            options = previousBundle;
        } else {
            options = makeActivityOptions(activity, intent);
        }
        return options;
    }
}
