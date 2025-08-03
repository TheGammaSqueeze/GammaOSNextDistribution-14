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

package com.android.cts.verifier;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class AudioTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void RingerModeTest() throws Exception {
        excludeFeatures("android.software.leanback", "android.hardware.type.automotive");

        runTest(".audio.RingerModeActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.8.2.1/C-1-1,C-1-2,C-1-3,C-1-4,C-2-1")
    public void AnalogHeadsetAudioTest() throws Exception {
        runTest(".audio.AnalogHeadsetAudioActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.media.audiofx.AcousticEchoCanceler#isAvailable",
                "android.media.audiofx.AcousticEchoCanceler#create",
                "android.media.audiofx.AcousticEchoCanceler#release",
                "android.media.audiofx.AcousticEchoCanceler#getEnabled"
            })
    public void AudioAECTest() throws Exception {
        requireFeatures("android.hardware.microphone", "android.hardware.audio.output");

        runTest(".audio.AudioAEC");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.media.AudioDescriptor#getStandard",
                "android.media.AudioDescriptor#getDescriptor"
            })
    public void AudioDescriptorTest() throws Exception {
        runTest(".audio.AudioDescriptorActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    // @NonApiTest(exemptionReasons = METRIC,
    // justification = "this test is currently informational only")
    public void AudioFrequencyLineTest() throws Exception {
        requireFeatures("android.hardware.microphone", "android.hardware.audio.output");

        runTest(".audio.AudioFrequencyLineActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    // @NonApiTest(exemptionReasons = METRIC,
    // justification = "this test is currently informational only")
    public void AudioFrequencyMicTest() throws Exception {
        requireFeatures(
                "android.hardware.microphone",
                "android.hardware.audio.output",
                "android.hardware.usb.host");

        runTest(".audio.AudioFrequencyMicActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    // @NonApiTest(exemptionReasons = METRIC,
    // justification = "this test is currently informational only")
    public void AudioFrequencySpeakerTest() throws Exception {
        requireFeatures("android.hardware.audio.output", "android.hardware.usb.host");

        runTest(".audio.AudioFrequencySpeakerActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "5.11/C-1-1,C-1-2,C-1-3,C-1-4,C-1-5")
    public void AudioFrequencyUnprocessedTest() throws Exception {
        requireFeatures("android.hardware.microphone", "android.hardware.usb.host");

        runTest(".audio.AudioFrequencyUnprocessedActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    // @NonApiTest(exemptionReasons = METRIC,
    // justification = "this test is currently informational only")
    public void AudioFrequencyVoiceRecognitionTest() throws Exception {
        requireFeatures("android.hardware.microphone", "android.hardware.usb.host");

        runTest(".audio.AudioFrequencyVoiceRecognitionActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "5.6/C-3-2")
    public void AudioInColdStartLatencyTest() throws Exception {
        excludeFeatures("android.hardware.type.watch", "android.hardware.type.television");

        runTest(".audio.AudioInColdStartLatencyActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.media.AudioManager#registerAudioDeviceCallback",
                "android.media.AudioDeviceCallback#onAudioDevicesAdded",
                "android.media.AudioDeviceCallback#onAudioDevicesRemoved"
            })
    public void AudioInputDeviceNotificationsTest() throws Exception {
        requireFeatures("android.hardware.microphone");
        excludeFeatures("android.software.leanback");

        runTest(".audio.AudioInputDeviceNotificationsActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.media.AudioRecord#addOnRoutingChangedListener",
                "android.media.AudioRecord.OnRoutingChangedListener#onRoutingChanged"
            })
    public void AudioInputRoutingNotificationsTest() throws Exception {
        requireFeatures("android.hardware.microphone");
        excludeFeatures("android.software.leanback");

        runTest(".audio.AudioInputRoutingNotificationsActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "5.10/C-1-2,C-1-5")
    public void AudioLoopbackLatencyTest() throws Exception {
        requireFeatures("android.hardware.microphone", "android.hardware.audio.output");
        excludeFeatures(
                "android.hardware.type.watch",
                "android.hardware.type.television",
                "android.hardware.type.automotive");

        runTest(".audio.AudioLoopbackLatencyActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "5.6/C-1-2")
    public void AudioOutColdStartLatencyTest() throws Exception {
        excludeFeatures("android.hardware.type.watch", "android.hardware.type.television");

        runTest(".audio.AudioOutColdStartLatencyActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.media.AudioManager#registerAudioDeviceCallback",
                "android.media.AudioDeviceCallback#onAudioDevicesAdded",
                "android.media.AudioDeviceCallback#onAudioDevicesRemoved"
            })
    public void AudioOutputDeviceNotificationsTest() throws Exception {
        requireFeatures("android.hardware.audio.output");
        excludeFeatures("android.software.leanback");

        runTest(".audio.AudioOutputDeviceNotificationsActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.media.AudioTrack#addOnRoutingChangedListener",
                "android.media.AudioTrack.OnRoutingChangedListener#onRoutingChanged"
            })
    public void AudioOutputRoutingNotificationsTest() throws Exception {
        requireFeatures("android.hardware.audio.output");
        excludeFeatures("android.software.leanback");

        runTest(".audio.AudioOutputRoutingNotificationsActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "5.6")
    public void AudioTap2ToneTest() throws Exception {
        // Interesting test
        excludeFeatures(
                "android.hardware.type.watch",
                "android.hardware.type.television",
                "android.hardware.type.automotive");

        runTest(".audio.AudioTap2ToneActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void HifiUltrasoundTest() throws Exception {
        requireFeatures("android.hardware.microphone");

        runTest(".audio.HifiUltrasoundTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.8.3/C-1-1,C-1-2,C-2-1")
    public void HifiUltrasoundSpeakerTest() throws Exception {
        requireFeatures("android.hardware.audio.output");

        runTest(".audio.HifiUltrasoundSpeakerTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "5.9/C-1-4,C-1-2")
    @ApiTest(
            apis = {
                "android.media.midi.MidiManager#registerDeviceCallback",
                "android.media.midi.MidiManager#getDevices",
                "android.media.midi.MidiDevice#getInfo",
                "android.media.midi.MidiDevice#openOutputPort",
                "android.media.midi.MidiDevice#openInputPort",
                "android.media.midi.MidiDeviceInfo#getOutputPortCount",
                "android.media.midi.MidiDeviceInfo#getInputPortCount",
                "android.media.midi.MidiInputPort#send"
            })
    public void MidiJavaTest() throws Exception {
        requireFeatures("android.hardware.usb.host:android.software.midi");

        runTest(".audio.MidiJavaTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "5.9/C-1-3,C-1-2")
    public void MidiNativeTest() throws Exception {
        requireFeatures("android.hardware.usb.host", "android.software.midi");

        runTest(".audio.MidiNativeTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "5.10/C-1-1,C-1-3,C-1-4")
    public void ProAudioTest() throws Exception {
        requireFeatures("android.hardware.usb.host", "android.hardware.audio.pro");

        runTest(".audio.ProAudioActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.7.2/H-1-1,H-4-4,H-4-5,H-4-6,H-4-7")
    @ApiTest(
            apis = {
                "android.media.AudioManager#registerAudioDeviceCallback",
                "android.media.AudioDeviceCallback#onAudioDevicesAdded",
                "android.media.AudioDeviceCallback#onAudioDevicesRemoved",
                "android.media.AudioDeviceInfo#getChannelCounts",
                "android.media.AudioDeviceInfo#getEncodings",
                "android.media.AudioDeviceInfo#getSampleRates",
                "android.media.AudioDeviceInfo#getChannelIndexMasks"
            })
    public void USBAudioPeripheralAttributesTest() throws Exception {
        requireFeatures("android.hardware.usb.host");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".audio.USBAudioPeripheralAttributesActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.7.2/C-2-1,C-2-2")
    public void USBAudioPeripheralButtonsTest() throws Exception {
        requireFeatures("android.hardware.usb.host");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".audio.USBAudioPeripheralButtonsActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.8.2.2/H-1-2,H-2-1,H-3-1,H-4-2,H-4-3,H-4-4,H-4-5")
    @ApiTest(
            apis = {
                "android.media.AudioManager#registerAudioDeviceCallback",
                "android.media.AudioDeviceCallback#onAudioDevicesAdded",
                "android.media.AudioDeviceCallback#onAudioDevicesRemoved",
                "android.content.BroadcastReceiver#onReceive"
            })
    public void USBAudioPeripheralNotificationsTest() throws Exception {
        requireFeatures("android.hardware.usb.host");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".audio.USBAudioPeripheralNotificationsTest");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.8.2/C-1-1,C-1-2")
    public void USBAudioPeripheralPlayTest() throws Exception {
        requireFeatures("android.hardware.usb.host");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".audio.USBAudioPeripheralPlayActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.8.2.2/H-1-1|7.7.2/C-2-1,C-2-2")
    @ApiTest(apis = "android.app.Activity#onKeyDown")
    public void USBAudioPeripheralRecordTest() throws Exception {
        requireFeatures("android.hardware.usb.host");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".audio.USBAudioPeripheralRecordActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.hardware.usb.UsbManager#getDeviceList",
                "android.hardware.usb.UsbManager#requestPermission"
            })
    public void USBRestrictRecordATest() throws Exception {
        requireFeatures("android.hardware.usb.host");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".audio.USBRestrictRecordAActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "9.8.13/C-1-3")
    public void AudioMicrophoneMuteToggleTest() throws Exception {
        runTest(".audio.AudioMicrophoneMuteToggleActivity", "config_has_mic_toggle");
    }
}
