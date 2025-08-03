/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define TLOG_TAG "hwrng_unittest"

#include <math.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <lib/rng/trusty_rng.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

static uint32_t _hist[256];
static uint8_t _rng_buf[1024];

static void hwrng_update_hist(uint8_t* data, unsigned int cnt) {
    for (unsigned int i = 0; i < cnt; i++) {
        _hist[data[i]]++;
    }
}

static void hwrng_show_data(const void* ptr, size_t len) {
    uintptr_t address = (uintptr_t)ptr;
    size_t count;
    size_t i;
    fprintf(stderr, "Dumping first hwrng request:\n");
    for (count = 0; count < len; count += 16) {
        for (i = 0; i < MIN(len - count, 16); i++) {
            fprintf(stderr, "0x%02hhx ", *(const uint8_t*)(address + i));
        }
        fprintf(stderr, "\n");
        address += 16;
    }
}

TEST(hwrng, show_data_test) {
    int rc;
    rc = trusty_rng_hw_rand(_rng_buf, 32);
    EXPECT_EQ(NO_ERROR, rc, "hwrng test");
    if (rc == NO_ERROR) {
        hwrng_show_data(_rng_buf, 32);
    }
}

TEST(hwrng, var_rng_req_test) {
    int rc;
    unsigned int i;
    size_t req_cnt;
    /* Issue 100 hwrng requests of variable sizes */
    for (i = 0; i < 100; i++) {
        req_cnt = ((size_t)rand() % sizeof(_rng_buf)) + 1;
        rc = trusty_rng_hw_rand(_rng_buf, req_cnt);
        EXPECT_EQ(NO_ERROR, rc, "hwrng test");
        if (rc != NO_ERROR) {
            TLOGI("trusty_rng_hw_rand returned %d\n", rc);
            continue;
        }
    }
}

/*
 * This Test is NOT intended as a replacement for a proper NIST SP 800-22
 * certification suites. It only attempts to detect detect gross misbehaviors of
 * rng early on, at low computational costs while not being flaky. It is
 * adapted from Section 2.13
 */

#define NB_RNG_QUERIES 1000
#define LEN_BYTES_RNG_QUERIES 1024
#define CUM_SUM_ERR_MSG_SZ 256
/*
 * Standard Normal Cumulative Probability Distribution Function as defined in
 * Section 5.5.3
 */
static double phi(double z) {
    return 0.5 * (1.0 + erf(z / sqrt(2.0)));
}

/*
 * This is the actual NIST Test as defined in NIST 800-22 Section 2.13. It
 * will return NO_ERROR if and only if it succeeds, otherwise an error code.
 */
static int cumulative_sums_forward_test_helper(void) {
    /* n is as defined in NIST 800-22 Section 2.13.7 recommends >=100 */
    const int64_t n = (NB_RNG_QUERIES * LEN_BYTES_RNG_QUERIES * 8);
    _Static_assert(
            n >= 100,
            "n as defined in NIST 800-22 Section 2.13.7 recommends >=100");

    int rc;
    int S = 0;
    size_t i, j, k;

    /* largest absolute value of the partial sums */
    int64_t z = 0;

    for (i = 0; i < NB_RNG_QUERIES; i++) {
        rc = trusty_rng_hw_rand(_rng_buf, LEN_BYTES_RNG_QUERIES);
        if (rc != NO_ERROR) {
            TLOGE("trusty_rng_hw_rand returned %d", rc);
            return rc;
        }
        for (j = 0; j < LEN_BYTES_RNG_QUERIES; j++) {
            for (k = 0; k < 8; k++) {
                S += (_rng_buf[j] >> k) & 1 ? 1 : -1;
                z = MAX(abs(S), z);
            }
        }
    }

    int64_t start = ((-(double)n / (double)z) + 1.0) / 4.0;
    int64_t end = (((double)n / (double)z) + 1.0) / 4.0;
    int64_t start2 = ((-(double)n / (double)z) - 3.0) / 4.0;
    double p = 1.0;
    for (int64_t k = start; k <= end; k++) {
        p -= phi(((4.0 * k + 1.0) * z) / sqrt(n));
        p += phi(((4.0 * k - 1.0) * z) / sqrt(n));
    }
    for (int64_t k = start2; k <= end; k++) {
        p += phi(((4.0 * k + 3.0) * z) / sqrt(n));
        p -= phi(((4.0 * k + 1.0) * z) / sqrt(n));
    }

    if (p <= 0.01) {
        trusty_unittest_printf(
                "[   WARN   ] NIST 800-22 - Section 2.13.5 Decision Rule (at the 1 Percent Level)\n");
        return ERR_GENERIC;
    }

    return NO_ERROR;
}

/*
 * To avoid flakiness on real devices the actual NIST test is attempted 3 times.
 * The helper which implements the Test as-is has ~1% failure rate.
 */
TEST(hwrng, cumulative_sums_forward_test) {
    int ret = NO_ERROR;
    int counter = 3;
    do {
        ret = cumulative_sums_forward_test_helper();
    } while (ret && counter--);

    EXPECT_EQ(
            ret, NO_ERROR,
            "NIST 800-22 - Section 2.13.5 criteria not met after 3 attempts.");
}

TEST(hwrng, stats_test) {
    int rc;
    unsigned int i;
    size_t req_cnt;
    uint32_t exp_cnt;
    uint32_t cnt = 0;
    uint32_t ave = 0;
    uint32_t dev = 0;
    /* issue 100x256 bytes requests */
    req_cnt = 256;
    exp_cnt = 1000 * req_cnt;
    memset(_hist, 0, sizeof(_hist));
    for (i = 0; i < 1000; i++) {
        rc = trusty_rng_hw_rand(_rng_buf, req_cnt);
        EXPECT_EQ(NO_ERROR, rc, "hwrng test");
        if (rc != NO_ERROR) {
            TLOGI("trusty_rng_hw_rand returned %d\n", rc);
            continue;
        }
        hwrng_update_hist(_rng_buf, req_cnt);
    }

    /* check hwrng stats */
    for (i = 0; i < 256; i++)
        cnt += _hist[i];
    ave = cnt / 256;
    EXPECT_EQ(exp_cnt, cnt, "hwrng ttl sample cnt");
    EXPECT_EQ(1000, ave, "hwrng eve sample cnt");

    /*
     * Ideally data should be uniformly distributed
     * Calculate average deviation from ideal model
     */
    for (i = 0; i < 256; i++) {
        uint32_t val = (_hist[i] > ave) ? _hist[i] - ave : ave - _hist[i];
        dev += val;
    }
    dev /= 256;
    /*
     * Check if average deviation is within 5% of ideal model
     * which is fairly arbitrary requirement. It could be useful
     * to alert is something terribly wrong with rng source.
     */
    EXPECT_GT(50, dev, "average dev");
}

PORT_TEST(hwrng, "com.android.trusty.hwrng.test")
