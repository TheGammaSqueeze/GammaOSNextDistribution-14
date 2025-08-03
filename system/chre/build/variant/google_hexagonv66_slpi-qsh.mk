#
# Google CHRE Reference Implementation for Hexagon v66 Architecture on SLPI
# using QSH.
#

include $(CHRE_PREFIX)/build/clean_build_template_args.mk

TARGET_NAME = google_hexagonv66_slpi-qsh
# Sized based on the buffer allocated in the host daemon (4096 bytes), minus
# FlatBuffer overhead (max 80 bytes), minus some extra space to make a nice
# round number and allow for addition of new fields to the FlatBuffer
TARGET_CFLAGS = -DCHRE_MESSAGE_TO_HOST_MAX_SIZE=4000
TARGET_CFLAGS += -DCHRE_QSH_ENABLED
TARGET_CFLAGS += -DCHRE_USE_FARF_LOGGING
TARGET_CFLAGS += $(GOOGLE_HEXAGONV66_SLPI-QSH_CFLAGS)
TARGET_CFLAGS += -DCHRE_FIRST_SUPPORTED_API_VERSION=CHRE_API_VERSION_1_2
TARGET_VARIANT_SRCS = $(GOOGLE_HEXAGONV66_SLPI-QSH_SRCS)
TARGET_SO_LATE_LIBS = $(GOOGLE_HEXAGONV66_SLPI-QSH_LATE_LIBS)
TARGET_PLATFORM_ID = 0x476f6f676c00000a
HEXAGON_ARCH = v66

TARGET_CFLAGS += $(SLPI_CFLAGS)
TARGET_VARIANT_SRCS += $(SLPI_SRCS)

# Add SLPI/QSH-specific compiler flags and source files
TARGET_CFLAGS += $(SLPI_QSH_CFLAGS)
TARGET_VARIANT_SRCS += $(SLPI_QSH_SRCS)

CHRE_USE_BUFFERED_LOGGING = false

ifneq ($(filter $(TARGET_NAME)% all, $(MAKECMDGOALS)),)
ifneq ($(IS_NANOAPP_BUILD),)
TARGET_SO_LATE_LIBS += $(CHRE_PREFIX)/build/app_support/google_slpi/libchre_slpi_skel.so
include $(CHRE_PREFIX)/build/nanoapp/google_slpi.mk
endif

include $(CHRE_PREFIX)/build/arch/hexagon.mk
include $(CHRE_PREFIX)/build/build_template.mk
endif
