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

#include <stddef.h>
#include <stdint.h>
#include <iostream>
#include <limits>
#include <thread>

#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <fmq/AidlMessageQueue.h>
#include <fmq/ConvertMQDescriptors.h>
#include <fmq/EventFlag.h>
#include <fmq/MessageQueue.h>

#include "fuzzer/FuzzedDataProvider.h"

using aidl::android::hardware::common::fmq::SynchronizedReadWrite;
using aidl::android::hardware::common::fmq::UnsynchronizedWrite;
using android::hardware::kSynchronizedReadWrite;
using android::hardware::kUnsynchronizedWrite;

typedef int32_t payload_t;

// The reader/writers will wait during blocking calls
static constexpr int kBlockingTimeoutNs = 100000;

/*
 * MessageQueueBase.h contains asserts when memory allocation fails. So we need
 * to set a reasonable limit if we want to avoid those asserts.
 */
static constexpr size_t kAlignment = 8;
static const size_t kPageSize = getpagesize();
static const size_t kMaxNumElements = kPageSize * 10 / sizeof(payload_t) - kAlignment + 1;
/*
 * limit the custom grantor case to one page of memory.
 * If we want to increase this, we need to make sure that all of grantors offset
 * plus extent are less than the size of the page aligned ashmem region that is
 * created
 */
static const size_t kMaxCustomGrantorMemoryBytes = kPageSize;

/*
 * The read counter can be found in the shared memory 16 bytes before the start
 * of the ring buffer.
 */
static constexpr int kReadCounterOffsetBytes = 16;
/*
 * The write counter can be found in the shared memory 8 bytes before the start
 * of the ring buffer.
 */
static constexpr int kWriteCounterOffsetBytes = 8;

static constexpr int kMaxNumSyncReaders = 1;
static constexpr int kMaxNumUnsyncReaders = 5;
static constexpr int kMaxDataPerReader = 1000;

typedef android::AidlMessageQueue<payload_t, SynchronizedReadWrite> AidlMessageQueueSync;
typedef android::AidlMessageQueue<payload_t, UnsynchronizedWrite> AidlMessageQueueUnsync;
typedef android::hardware::MessageQueue<payload_t, kSynchronizedReadWrite> MessageQueueSync;
typedef android::hardware::MessageQueue<payload_t, kUnsynchronizedWrite> MessageQueueUnsync;
typedef aidl::android::hardware::common::fmq::MQDescriptor<payload_t, SynchronizedReadWrite>
        AidlMQDescSync;
typedef aidl::android::hardware::common::fmq::MQDescriptor<payload_t, UnsynchronizedWrite>
        AidlMQDescUnsync;
typedef android::hardware::MQDescriptorSync<payload_t> MQDescSync;
typedef android::hardware::MQDescriptorUnsync<payload_t> MQDescUnsync;

// AIDL and HIDL have different ways of accessing the grantors
template <typename Desc>
uint64_t* getCounterPtr(payload_t* start, const Desc& desc, int grantorIndx);

uint64_t* createCounterPtr(payload_t* start, uint32_t offset, uint32_t data_offset) {
    // start is the address of the beginning of the FMQ data section in memory
    // offset is overall offset of the counter in the FMQ memory
    // data_offset is the overall offset of the data section in the FMQ memory
    // start - (data_offset) = beginning address of the FMQ memory
    return reinterpret_cast<uint64_t*>(reinterpret_cast<uint8_t*>(start) - data_offset + offset);
}

uint64_t* getCounterPtr(payload_t* start, const MQDescSync& desc, int grantorIndx) {
    uint32_t offset = desc.grantors()[grantorIndx].offset;
    uint32_t data_offset = desc.grantors()[android::hardware::details::DATAPTRPOS].offset;
    return createCounterPtr(start, offset, data_offset);
}

uint64_t* getCounterPtr(payload_t* start, const MQDescUnsync& desc, int grantorIndx) {
    uint32_t offset = desc.grantors()[grantorIndx].offset;
    uint32_t data_offset = desc.grantors()[android::hardware::details::DATAPTRPOS].offset;
    return createCounterPtr(start, offset, data_offset);
}

uint64_t* getCounterPtr(payload_t* start, const AidlMQDescSync& desc, int grantorIndx) {
    uint32_t offset = desc.grantors[grantorIndx].offset;
    uint32_t data_offset = desc.grantors[android::hardware::details::DATAPTRPOS].offset;
    return createCounterPtr(start, offset, data_offset);
}

