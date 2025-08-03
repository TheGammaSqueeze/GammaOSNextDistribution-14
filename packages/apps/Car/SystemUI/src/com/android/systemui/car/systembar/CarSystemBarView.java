/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import static com.android.systemui.car.systembar.CarSystemBar.DEBUG;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.car.dockutil.Flags;
import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacPanelOverlayViewController;
import com.android.systemui.car.hvac.HvacView;
import com.android.systemui.car.hvac.TemperatureControlView;
import com.android.systemui.car.notification.NotificationPanelViewController;
import com.android.systemui.car.statusicon.ui.QuickControlsEntryPointsController;
import com.android.systemui.car.systembar.CarSystemBarController.HvacPanelController;
import com.android.systemui.car.systembar.CarSystemBarController.NotificationsShadeController;
import com.android.systemui.settings.UserTracker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Set;

/**
 * A custom system bar for the automotive use case.
 * <p>
 * The system bar in the automotive use case is more like a list of shortcuts, rendered
 * in a linear layout.
 */
public class CarSystemBarView extends LinearLayout {

    @IntDef(value = {BUTTON_TYPE_NAVIGATION, BUTTON_TYPE_KEYGUARD, BUTTON_TYPE_OCCLUSION})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    private @interface ButtonsType {
    }

    private static final String TAG = CarSystemBarView.class.getSimpleName();

    public static final int BUTTON_TYPE_NAVIGATION = 0;
    public static final int BUTTON_TYPE_KEYGUARD = 1;
    public static final int BUTTON_TYPE_OCCLUSION = 2;

    private final boolean mConsumeTouchWhenPanelOpen;
    private final boolean mButtonsDraggable;
    private CarSystemBarButton mHomeButton;
    private CarSystemBarButton mPassengerHomeButton;
    private View mNavButtons;
    private CarSystemBarButton mNotificationsButton;
    private CarSystemBarButton mHvacButton;
    private HvacView mDriverHvacView;
    private HvacView mPassengerHvacView;
    private NotificationsShadeController mNotificationsShadeController;
    private HvacPanelController mHvacPanelController;
    private View mLockScreenButtons;
    private View mOcclusionButtons;
    private ViewGroup mQcEntryPointsContainer;
    // used to wire in open/close gestures for overlay panels
    private Set<OnTouchListener> mStatusBarWindowTouchListeners;
    private HvacPanelOverlayViewController mHvacPanelOverlayViewController;
    private NotificationPanelViewController mNotificationPanelViewController;
    private CarSystemBarButton mControlCenterButton;

