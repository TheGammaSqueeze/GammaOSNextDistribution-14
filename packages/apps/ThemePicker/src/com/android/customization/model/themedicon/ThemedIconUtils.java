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
package com.android.customization.model.themedicon;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Utility class for themed icon.
 */
public class ThemedIconUtils {

    private final Context mContext;
    private final String mProviderAuthority;

    private ProviderInfo mProviderInfo;

    public ThemedIconUtils(Context context, String authorityMetaKey) {
        mContext = context;
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivity(homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA);

        if (resolveInfo != null && resolveInfo.activityInfo.metaData != null) {
            mProviderAuthority = resolveInfo.activityInfo.metaData.getString(authorityMetaKey);
        } else {
            mProviderAuthority = null;
        }
        mProviderInfo = TextUtils.isEmpty(mProviderAuthority) ? null :
                mContext.getPackageManager().resolveContentProvider(mProviderAuthority, 0);
        if (mProviderInfo != null && !TextUtils.isEmpty(mProviderInfo.readPermission)) {
            if (mContext.checkSelfPermission(mProviderInfo.readPermission)
                    != PackageManager.PERMISSION_GRANTED) {
                mProviderInfo = null;
            }
        }
    }

    /**
     * Gets the Uri for this provider's authority with path information.
     *
     * @param path the path segment of {@link Uri}
     * @return {@link Uri} with path information
     */
    Uri getUriForPath(String path) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(mProviderInfo.authority)
                .appendPath(path)
                .build();
    }

    /**
     * Returns if themed icon is available.
     *
     * @return true if themed icon feature is available, false otherwise.
     */
    boolean isThemedIconAvailable() {
        return mProviderInfo != null;
    }
}
