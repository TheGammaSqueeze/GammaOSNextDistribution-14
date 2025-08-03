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
// The features in this file should be independent from system image builds.
// To add system image dependent features, please add them to
// FeatureControlDefGuest.h
//
// To add a new item, please add a new line in the following format:
// FEATURE_CONTROL_ITEM(YOUR_FEATURE_NAME , enum)
// You will also need to edit android/data/advancedFeatures.ini to set its
// default value.

// This file is supposed to be included multiple times. It should not have
// #pragma once here.

FEATURE_CONTROL_ITEM(GLPipeChecksum, 0)
FEATURE_CONTROL_ITEM(ForceANGLE, 1)
FEATURE_CONTROL_ITEM(ForceSwiftshader, 2)
// TODO(lpetrut): ensure that WHPX can be requested as an advanced feature.
// We may rename the feature name from HYPERV to WHPX as that's the accelerator
// name.
FEATURE_CONTROL_ITEM(HYPERV, 3)
FEATURE_CONTROL_ITEM(HVF, 4)
FEATURE_CONTROL_ITEM(KVM, 5)
FEATURE_CONTROL_ITEM(HAXM, 6)
FEATURE_CONTROL_ITEM(FastSnapshotV1, 7)
FEATURE_CONTROL_ITEM(ScreenRecording, 8)
FEATURE_CONTROL_ITEM(VirtualScene, 9)
FEATURE_CONTROL_ITEM(VideoPlayback, 10)
FEATURE_CONTROL_ITEM(GenericSnapshotsUI, 11)
FEATURE_CONTROL_ITEM(AllowSnapshotMigration, 12)
FEATURE_CONTROL_ITEM(WindowsOnDemandSnapshotLoad, 13)
FEATURE_CONTROL_ITEM(WindowsHypervisorPlatform, 14)
FEATURE_CONTROL_ITEM(LocationUiV2, 15)
FEATURE_CONTROL_ITEM(SnapshotAdb, 16)
FEATURE_CONTROL_ITEM(QuickbootFileBacked, 17)
FEATURE_CONTROL_ITEM(Offworld, 18)
FEATURE_CONTROL_ITEM(OffworldDisableSecurity, 19)
FEATURE_CONTROL_ITEM(OnDemandSnapshotLoad, 20)
FEATURE_CONTROL_ITEM(Vulkan, 21)
FEATURE_CONTROL_ITEM(MacroUi, 22)
FEATURE_CONTROL_ITEM(IpDisconnectOnLoad, 23)
FEATURE_CONTROL_ITEM(HasSharedSlotsHostMemoryAllocator, 24)
FEATURE_CONTROL_ITEM(CarVHalTable, 25)
FEATURE_CONTROL_ITEM(VulkanSnapshots, 26)
FEATURE_CONTROL_ITEM(DynamicMediaProfile, 27)
FEATURE_CONTROL_ITEM(CarVhalReplay, 28)
FEATURE_CONTROL_ITEM(NoDelayCloseColorBuffer, 29)
FEATURE_CONTROL_ITEM(NoDeviceFrame, 30)
FEATURE_CONTROL_ITEM(VirtioGpuNativeSync, 31)
FEATURE_CONTROL_ITEM(VulkanShaderFloat16Int8, 32)
FEATURE_CONTROL_ITEM(CarRotary, 33)
FEATURE_CONTROL_ITEM(TvRemote, 34)
FEATURE_CONTROL_ITEM(NativeTextureDecompression, 35)
FEATURE_CONTROL_ITEM(GuestUsesAngle, 36)
FEATURE_CONTROL_ITEM(VulkanNativeSwapchain, 37)
FEATURE_CONTROL_ITEM(VirtioGpuFenceContexts, 38)
FEATURE_CONTROL_ITEM(AsyncComposeSupport, 39)
FEATURE_CONTROL_ITEM(NoDraw, 40)
FEATURE_CONTROL_ITEM(MigratableSnapshotSave, 41)
FEATURE_CONTROL_ITEM(VulkanAstcLdrEmulation, 42)
FEATURE_CONTROL_ITEM(VulkanYcbcrEmulation, 43)
FEATURE_CONTROL_ITEM(VulkanEtc2Emulation, 44)
FEATURE_CONTROL_ITEM(ExternalBlob, 45)
FEATURE_CONTROL_ITEM(SystemBlob, 46)
FEATURE_CONTROL_ITEM(NetsimWebUi, 93)
FEATURE_CONTROL_ITEM(NetsimCliUi, 94)
FEATURE_CONTROL_ITEM(WiFiPacketStream, 95)
FEATURE_CONTROL_ITEM(VulkanAllocateDeviceMemoryOnly, 98)
FEATURE_CONTROL_ITEM(VulkanAllocateHostMemory, 99)
