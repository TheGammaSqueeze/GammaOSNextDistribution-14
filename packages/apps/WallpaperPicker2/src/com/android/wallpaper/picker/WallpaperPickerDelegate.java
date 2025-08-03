/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.Manifest.permission;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.service.wallpaper.WallpaperService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.wallpaper.R;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.CategoryProvider;
import com.android.wallpaper.model.CategoryReceiver;
import com.android.wallpaper.model.ImageWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.PackageStatusNotifier;
import com.android.wallpaper.module.PackageStatusNotifier.PackageStatus;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.picker.WallpaperDisabledFragment.WallpaperSupportLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements all the logic for handling a WallpaperPicker container Activity.
 * @see CustomizationPickerActivity for usage details.
 */
public class WallpaperPickerDelegate implements MyPhotosStarter {

    private static final String TAG = "WallpaperPickerDelegate";
    private final FragmentActivity mActivity;
    private final WallpapersUiContainer mContainer;
    public static boolean DISABLE_MY_PHOTOS_BLOCK_PREVIEW = false;
    public static final int SHOW_CATEGORY_REQUEST_CODE = 0;
    public static final int PREVIEW_WALLPAPER_REQUEST_CODE = 1;
    public static final int VIEW_ONLY_PREVIEW_WALLPAPER_REQUEST_CODE = 2;
    public static final int READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 3;
    public static final int PREVIEW_LIVE_WALLPAPER_REQUEST_CODE = 4;
    public static final String IS_LIVE_WALLPAPER = "isLiveWallpaper";
    private final MyPhotosIntentProvider mMyPhotosIntentProvider;
    private WallpaperPreferences mPreferences;
    private PackageStatusNotifier mPackageStatusNotifier;

    private List<PermissionChangedListener> mPermissionChangedListeners;
    private PackageStatusNotifier.Listener mLiveWallpaperStatusListener;
    private PackageStatusNotifier.Listener mThirdPartyStatusListener;
    private PackageStatusNotifier.Listener mDownloadableWallpaperStatusListener;
    private String mDownloadableIntentAction;
    private CategoryProvider mCategoryProvider;
    private WallpaperPersister mWallpaperPersister;
    private static final String READ_IMAGE_PERMISSION = permission.READ_MEDIA_IMAGES;

    public WallpaperPickerDelegate(WallpapersUiContainer container, FragmentActivity activity,
            Injector injector) {
        mContainer = container;
        mActivity = activity;

        mCategoryProvider = injector.getCategoryProvider(activity);
        mPreferences = injector.getPreferences(activity);

        mPackageStatusNotifier = injector.getPackageStatusNotifier(activity);
        mWallpaperPersister = injector.getWallpaperPersister(activity);

        mPermissionChangedListeners = new ArrayList<>();
        mDownloadableIntentAction = injector.getDownloadableIntentAction();
        mMyPhotosIntentProvider = injector.getMyPhotosIntentProvider();
    }

    public void initialize(boolean forceCategoryRefresh) {
        populateCategories(forceCategoryRefresh);
        mLiveWallpaperStatusListener = this::updateLiveWallpapersCategories;
        mThirdPartyStatusListener = this::updateThirdPartyCategories;
        mPackageStatusNotifier.addListener(
                mLiveWallpaperStatusListener,
                WallpaperService.SERVICE_INTERFACE);
        mPackageStatusNotifier.addListener(mThirdPartyStatusListener, Intent.ACTION_SET_WALLPAPER);
        if (mDownloadableIntentAction != null) {
            mDownloadableWallpaperStatusListener = (packageName, status) -> {
                if (status != PackageStatusNotifier.PackageStatus.REMOVED) {
                    populateCategories(/* forceRefresh= */ true);
                }
            };
            mPackageStatusNotifier.addListener(
                    mDownloadableWallpaperStatusListener, mDownloadableIntentAction);
        }
    }

