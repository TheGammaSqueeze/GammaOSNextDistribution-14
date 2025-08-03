/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.wallpaper.model.CreativeCategory.KEY_WALLPAPER_SAVE_CREATIVE_CATEGORY_WALLPAPER;

import android.annotation.Nullable;
import android.app.WallpaperInfo;
import android.content.ClipData;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Log;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.CreativeWallpaperThumbAsset;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a creative live wallpaper component.
 */
public class CreativeWallpaperInfo extends LiveWallpaperInfo {

    public static final Creator<CreativeWallpaperInfo> CREATOR =
            new Creator<CreativeWallpaperInfo>() {
                @Override
                public CreativeWallpaperInfo createFromParcel(Parcel in) {
                    return new CreativeWallpaperInfo(in);
                }

                @Override
                public CreativeWallpaperInfo[] newArray(int size) {
                    return new CreativeWallpaperInfo[size];
                }
            };

    private Uri mConfigPreviewUri;
    private Uri mCleanPreviewUri;
    private Uri mDeleteUri;
    private Uri mThumbnailUri;
    private Uri mShareUri;
    private String mTitle;
    private String mAuthor;
    private String mDescription;
    private String mContentDescription;
    private boolean mIsCurrent;
    private String mGroupName;

    private static final String TAG = "CreativeWallpaperInfo";

    private ArrayList<WallpaperAction> mEffectsToggles = new ArrayList<>();
    private String mEffectsBottomSheetTitle;
    private String mEffectsBottomSheetSubtitle;
    private Uri mClearActionsUri;
    private Uri mEffectsUri = null;
    private String mCurrentlyAppliedEffectId = null;

    public CreativeWallpaperInfo(WallpaperInfo info, String title, @Nullable String author,
            @Nullable String description, String contentDescription, Uri configPreviewUri,
            Uri cleanPreviewUri, Uri deleteUri, Uri thumbnailUri, Uri shareUri, String groupName,
            boolean isCurrent) {
        this(info, /* visibleTitle= */ false, /* collectionId= */ null);
        mTitle = title;
        mAuthor = author;
        mDescription = description;
        mContentDescription = contentDescription;
        mConfigPreviewUri = configPreviewUri;
        mCleanPreviewUri = cleanPreviewUri;
        mDeleteUri = deleteUri;
        mThumbnailUri = thumbnailUri;
        mShareUri = shareUri;
        mIsCurrent = isCurrent;
        mGroupName = groupName;
    }

    public CreativeWallpaperInfo(WallpaperInfo info, boolean isCurrent) {
        this(info, false, null);
        mIsCurrent = isCurrent;
    }

    public CreativeWallpaperInfo(WallpaperInfo info, boolean visibleTitle,
            @Nullable String collectionId) {
        super(info, visibleTitle, collectionId);
    }

    protected CreativeWallpaperInfo(Parcel in) {
        super(in);
        mTitle = in.readString();
        mAuthor = in.readString();
        mDescription = in.readString();
        mContentDescription = in.readString();
        mConfigPreviewUri = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
        mCleanPreviewUri = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
        mDeleteUri = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
        mThumbnailUri = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
        mShareUri = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
        mIsCurrent = in.readBoolean();
        mGroupName = in.readString();
        mCurrentlyAppliedEffectId = in.readString();
        mEffectsUri = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
        mClearActionsUri = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
        mEffectsToggles = in.readArrayList(WallpaperAction.class.getClassLoader(),
                WallpaperAction.class);
        mEffectsBottomSheetTitle = in.readString();
        mEffectsBottomSheetSubtitle = in.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        super.writeToParcel(parcel, flags);
        parcel.writeString(mTitle);
        parcel.writeString(mAuthor);
        parcel.writeString(mDescription);
        parcel.writeString(mContentDescription);
        parcel.writeParcelable(mConfigPreviewUri, flags);
        parcel.writeParcelable(mCleanPreviewUri, flags);
        parcel.writeParcelable(mDeleteUri, flags);
        parcel.writeParcelable(mThumbnailUri, flags);
        parcel.writeParcelable(mShareUri, flags);
        parcel.writeBoolean(mIsCurrent);
        parcel.writeString(mGroupName);
        parcel.writeString(mCurrentlyAppliedEffectId);
        parcel.writeParcelable(mEffectsUri, flags);
        parcel.writeParcelable(mClearActionsUri, flags);
        parcel.writeList(mEffectsToggles);
        parcel.writeString(mEffectsBottomSheetTitle);
        parcel.writeString(mEffectsBottomSheetSubtitle);
    }

