// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Name of java classes for UWB response and notifications:
pub(crate) const CONFIG_STATUS_DATA_CLASS: &str = "com/android/server/uwb/data/UwbConfigStatusData";
pub(crate) const MULTICAST_LIST_UPDATE_STATUS_CLASS: &str =
    "com/android/server/uwb/data/UwbMulticastListUpdateStatus";
pub(crate) const POWER_STATS_CLASS: &str = "com/android/server/uwb/info/UwbPowerStats";
pub(crate) const TLV_DATA_CLASS: &str = "com/android/server/uwb/data/UwbTlvData";
pub(crate) const UWB_DEVICE_INFO_RESPONSE_CLASS: &str =
    "com/android/server/uwb/data/UwbDeviceInfoResponse";
pub(crate) const UWB_RANGING_DATA_CLASS: &str = "com/android/server/uwb/data/UwbRangingData";
pub(crate) const UWB_TWO_WAY_MEASUREMENT_CLASS: &str =
    "com/android/server/uwb/data/UwbTwoWayMeasurement";
pub(crate) const UWB_OWR_AOA_MEASUREMENT_CLASS: &str =
    "com/android/server/uwb/data/UwbOwrAoaMeasurement";
pub(crate) const VENDOR_RESPONSE_CLASS: &str = "com/android/server/uwb/data/UwbVendorUciResponse";
pub(crate) const DT_RANGING_ROUNDS_STATUS_CLASS: &str =
    "com/android/server/uwb/data/DtTagUpdateRangingRoundsStatus";
pub(crate) const UWB_DL_TDOA_MEASUREMENT_CLASS: &str =
    "com/android/server/uwb/data/UwbDlTDoAMeasurement";
pub(crate) const UWB_RADAR_DATA_CLASS: &str = "com/android/server/uwb/data/UwbRadarData";
pub(crate) const UWB_RADAR_SWEEP_DATA_CLASS: &str = "com/android/server/uwb/data/UwbRadarSweepData";
