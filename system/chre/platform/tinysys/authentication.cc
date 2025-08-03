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

#include <inttypes.h>
#include <cstdint>

#include "chre/platform/log.h"
#include "chre/platform/shared/authentication.h"
#include "chre/util/macros.h"

#include "mbedtls/pk.h"
#include "mbedtls/sha256.h"

#include "cpufreq_vote.h"

namespace chre {
namespace {

// A data structure needed for SCP chip frequency change
DECLARE_OPPDEV(gChreScpFreqVote);

// All the size below are in bytes
constexpr uint32_t kEcdsaP256SigSize = 64;
constexpr uint32_t kEcdsaP256PublicKeySize = 64;
constexpr uint32_t kHeaderSize = 0x1000;
constexpr uint32_t kSha256HashSize = 32;

// ASCII of "CHRE", in BE
constexpr uint32_t kChreMagicNumber = 0x45524843;

// Production public key
const uint8_t kGooglePublicKey[kEcdsaP256PublicKeySize] = {
    0x97, 0x66, 0x1f, 0xe7, 0x26, 0xc5, 0xc3, 0x9c, 0xe6, 0x71, 0x59,
    0x1f, 0x26, 0x3b, 0x1c, 0x87, 0x50, 0x7f, 0xad, 0x4f, 0xeb, 0x4b,
    0xe5, 0x3b, 0xee, 0x76, 0xff, 0x80, 0x6a, 0x8b, 0x6d, 0xed, 0x58,
    0xd7, 0xed, 0xf3, 0x18, 0x9e, 0x9a, 0xac, 0xcf, 0xfc, 0xd2, 0x7,
    0x35, 0x64, 0x54, 0xcc, 0xbc, 0x8b, 0xe0, 0x6c, 0x77, 0xbe, 0xbb,
    0x1b, 0xdd, 0x18, 0x6d, 0x77, 0xfe, 0xb7, 0x0,  0xd5};

const uint8_t *const kTrustedPublicKeys[] = {kGooglePublicKey};

/**
 * A data structure encapsulating metadata necessary for nanoapp binary
 * signature verification.
 *
 * Note that the structure field names that start with 'reserved' are currently
 * unused.
 */
struct HeaderInfo {
  /**
   * A magic number indicating the start of the header info, ASCII decodes to
   * 'CHRE'.
   */
  uint32_t magic;

  uint32_t headerVersion;

  // TODO(b/260099197): We should have a hardware backed rollback info check.
  uint32_t reservedRollbackInfo;

  /** The size in bytes of the actual nanoapp binary. */
  uint32_t binaryLength;

  /** The flag indicating the public key size. */
  uint64_t flags[2];

  /** The SHA-256 hash of the actual nanoapp binary. */
  uint8_t binarySha256[kSha256HashSize];

  uint8_t reservedChipId[32];

  uint8_t reservedAuthConfig[256];

  uint8_t reservedImageConfig[256];
};

/**
 * A header containing information relevant to nanoapp signature authentication
 * that is tacked onto every signed nanoapp.
 */
struct ImageHeader {
  /** The zero-padded signature of the nanoapp binary. */
  uint8_t signature[512];

  /** The zero-padded public key for the key pair used to sign the hash, which
   * we use to verify whether we trust the signer or not. */
  uint8_t publicKey[512];

  /** @see struct HeaderInfo. */
  HeaderInfo headerInfo;
};

class Authenticator {
 public:
  Authenticator() {
    scp_vote_opp(&gChreScpFreqVote, CLK_OPP2);
    mbedtls_ecp_group_init(&mGroup);
    mbedtls_ecp_point_init(&mQ);
    mbedtls_mpi_init(&mR);
    mbedtls_mpi_init(&mS);
  }

  ~Authenticator() {
    mbedtls_mpi_free(&mS);
    mbedtls_mpi_free(&mR);
    mbedtls_ecp_point_free(&mQ);
    mbedtls_ecp_group_free(&mGroup);
    scp_unvote_opp(&gChreScpFreqVote, CLK_OPP2);
  }

  bool loadEcpGroup() {
    int result = mbedtls_ecp_group_load(&mGroup, MBEDTLS_ECP_DP_SECP256R1);
    if (result != 0) {
      LOGE("Failed to load ecp group. Error code: %d", result);
      return false;
    }
    return true;
  }

  bool loadPublicKey(const uint8_t *publicKey) {
    // 0x04 prefix is required by mbedtls
    constexpr uint8_t kPublicKeyPrefix = 0x04;
    uint8_t buffer[kEcdsaP256PublicKeySize + 1] = {kPublicKeyPrefix};
    memcpy(buffer + 1, publicKey, kEcdsaP256PublicKeySize);
    int result =
        mbedtls_ecp_point_read_binary(&mGroup, &mQ, buffer, ARRAY_SIZE(buffer));
    if (result != 0) {
      LOGE("Failed to load the public key. Error code: %d", result);
      return false;
    }
    return true;
  }

