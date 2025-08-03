#
# TFLM Makefile
#
# This file is automatically included by default.
# Please add USE_TFLM=true and TFLM=path_to_tflm to enable TFLM.

ifeq ($(USE_TFLM),true)

# Environment Checks ###########################################################

ifeq ($(TFLM_PATH),)
$(error "TFLM_PATH is empty. You must supply a TFLM_PATH environment \
         variable containing a path to the TFLM library. Example: \
         export TFLM_PATH=$$(CHRE_PREFIX)/external/tflm/latest")
endif

# TFLM Source Files ############################################################

TFLM_SRCS = $(shell find $(TFLM_PATH) \( -name '*.cc' -o -name '*.c' \))

ifeq ($(TFLM_SRCS),)
$(error "Your $$TFLM_PATH is empty. Please download the latest TFLM using \
         external/tflm/tflm_sync_srcs.sh")
endif

COMMON_SRCS += TFLM_SRCS

# TFLM Required flags ##########################################################

COMMON_CFLAGS += -I$(TFLM_PATH)
COMMON_CFLAGS += -I$(TFLM_PATH)/third_party
COMMON_CFLAGS += -I$(TFLM_PATH)/third_party/flatbuffers/include
COMMON_CFLAGS += -I$(TFLM_PATH)/third_party/gemmlowp

# TFLM uses <complex> which requires including several SDK headers
ifneq ($(HEXAGON_SDK_PREFIX),)
HEXAGON_CFLAGS += -I$(HEXAGON_SDK_PREFIX)/libs/qurt/latest/include/posix
HEXAGON_CFLAGS += -I$(HEXAGON_SDK_PREFIX)/libs/qurt/latest/include/qurt
endif

COMMON_CFLAGS += -DTF_LITE_STATIC_MEMORY

endif
