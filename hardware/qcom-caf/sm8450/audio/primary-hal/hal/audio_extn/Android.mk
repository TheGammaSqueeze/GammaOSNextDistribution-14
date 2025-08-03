LOCAL_PATH := $(call my-dir)

#-------------------------------------------
#            Build HFP LIB
#-------------------------------------------
include $(CLEAR_VARS)

ifeq ($(SOONG_CONFIG_android_hardware_audio_run_64bit), true)
LOCAL_MULTILIB := 64
endif

LOCAL_MODULE := libhfp_pal
LOCAL_VENDOR_MODULE := true

ifeq ($(TARGET_BOARD_AUTO),true)
  LOCAL_CFLAGS += -DPLATFORM_AUTO
endif

LOCAL_SRC_FILES:= Hfp.cpp

LOCAL_CFLAGS += \
    -Wall \
    -Werror \
    -Wno-unused-function \
    -Wno-unused-variable

LOCAL_SHARED_LIBRARIES := \
    libaudioroute \
    libaudioutils \
    libcutils \
    libdl \
    libexpat \
    liblog \
    libar-pal

LOCAL_C_INCLUDES := \
    $(call project-path-for,qcom-audio)/pal \
    $(call project-path-for,qcom-audio)/primary-hal/hal \
    $(call project-path-for,qcom-audio)/primary-hal/hal/audio_extn \
    external/expat/lib \
    system/media/audio_utils/include \
    $(call include-path-for, audio-route) \

LOCAL_HEADER_LIBRARIES += libhardware_headers
LOCAL_HEADER_LIBRARIES += libsystem_headers
include $(BUILD_SHARED_LIBRARY)

#-------------------------------------------
#            Build FM LIB
#-------------------------------------------
include $(CLEAR_VARS)

ifeq ($(SOONG_CONFIG_android_hardware_audio_run_64bit), true)
LOCAL_MULTILIB := 64
endif

LOCAL_MODULE := libfmpal
LOCAL_VENDOR_MODULE := true

LOCAL_SRC_FILES:= FM.cpp

LOCAL_CFLAGS += \
    -Wall \
    -Werror \
    -Wno-unused-function \
    -Wno-unused-variable

LOCAL_SHARED_LIBRARIES := \
    libaudioroute \
    libaudioutils \
    libcutils \
    libdl \
    libexpat \
    liblog \
    libar-pal

LOCAL_C_INCLUDES := \
    $(call project-path-for,qcom-audio)/pal \
    $(call project-path-for,qcom-audio)/primary-hal/hal \
    $(call project-path-for,qcom-audio)/primary-hal/hal/audio_extn \
    external/expat/lib \
    system/media/audio_utils/include \
    $(call include-path-for, audio-route) \

LOCAL_HEADER_LIBRARIES += libhardware_headers
LOCAL_HEADER_LIBRARIES += libsystem_headers
include $(BUILD_SHARED_LIBRARY)

#-------------------------------------------
#            Build BATTERY_LISTENER
#-------------------------------------------
include $(CLEAR_VARS)

LOCAL_MODULE := libbatterylistener
LOCAL_VENDOR_MODULE := true

LOCAL_SRC_FILES:= battery_listener.cpp

LOCAL_CFLAGS := \
    -Wall \
    -Werror \
    -Wno-unused-function \
    -Wno-unused-variable

ifneq ($(filter bengal blair,$(TARGET_BOARD_PLATFORM)),)
    LOCAL_CFLAGS += -DQTI_HEALTH
endif

LOCAL_SHARED_LIBRARIES := \
    android.hardware.health@1.0 \
    android.hardware.health@2.0 \
    android.hardware.health@2.1 \
    android.hardware.power@1.2 \
    libaudioutils \
    libbase \
    libcutils \
    libdl \
    libhidlbase \
    liblog \
    libutils \

ifneq ($(filter bengal blair,$(TARGET_BOARD_PLATFORM)),)
    LOCAL_SHARED_LIBRARIES += \
        android.hardware.health-V1-ndk \
        libbinder_ndk
endif

LOCAL_STATIC_LIBRARIES := libhealthhalutils

include $(BUILD_SHARED_LIBRARY)
