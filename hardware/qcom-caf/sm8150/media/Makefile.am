# Makefile.am - Automake script for mm-omxvideo
#
ACLOCAL_AMFLAGS = -I m4

BUILD_COMPONENTS := mm-core libc2dcolorconvert libplatformconfig libarbitrarybytes

if BUILD_MM_VIDEO
BUILD_COMPONENTS += mm-video-v4l2
endif

if TARGET_HYPERVISOR
BUILD_COMPONENTS += hypv-intercept
endif

SUBDIRS := $(BUILD_COMPONENTS)
