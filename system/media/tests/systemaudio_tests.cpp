/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <functional>
#include <string>
#include <unordered_set>

#include <gtest/gtest.h>

#define LOG_TAG "SysAudio_Test"
#include <log/log.h>
#include <media/PatchBuilder.h>
#include <system/audio.h>

using namespace android;

TEST(SystemAudioTest, PatchInvalid) {
    audio_patch patch{};
    ASSERT_FALSE(audio_patch_is_valid(&patch));
    patch.num_sources = AUDIO_PATCH_PORTS_MAX + 1;
    patch.num_sinks = 1;
    ASSERT_FALSE(audio_patch_is_valid(&patch));
    patch.num_sources = 1;
    patch.num_sinks = AUDIO_PATCH_PORTS_MAX + 1;
    ASSERT_FALSE(audio_patch_is_valid(&patch));
    patch.num_sources = 0;
    patch.num_sinks = 1;
    ASSERT_FALSE(audio_patch_is_valid(&patch));
}

TEST(SystemAudioTest, PatchValid) {
    const audio_port_config src = {
        .id = 1, .role = AUDIO_PORT_ROLE_SOURCE, .type = AUDIO_PORT_TYPE_DEVICE };
    // It's OK not to have sinks.
    ASSERT_TRUE(audio_patch_is_valid((PatchBuilder{}).addSource(src).patch()));
    const audio_port_config sink = {
        .id = 2, .role = AUDIO_PORT_ROLE_SINK, .type = AUDIO_PORT_TYPE_DEVICE };
    ASSERT_TRUE(audio_patch_is_valid((PatchBuilder{}).addSource(src).addSink(sink).patch()));
    ASSERT_TRUE(audio_patch_is_valid(
                    (PatchBuilder{}).addSource(src).addSource(src).addSink(sink).patch()));
    ASSERT_TRUE(audio_patch_is_valid(
                    (PatchBuilder{}).addSource(src).addSink(sink).addSink(sink).patch()));
    ASSERT_TRUE(audio_patch_is_valid(
                    (PatchBuilder{}).addSource(src).addSource(src).
                    addSink(sink).addSink(sink).patch()));
}

TEST(SystemAudioTest, PatchHwAvSync) {
    audio_port_config device_src_cfg = {
        .id = 1, .role = AUDIO_PORT_ROLE_SOURCE, .type = AUDIO_PORT_TYPE_DEVICE };
    ASSERT_FALSE(audio_port_config_has_hw_av_sync(&device_src_cfg));
    device_src_cfg.config_mask |= AUDIO_PORT_CONFIG_FLAGS;
    ASSERT_FALSE(audio_port_config_has_hw_av_sync(&device_src_cfg));
    device_src_cfg.flags.input = AUDIO_INPUT_FLAG_HW_AV_SYNC;
    ASSERT_TRUE(audio_port_config_has_hw_av_sync(&device_src_cfg));

    audio_port_config device_sink_cfg = {
        .id = 1, .role = AUDIO_PORT_ROLE_SINK, .type = AUDIO_PORT_TYPE_DEVICE };
    ASSERT_FALSE(audio_port_config_has_hw_av_sync(&device_sink_cfg));
    device_sink_cfg.config_mask |= AUDIO_PORT_CONFIG_FLAGS;
    ASSERT_FALSE(audio_port_config_has_hw_av_sync(&device_sink_cfg));
    device_sink_cfg.flags.output = AUDIO_OUTPUT_FLAG_HW_AV_SYNC;
    ASSERT_TRUE(audio_port_config_has_hw_av_sync(&device_sink_cfg));

    audio_port_config mix_sink_cfg = {
        .id = 1, .role = AUDIO_PORT_ROLE_SINK, .type = AUDIO_PORT_TYPE_MIX };
    ASSERT_FALSE(audio_port_config_has_hw_av_sync(&mix_sink_cfg));
    mix_sink_cfg.config_mask |= AUDIO_PORT_CONFIG_FLAGS;
    ASSERT_FALSE(audio_port_config_has_hw_av_sync(&mix_sink_cfg));
    mix_sink_cfg.flags.input = AUDIO_INPUT_FLAG_HW_AV_SYNC;
    ASSERT_TRUE(audio_port_config_has_hw_av_sync(&mix_sink_cfg));

    audio_port_config mix_src_cfg = {
        .id = 1, .role = AUDIO_PORT_ROLE_SOURCE, .type = AUDIO_PORT_TYPE_MIX };
    ASSERT_FALSE(audio_port_config_has_hw_av_sync(&mix_src_cfg));
    mix_src_cfg.config_mask |= AUDIO_PORT_CONFIG_FLAGS;
    ASSERT_FALSE(audio_port_config_has_hw_av_sync(&mix_src_cfg));
    mix_src_cfg.flags.output = AUDIO_OUTPUT_FLAG_HW_AV_SYNC;
    ASSERT_TRUE(audio_port_config_has_hw_av_sync(&mix_src_cfg));
}