uint64_t* getCounterPtr(payload_t* start, const AidlMQDescUnsync& desc, int grantorIndx) {
    uint32_t offset = desc.grantors[grantorIndx].offset;
    uint32_t data_offset = desc.grantors[android::hardware::details::DATAPTRPOS].offset;
    return createCounterPtr(start, offset, data_offset);
}

template <typename Queue, typename Desc>
void reader(const Desc& desc, std::vector<uint8_t> readerData, bool userFd) {
    Queue readMq(desc);
    if (!readMq.isValid()) {
        LOG(ERROR) << "read mq invalid";
        return;
    }
    FuzzedDataProvider fdp(&readerData[0], readerData.size());
    payload_t* ring = reinterpret_cast<payload_t*>(readMq.getRingBufferPtr());
    while (fdp.remaining_bytes()) {
        typename Queue::MemTransaction tx;
        size_t numElements = fdp.ConsumeIntegralInRange<size_t>(0, kMaxNumElements);
        if (!readMq.beginRead(numElements, &tx)) {
            continue;
        }
        const auto& region = tx.getFirstRegion();
        payload_t* firstStart = region.getAddress();

        // the ring buffer is only next to the read/write counters when there is
        // no user supplied fd
        if (!userFd) {
            if (fdp.ConsumeIntegral<uint8_t>() == 1) {
                uint64_t* writeCounter =
                        getCounterPtr(ring, desc, android::hardware::details::WRITEPTRPOS);
                *writeCounter = fdp.ConsumeIntegral<uint64_t>();
            }
        }
        (void)std::to_string(*firstStart);

        readMq.commitRead(numElements);
    }
}

template <typename Queue, typename Desc>
void readerBlocking(const Desc& desc, std::vector<uint8_t>& readerData,
                    std::atomic<size_t>& readersNotFinished,
                    std::atomic<size_t>& writersNotFinished) {
    android::base::ScopeGuard guard([&readersNotFinished]() { readersNotFinished--; });
    Queue readMq(desc);
    if (!readMq.isValid()) {
        LOG(ERROR) << "read mq invalid";
        return;
    }
    FuzzedDataProvider fdp(&readerData[0], readerData.size());
    do {
        size_t count = fdp.remaining_bytes()
                               ? fdp.ConsumeIntegralInRange<size_t>(0, readMq.getQuantumCount() + 1)
                               : 1;
        std::vector<payload_t> data;
        data.resize(count);
        readMq.readBlocking(data.data(), count, kBlockingTimeoutNs);
    } while (fdp.remaining_bytes() > sizeof(size_t) && writersNotFinished > 0);
}

// Can't use blocking calls with Unsync queues(there is a static_assert)
template <>
void readerBlocking<AidlMessageQueueUnsync, AidlMQDescUnsync>(const AidlMQDescUnsync&,
                                                              std::vector<uint8_t>&,
                                                              std::atomic<size_t>&,
                                                              std::atomic<size_t>&) {}
template <>
void readerBlocking<MessageQueueUnsync, MQDescUnsync>(const MQDescUnsync&, std::vector<uint8_t>&,
                                                      std::atomic<size_t>&, std::atomic<size_t>&) {}

template <typename Queue, typename Desc>
void writer(const Desc& desc, Queue& writeMq, FuzzedDataProvider& fdp, bool userFd) {
    payload_t* ring = reinterpret_cast<payload_t*>(writeMq.getRingBufferPtr());
    while (fdp.remaining_bytes()) {
        typename Queue::MemTransaction tx;
        size_t numElements = 1;
        if (!writeMq.beginWrite(numElements, &tx)) {
            // need to consume something so we don't end up looping forever
            fdp.ConsumeIntegral<uint8_t>();
            continue;
        }

        const auto& region = tx.getFirstRegion();
        payload_t* firstStart = region.getAddress();
        // the ring buffer is only next to the read/write counters when there is
        // no user supplied fd
        if (!userFd) {
            if (fdp.ConsumeIntegral<uint8_t>() == 1) {
                uint64_t* readCounter =
                        getCounterPtr(ring, desc, android::hardware::details::READPTRPOS);
                *readCounter = fdp.ConsumeIntegral<uint64_t>();
            }
        }
        *firstStart = fdp.ConsumeIntegral<uint8_t>();

        writeMq.commitWrite(numElements);
    }
}

template <typename Queue>
void writerBlocking(Queue& writeMq, FuzzedDataProvider& fdp,
                    std::atomic<size_t>& writersNotFinished,
                    std::atomic<size_t>& readersNotFinished) {
    android::base::ScopeGuard guard([&writersNotFinished]() { writersNotFinished--; });
    while (fdp.remaining_bytes() > sizeof(size_t) && readersNotFinished > 0) {
        size_t count = fdp.ConsumeIntegralInRange<size_t>(0, writeMq.getQuantumCount() + 1);
        std::vector<payload_t> data;
        for (int i = 0; i < count; i++) {
            data.push_back(fdp.ConsumeIntegral<uint8_t>());
        }
        writeMq.writeBlocking(data.data(), count, kBlockingTimeoutNs);
    }
}

