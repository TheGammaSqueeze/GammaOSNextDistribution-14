#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <CommandBufferStagingStream.h>
#include <string.h>

#include <condition_variable>
#include <mutex>
#include <string_view>
#include <thread>

namespace gfxstream {
namespace vk {

using ::testing::A;
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::Ge;
using ::testing::InSequence;
using ::testing::IsNull;
using ::testing::MockFunction;
using ::testing::NotNull;
using ::testing::Return;

static constexpr size_t kTestBufferSize = 1048576;

// tests allocBuffer can successfully allocate a buffer of given size
TEST(CommandBufferStagingStreamTest, AllocateBufferTestUnderMinSize) {
    CommandBufferStagingStream stream;
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());
}

// test reallocate buffer remembers previously committed buffers
TEST(CommandBufferStagingStreamTest, ReallocateBuffer) {
    CommandBufferStagingStream stream;
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // write some data
    const std::string_view commandData{"some command"};
    const size_t dataSize = commandData.size();
    memcpy(buffer, commandData.data(), dataSize);

    // commit data
    stream.commitBuffer(dataSize);

    // this will result in a reallocation
    buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // get stream data
    uint8_t* outBuf = nullptr;
    size_t outSize = 0;
    stream.getWritten(&outBuf, &outSize);

    std::string_view actualData{reinterpret_cast<char*>(outBuf), outSize};
    EXPECT_THAT(actualData, Eq(commandData));
}

// tests commitBuffer tracks the portion of the buffer to be committed
TEST(CommandBufferStagingStreamTest, CommitBuffer) {
    CommandBufferStagingStream stream;
    // allocate buffer
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));

    // write some arbitrary data
    const std::string_view commandData{"some command"};
    const size_t dataSize = commandData.size();
    memcpy(buffer, commandData.data(), dataSize);

    // commit
    stream.commitBuffer(dataSize);

    // writeBuffer should have the committed portion in CommandBufferStagingStream
    uint8_t* outBuf = nullptr;
    size_t outSize = 0;
    stream.getWritten(&outBuf, &outSize);

    std::string_view actualData{reinterpret_cast<char*>(outBuf), outSize};
    EXPECT_THAT(actualData, Eq(commandData));
}

// tests reset api
TEST(CommandBufferStagingStreamTest, Reset) {
    CommandBufferStagingStream stream;
    // allocate buffer
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));

    // write some arbitrary data
    const std::string_view commandData{"some command"};
    const size_t dataSize = commandData.size();
    memcpy(buffer, commandData.data(), dataSize);

    // commit
    stream.commitBuffer(dataSize);

    // reset
    stream.reset();

    size_t outSize;
    unsigned char* outBuf;
    // write buffer
    stream.getWritten(&outBuf, &outSize);

    // outSize should be 0 after reset
    EXPECT_EQ(outSize, 0) << "no data should be available for a write after a reset";
}

// tests that multiple alloc calls do not result in reallocations
TEST(CommandBufferStagingStreamTest, MultipleAllocationCalls) {
    CommandBufferStagingStream stream;
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // another call to allocBuffer should not result in reallocations
    uint8_t* anotherBuffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(anotherBuffer, Eq(buffer));
}

// this test verifies that allocBuffer doesn't cause reallcation if buffer has available space
TEST(CommandBufferStagingStream, NoReallocationIfBufferIsNotFull) {
    CommandBufferStagingStream stream;
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // commit portion of buffer
    const size_t writeSize = 10;
    stream.commitBuffer(writeSize);

    uint8_t* writePtr = static_cast<uint8_t*>(stream.allocBuffer(writeSize));
    EXPECT_THAT(writePtr, buffer + writeSize);
}

// tests that data written prior to reallocation is still intact
TEST(CommandBufferStagingStreamTest, ReallocationBoundary) {
    CommandBufferStagingStream stream;
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // split data into two batches
    const std::string firstBatchData(kTestBufferSize, 'a');

    // copy first batch of data into stream
    memcpy(buffer, firstBatchData.data(), kTestBufferSize);

    // commit first batch of data
    stream.commitBuffer(firstBatchData.size());

    // size of first batch of data brings stream buffer to capacity; this will result in a
    // reallocation
    buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // data written before reallocation should be intact
    unsigned char* outBuf = nullptr;
    size_t outSize = 0;
    stream.getWritten(&outBuf, &outSize);

    const std::string_view expectedData{firstBatchData};
    std::string_view actualData{reinterpret_cast<char*>(outBuf), outSize};
    EXPECT_THAT(actualData, Eq(expectedData));
}

