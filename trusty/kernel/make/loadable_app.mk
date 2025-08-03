#
# Copyright (c) 2021, Google, Inc. All rights reserved
#
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files
# (the "Software"), to deal in the Software without restriction,
# including without limitation the rights to use, copy, modify, merge,
# publish, distribute, sublicense, and/or sell copies of the Software,
# and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
# CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
# TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#

# The following set of variables must can be passed to trusty_app.mk:
#
#     APP_NAME - an output file name (without extension)
#     APP_TOP_MODULE - top module to compile
#     APP_BUILDDIR - build directory
#
# To sign the app with a different key than the default one from
# APPLOADER_SIGN_KEY_ID, set the following variable in rules.mk:
#     APPLOADER_SIGN_KEY_ID_FOR_$(MODULE)
#
# To encrypt an application, set the similar encryption variable:
#     APPLOADER_ENCRYPT_KEY_ID_FOR_$(MODULE)
#
# The following variable is modified and can be used by the caller:
#     LOADABLE_APP_LIST - list of loadable app locations

# Build a loadable application
ifeq ($(strip $(LOADABLE_APP_TOOL)),)
LOADABLE_APP_TOOL := $(BUILDDIR)/host_tools/apploader_package_tool
endif

ifeq ($(strip $(APP_ELF)),)
APP_ELF := $(_MODULES_$(APP_TOP_MODULE)_TRUSTY_APP_ELF)
endif
ifeq ($(strip $(APP_MANIFEST)),)
APP_MANIFEST := $(_MODULES_$(APP_TOP_MODULE)_TRUSTY_APP_MANIFEST_BIN)
endif

INITIAL_APP := $(patsubst %.elf,%.app.initial,$(APP_ELF))
LOADABLE_APP := $(patsubst %.elf,%.app,$(APP_ELF))

$(INITIAL_APP): LOADABLE_APP_TOOL := $(LOADABLE_APP_TOOL)
$(INITIAL_APP): LOG_NAME := $(APP_TOP_MODULE)
$(INITIAL_APP): $(APP_ELF) $(APP_MANIFEST) $(LOADABLE_APP_TOOL)
	@$(MKDIR)
	@$(call ECHO,$(LOG_NAME),building app,$@)
	$(NOECHO)$(LOADABLE_APP_TOOL) -m build $@ $< $(word 2,$^)
	@$(call ECHO_DONE_SILENT,$(LOG_NAME),building app,$@)

ifneq ($(APPLOADER_ENCRYPT_KEY_ID_FOR_$(APP_TOP_MODULE)),)
APP_ENCRYPT_KEY_ID := $(APPLOADER_ENCRYPT_KEY_ID_FOR_$(APP_TOP_MODULE))
APP_ENCRYPT_KEY_FILE := $(APPLOADER_ENCRYPT_KEY_$(APP_ENCRYPT_KEY_ID)_FILE)
endif

ifneq ($(APP_ENCRYPT_KEY_FILE),)
ENCRYPTED_APP := $(patsubst %.elf,%.app.encrypted,$(APP_ELF))

$(ENCRYPTED_APP): LOADABLE_APP_TOOL := $(LOADABLE_APP_TOOL)
$(ENCRYPTED_APP): APP_ENCRYPT_KEY_FILE := $(APP_ENCRYPT_KEY_FILE)
$(ENCRYPTED_APP): APP_ENCRYPT_KEY_ID := $(APP_ENCRYPT_KEY_ID)
$(ENCRYPTED_APP): LOG_NAME := $(APP_TOP_MODULE)
$(ENCRYPTED_APP): $(INITIAL_APP) $(APP_ENCRYPT_KEY_FILE) $(LOADABLE_APP_TOOL)
	@$(MKDIR)
	@$(call ECHO,$(LOG_NAME),building app,$@)
	$(NOECHO)$(LOADABLE_APP_TOOL) -m encrypt $@ $< \
		$(APP_ENCRYPT_KEY_FILE) $(APP_ENCRYPT_KEY_ID)
	@$(call ECHO_DONE_SILENT,$(LOG_NAME),building app,$@)

UNSIGNED_APP := $(ENCRYPTED_APP)
else
UNSIGNED_APP := $(INITIAL_APP)
endif

# If we have an app-specific key identifier then use it,
# otherwise use the global default
ifneq ($(APPLOADER_SIGN_KEY_ID_FOR_$(APP_TOP_MODULE)),)
APP_SIGN_KEY_ID := $(APPLOADER_SIGN_KEY_ID_FOR_$(APP_TOP_MODULE))
else
APP_SIGN_KEY_ID := $(APPLOADER_SIGN_KEY_ID)
endif

ifneq ($(APP_SIGN_KEY_ID),)
APP_SIGN_KEY_FILE := $(APPLOADER_SIGN_PRIVATE_KEY_$(APP_SIGN_KEY_ID)_FILE)
endif

ifneq ($(APP_SIGN_KEY_FILE),)
$(LOADABLE_APP): LOADABLE_APP_TOOL := $(LOADABLE_APP_TOOL)
$(LOADABLE_APP): APP_SIGN_KEY_FILE := $(APP_SIGN_KEY_FILE)
$(LOADABLE_APP): APP_SIGN_KEY_ID := $(APP_SIGN_KEY_ID)
$(LOADABLE_APP): LOG_NAME := $(APP_TOP_MODULE)
$(LOADABLE_APP): $(UNSIGNED_APP) $(APP_SIGN_KEY_FILE) $(LOADABLE_APP_TOOL)
	@$(MKDIR)
	@$(call ECHO,$(LOG_NAME),building app,$@)
	$(NOECHO)$(LOADABLE_APP_TOOL) -m sign $@ $< \
		$(APP_SIGN_KEY_FILE) $(APP_SIGN_KEY_ID)
	@$(call ECHO_DONE_SILENT,$(LOG_NAME),building app,$@)
else
# If we don't have a signature file, just use the unsigned file as the output
# This is needed because modules that import loadable apps, e.g.,
# app-mgmt-test, need the app files to exist
# Note: apploader will refuse to load the unsigned application
$(LOADABLE_APP): LOG_NAME := $(APP_TOP_MODULE)
$(LOADABLE_APP): $(UNSIGNED_APP)
	@$(MKDIR)
	@$(call ECHO,$(LOG_NAME),building app,$@)
	@cp $< $@
	@$(call ECHO_DONE_SILENT,$(LOG_NAME),building app,$@)

$(warning Loadable application is not signed: $(LOADABLE_APP))
endif

GENERATED += $(LOADABLE_APP)
EXTRA_BUILDDEPS += $(LOADABLE_APP)
LOADABLE_APP_LIST += $(LOADABLE_APP)

# Reset local variables
APP_NAME :=
APP_BUILDDIR :=
APP_TOP_MODULE :=

LOADABLE_APP_TOOL :=
APP_ELF :=
APP_MANIFEST :=

INITIAL_APP :=
UNSIGNED_APP :=
ENCRYPTED_APP :=
LOADABLE_APP :=

APP_SIGN_KEY_ID :=
APP_SIGN_KEY_FILE :=
APP_ENCRYPT_KEY_ID :=
APP_ENCRYPT_KEY_FILE :=
