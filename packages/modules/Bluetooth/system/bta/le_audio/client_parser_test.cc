/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "client_parser.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "le_audio_types.h"

namespace le_audio {
namespace client_parser {
namespace pacs {

TEST(LeAudioClientParserTest, testParsePacsInvalidLength) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t invalid_num_records[] = {0x01};
  ASSERT_FALSE(
      ParsePacs(pac_recs, sizeof(invalid_num_records), invalid_num_records));

  const uint8_t no_caps_len[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x02,
      0x03,
      0x04,
      0x05,
  };
  ASSERT_FALSE(ParsePacs(pac_recs, sizeof(no_caps_len), no_caps_len));

  const uint8_t no_metalen[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x02,
      0x03,
      0x04,
      0x05,
      // Codec Spec. Caps. Len
      0x00,
  };
  ASSERT_FALSE(ParsePacs(pac_recs, sizeof(no_metalen), no_metalen));
}

TEST(LeAudioClientParserTest, testParsePacsEmpty) {
  std::vector<struct types::acs_ac_record> pac_recs;
  const uint8_t value[] = {0x00};

  ASSERT_TRUE(ParsePacs(pac_recs, sizeof(value), value));
}

TEST(LeAudioClientParserTest, testParsePacsEmptyCapsEmptyMeta) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t value[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x03,
      0x02,
      0x05,
      0x04,
      // Codec Spec. Caps. Len
      0x00,
      // Metadata Length
      0x00,
  };
  ASSERT_TRUE(ParsePacs(pac_recs, sizeof(value), value));

  ASSERT_EQ(pac_recs.size(), 1u);
  ASSERT_EQ(pac_recs[0].codec_id.coding_format, 0x01u);
  ASSERT_EQ(pac_recs[0].codec_id.vendor_company_id, 0x0203u);
  ASSERT_EQ(pac_recs[0].codec_id.vendor_codec_id, 0x0405u);
}

TEST(LeAudioClientParserTest, testParsePacsInvalidCapsLen) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t bad_capslem[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x03,
      0x02,
      0x05,
      0x04,
      // Codec Spec. Caps. Len
      0x05,
      // Codec Spec. Caps.
      0x02,  // [0].length,
      0x02,  // [0].type,
      0x03,  // [0].value[0]
      0x03,  // [1].length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
      // Metadata Length
      0x00,
  };
  ASSERT_FALSE(ParsePacs(pac_recs, sizeof(bad_capslem), bad_capslem));

  std::vector<struct types::acs_ac_record> pac_recs2;

  const uint8_t bad_capslen2[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x03,
      0x02,
      0x05,
      0x04,
      // Codec Spec. Caps. Len
      0x20,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
      0x03,  // [1].length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
      // Metadata Length
      0x00,
  };
  ASSERT_FALSE(ParsePacs(pac_recs2, sizeof(bad_capslen2), bad_capslen2));
}

TEST(LeAudioClientParserTest, testParsePacsInvalidCapsLtvLen) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t bad_ltv_len[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x03,
      0x02,
      0x05,
      0x04,
      // Codec Spec. Caps. Len
      0x07,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
      0x06,  // [1].bad_length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
      // Metadata Length
      0x00,
  };
  ASSERT_FALSE(ParsePacs(pac_recs, sizeof(bad_ltv_len), bad_ltv_len));

  const uint8_t bad_ltv_len2[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x03,
      0x02,
      0x05,
      0x04,
      // Codec Spec. Caps. Len
      0x07,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
      0x04,  // [1].bad_length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
      // Metadata Length
      0x00,
  };
  ASSERT_FALSE(ParsePacs(pac_recs, sizeof(bad_ltv_len2), bad_ltv_len2));
}

TEST(LeAudioClientParserTest, testParsePacsNullLtv) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t value[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x03,
      0x02,
      0x05,
      0x04,
      // Codec Spec. Caps. Len
      0x0A,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
      0x03,  // [1].length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
      0x01,  // [2].length <-- a capability without a value
      0x04,  // [2].type
      0x00,  // [3]length <-- this seems possible although useless
      // Metadata Length
      0x00,
  };
  ASSERT_TRUE(ParsePacs(pac_recs, sizeof(value), value));

  ASSERT_EQ(pac_recs.size(), 1u);
  ASSERT_EQ(pac_recs[0].codec_id.coding_format, 0x01u);
  ASSERT_EQ(pac_recs[0].codec_id.vendor_company_id, 0x0203u);
  ASSERT_EQ(pac_recs[0].codec_id.vendor_codec_id, 0x0405u);

  auto codec_spec_caps = pac_recs[0].codec_spec_caps.Values();
  ASSERT_EQ(codec_spec_caps.size(), 3u);
  ASSERT_EQ(codec_spec_caps.count(0x02u), 1u);
  ASSERT_EQ(codec_spec_caps[0x02u].size(), 1u);
  ASSERT_EQ(codec_spec_caps[0x02u][0], 0x03u);
  ASSERT_EQ(codec_spec_caps.count(0x03u), 1u);
  ASSERT_EQ(codec_spec_caps[0x03u].size(), 2u);
  ASSERT_EQ(codec_spec_caps[0x03u][0], 0x04u);
  ASSERT_EQ(codec_spec_caps[0x03u][1], 0x05u);
  ASSERT_EQ(codec_spec_caps.count(0x04u), 1u);
  ASSERT_EQ(codec_spec_caps[0x04u].size(), 0u);
}

TEST(LeAudioClientParserTest, testParsePacsEmptyMeta) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t value[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01,
      0x03,
      0x02,
      0x05,
      0x04,
      // Codec Spec. Caps. Len
      0x07,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
      0x03,  // [1].length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
      // Metadata Length
      0x00,
  };
  ASSERT_TRUE(ParsePacs(pac_recs, sizeof(value), value));

  ASSERT_EQ(pac_recs.size(), 1u);
  ASSERT_EQ(pac_recs[0].codec_id.coding_format, 0x01u);
  ASSERT_EQ(pac_recs[0].codec_id.vendor_company_id, 0x0203u);
  ASSERT_EQ(pac_recs[0].codec_id.vendor_codec_id, 0x0405u);

  auto codec_spec_caps = pac_recs[0].codec_spec_caps.Values();
  ASSERT_EQ(codec_spec_caps.size(), 2u);
  ASSERT_EQ(codec_spec_caps.count(0x02u), 1u);
  ASSERT_EQ(codec_spec_caps[0x02u].size(), 1u);
  ASSERT_EQ(codec_spec_caps[0x02u][0], 0x03u);
  ASSERT_EQ(codec_spec_caps.count(0x03u), 1u);
  ASSERT_EQ(codec_spec_caps[0x03u].size(), 2u);
  ASSERT_EQ(codec_spec_caps[0x03u][0], 0x04u);
  ASSERT_EQ(codec_spec_caps[0x03u][1], 0x05u);
}

TEST(LeAudioClientParserTest, testParsePacsInvalidMetaLength) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t value[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01, 0x03, 0x02, 0x05, 0x04,
      // Codec Spec. Caps. Len
      0x07,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
      0x03,  // [1].length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
             // Metadata Length
      0x05,
      // Metadata
      0x03,  // [0].length
      0x02,  // [0].type
      0x01,  // [0].value[0]
      0x00,  // [0].value[1]
  };
  ASSERT_FALSE(ParsePacs(pac_recs, sizeof(value), value));
}

