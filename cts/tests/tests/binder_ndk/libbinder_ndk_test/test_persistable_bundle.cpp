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
#define LOG_TAG "Cts-NdkBinderTest"

#include <android/persistable_bundle.h>
#include <gtest/gtest.h>

#include "utilities.h"

class NdkBinderTest_APersistableBundle : public NdkBinderTest {};

TEST_F(NdkBinderTest_APersistableBundle, NewDelete) {
  APersistableBundle* bundle = APersistableBundle_new();
  EXPECT_NE(nullptr, bundle);
  APersistableBundle_delete(bundle);
}

TEST_F(NdkBinderTest_APersistableBundle, NewDupDelete) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);
  APersistableBundle* dup = APersistableBundle_dup(bundle);
  EXPECT_NE(nullptr, dup);
  EXPECT_NE(bundle, dup);
}

TEST_F(NdkBinderTest_APersistableBundle, ToFromParcel) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);
  AParcel* parcel = AParcel_create();
  // put anything in the bundle
  APersistableBundle_putBoolean(bundle, "a", true);
  EXPECT_OK(APersistableBundle_writeToParcel(bundle, parcel));
  APersistableBundle* readBundle = nullptr;
  EXPECT_OK(APersistableBundle_readFromParcel(parcel, &readBundle));
  // make sure that anything is in the new bundle
  bool val = false;
  EXPECT_TRUE(APersistableBundle_getBoolean(bundle, "a", &val));
  EXPECT_TRUE(val);
}

TEST_F(NdkBinderTest_APersistableBundle, IsEqual) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);
  APersistableBundle* otherBundle = APersistableBundle_new();
  ASSERT_NE(nullptr, otherBundle);
  EXPECT_TRUE(APersistableBundle_isEqual(bundle, otherBundle));

  APersistableBundle_putBoolean(bundle, "a", true);
  EXPECT_FALSE(APersistableBundle_isEqual(bundle, otherBundle));

  APersistableBundle_putBoolean(otherBundle, "a", true);
  EXPECT_TRUE(APersistableBundle_isEqual(bundle, otherBundle));

  APersistableBundle_putBoolean(otherBundle, "a", false);
  EXPECT_FALSE(APersistableBundle_isEqual(bundle, otherBundle));
}

TEST_F(NdkBinderTest_APersistableBundle, Size) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);
  EXPECT_EQ(0, APersistableBundle_size(bundle));
  APersistableBundle_putBoolean(bundle, "a", true);
  EXPECT_EQ(1, APersistableBundle_size(bundle));
}

const bool kBoolVal = true;
const int32_t kIntVal = 11111;
const int64_t kLongVal = 12345;
const double kDoubleVal = 54321;
const std::string kStringVal = "cool";
const int32_t kNumBools = 3;
const bool kBoolVVal[] = {true, false, true};
const std::vector<int32_t> kIntVVal = {1111, -2222, 3333};
const std::vector<int64_t> kLongVVal = {11111, -22222, 33333};
const std::vector<double> kDoubleVVal = {111111, -222222, 333333};
const int kNumStrings = 3;
const char* kStringVVal[] = {"hello", "monkey", "!"};

TEST_F(NdkBinderTest_APersistableBundle, Erase) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);
  APersistableBundle_putBoolean(bundle, "a", true);
  EXPECT_EQ(1, APersistableBundle_size(bundle));
  APersistableBundle_putIntVector(bundle, "b", kIntVVal.data(), kIntVVal.size());
  EXPECT_EQ(2, APersistableBundle_size(bundle));
  // erase does nothing if entry doesn't exist
  EXPECT_EQ(0, APersistableBundle_erase(bundle, "nothing"));
  EXPECT_EQ(2, APersistableBundle_size(bundle));
  // erase works on a single entry
  EXPECT_EQ(1, APersistableBundle_erase(bundle, "a"));
  EXPECT_EQ(1, APersistableBundle_size(bundle));
  // erase works on multiple entries
  EXPECT_EQ(1, APersistableBundle_erase(bundle, "b"));
  EXPECT_EQ(0, APersistableBundle_size(bundle));
}

