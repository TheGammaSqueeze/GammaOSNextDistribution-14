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

package android.media.drmframework.cts;

import android.media.MediaCodec;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaDrm;
import android.media.MediaDrm.MediaDrmStateException;
import android.media.MediaFormat;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.filters.SmallTest;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Clearkey DRM MediaCodec.
 *
 * In particular, check various API edge cases.
 */
@Presubmit
@SmallTest
@RequiresDevice
@AppModeFull(reason = "Instant apps cannot access the SD card")
public class MediaDrmCodecTest extends AndroidTestCase {
    private static final String TAG = "MediaDrmCodecTest";

    // parameters for the video encoder
                                                            // H.264 Advanced Video Coding
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 2000000;            // 2Mbps
    private static final int FRAME_RATE = 15;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    /**
     * Creates a MediaFormat with the basic set of values.
     */
    private static MediaFormat createMediaFormat() {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        return format;
    }

    private static boolean supportsCodec(String mimeType, boolean encoder) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (encoder != info.isEncoder()) {
                continue;
            }

            for (String type : info.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);

    /**
     * Tests:
     * <br> queueSecureInputBuffer() with erroneous input throws CryptoException
     * <br> getInputBuffer() after the failed queueSecureInputBuffer() succeeds.
     */
    public void testCryptoError() throws Exception {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID);
        byte[] sessionId = drm.openSession();
        MediaCrypto crypto = new MediaCrypto(CLEARKEY_SCHEME_UUID, new byte[0]);
        MediaCodec codec = MediaCodec.createDecoderByType(MIME_TYPE);

        try {
            crypto.setMediaDrmSession(sessionId);

            MediaCodec.CryptoInfo cryptoInfo = new MediaCodec.CryptoInfo();
            MediaFormat format = createMediaFormat();

            codec.configure(format, null, crypto, 0);
            codec.start();
            int index = codec.dequeueInputBuffer(-1);
            assertTrue(index >= 0);
            ByteBuffer buffer = codec.getInputBuffer(index);
            cryptoInfo.set(
                    1,
                    new int[] { 0 },
                    new int[] { buffer.capacity() },
                    new byte[16],
                    new byte[16],
                    // Trying to decrypt encrypted data in unencrypted mode
                    MediaCodec.CRYPTO_MODE_UNENCRYPTED);
            try {
                codec.queueSecureInputBuffer(index, 0, cryptoInfo, 0, 0);
                fail("queueSecureInputBuffer should fail when trying to decrypt " +
                        "encrypted data in unencrypted mode.");
            } catch (MediaCodec.CryptoException e) {
                // expected
            }
            buffer = codec.getInputBuffer(index);
            codec.stop();
        } finally {
            codec.release();
            crypto.release();
            drm.closeSession(sessionId);
        }
    }

    /*
     * Simulate ERROR_LOST_STATE error during decryption, expected
     * result is MediaCodec.CryptoException with errorCode == ERROR_LOST_STATE
     */
    public void testCryptoErrorLostSessionState() throws Exception {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID);
        drm.setPropertyString("drmErrorTest", "lostState");

        byte[] sessionId = drm.openSession();
        MediaCrypto crypto = new MediaCrypto(CLEARKEY_SCHEME_UUID, new byte[0]);
        MediaCodec codec = MediaCodec.createDecoderByType(MIME_TYPE);

        try {
            crypto.setMediaDrmSession(sessionId);

            MediaCodec.CryptoInfo cryptoInfo = new MediaCodec.CryptoInfo();
            MediaFormat format = createMediaFormat();

            codec.configure(format, null, crypto, 0);
            codec.start();
            int index = codec.dequeueInputBuffer(-1);
            assertTrue(index >= 0);
            ByteBuffer buffer = codec.getInputBuffer(index);
            cryptoInfo.set(
                    1,
                    new int[] { 0 },
                    new int[] { buffer.capacity() },
                            new byte[16],
                    new byte[16],
                    MediaCodec.CRYPTO_MODE_AES_CTR);
            try {
                codec.queueSecureInputBuffer(index, 0, cryptoInfo, 0, 0);
                fail("queueSecureInputBuffer should fail when trying to decrypt " +
                        "after session lost state error.");
            } catch (MediaCodec.CryptoException e) {
                if (e.getErrorCode() != MediaCodec.CryptoException.ERROR_LOST_STATE) {
                    fail("expected MediaCodec.CryptoException.ERROR_LOST_STATE: " +
                            e.getErrorCode() + ": " + e.getMessage());
                }
                // received expected lost state exception
            }
            buffer = codec.getInputBuffer(index);
            codec.stop();
        } finally {
            codec.release();
            crypto.release();
            try {
                drm.closeSession(sessionId);
            } catch (MediaDrmStateException e) {
                // expected since session lost state
            }
        }
    }
}
