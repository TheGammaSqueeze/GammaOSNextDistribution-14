# Copyright (C) 2018 The Android Open Source Project
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

PROJECT_QEMU_INC_LOCAL_DIR := $(GET_LOCAL_DIR)

APPLOADER_ALLOW_NS_CONNECT := true

# Include Secretkeeper TA
SECRETKEEPER_ENABLED := true

include project/$(QEMU_TRUSTY_PROJECT).mk

# limit physical memory to 29 bits to make the mapping
# as small as possible while covering the entire kernel plus
# enough pages for the page tables for the initial mappings
GLOBAL_DEFINES += MMU_IDENT_SIZE_SHIFT=29

# Qemu statically built with glibc<2.29 uses a syscall to implement
# clock_gettime instead of the VDSO, which makes the calls much slower
APP_TIMERTEST_MAX_CLOCK_PERIOD := 2000

# Derive RPMB key using HKDF
WITH_HKDF_RPMB_KEY ?= true

# Always allow provisioning for emulator builds
STATIC_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED := 1

# Emulator builds are unlocked by default. qemu-generic-arm32-test-debug
# overrides this to ensure that we have at least one target that tests with app
# loading locked.
STATIC_SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED ?= 1

MODULES += \
	trusty/user/app/storage/rpmb_dev \

RPMB_DEV := $(BUILDDIR)/host_tools/rpmb_dev

# Enable unittests that should only run in the emulator
STORAGE_UNITTEST_ON_EMULATOR := true

PROJECT_KEYS_DIR := $(PROJECT_QEMU_INC_LOCAL_DIR)/keys

APPLOADER_SIGN_PRIVATE_KEY_0_FILE := \
	$(PROJECT_KEYS_DIR)/apploader_sign_test_private_key_0.der

APPLOADER_SIGN_PUBLIC_KEY_0_FILE := \
	$(PROJECT_KEYS_DIR)/apploader_sign_test_public_key_0.der

APPLOADER_SIGN_PRIVATE_KEY_1_FILE := \
	$(PROJECT_KEYS_DIR)/apploader_sign_test_private_key_1.der

APPLOADER_SIGN_PUBLIC_KEY_1_FILE := \
	$(PROJECT_KEYS_DIR)/apploader_sign_test_public_key_1.der

# Key ID 1 should only be allowed if SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED is
# true
APPLOADER_SIGN_KEY_1_UNLOCKED_ONLY := true

# The default signing key is key 0, but each application
# can specify a different key identifier
APPLOADER_SIGN_KEY_ID ?= 0

# Treat key slot 1 as a dev key by default. In tests this key is only expected
# to be enabled if SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED is true.
APPLOADER_SIGN_UNLOCKED_KEY_ID ?= 1

APPLOADER_ENCRYPT_KEY_0_FILE := \
	$(PROJECT_KEYS_DIR)/apploader_encrypt_test_key_0.bin

ATF_DEBUG := 1
ATF_PLAT := qemu
ATF_WITH_TRUSTY_GENERIC_SERVICES := true
ATF_BUILD_BASE := $(abspath $(BUILDDIR)/atf)
ATF_TOOLCHAIN_PREFIX := $(ARCH_arm64_TOOLCHAIN_PREFIX)
ATF_ROOT := $(call FIND_EXTERNAL,arm-trusted-firmware)
include project/qemu-atf-inc.mk

QEMU_ROOT := $(call FIND_EXTERNAL,qemu)
QEMU_BUILD_BASE := $(abspath $(BUILDDIR)/qemu-build)
QEMU_ARCH := aarch64
QEMU_TARGET := aarch64-softmmu,arm-softmmu
include project/qemu-qemu-inc.mk

LINUX_ARCH ?= arm64
include project/linux-inc.mk

EXTRA_BUILDRULES += external/trusty/bootloader/test-runner/test-runner-inc.mk
TEST_RUNNER_BIN := $(BUILDDIR)/test-runner/external/trusty/bootloader/test-runner/test-runner.bin