// allocate a buffer for a string
static char* stringAllocator(int32_t bufferSizeBytes, void*) {
  return (char*)malloc(bufferSizeBytes);
}

TEST_F(NdkBinderTest_APersistableBundle, PutAndGetAllTheThings) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);
  // put all supported types && verify
  APersistableBundle_putBoolean(bundle, "bool", kBoolVal);
  APersistableBundle_putInt(bundle, "int", kIntVal);
  APersistableBundle_putLong(bundle, "long", kLongVal);
  APersistableBundle_putDouble(bundle, "double", kDoubleVal);
  APersistableBundle_putString(bundle, "string", kStringVal.c_str());
  APersistableBundle_putBooleanVector(bundle, "boolv", kBoolVVal, kNumBools);
  APersistableBundle_putIntVector(bundle, "intv", kIntVVal.data(), kIntVVal.size());
  APersistableBundle_putLongVector(bundle, "longv", kLongVVal.data(), kLongVVal.size());
  APersistableBundle_putDoubleVector(bundle, "doublev", kDoubleVVal.data(), kDoubleVVal.size());
  APersistableBundle_putStringVector(bundle, "stringv", kStringVVal, kNumStrings);
  APersistableBundle* innerBundle = APersistableBundle_new();
  APersistableBundle_putBoolean(innerBundle, "bool", kBoolVal);
  APersistableBundle_putInt(innerBundle, "int", kIntVal);
  APersistableBundle_putPersistableBundle(bundle, "pbundle", innerBundle);
  bool outBool = false;
  int32_t outInt = 0;
  int64_t outLong = 0;
  double outDouble = 0;
  char* outString = nullptr;
  bool* outBoolV = nullptr;
  int32_t* outIntV = nullptr;
  int64_t* outLongV = nullptr;
  double* outDoubleV = nullptr;
  char** outStringV = nullptr;
  APersistableBundle* outInnerBundle;
  EXPECT_TRUE(APersistableBundle_getBoolean(bundle, "bool", &outBool));
  EXPECT_EQ(outBool, kBoolVal);
  EXPECT_TRUE(APersistableBundle_getInt(bundle, "int", &outInt));
  EXPECT_EQ(outInt, kIntVal);
  EXPECT_TRUE(APersistableBundle_getLong(bundle, "long", &outLong));
  EXPECT_EQ(outLong, kLongVal);
  EXPECT_TRUE(APersistableBundle_getDouble(bundle, "double", &outDouble));
  EXPECT_EQ(outDouble, kDoubleVal);
  EXPECT_EQ(APersistableBundle_getString(bundle, "string", &outString, &stringAllocator, nullptr),
            kStringVal.size());
  EXPECT_EQ(outString, kStringVal);

  int32_t sizeBytes = APersistableBundle_getBooleanVector(bundle, "boolv", outBoolV, 0);
  EXPECT_GT(sizeBytes, 0);
  outBoolV = (bool*)malloc(sizeBytes);
  sizeBytes = APersistableBundle_getBooleanVector(bundle, "boolv", outBoolV, sizeBytes);
  for (int32_t i = 0; i < kNumBools; i++) {
    EXPECT_EQ(outBoolV[i], kBoolVVal[i]);
  }
  free(outBoolV);

  sizeBytes = APersistableBundle_getIntVector(bundle, "intv", outIntV, 0);
  EXPECT_GT(sizeBytes, 0);
  outIntV = (int32_t*)malloc(sizeBytes);
  sizeBytes = APersistableBundle_getIntVector(bundle, "intv", outIntV, sizeBytes);
  for (int32_t i = 0; i < kIntVVal.size(); i++) {
    EXPECT_EQ(outIntV[i], kIntVVal[i]);
  }
  free(outIntV);

  sizeBytes = APersistableBundle_getLongVector(bundle, "longv", outLongV, 0);
  EXPECT_GT(sizeBytes, 0);
  outLongV = (int64_t*)malloc(sizeBytes);
  sizeBytes = APersistableBundle_getLongVector(bundle, "longv", outLongV, sizeBytes);
  for (int32_t i = 0; i < kLongVVal.size(); i++) {
    EXPECT_EQ(outLongV[i], kLongVVal[i]);
  }
  free(outLongV);

  sizeBytes = APersistableBundle_getDoubleVector(bundle, "doublev", outDoubleV, 0);
  EXPECT_GT(sizeBytes, 0);
  outDoubleV = (double*)malloc(sizeBytes);
  sizeBytes = APersistableBundle_getDoubleVector(bundle, "doublev", outDoubleV, sizeBytes);
  for (int32_t i = 0; i < kDoubleVVal.size(); i++) {
    EXPECT_EQ(outDoubleV[i], kDoubleVVal[i]);
  }
  free(outDoubleV);

  sizeBytes = APersistableBundle_getDoubleVector(bundle, "doublev", outDoubleV, 0);
  EXPECT_GT(sizeBytes, 0);
  outDoubleV = (double*)malloc(sizeBytes);
  sizeBytes = APersistableBundle_getDoubleVector(bundle, "doublev", outDoubleV, sizeBytes);
  for (int32_t i = 0; i < kDoubleVVal.size(); i++) {
    EXPECT_EQ(outDoubleV[i], kDoubleVVal[i]);
  }
  free(outDoubleV);

  sizeBytes = APersistableBundle_getStringVector(bundle, "stringv", outStringV, 0, &stringAllocator,
                                                 nullptr);
  EXPECT_GT(sizeBytes, 0);
  outStringV = (char**)malloc(sizeBytes);
  sizeBytes = APersistableBundle_getStringVector(bundle, "stringv", outStringV, sizeBytes,
                                                 &stringAllocator, nullptr);
  for (int32_t i = 0; i < kNumStrings; i++) {
    EXPECT_EQ(0, std::strcmp(outStringV[i], kStringVVal[i]));
    free(outStringV[i]);
  }
  free(outStringV);

  EXPECT_TRUE(APersistableBundle_getPersistableBundle(bundle, "pbundle", &outInnerBundle));
  EXPECT_TRUE(APersistableBundle_isEqual(innerBundle, outInnerBundle));
}

