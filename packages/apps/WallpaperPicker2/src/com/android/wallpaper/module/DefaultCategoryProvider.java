/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import static com.android.wallpaper.module.NetworkStatusNotifier.NETWORK_NOT_INITIALIZED;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.XmlRes;

import com.android.wallpaper.R;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.CategoryProvider;
import com.android.wallpaper.model.CategoryReceiver;
import com.android.wallpaper.model.DefaultWallpaperInfo;
import com.android.wallpaper.model.ImageCategory;
import com.android.wallpaper.model.LegacyPartnerWallpaperInfo;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.PartnerWallpaperInfo;
import com.android.wallpaper.model.SystemStaticWallpaperInfo;
import com.android.wallpaper.model.ThirdPartyAppCategory;
import com.android.wallpaper.model.ThirdPartyLiveWallpaperCategory;
import com.android.wallpaper.model.WallpaperCategory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.NetworkStatusNotifier.NetworkStatus;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of CategoryProvider.
 */
public class DefaultCategoryProvider implements CategoryProvider {

    private static final String TAG = "DefaultCategoryProvider";

    /**
     * Relative category priorities. Lower numbers correspond to higher priorities (i.e., should
     * appear higher in the categories list).
     */
    public static final int PRIORITY_MY_PHOTOS_WHEN_CREATIVE_WALLPAPERS_DISABLED = 1;
    private static final int PRIORITY_MY_PHOTOS_WHEN_CREATIVE_WALLPAPERS_ENABLED = 51;
    private static final int PRIORITY_SYSTEM = 100;
    private static final int PRIORITY_ON_DEVICE = 200;
    private static final int PRIORITY_LIVE = 300;
    private static final int PRIORITY_THIRD_PARTY = 400;
    public static final int CREATIVE_CATEGORY_PRIORITY = 1;

    protected static List<Category> sSystemCategories;

    protected final Context mAppContext;
    protected ArrayList<Category> mCategories;
    protected boolean mFetchedCategories;

    private NetworkStatusNotifier mNetworkStatusNotifier;
    // The network status of the last fetch from the server.
    @NetworkStatus
    private int mNetworkStatus;
    private Locale mLocale;

    public DefaultCategoryProvider(Context context) {
        mAppContext = context.getApplicationContext();
        mCategories = new ArrayList<>();
        mNetworkStatusNotifier = InjectorProvider.getInjector().getNetworkStatusNotifier(context);
        mNetworkStatus = NETWORK_NOT_INITIALIZED;
    }

    @Override
    public void fetchCategories(CategoryReceiver receiver, boolean forceRefresh) {
        if (!forceRefresh && mFetchedCategories) {
            for (Category category : mCategories) {
                receiver.onCategoryReceived(category);
            }
            receiver.doneFetchingCategories();
            return;
        } else if (forceRefresh) {
            mCategories.clear();
            mFetchedCategories = false;
        }

        mNetworkStatus = mNetworkStatusNotifier.getNetworkStatus();
        mLocale = getLocale();
        doFetch(receiver, forceRefresh);
    }

    @Override
    public int getSize() {
        return mFetchedCategories ? mCategories.size() : 0;
    }

    @Override
    public Category getCategory(int index) {
        if (!mFetchedCategories) {
            throw new IllegalStateException("Categories are not available");
        }
        return mCategories.get(index);
    }

    @Override
    public Category getCategory(String collectionId) {
        Category category;
        for (int i = 0; i < mCategories.size(); i++) {
            category = mCategories.get(i);
            if (category.getCollectionId().equals(collectionId)) {
                return category;
            }
        }
        return null;
    }

    @Override
    public boolean isCategoriesFetched() {
        return mFetchedCategories;
    }

    @Override
    public boolean resetIfNeeded() {
        if (mNetworkStatus != mNetworkStatusNotifier.getNetworkStatus()
                || mLocale != getLocale()) {
            mCategories.clear();
            mFetchedCategories = false;
            return true;
        }
        return false;
    }

    /**
     * This function returns the value of priority of MyPhotos depending on whether
     * the CreativeWallpaperFlag is enabled or not
     * @param context
     * @return the value of priority of MyPhotos
     */
    public static int getPriorityMyPhotos(Context context) {
        boolean isCreativeWallpaperFlagEnabled = InjectorProvider.getInjector().getFlags()
                .isAIWallpaperEnabled(context);
        if (isCreativeWallpaperFlagEnabled) {
            return PRIORITY_MY_PHOTOS_WHEN_CREATIVE_WALLPAPERS_ENABLED;
        } else
            return PRIORITY_MY_PHOTOS_WHEN_CREATIVE_WALLPAPERS_DISABLED;
    }

