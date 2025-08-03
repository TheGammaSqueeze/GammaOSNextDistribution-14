/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.common;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.VIEW_UNKNOWN;
import static com.android.managedprovisioning.provisioning.Constants.LOCK_TO_PORTRAIT_MODE;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.android.managedprovisioning.ManagedProvisioningBaseApplication;
import com.android.managedprovisioning.ManagedProvisioningScreens;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.preprovisioning.EncryptionController;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Base class for setting up the layout.
 */
@AndroidEntryPoint(AppCompatActivity.class)
public abstract class SetupLayoutActivity extends Hilt_SetupLayoutActivity {
    protected final Utils mUtils;
    protected final SettingsFacade mSettingsFacade;
    private final ThemeHelper mThemeHelper;
    private final TransitionHelper mTransitionHelper;
    private TimeLogger mTimeLogger;

    public SetupLayoutActivity() {
        this(new Utils(), new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
    }

    @VisibleForTesting
    protected SetupLayoutActivity(
            Utils utils, SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        mUtils = utils;
        mSettingsFacade = settingsFacade;
        mThemeHelper = themeHelper;
        // TODO(b/183036855): Add dependency injection in ManagedProvisioning
        mTransitionHelper = new TransitionHelper();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!isWaitingScreen()) {
            mTransitionHelper.applyContentScreenTransitions(this);
        }
        updateDefaultNightMode();
        setTheme(mThemeHelper.inferThemeResId(this, getIntent()));
        if (shouldSetupDynamicColors()) {
            mThemeHelper.setupDynamicColors(this);
        }
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        mTimeLogger = new TimeLogger(this, getMetricsCategory());
        mTimeLogger.start();

        // lock orientation to portrait on phones if necessary
        if (LOCK_TO_PORTRAIT_MODE && getResources().getBoolean(R.bool.lock_to_portrait)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        getBaseApplication().keepScreenOn(this);
        logMetrics();
    }

    protected boolean shouldSetupDynamicColors() {
        return true;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateDefaultNightMode();
    }

    private void updateDefaultNightMode() {
        int nightMode = mThemeHelper.getDefaultNightMode(this, getIntent());
        AppCompatDelegate delegate = AppCompatDelegate.create(this, /* callback= */ null);
        delegate.setLocalNightMode(nightMode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isWaitingScreen()) {
            mTransitionHelper.applyWaitingScreenTransitions(this);
        }
    }

    private void logMetrics() {
        // TODO(b/183036855): Add dependency injection in ManagedProvisioning
        ProvisioningAnalyticsTracker analyticsTracker = new ProvisioningAnalyticsTracker(
                MetricsWriterFactory.getMetricsWriter(this, new SettingsFacade()),
                new ManagedProvisioningSharedPreferences(this));
        final int orientation = getResources().getConfiguration().orientation;
        analyticsTracker.logIsLandscape(
                orientation == Configuration.ORIENTATION_LANDSCAPE,
                getLocalClassName());
    }

    @Override
    public void onDestroy() {
        mTimeLogger.stop();
        super.onDestroy();
    }

    protected int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    protected Utils getUtils() {
        return mUtils;
    }

    protected ThemeHelper getThemeHelper() {
        return mThemeHelper;
    }

    protected TransitionHelper getTransitionHelper() {
        return mTransitionHelper;
    }

    private ManagedProvisioningBaseApplication getBaseApplication() {
        return ((ManagedProvisioningBaseApplication) getApplication());
    }

    protected Class<? extends Activity> getActivityForScreen(ManagedProvisioningScreens screen) {
        return getBaseApplication().getActivityClassForScreen(screen);
    }

    protected EncryptionController getEncryptionController() {
        return getBaseApplication().getEncryptionController();
    }

    /**
     * Whether the current screen is a waiting screen.
     *
     * <p>A waiting screen is a screen that shows a spinner and not content.
     */
    protected boolean isWaitingScreen() {
        return false;
    }

    /**
     * Constructs and shows a {@link DialogFragment} unless it is already displayed.
     *
     * @param dialogBuilder Lightweight builder, that it is inexpensive to discard it if dialog
     *                      already shown.
     * @param tag           The tag for this dialog, as per
     *                      {@link FragmentTransaction#add(Fragment, String)}.
     */
    protected void showDialog(DialogBuilder dialogBuilder, String tag) {
        FragmentManager fragmentManager = getFragmentManager();
        if (!isDialogAdded(tag)) {
            dialogBuilder.build().show(fragmentManager, tag);
        }
    }

    /**
     * Checks whether the {@link DialogFragment} associated with the given tag is currently showing.
     *
     * @param tag The tag for this dialog.
     */
    protected boolean isDialogAdded(String tag) {
        Fragment fragment = getFragmentManager().findFragmentByTag(tag);
        return (fragment != null) && (fragment.isAdded());
    }
}