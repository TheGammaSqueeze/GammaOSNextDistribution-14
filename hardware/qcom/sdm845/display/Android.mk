ifneq ($(TARGET_DISABLE_DISPLAY),true)
sdm-libs := sdm/libs
display-hals := include libdebug $(sdm-libs)/utils $(sdm-libs)/core

ifneq ($(TARGET_IS_HEADLESS), true)
    display-hals += libcopybit liblight libmemtrack hdmi_cec \
                    $(sdm-libs)/hwc2 gpu_tonemapper libdrmutils libdisplayconfig libhistogram drm.vendor
endif

display-hals += gralloc

ifneq ($(BUILD_WITHOUT_VENDOR),true)
ifneq (,$(call is-vendor-board-qcom))
    include $(call all-named-subdir-makefiles,$(display-hals))
else
ifneq ($(filter msm% apq%,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,$(display-hals))
endif
endif
endif
endif #TARGET_DISABLE_DISPLAY
