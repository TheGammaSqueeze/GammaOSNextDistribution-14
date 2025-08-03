IS_BUILD_REQUIRING_FLAG_LIBRARY = true
ACONFIG_EMB_DIR = $(CHRE_PREFIX)/build/aconfig_embedded_flagging
ACONFIG_BIN = $(ANDROID_BUILD_TOP)/prebuilts/build-tools/linux-x86/bin/aconfig
ACONFIG_FLAG_BUILD_SCRIPT = $(ACONFIG_EMB_DIR)/build_flags.sh
COMMON_ACONFIG_FLAG_VALUES = $(ACONFIG_EMB_DIR)/trunk-chre_embedded_flag_values.textproto
COMMON_CFLAGS += -I$(ACONFIG_EMB_DIR)/out/chre_embedded_flag_lib/include