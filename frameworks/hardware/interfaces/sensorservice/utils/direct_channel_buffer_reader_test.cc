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
#include <android-base/thread_annotations.h>
#include <gtest/gtest.h>
#include <stdlib.h>

#include <condition_variable>
#include <mutex>
#include <thread>

namespace {

// A derived class of DirectChannelBufferReader that allows blocking memory read
// for concurrency tests.
class TestableDirectChannelBufferReader : public DirectChannelBufferReader {
   public:
    TestableDirectChannelBufferReader(const sensors_event_t* direct_channel_buffer,
                                      int buffer_size_samples)
        : DirectChannelBufferReader(direct_channel_buffer, buffer_size_samples) {}

    const sensors_event_t ReadOneSample(int index) {
        {
            std::unique_lock lk(mutex_);
            reader_waiting_ = true;
            lk.unlock();
            cv_.notify_one();
        }
        {
            std::unique_lock lk(mutex_);
            cv_.wait(lk, [this] { return !should_block_reads_ || num_reads_unblocked_ > 0; });
            reader_waiting_ = false;
            auto return_value = DirectChannelBufferReader::ReadOneSample(index);
            num_reads_unblocked_--;
            lk.unlock();
            cv_.notify_one();
            return return_value;
        }
    }

    void BlockReads() {
        std::unique_lock lk(mutex_);
        should_block_reads_ = true;
        num_reads_unblocked_ = 0;
        lk.unlock();
        cv_.notify_one();
    }

    void UnblockReads() {
        std::unique_lock lk(mutex_);
        should_block_reads_ = false;
        lk.unlock();
        cv_.notify_one();
    }

    void UnblockAndWaitForReads(int num_reads) {
        {
            std::unique_lock lk(mutex_);
            CHECK_EQ(num_reads_unblocked_, 0);
            num_reads_unblocked_ = num_reads;
            lk.unlock();
            cv_.notify_one();
        }
        {
            std::unique_lock lk(mutex_);
            // Only proceed when reads are all done AND the reader is blocked again.
            // This way we ensure nothing is done on the reader thread (like sample
            // validation) when more samples are being written.
            cv_.wait(lk, [this] { return num_reads_unblocked_ == 0 && reader_waiting_; });
        }
    }

   private:
    std::mutex mutex_;
    std::condition_variable cv_;
    bool should_block_reads_ GUARDED_BY(mutex_) = false;
    bool reader_waiting_ GUARDED_BY(mutex_) = false;
    int num_reads_unblocked_ GUARDED_BY(mutex_) = 0;
};

class DirectChannelBufferReaderTest : public ::testing::Test {
   protected:
    DirectChannelBufferReaderTest() : buffer_{}, reader_(&buffer_[0], kBufferSize) {}

    void WriteOneSample() {
        WritePartialSample();
        FinishWritingSample();
    }

    void WritePartialSample() { buffer_[next_buffer_index_].timestamp = next_atomic_counter_; }

    void FinishWritingSample() {
        buffer_[next_buffer_index_].data[0] = next_atomic_counter_;
        buffer_[next_buffer_index_].reserved0 = next_atomic_counter_;
        next_buffer_index_ = (next_buffer_index_ + 1) % kBufferSize;
        next_atomic_counter_ = (next_atomic_counter_ % UINT32_MAX) + 1;
    }

    void WriteHalfSample() {
        if (buffer_[next_buffer_index_].timestamp != next_atomic_counter_) {
            WritePartialSample();
        } else {
            FinishWritingSample();
        }
    }

    void ValidateReaderSamples() {
        auto& samples = reader_.GetSampleContainer();
        for (int i = 0; i < samples.size(); i++) {
            int64_t expected_value =
                ((next_atomic_counter_ - samples.size() + i - 1 + UINT32_MAX) % UINT32_MAX) + 1;
            EXPECT_EQ(static_cast<uint32_t>(samples[i].reserved0), expected_value) << " i = " << i;
            EXPECT_EQ(samples[i].timestamp, expected_value);
            EXPECT_EQ(samples[i].data[0], expected_value);
        }
    }

