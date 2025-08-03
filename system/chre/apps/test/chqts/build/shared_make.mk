ifndef NANOAPP_NAME
$(error NANOAPP_NAME unset)
endif

ifndef NANOAPP_SRC_FILES
$(error NANOAPP_SRC_FILES unset)
endif

ifndef CHRE_PREFIX
ifndef ANDROID_BUILD_TOP
$(error Must supply CHRE_PREFIX or set Android build environment first)
else
CHRE_PREFIX = $(ANDROID_BUILD_TOP)/system/chre
endif
endif

TEST_SHARED_PATH = $(CHRE_PREFIX)/apps/test/common/shared
NANOAPP_DIR_NAME ?= $(NANOAPP_NAME)
NANOAPP_SRC_PATH = $(CHRE_PREFIX)/apps/test/chqts/src

SHARED_LIB_FILES = abort.cc \
  chunk_allocator.cc \
  nano_endian.cc \
  nano_string.cc \
  send_message.cc \
  test_success_marker.cc

COMMON_SRCS += \
  $(addprefix $(NANOAPP_SRC_PATH)/$(NANOAPP_DIR_NAME)/, $(NANOAPP_SRC_FILES)) \
  $(addprefix $(NANOAPP_SRC_PATH)/shared/, $(SHARED_LIB_FILES))

# Add util srcs since they may be included by the tests
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/ble.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/buffer_base.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/dynamic_vector_base.cc
COMMON_SRCS += $(TEST_SHARED_PATH)/src/audio_validation.cc

COMMON_CFLAGS += -DCHRE_NO_ENDIAN_H \
  -D__LITTLE_ENDIAN=1 \
  -D__BYTE_ORDER=1 \
  -D__BIG_ENDIAN=2 \
  -DCHRE_ASSERTIONS_ENABLED

COMMON_CFLAGS += -I$(NANOAPP_SRC_PATH)
COMMON_CFLAGS += -I$(CHRE_PREFIX)/util/include
COMMON_CFLAGS += -I$(TEST_SHARED_PATH)/inc

OPT_LEVEL=2

# Permission declarations ######################################################

CHRE_NANOAPP_USES_AUDIO = true
CHRE_NANOAPP_USES_GNSS = true
CHRE_NANOAPP_USES_WIFI = true
CHRE_NANOAPP_USES_WWAN = true
CHRE_NANOAPP_USES_BLE = true


include $(CHRE_PREFIX)/build/nanoapp/app.mk