TEST(LeAudioClientParserTest, testParsePacsValidMeta) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t value[] = {
      // Num records
      0x01,
      // Codec_ID
      0x01, 0x03, 0x02, 0x05, 0x04,
      // Codec Spec. Caps. Len
      0x07,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
      0x03,  // [1].length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
             // Metadata Length
      0x04,
      // Metadata
      0x03,  // [0].length
      0x02,  // [0].type
      0x01,  // [0].value[0]
      0x00,  // [0].value[1]
  };
  ASSERT_TRUE(ParsePacs(pac_recs, sizeof(value), value));

  ASSERT_EQ(pac_recs.size(), 1u);
  ASSERT_EQ(pac_recs[0].codec_id.coding_format, 0x01u);
  ASSERT_EQ(pac_recs[0].codec_id.vendor_company_id, 0x0203u);
  ASSERT_EQ(pac_recs[0].codec_id.vendor_codec_id, 0x0405u);

  auto codec_spec_caps = pac_recs[0].codec_spec_caps.Values();
  ASSERT_EQ(codec_spec_caps.size(), 2u);
  ASSERT_EQ(codec_spec_caps.count(0x02u), 1u);
  ASSERT_EQ(codec_spec_caps[0x02u].size(), 1u);
  ASSERT_EQ(codec_spec_caps[0x02u][0], 0x03u);
  ASSERT_EQ(codec_spec_caps.count(0x03u), 1u);
  ASSERT_EQ(codec_spec_caps[0x03u].size(), 2u);
  ASSERT_EQ(codec_spec_caps[0x03u][0], 0x04u);
  ASSERT_EQ(codec_spec_caps[0x03u][1], 0x05u);

  ASSERT_EQ(pac_recs[0].metadata.size(), 4u);
  ASSERT_EQ(pac_recs[0].metadata[0], 0x03u);
  ASSERT_EQ(pac_recs[0].metadata[1], 0x02u);
  ASSERT_EQ(pac_recs[0].metadata[2], 0x01u);
  ASSERT_EQ(pac_recs[0].metadata[3], 0x00u);
}

TEST(LeAudioClientParserTest, testParsePacsInvalidNumRecords) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t value[] = {
      // Num records
      0x02,
      // Codec_ID
      0x01, 0x03, 0x02, 0x05, 0x04,
      // Codec Spec. Caps. Len
      0x07,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
      0x03,  // [1].length
      0x03,  // [1].type
      0x04,  // [1].value[0]
      0x05,  // [1].value[1]
             // Metadata Length
      0x04,
      // Metadata
      0x03,  // [0].length
      0x02,  // [0].type
      0x01,  // [0].value[0]
      0x00,  // [0].value[1]
  };
  ASSERT_FALSE(ParsePacs(pac_recs, sizeof(value), value));
}

TEST(LeAudioClientParserTest, testParsePacsMultipleRecords) {
  std::vector<struct types::acs_ac_record> pac_recs;

  const uint8_t value[] = {
      // Num records
      0x03,
      // Codec_ID
      0x01, 0x03, 0x02, 0x05, 0x04,
      // Codec Spec. Caps. Len
      0x00,
      // Metadata Length
      0x00,
      // Codec_ID
      0x06, 0x08, 0x07, 0x0A, 0x09,
      // Codec Spec. Caps. Len
      0x03,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x02,  // [0].type
      0x03,  // [0].value[0]
             // Metadata Length
      0x04,
      // Metadata
      0x03,  // [0].length
      0x02,  // [0].type
      0x01,  // [0].value[0]
      0x00,  // [0].value[1],
             // Codec_ID
      0x11, 0x13, 0x12, 0x15, 0x14,
      // Codec Spec. Caps. Len
      0x07,
      // Codec Spec. Caps.
      0x02,  // [0].length
      0x12,  // [0].type
      0x13,  // [0].value[0]
      0x03,  // [1].length
      0x13,  // [1].type
      0x14,  // [1].value[0]
      0x15,  // [1].value[1]
             // Metadata Length
      0x04,
      // Metadata
      0x03,  // [0].length
      0x12,  // [0].type
      0x11,  // [0].value[0]
      0x10,  // [0].value[1]
  };
  ASSERT_TRUE(ParsePacs(pac_recs, sizeof(value), value));
  ASSERT_EQ(pac_recs.size(), 3u);

  // Verify 1st record
  auto& record0 = pac_recs[0];

  ASSERT_EQ(record0.codec_id.coding_format, 0x01u);
  ASSERT_EQ(record0.codec_id.vendor_company_id, 0x0203u);
  ASSERT_EQ(record0.codec_id.vendor_codec_id, 0x0405u);
  ASSERT_EQ(record0.codec_spec_caps.Size(), 0u);
  ASSERT_EQ(record0.metadata.size(), 0u);

  // Verify 2nd record
  auto& record1 = pac_recs[1];

  ASSERT_EQ(record1.codec_id.coding_format, 0x06u);
  ASSERT_EQ(record1.codec_id.vendor_company_id, 0x0708u);
  ASSERT_EQ(record1.codec_id.vendor_codec_id, 0x090Au);

  auto codec_spec_caps1 = record1.codec_spec_caps.Values();
  ASSERT_EQ(codec_spec_caps1.size(), 1u);
  ASSERT_EQ(codec_spec_caps1.count(0x02u), 1u);
  ASSERT_EQ(codec_spec_caps1[0x02u].size(), 1u);
  ASSERT_EQ(codec_spec_caps1[0x02u][0], 0x03u);

  ASSERT_EQ(record1.metadata.size(), 4u);
  ASSERT_EQ(record1.metadata[0], 0x03u);
  ASSERT_EQ(record1.metadata[1], 0x02u);
  ASSERT_EQ(record1.metadata[2], 0x01u);
  ASSERT_EQ(record1.metadata[3], 0x00u);

  // Verify 3rd record
  auto& record2 = pac_recs[2];

  ASSERT_EQ(record2.codec_id.coding_format, 0x11u);
  ASSERT_EQ(record2.codec_id.vendor_company_id, 0x1213u);
  ASSERT_EQ(record2.codec_id.vendor_codec_id, 0x1415u);

  auto codec_spec_caps2 = record2.codec_spec_caps.Values();
  ASSERT_EQ(codec_spec_caps2.size(), 2u);
  ASSERT_EQ(codec_spec_caps2.count(0x12u), 1u);
  ASSERT_EQ(codec_spec_caps2[0x12u].size(), 1u);
  ASSERT_EQ(codec_spec_caps2[0x12u][0], 0x13u);
  ASSERT_EQ(codec_spec_caps2.count(0x13u), 1u);
  ASSERT_EQ(codec_spec_caps2[0x13u].size(), 2u);
  ASSERT_EQ(codec_spec_caps2[0x13u][0], 0x14u);
  ASSERT_EQ(codec_spec_caps2[0x13u][1], 0x15u);

  ASSERT_EQ(record2.metadata.size(), 4u);
  ASSERT_EQ(record2.metadata[0], 0x03u);
  ASSERT_EQ(record2.metadata[1], 0x12u);
  ASSERT_EQ(record2.metadata[2], 0x11u);
  ASSERT_EQ(record2.metadata[3], 0x10u);
}

TEST(LeAudioClientParserTest, testParseAudioLocationsInvalidLength) {
  types::AudioLocations locations = codec_spec_conf::kLeAudioLocationNotAllowed;
  const uint8_t value1[] = {
      0x01,
      0x02,
      0x03,
  };
  ParseAudioLocations(locations, sizeof(value1), value1);
  ASSERT_EQ(locations, 0u);

  const uint8_t value2[] = {0x01, 0x02, 0x03, 0x04, 0x05};
  ParseAudioLocations(locations, sizeof(value2), value2);
  ASSERT_EQ(locations, 0u);
}

TEST(LeAudioClientParserTest, testParseAudioLocations) {
  types::AudioLocations locations = codec_spec_conf::kLeAudioLocationNotAllowed;
  const uint8_t value1[] = {0x01, 0x02, 0x03, 0x04};
  ParseAudioLocations(locations, sizeof(value1), value1);
  ASSERT_EQ(locations, 0x04030201u);
}

TEST(LeAudioClientParserTest, testParseAvailableAudioContextsInvalidLength) {
  types::BidirectionalPair<types::AudioContexts> avail_contexts;
  const uint8_t value1[] = {
      // Sink available contexts
      0x01, 0x02,
      // Missing Source available contexts
  };

  ParseAvailableAudioContexts(avail_contexts, sizeof(value1), value1);
  ASSERT_EQ(avail_contexts.sink.value(), 0u);
  ASSERT_EQ(avail_contexts.source.value(), 0u);
}