    /**
     * Returns an appropriate "my photos" custom photo category for the given device form factor.
     */
    private static Category getMyPhotosCategory(Context context) {
        return new ImageCategory(
                context.getString(R.string.my_photos_category_title),
                context.getString(R.string.image_wallpaper_collection_id),
                getPriorityMyPhotos(context),
                R.drawable.wallpaperpicker_emptystate /* overlayIconResId */);
    }

    @Override
    public boolean isFeaturedCollectionAvailable() {
        return false;
    }

    @Override
    public boolean isCreativeCategoryAvailable() {
        return false;
    }

    protected void doFetch(final CategoryReceiver receiver, boolean forceRefresh) {
        CategoryReceiver delegatingReceiver = new CategoryReceiver() {
            @Override
            public void onCategoryReceived(Category category) {
                receiver.onCategoryReceived(category);
                mCategories.add(category);
            }

            @Override
            public void doneFetchingCategories() {
                receiver.doneFetchingCategories();
                mFetchedCategories = true;
            }
        };

        new FetchCategoriesTask(delegatingReceiver, mAppContext).execute();
    }

    private Locale getLocale() {
        return mAppContext.getResources().getConfiguration().getLocales().get(0);
    }

    /**
     * AsyncTask subclass used for fetching all the categories and pushing them one at a time to
     * the receiver.
     */
    protected static class FetchCategoriesTask extends AsyncTask<Void, Category, Void> {
        protected final CategoryReceiver mReceiver;
        private PartnerProvider mPartnerProvider;
        protected final Context mAppContext;

        public FetchCategoriesTask(CategoryReceiver receiver, Context context) {
            mReceiver = receiver;
            mAppContext = context.getApplicationContext();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            mPartnerProvider = InjectorProvider.getInjector().getPartnerProvider(
                    mAppContext);

            // "My photos" wallpapers
            publishProgress(getMyPhotosCategory(mAppContext));

            publishDeviceCategories();

            // Legacy On-device wallpapers. Only show if on mobile.
            publishProgress(getOnDeviceCategory());

            // Live wallpapers -- if the device supports them.
            if (mAppContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)) {
                List<WallpaperInfo> liveWallpapers = LiveWallpaperInfo.getAll(
                        mAppContext, getExcludedLiveWallpaperPackageNames());
                if (liveWallpapers.size() > 0) {
                    publishProgress(
                            new ThirdPartyLiveWallpaperCategory(
                                    mAppContext.getString(R.string.live_wallpapers_category_title),
                                    mAppContext.getString(R.string.live_wallpaper_collection_id),
                                    liveWallpapers,
                                    PRIORITY_LIVE,
                                    getExcludedLiveWallpaperPackageNames()));
                }
            }

            // Third party apps.
            List<ThirdPartyAppCategory> thirdPartyApps = ThirdPartyAppCategory.getAll(
                    mAppContext, PRIORITY_THIRD_PARTY, getExcludedThirdPartyPackageNames());
            for (ThirdPartyAppCategory thirdPartyApp : thirdPartyApps) {
                publishProgress(thirdPartyApp);
            }

            return null;
        }

        /**
         * Publishes the device categories.
         */
        private void publishDeviceCategories() {
            if (sSystemCategories != null) {
                for (int i = 0; i < sSystemCategories.size(); i++) {
                    publishProgress(sSystemCategories.get(i));
                }
                return;
            }
            sSystemCategories = getSystemCategories();
        }

        public Set<String> getExcludedLiveWallpaperPackageNames() {
            Set<String> excluded = new HashSet<>();
            if (sSystemCategories != null) {
                excluded.addAll(sSystemCategories.stream()
                        .filter(c -> c instanceof WallpaperCategory)
                        .flatMap(c -> ((WallpaperCategory) c).getUnmodifiableWallpapers().stream()
                                .filter(wallpaperInfo -> wallpaperInfo instanceof LiveWallpaperInfo)
                                .map(wallpaperInfo ->
                                        ((LiveWallpaperInfo) wallpaperInfo).getWallpaperComponent()
                                                .getPackageName()))
                        .collect(Collectors.toSet()));
            }
            return excluded;
        }

        protected List<String> getExcludedThirdPartyPackageNames() {
            return Arrays.asList(
                    "com.android.launcher", // Legacy launcher
                    "com.android.wallpaper.livepicker"); // Live wallpaper picker
        }

        /**
         * Return a list of WallpaperInfos specific to this app. Overriding this method will
         * allow derivative projects to add custom wallpaper tiles to the
         * "On-device wallpapers" category.
         */
        protected List<WallpaperInfo> getPrivateDeviceWallpapers() {
            return null;
        }

