# Add any common flags to the module
include make/common_flags.mk

# Install libclang_rt into the SDK
LIBCLANG_RT := $(notdir $(TRUSTY_APP_LIBGCC))
ALL_SDK_LIBS += $(TRUSTY_SDK_LIB_DIR)/$(LIBCLANG_RT)
$(TRUSTY_SDK_LIB_DIR)/$(LIBCLANG_RT): $(TRUSTY_APP_LIBGCC)
	@$(MKDIR)
	$(NOECHO)cp $^ $@

# Install SDK make helper
ALL_SDK_EXTRA_FILES += $(TRUSTY_SDK_DIR)/make/trusty_sdk.mk
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: SDK_CFLAGS := \
	$(GLOBAL_SHARED_OPTFLAGS) $(GLOBAL_USER_OPTFLAGS) $(MODULE_OPTFLAGS) \
	$(GLOBAL_SHARED_COMPILEFLAGS) $(GLOBAL_USER_COMPILEFLAGS) $(ARCH_$(ARCH)_COMPILEFLAGS) $(MODULE_COMPILEFLAGS) \
	$(GLOBAL_SHARED_CFLAGS) $(GLOBAL_USER_CFLAGS) $(ARCH_$(ARCH)_CFLAGS) $(MODULE_CFLAGS)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: SDK_CXXFLAGS := \
	$(GLOBAL_SHARED_OPTFLAGS) $(GLOBAL_USER_OPTFLAGS) $(MODULE_OPTFLAGS) \
	$(GLOBAL_SHARED_COMPILEFLAGS) $(GLOBAL_USER_COMPILEFLAGS) $(ARCH_$(ARCH)_COMPILEFLAGS) $(MODULE_COMPILEFLAGS) \
	$(GLOBAL_SHARED_CPPFLAGS) $(GLOBAL_USER_CPPFLAGS) $(ARCH_$(ARCH)_CPPFLAGS) $(MODULE_CPPFLAGS)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: SDK_ASMFLAGS := \
	$(GLOBAL_SHARED_OPTFLAGS) $(GLOBAL_USER_OPTFLAGS) $(MODULE_OPTFLAGS) \
	$(GLOBAL_SHARED_COMPILEFLAGS) $(GLOBAL_USER_COMPILEFLAGS) $(ARCH_$(ARCH)_COMPILEFLAGS) $(MODULE_COMPILEFLAGS) \
	$(GLOBAL_SHARED_ASMFLAGS) $(GLOBAL_USER_ASMFLAGS) $(ARCH_$(ARCH)_ASMFLAGS) $(MODULE_ASMFLAGS)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: ARCH := $(ARCH)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: ASLR := $(ASLR)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: STANDARD_ARCH_NAME := $(STANDARD_ARCH_NAME)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: TRUSTY_APP_LDFLAGS := $(TRUSTY_APP_BASE_LDFLAGS)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: TRUSTY_APP_ALIGNMENT := $(TRUSTY_APP_ALIGNMENT)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: TRUSTY_APP_MEMBASE := $(TRUSTY_APP_MEMBASE)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: TRUSTY_APP_SYMTAB_ENABLED := $(TRUSTY_APP_SYMTAB_ENABLED)