TEST(LeAudioClientParserTest, testParseAvailableAudioContexts) {
  types::BidirectionalPair<types::AudioContexts> avail_contexts;
  const uint8_t value1[] = {
      // Sink available contexts
      0x01,
      0x02,
      // Source available contexts
      0x03,
      0x04,
  };

  ParseAvailableAudioContexts(avail_contexts, sizeof(value1), value1);
  ASSERT_EQ(avail_contexts.sink.value(), 0x0201u);
  ASSERT_EQ(avail_contexts.source.value(), 0x0403u);
}

TEST(LeAudioClientParserTest, testParseSupportedAudioContextsInvalidLength) {
  types::BidirectionalPair<types::AudioContexts> supp_contexts;
  const uint8_t value1[] = {
      // Sink supported contexts
      0x01, 0x02,
      // Missing Source supported contexts
  };

  ParseSupportedAudioContexts(supp_contexts, sizeof(value1), value1);
  ASSERT_EQ(supp_contexts.sink.value(), 0u);
  ASSERT_EQ(supp_contexts.source.value(), 0u);
}

TEST(LeAudioClientParserTest, testParseSupportedAudioContexts) {
  types::BidirectionalPair<types::AudioContexts> supp_contexts;
  const uint8_t value1[] = {
      // Sink supported contexts
      0x01,
      0x02,
      // Source supported contexts
      0x03,
      0x04,
  };

  ParseSupportedAudioContexts(supp_contexts, sizeof(value1), value1);
  ASSERT_EQ(supp_contexts.sink.value(), 0x0201u);
  ASSERT_EQ(supp_contexts.source.value(), 0x0403u);
}

}  // namespace pacs

