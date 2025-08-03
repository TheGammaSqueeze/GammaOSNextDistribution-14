set(BLUETOOTH_EMULATION True)
get_filename_component(AOSP "${CMAKE_CURRENT_LIST_DIR}/../../.." ABSOLUTE)
set(EXTERNAL ${AOSP}/external)
set(EXTERNAL_QEMU ${EXTERNAL}/qemu)
set(ANDROID_QEMU2_TOP_DIR ${EXTERNAL_QEMU})

if(NOT DEFINED ANDROID_TARGET_TAG)
  message(
    WARNING
      "You should invoke the cmake generator with a proper toolchain from ${EXTERNAL_QEMU}/android/build/cmake, "
      "Trying to infer toolchain, this might not work.")
  list(APPEND CMAKE_MODULE_PATH "${EXTERNAL_QEMU}/android/build/cmake/")
  include(toolchain)
  _get_host_tag(TAG)
  toolchain_configure_tags(${TAG})
endif()

include(android)
include(prebuilts)
prebuilt(Threads)

if(DARWIN_AARCH64 AND NOT Rust_COMPILER)
  message(
    STATUS
      "On Apple sillicon attempting to use platform toolchain if available.")
  list(APPEND CMAKE_MODULE_PATH
       "${EXTERNAL_QEMU}/android/build/cmake/corrosion/cmake/")
  find_package(Rust REQUIRED)
  if(TARGET Rust::Rustc)
    set(OPTION_ENABLE_SYSTEM_RUST TRUE)
  else()
    message(STATUS "Unable to derive local toolchain")
    message(
      FATAL_ERROR
        "If you are a developer you can install rust with `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`"
    )
  endif()
endif()

if(WINDOWS_MSVC_X86_64)
  # Set of msvc compat layer libraries.
  add_subdirectory(${EXTERNAL_QEMU}/android/third_party/mman-win32 mman-win32)
  add_subdirectory(${EXTERNAL_QEMU}/android/third_party/regex-win32 regex-win32)
  add_subdirectory(${EXTERNAL_QEMU}/android/third_party/dirent-win32
                   dirent-win32)
endif()

if(Rust_COMPILER OR OPTION_ENABLE_SYSTEM_RUST)
  if(OPTION_ENABLE_SYSTEM_RUST)
    message(STATUS "Attempting to use the system rust compiler")
    use_system_rust_toolchain()
  endif()

  enable_vendorized_crates("${EXTERNAL_QEMU}/android/third_party/rust/crates")
  add_subdirectory(${EXTERNAL_QEMU}/android/build/cmake/corrosion corrosion)
  ensure_rust_version_is_compliant()
endif()

set(_gRPC_RE2_INCLUDE_DIR "${EXTERNAL_QEMU}/android/third_party/re2")
set(_gRPC_RE2_LIBRARIES re2)
set(NETSIM_EXT TRUE)

# Let's bin place everything in the root, with the shared libs in the right
# place
set(DBG_INFO ${CMAKE_BINARY_DIR}/build/debug_info)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib64)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/archives)
set(CMAKE_PDB_OUTPUT_DIRECTORY ${DBG_INFO})
# Feeling courageous? Set this to $ANDROID_SDK_ROOT
if(DARWIN_X86_64 OR DARWIN_AARCH64)
  set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR}/distribution/emulator)
  set(CMAKE_INSTALL_CODESIGN ${CMAKE_BINARY_DIR}/distribution/_codesign)
else()
  set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR}/distribution/emulator)
endif()

# First make the protobuf and dependencies available to gRPC
add_subdirectory(${EXTERNAL}/qemu/android/third_party/protobuf protobuf)

add_subdirectory(${AOSP}/hardware/google/aemu/base aemu-base)
add_subdirectory(${AOSP}/hardware/google/aemu/host-common host-common)
add_subdirectory(${AOSP}/packages/modules/Bluetooth/tools/rootcanal rootcanal)
add_subdirectory(${EXTERNAL_QEMU}/android/third_party/abseil-cpp abseil-cpp)
add_subdirectory(${EXTERNAL_QEMU}/android/third_party/boringssl boringssl)
add_subdirectory(${EXTERNAL_QEMU}/android/third_party/google-benchmark
                 google-benchmark)
add_subdirectory(${EXTERNAL_QEMU}/android/third_party/hostapd hostapd)
add_subdirectory(${EXTERNAL_QEMU}/android/third_party/libslirp libslirp)
add_subdirectory(${EXTERNAL_QEMU}/android/third_party/googletest/ gtest)
add_subdirectory(${EXTERNAL_QEMU}/android/third_party/lz4 lz4)
add_subdirectory(${EXTERNAL_QEMU}/android/third_party/re2 re2)
add_subdirectory(${EXTERNAL}/cares cares)
add_subdirectory(${EXTERNAL}/glib/glib glib2)
add_subdirectory(${EXTERNAL}/grpc/emulator grpc)
add_subdirectory(${EXTERNAL}/qemu/android/android-emu-base android-emu-base)
add_subdirectory(${EXTERNAL}/qemu/android/android-net/android android-emu-net)
add_subdirectory(${EXTERNAL}/qemu/android-qemu2-glue/netsim
                 android-wifi-service)
add_subdirectory(${EXTERNAL}/qemu/android/emu/base emu-base)
add_subdirectory(${EXTERNAL}/qemu/android/emu/utils android-emu-utils)
add_subdirectory(${EXTERNAL}/webrtc/third_party/jsoncpp jsoncpp)

if(NOT TARGET gfxstream-snapshot.headers)
  # Fake dependency to satisfy linker
  add_library(gfxstream-snapshot.headers INTERFACE)
endif()

if(CMAKE_BUILD_TYPE MATCHES DEBUG)
  # This will help you find issues.
  set(CMAKE_C_FLAGS "-fsanitize=address -fno-omit-frame-pointer -g3 -O0")
  set(CMAKE_EXE_LINKER_FLAGS "-fsanitize=address")
endif()

if(LINUX_X86_64)
  # Our linux headers are from 2013, and do not define newer socket options.
  # (b/156635589)
  target_compile_options(grpc PRIVATE -DSO_REUSEPORT=15)
  target_compile_options(grpc_unsecure PRIVATE -DSO_REUSEPORT=15)
endif()

# Testing
enable_testing()
include(GoogleTest)
