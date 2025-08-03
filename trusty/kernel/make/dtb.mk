#
# Copyright (c) 2022, Google, Inc. All rights reserved
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

# Build a devicetree blob module for Trusty
#
# This makefile generates a .dtb from a .dts specified by MODULE_DTS, then
# generates a .c that includes the .dtb with an .incbin directive and builds the
# generated source file with library.mk.
#
# args:
# MODULE : module name (required)
# MODULE_DTS : a .dts file (required)
# MODULE_DTC_FLAGS : flags passed to dtc invocation
# MODULE_DTS_INCLUDES : list of include directories used to preprocess the .dts

MODULE_DTS_INCLUDES := $(foreach inc,$(MODULE_DTS_INCLUDES),$(addprefix -I,$(inc)))

MODULE_CPP_DTS := $(patsubst %.dts,$(BUILDDIR)/%.cpp.dts,$(MODULE_DTS))
MODULE_DTB := $(patsubst %.cpp.dts,%.dtb,$(MODULE_CPP_DTS))
MODULE_DTB_C := $(patsubst %.dtb,%.dtb.c,$(MODULE_DTB))

DTC_PREBUILT := prebuilts/misc/linux-x86/dtc/dtc

# Preprocess the .dts files
$(MODULE_CPP_DTS): MODULE_DTS_INCLUDES := $(MODULE_DTS_INCLUDES)
$(MODULE_CPP_DTS): $(BUILDDIR)/%.cpp.dts: %.dts
	@$(MKDIR)
	$(NOECHO)$(CC) -E -nostdinc $(MODULE_DTS_INCLUDES) -undef -D__DTS__ \
		-x assembler-with-cpp -o $@ $<

# Compile each .dts into a .dtb
$(MODULE_DTB): MODULE_DTC_FLAGS := $(MODULE_DTC_FLAGS)
$(MODULE_DTB): DTC_PREBUILT := $(DTC_PREBUILT)
$(MODULE_DTB): %.dtb: %.cpp.dts
	@$(MKDIR)
	$(NOECHO)$(DTC_PREBUILT) -O dtb -o $@ $(MODULE_DTC_FLAGS) --symbols $<

# We generate a symbol name by taking the module name and replacing
# all non-alphanumeric characters in it with underscores.
# We need to do this separately for every input file since the symbol
# names need to all be different. The command needs to be executed
# as a shell command inside the recipe.
DT_SYM_MANGLE_CMD = `printf "dtb_sym_$(basename $<)" | tr -c '[:alnum:]' '_'`

# Generate the .c that embeds the .dtb in the module.
$(MODULE_DTB_C): %.dtb.c: %.dtb
	@$(MKDIR)
	$(NOECHO) printf "#include <lk/compiler.h>\n" > $@
	$(NOECHO) printf "INCBIN_ALIGNED($(DT_SYM_MANGLE_CMD), \
		$(DT_SYM_MANGLE_CMD)_size, \"$<\", \".dtb\", 8);\n" >> $@

# Add the generated .c to the module sources
MODULE_SRCS += \
	$(MODULE_DTB_C) \

include make/library.mk

MODULE_DTS :=
MODULE_DTC_FLAGS :=
MODULE_DTS_INCLUDES :=

MODULE_CPP_DTS :=
MODULE_DTB :=
MODULE_DTB_C :=

DTC_PREBUILT :=
