#
# Copyright (C) 2024 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

LOCAL_PATH := $(call my-dir)

_shim_hidl_library_name     := android.hardware.radio.c_shim
_frontend_hidl_package_name := android.hardware.radio.config
_backend_hidl_package_name  := lineage.hardware.radio.config
_frontend_hidl_interface_name   := IRadioConfig
_backend_hidl_interface_name    := $(_frontend_hidl_interface_name)

ifeq ($(TARGET_IS_64_BIT),true)
_lib_dir := lib64
else
_lib_dir := lib
endif

define _shim_hidl_library_template
	_version := $(1)
	_sed_pattern := "s|$$(_frontend_hidl_package_name)@$$(_version)::$$(_frontend_hidl_interface_name)|$$(_backend_hidl_package_name)@$$(_version)::$$(_backend_hidl_interface_name)|g;s|$$(_frontend_hidl_package_name)(@[0-9]+\.[0-9]+\.so)|$$(_shim_hidl_library_name)\1|g"

	include $$(CLEAR_VARS)

	LOCAL_MODULE       := $$(_shim_hidl_library_name)@$$(_version)

	LOCAL_MODULE_STEM  := $$(LOCAL_MODULE).so
	LOCAL_MODULE_CLASS := DATA
	LOCAL_MODULE_TAGS  := optional
	LOCAL_MODULE_PATH  := $$(TARGET_OUT_VENDOR)/$$(_lib_dir)
	LOCAL_REQUIRED_MODULES := $$(_frontend_hidl_package_name)@$$(_version).vendor
	LOCAL_PREBUILT_MODULE_FILE := $$(TARGET_OUT_VENDOR)/$$(_lib_dir)/$$(_frontend_hidl_package_name)@$$(_version).so
	LOCAL_POST_INSTALL_CMD := /usr/bin/sed -E -i $$(_sed_pattern) $$(LOCAL_MODULE_PATH)/$$(LOCAL_MODULE_STEM)
	include $$(BUILD_PREBUILT)
endef

$(eval $(call _shim_hidl_library_template,1.0))
$(eval $(call _shim_hidl_library_template,1.1))
$(eval $(call _shim_hidl_library_template,1.2))
$(eval $(call _shim_hidl_library_template,1.3))
