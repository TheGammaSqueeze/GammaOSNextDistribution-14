/*
 * Copyright 2023 The Android Open Source Project
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

#include <gtest/gtest.h>

#include <tuple>
#include <vector>

#include "bta/ag/bta_ag_int.h"
#include "bta/include/bta_le_audio_api.h"
#include "stack/btm/btm_int_types.h"
#include "test/mock/mock_device_esco_parameters.h"

bool btm_peer_supports_esco_ev3(const RawAddress& remote_bda) { return true; }
tBTM_CB btm_cb;
LeAudioClient* LeAudioClient::Get() { return nullptr; }
bool LeAudioClient::IsLeAudioClientInStreaming() { return false; }

const RawAddress kRawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});

class BtaAgScoParameterSelectionTest
    : public ::testing::TestWithParam<
          std::tuple<tBTA_AG_FEAT, tBTA_AG_PEER_FEAT, bool>> {
 protected:
  void SetUp() override {
    test::mock::device_esco_parameters::esco_parameters_for_codec.body =
        [this](esco_codec_t codec) {
          this->codec = codec;
          return enh_esco_params_t{};
        };
  }
  void TearDown() override {
    test::mock::device_esco_parameters::esco_parameters_for_codec = {};
  }
  esco_codec_t codec;
};

TEST_P(BtaAgScoParameterSelectionTest, create_sco_cvsd) {
  bta_ag_api_set_active_device(kRawAddress);

  const auto [feature, peer_feature, is_local] = GetParam();
  tBTA_AG_SCB scb{
      .peer_addr = kRawAddress,
      .features = feature,
      .peer_features = peer_feature,
      .sco_idx = BTM_INVALID_SCO_INDEX,
      .inuse_codec = UUID_CODEC_CVSD,
  };

  this->codec = ESCO_CODEC_UNKNOWN;
  bta_ag_create_sco(&scb, is_local);
  if ((scb.features & BTA_AG_FEAT_ESCO_S4) &&
      (scb.peer_features & BTA_AG_PEER_FEAT_ESCO_S4)) {
    ASSERT_EQ(this->codec, ESCO_CODEC_CVSD_S4);
  } else {
    ASSERT_EQ(this->codec, ESCO_CODEC_CVSD_S3);
  }
}

TEST_P(BtaAgScoParameterSelectionTest, create_pending_sco_cvsd) {
  bta_ag_api_set_active_device(kRawAddress);

  const auto [feature, peer_feature, is_local] = GetParam();
  tBTA_AG_SCB scb{
      .peer_addr = kRawAddress,
      .features = feature,
      .peer_features = peer_feature,
      .sco_idx = BTM_INVALID_SCO_INDEX,
      .inuse_codec = UUID_CODEC_CVSD,
  };

  this->codec = ESCO_CODEC_UNKNOWN;
  bta_ag_create_pending_sco(&scb, is_local);
  if ((scb.features & BTA_AG_FEAT_ESCO_S4) &&
      (scb.peer_features & BTA_AG_PEER_FEAT_ESCO_S4)) {
    ASSERT_EQ(this->codec, ESCO_CODEC_CVSD_S4);
  } else {
    ASSERT_EQ(this->codec, ESCO_CODEC_CVSD_S3);
  }
}

std::vector<std::tuple<tBTA_AG_FEAT, tBTA_AG_PEER_FEAT, bool>>
BtaAgScoParameterSelectionTestParameters() {
  tBTA_AG_FEAT features[] = {0, BTA_AG_FEAT_ESCO_S4};
  tBTA_AG_PEER_FEAT peer_features[] = {0, BTA_AG_PEER_FEAT_ESCO_S4};
  bool is_local_or_orig[] = {false, true};
  std::vector<std::tuple<tBTA_AG_FEAT, tBTA_AG_PEER_FEAT, bool>> params;

  for (auto i : features) {
    for (auto j : peer_features) {
      for (auto k : is_local_or_orig) {
        params.push_back({i, j, k});
      }
    }
  }
  return params;
}

INSTANTIATE_TEST_SUITE_P(
    BtaAgScoParameterSelectionTests, BtaAgScoParameterSelectionTest,
    ::testing::ValuesIn(BtaAgScoParameterSelectionTestParameters()));
