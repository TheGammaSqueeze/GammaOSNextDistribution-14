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

#include <apploader/cbor.h>
#include <cstdint>
#include <limits>
#include <optional>
#include <string_view>
#include <vector>

#include "dice/cbor_reader.h"
#include "dice/cbor_writer.h"
#include "gtest/gtest.h"

#include "cppbor.h"
#include "cppbor_parse.h"

TEST(CborTest, ReadCborBoolean) {
    uint8_t buffer[8];
    struct CborOut out;
    CborOutInit(buffer, sizeof(buffer), &out);
    CborWriteTrue(&out);
    CborWriteFalse(&out);
    CborWriteNull(&out);
    ASSERT_FALSE(CborOutOverflowed(&out));

    struct CborIn in;
    CborInInit(buffer, CborOutSize(&out), &in);
    std::optional<bool> res;

    res = cbor::readCborBoolean(in);
    ASSERT_TRUE(res.has_value());
    EXPECT_TRUE(res.value());

    res = cbor::readCborBoolean(in);
    ASSERT_TRUE(res.has_value());
    EXPECT_FALSE(res.value());

    res = cbor::readCborBoolean(in);
    EXPECT_FALSE(res.has_value());
}

TEST(CborTest, EncodedSizeOf) {
    std::vector<uint64_t> cases = {0,
                                   1,
                                   23,
                                   24,
                                   255,
                                   256,
                                   65535,
                                   65536,
                                   4294967295,
                                   4294967296,
                                   std::numeric_limits<uint64_t>::max()};

    for (uint64_t val : cases) {
        EXPECT_EQ(cppbor::headerSize(val), cbor::encodedSizeOf(val));
    }
}

static std::vector<int64_t> int64_cases = {std::numeric_limits<int64_t>::min(),
                                           -4294967297,
                                           -4294967296,
                                           -65537,
                                           -65536,
                                           -257,
                                           -256,
                                           -25,
                                           -24,
                                           -1,
                                           0,
                                           1,
                                           23,
                                           24,
                                           255,
                                           256,
                                           65535,
                                           65536,
                                           4294967295,
                                           4294967296,
                                           std::numeric_limits<int64_t>::max()};

TEST(CborTest, EncodedSizeOfInt) {
    for (int64_t val : int64_cases) {
        size_t expected = val < 0 ? cppbor::headerSize(-1ll - val)
                                  : cppbor::headerSize(val);
        EXPECT_EQ(expected, cbor::encodedSizeOfInt(val));
    }
}

TEST(CborTest, EncodeBstrHeader) {
    uint8_t cborBuf[16], cppborBuf[16];
    std::vector<uint64_t> cases = {
            0, 1, 23, 24, 255, 256, 65535, 65536, 4294967295, 4294967296,
    };
    for (uint64_t payloadSize : cases) {
        uint8_t* cborHeaderEnd =
                cbor::encodeBstrHeader(payloadSize, sizeof(cborBuf), cborBuf);
        uint8_t* cppborHeaderEnd =
                cppbor::encodeHeader(cppbor::BSTR, payloadSize, cppborBuf,
                                     cppborBuf + sizeof(cppborBuf));

        ASSERT_NE(cborHeaderEnd, nullptr);
        const ptrdiff_t cborOutputLen = cborHeaderEnd - cborBuf;
        ASSERT_LT(cborOutputLen, (ptrdiff_t)sizeof(cborBuf));

        ASSERT_NE(cppborHeaderEnd, (uint8_t*)NULL);
        const ptrdiff_t cppborOutputLen = cppborHeaderEnd - cppborBuf;
        ASSERT_LT(cppborOutputLen, (ptrdiff_t)sizeof(cppborBuf));

        ASSERT_EQ(std::basic_string_view<uint8_t>(cborBuf, cborOutputLen),
                  std::basic_string_view<uint8_t>(cppborBuf, cppborOutputLen));
    }

    // Pass in a string that's longer than we could possibly handle
    auto res = cbor::encodeBstrHeader(std::numeric_limits<uint64_t>::max(),
                                      sizeof(cborBuf), cborBuf);
    EXPECT_EQ(res, nullptr);
}

TEST(CborTest, MergeMapsEmpty) {
    cppbor::Map empty;
    std::vector<uint8_t> emptyEncoded = empty.encode();
    std::basic_string_view<uint8_t> emptyView = {emptyEncoded.data(),
                                                 emptyEncoded.size()};
    auto res = cbor::mergeMaps(emptyView, emptyView);
    ASSERT_TRUE(res.has_value());

    // parse the bytes we got to ensure it is an empty map
    auto [item, _, err] = cppbor::parse(res.value());
    ASSERT_NE(item, nullptr);
    auto resMap = item->asMap();
    ASSERT_NE(resMap, nullptr);
    EXPECT_EQ(resMap->size(), 0lu);
}

