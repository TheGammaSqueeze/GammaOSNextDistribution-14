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

#include <fuzzer/FuzzedDataProvider.h>

#include <string>

#include "btcore/include/module.h"
#include "esco_parameters.h"
#include "interop.h"
#include "interop_config.h"

using namespace std;
constexpr size_t kNumAddressOctets = 6;
constexpr size_t kMaxStringLength = 10;
constexpr interop_feature_t kInteropFeature[] = {
    interop_feature_t::INTEROP_DISABLE_LE_SECURE_CONNECTIONS,
    interop_feature_t::INTEROP_AUTO_RETRY_PAIRING,
    interop_feature_t::INTEROP_DISABLE_ABSOLUTE_VOLUME,
    interop_feature_t::INTEROP_DISABLE_AUTO_PAIRING,
    interop_feature_t::INTEROP_KEYBOARD_REQUIRES_FIXED_PIN,
    interop_feature_t::INTEROP_2MBPS_LINK_ONLY,
    interop_feature_t::INTEROP_DISABLE_SDP_AFTER_PAIRING,
    interop_feature_t::INTEROP_REMOVE_HID_DIG_DESCRIPTOR,
    interop_feature_t::INTEROP_DISABLE_SNIFF_DURING_SCO,
    interop_feature_t::INTEROP_HID_PREF_CONN_SUP_TIMEOUT_3S,
    interop_feature_t::INTEROP_GATTC_NO_SERVICE_CHANGED_IND,
    interop_feature_t::INTEROP_INCREASE_AG_CONN_TIMEOUT,
    interop_feature_t::INTEROP_DISABLE_LE_CONN_PREFERRED_PARAMS,
    interop_feature_t::INTEROP_DISABLE_AAC_CODEC,
    interop_feature_t::INTEROP_DISABLE_AAC_VBR_CODEC,
    interop_feature_t::INTEROP_ENABLE_AAC_CODEC,
    interop_feature_t::INTEROP_DISABLE_ROLE_SWITCH_POLICY,
    interop_feature_t::INTEROP_HFP_1_7_DENYLIST,
    interop_feature_t::INTEROP_HFP_1_8_DENYLIST,
    interop_feature_t::INTEROP_ADV_PBAP_VER_1_1,
    interop_feature_t::INTEROP_UPDATE_HID_SSR_MAX_LAT,
    interop_feature_t::INTEROP_DISABLE_AUTH_FOR_HID_POINTING,
    interop_feature_t::INTEROP_DISABLE_AVDTP_RECONFIGURE,
    interop_feature_t::INTEROP_DYNAMIC_ROLE_SWITCH,
    interop_feature_t::INTEROP_DISABLE_HF_INDICATOR,
    interop_feature_t::INTEROP_DISABLE_ROLE_SWITCH,
    interop_feature_t::INTEROP_DELAY_SCO_FOR_MT_CALL,
    interop_feature_t::INTEROP_DISABLE_CODEC_NEGOTIATION,
    interop_feature_t::INTEROP_DISABLE_PLAYER_APPLICATION_SETTING_CMDS,
    interop_feature_t::INTEROP_DISABLE_CONNECTION_AFTER_COLLISION,
    interop_feature_t::INTEROP_DISABLE_LE_CONN_UPDATES,
    interop_feature_t::INTEROP_ADV_PBAP_VER_1_2,
    interop_feature_t::INTEROP_DISABLE_PCE_SDP_AFTER_PAIRING,
    interop_feature_t::INTEROP_AVRCP_BROWSE_OPEN_CHANNEL_COLLISION,
    interop_feature_t::INTEROP_DISABLE_SNIFF_LINK_DURING_SCO,
    interop_feature_t::INTEROP_DISABLE_SNIFF_DURING_CALL,
    interop_feature_t::INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL,
    interop_feature_t::INTEROP_DISABLE_REFRESH_ACCEPT_SIG_TIMER,
    interop_feature_t::INTEROP_BROWSE_PLAYER_ALLOW_LIST,
    interop_feature_t::INTEROP_SKIP_INCOMING_STATE,
    interop_feature_t::INTEROP_NOT_UPDATE_AVRCP_PAUSED_TO_REMOTE,
    interop_feature_t::
        INTEROP_PHONE_POLICY_INCREASED_DELAY_CONNECT_OTHER_PROFILES,
    interop_feature_t::INTEROP_DISABLE_NAME_REQUEST,
    interop_feature_t::INTEROP_AVRCP_1_4_ONLY,
    interop_feature_t::INTEROP_DISABLE_SNIFF,
    interop_feature_t::INTEROP_DISABLE_AVDTP_SUSPEND,
    interop_feature_t::INTEROP_SLC_SKIP_BIND_COMMAND,
    interop_feature_t::INTEROP_AVRCP_1_3_ONLY,
    interop_feature_t::
        INTEROP_PHONE_POLICY_REDUCED_DELAY_CONNECT_OTHER_PROFILES,
    interop_feature_t::INTEROP_HFP_FAKE_INCOMING_CALL_INDICATOR,
    interop_feature_t::INTEROP_HFP_SEND_CALL_INDICATORS_BACK_TO_BACK,
    interop_feature_t::INTEROP_SETUP_SCO_WITH_NO_DELAY_AFTER_SLC_DURING_CALL,
    interop_feature_t::INTEROP_ENABLE_PREFERRED_CONN_PARAMETER,
    interop_feature_t::INTEROP_RETRY_SCO_AFTER_REMOTE_REJECT_SCO,
    interop_feature_t::INTEROP_DELAY_SCO_FOR_MO_CALL,
    interop_feature_t::INTEROP_CHANGE_HID_VID_PID,
    interop_feature_t::INTEROP_DISABLE_ROLE_SWITCH_DURING_CONNECTION,
    interop_feature_t::INTEROP_DISABLE_ROBUST_CACHING,
    interop_feature_t::INTEROP_HFP_1_7_ALLOWLIST,
    interop_feature_t::INTEROP_IGNORE_DISC_BEFORE_SIGNALLING_TIMEOUT,
};
constexpr esco_codec_t kEscoCodec[] = {
    esco_codec_t::SCO_CODEC_CVSD_D1,  esco_codec_t::ESCO_CODEC_CVSD_S3,
    esco_codec_t::ESCO_CODEC_CVSD_S4, esco_codec_t::ESCO_CODEC_MSBC_T1,
    esco_codec_t::ESCO_CODEC_MSBC_T2, esco_codec_t::ESCO_CODEC_LC3_T1,
    esco_codec_t::ESCO_CODEC_LC3_T2,
};