namespace ascs {

TEST(LeAudioClientParserTest, testParseAseStatusHeaderInvalidLength) {
  ase_rsp_hdr arh;
  const uint8_t value1[] = {
      // Ase ID
      0x01,
      // ASE State is missing here
  };
  ASSERT_FALSE(ParseAseStatusHeader(arh, sizeof(value1), value1));
}

TEST(LeAudioClientParserTest, testParseAseStatusHeader) {
  ase_rsp_hdr arh;
  const uint8_t value1[] = {
      // Ase ID
      0x01,
      // ASE State
      0x00,  // 'Idle' state
             // No additional ASE Params for the 'Idle' state
  };
  ASSERT_TRUE(ParseAseStatusHeader(arh, sizeof(value1), value1));
  ASSERT_EQ(arh.id, 0x01u);
  ASSERT_EQ(arh.state, 0x00u);

  const uint8_t value2[] = {
      // Ase ID
      0x02,
      // ASE State
      0x04,  // 'Streaming' state
      // Additional ASE Params for the 'Streaming' state
      // Metadata Len
      0x03,
      // Metadata
      0x03,  // [0].length
      0x02,  // [0].type
      0x01,  // [0].value[0]
      0x00,  // [0].value[1]
  };
  ASSERT_TRUE(ParseAseStatusHeader(arh, sizeof(value2), value2));
  ASSERT_EQ(arh.id, 0x02u);
  ASSERT_EQ(arh.state, 0x04u);
  // Currently additional state parameters are not handled
}

TEST(LeAudioClientParserTest,
     testParseAseStatusCodecConfiguredStateParamsInvalidLength) {
  ase_codec_configured_state_params codec_configured_state_params;
  const uint8_t value1[] = {
      // Ase ID
      0x02,
      // ASE State
      0x01,  // 'Codec Configured' state
      // Framing
      0x01,  // Unframed
      // Peferred PHY
      0x02,  // 2M PHY
      // Preferred retransimssion Num.
      0x04,
      // Max transport Latency
      0x05, 0x00,
      // Pressentation delay min.
      0x00, 0x01, 0x02, 0x03,
      // Pressentation delay max.
      0x00, 0x01, 0x02, 0x03,
      // Preferred presentation delay min.
      0x01, 0x02, 0x03,
      // Preferred presentation delay max.
      0x01, 0x02, 0x03,
      // Codec ID
      0x01, 0x02, 0x03, 0x04, 0x05,
      // Missing Codec spec. conf. length
  };

  ASSERT_FALSE(ParseAseStatusCodecConfiguredStateParams(
      codec_configured_state_params, sizeof(value1) - 2, value1 + 2));
}

TEST(LeAudioClientParserTest, testParseAseStatusCodecConfiguredStateParams) {
  ase_codec_configured_state_params codec_configured_state_params;
  const uint8_t value1[] = {
      // Ase ID
      0x01,
      // ASE State
      0x01,  // 'Codec Configured' state
      // Framing
      0x01,  // Unframed
      // Peferred PHY
      0x02,  // 2M PHY
      // Preferred retransimssion Num.
      0x04,
      // Max transport Latency
      0x05,
      0x00,
      // Pressentation delay min.
      0x00,
      0x01,
      0x02,
      // Pressentation delay max.
      0x10,
      0x11,
      0x12,
      // Preferred presentation delay min.
      0x01,
      0x02,
      0x03,
      // Preferred presentation delay max.
      0x09,
      0x10,
      0x11,
      // Codec ID
      0x01,
      0x02,
      0x03,
      0x04,
      0x05,
      // Codec spec. conf. length
      0x00,
  };

  // State additional parameters are right after the ASE ID and state bytes
  ASSERT_TRUE(ParseAseStatusCodecConfiguredStateParams(
      codec_configured_state_params, sizeof(value1) - 2, value1 + 2));
  ASSERT_EQ(codec_configured_state_params.framing, 0x01u);
  ASSERT_EQ(codec_configured_state_params.preferred_phy, 0x02u);
  ASSERT_EQ(codec_configured_state_params.preferred_retrans_nb, 0x04u);
  ASSERT_EQ(codec_configured_state_params.max_transport_latency, 0x0005u);
  ASSERT_EQ(codec_configured_state_params.pres_delay_min, 0x020100u);
  ASSERT_EQ(codec_configured_state_params.pres_delay_max, 0x121110u);
  ASSERT_EQ(codec_configured_state_params.preferred_pres_delay_min, 0x030201u);
  ASSERT_EQ(codec_configured_state_params.preferred_pres_delay_max, 0x111009u);
  ASSERT_EQ(codec_configured_state_params.codec_id.coding_format, 0x01u);
  ASSERT_EQ(codec_configured_state_params.codec_id.vendor_company_id, 0x0302u);
  ASSERT_EQ(codec_configured_state_params.codec_id.vendor_codec_id, 0x0504u);
  ASSERT_EQ(codec_configured_state_params.codec_spec_conf.size(), 0u);

  const uint8_t value2[] = {
      // Ase ID
      0x02,
      // ASE State
      0x01,  // 'Codec Configured' state
      // Framing
      0x01,  // Unframed
      // Peferred PHY
      0x02,  // 2M PHY
      // Preferred retransimssion Num.
      0x04,
      // Max transport Latency
      0x05,
      0x00,
      // Pressentation delay min.
      0x00,
      0x01,
      0x02,
      // Pressentation delay max.
      0x10,
      0x11,
      0x12,
      // Preferred presentation delay min.
      0x01,
      0x02,
      0x03,
      // Preferred presentation delay max.
      0x09,
      0x10,
      0x11,
      // Codec ID
      0x01,
      0x02,
      0x03,
      0x04,
      0x05,
      // Codec spec. conf. length
      0x05,
      // Codec spec. conf.
      0x0A,
      0x0B,
      0x0C,
      0x0D,
      0x0E,
  };

  // State additional parameters are right after the ASE ID and state bytes
  ASSERT_TRUE(ParseAseStatusCodecConfiguredStateParams(
      codec_configured_state_params, sizeof(value2) - 2, value2 + 2));
  ASSERT_EQ(codec_configured_state_params.framing, 0x01u);
  ASSERT_EQ(codec_configured_state_params.preferred_phy, 0x02u);
  ASSERT_EQ(codec_configured_state_params.preferred_retrans_nb, 0x04u);
  ASSERT_EQ(codec_configured_state_params.max_transport_latency, 0x0005u);
  ASSERT_EQ(codec_configured_state_params.pres_delay_min, 0x020100u);
  ASSERT_EQ(codec_configured_state_params.pres_delay_max, 0x121110u);
  ASSERT_EQ(codec_configured_state_params.preferred_pres_delay_min, 0x030201u);
  ASSERT_EQ(codec_configured_state_params.preferred_pres_delay_max, 0x111009u);
  ASSERT_EQ(codec_configured_state_params.codec_id.coding_format, 0x01u);
  ASSERT_EQ(codec_configured_state_params.codec_id.vendor_company_id, 0x0302u);
  ASSERT_EQ(codec_configured_state_params.codec_id.vendor_codec_id, 0x0504u);
  ASSERT_EQ(codec_configured_state_params.codec_spec_conf.size(), 5u);
  ASSERT_EQ(codec_configured_state_params.codec_spec_conf[0], 0x0Au);
  ASSERT_EQ(codec_configured_state_params.codec_spec_conf[1], 0x0Bu);
  ASSERT_EQ(codec_configured_state_params.codec_spec_conf[2], 0x0Cu);
  ASSERT_EQ(codec_configured_state_params.codec_spec_conf[3], 0x0Du);
  ASSERT_EQ(codec_configured_state_params.codec_spec_conf[4], 0x0Eu);
}

TEST(LeAudioClientParserTest,
     testParseAseStatusQosConfiguredStateParamsInvalidLength) {
  struct ase_qos_configured_state_params rsp {
    .cig_id = 0, .cis_id = 0
  };
  const uint8_t value1[] = {
      // Ase ID
      0x01,
      // ASE State
      0x02,  // 'QoS Configured' state
      0x03,  // CIG_ID
      0x04,  // CIS_ID
  };

  ParseAseStatusQosConfiguredStateParams(rsp, sizeof(value1) - 2, value1 + 2);
  ASSERT_EQ(rsp.cig_id, 0);
  ASSERT_EQ(rsp.cis_id, 0);

  const uint8_t value2[] = {
      // Ase ID
      0x01,
      // ASE State
      0x02,  // 'QoS Configured' state
             // CIG_ID
      0x03,
      // CIS_ID
      0x04,
      // SDU Interval
      0x05, 0x06, 0x07,
      // Framing
      0x01,
      // PHY
      0x02,
      // Max SDU
      0x08, 0x09,
      // Retransmission Num.
      0x0A,
      // Max Transport Latency
      0x0B, 0x0C,
      // Presentation Delay
      0x0D, 0x0E,
      // Missing Byte
  };

  ParseAseStatusQosConfiguredStateParams(rsp, sizeof(value2) - 2, value2 + 2);
  ASSERT_EQ(rsp.cig_id, 0);
  ASSERT_EQ(rsp.cis_id, 0);
}

TEST(LeAudioClientParserTest, testParseAseStatusQosConfiguredStateParams) {
  struct ase_qos_configured_state_params rsp;
  const uint8_t value[] = {
      // Ase ID
      0x01,
      // ASE State - 'QoS Configured'
      0x02,
      // CIG_ID
      0x03,
      // CIS_ID
      0x04,
      // SDU Interval
      0x05,
      0x06,
      0x07,
      // Framing
      0x01,
      // PHY
      0x02,
      // Max SDU
      0x18,
      0x19,
      // Retransmission Num.
      0x1A,
      // Max Transport Latency
      0x1B,
      0x1C,
      // Presentation Delay
      0x1D,
      0x1E,
      0x1F,
  };

  ParseAseStatusQosConfiguredStateParams(rsp, sizeof(value) - 2, value + 2);
  ASSERT_EQ(rsp.cig_id, 0x03u);
  ASSERT_EQ(rsp.cis_id, 0x04u);
  ASSERT_EQ(rsp.sdu_interval, 0x070605u);
  ASSERT_EQ(rsp.framing, 0x01u);
  ASSERT_EQ(rsp.phy, 0x02u);
  ASSERT_EQ(rsp.max_sdu, 0x1918u);
  ASSERT_EQ(rsp.retrans_nb, 0x1Au);
  ASSERT_EQ(rsp.max_transport_latency, 0x1C1Bu);
  ASSERT_EQ(rsp.pres_delay, 0x1F1E1Du);
}

TEST(LeAudioClientParserTest,
     testParseAseStatusTransientStateParamsInvalidLength) {
  ase_transient_state_params params;
  const uint8_t value1[] = {
      // Ase ID
      0x01,
      // ASE State
      0x03,  // 'Enabling' state
             // missing Metadata length
             // missing Metadata
  };
  ParseAseStatusTransientStateParams(params, sizeof(value1) - 2, value1 + 2);
}

TEST(LeAudioClientParserTest, testParseAseStatusTransientStateParams) {
  ase_transient_state_params params;
  const uint8_t value1[] = {
      // Ase ID
      0x01,
      // ASE State
      0x03,  // 'Enabling' state
      // Metadata length
      0x00,
  };
  ParseAseStatusTransientStateParams(params, sizeof(value1) - 2, value1 + 2);
  ASSERT_EQ(params.metadata.size(), 0u);

  const uint8_t value2[] = {
      // Ase ID
      0x01,
      // ASE State
      0x03,  // 'Enabling' state
      // CIG_ID
      0x03,
      // CIS_ID
      0x04,
      // Metadata length
      0x03,
      // Metadata
      0x02,  // [0].length
      0x01,  // [0].type
      0x00,  // [0].value[0]
  };
  ParseAseStatusTransientStateParams(params, sizeof(value2) - 2, value2 + 2);

  ASSERT_EQ(params.metadata.size(), 3u);
  ASSERT_EQ(params.metadata[0], 0x02u);
  ASSERT_EQ(params.metadata[1], 0x01u);
  ASSERT_EQ(params.metadata[2], 0x00u);
}

TEST(LeAudioClientParserTest, testParseAseCtpNotificationInvalidLength) {
  ctp_ntf ntf;
  const uint8_t value1[] = {
      // Opcode
      0x01,
      // Number of ASEs
      0x02,
      // ASE ID
      0x01,
      // Response Code
      0x01,
      // Reason
      0x01,
      // ASE ID
      0x02,
      // Response Code
      0x02,
      // Missing Reason
  };
  ParseAseCtpNotification(ntf, sizeof(value1), value1);

  // In case of invalid payload at least we get the opcode
  ASSERT_EQ(ntf.op, 0x01u);
  ASSERT_EQ(ntf.entries.size(), 0u);

  const uint8_t value2[] = {
      // Opcode
      0x01,
      // Missing Number of ASEs
      // Missing ASE ID
      // Missing Response Code
      // Missing Reason
      // Missing ASE ID
      // Missing Response Code
      // Missing Reason
  };
  ntf.entries.clear();
  ParseAseCtpNotification(ntf, sizeof(value2), value2);

  // In case of invalid payload at least we get the opcode
  ASSERT_EQ(ntf.op, 0x01u);
  ASSERT_EQ(ntf.entries.size(), 0u);

  const uint8_t value3[] = {
      // Opcode
      0x01,
      // Number of ASEs
      0x03,
      // ASE ID
      0x01,
      // Response Code
      0x01,
      // Reason
      0x01,
      // ASE ID
      0x02,
      // Response Code
      0x02,
      // Reason
      0x03,
      // Missing the entire ASE entry
  };

  ntf.entries.clear();
  ParseAseCtpNotification(ntf, sizeof(value3), value3);
  // In case of invalid payload at least we get the opcode
  ASSERT_EQ(ntf.op, 0x01u);
  ASSERT_EQ(ntf.entries.size(), 0u);
}

TEST(LeAudioClientParserTest, testParseAseCtpNotification) {
  ctp_ntf ntf;
  const uint8_t value1[] = {
      // Opcode
      0x01,
      // Number of ASEs
      0x02,
      // ASE ID
      0x01,
      // Response Code
      0x01,
      // Reason
      0x01,
      // ASE ID
      0x03,
      // Response Code
      0x02,
      // Reason
      0x03,
  };
  ParseAseCtpNotification(ntf, sizeof(value1), value1);

  ASSERT_EQ(ntf.op, 0x01u);
  ASSERT_EQ(ntf.entries.size(), 2u);
  ASSERT_EQ(ntf.entries[0].ase_id, 0x01u);
  ASSERT_EQ(ntf.entries[0].response_code, 0x01u);
  ASSERT_EQ(ntf.entries[0].reason, 0x01);
  ASSERT_EQ(ntf.entries[1].ase_id, 0x03u);
  ASSERT_EQ(ntf.entries[1].response_code, 0x02u);
  ASSERT_EQ(ntf.entries[1].reason, 0x03);
}

TEST(LeAudioClientParserTest, testParseAseCtpNotificationConfigurationIssue) {
  ctp_ntf ntf;
  const uint8_t value1[] = {
      // Opcode
      0x01,
      // Number of ASEs
      0x02,
      // ASE ID
      0x01,
      // Response Code
      0x07,
      // Reason
      0x01,
      // ASE ID
      0x03,
      // Response Code
      0x05,
      // Reason
      0x05,
  };
  ParseAseCtpNotification(ntf, sizeof(value1), value1);

  ASSERT_EQ(ntf.op, 0x01u);
  ASSERT_EQ(ntf.entries.size(), 2u);
  ASSERT_EQ(ntf.entries[0].ase_id, 0x01u);
  ASSERT_EQ(ntf.entries[0].response_code, 0x07u);
  ASSERT_EQ(ntf.entries[0].reason, 0x01);
  ASSERT_EQ(ntf.entries[1].ase_id, 0x03u);
  ASSERT_EQ(ntf.entries[1].response_code, 0x05u);
  ASSERT_EQ(ntf.entries[1].reason, 0x05);
}

TEST(LeAudioClientParserTest, testParseAseCtpNotificationMetadataIssue) {
  ctp_ntf ntf;
  const uint8_t value1[] = {
      // Opcode
      0x01,
      // Number of ASEs
      0x02,
      // ASE ID
      0x01,
      // Response Code
      0x0A,
      // Reason
      0x01,
      // ASE ID
      0x03,
      // Response Code
      0x0D,
      // Reason
      0xFF,
  };
  ParseAseCtpNotification(ntf, sizeof(value1), value1);

  ASSERT_EQ(ntf.op, 0x01u);
  ASSERT_EQ(ntf.entries.size(), 2u);
  ASSERT_EQ(ntf.entries[0].ase_id, 0x01u);
  ASSERT_EQ(ntf.entries[0].response_code, 0x0Au);
  ASSERT_EQ(ntf.entries[0].reason, 0x01);
  ASSERT_EQ(ntf.entries[1].ase_id, 0x03u);
  ASSERT_EQ(ntf.entries[1].response_code, 0x0Du);
  ASSERT_EQ(ntf.entries[1].reason, 0xFF);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpCodecConfigEmpty) {
  std::vector<struct ctp_codec_conf> confs;
  std::vector<uint8_t> value;

  PrepareAseCtpCodecConfig(confs, value);

  ASSERT_EQ(value.size(), 0u);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpCodecConfigSingle) {
  std::vector<struct ctp_codec_conf> confs;
  std::vector<uint8_t> value;

  types::LeAudioCodecId codec_id{.coding_format = 0x06,
                                 .vendor_company_id = 0x0203,
                                 .vendor_codec_id = 0x0405};
  types::LeAudioLtvMap codec_conf =
      types::LeAudioLtvMap()
          .Add(codec_spec_conf::kLeAudioLtvTypeSamplingFreq, (uint8_t)0x10)
          .Add(codec_spec_conf::kLeAudioLtvTypeFrameDuration, (uint8_t)0x03)
          .Add(codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation,
               (uint32_t)0x04050607)
          .Add(codec_spec_conf::kLeAudioLtvTypeOctetsPerCodecFrame,
               (uint16_t)0x0203);

  confs.push_back(ctp_codec_conf{
      .ase_id = 0x05,
      .target_latency = 0x03,
      .target_phy = 0x02,
      .codec_id = codec_id,
      .codec_config = codec_conf,
  });
  PrepareAseCtpCodecConfig(confs, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x01);  // Config Codec Opcode
  ASSERT_EQ(value[i++], 0x01);  // Number of ASEs
  ASSERT_EQ(value[i++], 0x05);  // ASE[0] ASE ID
  ASSERT_EQ(value[i++], 0x03);  // ASE[0] Target Latency
  ASSERT_EQ(value[i++], 0x02);  // ASE[0] Target Phy
  ASSERT_EQ(value[i++], 0x06);  // ASE[0].CodecID Coding Format
  ASSERT_EQ(value[i++], 0x03);  // ASE[0].CodecID Company ID LSB
  ASSERT_EQ(value[i++], 0x02);  // ASE[0].CodecID Company ID MSB
  ASSERT_EQ(value[i++], 0x05);  // ASE[0].CodecID Codec ID LSB
  ASSERT_EQ(value[i++], 0x04);  // ASE[0].CodecID Codec ID MSB

  // ASE[0].Codec Spec. Conf. Length - LC3 specific
  ASSERT_EQ(value[i++], 8 + 8);  // * 4*2 bytes for 4 LTV types and lengths + 8
                                 // bytes for the values
  ASSERT_EQ(value[i++], 0x02);   // Sampling Freq. Length
  ASSERT_EQ(value[i++], 0x01);   // Sampling Freq. Type
  ASSERT_EQ(value[i++], 0x10);   // Sampling Freq. Value
  ASSERT_EQ(value[i++], 0x02);   // Frame Duration. Length
  ASSERT_EQ(value[i++], 0x02);   // Frame Duration. Type
  ASSERT_EQ(value[i++], 0x03);   // Frame Duration. Value
  ASSERT_EQ(value[i++], 0x05);   // Audio Channel Allocations Length
  ASSERT_EQ(value[i++], 0x03);   // Audio Channel Allocations Type
  ASSERT_EQ(value[i++], 0x07);   // Audio Channel Allocations Value[0]
  ASSERT_EQ(value[i++], 0x06);   // Audio Channel Allocations Value[1]
  ASSERT_EQ(value[i++], 0x05);   // Audio Channel Allocations Value[2]
  ASSERT_EQ(value[i++], 0x04);   // Audio Channel Allocations Value[3]
  ASSERT_EQ(value[i++], 0x03);   // Octets Per Frame Length
  ASSERT_EQ(value[i++], 0x04);   // Octets Per Frame Type
  ASSERT_EQ(value[i++], 0x03);   // Octets Per Frame Value[0]
  ASSERT_EQ(value[i++], 0x02);   // Octets Per Frame Value[1]
  ASSERT_EQ(value.size(), i);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpCodecConfigMultiple) {
  std::vector<struct ctp_codec_conf> confs;
  std::vector<uint8_t> value;

  types::LeAudioCodecId codec_id{.coding_format = 0x06,
                                 .vendor_company_id = 0x0203,
                                 .vendor_codec_id = 0x0405};
  types::LeAudioLtvMap codec_conf =
      types::LeAudioLtvMap()
          .Add(codec_spec_conf::kLeAudioLtvTypeSamplingFreq, (uint8_t)0x10)
          .Add(codec_spec_conf::kLeAudioLtvTypeFrameDuration, (uint8_t)0x03)
          .Add(codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation,
               (uint32_t)0x04050607)
          .Add(codec_spec_conf::kLeAudioLtvTypeOctetsPerCodecFrame,
               (uint16_t)0x0203);

  confs.push_back(ctp_codec_conf{
      .ase_id = 0x05,
      .target_latency = 0x03,
      .target_phy = 0x02,
      .codec_id = codec_id,
      .codec_config = codec_conf,
  });
  PrepareAseCtpCodecConfig(confs, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x01);  // Config Codec Opcode
  ASSERT_EQ(value[i++], 0x01);  // Number of ASEs
  ASSERT_EQ(value[i++], 0x05);  // ASE[0] ASE ID
  ASSERT_EQ(value[i++], 0x03);  // ASE[0] Target Latency
  ASSERT_EQ(value[i++], 0x02);  // ASE[0] Target Phy
  ASSERT_EQ(value[i++], 0x06);  // ASE[0].CodecID Coding Format
  ASSERT_EQ(value[i++], 0x03);  // ASE[0].CodecID Company ID LSB
  ASSERT_EQ(value[i++], 0x02);  // ASE[0].CodecID Company ID MSB
  ASSERT_EQ(value[i++], 0x05);  // ASE[0].CodecID Codec ID LSB
  ASSERT_EQ(value[i++], 0x04);  // ASE[0].CodecID Codec ID MSB

  // ASE[0].Codec Spec. Conf. Length - LC3 specific
  ASSERT_EQ(value[i++], 8 + 8);  // * 4*2 bytes for 4 LTV types and lengths + 8
                                 // bytes for the values
  ASSERT_EQ(value[i++], 0x02);   // Sampling Freq. Length
  ASSERT_EQ(value[i++], 0x01);   // Sampling Freq. Type
  ASSERT_EQ(value[i++], 0x10);   // Sampling Freq. Value
  ASSERT_EQ(value[i++], 0x02);   // Frame Duration. Length
  ASSERT_EQ(value[i++], 0x02);   // Frame Duration. Type
  ASSERT_EQ(value[i++], 0x03);   // Frame Duration. Value
  ASSERT_EQ(value[i++], 0x05);   // Audio Channel Allocations Length
  ASSERT_EQ(value[i++], 0x03);   // Audio Channel Allocations Type
  ASSERT_EQ(value[i++], 0x07);   // Audio Channel Allocations Value[0]
  ASSERT_EQ(value[i++], 0x06);   // Audio Channel Allocations Value[1]
  ASSERT_EQ(value[i++], 0x05);   // Audio Channel Allocations Value[2]
  ASSERT_EQ(value[i++], 0x04);   // Audio Channel Allocations Value[3]
  ASSERT_EQ(value[i++], 0x03);   // Octets Per Frame Length
  ASSERT_EQ(value[i++], 0x04);   // Octets Per Frame Type
  ASSERT_EQ(value[i++], 0x03);   // Octets Per Frame Value[0]
  ASSERT_EQ(value[i++], 0x02);   // Octets Per Frame Value[1]
  ASSERT_EQ(value.size(), i);

  types::LeAudioCodecId codec_id2{.coding_format = 0x16,
                                  .vendor_company_id = 0x1213,
                                  .vendor_codec_id = 0x1415};
  types::LeAudioLtvMap codec_conf2 =
      types::LeAudioLtvMap()
          .Add(codec_spec_conf::kLeAudioLtvTypeSamplingFreq, (uint8_t)0x11)
          .Add(codec_spec_conf::kLeAudioLtvTypeFrameDuration, (uint8_t)0x13)
          .Add(codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation,
               (uint32_t)0x14151617)
          .Add(codec_spec_conf::kLeAudioLtvTypeOctetsPerCodecFrame,
               (uint16_t)0x1213);

  confs.push_back(ctp_codec_conf{
      .ase_id = 0x15,
      .target_latency = 0x13,
      .target_phy = 0x01,
      .codec_id = codec_id2,
      .codec_config = codec_conf2,
  });
  PrepareAseCtpCodecConfig(confs, value);

  i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x01);  // Config Codec Opcode
  ASSERT_EQ(value[i++], 0x02);  // Number of ASEs
  ASSERT_EQ(value[i++], 0x05);  // ASE[0] ASE ID
  ASSERT_EQ(value[i++], 0x03);  // ASE[0] Target Latency
  ASSERT_EQ(value[i++], 0x02);  // ASE[0] Target Phy
  ASSERT_EQ(value[i++], 0x06);  // ASE[0].CodecID Coding Format
  ASSERT_EQ(value[i++], 0x03);  // ASE[0].CodecID Company ID LSB
  ASSERT_EQ(value[i++], 0x02);  // ASE[0].CodecID Company ID MSB
  ASSERT_EQ(value[i++], 0x05);  // ASE[0].CodecID Codec ID LSB
  ASSERT_EQ(value[i++], 0x04);  // ASE[0].CodecID Codec ID MSB

