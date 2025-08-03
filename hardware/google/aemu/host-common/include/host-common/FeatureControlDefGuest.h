// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file maintain a list of advanced features that can be switched on/off
// with feature control.
//
// The features in this file depend on system image builds. It needs to be
// enabled in BOTH system images and emulator to be actually enabled.
// To add system image independent features, please add them to
// FeatureControlDefHost.h
//
// To add a new item, please add a new line in the following format:
// FEATURE_CONTROL_ITEM(YOUR_FEATURE_NAME, idx)
// You will also need to edit its default value in the following two places:
// android/data/advancedFeatures.ini
// $(system_image)/development/sys-img/advancedFeatures.ini

// This file is supposed to be included multiple times. It should not have
// #pragma once here.

FEATURE_CONTROL_ITEM(GrallocSync, 47)
FEATURE_CONTROL_ITEM(EncryptUserData, 48)
FEATURE_CONTROL_ITEM(IntelPerformanceMonitoringUnit, 49)
FEATURE_CONTROL_ITEM(GLAsyncSwap, 50)
FEATURE_CONTROL_ITEM(GLDMA, 51)
FEATURE_CONTROL_ITEM(GLDMA2, 52)
FEATURE_CONTROL_ITEM(GLDirectMem, 53)
FEATURE_CONTROL_ITEM(GLESDynamicVersion, 54)
FEATURE_CONTROL_ITEM(Wifi, 55)
FEATURE_CONTROL_ITEM(PlayStoreImage, 56)
FEATURE_CONTROL_ITEM(LogcatPipe, 57)
FEATURE_CONTROL_ITEM(SystemAsRoot, 58)
FEATURE_CONTROL_ITEM(KernelDeviceTreeBlobSupport, 59)
FEATURE_CONTROL_ITEM(DynamicPartition, 60)
FEATURE_CONTROL_ITEM(RefCountPipe, 61)
FEATURE_CONTROL_ITEM(HostComposition, 62)
FEATURE_CONTROL_ITEM(WifiConfigurable, 63)
FEATURE_CONTROL_ITEM(VirtioInput, 64)
FEATURE_CONTROL_ITEM(MultiDisplay, 65)
FEATURE_CONTROL_ITEM(VulkanNullOptionalStrings, 66)
FEATURE_CONTROL_ITEM(YUV420888toNV21, 67)
FEATURE_CONTROL_ITEM(YUVCache, 68)
FEATURE_CONTROL_ITEM(KeycodeForwarding, 69)
FEATURE_CONTROL_ITEM(VulkanIgnoredHandles, 70)
FEATURE_CONTROL_ITEM(VirtioGpuNext, 71)
FEATURE_CONTROL_ITEM(Mac80211hwsimUserspaceManaged, 72)
FEATURE_CONTROL_ITEM(HardwareDecoder, 73)
FEATURE_CONTROL_ITEM(VirtioWifi, 74)
FEATURE_CONTROL_ITEM(ModemSimulator, 75)
FEATURE_CONTROL_ITEM(VirtioMouse, 76)
FEATURE_CONTROL_ITEM(VirtconsoleLogcat, 77)
FEATURE_CONTROL_ITEM(VirtioVsockPipe, 78)
FEATURE_CONTROL_ITEM(VulkanQueueSubmitWithCommands, 79)
FEATURE_CONTROL_ITEM(VulkanBatchedDescriptorSetUpdate, 80)
FEATURE_CONTROL_ITEM(Minigbm, 81)
FEATURE_CONTROL_ITEM(GnssGrpcV1, 82)
FEATURE_CONTROL_ITEM(AndroidbootProps, 83)
FEATURE_CONTROL_ITEM(AndroidbootProps2, 84)
FEATURE_CONTROL_ITEM(DeviceSkinOverlay, 85)
FEATURE_CONTROL_ITEM(BluetoothEmulation, 86)
FEATURE_CONTROL_ITEM(DeviceStateOnBoot, 87)
FEATURE_CONTROL_ITEM(HWCMultiConfigs, 88)
FEATURE_CONTROL_ITEM(VirtioSndCard, 89)
FEATURE_CONTROL_ITEM(VirtioTablet, 90)
FEATURE_CONTROL_ITEM(VsockSnapshotLoadFixed_b231345789, 91)
FEATURE_CONTROL_ITEM(DownloadableSnapshot, 92)
FEATURE_CONTROL_ITEM(SupportPixelFold, 96)
FEATURE_CONTROL_ITEM(DeviceKeyboardHasAssistKey, 97)
