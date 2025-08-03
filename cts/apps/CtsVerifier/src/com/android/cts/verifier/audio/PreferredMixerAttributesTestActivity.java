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

package com.android.cts.verifier.audio;

import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioMixerAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PreferredMixerAttributesTestActivity is used to test if the Android device supports setting
 * preferred mixer attributes for USB devices.
 */
public class PreferredMixerAttributesTestActivity extends PassFailButtons.Activity {
    private static final String TAG = "PreferredMixerAttributesTestActivity";

    private static final long TIMEOUT_MS = 1000;

    private AudioManager mAudioManager;
    private List<AudioDeviceInfo> mUsbDevices = new ArrayList<>();

    private HandlerThread mPreferredMixerAttrTestThread;
    private Handler mPreferredMixerAttrTestHandler;

    private TextView mUsbStatusTextView;
    private TextView mTestResultTextView;
    private TextView mFailureMsgTextView;

    private TestAudioDeviceCallback mAudioDeviceCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_preferred_mixer_attributes);

        mAudioManager = getSystemService(AudioManager.class);

        mUsbStatusTextView = (TextView) findViewById(R.id.usbDeviceConnectionStatus);
        mTestResultTextView = (TextView) findViewById(R.id.preferredMixerAttributesTestStatus);
        mFailureMsgTextView = (TextView) findViewById(R.id.preferredMixerAttributesTestFailure);

        setInfoResources(R.string.audio_preferred_mixer_attributes_test,
                R.string.audio_preferred_mixer_attributes_test_info, -1);
        setPassFailButtonClickListeners();

        mAudioDeviceCallback = new TestAudioDeviceCallback();
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();
        mAudioManager.registerAudioDeviceCallback(
                mAudioDeviceCallback, mPreferredMixerAttrTestHandler);
        detectUsbDeviceConnectionAndRunTest();
    }

    @Override
    public void onPause() {
        mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        stopBackgroundThread();
        super.onPause();
    }

    private void startBackgroundThread() {
        mPreferredMixerAttrTestThread = new HandlerThread("PreferredMixerAttrBackground");
        mPreferredMixerAttrTestThread.start();
        mPreferredMixerAttrTestHandler = new Handler(mPreferredMixerAttrTestThread.getLooper());
    }

    private void stopBackgroundThread() {
        mPreferredMixerAttrTestThread.quitSafely();
        try {
            mPreferredMixerAttrTestThread.join();
            mPreferredMixerAttrTestThread = null;
            mPreferredMixerAttrTestHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void detectUsbDeviceConnectionAndRunTest() {
        if (!detectUSBDevice()) {
            updateUI(R.string.audio_preferred_mixer_attributes_test_connect_usb_device,
                    R.string.empty,
                    R.string.empty,
                    false /*passButtonEnabled*/);
            return;
        }
        updateUI(R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                R.string.audio_preferred_mixer_attributes_test_running,
                R.string.empty,
                false /*passButtonEnabled*/);
        mPreferredMixerAttrTestHandler.post(new Runnable() {
            @Override
            public void run() {
                displayTestResult();
            }
        });
    }

    private void displayTestResult() {
        for (AudioDeviceInfo device : mUsbDevices) {
            List<AudioMixerAttributes> supportedMixerAttrs =
                    mAudioManager.getSupportedMixerAttributes(device);
            if (supportedMixerAttrs.isEmpty()) {
                updateUI(R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                        R.string.result_failure,
                        R.string.audio_can_not_set_preferred_mixer_attributes,
                        false /*passButtonEnabled*/);
                return;
            }
            AudioAttributes attr = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).build();
            MyPreferredMixerAttributesListener listener =
                    new MyPreferredMixerAttributesListener(attr, device.getId());
            mAudioManager.addOnPreferredMixerAttributesChangedListener(
                    Executors.newSingleThreadExecutor(), listener);
            for (AudioMixerAttributes mixerAttr : supportedMixerAttrs) {
                listener.reset();
                if (!mAudioManager.setPreferredMixerAttributes(attr, device, mixerAttr)) {
                    testComplete(
                            R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                            R.string.result_failure,
                            R.string.audio_set_preferred_mixer_attributes_failed,
                            false /*passButtonEnabled*/,
                            listener);
                    return;
                }
                listener.await(TIMEOUT_MS);
                if (!listener.isPreferredMixerAttributesChanged()) {
                    testComplete(
                            R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                            R.string.result_failure,
                            R.string.audio_no_callback_for_preferred_mixer_attributes_changed,
                            false /*passButtonEnabled*/,
                            listener);
                    return;
                }
                if (!mixerAttr.equals(mAudioManager.getPreferredMixerAttributes(attr, device))) {
                    testComplete(
                            R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                            R.string.result_failure,
                            R.string.audio_get_preferred_mixer_attributes_not_equal,
                            false /*passButtonEnabled*/,
                            listener);
                    return;
                }
                listener.reset();
                if (!mAudioManager.clearPreferredMixerAttributes(attr, device)) {
                    testComplete(
                            R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                            R.string.result_failure,
                            R.string.audio_clear_preferred_mixer_attributes_failed,
                            false /*passButtonEnabled*/,
                            listener);
                    return;
                }
                listener.await(TIMEOUT_MS);
                if (!listener.isPreferredMixerAttributesChanged()) {
                    testComplete(
                            R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                            R.string.result_failure,
                            R.string.audio_no_callback_for_preferred_mixer_attributes_changed,
                            false /*passButtonEnabled*/,
                            listener);
                    return;
                }
                if (mAudioManager.getPreferredMixerAttributes(attr, device) != null) {
                    testComplete(
                            R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                            R.string.result_failure,
                            R.string.audio_get_preferred_mixer_attributes_should_be_null,
                            false /*passButtonEnabled*/,
                            listener);
                    return;
                }
            }
            mAudioManager.removeOnPreferredMixerAttributesChangedListener(listener);
        }
        testComplete(R.string.audio_preferred_mixer_attributes_test_usb_device_connected,
                R.string.result_success,
                R.string.empty,
                true /*passButtonEnabled*/,
                null /*listener*/);
    }

    private void testComplete(int usbStatusResId, int testStatusResId, int failureMsgResId,
            boolean passButtonEnabled, MyPreferredMixerAttributesListener listener) {
        if (listener != null) {
            mAudioManager.removeOnPreferredMixerAttributesChangedListener(listener);
        }
        updateUI(usbStatusResId, testStatusResId, failureMsgResId, passButtonEnabled);
    }

    private void updateUI(int usbStatusResId, int testStatusResId, int failureMsgResId,
            boolean passButtonEnabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUsbStatusTextView.setText(usbStatusResId);
                mTestResultTextView.setText(testStatusResId);
                mFailureMsgTextView.setText(failureMsgResId);
                getPassButton().setEnabled(passButtonEnabled);
            }
        });
    }

    private boolean detectUSBDevice() {
        mUsbDevices.clear();
        AudioDeviceInfo[] deviceInfos = mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo deviceInfo : deviceInfos) {
            if (deviceInfo.isSink() && (deviceInfo.getType() == AudioDeviceInfo.TYPE_USB_DEVICE
                    || deviceInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET)) {
                mUsbDevices.add(deviceInfo);
            }
        }
        return !mUsbDevices.isEmpty();
    }

    private class TestAudioDeviceCallback extends AudioDeviceCallback {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            detectUsbDeviceConnectionAndRunTest();
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            detectUsbDeviceConnectionAndRunTest();
        }
    }

    private final class MyPreferredMixerAttributesListener
            implements AudioManager.OnPreferredMixerAttributesChangedListener {
        private final AudioAttributes mAttr;
        private final int mDeviceId;

        private CountDownLatch mCountDownLatch;
        private AtomicBoolean mIsCalled = new AtomicBoolean(false);

        MyPreferredMixerAttributesListener(AudioAttributes attr, int deviceId) {
            mAttr = attr;
            mDeviceId = deviceId;
            reset();
        }

        @Override
        public void onPreferredMixerAttributesChanged(AudioAttributes attributes,
                AudioDeviceInfo device, AudioMixerAttributes mixerAttributes) {
            if (device.getId() == mDeviceId && mAttr.equals(attributes)) {
                mIsCalled.set(true);
            }
            mCountDownLatch.countDown();
        }

        public void reset() {
            mIsCalled.set(false);
            mCountDownLatch = new CountDownLatch(1);
        }

        void await(long timeoutMs) {
            try {
                mCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
        }

        public boolean isPreferredMixerAttributesChanged() {
            return mIsCalled.get();
        }
    }
}