TEST(SystemAudioTest, PatchEqual) {
    const audio_patch patch1{}, patch2{};
    // Invalid patches are not equal.
    ASSERT_FALSE(audio_patches_are_equal(&patch1, &patch2));
    const audio_port_config src = {
        .id = 1, .role = AUDIO_PORT_ROLE_SOURCE, .type = AUDIO_PORT_TYPE_DEVICE };
    const audio_port_config sink = {
        .id = 2, .role = AUDIO_PORT_ROLE_SINK, .type = AUDIO_PORT_TYPE_DEVICE };
    ASSERT_FALSE(audio_patches_are_equal(
                    (PatchBuilder{}).addSource(src).patch(),
                    (PatchBuilder{}).addSource(src).addSink(sink).patch()));
    ASSERT_TRUE(audio_patches_are_equal(
                    (PatchBuilder{}).addSource(src).addSink(sink).patch(),
                    (PatchBuilder{}).addSource(src).addSink(sink).patch()));
    ASSERT_FALSE(audio_patches_are_equal(
                    (PatchBuilder{}).addSource(src).addSink(sink).patch(),
                    (PatchBuilder{}).addSource(src).addSource(src).addSink(sink).patch()));
    audio_port_config sink_hw_av_sync = sink;
    sink_hw_av_sync.config_mask |= AUDIO_PORT_CONFIG_FLAGS;
    sink_hw_av_sync.flags.output = AUDIO_OUTPUT_FLAG_HW_AV_SYNC;
    ASSERT_FALSE(audio_patches_are_equal(
                    (PatchBuilder{}).addSource(src).addSink(sink).patch(),
                    (PatchBuilder{}).addSource(src).addSink(sink_hw_av_sync).patch()));
    ASSERT_TRUE(audio_patches_are_equal(
                    (PatchBuilder{}).addSource(src).addSink(sink_hw_av_sync).patch(),
                    (PatchBuilder{}).addSource(src).addSink(sink_hw_av_sync).patch()));
}

void runAudioDeviceTypeHelperFunction(const std::unordered_set<audio_devices_t>& allDevices,
                                      const audio_devices_t targetDevices[],
                                      unsigned int targetDeviceCount,
                                      const std::string& deviceTag,
                                      bool (*device_type_helper_function)(audio_devices_t))
{
    std::unordered_set<audio_devices_t> devices(targetDevices, targetDevices + targetDeviceCount);
    for (auto device : allDevices) {
        if (devices.find(device) == devices.end()) {
            ASSERT_FALSE(device_type_helper_function(device))
                    << std::hex << device << " should not be " << deviceTag << " device";
        } else {
            ASSERT_TRUE(device_type_helper_function(device))
                    << std::hex << device << " should be " << deviceTag << " device";
        }
    }
}

TEST(SystemAudioTest, AudioDeviceTypeHelperFunction) {
    std::unordered_set<audio_devices_t> allDeviceTypes;
    allDeviceTypes.insert(std::begin(AUDIO_DEVICE_OUT_ALL_ARRAY),
            std::end(AUDIO_DEVICE_OUT_ALL_ARRAY));
    allDeviceTypes.insert(std::begin(AUDIO_DEVICE_IN_ALL_ARRAY),
            std::end(AUDIO_DEVICE_IN_ALL_ARRAY));

    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_OUT_ALL_ARRAY,
            std::size(AUDIO_DEVICE_OUT_ALL_ARRAY), "output", audio_is_output_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_IN_ALL_ARRAY,
            std::size(AUDIO_DEVICE_IN_ALL_ARRAY), "input", audio_is_input_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_OUT_ALL_A2DP_ARRAY,
            std::size(AUDIO_DEVICE_OUT_ALL_A2DP_ARRAY), "a2dp out", audio_is_a2dp_out_device);
    const audio_devices_t bluetoothInA2dpDevices[] = { AUDIO_DEVICE_IN_BLUETOOTH_A2DP };
    runAudioDeviceTypeHelperFunction(allDeviceTypes, bluetoothInA2dpDevices,
            std::size(bluetoothInA2dpDevices), "a2dp in", audio_is_a2dp_in_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_OUT_ALL_SCO_ARRAY,
            std::size(AUDIO_DEVICE_OUT_ALL_SCO_ARRAY), "bluetooth out sco",
            audio_is_bluetooth_out_sco_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_IN_ALL_SCO_ARRAY,
            std::size(AUDIO_DEVICE_IN_ALL_SCO_ARRAY), "bluetooth in sco",
            audio_is_bluetooth_in_sco_device);
    const unsigned int scoDeviceCount = AUDIO_DEVICE_OUT_SCO_CNT + AUDIO_DEVICE_IN_SCO_CNT;
    audio_devices_t scoDevices[scoDeviceCount];
    std::copy(std::begin(AUDIO_DEVICE_OUT_ALL_SCO_ARRAY), std::end(AUDIO_DEVICE_OUT_ALL_SCO_ARRAY),
              std::begin(scoDevices));
    std::copy(std::begin(AUDIO_DEVICE_IN_ALL_SCO_ARRAY), std::end(AUDIO_DEVICE_IN_ALL_SCO_ARRAY),
              std::begin(scoDevices) + AUDIO_DEVICE_OUT_SCO_CNT);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, scoDevices,
            std::size(scoDevices), "bluetooth sco", audio_is_bluetooth_sco_device);
    const audio_devices_t hearingAidOutDevices[] = { AUDIO_DEVICE_OUT_HEARING_AID };
    runAudioDeviceTypeHelperFunction(allDeviceTypes, hearingAidOutDevices,
            std::size(hearingAidOutDevices), "hearing aid out", audio_is_hearing_aid_out_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_OUT_ALL_USB_ARRAY,
            std::size(AUDIO_DEVICE_OUT_ALL_USB_ARRAY), "usb out", audio_is_usb_out_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_IN_ALL_USB_ARRAY,
            std::size(AUDIO_DEVICE_IN_ALL_USB_ARRAY), "usb in", audio_is_usb_in_device);
    const audio_devices_t remoteSubmixDevices[] = {
            AUDIO_DEVICE_IN_REMOTE_SUBMIX, AUDIO_DEVICE_OUT_REMOTE_SUBMIX };
    runAudioDeviceTypeHelperFunction(allDeviceTypes, remoteSubmixDevices,
            std::size(remoteSubmixDevices), "remote submix", audio_is_remote_submix_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_OUT_ALL_DIGITAL_ARRAY,
            std::size(AUDIO_DEVICE_OUT_ALL_DIGITAL_ARRAY), "digital out",
            audio_is_digital_out_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_IN_ALL_DIGITAL_ARRAY,
            std::size(AUDIO_DEVICE_IN_ALL_DIGITAL_ARRAY), "digital in",
            audio_is_digital_in_device);
    const unsigned int digitalDeviceCount
            = AUDIO_DEVICE_OUT_DIGITAL_CNT + AUDIO_DEVICE_IN_DIGITAL_CNT;
    audio_devices_t digitalDevices[digitalDeviceCount];
    std::copy(std::begin(AUDIO_DEVICE_OUT_ALL_DIGITAL_ARRAY),
              std::end(AUDIO_DEVICE_OUT_ALL_DIGITAL_ARRAY),
              std::begin(digitalDevices));
    std::copy(std::begin(AUDIO_DEVICE_IN_ALL_DIGITAL_ARRAY),
              std::end(AUDIO_DEVICE_IN_ALL_DIGITAL_ARRAY),
              std::begin(digitalDevices) + AUDIO_DEVICE_OUT_DIGITAL_CNT);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, digitalDevices,
              std::size(digitalDevices), "digital", audio_device_is_digital);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_OUT_ALL_BLE_ARRAY,
            std::size(AUDIO_DEVICE_OUT_ALL_BLE_ARRAY), "ble out",
            audio_is_ble_out_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_IN_ALL_BLE_ARRAY,
            std::size(AUDIO_DEVICE_IN_ALL_BLE_ARRAY), "ble in",
            audio_is_ble_in_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_OUT_BLE_UNICAST_ARRAY,
            std::size(AUDIO_DEVICE_OUT_BLE_UNICAST_ARRAY), "ble unicast",
            audio_is_ble_unicast_device);
    runAudioDeviceTypeHelperFunction(allDeviceTypes, AUDIO_DEVICE_OUT_BLE_BROADCAST_ARRAY,
            std::size(AUDIO_DEVICE_OUT_BLE_BROADCAST_ARRAY), "ble broadcast",
            audio_is_ble_broadcast_device);
}


