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
import static android.provider.CloudMediaProviderContract.EXTRA_LOOPING_PLAYBACK_ENABLED;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.photopicker.cts.PickerProviderMediaGenerator;
import android.provider.CloudMediaProvider;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;

/**
 * Implements a cloud {@link CloudMediaProvider} interface over items generated with
 * {@link MediaGenerator}.
 */
public class CloudProviderPrimary extends CloudMediaProvider {
    public static final String AUTHORITY = "android.photopicker.cts.cloudproviders.cloud_primary";

    private static final String TAG = "CloudProviderPrimary";
    private static final CloudMediaSurfaceControllerImpl sMockSurfaceControllerListener =
            mock(CloudMediaSurfaceControllerImpl.class);

    private static CloudMediaSurfaceControllerImpl sSurfaceControllerImpl = null;

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

    @Override
    public CloudMediaSurfaceController onCreateCloudMediaSurfaceController(@NonNull Bundle config,
            @NonNull CloudMediaSurfaceStateChangedCallback callback) {
        final boolean enableLoop = config.getBoolean(EXTRA_LOOPING_PLAYBACK_ENABLED, false);
        sSurfaceControllerImpl =
                new CloudMediaSurfaceControllerImpl(getContext(), enableLoop, callback);
        return sSurfaceControllerImpl;
    }

    /**
     * @return mock {@link CloudMediaSurfaceController} that enables us to test API calls from
     * PhotoPicker to the {@link CloudMediaProvider}.
     */
    public static CloudMediaSurfaceControllerImpl getMockSurfaceControllerListener() {
        return sMockSurfaceControllerListener;
    }

    public static void setPlaybackState(int surfaceId, int state) {
        if (sSurfaceControllerImpl == null) {
            throw new IllegalStateException("Surface Controller object expected to be not null");
        }

        sSurfaceControllerImpl.sendPlaybackEvent(surfaceId, state);
    }

    public static class CloudMediaSurfaceControllerImpl extends CloudMediaSurfaceController {

        private final CloudMediaSurfaceStateChangedCallback mCallback;

        CloudMediaSurfaceControllerImpl(Context context, boolean enableLoop,
                CloudMediaSurfaceStateChangedCallback callback) {
            mCallback = callback;
            Log.d(TAG, "Surface controller created.");
        }

        @Override
        public void onPlayerCreate() {
            sMockSurfaceControllerListener.onPlayerCreate();
            Log.d(TAG, "Player created.");
        }

        @Override
        public void onPlayerRelease() {
            sMockSurfaceControllerListener.onPlayerRelease();
            Log.d(TAG, "Player released.");
        }

        @Override
        public void onSurfaceCreated(int surfaceId, @NonNull Surface surface,
                @NonNull String mediaId) {
            sMockSurfaceControllerListener.onSurfaceCreated(surfaceId, surface, mediaId);

            Log.d(TAG, "Surface prepared: " + surfaceId + ". Surface: " + surface
                    + ". MediaId: " + mediaId);
        }

        @Override
        public void onSurfaceChanged(int surfaceId, int format, int width, int height) {
            sMockSurfaceControllerListener.onSurfaceChanged(surfaceId, format, width, height);
            Log.d(TAG, "Surface changed: " + surfaceId + ". Format: " + format + ". Width: "
                    + width + ". Height: " + height);
        }

        @Override
        public void onSurfaceDestroyed(int surfaceId) {
            sMockSurfaceControllerListener.onSurfaceDestroyed(surfaceId);
            Log.d(TAG, "onSurfaceDestroyed: " + surfaceId);
        }

        @Override
        public void onMediaPlay(int surfaceId) {
            sMockSurfaceControllerListener.onMediaPlay(surfaceId);
            Log.d(TAG, "onMediaPlay: " + surfaceId);
        }

        @Override
        public void onMediaPause(int surfaceId) {
            sMockSurfaceControllerListener.onMediaPause(surfaceId);
            Log.d(TAG, "onMediaPause: " + surfaceId);
        }

        @Override
        public void onMediaSeekTo(int surfaceId, long timestampMillis) {
            sMockSurfaceControllerListener.onMediaSeekTo(surfaceId, timestampMillis);
            Log.d(TAG, "Media seeked: " + surfaceId + ". Timestamp: " + timestampMillis);
        }

        @Override
        public void onConfigChange(@NonNull Bundle config) {
            sMockSurfaceControllerListener.onConfigChange(config);
            Log.d(TAG, "onConfigChange config: " + config);
        }

        @Override
        public void onDestroy() {
            sMockSurfaceControllerListener.onDestroy();
            Log.d(TAG, "Surface controller destroyed.");
        }

        public void sendPlaybackEvent(int surfaceId, int state) {
            mCallback.setPlaybackState(surfaceId, state, null);
        }
    }
}
