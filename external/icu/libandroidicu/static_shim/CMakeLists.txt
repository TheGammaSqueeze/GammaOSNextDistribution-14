# Minimum CMake required
cmake_minimum_required(VERSION 3.1.3)

# Project
project(libandroidicu C CXX)

add_definitions("-DANDROID_LINK_SHARED_ICU4C")

# Add other source dirs that icuuc uses
set(ICU_CONFIG_LOCAL_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../android_icu4c/include")

# Compile icuuc
set(ICUUC_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../icu4c/source/common")

# Compile icui18n
set(ICU_I18N_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../icu4c/source/i18n")
set(ICU_I18N_TARGET_BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/libicui18n-target")
add_subdirectory(${ICU_I18N_SOURCE_DIR} ${ICU_I18N_TARGET_BINARY_DIR})

# Glob shim sources
set(ICU_SHIM_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}")
file(
   GLOB_RECURSE
   ICU_STATIC_SHIM_CC_SOURCES
   RELATIVE ${CMAKE_CURRENT_SOURCE_DIR}
   "${ICU_SHIM_SOURCE_DIR}/*.cpp"
   "${ICU_SHIM_SOURCE_DIR}/*.h"
)
message(STATUS "ICU_STATIC_SHIM_CC_SOURCES=${ICU_STATIC_SHIM_CC_SOURCES}")

add_library(
   # name
   libandroidicu

   # SHARED or STATIC
   STATIC

   # Provides a relative path to your source file(s).
   ${ICU_STATIC_SHIM_CC_SOURCES}
)
target_include_directories(libandroidicu PRIVATE ${ICUUC_SOURCE_DIR})
target_include_directories(libandroidicu PRIVATE ${ICU_CONFIG_LOCAL_SOURCE_DIR})
target_include_directories(libandroidicu PRIVATE ${ICU_I18N_SOURCE_DIR})
target_include_directories(libandroidicu PRIVATE ${ICU_STATIC_SHIM_CC_SOURCES})
target_link_libraries(libandroidicu icui18n)

set_target_properties(libandroidicu PROPERTIES LINKER_LANGUAGE CXX)