  // ASE[0].Codec Spec. Conf. Length - LC3 specific
  ASSERT_EQ(value[i++], 8 + 8);  // * 4*2 bytes for 4 LTV types and lengths + 8
                                 // bytes for the values
  ASSERT_EQ(value[i++], 0x02);   // Sampling Freq. Length
  ASSERT_EQ(value[i++], 0x01);   // Sampling Freq. Type
  ASSERT_EQ(value[i++], 0x10);   // Sampling Freq. Value
  ASSERT_EQ(value[i++], 0x02);   // Frame Duration. Length
  ASSERT_EQ(value[i++], 0x02);   // Frame Duration. Type
  ASSERT_EQ(value[i++], 0x03);   // Frame Duration. Value
  ASSERT_EQ(value[i++], 0x05);   // Audio Channel Allocations Length
  ASSERT_EQ(value[i++], 0x03);   // Audio Channel Allocations Type
  ASSERT_EQ(value[i++], 0x07);   // Audio Channel Allocations Value[0]
  ASSERT_EQ(value[i++], 0x06);   // Audio Channel Allocations Value[1]
  ASSERT_EQ(value[i++], 0x05);   // Audio Channel Allocations Value[2]
  ASSERT_EQ(value[i++], 0x04);   // Audio Channel Allocations Value[3]
  ASSERT_EQ(value[i++], 0x03);   // Octets Per Frame Length
  ASSERT_EQ(value[i++], 0x04);   // Octets Per Frame Type
  ASSERT_EQ(value[i++], 0x03);   // Octets Per Frame Value[0]
  ASSERT_EQ(value[i++], 0x02);   // Octets Per Frame Value[1]