// this test is a death test for unsupported apis
TEST(CommandBufferStagingStreamDeathTest, UnsupportedAPIs) {
    CommandBufferStagingStream stream;

    EXPECT_DEATH(
        {
            void* buffer = nullptr;
            size_t size = 0;
            stream.readFully(buffer, size);
        },
        "")
        << "readFully should not be supported";

    EXPECT_DEATH(
        {
            void* buffer = nullptr;
            size_t size = 0;
            stream.read(buffer, &size);
        },
        "")
        << "read should not be supported";

    EXPECT_DEATH(
        {
            void* buffer = nullptr;
            size_t size = 0;
            stream.writeFully(buffer, size);
        },
        "")
        << "writeFully should not be supported";

    EXPECT_DEATH(
        {
            void* buffer = nullptr;
            size_t size = 0;
            size_t len = 0;
            stream.commitBufferAndReadFully(size, buffer, len);
        },
        "")
        << "commitBufferAndReadFully should not be supported";
}

using MockAlloc = MockFunction<CommandBufferStagingStream::Memory(size_t)>;
using MockFree = MockFunction<void(const CommandBufferStagingStream::Memory&)>;
// default empty implementation of free
static std::function<void(const CommandBufferStagingStream::Memory&)> EmptyFree =
    [](const CommandBufferStagingStream::Memory&) {};
// CommandBufferStagingStreamCustomAllocationTest tests behavior of CommandBufferStagingStream
// when initialized with custom allocator/free function.
// These tests test the same outcome as CommandBufferStagingStreamTest tests

// tests allocBuffer can successfully allocate a buffer of given size
TEST(CommandBufferStagingStreamCustomAllocationTest, AllocateBufferTest) {
    // memory source
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);

    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    MockAlloc allocFn;

    // alloc function should be called once
    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

    // free function should be called once
    MockFree freeFn;
    EXPECT_CALL(freeFn, Call(Eq(memory))).Times(1);

    // scope: CommandBufferStagingStream_Creation
    {
        auto allocStdFn = allocFn.AsStdFunction();
        auto freeStdFn = freeFn.AsStdFunction();
        CommandBufferStagingStream stream(allocStdFn, freeStdFn);
        uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
        EXPECT_THAT(buffer, NotNull());
    }
}

// test allocBuffer returns nullptr if custom allocation fails
TEST(CommandBufferStagingStreamCustomAllocationTest, AllocateBufferFailure) {
    // memory source for initial allocation
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);

    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = nullptr,                  // to test alloc call failing
    };

    MockAlloc allocFn;

    // alloc function should be called once
    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

    // free function should not be called if allocation fails
    MockFree freeFn;
    EXPECT_CALL(freeFn, Call).Times(0);

    // scope: CommandBufferStagingStream_Creation
    {
        auto allocStdFn = allocFn.AsStdFunction();
        auto freeStdFn = freeFn.AsStdFunction();
        CommandBufferStagingStream stream(allocStdFn, freeStdFn);
        void* buffer = stream.allocBuffer(kTestBufferSize);
        EXPECT_THAT(buffer, IsNull());
    }
}

TEST(CommandBufferStagingStreamCustomAllocationTest, DeviceMemoryPointerIsPassedDuringFree) {
    // memory source
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);

    // device memory for test purposes. The test just needs a pointer
    uint64_t deviceMem = 0;
    VkDeviceMemory deviceMemPtr = (VkDeviceMemory)(&deviceMem);

    CommandBufferStagingStream::Memory memory{.deviceMemory = deviceMemPtr,
                                              .ptr = memorySrc.data()};

    MockAlloc allocFn;

    // alloc function should be called once
    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

    // free function should be called once
    MockFree freeFn;
    EXPECT_CALL(freeFn, Call(Eq(memory))).Times(1);

    // scope: CommandBufferStagingStream_Creation
    {
        auto allocStdFn = allocFn.AsStdFunction();
        auto freeStdFn = freeFn.AsStdFunction();
        CommandBufferStagingStream stream(allocStdFn, freeStdFn);
        uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
        EXPECT_THAT(buffer, NotNull());
    }
}

