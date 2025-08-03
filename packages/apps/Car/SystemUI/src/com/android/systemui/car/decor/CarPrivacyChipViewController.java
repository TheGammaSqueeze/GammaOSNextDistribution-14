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

package com.android.systemui.car.decor;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets.Type.InsetsType;
import android.view.WindowInsetsController;

import androidx.annotation.UiThread;

import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.R;
import com.android.systemui.car.systembar.SystemBarConfigs;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.events.PrivacyDotViewController;
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler;
import com.android.systemui.statusbar.events.ViewState;
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import kotlinx.coroutines.CoroutineScope;

/**
 * Subclass of {@link PrivacyDotViewController}.
 */
@SysUISingleton
public class CarPrivacyChipViewController extends PrivacyDotViewController
        implements CommandQueue.Callbacks {
    private static final String TAG = CarPrivacyChipViewController.class.getSimpleName();
    private boolean mAreaVisible;
    private final @InsetsType int mBarType;
    private DelayableExecutor mExecutor;
    private CarPrivacyChipAnimationHelper mAnimationHelper;

    @Inject
    public CarPrivacyChipViewController(
            @NotNull @Main Executor mainExecutor,
            @Application CoroutineScope scope,
            @NotNull Context context,
            @NotNull StatusBarStateController stateController,
            @NotNull ConfigurationController configurationController,
            @NotNull StatusBarContentInsetsProvider contentInsetsProvider,
            @NotNull SystemStatusAnimationScheduler animationScheduler,
            CommandQueue commandQueue) {
        super(mainExecutor, scope, stateController, configurationController, contentInsetsProvider,
                animationScheduler, null);
        commandQueue.addCallback(this);
        mAnimationHelper = new CarPrivacyChipAnimationHelper(context);
        mBarType = SystemBarConfigs.BAR_PROVIDER_MAP[context.getResources().getInteger(
                R.integer.config_privacyIndicatorLocation)].getType();
    }

    @Override
    @UiThread
    public void updateDotView(ViewState state) {
        updatePrivacyChipView(state, false);
    }

    private void updatePrivacyChipView(ViewState state, boolean areaVisibilityChange) {
        Log.d(TAG, "updatePrivacyChipView");
        boolean shouldShow = state.shouldShowDot();
        View designatedCorner = state.getDesignatedCorner();
        if (!mAreaVisible) {
            mAnimationHelper.hidePrivacyDotWithoutAnimation(designatedCorner);
        } else if (mAreaVisible && areaVisibilityChange) {
            String contentDescription = state.getContentDescription();
            if (state.getSystemPrivacyEventIsActive()
                    && (useCamera(contentDescription) || useMic(contentDescription))) {
                mAnimationHelper.showPrivacyDot(designatedCorner);
            }
        } else {
            if (shouldShow && designatedCorner != null) {
                showIndicator(state);
            } else {
                if (designatedCorner.getVisibility() == View.VISIBLE) {
                    hideIndicator(state);
                }
            }
        }
    }

    @UiThread
    private void showIndicator(ViewState viewState) {
        Log.d(TAG, "Show the immersive indicator");
        View container = viewState.getDesignatedCorner();
        container.setVisibility(View.VISIBLE);

        String contentDescription = viewState.getContentDescription();
        if (useCamera(contentDescription) && useMic(contentDescription)) {
            mAnimationHelper.showCameraAndMicChip(container);
        } else if (useCamera(contentDescription)) {
            mAnimationHelper.showCameraChip(container);
        } else if (useMic(contentDescription)) {
            mAnimationHelper.showMicChip(container);
        }

        if (getShowingListener() != null) {
            getShowingListener().onPrivacyDotShown(container);
        }
    }

    @UiThread
    private void hideIndicator(ViewState viewState) {
        Log.d(TAG, "Hide the immersive indicators");
        View container = viewState.getDesignatedCorner();

        mAnimationHelper.hidePrivacyDot(container);

        if (getShowingListener() != null) {
            getShowingListener().onPrivacyDotHidden(container);
        }
    }

    @Override
    public void onSystemBarAttributesChanged(
            int displayId,
            @WindowInsetsController.Appearance int appearance,
            AppearanceRegion[] appearanceRegions,
            boolean navbarColorManagedByIme,
            @WindowInsetsController.Behavior int behavior,
            @InsetsType int requestedVisibleTypes,
            String packageName,
            LetterboxDetails[] letterboxDetails) {
        boolean newAreaVisibility = (mBarType & requestedVisibleTypes) == 0;
        if (newAreaVisibility != mAreaVisible) {
            mAreaVisible = newAreaVisibility;
            mExecutor = getUiExecutor();
            // Null check to avoid crashing caused by debug.disable_screen_decorations=true
            if (mExecutor != null) {
                mAnimationHelper.setExecutor(mExecutor);
                mExecutor.execute(() -> updatePrivacyChipView(getCurrentViewState(), true));
            }
        }
    }

    private boolean useCamera(String contentDescription) {
        if (contentDescription != null && !contentDescription.isEmpty()) {
            return contentDescription.contains(PrivacyType.TYPE_CAMERA.getLogName());
        }
        return false;
    }

    private boolean useMic(String contentDescription) {
        if (contentDescription != null && !contentDescription.isEmpty()) {
            return contentDescription.contains(PrivacyType.TYPE_MICROPHONE.getLogName());
        }
        return false;
    }

    @Override
    @UiThread
    public void updateRotations(int rotation, int paddingTop) {
        // Do nothing.
    }

    @Override
    @UiThread
    public void setCornerSizes(ViewState state) {
        // Do nothing.
    }
}
