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

import static android.stats.style.StyleEnums.COLOR_SOURCE_HOME_SCREEN_WALLPAPER;
import static android.stats.style.StyleEnums.COLOR_SOURCE_LOCK_SCREEN_WALLPAPER;
import static android.stats.style.StyleEnums.COLOR_SOURCE_PRESET_COLOR;
import static android.stats.style.StyleEnums.COLOR_SOURCE_UNSPECIFIED;

import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE;
import static com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_PRESET;
import static com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_BOTH;
import static com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_INDEX;
import static com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_SOURCE;
import static com.android.customization.model.color.ColorOptionsProvider.OVERLAY_THEME_STYLE;

import android.app.WallpaperColors;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.ResourceConstants;
import com.android.customization.model.color.ColorOptionsProvider.ColorSource;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.customization.module.logging.ThemesUserEventLogger;
import com.android.themepicker.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The Color manager to manage Color bundle related operations. */
public class ColorCustomizationManager implements CustomizationManager<ColorOption> {

    private static final String TAG = "ColorCustomizationManager";
    private static final ExecutorService sExecutorService = Executors.newSingleThreadExecutor();

    private static final Set<String> COLOR_OVERLAY_SETTINGS = new HashSet<>();
    static {
        COLOR_OVERLAY_SETTINGS.add(OVERLAY_CATEGORY_SYSTEM_PALETTE);
        COLOR_OVERLAY_SETTINGS.add(OVERLAY_CATEGORY_COLOR);
        COLOR_OVERLAY_SETTINGS.add(OVERLAY_COLOR_SOURCE);
        COLOR_OVERLAY_SETTINGS.add(OVERLAY_THEME_STYLE);
    }

    private static ColorCustomizationManager sColorCustomizationManager;

    private final ColorOptionsProvider mProvider;
    private final OverlayManagerCompat mOverlayManagerCompat;
    private final ContentResolver mContentResolver;
    private final ContentObserver mObserver;

    private Map<String, String> mCurrentOverlays;
    @ColorSource private String mCurrentSource;
    private String mCurrentStyle;
    private WallpaperColors mHomeWallpaperColors;
    private WallpaperColors mLockWallpaperColors;

    /** Returns the {@link ColorCustomizationManager} instance. */
    public static ColorCustomizationManager getInstance(Context context,
            OverlayManagerCompat overlayManagerCompat) {
        if (sColorCustomizationManager == null) {
            Context appContext = context.getApplicationContext();
            sColorCustomizationManager = new ColorCustomizationManager(
                    new ColorProvider(appContext,
                            appContext.getString(R.string.themes_stub_package)),
                    appContext.getContentResolver(), overlayManagerCompat);
        }
        return sColorCustomizationManager;
    }

