#
# Copyright 2020 Arm Ltd. All rights reserved.
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

# This file contains system partition contents needed for a minimal
# Android build that boots to shell. The items here should be present in
# build/make/target/product/base_system.mk.

PRODUCT_PACKAGES += \
    adbd_system_api \
    apexd \
    boringssl_self_test \
    cgroups.json \
    com.android.adbd \
    com.android.conscrypt \
    debuggerd \
    gsid \
    init.environ.rc \
    init_system \
    libbinder \
    libc.bootstrap \
    libdl.bootstrap \
    libdl_android.bootstrap \
    libm.bootstrap \
    libstdc++ \
    linker \
    logcat \
    logd \
    odsign \
    remount \
    run-as \
    selinux_policy_system \
    servicemanager \
    shell_and_utilities_system \
    task_profiles.json \
    tombstoned \
    vold \

PRODUCT_HOST_PACKAGES += \
    adb \

PRODUCT_COPY_FILES += \
    system/core/rootdir/init.usb.rc:system/etc/init/hw/init.usb.rc \
    system/core/rootdir/init.usb.configfs.rc:system/etc/init/hw/init.usb.configfs.rc \
    system/core/rootdir/etc/hosts:system/etc/hosts \
    art/tools/public.libraries.buildbot.txt:system/etc/public.libraries.txt

PRODUCT_SYSTEM_PROPERTIES += debug.atrace.tags.enableflags=0

PRODUCT_PACKAGES_DEBUG := \
    strace \
    su \