// An array whose length is AUDIO_PORT_MAX_CHANNEL_MASKS for generating audio port information.
static constexpr audio_channel_mask_t OUT_CHANNEL_MASKS[AUDIO_PORT_MAX_CHANNEL_MASKS] = {
    AUDIO_CHANNEL_OUT_FRONT_LEFT,
    AUDIO_CHANNEL_OUT_FRONT_RIGHT,
    AUDIO_CHANNEL_OUT_FRONT_CENTER,
    AUDIO_CHANNEL_OUT_LOW_FREQUENCY,
    AUDIO_CHANNEL_OUT_BACK_LEFT,
    AUDIO_CHANNEL_OUT_BACK_RIGHT,
    AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER,
    AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER,
    AUDIO_CHANNEL_OUT_BACK_CENTER,
    AUDIO_CHANNEL_OUT_SIDE_LEFT,
    AUDIO_CHANNEL_OUT_SIDE_RIGHT,
    AUDIO_CHANNEL_OUT_TOP_CENTER,
    AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT,
    AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER,
    AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT,
    AUDIO_CHANNEL_OUT_TOP_BACK_LEFT,
    AUDIO_CHANNEL_OUT_TOP_BACK_CENTER,
    AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT,
    AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT,
    AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT,
    AUDIO_CHANNEL_OUT_HAPTIC_A,
    AUDIO_CHANNEL_OUT_HAPTIC_B,
    AUDIO_CHANNEL_OUT_MONO,
    AUDIO_CHANNEL_OUT_STEREO,
    AUDIO_CHANNEL_OUT_2POINT1,
    AUDIO_CHANNEL_OUT_TRI,
    AUDIO_CHANNEL_OUT_TRI_BACK,
    AUDIO_CHANNEL_OUT_3POINT1,
    AUDIO_CHANNEL_OUT_2POINT0POINT2,
    AUDIO_CHANNEL_OUT_2POINT1POINT2,
    AUDIO_CHANNEL_OUT_3POINT0POINT2,
    AUDIO_CHANNEL_OUT_QUAD
};

// An array whose length is AUDIO_PORT_MAX_CHANNEL_MASKS for generating audio port information.
static constexpr audio_channel_mask_t IN_CHANNEL_MASKS[AUDIO_PORT_MAX_CHANNEL_MASKS] = {
    AUDIO_CHANNEL_IN_LEFT,
    AUDIO_CHANNEL_IN_RIGHT,
    AUDIO_CHANNEL_IN_FRONT,
    AUDIO_CHANNEL_IN_BACK,
    AUDIO_CHANNEL_IN_LEFT_PROCESSED,
    AUDIO_CHANNEL_IN_RIGHT_PROCESSED,
    AUDIO_CHANNEL_IN_FRONT_PROCESSED,
    AUDIO_CHANNEL_IN_BACK_PROCESSED,
    AUDIO_CHANNEL_IN_PRESSURE,
    AUDIO_CHANNEL_IN_X_AXIS,
    AUDIO_CHANNEL_IN_Y_AXIS,
    AUDIO_CHANNEL_IN_Z_AXIS,
    AUDIO_CHANNEL_IN_BACK_LEFT,
    AUDIO_CHANNEL_IN_BACK_RIGHT,
    AUDIO_CHANNEL_IN_CENTER,
    AUDIO_CHANNEL_IN_LOW_FREQUENCY,
    AUDIO_CHANNEL_IN_TOP_LEFT,
    AUDIO_CHANNEL_IN_TOP_RIGHT,
    AUDIO_CHANNEL_IN_VOICE_UPLINK,
    AUDIO_CHANNEL_IN_VOICE_DNLINK,
    AUDIO_CHANNEL_IN_MONO,
    AUDIO_CHANNEL_IN_STEREO,
    AUDIO_CHANNEL_IN_FRONT_BACK,
    AUDIO_CHANNEL_IN_6,
    AUDIO_CHANNEL_IN_2POINT0POINT2,
    AUDIO_CHANNEL_IN_2POINT1POINT2,
    AUDIO_CHANNEL_IN_3POINT0POINT2,
    AUDIO_CHANNEL_IN_3POINT1POINT2,
    AUDIO_CHANNEL_IN_5POINT1,
    AUDIO_CHANNEL_IN_VOICE_UPLINK_MONO,
    AUDIO_CHANNEL_IN_VOICE_DNLINK_MONO,
    AUDIO_CHANNEL_IN_VOICE_CALL_MONO
};

