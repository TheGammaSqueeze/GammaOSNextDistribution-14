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
package com.android.customization.model.font;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.CONFIG_BODY_FONT_FAMILY;
import static com.android.customization.model.ResourceConstants.CONFIG_HEADLINE_FONT_FAMILY;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Typeface;
import android.os.UserHandle;
import android.util.Log;

import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.wallpaper.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FontOptionProvider {

    private static final String TAG = "FontOptionProvider";

    private Context mContext;
    private PackageManager mPm;
    private final List<String> mOverlayPackages;
    private final List<FontOption> mOptions = new ArrayList<>();
    private String mActiveOverlay;

    public FontOptionProvider(Context context, OverlayManagerCompat manager) {
        mContext = context;
        mPm = context.getPackageManager();
        mOverlayPackages = new ArrayList<>();
        mOverlayPackages.addAll(manager.getOverlayPackagesForCategory(OVERLAY_CATEGORY_FONT,
                UserHandle.myUserId(), ResourceConstants.getPackagesToOverlay(mContext)));
        mActiveOverlay = manager.getEnabledPackageName(ANDROID_PACKAGE, OVERLAY_CATEGORY_FONT);
    }

    public List<FontOption> getOptions(boolean reload) {
        if (reload) mOptions.clear();
        if (mOptions.isEmpty()) loadOptions();
        return mOptions;
    }

    private void loadOptions() {
        addDefault();
        for (String overlayPackage : mOverlayPackages) {
            try {
                Resources overlayRes = mPm.getResourcesForApplication(overlayPackage);
                Typeface headlineFont = Typeface.create(
                        getFontFamily(overlayPackage, overlayRes, CONFIG_HEADLINE_FONT_FAMILY),
                        Typeface.NORMAL);
                Typeface bodyFont = Typeface.create(
                        getFontFamily(overlayPackage, overlayRes, CONFIG_BODY_FONT_FAMILY),
                        Typeface.NORMAL);
                String label = mPm.getApplicationInfo(overlayPackage, 0).loadLabel(mPm).toString();
                mOptions.add(new FontOption(overlayPackage, label, headlineFont, bodyFont));
            } catch (NameNotFoundException | NotFoundException e) {
                Log.w(TAG, String.format("Couldn't load font overlay %s, will skip it",
                        overlayPackage), e);
            }
        }
    }

    private void addDefault() {
        Resources system = Resources.getSystem();
        Typeface headlineFont = Typeface.create(system.getString(system.getIdentifier(
                ResourceConstants.CONFIG_HEADLINE_FONT_FAMILY,"string", ANDROID_PACKAGE)),
                Typeface.NORMAL);
        Typeface bodyFont = Typeface.create(system.getString(system.getIdentifier(
                ResourceConstants.CONFIG_BODY_FONT_FAMILY,
                "string", ANDROID_PACKAGE)),
                Typeface.NORMAL);
        mOptions.add(new FontOption(null, mContext.getString(R.string.default_theme_title),
                headlineFont, bodyFont));
    }

    private String getFontFamily(String overlayPackage, Resources overlayRes, String configName) {
        return overlayRes.getString(overlayRes.getIdentifier(configName, "string", overlayPackage));
    }
}
