/*
 * Copyright 2020 The Android Open Source Project
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

#include <ImageHashManager.h>
#include <android-base/file.h>
#include <gtest/gtest.h>
#include <pHash/phash_fingerprinter.h>

namespace android {
class ImageHashManagerTest : public ::testing::Test {};

namespace {

static std::array<uint8_t, kImageSize * 4> convert1ByteBufferTo4Bytes(const uint8_t* buffer) {
    std::array<uint8_t, kImageSize * 4> newBuffer;
    for (int i = 0; i < kImageSize; i++) {
        const uint8_t* p = buffer + i;
        size_t index = i * 4;
        newBuffer[index] = *p;
        newBuffer[index + 1] = *p;
        newBuffer[index + 2] = *p;
        newBuffer[index + 3] = 0xFF;
    }
    return newBuffer;
}

std::string GetTestDataPath(const std::string& fn) {
    static std::string exec_dir = android::base::GetExecutableDirectory();
    return exec_dir + "/test_data/" + fn;
}

std::string NewFrameFromJpeg(const char* filename) {
    // Read the jpeg file
    const std::string full_filename = GetTestDataPath(filename);
    std::string raw_data;
    EXPECT_TRUE(base::ReadFileToString(full_filename, &raw_data));
    return raw_data;
}

int64_t GetFingerprint(const char* filename) {
    const auto frame = NewFrameFromJpeg(filename);
    PhashFingerprinter fingerprinter;
    return fingerprinter.GenerateFingerprint(reinterpret_cast<const uint8_t*>(frame.c_str()));
}

TEST(ImageHashManagerTest, ShouldGenerateFingerprintCorrectly) {
    ASSERT_EQ(5241969330366601001LL, GetFingerprint("120.jpg.raw"));
    ASSERT_EQ(6191181876346691487LL, GetFingerprint("124.jpg.raw"));
    ASSERT_EQ(5902951508784914335LL, GetFingerprint("125.jpg.raw"));
    ASSERT_EQ(5015741588639023054LL, GetFingerprint("126.jpg.raw"));
}

int64_t CreatePHash(const char* filename) {
    const auto frame = NewFrameFromJpeg(filename);
    std::array<uint8_t, 8> outImageHash;
    const auto buffer = reinterpret_cast<const uint8_t*>(frame.c_str());
    const auto expandedBuffer = convert1ByteBufferTo4Bytes(buffer);

    int32_t status = ImageHashManager::generatePHash(expandedBuffer.data(), 2, 2, 2, &outImageHash);
    EXPECT_EQ(-EINVAL, status); // should fail due to wrong size

    status = ImageHashManager::generatePHash(expandedBuffer.data(), 32, 32, 32, &outImageHash);
    EXPECT_EQ(0, status); // should success
    return *reinterpret_cast<const int64_t*>(outImageHash.data());
}

TEST(ImageHashManagerTest, ShouldCreatePHashCorrectly) {
    ASSERT_EQ(5241969330366601001LL, CreatePHash("120.jpg.raw"));
    ASSERT_EQ(6191181876346691487LL, CreatePHash("124.jpg.raw"));
    ASSERT_EQ(5902951508784914335LL, CreatePHash("125.jpg.raw"));
    ASSERT_EQ(5015741588639023054LL, CreatePHash("126.jpg.raw"));
}

TEST(ImageHashManagerTest, TestGenerateHashWithPhash) {
    const auto frame = NewFrameFromJpeg("120.jpg.raw");
    std::array<uint8_t, 8> outImageHash;
    const auto buffer = reinterpret_cast<const uint8_t*>(frame.c_str());
    auto expandedBuffer = convert1ByteBufferTo4Bytes(buffer);

    AHardwareBuffer_Desc desc = {
            .width = 32,
            .height = 32,
            .layers = 1,
            .format = AHARDWAREBUFFER_FORMAT_BLOB,
            .usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
    };

    int32_t status =
            ImageHashManager::generateHash("phash", expandedBuffer.data(), desc, &outImageHash);
    EXPECT_EQ(0, status); // should succeed

    ASSERT_EQ(5241969330366601001LL, *reinterpret_cast<const int64_t*>(outImageHash.data()));
}

TEST(ImageHashManagerTest, TestGenerateHashWithInvalidHash) {
    const auto frame = NewFrameFromJpeg("120.jpg.raw");
    std::array<uint8_t, 8> outImageHash;
    const auto buffer = reinterpret_cast<const uint8_t*>(frame.c_str());
    auto expandedBuffer = convert1ByteBufferTo4Bytes(buffer);

    AHardwareBuffer_Desc desc = {
            .width = 32,
            .height = 32,
            .layers = 1,
            .format = AHARDWAREBUFFER_FORMAT_BLOB,
            .usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
    };

    int32_t status =
            ImageHashManager::generateHash("fakeHash", expandedBuffer.data(), desc, &outImageHash);
    EXPECT_EQ(-EINVAL, status); // should fail
}

} // namespace

} // namespace android