TEST_F(NdkBinderTest_APersistableBundle, WrongKeyBool) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);

  bool outBool = false;
  EXPECT_FALSE(APersistableBundle_getBoolean(bundle, "not the right key", &outBool));
}

TEST_F(NdkBinderTest_APersistableBundle, WrongKeyString) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);

  char* outString = nullptr;
  EXPECT_EQ(APersistableBundle_getString(bundle, "not the right key", &outString, &stringAllocator,
                                         nullptr),
            APERSISTABLEBUNDLE_KEY_NOT_FOUND);
}

TEST_F(NdkBinderTest_APersistableBundle, WrongKeyVector) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);

  bool* outBools = nullptr;
  EXPECT_EQ(APersistableBundle_getBooleanVector(bundle, "not the right key", outBools, 0),
            APERSISTABLEBUNDLE_KEY_NOT_FOUND);
}

TEST_F(NdkBinderTest_APersistableBundle, EmptyStringVal) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);

  APersistableBundle_putString(bundle, "key", "");
  char* outString = nullptr;
  EXPECT_EQ(APersistableBundle_getString(bundle, "key", &outString, &stringAllocator, nullptr), 0);
}

TEST_F(NdkBinderTest_APersistableBundle, EmptyVectorVal) {
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);

  // empty vector is size 0
  const bool inEmptyBools[] = {};
  APersistableBundle_putBooleanVector(bundle, "key", inEmptyBools, 0);
  bool* outBools = nullptr;
  EXPECT_EQ(APersistableBundle_getBooleanVector(bundle, "key", outBools, 0), 0);
}

