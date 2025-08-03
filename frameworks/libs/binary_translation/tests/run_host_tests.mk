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

################################################################################
#
# Run host tests
#
# Tests are being run with errors ignored, so that they don't break the build.
# Gtest reports should be parsed as a separate step (b/34749275).
# If test crashes before writing into xml, failure is not detected.
# Thus, we create malformed xml result before launching the test.
#
################################################################################

.PHONY: berberis_host_tests_result

.PHONY: berberis_run_host_tests

# TODO(b/295236834): Add berberis_host_tests_result to berberis_all once the tests pass in
# post-submit.  They are currently failing due to unimplemented bit vector instructions in
# stock builds.
# berberis_all: berberis_host_tests_result

test_dir := $(call intermediates-dir-for,PACKAGING,berberis_tests)

gen_failure_template := $(BERBERIS_DIR)/tests/gen_gtest_failure_template.py
runner_riscv64 := $(HOST_OUT)/bin/berberis_program_runner_riscv64
# Android's make environment only exposes this path as part of CLANG_HOST_GLOBAL_CFLAGS. It is
# difficult to extract it from there. On the other hand it hasn't changed between R and U.
# So we simply hardcode it as it's low maintenance.
host_libc_root := prebuilts/gcc/linux-x86/host/x86_64-linux-glibc2.17-4.8

test_guard := $(test_dir)/remove_me_to_trigger_tests_run
test_trigger := $(test_dir)/test_run_trigger


$(test_guard):
	echo dummy > $@

$(test_trigger): $(test_guard)
	-rm $<
	echo dummy > $@


# Run gtest
# $(1): test name
# $(2): result path
# $(3): binary path
# $(4): env
define run_test

$(2): $(3) $(gen_failure_template) $(test_trigger)
	$(gen_failure_template) berberis_host_tests $(1) >$(2)
	-$(4) $(3) --gtest_output=xml:$(2)

endef


# Run x86_64_riscv64 gtest
# $(1): test name
# $(2): result path
# $(3): binary path
# $(4): env
define run_test_x86_64_riscv64

$(2): $(3) $(runner_riscv64) $(gen_failure_template) $(test_trigger)
	$(gen_failure_template) berberis_host_tests_riscv64 $(1) >$(2)
	# Force running with the prebuilt host libc due to b/254755879.
	-$(4) LD_LIBRARY_PATH=$(host_libc_root)/x86_64-linux/lib64:$(host_libc_root)/sysroot/usr/lib \
		$(host_libc_root)/sysroot/usr/lib/ld-linux-x86-64.so.2 \
		$(runner_riscv64) $(3) --gtest_output=xml:$(2)

endef


# Add gtest to run
# $(1): test name
# $(2): run rule
# $(3): binary path
# $(4): env
define add_test

# Rule to create result file.
$(call $(2),$(1),$(test_dir)/$(1)_result.xml,$(3),$(4))

berberis_host_tests_result: $(test_dir)/$(1)_result.xml

$$(call dist-for-goals,berberis_host_tests_result, $(test_dir)/$(1)_result.xml:gtest/$(1)_result.xml)

# Rule to check result file for errors.
.PHONY: $(1)_check_errors
$(1)_check_errors: $(test_dir)/$(1)_result.xml
	grep "testsuites.*failures=\"0\"" $(test_dir)/$(1)_result.xml

berberis_run_host_tests: $(1)_check_errors

endef


# ATTENTION: no spaces or line continuations around test name!

ifeq ($(BUILD_BERBERIS_RISCV64_TO_X86_64),true)

$(eval $(call add_test,berberis_ndk_program_tests,\
	run_test_x86_64_riscv64,\
	$(TARGET_OUT_TESTCASES)/berberis_ndk_program_tests_static.native_bridge/x86_64/berberis_ndk_program_tests_static,\
	))

$(eval $(call add_test,berberis_host_tests,\
	run_test,\
	$(HOST_OUT)/nativetest64/berberis_host_tests/berberis_host_tests))

endif  # BUILD_BERBERIS_RISCV64_TO_X86_64


test_dir :=
gen_failure_template :=
runner_riscv64 :=
host_libc_root :=
test_guard :=
test_trigger :=
run_test :=
run_test_x86_64_riscv64 :=
add_test :=
