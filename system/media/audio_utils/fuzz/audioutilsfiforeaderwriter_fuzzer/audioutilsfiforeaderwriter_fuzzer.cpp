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

#include <vector>
#include <audio_utils/sndfile.h>
#include <audio_utils/fifo.h>
#include <fuzzer/FuzzedDataProvider.h>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size){
    if (data == nullptr || size < 6) {
        return 0;
    }

    FuzzedDataProvider provider(data, size);

    const bool throttleRead = provider.ConsumeBool();
    const size_t frameSize = provider.ConsumeIntegralInRange<size_t>(1, 128);

    size_t maxNumberFrames = provider.remaining_bytes() / frameSize;
    if (maxNumberFrames >= ((uint32_t) INT32_MAX) / frameSize) {
        maxNumberFrames = (((uint32_t) INT32_MAX) / frameSize) - 1;
    }

    if (maxNumberFrames == 0) {
        // FrameSize is larger than bytes passed in
        return 0;
    }

    // Get buffer size
    size_t bufferMaxFrameCount = provider.ConsumeIntegralInRange<size_t>(1, maxNumberFrames);
    uint8_t* fifoBuffer = new uint8_t[bufferMaxFrameCount * frameSize];


    // Audio_utils_fifo to be shared across reader/writer
    audio_utils_fifo fifo(bufferMaxFrameCount, frameSize, fifoBuffer, throttleRead);
    audio_utils_fifo_writer writer(fifo);
    audio_utils_fifo_reader reader(fifo);

    bool error = false;
    int32_t framesWritten = 0;
    while (provider.remaining_bytes() > 4) {

        //TODO use bufferMaxFrameCount?
        int32_t loopMaxWrite = bufferMaxFrameCount - framesWritten;

        int32_t framesToWrite = provider.ConsumeIntegralInRange<int32_t>(1, loopMaxWrite);
        size_t maxFramesLeft = provider.remaining_bytes() / frameSize;

        if (framesToWrite > maxFramesLeft) {
            framesToWrite = maxFramesLeft;
        }

        if (framesToWrite == 0) {
            // No more data to process after consuming Integral
            break;
        }

        std::vector<uint8_t> bytesToWrite =
            provider.ConsumeBytes<uint8_t>(framesToWrite * frameSize);
        ssize_t actualFramesWritten = writer.write(&bytesToWrite[0], framesToWrite);

        // Verify actualFramesWritten
        if (actualFramesWritten != framesToWrite) {
            error = true;
            break;
        }

        // Add count of frames just written to framesWritten
        framesWritten += actualFramesWritten;

        // Init framesToRead to read all framesWritten.
        // If another integral can be read from provider then update
        int32_t framesToRead = framesWritten;
        if (provider.remaining_bytes() >= 4) {
            framesToRead = provider.ConsumeIntegralInRange<size_t>(1, framesToRead);
        }

        uint8_t * readBuffer = new uint8_t[framesToRead * frameSize];
        ssize_t actualFramesRead = reader.read(readBuffer, framesToRead);

        // Verify framesRead
        if (actualFramesRead != framesToRead) {
            error = true;
            break;
        }

        framesWritten -= framesToRead;

        delete[] readBuffer;
        readBuffer = NULL;
    }

    if (error) {
        abort();
    }

    delete[] fifoBuffer;
    fifoBuffer = NULL;

    return 0;
}