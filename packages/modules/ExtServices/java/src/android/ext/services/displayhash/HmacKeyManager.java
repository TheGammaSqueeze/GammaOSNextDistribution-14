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

import android.graphics.Rect;
import android.os.Build;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@RequiresApi(Build.VERSION_CODES.S)
class HmacKeyManager {
    private static final String TAG = DisplayHashingServiceImpl.TAG;
    private static final String ALGORITHM = "HmacSHA256";

    HmacKeyManager() { }

    byte[] generateHmac(byte[] salt, long timestamp, Rect bounds, int hashAlgorithm,
            byte[] imageHash) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(salt, ALGORITHM);
            mac.init(keySpec);

            final VerificationObject object = new VerificationObject(timestamp, bounds,
                    hashAlgorithm, imageHash);
            mac.update(object.convertToBytes());

            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.e(TAG, "Failed to generate HMAC", e);
            return null;
        }
    }

    boolean verifyHmac(byte[] salt, long timestamp, Rect bounds,
            int hashAlgorithm, byte[] imageHash, byte[] hmac) {
        if (hmac == null || hmac.length != 32) {
            return false;
        }

        byte[] calculatedHmac = generateHmac(salt, timestamp, bounds, hashAlgorithm,
                imageHash);
        return Arrays.equals(hmac, calculatedHmac);
    }

    private static class VerificationObject {
        private final long mTimestamp;
        private final Rect mBounds;
        private final int mHashAlgorithm;
        private final byte[] mImageHash;

        VerificationObject(long timestamp, Rect bounds, int hashAlgorithm, byte[] imageHash) {
            mTimestamp = timestamp;
            mBounds = bounds;
            mHashAlgorithm = hashAlgorithm;
            mImageHash = imageHash;
        }

        byte[] convertToBytes() {
            Parcel parcel = Parcel.obtain();
            parcel.writeLong(mTimestamp);
            parcel.writeParcelable(mBounds, 0);
            parcel.writeInt(mHashAlgorithm);
            parcel.writeByteArray(mImageHash);
            byte[] bytes = parcel.marshall();
            parcel.recycle();
            return bytes;
        }
    }
}