RUN_QEMU_SCRIPT := $(BUILDDIR)/run-qemu
RUN_SCRIPT := $(BUILDDIR)/run
STOP_SCRIPT := $(BUILDDIR)/stop
QEMU_CONFIG := $(BUILDDIR)/config.json
QEMU_PY := $(BUILDDIR)/qemu.py
QEMU_ERROR_PY := $(BUILDDIR)/qemu_error.py
QEMU_OPTIONS_PY := $(BUILDDIR)/qemu_options.py
QEMU_LLDB_SUPPORT_PY := $(BUILDDIR)/lldb_support.py
QEMU_LLDBINIT := $(BUILDDIR)/lldbinit
PY3_CMD := $(BUILDDIR)/py3-cmd
RUN_PY := $(BUILDDIR)/run.py

$(ATF_OUT_DIR):
	mkdir -p $@

# For ATF bootloader semihosting calls, bl32 and bl33 need to be in place
ATF_SYMLINKS := \
	$(ATF_OUT_DIR)/bl32.bin \
	$(ATF_OUT_DIR)/bl33.bin \

$(ATF_OUT_DIR)/bl32.bin: $(BUILDDIR)/lk.bin $(ATF_OUT_DIR)
	ln -sf $(abspath $<) $@

$(ATF_OUT_DIR)/bl33.bin: $(TEST_RUNNER_BIN) $(ATF_OUT_DIR)
	ln -sf $(abspath $<) $@

ATF_OUT_COPIED_FILES := \
	$(ATF_OUT_DIR)/firmware.android.dts \
	$(ATF_OUT_DIR)/run-qemu-helper \

$(ATF_OUT_COPIED_FILES): $(ATF_OUT_DIR)/% : $(PROJECT_QEMU_INC_LOCAL_DIR)/qemu/% $(ATF_OUT_DIR)
	@echo copying $@
	@cp $< $@

$(ATF_OUT_DIR)/RPMB_DATA: ATF_OUT_DIR := $(ATF_OUT_DIR)
$(ATF_OUT_DIR)/RPMB_DATA: $(RPMB_DEV)
	@echo Initialize rpmb device
	$< --dev $(ATF_OUT_DIR)/RPMB_DATA --init --size 2048

QEMU_SCRIPTS := \
	$(QEMU_PY) \
	$(QEMU_ERROR_PY) \
	$(QEMU_OPTIONS_PY) \
	$(QEMU_LLDB_SUPPORT_PY) \
	$(QEMU_LLDBINIT) \
	$(RUN_PY)

$(QEMU_SCRIPTS): .PHONY
EXTRA_BUILDDEPS += $(QEMU_SCRIPTS)

# Copied so that the resulting build tree contains all files needed to run
$(QEMU_PY): $(PROJECT_QEMU_INC_LOCAL_DIR)/qemu/qemu.py
	@echo copying $@
	@cp $< $@

# Copied so that the resulting build tree contains all files needed to run
$(QEMU_ERROR_PY): $(PROJECT_QEMU_INC_LOCAL_DIR)/qemu/qemu_error.py
	@echo copying $@
	@cp $< $@

# Script used to generate qemu architecture options. Need to specify qemu
# options file name since different projects use different python script
$(QEMU_OPTIONS_PY): $(PROJECT_QEMU_INC_LOCAL_DIR)/qemu/qemu_arm64_options.py
	@echo copying $@
	@cp $< $@

# Script used in LLDB for setting breakpoints adjusted for KASLR and ASLR
$(QEMU_LLDB_SUPPORT_PY): $(PROJECT_QEMU_INC_LOCAL_DIR)/qemu/lldb_support.py
	@echo copying $@
	@cp $< $@

# Companion script to lldb_support.py that contains LLDB commands
$(QEMU_LLDBINIT): $(PROJECT_QEMU_INC_LOCAL_DIR)/qemu/lldbinit
	@echo copying $@
	@cp $< $@

# Python version of the run shell script which can be imported by other Python
# scripts for more granular control over how tests share emulator instances.
# It serves as a wrapper around qemu.py which defaults the config.
$(RUN_PY): $(PROJECT_QEMU_INC_LOCAL_DIR)/qemu/run.py
	@echo copying $@
	@cp $< $@

# Copy prebuilt hermetic Python 3 command into the build directory so that the
# build does not rely on the host having Python 3 installed. Hermetic python 3
# contains the standard library so this is all we need to run the qemu scripts
$(PY3_CMD): $(BUILDTOOLS_BINDIR)/py3-cmd
	@echo copying $@
	@$(MKDIR)
	@cp $< $@

