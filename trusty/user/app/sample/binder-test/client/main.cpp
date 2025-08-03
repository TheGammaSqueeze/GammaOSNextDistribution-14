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

#define TLOG_TAG "binder-test-client"

#include <binder/RpcSession.h>
#include <binder/RpcTransportTipcTrusty.h>
#include <lib/unittest/unittest.h>
#include <stdio.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#include <com/android/trusty/binder/test/ITestService.h>

using ::android::String16;
using ::android::binder::Status;

using com::android::trusty::binder::test::ByteEnum;
using com::android::trusty::binder::test::IntEnum;
using com::android::trusty::binder::test::ITestService;
using com::android::trusty::binder::test::LongEnum;

typedef struct {
    android::sp<android::RpcSession> sess;
    android::sp<ITestService> srv;
} BinderTest_t;

static void BinderTest_SetUp(BinderTest_t* state) {
    int rc;
    android::base::unique_fd chan;
    android::status_t status;
    android::sp<android::IBinder> root;

    rc = connect(ITestService::PORT().c_str(), IPC_CONNECT_WAIT_FOR_PORT);
    ASSERT_GE(rc, 0);
    chan.reset(rc);

    state->sess = android::RpcSession::make(
            android::RpcTransportCtxFactoryTipcTrusty::make());
    ASSERT_NE(nullptr, state->sess.get());

    status = state->sess->setupPreconnectedClient(
            std::move(chan), []() { return android::base::unique_fd(); });
    ASSERT_EQ(android::OK, status);

    root = state->sess->getRootObject();
    ASSERT_NE(nullptr, root.get());

    state->srv = ITestService::asInterface(root);
    ASSERT_NE(nullptr, state->srv.get());

test_abort:;
}

static void BinderTest_TearDown(BinderTest_t* state) {
    state->srv.clear();
}

template <typename T, typename U, typename V>
static void CheckRepeat(const BinderTest_t* state,
                        Status (ITestService::*func)(T, U*),
                        V in) {
    U out;
    auto status = (*state->srv.*func)(in, &out);
    EXPECT_EQ(true, status.isOk());
    EXPECT_EQ(in, out);
}

static void CheckRepeat(const BinderTest_t* state, const String16& in) {
    String16 out;
    auto status = state->srv->RepeatString(in, &out);
    EXPECT_EQ(true, status.isOk());
    /*
        EXPECT_EQ expects the input types to be C-style
        castable to long; String16 is cannot
    */
    EXPECT_EQ(in == out, true);
}

template <typename T>
static void CheckReverse(const BinderTest_t* state,
                         Status (ITestService::*func)(const std::vector<T>&,
                                                      std::vector<T>*,
                                                      std::vector<T>*),
                         const std::vector<T>& input) {
    // must be preallocated for Java servers
    std::vector<T> repeated(input.size());
    std::vector<T> reversed(input.size());
    auto status = (*state->srv.*func)(input, &repeated, &reversed);
    EXPECT_EQ(status.isOk(), true);
    EXPECT_EQ(repeated == input, true);

    std::vector<T> reversed_input(input);
    std::reverse(reversed_input.begin(), reversed_input.end());
    EXPECT_EQ(reversed == reversed_input, true);
}

#define CHECK_REPEAT(func, in) \
    { CheckRepeat(_state, &ITestService::func, in); }

#define CHECK_REPEAT_STRING(in) \
    { CheckRepeat(_state, in); }

#define CHECK_REVERSE(func, in) \
    { CheckReverse(_state, &ITestService::func, in); }

TEST_F(BinderTest, aBoolean) {
    CHECK_REPEAT(RepeatBoolean, true);
}

TEST_F(BinderTest, aByte) {
    CHECK_REPEAT(RepeatByte, int8_t{-128});
}

TEST_F(BinderTest, aChar) {
    CHECK_REPEAT(RepeatChar, char16_t{'A'});
}

TEST_F(BinderTest, aInt) {
    CHECK_REPEAT(RepeatInt, int32_t{1 << 30});
}

TEST_F(BinderTest, aLong) {
    CHECK_REPEAT(RepeatLong, int64_t{1LL << 60});
}

TEST_F(BinderTest, aFloat) {
    CHECK_REPEAT(RepeatFloat, float{1.0f / 3.0f});
}

TEST_F(BinderTest, aDouble) {
    CHECK_REPEAT(RepeatDouble, double{1.0 / 3.0});
}

TEST_F(BinderTest, aByteEnum) {
    CHECK_REPEAT(RepeatByteEnum, ByteEnum::BAR);
}

