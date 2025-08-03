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

package android.ext.services.displayhash;

import static android.view.displayhash.DisplayHashResultCallback.DISPLAY_HASH_ERROR_INVALID_HASH_ALGORITHM;
import static android.view.displayhash.DisplayHashResultCallback.DISPLAY_HASH_ERROR_UNKNOWN;

import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.service.displayhash.DisplayHashParams;
import android.service.displayhash.DisplayHashingService;
import android.util.ArrayMap;
import android.util.Log;
import android.view.displayhash.DisplayHash;
import android.view.displayhash.DisplayHashResultCallback;
import android.view.displayhash.VerifiedDisplayHash;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.common.annotations.VisibleForTesting;

import java.util.Map;

/**
 * The implementation service for {@link DisplayHashingService}
 */
@RequiresApi(Build.VERSION_CODES.S)
public class DisplayHashingServiceImpl extends DisplayHashingService {
    static final String TAG = "DisplayHashingService";

    private static final int INTERVAL_BETWEEN_REQUESTS_MILLIS = 10000;

    private ImageHashManager mImageHashManager = new ImageHashManager();
    private final HmacKeyManager mHmacKeyManager = new HmacKeyManager();
    private final ArrayMap<String, DisplayHashParams> mDisplayHashParams = new ArrayMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        mDisplayHashParams.put("PHASH", new DisplayHashParams.Builder()
                .setBufferSize(32, 32)
                .setGrayscaleBuffer(true).build());
    }

    @Override
    public void onGenerateDisplayHash(@NonNull byte[] salt,
            @NonNull HardwareBuffer buffer, @NonNull Rect bounds,
            @NonNull String hashAlgorithm, @NonNull DisplayHashResultCallback callback) {
        if (salt == null) {
            Log.w(TAG, "Failed to generate display hash: salt was null");
            callback.onDisplayHashError(DISPLAY_HASH_ERROR_UNKNOWN);
            return;
        }

        int hashAlgorithmIndex = getIndexForHashAlgorithm(hashAlgorithm);
        if (hashAlgorithmIndex < 0) {
            Log.w(TAG, "Failed to generate display hash: invalid hash request");
            callback.onDisplayHashError(DISPLAY_HASH_ERROR_INVALID_HASH_ALGORITHM);
            return;
        }

        if (buffer == null) {
            Log.w(TAG, "Failed to generate display hash: null buffer");
            callback.onDisplayHashError(DISPLAY_HASH_ERROR_UNKNOWN);
            return;
        }

        long timestamp = System.currentTimeMillis();
        byte[] imageHash = mImageHashManager.generateHash(buffer, hashAlgorithm);
        if (imageHash == null) {
            Log.w(TAG, "Failed to generate display hash: failed to create image hash");
            callback.onDisplayHashError(DISPLAY_HASH_ERROR_UNKNOWN);
            return;
        }

        byte[] hmac = mHmacKeyManager.generateHmac(salt, timestamp, bounds, hashAlgorithmIndex,
                imageHash);
        callback.onDisplayHashResult(
                new DisplayHash(timestamp, bounds, hashAlgorithm, imageHash, hmac));
    }

    @Override
    public VerifiedDisplayHash onVerifyDisplayHash(@NonNull byte[] salt,
            @NonNull DisplayHash displayHash) {
        if (displayHash == null || salt == null) {
            Log.w(TAG, "Failed to verify display hash: invalid token or salt");
            return null;
        }

        boolean verified = mHmacKeyManager.verifyHmac(salt,
                displayHash.getTimeMillis(), displayHash.getBoundsInWindow(),
                getIndexForHashAlgorithm(displayHash.getHashAlgorithm()),
                displayHash.getImageHash(), displayHash.getHmac());

        if (verified) {
            return new VerifiedDisplayHash(displayHash.getTimeMillis(),
                    displayHash.getBoundsInWindow(), displayHash.getHashAlgorithm(),
                    displayHash.getImageHash());
        } else {
            return null;
        }
    }

    @Override
    public int onGetIntervalBetweenRequestsMillis() {
        return INTERVAL_BETWEEN_REQUESTS_MILLIS;
    }

    private int getIndexForHashAlgorithm(String hashAlgorithm) {
        return mDisplayHashParams.indexOfKey(hashAlgorithm);
    }

    @VisibleForTesting
    public void setImageHashManager(ImageHashManager imageHashManager) {
        mImageHashManager = imageHashManager;
    }

    @NonNull
    @Override
    public Map<String, DisplayHashParams> onGetDisplayHashAlgorithms() {
        return mDisplayHashParams;
    }
}
