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
FindLibDw
---------

Finds the DWARF processing library (libdw).

Imported Targets
^^^^^^^^^^^^^^^^

This module provides the following imported targets, if found:

``LibDw::LibDw``
  The LibDw library

Result Variables
^^^^^^^^^^^^^^^^

This will define the following variables:

``LibDw_FOUND``
  True if the system has the LibDw library.
``LibDw_VERSION``
  The version of the LibDw library which was found.
``LibDw_INCLUDE_DIRS``
  Include directories needed to use LibDw.
``LibDw_LIBRARIES``
  Libraries needed to link to LibDw.
``LibDw_DEFINITIONS``
  the compiler switches required for using LibDw

Cache Variables
^^^^^^^^^^^^^^^

The following cache variables may also be set:

``LibDw_INCLUDE_DIR``
  The directory containing ``dwarf.h``.
``LibDw_LIBRARY``
  The path to the ``libdw.so``.

#]=======================================================================]

find_package(PkgConfig)
pkg_check_modules(PC_LibDw QUIET libdw)

find_library(
  LibDw_LIBRARY
  NAMES dw
  HINTS ${PC_LibDw_LIBDIR} ${PC_LibDw_LIBRARY_DIRS})
# Try the value from user if the library is not found.
if(DEFINED LibDw_LIBRARIES AND NOT DEFINED LibDw_LIBRARY)
  set(LibDw_LIBRARY ${LibDw_LIBRARIES})
endif()
mark_as_advanced(LibDw_LIBRARY)

find_path(
  LibDw_INCLUDE_DIR
  NAMES dwarf.h
  HINTS ${PC_LibDw_INCLUDEDIR} ${PC_LibDw_INCLUDE_DIRS})
# Try the value from user if the library is not found.
if(DEFINED LibDw_INCLUDE_DIRS AND NOT DEFINED LibDw_INCLUDE_DIR)
  set(LibDw_INCLUDE_DIR ${LibDw_INCLUDE_DIRS})
endif()
mark_as_advanced(LibDw_INCLUDE_DIR)

set(LibDw_VERSION ${PC_LibDw_VERSION})

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(
  LibDw
  REQUIRED_VARS LibDw_LIBRARY LibDw_INCLUDE_DIR
  VERSION_VAR LibDw_VERSION)

if(LibDw_FOUND)
  set(LibDw_LIBRARIES ${LibDw_LIBRARY})
  set(LibDw_INCLUDE_DIRS ${LibDw_INCLUDE_DIR})
  set(LibDw_DEFINITIONS ${PC_LibDw_CFLAGS_OTHER})
endif()

if(LibDw_FOUND AND NOT TARGET LibDw::LibDw)
  add_library(LibDw::LibDw UNKNOWN IMPORTED)
  set_target_properties(
    LibDw::LibDw
    PROPERTIES IMPORTED_LOCATION "${LibDw_LIBRARY}"
               INTERFACE_COMPILE_OPTIONS "${PC_LibDw_CFLAGS_OTHER}"
               INTERFACE_INCLUDE_DIRECTORIES "${LibDw_INCLUDE_DIR}")
endif()