    void StartReaderThread() {
        reader_.BlockReads();
        reader_thread_ = std::make_unique<std::thread>([this] {
            while (keep_reading_) {
                reader_.Read();
                // At this point we want to validate the samples and check the values
                // against next_atomic_counter_. To prevent next_atomic_counter_ from
                // being modified by the writer thread, we make the writer thread
                // blocked inside UnblockAndWaitForReads() until the validation is done
                // and reader_.Read() is called again.
                ValidateReaderSamples();
            }
        });
    }

    void StopAndJoinReaderThread() {
        reader_.UnblockReads();
        keep_reading_ = false;
        reader_thread_->join();
    }

    static constexpr int kBufferSize = 20;
    std::array<sensors_event_t, kBufferSize> buffer_;
    TestableDirectChannelBufferReader reader_;

    int next_buffer_index_ = 0;
    int64_t next_atomic_counter_ = 1;

    std::unique_ptr<std::thread> reader_thread_;
    bool keep_reading_ = true;
};

TEST_F(DirectChannelBufferReaderTest, ReturnNoDataForEmptyBuffer) {
    EXPECT_EQ(reader_.Read(), 0);
    EXPECT_EQ(reader_.GetSampleContainer().size(), 0);
}

TEST_F(DirectChannelBufferReaderTest, ReturnOneSample) {
    WriteOneSample();
    EXPECT_EQ(reader_.Read(), 1);
    EXPECT_EQ(reader_.GetSampleContainer().size(), 1);
}

TEST_F(DirectChannelBufferReaderTest, ReturnSamplesWithFullBuffer) {
    for (int i = 0; i < kBufferSize; i++) {
        WriteOneSample();
    }
    EXPECT_EQ(reader_.Read(), kBufferSize - 1);
    EXPECT_EQ(reader_.GetSampleContainer().size(), kBufferSize - 1);
    ValidateReaderSamples();
}

TEST_F(DirectChannelBufferReaderTest, ReturnSamplesWithInterleavedWriteRead) {
    WriteOneSample();
    EXPECT_EQ(reader_.Read(), 1);
    WriteOneSample();
    WriteOneSample();
    EXPECT_EQ(reader_.Read(), 2);
    EXPECT_EQ(reader_.GetSampleContainer().size(), 3);
    ValidateReaderSamples();
}

TEST_F(DirectChannelBufferReaderTest, ReturnNothingAfterPartialWrite) {
    WriteOneSample();
    EXPECT_EQ(reader_.Read(), 1);
    WritePartialSample();
    EXPECT_EQ(reader_.Read(), 0);
    FinishWritingSample();
    EXPECT_EQ(reader_.Read(), 1);
    EXPECT_EQ(reader_.GetSampleContainer().size(), 2);
    ValidateReaderSamples();
}

TEST_F(DirectChannelBufferReaderTest, DiscardPartiallyWrittenSample) {
    WriteOneSample();
    EXPECT_EQ(reader_.Read(), 1);
    for (int i = 0; i < kBufferSize; i++) {
        WriteOneSample();
    }
    // State of the buffer: 21 2 3 4 5 .... 20
    //                         ^
    //         Both read and write head point here

    WritePartialSample();
    // State of the buffer: 21 2 3 4 5 .... 20
    //                         ^
    //     Partially overwritten with sample 22
    // The next Read() should get sample 3-21. Sample 2 should be discarded.
    EXPECT_EQ(reader_.Read(), kBufferSize - 1);
    EXPECT_EQ(reader_.GetSampleContainer().front().timestamp, 3);
    EXPECT_EQ(reader_.GetSampleContainer().back().timestamp, 21);
}

TEST_F(DirectChannelBufferReaderTest, ReturnCorrectSamplesAfterWriterOverflow) {
    WriteOneSample();
    reader_.Read();
    for (int i = 0; i < kBufferSize + 5; i++) {
        WriteOneSample();
    }
    EXPECT_EQ(reader_.Read(), kBufferSize - 1);
    EXPECT_EQ(reader_.GetSampleContainer().size(), kBufferSize - 1);
    ValidateReaderSamples();
}

TEST_F(DirectChannelBufferReaderTest, ReturnNumOfSkippedSamples) {
    WriteOneSample();
    reader_.Read();
    for (int i = 0; i < kBufferSize + 5; i++) {
        WriteOneSample();
    }
    int num_samples_skipped = 0;
    reader_.Read(&num_samples_skipped);
    EXPECT_EQ(num_samples_skipped, 6);
}

TEST_F(DirectChannelBufferReaderTest, WrapAroundUINT32Max) {
    next_atomic_counter_ = UINT32_MAX - 3;
    for (int i = 0; i < kBufferSize; i++) {
        WriteOneSample();
    }
    EXPECT_EQ(reader_.Read(), kBufferSize - 1);
    EXPECT_EQ(reader_.GetSampleContainer().size(), kBufferSize - 1);
    ValidateReaderSamples();
}

TEST_F(DirectChannelBufferReaderTest, ConcurrentWriteReadSequence) {
    WriteOneSample();
    // Buffer:                  1 0 0 0 ...
    // Writer head:             ^

    reader_.Read();
    // Buffer:                  1 0 0 0 ...
    // Writer head:             ^
    // What reader sees so far: 1

    StartReaderThread();
    for (int i = 0; i < kBufferSize; i++) {
        WriteOneSample();
    }
    // Buffer:                  21 2 3 4 ...
    // Writer head:             ^
    // What reader sees so far: 1

    WriteHalfSample();
    // Buffer:                  21 <counter:2,content:22> 3 4 ...
    // Writer head:                          ^
    // What reader sees so far: 1

    reader_.UnblockAndWaitForReads(2);
    // Buffer:                  21 <counter:2,content:22> 3 4 ...
    // Writer head:                          ^
    // What reader sees so far: 1  2*                     3
    // (sample 2 is corrupted)

    WriteHalfSample();
    // Buffer:                  21 22 3 4 5 ...
    // Writer head:                ^
    // What reader sees so far: 1  2  3

    WriteOneSample();
    WriteOneSample();
    // Buffer:                  21 22 23 24 5 6 ...
    // Writer head:                      ^
    // What reader sees so far: 1  2  3

    WriteHalfSample();
    // Buffer:                  21 22 23 24 <counter:5,content:25> 6 ...
    // Writer head:                                   ^
    // What reader sees so far: 1  2  3

    StopAndJoinReaderThread();
    // Buffer:                  21 22 23 24 <counter:5,content:25> 6 ...
    // Writer head:                                   ^
    // What reader sees so far: 21 22 23 24 5*                     6 ...
    // (sample 5 is corrupted)
    //
    // The validation performed on the reader thread would ensure that sample 2
    // and 5 were not returned.
}

TEST_F(DirectChannelBufferReaderTest, GeneratedConcurrentWriteReadSequence) {
    constexpr int kNumRounds = 5000;
    constexpr int kMaxReadWritePerRound = kBufferSize + 5;
    StartReaderThread();
    // For deterministic results, use an arbitrary fixed seed for random number
    // generator.
    srand(12345);
    for (int i = 0; i < kNumRounds; i++) {
        bool write = rand() % 2 == 0;
        if (write) {
            // Multiply by 2 since each call only writes half a sample.
            int num_writes = rand() % (kMaxReadWritePerRound * 2);
            for (int j = 0; j < num_writes; j++) {
                WriteHalfSample();
            }
        } else {
            int num_reads = rand() % kMaxReadWritePerRound;
            reader_.UnblockAndWaitForReads(num_reads);
        }
    }
    StopAndJoinReaderThread();
}

}  // namespace
