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

import static android.content.Intent.ACTION_OVERLAY_CHANGED;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

import static com.android.systemui.car.Flags.configAwareSystemui;
import static com.android.systemui.car.systembar.SystemBarConfigs.BOTTOM;
import static com.android.systemui.car.systembar.SystemBarConfigs.LEFT;
import static com.android.systemui.car.systembar.SystemBarConfigs.RIGHT;
import static com.android.systemui.car.systembar.SystemBarConfigs.TOP;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_SEMI_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_TRANSPARENT;

import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.StatusBarManager.Disable2Flags;
import android.app.StatusBarManager.DisableFlags;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsets.Type.InsetsType;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.VisibleForTesting;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.displaycompat.ToolbarController;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.systemui.statusbar.AutoHideUiElement;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy;
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.wm.MDSystemBarsController;

import dagger.Lazy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/** Navigation bars customized for the automotive use case. */
@SysUISingleton
public class CarSystemBar implements CoreStartable, CommandQueue.Callbacks,
        ConfigurationController.ConfigurationListener,
        MDSystemBarsController.Listener {
    private static final String TAG = CarSystemBar.class.getSimpleName();
    private static final String OVERLAY_FILTER_DATA_SCHEME = "package";

    protected static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;
    private final CarSystemBarController mCarSystemBarController;
    private final SysuiDarkIconDispatcher mStatusBarIconController;
    private final WindowManager mWindowManager;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final CommandQueue mCommandQueue;
    private final AutoHideController mAutoHideController;
    private final ButtonSelectionStateListener mButtonSelectionStateListener;
    private final DelayableExecutor mExecutor;
    private final Executor mUiBgExecutor;
    private final IStatusBarService mBarService;
    private final DisplayTracker mDisplayTracker;
    private final Lazy<KeyguardStateController> mKeyguardStateControllerLazy;
    private final Lazy<PhoneStatusBarPolicy> mIconPolicyLazy;
    private final HvacController mHvacController;
    private final ConfigurationController mConfigurationController;
    private final CarSystemBarRestartTracker mCarSystemBarRestartTracker;
    private final int mDisplayId;
    private final SystemBarConfigs mSystemBarConfigs;
    @Nullable
    private final ToolbarController mDisplayCompatToolbarController;
    private UiModeManager mUiModeManager;
    private StatusBarSignalPolicy mSignalPolicy;

    // If the nav bar should be hidden when the soft keyboard is visible.
    private boolean mHideTopBarForKeyboard;
    private boolean mHideLeftBarForKeyboard;
    private boolean mHideRightBarForKeyboard;
    private boolean mHideBottomBarForKeyboard;
    // Nav bar views.
    private ViewGroup mTopSystemBarWindow;
    private ViewGroup mBottomSystemBarWindow;
    private ViewGroup mLeftSystemBarWindow;
    private ViewGroup mRightSystemBarWindow;
    private CarSystemBarView mTopSystemBarView;
    private CarSystemBarView mBottomSystemBarView;
    private CarSystemBarView mLeftSystemBarView;
    private CarSystemBarView mRightSystemBarView;
    private boolean mTopSystemBarAttached;
    private boolean mBottomSystemBarAttached;
    private boolean mLeftSystemBarAttached;
    private boolean mRightSystemBarAttached;

    // To be attached to the navigation bars such that they can close the notification panel if
    // it's open.
    private boolean mDeviceIsSetUpForUser = true;
    private boolean mIsUserSetupInProgress = false;

    private AppearanceRegion[] mAppearanceRegions = new AppearanceRegion[0];
    @BarTransitions.TransitionMode
    private int mStatusBarMode;
    @BarTransitions.TransitionMode
    private int mSystemBarMode;
    private boolean mStatusBarTransientShown;
    private boolean mNavBarTransientShown;

    private boolean mIsUiModeNight = false;
    private MDSystemBarsController mMDSystemBarsController;

    private Locale mCurrentLocale;

    @Inject
    public CarSystemBar(Context context,
            CarSystemBarController carSystemBarController,
            // TODO(b/156052638): Should not need to inject LightBarController
            LightBarController lightBarController,
            DarkIconDispatcher darkIconDispatcher,
            WindowManager windowManager,
            CarDeviceProvisionedController deviceProvisionedController,
            CommandQueue commandQueue,
            AutoHideController autoHideController,
            ButtonSelectionStateListener buttonSelectionStateListener,
            @Main DelayableExecutor mainExecutor,
            @UiBackground Executor uiBgExecutor,
            IStatusBarService barService,
            Lazy<KeyguardStateController> keyguardStateControllerLazy,
            Lazy<PhoneStatusBarPolicy> iconPolicyLazy,
            HvacController hvacController,
            StatusBarSignalPolicy signalPolicy,
            SystemBarConfigs systemBarConfigs,
            ConfigurationController configurationController,
            CarSystemBarRestartTracker restartTracker,
            DisplayTracker displayTracker,
            Optional<MDSystemBarsController> mdSystemBarsController,
            @Nullable ToolbarController toolbarController
    ) {
        mContext = context;
        mCarSystemBarController = carSystemBarController;
        mStatusBarIconController = (SysuiDarkIconDispatcher) darkIconDispatcher;
        mWindowManager = windowManager;
        mCarDeviceProvisionedController = deviceProvisionedController;
        mCommandQueue = commandQueue;
        mAutoHideController = autoHideController;
        mButtonSelectionStateListener = buttonSelectionStateListener;
        mExecutor = mainExecutor;
        mUiBgExecutor = uiBgExecutor;
        mBarService = barService;
        mKeyguardStateControllerLazy = keyguardStateControllerLazy;
        mIconPolicyLazy = iconPolicyLazy;
        mHvacController = hvacController;
        mSystemBarConfigs = systemBarConfigs;
        mSignalPolicy = signalPolicy;
        mDisplayId = context.getDisplayId();
        mUiModeManager = mContext.getSystemService(UiModeManager.class);
        mDisplayTracker = displayTracker;
        mIsUiModeNight = mContext.getResources().getConfiguration().isNightModeActive();
        mMDSystemBarsController = mdSystemBarsController.orElse(null);
        mCurrentLocale = mContext.getResources().getConfiguration().getLocales().get(0);
        mConfigurationController = configurationController;
        mCarSystemBarRestartTracker = restartTracker;
        mDisplayCompatToolbarController = toolbarController;
    }

    private void registerOverlayChangeBroadcastReceiver() {
        if (!configAwareSystemui()) {
            if (DEBUG) {
                Log.d(TAG, "Ignore overlay change for car systemui");
            }
            return;
        }
        IntentFilter overlayFilter = new IntentFilter(ACTION_OVERLAY_CHANGED);
        overlayFilter.addDataScheme(OVERLAY_FILTER_DATA_SCHEME);
        overlayFilter.addDataSchemeSpecificPart(mContext.getPackageName(),
                PatternMatcher.PATTERN_LITERAL);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mTopSystemBarAttached || mBottomSystemBarAttached || mLeftSystemBarAttached
                        || mRightSystemBarAttached) {
                    restartSystemBars();
                }
            }
        };
        mContext.registerReceiverAsUser(receiver, UserHandle.ALL,
                overlayFilter, /* broadcastPermission= */null, /* handler= */ null);
    }

    @Override
    public void start() {
        // Set initial state.
        mHideTopBarForKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(TOP);
        mHideBottomBarForKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(BOTTOM);
        mHideLeftBarForKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(LEFT);
        mHideRightBarForKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(RIGHT);

        // Connect into the status bar manager service
        mCommandQueue.addCallback(this);

        RegisterStatusBarResult result = null;
        //Register only for Primary User.
        if (!CarSystemUIUserUtil.isSecondaryMUMDSystemUI()) {
            try {
                result = mBarService.registerStatusBar(mCommandQueue);
            } catch (RemoteException ex) {
                ex.rethrowFromSystemServer();
            }
        } else if (mMDSystemBarsController != null) {
            mMDSystemBarsController.addListener(this);
        }

        if (result != null) {
            onSystemBarAttributesChanged(mDisplayId, result.mAppearance, result.mAppearanceRegions,
                    result.mNavbarColorManagedByIme, result.mBehavior,
                    result.mRequestedVisibleTypes,
                    result.mPackageName, result.mLetterboxDetails);

            // StatusBarManagerService has a back up of IME token and it's restored here.
            setImeWindowStatus(mDisplayId, result.mImeToken, result.mImeWindowVis,
                    result.mImeBackDisposition, result.mShowImeSwitcher);

            // Set up the initial icon state
            int numIcons = result.mIcons.size();
            for (int i = 0; i < numIcons; i++) {
                mCommandQueue.setIcon(result.mIcons.keyAt(i), result.mIcons.valueAt(i));
            }
        }

        mAutoHideController.setStatusBar(new AutoHideUiElement() {
            @Override
            public void synchronizeState() {
                // No op.
            }

            @Override
            public boolean isVisible() {
                return mStatusBarTransientShown;
            }

            @Override
            public void hide() {
                clearTransient();
            }
        });

        mAutoHideController.setNavigationBar(new AutoHideUiElement() {
            @Override
            public void synchronizeState() {
                // No op.
            }

            @Override
            public boolean isVisible() {
                return mNavBarTransientShown;
            }

            @Override
            public void hide() {
                clearTransient();
            }
        });

        mDeviceIsSetUpForUser = mCarDeviceProvisionedController.isCurrentUserSetup();
        mIsUserSetupInProgress = mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
        mCarDeviceProvisionedController.addCallback(
                new CarDeviceProvisionedListener() {
                    @Override
                    public void onUserSetupInProgressChanged() {
                        mExecutor.execute(() -> resetSystemBarContentIfNecessary());
                    }

                    @Override
                    public void onUserSetupChanged() {
                        mExecutor.execute(() -> resetSystemBarContentIfNecessary());
                    }

                    @Override
                    public void onUserSwitched() {
                        mExecutor.execute(() -> resetSystemBarContentIfNecessary());
                    }
                });

        mConfigurationController.addCallback(/* listener= */ this);
        registerOverlayChangeBroadcastReceiver();

        createSystemBar(result);

        TaskStackChangeListeners.getInstance().registerTaskStackListener(
                mButtonSelectionStateListener);
        TaskStackChangeListeners.getInstance().registerTaskStackListener(
                new TaskStackChangeListener() {
                    @Override
                    public void onLockTaskModeChanged(int mode) {
                        mCarSystemBarController.refreshSystemBar();
                    }

                    @Override
                    public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
                        if (mDisplayCompatToolbarController != null) {
                            mDisplayCompatToolbarController.update(taskInfo);
                        }
                    }
                });

        // Lastly, call to the icon policy to install/update all the icons.
        // Must be called on the main thread due to the use of observeForever() in
        // mIconPolicy.init().
        mExecutor.execute(() -> {
            mIconPolicyLazy.get().init();
        });
    }

    private void resetSystemBarContentIfNecessary() {
        boolean currentUserSetup = mCarDeviceProvisionedController.isCurrentUserSetup();
        boolean currentUserSetupInProgress = mCarDeviceProvisionedController
                .isCurrentUserSetupInProgress();
        if (mIsUserSetupInProgress != currentUserSetupInProgress
                || mDeviceIsSetUpForUser != currentUserSetup) {
            mDeviceIsSetUpForUser = currentUserSetup;
            mIsUserSetupInProgress = currentUserSetupInProgress;
            resetSystemBarContent(/* isProvisionedStateChange= */ true);
        }
    }

    /**
     * Remove all content from navbars and rebuild them. Used to allow for different nav bars
     * before and after the device is provisioned. . Also for change of density and font size.
     */
    private void resetSystemBarContent(boolean isProvisionedStateChange) {
        mCarSystemBarRestartTracker.notifyPendingRestart(/* recreateWindows= */ false,
                isProvisionedStateChange);

        if (!isProvisionedStateChange) {
            mCarSystemBarController.resetViewCache();
        }
        // remove and reattach all components such that we don't keep a reference to unused ui
        // elements
        mCarSystemBarController.removeAll();
        clearSystemBarWindow(/* removeUnusedWindow= */ false);

        buildNavBarContent();
        // If the UI was rebuilt (day/night change or user change) while the keyguard was up we need
        // to correctly respect that state.
        if (mKeyguardStateControllerLazy.get().isShowing()) {
            mCarSystemBarController.showAllKeyguardButtons(isDeviceSetupForUser());
        } else {
            mCarSystemBarController.showAllNavigationButtons(isDeviceSetupForUser());
        }

        // Upon restarting the Navigation Bar, CarFacetButtonController should immediately apply the
        // selection state that reflects the current task stack.
        mButtonSelectionStateListener.onTaskStackChanged();

        mCarSystemBarRestartTracker.notifyRestartComplete(/* recreateWindows= */ false,
                isProvisionedStateChange);
    }

    private boolean isDeviceSetupForUser() {
        return mDeviceIsSetUpForUser && !mIsUserSetupInProgress;
    }

    private void createSystemBar(RegisterStatusBarResult result) {
        buildNavBarWindows();
        buildNavBarContent();
        attachNavBarWindows();

        // Try setting up the initial state of the nav bar if applicable.
        if (result != null) {
            setImeWindowStatus(mDisplayTracker.getDefaultDisplayId(), result.mImeToken,
                    result.mImeWindowVis, result.mImeBackDisposition,
                    result.mShowImeSwitcher);
        }
    }

    private void buildNavBarWindows() {
        mTopSystemBarWindow = mCarSystemBarController.getTopWindow();
        mBottomSystemBarWindow = mCarSystemBarController.getBottomWindow();
        mLeftSystemBarWindow = mCarSystemBarController.getLeftWindow();
        mRightSystemBarWindow = mCarSystemBarController.getRightWindow();

        if (mDisplayCompatToolbarController != null) {
            if (mSystemBarConfigs
                    .isLeftDisplayCompatToolbarEnabled()) {
                mDisplayCompatToolbarController.init(mLeftSystemBarWindow);
            } else if (mSystemBarConfigs
                    .isRightDisplayCompatToolbarEnabled()) {
                mDisplayCompatToolbarController.init(mRightSystemBarWindow);
            }
        }
    }

    private void buildNavBarContent() {
        mTopSystemBarView = mCarSystemBarController.getTopBar(isDeviceSetupForUser());
        if (mTopSystemBarView != null) {
            mSystemBarConfigs.insetSystemBar(TOP, mTopSystemBarView);
            mHvacController.registerHvacViews(mTopSystemBarView);
            mTopSystemBarWindow.addView(mTopSystemBarView);
        }

        mBottomSystemBarView = mCarSystemBarController.getBottomBar(isDeviceSetupForUser());
        if (mBottomSystemBarView != null) {
            mSystemBarConfigs.insetSystemBar(BOTTOM, mBottomSystemBarView);
            mHvacController.registerHvacViews(mBottomSystemBarView);
            mBottomSystemBarWindow.addView(mBottomSystemBarView);
        }

        mLeftSystemBarView = mCarSystemBarController.getLeftBar(isDeviceSetupForUser());
        if (mLeftSystemBarView != null) {
            mSystemBarConfigs.insetSystemBar(LEFT, mLeftSystemBarView);
            mHvacController.registerHvacViews(mLeftSystemBarView);
            mLeftSystemBarWindow.addView(mLeftSystemBarView);
        }

        mRightSystemBarView = mCarSystemBarController.getRightBar(isDeviceSetupForUser());
        if (mRightSystemBarView != null) {
            mSystemBarConfigs.insetSystemBar(RIGHT, mRightSystemBarView);
            mHvacController.registerHvacViews(mRightSystemBarView);
            mRightSystemBarWindow.addView(mRightSystemBarView);
        }
    }

    private void attachNavBarWindows() {
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(this::attachNavBarBySide);
    }

    @VisibleForTesting
    ViewGroup getSystemBarWindowBySide(int side) {
        switch (side) {
            case TOP:
                return mTopSystemBarWindow;
            case BOTTOM:
                return mBottomSystemBarWindow;
            case LEFT:
                return mLeftSystemBarWindow;
            case RIGHT:
                return mRightSystemBarWindow;
            default:
                return null;
        }
    }

    private void attachNavBarBySide(int side) {
        switch (side) {
            case TOP:
                if (DEBUG) {
                    Log.d(TAG, "mTopSystemBarWindow = " + mTopSystemBarWindow
                            + ", mTopSystemBarAttached=" + mTopSystemBarAttached
                            + ", enabled=" + mSystemBarConfigs.getEnabledStatusBySide(TOP));
                }
                if (mTopSystemBarWindow != null && !mTopSystemBarAttached
                        && mSystemBarConfigs.getEnabledStatusBySide(TOP)) {
                    mWindowManager.addView(mTopSystemBarWindow,
                            mSystemBarConfigs.getLayoutParamsBySide(TOP));
                    mTopSystemBarAttached = true;
                }
                break;
            case BOTTOM:
                if (DEBUG) {
                    Log.d(TAG, "mBottomSystemBarWindow = " + mBottomSystemBarWindow
                            + ", mBottomSystemBarAttached=" + mBottomSystemBarAttached
                            + ", enabled=" + mSystemBarConfigs.getEnabledStatusBySide(BOTTOM));
                }
                if (mBottomSystemBarWindow != null && !mBottomSystemBarAttached
                        && mSystemBarConfigs.getEnabledStatusBySide(BOTTOM)) {
                    mWindowManager.addView(mBottomSystemBarWindow,
                            mSystemBarConfigs.getLayoutParamsBySide(BOTTOM));
                    mBottomSystemBarAttached = true;
                }
                break;
            case LEFT:
                if (DEBUG) {
                    Log.d(TAG, "mLeftSystemBarWindow = " + mLeftSystemBarWindow
                            + ", mLeftSystemBarAttached=" + mLeftSystemBarAttached
                            + ", enabled=" + mSystemBarConfigs.getEnabledStatusBySide(LEFT));
                }
                if (mLeftSystemBarWindow != null && !mLeftSystemBarAttached
                        && mSystemBarConfigs.getEnabledStatusBySide(LEFT)) {
                    mWindowManager.addView(mLeftSystemBarWindow,
                            mSystemBarConfigs.getLayoutParamsBySide(LEFT));
                    mLeftSystemBarAttached = true;
                }
                break;
            case RIGHT:
                if (DEBUG) {
                    Log.d(TAG, "mRightSystemBarWindow = " + mRightSystemBarWindow
                            + ", mRightSystemBarAttached=" + mRightSystemBarAttached
                            + ", "
                            + "enabled=" + mSystemBarConfigs.getEnabledStatusBySide(RIGHT));
                }
                if (mRightSystemBarWindow != null && !mRightSystemBarAttached
                        && mSystemBarConfigs.getEnabledStatusBySide(RIGHT)) {
                    mWindowManager.addView(mRightSystemBarWindow,
                            mSystemBarConfigs.getLayoutParamsBySide(RIGHT));
                    mRightSystemBarAttached = true;
                }
                break;
            default:
                return;
        }
    }

    /**
     * We register for soft keyboard visibility events such that we can hide the navigation bar
     * giving more screen space to the IME. Note: this is optional and controlled by
     * {@code com.android.internal.R.bool.config_hideNavBarForKeyboard}.
     */
    @Override
    public void setImeWindowStatus(int displayId, IBinder token, int vis, int backDisposition,
            boolean showImeSwitcher) {
        if (mContext.getDisplayId() != displayId) {
            return;
        }

        boolean isKeyboardVisible = (vis & InputMethodService.IME_VISIBLE) != 0;

        updateKeyboardVisibility(isKeyboardVisible);
    }

    private void updateKeyboardVisibility(boolean isKeyboardVisible) {
        if (mHideTopBarForKeyboard) {
            mCarSystemBarController.setTopWindowVisibility(
                    isKeyboardVisible ? View.GONE : View.VISIBLE);
        }

        if (mHideBottomBarForKeyboard) {
            mCarSystemBarController.setBottomWindowVisibility(
                    isKeyboardVisible ? View.GONE : View.VISIBLE);
        }

        if (mHideLeftBarForKeyboard) {
            mCarSystemBarController.setLeftWindowVisibility(
                    isKeyboardVisible ? View.GONE : View.VISIBLE);
        }
        if (mHideRightBarForKeyboard) {
            mCarSystemBarController.setRightWindowVisibility(
                    isKeyboardVisible ? View.GONE : View.VISIBLE);
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
        if (displayId != mDisplayId) {
            return;
        }
        boolean barModeChanged = updateStatusBarMode(
                mStatusBarTransientShown ? MODE_SEMI_TRANSPARENT : MODE_TRANSPARENT);
        int numStacks = appearanceRegions.length;
        boolean stackAppearancesChanged = mAppearanceRegions.length != numStacks;
        for (int i = 0; i < numStacks && !stackAppearancesChanged; i++) {
            stackAppearancesChanged |= !appearanceRegions[i].equals(mAppearanceRegions[i]);
        }
        if (stackAppearancesChanged || barModeChanged) {
            mAppearanceRegions = appearanceRegions;
            updateStatusBarAppearance();
        }
        mCarSystemBarController.refreshSystemBar();
    }

    @Override
    public void disable(int displayId, @DisableFlags int state1, @Disable2Flags int state2,
            boolean animate) {
        if (displayId != mDisplayId) {
            return;
        }
        mCarSystemBarController.setSystemBarStates(state1, state2);
    }

    private void updateStatusBarAppearance() {
        int numStacks = mAppearanceRegions.length;
        final ArrayList<Rect> lightBarBounds = new ArrayList<>();

        for (int i = 0; i < numStacks; i++) {
            final AppearanceRegion ar = mAppearanceRegions[i];
            if (isLight(ar.getAppearance())) {
                lightBarBounds.add(ar.getBounds());
            }
        }

        // If all stacks are light, all icons become dark.
        if (lightBarBounds.size() == numStacks) {
            mStatusBarIconController.setIconsDarkArea(null);
            mStatusBarIconController.getTransitionsController().setIconsDark(
                    /* dark= */ true, /* animate= */ false);
        } else if (lightBarBounds.isEmpty()) {
            // If no one is light, all icons become white.
            mStatusBarIconController.getTransitionsController().setIconsDark(
                    /* dark= */ false, /* animate= */ false);
        } else {
            // Not the same for every stack, update icons in area only.
            mStatusBarIconController.setIconsDarkArea(lightBarBounds);
            mStatusBarIconController.getTransitionsController().setIconsDark(
                    /* dark= */ true, /* animate= */ false);
        }
    }

    private static boolean isLight(int appearance) {
        return (appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0;
    }

    @Override
    public void showTransient(int displayId, int types, boolean isGestureOnSystemBar) {
        if (displayId != mDisplayId) {
            return;
        }
        if ((types & WindowInsets.Type.statusBars()) != 0) {
            if (!mStatusBarTransientShown) {
                mStatusBarTransientShown = true;
                handleTransientChanged();
            }
        }
        if ((types & WindowInsets.Type.navigationBars()) != 0) {
            if (!mNavBarTransientShown) {
                mNavBarTransientShown = true;
                handleTransientChanged();
            }
        }
    }

    @Override
    public void abortTransient(int displayId, int types) {
        if (displayId != mDisplayId) {
            return;
        }
        if ((types & (WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars())) == 0) {
            return;
        }
        clearTransient();
    }

    private void clearTransient() {
        if (mStatusBarTransientShown) {
            mStatusBarTransientShown = false;
            handleTransientChanged();
        }
        if (mNavBarTransientShown) {
            mNavBarTransientShown = false;
            handleTransientChanged();
        }
    }

    @VisibleForTesting
    boolean isStatusBarTransientShown() {
        return mStatusBarTransientShown;
    }

    @VisibleForTesting
    boolean isNavBarTransientShown() {
        return mNavBarTransientShown;
    }

    @VisibleForTesting
    void setSignalPolicy(StatusBarSignalPolicy signalPolicy) {
        mSignalPolicy = signalPolicy;
    }

    @Override
    public void dump(PrintWriter pw, String[] args) {
        pw.print("  mTaskStackListener=");
        pw.println(mButtonSelectionStateListener);
        pw.print("  mBottomSystemBarView=");
        pw.println(mBottomSystemBarView);
    }

    private void handleTransientChanged() {
        updateStatusBarMode(mStatusBarTransientShown ? MODE_SEMI_TRANSPARENT : MODE_TRANSPARENT);
        updateNavBarMode(mNavBarTransientShown ? MODE_SEMI_TRANSPARENT : MODE_TRANSPARENT);
    }

    // Returns true if the status bar mode has changed.
    private boolean updateStatusBarMode(int barMode) {
        if (mStatusBarMode != barMode) {
            mStatusBarMode = barMode;
            mAutoHideController.touchAutoHide();
            return true;
        }
        return false;
    }

    // Returns true if the nav bar mode has changed.
    private boolean updateNavBarMode(int barMode) {
        if (mSystemBarMode != barMode) {
            mSystemBarMode = barMode;
            mAutoHideController.touchAutoHide();
            return true;
        }
        return false;
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        Locale oldLocale = mCurrentLocale;
        mCurrentLocale = newConfig.getLocales().get(0);

        boolean isConfigNightMode = newConfig.isNightModeActive();
        if (isConfigNightMode == mIsUiModeNight
                && (mCurrentLocale != null && mCurrentLocale.equals(oldLocale)
                || mCurrentLocale == oldLocale)) {
            return;
        }

        // Refresh UI on Night mode or system language changes.
        if (isConfigNightMode != mIsUiModeNight) {
            mIsUiModeNight = isConfigNightMode;
            mUiModeManager.setNightModeActivated(mIsUiModeNight);
        }

        // cache the current state
        // The focused view will be destroyed during re-layout, causing the framework to adjust
        // the focus unexpectedly. To avoid that, move focus to a view that won't be
        // destroyed during re-layout and has no focus highlight (the FocusParkingView), then
        // move focus back to the previously focused view after re-layout.
        mCarSystemBarController.cacheAndHideFocus();
        String selectedQuickControlsClsName = null;
        View profilePickerView = null;
        boolean isProfilePickerOpen = false;
        if (mTopSystemBarView != null) {
            profilePickerView = mTopSystemBarView.findViewById(R.id.user_name);
        }
        if (profilePickerView != null) isProfilePickerOpen = profilePickerView.isSelected();
        if (isProfilePickerOpen) {
            profilePickerView.callOnClick();
        } else {
            selectedQuickControlsClsName =
                    mCarSystemBarController.getSelectedQuickControlsClassName();
            mCarSystemBarController.callQuickControlsOnClickFromClassName(
                    selectedQuickControlsClsName);
        }

        resetSystemBarContent(/* isProvisionedStateChange= */ false);

        // retrieve the previous state
        if (isProfilePickerOpen) {
            if (mTopSystemBarView != null) {
                profilePickerView = mTopSystemBarView.findViewById(R.id.user_name);
            }
            if (profilePickerView != null) profilePickerView.callOnClick();
        } else {
            mCarSystemBarController.callQuickControlsOnClickFromClassName(
                    selectedQuickControlsClsName);
        }
        mCarSystemBarController.restoreFocus();
    }

    @VisibleForTesting
    void restartSystemBars() {
        mCarSystemBarRestartTracker.notifyPendingRestart(/* recreateWindows= */ true,
                /* provisionedStateChanged= */ false);

        mCarSystemBarController.removeAll();
        mCarSystemBarController.resetSystemBarConfigs();
        clearSystemBarWindow(/* removeUnusedWindow= */ true);
        buildNavBarWindows();
        buildNavBarContent();
        attachNavBarWindows();

        mCarSystemBarRestartTracker.notifyRestartComplete(/* recreateWindows= */ true,
                /* provisionedStateChanged= */ false);
    }

    private void clearSystemBarWindow(boolean removeUnusedWindow) {
        if (mTopSystemBarWindow != null) {
            mTopSystemBarWindow.removeAllViews();
            mHvacController.unregisterViews(mTopSystemBarView);
            if (removeUnusedWindow) {
                mWindowManager.removeViewImmediate(mTopSystemBarWindow);
                mTopSystemBarAttached = false;
            }
            mTopSystemBarView = null;
        }

        if (mBottomSystemBarWindow != null) {
            mBottomSystemBarWindow.removeAllViews();
            mHvacController.unregisterViews(mBottomSystemBarView);
            if (removeUnusedWindow) {
                mWindowManager.removeViewImmediate(mBottomSystemBarWindow);
                mBottomSystemBarAttached = false;
            }
            mBottomSystemBarView = null;
        }

        if (mLeftSystemBarWindow != null) {
            mLeftSystemBarWindow.removeAllViews();
            mHvacController.unregisterViews(mLeftSystemBarView);
            if (removeUnusedWindow) {
                mWindowManager.removeViewImmediate(mLeftSystemBarWindow);
                mLeftSystemBarAttached = false;
            }
            mLeftSystemBarView = null;
        }

        if (mRightSystemBarWindow != null) {
            mRightSystemBarWindow.removeAllViews();
            mHvacController.unregisterViews(mRightSystemBarView);
            if (removeUnusedWindow) {
                mWindowManager.removeViewImmediate(mRightSystemBarWindow);
                mRightSystemBarAttached = false;
            }
            mRightSystemBarView = null;
        }
    }

    @VisibleForTesting
    void setUiModeManager(UiModeManager uiModeManager) {
        mUiModeManager = uiModeManager;
    }

    @Override
    public void onKeyboardVisibilityChanged(boolean show) {
        updateKeyboardVisibility(show);
    }
}