EXTRA_BUILDDEPS += $(PY3_CMD)

# List of files we need from Android
ANDROID_OUT_FILES := \
	out/host/linux-x86/bin/adb \
	out/target/product/trusty/system.img \
	out/target/product/trusty/vendor.img \
	out/target/product/trusty/userdata.img \
	out/target/product/trusty/data/nativetest64 \

# Copy Android prebuilts into the build directory so that the build does not
# depend on any files in the source tree. We want to package the build artifacts
# without any dependencies on the sources.
# Because files in the prebuilts directory are not writeable the directory must
# be cleared before copying in the fresh content. `rm -rf` is used to accomplish
# this because it bypasses writing un-writeable files in addition to bringing
# the target directory to the same state as with a clean build.
ANDROID_OUT_BUILD_DIR := $(BUILDDIR)/aosp/android

ifneq (,$(ANDROID_BUILD_TOP))
# We are building Trusty inside an Android environment,
# which means we can use a fresh Android build instead of prebuilts
ANDROID_OUT_SRC_DIR := $(ANDROID_BUILD_TOP)
else
ANDROID_OUT_SRC_DIR := trusty/prebuilts/aosp/android
endif

ANDROID_OUT_SRC_FILES := $(addprefix $(ANDROID_OUT_SRC_DIR)/,$(ANDROID_OUT_FILES))

# Copy the files listed in ANDROID_OUT_FILES from ANDROID_OUT_SRC_DIR into
# ANDROID_OUT_BUILD_DIR preserving the directory structure relative to the
# top-level ANDROID_OUT_SRC_DIR directory
$(ANDROID_OUT_BUILD_DIR): ANDROID_OUT_SRC_DIR := $(ANDROID_OUT_SRC_DIR)
$(ANDROID_OUT_BUILD_DIR): ANDROID_OUT_FILES := $(ANDROID_OUT_FILES)
$(ANDROID_OUT_BUILD_DIR): $(ANDROID_OUT_SRC_FILES)
	@echo creating Android output directory
	@rm -rf $@
	@mkdir -p $@
	@cd $(ANDROID_OUT_SRC_DIR) && cp -r --parents -t $@ $(ANDROID_OUT_FILES)

EXTRA_BUILDDEPS += $(ANDROID_OUT_BUILD_DIR)

# Save variables to a json file to export paths known to the build system to
# the test system
$(QEMU_CONFIG): QEMU_BIN := $(subst $(BUILDDIR)/,,$(QEMU_BIN))
$(QEMU_CONFIG): EXTRA_QEMU_FLAGS := ["-machine", "gic-version=$(GIC_VERSION)"]
$(QEMU_CONFIG): ATF_OUT_DIR := $(subst $(BUILDDIR)/,,$(ATF_OUT_DIR))
$(QEMU_CONFIG): LINUX_BUILD_DIR := $(subst $(BUILDDIR)/,,$(LINUX_BUILD_DIR))
$(QEMU_CONFIG): LINUX_ARCH := $(LINUX_ARCH)
$(QEMU_CONFIG): ANDROID_OUT_BUILD_DIR := $(subst $(BUILDDIR)/,,$(ANDROID_OUT_BUILD_DIR))
$(QEMU_CONFIG): RPMB_DEV := $(subst $(BUILDDIR)/,,$(RPMB_DEV))
$(QEMU_CONFIG): $(ATF_OUT_COPIED_FILES) $(ATF_SYMLINKS) $(ATF_OUT_DIR)/RPMB_DATA
	@echo generating $@
	@echo '{ "linux": "$(LINUX_BUILD_DIR)",' > $@
	@echo '  "linux_arch": "$(LINUX_ARCH)",' >> $@
	@echo '  "atf": "$(ATF_OUT_DIR)", ' >> $@
	@echo '  "qemu": "$(QEMU_BIN)", ' >> $@
	@echo '  "extra_qemu_flags": $(EXTRA_QEMU_FLAGS), ' >> $@
	@echo '  "android": "$(ANDROID_OUT_BUILD_DIR)", ' >> $@
	@echo '  "rpmbd": "$(RPMB_DEV)", ' >> $@
	@echo '  "arch": "$(ARCH)" }' >> $@

