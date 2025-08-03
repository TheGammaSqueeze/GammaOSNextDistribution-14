#
# Clang config
#

# Environment Checks ##########################################################
ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif

# Clang toolchain path ########################################################
CLANG_TOOLCHAIN_PATH=$(ANDROID_BUILD_TOP)/prebuilts/clang/host/linux-x86/clang-r498229b
IS_CLANG_TOOLCHAIN=true
