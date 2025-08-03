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

#pragma once

#include <hardware/sensors.h>

#include <algorithm>
#include <deque>

// A utility class that reads sensor samples from a direct channel buffer.
// Direct channel operates in a lockless manner and uses an atomic counter for
// synchronization. This class implements the counter based synchronization
// protocol and therefore guarantees data consistency. See
// https://developer.android.com/reference/android/hardware/SensorDirectChannel
// for more details on the atomic counter.
//
// Besides reading samples, the reader also supports keeping track of recently
// obtained samples.
//
// DirectChannelBufferReader is not thread safe. It's the caller's responsibility
// to serialize the calls, including the access to the returned sample
// container.
//
// Example usage:
//   DirectChannelBufferReader reader(buf, 100);
//
//   int num_samples = reader.Read();
//   const std::deque<sensors_event_t>& samples = reader.GetSampleContainer();
//   for (auto it = samples.end() - num_samples; it != samples.end(); it++) {
//     HandleNewSamples(*it);
//   }
//
//   int num_samples_skipped;
//   reader.Read(&num_samples_skipped);
//   if (num_samples_skipped > 0) {
//     ReportMissedSamples(num_samples_skipped);
//   }
//
//
// Another example:
//
//   DirectChannelBufferReader reader(buf, 100);
//
//   std::vector<sensors_event_t> Query(int start_time, int end_time) {
//     reader.Read();
//     std::vector<sensors_event_t> output;
//     for (auto& sample : reader_.GetSampleContainer()) {
//       if (sample.timestamp >= start_time && sample.timestamp < end_time) {
//         output.push_back(sample);
//       }
//     }
//     return output;
//   }

class DirectChannelBufferReader {
   public:
    static constexpr int kErrorHeadOfBufferNotFound = -1;

    // Constructor
    // direct_channel_buffer: Pointer to the shared buffer where sensor samples
    //                        are written into.
    // buffer_size_samples: The size of direct_channel_buffer in number of
    //                      samples.
    DirectChannelBufferReader(const sensors_event_t* direct_channel_buffer,
                              int buffer_size_samples);

    virtual ~DirectChannelBufferReader() {}

    // Attempts to read samples from the direct channel buffer. Returns
    // the number of samples read, or kErrorHeadOfBufferNotFound if the reader
    // can not find the write head e.g. due to corrupted data in the buffer.
    // The function is non-blocking and returns 0 if new samples are not available.
    // The caller should control its polling based on external factors like
    // events in a different subsystem (e.g. camera frame ready)
    // After the call completes, the caller can use GetSampleContainer() to
    // access the samples. Sometimes it may be possible for one or more samples
    // in the direct channel buffer to be overwritten by the writter before the
    // reader has a chance to read it, e.g. when the reader does not keep up
    // with the writer. The number of samples that were lost / skipped is
    // written to <num_samples_skipped>, if the argument is not null.
    int Read(int* num_samples_skipped = nullptr);

    // Returns the container that holds recent samples. New samples are appended
    // to the end of the container when Read() is called. Samples from previous
    // rounds of Read() are kept around in the container, except when the total
    // samples exceeds <buffer_size_samples> - 1, in which case older samples
    // would be truncated. The caller is free to remove samples from the
    // container, e.g. after the samples are consumed.
    //
    // Calls to the returned container must be synchronized with calls to this
    // instance of DirectChannelBufferReader.
    std::deque<sensors_event_t>& GetSampleContainer() { return buffer_; }

   protected:
    // For test only.
    virtual const sensors_event_t ReadOneSample(int index);

   private:
    // Truncates the head of <buffer_> until its size <= buffer_size_samples - 1.
    void TruncateBuffer();

    // Points to the direct channel buffer where the sensor writes samples into.
    const volatile sensors_event_t* direct_channel_buffer_;

    // The number of samples that <direct_channel_buffer_> is able to hold.
    const int buffer_size_samples_;

    // The atomic counter value of the last valid sample.
    int64_t last_atomic_counter_ = 0;

    // The index into <direct_channel_buffer_> that should be read next time.
    int index_ = 0;

    // The number of successive sensors_event_t reads with consecutive atomic
    // counters values.
    // E.g. 1           => streak_ = 1
    //      5 6 7       => streak_ = 3
    //      1 2 3 14    => streak_ = 1
    //      1 2 3 14 15 => streak_ = 2
    int streak_ = 0;

    // The buffer holding recent samples.
    std::deque<sensors_event_t> buffer_;
};
