#
# Makefile for Pigweed's tokenizer module
#

# Environment Checks
ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif

# Location of various Pigweed modules
PIGWEED_DIR = $(ANDROID_BUILD_TOP)/external/pigweed
PIGWEED_TOKENIZER_DIR=$(ANDROID_BUILD_TOP)/external/pigweed
PIGWEED_SCRIPTS_DIR=$(PIGWEED_TOKENIZER_DIR)/pw_tokenizer/py/pw_tokenizer

# Variables used by build_template.mk to generate the token mapping
TOKEN_MAP_GEN_CMD     = $(PYTHON) $(PIGWEED_SCRIPTS_DIR)/database.py create \
                          --force --type binary --database
TOKEN_MAP_CSV_GEN_CMD = $(PYTHON) $(PIGWEED_SCRIPTS_DIR)/database.py create \
                          --force --type csv --database

# Pigweed source files
COMMON_SRCS += $(PIGWEED_DIR)/pw_tokenizer/encode_args.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_tokenizer/tokenize.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_varint/varint.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_varint/varint_c.c

# Pigweed include paths
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_containers/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_log_tokenized/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_log/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/standard_library_public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_preprocessor/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_span/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_span/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_tokenizer/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_varint/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/third_party/fuchsia/repo/sdk/lib/stdcompat/include