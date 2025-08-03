#!/usr/bin/python3
#
# Copyright (C) 2023 The Android Open Source Project
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

import sys
import os

# Paths for output, relative to system/chre
CHPP_PARSER_INCLUDE_PATH = 'chpp/include/chpp/common'
CHPP_PARSER_SOURCE_PATH = 'chpp/common'

LICENSE_HEADER = """/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
"""


def android_build_top_abs_path():
    """Gets the absolute path to the Android build top directory or exits the program."""

    android_build_top = os.environ.get('ANDROID_BUILD_TOP')
    if android_build_top == None or len(android_build_top) == 0:
        print('ANDROID_BUILD_TOP not found. Please source build/envsetup.sh.')
        sys.exit(1)

    return android_build_top


def system_chre_abs_path():
    """Gets the absolute path to the system/chre directory or exits this program."""

    return android_build_top_abs_path() + '/system/chre'