void generateString(FuzzedDataProvider& fdp, string& addressString) {
  addressString.clear();
  if (fdp.ConsumeBool()) {
    for (size_t i = 0; i < kNumAddressOctets; ++i) {
      addressString.append(fdp.ConsumeBytesAsString(sizeof(uint8_t)));
      if (i != kNumAddressOctets - 1) {
        addressString.append(":");
      }
    }
  } else {
    addressString = fdp.ConsumeRandomLengthString(kMaxStringLength);
  }
}

extern module_t interop_module;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider mFuzzedDataProvider = FuzzedDataProvider(data, size);
  RawAddress fuzzAddress;
  string addressString;
  module_init(&interop_module);

  while (mFuzzedDataProvider.remaining_bytes()) {
    auto invokeBtDeviceApi = mFuzzedDataProvider.PickValueInArray<
        const std::function<void()>>({
        [&]() {
          generateString(mFuzzedDataProvider, addressString);
          RawAddress::FromString(addressString, fuzzAddress);
          interop_match_addr(mFuzzedDataProvider.PickValueInArray(
                                 kInteropFeature) /* feature */,
                             &fuzzAddress);
        },
        [&]() {
          interop_match_name(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider.ConsumeRandomLengthString(kMaxStringLength)
                  .c_str() /* name */
          );
        },
        [&]() {
          interop_match_manufacturer(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider.ConsumeIntegral<int32_t>() /* manufacturer */
          );
        },
        [&]() {
          interop_match_vendor_product_ids(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider.ConsumeIntegral<int16_t>() /* vendor_id */,
              mFuzzedDataProvider.ConsumeIntegral<int16_t>() /* product_id */
          );
        },
        [&]() {
          generateString(mFuzzedDataProvider, addressString);
          RawAddress::FromString(addressString, fuzzAddress);
          interop_database_add(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              &fuzzAddress,
              mFuzzedDataProvider.ConsumeIntegralInRange<int32_t>(
                  1, RawAddress::kLength - 1) /* length */
          );
        },
        [&]() { interop_database_clear(); },
        [&]() {
          interop_database_match_version(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider.ConsumeIntegral<int32_t>() /* version */
          );
        },
        [&]() {
          generateString(mFuzzedDataProvider, addressString);
          RawAddress::FromString(addressString, fuzzAddress);
          uint16_t max_lat = 0;
          interop_match_addr_get_max_lat(mFuzzedDataProvider.PickValueInArray(
                                             kInteropFeature) /* feature */,
                                         &fuzzAddress, &max_lat);
        },
        [&]() {
          generateString(mFuzzedDataProvider, addressString);
          RawAddress::FromString(addressString, fuzzAddress);
          interop_feature_name_to_feature_id(addressString.c_str());
        },
        [&]() {
          esco_parameters_for_codec(
              mFuzzedDataProvider.PickValueInArray(kEscoCodec) /* codec */,
              true);
        },
        [&]() {
          interop_database_add_manufacturer(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider
                  .ConsumeIntegral<uint16_t>() /* manufacturer */);
        },
        [&]() {
          interop_database_add_vndr_prdt(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider.ConsumeIntegral<uint16_t>() /* vendor_id */,
              mFuzzedDataProvider.ConsumeIntegral<uint16_t>() /* product_id */);
        },
        [&]() {
          generateString(mFuzzedDataProvider, addressString);
          RawAddress::FromString(addressString, fuzzAddress);
          interop_database_add_addr_max_lat(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              &fuzzAddress,
              mFuzzedDataProvider.ConsumeIntegral<uint16_t>() /* max_lat */);
        },
        [&]() {
          interop_database_add_version(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider.ConsumeIntegral<uint16_t>() /* version */);
        },
        [&]() {
          interop_database_add_addr_lmp_version(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              &fuzzAddress,
              mFuzzedDataProvider.ConsumeIntegral<uint8_t>() /* lmp_ver */,
              mFuzzedDataProvider
                  .ConsumeIntegral<uint16_t>() /* lmp_sub_ver */);
        },
        [&]() {
          uint8_t lmp_ver = 0;
          uint16_t lmp_sub_ver = 0;
          interop_database_match_addr_get_lmp_ver(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              &fuzzAddress, &lmp_ver, &lmp_sub_ver);
        },
        [&]() {
          interop_database_remove_manufacturer(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider
                  .ConsumeIntegral<uint16_t>() /* manufacturer */);
        },
        [&]() {
          interop_database_remove_vndr_prdt(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider.ConsumeIntegral<uint16_t>() /* vendor_id */,
              mFuzzedDataProvider.ConsumeIntegral<uint16_t>() /* product_id */);
        },
        [&]() {
          generateString(mFuzzedDataProvider, addressString);
          RawAddress::FromString(addressString, fuzzAddress);
          interop_database_remove_addr_max_lat(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              &fuzzAddress,
              mFuzzedDataProvider.ConsumeIntegral<uint16_t>() /* max_lat */);
        },
        [&]() {
          interop_database_remove_version(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              mFuzzedDataProvider.ConsumeIntegral<uint16_t>() /*version*/);
        },
        [&]() {
          interop_database_remove_addr_lmp_version(
              mFuzzedDataProvider.PickValueInArray(
                  kInteropFeature) /* feature */,
              &fuzzAddress,
              mFuzzedDataProvider.ConsumeIntegral<uint8_t>() /* lmp_ver */,
              mFuzzedDataProvider
                  .ConsumeIntegral<uint16_t>() /* lmp_sub_ver */);
        },
    });
    invokeBtDeviceApi();
  }
  module_clean_up(&interop_module);
  return 0;
}
