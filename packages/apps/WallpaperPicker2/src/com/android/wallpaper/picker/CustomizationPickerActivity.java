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
package com.android.wallpaper.picker;

import static com.android.wallpaper.util.ActivityUtils.isSUWMode;
import static com.android.wallpaper.util.ActivityUtils.isWallpaperOnlyMode;
import static com.android.wallpaper.util.ActivityUtils.startActivityForResultSafely;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.android.wallpaper.R;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.CustomizationSectionController.CustomizationSectionNavigationController;
import com.android.wallpaper.model.PermissionRequester;
import com.android.wallpaper.model.WallpaperCategory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.model.WallpaperPreviewNavigator;
import com.android.wallpaper.module.DailyLoggingAlarmScheduler;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LargeScreenMultiPanesChecker;
import com.android.wallpaper.module.MultiPanesChecker;
import com.android.wallpaper.module.NetworkStatusNotifier;
import com.android.wallpaper.module.NetworkStatusNotifier.NetworkStatus;
import com.android.wallpaper.module.logging.UserEventLogger;
import com.android.wallpaper.picker.AppbarFragment.AppbarFragmentHost;
import com.android.wallpaper.picker.CategorySelectorFragment.CategorySelectorFragmentHost;
import com.android.wallpaper.picker.MyPhotosStarter.PermissionChangedListener;
import com.android.wallpaper.picker.individual.IndividualPickerFragment.IndividualPickerFragmentHost;
import com.android.wallpaper.util.ActivityUtils;
import com.android.wallpaper.util.DeepLinkUtils;
import com.android.wallpaper.util.DisplayUtils;
import com.android.wallpaper.util.LaunchUtils;
import com.android.wallpaper.widget.BottomActionBar;
import com.android.wallpaper.widget.BottomActionBar.BottomActionBarHost;

/**
 *  Main Activity allowing containing view sections for the user to switch between the different
 *  Fragments providing customization options.
 */
