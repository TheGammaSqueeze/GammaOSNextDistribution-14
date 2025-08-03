# SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
#
# Copyright 2023 Google LLC
#
# Licensed under the Apache License v2.0 with LLVM Exceptions (the "License");
# you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
# https://llvm.org/LICENSE.txt
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
#
# Author: Aleksei Vetrov

#[=======================================================================[.rst:
FindLibElf
----------

Finds the ELF processing library (libelf).

Imported Targets
^^^^^^^^^^^^^^^^

This module provides the following imported targets, if found:

``LibElf::LibElf``
  The LibElf library

Result Variables
^^^^^^^^^^^^^^^^

This will define the following variables:

``LibElf_FOUND``
  True if the system has the LibElf library.
``LibElf_VERSION``
  The version of the LibElf library which was found.
``LibElf_INCLUDE_DIRS``
  Include directories needed to use LibElf.
``LibElf_LIBRARIES``
  Libraries needed to link to LibElf.
``LibElf_DEFINITIONS``
  the compiler switches required for using LibElf

Cache Variables
^^^^^^^^^^^^^^^

The following cache variables may also be set:

``LibElf_INCLUDE_DIR``
  The directory containing ``libelf.h``.
``LibElf_LIBRARY``
  The path to the ``libelf.so``.

#]=======================================================================]

find_package(PkgConfig)
pkg_check_modules(PC_LibElf QUIET libelf)

find_library(
  LibElf_LIBRARY
  NAMES elf
  HINTS ${PC_LibElf_LIBDIR} ${PC_LibElf_LIBRARY_DIRS})
# Try the value from user if the library is not found.
if(DEFINED LibElf_LIBRARIES AND NOT DEFINED LibElf_LIBRARY)
  set(LibElf_LIBRARY ${LibElf_LIBRARIES})
endif()
mark_as_advanced(LibElf_LIBRARY)

find_path(
  LibElf_INCLUDE_DIR
  NAMES libelf.h
  HINTS ${PC_LibElf_INCLUDEDIR} ${PC_LibElf_INCLUDE_DIRS})
# Try the value from user if the library is not found.
if(DEFINED LibElf_INCLUDE_DIRS AND NOT DEFINED LibElf_INCLUDE_DIR)
  set(LibElf_INCLUDE_DIR ${LibElf_INCLUDE_DIRS})
endif()
mark_as_advanced(LibElf_INCLUDE_DIR)

set(LibElf_VERSION ${PC_LibElf_VERSION})

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(
  LibElf
  REQUIRED_VARS LibElf_LIBRARY LibElf_INCLUDE_DIR
  VERSION_VAR LibElf_VERSION)

if(LibElf_FOUND)
  set(LibElf_LIBRARIES ${LibElf_LIBRARY})
  set(LibElf_INCLUDE_DIRS ${LibElf_INCLUDE_DIR})
  set(LibElf_DEFINITIONS ${PC_LibElf_CFLAGS_OTHER})
endif()

if(LibElf_FOUND AND NOT TARGET LibElf::LibElf)
  add_library(LibElf::LibElf UNKNOWN IMPORTED)
  set_target_properties(
    LibElf::LibElf
    PROPERTIES IMPORTED_LOCATION "${LibElf_LIBRARY}"
               INTERFACE_COMPILE_OPTIONS "${PC_LibElf_CFLAGS_OTHER}"
               INTERFACE_INCLUDE_DIRECTORIES "${LibElf_INCLUDE_DIR}")
endif()