EXTRA_BUILDDEPS += $(QEMU_CONFIG)

# Create a wrapper script around run-qemu-helper which defaults arguments to
# those needed to run this build
$(RUN_QEMU_SCRIPT): QEMU_BIN := $(subst $(BUILDDIR)/,,$(QEMU_BIN))
$(RUN_QEMU_SCRIPT): ATF_OUT_DIR := $(subst $(BUILDDIR)/,,$(ATF_OUT_DIR))
$(RUN_QEMU_SCRIPT): LINUX_BUILD_DIR := $(subst $(BUILDDIR)/,,$(LINUX_BUILD_DIR))
$(RUN_QEMU_SCRIPT): $(ATF_OUT_COPIED_FILES) $(ATF_SYMLINKS) $(ATF_OUT_DIR)/RPMB_DATA
	@echo generating $@
	@echo "#!/bin/sh" >$@
	@echo 'SCRIPT_DIR=$$(dirname "$$0")' >>$@
	@echo 'cd "$$SCRIPT_DIR/$(ATF_OUT_DIR)"' >>$@
	@echo 'KERNEL_DIR="$$SCRIPT_DIR/$(LINUX_BUILD_DIR)" QEMU="$$SCRIPT_DIR/$(QEMU_BIN)" ./run-qemu-helper "$$@"' >>$@
	@chmod +x $@

EXTRA_BUILDDEPS += $(RUN_QEMU_SCRIPT)

# The original run shell script was replaced by run.py. Create symlink to
# preserve backwards compatibility.
$(RUN_SCRIPT): $(RUN_PY)
	@echo creating $@
	@ln -sf $(abspath $<) $@

EXTRA_BUILDDEPS += $(RUN_SCRIPT)

# Create a script to stop all stale emulators.
$(STOP_SCRIPT):
	@echo generating $@
	@echo "#!/bin/sh" >$@
	@echo 'killall qemu-system-aarch64' >>$@
	@chmod +x $@

EXTRA_BUILDDEPS += $(STOP_SCRIPT)

ifeq (true,$(call TOBOOL,$(PACKAGE_QEMU_TRUSTY)))

# Files & directories to copy into QEMU package archive
QEMU_PACKAGE_FILES := \
	$(OUTBIN) $(QEMU_SCRIPTS) $(PY3_CMD) $(QEMU_CONFIG) $(RPMB_DEV) \
	$(RUN_SCRIPT) $(RUN_QEMU_SCRIPT) $(STOP_SCRIPT) $(ANDROID_OUT_BUILD_DIR) \
	$(QEMU_BIN) $(ATF_SYMLINKS) $(ATF_OUT_DIR)/bl31.bin \
	$(ATF_OUT_DIR)/RPMB_DATA $(ATF_OUT_COPIED_FILES) $(LINUX_IMAGE) \

# Other files/directories that should be included in the package but which are
# not make targets and therefore cannot be pre-requisites. The target that
# creates these files must be in the QEMU_PACKAGE_FILES variable.
QEMU_PACKAGE_EXTRA_FILES := \
	$(LINUX_BUILD_DIR)/arch $(LINUX_BUILD_DIR)/scripts $(ATF_BUILD_BASE) \
	$(QEMU_BUILD_BASE) \

include project/qemu-package-inc.mk
endif

ANDROID_OUT_FILES :=
ANDROID_OUT_BUILD_DIR :=
ANDROID_OUT_SRC_DIR :=
ANDROID_OUT_SRC_FILES :=
ATF_BUILD_BASE :=
ATF_OUT_COPIED_FILES :=
ATF_OUT_DIR :=
ATF_SYMLINKS :=
LINUX_ARCH :=
LINUX_BUILD_DIR :=
LINUX_IMAGE :=
RUN_QEMU_SCRIPT :=
RUN_SCRIPT :=
TEST_RUNNER_BIN :=
QEMU_BIN :=
QEMU_BUILD_BASE :=
QEMU_CONFIG :=
QEMU_ERROR_PY :=
QEMU_OPTIONS_PY :=
QEMU_LLDB_SUPPORT_PY :=
QEMU_LLDBINIT :=
QEMU_PY :=
QEMU_SCRIPTS :=
PY3_CMD :=
RUN_PY :=
