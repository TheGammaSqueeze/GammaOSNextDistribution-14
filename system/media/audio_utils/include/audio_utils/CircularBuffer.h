/*
 * Copyright 2023, The Android Open Source Project
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

#pragma once

#include <algorithm>
#include <cstdint>
#include <memory>
#include <vector>

namespace android::audio_utils {

/**
 * Simple circular buffer that provides facilities to read and write single or multiple bytes
 * to and from the buffer. This implementation is not thread-safe and the reader and
 * writer must be on the same thread.
 */
class CircularBuffer {
 public:
    /**
     * \brief Create new instance specifying its maximum capacity.
     * \param maxBytes Maximum buffer capacity, in bytes.
     */
    explicit CircularBuffer(size_t maxBytes) {
        mBuffer.resize(maxBytes);
    }

    /**
     * \brief Read bytes into buffer from this instance.
     * \param buffer The buffer to read into.
     * \param numBytes The number of bytes to read.
     * \return The number of bytes read.
     */
    size_t read(uint8_t *buffer, size_t numBytes) {
        if (empty() || numBytes == 0) {
            return 0;
        }
        const auto bytesToRead = std::min(numBytes, availableToRead());
        if (mHeadPosition > mTailPosition) {
            memcpy(buffer, &mBuffer[mTailPosition], bytesToRead);
            mTailPosition += bytesToRead;
        } else {
            // handle loop around
            auto bytesToEnd = mBuffer.size() - mTailPosition;
            if (bytesToRead <= bytesToEnd) {
                memcpy(buffer, &mBuffer[mTailPosition], bytesToRead);
                mTailPosition += bytesToRead;
                if (mTailPosition == mBuffer.size()) {
                    mTailPosition = 0;
                }
            } else {
                memcpy(buffer, &mBuffer[mTailPosition], bytesToEnd);
                const auto remaining = bytesToRead - bytesToEnd;
                memcpy(buffer + bytesToEnd, &mBuffer[0], remaining);
                mTailPosition = remaining;
            }
        }
        mFull = false;
        return bytesToRead;
    }

    /**
     * \brief Read out the next byte from this instance.
     * Check that empty() is false before calling this function.
     * \return The value of the byte read.
     */
    uint8_t readByte() {
        if (empty()) {
            return 0;
        }
        mFull = false;
        auto result = mBuffer[mTailPosition++];
        if (mTailPosition == mBuffer.size()) {
            mTailPosition = 0;
        }
        return result;
    }

    /**
     * \brief Write bytes from buffer into this instance.
     * \param buffer The buffer to read from.
     * \param numBytes The number of bytes to write.
     * \return The number of bytes written.
     */
    size_t write(const uint8_t *buffer, size_t numBytes) {
        if (mFull || numBytes == 0) {
            return 0;
        }
        const auto bytesToWrite = std::min(numBytes, availableToWrite());
        if (mHeadPosition < mTailPosition) {
            memcpy(&mBuffer[mHeadPosition], buffer, bytesToWrite);
            mHeadPosition += bytesToWrite;
        } else {
            // handle loop around
            const auto bytesToEnd = mBuffer.size() - mHeadPosition;
            if (bytesToWrite <= bytesToEnd) {
                memcpy(&mBuffer[mHeadPosition], buffer, bytesToWrite);
                mHeadPosition += bytesToWrite;
                if (mHeadPosition == mBuffer.size()) {
                    mHeadPosition = 0;
                }
            } else {
                memcpy(&mBuffer[mHeadPosition], buffer, bytesToEnd);
                const auto remaining = bytesToWrite - bytesToEnd;
                memcpy(&mBuffer[0], buffer + bytesToEnd, remaining);
                mHeadPosition = remaining;
            }
        }
        mFull = (mHeadPosition == mTailPosition);
        return bytesToWrite;
    }

    /**
     * \brief Write a single byte into this instance.
     * Check availableToWrite() > 0 before calling this function.
     * \param byte The value of the byte to write.
     */
    void writeByte(uint8_t byte) {
        if (availableToWrite() == 0) {
            return;
        }
        mBuffer[mHeadPosition++] = byte;
        if (mHeadPosition == mBuffer.size()) {
            mHeadPosition = 0;
        }
        mFull = (mHeadPosition == mTailPosition);
    }

    /**
     * Clear the data stored in this instance.
     */
    void clear() {
        mHeadPosition = 0;
        mTailPosition = 0;
        mFull = false;
    }

    /**
     * \brief The number of bytes stored in this instance.
     * \return The number of bytes stored.
     */
    size_t availableToRead() const {
        if (mFull) {
            return mBuffer.size();
        }
        return (mHeadPosition >= mTailPosition) ? (mHeadPosition - mTailPosition)
                : (mHeadPosition - mTailPosition + mBuffer.size());
    }

    /**
     * \brief The free space remaining that can be written into before buffer is full.
     * \return The number of bytes of free space.
     */
    size_t availableToWrite() const {
        return mBuffer.size() - availableToRead();
    }

    /**
     * \brief Is there any data stored in this instance?
     * \return true if there is no data stored in this instance, otherwise false.
     */
    bool empty() const {
        return !mFull && mHeadPosition == mTailPosition;
    }

 private:
    std::vector<uint8_t> mBuffer;
    size_t mHeadPosition = 0u;
    size_t mTailPosition = 0u;
    bool mFull = false;
};

}  // namespace android::audio_utils