static constexpr unsigned int SHORT_AUDIO_DESCRIPTOR_LENGTH = 3;

using SystemAudioPortTestParams = std::tuple<audio_port_role_t, audio_port_type_t>;

class SystemAudioPortTest : public testing::TestWithParam<SystemAudioPortTestParams> {
protected:
    const struct audio_gain_config mGainConfig = {
            .index = 0,
            .mode = AUDIO_GAIN_MODE_JOINT,
            .channel_mask = AUDIO_CHANNEL_OUT_FRONT_LEFT,
            .values = {1, 2},
            .ramp_duration_ms = 10
    };
    const struct audio_gain mGain = {
            .mode = AUDIO_GAIN_MODE_JOINT,
            .channel_mask = AUDIO_CHANNEL_OUT_FRONT_LEFT,
            .min_value = 10,
            .max_value = 100,
            .default_value = 42,
            .step_value = 2,
            .min_ramp_ms = 10,
            .max_ramp_ms = 20
    };
    const std::string mName = "SystemAudioPortTestName";
    const size_t mLastFormat = 0x7f;
    const audio_input_flags_t mInputFlag = AUDIO_INPUT_FLAG_FAST;
    const audio_output_flags_t mOutputFlag = AUDIO_OUTPUT_FLAG_FAST;
    const audio_module_handle_t mHwModule = 1;
    const std::string mAddress = "SystemAudioPortTestAddress";
    const audio_devices_t mInputDeviceType = AUDIO_DEVICE_IN_BUILTIN_MIC;
    const audio_devices_t mOutputDeviceType = AUDIO_DEVICE_OUT_SPEAKER;
    const audio_io_handle_t mIoHandle = 1;
    const audio_stream_type_t mStream = AUDIO_STREAM_MUSIC;
    const audio_source_t mSource = AUDIO_SOURCE_MIC;
    const audio_session_t mSession = AUDIO_SESSION_DEVICE;

    size_t fillFakeFormats(audio_format_t formats[], size_t numFormats);
    void fillFakeAudioPortConfigInfo(struct audio_port_config* config);
    void fillFakeAudioPortInfo(struct audio_port* audioPort);
    void fillFakeAudioPortV7Info(struct audio_port_v7* portV7, bool containsExtraAudioDescriptor);

    template <typename T, typename U, typename Func>
    void updateFieldAndCompare(const T updatedValue, T U::*field, U* lhs, U* rhs, Func p) {
        lhs->*field = updatedValue;
        ASSERT_FALSE(p(lhs, rhs));
        lhs->*field = rhs->*field;
        ASSERT_TRUE(p(lhs, rhs));
    }

    template <typename T, typename Func>
    void updateConfigFieldAndCompare(const T updatedValue, T audio_port_config::*field,
            struct audio_port_config* lhs, struct audio_port_config* rhs,
            unsigned int configMask, Func p) {
        lhs->*field = updatedValue;
        ASSERT_FALSE(p(lhs, rhs));
        unsigned int savedConfigMask = lhs->config_mask;
        lhs->config_mask &= ~configMask;
        rhs->config_mask = lhs->config_mask;
        ASSERT_TRUE(p(lhs, rhs));
        lhs->config_mask = savedConfigMask;
        rhs->config_mask = savedConfigMask;
        lhs->*field = rhs->*field;
        ASSERT_TRUE(p(lhs, rhs));
    }

    template <typename T>
    void fillFakeAudioPortBaseInfo(T* port) {
        port->id = 1;
        port->role = std::get<0>(GetParam());
        port->type = std::get<1>(GetParam());
        // Intentionally make a name that is not ended with '\0' to test the conversion function.
        strncpy(port->name, mName.c_str(), AUDIO_PORT_MAX_NAME_LEN);
        port->num_gains = AUDIO_PORT_MAX_GAINS;
        for (size_t i = 0; i < port->num_gains; ++i) {
            port->gains[i] = mGain;
            port->gains[i].max_ramp_ms *= (i + 1);
        }
        fillFakeAudioPortConfigInfo(&port->active_config);
        switch (port->type) {
        case AUDIO_PORT_TYPE_DEVICE:
            port->ext.device.hw_module = mHwModule;
            port->ext.device.type = port->role == AUDIO_PORT_ROLE_SINK ? mOutputDeviceType
                                                                       : mInputDeviceType;
            strncpy(port->ext.device.address, mAddress.c_str(), AUDIO_DEVICE_MAX_ADDRESS_LEN);
#ifndef AUDIO_NO_SYSTEM_DECLARATIONS
            port->ext.device.encapsulation_modes = AUDIO_ENCAPSULATION_MODE_ELEMENTARY_STREAM;
            port->ext.device.encapsulation_metadata_types =
                    AUDIO_ENCAPSULATION_METADATA_TYPE_FRAMEWORK_TUNER;
#endif
            break;
        case AUDIO_PORT_TYPE_MIX:
            port->ext.mix = {
                    mHwModule,
                    mIoHandle,
                    AUDIO_LATENCY_NORMAL
            };
            break;
        case AUDIO_PORT_TYPE_SESSION:
            port->ext.session.session = mSession;
            break;
        default:
            // Must not happen
            FAIL() << "Unknown port type " << port->type;
        }
    }

