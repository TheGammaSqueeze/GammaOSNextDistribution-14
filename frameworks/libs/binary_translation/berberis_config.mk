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

# This file defines:
#   BERBERIS_PRODUCT_PACKAGES - list of main product packages
#   BERBERIS_DEV_PRODUCT_PACKAGES - list of development packages
#

include frameworks/libs/native_bridge_support/native_bridge_support.mk

BERBERIS_PRODUCT_PACKAGES := \
    libberberis_exec_region

BERBERIS_PRODUCT_PACKAGES_RISCV64_TO_X86_64 := \
    libberberis_proxy_libEGL \
    libberberis_proxy_libGLESv1_CM \
    libberberis_proxy_libGLESv2 \
    libberberis_proxy_libGLESv3 \
    libberberis_proxy_libOpenMAXAL \
    libberberis_proxy_libOpenSLES \
    libberberis_proxy_libaaudio \
    libberberis_proxy_libamidi \
    libberberis_proxy_libandroid \
    libberberis_proxy_libbinder_ndk \
    libberberis_proxy_libc \
    libberberis_proxy_libcamera2ndk \
    libberberis_proxy_libjnigraphics \
    libberberis_proxy_libmediandk \
    libberberis_proxy_libnativehelper \
    libberberis_proxy_libnativewindow \
    libberberis_proxy_libneuralnetworks \
    libberberis_proxy_libwebviewchromium_plat_support \
    berberis_prebuilt_riscv64 \
    berberis_program_runner_binfmt_misc_riscv64 \
    berberis_program_runner_riscv64 \
    libberberis_riscv64

# TODO(b/277625560): Include $(NATIVE_BRIDGE_PRODUCT_PACKAGES) instead
# when all its bits are ready for riscv64.
BERBERIS_PRODUCT_PACKAGES_RISCV64_TO_X86_64 += $(NATIVE_BRIDGE_PRODUCT_PACKAGES_RISCV64_READY)

BERBERIS_DEV_PRODUCT_PACKAGES := \
    berberis_hello_world.native_bridge \
    berberis_hello_world_static.native_bridge \
    berberis_host_tests \
    berberis_ndk_program_tests \
    berberis_ndk_program_tests.native_bridge \
    dwarf_reader \
    libberberis_emulated_libcamera2ndk_api_checker \
    nogrod_unit_tests \
    gen_intrinsics_tests

BERBERIS_DEV_PRODUCT_PACKAGES_RISCV64_TO_X86_64 := \
    berberis_guest_loader_riscv64_tests

BERBERIS_DISTRIBUTION_ARTIFACTS_RISCV64 := \
    system/bin/berberis_program_runner_binfmt_misc_riscv64