// test verifies that there are no crashes if alloc/free function reference becomes null
TEST(CommandBufferStagingStreamCustomAllocationTest, AllocFreeInvalidReference) {
    MockAlloc allocFn;
    // alloc shouldn't be called if reference is invalidated
    EXPECT_CALL(allocFn, Call).Times(0);

    MockFree freeFn;
    // free shouldn't be called if reference is invalidated
    EXPECT_CALL(freeFn, Call).Times(0);

    auto allocStdFn = allocFn.AsStdFunction();
    auto freeStdFn = freeFn.AsStdFunction();
    // scope: CommandBufferStagingStream_Creation
    {
        CommandBufferStagingStream stream(allocStdFn, freeStdFn);
        // invalidate alloc/free functions
        allocStdFn = nullptr;
        freeStdFn = nullptr;
        stream.allocBuffer(kTestBufferSize);
        uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
        EXPECT_THAT(buffer, IsNull());
    }
}

// test reallocate buffer remembers previously committed buffers
TEST(CommandBufferStagingStreamCustomAllocationTest, ReallocateBuffer) {
    // memory source for initial allocation
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);
    // memory source after reallocation
    std::vector<uint8_t> reallocatedMemorySrc(kTestBufferSize * 3);

    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    CommandBufferStagingStream::Memory reallocatedMemory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = reallocatedMemorySrc.data()};

    MockAlloc allocFn;

    // alloc function should be called twice
    {
        InSequence seq;

        // expect initial allocation call with allocation size == kTestBufferSize;
        EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

        // expect reallocation call with allocation size > kTestBufferSize.
        EXPECT_CALL(allocFn, Call(testing::Ge(kTestBufferSize)))
            .Times(1)
            .WillRepeatedly(Return(reallocatedMemory));
    }

    MockFree freeFn;
    {
        InSequence seq;
        // free function should be called when reallocation happens
        EXPECT_CALL(freeFn, Call(Eq(memory))).Times(1);
        // free function should be called when stream goes out of scope
        EXPECT_CALL(freeFn, Call(Eq(reallocatedMemory))).Times(1);
    }

    // scope: CommandBufferStagingStream_Creation
    {
        auto allocStdFn = allocFn.AsStdFunction();
        auto freeStdFn = freeFn.AsStdFunction();
        CommandBufferStagingStream stream(allocStdFn, freeStdFn);
        uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
        EXPECT_THAT(buffer, NotNull());

        // write some data
        const std::string_view commandData{"some command"};
        const size_t dataSize = commandData.size();
        memcpy(buffer, commandData.data(), dataSize);

        // commit data
        stream.commitBuffer(dataSize);

        // this will result in a reallocation
        buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
        EXPECT_THAT(buffer, NotNull());

        // get stream data
        unsigned char* outBuf = nullptr;
        size_t outSize = 0;
        stream.getWritten(&outBuf, &outSize);

        std::string_view actualData{reinterpret_cast<char*>(outBuf), outSize};
        EXPECT_THAT(actualData, Eq(commandData));
    }
}

// tests commitBuffer tracks the portion of the buffer to be committed
TEST(CommandBufferStagingStreamCustomAllocationTest, CommitBuffer) {
    // memory source
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);

    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    MockAlloc allocFn;

    // alloc function should be called once
    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));
    auto allocStdFn = allocFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, EmptyFree);
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // write some arbitrary data
    const std::string_view commandData{"some command"};
    const size_t dataSize = commandData.size();
    memcpy(buffer, commandData.data(), dataSize);

    // commit
    stream.commitBuffer(dataSize);

    // writeBuffer should have the committed portion in CommandBufferStagingStream
    uint8_t* outBuf = nullptr;
    size_t outSize = 0;
    stream.getWritten(&outBuf, &outSize);

    std::string_view actualData{reinterpret_cast<char*>(outBuf), outSize};
    EXPECT_THAT(actualData, Eq(commandData));
}

