/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.internal.annotations.GuardedBy;

import java.util.List;
import java.util.concurrent.Executors;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class AudioCommunicationDeviceTest extends CtsAndroidTestCase {
    private final static String TAG = "AudioCommunicationDeviceTest";

    private AudioManager mAudioManager;
    private int mOriginalMode;
    private MyOnModeChangedListener mModelistener;

    private MyOnCommunicationDeviceChangedListener mCommunicationDeviceListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAudioManager = getInstrumentation().getContext().getSystemService(AudioManager.class);

        mOriginalMode = mAudioManager.getMode();
        mModelistener = new MyOnModeChangedListener(mAudioManager);
        mAudioManager.addOnModeChangedListener(
                Executors.newSingleThreadExecutor(), mModelistener);

        mCommunicationDeviceListener = new MyOnCommunicationDeviceChangedListener(mAudioManager);
        mAudioManager.addOnCommunicationDeviceChangedListener(
                Executors.newSingleThreadExecutor(), mCommunicationDeviceListener);
    }

    @Override
    protected void tearDown() throws Exception {
        mAudioManager.clearCommunicationDevice();
        // This will time out if the communication device is already the default one but as there
        // is no way to tell what the default communication device should be it is the only way to
        // safely return to the default device before next test
        mCommunicationDeviceListener.waitForDeviceUpdate();
        mAudioManager.removeOnCommunicationDeviceChangedListener(mCommunicationDeviceListener);

        mModelistener.setAudioMode(mOriginalMode);
        mAudioManager.removeOnModeChangedListener(mModelistener);

        super.tearDown();
    }

    public void testGetCommunicationDevice() {
        if (!isValidPlatform("testSetValidCommunicationDevice")) return;

        AudioDeviceInfo commDevice = null;
        try {
            commDevice = mAudioManager.getCommunicationDevice();
        } catch (Exception e) {
            fail("getCommunicationDevice failed with exception: " + e);
        }
        if (commDevice == null) {
            fail("platform has no default communication device");
        }
    }

    public void testClearCommunicationDevice() {
        if (!isValidPlatform("testSetValidCommunicationDevice")) return;

        try {
            mAudioManager.clearCommunicationDevice();
        } catch (Exception e) {
            fail("clearCommunicationDevice failed with exception: " + e);
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_SETTINGS")
    public void testSetCommunicationDeviceSuccessModeOwner() {
        if (!isValidPlatform("testSetValidCommunicationDevice")) return;

        mModelistener.setAudioMode(AudioManager.MODE_IN_COMMUNICATION);

        doSetCommunicationDeviceSuccessTest();
    }

    public void testSetCommunicationDeviceSuccessPrivileged() {
        if (!isValidPlatform("testSetValidCommunicationDevice")) return;

        try {
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.MODIFY_PHONE_STATE);

            doSetCommunicationDeviceSuccessTest();
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private void doSetCommunicationDeviceSuccessTest() {
        List<AudioDeviceInfo> devices = mAudioManager.getAvailableCommunicationDevices();
        for (AudioDeviceInfo device : devices) {
            AudioDeviceInfo commDevice = null;
            try {
                mCommunicationDeviceListener.setCommunicationDevice(device);
                try {
                    commDevice = mAudioManager.getCommunicationDevice();
                    assertNotNull("Platform has no default communication device", commDevice);
                } catch (Exception e) {
                    fail("getCommunicationDevice failed with exception: " + e);
                }
                if (commDevice.getType() != device.getType()) {
                    fail("setCommunicationDevice failed, expected device: "
                            + device.getType() + " but got: " + commDevice.getType());
                }
            } catch (Exception e) {
                fail("setCommunicationDevice failed with exception: " + e);
            }
        }
    }

    public void testSetCommunicationDeviceDeniedNotModeOwnerNotPrivileged() {
        final long kDeviceListenerIdleWaitTimeoutMs = 10000;

        if (!isValidPlatform("testSetValidCommunicationDevice")) return;

        AudioDeviceInfo originalCommDevice = mAudioManager.getCommunicationDevice();
        assertNotNull("Platform has no default communication device", originalCommDevice);

        List<AudioDeviceInfo> devices = mAudioManager.getAvailableCommunicationDevices();
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == originalCommDevice.getType()) {
                continue;
            }
            MyOnCommunicationDeviceChangedListener listener =
                    new MyOnCommunicationDeviceChangedListener(mAudioManager);
            try {
                mAudioManager.addOnCommunicationDeviceChangedListener(
                        Executors.newSingleThreadExecutor(), listener);

                listener.setCommunicationDevice(device);
                // The following behaviors are acceptable:
                // - U: the communication device changes: log a warning to
                // track migration to post U QPR1 behavior
                // - U QPR1 and after:
                //    - the communication device does not change OR
                //    - the communication device changes temporarily and comes back to
                //    the initial device after the inactivity grace period.
                AudioDeviceInfo listenerDevice = listener.getDevice();
                if (listenerDevice != null
                        && listenerDevice.getType() != originalCommDevice.getType()) {
                    listener.waitForDeviceUpdate(
                            originalCommDevice, kDeviceListenerIdleWaitTimeoutMs);
                    listenerDevice = listener.getDevice();
                    if (listenerDevice == null
                            || listenerDevice.getType() != originalCommDevice.getType()) {
                        Log.w(TAG, "setCommunicationDevice not denied, expected device: "
                                + originalCommDevice.getType()
                                + " but got: " + (listenerDevice == null
                                    ? AudioDeviceInfo.TYPE_UNKNOWN : listenerDevice.getType()));
                    }
                }
            } catch (Exception e) {
                fail("AudioManager call failed with exception: " + e);
            } finally {
                mAudioManager.removeOnCommunicationDeviceChangedListener(listener);
            }
        }
    }

    public void testSetInvalidCommunicationDeviceFail() {
        if (!isValidPlatform("testSetInvalidCommunicationDevice")) return;

        AudioDeviceInfo[] alldevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        List<AudioDeviceInfo> validDevices = mAudioManager.getAvailableCommunicationDevices();

        for (AudioDeviceInfo device : alldevices) {
            if (validDevices.contains(device)) {
                continue;
            }
            try {
                mAudioManager.setCommunicationDevice(device);
                fail("setCommunicationDevice should fail for device: " + device.getType());
            } catch (Exception e) {
            }
        }
    }

    public void testNoSourceInCommunicationDevices() {
        if (!isValidPlatform("testNoSourceInCommunicationDevices")) return;

        List<AudioDeviceInfo> devices = mAudioManager.getAvailableCommunicationDevices();
        for (AudioDeviceInfo device : devices) {
            assertFalse(device.isSource());
        }
    }

    static class MyOnCommunicationDeviceChangedListener implements
            AudioManager.OnCommunicationDeviceChangedListener {

        private static final long LISTENER_WAIT_TIMEOUT_MS = 3000;

        private final Object mCbLock = new Object();
        @GuardedBy("mCbLock")
        private boolean mCalled;
        @GuardedBy("mCbLock")
        private AudioDeviceInfo mDevice;

        private final AudioManager mAudioManager;

        void reset() {
            synchronized (mCbLock) {
                mCalled = false;
                mDevice = null;
            }
        }

        AudioDeviceInfo waitForDeviceUpdate() {
            return waitForDeviceUpdateTo(null, LISTENER_WAIT_TIMEOUT_MS);
        }

        AudioDeviceInfo waitForDeviceUpdate(AudioDeviceInfo device) {
            return waitForDeviceUpdateTo(device, LISTENER_WAIT_TIMEOUT_MS);
        }

        AudioDeviceInfo waitForDeviceUpdate(AudioDeviceInfo device, long timeoutMs) {
            return waitForDeviceUpdateTo(device, timeoutMs);
        }

        // Waits for the communication device to be the one passed as argument.
        // If the device passed is null, it will wait unconditionally for the listener to be called
        private AudioDeviceInfo waitForDeviceUpdateTo(AudioDeviceInfo device,
                long timeoutMs) {
            synchronized (mCbLock) {
                long endTimeMillis = SystemClock.uptimeMillis() + timeoutMs;
                long waiTimeMillis = endTimeMillis - SystemClock.uptimeMillis();
                while (((device != null && !device.equals(mAudioManager.getCommunicationDevice()))
                            || !mCalled) && (waiTimeMillis > 0)) {
                    try {
                        mCbLock.wait(waiTimeMillis);
                    } catch (InterruptedException e) {
                    }
                    waiTimeMillis = endTimeMillis - SystemClock.uptimeMillis();
                }
                return mDevice;
            }
        }

        void setCommunicationDevice(AudioDeviceInfo device) {
            mAudioManager.setCommunicationDevice(device);
            waitForDeviceUpdateTo(device, LISTENER_WAIT_TIMEOUT_MS);
        }

        AudioDeviceInfo getDevice() {
            synchronized (mCbLock) {
                return mDevice;
            }
        }

        MyOnCommunicationDeviceChangedListener(AudioManager audioManager) {
            mAudioManager = audioManager;
            reset();
        }

        @Override
        public void onCommunicationDeviceChanged(AudioDeviceInfo device) {
            synchronized (mCbLock) {
                mCalled = true;
                mDevice = device;
                mCbLock.notifyAll();
            }
        }
    }

    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_SETTINGS")
    public void testCommunicationDeviceListener() {
        if (!isValidPlatform("testCommunicationDeviceListener")) return;

        mModelistener.setAudioMode(AudioManager.MODE_IN_COMMUNICATION);

        MyOnCommunicationDeviceChangedListener listener =
                new MyOnCommunicationDeviceChangedListener(mAudioManager);

        try {
            mAudioManager.addOnCommunicationDeviceChangedListener(null, listener);
            fail("addOnCommunicationDeviceChangedListener should fail with null executor");
        } catch (Exception e) {
        }

        try {
            mAudioManager.addOnCommunicationDeviceChangedListener(
                    Executors.newSingleThreadExecutor(), null);
            fail("addOnCommunicationDeviceChangedListener should fail with null listener");
        } catch (Exception e) {
        }

        try {
            mAudioManager.removeOnCommunicationDeviceChangedListener(null);
            fail("removeOnCommunicationDeviceChangedListener should fail with null listener");
        } catch (Exception e) {
        }

        try {
            mAudioManager.addOnCommunicationDeviceChangedListener(
                Executors.newSingleThreadExecutor(), listener);
        } catch (Exception e) {
            fail("addOnCommunicationDeviceChangedListener failed with exception: "
                    + e);
        }

        try {
            mAudioManager.addOnCommunicationDeviceChangedListener(
                Executors.newSingleThreadExecutor(), listener);
            fail("addOnCommunicationDeviceChangedListener succeeded for same listener");
        } catch (Exception e) {
        }

        AudioDeviceInfo originalDevice = mAudioManager.getCommunicationDevice();
        assertNotNull("Platform has no default communication device", originalDevice);

        AudioDeviceInfo requestedDevice = null;
        List<AudioDeviceInfo> devices = mAudioManager.getAvailableCommunicationDevices();

        for (AudioDeviceInfo device : devices) {
            if (device.getType() != originalDevice.getType()) {
                requestedDevice = device;
                break;
            }
        }
        if (requestedDevice == null) {
            Log.i(TAG,"Skipping end of testCommunicationDeviceListener test,"
                    +" no valid decice to test");
            return;
        }

        mAudioManager.setCommunicationDevice(requestedDevice);
        AudioDeviceInfo listenerDevice = listener.waitForDeviceUpdate();
        if (listenerDevice == null || listenerDevice.getType() != requestedDevice.getType()) {
            fail("listener and setter device mismatch, expected device: "
                    + requestedDevice.getType() + " but got: "
                    + ((listenerDevice == null)
                        ? AudioDeviceInfo.TYPE_UNKNOWN : listenerDevice.getType()));
        }
        AudioDeviceInfo getterDevice = mAudioManager.getCommunicationDevice();
        assertNotNull("Platform has no default communication device", getterDevice);

        if (getterDevice.getType() != listenerDevice.getType()) {
            fail("listener and getter device mismatch, expected device: "
                    + listenerDevice.getType() + " but got: "
                    + getterDevice.getType());
        }

        listener.reset();

        mAudioManager.setCommunicationDevice(originalDevice);

        listenerDevice = listener.waitForDeviceUpdate();
        assertNotNull("Platform has no default communication device", listenerDevice);

        if (listenerDevice.getType() != originalDevice.getType()) {
            fail("communication device listener failed on clear, expected device: "
                    + originalDevice.getType() + " but got: " + listenerDevice.getType());
        }

        try {
            mAudioManager.removeOnCommunicationDeviceChangedListener(listener);
        } catch (Exception e) {
            fail("removeOnCommunicationDeviceChangedListener failed with exception: "
                    + e);
        }
    }

    static class MyOnModeChangedListener implements AudioManager.OnModeChangedListener {

        private final Object mCbLock = new Object();
        @GuardedBy("mCbLock")
        private boolean mCalled;
        @GuardedBy("mCbLock")
        private int mMode;
        private final AudioManager mAudioManager;

        private static final int LISTENER_WAIT_TIMEOUT_MS = 3000;
        void reset() {
            synchronized (mCbLock) {
                mCalled = false;
                mMode = AudioManager.MODE_INVALID;
            }
        }

        private int waitForModeUpdateTo(int mode) {
            synchronized (mCbLock) {
                long endTimeMillis = SystemClock.uptimeMillis() + LISTENER_WAIT_TIMEOUT_MS;
                long waiTimeMillis = endTimeMillis - SystemClock.uptimeMillis();
                while ((mAudioManager.getMode() != mode || !mCalled) && waiTimeMillis > 0) {
                    try {
                        mCbLock.wait(waiTimeMillis);
                    } catch (InterruptedException e) {
                    }
                    waiTimeMillis = endTimeMillis - SystemClock.uptimeMillis();
                }
                return mMode;
            }
        }

        void setAudioMode(int mode) {
            mAudioManager.setMode(mode);
            waitForModeUpdateTo(mode);
        }

        int getMode() {
            synchronized (mCbLock) {
                return mMode;
            }
        }

        MyOnModeChangedListener(AudioManager audioManager) {
            mAudioManager = audioManager;
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