    template <typename T, typename Func>
    void testAudioPortExtBaseEquivalent(T* lhs, T* rhs, Func p) {
        switch (lhs->type) {
        case AUDIO_PORT_TYPE_DEVICE:
            lhs->ext.device.hw_module = rhs->ext.device.hw_module + 1;
            ASSERT_FALSE(p(lhs, rhs));
            lhs->ext.device.hw_module = rhs->ext.device.hw_module;
            ASSERT_TRUE(p(lhs, rhs));
            lhs->ext.device.type = AUDIO_DEVICE_NONE;
            ASSERT_FALSE(p(lhs, rhs));
            lhs->ext.device.type = rhs->ext.device.type;
            ASSERT_TRUE(p(lhs, rhs));
            memset(lhs->ext.device.address, 0, sizeof(lhs->ext.device.address));
            ASSERT_FALSE(p(lhs, rhs));
            strncpy(lhs->ext.device.address, rhs->ext.device.address, AUDIO_DEVICE_MAX_ADDRESS_LEN);
            ASSERT_TRUE(p(lhs, rhs));
            break;
        case AUDIO_PORT_TYPE_MIX:
            lhs->ext.mix.hw_module = rhs->ext.mix.hw_module + 1;
            ASSERT_FALSE(p(lhs, rhs));
            lhs->ext.mix.hw_module = rhs->ext.mix.hw_module;
            ASSERT_TRUE(p(lhs, rhs));
            lhs->ext.mix.handle = rhs->ext.mix.handle + 1;
            ASSERT_FALSE(p(lhs, rhs));
            lhs->ext.mix.handle = rhs->ext.mix.handle;
            ASSERT_TRUE(p(lhs, rhs));
            break;
        case AUDIO_PORT_TYPE_SESSION:
            lhs->ext.session.session = AUDIO_SESSION_NONE;
            ASSERT_FALSE(p(lhs, rhs));
            lhs->ext.session.session = rhs->ext.session.session;
            ASSERT_TRUE(p(lhs, rhs));
            break;
        default:
            break;
        }
    }

    template <typename T, typename Func>
    void testAudioPortEquivalent(T* lhs, T* rhs, Func p) {
        updateFieldAndCompare(rhs->id + 1, &T::id, lhs, rhs, p);
        updateFieldAndCompare(AUDIO_PORT_ROLE_NONE, &T::role, lhs, rhs, p);
        updateFieldAndCompare(AUDIO_PORT_TYPE_NONE, &T::type, lhs, rhs, p);
        memset(lhs->name, 0, sizeof(lhs->name));
        ASSERT_FALSE(p(lhs, rhs));
        strncpy(lhs->name, rhs->name, AUDIO_PORT_MAX_NAME_LEN);
        ASSERT_TRUE(p(lhs, rhs));
        updateFieldAndCompare(rhs->num_gains + 1, &T::num_gains, lhs, rhs, p);

        lhs->gains[0] = {};
        ASSERT_FALSE(p(lhs, rhs));
        lhs->gains[0] = rhs->gains[0];
        ASSERT_TRUE(p(lhs, rhs));

        testAudioPortExtBaseEquivalent(lhs, rhs, p);
        switch (lhs->type) {
        case AUDIO_PORT_TYPE_DEVICE:
#ifndef AUDIO_NO_SYSTEM_DECLARATIONS
            lhs->ext.device.encapsulation_modes = AUDIO_ENCAPSULATION_MODE_NONE;
            ASSERT_FALSE(p(lhs, rhs));
            lhs->ext.device.encapsulation_modes = rhs->ext.device.encapsulation_modes;
            ASSERT_TRUE(p(lhs, rhs));
            lhs->ext.device.encapsulation_metadata_types = AUDIO_ENCAPSULATION_METADATA_TYPE_NONE;
            ASSERT_FALSE(p(lhs, rhs));
            lhs->ext.device.encapsulation_metadata_types =
                    rhs->ext.device.encapsulation_metadata_types;
            ASSERT_TRUE(p(lhs, rhs));
#endif
            break;
        case AUDIO_PORT_TYPE_MIX:
            lhs->ext.mix.latency_class = AUDIO_LATENCY_LOW;
            ASSERT_FALSE(p(lhs, rhs));
            lhs->ext.mix.latency_class = rhs->ext.mix.latency_class;
            ASSERT_TRUE(p(lhs, rhs));
            break;
        default:
            break;
        }
    }

    template <typename T, typename U, typename Func>
    void testAudioPortCapabilityArraysEquivalent(T values[], size_t size, const T& replacedValue,
            U* lhs, U* rhs, Func p) {
        ASSERT_GT(size, 1);
        std::swap(values[0], values[1]);
        ASSERT_TRUE(p(lhs, rhs));
        T savedValue = values[0];
        values[0] = replacedValue;
        ASSERT_FALSE(p(lhs, rhs));
        values[0] = savedValue;
        ASSERT_TRUE(p(lhs, rhs));
    }
};

void SystemAudioPortTest::fillFakeAudioPortConfigInfo(struct audio_port_config* config) {
    config->id = 0;
    config->role = std::get<0>(GetParam());
    config->type = std::get<1>(GetParam());
    config->config_mask = AUDIO_PORT_CONFIG_ALL;
    config->sample_rate = 48000;
    config->channel_mask = audio_port_config_has_input_direction(config) ?
            AUDIO_CHANNEL_IN_MONO : AUDIO_CHANNEL_OUT_MONO;
    config->format = AUDIO_FORMAT_PCM_16_BIT;
    config->gain = mGainConfig;
    if (audio_port_config_has_input_direction(config)) {
        config->flags.input = mInputFlag;
    } else {
        config->flags.output = mOutputFlag;
    }
    switch (config->type) {
    case AUDIO_PORT_TYPE_DEVICE:
        config->ext.device.hw_module = mHwModule;
        config->ext.device.type =
                config->role == AUDIO_PORT_ROLE_SINK ? mOutputDeviceType : mInputDeviceType;
        strncpy(config->ext.device.address, mAddress.c_str(), AUDIO_DEVICE_MAX_ADDRESS_LEN);
        break;
    case AUDIO_PORT_TYPE_MIX:
        config->ext.mix.hw_module = mHwModule;
        config->ext.mix.handle = mIoHandle;
        if (config->role == AUDIO_PORT_ROLE_SOURCE) {
            config->ext.mix.usecase.stream = mStream;
        } else {
            config->ext.mix.usecase.source = mSource;
        }
        break;
    case AUDIO_PORT_TYPE_SESSION:
        config->ext.session.session = mSession;
        break;
    default:
        // Must not happen
        FAIL() << "Unknown port type " << config->type;
    }
}

