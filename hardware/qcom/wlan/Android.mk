#set TARGET_USES_HARDWARE_QCOM_WLAN to false to disable this project.
ifneq ($(TARGET_USES_HARDWARE_QCOM_WLAN),false)
  ifneq ($(filter wcn6740,$(BOARD_WLAN_CHIP)),)
    include $(call all-named-subdir-makefiles,wcn6740)
  else
	include $(call all-named-subdir-makefiles,legacy)
  endif
endif
