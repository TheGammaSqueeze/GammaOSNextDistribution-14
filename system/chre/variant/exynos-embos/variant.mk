
ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif

# Variant Prefix ###############################################################

VARIANT_PREFIX = $(ANDROID_BUILD_TOP)/system/chre/variant

# Chre Version String ##########################################################

COMMIT_HASH_COMMAND = git describe --always --long --dirty
COMMIT_HASH = $(shell $(COMMIT_HASH_COMMAND))

COMMON_CFLAGS += -DCHRE_VERSION_STRING="\"chre=embos@$(COMMIT_HASH)\""

# Common Compiler Flags ########################################################

# Supply a symbol to indicate that the build variant supplies the static
# nanoapp list.
COMMON_CFLAGS += -DCHRE_VARIANT_SUPPLIES_STATIC_NANOAPP_LIST

# CHRE event count #############################################################

EMBOS_CFLAGS += -DCHRE_EVENT_PER_BLOCK=32
EMBOS_CFLAGS += -DCHRE_MAX_EVENT_BLOCKS=4

# Optional Features ############################################################

CHRE_AUDIO_SUPPORT_ENABLED = true
CHRE_GNSS_SUPPORT_ENABLED = false
CHRE_SENSORS_SUPPORT_ENABLED = true
CHRE_WIFI_SUPPORT_ENABLED = false
CHRE_WWAN_SUPPORT_ENABLED = false
CHRE_BLE_SUPPORT_ENABLED = false

# Common Source Files ##########################################################

COMMON_SRCS += $(VARIANT_PREFIX)/exynos-embos/static_nanoapps.cc

