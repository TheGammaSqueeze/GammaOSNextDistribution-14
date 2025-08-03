/*
 * Copyright 2014 The Android Open Source Project
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

#include "android_keymaster_test_utils.h"

#include <algorithm>

#include <openssl/rand.h>

#include <keymaster/android_keymaster_messages.h>
#include <keymaster/android_keymaster_utils.h>

using std::copy_if;
using std::find_if;
using std::is_permutation;
using std::ostream;
using std::string;
using std::vector;

#ifndef KEYMASTER_NAME_TAGS
#error Keymaster test code requires that KEYMASTER_NAME_TAGS is defined
#endif

std::ostream& operator<<(std::ostream& os, const keymaster_key_param_t& param) {
    os << "Tag: " << keymaster::StringifyTag(param.tag);
    switch (keymaster_tag_get_type(param.tag)) {
    case KM_INVALID:
        os << " Invalid";
        break;
    case KM_UINT_REP:
        os << " (Rep)";
        /* Falls through */
        [[fallthrough]];
    case KM_UINT:
        os << " Int: " << param.integer;
        break;
    case KM_ENUM_REP:
        os << " (Rep)";
        /* Falls through */
        [[fallthrough]];
    case KM_ENUM:
        os << " Enum: " << param.enumerated;
        break;
    case KM_ULONG_REP:
        os << " (Rep)";
        /* Falls through */
        [[fallthrough]];
    case KM_ULONG:
        os << " Long: " << param.long_integer;
        break;
    case KM_DATE:
        os << " Date: " << param.date_time;
        break;
    case KM_BOOL:
        os << " Bool: " << param.boolean;
        break;
    case KM_BIGNUM:
        os << " Bignum: ";
        if (!param.blob.data)
            os << "(null)";
        else
            for (size_t i = 0; i < param.blob.data_length; ++i)
                os << std::hex << std::setw(2) << static_cast<int>(param.blob.data[i]) << std::dec;
        break;
    case KM_BYTES:
        os << " Bytes: ";
        if (!param.blob.data)
            os << "(null)";
        else
            for (size_t i = 0; i < param.blob.data_length; ++i)
                os << std::hex << std::setw(2) << static_cast<int>(param.blob.data[i]) << std::dec;
        break;
    }
    return os;
}

bool operator==(const keymaster_key_param_t& a, const keymaster_key_param_t& b) {
    if (a.tag != b.tag) {
        return false;
    }

    switch (keymaster_tag_get_type(a.tag)) {
    case KM_INVALID:
        return true;
    case KM_UINT_REP:
    case KM_UINT:
        return a.integer == b.integer;
    case KM_ENUM_REP:
    case KM_ENUM:
        return a.enumerated == b.enumerated;
    case KM_ULONG:
    case KM_ULONG_REP:
        return a.long_integer == b.long_integer;
    case KM_DATE:
        return a.date_time == b.date_time;
    case KM_BOOL:
        return a.boolean == b.boolean;
    case KM_BIGNUM:
    case KM_BYTES:
        if ((a.blob.data == nullptr || b.blob.data == nullptr) && a.blob.data != b.blob.data)
            return false;
        return a.blob.data_length == b.blob.data_length &&
               (memcmp(a.blob.data, b.blob.data, a.blob.data_length) == 0);
    }

    return false;
}

static char hex_value[256] = {
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  0,  0,  0,  0,  0,  0,
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  0,  0,  0,  0,  0,  0,
    0, 1,  2,  3,  4,  5,  6,  7, 8, 9, 0, 0, 0, 0, 0, 0,  // '0'..'9'
    0, 10, 11, 12, 13, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // 'A'..'F'
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 11, 12, 13, 14, 15, 0,
    0, 0,  0,  0,  0,  0,  0,  0,  // 'a'..'f'
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  0,  0,  0,  0,  0,  0,
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  0,  0,  0,  0,  0,  0,
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  0,  0,  0,  0,  0,  0,
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  0,  0,  0,  0,  0,  0,
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  0,  0,  0,  0,  0,  0,
    0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  0,  0,  0,  0,  0,  0};

string hex2str(string a) {
    string b;
    size_t num = a.size() / 2;
    b.resize(num);
    for (size_t i = 0; i < num; i++) {
        b[i] = (hex_value[a[i * 2] & 0xFF] << 4) + (hex_value[a[i * 2 + 1] & 0xFF]);
    }
    return b;
}

namespace keymaster {

bool operator==(const AuthorizationSet& a, const AuthorizationSet& b) {
    if (a.size() != b.size()) return false;

    for (size_t i = 0; i < a.size(); ++i)
        if (!(a[i] == b[i])) return false;
    return true;
}

bool operator!=(const AuthorizationSet& a, const AuthorizationSet& b) {
    return !(a == b);
}

std::ostream& operator<<(std::ostream& os, const AuthorizationSet& set) {
    if (set.size() == 0)
        os << "(Empty)" << std::endl;
    else {
        os << "\n";
        for (size_t i = 0; i < set.size(); ++i)
            os << set[i] << std::endl;
    }
    return os;
}

}  // namespace keymaster
