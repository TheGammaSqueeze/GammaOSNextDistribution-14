/*
 * Copyright (C) 2022, The Android Open Source Project
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

#include <aidl/android/frameworks/stats/VendorAtom.h>
#include <gtest/gtest.h>
#include <test_vendor_atoms.h>

#include <limits>

#include "frameworks/proto_logging/stats/stats_log_api_gen/test_vendor_atoms.pb.h"

namespace android {
namespace api_gen_vendor_tests {

using namespace android::VendorAtoms;
using namespace aidl::android::frameworks::stats;

using std::string;
using std::vector;

namespace {

static const int32_t kTestIntValue = 100;
static const int32_t kTestUidValue = 1000;
static const int32_t kTestPidValue = 3000;
static const int64_t kTestLongValue = std::numeric_limits<int64_t>::max() - kTestIntValue;
static const float kTestFloatValue = (float)kTestIntValue / kTestLongValue;
static const bool kTestBoolValue = true;
static const char* kTestStringValue = "test_string";
static const char* kTestStringValue2 = "test_string2";

}  // namespace

/**
 * Tests native auto generated code for specific vendor atom contains proper ids
 */
TEST(ApiGenVendorAtomTest, AtomIdConstantsTest) {
    EXPECT_EQ(VENDOR_ATOM1, 105501);
    EXPECT_EQ(VENDOR_ATOM2, 105502);
    EXPECT_EQ(VENDOR_ATOM4, 105504);
}

/**
 * Tests native auto generated code for specific vendor atom contains proper enums
 */
TEST(ApiGenVendorAtomTest, AtomEnumTest) {
    EXPECT_EQ(VendorAtom1::TYPE_UNKNOWN, 0);
    EXPECT_EQ(VendorAtom1::TYPE_1, 1);
    EXPECT_EQ(VendorAtom1::TYPE_2, 2);
    EXPECT_EQ(VendorAtom1::TYPE_3, 3);

    EXPECT_EQ(VendorAtom1::ANOTHER_TYPE_UNKNOWN, 0);
    EXPECT_EQ(VendorAtom1::ANOTHER_TYPE_1, 1);
    EXPECT_EQ(VendorAtom1::ANOTHER_TYPE_2, 2);
    EXPECT_EQ(VendorAtom1::ANOTHER_TYPE_3, 3);

    EXPECT_EQ(VendorAtom2::TYPE_UNKNOWN, 0);
    EXPECT_EQ(VendorAtom2::TYPE_1, 1);
    EXPECT_EQ(VendorAtom2::TYPE_2, 2);
    EXPECT_EQ(VendorAtom2::TYPE_3, 3);

    EXPECT_EQ(VendorAtom2::ANOTHER_TYPE_UNKNOWN, 0);
    EXPECT_EQ(VendorAtom2::ANOTHER_TYPE_1, 1);
    EXPECT_EQ(VendorAtom2::ANOTHER_TYPE_2, 2);
    EXPECT_EQ(VendorAtom2::ANOTHER_TYPE_3, 3);

    EXPECT_EQ(VendorAtom4::TYPE_UNKNOWN, 0);
    EXPECT_EQ(VendorAtom4::TYPE_1, 1);

    typedef void (*Atom1FuncWithEnum)(VendorAtom1::EnumType arg);
    typedef void (*Atom1FuncWithEnum2)(VendorAtom1::EnumType2 arg);
    typedef void (*Atom2FuncWithEnum)(VendorAtom2::EnumType arg);
    typedef void (*Atom2FuncWithEnum2)(VendorAtom2::EnumType2 arg);

    Atom1FuncWithEnum f1 = nullptr;
    Atom1FuncWithEnum2 f2 = nullptr;
    Atom2FuncWithEnum f3 = nullptr;
    Atom2FuncWithEnum2 f4 = nullptr;

    EXPECT_EQ(f1, nullptr);
    EXPECT_EQ(f2, nullptr);
    EXPECT_EQ(f3, nullptr);
    EXPECT_EQ(f4, nullptr);
}

