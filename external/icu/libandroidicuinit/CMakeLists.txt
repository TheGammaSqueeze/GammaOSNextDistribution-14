# Minimum CMake required
cmake_minimum_required(VERSION 3.1.3)

# Project
project(androidicuinit C CXX)

add_definitions("-DANDROID_LINK_SHARED_ICU4C")
add_definitions("-DANDROID_ICU_NO_DAT")
add_definitions("-DNO_ANDROID_LIBLOG")

# Glob libandroidicuinit source files
file(
   GLOB_RECURSE
   ANDROIDICU_INIT_CC_SOURCES
   RELATIVE ${CMAKE_CURRENT_SOURCE_DIR}
   "${CMAKE_CURRENT_SOURCE_DIR}/*.h"
   "${CMAKE_CURRENT_SOURCE_DIR}/*.cpp"
)
message(STATUS "ANDROIDICU_INIT_CC_SOURCES=${ANDROIDICU_INIT_CC_SOURCES}")

# Add icuuc, android_icu4c headers
set(ICU_COMMON_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../icu4c/source/common")
set(ICU_CONFIG_LOCAL_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../android_icu4c/include")

add_library(
   # name
   androidicuinit

   # SHARED or STATIC
   STATIC

   # Provides a relative path to your source file(s).
   ${ANDROIDICU_INIT_CC_SOURCES}
)
target_include_directories(androidicuinit PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_include_directories(androidicuinit PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_include_directories(androidicuinit PRIVATE ${ICU_COMMON_SOURCE_DIR})
target_include_directories(androidicuinit PRIVATE ${ICU_CONFIG_LOCAL_SOURCE_DIR})

set_target_properties(androidicuinit PROPERTIES LINKER_LANGUAGE CXX)