  ASSERT_EQ(value[i++], 0x15);  // ASE[1] ASE ID
  ASSERT_EQ(value[i++], 0x13);  // ASE[1] Target Latency
  ASSERT_EQ(value[i++], 0x01);  // ASE[1] Target Phy
  ASSERT_EQ(value[i++], 0x16);  // ASE[1].CodecID Coding Format
  ASSERT_EQ(value[i++], 0x13);  // ASE[1].CodecID Company ID LSB
  ASSERT_EQ(value[i++], 0x12);  // ASE[1].CodecID Company ID MSB
  ASSERT_EQ(value[i++], 0x15);  // ASE[1].CodecID Codec ID LSB
  ASSERT_EQ(value[i++], 0x14);  // ASE[1].CodecID Codec ID MSB

  // ASE[1].Codec Spec. Conf. Length - LC3 specific
  ASSERT_EQ(value[i++], 8 + 8);  // * 4*2 bytes for 4 LTV types and lengths + 8
                                 // bytes for the values
  ASSERT_EQ(value[i++], 0x02);   // Sampling Freq. Length
  ASSERT_EQ(value[i++], 0x01);   // Sampling Freq. Type
  ASSERT_EQ(value[i++], 0x11);   // Sampling Freq. Value
  ASSERT_EQ(value[i++], 0x02);   // Frame Duration. Length
  ASSERT_EQ(value[i++], 0x02);   // Frame Duration. Type
  ASSERT_EQ(value[i++], 0x13);   // Frame Duration. Value
  ASSERT_EQ(value[i++], 0x05);   // Audio Channel Allocations Length
  ASSERT_EQ(value[i++], 0x03);   // Audio Channel Allocations Type
  ASSERT_EQ(value[i++], 0x17);   // Audio Channel Allocations Value[0]
  ASSERT_EQ(value[i++], 0x16);   // Audio Channel Allocations Value[1]
  ASSERT_EQ(value[i++], 0x15);   // Audio Channel Allocations Value[2]
  ASSERT_EQ(value[i++], 0x14);   // Audio Channel Allocations Value[3]
  ASSERT_EQ(value[i++], 0x03);   // Octets Per Frame Length
  ASSERT_EQ(value[i++], 0x04);   // Octets Per Frame Type
  ASSERT_EQ(value[i++], 0x13);   // Octets Per Frame Value[0]
  ASSERT_EQ(value[i++], 0x12);   // Octets Per Frame Value[1]

