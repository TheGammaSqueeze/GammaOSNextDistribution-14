#!/usr/bin/env python3
#
# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#            http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Generate ICU stable C API wrapper source.

This script parses all the header files specified by the ICU module names. For
each function in the allowlist, it generates a wrapper function to be
called by libandroidicu, which in turn calls the available function at runtime. The tool
relies on libclang to parse header files, which is a component provided by
Clang.

Reference to ICU4C stable C APIs:
http://icu-project.org/apiref/icu4c/files.html
"""
from __future__ import absolute_import
from __future__ import print_function

import logging
import os
import shutil
import subprocess

from genutil import (
    android_path,
    generate_shim,
    generate_symbol_txt,
    get_allowlisted_apis,
    DeclaredFunctionsParser,
    StableDeclarationFilter,
)

SYMBOL_SUFFIX = '_android'

def copy_header_only_files():
    """Copy required header only files"""
    base_src_path = android_path('external/icu/icu4c/source/')
    base_dest_path = android_path('external/icu/libandroidicu/include/unicode/')
    with open(android_path('external/icu/tools/icu4c_srcgen/required_header_only_files.txt'),
              'r') as in_file:
        header_only_files = [
            base_src_path + line.strip() for line in in_file.readlines() if not line.startswith('#')
        ]

    for src_path in header_only_files:
        dest_path = base_dest_path + os.path.basename(src_path)
        cmd = ['sed',
               "s/U_SHOW_CPLUSPLUS_API/LIBANDROIDICU_U_SHOW_CPLUSPLUS_API/g",
               src_path
               ]

        with open(dest_path, "w") as destfile:
            subprocess.check_call(cmd, stdout=destfile)

def main():
    """Parse the ICU4C headers and generate the shim libandroidicu."""
    logging.basicConfig(level=logging.DEBUG)

    decl_filters = [StableDeclarationFilter()]
    parser = DeclaredFunctionsParser(decl_filters, [])

    parser.parse()

    includes = parser.header_includes
    functions = parser.declared_functions

    # The shim has the allowlisted functions only
    allowlisted_apis = get_allowlisted_apis('libandroidicu_allowlisted_api.txt')
    functions = [f for f in functions if f.name in allowlisted_apis]

    headers_folder = android_path('external/icu/libandroidicu/include/unicode')
    if os.path.exists(headers_folder):
        shutil.rmtree(headers_folder)
    os.mkdir(headers_folder)

    with open(android_path('external/icu/libandroidicu/static_shim/shim.cpp'),
              'w') as out_file:
        out_file.write(generate_shim(functions, includes, SYMBOL_SUFFIX,
                                     'libandroidicu_shim.cpp.j2'))

    with open(android_path('external/icu/libandroidicu/libandroidicu.map.txt'),
              'w') as out_file:
        out_file.write(generate_symbol_txt(functions, [], 'libandroidicu.map.txt.j2'))

    for path in parser.header_paths_to_copy:
        basename = os.path.basename(path)
        shutil.copyfile(path, os.path.join(headers_folder, basename))

    copy_header_only_files()

    print("Done. See libandroidicu/ for the generated content.")

if __name__ == '__main__':
    main()
