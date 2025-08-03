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

#include <audio_utils/CircularBuffer.h>
#include <gtest/gtest.h>
#include <vector>

using android::audio_utils::CircularBuffer;

inline constexpr auto MAX_BUFFER_SIZE = 256u;
// This array has length that is a divisor of MAX_BUFFER_SIZE
inline const uint8_t REFERENCE_DATA_1[] = { 0, 1, 2, 3, 4, 5, 6, 7};
// This array has length that is prime with respect to MAX_BUFFER_SIZE
inline const uint8_t REFERENCE_DATA_2[] = { 0, 1, 2, 3, 4, 5, 6 };

TEST(audio_utils_circular_buffer, TestBufferConstructor) {
    CircularBuffer buffer(MAX_BUFFER_SIZE);
    ASSERT_EQ(0u, buffer.availableToRead());
    ASSERT_EQ(MAX_BUFFER_SIZE, buffer.availableToWrite());
    ASSERT_TRUE(buffer.empty());
}

TEST(audio_utils_circular_buffer, TestBufferReadByte) {
    CircularBuffer buffer(MAX_BUFFER_SIZE);
    buffer.write(REFERENCE_DATA_1, sizeof(REFERENCE_DATA_1));
    ASSERT_EQ(MAX_BUFFER_SIZE - sizeof(REFERENCE_DATA_1), buffer.availableToWrite());
    for (auto i = 0u; i < sizeof(REFERENCE_DATA_1); ++i) {
        ASSERT_EQ(REFERENCE_DATA_1[i], buffer.readByte());
    }
    ASSERT_EQ(MAX_BUFFER_SIZE, buffer.availableToWrite());
    ASSERT_TRUE(buffer.empty());
}

TEST(audio_utils_circular_buffer, TestBufferWriteByte) {
    CircularBuffer buffer(MAX_BUFFER_SIZE);
    auto bytesToWrite = buffer.availableToWrite();
    for (auto i = 0u; i < bytesToWrite; ++i) {
      buffer.writeByte(REFERENCE_DATA_2[i % sizeof(REFERENCE_DATA_2)]);
    }
    ASSERT_EQ(MAX_BUFFER_SIZE, buffer.availableToRead());
    ASSERT_EQ(0u, buffer.availableToWrite());

    // Read data and check it matches with reference
    uint8_t readData[sizeof(REFERENCE_DATA_2)] = { 0 };
    ASSERT_EQ(sizeof(REFERENCE_DATA_2), buffer.read(readData, sizeof(readData)));
    ASSERT_EQ(0, memcmp(REFERENCE_DATA_2, readData, sizeof(REFERENCE_DATA_2)));

    // Write reference data which will wrap around the circular buffer
    ASSERT_EQ(sizeof(REFERENCE_DATA_2), buffer.availableToWrite());
    bytesToWrite = buffer.availableToWrite();
    for (auto i = 0u; i < bytesToWrite; ++i) {
        buffer.writeByte(REFERENCE_DATA_2[i]);
    }

    // Ensure that all the bytes in the buffer are correct
    uint8_t tmp[MAX_BUFFER_SIZE] = { 0 };
    uint8_t *p = tmp;
    ASSERT_EQ(MAX_BUFFER_SIZE, buffer.read(tmp, sizeof(tmp)));
    constexpr auto BYTES_LEFT = MAX_BUFFER_SIZE - sizeof(REFERENCE_DATA_2);
    for (auto i = 0u; i < BYTES_LEFT; ++i, ++p) {
        ASSERT_EQ(REFERENCE_DATA_2[i % sizeof(REFERENCE_DATA_2)], *p);
    }
    for (auto i = 0u; i < sizeof(REFERENCE_DATA_2); ++i, ++p) {
        ASSERT_EQ(REFERENCE_DATA_2[i % sizeof(REFERENCE_DATA_2)], *p);
    }
}

// Test reading and writing with data length that is a divisor with respect to buffer sizing.
TEST(audio_utils_circular_buffer, TestBufferReadWrite1) {
    CircularBuffer buffer(MAX_BUFFER_SIZE);

    // Write a few copies of the reference data into the buffer
    constexpr auto NUM_WRITES = MAX_BUFFER_SIZE / sizeof(REFERENCE_DATA_1) - 1;
    for (auto i = 0u; i < NUM_WRITES; ++i) {
        ASSERT_EQ(sizeof(REFERENCE_DATA_1), buffer.write(REFERENCE_DATA_1,
                sizeof(REFERENCE_DATA_1)));
    }

    // Exercise read() and write() with reference data and ensure buffer wrap around
    uint8_t tmpData[sizeof(REFERENCE_DATA_1)] = { 0 };
    for (auto i = 0u; i < 3 * NUM_WRITES; ++i) {
        ASSERT_EQ(sizeof(REFERENCE_DATA_1), buffer.write(REFERENCE_DATA_1,
                sizeof(REFERENCE_DATA_1)));
        ASSERT_EQ(sizeof(tmpData), buffer.read(tmpData, sizeof(tmpData)));
        ASSERT_EQ(0, memcmp(REFERENCE_DATA_1, tmpData, sizeof(tmpData)));
    }
}

// Test reading and writing with prime sized data length with respect to buffer sizing.
TEST(audio_utils_circular_buffer, TestBufferReadWrite2) {
    CircularBuffer buffer(MAX_BUFFER_SIZE);

    // Write a few copies of the reference data into the buffer
    constexpr auto NUM_WRITES = MAX_BUFFER_SIZE / sizeof(REFERENCE_DATA_2) - 1;
    for (auto i = 0u; i < NUM_WRITES; ++i) {
        ASSERT_EQ(sizeof(REFERENCE_DATA_2), buffer.write(REFERENCE_DATA_2,
                sizeof(REFERENCE_DATA_2)));
    }

    // Exercise read() and write() with reference data and ensure buffer wrap around
    uint8_t tmpData[sizeof(REFERENCE_DATA_2)] = { 0 };
    for (auto i = 0u; i < 3 * NUM_WRITES; ++i) {
        ASSERT_EQ(sizeof(REFERENCE_DATA_2), buffer.write(REFERENCE_DATA_2,
                sizeof(REFERENCE_DATA_2)));
        ASSERT_EQ(sizeof(tmpData), buffer.read(tmpData, sizeof(tmpData)));
        ASSERT_EQ(0, memcmp(REFERENCE_DATA_2, tmpData, sizeof(tmpData)));
    }
}

TEST(audio_utils_circular_buffer, TestBufferClear) {
    CircularBuffer buffer(MAX_BUFFER_SIZE);
    uint8_t zeroData[MAX_BUFFER_SIZE] = { 0 };
    buffer.write(zeroData, MAX_BUFFER_SIZE);
    ASSERT_EQ(0u, buffer.availableToWrite());
    ASSERT_EQ(MAX_BUFFER_SIZE, buffer.availableToRead());
    buffer.clear();
    ASSERT_EQ(MAX_BUFFER_SIZE, buffer.availableToWrite());
    ASSERT_EQ(0u, buffer.availableToRead());
    ASSERT_TRUE(buffer.empty());
}
