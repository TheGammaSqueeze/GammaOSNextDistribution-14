#
# CHRE API Makefile
#

# TODO: Consider switching out the included headers and compilation
# configuration based on the version of the CHRE API provided here. This is
# currently only used for nanoapp header generation which only supports
# targetting the current API version.

include $(CHRE_PREFIX)/chre_api/chre_api_version.mk

# Common Compiler Flags ########################################################

# Include paths.
COMMON_CFLAGS += -I$(CHRE_PREFIX)/chre_api/include

# Allows including chre.h directly
COMMON_CFLAGS += -I$(CHRE_PREFIX)/chre_api/include/chre_api