    /**
     * Creates a new {@link ActivityResultContract} used to request the settings Activity overlay
     * for this wallpaper.
     *
     * @param intent settings intent
     */
    public static ActivityResultContract<Void, Integer> getContract(Intent intent) {
        return new ActivityResultContract<Void, Integer>() {
            @NonNull
            @Override
            public Intent createIntent(@NonNull Context context, Void unused) {
                return intent;
            }

            @Override
            public Integer parseResult(int i, @Nullable Intent intent) {
                return i;
            }
        };
    }

    /**
     * Loads the current wallpaper's effects.
     *
     * @param context context of the current android component
     * @return an array list of WallpaperAction data objects
     * for the currently previewing wallpaper
     */
    @Nullable
    public ArrayList<WallpaperAction> getWallpaperEffects(Context context) {
        if (mEffectsUri == null) {
            return null;
        }
        mEffectsToggles.clear();
        // TODO (269350033): Move content provider query off the main thread.
        try (ContentProviderClient effectsClient =
                     context.getContentResolver().acquireContentProviderClient(
                             mEffectsUri.getAuthority())) {
            try (Cursor effectsCursor = effectsClient.query(mEffectsUri, /* projection= */ null,
                    /* selection= */ null, /* selectionArgs= */ null, /* sortOrder= */ null)) {
                if (effectsCursor == null) {
                    return null;
                }
                while (effectsCursor.moveToNext()) {
                    Uri effectsToggleUri = Uri.parse(
                            effectsCursor.getString(effectsCursor.getColumnIndex(
                                    WallpaperInfoContract.WALLPAPER_EFFECTS_TOGGLE_URI)));
                    String effectsButtonLabel = effectsCursor.getString(
                            effectsCursor.getColumnIndex(
                                    WallpaperInfoContract.WALLPAPER_EFFECTS_BUTTON_LABEL));
                    String effectsId = effectsCursor.getString(
                            effectsCursor.getColumnIndex(
                                    WallpaperInfoContract.WALLPAPER_EFFECTS_TOGGLE_ID));
                    mEffectsToggles.add(new WallpaperAction(effectsButtonLabel,
                            effectsToggleUri, effectsId, /* toggled= */ false));
                }
                return mEffectsToggles;
            }
        } catch (Exception e) {
            Log.e(TAG, "Read wallpaper effects with exception.", e);
        }
        return null;
    }

    @Override
    public String getTitle(Context context) {
        if (mVisibleTitle) {
            return mTitle;
        }
        return null;
    }

    @Override
    public List<String> getAttributions(Context context) {
        PackageManager packageManager = context.getPackageManager();
        CharSequence labelCharSeq = mInfo.loadLabel(packageManager);
        List<String> attributions = new ArrayList<>();
        attributions.add(labelCharSeq == null ? null : labelCharSeq.toString());
        attributions.add(mAuthor == null ? "" : mAuthor);
        attributions.add(mDescription == null ? "" : mDescription);

        return attributions;
    }

    @Override
    public String getContentDescription(Context context) {
        return mContentDescription;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        if (mThumbAsset == null) {
            mThumbAsset = new CreativeWallpaperThumbAsset(context, mInfo, mThumbnailUri);
        }
        return mThumbAsset;
    }

    @Override
    public String getGroupName(Context context) {
        return mGroupName;
    }

    /**
     * Calls the config URI to initialize the preview for this wallpaper.
     */
    public void initializeWallpaperPreview(Context context) {
        if (mConfigPreviewUri != null) {
            context.getContentResolver().update(mConfigPreviewUri, new ContentValues(), null);
        }
    }

    /**
     * Calls the clean URI to de-initialize the preview for this wallpaper.
     */
    public void cleanUpWallpaperPreview(Context context) {
        if (mCleanPreviewUri != null) {
            context.getContentResolver().update(mCleanPreviewUri, new ContentValues(), null);
        }
    }

    /**
     * Returns true if this wallpaper can be deleted.
     */
    public boolean canBeDeleted() {
        return mDeleteUri != null && !TextUtils.isEmpty(mDeleteUri.toString());
    }

