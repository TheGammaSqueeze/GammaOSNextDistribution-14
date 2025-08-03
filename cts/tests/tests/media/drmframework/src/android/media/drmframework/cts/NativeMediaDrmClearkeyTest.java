/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;

import android.content.pm.PackageManager;
import android.media.MediaDrm;
import android.media.cts.ConnectionStatus;
import android.media.cts.IConnectionStatus;
import android.net.Uri;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.util.Log;
import android.view.Surface;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Tests MediaDrm NDK APIs. ClearKey system uses a subset of NDK APIs,
 * this test only tests the APIs that are supported by ClearKey system.
 */
@AppModeFull(reason = "TODO: evaluate and port to instant")
public class NativeMediaDrmClearkeyTest extends MediaPlayerDrmTestBase {
    private static final String TAG = NativeMediaDrmClearkeyTest.class.getSimpleName();

    private static final int CONNECTION_RETRIES = 10;
    private static final int VIDEO_WIDTH_CENC = 1280;
    private static final int VIDEO_HEIGHT_CENC = 720;
    private static final String ISO_BMFF_VIDEO_MIME_TYPE = "video/avc";
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final Uri CENC_AUDIO_URL =
            Uri.fromFile(new File(MEDIA_DIR + "llama_aac_audio.mp4"));
    private static final Uri CENC_VIDEO_URL =
            Uri.fromFile(new File(MEDIA_DIR + "llama_h264_main_720p_8000.mp4"));

