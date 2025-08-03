# Copyright (C) 2020 The Android Open Source Project
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

# Package binary build of Trusty, QEMU, AOSP, and scripts for standalone use

# Inputs:
# QEMU_PACKAGE_FILES: files and folders to include in the package archive
# 		These files/folders must be valid make targets, as they will be included
# 		as pre-requisites to the package zip.
# QEMU_PACKAGE_EXTRA_FILES: additional files and folders to include in the
# 		package archive, which are not make targets. These files must be created
# 		by a target in QEMU_PACKAGE_FILES.

QEMU_PACKAGE_ZIP := $(BUILDDIR)/trusty_qemu_package.zip
QEMU_PACKAGE_LICENSE := $(BUILDDIR)/LICENSE

QEMU_PACKAGE_LICENSE_FILES := \
	$(call FIND_EXTERNAL,qemu/LICENSE) \
	$(call FIND_EXTERNAL,qemu/COPYING) \
	$(call FIND_EXTERNAL,linux/COPYING) \
	$(call FIND_EXTERNAL,linux/LICENSES/preferred/GPL-2.0) \
	$(call FIND_EXTERNAL,linux/LICENSES/exceptions/Linux-syscall-note) \
	$(call FIND_EXTERNAL,arm-trusted-firmware/docs/license.rst) \

# TODO: Unify with SDK license construction when it lands
$(QEMU_PACKAGE_LICENSE): LOCAL_DIR := $(GET_LOCAL_DIR)
$(QEMU_PACKAGE_LICENSE): $(QEMU_PACKAGE_LICENSE_FILES)
	@$(MKDIR)
	@echo Generating QEMU package license
	$(NOECHO)rm -f $@.tmp
	$(NOECHO)cat $(LOCAL_DIR)/../LICENSE >> $@.tmp;
	$(NOECHO)for license in $^; do \
		printf "\n-------------------------------------------------------------------\n" >> $@.tmp;\
		printf "Copied from $$license\n\n\n" >> $@.tmp;\
		cat "$$license" >> $@.tmp;\
		done
	$(call TESTANDREPLACEFILE,$@.tmp,$@)

QEMU_PACKAGE_FILES += $(QEMU_PACKAGE_LICENSE)

$(QEMU_PACKAGE_ZIP): BUILDDIR := $(BUILDDIR)
$(QEMU_PACKAGE_ZIP): QEMU_PACKAGE_EXTRA_FILES := $(QEMU_PACKAGE_EXTRA_FILES)
$(QEMU_PACKAGE_ZIP): $(QEMU_PACKAGE_FILES)
	@$(MKDIR)
	@echo Creating QEMU archive package
	$(NOECHO)rm -f $@
	$(NOECHO)(cd $(BUILDDIR) && zip -q -u -r $@ $(subst $(BUILDDIR)/,,$^))
	$(NOECHO)(cd $(BUILDDIR) && zip -q -u -r $@ $(subst $(BUILDDIR)/,,$(QEMU_PACKAGE_EXTRA_FILES)))

EXTRA_BUILDDEPS += $(QEMU_PACKAGE_ZIP)

QEMU_PACKAGE_CONFIG :=
QEMU_PACKAGE_FILES :=
QEMU_PACKAGE_EXTRA_FILES :=
QEMU_PACKAGE_LICENSE :=
QEMU_PACKAGE_LICENSE_FILES :=
QEMU_PACKAGE_ZIP :=