size_t SystemAudioPortTest::fillFakeFormats(audio_format_t formats[], size_t numFormats) {
    size_t j = 0;
    size_t format = 0x1;
    while (j < numFormats) {
        while (format <= mLastFormat) {
            if (audio_is_valid_format(static_cast<audio_format_t>(format << 24))) {
                break;
            }
            format++;
        }
        if (format > mLastFormat) {
            break;
        }
        formats[j++] = static_cast<audio_format_t>((format++) << 24);
    }
    return j;
}

void SystemAudioPortTest::fillFakeAudioPortInfo(struct audio_port* audioPort) {
    fillFakeAudioPortBaseInfo(audioPort);
    // Use the maximum number of sample rates, formats, channel masks and gains for test
    audioPort->num_sample_rates = AUDIO_PORT_MAX_SAMPLING_RATES;
    for (size_t i = 0; i < audioPort->num_sample_rates; ++i) {
        // The numbers doesn't make any difference.
        audioPort->sample_rates[i] = (i + 1) * 1000;
    }
    audioPort->num_channel_masks = AUDIO_PORT_MAX_CHANNEL_MASKS;
    auto channelMasks = audioPort->role == AUDIO_PORT_ROLE_SINK ? OUT_CHANNEL_MASKS
                                                                : IN_CHANNEL_MASKS;
    std::copy(channelMasks, channelMasks+audioPort->num_channel_masks,
            std::begin(audioPort->channel_masks));
    audioPort->num_formats = AUDIO_PORT_MAX_FORMATS;
    audioPort->num_formats = fillFakeFormats(audioPort->formats, audioPort->num_formats);
}

void SystemAudioPortTest::fillFakeAudioPortV7Info(struct audio_port_v7* portV7,
                                                  bool containsExtraAudioDescriptor) {
    fillFakeAudioPortBaseInfo(portV7);
    audio_format_t formats[AUDIO_PORT_MAX_FORMATS];
    portV7->num_audio_profiles = fillFakeFormats(formats, AUDIO_PORT_MAX_FORMATS);
    for (size_t i = 0; i < portV7->num_audio_profiles; ++i) {
        portV7->audio_profiles[i].format = formats[i];
        // Use the maximum number of sample rates, formats, channel masks and gains for test
        portV7->audio_profiles[i].num_sample_rates = AUDIO_PORT_MAX_SAMPLING_RATES;
        for (size_t j = 0; j < portV7->audio_profiles[i].num_sample_rates; ++j) {
            // The numbers doesn't make any difference.
            portV7->audio_profiles[i].sample_rates[j] = rand();
        }
        portV7->audio_profiles[i].num_channel_masks = AUDIO_PORT_MAX_CHANNEL_MASKS;
        auto channelMasks = portV7->role == AUDIO_PORT_ROLE_SINK ? OUT_CHANNEL_MASKS
                                                                 : IN_CHANNEL_MASKS;
        std::copy(channelMasks, channelMasks+portV7->audio_profiles[i].num_channel_masks,
                std::begin(portV7->audio_profiles[i].channel_masks));
    }
    if (containsExtraAudioDescriptor) {
        portV7->num_extra_audio_descriptors = AUDIO_PORT_MAX_EXTRA_AUDIO_DESCRIPTORS;
        for (size_t i = 0; i < portV7->num_extra_audio_descriptors; ++i) {
            portV7->extra_audio_descriptors[i].standard = AUDIO_STANDARD_EDID;
            portV7->extra_audio_descriptors[i].descriptor_length = SHORT_AUDIO_DESCRIPTOR_LENGTH;
            for (unsigned int j = 0; j < SHORT_AUDIO_DESCRIPTOR_LENGTH; ++j) {
                portV7->extra_audio_descriptors[i].descriptor[j] = rand() % 254 + 1;
            }
            portV7->extra_audio_descriptors[i].encapsulation_type =
                    AUDIO_ENCAPSULATION_TYPE_IEC61937;
        }
    }
}

TEST_F(SystemAudioPortTest, AudioGainConfigEquivalentTest) {
    struct audio_gain_config lhs = mGainConfig;
    struct audio_gain_config rhs = mGainConfig;
    ASSERT_TRUE(audio_gain_config_are_equal(&lhs, &rhs));
    lhs.index = rhs.index + 1;
    ASSERT_TRUE(audio_gain_config_are_equal(&lhs, &rhs));
    lhs.index = rhs.index;
    lhs.values[0] = rhs.values[0] + 1;
    ASSERT_FALSE(audio_gain_config_are_equal(&lhs, &rhs));
    lhs.values[0] = rhs.values[0];
    updateFieldAndCompare(rhs.ramp_duration_ms + 10, &audio_gain_config::ramp_duration_ms,
            &lhs, &rhs, audio_gain_config_are_equal);
    for (const audio_gain_mode_t mode : {AUDIO_GAIN_MODE_CHANNELS, AUDIO_GAIN_MODE_RAMP}) {
        lhs.mode = mode;
        ASSERT_FALSE(audio_gain_config_are_equal(&lhs, &rhs));
        rhs.mode = lhs.mode;
        ASSERT_TRUE(audio_gain_config_are_equal(&lhs, &rhs));
        lhs.values[2] = rhs.values[2] + 1;
        ASSERT_TRUE(audio_gain_config_are_equal(&lhs, &rhs));
        lhs.values[2] = rhs.values[2];
        lhs.values[0] = rhs.values[0] + 1;
        ASSERT_FALSE(audio_gain_config_are_equal(&lhs, &rhs));
        lhs.values[0] = rhs.values[0];
    }
}

