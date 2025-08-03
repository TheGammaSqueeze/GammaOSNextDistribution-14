/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.common;

import static android.view.View.GONE;
import static android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS;
import static android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS;

import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.drivingstate.CarUxRestrictionsManager.OnUxRestrictionsChangedListener;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager.OnBackStackChangedListener;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.car.apps.common.util.Themes;
import com.android.car.settings.R;
import com.android.car.settings.common.rotary.SettingsFocusParkingView;
import com.android.car.ui.baselayout.Insets;
import com.android.car.ui.baselayout.InsetsChangedListener;
import com.android.car.ui.core.CarUi;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.settingslib.core.lifecycle.HideNonSystemOverlayMixin;

import java.util.Collections;
import java.util.List;

/**
 * Base activity class for car settings, provides a action bar with a back button that goes to
 * previous activity.
 */
public abstract class BaseCarSettingsActivity extends FragmentActivity implements
        FragmentHost, OnUxRestrictionsChangedListener, UxRestrictionsProvider,
        OnBackStackChangedListener, PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        InsetsChangedListener {

    /**
     * Meta data key for specifying the preference key of the top level menu preference that the
     * initial activity's fragment falls under. If this is not specified in the activity's
     * metadata, the top level menu preference will not be highlighted upon activity launch.
     */
    public static final String META_DATA_KEY_HEADER_KEY =
            "com.android.car.settings.TOP_LEVEL_HEADER_KEY";

    /**
     * Meta data key for specifying activities that should always be shown in the single pane
     * configuration. If not specified for the activity, the activity will default to the value
     * {@link R.bool.config_global_force_single_pane}.
     */
    public static final String META_DATA_KEY_SINGLE_PANE = "com.android.car.settings.SINGLE_PANE";

    private static final Logger LOG = new Logger(BaseCarSettingsActivity.class);
    private static final int SEARCH_REQUEST_CODE = 501;
    private static final String KEY_HAS_NEW_INTENT = "key_has_new_intent";

    private boolean mHasNewIntent = true;
    private boolean mHasInitialFocus = false;
    private boolean mIsInitialFragmentTransaction = true;

    private String mTopLevelHeaderKey;
    private boolean mIsSinglePane;

    private ToolbarController mGlobalToolbar;
    private ToolbarController mMiniToolbar;

    private CarUxRestrictionsHelper mUxRestrictionsHelper;
    private ViewGroup mFragmentContainer;
    private View mRestrictedMessage;
    // Default to minimum restriction.
    private CarUxRestrictions mCarUxRestrictions = new CarUxRestrictions.Builder(
            /* reqOpt= */ true,
            CarUxRestrictions.UX_RESTRICTIONS_BASELINE,
            /* timestamp= */ 0
    ).build();

    private ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener;
    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener =
            (oldFocus, newFocus) -> {
                if (oldFocus instanceof SettingsFocusParkingView) {
                    // Focus is manually shifted away from the SettingsFocusParkingView.
                    // Therefore, the focus should no longer shift upon global layout.
                    removeGlobalLayoutListener();
                }
                if (newFocus instanceof SettingsFocusParkingView && mGlobalLayoutListener == null) {
                    // Attempting to shift focus to the SettingsFocusParkingView without a layout
                    // listener is not allowed, since it can cause undermined focus behavior
                    // in these rare edge cases.
                    requestTopLevelMenuFocus();
                }

                // This will maintain focus in the content pane if a view goes from
                // focusable -> unfocusable.
                if (oldFocus == null && mHasInitialFocus) {
                    requestContentPaneFocus();
                } else {
                    mHasInitialFocus = true;
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));
        if (savedInstanceState != null) {
            mHasNewIntent = savedInstanceState.getBoolean(KEY_HAS_NEW_INTENT, mHasNewIntent);
        }
        populateMetaData();
        setContentView(R.layout.car_setting_activity);
        mFragmentContainer = findViewById(R.id.fragment_container);

        // We do this so that the insets are not automatically sent to the fragments.
        // The fragments have their own insets handled by the installBaseLayoutAround() method.
        CarUi.replaceInsetsChangedListenerWith(this, this);

        setUpToolbars();
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        mRestrictedMessage = findViewById(R.id.restricted_message);

        if (mHasNewIntent) {
            launchIfDifferent(getInitialFragment());
            mHasNewIntent = false;
        } else if (!mIsSinglePane) {
            updateMiniToolbarState();
        }
        mUxRestrictionsHelper = new CarUxRestrictionsHelper(/* context= */ this, /* listener= */
                this);

        if (shouldFocusContentOnLaunch()) {
            requestContentPaneFocus();
            mHasInitialFocus = true;
        } else {
            requestTopLevelMenuFocus();
        }
        setUpFocusChangeListener(true);
        hideFocusParkingViewIfNeeded();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_HAS_NEW_INTENT, mHasNewIntent);
    }

    @Override
    public void onDestroy() {
        setUpFocusChangeListener(false);
        removeGlobalLayoutListener();
        mUxRestrictionsHelper.destroy();
        mUxRestrictionsHelper = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        hideKeyboard();
        // If the backstack is empty, finish the activity.
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            finish();
        }
    }

    @Override
    public Intent getIntent() {
        Intent superIntent = super.getIntent();
        if (mTopLevelHeaderKey != null) {
            superIntent.putExtra(META_DATA_KEY_HEADER_KEY, mTopLevelHeaderKey);
        }
        superIntent.putExtra(META_DATA_KEY_SINGLE_PANE, mIsSinglePane);
        return superIntent;
    }

    @Override
    public void launchFragment(Fragment fragment) {
        if (fragment instanceof DialogFragment) {
            throw new IllegalArgumentException(
                    "cannot launch dialogs with launchFragment() - use showDialog() instead");
        }

        if (mIsSinglePane) {
            Intent intent = SubSettingsActivity.newInstance(/* context= */ this, fragment);
            startActivity(intent);
        } else {
            launchFragmentInternal(fragment);
        }
    }

    protected void launchFragmentInternal(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        Themes.getAttrResourceId(/* context= */ this,
                                android.R.attr.fragmentOpenEnterAnimation),
                        Themes.getAttrResourceId(/* context= */ this,
                                android.R.attr.fragmentOpenExitAnimation),
                        Themes.getAttrResourceId(/* context= */ this,
                                android.R.attr.fragmentCloseEnterAnimation),
                        Themes.getAttrResourceId(/* context= */ this,
                                android.R.attr.fragmentCloseExitAnimation))
                .replace(R.id.fragment_container, fragment,
                        Integer.toString(getSupportFragmentManager().getBackStackEntryCount()))
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void goBack() {
        onBackPressed();
    }

    @Override
    public void showBlockingMessage() {
        Toast.makeText(this, R.string.restricted_while_driving, Toast.LENGTH_SHORT).show();
    }

    @Override
    public ToolbarController getToolbar() {
        if (mIsSinglePane) {
            return mGlobalToolbar;
        }
        return mMiniToolbar;
    }

    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        mCarUxRestrictions = restrictionInfo;

        // Update restrictions for current fragment.
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof OnUxRestrictionsChangedListener) {
            ((OnUxRestrictionsChangedListener) currentFragment)
                    .onUxRestrictionsChanged(restrictionInfo);
        }
        updateBlockingView(currentFragment);

        if (!mIsSinglePane) {
            // Update restrictions for top level menu (if present).
            Fragment topLevelMenu =
                    getSupportFragmentManager().findFragmentById(R.id.top_level_menu);
            if (topLevelMenu instanceof CarUxRestrictionsManager.OnUxRestrictionsChangedListener) {
                ((CarUxRestrictionsManager.OnUxRestrictionsChangedListener) topLevelMenu)
                        .onUxRestrictionsChanged(restrictionInfo);
            }
        }
    }

    @Override
    public CarUxRestrictions getCarUxRestrictions() {
        return mCarUxRestrictions;
    }

    @Override
    public void onBackStackChanged() {
        onUxRestrictionsChanged(getCarUxRestrictions());
        if (!mIsSinglePane) {
            if (mHasInitialFocus && shouldFocusContentOnBackstackChange()) {
                requestContentPaneFocus();
            }
            updateMiniToolbarState();
        }
    }

    @Override
    public void onCarUiInsetsChanged(Insets insets) {
        // intentional no-op - insets are handled by the listeners created during toolbar setup
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        if (pref.getFragment() != null) {
            Fragment fragment = Fragment.instantiate(/* context= */ this, pref.getFragment(),
                    pref.getExtras());
            launchFragment(fragment);
            return true;
        }
        return false;
    }

    /**
     * Gets the fragment to show onCreate. If null, the activity will not perform an initial
     * fragment transaction.
     */
    @Nullable
    protected abstract Fragment getInitialFragment();

    protected Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    }

    /**
     * Returns whether the content pane should get focus initially when in dual-pane configuration.
     */
    protected boolean shouldFocusContentOnLaunch() {
        return true;
    }

    private void launchIfDifferent(Fragment newFragment) {
        Fragment currentFragment = getCurrentFragment();
        if ((newFragment != null) && differentFragment(newFragment, currentFragment)) {
            LOG.d("launchIfDifferent: " + newFragment + " replacing " + currentFragment);
            launchFragmentInternal(newFragment);
        }
    }

    /**
     * Returns {code true} if newFragment is different from current fragment.
     */
    private boolean differentFragment(Fragment newFragment, Fragment currentFragment) {
        return (currentFragment == null)
                || (!currentFragment.getClass().equals(newFragment.getClass()));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
    }

    private void updateBlockingView(@Nullable Fragment currentFragment) {
        if (mRestrictedMessage == null) {
            return;
        }
        if (currentFragment instanceof BaseFragment
                && !((BaseFragment) currentFragment).canBeShown(mCarUxRestrictions)) {
            mRestrictedMessage.setVisibility(View.VISIBLE);
            mFragmentContainer.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            mFragmentContainer.clearFocus();
            hideKeyboard();
        } else {
            mRestrictedMessage.setVisibility(View.GONE);
            mFragmentContainer.setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
        }
    }

    private void populateMetaData() {
        try {
            ActivityInfo ai = getPackageManager().getActivityInfo(getComponentName(),
                    PackageManager.GET_META_DATA);
            if (ai == null || ai.metaData == null) {
                mIsSinglePane = getResources().getBoolean(R.bool.config_global_force_single_pane);
                return;
            }
            mTopLevelHeaderKey = ai.metaData.getString(META_DATA_KEY_HEADER_KEY);
            mIsSinglePane = ai.metaData.getBoolean(META_DATA_KEY_SINGLE_PANE,
                    getResources().getBoolean(R.bool.config_global_force_single_pane));
        } catch (PackageManager.NameNotFoundException e) {
            LOG.w("Unable to find package", e);
        }
    }

    private void setUpToolbars() {
        View globalToolbarWrappedView = mIsSinglePane ? findViewById(
                R.id.fragment_container_wrapper) : findViewById(R.id.top_level_menu_container);
        mGlobalToolbar = CarUi.installBaseLayoutAround(
                globalToolbarWrappedView,
                insets -> globalToolbarWrappedView.setPadding(
                        insets.getLeft(), insets.getTop(), insets.getRight(),
                        insets.getBottom()), /* hasToolbar= */ true);
        if (mIsSinglePane) {
            mGlobalToolbar.setNavButtonMode(NavButtonMode.BACK);
            findViewById(R.id.top_level_menu_container).setVisibility(View.GONE);
            findViewById(R.id.top_level_divider).setVisibility(View.GONE);
            return;
        }
        mMiniToolbar = CarUi.installBaseLayoutAround(
                findViewById(R.id.fragment_container_wrapper),
                insets -> findViewById(R.id.fragment_container_wrapper).setPadding(
                        insets.getLeft(), insets.getTop(), insets.getRight(),
                        insets.getBottom()), /* hasToolbar= */ true);

        MenuItem searchButton = new MenuItem.Builder(this)
                .setToSearch()
                .setOnClickListener(i -> onSearchButtonClicked())
                .setUxRestrictions(CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD)
                .setId(R.id.toolbar_menu_item_0)
                .build();
        List<MenuItem> items = Collections.singletonList(searchButton);

        mGlobalToolbar.setTitle(R.string.settings_label);
        mGlobalToolbar.setNavButtonMode(NavButtonMode.DISABLED);
        mGlobalToolbar.setLogo(R.drawable.ic_launcher_settings);
        mGlobalToolbar.setMenuItems(items);
    }

    private void updateMiniToolbarState() {
        if (mMiniToolbar == null) {
            return;
        }
        if (getSupportFragmentManager().getBackStackEntryCount() > 1 || !isTaskRoot()) {
            mMiniToolbar.setNavButtonMode(NavButtonMode.BACK);
        } else {
            mMiniToolbar.setNavButtonMode(NavButtonMode.DISABLED);
        }
    }

    private void hideFocusParkingViewIfNeeded() {
        if (mIsSinglePane) {
            findViewById(R.id.settings_focus_parking_view).setVisibility(GONE);
        }
    }

    private void setUpFocusChangeListener(boolean enable) {
        if (mIsSinglePane) {
            // The focus change listener is only needed with two panes.
            return;
        }
        ViewTreeObserver observer = findViewById(
                R.id.car_settings_activity_wrapper).getViewTreeObserver();
        if (enable) {
            observer.addOnGlobalFocusChangeListener(mFocusChangeListener);
        } else {
            observer.removeOnGlobalFocusChangeListener(mFocusChangeListener);
        }
    }

    private void requestTopLevelMenuFocus() {
        if (mIsSinglePane) {
            return;
        }
        Fragment topLevelMenu = getSupportFragmentManager().findFragmentById(R.id.top_level_menu);
        if (topLevelMenu == null) {
            return;
        }
        View fragmentView = topLevelMenu.getView();
        if (fragmentView == null) {
            return;
        }
        View focusArea = fragmentView.findViewById(R.id.settings_car_ui_focus_area);
        if (focusArea == null) {
            return;
        }
        removeGlobalLayoutListener();
        mGlobalLayoutListener = () -> {
            if (focusArea.isInTouchMode() || focusArea.hasFocus()) {
                return;
            }
            focusArea.performAccessibilityAction(ACTION_FOCUS, /* arguments= */ null);
            removeGlobalLayoutListener();
        };
        fragmentView.getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
    }

    private void requestContentPaneFocus() {
        if (mIsSinglePane) {
            return;
        }
        if (getCurrentFragment() == null) {
            return;
        }
        View fragmentView = getCurrentFragment().getView();
        if (fragmentView == null) {
            return;
        }
        removeGlobalLayoutListener();
        if (fragmentView.isInTouchMode()) {
            mHasInitialFocus = false;
            return;
        }
        View focusArea = fragmentView.findViewById(R.id.settings_car_ui_focus_area);

        if (focusArea == null) {
            focusArea = fragmentView.findViewById(R.id.settings_content_focus_area);
            if (focusArea == null) {
                return;
            }
        }
        removeGlobalLayoutListener();
        View finalFocusArea = focusArea; // required to be effectively final for inner class access
        mGlobalLayoutListener = () -> {
            if (finalFocusArea.isInTouchMode() || finalFocusArea.hasFocus()) {
                return;
            }
            boolean success = finalFocusArea.performAccessibilityAction(
                    ACTION_FOCUS, /* arguments= */ null);
            if (success) {
                removeGlobalLayoutListener();
            } else {
                findViewById(
                        R.id.settings_focus_parking_view).performAccessibilityAction(
                        ACTION_FOCUS, /* arguments= */ null);
            }
        };
        fragmentView.getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
    }

    private boolean shouldFocusContentOnBackstackChange() {
        // We don't want to reset mHasInitialFocus when initial fragment is added
        if (mIsInitialFragmentTransaction && getInitialFragment() != null) {
            mIsInitialFragmentTransaction = false;
            return false;
        }

        return true;
    }

    private void removeGlobalLayoutListener() {
        if (mGlobalLayoutListener == null) {
            return;
        }

        // Check content pane
        Fragment contentFragment = getCurrentFragment();
        if (contentFragment != null && contentFragment.getView() != null) {
            contentFragment.getView().getViewTreeObserver()
                    .removeOnGlobalLayoutListener(mGlobalLayoutListener);
        }

        // Check top level menu
        Fragment topLevelMenu = getSupportFragmentManager().findFragmentById(R.id.top_level_menu);
        if (topLevelMenu != null && topLevelMenu.getView() != null) {
            topLevelMenu.getView().getViewTreeObserver()
                    .removeOnGlobalLayoutListener(mGlobalLayoutListener);
        }

        mGlobalLayoutListener = null;
    }

    private void onSearchButtonClicked() {
        Intent intent = new Intent(Settings.ACTION_APP_SEARCH_SETTINGS)
                .setPackage(getSettingsIntelligencePkgName());
        if (intent.resolveActivity(getPackageManager()) == null) {
            return;
        }
        startActivityForResult(intent, SEARCH_REQUEST_CODE);
    }

    private String getSettingsIntelligencePkgName() {
        return getString(R.string.config_settingsintelligence_package_name);
    }
}