    @Override
    public boolean isApplied(@Nullable WallpaperInfo currentHomeWallpaper,
            @Nullable WallpaperInfo currentLockWallpaper) {
        return super.isApplied(currentHomeWallpaper, currentLockWallpaper) && mIsCurrent;
    }

    /**
     * Requests the content provider to delete this wallpaper.
     */
    public void requestDelete(Context context) {
        context.getContentResolver().delete(mDeleteUri, null, null);
    }

    public void setEffectsToggles(ArrayList<WallpaperAction> effectsToggles) {
        mEffectsToggles = effectsToggles;
    }

    public ArrayList<WallpaperAction> getEffectsToggles() {
        return mEffectsToggles;
    }

    public String getEffectsBottomSheetTitle() {
        return mEffectsBottomSheetTitle;
    }

    public void setEffectsBottomSheetTitle(String effectsBottomSheetTitle) {
        mEffectsBottomSheetTitle = effectsBottomSheetTitle;
    }

    /**
     * Returns the URI that can be used to save a creative category wallpaper.
     * @return the save wallpaper URI
     */
    public Uri getSaveWallpaperUriForCreativeWallpaper() {
        Bundle metaData = this.getWallpaperComponent().getServiceInfo().metaData;
        if (metaData == null || !metaData.containsKey(
                KEY_WALLPAPER_SAVE_CREATIVE_CATEGORY_WALLPAPER)) {
            return null;
        }
        String keyForCreativeCategoryWallpaper = (String) metaData.get(
                KEY_WALLPAPER_SAVE_CREATIVE_CATEGORY_WALLPAPER);
        return Uri.parse(keyForCreativeCategoryWallpaper);
    }

    public String getEffectsBottomSheetSubtitle() {
        return mEffectsBottomSheetSubtitle;
    }

    public void setEffectsBottomSheetSubtitle(String effectsBottomSheetSubtitle) {
        mEffectsBottomSheetSubtitle = effectsBottomSheetSubtitle;
    }

    public Uri getClearActionsUri() {
        return mClearActionsUri;
    }

    public void setClearActionsUri(Uri clearActionsUri) {
        mClearActionsUri = clearActionsUri;
    }

    public Uri getEffectsUri() {
        return mEffectsUri;
    }

    public void setEffectsUri(Uri effectsUri) {
        mEffectsUri = effectsUri;
    }

    public String getCurrentlyAppliedEffectId() {
        return mCurrentlyAppliedEffectId;
    }

    public void setCurrentlyAppliedEffectId(String currentlyAppliedEffectId) {
        mCurrentlyAppliedEffectId = currentlyAppliedEffectId;
    }
    /**
     * Returns true if this wallpaper can be shared.
     */
    public boolean canBeShared() {
        return mShareUri != null && !TextUtils.isEmpty(mShareUri.toString());
    }

