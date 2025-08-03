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

#include "CborConverter.h"

#include <map>
#include <string>

#include <android-base/logging.h>

#include <KeyMintUtils.h>

namespace keymint::javacard {
using ::aidl::android::hardware::security::keymint::KeyParameterValue;
using ::aidl::android::hardware::security::keymint::SecurityLevel;
using ::aidl::android::hardware::security::keymint::km_utils::kmParam2Aidl;
using ::aidl::android::hardware::security::keymint::km_utils::legacy_enum_conversion;
using ::aidl::android::hardware::security::keymint::km_utils::typeFromTag;

constexpr int SB_ENFORCED = 0;
constexpr int TEE_ENFORCED = 1;
constexpr int SW_ENFORCED = 2;

namespace {

template <KeyParameterValue::Tag aidl_tag>
std::optional<uint32_t> aidlEnumVal2Uint32(const KeyParameterValue& value) {
    return (value.getTag() == aidl_tag)
               ? std::optional(static_cast<uint32_t>(value.get<aidl_tag>()))
               : std::nullopt;
}

std::optional<uint32_t> aidlEnumParam2Uint32(const KeyParameter& param) {
    auto tag = legacy_enum_conversion(param.tag);
    switch (tag) {
    case KM_TAG_PURPOSE:
        return aidlEnumVal2Uint32<KeyParameterValue::keyPurpose>(param.value);
    case KM_TAG_ALGORITHM:
        return aidlEnumVal2Uint32<KeyParameterValue::algorithm>(param.value);
    case KM_TAG_BLOCK_MODE:
        return aidlEnumVal2Uint32<KeyParameterValue::blockMode>(param.value);
    case KM_TAG_DIGEST:
    case KM_TAG_RSA_OAEP_MGF_DIGEST:
        return aidlEnumVal2Uint32<KeyParameterValue::digest>(param.value);
    case KM_TAG_PADDING:
        return aidlEnumVal2Uint32<KeyParameterValue::paddingMode>(param.value);
    case KM_TAG_EC_CURVE:
        return aidlEnumVal2Uint32<KeyParameterValue::ecCurve>(param.value);
    case KM_TAG_USER_AUTH_TYPE:
        return aidlEnumVal2Uint32<KeyParameterValue::hardwareAuthenticatorType>(param.value);
    case KM_TAG_ORIGIN:
        return aidlEnumVal2Uint32<KeyParameterValue::origin>(param.value);
    case KM_TAG_BLOB_USAGE_REQUIREMENTS:
    case KM_TAG_KDF:
    default:
        LOG(FATAL) << "Unknown or unused enum tag: Something is broken";
        return std::nullopt;
    }
}

}  // namespace

bool CborConverter::addAttestationKey(Array& array,
                                      const std::optional<AttestationKey>& attestationKey) {
    if (attestationKey.has_value()) {
        array.add(Bstr(attestationKey->keyBlob));
        addKeyparameters(array, attestationKey->attestKeyParams);
        array.add(Bstr(attestationKey->issuerSubjectName));
    } else {
        array.add(std::move(Bstr(vector<uint8_t>(0))));
        array.add(std::move(Map()));
        array.add(std::move(Bstr(vector<uint8_t>(0))));
    }
    return true;
}

bool CborConverter::addKeyparameters(Array& array, const vector<KeyParameter>& keyParams) {
    Map map;
    std::map<uint32_t, vector<uint8_t>> enum_repetition;
    std::map<uint32_t, Array> uint_repetition;
    for (auto& param : keyParams) {
        auto tag = legacy_enum_conversion(param.tag);
        switch (typeFromTag(tag)) {
        case KM_ENUM: {
            auto paramEnum = aidlEnumParam2Uint32(param);
            if (paramEnum.has_value()) {
                map.add(static_cast<uint32_t>(tag), *paramEnum);
            }
            break;
        }
        case KM_UINT:
            if (param.value.getTag() == KeyParameterValue::integer) {
                uint32_t intVal = param.value.get<KeyParameterValue::integer>();
                map.add(static_cast<uint32_t>(tag), intVal);
            }
            break;
        case KM_UINT_REP:
            if (param.value.getTag() == KeyParameterValue::integer) {
                uint32_t intVal = param.value.get<KeyParameterValue::integer>();
                uint_repetition[static_cast<uint32_t>(tag)].add(intVal);
            }
            break;
        case KM_ENUM_REP: {
            auto paramEnumRep = aidlEnumParam2Uint32(param);
            if (paramEnumRep.has_value()) {
                enum_repetition[static_cast<uint32_t>(tag)].push_back(*paramEnumRep);
            }
            break;
        }
        case KM_ULONG:
            if (param.value.getTag() == KeyParameterValue::longInteger) {
                uint64_t longVal = param.value.get<KeyParameterValue::longInteger>();
                map.add(static_cast<uint32_t>(tag), longVal);
            }
            break;
        case KM_ULONG_REP:
            if (param.value.getTag() == KeyParameterValue::longInteger) {
                uint64_t longVal = param.value.get<KeyParameterValue::longInteger>();
                uint_repetition[static_cast<uint32_t>(tag)].add(longVal);
            }
            break;
        case KM_DATE:
            if (param.value.getTag() == KeyParameterValue::dateTime) {
                uint64_t dateVal = param.value.get<KeyParameterValue::dateTime>();
                map.add(static_cast<uint32_t>(tag), dateVal);
            }
            break;
        case KM_BOOL:
            map.add(static_cast<uint32_t>(tag), 1 /* true */);
            break;
        case KM_BIGNUM:
        case KM_BYTES:
            if (param.value.getTag() == KeyParameterValue::blob) {
                const auto& value = param.value.get<KeyParameterValue::blob>();
                map.add(static_cast<uint32_t>(tag), value);
            }
            break;
        case KM_INVALID:
            break;
        }
    }

    for (auto const& [key, val] : enum_repetition) {
        Bstr bstr(val);
        map.add(key, std::move(bstr));
    }

    for (auto& [key, val] : uint_repetition) {
        map.add(key, std::move(val));
    }
    array.add(std::move(map));
    return true;
}

// Array of three maps
std::optional<vector<KeyCharacteristics>>
CborConverter::getKeyCharacteristics(const unique_ptr<Item>& item, const uint32_t pos) {
    vector<KeyCharacteristics> keyCharacteristics;
    auto arrayItem = getItemAtPos(item, pos);
    if (!arrayItem || (MajorType::ARRAY != getType(arrayItem.value()))) {
        return std::nullopt;
    }
    KeyCharacteristics swEnf{SecurityLevel::KEYSTORE, {}};
    KeyCharacteristics teeEnf{SecurityLevel::TRUSTED_ENVIRONMENT, {}};
    KeyCharacteristics sbEnf{SecurityLevel::STRONGBOX, {}};

    auto optSbEnf = getKeyParameters(arrayItem.value(), SB_ENFORCED);
    if (!optSbEnf) {
        return std::nullopt;
    }
    sbEnf.authorizations = std::move(optSbEnf.value());
    auto optTeeEnf = getKeyParameters(arrayItem.value(), TEE_ENFORCED);
    if (!optTeeEnf) {
        return std::nullopt;
    }
    teeEnf.authorizations = std::move(optTeeEnf.value());
    auto optSwEnf = getKeyParameters(arrayItem.value(), SW_ENFORCED);
    if (!optSwEnf) {
        return std::nullopt;
    }
    swEnf.authorizations = std::move(optSwEnf.value());
    // VTS will fail if the authorizations list is empty.
    if (!sbEnf.authorizations.empty()) keyCharacteristics.push_back(std::move(sbEnf));
    if (!teeEnf.authorizations.empty()) keyCharacteristics.push_back(std::move(teeEnf));
    if (!swEnf.authorizations.empty()) keyCharacteristics.push_back(std::move(swEnf));
    return keyCharacteristics;
}

std::optional<std::vector<KeyParameter>> CborConverter::getKeyParameter(
    const std::pair<const std::unique_ptr<Item>&, const std::unique_ptr<Item>&> pair) {
    std::vector<KeyParameter> keyParams;
    keymaster_tag_t key;
    auto optValue = getUint64(pair.first);
    if (!optValue) {
        return std::nullopt;
    }
    key = static_cast<keymaster_tag_t>(optValue.value());
    switch (keymaster_tag_get_type(key)) {
    case KM_ENUM_REP: {
        /* ENUM_REP contains values encoded in a Byte string */
        const Bstr* bstr = pair.second.get()->asBstr();
        if (bstr == nullptr) {
            return std::nullopt;
        }
        for (auto bchar : bstr->value()) {
            keymaster_key_param_t keyParam;
            keyParam.tag = key;
            keyParam.enumerated = bchar;
            keyParams.push_back(kmParam2Aidl(keyParam));
        }
        return keyParams;
    }
    case KM_ENUM: {
        keymaster_key_param_t keyParam;
        keyParam.tag = key;
        if (!(optValue = getUint64(pair.second))) {
            return std::nullopt;
        }
        keyParam.enumerated = static_cast<uint32_t>(optValue.value());
        keyParams.push_back(kmParam2Aidl(keyParam));
        return keyParams;
    }
    case KM_UINT: {
        keymaster_key_param_t keyParam;
        keyParam.tag = key;
        if (!(optValue = getUint64(pair.second))) {
            return std::nullopt;
        }
        keyParam.integer = static_cast<uint32_t>(optValue.value());
        keyParams.push_back(kmParam2Aidl(keyParam));
        return keyParams;
    }
    case KM_ULONG: {
        keymaster_key_param_t keyParam;
        keyParam.tag = key;
        if (!(optValue = getUint64(pair.second))) {
            return std::nullopt;
        }
        keyParam.long_integer = optValue.value();
        keyParams.push_back(kmParam2Aidl(keyParam));
        return keyParams;
    }
    case KM_UINT_REP: {
        /* UINT_REP contains values encoded in a Array */
        Array* array = const_cast<Array*>(pair.second.get()->asArray());
        if (array == nullptr) return std::nullopt;
        for (int i = 0; i < array->size(); i++) {
            keymaster_key_param_t keyParam;
            keyParam.tag = key;
            const std::unique_ptr<Item>& item = array->get(i);
            if (!(optValue = getUint64(item))) {
                return std::nullopt;
            }
            keyParam.integer = static_cast<uint32_t>(optValue.value());
            keyParams.push_back(kmParam2Aidl(keyParam));
        }
        return keyParams;
    }
    case KM_ULONG_REP: {
        /* ULONG_REP contains values encoded in a Array */
        Array* array = const_cast<Array*>(pair.second.get()->asArray());
        if (array == nullptr) return std::nullopt;
        for (int i = 0; i < array->size(); i++) {
            keymaster_key_param_t keyParam;
            keyParam.tag = key;
            const std::unique_ptr<Item>& item = array->get(i);
            if (!(optValue = getUint64(item))) {
                return std::nullopt;
            }
            keyParam.long_integer = optValue.value();
            keyParams.push_back(kmParam2Aidl(keyParam));
        }
        return keyParams;
    }
    case KM_DATE: {
        keymaster_key_param_t keyParam;
        keyParam.tag = key;
        if (!(optValue = getUint64(pair.second))) {
            return std::nullopt;
        }
        keyParam.date_time = optValue.value();
        keyParams.push_back(kmParam2Aidl(keyParam));
        return keyParams;
    }
    case KM_BOOL: {
        keymaster_key_param_t keyParam;
        keyParam.tag = key;
        if (!(optValue = getUint64(pair.second))) {
            return std::nullopt;
        }
        // If a tag with this type is present, the value is true.  If absent,
        // false.
        keyParam.boolean = true;
        keyParams.push_back(kmParam2Aidl(keyParam));
        return keyParams;
    }
    case KM_BIGNUM:
    case KM_BYTES: {
        keymaster_key_param_t keyParam;
        keyParam.tag = key;
        const Bstr* bstr = pair.second.get()->asBstr();
        if (bstr == nullptr) return std::nullopt;
        keyParam.blob.data = bstr->value().data();
        keyParam.blob.data_length = bstr->value().size();
        keyParams.push_back(kmParam2Aidl(keyParam));
        return keyParams;
    }
    case KM_INVALID:
        break;
    }
    return std::nullopt;
}

// array of a blobs
std::optional<vector<Certificate>>
CborConverter::getCertificateChain(const std::unique_ptr<Item>& item, const uint32_t pos) {
    vector<Certificate> certChain;
    auto arrayItem = getItemAtPos(item, pos);
    if (!arrayItem || (MajorType::ARRAY != getType(arrayItem.value()))) return std::nullopt;

    const Array* arr = arrayItem.value().get()->asArray();
    for (int i = 0; i < arr->size(); i++) {
        Certificate cert;
        auto optTemp = getByteArrayVec(arrayItem.value(), i);
        if (!optTemp) return std::nullopt;
        cert.encodedCertificate = std::move(optTemp.value());
        certChain.push_back(std::move(cert));
    }
    return certChain;
}

std::optional<string> CborConverter::getTextStr(const unique_ptr<Item>& item, const uint32_t pos) {
    auto textStrItem = getItemAtPos(item, pos);
    if (!textStrItem || (MajorType::TSTR != getType(textStrItem.value()))) {
        return std::nullopt;
    }
    const Tstr* tstr = textStrItem.value().get()->asTstr();
    return tstr->value();
}

std::optional<string> CborConverter::getByteArrayStr(const unique_ptr<Item>& item,
                                                     const uint32_t pos) {
    auto optTemp = getByteArrayVec(item, pos);
    if (!optTemp) {
        return std::nullopt;
    }
    std::string str(optTemp->begin(), optTemp->end());
    return str;
}

std::optional<std::vector<uint8_t>> CborConverter::getByteArrayVec(const unique_ptr<Item>& item,
                                                                   const uint32_t pos) {
    auto strItem = getItemAtPos(item, pos);
    if (!strItem || (MajorType::BSTR != getType(strItem.value()))) {
        return std::nullopt;
    }
    const Bstr* bstr = strItem.value().get()->asBstr();
    return bstr->value();
}

std::optional<SharedSecretParameters>
CborConverter::getSharedSecretParameters(const unique_ptr<Item>& item, const uint32_t pos) {
    SharedSecretParameters params;
    // Array [seed, nonce]
    auto arrayItem = getItemAtPos(item, pos);
    if (!arrayItem || (MajorType::ARRAY != getType(arrayItem.value()))) {
        return std::nullopt;
    }
    auto optSeed = getByteArrayVec(arrayItem.value(), 0);
    auto optNonce = getByteArrayVec(arrayItem.value(), 1);
    if (!optSeed || !optNonce) {
        return std::nullopt;
    }
    params.seed = std::move(optSeed.value());
    params.nonce = std::move(optNonce.value());
    return params;
}

bool CborConverter::addSharedSecretParameters(Array& array,
                                              const vector<SharedSecretParameters>& params) {
    Array cborParamsVec;
    for (auto param : params) {
        Array cborParam;
        cborParam.add(Bstr(param.seed));
        cborParam.add(Bstr(param.nonce));
        cborParamsVec.add(std::move(cborParam));
    }
    array.add(std::move(cborParamsVec));
    return true;
}

bool CborConverter::addTimeStampToken(Array& array, const TimeStampToken& token) {
    Array vToken;
    vToken.add(static_cast<uint64_t>(token.challenge));
    vToken.add(static_cast<uint64_t>(token.timestamp.milliSeconds));
    vToken.add((std::vector<uint8_t>(token.mac)));
    array.add(std::move(vToken));
    return true;
}

bool CborConverter::addHardwareAuthToken(Array& array, const HardwareAuthToken& authToken) {
    Array hwAuthToken;
    hwAuthToken.add(static_cast<uint64_t>(authToken.challenge));
    hwAuthToken.add(static_cast<uint64_t>(authToken.userId));
    hwAuthToken.add(static_cast<uint64_t>(authToken.authenticatorId));
    hwAuthToken.add(static_cast<uint64_t>(authToken.authenticatorType));
    hwAuthToken.add(static_cast<uint64_t>(authToken.timestamp.milliSeconds));
    hwAuthToken.add((std::vector<uint8_t>(authToken.mac)));
    array.add(std::move(hwAuthToken));
    return true;
}

std::optional<TimeStampToken> CborConverter::getTimeStampToken(const unique_ptr<Item>& item,
                                                               const uint32_t pos) {
    TimeStampToken token;
    // {challenge, timestamp, Mac}
    auto optChallenge = getUint64(item, pos);
    auto optTimestampMillis = getUint64(item, pos + 1);
    auto optTemp = getByteArrayVec(item, pos + 2);
    if (!optChallenge || !optTimestampMillis || !optTemp) {
        return std::nullopt;
    }
    token.mac = std::move(optTemp.value());
    token.challenge = static_cast<long>(std::move(optChallenge.value()));
    token.timestamp.milliSeconds = static_cast<long>(std::move(optTimestampMillis.value()));
    return token;
}

std::optional<Array> CborConverter::getArrayItem(const std::unique_ptr<Item>& item,
                                                 const uint32_t pos) {
    Array array;
    auto arrayItem = getItemAtPos(item, pos);
    if (!arrayItem || (MajorType::ARRAY != getType(arrayItem.value()))) {
        return std::nullopt;
    }
    array = std::move(*(arrayItem.value().get()->asArray()));
    return array;
}

std::optional<Map> CborConverter::getMapItem(const std::unique_ptr<Item>& item,
                                             const uint32_t pos) {
    Map map;
    auto mapItem = getItemAtPos(item, pos);
    if (!mapItem || (MajorType::MAP != getType(mapItem.value()))) {
        return std::nullopt;
    }
    map = std::move(*(mapItem.value().get()->asMap()));
    return map;
}

std::optional<vector<KeyParameter>> CborConverter::getKeyParameters(const unique_ptr<Item>& item,
                                                                    const uint32_t pos) {
    vector<KeyParameter> params;
    auto mapItem = getItemAtPos(item, pos);
    if (!mapItem || (MajorType::MAP != getType(mapItem.value()))) return std::nullopt;
    const Map* map = mapItem.value().get()->asMap();
    size_t mapSize = map->size();
    for (int i = 0; i < mapSize; i++) {
        auto optKeyParams = getKeyParameter((*map)[i]);
        if (optKeyParams) {
            params.insert(params.end(), optKeyParams->begin(), optKeyParams->end());
        } else {
            return std::nullopt;
        }
    }
    return params;
}

std::tuple<std::unique_ptr<Item>, keymaster_error_t>
CborConverter::decodeData(const std::vector<uint8_t>& response) {
    auto [item, pos, message] = cppbor::parse(response);
    if (!item || MajorType::ARRAY != getType(item)) {
        return {nullptr, KM_ERROR_UNKNOWN_ERROR};
    }
    auto optErrorCode = getErrorCode(item, 0);
    if (!optErrorCode) {
        return {nullptr, KM_ERROR_UNKNOWN_ERROR};
    }
    return {std::move(item), optErrorCode.value()};
}

std::optional<keymaster_error_t>
CborConverter::getErrorCode(const std::unique_ptr<cppbor::Item>& item, const uint32_t pos) {
    auto optErrorVal = getUint64(item, pos);
    if (!optErrorVal) {
        return std::nullopt;
    }
    return static_cast<keymaster_error_t>(0 - optErrorVal.value());
}

std::optional<uint64_t> CborConverter::getUint64(const unique_ptr<Item>& item) {
    if ((item == nullptr) || (MajorType::UINT != getType(item))) {
        return std::nullopt;
    }
    const Uint* uintVal = item.get()->asUint();
    return uintVal->unsignedValue();
}

std::optional<uint64_t> CborConverter::getUint64(const unique_ptr<Item>& item, const uint32_t pos) {
    auto intItem = getItemAtPos(item, pos);
    if (!intItem) {
        return std::nullopt;
    }
    return getUint64(intItem.value());
}

}  // namespace keymint::javacard