    private static final int UUID_BYTE_SIZE = 16;
    private static final UUID COMMON_PSSH_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);
    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0xe2719d58a985b3c9L, 0x781ab030af78d30eL);
    private static final UUID BAD_SCHEME_UUID =
            new UUID(0xffffffffffffffffL, 0xffffffffffffffffL);

    static {
        try {
            System.loadLibrary("mediadrm_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "NativeMediaDrmClearkeyTest: Error loading JNI library");
            e.printStackTrace();
        }
        try {
            System.loadLibrary("mediandk");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "NativeMediaDrmClearkeyTest: Error loading JNI library");
            e.printStackTrace();
        }
    }

    public static class PlaybackParams {
        public Surface surface;
        public String mimeType;
        public String audioUrl;
        public String videoUrl;
    }

    protected void setUp() throws Exception {
        super.setUp();
        if (false == deviceHasMediaDrm()) {
            tearDown();
        }
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private boolean watchHasNoClearkeySupport() {
        if (!MediaDrm.isCryptoSchemeSupported(CLEARKEY_SCHEME_UUID)) {
            if (isWatchDevice()) {
                return true;
            } else {
                throw new Error("Crypto scheme is not supported");
            }
        }
        return false;
    }

    private boolean isWatchDevice() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private boolean deviceHasMediaDrm() {
        // ClearKey is introduced after KitKat.
        if (ApiLevelUtil.isAtMost(android.os.Build.VERSION_CODES.KITKAT)) {
            return false;
        }
        return true;
    }

    private static final byte[] uuidByteArray(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[UUID_BYTE_SIZE]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    @Presubmit
    public void testIsCryptoSchemeSupported() throws Exception {
        if (watchHasNoClearkeySupport()) {
            return;
        }

        assertTrue(isCryptoSchemeSupportedNative(uuidByteArray(COMMON_PSSH_SCHEME_UUID)));
        assertTrue(isCryptoSchemeSupportedNative(uuidByteArray(CLEARKEY_SCHEME_UUID)));
    }

    @Presubmit
    public void testIsCryptoSchemeNotSupported() throws Exception {
        assertFalse(isCryptoSchemeSupportedNative(uuidByteArray(BAD_SCHEME_UUID)));
    }

    @Presubmit
    public void testPssh() throws Exception {
        // The test uses a canned PSSH that contains the common box UUID.
        assertTrue(testPsshNative(uuidByteArray(COMMON_PSSH_SCHEME_UUID),
                CENC_VIDEO_URL.toString()));
    }

    @Presubmit
    public void testQueryKeyStatus() throws Exception {
        if (watchHasNoClearkeySupport()) {
            return;
        }

        assertTrue(testQueryKeyStatusNative(uuidByteArray(CLEARKEY_SCHEME_UUID)));
    }

    @Presubmit
    public void testFindSessionId() throws Exception {
        if (watchHasNoClearkeySupport()) {
            return;
        }

        assertTrue(testFindSessionIdNative(uuidByteArray(CLEARKEY_SCHEME_UUID)));
    }

    @Presubmit
    public void testGetPropertyString() throws Exception {
        if (watchHasNoClearkeySupport()) {
            return;
        }

        StringBuffer value = new StringBuffer();
        testGetPropertyStringNative(uuidByteArray(CLEARKEY_SCHEME_UUID), "description", value);
        assertEquals("ClearKey CDM", value.toString());

        value.delete(0, value.length());
        testGetPropertyStringNative(uuidByteArray(CLEARKEY_SCHEME_UUID), "description", value);
        assertEquals("ClearKey CDM", value.toString());
    }

    @Presubmit
    public void testPropertyByteArray() throws Exception {
        if (watchHasNoClearkeySupport()) {
            return;
        }

        assertTrue(testPropertyByteArrayNative(uuidByteArray(CLEARKEY_SCHEME_UUID)));
    }

    @Presubmit
    public void testUnknownPropertyString() throws Exception {
        StringBuffer value = new StringBuffer();

        try {
            testGetPropertyStringNative(uuidByteArray(CLEARKEY_SCHEME_UUID),
                    "unknown-property", value);
            fail("Should have thrown an exception");
        } catch (RuntimeException e) {
            Log.e(TAG, "testUnknownPropertyString error = '" + e.getMessage() + "'");
            assertThat(e.getMessage(), containsString("get property string returns"));
        }

        value.delete(0, value.length());
        try {
            testGetPropertyStringNative(uuidByteArray(CLEARKEY_SCHEME_UUID),
                    "unknown-property", value);
            fail("Should have thrown an exception");
        } catch (RuntimeException e) {
            Log.e(TAG, "testUnknownPropertyString error = '" + e.getMessage() + "'");
            assertThat(e.getMessage(), containsString("get property string returns"));
        }
    }

    /**
     * Tests native clear key system playback.
     */
    private void testClearKeyPlayback(
            UUID drmSchemeUuid, String mimeType, /*String initDataType,*/ Uri audioUrl, Uri videoUrl,
            int videoWidth, int videoHeight) throws Exception {

        if (isWatchDevice()) {
            return;
        }

        if (!isCryptoSchemeSupportedNative(uuidByteArray(drmSchemeUuid))) {
            throw new Error("Crypto scheme is not supported.");
        }

        IConnectionStatus connectionStatus = new ConnectionStatus(mContext);
        if (!connectionStatus.isAvailable()) {
            throw new Error("Network is not available, reason: " +
                    connectionStatus.getNotConnectedReason());
        }

        // If device is not online, recheck the status a few times.
        int retries = 0;
        while (!connectionStatus.isConnected()) {
            if (retries++ >= CONNECTION_RETRIES) {
                throw new Error("Device is not online, reason: " +
                        connectionStatus.getNotConnectedReason());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        connectionStatus.testConnection(videoUrl);

        if (!MediaUtils.checkCodecsForPath(mContext, videoUrl.toString())) {
            Log.i(TAG, "Device does not support " +
                  videoWidth + "x" + videoHeight + " resolution for " + mimeType);
            return;  // skip
        }

        PlaybackParams params = new PlaybackParams();
        params.surface = mActivity.getSurfaceHolder().getSurface();
        params.mimeType = mimeType;
        params.audioUrl = audioUrl.toString();
        params.videoUrl = videoUrl.toString();

        if (!testClearKeyPlaybackNative(
            uuidByteArray(drmSchemeUuid), params)) {
            Log.e(TAG, "Fails play back using native media drm APIs.");
        }
        params.surface.release();
    }

    private static native boolean isCryptoSchemeSupportedNative(final byte[] uuid);

    private static native boolean testClearKeyPlaybackNative(final byte[] uuid,
            PlaybackParams params);

    private static native boolean testFindSessionIdNative(final byte[] uuid);

    private static native boolean testGetPropertyStringNative(final byte[] uuid,
            final String name, StringBuffer value);

    private static native boolean testPropertyByteArrayNative(final byte[] uuid);

    private static native boolean testPsshNative(final byte[] uuid, final String videoUrl);

    private static native boolean testQueryKeyStatusNative(final byte[] uuid);

    private static native boolean testGetKeyRequestNative(final byte[] uuid,
            PlaybackParams params);

    public void testClearKeyPlaybackCenc() throws Exception {
        testClearKeyPlayback(
                COMMON_PSSH_SCHEME_UUID,
                ISO_BMFF_VIDEO_MIME_TYPE,
                CENC_AUDIO_URL,
                CENC_VIDEO_URL,
                VIDEO_WIDTH_CENC, VIDEO_HEIGHT_CENC);
    }

    @FlakyTest(bugId = 173646795)
    @Presubmit
    public void testClearKeyPlaybackCenc2() throws Exception {
        testClearKeyPlayback(
                CLEARKEY_SCHEME_UUID,
                ISO_BMFF_VIDEO_MIME_TYPE,
                CENC_AUDIO_URL,
                CENC_VIDEO_URL,
                VIDEO_WIDTH_CENC, VIDEO_HEIGHT_CENC);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testClearKeyGetKeyRequest() throws Exception {
        PlaybackParams params = new PlaybackParams();
        params.surface = mActivity.getSurfaceHolder().getSurface();
        params.mimeType = ISO_BMFF_VIDEO_MIME_TYPE;
        params.audioUrl = CENC_AUDIO_URL.toString();
        params.videoUrl = CENC_VIDEO_URL.toString();
        boolean status = testGetKeyRequestNative(
                uuidByteArray(CLEARKEY_SCHEME_UUID),
                params);
        assertTrue(status);
        params.surface.release();
    }
}