    @Override
    public void requestCustomPhotoPicker(PermissionChangedListener listener) {
        //TODO (b/282073506): Figure out a better way to have better photos experience
        if (DISABLE_MY_PHOTOS_BLOCK_PREVIEW) {
            if (!isReadExternalStoragePermissionGranted()) {
                PermissionChangedListener wrappedListener = new PermissionChangedListener() {
                    @Override
                    public void onPermissionsGranted() {
                        listener.onPermissionsGranted();
                        showCustomPhotoPicker();
                    }

                    @Override
                    public void onPermissionsDenied(boolean dontAskAgain) {
                        listener.onPermissionsDenied(dontAskAgain);
                    }
                };
                requestExternalStoragePermission(wrappedListener);

                return;
            }
        }

        showCustomPhotoPicker();
    }

    /**
     * Requests to show the Android custom photo picker for the sake of picking a
     * photo to set as the device's wallpaper.
     */
    public void requestExternalStoragePermission(PermissionChangedListener listener) {
        mPermissionChangedListeners.add(listener);
        mActivity.requestPermissions(
                new String[]{READ_IMAGE_PERMISSION},
                READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE);
    }

    /**
     * Returns whether READ_MEDIA_IMAGES has been granted for the application.
     */
    public boolean isReadExternalStoragePermissionGranted() {
        return mActivity.getPackageManager().checkPermission(
                permission.READ_MEDIA_IMAGES,
                mActivity.getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    private void showCustomPhotoPicker() {
        try {
            Intent intent = mMyPhotosIntentProvider.getMyPhotosIntent(mActivity);
            mActivity.startActivityForResult(intent, SHOW_CATEGORY_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Intent fallback = mMyPhotosIntentProvider.getFallbackIntent(mActivity);
            if (fallback != null) {
                Log.i(TAG, "Couldn't launch photo picker with main intent, trying with fallback");
                mActivity.startActivityForResult(fallback, SHOW_CATEGORY_REQUEST_CODE);
            } else {
                Log.e(TAG,
                        "Couldn't launch photo picker with main intent and no fallback is "
                                + "available");
                throw e;
            }
        }
    }

    private void updateThirdPartyCategories(String packageName, @PackageStatus int status) {
        if (status == PackageStatus.ADDED) {
            mCategoryProvider.fetchCategories(new CategoryReceiver() {
                @Override
                public void onCategoryReceived(Category category) {
                    if (category.supportsThirdParty() && category.containsThirdParty(packageName)) {
                        addCategory(category, false);
                    }
                }

                @Override
                public void doneFetchingCategories() {
                    // Do nothing here.
                }
            }, true);
        } else if (status == PackageStatus.REMOVED) {
            Category oldCategory = findThirdPartyCategory(packageName);
            if (oldCategory != null) {
                mCategoryProvider.fetchCategories(new CategoryReceiver() {
                    @Override
                    public void onCategoryReceived(Category category) {
                        // Do nothing here
                    }

                    @Override
                    public void doneFetchingCategories() {
                        removeCategory(oldCategory);
                    }
                }, true);
            }
        } else {
            // CHANGED package, let's reload all categories as we could have more or fewer now
            populateCategories(/* forceRefresh= */ true);
        }
    }

    private Category findThirdPartyCategory(String packageName) {
        int size = mCategoryProvider.getSize();
        for (int i = 0; i < size; i++) {
            Category category = mCategoryProvider.getCategory(i);
            if (category.supportsThirdParty() && category.containsThirdParty(packageName)) {
                return category;
            }
        }
        return null;
    }

    private void updateLiveWallpapersCategories(String packageName,
            @PackageStatus int status) {
        String liveWallpaperCollectionId = mActivity.getString(
                R.string.live_wallpaper_collection_id);
        Category oldLiveWallpapersCategory = mCategoryProvider.getCategory(
                liveWallpaperCollectionId);
        if (status == PackageStatus.REMOVED
                && (oldLiveWallpapersCategory == null
                || !oldLiveWallpapersCategory.containsThirdParty(packageName))) {
            // If we're removing a wallpaper and the live category didn't contain it already,
            // there's nothing to do.
            return;
        }
        mCategoryProvider.fetchCategories(new CategoryReceiver() {
            @Override
            public void onCategoryReceived(Category category) {
                // Do nothing here
            }

            @Override
            public void doneFetchingCategories() {
                Category liveWallpapersCategory =
                        mCategoryProvider.getCategory(liveWallpaperCollectionId);
                if (liveWallpapersCategory == null) {
                    // There are no more 3rd party live wallpapers, so the Category is gone.
                    removeCategory(oldLiveWallpapersCategory);
                } else {
                    if (oldLiveWallpapersCategory != null) {
                        updateCategory(liveWallpapersCategory);
                    } else {
                        addCategory(liveWallpapersCategory, false);
                    }
                }
            }
        }, true);
    }

    /**
     * Fetch the wallpaper categories but don't call any callbacks on the result, just so that
     * they're cached when loading later.
     */
    public void prefetchCategories() {
        boolean forceRefresh = mCategoryProvider.resetIfNeeded();
        mCategoryProvider.fetchCategories(new CategoryReceiver() {
            @Override
            public void onCategoryReceived(Category category) {
                // Do nothing
            }

            @Override
            public void doneFetchingCategories() {
                // Do nothing
            }
        }, forceRefresh);
    }

    /**
     * Populates the categories appropriately.
     *
     * @param forceRefresh        Whether to force a refresh of categories from the
     *                            CategoryProvider. True if
     *                            on first launch.
     */
    public void populateCategories(boolean forceRefresh) {

        final CategorySelectorFragment categorySelectorFragment = getCategorySelectorFragment();

        if (forceRefresh && categorySelectorFragment != null) {
            categorySelectorFragment.clearCategories();
        }

        mCategoryProvider.fetchCategories(new CategoryReceiver() {
            @Override
            public void onCategoryReceived(Category category) {
                addCategory(category, true);
            }

            @Override
            public void doneFetchingCategories() {
                notifyDoneFetchingCategories();
            }
        }, forceRefresh);
    }

    private void notifyDoneFetchingCategories() {
        CategorySelectorFragment categorySelectorFragment = getCategorySelectorFragment();
        if (categorySelectorFragment != null) {
            categorySelectorFragment.doneFetchingCategories();
        }
    }

    public void addCategory(Category category, boolean fetchingAll) {
        CategorySelectorFragment categorySelectorFragment = getCategorySelectorFragment();
        if (categorySelectorFragment != null) {
            categorySelectorFragment.addCategory(category, fetchingAll);
        }
    }

    public void removeCategory(Category category) {
        CategorySelectorFragment categorySelectorFragment = getCategorySelectorFragment();
        if (categorySelectorFragment != null) {
            categorySelectorFragment.removeCategory(category);
        }
    }

    public void updateCategory(Category category) {
        CategorySelectorFragment categorySelectorFragment = getCategorySelectorFragment();
        if (categorySelectorFragment != null) {
            categorySelectorFragment.updateCategory(category);
        }
    }

    @Nullable
    private CategorySelectorFragment getCategorySelectorFragment() {
        return mContainer.getCategorySelectorFragment();
    }

    /**
     * Shows the view-only preview activity for the given wallpaper.
     */
    public void showViewOnlyPreview(WallpaperInfo wallpaperInfo, boolean isAssetIdPresent) {
        wallpaperInfo.showPreview(
                mActivity, InjectorProvider.getInjector().getViewOnlyPreviewActivityIntentFactory(),
                VIEW_ONLY_PREVIEW_WALLPAPER_REQUEST_CODE, isAssetIdPresent);
    }

    /**
     * Shows the picker activity for the given category.
     */
    public void show(String collectionId) {
        Category category = findCategoryForCollectionId(collectionId);
        if (category == null) {
            return;
        }
        category.show(mActivity, SHOW_CATEGORY_REQUEST_CODE);
    }

    @Nullable
    public Category findCategoryForCollectionId(String collectionId) {
        return mCategoryProvider.getCategory(collectionId);
    }

    @WallpaperSupportLevel
    public int getWallpaperSupportLevel() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mActivity);

        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            if (wallpaperManager.isWallpaperSupported()) {
                return wallpaperManager.isSetWallpaperAllowed()
                        ? WallpaperDisabledFragment.SUPPORTED_CAN_SET
                        : WallpaperDisabledFragment.NOT_SUPPORTED_BLOCKED_BY_ADMIN;
            }
            return WallpaperDisabledFragment.NOT_SUPPORTED_BY_DEVICE;
        } else if (VERSION.SDK_INT >= VERSION_CODES.M) {
            return wallpaperManager.isWallpaperSupported()
                    ? WallpaperDisabledFragment.SUPPORTED_CAN_SET
                    : WallpaperDisabledFragment.NOT_SUPPORTED_BY_DEVICE;
        } else {
            boolean isSupported = WallpaperManager.getInstance(mActivity.getApplicationContext())
                    .getDrawable() != null;
            wallpaperManager.forgetLoadedWallpaper();
            return isSupported ? WallpaperDisabledFragment.SUPPORTED_CAN_SET
                    : WallpaperDisabledFragment.NOT_SUPPORTED_BY_DEVICE;
        }
    }

