/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.tv.settings.overlay.FlavorUtils.ALL_FLAVORS_MASK;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.Scene;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.android.tv.settings.overlay.FlavorUtils;

import java.util.List;

public abstract class TvSettingsActivity extends FragmentActivity {
    private static final String TAG = "TvSettingsActivity";

    private static final String SETTINGS_FRAGMENT_TAG =
            "com.android.tv.settings.MainSettings.SETTINGS_FRAGMENT";

    private static final int UI_UPDATE_DELAY_MS = 200;
    private static final int REQUEST_CODE_STARTUP_VERIFICATION = 1;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    public Fragment getVisibleFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragments = fragmentManager.getFragments();
        if (fragments != null) {
            for (Fragment fragment : fragments) {
                if (fragment != null && fragment.isVisible()) {
                    return fragment;
                }
            }
        }
        return null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        if ((FlavorUtils.getFlavor(this) & getAvailableFlavors()) == 0) {
            Log.w(TAG, "Activity is not supported in current flavor");
            finish();
        }
        if (savedInstanceState == null) {
            final Fragment fragment = createSettingsFragment();
            if (fragment == null) {
                return;
            }
            if (isStartupVerificationRequired()) {
                if (FlavorUtils.getFeatureFactory(this)
                        .getStartupVerificationFeatureProvider()
                        .startStartupVerificationActivityForResult(
                                this, REQUEST_CODE_STARTUP_VERIFICATION)) {
                    return;
                }
            }
            if (FlavorUtils.isTwoPanel(this)) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.animator.fade_in,
                                android.R.animator.fade_out)
                        .add(android.R.id.content, fragment, SETTINGS_FRAGMENT_TAG)
                        .commitNowAllowingStateLoss();
                return;
            }

            final ViewGroup root = findViewById(android.R.id.content);
            root.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            root.getViewTreeObserver().removeOnPreDrawListener(this);
                            final Scene scene = new Scene(root);
                            scene.setEnterAction(() -> {
                                if (getFragmentManager().isStateSaved()
                                        || getFragmentManager().isDestroyed()) {
                                    Log.d(TAG, "Got torn down before adding fragment");
                                    return;
                                }
                                getSupportFragmentManager().beginTransaction()
                                        .add(android.R.id.content, fragment,
                                                SETTINGS_FRAGMENT_TAG)
                                        .commitNowAllowingStateLoss();
                            });

                            final Slide slide = new Slide(Gravity.END);
                            slide.setSlideFraction(
                                    getResources().getDimension(R.dimen.lb_settings_pane_width)
                                            / root.getWidth());
                            TransitionManager.go(scene, slide);

                            // Skip the current draw, there's nothing in it
                            return false;
                        }
                    });
        }
    }

    @Override
    public void finish() {
        final Fragment fragment = getSupportFragmentManager().findFragmentByTag(
                SETTINGS_FRAGMENT_TAG);
        if (FlavorUtils.isTwoPanel(this)) {
            super.finish();
            return;
        }

        if (isResumed() && fragment != null) {
            final ViewGroup root = findViewById(android.R.id.content);
            final Scene scene = new Scene(root);
            scene.setEnterAction(() -> getSupportFragmentManager().beginTransaction()
                    .remove(fragment)
                    .commitNowAllowingStateLoss());
            final Slide slide = new Slide(Gravity.END);
            slide.setSlideFraction(
                    getResources().getDimension(R.dimen.lb_settings_pane_width) / root.getWidth());
            slide.addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                    getWindow().setDimAmount(0);
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    transition.removeListener(this);
                    TvSettingsActivity.super.finish();
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                }

                @Override
                public void onTransitionPause(Transition transition) {
                }

                @Override
                public void onTransitionResume(Transition transition) {
                }
            });
            TransitionManager.go(scene, slide);
        } else {
            super.finish();
        }
    }

    protected abstract Fragment createSettingsFragment();

    /**
     * Subclass may override this to return true to indicate that the Activity may only be started
     * after some verification. Example: in special mode, we need to challenge the user with re-auth
     * before launching account settings.
     *
     * This only works in certain flavors as we do not have features requiring the startup
     * verification in classic flavor or ordinary two panel flavor.
     */
    protected boolean isStartupVerificationRequired() {
        return false;
    }

    /** Subclass may override this to specify the flavor, in which the activity is available. */
    protected int getAvailableFlavors() {
        return ALL_FLAVORS_MASK;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_STARTUP_VERIFICATION) {
            if (resultCode == RESULT_OK) {
                Log.v(TAG, "Startup verification succeeded.");
                if (FlavorUtils.getFlavor(this) == FlavorUtils.FLAVOR_X
                        || FlavorUtils.getFlavor(this) == FlavorUtils.FLAVOR_VENDOR) {
                    if (createSettingsFragment() == null) {
                        Log.e(TAG, "Fragment is null.");
                        finish();
                        return;
                    }
                    getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(
                                    android.R.animator.fade_in, android.R.animator.fade_out)
                            .add(
                                    android.R.id.content,
                                    createSettingsFragment(),
                                    SETTINGS_FRAGMENT_TAG)
                            .commitNowAllowingStateLoss();
                }
            } else {
                Log.v(TAG, "Startup verification cancelled or failed.");
                finish();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
    }
}
