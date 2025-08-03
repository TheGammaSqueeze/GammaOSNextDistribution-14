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
package com.android.wallpaper.asset;

import android.app.WallpaperInfo;
import android.content.Context;
import android.net.Uri;

/** Defines creative wallpaper's thumbnail asset. */
public class CreativeWallpaperThumbAsset extends LiveWallpaperThumbAsset {

    public CreativeWallpaperThumbAsset(Context context, WallpaperInfo info, Uri thumbnailUri) {
        super(context, info, thumbnailUri, /* shouldCacheThumbnail= */ false);
    }
}