// tests reset api
TEST(CommandBufferStagingStreamCustomAllocationTest, Reset) {
    // memory source
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);

    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    MockAlloc allocFn;

    // alloc function should be called once
    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));
    auto allocStdFn = allocFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, EmptyFree);
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));

    // write some arbitrary data
    const std::string_view commandData{"some command"};
    const size_t dataSize = commandData.size();
    memcpy(buffer, commandData.data(), dataSize);

    // commit
    stream.commitBuffer(dataSize);

    // reset
    stream.reset();

    size_t outSize;
    unsigned char* outBuf;
    // write buffer
    stream.getWritten(&outBuf, &outSize);

    // outSize should be 0 after reset
    EXPECT_EQ(outSize, 0) << "no data should be available for a write after a reset";
}

// tests that multiple alloc calls do not result in reallocations
TEST(CommandBufferStagingStreamCustomAllocationTest, MultipleAllocationCalls) {
    // memory source
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);

    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    MockAlloc allocFn;

    // alloc function should be called once, no reallocation
    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

    auto allocStdFn = allocFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, EmptyFree);
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // another call to allocBuffer should not result in reallocations
    uint8_t* anotherBuffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(anotherBuffer, Eq(buffer));
}

// tests that data written prior to reallocation is still intact
TEST(CommandBufferStagingStreamCustomAllocationTest, ReallocationBoundary) {
    // memory source
    std::vector<uint8_t> memorySrc(kTestBufferSize * 3);
    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    MockAlloc allocFn;

    // alloc function should be called twice
    {
        InSequence seq;

        // expect initial allocation call with allocation size >= kTestBufferSize;
        EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

        // expect reallocation call with allocation size > kTestBufferSize.
        EXPECT_CALL(allocFn, Call(testing::Ge(kTestBufferSize)))
            .Times(1)
            .WillRepeatedly(Return(memory));
    }

    // free function should be called once during reallocation,
    // once when stream goes out of scope
    MockFree freeFn;

    EXPECT_CALL(freeFn, Call(Eq(memory))).Times(2);
    auto allocStdFn = allocFn.AsStdFunction();
    auto freeStdFn = freeFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, freeStdFn);
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // split data into two batches
    // size of first batch data should be enough to fill the allocated buffer
    // first data batch size = kTestBufferSize - sync data size
    const std::string firstBatchData(kTestBufferSize - CommandBufferStagingStream::kSyncDataSize,
                                     'a');

    // copy first batch of data into stream
    memcpy(buffer, firstBatchData.data(), firstBatchData.size());

    // commit first batch of data
    stream.commitBuffer(firstBatchData.size());

    // size of first batch of data brings stream buffer to capacity; this will result in a
    // reallocation
    buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // data written before reallocation should be intact
    unsigned char* outBuf = nullptr;
    size_t outSize = 0;
    stream.getWritten(&outBuf, &outSize);

    const std::string_view expectedData{firstBatchData};
    std::string_view actualData{reinterpret_cast<char*>(outBuf), outSize};
    EXPECT_THAT(actualData, Eq(expectedData));
}

// this test verifies that allocBuffer doesn't cause reallcation if buffer has available space
TEST(CommandBufferStagingStreamCustomAllocationTest, NoReallocationIfBufferIsNotFull) {
    // memory source
    std::vector<uint8_t> memorySrc(kTestBufferSize * 3);

    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    MockAlloc allocFn;
    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

    auto allocStdFn = allocFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, EmptyFree);
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    EXPECT_THAT(buffer, NotNull());

    // commit portion of buffer
    const size_t writeSize = 10;
    stream.commitBuffer(writeSize);

    uint8_t* writePtr = static_cast<uint8_t*>(stream.allocBuffer(writeSize));
    EXPECT_THAT(writePtr, buffer + writeSize);
}

// this test verifies that CommandBufferStagingStream accounts for metadata in the
// beginning of its stream buffer
TEST(CommandBufferStagingStreamCustomAllocationTest, MetadataCheck) {
    // memory source
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);
    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    // CommandBufferStagingStream allocates metadata when using custom allocators
    static const size_t expectedMetadataSize = 8;
    MockAlloc allocFn;

    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

    auto allocStdFn = allocFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, EmptyFree);
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));
    // data should start after metadata
    EXPECT_THAT(buffer, memorySrc.data() + expectedMetadataSize);
    // metadata should be initialized to read complete
    uint32_t* metadataPtr = reinterpret_cast<uint32_t*>(memorySrc.data());
    EXPECT_THAT(*metadataPtr, CommandBufferStagingStream::kSyncDataReadComplete);
}