TEST_F(BinderTest, aIntEnum) {
    CHECK_REPEAT(RepeatIntEnum, IntEnum::BAZ);
}

TEST_F(BinderTest, aLongEnum) {
    CHECK_REPEAT(RepeatLongEnum, LongEnum::FOO);
}

TEST_F(BinderTest, byteConstants) {
    constexpr int8_t consts[] = {ITestService::BYTE_TEST_CONSTANT};
    for (const auto& sent : consts) {
        CHECK_REPEAT(RepeatByte, sent);
    }
}

TEST_F(BinderTest, intConstants) {
    constexpr int32_t consts[] = {
            ITestService::TEST_CONSTANT,   ITestService::TEST_CONSTANT2,
            ITestService::TEST_CONSTANT3,  ITestService::TEST_CONSTANT4,
            ITestService::TEST_CONSTANT5,  ITestService::TEST_CONSTANT6,
            ITestService::TEST_CONSTANT7,  ITestService::TEST_CONSTANT8,
            ITestService::TEST_CONSTANT9,  ITestService::TEST_CONSTANT10,
            ITestService::TEST_CONSTANT11, ITestService::TEST_CONSTANT12};
    for (const auto& sent : consts) {
        CHECK_REPEAT(RepeatInt, sent);
    }
}

TEST_F(BinderTest, longConstants) {
    constexpr int64_t consts[] = {ITestService::LONG_TEST_CONSTANT};
    for (const auto& sent : consts) {
        CHECK_REPEAT(RepeatLong, sent);
    }
}

TEST_F(BinderTest, strings) {
    std::vector<String16> strings = {
            String16("Deliver us from evil."), String16(), String16("\0\0", 2),
            // This is actually two unicode code points:
            //   U+10437: The 'small letter yee' character in the deseret
            //   alphabet U+20AC: A euro sign
            String16("\xD8\x01\xDC\x37\x20\xAC"),
            ITestService::STRING_TEST_CONSTANT(),
            ITestService::STRING_TEST_CONSTANT2()};
    for (const auto& sent : strings) {
        CHECK_REPEAT_STRING(sent);
    }
}

TEST_F(BinderTest, booleanArray) {
    std::vector<bool> bools{true, false, false};
    CHECK_REVERSE(ReverseBoolean, bools);
}

TEST_F(BinderTest, byteArray) {
    std::vector<uint8_t> bytes{uint8_t{255}, uint8_t{0}, uint8_t{127}};
    CHECK_REVERSE(ReverseByte, bytes);
}

TEST_F(BinderTest, charArray) {
    std::vector<char16_t> chars{char16_t{'A'}, char16_t{'B'}, char16_t{'C'}};
    CHECK_REVERSE(ReverseChar, chars);
}

TEST_F(BinderTest, intArray) {
    std::vector<int> ints{1, 2, 3};
    CHECK_REVERSE(ReverseInt, ints);
}

TEST_F(BinderTest, longArray) {
    std::vector<int64_t> longs{-1LL, 0LL, int64_t{1LL << 60}};
    CHECK_REVERSE(ReverseLong, longs);
}

TEST_F(BinderTest, floatArray) {
    std::vector<float> floats{-0.3f, -0.7f, 8.0f};
    CHECK_REVERSE(ReverseFloat, floats);
}

TEST_F(BinderTest, doubleArray) {
    std::vector<double> doubles{1.0 / 3.0, 1.0 / 7.0, 42.0};
    CHECK_REVERSE(ReverseDouble, doubles);
}

TEST_F(BinderTest, byteEnumArray) {
    std::vector<ByteEnum> bytes{ByteEnum::BAR, ByteEnum::FOO, ByteEnum::BAZ};
    CHECK_REVERSE(ReverseByteEnum, bytes);
}

TEST_F(BinderTest, byteEnumArray2) {
    std::vector<ByteEnum> bytes{std::begin(android::enum_range<ByteEnum>()),
                                std::end(android::enum_range<ByteEnum>())};
    CHECK_REVERSE(ReverseByteEnum, bytes);
}

TEST_F(BinderTest, intEnumArray) {
    std::vector<IntEnum> ints{IntEnum::BAR, IntEnum::BAZ, IntEnum::FOO};
    CHECK_REVERSE(ReverseIntEnum, ints);
}

TEST_F(BinderTest, longEnumArray) {
    std::vector<LongEnum> longs{LongEnum::BAR, LongEnum::BAZ, LongEnum::FOO};
    CHECK_REVERSE(ReverseLongEnum, longs);
}

PORT_TEST(memref, "com.android.trusty.binder.test")
