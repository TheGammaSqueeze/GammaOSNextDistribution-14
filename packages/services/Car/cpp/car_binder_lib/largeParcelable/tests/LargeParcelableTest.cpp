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

#include <aidl/aidl/TestStableLargeParcelable.h>
#include <aidl/aidl/TestStableLargeParcelableVector.h>
#include <aidl/aidl/TestStableParcelable.h>
#include <android/binder_auto_utils.h>
#include <android/binder_parcel.h>
#include <gtest/gtest.h>

#include <LargeParcelable.h>
#include <LargeParcelableBase.h>
#include <LargeParcelableVector.h>

#include <algorithm>
#include <vector>

namespace {

using ::aidl::aidl::TestStableLargeParcelable;
using ::aidl::aidl::TestStableLargeParcelableVector;
using ::aidl::aidl::TestStableParcelable;
using ::android::automotive::car_binder_lib::LargeParcelable;
using ::android::automotive::car_binder_lib::LargeParcelableBase;
using ::android::automotive::car_binder_lib::LargeParcelableVector;
using ::ndk::ScopedAParcel;
using ::ndk::ScopedFileDescriptor;

int TEST_VALUE = 1234;
size_t VECTOR_SIZE = 16;

std::unique_ptr<TestStableParcelable> createTestStableParcelable(size_t dataSize) {
    std::unique_ptr<TestStableParcelable> p(new TestStableParcelable);
    p->bytes.resize(dataSize);
    std::fill(p->bytes.begin(), p->bytes.end(), 0x7f);
    p->value = TEST_VALUE;
    return p;
}

std::vector<TestStableParcelable> createTestStableParcelableVector(size_t dataSize) {
    std::vector<TestStableParcelable> p;
    size_t VECTOR_SIZE = 16;
    for (size_t i = 0; i < VECTOR_SIZE; i++) {
        p.push_back(*createTestStableParcelable(dataSize / VECTOR_SIZE));
    }
    return p;
}

void checkTestStableParcelable(const TestStableParcelable* p, size_t dataSize) {
    ASSERT_EQ(dataSize, p->bytes.size());
    for (size_t i = 0; i < dataSize; i++) {
        ASSERT_EQ(0x7f, p->bytes[i]);
    }
    ASSERT_EQ(TEST_VALUE, p->value);
}

void checkTestStableParcelableVector(const std::vector<TestStableParcelable>* p, size_t dataSize) {
    ASSERT_EQ(VECTOR_SIZE, p->size());
    for (size_t i = 0; i < VECTOR_SIZE; i++) {
        checkTestStableParcelable(&((*p)[i]), dataSize / VECTOR_SIZE);
    }
}

void testWrapStableAidlWriteReadPayload(size_t dataSize) {
    std::unique_ptr<TestStableParcelable> p = createTestStableParcelable(dataSize);

    LargeParcelable sendData(std::move(p));
    ScopedAParcel parcel(AParcel_create());
    binder_status_t status;
    status = sendData.writeToParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    // Set the parcel to start from 0 because we need to read from 0.
    AParcel_setDataPosition(parcel.get(), 0);

    LargeParcelable<TestStableParcelable> receiveData;
    status = receiveData.readFromParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    ASSERT_TRUE(receiveData.getParcelable().has_value());

    const TestStableParcelable* getP = receiveData.getParcelable().value();

    checkTestStableParcelable(getP, dataSize);
}

TEST(LargeParcelableTest, LargeParcelableWrapStableAidlWriteReadSmallPayload) {
    testWrapStableAidlWriteReadPayload(1024);
}

TEST(LargeParcelableTest, LargeParcelableWrapStableAidlWriteReadLargePayload) {
    testWrapStableAidlWriteReadPayload(8 * 1024);
}

TEST(LargeParcelableTest, WrapStableAidlReuseSharedMemoryFile) {
    size_t dataSize = 8 * 1024;
    std::unique_ptr<TestStableParcelable> p = createTestStableParcelable(dataSize);

    LargeParcelable sendData(std::move(p));

    ScopedAParcel parcel(AParcel_create());
    binder_status_t status = sendData.writeToParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    ScopedAParcel parcel2(AParcel_create());
    status = sendData.writeToParcel(parcel2.get());

    ASSERT_EQ(status, STATUS_OK);

    // Set the parcel to start from 0 because we need to read from 0.
    AParcel_setDataPosition(parcel2.get(), 0);

    LargeParcelable<TestStableParcelable> receiveData;
    status = receiveData.readFromParcel(parcel2.get());

    ASSERT_EQ(status, STATUS_OK);

    ASSERT_TRUE(receiveData.getParcelable().has_value());
    const TestStableParcelable* getP = receiveData.getParcelable().value();

    checkTestStableParcelable(getP, dataSize);
}

void testParcelableToStableLargeParcelable(size_t dataSize) {
    std::unique_ptr<TestStableParcelable> p = createTestStableParcelable(dataSize);

    TestStableLargeParcelable largeP;
    largeP.payload = *p;
    auto result = LargeParcelableBase::parcelableToStableLargeParcelable(largeP);

    ASSERT_TRUE(result.ok()) << result.error();

    TestStableLargeParcelable out;
    if (result.value() == nullptr) {
        out.payload = *p;
    } else {
        out.sharedMemoryFd = std::move(*result.value());
    }

    ScopedAParcel parcel(AParcel_create());
    binder_status_t status = out.writeToParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    // Set the parcel to start from 0 because we need to read from 0.
    AParcel_setDataPosition(parcel.get(), 0);

    // The parcel generated from StableLargeParcelable should be compatible with LargeParcelable.
    // We use LargeParcelable based on TestStableParcelable to read from the generated parcel.
    LargeParcelable<TestStableParcelable> receiveData;
    status = receiveData.readFromParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    ASSERT_TRUE(receiveData.getParcelable().has_value());
    const TestStableParcelable* getP = receiveData.getParcelable().value();

    checkTestStableParcelable(getP, dataSize);
}

TEST(LargeParcelableTest, ParcelableToStableLargeParcelableSmallPayload) {
    testParcelableToStableLargeParcelable(1024);
}

TEST(LargeParcelableTest, ParcelableToStableLargeParcelableLargePayload) {
    testParcelableToStableLargeParcelable(8 * 1024);
}

void testStableLargeParcelableToParcelable(size_t dataSize) {
    std::unique_ptr<TestStableParcelable> p = createTestStableParcelable(dataSize);

    // Use LargeParcelable to write to a parcel. Since it is compatible with StableLargeParcelable,
    // it should be able to be parsed.
    LargeParcelable sendData(std::move(p));
    ScopedAParcel parcel(AParcel_create());
    binder_status_t status;
    status = sendData.writeToParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    // Set the parcel to start from 0 because we need to read from 0.
    AParcel_setDataPosition(parcel.get(), 0);

    TestStableLargeParcelable largeParcelable;
    status = largeParcelable.readFromParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    // Convert the StableLargeParcelable back to the original parcelable.
    auto result = LargeParcelableBase::stableLargeParcelableToParcelable(largeParcelable);

    ASSERT_TRUE(result.ok()) << result.error();

    const TestStableLargeParcelable* out = result.value().getObject();
    ASSERT_TRUE(out->payload.has_value());
    checkTestStableParcelable(&(out->payload.value()), dataSize);
}

TEST(LargeParcelableTest, StableLargeParcelableToParcelableSmallPayload) {
    testStableLargeParcelableToParcelable(1024);
}

TEST(LargeParcelableTest, StableLargeParcelableToParcelableLargePayload) {
    testStableLargeParcelableToParcelable(8 * 1024);
}

void testParcelableToStableLargeParcelableBackToParcelable(size_t dataSize) {
    std::unique_ptr<TestStableParcelable> p = createTestStableParcelable(dataSize);

    TestStableLargeParcelable largeP;
    largeP.payload = *p;
    auto result1 = LargeParcelableBase::parcelableToStableLargeParcelable(largeP);

    ASSERT_TRUE(result1.ok()) << result1.error();

    TestStableLargeParcelable intermediate;
    if (result1.value() == nullptr) {
        intermediate.payload = std::move(*p);
    } else {
        intermediate.sharedMemoryFd = std::move(*result1.value());
    }

    auto result2 = LargeParcelableBase::stableLargeParcelableToParcelable(intermediate);

    ASSERT_TRUE(result2.ok()) << result2.error();

    const TestStableLargeParcelable* out = result2.value().getObject();
    ASSERT_TRUE(out->payload.has_value());
    checkTestStableParcelable(&(out->payload.value()), dataSize);
}

TEST(LargeParcelableTest, ParcelableToStableLargeParcelableBackToParcelableSmallPayload) {
    testParcelableToStableLargeParcelableBackToParcelable(1024);
}

TEST(LargeParcelableTest, ParcelableToStableLargeParcelableBackToParcelableLargePayload) {
    testParcelableToStableLargeParcelableBackToParcelable(8 * 1024);
}

void testWrapStableAidlVectorWriteReadPayload(size_t dataSize) {
    std::vector<TestStableParcelable> p = createTestStableParcelableVector(dataSize);

    LargeParcelableVector sendData(std::move(p));
    ScopedAParcel parcel(AParcel_create());
    binder_status_t status;
    status = sendData.writeToParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    // Set the parcel to start from 0 because we need to read from 0.
    AParcel_setDataPosition(parcel.get(), 0);

    LargeParcelableVector<TestStableParcelable> receiveData;
    status = receiveData.readFromParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    ASSERT_TRUE(receiveData.getParcelables().has_value());
    const std::vector<TestStableParcelable>* getP = receiveData.getParcelables().value();

    checkTestStableParcelableVector(getP, dataSize);
}

TEST(LargeParcelableTest, LargeParcelableWrapStableAidlVectorWriteReadSmallPayload) {
    testWrapStableAidlVectorWriteReadPayload(1024);
}

TEST(LargeParcelableTest, LargeParcelableWrapStableAidlVectorWriteReadLargePayload) {
    testWrapStableAidlVectorWriteReadPayload(8 * 1024);
}

void testParcelableVectorToStableLargeParcelable(size_t dataSize) {
    std::vector<TestStableParcelable> p = createTestStableParcelableVector(dataSize);
    TestStableLargeParcelableVector largeP;
    largeP.payload = p;

    auto result = LargeParcelableBase::parcelableToStableLargeParcelable(largeP);

    ASSERT_TRUE(result.ok()) << result.error();

    TestStableLargeParcelableVector out;
    if (result.value() == nullptr) {
        out.payload = std::move(p);
    } else {
        out.sharedMemoryFd = std::move(*result.value());
    }

    ScopedAParcel parcel(AParcel_create());
    binder_status_t status = out.writeToParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    // Set the parcel to start from 0 because we need to read from 0.
    AParcel_setDataPosition(parcel.get(), 0);

    // The parcel generated from StableLargeParcelable should be compatible with LargeParcelable.
    // We use LargeParcelable based on TestStableParcelable to read from the generated parcel.
    LargeParcelableVector<TestStableParcelable> receiveData;
    status = receiveData.readFromParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    ASSERT_TRUE(receiveData.getParcelables().has_value());
    const std::vector<TestStableParcelable>* getP = receiveData.getParcelables().value();

    checkTestStableParcelableVector(getP, dataSize);
}

TEST(LargeParcelableTest, ParcelableVectorToStableLargeParcelableSmallPayload) {
    testParcelableVectorToStableLargeParcelable(1024);
}

TEST(LargeParcelableTest, ParcelableVectorToStableLargeParcelableLargePayload) {
    testParcelableVectorToStableLargeParcelable(8 * 1024);
}

void testStableLargeParcelableToParcelableVector(size_t dataSize) {
    std::vector<TestStableParcelable> p = createTestStableParcelableVector(dataSize);

    // Use LargeParcelable to write to a parcel. Since it is compatible with StableLargeParcelable,
    // it should be able to be parsed.
    LargeParcelableVector sendData(std::move(p));
    ScopedAParcel parcel(AParcel_create());
    binder_status_t status;
    status = sendData.writeToParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    // Set the parcel to start from 0 because we need to read from 0.
    AParcel_setDataPosition(parcel.get(), 0);

    TestStableLargeParcelableVector largeParcelable;
    status = largeParcelable.readFromParcel(parcel.get());

    ASSERT_EQ(status, STATUS_OK);

    std::vector<TestStableParcelable> out;
    // Convert the StableLargeParcelable back to the original parcelable.
    auto result = LargeParcelableBase::stableLargeParcelableToParcelable(largeParcelable);

    ASSERT_TRUE(result.ok()) << result.error();

    checkTestStableParcelableVector(&(result.value().getObject()->payload), dataSize);
}

TEST(LargeParcelableTest, StableLargeParcelableToParcelableVectorSmallPayload) {
    testStableLargeParcelableToParcelableVector(1024);
}

TEST(LargeParcelableTest, StableLargeParcelableToParcelableVectorLargePayload) {
    testStableLargeParcelableToParcelableVector(8 * 1024);
}

void testParcelableVectorToStableLargeParcelableBackToParcelableVector(size_t dataSize) {
    std::vector<TestStableParcelable> p = createTestStableParcelableVector(dataSize);
    TestStableLargeParcelableVector largeP;
    largeP.payload = p;

    auto result1 = LargeParcelableBase::parcelableToStableLargeParcelable(largeP);

    ASSERT_TRUE(result1.ok()) << result1.error();

    TestStableLargeParcelableVector intermediate;
    if (result1.value() == nullptr) {
        intermediate.payload = std::move(p);
    } else {
        intermediate.sharedMemoryFd = std::move(*result1.value());
    }

    auto result2 = LargeParcelableBase::stableLargeParcelableToParcelable(intermediate);

    ASSERT_TRUE(result2.ok()) << result2.error();

    checkTestStableParcelableVector(&(result2.value().getObject()->payload), dataSize);
}

TEST(LargeParcelableTest,
     ParcelableVectorToStableLargeParcelableBackToParcelableVectorSmallPayload) {
    testParcelableVectorToStableLargeParcelableBackToParcelableVector(1024);
}

TEST(LargeParcelableTest,
     ParcelableVectorToStableLargeParcelableBackToParcelableVectorLargePayload) {
    testParcelableVectorToStableLargeParcelableBackToParcelableVector(8 * 1024);
}

}  //  namespace
