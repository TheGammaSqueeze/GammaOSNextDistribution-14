ifneq ($(filter sm8150 sm7150,$(PRODUCT_PLATFORM)),)
ifneq ($(BUILD_WITHOUT_VENDOR), true)
LOCAL_PATH := $(call my-dir)
subdir_makefiles=$(call first-makefiles-under,$(LOCAL_PATH))

ifeq ($(PRODUCT_PLATFORM),sm7150)
SKIP_BUILD_DIRS :=    \
  thermal
SKIP_MAKEFILES := $(call all-named-subdir-makefiles, $(SKIP_BUILD_DIRS))
subdir_makefiles := $(filter-out $(SKIP_MAKEFILES), $(subdir_makefiles))
endif

$(foreach mk,$(subdir_makefiles),$(info including $(mk) ...)$(eval include $(mk)))
endif
endif
