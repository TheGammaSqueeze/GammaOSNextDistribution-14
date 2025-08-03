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

#pragma once

#include <optional>
#include <string>
#include <vector>

#include <fs_avb/fs_avb_util.h>

uint8_t HexDigitToByte(char c);

bool HexToBytes(const std::string &hex, std::vector<uint8_t> *bytes);

std::string BytesToHex(const std::vector<uint8_t> &bytes);

// The abstract class of SHA algorithms.
class ShaHasher {
 protected:
  const uint32_t digest_size_;

  ShaHasher(uint32_t digest_size) : digest_size_(digest_size) {}

 public:
  virtual ~ShaHasher() {}

  uint32_t GetDigestSize() const { return digest_size_; }

  virtual bool CalculateDigest(const void *buffer, size_t size,
                               const void *salt, uint32_t block_length,
                               uint8_t *digest) const = 0;
};

template <typename CTX_TYPE>
class ShaHasherImpl : public ShaHasher {
 private:
  typedef int (*InitFunc)(CTX_TYPE *);
  typedef int (*UpdateFunc)(CTX_TYPE *sha, const void *data, size_t len);
  typedef int (*FinalFunc)(uint8_t *md, CTX_TYPE *sha);

  const InitFunc init_func_;
  const UpdateFunc update_func_;
  const FinalFunc final_func_;

 public:
  ShaHasherImpl(InitFunc init_func, UpdateFunc update_func,
                FinalFunc final_func, uint32_t digest_size)
      : ShaHasher(digest_size),
        init_func_(init_func),
        update_func_(update_func),
        final_func_(final_func) {}

  ~ShaHasherImpl() {}

  bool CalculateDigest(const void *buffer, size_t size, const void *salt,
                       uint32_t salt_length, uint8_t *digest) const {
    CTX_TYPE ctx;
    if (init_func_(&ctx) != 1) {
      return false;
    }
    if (update_func_(&ctx, salt, salt_length) != 1) {
      return false;
    }
    if (update_func_(&ctx, buffer, size) != 1) {
      return false;
    }
    if (final_func_(digest, &ctx) != 1) {
      return false;
    }
    return true;
  }
};

// Creates a hasher with the parameters corresponding to the algorithm name.
std::unique_ptr<ShaHasher> CreateShaHasher(const std::string &algorithm);

// Checks whether the public key is an official GSI key or not.
bool ValidatePublicKeyBlob(const std::string &key_blob_to_validate);

uint32_t GetSdkLevel();

uint32_t GetProductFirstApiLevel();

uint32_t GetVendorApiLevel();

// Return board API level on GRF devices.
// On non-GRF, this function will return std::nullopt.
std::optional<uint32_t> GetBoardApiLevel();

bool IsReleasedAndroidVersion();

bool IsAutomotiveDevice();

bool IsTvDevice();

bool IsGoDevice();
