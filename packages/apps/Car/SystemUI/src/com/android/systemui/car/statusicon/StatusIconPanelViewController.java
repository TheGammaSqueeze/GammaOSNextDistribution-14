/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.statusicon;

import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
import static android.widget.ListPopupWindow.WRAP_CONTENT;

import android.annotation.DimenRes;
import android.annotation.LayoutRes;
import android.app.PendingIntent;
import android.car.app.CarActivityManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.qc.QCItem;
import com.android.car.qc.view.QCView;
import com.android.car.ui.FocusParkingView;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.qc.SystemUIQCViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.ViewController;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * A controller for a panel view associated with a status icon.
 */
public class StatusIconPanelViewController extends ViewController<View> {
    private final Context mContext;
    private final UserTracker mUserTracker;
    private final CarServiceProvider mCarServiceProvider;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final ConfigurationController mConfigurationController;
    private final CarSystemBarElementInitializer mCarSystemBarElementInitializer;
    private final String mIdentifier;
    @LayoutRes
    private final int mPanelLayoutRes;
    @DimenRes
    private final int mPanelWidthRes;
    private final int mXOffsetPixel;
    private final int mYOffsetPixel;
    private final int mPanelGravity;
    private final boolean mIsDisabledWhileDriving;
    private final boolean mShowAsDropDown;
    private final ArrayList<SystemUIQCViewController> mQCViewControllers = new ArrayList<>();

    private PopupWindow mPanel;
    private ViewGroup mPanelContent;
    private CarUxRestrictionsUtil mCarUxRestrictionsUtil;
    private CarActivityManager mCarActivityManager;
    private float mDimValue = -1.0f;
    private View.OnClickListener mOnClickListener;

    private final ConfigurationController.ConfigurationListener mConfigurationListener =
            new ConfigurationController.ConfigurationListener() {
                @Override
                public void onLayoutDirectionChanged(boolean isLayoutRtl) {
                    recreatePanel();
                }
            };