public class CustomizationPickerActivity extends FragmentActivity implements AppbarFragmentHost,
        WallpapersUiContainer, BottomActionBarHost, FragmentTransactionChecker,
        PermissionRequester, CategorySelectorFragmentHost, IndividualPickerFragmentHost,
        WallpaperPreviewNavigator {

    private static final String TAG = "CustomizationPickerActivity";
    private static final String EXTRA_DESTINATION = "destination";

    private WallpaperPickerDelegate mDelegate;
    private UserEventLogger mUserEventLogger;
    private NetworkStatusNotifier mNetworkStatusNotifier;
    private NetworkStatusNotifier.Listener mNetworkStatusListener;
    @NetworkStatus private int mNetworkStatus;
    private DisplayUtils mDisplayUtils;

    private BottomActionBar mBottomActionBar;
    private boolean mIsSafeToCommitFragmentTransaction;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Injector injector = InjectorProvider.getInjector();
        mDelegate = new WallpaperPickerDelegate(this, this, injector);
        mUserEventLogger = injector.getUserEventLogger();
        mNetworkStatusNotifier = injector.getNetworkStatusNotifier(this);
        mNetworkStatus = mNetworkStatusNotifier.getNetworkStatus();
        mDisplayUtils = injector.getDisplayUtils(this);

        enforcePortraitForHandheldAndFoldedDisplay();

        // Restore this Activity's state before restoring contained Fragments state.
        super.onCreate(savedInstanceState);
        // Trampoline for the two panes
        final MultiPanesChecker mMultiPanesChecker = new LargeScreenMultiPanesChecker();
        if (mMultiPanesChecker.isMultiPanesEnabled(this)) {
            Intent intent = getIntent();
            if (!ActivityUtils.isLaunchedFromSettingsTrampoline(intent)
                    && !ActivityUtils.isLaunchedFromSettingsRelated(intent)) {
                startActivityForResultSafely(this,
                        mMultiPanesChecker.getMultiPanesIntent(intent), /* requestCode= */ 0);
                finish();
            }
        }

        setContentView(R.layout.activity_customization_picker);
        mBottomActionBar = findViewById(R.id.bottom_actionbar);

        // See go/pdr-edge-to-edge-guide.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), isSUWMode(this));

        final boolean startFromLockScreen = getIntent() == null
                || !ActivityUtils.isLaunchedFromLauncher(getIntent());

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment == null) {
            // App launch specific logic: log the "app launch source" event.
            if (getIntent() != null) {
                mUserEventLogger.logAppLaunched(getIntent());
            }
            injector.getPreferences(this).incrementAppLaunched();
            DailyLoggingAlarmScheduler.setAlarm(getApplicationContext());

            // Switch to the target fragment.
            switchFragment(isWallpaperOnlyMode(getIntent())
                    ? WallpaperOnlyFragment.newInstance()
                    : CustomizationPickerFragment.newInstance(startFromLockScreen));

            // Cache the categories, but only if we're not restoring state (b/276767415).
            mDelegate.prefetchCategories();
        }

        if (savedInstanceState == null) {
            // We only want to start a new undo session if this activity is brand-new. A non-new
            // activity will have a non-null savedInstanceState.
            injector.getUndoInteractor(this, this).startSession();
        }

        final Intent intent = getIntent();
        final String navigationDestination = intent.getStringExtra(EXTRA_DESTINATION);
        // Consume the destination and commit the intent back so the OS doesn't revert to the same
        // destination when we change color or wallpaper (which causes the activity to be
        // recreated).
        intent.removeExtra(EXTRA_DESTINATION);
        setIntent(intent);

        final String deepLinkCollectionId = DeepLinkUtils.getCollectionId(intent);

        if (!TextUtils.isEmpty(navigationDestination)) {
            // Navigation deep link case
            fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (fragment instanceof CustomizationSectionNavigationController) {
                final CustomizationSectionNavigationController navController =
                        (CustomizationSectionNavigationController) fragment;
                navController.standaloneNavigateTo(navigationDestination);
            }
        } else if (!TextUtils.isEmpty(deepLinkCollectionId)) {
            // Wallpaper Collection deep link case
            switchFragmentWithBackStack(new CategorySelectorFragment());
            switchFragmentWithBackStack(InjectorProvider.getInjector().getIndividualPickerFragment(
                    this, deepLinkCollectionId));
            intent.setData(null);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mNetworkStatusListener == null) {
            mNetworkStatusListener = status -> {
                if (status == mNetworkStatus) {
                    return;
                }
                Log.i(TAG, "Network status changes, refresh wallpaper categories.");
                mNetworkStatus = status;
                mDelegate.initialize(/* forceCategoryRefresh= */ true);
            };
            // Upon registering a listener, the onNetworkChanged method is immediately called with
            // the initial network status.
            mNetworkStatusNotifier.registerListener(mNetworkStatusListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsSafeToCommitFragmentTransaction = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsSafeToCommitFragmentTransaction = false;
    }

    @Override
    protected void onStop() {
        if (mNetworkStatusListener != null) {
            mNetworkStatusNotifier.unregisterListener(mNetworkStatusListener);
            mNetworkStatusListener = null;
        }
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof BottomActionBarFragment
                && ((BottomActionBarFragment) fragment).onBackPressed()) {
            return;
        }

        if (getSupportFragmentManager().popBackStackImmediate()) {
            return;
        }
        if (moveTaskToBack(false)) {
            return;
        }
        super.onBackPressed();
    }

    private void switchFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitNow();
    }

    private void switchFragmentWithBackStack(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
        getSupportFragmentManager().executePendingTransactions();
    }


    @Override
    public void requestExternalStoragePermission(PermissionChangedListener listener) {
        mDelegate.requestExternalStoragePermission(listener);
    }

    @Override
    public void showViewOnlyPreview(WallpaperInfo wallpaperInfo, boolean isAssetIdPresent) {
        mDelegate.showViewOnlyPreview(wallpaperInfo, isAssetIdPresent);
    }

    @Override
    public void requestCustomPhotoPicker(PermissionChangedListener listener) {
        mDelegate.requestCustomPhotoPicker(listener);
    }

    @Override
    public void show(Category category) {
        if (!(category instanceof WallpaperCategory)) {
            mDelegate.show(category.getCollectionId());
            return;
        }
        switchFragmentWithBackStack(InjectorProvider.getInjector().getIndividualPickerFragment(
                this, category.getCollectionId()));
    }

    @Override
    public boolean isHostToolbarShown() {
        return false;
    }

    @Override
    public void setToolbarTitle(CharSequence title) {

    }

    @Override
    public void setToolbarMenu(int menuResId) {

    }

    @Override
    public void removeToolbarMenu() {

    }

    @Override
    public void moveToPreviousFragment() {
        getSupportFragmentManager().popBackStack();
    }

    @Override
    public void fetchCategories() {
        mDelegate.initialize(mDelegate.getCategoryProvider().shouldForceReload(this));
    }

    @Override
    public void cleanUp() {
        mDelegate.cleanUp();
    }

    @Override
    public void onWallpapersReady() {

    }

    @Nullable
    @Override
    public CategorySelectorFragment getCategorySelectorFragment() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragment_container);
        if (fragment instanceof CategorySelectorFragment) {
            return (CategorySelectorFragment) fragment;
        } else {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void doneFetchingCategories() {

    }

    @SuppressWarnings("MissingSuperCall") // TODO: Fix me
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        mDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mDelegate.handleActivityResult(requestCode, resultCode, data)) {
            if (isSUWMode(this)) {
                finishActivityForSUW();
            } else {
                // We don't finish in the revamped UI to let the user have a chance to reset the
                // change they made, should they want to. We do, however, remove all the fragments
                // from our back stack to reveal the root fragment, revealing the main screen of the
                // app.
                final FragmentManager fragmentManager = getSupportFragmentManager();
                while (fragmentManager.getBackStackEntryCount() > 0) {
                    fragmentManager.popBackStackImmediate();
                }
            }
        }
    }

    private void finishActivityWithResultOk() {
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        setResult(Activity.RESULT_OK);
        finish();

        // Go back to launcher home
        LaunchUtils.launchHome(this);
    }

    private void finishActivityForSUW() {
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        // Return RESULT_CANCELED to make the "Change wallpaper" tile in SUW not be disabled.
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public BottomActionBar getBottomActionBar() {
        return mBottomActionBar;
    }

    @Override
    public boolean isSafeToCommitFragmentTransaction() {
        return mIsSafeToCommitFragmentTransaction;
    }

    @Override
    public void onUpArrowPressed() {
        // TODO(b/189166781): Remove interface AppbarFragmentHost#onUpArrowPressed.
        onBackPressed();
    }

    @Override
    public boolean isUpArrowSupported() {
        return !isSUWMode(this);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        enforcePortraitForHandheldAndFoldedDisplay();
    }

    /**
     * If the display is a handheld display or a folded display from a foldable, we enforce the
     * activity to be portrait.
     *
     * This method should be called upon initialization of this activity, and whenever there is a
     * configuration change.
     */
    @SuppressLint("SourceLockedOrientationActivity")
    private void enforcePortraitForHandheldAndFoldedDisplay() {
        int wantedOrientation = mDisplayUtils.isLargeScreenOrUnfoldedDisplay(this)
                ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (getRequestedOrientation() != wantedOrientation) {
            setRequestedOrientation(wantedOrientation);
        }
    }
}
