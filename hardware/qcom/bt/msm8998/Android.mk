ifneq (,$(call is-vendor-board-qcom))
include $(call all-named-subdir-makefiles,libbt-vendor)
endif # is-vendor-board-platform
