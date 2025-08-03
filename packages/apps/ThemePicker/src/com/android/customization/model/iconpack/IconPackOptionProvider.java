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
package com.android.customization.model.iconpack;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.ICONS_FOR_PREVIEW;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_ANDROID;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SETTINGS;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SYSUI;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.Log;

import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.wallpaper.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconPackOptionProvider {

    private static final String TAG = "IconPackOptionProvider";

    private Context mContext;
    private PackageManager mPm;
    private final List<String> mOverlayPackages;
    private final List<IconPackOption> mOptions = new ArrayList<>();
    private final List<String> mSysUiIconsOverlayPackages = new ArrayList<>();
    private final List<String> mSettingsIconsOverlayPackages = new ArrayList<>();

    public IconPackOptionProvider(Context context, OverlayManagerCompat manager) {
        mContext = context;
        mPm = context.getPackageManager();
        String[] targetPackages = ResourceConstants.getPackagesToOverlay(context);
        mSysUiIconsOverlayPackages.addAll(manager.getOverlayPackagesForCategory(
                OVERLAY_CATEGORY_ICON_SYSUI, UserHandle.myUserId(), targetPackages));
        mSettingsIconsOverlayPackages.addAll(manager.getOverlayPackagesForCategory(
                OVERLAY_CATEGORY_ICON_SETTINGS, UserHandle.myUserId(), targetPackages));
        mOverlayPackages = new ArrayList<>();
        mOverlayPackages.addAll(manager.getOverlayPackagesForCategory(OVERLAY_CATEGORY_ICON_ANDROID,
                UserHandle.myUserId(), ResourceConstants.getPackagesToOverlay(mContext)));
    }

    public List<IconPackOption> getOptions() {
        if (mOptions.isEmpty()) loadOptions();
        return mOptions;
    }

    private void loadOptions() {
        addDefault();

        Map<String, IconPackOption> optionsByPrefix = new HashMap<>();
        for (String overlayPackage : mOverlayPackages) {
            IconPackOption option = addOrUpdateOption(optionsByPrefix, overlayPackage,
                    OVERLAY_CATEGORY_ICON_ANDROID);
            try{
                for (String iconName : ICONS_FOR_PREVIEW) {
                    option.addIcon(loadIconPreviewDrawable(iconName, overlayPackage));
                }
            } catch (NotFoundException | NameNotFoundException e) {
                Log.w(TAG, String.format("Couldn't load icon overlay details for %s, will skip it",
                        overlayPackage), e);
            }
        }

        for (String overlayPackage : mSysUiIconsOverlayPackages) {
            addOrUpdateOption(optionsByPrefix, overlayPackage, OVERLAY_CATEGORY_ICON_SYSUI);
        }

        for (String overlayPackage : mSettingsIconsOverlayPackages) {
            addOrUpdateOption(optionsByPrefix, overlayPackage, OVERLAY_CATEGORY_ICON_SETTINGS);
        }

        for (IconPackOption option : optionsByPrefix.values()) {
            if (option.isValid(mContext)) {
                mOptions.add(option);
            }
        }
    }

    private IconPackOption addOrUpdateOption(Map<String, IconPackOption> optionsByPrefix,
            String overlayPackage, String category) {
        String prefix = overlayPackage.substring(0, overlayPackage.lastIndexOf("."));
        IconPackOption option = null;
        try {
            if (!optionsByPrefix.containsKey(prefix)) {
                option = new IconPackOption(mPm.getApplicationInfo(overlayPackage, 0).loadLabel(mPm).toString());
                optionsByPrefix.put(prefix, option);
            } else {
                option = optionsByPrefix.get(prefix);
            }
            option.addOverlayPackage(category, overlayPackage);
        } catch (NameNotFoundException e) {
            Log.e(TAG, String.format("Package %s not found", overlayPackage), e);
        }
        return option;
    }

    private Drawable loadIconPreviewDrawable(String drawableName, String packageName)
            throws NameNotFoundException, NotFoundException {
        final Resources resources = ANDROID_PACKAGE.equals(packageName)
                ? Resources.getSystem()
                : mPm.getResourcesForApplication(packageName);
        return resources.getDrawable(
                resources.getIdentifier(drawableName, "drawable", packageName), null);
    }

    private void addDefault() {
        IconPackOption option = new IconPackOption(mContext.getString(R.string.default_theme_title), true);
        try {
            for (String iconName : ICONS_FOR_PREVIEW) {
                option.addIcon(loadIconPreviewDrawable(iconName, ANDROID_PACKAGE));
            }
        } catch (NameNotFoundException | NotFoundException e) {
            Log.w(TAG, "Didn't find SystemUi package icons, will skip option", e);
        }
        option.addOverlayPackage(OVERLAY_CATEGORY_ICON_ANDROID, null);
        option.addOverlayPackage(OVERLAY_CATEGORY_ICON_SYSUI, null);
        option.addOverlayPackage(OVERLAY_CATEGORY_ICON_SETTINGS, null);
        mOptions.add(option);
    }

}