TEST(ApiGenVendorAtomTest, buildVendorAtom1ApiTest) {
    typedef VendorAtom (*VendorAtom1BuildFunc)(
            int32_t code, char const* reverse_domain_name, int32_t enumField1, int32_t enumField2,
            int32_t int_value32, int64_t int_value64, float float_value, bool bool_value,
            int32_t enumField3, int32_t enumField4);
    VendorAtom1BuildFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    VendorAtom atom = func(VENDOR_ATOM1, kTestStringValue, VendorAtom1::TYPE_1, VendorAtom1::TYPE_2,
                           kTestIntValue, kTestLongValue, kTestFloatValue, kTestBoolValue,
                           VendorAtom1::ANOTHER_TYPE_2, VendorAtom1::ANOTHER_TYPE_3);

    EXPECT_EQ(atom.atomId, VENDOR_ATOM1);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(8));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::intValue>(), VendorAtom1::TYPE_1);
    EXPECT_EQ(atom.values[1].get<VendorAtomValue::intValue>(), VendorAtom1::TYPE_2);
    EXPECT_EQ(atom.values[2].get<VendorAtomValue::intValue>(), kTestIntValue);
    EXPECT_EQ(atom.values[3].get<VendorAtomValue::longValue>(), kTestLongValue);
    EXPECT_EQ(atom.values[4].get<VendorAtomValue::floatValue>(), kTestFloatValue);
    EXPECT_EQ(atom.values[5].get<VendorAtomValue::boolValue>(), kTestBoolValue);
    EXPECT_EQ(atom.values[6].get<VendorAtomValue::intValue>(), VendorAtom1::ANOTHER_TYPE_2);
    EXPECT_EQ(atom.values[7].get<VendorAtomValue::intValue>(), VendorAtom1::ANOTHER_TYPE_3);
    EXPECT_EQ(atom.atomAnnotations, std::nullopt);
}

TEST(ApiGenVendorAtomTest, buildVendorAtom3ApiTest) {
    typedef VendorAtom (*VendorAtom3BuildFunc)(int32_t code, char const* arg1, int32_t arg2);
    VendorAtom3BuildFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    VendorAtom atom = func(VENDOR_ATOM3, kTestStringValue, kTestIntValue);

    EXPECT_EQ(atom.atomId, VENDOR_ATOM3);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::intValue>(), kTestIntValue);
    EXPECT_EQ(atom.atomAnnotations, std::nullopt);
}

TEST(ApiGenVendorAtomTest, buildVendorAtom4ApiTest) {
    typedef VendorAtom (*VendorAtom4BuildFunc)(
            int32_t code, char const* arg1, float arg2, int32_t arg3, int64_t arg4, bool arg5,
            int32_t arg6, const vector<bool>& arg7, const vector<float>& arg8,
            const vector<int32_t>& arg9, const vector<int64_t>& arg10,
            const vector<char const*>& arg11, const vector<int32_t>& arg12);
    VendorAtom4BuildFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    const vector<bool> repeatedBool{true, false, true};
    const vector<float> repeatedFloat{kTestFloatValue, kTestFloatValue + 1.f,
                                      kTestFloatValue + 2.f};
    const vector<int32_t> repeatedInt{kTestIntValue, kTestIntValue + 1, kTestIntValue + 2};
    const vector<int64_t> repeatedLong{kTestLongValue, kTestLongValue + 1, kTestLongValue + 2};
    const vector<const char*> repeatedString{kTestStringValue, kTestStringValue2, kTestStringValue};
    const vector<int32_t> repeatedEnum{VendorAtom4::TYPE_1, VendorAtom4::TYPE_UNKNOWN,
                                       VendorAtom4::TYPE_1};

    VendorAtom atom = func(VENDOR_ATOM4, kTestStringValue, kTestFloatValue, kTestIntValue,
                           kTestLongValue, kTestBoolValue, VendorAtom4::TYPE_1, repeatedBool,
                           repeatedFloat, repeatedInt, repeatedLong, repeatedString, repeatedEnum);

    EXPECT_EQ(atom.atomId, VENDOR_ATOM4);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(11));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::floatValue>(), kTestFloatValue);
    EXPECT_EQ(atom.values[1].get<VendorAtomValue::intValue>(), kTestIntValue);
    EXPECT_EQ(atom.values[2].get<VendorAtomValue::longValue>(), kTestLongValue);
    EXPECT_EQ(atom.values[3].get<VendorAtomValue::boolValue>(), kTestBoolValue);
    EXPECT_EQ(atom.values[4].get<VendorAtomValue::intValue>(), VendorAtom4::TYPE_1);

    EXPECT_EQ(atom.values[5].get<VendorAtomValue::repeatedBoolValue>(), repeatedBool);
    EXPECT_EQ(atom.values[6].get<VendorAtomValue::repeatedFloatValue>(), repeatedFloat);
    EXPECT_EQ(atom.values[7].get<VendorAtomValue::repeatedIntValue>(), repeatedInt);
    EXPECT_EQ(atom.values[8].get<VendorAtomValue::repeatedLongValue>(), repeatedLong);
    EXPECT_TRUE(atom.values[9].get<VendorAtomValue::repeatedStringValue>().has_value());
    EXPECT_EQ(atom.values[9].get<VendorAtomValue::repeatedStringValue>()->size(),
              repeatedString.size());
    const auto& repeatedStringValue = *atom.values[9].get<VendorAtomValue::repeatedStringValue>();
    for (size_t i = 0; i < repeatedString.size(); i++) {
        EXPECT_EQ(repeatedString[i], *repeatedStringValue[i]);
    }
    EXPECT_EQ(atom.values[10].get<VendorAtomValue::repeatedIntValue>(), repeatedEnum);
    EXPECT_EQ(atom.atomAnnotations, std::nullopt);
}

