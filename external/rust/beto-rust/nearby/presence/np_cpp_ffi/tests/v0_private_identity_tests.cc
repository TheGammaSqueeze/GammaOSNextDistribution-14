// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "nearby_protocol.h"
#include "np_cpp_test.h"
#include "gtest/gtest.h"

#include <algorithm>

static nearby_protocol::RawAdvertisementPayload
    V0AdvPrivateIdentity(nearby_protocol::ByteBuffer<255>(
        {20,
         {
             0x00,             // Adv Header
             0x21,             // private DE w/ a 2 byte payload
             0x22, 0x22,       // salt
             0x85, 0xBF, 0xA8, // encrypted de contents, Tx Power with value 3
             0x83, 0x58, 0x7C, 0x50, 0xCF, 0x98, 0x38,
             0xA7, 0x8A, 0xC0, 0x1C, 0x96, 0xF9,
         }}));

static uint8_t encrypted_metadata[] = {
    0x26, 0xC5, 0xEA, 0xD4, 0xED, 0x58, 0xF8, 0xFC, 0xE8, 0xF4, 0xAB, 0x0C,
    0x93, 0x2B, 0x75, 0xAA, 0x74, 0x39, 0x67, 0xDB, 0x1E, 0xF2, 0x33, 0xB5,
    0x43, 0xCC, 0x94, 0xAA, 0xA3, 0xBB, 0xB9, 0x4C, 0xBF, 0x57, 0x77, 0xD0,
    0x43, 0x0C, 0x7F, 0xF7, 0x36, 0x03, 0x29, 0xE0, 0x57, 0xBA, 0x97, 0x7F,
    0xF2, 0xD1, 0x51, 0xDB, 0xC9, 0x01, 0x47, 0xE7, 0x48, 0x36,
};

static std::array<uint8_t, 32> legacy_metadata_key_hmac = {
    0x88, 0x33, 0xDE, 0xD5, 0x4D, 0x00, 0x92, 0xE8, 0x80, 0x70, 0xD5,
    0x1F, 0x18, 0xEC, 0x22, 0x45, 0x75, 0x7C, 0x24, 0xDF, 0xE3, 0x8C,
    0xB2, 0xDE, 0x77, 0xB6, 0x78, 0x85, 0xFC, 0xA5, 0x67, 0x4D,
};

// The canned data in this test was taken from np_adv/tests/examples_v0.rs
TEST_F(NpCppTest, V0PrivateIdentitySimpleCase) {
  auto slab_result = nearby_protocol::CredentialSlab::TryCreate();
  ASSERT_TRUE(slab_result.ok());

  std::span<uint8_t> metadata_span(encrypted_metadata);
  nearby_protocol::MatchedCredentialData match_data(123, metadata_span);

  std::array<uint8_t, 32> key_seed = {};
  std::fill_n(key_seed.begin(), 32, 0x11);

  nearby_protocol::V0MatchableCredential v0_cred(
      key_seed, legacy_metadata_key_hmac, match_data);

  auto add_result = slab_result->AddV0Credential(v0_cred);
  ASSERT_EQ(add_result, absl::OkStatus());

  auto book_result =
      nearby_protocol::CredentialBook::TryCreateFromSlab(*slab_result);
  ASSERT_TRUE(book_result.ok());

  auto deserialize_result =
      nearby_protocol::Deserializer::DeserializeAdvertisement(
          V0AdvPrivateIdentity, *book_result);
  ASSERT_EQ(deserialize_result.GetKind(),
            nearby_protocol::DeserializeAdvertisementResultKind::V0);

  auto v0_adv = deserialize_result.IntoV0();
  auto kind = v0_adv.GetKind();
  ASSERT_EQ(kind, nearby_protocol::DeserializedV0AdvertisementKind::Legible);

  auto legible_adv = v0_adv.IntoLegible();
  auto identity_kind = legible_adv.GetIdentityKind();
  ASSERT_EQ(identity_kind,
            nearby_protocol::DeserializedV0IdentityKind::Decrypted);
  ASSERT_EQ(legible_adv.GetNumberOfDataElements(), 1);

  auto payload = legible_adv.IntoPayload();
  auto de = payload.TryGetDataElement(0);
  ASSERT_TRUE(de.ok());

  auto metadata = payload.DecryptMetadata();
  ASSERT_TRUE(metadata.ok());
  ASSERT_EQ(std::string("{\"name\":\"Alice\",\"email\":\"alice@gmail.com\"}"),
            std::string(metadata->begin(), metadata->end()));

  auto identity_details = payload.GetIdentityDetails();
  ASSERT_TRUE(identity_details.ok());
  ASSERT_EQ(identity_details->cred_id, 123);
  ASSERT_EQ(identity_details->identity_type,
            nearby_protocol::EncryptedIdentityType::Private);

  auto de_type = de->GetKind();
  ASSERT_EQ(de_type, nearby_protocol::V0DataElementKind::TxPower);

  auto tx_power_de = de->AsTxPower();
  ASSERT_EQ(tx_power_de.tx_power, 3);
}