    public WallpaperPreferences getPreferences() {
        return mPreferences;
    }

    public List<PermissionChangedListener> getPermissionChangedListeners() {
        return mPermissionChangedListeners;
    }

    public CategoryProvider getCategoryProvider() {
        return mCategoryProvider;
    }

    /**
     * Call when the owner activity is destroyed to clean up listeners.
     */
    public void cleanUp() {
        if (mPackageStatusNotifier != null) {
            mPackageStatusNotifier.removeListener(mLiveWallpaperStatusListener);
            mPackageStatusNotifier.removeListener(mThirdPartyStatusListener);
            mPackageStatusNotifier.removeListener(mDownloadableWallpaperStatusListener);
        }
    }

    /**
     * Call from the Activity's onRequestPermissionsResult callback to handle permission request
     * relevant to wallpapers (ie, READ_MEDIA_IMAGES)
     * @see androidx.fragment.app.FragmentActivity#onRequestPermissionsResult(int, String[], int[])
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == WallpaperPickerDelegate.READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE
                && permissions.length > 0
                && permissions[0].equals(READ_IMAGE_PERMISSION)
                && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                for (PermissionChangedListener listener : getPermissionChangedListeners()) {
                    listener.onPermissionsGranted();
                }
            } else if (!mActivity.shouldShowRequestPermissionRationale(READ_IMAGE_PERMISSION)) {
                for (PermissionChangedListener listener : getPermissionChangedListeners()) {
                    listener.onPermissionsDenied(true /* dontAskAgain */);
                }
            } else {
                for (PermissionChangedListener listener :getPermissionChangedListeners()) {
                    listener.onPermissionsDenied(false /* dontAskAgain */);
                }
            }
        }
       getPermissionChangedListeners().clear();
    }

    /**
     * To be called from an Activity's onActivityResult method.
     * Checks the result for ones that are handled by this delegate
     * @return true if the intent was handled and calling Activity needs to finish with result
     * OK, false otherwise.
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return false;
        }

        switch (requestCode) {
            case SHOW_CATEGORY_REQUEST_CODE:
                Uri imageUri = (data == null) ? null : data.getData();
                if (imageUri == null) {
                    // User finished viewing a category without any data, which implies that the
                    // user previewed and selected a wallpaper in-app, so finish this activity.
                    return true;
                }

                // User selected an image from the system picker, so launch the preview for that
                // image.
                ImageWallpaperInfo imageWallpaper = new ImageWallpaperInfo(imageUri);

                mWallpaperPersister.setWallpaperInfoInPreview(imageWallpaper);
                imageWallpaper.showPreview(mActivity,
                        InjectorProvider.getInjector().getPreviewActivityIntentFactory(),
                        PREVIEW_WALLPAPER_REQUEST_CODE, true);
                return false;
            case PREVIEW_LIVE_WALLPAPER_REQUEST_CODE:
                populateCategories(/* forceRefresh= */ true);
                return true;
            case VIEW_ONLY_PREVIEW_WALLPAPER_REQUEST_CODE:
                return true;
            case PREVIEW_WALLPAPER_REQUEST_CODE:
                // User previewed and selected a wallpaper, so finish this activity.
                if (data != null && data.getBooleanExtra(IS_LIVE_WALLPAPER, false)) {
                    populateCategories(/* forceRefresh= */ true);
                }
                return true;
            default:
                return false;
        }
    }
}
