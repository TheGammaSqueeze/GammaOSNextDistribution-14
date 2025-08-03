/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.R;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.ExploreIntentChecker;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.util.ActivityUtils;

/** A helper class for wallpaper info. */
public class WallpaperInfoHelper {

    /** A callback for receiving explore Intent. */
    public interface ExploreIntentReceiver {
        /** Gets called when received explore Intent. */
        void onReceiveExploreIntent(CharSequence actionLabel, @Nullable Intent exploreIntent);
    }

    /** Loads the explore Intent from the specific wallpaper. */
    public static void loadExploreIntent(
            Context context,
            @NonNull WallpaperInfo wallpaperInfo,
            @NonNull ExploreIntentReceiver callback) {
        String actionUrl = wallpaperInfo.getActionUrl(context);
        CharSequence actionLabel = context.getString(R.string.explore);
        if (actionUrl != null && !actionUrl.isEmpty()) {
            Uri exploreUri = Uri.parse(wallpaperInfo.getActionUrl(context));
            ExploreIntentChecker intentChecker =
                    InjectorProvider.getInjector().getExploreIntentChecker(context);
            intentChecker.fetchValidActionViewIntent(exploreUri,
                    intent -> callback.onReceiveExploreIntent(actionLabel, intent));
        } else {
            callback.onReceiveExploreIntent(actionLabel, null);
        }
    }

    /**
     * Loads the explore Intent from the actionUrl
     */
    public static void loadExploreIntent(
            Context context,
            @Nullable String actionUrl,
            @NonNull ExploreIntentReceiver callback) {
        CharSequence actionLabel = context.getString(R.string.explore);

        if (!TextUtils.isEmpty(actionUrl)) {
            Uri exploreUri = Uri.parse(actionUrl);
            ExploreIntentChecker intentChecker =
                    InjectorProvider.getInjector().getExploreIntentChecker(context);
            intentChecker.fetchValidActionViewIntent(exploreUri,
                    intent -> callback.onReceiveExploreIntent(actionLabel, intent));
        } else {
            callback.onReceiveExploreIntent(actionLabel, null);
        }
    }

    /** Indicates if the explore button should show up in the wallpaper info view. */
    public static boolean shouldShowExploreButton(Context context, @Nullable Intent exploreIntent) {
        return exploreIntent != null && !ActivityUtils.isSUWMode(context);
    }

    private WallpaperInfoHelper() {}
}
