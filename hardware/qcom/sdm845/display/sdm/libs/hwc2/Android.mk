LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../../../common.mk

ifeq ($(use_hwc2),true)

LOCAL_MODULE                  := hwcomposer.$(TARGET_BOARD_PLATFORM)
LOCAL_LICENSE_KINDS           := SPDX-license-identifier-Apache-2.0 SPDX-license-identifier-BSD legacy_not_a_contribution
LOCAL_LICENSE_CONDITIONS      := by_exception_only not_allowed notice
LOCAL_VENDOR_MODULE           := true
LOCAL_MODULE_RELATIVE_PATH    := hw
LOCAL_MODULE_TAGS             := optional
LOCAL_C_INCLUDES              := $(common_includes)
LOCAL_HEADER_LIBRARIES        := display_headers

LOCAL_CFLAGS                  := -Wno-missing-field-initializers -Wno-unused-parameter \
                                 -fcolor-diagnostics\
                                 -DLOG_TAG=\"SDM\" $(common_flags)
LOCAL_CLANG                   := true

LOCAL_SHARED_LIBRARIES        := libsdmcore libqservice libbinder libhardware libhardware_legacy \
                                 libutils libcutils libsync libqdutils libqdMetaData \
                                 libsdmutils libc++ liblog libgrallocutils libui libgpu_tonemapper \
                                 libhidlbase vendor.display.config@1.0 \
                                 android.hardware.graphics.mapper@2.0 \
                                 android.hardware.graphics.mapper@2.1 \
                                 android.hardware.graphics.composer@2.2 \
                                 android.hardware.graphics.composer@2.3 \
                                 android.hardware.graphics.allocator@2.0 \
                                 libdisplaydebug \
                                 hardware.google.light@1.0 \
                                 libdrm \

LOCAL_STATIC_LIBRARIES        := libhistogram

ifeq ($(display_config_version), DISPLAY_CONFIG_1_1)
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.1
endif
ifeq ($(display_config_version), DISPLAY_CONFIG_1_2)
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.2 vendor.display.config@1.1
endif
ifeq ($(display_config_version), DISPLAY_CONFIG_1_3)
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.1
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.2
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.3
endif

# Allow implicit fallthroughs in hwc_display.cpp until they are fixed.
LOCAL_CFLAGS                  += -Wno-error=implicit-fallthrough

LOCAL_SRC_FILES               := hwc_session.cpp \
                                 hwc_session_services.cpp \
                                 hwc_display.cpp \
                                 hwc_display_primary.cpp \
                                 hwc_display_external.cpp \
                                 hwc_display_virtual.cpp \
                                 hwc_debugger.cpp \
                                 hwc_buffer_sync_handler.cpp \
                                 hwc_color_manager.cpp \
                                 hwc_layers.cpp \
                                 hwc_callbacks.cpp \
                                 cpuhint.cpp \
                                 hwc_tonemapper.cpp \
                                 display_null.cpp \
                                 hwc_socket_handler.cpp \
                                 hwc_buffer_allocator.cpp \
                                 hwc_display_external_test.cpp

include $(BUILD_SHARED_LIBRARY)
endif
