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

#include "direct_channel_buffer_reader.h"

#include <android-base/logging.h>
#include <hardware/sensors.h>

namespace {

// DirectChannelBufferReader::Read() keeps reading until it catches up with the
// write head. To avoid infinite reads in case of corrupted buffer, put an upper
// bound on number of reads. Read() would read at most
// <kMaxReadRounds * buffer_size_samples> samples.
constexpr int kMaxReadRounds = 2;

}  // namespace

DirectChannelBufferReader::DirectChannelBufferReader(const sensors_event_t* direct_channel_buffer,
                                                     int buffer_size_samples)
    : direct_channel_buffer_(direct_channel_buffer), buffer_size_samples_(buffer_size_samples) {}

int DirectChannelBufferReader::Read(int* num_samples_skipped) {
    int num_samples_read = 0;
    int64_t last_atomic_counter_before_read = last_atomic_counter_;
    // Keep reading samples until reaching the write head.
    // Example: 1 2 3 4 0
    //                  ^
    //                head
    //
    // Example: 11 12 13 14 5 6 7 8 9 10
    //                      ^
    //                    head
    //
    // Example: UINT32_MAX-1  UINT32_MAX  1  UINT32_MAX-3 UINT32_MAX-2
    //                                    ^
    //                                  head
    //
    // Here is a more interesting corner case:
    //           1  2  <- samples obtained in previous calls to Read()
    //           1  2  3
    //                 ^
    //                 Got a new sample. Keep reading.
    //
    //           1  2  3 14 15 16 7
    //                   -------- ^
    //                            Reached the head but only got 3 samples with
    //                            consecutive counter values. Sample 3 may be
    //                            corrupted so it should be discarded. Also we
    //                            are still missing sample 8-13. Keep reading.
    //
    //           1  2  3 14 15 16 7 8 9 10 (Got 8-10. Keep reading)
    //
    //          11 12 13 14 15 16 7 8 9 10
    //                            ^
    //                            Reached the head and got all 10 consecutive
    //                            samples. Stop reading. Sample 3 was discarded
    //                            when buffer_ was truncated.
    while (true) {
        buffer_.push_back(ReadOneSample(index_));
        num_samples_read++;
        int64_t atomic_counter = static_cast<uint32_t>(buffer_.back().reserved0);
        bool reached_zero_counter_head = atomic_counter == 0;
        bool reached_regular_head =
            atomic_counter ==
            ((last_atomic_counter_ + UINT32_MAX - buffer_size_samples_) % UINT32_MAX) + 1;
        bool has_enough_consecutive_samples = streak_ >= buffer_size_samples_;
        if (reached_zero_counter_head || (reached_regular_head && has_enough_consecutive_samples)) {
            buffer_.pop_back();
            num_samples_read--;
            // At this point the samples in <buffer_> are guaranteed to be free
            // of corruption from data race. Here's the proof.
            // Case 1: reached_zero_counter_head = true. The writer has not
            // started overwriting any samples so all samples that have been
            // read so far are valid.
            // Case 2: reached_regular_head = true. E.g. suppose
            // last_atomic_counter_ = 15 and buffer_size_samples_ = 10, now
            // buffer_ would be [7, 8, 9, 10, 11, 12, 13, 14, 15]. The fact that we just
            // saw a counter value of 6 means the writer has not start
            // overwriting samples 7-15 yet. Therefore these samples are all
            // valid.
            break;
        }
        if (atomic_counter != (last_atomic_counter_ % UINT32_MAX) + 1) {
            streak_ = 0;
        }
        streak_++;
        last_atomic_counter_ = atomic_counter;
        index_ = (index_ + 1) % buffer_size_samples_;
        TruncateBuffer();
        if (num_samples_read > kMaxReadRounds * buffer_size_samples_) {
            buffer_.clear();
            return kErrorHeadOfBufferNotFound;
        }
    }
    num_samples_read = std::min(num_samples_read, buffer_size_samples_ - 1);
    if (num_samples_skipped != nullptr) {
        *num_samples_skipped =
            last_atomic_counter_ - last_atomic_counter_before_read - num_samples_read;
    }
    return num_samples_read;
}

const sensors_event_t DirectChannelBufferReader::ReadOneSample(int index) {
    sensors_event_t event;
    // reserved0 is the atomic counter and should be read first.
    event.reserved0 = direct_channel_buffer_[index].reserved0;
    event.version = direct_channel_buffer_[index].version;
    event.sensor = direct_channel_buffer_[index].sensor;
    event.type = direct_channel_buffer_[index].type;
    event.timestamp = direct_channel_buffer_[index].timestamp;
    event.u64.data[0] = direct_channel_buffer_[index].u64.data[0];
    event.u64.data[1] = direct_channel_buffer_[index].u64.data[1];
    event.u64.data[2] = direct_channel_buffer_[index].u64.data[2];
    event.u64.data[3] = direct_channel_buffer_[index].u64.data[3];
    event.u64.data[4] = direct_channel_buffer_[index].u64.data[4];
    event.u64.data[5] = direct_channel_buffer_[index].u64.data[5];
    event.u64.data[6] = direct_channel_buffer_[index].u64.data[6];
    event.u64.data[7] = direct_channel_buffer_[index].u64.data[7];
    return event;
}

void DirectChannelBufferReader::TruncateBuffer() {
    while (buffer_.size() > buffer_size_samples_ - 1) {
        buffer_.pop_front();
    }
}