    public CarSystemBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mConsumeTouchWhenPanelOpen = getResources().getBoolean(
                R.bool.config_consumeSystemBarTouchWhenNotificationPanelOpen);
        mButtonsDraggable = getResources().getBoolean(R.bool.config_systemBarButtonsDraggable);
    }

    @Override
    public void onFinishInflate() {
        mHomeButton = findViewById(R.id.home);
        mPassengerHomeButton = findViewById(R.id.passenger_home);
        mNavButtons = findViewById(R.id.nav_buttons);
        mLockScreenButtons = findViewById(R.id.lock_screen_nav_buttons);
        mOcclusionButtons = findViewById(R.id.occlusion_buttons);
        mNotificationsButton = findViewById(R.id.notifications);
        mHvacButton = findViewById(R.id.hvac);
        mDriverHvacView = findViewById(R.id.driver_hvac);
        mPassengerHvacView = findViewById(R.id.passenger_hvac);
        mQcEntryPointsContainer = findViewById(R.id.qc_entry_points_container);
        mControlCenterButton = findViewById(R.id.control_center_nav);
        if (mNotificationsButton != null) {
            mNotificationsButton.setOnClickListener(this::onNotificationsClick);
        }
        setupHvacButton();
        // Needs to be clickable so that it will receive ACTION_MOVE events.
        setClickable(true);
        // Needs to not be focusable so rotary won't highlight the entire nav bar.
        setFocusable(false);
    }

    void updateHomeButtonVisibility(boolean isPassenger) {
        if (!isPassenger) {
            return;
        }
        if (mPassengerHomeButton != null) {
            if (mHomeButton != null) {
                mHomeButton.setVisibility(GONE);
            }
            mPassengerHomeButton.setVisibility(VISIBLE);
        }
    }

    void setupHvacButton() {
        if (mHvacButton != null) {
            mHvacButton.setOnClickListener(this::onHvacClick);
        }

        if (Flags.dockFeature()) {
            if (mDriverHvacView instanceof TemperatureControlView) {
                ((TemperatureControlView) mDriverHvacView).setTemperatureTextClickListener(
                        this::onHvacClick);
            }
            if (mPassengerHvacView instanceof TemperatureControlView) {
                ((TemperatureControlView) mPassengerHvacView).setTemperatureTextClickListener(
                        this::onHvacClick);
            }
        }
    }

    void setupQuickControlsEntryPoints(
            QuickControlsEntryPointsController quickControlsEntryPointsController,
            boolean isSetUp) {
        if (mQcEntryPointsContainer != null) {
            quickControlsEntryPointsController.addIconViews(mQcEntryPointsContainer, isSetUp);
        }
    }

    void setupSystemBarButtons(UserTracker userTracker) {
        setupSystemBarButtons(this, userTracker);
    }

    private void setupSystemBarButtons(View v, UserTracker userTracker) {
        if (v instanceof CarSystemBarButton) {
            ((CarSystemBarButton) v).setUserTracker(userTracker);
        } else if (v instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) v;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setupSystemBarButtons(viewGroup.getChildAt(i), userTracker);
            }
        }
    }

    void updateControlCenterButtonVisibility(boolean isMumd) {
        if (mControlCenterButton != null) {
            mControlCenterButton.setVisibility(isMumd ? VISIBLE : GONE);
        }
    }

    // Used to forward touch events even if the touch was initiated from a child component
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mStatusBarWindowTouchListeners != null && !mStatusBarWindowTouchListeners.isEmpty()) {
            if (!mButtonsDraggable) {
                return false;
            }
            boolean shouldConsumeEvent = mNotificationsShadeController == null ? false
                    : mNotificationsShadeController.isNotificationPanelOpen();

            // Forward touch events to the status bar window so it can drag
            // windows if required (ex. Notification shade)
            triggerAllTouchListeners(this, ev);

            if (mConsumeTouchWhenPanelOpen && shouldConsumeEvent) {
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    /** Sets the notifications panel controller. */
    public void setNotificationsPanelController(NotificationsShadeController controller) {
        mNotificationsShadeController = controller;
    }

    /** Sets the HVAC panel controller. */
    public void setHvacPanelController(HvacPanelController controller) {
        mHvacPanelController = controller;
    }

    /** Gets the notifications panel controller. */
    public NotificationsShadeController getNotificationsPanelController() {
        return mNotificationsShadeController;
    }

    /** Gets the HVAC panel controller. */
    public HvacPanelController getHvacPanelController() {
        return mHvacPanelController;
    }

    /**
     * Sets the touch listeners that will be called from onInterceptTouchEvent and onTouchEvent
     *
     * @param statusBarWindowTouchListeners List of listeners to call from touch and intercept touch
     */
    public void setStatusBarWindowTouchListeners(
            Set<OnTouchListener> statusBarWindowTouchListeners) {
        mStatusBarWindowTouchListeners = statusBarWindowTouchListeners;
    }

    /** Gets the touch listeners that will be called from onInterceptTouchEvent and onTouchEvent. */
    public Set<OnTouchListener> getStatusBarWindowTouchListeners() {
        return mStatusBarWindowTouchListeners;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        triggerAllTouchListeners(this, event);
        return super.onTouchEvent(event);
    }

    protected void onNotificationsClick(View v) {
        if (mNotificationsButton != null
                && mNotificationsButton.getDisabled()) {
            mNotificationsButton.runOnClickWhileDisabled();
            return;
        }
        if (mNotificationsShadeController != null) {
            // If the notification shade is about to open, close the hvac panel
            if (!mNotificationsShadeController.isNotificationPanelOpen()
                    && mHvacPanelController != null
                    && mHvacPanelController.isHvacPanelOpen()) {
                mHvacPanelController.togglePanel();
            }
            mNotificationsShadeController.togglePanel();
        }
    }

    protected void onHvacClick(View v) {
        if (mHvacPanelController != null) {
            // If the hvac panel is about to open, close the notification shade
            if (!mHvacPanelController.isHvacPanelOpen()
                    && mNotificationsShadeController != null
                    && mNotificationsShadeController.isNotificationPanelOpen()) {
                mNotificationsShadeController.togglePanel();
            }
            mHvacPanelController.togglePanel();
        }
    }

    /**
     * Shows buttons of the specified {@link ButtonsType}.
     *
     * NOTE: Only one type of buttons can be shown at a time, so showing buttons of one type will
     * hide all buttons of other types.
     *
     * @param buttonsType
     */
    public void showButtonsOfType(@ButtonsType int buttonsType) {
        switch(buttonsType) {
            case BUTTON_TYPE_NAVIGATION:
                setNavigationButtonsVisibility(View.VISIBLE);
                setKeyguardButtonsVisibility(View.GONE);
                setOcclusionButtonsVisibility(View.GONE);
                break;
            case BUTTON_TYPE_KEYGUARD:
                setNavigationButtonsVisibility(View.GONE);
                setKeyguardButtonsVisibility(View.VISIBLE);
                setOcclusionButtonsVisibility(View.GONE);
                break;
            case BUTTON_TYPE_OCCLUSION:
                setNavigationButtonsVisibility(View.GONE);
                setKeyguardButtonsVisibility(View.GONE);
                setOcclusionButtonsVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * Sets the system bar view's disabled state and runnable when disabled.
     */
    public void setDisabledSystemBarButton(int viewId, boolean disabled, Runnable runnable,
                @Nullable String buttonName) {
        CarSystemBarButton button = findViewById(viewId);
        if (button != null) {
            if (DEBUG) {
                Log.d(TAG, "setDisabledSystemBarButton for: " + buttonName + " to: " + disabled);
            }
            button.setDisabled(disabled, runnable);
        }
    }

    /**
     * Sets the system bar specific View container's visibility. ViewName is used just for
     * debugging.
     */
    public void setVisibilityByViewId(int viewId, @Nullable String viewName,
                @View.Visibility int visibility) {
        View v = findViewById(viewId);
        if (v != null) {
            if (DEBUG) Log.d(TAG, "setVisibilityByViewId for: " + viewName + " to: " + visibility);
            v.setVisibility(visibility);
        }
    }

    /**
     * Sets the HvacPanelOverlayViewController and adds HVAC button listeners
     */
    public void registerHvacPanelOverlayViewController(HvacPanelOverlayViewController controller) {
        mHvacPanelOverlayViewController = controller;
        if (mHvacPanelOverlayViewController != null && mHvacButton != null) {
            mHvacPanelOverlayViewController.registerViewStateListener(mHvacButton);
        }
    }

    /**
     * Sets the NotificationPanelViewController and adds button listeners
     */
    public void registerNotificationPanelViewController(
            NotificationPanelViewController controller) {
        mNotificationPanelViewController = controller;
        if (mNotificationPanelViewController != null && mNotificationsButton != null) {
            mNotificationPanelViewController.registerViewStateListener(mNotificationsButton);
        }
    }

    private void setNavigationButtonsVisibility(@View.Visibility int visibility) {
        if (mNavButtons != null) {
            mNavButtons.setVisibility(visibility);
        }
    }

    private void setKeyguardButtonsVisibility(@View.Visibility int visibility) {
        if (mLockScreenButtons != null) {
            mLockScreenButtons.setVisibility(visibility);
        }
    }

    private void setOcclusionButtonsVisibility(@View.Visibility int visibility) {
        if (mOcclusionButtons != null) {
            mOcclusionButtons.setVisibility(visibility);
        }
    }

    private void triggerAllTouchListeners(View view, MotionEvent event) {
        if (mStatusBarWindowTouchListeners == null) {
            return;
        }
        for (OnTouchListener listener : mStatusBarWindowTouchListeners) {
            listener.onTouch(view, event);
        }
    }

    /**
     * Toggles the notification unseen indicator on/off.
     *
     * @param hasUnseen true if the unseen notification count is great than 0.
     */
    public void toggleNotificationUnseenIndicator(Boolean hasUnseen) {
        if (mNotificationsButton == null) return;

        mNotificationsButton.setUnseen(hasUnseen);
    }
}