TEST(ApiGenVendorAtomTest, buildVendorAtom5ApiTest) {
    typedef VendorAtom (*VendorAtom5BuildFunc)(int32_t code, char const* arg1, float arg2,
                                               int32_t arg3, int64_t arg4,
                                               const vector<uint8_t>& arg5);
    VendorAtom5BuildFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    ::android::stats_log_api_gen::TestNestedMessage nestedMessage;
    nestedMessage.set_float_field(kTestFloatValue);
    nestedMessage.set_int_field(kTestIntValue);
    nestedMessage.set_long_field(kTestLongValue);

    string nestedMessageString;
    nestedMessage.SerializeToString(&nestedMessageString);

    vector<uint8_t> nestedMessageBytes(nestedMessageString.begin(), nestedMessageString.end());

    VendorAtom atom = func(VENDOR_ATOM5, kTestStringValue, kTestFloatValue, kTestIntValue,
                           kTestLongValue, nestedMessageBytes);

    EXPECT_EQ(atom.atomId, VENDOR_ATOM5);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(4));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::floatValue>(), kTestFloatValue);
    EXPECT_EQ(atom.values[1].get<VendorAtomValue::intValue>(), kTestIntValue);
    EXPECT_EQ(atom.values[2].get<VendorAtomValue::longValue>(), kTestLongValue);
    EXPECT_EQ(atom.values[3].get<VendorAtomValue::byteArrayValue>(), nestedMessageBytes);
    EXPECT_EQ(atom.atomAnnotations, std::nullopt);

    string nestedMessageStringResult(atom.values[3].get<VendorAtomValue::byteArrayValue>()->begin(),
                                     atom.values[3].get<VendorAtomValue::byteArrayValue>()->end());
    EXPECT_EQ(nestedMessageStringResult, nestedMessageString);

    ::android::stats_log_api_gen::TestNestedMessage nestedMessageResult;
    nestedMessageResult.ParseFromString(nestedMessageStringResult);
    EXPECT_EQ(nestedMessageResult.float_field(), kTestFloatValue);
    EXPECT_EQ(nestedMessageResult.int_field(), kTestIntValue);
    EXPECT_EQ(nestedMessageResult.long_field(), kTestLongValue);
}

