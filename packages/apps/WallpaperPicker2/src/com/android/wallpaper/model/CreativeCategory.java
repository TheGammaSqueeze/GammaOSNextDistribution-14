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
package com.android.wallpaper.model;


import static com.android.wallpaper.model.WallpaperInfoContract.WALLPAPER_EFFECTS_CLEAR_URI;
import static com.android.wallpaper.model.WallpaperInfoContract.WALLPAPER_EFFECTS_CURRENT_ID;
import static com.android.wallpaper.model.WallpaperInfoContract.WALLPAPER_EFFECTS_SECTION_SUBTITLE;
import static com.android.wallpaper.model.WallpaperInfoContract.WALLPAPER_EFFECTS_SECTION_TITLE;

import android.annotation.Nullable;
import android.content.ContentProviderClient;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.android.wallpaper.asset.CreativeWallpaperThumbAsset;

import java.util.ArrayList;
import java.util.List;

/** The {@link WallpaperCategory} implements category for user created wallpapers. */
public class CreativeCategory extends WallpaperCategory {

    private static final String TAG = "CreativeCategory";
    public static final String KEY_WALLPAPER_CREATIVE_CATEGORY =
            "android.service.wallpaper.category";

    public static final String KEY_WALLPAPER_CREATIVE_WALLPAPERS =
            "android.service.wallpaper.categorizedwallpapers";
    public static final String KEY_WALLPAPER_CREATIVE_WALLPAPER_EFFECTS =
            "android.service.wallpaper.effects";

    public final android.app.WallpaperInfo mWallpaperInfo;

    public static final String KEY_WALLPAPER_SAVE_CREATIVE_CATEGORY_WALLPAPER =
            "android.service.wallpaper.savewallpaper";

    /** Return true for CreativeCategories since we support user generated wallpapers here. */
    @Override
    public boolean supportsUserCreatedWallpapers() {
        return true;
    }

