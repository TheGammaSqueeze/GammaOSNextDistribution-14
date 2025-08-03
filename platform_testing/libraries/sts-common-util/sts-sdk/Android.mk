LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := sts-sdk-samples.zip
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional

sts_sdk_sample_files := \
	$(sort $(patsubst ./%,%,$(shell find -L $(LOCAL_PATH)/package -type f -and -not -name ".*")))
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): STS_SDK_SAMPLE_FILES := $(sts_sdk_sample_files)
$(LOCAL_BUILT_MODULE): STS_SDK_SAMPLE_ROOT := $(LOCAL_PATH)/package
$(LOCAL_BUILT_MODULE): STS_SDK_TMP_DIR := $(dir $(LOCAL_BUILT_MODULE))/tmp/
$(LOCAL_BUILT_MODULE): $(sts_sdk_sample_files) $(SOONG_ZIP)
	rm -rf $(STS_SDK_TMP_DIR)
	mkdir -p $(STS_SDK_TMP_DIR)
	cp -a $(STS_SDK_SAMPLE_ROOT)/* $(STS_SDK_TMP_DIR)
	for tmplfile in $$(find $(STS_SDK_TMP_DIR) -type f -iname "*.template"); do \
		echo $${tmplfile}; \
		sed -i 's~{{PLATFORM_SDK_VERSION}}~$(PLATFORM_SDK_VERSION)~g' $${tmplfile}; \
		mv $${tmplfile} $${tmplfile/.template/}; \
	done
	# Build system can't cleanly handle hidden files
	mv $(STS_SDK_TMP_DIR)/dotidea $(STS_SDK_TMP_DIR)/.idea
	$(SOONG_ZIP) -o $@ -C $(STS_SDK_TMP_DIR) -D $(STS_SDK_TMP_DIR) -D $(STS_SDK_TMP_DIR)/.idea

sts_sdk_sample_files :=
