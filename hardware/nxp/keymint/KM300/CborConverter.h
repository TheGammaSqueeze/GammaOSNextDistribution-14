/*
 **
 ** Copyright 2020, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */
#pragma once

#include <iostream>
#include <memory>
#include <numeric>
#include <vector>

#include <cppbor.h>
#include <cppbor_parse.h>

#include <aidl/android/hardware/security/keymint/Certificate.h>
#include <aidl/android/hardware/security/keymint/IKeyMintDevice.h>
#include <aidl/android/hardware/security/secureclock/TimeStampToken.h>
#include <aidl/android/hardware/security/sharedsecret/ISharedSecret.h>

#include <keymaster/android_keymaster_messages.h>

namespace keymint::javacard {
using aidl::android::hardware::security::keymint::AttestationKey;
using aidl::android::hardware::security::keymint::Certificate;
using aidl::android::hardware::security::keymint::HardwareAuthToken;
using aidl::android::hardware::security::keymint::KeyCharacteristics;
using aidl::android::hardware::security::keymint::KeyParameter;
using aidl::android::hardware::security::secureclock::TimeStampToken;
using aidl::android::hardware::security::sharedsecret::SharedSecretParameters;
using cppbor::Array;
using cppbor::Bstr;
using cppbor::EncodedItem;
using cppbor::Item;
using cppbor::MajorType;
using cppbor::Map;
using cppbor::Nint;
using cppbor::Tstr;
using cppbor::Uint;
using std::string;
using std::unique_ptr;
using std::vector;

class CborConverter {
  public:
    CborConverter() = default;

    ~CborConverter() = default;

    std::tuple<std::unique_ptr<Item>, keymaster_error_t>
    decodeData(const std::vector<uint8_t>& response);

    std::optional<uint64_t> getUint64(const unique_ptr<Item>& item);

    std::optional<uint64_t> getUint64(const unique_ptr<Item>& item, const uint32_t pos);

    std::optional<SharedSecretParameters>
    getSharedSecretParameters(const std::unique_ptr<Item>& item, const uint32_t pos);

    std::optional<string> getByteArrayStr(const unique_ptr<Item>& item, const uint32_t pos);

    std::optional<string> getTextStr(const unique_ptr<Item>& item, const uint32_t pos);

    std::optional<std::vector<uint8_t>> getByteArrayVec(const unique_ptr<Item>& item,
                                                        const uint32_t pos);

    std::optional<vector<KeyParameter>> getKeyParameters(const unique_ptr<Item>& item,
                                                         const uint32_t pos);

    bool addKeyparameters(Array& array, const vector<KeyParameter>& keyParams);

    bool addAttestationKey(Array& array, const std::optional<AttestationKey>& attestationKey);

    bool addHardwareAuthToken(Array& array, const HardwareAuthToken& authToken);

    bool addSharedSecretParameters(Array& array, const vector<SharedSecretParameters>& params);

    std::optional<TimeStampToken> getTimeStampToken(const std::unique_ptr<Item>& item,
                                                    const uint32_t pos);

    std::optional<vector<KeyCharacteristics>>
    getKeyCharacteristics(const std::unique_ptr<Item>& item, const uint32_t pos);

    std::optional<vector<Certificate>> getCertificateChain(const std::unique_ptr<Item>& item,
                                                           const uint32_t pos);

     std::optional<vector<vector<uint8_t>>> getMultiByteArray(const unique_ptr<Item>& item,
                                                              const uint32_t pos);

    bool addTimeStampToken(Array& array, const TimeStampToken& token);

    std::optional<Map> getMapItem(const std::unique_ptr<Item>& item, const uint32_t pos);

    std::optional<Array> getArrayItem(const std::unique_ptr<Item>& item, const uint32_t pos);

    std::optional<keymaster_error_t> getErrorCode(const std::unique_ptr<Item>& item,
                                                  const uint32_t pos);

  private:
    /**
     * Get the type of the Item pointer.
     */
    inline MajorType getType(const unique_ptr<Item>& item) { return item.get()->type(); }

    /**
     * Construct Keyparameter structure from the pair of key and value. If TagType is  ENUM_REP the
     * value contains binary string. If TagType is UINT_REP or ULONG_REP the value contains Array of
     * unsigned integers.
     */
    std::optional<std::vector<KeyParameter>> getKeyParameter(
        const std::pair<const std::unique_ptr<Item>&, const std::unique_ptr<Item>&> pair);

    /**
     * Get the sub item pointer from the root item pointer at the given position.
     */
    inline std::optional<unique_ptr<Item>> getItemAtPos(const unique_ptr<Item>& item,
                                                        const uint32_t pos) {
        Array* arr = nullptr;

        if (MajorType::ARRAY != getType(item)) {
            return std::nullopt;
        }
        arr = const_cast<Array*>(item.get()->asArray());
        if (arr->size() < (pos + 1)) {
            return std::nullopt;
        }
        return std::move((*arr)[pos]);
    }
};

}  // namespace keymint::javacard
