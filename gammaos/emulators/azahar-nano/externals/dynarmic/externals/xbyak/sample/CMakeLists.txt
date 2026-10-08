cmake_minimum_required(VERSION 3.10)
project(XbyakSamples)

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/..)

# Read warning flags if available (only for GCC/Clang)
if(NOT MSVC AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../test/CFLAGS_WARN.cfg")
    file(STRINGS "${CMAKE_CURRENT_SOURCE_DIR}/../test/CFLAGS_WARN.cfg" CFLAGS_WARN_STR)
    separate_arguments(CFLAGS_WARN UNIX_COMMAND "${CFLAGS_WARN_STR}")
endif()

# Platform-specific compiler flags
if(WIN32)
    if(MSVC)
        set(COMMON_COMPILE_FLAGS /W3)
    else()
        set(COMMON_COMPILE_FLAGS -Wall -fomit-frame-pointer ${CFLAGS_WARN})
    endif()
else()
    set(COMMON_COMPILE_FLAGS -Wall -fomit-frame-pointer ${CFLAGS_WARN})
endif()

# Check for Boost (optional for calc samples)
find_package(Boost QUIET COMPONENTS system)

# Helper function to add sample executable
function(add_sample_target target_name source_file)
    add_executable(${target_name} ${source_file})
    target_compile_options(${target_name} PRIVATE ${COMMON_COMPILE_FLAGS})

    # Set output directory
    set_target_properties(${target_name} PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
    )

    # Platform-specific settings
    if(NOT WIN32)
        find_package(Threads)
        if(Threads_FOUND)
            target_link_libraries(${target_name} ${CMAKE_THREAD_LIBS_INIT})
        endif()
    endif()
endfunction()

# 64-bit targets (always available on 64-bit platforms)
if(CMAKE_SIZEOF_VOID_P EQUAL 8)
    add_sample_target(test64 test0.cpp)
    add_sample_target(bf64 bf.cpp)
    add_sample_target(memfunc64 memfunc.cpp)
    add_sample_target(test_util64 test_util.cpp)
    add_sample_target(cputopology cputopology.cpp)
    add_sample_target(jmp_table64 jmp_table.cpp)
    add_sample_target(zero_upper zero_upper.cpp)
    add_sample_target(ccmp ccmp.cpp)
    add_sample_target(no_flags no_flags.cpp)

    # Boost-dependent targets
    if(Boost_FOUND)
        add_sample_target(calc64 calc.cpp)
        target_link_libraries(calc64 Boost::boost)
    endif()

    # Non-macOS targets
    if(NOT APPLE)
        if(UNIX)
            # static_buf uses Linux-specific mmap feature
            add_sample_target(static_buf64 static_buf.cpp)
            add_sample_target(memfd memfd.cpp)
        endif()
    endif()

    # Additional useful targets
    add_sample_target(quantize quantize.cpp)
endif()

# Optional 32-bit targets (skip on macOS which is 64-bit only)
# Note: Building 32-bit on 64-bit requires multilib on Linux (gcc-multilib g++-multilib)
# Enable with: cmake -DBUILD_32BIT_TARGETS=ON ..
option(BUILD_32BIT_TARGETS "Build 32-bit targets on 64-bit systems (requires multilib)" OFF)

if(BUILD_32BIT_TARGETS AND NOT APPLE AND CMAKE_SIZEOF_VOID_P EQUAL 8 AND UNIX AND NOT WIN32)
    message(STATUS "Building 32-bit targets (requires multilib)")

    add_sample_target(test test0.cpp)
    target_compile_options(test PRIVATE -m32)
    target_link_options(test PRIVATE -m32)

    add_sample_target(bf bf.cpp)
    target_compile_options(bf PRIVATE -m32)
    target_link_options(bf PRIVATE -m32)

    add_sample_target(toyvm toyvm.cpp)
    target_compile_options(toyvm PRIVATE -m32)
    target_link_options(toyvm PRIVATE -m32)

    add_sample_target(test_util test_util.cpp)
    target_compile_options(test_util PRIVATE -m32)
    target_link_options(test_util PRIVATE -m32)

    add_sample_target(memfunc memfunc.cpp)
    target_compile_options(memfunc PRIVATE -m32)
    target_link_options(memfunc PRIVATE -m32)

    add_sample_target(static_buf static_buf.cpp)
    target_compile_options(static_buf PRIVATE -m32)
    target_link_options(static_buf PRIVATE -m32)

    add_sample_target(jmp_table jmp_table.cpp)
    target_compile_options(jmp_table PRIVATE -m32)
    target_link_options(jmp_table PRIVATE -m32)

    if(Boost_FOUND)
        add_sample_target(calc calc.cpp)
        target_compile_options(calc PRIVATE -m32)
        target_link_options(calc PRIVATE -m32)
        target_link_libraries(calc Boost::boost)
    endif()
endif()

# Additional utilities
add_sample_target(profiler profiler.cpp)

# Optional VTune profiler build
option(BUILD_PROFILER_VTUNE "Build profiler with VTune support" OFF)
if(BUILD_PROFILER_VTUNE AND UNIX)
    add_sample_target(profiler-vtune profiler.cpp)
    target_compile_definitions(profiler-vtune PRIVATE XBYAK_USE_VTUNE)
    target_include_directories(profiler-vtune PRIVATE /opt/intel/vtune_amplifier/include/)
    target_link_directories(profiler-vtune PRIVATE /opt/intel/vtune_amplifier/lib64)
    target_link_libraries(profiler-vtune jitprofiling dl)
endif()

# Print configuration summary
message(STATUS "Xbyak Samples Configuration:")
message(STATUS "  Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  Architecture: ${CMAKE_SIZEOF_VOID_P} bytes (${CMAKE_SIZEOF_VOID_P} * 8 = ${CMAKE_SYSTEM_PROCESSOR})")
message(STATUS "  Boost found: ${Boost_FOUND}")
message(STATUS "  Output directory: ${CMAKE_BINARY_DIR}/bin")
