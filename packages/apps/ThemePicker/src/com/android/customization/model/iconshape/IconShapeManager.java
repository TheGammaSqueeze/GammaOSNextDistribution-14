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
package com.android.customization.model.iconshape;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.theme.OverlayManagerCompat;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class IconShapeManager implements CustomizationManager<IconShapeOption> {

    private static IconShapeManager sIconShapeOptionManager;
    private Context mContext;
    private IconShapeOption mActiveOption;
    private OverlayManagerCompat mOverlayManager;
    private IconShapeOptionProvider mProvider;
    private static final String TAG = "IconShapeManager";
    private static final String KEY_STATE_CURRENT_SELECTION = "IconShapeManager.currentSelection";

    IconShapeManager(Context context, OverlayManagerCompat overlayManager, IconShapeOptionProvider provider) {
        mContext = context;
        mProvider = provider;
        mOverlayManager = overlayManager;
    }

    @Override
    public boolean isAvailable() {
        return mOverlayManager.isAvailable();
    }

    @Override
    public void apply(IconShapeOption option, @Nullable Callback callback) {
        if (!persistOverlay(option)) {
            Toast failed = Toast.makeText(mContext, "Failed to apply font, reboot to try again.", Toast.LENGTH_SHORT);
            failed.show();
            if (callback != null) {
                callback.onError(null);
            }
            return;
        }
        if (option.getPackageName() == null) {
            if (mActiveOption.getPackageName() == null) return;
            for (String overlay : mOverlayManager.getOverlayPackagesForCategory(
                    OVERLAY_CATEGORY_SHAPE, UserHandle.myUserId(), ANDROID_PACKAGE)) {
                mOverlayManager.disableOverlay(overlay, UserHandle.myUserId());
            }
        } else {
            mOverlayManager.setEnabledExclusiveInCategory(option.getPackageName(), UserHandle.myUserId());
        }
        if (callback != null) {
            callback.onSuccess();
        }
        mActiveOption = option;
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<IconShapeOption> callback, boolean reload) {
        List<IconShapeOption> options = mProvider.getOptions();
        for (IconShapeOption option : options) {
            if (option.isActive(this)) {
                mActiveOption = option;
                break;
            }
        }
        callback.onOptionsLoaded(options);
    }

    public OverlayManagerCompat getOverlayManager() {
        return mOverlayManager;
    }

    private boolean persistOverlay(IconShapeOption toPersist) {
        String value = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, UserHandle.myUserId());
        JSONObject json;
        if (value == null) {
            json = new JSONObject();
        } else {
            try {
                json = new JSONObject(value);
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing current settings value:\n" + e.getMessage());
                return false;
            }
        }
        // removing all currently enabled overlays from the json
        json.remove(OVERLAY_CATEGORY_SHAPE);
        // adding the new ones
        try {
            json.put(OVERLAY_CATEGORY_SHAPE, toPersist.getPackageName());
        } catch (JSONException e) {
            Log.e(TAG, "Error adding new settings value:\n" + e.getMessage());
            return false;
        }
        // updating the setting
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                json.toString(), UserHandle.myUserId());
        return true;
    }

    public static IconShapeManager getInstance(Context context, OverlayManagerCompat overlayManager) {
        if (sIconShapeOptionManager == null) {
            Context applicationContext = context.getApplicationContext();
            sIconShapeOptionManager = new IconShapeManager(context, overlayManager, new IconShapeOptionProvider(applicationContext, overlayManager));
        }
        return sIconShapeOptionManager;
    }

}
