/*
 *
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "include/KeyMintAidlUtils.h"

namespace aidl {
namespace android {
namespace hardware {
namespace keymint {

using namespace ::keymaster;

// TODO(seleneh): This needs to be modified depends on how aidl support for union came out to
// be.
vector<KeyParameter> kmParamSet2Aidl(const keymaster_key_param_set_t& set) {
    vector<KeyParameter> result;
    if (set.length == 0 || set.params == nullptr) return result;

    result.resize(set.length);
    keymaster_key_param_t* params = set.params;
    for (size_t i = 0; i < set.length; ++i) {
        auto tag = params[i].tag;
        result[i].tag = legacy_enum_conversion(tag);
        switch (typeFromTag(tag)) {
        case KM_ENUM:
        case KM_ENUM_REP:
            result[i].integer = params[i].enumerated;
            break;
        case KM_UINT:
        case KM_UINT_REP:
            result[i].integer = params[i].integer;
            break;
        case KM_ULONG:
        case KM_ULONG_REP:
            result[i].longInteger = params[i].long_integer;
            break;
        case KM_DATE:
            result[i].longInteger = params[i].date_time;
            break;
        case KM_BOOL:
            result[i].boolValue = params[i].boolean;
            break;
        case KM_BIGNUM:
        case KM_BYTES:
            result[i].blob.assign(params[i].blob.data,
                                  params[i].blob.data + params[i].blob.data_length);
            break;
        case KM_INVALID:
        default:
            params[i].tag = KM_TAG_INVALID;
            /* just skip */
            break;
        }
    }
    return result;
}

// TODO(seleneh): This needs to be modified depends on how aidl support for union came out to
// be.
keymaster_key_param_set_t aidlKeyParams2Km(const vector<KeyParameter>& keyParams) {
    keymaster_key_param_set_t set;

    set.params = new (std::nothrow) keymaster_key_param_t[keyParams.size()];
    set.length = keyParams.size();

    for (size_t i = 0; i < keyParams.size(); ++i) {
        auto tag = legacy_enum_conversion(keyParams[i].tag);
        switch (typeFromTag(tag)) {
        case KM_ENUM:
        case KM_ENUM_REP:
            set.params[i] = keymaster_param_enum(tag, keyParams[i].integer);
            break;
        case KM_UINT:
        case KM_UINT_REP:
            set.params[i] = keymaster_param_int(tag, keyParams[i].integer);
            break;
        case KM_ULONG:
        case KM_ULONG_REP:
            set.params[i] = keymaster_param_long(tag, keyParams[i].longInteger);
            break;
        case KM_DATE:
            set.params[i] = keymaster_param_date(tag, keyParams[i].longInteger);
            break;
        case KM_BOOL:
            if (keyParams[i].boolValue)
                set.params[i] = keymaster_param_bool(tag);
            else
                set.params[i].tag = KM_TAG_INVALID;
            break;
        case KM_BIGNUM:
        case KM_BYTES:
            set.params[i] =
                keymaster_param_blob(tag, keyParams[i].blob.data(), keyParams[i].blob.size());
            break;
        case KM_INVALID:
        default:
            set.params[i].tag = KM_TAG_INVALID;
            /* just skip */
            break;
        }
    }

    return set;
}

}  // namespace keymint
}  // namespace hardware
}  // namespace android
}  // namespace aidl
