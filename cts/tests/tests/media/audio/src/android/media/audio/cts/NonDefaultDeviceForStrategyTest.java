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

package android.media.audio.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class NonDefaultDeviceForStrategyTest {
    private static final String TAG = NonDefaultDeviceForStrategyTest.class.getSimpleName();

    private static final AudioAttributes MEDIA_ATTR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).build();
    private static final AudioAttributes COMMUNICATION_ATTR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build();
    private static final int TEST_TIMING_TOLERANCE_MS = 100;

    /**
     * Device types that are communication devices but may not be picked by default engine.
     */
    private static final HashSet<Integer> EXCLUDED_COMMUNICATION_DEVICE_TYPES = new HashSet<>() {{
            add(AudioDeviceInfo.TYPE_HDMI);
        }};

    private AudioManager mAudioManager;
    private List<AudioProductStrategy> mStrategies;
    private AudioProductStrategy mStrategyForMedia;
    private AudioProductStrategy mStrategyForPhone;

    /** Test setup */
    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
        mAudioManager = context.getSystemService(AudioManager.class);
        mStrategies = mAudioManager.getAudioProductStrategies();

        mStrategyForMedia = null;
        for (AudioProductStrategy strategy : mStrategies) {
            if (strategy.supportsAudioAttributes(MEDIA_ATTR)) {
                mStrategyForMedia = strategy;
                break;
            }
        }

        mStrategyForPhone = null;
        for (AudioProductStrategy strategy : mStrategies) {
            if (strategy.supportsAudioAttributes(COMMUNICATION_ATTR)) {
                mStrategyForPhone = strategy;
                break;
            }
        }
    }

    /** Test teardown */
    @After
    public void tearDown() throws Exception {
        List<AudioDeviceAttributes> devices =
                mAudioManager.getNonDefaultDevicesForStrategy(mStrategyForMedia);
        for (AudioDeviceAttributes device : devices) {
            mAudioManager.removeDeviceAsNonDefaultForStrategy(mStrategyForMedia, device);
        }

        devices = mAudioManager.getNonDefaultDevicesForStrategy(mStrategyForPhone);
        for (AudioDeviceAttributes device : devices) {
            mAudioManager.removeDeviceAsNonDefaultForStrategy(mStrategyForPhone, device);
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testNullability() throws Exception {
        AudioDeviceAttributes speakerDevice = new AudioDeviceAttributes(
                AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "");

        // setDeviceAsNonDefaultForStrategy
        assertThrows("setDeviceAsNonDefaultForStrategy should throw on null strategy",
                NullPointerException.class,
                () -> mAudioManager.setDeviceAsNonDefaultForStrategy(null, speakerDevice));
        assertThrows("setDeviceAsNonDefaultForStrategy should throw on null device",
                NullPointerException.class,
                () -> mAudioManager.setDeviceAsNonDefaultForStrategy(mStrategyForMedia, null));

        // removeDeviceAsNonDefaultForStrategy
        assertThrows("removeDeviceAsNonDefaultForStrategy should throw on null strategy",
                NullPointerException.class,
                () -> mAudioManager.removeDeviceAsNonDefaultForStrategy(null, speakerDevice));
        assertThrows("removeDeviceAsNonDefaultForStrategy should throw on null device",
                NullPointerException.class,
                () -> mAudioManager.removeDeviceAsNonDefaultForStrategy(mStrategyForMedia, null));

        // getNonDefaultDevicesForStrategy
        assertThrows("getNonDefaultDevicesForStrategy should throw on null strategy",
                NullPointerException.class,
                () -> mAudioManager.getNonDefaultDevicesForStrategy(null));
    }

    @Test
    public void testInvalidStrategy() throws Exception {
        assertFalse("No product strategies", mStrategies.isEmpty());
        int maxId = Integer.MIN_VALUE;
        for (AudioProductStrategy strategy : mStrategies) {
            maxId = Math.max(maxId, strategy.getId());
        }
        AudioProductStrategy badStrategy =
                AudioProductStrategy.createInvalidAudioProductStrategy(maxId + 1);
        AudioDeviceAttributes device = new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT,
                AudioDeviceInfo.TYPE_AUX_LINE, "");
        assertFalse("setDeviceAsNonDefaultForStrategy should fail on invalid strategy",
                mAudioManager.setDeviceAsNonDefaultForStrategy(badStrategy, device));
        assertFalse("removeDeviceAsNonDefaultForStrategy should fail on invalid strategy",
                mAudioManager.removeDeviceAsNonDefaultForStrategy(badStrategy, device));

        List<AudioDeviceAttributes> devices =
                mAudioManager.getNonDefaultDevicesForStrategy(badStrategy);
        assertEquals("getNonDefaultDeviceForStrategy should return empty list on invalid strategy",
                devices.size(), 0);
    }

    @Test
    public void testSetNonDefaultDevice() throws Exception {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        assumeTrue("Skip test: no output devices", devices.length > 0);
        AudioDeviceAttributes device = new AudioDeviceAttributes(devices[0]);

        List<AudioDeviceAttributes> nonDefaultDevices =
                mAudioManager.getNonDefaultDevicesForStrategy(mStrategyForMedia);
        assertEquals("getNonDefaultDeviceForStrategy should return empty list",
                nonDefaultDevices.size(), 0);

        assertTrue("Error calling setDeviceAsNonDefaultForStrategy",
                mAudioManager.setDeviceAsNonDefaultForStrategy(mStrategyForMedia, device));

        // verify getter result
        nonDefaultDevices = mAudioManager.getNonDefaultDevicesForStrategy(mStrategyForMedia);
        assertEquals("Expected exactly 1 non-default device", nonDefaultDevices.size(), 1);
        assertTrue("getNonDefaultDevicesForStrategy returns incorrect non-default device",
                nonDefaultDevices.get(0).equalTypeAddress(device));

        assertTrue("Error calling removeDeviceAsNonDefaultForStrategy",
                mAudioManager.removeDeviceAsNonDefaultForStrategy(mStrategyForMedia, device));

        nonDefaultDevices = mAudioManager.getNonDefaultDevicesForStrategy(mStrategyForMedia);
        assertEquals("Expected no non-default device", nonDefaultDevices.size(), 0);
    }

    @Test
    public void testSetNonDefaultDeviceRouting() throws Exception {
        List<AudioDeviceInfo> availableDevices = mAudioManager.getAvailableCommunicationDevices();
        availableDevices.removeIf(
                device -> EXCLUDED_COMMUNICATION_DEVICE_TYPES.contains(device.getType()));
        assumeTrue("Skip test: less than 2 available communication devices",
                availableDevices.size() > 1);

        List<AudioDeviceAttributes> devices =
                mAudioManager.getDevicesForAttributes(COMMUNICATION_ATTR);
        assertTrue("No device found for phone strategy", devices.size() > 0);

        AudioDeviceAttributes device = devices.get(0);
        assertTrue("Error calling setDeviceAsNonDefaultForStrategy",
                mAudioManager.setDeviceAsNonDefaultForStrategy(mStrategyForPhone, device));

        devices = mAudioManager.getDevicesForAttributes(COMMUNICATION_ATTR);
        assertTrue("No device found for phone strategy", devices.size() > 0);

        assertFalse("Phone strategy should route to a different device",
                devices.get(0).equalTypeAddress(device));

        assertTrue("Error calling removeDeviceAsNonDefaultForStrategy",
                mAudioManager.removeDeviceAsNonDefaultForStrategy(mStrategyForPhone, device));
    }

    @Test
    public void testNonDefaultDeviceListener() throws Exception {
        List<AudioDeviceInfo> availableDevices = mAudioManager.getAvailableCommunicationDevices();
        availableDevices.removeIf(
                device -> EXCLUDED_COMMUNICATION_DEVICE_TYPES.contains(device.getType()));
        assumeTrue("Skip test: less than 2 available communication devices",
                availableDevices.size() > 1);

        List<AudioDeviceAttributes> devices =
                mAudioManager.getDevicesForAttributes(COMMUNICATION_ATTR);
        assertTrue("No device found for phone strategy", devices.size() > 0);

        AudioDeviceAttributes device = devices.get(0);

        NonDefDevListener listener = new NonDefDevListener();
        mAudioManager.addOnNonDefaultDevicesForStrategyChangedListener(
                Executors.newSingleThreadExecutor(), listener);

        // Verify setDeviceAsNonDefaultForStrategy triggers callback
        assertTrue("Error calling setDeviceAsNonDefaultForStrategy",
                mAudioManager.setDeviceAsNonDefaultForStrategy(mStrategyForPhone, device));
        listener.await(TEST_TIMING_TOLERANCE_MS);
        // verify listener results
        assertTrue("Non-default device listener wasn't called for set", listener.mCalled);
        assertEquals("Listener called with wrong strategy ID for set",
                mStrategyForPhone.getId(), listener.mReceivedStrategy.getId());
        assertEquals("Non-default devices list received is wrong size",
                1, listener.mReceivedDevices.size());
        assertTrue("Listener was called with wrong device for set",
                listener.mReceivedDevices.get(0).equalTypeAddress(device));

        // Verify removeDeviceAsNonDefaultForStrategy triggers callback
        listener.reset();
        assertTrue("Error removing non-default device for phone",
                mAudioManager.removeDeviceAsNonDefaultForStrategy(mStrategyForPhone, device));
        listener.await(TEST_TIMING_TOLERANCE_MS);
        // verify listener results
        assertTrue("Non-default device listener wasn't called for remove", listener.mCalled);
        assertEquals("Listener called with wrong strategy ID for remove",
                mStrategyForPhone.getId(), listener.mReceivedStrategy.getId());
        assertTrue("Listener was called with wrong device for remove",
                listener.mReceivedDevices.isEmpty());

        // Verify removing listener works
        listener.reset();
        mAudioManager.removeOnNonDefaultDevicesForStrategyChangedListener(listener);
        assertTrue("Error calling setDeviceAsNonDefaultForStrategy",
                mAudioManager.setPreferredDeviceForStrategy(mStrategyForPhone, device));
        listener.await(TEST_TIMING_TOLERANCE_MS);
        assertFalse("Preferred device listener failed to be removed", listener.mCalled);
    }

    static class NonDefDevListener implements
            AudioManager.OnNonDefaultDevicesForStrategyChangedListener {
        boolean mCalled;
        private AudioProductStrategy mReceivedStrategy;
        private List<AudioDeviceAttributes> mReceivedDevices;
        private CountDownLatch mCountDownLatch;

        NonDefDevListener() {
            reset();
        }

        @Override
        public void onNonDefaultDevicesForStrategyChanged(AudioProductStrategy strategy,
                                                          List<AudioDeviceAttributes> devices) {
            mReceivedStrategy = strategy;
            mReceivedDevices = devices;
            mCalled = true;
            mCountDownLatch.countDown();

        }

        void await(long timeoutMs) {
            try {
                mCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
        }

        void reset() {
            mReceivedStrategy = null;
            mReceivedDevices = null;
            mCountDownLatch = new CountDownLatch(1);
            mCalled = false;
        }
    }
}