$(TRUSTY_SDK_DIR)/make/trusty_sdk.mk: trusty/user/base/sdk/make/trusty_sdk.mk
	@$(MKDIR)
	$(NOECHO)rm -f $@.tmp
	$(NOECHO)cp $^ $@.tmp
	$(NOECHO)echo 'ARCH := $(ARCH)' >> $@.tmp
	$(NOECHO)echo 'ASLR := $(ASLR)' >> $@.tmp
	$(NOECHO)echo 'CFLAGS := $(call prepare-sdk-flags,$(SDK_CFLAGS)) $$(CFLAGS)' >> $@.tmp
	$(NOECHO)echo 'CXXFLAGS := $(call prepare-sdk-flags,$(SDK_CXXFLAGS)) $$(CXXFLAGS)' >> $@.tmp
	$(NOECHO)echo 'ASMFLAGS := $(call prepare-sdk-flags,$(SDK_ASMFLAGS)) $$(ASMFLAGS)' >> $@.tmp
	$(NOECHO)echo 'TRUSTY_APP_BASE_LDFLAGS := $$(TRUSTY_APP_LDFLAGS) $(call prepare-sdk-flags,$(TRUSTY_APP_LDFLAGS)) $$(LDFLAGS)' >> $@.tmp
	$(NOECHO)echo 'TRUSTY_APP_ALIGNMENT := $(TRUSTY_APP_ALIGNMENT)' >> $@.tmp
	$(NOECHO)echo 'TRUSTY_APP_MEMBASE := $(TRUSTY_APP_MEMBASE)' >> $@.tmp
	$(NOECHO)echo 'TRUSTY_APP_SYMTAB_ENABLED := $(TRUSTY_APP_SYMTAB_ENABLED)' >> $@.tmp
	$(NOECHO)echo 'TRUSTY_APP_LIBGCC := $$(SDK_SYSROOT_DIR)/usr/lib/$(LIBCLANG_RT)' >> $@.tmp
	$(NOECHO)echo 'SCS_ENABLED := $(SCS_ENABLED)' >> $@.tmp
	$(NOECHO)echo 'STANDARD_ARCH_NAME := $(STANDARD_ARCH_NAME)' >> $@.tmp

	$(NOECHO)echo '# Include the base trusty app makefile which uses the variables provided and' >> $@.tmp
	$(NOECHO)echo '# defined above to link the final app binary.' >> $@.tmp
	$(NOECHO)echo 'ifneq ($$(TRUSTY_APP_NAME),)' >> $@.tmp
	$(NOECHO)echo 'APP_NAME := $$(TRUSTY_APP_NAME)' >> $@.tmp
	$(NOECHO)echo 'APP_ELF := $$(BUILDDIR)/$$(TRUSTY_APP_NAME).elf' >> $@.tmp
	$(NOECHO)echo 'APP_MANIFEST := $$(BUILDDIR)/$$(TRUSTY_APP_NAME).manifest' >> $@.tmp
	$(NOECHO)echo 'include $$(SDK_DIR)/make/trusted_app.mk' >> $@.tmp
	$(NOECHO)echo 'include $$(SDK_DIR)/make/loadable_app.mk' >> $@.tmp
	$(NOECHO)echo 'endif' >> $@.tmp

	$(NOECHO)echo '# Bind MODULE_INCLUDES to compile flags' >> $@.tmp
	$(NOECHO)echo 'MODULE_INCLUDES := $$(addprefix -I,$$(MODULE_INCLUDES))' >> $@.tmp
	$(NOECHO)echo 'CFLAGS := $$(CFLAGS) $$(MODULE_INCLUDES)' >> $@.tmp
	$(NOECHO)echo 'CXXFLAGS := $$(CXXFLAGS) $$(MODULE_INCLUDES)' >> $@.tmp
	$(NOECHO)echo 'ASMFLAGS := $$(ASMFLAGS) $$(MODULE_INCLUDES)' >> $@.tmp

	$(NOECHO)echo '# Add any extra files, e.g. loadable app to default target' >> $@.tmp
	$(NOECHO)echo 'all:: $$(EXTRA_BUILDDEPS)' >> $@.tmp
	$(NOECHO)sed -i 's/ \+$$//' $@.tmp
	$(call TESTANDREPLACEFILE,$@.tmp,$@)

# Include the shared lk headers
EXTRA_SDK_INCLUDES := $(GLOBAL_UAPI_INCLUDES) $(GLOBAL_SHARED_INCLUDES) $(GLOBAL_USER_INCLUDES)
EXTRA_SDK_HEADERS := $(foreach dir,$(sort $(EXTRA_SDK_INCLUDES)),$(wildcard $(dir)/*))
.PHONY: EXTRA_includes
EXTRA_includes: EXTRA_SDK_HEADERS := $(EXTRA_SDK_HEADERS)
EXTRA_includes: $(EXTRA_SDK_HEADERS)
	$(NOECHO)mkdir -p $(TRUSTY_SDK_INCLUDE_DIR)
	-cp -r -L $(EXTRA_SDK_HEADERS) $(TRUSTY_SDK_INCLUDE_DIR)
ALL_SDK_INCLUDES += EXTRA_includes

# Rewrite the exec header from the manifest compiler to remove the extra setup
# we use in-tree to force the use of our hermetic python host binary.
$(TRUSTY_SDK_DIR)/tools/manifest_compiler.py: trusty/user/base/tools/manifest_compiler.py
	@$(MKDIR)
	$(NOECHO)rm -f $@.tmp
	$(NOECHO)printf '#!/usr/bin/env python3\n"""' > $@.tmp
	$(NOECHO)tail -n +8 $^ >> $@.tmp
	$(NOECHO)chmod +x $@.tmp
	$(NOECHO)$(BUILDTOOLS_BINDIR)/py3-cmd $@.tmp --help > /dev/null
	$(call TESTANDREPLACEFILE,$@.tmp,$@)
ALL_SDK_EXTRA_FILES += $(TRUSTY_SDK_DIR)/tools/manifest_compiler.py