    /**
     * Gets the share wallpaper image intent.
     */
    public Intent getShareIntent() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, mShareUri);
        shareIntent.setType("image/*");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setClipData(ClipData.newRawUri(null, mShareUri));
        return Intent.createChooser(shareIntent, null);
    }

    /**
     * This method returns whether wallpaper effects are supported for this wallpaper.
     * @return boolean
     */
    public boolean doesSupportWallpaperEffects() {
        return (mClearActionsUri != null && !TextUtils.isEmpty(mEffectsBottomSheetTitle));
    }

    /**
     * Triggers the content provider call to clear all effects upon the current.
     * wallpaper.
     */
    public void clearEffects(Context context) {
        if (doesSupportWallpaperEffects() && mClearActionsUri != null) {
            context.getContentResolver().update(mClearActionsUri, new ContentValues(), null);
        }
    }

    /**
     * Triggers the content provider call to apply the selected effect upon the current
     * wallpaper.
     */
    public void applyEffect(Context context, Uri applyEffectUri) {
        if (doesSupportWallpaperEffects()) {
            context.getContentResolver().update(applyEffectUri, new ContentValues(), null);
        }
    }

    /**
     * Creates an object of CreativeWallpaperInfo from the given cursor object.
     *
     * @param wallpaperInfo contains relevant metadata information about creative-category wallpaper
     * @param cursor contains relevant info to create an object of CreativeWallpaperInfo
     * @return an object of type CreativeWallpaperInfo
     */
    @NonNull
    public static CreativeWallpaperInfo buildFromCursor(WallpaperInfo wallpaperInfo,
            Cursor cursor) {
        String wallpaperTitle = cursor.getString(
                cursor.getColumnIndex(WallpaperInfoContract.WALLPAPER_TITLE));
        String wallpaperAuthor = null;
        if (cursor.getColumnIndex(WallpaperInfoContract.WALLPAPER_AUTHOR) >= 0) {
            wallpaperAuthor = cursor.getString(
                    cursor.getColumnIndex(WallpaperInfoContract.WALLPAPER_AUTHOR));
        }
        String wallpaperDescription = null;
        if (cursor.getColumnIndex(WallpaperInfoContract.WALLPAPER_DESCRIPTION) >= 0) {
            wallpaperDescription = cursor.getString(
                    cursor.getColumnIndex(WallpaperInfoContract.WALLPAPER_DESCRIPTION));
        }
        String wallpaperContentDescription = null;
        int wallpaperContentDescriptionIndex = cursor.getColumnIndex(
                WallpaperInfoContract.WALLPAPER_CONTENT_DESCRIPTION);
        if (wallpaperContentDescriptionIndex >= 0) {
            wallpaperContentDescription = cursor.getString(
                    wallpaperContentDescriptionIndex);
        }
        Uri thumbnailUri = Uri.parse(cursor.getString(cursor.getColumnIndex(
                WallpaperInfoContract.WALLPAPER_THUMBNAIL)));
        Uri configPreviewUri = Uri.parse(cursor.getString(cursor.getColumnIndex(
                WallpaperInfoContract.WALLPAPER_CONFIG_PREVIEW_URI)));
        Uri cleanPreviewUri = Uri.parse(cursor.getString(cursor.getColumnIndex(
                WallpaperInfoContract.WALLPAPER_CLEAN_PREVIEW_URI)));
        Uri deleteUri = Uri.parse(cursor.getString(
                cursor.getColumnIndex(WallpaperInfoContract.WALLPAPER_DELETE_URI)));
        Uri shareUri = Uri.parse(cursor.getString(cursor.getColumnIndex(
                WallpaperInfoContract.WALLPAPER_SHARE_URI)));
        String groupName = cursor.getString(
                cursor.getColumnIndex(WallpaperInfoContract.WALLPAPER_GROUP_NAME));
        int isCurrentApplied = cursor.getInt(
                cursor.getColumnIndex(WallpaperInfoContract.WALLPAPER_IS_APPLIED));

        return new CreativeWallpaperInfo(wallpaperInfo, wallpaperTitle, wallpaperAuthor,
                wallpaperDescription, wallpaperContentDescription, configPreviewUri,
                cleanPreviewUri, deleteUri, thumbnailUri, shareUri, groupName, /* isCurrent= */
                (isCurrentApplied == 1));
    }

    /**
     * Saves a wallpaper of type of CreativeWallpaperInfo for a particular destination.
     * @param context context of the calling activity
     * @param destination depicts the destination of the wallpaper being saved
     * @return CreativeWallpaperInfo object that has been saved
     */
    @Override
    public CreativeWallpaperInfo saveWallpaper(Context context, int destination) {
        if (context == null) {
            Log.w(TAG, "Context is null!!");
            return null;
        }

        Uri saveWallpaperUri = getSaveWallpaperUriForCreativeWallpaper();
        if (saveWallpaperUri == null) {
            Log.w(TAG, "Missing save wallpaper uri in  " + this.getWallpaperComponent()
                    .getServiceName());
            return null;
        }
        return CreativeCategory.saveCreativeCategoryWallpaper(
                context, this, saveWallpaperUri, destination);
    }

    public Uri getConfigPreviewUri() {
        return mConfigPreviewUri;
    }

    public Uri getCleanPreviewUri() {
        return mCleanPreviewUri;
    }

    public Uri getDeleteUri() {
        return mDeleteUri;
    }

    public Uri getThumbnailUri() {
        return mThumbnailUri;
    }

    public Uri getShareUri() {
        return mShareUri;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getContentDescription() {
        return mContentDescription;
    }

    public boolean isCurrent() {
        return mIsCurrent;
    }

    public String getGroupName() {
        return mGroupName;
    }
}