TEST(CommandBufferStagingStreamCustomAllocationTest, MarkFlushing) {
    // memory source for  allocation
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);
    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};
    MockAlloc allocFn;

    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

    auto allocStdFn = allocFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, EmptyFree);
    uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));

    // write some data
    const std::string_view commandData{"some command"};
    const size_t dataSize = commandData.size();
    memcpy(buffer, commandData.data(), dataSize);

    // commit data
    stream.commitBuffer(dataSize);

    // will set metadata of the stream buffer to pending read
    stream.markFlushing();

    uint32_t* readPtr = reinterpret_cast<uint32_t*>(memorySrc.data());
    EXPECT_EQ(*readPtr, CommandBufferStagingStream::kSyncDataReadPending);
}

// this test verifies that realloc waits till consumer of memory has completed read
TEST(CommandBufferStagingStreamCustomAllocationTest, ReallocNotCalledTillBufferIsRead) {
    // memory source for  allocation
    // allocate a big enough buffer to avoid resizes in test
    std::vector<uint8_t> memorySrc(kTestBufferSize * 3);
    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};

    std::condition_variable memoryFlushedCondition;
    std::mutex mutex;

    MockAlloc allocFn;

    // mock function to notify read is complete
    // this will be used to set up the expectation that realloc should
    // not be called till read is complete
    MockFunction<void(void)> readCompleteCall;

    testing::Expectation readCompleteExpectation = EXPECT_CALL(readCompleteCall, Call).Times(1);

    std::thread consumer([&]() {
        std::unique_lock readLock(mutex);
        memoryFlushedCondition.wait(readLock, [&]() {
            // wait till memorySrc is ready for read
            uint32_t* syncData = static_cast<uint32_t*>(memory.ptr);
            return *syncData = CommandBufferStagingStream::kSyncDataReadPending;
        });

        readLock.unlock();

        __atomic_store_n(memorySrc.data(), CommandBufferStagingStream::kSyncDataReadComplete,
                         __ATOMIC_RELEASE);

        auto fn = readCompleteCall.AsStdFunction();
        fn();
    });

    auto allocStdFn = allocFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, EmptyFree);  // scope for writeLock
    {
        std::lock_guard writeLock(mutex);

        EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

        uint8_t* buffer = static_cast<uint8_t*>(stream.allocBuffer(kTestBufferSize));

        // write some data
        const std::string_view commandData{"some command"};
        const size_t dataSize = commandData.size();
        memcpy(buffer, commandData.data(), dataSize);

        // commit data
        stream.commitBuffer(dataSize);

        // will set metadata of the stream buffer to pending read
        stream.markFlushing();

        memoryFlushedCondition.notify_one();
    }

    // expecatation call for reallocation call
    EXPECT_CALL(allocFn, Call(testing::Ge(kTestBufferSize)))
        .Times(1)
        .After(readCompleteExpectation)
        .WillRepeatedly(Return(memory));

    // realloc will be blocked till buffer read is complete by reader
    (void)stream.allocBuffer(kTestBufferSize);

    // wait for read thread to finish
    consumer.join();
}

// this test verifies that allocBuffer() cannot be called on a stream
// that is currently being read by the host
TEST(CommandBufferStagingStreamCustomAllocationTest, AllocBufferFailsIfReadPending) {
    // memory source for  allocation
    std::vector<uint8_t> memorySrc(kTestBufferSize * 2);
    CommandBufferStagingStream::Memory memory{
        .deviceMemory = VK_NULL_HANDLE,  // not needed for this test
        .ptr = memorySrc.data()};
    MockAlloc allocFn;

    EXPECT_CALL(allocFn, Call(Ge(kTestBufferSize))).Times(1).WillRepeatedly(Return(memory));

    auto allocStdFn = allocFn.AsStdFunction();
    CommandBufferStagingStream stream(allocStdFn, EmptyFree);
    (void)stream.allocBuffer(kTestBufferSize);

    // will set metadata of the stream buffer to pending read
    stream.markFlushing();

    EXPECT_DEATH({ (void)stream.allocBuffer(kTestBufferSize); }, "")
        << "allocBuffer() should not be called while previous data is being flushed";
}

}  // namespace vk
}  // namespace gfxstream