TEST(ApiGenVendorAtomTest, buildAtomWithTruncateTimestampTest) {
    /**
     * Expected signature equal to VendorAtomWithTrancateTimestampCreateFunc to log
     * 3 different atoms with truncate_timestamp
     *      VendorAtomWithTruncateTimestamp truncateTimestampAtom1 = 105510 [
     *          (android.os.statsd.truncate_timestamp) = true
     *      ];
     *      VendorAtomWithTruncateTimestamp2 truncateTimestampAtom2 = 105511 [
     *          (android.os.statsd.truncate_timestamp) = true
     *      ];
     *      VendorAtomWithTruncateTimestamp3 truncateTimestampAtom3 = 105512 [
     *          (android.os.statsd.truncate_timestamp) = true
     *      ];
     *
     */
    typedef VendorAtom (*VendorAtomWithTrancateTimestampCreateFunc)(
            int32_t code, char const* reverse_domain_name, int32_t state);
    VendorAtomWithTrancateTimestampCreateFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    VendorAtom atom1 = func(TRUNCATE_TIMESTAMP_ATOM1, kTestStringValue,
                            VendorAtomWithTruncateTimestamp::TEST_STATE_1);
    EXPECT_EQ(atom1.atomId, TRUNCATE_TIMESTAMP_ATOM1);
    EXPECT_EQ(atom1.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom1.values.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom1.values[0].get<VendorAtomValue::intValue>(),
              VendorAtomWithTruncateTimestamp::TEST_STATE_1);
    EXPECT_NE(atom1.atomAnnotations, std::nullopt);
    EXPECT_EQ(atom1.atomAnnotations->size(), static_cast<size_t>(1));
    EXPECT_NE(atom1.atomAnnotations.value()[0], std::nullopt);
    EXPECT_EQ(atom1.atomAnnotations.value()[0]->annotationId, AnnotationId::TRUNCATE_TIMESTAMP);
    EXPECT_TRUE(atom1.atomAnnotations.value()[0]->value.get<AnnotationValue::boolValue>());

    VendorAtom atom2 = func(TRUNCATE_TIMESTAMP_ATOM2, kTestStringValue,
                            VendorAtomWithTruncateTimestamp2::TEST_STATE_2);
    EXPECT_EQ(atom2.atomId, TRUNCATE_TIMESTAMP_ATOM2);
    EXPECT_EQ(atom2.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom2.values.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom2.values[0].get<VendorAtomValue::intValue>(),
              VendorAtomWithTruncateTimestamp2::TEST_STATE_2);
    EXPECT_NE(atom2.atomAnnotations, std::nullopt);
    EXPECT_EQ(atom2.atomAnnotations->size(), static_cast<size_t>(1));
    EXPECT_NE(atom2.atomAnnotations.value()[0], std::nullopt);
    EXPECT_EQ(atom2.atomAnnotations.value()[0]->annotationId, AnnotationId::TRUNCATE_TIMESTAMP);
    EXPECT_TRUE(atom2.atomAnnotations.value()[0]->value.get<AnnotationValue::boolValue>());

    VendorAtom atom3 = func(TRUNCATE_TIMESTAMP_ATOM2, kTestStringValue, kTestIntValue);
    EXPECT_EQ(atom3.atomId, TRUNCATE_TIMESTAMP_ATOM2);
    EXPECT_EQ(atom3.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom3.values.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom3.values[0].get<VendorAtomValue::intValue>(), kTestIntValue);
    EXPECT_NE(atom3.atomAnnotations, std::nullopt);
    EXPECT_EQ(atom3.atomAnnotations->size(), static_cast<size_t>(1));
    EXPECT_NE(atom3.atomAnnotations.value()[0], std::nullopt);
    EXPECT_EQ(atom3.atomAnnotations.value()[0]->annotationId, AnnotationId::TRUNCATE_TIMESTAMP);
    EXPECT_TRUE(atom3.atomAnnotations.value()[0]->value.get<AnnotationValue::boolValue>());
}

TEST(ApiGenVendorAtomTest, buildAtomWithExclusiveStateAnnotationTest) {
    /* Expected signature equal to VendorAtomWithStateCreateFunc to log
     * atoms with similar definitions:
     *      VendorAtomWithState3 stateAtom3 = 105508
     */
    typedef VendorAtom (*VendorAtomWithStateCreateFunc)(
            int32_t code, char const* reverse_domain_name, int32_t state);
    VendorAtomWithStateCreateFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    VendorAtom atom = func(STATE_ATOM3, kTestStringValue, VendorAtomWithState3::TEST_STATE_3);
    EXPECT_EQ(atom.atomId, STATE_ATOM3);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::intValue>(), VendorAtomWithState3::TEST_STATE_3);
    EXPECT_NE(atom.valuesAnnotations, std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations->size(), static_cast<size_t>(1));
    EXPECT_NE(atom.valuesAnnotations.value()[0], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->valueIndex, 0);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations[0].annotationId,
              AnnotationId::EXCLUSIVE_STATE);
    EXPECT_TRUE(atom.valuesAnnotations.value()[0]
                        ->annotations[0]
                        .value.get<AnnotationValue::boolValue>());

    EXPECT_EQ(atom.atomAnnotations, std::nullopt);
}