    @VisibleForTesting
    ColorCustomizationManager(ColorOptionsProvider provider, ContentResolver contentResolver,
            OverlayManagerCompat overlayManagerCompat) {
        mProvider = provider;
        mContentResolver = contentResolver;
        mObserver = new ContentObserver(/* handler= */ null) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                // Resets current overlays when system's theme setting is changed.
                if (TextUtils.equals(uri.getLastPathSegment(), ResourceConstants.THEME_SETTING)) {
                    Log.i(TAG, "Resetting " + mCurrentOverlays + ", " + mCurrentStyle + ", "
                            + mCurrentSource + " to null");
                    mCurrentOverlays = null;
                    mCurrentStyle = null;
                    mCurrentSource = null;
                }
            }
        };
        mContentResolver.registerContentObserver(
                Settings.Secure.CONTENT_URI, /* notifyForDescendants= */ true, mObserver);
        mOverlayManagerCompat = overlayManagerCompat;
    }

    @Override
    public boolean isAvailable() {
        return mOverlayManagerCompat.isAvailable() && mProvider.isAvailable();
    }

    @Override
    public void apply(ColorOption theme, Callback callback) {
        applyOverlays(theme, callback);
    }

    private void applyOverlays(ColorOption colorOption, Callback callback) {
        sExecutorService.submit(() -> {
            String currentStoredOverlays = getStoredOverlays();
            if (TextUtils.isEmpty(currentStoredOverlays)) {
                currentStoredOverlays = "{}";
            }
            JSONObject overlaysJson = null;
            try {
                overlaysJson = new JSONObject(currentStoredOverlays);
                JSONObject colorJson = colorOption.getJsonPackages(true);
                for (String setting : COLOR_OVERLAY_SETTINGS) {
                    overlaysJson.remove(setting);
                }
                for (Iterator<String> it = colorJson.keys(); it.hasNext(); ) {
                    String key = it.next();
                    overlaysJson.put(key, colorJson.get(key));
                }
                overlaysJson.put(OVERLAY_COLOR_SOURCE, colorOption.getSource());
                overlaysJson.put(OVERLAY_COLOR_INDEX, String.valueOf(colorOption.getIndex()));
                overlaysJson.put(OVERLAY_THEME_STYLE,
                        String.valueOf(colorOption.getStyle().toString()));

                // OVERLAY_COLOR_BOTH is only for wallpaper color case, not preset.
                if (!COLOR_SOURCE_PRESET.equals(colorOption.getSource())) {
                    boolean isForBoth =
                            (mLockWallpaperColors == null || mLockWallpaperColors.equals(
                                    mHomeWallpaperColors));
                    overlaysJson.put(OVERLAY_COLOR_BOTH, isForBoth ? "1" : "0");
                } else {
                    overlaysJson.remove(OVERLAY_COLOR_BOTH);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            boolean allApplied = overlaysJson != null && Settings.Secure.putString(
                    mContentResolver, ResourceConstants.THEME_SETTING, overlaysJson.toString());
            new Handler(Looper.getMainLooper()).post(() -> {
                if (allApplied) {
                    callback.onSuccess();
                } else {
                    callback.onError(null);
                }
            });
        });
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<ColorOption> callback, boolean reload) {
        WallpaperColors lockWallpaperColors = mLockWallpaperColors;
        if (lockWallpaperColors != null && mLockWallpaperColors.equals(mHomeWallpaperColors)) {
            lockWallpaperColors = null;
        }
        mProvider.fetch(callback, reload, mHomeWallpaperColors,
                lockWallpaperColors);
    }

    /**
     * Sets the current wallpaper colors to extract seeds from
     */
    public void setWallpaperColors(WallpaperColors homeColors,
            @Nullable WallpaperColors lockColors) {
        mHomeWallpaperColors = homeColors;
        mLockWallpaperColors = lockColors;
    }

    /**
     * Gets current overlays mapping
     * @return the {@link Map} of overlays
     */
    public Map<String, String> getCurrentOverlays() {
        if (mCurrentOverlays == null) {
            parseSettings(getStoredOverlays());
        }
        return mCurrentOverlays;
    }

    /** */
    public int getCurrentColorSourceForLogging() {
        String colorSource = getCurrentColorSource();
        if (colorSource == null) {
            return COLOR_SOURCE_UNSPECIFIED;
        }
        return switch (colorSource) {
            case ColorOptionsProvider.COLOR_SOURCE_PRESET -> COLOR_SOURCE_PRESET_COLOR;
            case ColorOptionsProvider.COLOR_SOURCE_HOME -> COLOR_SOURCE_HOME_SCREEN_WALLPAPER;
            case ColorOptionsProvider.COLOR_SOURCE_LOCK -> COLOR_SOURCE_LOCK_SCREEN_WALLPAPER;
            default -> COLOR_SOURCE_UNSPECIFIED;
        };
    }

    /** */
    public int getCurrentStyleForLogging() {
        String style = getCurrentStyle();
        return style != null ? style.hashCode() : 0;
    }

    /** */
    public int getCurrentSeedColorForLogging() {
        String seedColor = getCurrentOverlays().get(OVERLAY_CATEGORY_SYSTEM_PALETTE);
        if (seedColor == null || seedColor.isEmpty()) {
            return ThemesUserEventLogger.NULL_SEED_COLOR;
        }
        if (!seedColor.startsWith("#")) {
            seedColor = "#" + seedColor;
        }
        return Color.parseColor(seedColor);
    }

    /**
     * @return The source of the currently applied color. One of
     * {@link ColorOptionsProvider#COLOR_SOURCE_HOME},{@link ColorOptionsProvider#COLOR_SOURCE_LOCK}
     * or {@link ColorOptionsProvider#COLOR_SOURCE_PRESET}.
     */
    @ColorSource
    public @Nullable String getCurrentColorSource() {
        if (mCurrentSource == null) {
            parseSettings(getStoredOverlays());
        }
        return mCurrentSource;
    }

    /**
     * @return The style of the currently applied color. One of enum values in
     * {@link com.android.systemui.monet.Style}.
     */
    public @Nullable String getCurrentStyle() {
        if (mCurrentStyle == null) {
            parseSettings(getStoredOverlays());
        }
        return mCurrentStyle;
    }

    public String getStoredOverlays() {
        return Settings.Secure.getString(mContentResolver, ResourceConstants.THEME_SETTING);
    }

    @VisibleForTesting
    void parseSettings(String serializedJson) {
        Map<String, String> allSettings = parseColorSettings(serializedJson);
        mCurrentSource = allSettings.remove(OVERLAY_COLOR_SOURCE);
        mCurrentStyle = allSettings.remove(OVERLAY_THEME_STYLE);
        mCurrentOverlays = allSettings;
    }

    private Map<String, String> parseColorSettings(String serializedJsonSettings) {
        Map<String, String> overlayPackages = new HashMap<>();
        if (serializedJsonSettings != null) {
            try {
                final JSONObject jsonPackages = new JSONObject(serializedJsonSettings);

                JSONArray names = jsonPackages.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String category = names.getString(i);
                        if (COLOR_OVERLAY_SETTINGS.contains(category)) {
                            try {
                                overlayPackages.put(category, jsonPackages.getString(category));
                            } catch (JSONException e) {
                                Log.e(TAG, "parseColorOverlays: " + e.getLocalizedMessage(), e);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "parseColorOverlays: " + e.getLocalizedMessage(), e);
            }
        }
        return overlayPackages;
    }
}