  ASSERT_EQ(value.size(), i);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpConfigQosEmpty) {
  std::vector<struct ctp_qos_conf> confs;
  std::vector<uint8_t> value;

  PrepareAseCtpConfigQos(confs, value);
  ASSERT_EQ(value.size(), 0u);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpConfigQosSingle) {
  std::vector<struct ctp_qos_conf> confs;
  std::vector<uint8_t> value;

  const ctp_qos_conf conf{.ase_id = 0x01,
                          .cig = 0x11,
                          .cis = 0x12,
                          .sdu_interval = 0x00131415,
                          .framing = 0x01,
                          .phy = 0x01,
                          .max_sdu = 0x0203,
                          .retrans_nb = 0x04,
                          .max_transport_latency = 0x0302,
                          .pres_delay = 0x00121314};
  confs.push_back(conf);

  PrepareAseCtpConfigQos(confs, value);
  ASSERT_NE(value.size(), 0u);

  uint8_t i = 0;
  ASSERT_EQ(value[i++], 0x02u);  // Config QOS Opcode
  ASSERT_EQ(value[i++], 0x01u);  // Number of ASE
  ASSERT_EQ(value[i++], 0x01u);  // ASE ID
  ASSERT_EQ(value[i++], 0x11u);  // CIG ID
  ASSERT_EQ(value[i++], 0x12u);  // CIS ID
  ASSERT_EQ(value[i++], 0x15u);  // SDU Interval [0]
  ASSERT_EQ(value[i++], 0x14u);  // SDU Interval [1]
  ASSERT_EQ(value[i++], 0x13u);  // SDU Interval [2]
  ASSERT_EQ(value[i++], 0x01u);  // Framing
  ASSERT_EQ(value[i++], 0x01u);  // Phy
  ASSERT_EQ(value[i++], 0x03u);  // Max SDU LSB
  ASSERT_EQ(value[i++], 0x02u);  // Max SDU MSB
  ASSERT_EQ(value[i++], 0x04u);  // Retransmission
  ASSERT_EQ(value[i++], 0x02u);  // Max. Trans. Latency LSB
  ASSERT_EQ(value[i++], 0x03u);  // Max. Trans. Latency MSB
  ASSERT_EQ(value[i++], 0x14u);  // Pres. Delay[0]
  ASSERT_EQ(value[i++], 0x13u);  // Pres. Delay[1]
  ASSERT_EQ(value[i++], 0x12u);  // Pres. Delay[2]
  ASSERT_EQ(value.size(), i);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpConfigQosMultiple) {
  std::vector<struct ctp_qos_conf> confs;
  std::vector<uint8_t> value;

  const ctp_qos_conf conf{.ase_id = 0x01,
                          .cig = 0x11,
                          .cis = 0x12,
                          .sdu_interval = 0x131415,
                          .framing = 0x01,
                          .phy = 0x01,
                          .max_sdu = 0x0203,
                          .retrans_nb = 0x04,
                          .max_transport_latency = 0x0302,
                          .pres_delay = 0x121314};
  confs.push_back(conf);

  const ctp_qos_conf conf2{.ase_id = 0x11,
                           .cig = 0x21,
                           .cis = 0x22,
                           .sdu_interval = 0x232425,
                           .framing = 0x02,
                           .phy = 0x02,
                           .max_sdu = 0x2223,
                           .retrans_nb = 0x24,
                           .max_transport_latency = 0x2322,
                           .pres_delay = 0x222324};
  confs.push_back(conf2);

  PrepareAseCtpConfigQos(confs, value);
  ASSERT_NE(value.size(), 0u);

  uint8_t i = 0;
  ASSERT_EQ(value[i++], 0x02u);  // Config QOS Opcode
  ASSERT_EQ(value[i++], 0x02u);  // Number of ASE
  // 1st ASE Config
  ASSERT_EQ(value[i++], 0x01u);  // ASE ID
  ASSERT_EQ(value[i++], 0x11u);  // CIG ID
  ASSERT_EQ(value[i++], 0x12u);  // CIS ID
  ASSERT_EQ(value[i++], 0x15u);  // SDU Interval [0]
  ASSERT_EQ(value[i++], 0x14u);  // SDU Interval [1]
  ASSERT_EQ(value[i++], 0x13u);  // SDU Interval [2]
  ASSERT_EQ(value[i++], 0x01u);  // Framing
  ASSERT_EQ(value[i++], 0x01u);  // Phy
  ASSERT_EQ(value[i++], 0x03u);  // Max SDU LSB
  ASSERT_EQ(value[i++], 0x02u);  // Max SDU MSB
  ASSERT_EQ(value[i++], 0x04u);  // Retransmission
  ASSERT_EQ(value[i++], 0x02u);  // Max. Trans. Latency LSB
  ASSERT_EQ(value[i++], 0x03u);  // Max. Trans. Latency MSB
  ASSERT_EQ(value[i++], 0x14u);  // Pres. Delay[0]
  ASSERT_EQ(value[i++], 0x13u);  // Pres. Delay[1]
  ASSERT_EQ(value[i++], 0x12u);  // Pres. Delay[2]
  // 2nd ASE Config
  ASSERT_EQ(value[i++], 0x11u);  // ASE ID
  ASSERT_EQ(value[i++], 0x21u);  // CIG ID
  ASSERT_EQ(value[i++], 0x22u);  // CIS ID
  ASSERT_EQ(value[i++], 0x25u);  // SDU Interval [0]
  ASSERT_EQ(value[i++], 0x24u);  // SDU Interval [1]
  ASSERT_EQ(value[i++], 0x23u);  // SDU Interval [2]
  ASSERT_EQ(value[i++], 0x02u);  // Framing
  ASSERT_EQ(value[i++], 0x02u);  // Phy
  ASSERT_EQ(value[i++], 0x23u);  // Max SDU LSB
  ASSERT_EQ(value[i++], 0x22u);  // Max SDU MSB
  ASSERT_EQ(value[i++], 0x24u);  // Retransmission
  ASSERT_EQ(value[i++], 0x22u);  // Max. Trans. Latency LSB
  ASSERT_EQ(value[i++], 0x23u);  // Max. Trans. Latency MSB
  ASSERT_EQ(value[i++], 0x24u);  // Pres. Delay[0]
  ASSERT_EQ(value[i++], 0x23u);  // Pres. Delay[1]
  ASSERT_EQ(value[i++], 0x22u);  // Pres. Delay[2]

  ASSERT_EQ(value.size(), i);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpEnableEmpty) {
  std::vector<struct ctp_enable> confs;
  std::vector<uint8_t> value;

  PrepareAseCtpEnable(confs, value);
  ASSERT_EQ(value.size(), 0u);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpEnableSingle) {
  std::vector<struct ctp_enable> confs;
  std::vector<uint8_t> value;

  ctp_enable conf{.ase_id = 0x11, .metadata = {0x02, 0x22, 0x21}};
  confs.push_back(conf);

  PrepareAseCtpEnable(confs, value);
  ASSERT_NE(value.size(), 0u);

  uint8_t i = 0;
  ASSERT_EQ(value[i++], 0x03u);  // Enable Opcode
  ASSERT_EQ(value[i++], 0x01u);  // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(value[i++], 0x03u);  // Metadata Len
  ASSERT_EQ(value[i++], 0x02u);  // Metadata[0]
  ASSERT_EQ(value[i++], 0x22u);  // Metadata[1]
  ASSERT_EQ(value[i++], 0x21u);  // Metadata[2]
  ASSERT_EQ(value.size(), i);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpEnableMultiple) {
  std::vector<struct ctp_enable> confs;
  std::vector<uint8_t> value;

  ctp_enable conf{.ase_id = 0x11, .metadata = {0x02, 0x22, 0x21}};
  confs.push_back(conf);

  ctp_enable conf2{.ase_id = 0x21, .metadata = {0x03, 0x35, 0x36, 0x37}};
  confs.push_back(conf2);

  PrepareAseCtpEnable(confs, value);
  ASSERT_NE(value.size(), 0u);

  uint8_t i = 0;
  ASSERT_EQ(value[i++], 0x03u);  // Enable Opcode
  ASSERT_EQ(value[i++], 0x02u);  // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(value[i++], 0x03u);  // ASE[0] Metadata Len
  ASSERT_EQ(value[i++], 0x02u);  // ASE[0] Metadata[0]
  ASSERT_EQ(value[i++], 0x22u);  // ASE[0] Metadata[1]
  ASSERT_EQ(value[i++], 0x21u);  // ASE[0] Metadata[2]
  ASSERT_EQ(value[i++], 0x21u);  // ASE[1] ID
  ASSERT_EQ(value[i++], 0x04u);  // ASE[1] Metadata Len
  ASSERT_EQ(value[i++], 0x03u);  // ASE[1] Metadata[0]
  ASSERT_EQ(value[i++], 0x35u);  // ASE[1] Metadata[1]
  ASSERT_EQ(value[i++], 0x36u);  // ASE[1] Metadata[2]
  ASSERT_EQ(value[i++], 0x37u);  // ASE[1] Metadata[3]
  ASSERT_EQ(value.size(), i);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpAudioReceiverStartReadyEmpty) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  PrepareAseCtpAudioReceiverStartReady(ase_ids, value);
  ASSERT_EQ(value.size(), 0u);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpAudioReceiverStartReadySingle) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  ase_ids.push_back(0x11);

  PrepareAseCtpAudioReceiverStartReady(ase_ids, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x04u);  // Receiver Start Ready Opcode
  ASSERT_EQ(value[i++], 1u);     // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest,
     testPrepareAseCtpAudioReceiverStartReadyMultiple) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  ase_ids.push_back(0x11);
  ase_ids.push_back(0x36);

  PrepareAseCtpAudioReceiverStartReady(ase_ids, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x04u);  // Receiver Start Ready Opcode
  ASSERT_EQ(value[i++], 2u);     // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(value[i++], 0x36u);  // ASE[0] ID
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest, testPrepareAseCtpDisableEmpty) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  PrepareAseCtpDisable(ase_ids, value);
  ASSERT_EQ(value.size(), 0u);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpDisableSingle) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  ase_ids.push_back(0x11);

  PrepareAseCtpDisable(ase_ids, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x05u);  // Disable Opcode
  ASSERT_EQ(value[i++], 1u);     // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest, testPrepareAseCtpDisableMultiple) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  ase_ids.push_back(0x11);
  ase_ids.push_back(0x36);

  PrepareAseCtpDisable(ase_ids, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x05u);  // Disable Opcode
  ASSERT_EQ(value[i++], 2u);     // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(value[i++], 0x36u);  // ASE[0] ID
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest, testPrepareAseCtpAudioReceiverStopReadyEmpty) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  PrepareAseCtpAudioReceiverStopReady(ase_ids, value);
  ASSERT_EQ(value.size(), 0u);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpAudioReceiverStopReadySingle) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  ase_ids.push_back(0x11);

  PrepareAseCtpAudioReceiverStopReady(ase_ids, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x06u);  // Reveicer Stop Ready Opcode
  ASSERT_EQ(value[i++], 1u);     // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest, testPrepareAseCtpAudioReceiverStopReadyMultiple) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  ase_ids.push_back(0x11);
  ase_ids.push_back(0x36);

  PrepareAseCtpAudioReceiverStopReady(ase_ids, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x06u);  // Reveicer Stop Ready Opcode
  ASSERT_EQ(value[i++], 2u);     // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(value[i++], 0x36u);  // ASE[0] ID
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest, testPrepareAseCtpUpdateMetadataEmpty) {
  std::vector<struct ctp_update_metadata> confs;
  std::vector<uint8_t> value;

  PrepareAseCtpUpdateMetadata(confs, value);
  ASSERT_EQ(value.size(), 0u);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpUpdateMetadataSingle) {
  std::vector<struct ctp_update_metadata> confs;
  std::vector<uint8_t> value;

  ctp_update_metadata conf{.ase_id = 0x11, .metadata = {0x02, 0x22, 0x21}};
  confs.push_back(conf);

  PrepareAseCtpUpdateMetadata(confs, value);
  ASSERT_NE(value.size(), 0u);

  uint8_t i = 0;
  ASSERT_EQ(value[i++], 0x07u);  // Update Metadata Opcode
  ASSERT_EQ(value[i++], 0x01u);  // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(value[i++], 0x03u);  // Metadata Len
  ASSERT_EQ(value[i++], 0x02u);  // Metadata[0]
  ASSERT_EQ(value[i++], 0x22u);  // Metadata[1]
  ASSERT_EQ(value[i++], 0x21u);  // Metadata[2]
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest, testPrepareAseCtpUpdateMetadataMultiple) {
  std::vector<struct ctp_update_metadata> confs;
  std::vector<uint8_t> value;

  ctp_update_metadata conf{.ase_id = 0x11, .metadata = {0x02, 0x22, 0x21}};
  confs.push_back(conf);

  ctp_update_metadata conf2{.ase_id = 0x21,
                            .metadata = {0x03, 0x35, 0x36, 0x37}};
  confs.push_back(conf2);

  PrepareAseCtpUpdateMetadata(confs, value);
  ASSERT_NE(value.size(), 0u);

  uint8_t i = 0;
  ASSERT_EQ(value[i++], 0x07u);  // Update Metadata Opcode
  ASSERT_EQ(value[i++], 0x02u);  // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(value[i++], 0x03u);  // ASE[0] Metadata Len
  ASSERT_EQ(value[i++], 0x02u);  // ASE[0] Metadata[0]
  ASSERT_EQ(value[i++], 0x22u);  // ASE[0] Metadata[1]
  ASSERT_EQ(value[i++], 0x21u);  // ASE[0] Metadata[2]
  ASSERT_EQ(value[i++], 0x21u);  // ASE[1] ID
  ASSERT_EQ(value[i++], 0x04u);  // ASE[1] Metadata Len
  ASSERT_EQ(value[i++], 0x03u);  // ASE[1] Metadata[0]
  ASSERT_EQ(value[i++], 0x35u);  // ASE[1] Metadata[1]
  ASSERT_EQ(value[i++], 0x36u);  // ASE[1] Metadata[2]
  ASSERT_EQ(value[i++], 0x37u);  // ASE[1] Metadata[2]
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest, testPrepareAseCtpReleaseEmpty) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  PrepareAseCtpRelease(ase_ids, value);
  ASSERT_EQ(value.size(), 0u);
}

