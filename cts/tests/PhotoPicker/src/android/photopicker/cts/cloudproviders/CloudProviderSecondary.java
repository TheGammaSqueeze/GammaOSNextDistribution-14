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

package android.photopicker.cts.cloudproviders;

import static android.photopicker.cts.PickerProviderMediaGenerator.MediaGenerator;
import static android.photopicker.cts.PickerProviderMediaGenerator.QueryExtras;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.photopicker.cts.PickerProviderMediaGenerator;
import android.provider.CloudMediaProvider;

import java.io.FileNotFoundException;

/**
 * Implements a cloud {@link CloudMediaProvider} interface over items generated with
 * {@link MediaGenerator}
 */
public class CloudProviderSecondary extends CloudMediaProvider {
    public static final String AUTHORITY = "android.photopicker.cts.cloudproviders.cloud_secondary";

    private MediaGenerator mMediaGenerator;

    @Override
    public boolean onCreate() {
        mMediaGenerator = PickerProviderMediaGenerator.getMediaGenerator(getContext(), AUTHORITY);
        return true;
    }

    @Override
    public Cursor onQueryMedia(Bundle extras) {
        final QueryExtras queryExtras = new QueryExtras(extras);

        return mMediaGenerator.getMedia(queryExtras.generation, queryExtras.albumId,
                queryExtras.mimeType, queryExtras.sizeBytes, queryExtras.pageSize);
    }

    @Override
    public Cursor onQueryDeletedMedia(Bundle extras) {
        final QueryExtras queryExtras = new QueryExtras(extras);

        return mMediaGenerator.getDeletedMedia(queryExtras.generation);
    }

    @Override
    public Cursor onQueryAlbums(Bundle extras) {
        final QueryExtras queryExtras = new QueryExtras(extras);

        return mMediaGenerator.getAlbums(queryExtras.mimeType, queryExtras.sizeBytes);
    }

    @Override
    public AssetFileDescriptor onOpenPreview(String mediaId, Point size, Bundle extras,
            CancellationSignal signal) throws FileNotFoundException {
        return new AssetFileDescriptor(mMediaGenerator.openMedia(mediaId), 0,
                AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    @Override
    public ParcelFileDescriptor onOpenMedia(String mediaId, Bundle extras,
            CancellationSignal signal) throws FileNotFoundException {
        return mMediaGenerator.openMedia(mediaId);
    }

    @Override
    public Bundle onGetMediaCollectionInfo(Bundle extras) {
        return mMediaGenerator.getMediaCollectionInfo();
    }
}
