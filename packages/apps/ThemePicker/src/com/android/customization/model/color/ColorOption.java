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
package com.android.customization.model.color;

import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.customization.model.color.ColorOptionsProvider.ColorSource;
import com.android.customization.module.logging.ThemesUserEventLogger;
import com.android.systemui.monet.Style;
import com.android.themepicker.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a color choice for the user.
 * This could be a preset color or those obtained from a wallpaper.
 */
public abstract class ColorOption implements CustomizationOption<ColorOption> {

    private static final String TAG = "ColorOption";
    private static final String EMPTY_JSON = "{}";
    @VisibleForTesting
    static final String TIMESTAMP_FIELD = "_applied_timestamp";

    protected final Map<String, String> mPackagesByCategory;
    private final String mTitle;
    private final boolean mIsDefault;
    private final Style mStyle;
    private final int mIndex;
    private CharSequence mContentDescription;

    protected ColorOption(String title, Map<String, String> overlayPackages, boolean isDefault,
            Style style, int index) {
        mTitle = title;
        mIsDefault = isDefault;
        mStyle = style;
        mIndex = index;
        mPackagesByCategory = Collections.unmodifiableMap(removeNullValues(overlayPackages));
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public boolean isActive(CustomizationManager<ColorOption> manager) {
        ColorCustomizationManager colorManager = (ColorCustomizationManager) manager;

        String currentStyle = colorManager.getCurrentStyle();
        if (TextUtils.isEmpty(currentStyle)) {
            currentStyle = Style.TONAL_SPOT.toString();
        }
        boolean isCurrentStyle = TextUtils.equals(getStyle().toString(), currentStyle);

        if (mIsDefault) {
            String serializedOverlays = colorManager.getStoredOverlays();
            // a default color option is active if the manager has no stored overlays or current
            // overlays, or the stored overlay does not contain either category system palette or
            // category color
            return (TextUtils.isEmpty(serializedOverlays) || EMPTY_JSON.equals(serializedOverlays)
                    || colorManager.getCurrentOverlays().isEmpty() || !(serializedOverlays.contains(
                    OVERLAY_CATEGORY_SYSTEM_PALETTE) || serializedOverlays.contains(
                    OVERLAY_CATEGORY_COLOR))) && isCurrentStyle;
        } else {
            Map<String, String> currentOverlays = colorManager.getCurrentOverlays();
            String currentSource = colorManager.getCurrentColorSource();
            boolean isCurrentSource = TextUtils.isEmpty(currentSource) || getSource().equals(
                    currentSource);
            return isCurrentSource && isCurrentStyle && mPackagesByCategory.equals(currentOverlays);
        }
    }

    /**
     * Gets the seed color from the overlay packages for logging.
     *
     * @return an int representing the seed color, or NULL_SEED_COLOR
     */
    public int getSeedColorForLogging() {
        String seedColor = mPackagesByCategory.get(OVERLAY_CATEGORY_SYSTEM_PALETTE);
        if (seedColor == null || seedColor.isEmpty()) {
            return ThemesUserEventLogger.NULL_SEED_COLOR;
        }
        if (!seedColor.startsWith("#")) {
            seedColor = "#" + seedColor;
        }
        return Color.parseColor(seedColor);
    }

    /**
     * This is similar to #equals() but it only compares this theme's packages with the other, that
     * is, it will return true if applying this theme has the same effect of applying the given one.
     */
    public boolean isEquivalent(ColorOption other) {
        if (other == null) {
            return false;
        }
        if (mStyle != other.getStyle()) {
            return false;
        }
        String thisSerializedPackages = getSerializedPackages();
        if (mIsDefault || TextUtils.isEmpty(thisSerializedPackages)
                || EMPTY_JSON.equals(thisSerializedPackages)) {
            String otherSerializedPackages = other.getSerializedPackages();
            return other.isDefault() || TextUtils.isEmpty(otherSerializedPackages)
                    || EMPTY_JSON.equals(otherSerializedPackages);
        }
        // Map#equals ensures keys and values are compared.
        return mPackagesByCategory.equals(other.mPackagesByCategory);
    }

    /**
     * Returns the {@link PreviewInfo} object for this ColorOption
     */
    public abstract PreviewInfo getPreviewInfo();

    boolean isDefault() {
        return mIsDefault;
    }

    public Map<String, String> getPackagesByCategory() {
        return mPackagesByCategory;
    }

    public String getSerializedPackages() {
        return getJsonPackages(false).toString();
    }

    public String getSerializedPackagesWithTimestamp() {
        return getJsonPackages(true).toString();
    }

    /**
     * Get a JSONObject representation of this color option, with the current values for each
     * field, and optionally a {@link TIMESTAMP_FIELD} field.
     * @param insertTimestamp whether to add a field with the current timestamp
     * @return the JSONObject for this color option
     */
    public JSONObject getJsonPackages(boolean insertTimestamp) {
        JSONObject json;
        if (isDefault()) {
            json = new JSONObject();
        } else {
            json = new JSONObject(mPackagesByCategory);
            // Remove items with null values to avoid deserialization issues.
            removeNullValues(json);
        }
        if (insertTimestamp) {
            try {
                json.put(TIMESTAMP_FIELD, System.currentTimeMillis());
            } catch (JSONException e) {
                Log.e(TAG, "Couldn't add timestamp to serialized themebundle");
            }
        }
        return json;
    }

    private void removeNullValues(JSONObject json) {
        Iterator<String> keys = json.keys();
        Set<String> keysToRemove = new HashSet<>();
        while (keys.hasNext()) {
            String key = keys.next();
            if (json.isNull(key)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            json.remove(key);
        }
    }

    private Map<String, String> removeNullValues(Map<String, String> map) {
        return map.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** */
    public CharSequence getContentDescription(Context context) {
        if (mContentDescription == null) {
            CharSequence defaultName = context.getString(R.string.default_theme_title);
            if (isDefault()) {
                mContentDescription = defaultName;
            } else {
                mContentDescription = mTitle;
            }
        }
        return mContentDescription;
    }

    /**
     * @return the source of this color option
     */
    @ColorSource
    public abstract String getSource();

    /**
     * @return the source of this color option for logging
     */
    @ThemesUserEventLogger.ColorSource
    public abstract int getSourceForLogging();

    /**
     * @return the style of this color option
     */
    public Style getStyle() {
        return mStyle;
    }

    /**
     * @return the style of this color option for logging
     */
    public abstract int getStyleForLogging();

    /**
     * @return the index of this color option
     */
    public int getIndex() {
        return mIndex;
    }

    /**
     * The preview information of {@link ColorOption}
     */
    public interface PreviewInfo {
    }

}