TEST_F(SystemAudioPortTest, AudioGainEquivalentTest) {
    struct audio_gain lhs = mGain;
    struct audio_gain rhs = mGain;
    ASSERT_TRUE(audio_gains_are_equal(&lhs, &rhs));

    lhs.mode = AUDIO_GAIN_MODE_CHANNELS;
    ASSERT_FALSE(audio_gains_are_equal(&lhs, &rhs));
    rhs.mode = lhs.mode;
    ASSERT_TRUE(audio_gains_are_equal(&lhs, &rhs));
    updateFieldAndCompare(static_cast<audio_channel_mask_t>(rhs.channel_mask << 1),
            &audio_gain::channel_mask, &lhs, &rhs, audio_gains_are_equal);

    updateFieldAndCompare(rhs.min_value + 10, &audio_gain::min_value,
            &lhs, &rhs, audio_gains_are_equal);
    updateFieldAndCompare(rhs.max_value + 10, &audio_gain::max_value,
            &lhs, &rhs, audio_gains_are_equal);
    updateFieldAndCompare(rhs.default_value + 10, &audio_gain::default_value,
            &lhs, &rhs, audio_gains_are_equal);
    updateFieldAndCompare(rhs.step_value + 10, &audio_gain::step_value,
            &lhs, &rhs, audio_gains_are_equal);
    updateFieldAndCompare(rhs.min_ramp_ms + 10, &audio_gain::min_ramp_ms,
            &lhs, &rhs, audio_gains_are_equal);
    updateFieldAndCompare(rhs.max_ramp_ms + 10, &audio_gain::max_ramp_ms,
            &lhs, &rhs, audio_gains_are_equal);
}

TEST_P(SystemAudioPortTest, AudioPortConfigEquivalentTest) {
    struct audio_port_config lhs;
    struct audio_port_config rhs;
    ASSERT_NO_FATAL_FAILURE(fillFakeAudioPortConfigInfo(&lhs));
    ASSERT_NO_FATAL_FAILURE(fillFakeAudioPortConfigInfo(&rhs));
    ASSERT_TRUE(audio_port_configs_are_equal(&lhs, &rhs));

    updateFieldAndCompare(AUDIO_PORT_ROLE_NONE, &audio_port_config::role,
            &lhs, &rhs, audio_port_configs_are_equal);
    updateFieldAndCompare(AUDIO_PORT_TYPE_NONE, &audio_port_config::type,
            &lhs, &rhs, audio_port_configs_are_equal);

    updateConfigFieldAndCompare(rhs.sample_rate * 2, &audio_port_config::sample_rate,
            &lhs, &rhs, AUDIO_PORT_CONFIG_SAMPLE_RATE, audio_port_configs_are_equal);
    updateConfigFieldAndCompare(AUDIO_CHANNEL_NONE, &audio_port_config::channel_mask,
            &lhs, &rhs, AUDIO_PORT_CONFIG_CHANNEL_MASK, audio_port_configs_are_equal);
    updateConfigFieldAndCompare(AUDIO_FORMAT_DEFAULT, &audio_port_config::format,
            &lhs, &rhs, AUDIO_PORT_CONFIG_FORMAT, audio_port_configs_are_equal);
    lhs.gain.ramp_duration_ms = rhs.gain.ramp_duration_ms * 2;
    ASSERT_FALSE(audio_port_configs_are_equal(&lhs, &rhs));
    lhs.config_mask &= ~AUDIO_PORT_CONFIG_GAIN;
    rhs.config_mask = lhs.config_mask;
    ASSERT_TRUE(audio_port_configs_are_equal(&lhs, &rhs));

    lhs.config_mask |= AUDIO_PORT_CONFIG_FLAGS;
    rhs.config_mask = lhs.config_mask;
    ASSERT_TRUE(audio_port_configs_are_equal(&lhs, &rhs));
    if (audio_port_config_has_input_direction(&lhs)) {
        lhs.flags.input = AUDIO_INPUT_FLAG_NONE;
    } else {
        lhs.flags.output = AUDIO_OUTPUT_FLAG_NONE;
    }
    ASSERT_FALSE(audio_port_configs_are_equal(&lhs, &rhs));
    lhs.config_mask &= ~AUDIO_PORT_CONFIG_FLAGS;
    rhs.config_mask = lhs.config_mask;
    ASSERT_TRUE(audio_port_configs_are_equal(&lhs, &rhs));

    testAudioPortExtBaseEquivalent(&lhs, &rhs, audio_port_configs_are_equal);
    if (lhs.type == AUDIO_PORT_TYPE_MIX) {
        if (lhs.role == AUDIO_PORT_ROLE_SINK) {
            lhs.ext.mix.usecase.source = AUDIO_SOURCE_DEFAULT;
            ASSERT_FALSE(audio_port_configs_are_equal(&lhs, &rhs));
            lhs.ext.mix.usecase.source = rhs.ext.mix.usecase.source;
            ASSERT_TRUE(audio_port_configs_are_equal(&lhs, &rhs));
        } else if (lhs.role == AUDIO_PORT_ROLE_SOURCE) {
            lhs.ext.mix.usecase.stream = AUDIO_STREAM_DEFAULT;
            ASSERT_FALSE(audio_port_configs_are_equal(&lhs, &rhs));
            lhs.ext.mix.usecase.stream = rhs.ext.mix.usecase.stream;
            ASSERT_TRUE(audio_port_configs_are_equal(&lhs, &rhs));
        }
    }
}