TEST(CborTest, MergeMapsCanonical) {
    cppbor::Map first, second, expected;

    for (auto k = int64_cases.rbegin(); k != int64_cases.rend(); k++) {
        int64_t key = *k;
        if (key & 1) {
            first.add(key, key);
        } else {
            second.add(key, key);
        }
        expected.add(key, key);
    }

    expected.canonicalize();

    auto firstEncoded = first.encode();
    std::basic_string_view<uint8_t> firstView = {firstEncoded.data(),
                                                 firstEncoded.size()};
    auto secondEncoded = second.encode();
    std::basic_string_view<uint8_t> secondView = {secondEncoded.data(),
                                                  secondEncoded.size()};

    auto merged = cbor::mergeMaps(firstView, secondView);
    ASSERT_TRUE(merged.has_value());

    EXPECT_TRUE(expected.isCanonical());
    auto expectedEncoded = expected.encode();
    ASSERT_TRUE(merged.value() == expectedEncoded);
}

TEST(CborTest, MergeMapsCanonicalNoncanonicalInput) {
    cppbor::Map first, second, expected;
    /*
     * -1 comes before 1000 in CBOR order so this map will be non-canonical
     * because cppbor stores map items in the order they were added and we do
     * not make it canonical before encoding.
     */
    first.add(1000, 1000);
    first.add(-1, -1);
    /* check that cppbor::Map preserves insertion order */
    ASSERT_EQ(first[0].first->asInt()->value(), 1000);
    ASSERT_EQ(first[1].first->asInt()->value(), -1);

    const std::vector<uint8_t> firstEncoded = first.encode();
    std::basic_string_view<uint8_t> firstView = {firstEncoded.data(),
                                                 firstEncoded.size()};

    second.add(1, 1);
    second.add(42, 42);
    const std::vector<uint8_t> secondEncoded = second.encode();
    std::basic_string_view<uint8_t> secondView = {secondEncoded.data(),
                                                  secondEncoded.size()};

    auto merged = cbor::mergeMaps(firstView, secondView);
    ASSERT_TRUE(merged.has_value());

    for (int64_t key : {-1, 1, 42, 1000}) {
        expected.add(key, key);
    }
    expected.canonicalize();

    EXPECT_TRUE(expected.isCanonical());
    auto expectedEncoded = expected.encode();
    ASSERT_TRUE(merged.value() == expectedEncoded);
}

TEST(CborTest, MapEncodingIsCanonical) {
    cbor::VectorCborEncoder canonical;
    canonical.encodeMap([&](auto& enc) {
        for (int64_t key : {-1, 1000}) {
            enc.encodeKeyValue(key, key);
        }
    });
    EXPECT_EQ(canonical.state(), cbor::VectorCborEncoder::State::kEncoding);

    cbor::VectorCborEncoder innerCanonical;
    innerCanonical.encodeMap([&](auto& enc) {
        for (int64_t key : {-1, 42}) {
            enc.encodeKeyValue(key, [&](auto& kvenc) {
                kvenc.encodeMap([&](auto& innerenc) {
                    for (int64_t key : {1, 1000}) {
                        innerenc.encodeKeyValue(key, key);
                    }
                });
            });
        }
    });
    EXPECT_EQ(innerCanonical.state(),
              cbor::VectorCborEncoder::State::kEncoding);

    cbor::VectorCborEncoder nonCanonical;
    nonCanonical.encodeMap([&](auto& enc) {
        /* -1 comes before 1000 in CBOR order so map will be non-canonical */
        for (int64_t key : {1000, -1}) {
            enc.encodeKeyValue(key, key);
        }
    });

    EXPECT_EQ(nonCanonical.state(), cbor::VectorCborEncoder::State::kInvalid);

    cbor::VectorCborEncoder innerNonCanonical;
    innerNonCanonical.encodeMap([&](auto& enc) {
        for (int64_t key : {-1, 1000}) {
            enc.encodeKeyValue(key, [&](auto& kvenc) {
                kvenc.encodeMap([&](auto& innerenc) {
                    /* write inner map keys in non-canonical order */
                    for (int64_t key : {1000, -1}) {
                        innerenc.encodeKeyValue(key, key);
                    }
                });
            });
        }
    });

    EXPECT_EQ(innerNonCanonical.state(),
              cbor::VectorCborEncoder::State::kInvalid);
}

TEST(CborTest, EncodeKeyValue) {
    cbor::VectorCborEncoder enc;
    enc.encodeArray([&](auto& enc) {
        /*
         * calling encodeKeyValue outside of an encodeMap operation is an error
         */
        enc.encodeKeyValue(1, 1);
    });
    EXPECT_EQ(enc.state(), cbor::VectorCborEncoder::State::kInvalid);
}

