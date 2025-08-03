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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test suite derived from GTS DevicesForAttributesTest.java
 */
@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
@FlakyTest(bugId = 326206728)
public class DevicesForAttributesTest {
    private static final String TAG = DevicesForAttributesTest.class.getSimpleName();

    private static final AudioAttributes MEDIA_ATTR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).build();
    private static final AudioAttributes COMMUNICATION_ATTR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build();
    private static final int TEST_TIMING_TOLERANCE_MS = 300;

    private AudioManager mAudioManager;
    private AudioPolicy mAudioPolicy;

    /** Test setup */
    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
        mAudioManager = context.getSystemService(AudioManager.class);
    }

    /** Test teardown */
    @After
    public void tearDown() {
        if (mAudioPolicy != null) {
            mAudioManager.unregisterAudioPolicy(mAudioPolicy);
            mAudioPolicy = null;
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testNullability() throws Exception {
        Log.i(TAG, "testNullAttributes");
        assertThrows("getDevicesForAttributes must throw on null attributes",
                NullPointerException.class,
                () -> mAudioManager.getDevicesForAttributes(null));

        DevicesForAttributesListener listener = new DevicesForAttributesListener();
        assertThrows("addOnDevicesForAttributesChangedListener must throw on null listener",
                NullPointerException.class,
                () -> mAudioManager.addOnDevicesForAttributesChangedListener(
                        MEDIA_ATTR, Executors.newSingleThreadExecutor(), null));
        assertThrows("addOnDevicesForAttributesChangedListener must throw on null executor",
                NullPointerException.class,
                () -> mAudioManager.addOnDevicesForAttributesChangedListener(
                        MEDIA_ATTR, null, listener));
        assertThrows("addOnDevicesForAttributesChangedListener must throw on null attributes",
                NullPointerException.class,
                () -> mAudioManager.addOnDevicesForAttributesChangedListener(
                        null, Executors.newSingleThreadExecutor(), listener));
    }

    @Test
    public void testListenerRegistration() {
        DevicesForAttributesListener listener = new DevicesForAttributesListener();
        List<AudioDeviceAttributes> devices;

        mAudioManager.addOnDevicesForAttributesChangedListener(
                MEDIA_ATTR, Executors.newSingleThreadExecutor(), listener);
        listener.await(TEST_TIMING_TOLERANCE_MS);

        // Listener should not be called on register
        assertFalse("listener should not be called on register", listener.mCalled);

        mAudioManager.removeOnDevicesForAttributesChangedListener(listener);
    }

    @Test
    public void testListenerWithAudioPolicy() {
        if (isAutomotive()) {
            Log.i(TAG, "skipping test: automotive platform");
            return;
        }

        DevicesForAttributesListener listener = new DevicesForAttributesListener();
        List<AudioDeviceAttributes> devices;

        mAudioManager.addOnDevicesForAttributesChangedListener(
                MEDIA_ATTR, Executors.newSingleThreadExecutor(), listener);

        // route MEDIA_ATTR with audio policy
        final AudioMix mediaMix = makeMixFromAttr(MEDIA_ATTR);
        final AudioPolicy.Builder policyBuilder =
                new AudioPolicy.Builder(InstrumentationRegistry.getTargetContext());
        policyBuilder.addMix(mediaMix);
        mAudioPolicy = policyBuilder.build();
        assertNotNull(mAudioPolicy);
        assertEquals(AudioManager.SUCCESS, mAudioManager.registerAudioPolicy(mAudioPolicy));
        final AudioRecord recorder = mAudioPolicy.createAudioRecordSink(mediaMix);
        assertNotNull(recorder);
        recorder.startRecording();
        listener.await(TEST_TIMING_TOLERANCE_MS);

        assertTrue("Listener should be called for MEDIA_ATTR", listener.mCalled);
        devices = listener.mDevicesForAttributes.get(MEDIA_ATTR);
        assertNotNull("Routing for MEDIA_ATTR is not reported", devices);
        assertTrue("Routed device list is empty", devices.size() > 0);
        listener.reset();

        mAudioManager.removeOnDevicesForAttributesChangedListener(listener);

        mAudioManager.unregisterAudioPolicy(mAudioPolicy);
        mAudioPolicy = null;

        listener.await(TEST_TIMING_TOLERANCE_MS);
        assertFalse("Listener should not be called after being removed", listener.mCalled);
    }

    @Test
    public void testListenerWithMultipleAttributes() {
        if (isAutomotive()) {
            Log.i(TAG, "skipping test: automotive platform");
            return;
        }

        final AudioAttributes[] attributes = { MEDIA_ATTR, COMMUNICATION_ATTR };
        final DevicesForAttributesListener[] listeners =
                new DevicesForAttributesListener[attributes.length];

        for (int i = 0; i < attributes.length; ++i) {
            listeners[i] = new DevicesForAttributesListener();
            mAudioManager.addOnDevicesForAttributesChangedListener(
                    attributes[i], Executors.newSingleThreadExecutor(), listeners[i]);

            listeners[i].await(TEST_TIMING_TOLERANCE_MS);
            listeners[i].reset();
        }

        // test routing change for each attribute
        final AudioMix[] mixes = new AudioMix[attributes.length];
        final AudioPolicy.Builder policyBuilder =
                new AudioPolicy.Builder(InstrumentationRegistry.getTargetContext());
        for (int i = 0; i < attributes.length; ++i) {
            mixes[i] = makeMixFromAttr(attributes[i]);
            policyBuilder.addMix(mixes[i]);
        }

        mAudioPolicy = policyBuilder.build();
        assertNotNull(mAudioPolicy);
        assertEquals(AudioManager.SUCCESS, mAudioManager.registerAudioPolicy(mAudioPolicy));

        final AudioRecord[] recorders = new AudioRecord[attributes.length];

        for (int i = 0; i < attributes.length; ++i) {
            recorders[i] = mAudioPolicy.createAudioRecordSink(mixes[i]);
            assertNotNull(recorders[i]);
            recorders[i].startRecording();

            // listener should be called only for attributes[i]
            listeners[i].await(TEST_TIMING_TOLERANCE_MS);
            assertTrue("Routing should be updated", listeners[i].mCalled);
            List<AudioDeviceAttributes> devices =
                    listeners[i].mDevicesForAttributes.get(attributes[i]);
            assertNotNull("Routing is not reported", devices);
            assertTrue("Routed device list is empty", devices.size() > 0);
            for (int j = 0; j < attributes.length; ++j) {
                if (i == j) {
                    continue;
                }
                assertFalse("Wrong listener is called", listeners[j].mCalled);
            }
            listeners[i].reset();

            recorders[i].stop();

            // listener should be called only for attributes[i]
            listeners[i].await(TEST_TIMING_TOLERANCE_MS);
            assertTrue("Routing should be updated", listeners[i].mCalled);
            devices = listeners[i].mDevicesForAttributes.get(attributes[i]);
            assertNotNull("Routing is not reported", devices);
            assertTrue("Routed device list is empty", devices.size() > 0);
            listeners[i].reset();
        }

        for (int i = 0; i < attributes.length; ++i) {
            mAudioManager.removeOnDevicesForAttributesChangedListener(listeners[i]);
        }
    }

    static class DevicesForAttributesListener implements
            AudioManager.OnDevicesForAttributesChangedListener {
        boolean mCalled;
        private ConcurrentHashMap<AudioAttributes, List<AudioDeviceAttributes>>
                mDevicesForAttributes = new ConcurrentHashMap<>();
        private CountDownLatch mCountDownLatch;

        DevicesForAttributesListener() {
            reset();
        }

        @Override
        public void onDevicesForAttributesChanged(AudioAttributes attributes,
                                                  List<AudioDeviceAttributes> devices) {
            mDevicesForAttributes.put(attributes, devices);
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
            mDevicesForAttributes.clear();
            mCalled = false;
            mCountDownLatch = new CountDownLatch(1);
        }
    }

    private AudioMix makeMixFromAttr(AudioAttributes attr) {
        return new AudioMix.Builder(
                new AudioMixingRule.Builder()
                        .addRule(attr, AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE)
                        .build())
                .setFormat(new AudioFormat.Builder()
                        .setSampleRate(16000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build();
    }

    private boolean isAutomotive() {
        Context context = InstrumentationRegistry.getTargetContext();
        PackageManager packageManager = context.getPackageManager();
        return (packageManager != null
                && packageManager.hasSystemFeature(packageManager.FEATURE_AUTOMOTIVE));
    }
}
