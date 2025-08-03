/*
 * Copyright 2022 The Android Open Source Project
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

#include "second_imei_attestation.h"

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

namespace keymaster {

// Calculates the checksum digit of a given number according to the
// Luhn algorithm.
// The algorithm:
// * Starting from the rightmost digit, moving left: Double the value of every
//   second digit.
// * Sum the digits of the resulting value in each position (if the value
//   was not multiplied, use it as-is) as s.
// * Caclculate the sum digit to be (10 - (s % 10)) % 10.
uint8_t calculate_luhn_checksum_digit(uint64_t val) {
    int iteration_counter = 0;
    uint32_t sum_digits = 0;
    while (val != 0) {
        int curr_digit = val % 10;
        int multiplier = (iteration_counter % 2) == 0 ? 2 : 1;
        int digit_multiplied = curr_digit * multiplier;
        sum_digits += (digit_multiplied % 10) + (digit_multiplied / 10);
        val = val / 10;
        iteration_counter++;
    }

    return (10 - (sum_digits % 10)) % 10;
}

// Validate that the second IMEI sent by the platform is the one following
// the first IMEI, and that the checksum digit matches.
// On most devices with two IMEIs, the IMEIs are sequential. This enables
// providing attestation for the 2nd IMEI even if KeyMint was not provisioned
// with it.
bool validate_second_imei(const keymaster_blob_t& received_second_imei,
                          uint64_t first_imei) {
    // The first IMEI includes the checksum digit, so get rid of it and increase
    // by 1 to get the value of the 2nd IMEI.
    const uint64_t second_imei_no_checksum = (first_imei / 10) + 1;
    const uint8_t checksum_digit =
            calculate_luhn_checksum_digit(second_imei_no_checksum);
    const uint64_t second_imei = second_imei_no_checksum * 10 + checksum_digit;
    // Compare the second IMEI with the caller-provided value.
    char calculated_second_imei[64];
    const size_t calculated_second_imei_len =
            snprintf(calculated_second_imei, sizeof(calculated_second_imei),
                     "%" PRIu64, second_imei);
    bool result =
            (calculated_second_imei_len == received_second_imei.data_length) &&
            (memcmp(calculated_second_imei, received_second_imei.data,
                    calculated_second_imei_len) == 0);
    return result;
}

}  // namespace keymaster