  bool loadSignature(const ImageHeader *header) {
    constexpr uint32_t kRSigSize = kEcdsaP256SigSize / 2;
    constexpr uint32_t kSSigSize = kEcdsaP256SigSize / 2;
    int result = mbedtls_mpi_read_binary(&mR, header->signature, kRSigSize);
    if (result != 0) {
      LOGE("Failed to read r signature. Error code: %d", result);
      return false;
    }
    result =
        mbedtls_mpi_read_binary(&mS, header->signature + kRSigSize, kSSigSize);
    if (result != 0) {
      LOGE("Failed to read s signature. Error code: %d", result);
      return false;
    }
    return true;
  }

  bool authenticate(const void *binary) {
    constexpr size_t kDataOffset = 0x200;
    constexpr size_t kDataSize = kHeaderSize - kDataOffset;
    auto data = static_cast<const uint8_t *>(binary) + kDataOffset;
    unsigned char digest[kSha256HashSize] = {};
    mbedtls_sha256(data, kDataSize, digest, /* is224= */ 0);
    int result = mbedtls_ecdsa_verify(&mGroup, digest, ARRAY_SIZE(digest), &mQ,
                                      &mR, &mS);
    if (result != 0) {
      LOGE("Signature verification failed. Error code: %d", result);
      return false;
    }
    return true;
  }

 private:
  mbedtls_ecp_group mGroup;
  mbedtls_ecp_point mQ;
  mbedtls_mpi mR;
  mbedtls_mpi mS;
};

/** Retrieves the public key length based on the flag. */
uint32_t getPublicKeyLength(const uint64_t *flag) {
  constexpr int kPkSizeMaskPosition = 9;
  constexpr uint64_t kPkSizeMask = 0x3;
  uint8_t keySizeFlag = ((*flag) >> kPkSizeMaskPosition) & kPkSizeMask;
  switch (keySizeFlag) {
    case 0:
      return 64;
    case 1:
      return 96;
    case 2:
      return 132;
    default:
      LOGE("Unsupported flags in nanoapp header!");
      return 0;
  }
}

/** Checks if the hash prvided in the header is derived from the image. */
bool hasCorrectHash(const void *head, size_t realImageSize,
                    const uint8_t *hashProvided) {
  auto image = static_cast<const uint8_t *>(head) + kHeaderSize;
  uint8_t hashCalculated[kSha256HashSize] = {};
  mbedtls_sha256(image, realImageSize, hashCalculated, /* is224= */ 0);
  return memcmp(hashCalculated, hashProvided, kSha256HashSize) == 0;
}

/** Checks if the public key in the header matches the production public key. */
bool isValidProductionPublicKey(const uint8_t *publicKey,
                                size_t publicKeyLength) {
  if (publicKeyLength != kEcdsaP256PublicKeySize) {
    LOGE("Public key length %zu is unexpected.", publicKeyLength);
    return false;
  }
  for (size_t i = 0; i < ARRAY_SIZE(kTrustedPublicKeys); i++) {
    if (memcmp(kTrustedPublicKeys[i], publicKey, kEcdsaP256PublicKeySize) ==
        0) {
      return true;
    }
  }
  return false;
}
}  // anonymous namespace

bool authenticateBinary(const void *binary, size_t appBinaryLen,
                        void **realBinaryStart) {
#ifndef CHRE_NAPP_AUTHENTICATION_ENABLED
  UNUSED_VAR(binary);
  UNUSED_VAR(realBinaryStart);
  LOGW(
      "Nanoapp authentication is disabled, which exposes the device to "
      "security risks!");
  return true;
#endif
  if (appBinaryLen <= kHeaderSize) {
    LOGE("Binary size %zu is too short.", appBinaryLen);
    return false;
  }
  Authenticator authenticator;
  auto *header = static_cast<const ImageHeader *>(binary);
  const uint8_t *imageHash = header->headerInfo.binarySha256;
  const uint8_t *publicKey = header->publicKey;
  const uint32_t expectedAppBinaryLength =
      header->headerInfo.binaryLength + kHeaderSize;

  if (header->headerInfo.magic != kChreMagicNumber) {
    LOGE("Mismatched magic number.");
  } else if (header->headerInfo.headerVersion != 1) {
    LOGE("Header version %" PRIu32 " is unsupported.",
         header->headerInfo.headerVersion);
  } else if (expectedAppBinaryLength != appBinaryLen) {
    LOGE("Invalid binary length %zu. Expected %" PRIu32, appBinaryLen,
         expectedAppBinaryLength);
  } else if (!isValidProductionPublicKey(
                 publicKey, getPublicKeyLength(header->headerInfo.flags))) {
    LOGE("Invalid public key attached on the image.");
  } else if (!hasCorrectHash(binary, header->headerInfo.binaryLength,
                             imageHash)) {
    LOGE("Hash of the nanoapp image is incorrect.");
  } else if (!authenticator.loadEcpGroup() ||
             !authenticator.loadPublicKey(publicKey) ||
             !authenticator.loadSignature(header)) {
    LOGE("Failed to load authentication data.");
  } else if (!authenticator.authenticate(binary)) {
    LOGE("Failed to authenticate the image.");
  } else {
    *realBinaryStart = reinterpret_cast<void *>(
        reinterpret_cast<uintptr_t>(binary) + kHeaderSize);
    LOGI("Image is authenticated successfully!");
    return true;
  }
  return false;
}
}  // namespace chre