TEST(CborTest, MustCallEncodeArrayTagOrMap) {
    const char* err =
            "Call encodeArray, encodeTag, or encodeMap before this method";

    EXPECT_DEBUG_DEATH(
            {
                cbor::VectorCborEncoder bstrEnc;
                const std::basic_string_view<uint8_t> empty;
                bstrEnc.encodeBstr(empty);
            },
            err);

    EXPECT_DEBUG_DEATH(
            {
                cbor::VectorCborEncoder emptyBstrEnc;
                emptyBstrEnc.encodeEmptyBstr();
            },
            err);

    EXPECT_DEBUG_DEATH(
            {
                const char* testStr = "Carsten Bormann";
                cbor::VectorCborEncoder tstrEnc;
                tstrEnc.encodeBstr(testStr);
            },
            err);

    EXPECT_DEBUG_DEATH(
            {
                cbor::VectorCborEncoder intEnc;
                intEnc.encodeInt(42);
            },
            err);

    EXPECT_DEBUG_DEATH(
            {
                cbor::VectorCborEncoder uintEnc;
                uintEnc.encodeUint(42u);
            },
            err);
}

TEST(CborTest, EncodeArrayOfTstr) {
    const char* testStr = "Carsten Bormann";
    const size_t testStrlen = strlen(testStr);

    cbor::VectorCborEncoder enc;
    enc.encodeArray([&](auto& enc) { enc.encodeTstr(testStr); });

    auto res = enc.view();
    const char* decodedStr;
    size_t arrLen, decodedStrLen;
    struct CborIn in;
    enum CborReadResult rr;
    CborInInit(res.data(), res.size(), &in);

    rr = CborReadArray(&in, &arrLen);
    ASSERT_EQ(rr, CBOR_READ_RESULT_OK);
    ASSERT_EQ(arrLen, 1u);

    CborReadTstr(&in, &decodedStrLen, &decodedStr);
    ASSERT_EQ(rr, CBOR_READ_RESULT_OK);
    ASSERT_EQ(CborInAtEnd(&in), true);

    ASSERT_EQ(testStrlen, decodedStrLen);
    /* Use memcmp instead of strcmp since decodedStr isn't null terminated */
    ASSERT_EQ(0, memcmp(testStr, decodedStr, decodedStrLen));
}

TEST(CborTest, ViewsAndVectors) {
    cbor::VectorCborEncoder initialEnc;
    auto initialView = initialEnc.view();
    EXPECT_EQ(initialView.size(), 0u);
    auto initialVec = initialEnc.intoVec();
    EXPECT_EQ(initialVec.size(), 0u);
    EXPECT_DEATH({ initialEnc.intoVec(); }, "buffer was moved out of encoder");
    EXPECT_DEATH({ initialEnc.view(); },
                 "requested view of buffer from encoder in invalid state");

    cbor::VectorCborEncoder enc;
    enc.encodeArray([&](auto& enc) { enc.encodeEmptyBstr(); });
    auto view = enc.view();
    EXPECT_EQ(view.size(), 2u);
    auto vec = enc.intoVec();
    EXPECT_EQ(vec.size(), 2u);
    EXPECT_DEATH({ enc.intoVec(); }, "buffer was moved out of encoder");
    EXPECT_DEATH({ enc.view(); },
                 "requested view of buffer from encoder in invalid state");
}

#if __clang_major__ < 15
TEST(CborTest, EncodeArrayOfFakeBstrOverflows) {
#else
// Newer clang compilers, e.g. clang-r458507 15.0.1, have ASAN that
// can detect stack overview in enc.encodeBstr(fake).
// So this test will fail at that point with newer compilers.
TEST(CborTest, EncodeArrayOfFakeBstrOverflows) __attribute__((no_sanitize("address"))) {
#endif
    cbor::VectorCborEncoder enc;
    const std::basic_string_view<uint8_t> fake = {
            nullptr, std::numeric_limits<size_t>::max()};
    enc.encodeArray([&](auto& enc) { enc.encodeBstr(fake); });
    EXPECT_EQ(enc.state(), cbor::VectorCborEncoder::State::kOverflowed);
    EXPECT_DEATH({ enc.size(); }, "requested encoding size after overflow");
    EXPECT_DEATH({ enc.intoVec(); },
                 "buffer was too small to hold cbor encoded content");
    EXPECT_DEATH({ enc.view(); },
                 "requested view of buffer from encoder in invalid state");
}

TEST(CborTest, CopyBytes) {
    const uint64_t ans = 42;
    cbor::VectorCborEncoder innerEncoder, outerEncoder;
    innerEncoder.encodeArray([&](auto& enc) { enc.encodeUint(ans); });
    const auto view = innerEncoder.view();

    outerEncoder.encodeArray([&](auto& enc) { enc.copyBytes(view); });

    const auto vec = outerEncoder.intoVec();
    auto [item, _, err] = cppbor::parse(vec);
    ASSERT_NE(item, nullptr);
    const auto outerArray = item->asArray();
    ASSERT_NE(outerArray, nullptr);
    ASSERT_EQ(outerArray->size(), 1u);
    const auto innerArray = outerArray->get(0)->asArray();
    ASSERT_NE(innerArray, nullptr);
    ASSERT_EQ(innerArray->size(), 1u);
    const auto innerInt = innerArray->get(0)->asUint();
    ASSERT_NE(innerInt, nullptr);
    ASSERT_EQ(innerInt->unsignedValue(), ans);
}