static nearby_protocol::CredentialBook CreateEmptyCredBook() {
  auto slab = nearby_protocol::CredentialSlab::TryCreate().value();
  auto book = nearby_protocol::CredentialBook::TryCreateFromSlab(slab).value();
  return book;
}

TEST_F(NpCppTest, V0PrivateIdentityEmptyBook) {
  auto book = CreateEmptyCredBook();
  auto deserialize_result =
      nearby_protocol::Deserializer::DeserializeAdvertisement(
          V0AdvPrivateIdentity, book);
  ASSERT_EQ(deserialize_result.GetKind(),
            nearby_protocol::DeserializeAdvertisementResultKind::V0);

  auto v0_adv = deserialize_result.IntoV0();
  ASSERT_EQ(
      v0_adv.GetKind(),
      nearby_protocol::DeserializedV0AdvertisementKind::NoMatchingCredentials);

  // Should not be able to actually access contents
  ASSERT_DEATH([[maybe_unused]] auto failure = v0_adv.IntoLegible(), "");
}

TEST_F(NpCppTest, V0PrivateIdentityNoMatchingCreds) {
  auto slab_result = nearby_protocol::CredentialSlab::TryCreate();
  ASSERT_TRUE(slab_result.ok());

  uint8_t metadata[] = {0};
  std::span<uint8_t> metadata_span(metadata);
  nearby_protocol::MatchedCredentialData match_data(123, metadata_span);

  // A randomly picked key seed, does NOT match what was used for the canned adv
  std::array<uint8_t, 32> key_seed = {};
  std::fill_n(key_seed.begin(), 31, 0x11);

  nearby_protocol::V0MatchableCredential v0_cred(
      key_seed, legacy_metadata_key_hmac, match_data);

  auto add_result = slab_result->AddV0Credential(v0_cred);
  ASSERT_EQ(add_result, absl::OkStatus());

  auto book_result =
      nearby_protocol::CredentialBook::TryCreateFromSlab(*slab_result);
  ASSERT_TRUE(book_result.ok());

  auto deserialize_result =
      nearby_protocol::Deserializer::DeserializeAdvertisement(
          V0AdvPrivateIdentity, *book_result);
  ASSERT_EQ(deserialize_result.GetKind(),
            nearby_protocol::DeserializeAdvertisementResultKind::V0);

  auto v0_adv = deserialize_result.IntoV0();
  ASSERT_EQ(
      v0_adv.GetKind(),
      nearby_protocol::DeserializedV0AdvertisementKind::NoMatchingCredentials);

  // Should not be able to actually access contents
  ASSERT_DEATH([[maybe_unused]] auto failure = v0_adv.IntoLegible(), "");
}

// Make sure the correct credential is matched out of multiple provided
TEST_F(NpCppTest, V0PrivateIdentityMultipleCredentials) {
  auto slab = nearby_protocol::CredentialSlab::TryCreate().value();
  std::span<uint8_t> metadata_span(encrypted_metadata);
  std::array<uint8_t, 32> key_seed = {};

  // Non matching credential
  nearby_protocol::MatchedCredentialData match_data(123, metadata_span);
  std::fill_n(key_seed.begin(), 32, 0x12);
  nearby_protocol::V0MatchableCredential v0_cred(
      key_seed, legacy_metadata_key_hmac, match_data);
  ASSERT_TRUE(slab.AddV0Credential(v0_cred).ok());

  // Matching credential
  nearby_protocol::MatchedCredentialData match_data2(456, metadata_span);
  std::fill_n(key_seed.begin(), 32, 0x11);
  nearby_protocol::V0MatchableCredential v0_cred2(
      key_seed, legacy_metadata_key_hmac, match_data2);
  ASSERT_TRUE(slab.AddV0Credential(v0_cred2).ok());

  // Non matching credential
  nearby_protocol::MatchedCredentialData match_data3(789, metadata_span);
  std::fill_n(key_seed.begin(), 32, 0x13);
  nearby_protocol::V0MatchableCredential v0_cred3(
      key_seed, legacy_metadata_key_hmac, match_data3);
  ASSERT_TRUE(slab.AddV0Credential(v0_cred3).ok());

  auto book =
      nearby_protocol::CredentialBook::TryCreateFromSlab(slab).value();
  auto legible_adv =
      nearby_protocol::Deserializer::DeserializeAdvertisement(
          V0AdvPrivateIdentity, book).IntoV0().IntoLegible();
  ASSERT_EQ(legible_adv.GetIdentityKind(),
            nearby_protocol::DeserializedV0IdentityKind::Decrypted);
  ASSERT_EQ(legible_adv.GetNumberOfDataElements(), 1);

  auto payload = legible_adv.IntoPayload();
  ASSERT_TRUE(payload.TryGetDataElement(0).ok());

  // Make sure the correct credential matches
  auto identity_details = payload.GetIdentityDetails();
  ASSERT_TRUE(identity_details.ok());
  ASSERT_EQ(identity_details->cred_id, 456);
  ASSERT_EQ(identity_details->identity_type,
            nearby_protocol::EncryptedIdentityType::Private);
}
