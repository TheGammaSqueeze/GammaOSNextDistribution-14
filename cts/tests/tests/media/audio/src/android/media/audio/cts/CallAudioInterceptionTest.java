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

package android.media.audio.cts;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.platform.test.annotations.AppModeFull;
// TODO: b/189472651 uncomment when TM version code is published
//  import android.os.Build;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

// TODO: b/189472651 uncomment when TM version code is published
//  import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Class to exercise AudioManager.getDevicesForAttributes()
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot hold android.permission.CALL_AUDIO_INTERCEPTION")
public class CallAudioInterceptionTest {
    private static final String TAG = CallAudioInterceptionTest.class.getSimpleName();
    private static final int SET_MODE_DELAY_MS = 300;

    private AudioManager mAudioManager;

    /** Test setup */
    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mAudioManager = context.getSystemService(AudioManager.class);
        mAudioManager.setMode(AudioManager.MODE_NORMAL);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.CALL_AUDIO_INTERCEPTION,
                        Manifest.permission.CAPTURE_AUDIO_OUTPUT,
                        Manifest.permission.MODIFY_PHONE_STATE);

        assumeTrue(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        clearAudioserverPermissionCache();
    }

    /** Test teardown */
    @After
    public void tearDown() {
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
        InstrumentationRegistry.getInstrumentation()
              .getUiAutomation()
              .dropShellPermissionIdentity();
        clearAudioserverPermissionCache();
    }

    /**
     * Test AudioManager.isPstnCallAudioInterceptable() API is implemented
     */
    @Test
    public void testIsIsPstnCallAudioInterceptable() throws Exception {
        skipIfCallredirectNotAvailable();

        try {
            boolean result = mAudioManager.isPstnCallAudioInterceptable();
            Log.i(TAG, "isPstnCallAudioInterceptable: " + result);
        } catch (Exception e) {
            fail("isPstnCallAudioInterceptable() exception: " + e);
        }
    }

    /**
     * Test AudioManager.getCallUplinkInjectionAudioTrack() fails when success conditions are
     * not met.
     */
    @Test
    public void testGetCallUplinkInjectionAudioTrackFail() throws Exception {
        skipIfCallredirectNotAvailable();

        try {
            AudioTrack track = mAudioManager.getCallUplinkInjectionAudioTrack(null);
            fail("getCallUplinkInjectionAudioTrack should throw NullPointerException");
        } catch (NullPointerException e) {
        }
        AudioFormat format = new AudioFormat.Builder().setSampleRate(16000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
        setAudioMode(AudioManager.MODE_NORMAL);
        try {
            AudioTrack track = mAudioManager.getCallUplinkInjectionAudioTrack(format);
            fail("getCallUplinkInjectionAudioTrack should throw IllegalStateException "
                    + "in mode NORMAL");
        } catch (IllegalStateException e) {
        }

        if (setAudioMode(AudioManager.MODE_IN_CALL)) {
            try {
                AudioTrack track = mAudioManager.getCallUplinkInjectionAudioTrack(format);
                if (!mAudioManager.isPstnCallAudioInterceptable()) {
                    fail("getCallUplinkInjectionAudioTrack should throw"
                            + "UnsupportedOperationException in mode IN_CALL");
                }
            } catch (UnsupportedOperationException e) {
                if (mAudioManager.isPstnCallAudioInterceptable()) {
                    fail("getCallUplinkInjectionAudioTrack should not throw"
                            + "UnsupportedOperationException in mode IN_CALL");
                }
            }
        } else {
            Log.i(TAG, "Cannot set mode to MODE_IN_CALL");
        }

        if (setAudioMode(AudioManager.MODE_IN_COMMUNICATION)) {
            try {
                format = new AudioFormat.Builder().setSampleRate(96000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                AudioTrack track = mAudioManager.getCallUplinkInjectionAudioTrack(format);
                fail("getCallUplinkInjectionAudioTrack should throw"
                        + "UnsupportedOperationException for 96000Hz");
                format = new AudioFormat.Builder().setSampleRate(16000)
                        .setEncoding(AudioFormat.ENCODING_MP3)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                track = mAudioManager.getCallUplinkInjectionAudioTrack(format);
                fail("getCallUplinkInjectionAudioTrack should throw"
                        + "UnsupportedOperationException for MP3 encoding");
                format = new AudioFormat.Builder().setSampleRate(16000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).build();
                track = mAudioManager.getCallUplinkInjectionAudioTrack(format);
                fail("getCallUplinkInjectionAudioTrack should throw"
                        + "UnsupportedOperationException for 5.1 channels");
            } catch (UnsupportedOperationException e) {
            }
        } else {
            Log.i(TAG, "Cannot set mode to MODE_IN_COMMUNICATION");
        }
    }

    /**
     * Test AudioManager.getCallUplinkInjectionAudioTrack() succeeds when success conditions are
     * met.
     */
    @Test
    public void testGetCallUplinkInjectionAudioTrackSuccess() throws Exception {
        skipIfCallredirectNotAvailable();

        AudioFormat format = new AudioFormat.Builder().setSampleRate(16000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
        final int[] TEST_MODES = new int[] { AudioManager.MODE_IN_CALL,
                AudioManager.MODE_IN_COMMUNICATION,
                AudioManager.MODE_CALL_REDIRECT,
                AudioManager.MODE_COMMUNICATION_REDIRECT
        };

        for (int mode : TEST_MODES) {
            if (setAudioMode(mode)) {
                if ((mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_CALL_REDIRECT)
                        || mAudioManager.isPstnCallAudioInterceptable()) {
                    try {
                        Log.i(TAG, "testing mode: " + mode);
                        AudioTrack track = mAudioManager.getCallUplinkInjectionAudioTrack(format);
                    } catch (Exception e) {
                        fail("getCallUplinkInjectionAudioTrack should not throw " + e);
                    }
                }
            } else {
                Log.i(TAG, "Cannot set mode to: " + mode);
            }
        }
    }

    /**
     * Test AudioManager.getCallDownlinkExtractionAudioRecord() fails when success conditions are
     * not met.
     */
    @Test
    public void testGetCallDownlinkExtractionAudioRecordFail() throws Exception {
        skipIfCallredirectNotAvailable();

        try {
            AudioRecord record = mAudioManager.getCallDownlinkExtractionAudioRecord(null);
            fail("getCallDownlinkExtractionAudioRecord should throw NullPointerException");
        } catch (NullPointerException e) {
        }
        AudioFormat format = new AudioFormat.Builder().setSampleRate(16000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
        setAudioMode(AudioManager.MODE_NORMAL);
        try {
            AudioRecord record = mAudioManager.getCallDownlinkExtractionAudioRecord(format);
            fail("getCallDownlinkExtractionAudioRecord should throw IllegalStateException "
                    + "in mode NORMAL");
        } catch (IllegalStateException e) {
        }

        if (setAudioMode(AudioManager.MODE_IN_CALL)) {
            try {
                AudioRecord record = mAudioManager.getCallDownlinkExtractionAudioRecord(format);
                if (!mAudioManager.isPstnCallAudioInterceptable()) {
                    fail("getCallDownlinkExtractionAudioRecord should throw"
                            + "UnsupportedOperationException in mode IN_CALL");
                }
            } catch (UnsupportedOperationException e) {
                if (mAudioManager.isPstnCallAudioInterceptable()) {
                    fail("getCallDownlinkExtractionAudioRecord should not throw"
                            + " UnsupportedOperationException in mode IN_CALL: " + e);
                }
            }
        } else {
            Log.i(TAG, "Cannot set mode to MODE_IN_CALL");
        }
        if (setAudioMode(AudioManager.MODE_IN_COMMUNICATION)) {
            try {
                format = new AudioFormat.Builder().setSampleRate(96000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
                AudioRecord record = mAudioManager.getCallDownlinkExtractionAudioRecord(format);
                fail("getCallDownlinkExtractionAudioRecord should throw"
                        + "UnsupportedOperationException for 96000Hz");
                format = new AudioFormat.Builder().setSampleRate(16000)
                        .setEncoding(AudioFormat.ENCODING_MP3)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
                record = mAudioManager.getCallDownlinkExtractionAudioRecord(format);
                fail("getCallDownlinkExtractionAudioRecord should throw"
                        + "UnsupportedOperationException for MP3 encoding");
                format = new AudioFormat.Builder().setSampleRate(16000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_5POINT1).build();
                record = mAudioManager.getCallDownlinkExtractionAudioRecord(format);
                fail("getCallDownlinkExtractionAudioRecord should throw"
                        + "UnsupportedOperationException for 5.1 channels");
            } catch (UnsupportedOperationException e) {
            }
        } else {
            Log.i(TAG, "Cannot set mode to MODE_IN_COMMUNICATION");
        }
    }

    /**
     * Test AudioManager.getCallDownlinkExtractionAudioRecord() succeeds when success conditions are
     * met.
     */
    @Test
    public void testGetCallDownlinkExtractionAudioRecordSuccess() throws Exception {
        skipIfCallredirectNotAvailable();

        AudioFormat format = new AudioFormat.Builder().setSampleRate(16000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
        final int[] TEST_MODES = new int[] { AudioManager.MODE_IN_CALL,
                AudioManager.MODE_IN_COMMUNICATION,
                AudioManager.MODE_CALL_REDIRECT,
                AudioManager.MODE_COMMUNICATION_REDIRECT
        };

        for (int mode : TEST_MODES) {
            if (setAudioMode(mode)) {
                if ((mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_CALL_REDIRECT)
                        || mAudioManager.isPstnCallAudioInterceptable()) {
                    try {
                        AudioRecord record =
                                mAudioManager.getCallDownlinkExtractionAudioRecord(format);
                    } catch (Exception e) {
                        fail("getCallDownlinkExtractionAudioRecord should not throw " + e);
                    }
                }
            } else {
                Log.i(TAG, "Cannot set mode to: " + mode);
            }
        }
    }

    private boolean setAudioMode(int mode) {
        mAudioManager.setMode(mode);
        try {
            Thread.sleep(SET_MODE_DELAY_MS);
        } catch (InterruptedException e) {

        }
        return mAudioManager.getMode() == mode;
    }

    private void skipIfCallredirectNotAvailable() {
// TODO: b/189472651 uncomment when TM version code is published
//        assumeTrue(" Call redirection not available",
//                ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
    }

    private void clearAudioserverPermissionCache() {
        try {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "cmd media.audio_policy purge_permission-cache");
        } catch (IOException e) {
            fail("cannot purge audio server permission cache");
        }
    }
}