// Can't use blocking calls with Unsync queues(there is a static_assert)
template <>
void writerBlocking<AidlMessageQueueUnsync>(AidlMessageQueueUnsync&, FuzzedDataProvider&,
                                            std::atomic<size_t>&, std::atomic<size_t>&) {}
template <>
void writerBlocking<MessageQueueUnsync>(MessageQueueUnsync&, FuzzedDataProvider&,
                                        std::atomic<size_t>&, std::atomic<size_t>&) {}

template <typename Queue, typename Desc>
inline std::optional<Desc> getDesc(std::unique_ptr<Queue>& queue, FuzzedDataProvider& fdp);

template <typename Queue, typename Desc>
inline std::optional<Desc> getAidlDesc(std::unique_ptr<Queue>& queue, FuzzedDataProvider& fdp) {
    if (queue) {
        // get the existing descriptor from the queue
        Desc desc = queue->dupeDesc();
        if (desc.handle.fds[0].get() == -1) {
            return std::nullopt;
        } else {
            return std::make_optional(std::move(desc));
        }
    } else {
        // create a custom descriptor
        std::vector<aidl::android::hardware::common::fmq::GrantorDescriptor> grantors;
        size_t numGrantors = fdp.ConsumeIntegralInRange<size_t>(0, 4);
        for (int i = 0; i < numGrantors; i++) {
            grantors.push_back({fdp.ConsumeIntegralInRange<int32_t>(0, 2) /* fdIndex */,
                                fdp.ConsumeIntegralInRange<int32_t>(
                                        0, kMaxCustomGrantorMemoryBytes) /* offset */,
                                fdp.ConsumeIntegralInRange<int64_t>(
                                        0, kMaxCustomGrantorMemoryBytes) /* extent */});
            // ashmem region is kPageSize and we need to make sure all of the
            // pointers and data region fit inside
            if (grantors.back().offset + grantors.back().extent > kPageSize) return std::nullopt;
        }

        android::base::unique_fd fd(
                ashmem_create_region("AidlCustomGrantors", kMaxCustomGrantorMemoryBytes));
        ashmem_set_prot_region(fd, PROT_READ | PROT_WRITE);
        aidl::android::hardware::common::NativeHandle handle;
        handle.fds.emplace_back(fd.get());

        return std::make_optional<Desc>(
                {grantors, std::move(handle), sizeof(payload_t), fdp.ConsumeBool()});
    }
}

template <>
inline std::optional<AidlMQDescSync> getDesc(std::unique_ptr<AidlMessageQueueSync>& queue,
                                             FuzzedDataProvider& fdp) {
    return getAidlDesc<AidlMessageQueueSync, AidlMQDescSync>(queue, fdp);
}

template <>
inline std::optional<AidlMQDescUnsync> getDesc(std::unique_ptr<AidlMessageQueueUnsync>& queue,
                                               FuzzedDataProvider& fdp) {
    return getAidlDesc<AidlMessageQueueUnsync, AidlMQDescUnsync>(queue, fdp);
}

template <typename Queue, typename Desc>
inline std::optional<Desc> getHidlDesc(std::unique_ptr<Queue>& queue, FuzzedDataProvider& fdp) {
    if (queue) {
        auto desc = queue->getDesc();
        if (!desc->isHandleValid()) {
            return std::nullopt;
        } else {
            return std::make_optional(std::move(*desc));
        }
    } else {
        // create a custom descriptor
        std::vector<android::hardware::GrantorDescriptor> grantors;
        size_t numGrantors = fdp.ConsumeIntegralInRange<size_t>(0, 4);
        for (int i = 0; i < numGrantors; i++) {
            grantors.push_back({fdp.ConsumeIntegral<uint32_t>() /* flags */,
                                fdp.ConsumeIntegralInRange<uint32_t>(0, 2) /* fdIndex */,
                                fdp.ConsumeIntegralInRange<uint32_t>(
                                        0, kMaxCustomGrantorMemoryBytes) /* offset */,
                                fdp.ConsumeIntegralInRange<uint64_t>(
                                        0, kMaxCustomGrantorMemoryBytes) /* extent */});
            // ashmem region is kPageSize and we need to make sure all of the
            // pointers and data region fit inside
            if (grantors.back().offset + grantors.back().extent > kPageSize) return std::nullopt;
        }

        native_handle_t* handle = native_handle_create(1, 0);
        int ashmemFd = ashmem_create_region("HidlCustomGrantors", kMaxCustomGrantorMemoryBytes);
        ashmem_set_prot_region(ashmemFd, PROT_READ | PROT_WRITE);
        handle->data[0] = ashmemFd;

        return std::make_optional<Desc>(grantors, handle, sizeof(payload_t));
    }
}