TEST_P(SystemAudioPortTest, AudioPortEquivalentTest) {
    struct audio_port lhs;
    ASSERT_NO_FATAL_FAILURE(fillFakeAudioPortInfo(&lhs));
    struct audio_port rhs = lhs;
    ASSERT_TRUE(audio_ports_are_equal(&lhs, &rhs));

    testAudioPortEquivalent(&lhs, &rhs, audio_ports_are_equal);

    testAudioPortCapabilityArraysEquivalent(lhs.formats, lhs.num_formats,
            AUDIO_FORMAT_DEFAULT, &lhs, &rhs, audio_ports_are_equal);
    testAudioPortCapabilityArraysEquivalent(lhs.channel_masks, lhs.num_channel_masks,
            AUDIO_CHANNEL_NONE, &lhs, &rhs, audio_ports_are_equal);
    testAudioPortCapabilityArraysEquivalent(lhs.sample_rates, lhs.num_sample_rates,
            (unsigned int) 0 /*replacedValue*/, &lhs, &rhs, audio_ports_are_equal);
}

TEST_P(SystemAudioPortTest, AudioPortV7EquivalentTest) {
    struct audio_port_v7 lhs;
    ASSERT_NO_FATAL_FAILURE(fillFakeAudioPortV7Info(&lhs, true /*containsExtraAudioDescriptor*/));
    struct audio_port_v7 rhs = lhs;
    ASSERT_TRUE(audio_ports_v7_are_equal(&lhs, &rhs));

    testAudioPortEquivalent(&lhs, &rhs, audio_ports_v7_are_equal);

    struct audio_profile emptyProfile = {};
    testAudioPortCapabilityArraysEquivalent(lhs.audio_profiles, lhs.num_audio_profiles,
            emptyProfile, &lhs, &rhs, audio_ports_v7_are_equal);
    auto& firstProfile = lhs.audio_profiles[0];
    testAudioPortCapabilityArraysEquivalent(firstProfile.sample_rates,
            firstProfile.num_sample_rates, (unsigned int) 0 /*replacedValue*/,
            &lhs, &rhs, audio_ports_v7_are_equal);
    testAudioPortCapabilityArraysEquivalent(firstProfile.channel_masks,
            firstProfile.num_channel_masks, AUDIO_CHANNEL_NONE,
            &lhs, &rhs, audio_ports_v7_are_equal);

    struct audio_extra_audio_descriptor emptyDesc = {};
    testAudioPortCapabilityArraysEquivalent(lhs.extra_audio_descriptors,
            lhs.num_extra_audio_descriptors, emptyDesc, &lhs, &rhs, audio_ports_v7_are_equal);
}

TEST_P(SystemAudioPortTest, AudioPortV7ConversionTest) {
    struct audio_port srcPort, dstPort = {};
    struct audio_port_v7 portV7;
    ASSERT_NO_FATAL_FAILURE(fillFakeAudioPortInfo(&srcPort));
    audio_populate_audio_port_v7(&srcPort, &portV7);
    ASSERT_TRUE(audio_populate_audio_port(&portV7, &dstPort));
    ASSERT_TRUE(audio_ports_are_equal(&srcPort, &dstPort));

    struct audio_port_v7 srcPortV7, dstPortV7 = {};
    struct audio_port audioPort;
    ASSERT_NO_FATAL_FAILURE(
            fillFakeAudioPortV7Info(&srcPortV7, false /*containsExtraAudioDescriptor*/));
    ASSERT_EQ(srcPortV7.num_audio_profiles, AUDIO_PORT_MAX_AUDIO_PROFILES);
    auto& profile = srcPortV7.audio_profiles[0];
    ASSERT_EQ(profile.num_channel_masks, AUDIO_PORT_MAX_CHANNEL_MASKS);
    // Set a channel mask that is not present in the list
    profile.channel_masks[0] = AUDIO_CHANNEL_NONE;
    ASSERT_FALSE(audio_populate_audio_port(&srcPortV7, &audioPort));
    audio_populate_audio_port_v7(&audioPort, &dstPortV7);
    ASSERT_EQ(dstPortV7.num_audio_profiles, AUDIO_PORT_MAX_AUDIO_PROFILES);
    // Do not compare audio profiles' information as the audio profiles will not be the
    // same after conversion from audio_port_v7->audio_port and audio_port->audio_port_v7
    srcPortV7.num_audio_profiles = 0;
    dstPortV7.num_audio_profiles = 0;
    ASSERT_TRUE(audio_ports_v7_are_equal(&srcPortV7, &dstPortV7));
}

TEST_P(SystemAudioPortTest, AudioPortV7ContainingExtraAudioDescriptorConversionTest) {
    struct audio_port_v7 srcPortV7, dstPortV7 = {};
    struct audio_port audioPort;
    ASSERT_NO_FATAL_FAILURE(
            fillFakeAudioPortV7Info(&srcPortV7, true /*containsExtraAudioDescriptor*/));
    ASSERT_FALSE(audio_populate_audio_port(&srcPortV7, &audioPort));
    audio_populate_audio_port_v7(&audioPort, &dstPortV7);
    ASSERT_FALSE(audio_ports_v7_are_equal(&srcPortV7, &dstPortV7));
}

INSTANTIATE_TEST_CASE_P(SystemAudioPortTest, SystemAudioPortTest,
        testing::Combine(
                testing::Values(AUDIO_PORT_ROLE_SOURCE,
                                AUDIO_PORT_ROLE_SINK),
                testing::Values(AUDIO_PORT_TYPE_DEVICE,
                                AUDIO_PORT_TYPE_MIX,
                                AUDIO_PORT_TYPE_SESSION))
        );
