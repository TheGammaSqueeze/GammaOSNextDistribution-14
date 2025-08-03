/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.util;

import android.app.slice.SliceManager;
import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.Collection;

/** Utility class for slice **/
public final class SliceUtils {
    private static final String TAG = "SliceUtils";

    public static final String PATH_SLICE_FRAGMENT =
            "com.android.tv.twopanelsettings.slices.SliceFragment";

    /**
     * Check if slice provider exists.
     */
    public static boolean isSliceProviderValid(Context context, String uri) {
        if (uri == null) {
            return false;
        }
        ContentProviderClient client =
                context.getContentResolver().acquireContentProviderClient(Uri.parse(uri));
        if (client != null) {
            client.close();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if the slice is enabled
     *
     * @param context                  Current context of the app
     * @param uri                      Settings slice uri
     * @param topLevelSettingsSliceUri Top level settings slice uri, if null, use provided uri to
     *                                 deduce top level settings slice uri.
     * @return returns true if slice is enabled, false otherwise
     */
    public static boolean isSettingsSliceEnabled(Context context, String uri,
            String topLevelSettingsSliceUri) {
        if (uri == null) {
            return false;
        }
        final SliceManager sliceManager = context.getSystemService(SliceManager.class);
        if (sliceManager == null) {
            return false;
        }
        try {
            Uri topLevelSettingsSlice = topLevelSettingsSliceUri == null
                    ? Uri.parse(uri).buildUpon().path("/").build()
                    : Uri.parse(ResourcesUtil.getString(context, topLevelSettingsSliceUri));
            final Collection<Uri> enabledSlicesUri = sliceManager
                    .getSliceDescendants(topLevelSettingsSlice);
            if (enabledSlicesUri != null) {
                for (final Uri sliceUri : enabledSlicesUri) {
                    Log.i(TAG, "Enabled slice: " + sliceUri);
                    if (sliceUri.toString().equals(uri)) {
                        return true;
                    }
                }
            }
        } catch (NullPointerException nullPointerException) {
            Log.e(TAG, "Unable to get slice descendants", nullPointerException);
        }
        return false;
    }
}