TEST(ApiGenVendorAtomTest, buildAtomWithExclusiveStateAndPrimaryFieldAnnotationTest) {
    /**
     * Expected signature equal to VendorAtomWithStateCreateFunc to log atom
     *      VendorAtomWithState stateAtom1 = 105506
     * which has 1 primary_field & 1 exclusive_state annotations associated with 2 fields
     */
    typedef VendorAtom (*VendorAtomWithStateCreateFunc)(
            int32_t code, char const* reverse_domain_name, int32_t uid, int32_t state);
    VendorAtomWithStateCreateFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    VendorAtom atom =
            func(STATE_ATOM1, kTestStringValue, kTestUidValue, VendorAtomWithState::TEST_STATE_3);
    EXPECT_EQ(atom.atomId, STATE_ATOM1);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(2));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::intValue>(), kTestUidValue);
    EXPECT_EQ(atom.values[1].get<VendorAtomValue::intValue>(), VendorAtomWithState::TEST_STATE_3);
    EXPECT_NE(atom.valuesAnnotations, std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations->size(), static_cast<size_t>(2));
    EXPECT_NE(atom.valuesAnnotations.value()[0], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->valueIndex, 0);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations[0].annotationId,
              AnnotationId::PRIMARY_FIELD);
    EXPECT_TRUE(atom.valuesAnnotations.value()[0]
                        ->annotations[0]
                        .value.get<AnnotationValue::boolValue>());
    EXPECT_NE(atom.valuesAnnotations.value()[1], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->valueIndex, 1);
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->annotations.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->annotations[0].annotationId,
              AnnotationId::EXCLUSIVE_STATE);
    EXPECT_TRUE(atom.valuesAnnotations.value()[1]
                        ->annotations[0]
                        .value.get<AnnotationValue::boolValue>());

    EXPECT_EQ(atom.atomAnnotations, std::nullopt);
}

TEST(ApiGenVendorAtomTest, buildAtomWithExclusiveStateAndTwoPrimaryFieldAnnotationTest) {
    /**
     * Expected signature equal to VendorAtomWithStateCreateFunc to log atom
     *      VendorAtomWithState2 stateAtom2 = 105507
     * which has 2 primary_field & 1 exclusive_state annotations associated with 3 fields
     */
    typedef VendorAtom (*VendorAtomWithStateCreateFunc)(
            int32_t code, char const* reverse_domain_name, int32_t uid, int32_t pid, int32_t state);
    VendorAtomWithStateCreateFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    VendorAtom atom = func(STATE_ATOM2, kTestStringValue, kTestUidValue, kTestPidValue,
                           VendorAtomWithState2::TEST_STATE_2);
    EXPECT_EQ(atom.atomId, STATE_ATOM2);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(3));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::intValue>(), kTestUidValue);
    EXPECT_EQ(atom.values[1].get<VendorAtomValue::intValue>(), kTestPidValue);
    EXPECT_EQ(atom.values[2].get<VendorAtomValue::intValue>(), VendorAtomWithState2::TEST_STATE_2);
    EXPECT_NE(atom.valuesAnnotations, std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations->size(), static_cast<size_t>(3));
    EXPECT_NE(atom.valuesAnnotations.value()[0], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->valueIndex, 0);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations[0].annotationId,
              AnnotationId::PRIMARY_FIELD);
    EXPECT_TRUE(atom.valuesAnnotations.value()[0]
                        ->annotations[0]
                        .value.get<AnnotationValue::boolValue>());
    EXPECT_NE(atom.valuesAnnotations.value()[1], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->valueIndex, 1);
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->annotations.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->annotations[0].annotationId,
              AnnotationId::PRIMARY_FIELD);
    EXPECT_TRUE(atom.valuesAnnotations.value()[1]
                        ->annotations[0]
                        .value.get<AnnotationValue::boolValue>());
    EXPECT_NE(atom.valuesAnnotations.value()[2], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[2]->valueIndex, 2);
    EXPECT_EQ(atom.valuesAnnotations.value()[2]->annotations.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.valuesAnnotations.value()[2]->annotations[0].annotationId,
              AnnotationId::EXCLUSIVE_STATE);
    EXPECT_TRUE(atom.valuesAnnotations.value()[2]
                        ->annotations[0]
                        .value.get<AnnotationValue::boolValue>());

    EXPECT_EQ(atom.atomAnnotations, std::nullopt);
}