template <>
inline std::optional<MQDescSync> getDesc(std::unique_ptr<MessageQueueSync>& queue,
                                         FuzzedDataProvider& fdp) {
    return getHidlDesc<MessageQueueSync, MQDescSync>(queue, fdp);
}

template <>
inline std::optional<MQDescUnsync> getDesc(std::unique_ptr<MessageQueueUnsync>& queue,
                                           FuzzedDataProvider& fdp) {
    return getHidlDesc<MessageQueueUnsync, MQDescUnsync>(queue, fdp);
}

template <typename Queue, typename Desc>
void fuzzWithReaders(std::vector<uint8_t>& writerData,
                     std::vector<std::vector<uint8_t>>& readerData, bool blocking) {
    FuzzedDataProvider fdp(&writerData[0], writerData.size());
    bool evFlag = blocking || fdp.ConsumeBool();
    size_t numElements = fdp.ConsumeIntegralInRange<size_t>(1, kMaxNumElements);
    size_t bufferSize = numElements * sizeof(payload_t);
    bool userFd = fdp.ConsumeBool();
    bool manualGrantors = fdp.ConsumeBool();
    std::unique_ptr<Queue> writeMq = nullptr;
    if (manualGrantors) {
        std::optional<Desc> customDesc(getDesc<Queue, Desc>(writeMq, fdp));
        if (customDesc) {
            writeMq = std::make_unique<Queue>(*customDesc);
        }
    } else {
        android::base::unique_fd dataFd;
        if (userFd) {
            // run test with our own data region
            dataFd.reset(::ashmem_create_region("CustomData", bufferSize));
        }
        writeMq = std::make_unique<Queue>(numElements, evFlag, std::move(dataFd), bufferSize);
    }

    if (writeMq == nullptr || !writeMq->isValid()) {
        return;
    }
    // get optional desc
    const std::optional<Desc> desc(std::move(getDesc<Queue, Desc>(writeMq, fdp)));
    CHECK(desc != std::nullopt);

    std::atomic<size_t> readersNotFinished = readerData.size();
    std::atomic<size_t> writersNotFinished = 1;
    std::vector<std::thread> readers;
    for (int i = 0; i < readerData.size(); i++) {
        if (blocking) {
            readers.emplace_back(readerBlocking<Queue, Desc>, std::ref(*desc),
                                 std::ref(readerData[i]), std::ref(readersNotFinished),
                                 std::ref(writersNotFinished));
        } else {
            readers.emplace_back(reader<Queue, Desc>, std::ref(*desc), std::ref(readerData[i]),
                                 userFd);
        }
    }

    if (blocking) {
        writerBlocking<Queue>(*writeMq, fdp, writersNotFinished, readersNotFinished);
    } else {
        writer<Queue>(*desc, *writeMq, fdp, userFd);
    }

    for (auto& reader : readers) {
        reader.join();
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    if (size < 1 || size > 50000) {
        return 0;
    }
    FuzzedDataProvider fdp(data, size);

    bool fuzzSync = fdp.ConsumeBool();
    std::vector<std::vector<uint8_t>> readerData;
    uint8_t numReaders = fuzzSync ? fdp.ConsumeIntegralInRange<uint8_t>(0, kMaxNumSyncReaders)
                                  : fdp.ConsumeIntegralInRange<uint8_t>(0, kMaxNumUnsyncReaders);
    for (int i = 0; i < numReaders; i++) {
        readerData.emplace_back(fdp.ConsumeBytes<uint8_t>(kMaxDataPerReader));
    }
    bool fuzzBlocking = fdp.ConsumeBool();
    std::vector<uint8_t> writerData = fdp.ConsumeRemainingBytes<uint8_t>();
    if (fuzzSync) {
        fuzzWithReaders<MessageQueueSync, MQDescSync>(writerData, readerData, fuzzBlocking);
        fuzzWithReaders<AidlMessageQueueSync, AidlMQDescSync>(writerData, readerData, fuzzBlocking);
    } else {
        fuzzWithReaders<MessageQueueUnsync, MQDescUnsync>(writerData, readerData, false);
        fuzzWithReaders<AidlMessageQueueUnsync, AidlMQDescUnsync>(writerData, readerData, false);
    }

    return 0;
}
