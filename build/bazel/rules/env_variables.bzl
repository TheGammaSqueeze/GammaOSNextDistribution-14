# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

CAPTURED_ENV_VARS = [
    # clang-tidy
    "ALLOW_LOCAL_TIDY_TRUE",
    "DEFAULT_TIDY_HEADER_DIRS",
    "TIDY_TIMEOUT",
    "WITH_TIDY",
    "WITH_TIDY_FLAGS",
    "TIDY_EXTERNAL_VENDOR",

    # Other variables
    "SKIP_ABI_CHECKS",
    "UNSAFE_DISABLE_APEX_ALLOWED_DEPS_CHECK",
    "AUTO_ZERO_INITIALIZE",
    "AUTO_PATTERN_INITIALIZE",
    "AUTO_UNINITIALIZE",
    "USE_CCACHE",
    "LLVM_NEXT",
    "LLVM_PREBUILTS_VERSION",
    "LLVM_RELEASE_VERSION",
    "ALLOW_UNKNOWN_WARNING_OPTION",
    "UNBUNDLED_BUILD_TARGET_SDK_WITH_API_FINGERPRINT",
    "CLANG_DEFAULT_DEBUG_LEVEL",
    "RUN_ERROR_PRONE",
    "RUST_PREBUILTS_VERSION",
    "DEVICE_TEST_RBE_DOCKER_IMAGE_LINK",

    # REMOTE_AVD is an env var knob to apply conditionals in parts of the build
    # that can't read build settings, like macros, which defines
    # execution-related tags.
    "REMOTE_AVD",

    # Overrides the version in the apex_manifest.json. The version is unique for
    # each branch (internal, aosp, mainline releases, dessert releases).  This
    # enables modules built on an older branch to be installed against a newer
    # device for development purposes.
    "OVERRIDE_APEX_MANIFEST_DEFAULT_VERSION",
]