TEST_F(NdkBinderTest_APersistableBundle, EmptyStringKeys) {
  int32_t sizeBytes = sizeof(char*);
  char** outKeys = (char**)malloc(sizeBytes);
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);

  // No keys yet, should be size 0
  int32_t outSizeBytes =
      APersistableBundle_getBooleanKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  ASSERT_EQ(outSizeBytes, 0);

  // Empty string as a key needs an entry like any other key
  APersistableBundle_putBoolean(bundle, "", kBoolVal);
  outSizeBytes =
      APersistableBundle_getBooleanKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  ASSERT_EQ(outSizeBytes, sizeof(char*));
  EXPECT_EQ(0, std::strcmp("", outKeys[0]));
}

static char* failAllocator(int32_t, void*) {
  return nullptr;
}

TEST_F(NdkBinderTest_APersistableBundle, FailAllocatorKeys) {
  int32_t sizeBytes = sizeof(char*);
  char** outKeys = (char**)malloc(sizeBytes);
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);

  APersistableBundle_putBoolean(bundle, "This will fail to allocate", kBoolVal);
  int32_t outSizeBytes =
      APersistableBundle_getBooleanKeys(bundle, outKeys, sizeBytes, &failAllocator, nullptr);
  EXPECT_EQ(outSizeBytes, APERSISTABLEBUNDLE_ALLOCATOR_FAILED);
}

// Check bytes and string arrays for equality and free all of the outKeys
inline void checkAndFree(int32_t inBytes, int32_t outBytes, const char** inKeys, char** outKeys,
                         int numKeys) {
  ASSERT_EQ(inBytes, outBytes);
  for (int32_t i = 0; i < numKeys; i++) {
    EXPECT_EQ(0, std::strcmp(inKeys[i], outKeys[i]));
    free(outKeys[i]);
  }
}

TEST_F(NdkBinderTest_APersistableBundle, getKeys) {
  // We will use three keys per, so we know the size of the buffer
  const int numKeys = 3;
  int32_t sizeBytes = numKeys * sizeof(char*);
  char** outKeys = (char**)malloc(sizeBytes);
  const char* keys[] = {"key1", "key2", "key3"};
  APersistableBundle* bundle = APersistableBundle_new();
  ASSERT_NE(nullptr, bundle);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putBoolean(bundle, keys[i], kBoolVal);
  }
  int32_t outSizeBytes =
      APersistableBundle_getBooleanKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putInt(bundle, keys[i], kIntVal);
  }
  outSizeBytes =
      APersistableBundle_getIntKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putLong(bundle, keys[i], kLongVal);
  }
  outSizeBytes =
      APersistableBundle_getLongKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putDouble(bundle, keys[i], kDoubleVal);
  }
  outSizeBytes =
      APersistableBundle_getDoubleKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putString(bundle, keys[i], kStringVal.c_str());
  }
  outSizeBytes =
      APersistableBundle_getStringKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putBooleanVector(bundle, keys[i], kBoolVVal, kNumBools);
  }
  outSizeBytes = APersistableBundle_getBooleanVectorKeys(bundle, outKeys, sizeBytes,
                                                         &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putIntVector(bundle, keys[i], kIntVVal.data(), kIntVVal.size());
  }
  outSizeBytes =
      APersistableBundle_getIntVectorKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putLongVector(bundle, keys[i], kLongVVal.data(), kLongVVal.size());
  }
  outSizeBytes =
      APersistableBundle_getLongVectorKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putDoubleVector(bundle, keys[i], kDoubleVVal.data(), kDoubleVVal.size());
  }
  outSizeBytes =
      APersistableBundle_getDoubleVectorKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putStringVector(bundle, keys[i], kStringVVal, kNumStrings);
  }
  outSizeBytes =
      APersistableBundle_getStringVectorKeys(bundle, outKeys, sizeBytes, &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);

  APersistableBundle* innerBundle = APersistableBundle_new();
  for (int32_t i = 0; i < numKeys; i++) {
    APersistableBundle_putPersistableBundle(bundle, keys[i], innerBundle);
  }
  outSizeBytes = APersistableBundle_getPersistableBundleKeys(bundle, outKeys, sizeBytes,
                                                             &stringAllocator, nullptr);
  checkAndFree(sizeBytes, outSizeBytes, keys, outKeys, numKeys);
}
