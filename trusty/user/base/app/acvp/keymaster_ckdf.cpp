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

#define TLOG_TAG "acvp"

// NOTE: modulewrapper.h is not guarded against double inclusion and
// keymaster_ckdf.h uses it, so we need to include it before keymaster_ckdf.h
#include "modulewrapper.h"

#include "acvp.h"
#include "keymaster_ckdf.h"

#include <openssl/span.h>
#include <string.h>
#include <trusty_log.h>
#include "keymaster/android_keymaster_utils.h"
#include "keymaster/km_openssl/ckdf.h"
#include "openssl/rand.h"

constexpr const char* kSharedHmacLabel = "KeymasterSharedMac";
constexpr size_t kContextLength = 32;

bool KeymasterCKDF(const bssl::Span<const uint8_t> args[],
                   bssl::acvp::ReplyCallback write_reply) {
    const bssl::Span<const uint8_t> out_len_bytes = args[0];
    const bssl::Span<const uint8_t> prf = args[1];
    const bssl::Span<const uint8_t> counter_location = args[2];
    const bssl::Span<const uint8_t> key_input = args[3];
    const bssl::Span<const uint8_t> counter_len_bits = args[4];

    if (!StringEq(prf, "CMAC-AES128") && !StringEq(prf, "CMAC-AES256")) {
        TLOGE("bad prf\n");
        return false;
    }
    if (!StringEq(counter_location, "before fixed data")) {
        TLOGE("bad counter_location\n");
        return false;
    }

    uint32_t out_len;
    if (out_len_bytes.size() != sizeof(out_len)) {
        TLOGE("bad out_len_bytes size\n");
        return false;
    }
    memcpy(&out_len, out_len_bytes.data(), sizeof(out_len));

    uint32_t counter_len;
    if (counter_len_bits.size() != sizeof(counter_len)) {
        TLOGE("bad counter_len_bits size\n");
        return false;
    }
    memcpy(&counter_len, counter_len_bits.data(), sizeof(counter_len));
    if (counter_len != 32) {
        TLOGE("bad counter_len_bits\n");
        return false;
    }

    keymaster::KeymasterKeyBlob key(key_input.data(), key_input.size());
    keymaster::KeymasterKeyBlob output(out_len);

    keymaster::KeymasterBlob label(
            reinterpret_cast<const uint8_t*>(kSharedHmacLabel),
            strlen(kSharedHmacLabel));

    uint8_t context_buf[kContextLength];
    auto ret = RAND_bytes(context_buf, sizeof(context_buf));
    if (ret != 1) {
        return false;
    }
    keymaster_blob_t context = {
            context_buf,
            sizeof(context_buf),
    };

    auto ckdf_ret = ckdf(key, label, &context, 1, &output);
    if (ckdf_ret != KM_ERROR_OK) {
        TLOGE("ckdf returned code: %u\n", ckdf_ret);
        return false;
    }

    const uint32_t L = out_len * 8;  // bits
    const uint32_t net_order_L = keymaster::hton(L);

    size_t fixed_data_size = label.data_length + 1 + context.data_length + 4;
    std::unique_ptr<uint8_t[]> fixed_data(new (std::nothrow)
                                                  uint8_t[fixed_data_size]);

    memcpy(fixed_data.get(), label.data, label.data_length);
    fixed_data[label.data_length] = 0x00;
    memcpy(&fixed_data[label.data_length + 1], context.data,
           context.data_length);
    memcpy(&fixed_data[label.data_length + 1 + context.data_length],
           &net_order_L, 4);

    return write_reply({
            bssl::Span<const uint8_t>(key.key_material, key.key_material_size),
            bssl::Span<const uint8_t>(fixed_data.get(), fixed_data_size),
            bssl::Span<const uint8_t>(output.key_material,
                                      output.key_material_size),
    });
}