TEST(LeAudioClientParserTest, testPrepareAseCtpReleaseSingle) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  ase_ids.push_back(0x11);

  PrepareAseCtpRelease(ase_ids, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x08u);  // Release Opcode
  ASSERT_EQ(value[i++], 1u);     // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(i, value.size());
}

TEST(LeAudioClientParserTest, testPrepareAseCtpReleaseMultiple) {
  std::vector<uint8_t> ase_ids;
  std::vector<uint8_t> value;

  ase_ids.push_back(0x11);
  ase_ids.push_back(0x36);

  PrepareAseCtpRelease(ase_ids, value);

  uint8_t i = 0;
  ASSERT_NE(value.size(), 0u);
  ASSERT_EQ(value[i++], 0x08u);  // Release Opcode
  ASSERT_EQ(value[i++], 2u);     // Number of ASEs
  ASSERT_EQ(value[i++], 0x11u);  // ASE[0] ID
  ASSERT_EQ(value[i++], 0x36u);  // ASE[0] ID
  ASSERT_EQ(i, value.size());
}

}  // namespace ascs

namespace tmap {

TEST(LeAudioClientParserTest, testParseTmapRoleValid) {
  std::bitset<16> role;
  const uint8_t value[] = {0x3F, 0x00};

  ASSERT_TRUE(ParseTmapRole(role, 2, value));

  ASSERT_EQ(role, 0x003F);  // All possible TMAP roles
}

TEST(LeAudioClientParserTest, testParseTmapRoleInvalidLen) {
  std::bitset<16> role;
  const uint8_t value[] = {0x00, 0x3F};

  ASSERT_FALSE(ParseTmapRole(role, 3, value));
}

}  // namespace tmap

}  // namespace client_parser
}  // namespace le_audio