    private final CarUxRestrictionsUtil.OnUxRestrictionsChangedListener
            mUxRestrictionsChangedListener =
            new CarUxRestrictionsUtil.OnUxRestrictionsChangedListener() {
                @Override
                public void onRestrictionsChanged(@NonNull CarUxRestrictions carUxRestrictions) {
                    if (mIsDisabledWhileDriving
                            && carUxRestrictions.isRequiresDistractionOptimization()
                            && isPanelShowing()) {
                        mPanel.dismiss();
                    }
                }
            };

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            car -> {
                mCarActivityManager = car.getCarManager(CarActivityManager.class);
            };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean isIntentFromSelf =
                    intent.getIdentifier() != null && intent.getIdentifier().equals(mIdentifier);

            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action) && !isIntentFromSelf
                    && isPanelShowing()) {
                mPanel.dismiss();
            }
        }
    };

    private final UserTracker.Callback mUserTrackerCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, Context userContext) {
            mBroadcastDispatcher.unregisterReceiver(mBroadcastReceiver);
            mBroadcastDispatcher.registerReceiver(mBroadcastReceiver,
                    new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                    mUserTracker.getUserHandle());
        }
    };

    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener =
            (oldFocus, newFocus) -> {
                if (isPanelShowing() && oldFocus != null && newFocus instanceof FocusParkingView) {
                    // When nudging out of the panel, RotaryService will focus on the
                    // FocusParkingView to clear the focus highlight. When this occurs, dismiss the
                    // panel.
                    mPanel.dismiss();
                }
            };

    private final QCView.QCActionListener mQCActionListener = (item, action) -> {
        if (!isPanelShowing()) {
            return;
        }
        if (action instanceof PendingIntent) {
            if (((PendingIntent) action).isActivity()) {
                mPanel.dismiss();
            }
        } else if (action instanceof QCItem.ActionHandler) {
            if (((QCItem.ActionHandler) action).isActivity()) {
                mPanel.dismiss();
            }
        }
    };

    private StatusIconPanelViewController(Context context,
            UserTracker userTracker,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configurationController,
            CarSystemBarElementInitializer elementInitializer,
            View anchorView, @LayoutRes int layoutRes, @DimenRes int widthRes,
            int xOffset, int yOffset, int gravity, boolean isDisabledWhileDriving,
            boolean showAsDropDown) {
        super(anchorView);
        mContext = context;
        mUserTracker = userTracker;
        mCarServiceProvider = carServiceProvider;
        mBroadcastDispatcher = broadcastDispatcher;
        mConfigurationController = configurationController;
        mCarSystemBarElementInitializer = elementInitializer;
        mIsDisabledWhileDriving = isDisabledWhileDriving;
        mPanelLayoutRes = layoutRes;
        mPanelWidthRes = widthRes;
        mXOffsetPixel = xOffset;
        mYOffsetPixel = yOffset;
        mPanelGravity = gravity;
        mShowAsDropDown = showAsDropDown;
        mIdentifier = Integer.toString(System.identityHashCode(this));
    }

    @Override
    protected void onInit() {
        mOnClickListener = v -> {
            if (mIsDisabledWhileDriving && mCarUxRestrictionsUtil.getCurrentRestrictions()
                    .isRequiresDistractionOptimization()) {
                dismissAllSystemDialogs();
                Toast.makeText(mContext, R.string.car_ui_restricted_while_driving,
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (mPanel == null && !createPanel()) {
                return;
            }

            if (mPanel.isShowing()) {
                mPanel.dismiss();
                return;
            }

            // Dismiss all currently open system dialogs before opening this panel.
            dismissAllSystemDialogs();

            registerFocusListener(true);

            if (CarSystemUIUserUtil.isMUMDSystemUI()
                    && mPanelLayoutRes == R.layout.qc_profile_switcher) {
                // TODO(b/269490856): consider removal of UserPicker carve-outs
                if (mCarActivityManager != null) {
                    mCarActivityManager.startUserPickerOnDisplay(mContext.getDisplayId());
                }
            } else {
                if (mShowAsDropDown) {
                    // TODO(b/202563671): remove yOffsetPixel when the PopupWindow API is updated.
                    mPanel.showAsDropDown(mView, mXOffsetPixel, mYOffsetPixel, mPanelGravity);
                } else {
                    int verticalGravity = mPanelGravity & Gravity.VERTICAL_GRAVITY_MASK;
                    int animationStyle = verticalGravity == Gravity.BOTTOM
                            ? com.android.internal.R.style.Animation_DropDownUp
                            : com.android.internal.R.style.Animation_DropDownDown;
                    mPanel.setAnimationStyle(animationStyle);
                    mPanel.showAtLocation(mView, mPanelGravity, mXOffsetPixel, mYOffsetPixel);
                }
                mView.setSelected(true);
                setAnimatedStatusIconHighlightedStatus(true);
                dimBehind(mPanel);
            }
        };

        mView.setOnClickListener(mOnClickListener);
    }

    @Override
    protected void onViewAttached() {
        if (mPanel == null) {
            createPanel();
        }
        mBroadcastDispatcher.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                mUserTracker.getUserHandle());
        mUserTracker.addCallback(mUserTrackerCallback, mContext.getMainExecutor());
        mConfigurationController.addCallback(mConfigurationListener);

        if (mIsDisabledWhileDriving) {
            mCarUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(mContext);
            mCarUxRestrictionsUtil.register(mUxRestrictionsChangedListener);
        }
        mCarServiceProvider.addListener(mCarServiceOnConnectedListener);
    }

    @Override
    protected void onViewDetached() {
        reset();
        if (mCarUxRestrictionsUtil != null) {
            mCarUxRestrictionsUtil.unregister(mUxRestrictionsChangedListener);
        }
        mCarServiceProvider.removeListener(mCarServiceOnConnectedListener);
        mConfigurationController.removeCallback(mConfigurationListener);
        mUserTracker.removeCallback(mUserTrackerCallback);
        mBroadcastDispatcher.unregisterReceiver(mBroadcastReceiver);
    }

    @VisibleForTesting
    PopupWindow getPanel() {
        return mPanel;
    }

    @VisibleForTesting
    BroadcastReceiver getBroadcastReceiver() {
        return mBroadcastReceiver;
    }

    @VisibleForTesting
    String getIdentifier() {
        return mIdentifier;
    }

    @VisibleForTesting
    View.OnClickListener getOnClickListener() {
        return mOnClickListener;
    }

    @VisibleForTesting
    ConfigurationController.ConfigurationListener getConfigurationListener() {
        return mConfigurationListener;
    }

    @VisibleForTesting
    UserTracker.Callback getUserTrackerCallback() {
        return mUserTrackerCallback;
    }

    @VisibleForTesting
    ViewTreeObserver.OnGlobalFocusChangeListener getFocusChangeListener() {
        return mFocusChangeListener;
    }

    @VisibleForTesting
    QCView.QCActionListener getQCActionListener() {
        return mQCActionListener;
    }

    /**
     * Create the PopupWindow panel and assign to {@link mPanel}.
     * @return true if the panel was created, false otherwise
     */
    private boolean createPanel() {
        if (mPanelWidthRes == 0 || mPanelLayoutRes == 0) {
            return false;
        }

        int panelWidth = mContext.getResources().getDimensionPixelSize(mPanelWidthRes);
        Drawable panelBackgroundDrawable = mContext.getResources()
                .getDrawable(R.drawable.status_icon_panel_bg, mContext.getTheme());
        mPanelContent = (ViewGroup) LayoutInflater.from(mContext).inflate(mPanelLayoutRes,
                /* root= */ null);
        // clip content to the panel background (to handle rounded corners)
        mPanelContent.setOutlineProvider(new DrawableViewOutlineProvider(panelBackgroundDrawable));
        mPanelContent.setClipToOutline(true);

        // initialize special views
        initQCElementViews(mPanelContent);

        // initialize panel
        mPanel = new PopupWindow(mPanelContent, panelWidth, WRAP_CONTENT);
        mPanel.setBackgroundDrawable(panelBackgroundDrawable);
        mPanel.setWindowLayoutType(TYPE_SYSTEM_DIALOG);
        mPanel.setFocusable(true);
        mPanel.setOutsideTouchable(false);
        mPanel.setOnDismissListener(() -> {
            setAnimatedStatusIconHighlightedStatus(false);
            mView.setSelected(false);
            registerFocusListener(false);
        });

        return true;
    }

    private void dimBehind(PopupWindow popupWindow) {
        View container = popupWindow.getContentView().getRootView();
        WindowManager wm = mContext.getSystemService(WindowManager.class);

        if (wm == null) return;

        if (mDimValue < 0) {
            mDimValue = mContext.getResources().getFloat(R.dimen.car_status_icon_panel_dim);
        }

        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) container.getLayoutParams();
        lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        lp.dimAmount = mDimValue;
        wm.updateViewLayout(container, lp);
    }

    private void dismissAllSystemDialogs() {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        intent.setIdentifier(mIdentifier);
        mContext.sendBroadcastAsUser(intent, mUserTracker.getUserHandle());
    }

    private void registerFocusListener(boolean register) {
        if (mPanelContent == null) {
            return;
        }
        if (register) {
            mPanelContent.getViewTreeObserver().addOnGlobalFocusChangeListener(
                    mFocusChangeListener);
        } else {
            mPanelContent.getViewTreeObserver().removeOnGlobalFocusChangeListener(
                    mFocusChangeListener);
        }
    }

    private void reset() {
        if (mPanel == null) return;

        mPanel.dismiss();
        mPanel = null;
        mPanelContent = null;
        mQCViewControllers.forEach(SystemUIQCViewController::destroyQCViews);
        mQCViewControllers.clear();
    }

    private void recreatePanel() {
        reset();
        createPanel();
    }

    private void initQCElementViews(ViewGroup rootView) {
        List<CarSystemBarElementController> controllers =
                mCarSystemBarElementInitializer.initializeCarSystemBarElements(rootView);
        for (CarSystemBarElementController controller : controllers) {
            if (controller instanceof SystemUIQCViewController) {
                SystemUIQCViewController qcController = (SystemUIQCViewController) controller;
                qcController.setActionListener(mQCActionListener);
                mQCViewControllers.add(qcController);
            }
        }
    }

    private <T extends View> List<T> findViewsOfType(ViewGroup rootView, Class<T> clazz) {
        List<T> views = new ArrayList<>();
        for (int i = 0; i < rootView.getChildCount(); i++) {
            View v = rootView.getChildAt(i);
            if (clazz.isInstance(v)) {
                views.add(clazz.cast(v));
            } else if (v instanceof ViewGroup) {
                views.addAll(findViewsOfType((ViewGroup) v, clazz));
            }
        }
        return views;
    }

    private void setAnimatedStatusIconHighlightedStatus(boolean isHighlighted) {
        if (mView instanceof AnimatedStatusIcon) {
            ((AnimatedStatusIcon) mView).setIconHighlighted(isHighlighted);
        }
    }

    private boolean isPanelShowing() {
        return mPanel != null && mPanel.isShowing();
    }

    private static class DrawableViewOutlineProvider extends ViewOutlineProvider {
        private final Drawable mDrawable;

        private DrawableViewOutlineProvider(Drawable drawable) {
            mDrawable = drawable;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            if (mDrawable != null) {
                mDrawable.getOutline(outline);
            } else {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
                outline.setAlpha(0.0f);
            }
        }
    }

    /** Daggerized builder for StatusIconPanelViewController */
    public static class Builder {
        private final Context mContext;
        private final UserTracker mUserTracker;
        private final CarServiceProvider mCarServiceProvider;
        private final BroadcastDispatcher mBroadcastDispatcher;
        private final ConfigurationController mConfigurationController;
        private final CarSystemBarElementInitializer mCarSystemBarElementInitializer;

        private int mXOffset = 0;
        private int mYOffset;
        private int mGravity = Gravity.TOP | Gravity.START;
        private boolean mIsDisabledWhileDriving = false;
        private boolean mShowAsDropDown = true;

        @Inject
        public Builder(
                Context context,
                UserTracker userTracker,
                CarServiceProvider carServiceProvider,
                BroadcastDispatcher broadcastDispatcher,
                ConfigurationController configurationController,
                CarSystemBarElementInitializer elementInitializer) {
            mContext = context;
            mUserTracker = userTracker;
            mCarServiceProvider = carServiceProvider;
            mBroadcastDispatcher = broadcastDispatcher;
            mConfigurationController = configurationController;
            mCarSystemBarElementInitializer = elementInitializer;

            int panelMarginTop = mContext.getResources().getDimensionPixelSize(
                    R.dimen.car_status_icon_panel_margin_top);
            int topSystemBarHeight = mContext.getResources().getDimensionPixelSize(
                    R.dimen.car_top_system_bar_height);
            // TODO(b/202563671): remove mYOffset when the PopupWindow API is updated.
            mYOffset = panelMarginTop - topSystemBarHeight;
        }

        /** Set the panel offset in the x direction by a specified number of pixels. */
        public Builder setXOffset(int offset) {
            mXOffset = offset;
            return this;
        }

        /** Set the panel offset in the y direction by a specified number of pixels. */
        public Builder setYOffset(int offset) {
            mYOffset = offset;
            return this;
        }

        /** Set the panel's gravity - by default the gravity will be `Gravity.TOP | Gravity.START`*/
        public Builder setGravity(int gravity) {
            mGravity = gravity;
            return this;
        }

        /** Set whether the panel should be shown while driving or not - defaults to true. */
        public Builder setDisabledWhileDriving(boolean disabled) {
            mIsDisabledWhileDriving = disabled;
            return this;
        }

        /**
         * Set whether the panel should be shown as a dropdown (vs. at a specific location)
         * - defaults to true.
         */
        public Builder setShowAsDropDown(boolean dropDown) {
            mShowAsDropDown = dropDown;
            return this;
        }

        /**
         * Builds the controller with the required parameters of anchor view, panel layout resource,
         * and panel width resources.
         */
        public StatusIconPanelViewController build(View anchorView, @LayoutRes int layoutRes,
                @DimenRes int widthRes) {
            return new StatusIconPanelViewController(mContext, mUserTracker, mCarServiceProvider,
                    mBroadcastDispatcher, mConfigurationController, mCarSystemBarElementInitializer,
                    anchorView, layoutRes, widthRes, mXOffset, mYOffset, mGravity,
                    mIsDisabledWhileDriving, mShowAsDropDown);
        }
    }
}
