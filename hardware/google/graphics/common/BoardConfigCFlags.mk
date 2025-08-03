# ***************************************************************
# Vendor libs in hardware/google/graphics refer to this make file
# ***************************************************************

#for Build Split
ifeq ($(BOARD_USES_VENDORIMAGE), true)
LOCAL_PROPRIETARY_MODULE := true
endif

LOCAL_CFLAGS += -DUSES_GSCALER

ifeq ($(HWC_NO_SUPPORT_SKIP_VALIDATE),true)
	LOCAL_CFLAGS += -DHWC_NO_SUPPORT_SKIP_VALIDATE
endif

ifeq ($(HWC_SUPPORT_COLOR_TRANSFORM), true)
    LOCAL_CFLAGS += -DHWC_SUPPORT_COLOR_TRANSFORM
endif

ifeq ($(HWC_SUPPORT_RENDER_INTENT), true)
    LOCAL_CFLAGS += -DHWC_SUPPORT_RENDER_INTENT
endif

ifeq ($(BOARD_USES_VIRTUAL_DISPLAY), true)
	LOCAL_CFLAGS += -DUSES_VIRTUAL_DISPLAY
endif

ifeq ($(BOARD_USES_DT), true)
	LOCAL_CFLAGS += -DUSES_DT
endif

ifeq ($(TARGET_ARCH), arm64)
	LOCAL_CFLAGS += -DUSES_ARCH_ARM64
endif

ifeq ($(BOARD_USES_DECON_64BIT_ADDRESS), true)
	LOCAL_CFLAGS += -DUSES_DECON_64BIT_ADDRESS
endif

ifeq ($(BOARD_USES_HDRUI_GLES_CONVERSION), true)
    LOCAL_CFLAGS += -DUSES_HDR_GLES_CONVERSION
endif

ifeq ($(USES_IDISPLAY_INTF_SEC),true)
    LOCAL_CFLAGS += -DUSES_IDISPLAY_INTF_SEC
endif
