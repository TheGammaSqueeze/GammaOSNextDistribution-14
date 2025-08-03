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

package android.media.audio.cts;

import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM;
import static android.media.AudioAttributes.CONTENT_TYPE_MUSIC;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioManager.CSD_WARNING_MOMENTARY_EXPOSURE;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.cts.AudioHelper.hasAudioSilentProperty;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.IVolumeController;
import android.media.SoundPool;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.NonMainlineTest;

import java.util.concurrent.atomic.AtomicInteger;

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class SoundDoseHelperTest extends CtsAndroidTestCase {
    private static final String TAG = "SoundDoseHelperTest";

    private static final int TEST_TIMING_TOLERANCE_MS = 100;
    private static final int TEST_TIMEOUT_SOUNDPOOL_LOAD_MS = 3000;
    private static final int TEST_MAX_TIME_EXPOSURE_WARNING_MS = 2500;

    private static final float DEFAULT_RS2_VALUE = 100.f;
    private static final float MIN_RS2_VALUE = 80.f;
    private static final float[] CUSTOM_VALID_RS2 = {80.f, 90.f, 100.f};
    private static final float[] CUSTOM_INVALID_RS2 = {79.9f, 100.1f};

    private static final float CSD_VALUE_100PERC = 1.0f;

    private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(USAGE_MEDIA)
            .setContentType(CONTENT_TYPE_MUSIC)
            .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_SYSTEM)
            .build();

    private final AtomicInteger mDisplayedCsdWarningTimes = new AtomicInteger(0);

    private Context mContext;

    private final IVolumeController mVolumeController = new IVolumeController.Stub() {
        @Override
        public void displaySafeVolumeWarning(int flags) throws RemoteException {
            // do nothing
        }

        @Override
        public void volumeChanged(int streamType, int flags) throws RemoteException {
            // do nothing
        }

        @Override
        public void masterMuteChanged(int flags) throws RemoteException {
            // do nothing
        }

        @Override
        public void setLayoutDirection(int layoutDirection) throws RemoteException {
            // do nothing
        }

        @Override
        public void dismiss() throws RemoteException {
            // do nothing
        }

        @Override
        public void setA11yMode(int mode) throws RemoteException {
            // do nothing
        }

        @Override
        public void displayCsdWarning(int warning, int displayDurationMs) throws RemoteException {
            if (warning == CSD_WARNING_MOMENTARY_EXPOSURE) {
                mDisplayedCsdWarningTimes.incrementAndGet();
            }
        }
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED,
                Manifest.permission.STATUS_BAR_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    public void testGetSetRs2Value() throws Exception {
        final AudioManager am = new AudioManager(mContext);
        if (!platformSupportsSoundDose("testGetSetRs2Value", am)) {
            return;
        }

        float prevRS2Value = am.getRs2Value();

        for (float rs2Value : CUSTOM_INVALID_RS2) {
            am.setRs2Value(rs2Value);
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);  // waiting for RS2 to propagate
            assertEquals(DEFAULT_RS2_VALUE, am.getRs2Value());
        }

        for (float rs2Value : CUSTOM_VALID_RS2) {
            am.setRs2Value(rs2Value);
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);  // waiting for RS2 to propagate
            assertEquals(rs2Value, am.getRs2Value());
        }

        // Restore the RS2 value
        am.setRs2Value(prevRS2Value);
    }

    public void testGetSetCsd() throws Exception {
        final AudioManager am = new AudioManager(mContext);
        if (!platformSupportsSoundDose("testGetSetCsd", am)) {
            return;
        }

        am.setCsd(CSD_VALUE_100PERC);
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);  // waiting for CSD to propagate
        assertEquals(CSD_VALUE_100PERC, am.getCsd());
    }

    public void testFrameworkMomentaryExposure() throws Exception {
        final AudioManager am = new AudioManager(mContext);
        if (!platformSupportsSoundDose("testFrameworkMomentaryExposure", am)) {
            return;
        }
        if (hasAudioSilentProperty()) {
            Log.w(TAG, "Device has ro.audio.silent set, skipping testFrameworkMomentaryExposure");
            return;
        }

        am.forceComputeCsdOnAllDevices(/* computeCsdOnAllDevices= */true);
        am.forceUseFrameworkMel(/* useFrameworkMel= */true);
        am.setCsd(-1.f);  // reset csd timeouts
        am.setRs2Value(MIN_RS2_VALUE);  // lower the RS2 as much as possible

        IVolumeController sysUiVolumeController = null;
        int prevVolume = -1;
        try {
            sysUiVolumeController = am.getVolumeController();
            prevVolume = am.getStreamVolume(STREAM_MUSIC);
            am.setVolumeController(mVolumeController);

            playLoudSound(am);

            Thread.sleep(TEST_MAX_TIME_EXPOSURE_WARNING_MS);
            assertTrue("Exposure warning should have been triggered once!",
                    mDisplayedCsdWarningTimes.get() > 0);
        } finally {
            if (prevVolume != -1) {
                // restore the previous volume
                am.setStreamVolume(STREAM_MUSIC, prevVolume, /* flags= */0);
            }
            if (sysUiVolumeController != null) {
                // restore SysUI volume controller
                am.setVolumeController(sysUiVolumeController);
            }
            am.setRs2Value(DEFAULT_RS2_VALUE);  // restore RS2 to default
            am.forceComputeCsdOnAllDevices(/* computeCsdOnAllDevices= */false);
            am.forceUseFrameworkMel(/* useFrameworkMel= */false);
        }
    }

    private void playLoudSound(AudioManager am) throws Exception {
        int maxVolume = am.getStreamMaxVolume(STREAM_MUSIC);
        am.setStreamVolume(STREAM_MUSIC, maxVolume, /* flags= */0);

        final Object loadLock = new Object();
        final SoundPool soundpool = new SoundPool.Builder()
                .setAudioAttributes(ATTRIBUTES)
                .setMaxStreams(1)
                .build();
        // load a sound and play it once load completion is reported
        soundpool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            assertEquals("Load completion error", 0 /*success expected*/, status);
            synchronized (loadLock) {
                loadLock.notify();
            }
        });
        final int loadId = soundpool.load(mContext, R.raw.sine1320hz5sec, 1/*priority*/);
        synchronized (loadLock) {
            loadLock.wait(TEST_TIMEOUT_SOUNDPOOL_LOAD_MS);
        }

        int res = soundpool.play(loadId, 1.0f /*leftVolume*/, 1.0f /*rightVolume*/, 1 /*priority*/,
                0 /*loop*/, 1.0f/*rate*/);
        assertTrue("Error playing sound through SoundPool", res > 0);
    }

    private boolean platformSupportsSoundDose(String testName, AudioManager am) {
        if (!mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL, skipping test " + testName);
            return false;
        }

        if (!am.isCsdEnabled()) {
            Log.w(TAG, "Device does not have the sound dose feature enabled, skipping test "
                    + testName);
            return false;
        }

        return true;
    }
}
