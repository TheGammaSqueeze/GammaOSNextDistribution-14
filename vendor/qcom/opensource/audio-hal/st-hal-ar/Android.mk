ifneq ($(AUDIO_USE_STUB_HAL), true)
ifeq ($(strip $(BOARD_SUPPORTS_OPENSOURCE_STHAL)),true)
ifeq ($(TARGET_USES_QCOM_MM_AUDIO),true)
ifeq ($(TARGET_USES_QCOM_AUDIO_AR),true)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ARM_MODE             := arm
LOCAL_MODULE               := sound_trigger.primary.$(TARGET_BOARD_PLATFORM)
LOCAL_MODULE_TAGS          := optional
LOCAL_MODULE_OWNER         := qti
LOCAL_MODULE_RELATIVE_PATH := hw
ifeq ($(SOONG_CONFIG_android_hardware_audio_run_64bit), true)
LOCAL_MULTILIB             := 64
else
LOCAL_MULTILIB             := $(AUDIOSERVER_MULTILIB)
endif
LOCAL_VENDOR_MODULE        := true

LOCAL_CFLAGS += -Wall -Werror
LOCAL_CFLAGS += -DSOUND_TRIGGER_PLATFORM=$(TARGET_BOARD_PLATFORM)

LOCAL_C_INCLUDES += \
    system/media/audio_utils/include \
    external/expat/lib \
    vendor/qcom/opensource/core-utils/fwk-detect \
    $(call project-path-for,qcom-audio)/pal \
    $(call project-path-for,qcom-audio)/primary-hal/hal/audio_extn \
    $(call project-path-for,qcom-audio)/primary-hal/hal

LOCAL_SRC_FILES := \
    SoundTriggerDevice.cpp \
    SoundTriggerSession.cpp

LOCAL_HEADER_LIBRARIES := \
    libarpal_headers \
    libhardware_headers \
    libsystem_headers

LOCAL_SHARED_LIBRARIES := \
    libbase \
    liblog \
    libcutils \
    libdl \
    libaudioutils \
    libexpat \
    libhidlbase \
    libprocessgroup \
    libutils \
    libar-pal

ifneq ($(QCPATH),)
ifeq ($(strip $(AUDIO_FEATURE_ENABLED_LSM_HIDL)),true)
    LOCAL_HEADER_LIBRARIES += liblisten_headers

    LOCAL_SHARED_LIBRARIES += \
        vendor.qti.hardware.ListenSoundModel@1.0-impl \
        vendor.qti.hardware.ListenSoundModel@1.0

    LOCAL_CFLAGS += -DLSM_HIDL_ENABLED
endif
endif #QCPATH

include $(BUILD_SHARED_LIBRARY)
endif #TARGET_USES_QCOM_AUDIO_AR
endif #TARGET_USES_QCOM_MM_AUDIO
endif #BOARD_SUPPORTS_OPENSOURCE_STHAL
endif #AUDIO_USE_STUB_HAL
