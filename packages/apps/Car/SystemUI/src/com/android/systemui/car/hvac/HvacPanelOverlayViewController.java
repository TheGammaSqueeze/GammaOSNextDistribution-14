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

package com.android.systemui.car.hvac;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.window.OverlayPanelViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.wm.shell.animation.FlingAnimationUtils;

import javax.inject.Inject;

@SysUISingleton
public class HvacPanelOverlayViewController extends OverlayPanelViewController implements
        ConfigurationController.ConfigurationListener {

    private final Context mContext;
    private final Resources mResources;
    private final HvacController mHvacController;
    private final UiModeManager mUiModeManager;
    private final float mFullyOpenDimAmount;

    private boolean mIsUiModeNight;
    private float mCurrentDimAmount = 0f;

    private HvacPanelView mHvacPanelView;

    @Inject
    public HvacPanelOverlayViewController(Context context,
            @Main Resources resources,
            HvacController hvacController,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            FlingAnimationUtils.Builder flingAnimationUtilsBuilder,
            CarDeviceProvisionedController carDeviceProvisionedController,
            ConfigurationController configurationController,
            UiModeManager uiModeManager) {
        super(context, resources, R.id.hvac_panel_stub, overlayViewGlobalStateController,
                flingAnimationUtilsBuilder, carDeviceProvisionedController);
        mContext = context;
        mResources = resources;
        mHvacController = hvacController;
        mUiModeManager = uiModeManager;
        configurationController.addCallback(this);
        mFullyOpenDimAmount = mContext.getResources().getFloat(
                R.fraction.hvac_overlay_window_dim_amount);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View closeButton = getLayout().findViewById(R.id.hvac_panel_close_button);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> toggle());
        }

        mHvacPanelView = getLayout().findViewById(R.id.hvac_panel);
        mHvacController.registerHvacViews(mHvacPanelView);

        mHvacPanelView.setKeyEventHandler((event) -> {
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                return false;
            }

            if (event.getAction() == KeyEvent.ACTION_UP && isPanelExpanded()) {
                toggle();
            }
            return true;
        });
    }

    @Override
    protected int getInsetTypesToFit() {
        return WindowInsets.Type.systemBars();
    }

    @Override
    protected boolean shouldShowStatusBarInsets() {
        return true;
    }

    @Override
    protected boolean shouldShowNavigationBarInsets() {
        return true;
    }

    @Override
    protected boolean shouldAnimateCollapsePanel() {
        return true;
    }

    @Override
    protected boolean shouldAnimateExpandPanel() {
        return true;
    }

    @Override
    protected boolean shouldAllowClosingScroll() {
        return true;
    }

    @Override
    protected float getDefaultDimAmount() {
        return mCurrentDimAmount;
    }

    @Override
    protected Integer getHandleBarViewId() {
        return R.id.handle_bar;
    }

    @Override
    protected int getFocusAreaViewId() {
        return R.id.hvac_panel_container;
    }

    @Override
    protected int getSettleClosePercentage() {
        return mResources.getInteger(R.integer.hvac_panel_settle_close_percentage);
    }

    @Override
    protected void onAnimateCollapsePanel() {
        // no-op.
    }

    @Override
    protected void onAnimateExpandPanel() {
        // no-op.
    }

    @Override
    protected void onCollapseAnimationEnd() {
        // no-op.
    }

    @Override
    protected void onExpandAnimationEnd() {
        // no-op.
    }

    @Override
    protected void onOpenScrollStart() {
        // no-op.
    }

    @Override
    protected void onTouchEvent(View view, MotionEvent event) {
        if (mHvacPanelView == null) {
            return;
        }
        Rect outBounds = new Rect();
        mHvacPanelView.getBoundsInWindow(outBounds, /* clipToParent= */ true);
        if (isPanelExpanded() && (event.getAction() == MotionEvent.ACTION_UP)
                && isTouchOutside(outBounds, event.getX(), event.getY())) {
            toggle();
        }
    }

    @Override
    protected void onScroll(int y) {
        super.onScroll(y);

        float percentageOpen =
                ((float) (mAnimateDirection > 0 ? y : getLayout().getHeight() - y))
                        / getLayout().getHeight();
        mCurrentDimAmount = mFullyOpenDimAmount * percentageOpen;
        getOverlayViewGlobalStateController().updateWindowDimBehind(this, mCurrentDimAmount);
    }

    private boolean isTouchOutside(Rect bounds, float x, float y) {
        return x < bounds.left || x > bounds.right || y < bounds.top || y > bounds.bottom;
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        boolean isConfigNightMode = newConfig.isNightModeActive();

        // Only refresh UI on Night mode changes
        if (isConfigNightMode != mIsUiModeNight) {
            mIsUiModeNight = isConfigNightMode;
            mUiModeManager.setNightModeActivated(mIsUiModeNight);

            if (getLayout() == null) return;
            mHvacPanelView = getLayout().findViewById(R.id.hvac_panel);
            if (mHvacPanelView == null) return;
            ViewGroup hvacViewGroupParent = (ViewGroup) mHvacPanelView.getParent();

            // cache properties of {@link HvacPanelView}
            int inflatedId = mHvacPanelView.getId();
            ViewGroup.LayoutParams layoutParams = mHvacPanelView.getLayoutParams();
            HvacPanelView.KeyEventHandler hvacKeyEventHandler = mHvacPanelView
                    .getKeyEventHandler();
            int indexOfView = hvacViewGroupParent.indexOfChild(mHvacPanelView);

            // remove {@link HvacPanelView} from its parent and reinflate it
            hvacViewGroupParent.removeView(mHvacPanelView);
            HvacPanelView newHvacPanelView = (HvacPanelView) LayoutInflater.from(mContext).inflate(
                    R.layout.hvac_panel, /* root= */ hvacViewGroupParent,
                    /* attachToRoot= */ false);
            hvacViewGroupParent.addView(newHvacPanelView, indexOfView);
            mHvacPanelView = newHvacPanelView;

            // reset {@link HvacPanelView} cached properties
            mHvacPanelView.setId(inflatedId);
            mHvacPanelView.setLayoutParams(layoutParams);
            mHvacController.registerHvacViews(mHvacPanelView);
            mHvacPanelView.setKeyEventHandler(hvacKeyEventHandler);
        }
    }
}
