set(BUILD_STANDALONE ON PARENT_SCOPE)
set(GFXSTREAM_BASE_LIB aemu-base PARENT_SCOPE)
set(GFXSTREAM_HOST_COMMON_LIB aemu-host-common PARENT_SCOPE)

# aemu-base overrides:
#   - aemu-base-srcs: aemu-base source files
#   - aemu-base-test-srcs: aemu-base test source files
set(aemu-base-srcs
    AlignedBuf.cpp
    CLog.cpp
    CpuTime.cpp
    FileUtils.cpp
    FunctorThread.cpp
    GLObjectCounter.cpp
    HealthMonitor.cpp
    LayoutResolver.cpp
    MemStream.cpp
    StdioStream.cpp
    MemoryTracker.cpp
    MessageChannel.cpp
    PathUtils.cpp
    ring_buffer.cpp
    SharedLibrary.cpp
    StringFormat.cpp
    Stream.cpp
    StreamSerializing.cpp
    SubAllocator.cpp
    System.cpp
    Tracing.cpp)
if(AEMU_BASE_USE_LZ4)
    list(APPEND aemu-base-srcs CompressingStream.cpp DecompressingStream.cpp)
endif()

set(aemu-base-posix-srcs
    SharedMemory_posix.cpp
    Thread_pthread.cpp)
set(aemu-base-windows-srcs
    msvc.cpp
    SharedMemory_win32.cpp
    Thread_win32.cpp
    Win32UnicodeString.cpp)

if (APPLE)
    set(aemu-base-platform-srcs ${aemu-base-posix-srcs} system-native-mac.mm)
elseif (WIN32)
    set(aemu-base-platform-srcs ${aemu-base-windows-srcs})
else()
    set(aemu-base-platform-srcs ${aemu-base-posix-srcs})
endif()

set(aemu-base-srcs ${aemu-base-srcs} ${aemu-base-platform-srcs} PARENT_SCOPE)

set(aemu-base-test-srcs
    AlignedBuf_unittest.cpp
    HealthMonitor_unittest.cpp
    ArraySize_unittest.cpp
    LayoutResolver_unittest.cpp
    LruCache_unittest.cpp
    ManagedDescriptor_unittest.cpp
    Optional_unittest.cpp
    ring_buffer_unittest.cpp
    StringFormat_unittest.cpp
    SubAllocator_unittest.cpp
    TypeTraits_unittest.cpp
    WorkerThread_unittest.cpp PARENT_SCOPE)