TEST(ApiGenVendorAtomTest, buildAtomWithMultipleAnnotationsPerValueTest) {
    /**
     * Expected signature equal to VendorAtomWithStateCreateFunc to log atom
     *      VendorAtomWithState4 stateAtom4 = 105509
     * which has 4 state annotations associated with single value field (index 0)
     * testing that TRIGGER_STATE_RESET not be added
     */
    typedef VendorAtom (*VendorAtomWithStateCreateFunc)(
            int32_t code, char const* reverse_domain_name, int32_t state, bool someFlag);
    VendorAtomWithStateCreateFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    VendorAtom atom = func(STATE_ATOM4, kTestStringValue, VendorAtomWithState4::ON, kTestBoolValue);
    EXPECT_EQ(atom.atomId, STATE_ATOM4);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(2));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::intValue>(), VendorAtomWithState4::ON);
    EXPECT_EQ(atom.values[1].get<VendorAtomValue::boolValue>(), kTestBoolValue);
    EXPECT_NE(atom.valuesAnnotations, std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations->size(), static_cast<size_t>(2));
    EXPECT_NE(atom.valuesAnnotations.value()[0], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->valueIndex, 0);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations.size(), static_cast<size_t>(2));
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations[0].annotationId,
              AnnotationId::EXCLUSIVE_STATE);
    EXPECT_TRUE(atom.valuesAnnotations.value()[0]
                        ->annotations[0]
                        .value.get<AnnotationValue::boolValue>());
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations[1].annotationId,
              AnnotationId::STATE_NESTED);
    EXPECT_TRUE(atom.valuesAnnotations.value()[0]
                        ->annotations[1]
                        .value.get<AnnotationValue::boolValue>());
    EXPECT_NE(atom.valuesAnnotations.value()[1], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->valueIndex, 1);
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->annotations.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->annotations[0].annotationId,
              AnnotationId::PRIMARY_FIELD);
    EXPECT_EQ(atom.atomAnnotations, std::nullopt);
}

TEST(ApiGenVendorAtomTest, buildAtomWithTriggerResetAnnotationTest) {
    /**
     * Expected signature equal to CreateStateAtom4ApiWrapper to log atom
     *      VendorAtomWithState4 stateAtom4 = 105509
     * which has 4 state annotations associated with single value field (index 0)
     * testing that TRIGGER_STATE_RESET will be added
     */
    typedef VendorAtom (*VendorAtomWithStateCreateFunc)(
            int32_t code, char const* reverse_domain_name, int32_t state, bool someFlag);
    VendorAtomWithStateCreateFunc func = &createVendorAtom;

    EXPECT_NE(func, nullptr);

    const int kDefaultStateValue = VendorAtomWithState4::OFF;

    VendorAtom atom =
            func(STATE_ATOM4, kTestStringValue, VendorAtomWithState4::RESET, kTestBoolValue);
    EXPECT_EQ(atom.atomId, STATE_ATOM4);
    EXPECT_EQ(atom.reverseDomainName, kTestStringValue);
    EXPECT_EQ(atom.values.size(), static_cast<size_t>(2));
    EXPECT_EQ(atom.values[0].get<VendorAtomValue::intValue>(), VendorAtomWithState4::RESET);
    EXPECT_EQ(atom.values[1].get<VendorAtomValue::boolValue>(), kTestBoolValue);
    EXPECT_NE(atom.valuesAnnotations, std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations->size(), static_cast<size_t>(2));
    EXPECT_NE(atom.valuesAnnotations.value()[0], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->valueIndex, 0);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations.size(), static_cast<size_t>(3));
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations[0].annotationId,
              AnnotationId::EXCLUSIVE_STATE);
    EXPECT_TRUE(atom.valuesAnnotations.value()[0]
                        ->annotations[0]
                        .value.get<AnnotationValue::boolValue>());
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations[1].annotationId,
              AnnotationId::STATE_NESTED);
    EXPECT_TRUE(atom.valuesAnnotations.value()[0]
                        ->annotations[1]
                        .value.get<AnnotationValue::boolValue>());
    EXPECT_EQ(atom.valuesAnnotations.value()[0]->annotations[2].annotationId,
              AnnotationId::TRIGGER_STATE_RESET);
    EXPECT_EQ(atom.valuesAnnotations.value()[0]
                      ->annotations[2]
                      .value.get<AnnotationValue::intValue>(),
              kDefaultStateValue);
    EXPECT_NE(atom.valuesAnnotations.value()[1], std::nullopt);
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->valueIndex, 1);
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->annotations.size(), static_cast<size_t>(1));
    EXPECT_EQ(atom.valuesAnnotations.value()[1]->annotations[0].annotationId,
              AnnotationId::PRIMARY_FIELD);
    EXPECT_EQ(atom.atomAnnotations, std::nullopt);
}

}  // namespace api_gen_vendor_tests
}  // namespace android
