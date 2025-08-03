# This is the top-level build file for the Android HW OpenGL ES emulation
# in Android.
#
# You must define BUILD_EMULATOR_OPENGL to 'true' in your environment to
# build the following files.
#
# Also define BUILD_EMULATOR_OPENGL_DRIVER to 'true' to build the gralloc
# stuff as well.
#
# Top-level for all modules
GOLDFISH_OPENGL_PATH := $(call my-dir)

HARDWARE_GOOGLE_GFXSTREAM_PATH := $(GOLDFISH_OPENGL_PATH)/../../../hardware/google/gfxstream

ifeq (true,$(GOLDFISH_OPENGL_BUILD_FOR_HOST))
ENABLE_GOLDFISH_OPENGL_FOLDER := true
else
ifneq ($(filter $(GOLDFISH_OPENGL_PATH),$(PRODUCT_SOONG_NAMESPACES)),)
ENABLE_GOLDFISH_OPENGL_FOLDER := true
endif
endif

ifeq (true,$(ENABLE_GOLDFISH_OPENGL_FOLDER))

# There are two kinds of builds for goldfish-opengl:
# 1. The standard guest build, denoted by BUILD_EMULATOR_OPENGL
# 2. The host-side build, denoted by GOLDFISH_OPENGL_BUILD_FOR_HOST
#
# Variable controlling whether the build for goldfish-opengl
# libraries (including their Android.mk's) should be triggered.
GOLDFISH_OPENGL_SHOULD_BUILD := false

# In the host build, some libraries have name collisions with
# other libraries, so we have this variable here to control
# adding a suffix to the names of libraries. Should be blank
# for the guest build.
GOLDFISH_OPENGL_LIB_SUFFIX :=

# Directory containing common headers used by several modules
# This is always set to a module's LOCAL_C_INCLUDES
# See the definition of emugl-begin-module in common.mk
EMUGL_COMMON_INCLUDES := \
    $(HARDWARE_GOOGLE_GFXSTREAM_PATH)/guest/iostream/include/libOpenglRender \
    $(HARDWARE_GOOGLE_GFXSTREAM_PATH)/guest/include

# This is always set to a module's LOCAL_CFLAGS
# See the definition of emugl-begin-module in common.mk
EMUGL_COMMON_CFLAGS :=

# Whether or not to build the Vulkan library.
GFXSTREAM := false

# Host build
ifeq (true,$(GOLDFISH_OPENGL_BUILD_FOR_HOST))

GOLDFISH_OPENGL_SHOULD_BUILD := true
GOLDFISH_OPENGL_LIB_SUFFIX := _host

GFXSTREAM := true

# Set modern defaults for the codename, version, etc.
PLATFORM_VERSION_CODENAME:=Q
PLATFORM_SDK_VERSION:=29
IS_AT_LEAST_OPD1:=true

# The host-side Android framework implementation
HOST_EMUGL_PATH := $(GOLDFISH_OPENGL_PATH)/../../../external/qemu/android/android-emugl
EMUGL_COMMON_INCLUDES += $(HOST_EMUGL_PATH)/guest

EMUGL_COMMON_CFLAGS += \
    -DPLATFORM_SDK_VERSION=29 \
    -DGOLDFISH_HIDL_GRALLOC \
    -DHOST_BUILD \
    -DANDROID \
    -DGL_GLEXT_PROTOTYPES \
    -fvisibility=default \
    -DPAGE_SIZE=4096 \
    -DGFXSTREAM \
    -DENABLE_ANDROID_HEALTH_MONITOR \
    -Wno-unused-parameter

endif # GOLDFISH_OPENGL_BUILD_FOR_HOST

ifeq (true,$(BUILD_EMULATOR_OPENGL)) # Guest build

GOLDFISH_OPENGL_SHOULD_BUILD := true

EMUGL_COMMON_CFLAGS += -DPLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION)

ifeq (O, $(PLATFORM_VERSION_CODENAME))
EMUGL_COMMON_CFLAGS += -DGOLDFISH_HIDL_GRALLOC
endif

ifeq ($(shell test $(PLATFORM_SDK_VERSION) -gt 25 && echo isApi26OrHigher),isApi26OrHigher)
EMUGL_COMMON_CFLAGS += -DGOLDFISH_HIDL_GRALLOC
endif

ifeq ($(shell test $(PLATFORM_SDK_VERSION) -lt 18 && echo PreJellyBeanMr2),PreJellyBeanMr2)
    ifeq ($(ARCH_ARM_HAVE_TLS_REGISTER),true)
        EMUGL_COMMON_CFLAGS += -DHAVE_ARM_TLS_REGISTER
    endif
endif
ifeq ($(shell test $(PLATFORM_SDK_VERSION) -lt 16 && echo PreJellyBean),PreJellyBean)
    EMUGL_COMMON_CFLAGS += -DALOG_ASSERT=LOG_ASSERT
    EMUGL_COMMON_CFLAGS += -DALOGE=LOGE
    EMUGL_COMMON_CFLAGS += -DALOGW=LOGW
    EMUGL_COMMON_CFLAGS += -DALOGD=LOGD
    EMUGL_COMMON_CFLAGS += -DALOGV=LOGV
endif

ifeq ($(shell test $(PLATFORM_SDK_VERSION) -gt 27 && echo isApi28OrHigher),isApi28OrHigher)
    GFXSTREAM := true
    EMUGL_COMMON_CFLAGS += -DGFXSTREAM
endif

# Include common definitions used by all the modules included later
# in this build file. This contains the definition of all useful
# emugl-xxxx functions.
#
include $(GOLDFISH_OPENGL_PATH)/common.mk

endif # BUILD_EMULATOR_OPENGL (guest build)

ifeq (true,$(GOLDFISH_OPENGL_SHOULD_BUILD))

# Uncomment the following line if you want to enable debug traces
# in the GLES emulation libraries.
# EMUGL_COMMON_CFLAGS += -DEMUGL_DEBUG=1

# IMPORTANT: ORDER IS CRUCIAL HERE
#
# For the import/export feature to work properly, you must include
# modules below in correct order. That is, if module B depends on
# module A, then it must be included after module A below.
#
# This ensures that anything exported by module A will be correctly
# be imported by module B when it is declared.
#
# Note that the build system will complain if you try to import a
# module that hasn't been declared yet anyway.
#
ifneq (true,$(GOLDFISH_OPENGL_BUILD_FOR_HOST))
include $(GOLDFISH_OPENGL_PATH)/system/hals/Android.mk
endif

ifeq ($(shell test $(PLATFORM_SDK_VERSION) -gt 28 -o $(IS_AT_LEAST_QPR1) = true && echo isApi29OrHigher),isApi29OrHigher)
    # hardware codecs enabled after P
    include $(GOLDFISH_OPENGL_PATH)/system/codecs/omx/common/Android.mk
    include $(GOLDFISH_OPENGL_PATH)/system/codecs/omx/plugin/Android.mk
    include $(GOLDFISH_OPENGL_PATH)/system/codecs/omx/avcdec/Android.mk
    include $(GOLDFISH_OPENGL_PATH)/system/codecs/omx/vpxdec/Android.mk
endif

endif

endif # ENABLE_GOLDFISH_OPENGL_FOLDER
