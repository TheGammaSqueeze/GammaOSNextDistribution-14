#
# Copyright (c) 2020, Google, Inc. All rights reserved
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Generate and add constant headers, if necessary
#
# args:
# MODULE : module name (required)
# PY3 : Path of the Python 3 interpreter to use to run the manifest compiler
#       script.
# MODULE_CONSTANTS : JSON files with constants used for both the manifest and C
# 		headers (optional) (CONSTANTS is a deprecated equivalent to
# 		MODULE_CONSTANTS)
# MANIFEST : manifest for the application (optional)
# MANIFEST_OVERLAY : additional overlay manifests for the application (optional)
#
# outputs:
# TRUSTY_APP_MANIFEST_BIN : manifest binary name, if MANIFEST
#
# If neither MODULE_CONSTANTS nor MANIFEST are set, this file does nothing.

ifneq ($(strip $(MODULE_CONSTANTS)),)
MODULE_INCLUDES += \
	$(BUILDDIR)/constants/include
endif

ifneq ($(strip $(MODULE_CONSTANTS)$(MANIFEST)),)

CONSTANTS_HEADER_DIR := $(BUILDDIR)/constants/include

ifeq ($(strip $(MANIFEST_COMPILER)),)
MANIFEST_COMPILER := trusty/user/base/tools/manifest_compiler.py
endif

# build manifest objects if we are building an app, otherwise generate shared
# constants headers if constants provided
ifeq ($(call TOBOOL,$(TRUSTY_APP)),true)

TRUSTY_APP_MANIFEST_BIN := $(BUILDDIR)/$(TRUSTY_APP_NAME).manifest

# Save the manifest path for use in user-tasks.mk
_MODULES_$(MODULE)_TRUSTY_APP_MANIFEST_BIN := $(TRUSTY_APP_MANIFEST_BIN)
$(INFO_DONE $(MODULE),generating manifest,$(TRUSTY_APP_MANIFEST_BIN))

# TODO Until the SDK supports library variants, this flag will only work as
# intended for applications that have no library dependencies.
$(TRUSTY_APP_MANIFEST_BIN): TRUSTY_APP_ENABLE_SCS :=
ifeq (false,$(call TOBOOL,$(TRUSTY_APP_DISABLE_SCS)))
ifeq (true,$(call TOBOOL,$(SCS_ENABLED)))
$(TRUSTY_APP_MANIFEST_BIN): TRUSTY_APP_ENABLE_SCS := --enable-shadow-call-stack
endif
endif
ifdef ARCH_$(ARCH)_DEFAULT_USER_SHADOW_STACK_SIZE
$(TRUSTY_APP_MANIFEST_BIN): DEFAULT_USER_SHADOW_STACK_SIZE := \
--default-shadow-call-stack-size $(ARCH_$(ARCH)_DEFAULT_USER_SHADOW_STACK_SIZE)
else
$(TRUSTY_APP_MANIFEST_BIN): DEFAULT_USER_SHADOW_STACK_SIZE :=
endif
$(TRUSTY_APP_MANIFEST_BIN): MANIFEST_COMPILER := $(MANIFEST_COMPILER)
$(TRUSTY_APP_MANIFEST_BIN): PY3 := $(PY3)
$(TRUSTY_APP_MANIFEST_BIN): CONFIG_CONSTANTS := $(MODULE_CONSTANTS)
$(TRUSTY_APP_MANIFEST_BIN): MODULE := $(MODULE)
$(TRUSTY_APP_MANIFEST_BIN): MANIFEST := $(MANIFEST)
$(TRUSTY_APP_MANIFEST_BIN): MANIFEST_OVERLAY := $(MANIFEST_OVERLAY)
$(TRUSTY_APP_MANIFEST_BIN): HEADER_DIR := $(CONSTANTS_HEADER_DIR)
$(TRUSTY_APP_MANIFEST_BIN): $(MANIFEST) $(MANIFEST_OVERLAY) $(MANIFEST_COMPILER) $(MODULE_CONSTANTS)
	@$(MKDIR)
	@$(call ECHO,$(MODULE),compiling,$< to $@)
	$(NOECHO)$(PY3) $(MANIFEST_COMPILER) $(addprefix -i,$(MANIFEST) $(MANIFEST_OVERLAY)) -o $@ $(addprefix -c,$(CONFIG_CONSTANTS)) --header-dir $(HEADER_DIR) \
	$(TRUSTY_APP_ENABLE_SCS) $(DEFAULT_USER_SHADOW_STACK_SIZE)
	@$(call ECHO_DONE_SILENT,$(MODULE),compiling,$< to $@)

# We need the constants headers to be generated before the sources are compiled
MODULE_SRCDEPS += $(TRUSTY_APP_MANIFEST_BIN)

else # we are not building an app

ifneq ($(strip $(MODULE_CONSTANTS)),)

# generate shared constants headers if only constants and no manifest provided
# generate also a hidden generated for each module that will
# say that constants are already generated for this module
$(CONSTANTS_HEADER_DIR)/.generated/$(MODULE): MANIFEST_COMPILER := $(MANIFEST_COMPILER)
$(CONSTANTS_HEADER_DIR)/.generated/$(MODULE): PY3 := $(PY3)
$(CONSTANTS_HEADER_DIR)/.generated/$(MODULE): MODULE := $(MODULE)
$(CONSTANTS_HEADER_DIR)/.generated/$(MODULE): CONFIG_CONSTANTS := $(MODULE_CONSTANTS)
$(CONSTANTS_HEADER_DIR)/.generated/$(MODULE): HEADER_DIR := $(CONSTANTS_HEADER_DIR)
$(CONSTANTS_HEADER_DIR)/.generated/$(MODULE): $(MANIFEST_COMPILER) $(MODULE_CONSTANTS)
	@$(MKDIR)
	@$(call ECHO,$(MODULE),compiling constants,for $(MODULE))
	$(NOECHO)$(PY3) $(MANIFEST_COMPILER) $(addprefix -c,$(CONFIG_CONSTANTS)) --header-dir $(HEADER_DIR)
	@touch $(HEADER_DIR)/.generated/$(MODULE)
	@$(call ECHO_DONE_SILENT,$(MODULE),compiling constants,for $(MODULE))

MODULE_SRCDEPS += $(CONSTANTS_HEADER_DIR)/.generated/$(MODULE)

endif # MODULE_CONSTANTS is non-empty

endif # TRUSTY_APP = false

endif # MODULE_CONSTANTS and/or MANIFEST is non-empty

CONSTANTS :=
CONSTANTS_HEADER_DIR :=
MODULE_CONSTANTS :=
MANIFEST :=
MANIFEST_OVERLAY :=
