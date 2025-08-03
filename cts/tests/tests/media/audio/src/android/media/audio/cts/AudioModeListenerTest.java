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

import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.Executors;



@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class AudioModeListenerTest extends CtsAndroidTestCase {
    private final static String TAG = "AudioModeListenerTest";

    private AudioManager mAudioManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAudioManager = getInstrumentation().getContext().getSystemService(AudioManager.class);
    }

    static class MyOnModeChangedListener implements AudioManager.OnModeChangedListener {

        private final Object mCbLock = new Object();
        @GuardedBy("mCbLock")
        private boolean mCalled;
        @GuardedBy("mCbLock")
        private int mMode;

        private static final int LISTENER_WAIT_TIMEOUT_MS = 3000;
        void reset() {
            synchronized (mCbLock) {
                mCalled = false;
                mMode = -1977; // magic value that doesn't match any mode
            }
        }

        boolean wasCalled() {
            synchronized (mCbLock) {
                return mCalled;
            }
        }

        int waitForModeUpdate() {
            synchronized (mCbLock) {
                long endTimeMillis = SystemClock.uptimeMillis() + LISTENER_WAIT_TIMEOUT_MS;
                long waiTimeMillis = endTimeMillis - SystemClock.uptimeMillis();
                while (!mCalled && waiTimeMillis > 0) {
                    try {
                        mCbLock.wait(waiTimeMillis);
                    } catch (InterruptedException e) {
                    }
                    waiTimeMillis = endTimeMillis - SystemClock.uptimeMillis();
                }
                return mMode;
            }
        }

        int getMode() {
            synchronized (mCbLock) {
                return mMode;
            }
        }

        MyOnModeChangedListener() {
            reset();
        }

        @Override
        public void onModeChanged(int mode) {
            synchronized (mCbLock) {
                mCalled = true;
                mMode = mode;
                mCbLock.notifyAll();
            }
        }
    }

    @AppModeFull(reason = "Instant apps don't have MODIFY_AUDIO_SETTINGS")
    public void testModeListener() throws Exception {
        if (!isValidPlatform("testModeListener")) return;

        MyOnModeChangedListener listener = new MyOnModeChangedListener();

        try {
            mAudioManager.addOnModeChangedListener(null, listener);
            fail("addOnModeChangedListener should fail with null executor");
        } catch (Exception e) {
        }

        try {
            mAudioManager.addOnModeChangedListener(
                    Executors.newSingleThreadExecutor(), null);
            fail("addOnModeChangedListener should fail with null listener");
        } catch (Exception e) {
        }

        try {
            mAudioManager.removeOnModeChangedListener(null);
            fail("removeOnModeChangedListener should fail with null listener");
        } catch (Exception e) {
        }

        try {
            mAudioManager.addOnModeChangedListener(
                Executors.newSingleThreadExecutor(), listener);
        } catch (Exception e) {
            fail("addOnModeChangedListener failed with exception: " + e);
        }

        try {
            mAudioManager.addOnModeChangedListener(
                Executors.newSingleThreadExecutor(), listener);
            fail("addOnCommunicationDeviceChangedListener succeeded for same listener");
        } catch (Exception e) {
        }

        int originalMode = mAudioManager.getMode();
        int testMode = (originalMode == AudioManager.MODE_NORMAL)
                ? AudioManager.MODE_RINGTONE : AudioManager.MODE_NORMAL;

        mAudioManager.setMode(testMode);
        int dispatchedMode = listener.waitForModeUpdate();
        assertTrue("listener wasn't called", listener.wasCalled());
        assertEquals("Mode not updated correctly", testMode, dispatchedMode);

        listener.reset();

        if (originalMode == AudioManager.MODE_NORMAL) {
            mAudioManager.setMode(originalMode);

            dispatchedMode = listener.waitForModeUpdate();
            assertTrue("listener wasn't called for mode restore", listener.wasCalled());
            assertEquals("Mode not updated correctly for mode restore",
                    originalMode, dispatchedMode);
        }

        try {
            mAudioManager.removeOnModeChangedListener(listener);
        } catch (Exception e) {
            fail("removeOnModeChangedListener failed with exception: " + e);
        }
    }

    private boolean isValidPlatform(String testName) {
        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
                ||  pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
                || !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.i(TAG,"Skipping test " + testName
                    + " : device has no audio output or is a TV or does not support telephony");
            return false;
        }
        return true;
    }
}