    /**
     * This function aims at saving a creative category wallpaper by calling the relevant URI
     * of the creative category wallpaper provider.
     *
     * @param context Context of calling activity
     * @param wallpaper instance of the WallpaperInfo that we want to save
     * @param saveWallpaperUri the URI to be used for saving the wallpaper
     * @param destination depicts the destination of the wallpaper being saved
     */
    @Nullable
    public static CreativeWallpaperInfo saveCreativeCategoryWallpaper(Context context,
            LiveWallpaperInfo wallpaper, Uri saveWallpaperUri, int destination) {

        // Adding destination field which depicts whether it is home-screen or lock-screen.
        Uri updatedSaveWallpaperUri = saveWallpaperUri.buildUpon()
                .appendQueryParameter("destination", String.valueOf(destination))
                .build();
        try (ContentProviderClient client =
                     context.getContentResolver().acquireContentProviderClient(
                             updatedSaveWallpaperUri.getAuthority())) {
            if (client == null) {
                Log.w(TAG, "Couldn't resolve content provider for " + updatedSaveWallpaperUri);
                return null;
            }
            try (Cursor cursor = client.query(updatedSaveWallpaperUri, /* projection= */ null,
                    /* selection= */ null, /* selectionArgs= */ null, /* sortOrder= */ null)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    return null;
                }
                return CreativeWallpaperInfo.buildFromCursor(wallpaper.getWallpaperComponent(),
                        cursor);
            } catch (Throwable e) {
                Log.e(TAG, "Couldn't read creative category.", e);
            }
        }
        return null;
    }

    public CreativeCategory(Context context, String title, String collectionId, Uri thumbUri,
            List<WallpaperInfo> wallpaperInfos, int priority,
            android.app.WallpaperInfo wallpaperInfo) {
        super(title,
                collectionId,
                wallpaperInfos.isEmpty() ? null : new CreativeWallpaperThumbAsset(context,
                        wallpaperInfos.get(0).getWallpaperComponent(), thumbUri),
                wallpaperInfos,
                priority);
        mWallpaperInfo = wallpaperInfo;
    }

    @Override
    public boolean supportsWallpaperSetUpdates() {
        return true;
    }

    @Override
    public void fetchWallpapers(Context context, WallpaperReceiver receiver, boolean forceReload) {
        if (!forceReload) {
            super.fetchWallpapers(context, receiver, forceReload);
            return;
        }
        List<WallpaperInfo> wallpapers = readCreativeWallpapers(
                context, getCollectionId(), mWallpaperInfo);
        synchronized (this) {
            getMutableWallpapers().clear();
            getMutableWallpapers().addAll(wallpapers);
        }
        if (receiver != null) {
            receiver.onWallpapersReceived(wallpapers);
        }
    }

    /**
     * Returns a list of [CreativeWallpaperInfo] objects by creating them using the relevant
     * info. obtained from creative-category APK on device.
     *
     * @param context context of the hosting activity
     * @param collectionId ID of the collection to which these wallpapers belong to
     * @param wallpaperInfo contains relevant metadata information about creative-category wallpaper
     * @return list of CreativeWallpaperInfo objects
     */
    public static List<WallpaperInfo> readCreativeWallpapers(Context context,
            String collectionId, android.app.WallpaperInfo wallpaperInfo) {
        List<WallpaperInfo> wallpapers = new ArrayList<>();
        Bundle metaData = wallpaperInfo.getServiceInfo().metaData;
        Uri wallpapersUri = Uri.parse((String) metaData.get(KEY_WALLPAPER_CREATIVE_WALLPAPERS));
        try (ContentProviderClient client =
                     context.getContentResolver().acquireContentProviderClient(
                             wallpapersUri.getAuthority())) {
            try (Cursor cursor = client.query(wallpapersUri, /* projection= */ null,
                    /* selection= */ null, /* selectionArgs= */ null, /* sortOrder= */ null)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    return wallpapers;
                }
                do {
                    String categoryId = cursor.getString(
                            cursor.getColumnIndex(WallpaperInfoContract.CATEGORY_ID));
                    if (!TextUtils.equals(categoryId, collectionId)) {
                        continue;
                    }
                    CreativeWallpaperInfo creativeWallpaperInfo =
                            CreativeWallpaperInfo.buildFromCursor(wallpaperInfo, cursor);
                    // If the meta data for wallpaper actions exists, only then can we query the
                    // action fields and action table.
                    if (metaData.get(KEY_WALLPAPER_CREATIVE_WALLPAPER_EFFECTS) != null) {
                        String effectsBottomSheetTitle = cursor.getString(
                                cursor.getColumnIndex(WALLPAPER_EFFECTS_SECTION_TITLE));
                        String effectsBottomSheetSubtitle = cursor.getString(
                                cursor.getColumnIndex(WALLPAPER_EFFECTS_SECTION_SUBTITLE));
                        String currentEffectId = cursor.getString(
                                cursor.getColumnIndex(WALLPAPER_EFFECTS_CURRENT_ID));
                        Uri clearActionUri = Uri.parse(cursor.getString(
                                cursor.getColumnIndex(WALLPAPER_EFFECTS_CLEAR_URI)));

                        creativeWallpaperInfo.setEffectsBottomSheetTitle(
                                effectsBottomSheetTitle);
                        creativeWallpaperInfo.setEffectsBottomSheetSubtitle(
                                effectsBottomSheetSubtitle);
                        creativeWallpaperInfo.setClearActionsUri(clearActionUri);
                        creativeWallpaperInfo.setCurrentlyAppliedEffectId(currentEffectId);

                        Uri effectsUri = Uri.parse((String) metaData.get(
                                KEY_WALLPAPER_CREATIVE_WALLPAPER_EFFECTS));
                        creativeWallpaperInfo.setEffectsUri(effectsUri);
                    }
                    wallpapers.add(creativeWallpaperInfo);
                } while (cursor.moveToNext());
            }
        } catch (Throwable e) {
            Log.e(TAG, "Exception reading creative wallpapers", e);
        }
        return wallpapers;
    }

    @Override
    public boolean supportsThirdParty() {
        return true;
    }

    @Override
    public boolean isSingleWallpaperCategory() {
        // if the category is empty then we go directly to the creation screen
        // if there is at least one wallpaper then we show the collection of wallpapers
        // NOTE: don't count the "add walpapper button" wallpaper. This is why <= 1 is being used
        // as opposed to <= 0
        return getMutableWallpapers() == null || getMutableWallpapers().size() <= 1;
    }
}
