#
# Common global constants used as part of the build.
#

# Output Directory Name ########################################################

OUT = out

# Helper Scripts ###############################################################

BE_TO_LE_SCRIPT = $(CHRE_PREFIX)/build/be_to_le.sh

# Build verbosity ##############################################################

ifneq ($(CHRE_BUILD_VERBOSE),true)
V=@
endif

# Default tools definitions ####################################################

PYTHON ?= python3

