if (WIN32)
endif()

add_library(aemu-base.headers INTERFACE)
# TODO: Remove this once host-common/logging.h is moved into base.
target_link_libraries(aemu-base.headers INTERFACE aemu-host-common.headers)
target_include_directories(aemu-base.headers INTERFACE include)

if(WIN32)
    target_compile_definitions(aemu-base.headers INTERFACE WIN32_LEAN_AND_MEAN)
endif()

if(ENABLE_CLANG_THREAD_SAFETY_CHECKS)
    target_compile_definitions(aemu-base.headers INTERFACE -D_LIBCPP_ENABLE_THREAD_SAFETY_ANNOTATIONS)
    target_compile_options(aemu-base.headers INTERFACE -Wthread-safety)
endif()

if (BUILD_STANDALONE)
    if (NOT DEFINED aemu-base-srcs)
        # Build everything by default
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
        set(aemu-base-posix-srcs
            SharedMemory_posix.cpp
            Thread_pthread.cpp)
        set(aemu-base-windows-srcs
            msvc.cpp
            SharedMemory_win32.cpp
            Thread_win32.cpp
            Win32UnicodeString.cpp)
        if(AEMU_BASE_USE_LZ4)
            list(APPEND aemu-base-srcs CompressingStream.cpp DecompressingStream.cpp)
        endif()

        if (APPLE)
            set(aemu-platform-srcs
                ${aemu-base-posix-srcs}
                system-native-mac.mm)
        elseif (WIN32)
            set(aemu-platform-srcs
                ${aemu-base-windows-srcs})
        else()
            set(aemu-platform-srcs
                ${aemu-base-posix-srcs})
        endif()

        set(aemu-base-srcs ${aemu-base-srcs} ${aemu-platform-srcs})
    endif()

    add_library(aemu-base ${aemu-base-srcs})
    if (BUILD_SHARED_LIBS)
        set_target_properties(
            aemu-base
            PROPERTIES
            VERSION ${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}
            SOVERSION ${VERSION_MAJOR})
    endif()

    target_compile_definitions(aemu-base PRIVATE)

    if (WIN32)
        set(aemu-base-platform-deps Shlwapi)
    elseif (QNX)
        set(aemu-base-platform-deps dl)
    elseif(LINUX)
        set(aemu-base-platform-deps dl rt)
    elseif(APPLE)
        set(aemu-base-platform-deps "-framework Foundation" "-framework AppKit" "-framework IOKit")
    endif()

    target_link_libraries(
        aemu-base
        PUBLIC
        aemu-base.headers
        # TODO(joshuaduong): Remove this when logging.h is moved into base
        aemu-host-common.headers
        PRIVATE
        ${LOGGING_LIB_NAME}
        ${aemu-base-platform-deps})

    target_include_directories(
        aemu-base
        PRIVATE
        ${AEMU_COMMON_REPO_ROOT}/include
        PUBLIC
        ${AEMU_COMMON_REPO_ROOT})
    if (NOT MSVC)
        target_compile_options(
            aemu-base PRIVATE -fvisibility=default)
    endif()
    if(AEMU_COMMON_USE_PERFETTO)
        target_compile_definitions(aemu-base PRIVATE "USE_PERFETTO_TRACING")
        target_link_libraries(aemu-base PRIVATE perfetto-tracing-only)
    endif()
    if(AEMU_BASE_USE_LZ4)
        target_link_libraries(aemu-base PRIVATE lz4_static)
    endif()
endif()

if (APPLE)
    set(aemu-base-platform-test-srcs "")
elseif (WIN32)
    set(aemu-base-platform-test-srcs Win32UnicodeString_unittest.cpp)
else()
    set(aemu-base-platform-test-srcs "")
endif()

if (ENABLE_VKCEREAL_TESTS)
    # Tests
    add_library(
        aemu-base-testing-support
        testing/file_io.cpp)
    target_link_libraries(
        aemu-base-testing-support
        PRIVATE
        aemu-base.headers
        gtest
        gmock)

    if (NOT DEFINED aemu-base-test-srcs)
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
            WorkerThread_unittest.cpp)
    endif()
    add_executable(aemu-base_unittests ${aemu-base-test-srcs})
    target_link_libraries(
        aemu-base_unittests
        PRIVATE
        aemu-base.headers
        ${GFXSTREAM_BASE_LIB}
        ${LOGGING_LIB_NAME}
        aemu-base-testing-support
        gmock
        gtest_main)
    gtest_discover_tests(aemu-base_unittests)
endif()
