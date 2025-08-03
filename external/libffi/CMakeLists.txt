# Copyright 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Build libffi for Windows x86_64 using MSVC.
#

project(libffi LANGUAGES C ASM_MASM)
cmake_minimum_required(VERSION 3.18.1)

set(src ${CMAKE_CURRENT_SOURCE_DIR})
set(out ${CMAKE_CURRENT_BINARY_DIR})

set(TARGET X86_WIN64)
set(HAVE_LONG_DOUBLE 0)
set(FFI_EXEC_TRAMPOLINE_TABLE 0)
configure_file(include/ffi.h.in ${out}/ffi.h @ONLY)

add_library(libffi SHARED
  src/prep_cif.c
  src/types.c
  src/raw_api.c
  src/java_raw_api.c
  src/closures.c
  src/x86/ffiw64.c
  ${out}/win64_intel.asm
)

configure_file(src/x86/ffitarget.h    ${out}/dist/include/ffitarget.h   COPYONLY)
configure_file(${out}/ffi.h           ${out}/dist/include/ffi.h         COPYONLY)

set(defines
  -DHAVE_CONFIG_H=1
  -DFFI_BUILDING_DLL=1
)

set(include_dirs
  ${src}/include
  ${src}/windows-msvc-x86_64
  ${src}/src/x86
  ${out}
)

target_compile_definitions(libffi PRIVATE ${defines})
target_include_directories(libffi PUBLIC ${include_dirs})

list(TRANSFORM include_dirs PREPEND -I OUTPUT_VARIABLE include_dir_args)

# Preprocess this .S file before assembling it with ml64. It's not clear to me whether CMake is
# supposed to support this inherently. The msvc_build/aarch64/Ffi_staticLib.vcxproj project also
# uses a CustomBuild step. This step won't rebuild if an included file changes, which might be
# fixable (someday), perhaps using the DEPFILE argument to add_custom_command.
add_custom_command(
  OUTPUT ${out}/win64_intel.asm
  COMMAND cl /EP ${src}/src/x86/win64_intel.S ${defines} ${include_dir_args} >${out}/win64_intel.asm
  DEPENDS src/x86/win64_intel.S ${out}/ffi.h
)