        protected List<Category> getSystemCategories() {
            Resources partnerRes = mPartnerProvider.getResources();
            String packageName = mPartnerProvider.getPackageName();
            List<Category> categories = new ArrayList<>();
            if (partnerRes == null || packageName == null) {
                return categories;
            }

            @XmlRes int wallpapersResId = partnerRes.getIdentifier(PartnerProvider.WALLPAPER_RES_ID,
                    "xml", packageName);
            // Certain partner configurations don't have wallpapers provided, so need to check;
            // return early if they are missing.
            if (wallpapersResId == 0) {
                return categories;
            }

            try (XmlResourceParser parser = partnerRes.getXml(wallpapersResId)) {
                final int depth = parser.getDepth();
                int type;
                int priorityTracker = 0;
                while (((type = parser.next()) != XmlPullParser.END_TAG
                        || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                    if ((type == XmlPullParser.START_TAG)
                            && WallpaperCategory.TAG_NAME.equals(parser.getName())) {

                        WallpaperCategory.Builder categoryBuilder =
                                new WallpaperCategory.Builder(mPartnerProvider.getResources(),
                                        Xml.asAttributeSet(parser));
                        categoryBuilder.setPriorityIfEmpty(PRIORITY_SYSTEM + priorityTracker++);
                        final int categoryDepth = parser.getDepth();
                        while (((type = parser.next()) != XmlPullParser.END_TAG
                                || parser.getDepth() > categoryDepth)
                                && type != XmlPullParser.END_DOCUMENT) {
                            if (type == XmlPullParser.START_TAG) {
                                WallpaperInfo wallpaper = null;
                                if (SystemStaticWallpaperInfo.TAG_NAME.equals(parser.getName())) {
                                    wallpaper = SystemStaticWallpaperInfo
                                            .fromAttributeSet(mPartnerProvider.getPackageName(),
                                                    categoryBuilder.getId(),
                                                    Xml.asAttributeSet(parser));

                                } else if (LiveWallpaperInfo.TAG_NAME.equals(parser.getName())) {
                                    wallpaper = LiveWallpaperInfo.fromAttributeSet(mAppContext,
                                            categoryBuilder.getId(), Xml.asAttributeSet(parser));
                                }
                                if (wallpaper != null) {
                                    categoryBuilder.addWallpaper(wallpaper);
                                }
                            }
                        }
                        WallpaperCategory category = categoryBuilder.build();
                        if (!category.getUnmodifiableWallpapers().isEmpty()) {
                            categories.add(category);
                            publishProgress(category);
                        }
                    }
                }
            } catch (IOException | XmlPullParserException e) {
                Log.w(TAG, "Couldn't read system wallpapers definition", e);
                return Collections.emptyList();
            }
            return categories;
        }

        /**
         * Returns a category which incorporates both GEL and bundled wallpapers.
         */
        protected Category getOnDeviceCategory() {
            List<WallpaperInfo> onDeviceWallpapers = new ArrayList<>();

            if (!mPartnerProvider.shouldHideDefaultWallpaper()) {
                DefaultWallpaperInfo defaultWallpaperInfo = new DefaultWallpaperInfo();
                onDeviceWallpapers.add(defaultWallpaperInfo);
            }

            List<WallpaperInfo> partnerWallpaperInfos = PartnerWallpaperInfo.getAll(mAppContext);
            onDeviceWallpapers.addAll(partnerWallpaperInfos);

            List<WallpaperInfo> legacyPartnerWallpaperInfos = LegacyPartnerWallpaperInfo.getAll(
                    mAppContext);
            onDeviceWallpapers.addAll(legacyPartnerWallpaperInfos);

            List<WallpaperInfo> privateWallpapers = getPrivateDeviceWallpapers();
            if (privateWallpapers != null) {
                onDeviceWallpapers.addAll(privateWallpapers);
            }

            return onDeviceWallpapers.isEmpty() ? null : new WallpaperCategory(
                    mAppContext.getString(R.string.on_device_wallpapers_category_title),
                    mAppContext.getString(R.string.on_device_wallpaper_collection_id),
                    onDeviceWallpapers,
                    PRIORITY_ON_DEVICE);
        }

        @Override
        protected void onProgressUpdate(Category... values) {
            super.onProgressUpdate(values);

            for (int i = 0; i < values.length; i++) {
                Category category = values[i];
                if (category != null) {
                    mReceiver.onCategoryReceived(category);
                }
            }
        }

        @Override
        protected void onPostExecute(Void unused) {
            mReceiver.doneFetchingCategories();
        }
    }
}
