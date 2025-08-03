#
# Copyright (C) 2022 The Android Open Source Project
# Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# It's a very simplified version of upstream Makefile which is
# just enough build gensrc/Gensrc*.gmk files which re-generate
# .java files from template files.

ANDROID_BUILD_TOP := $(realpath $(shell pwd)/../../../../../)

# Define command line tools
MKDIR := mkdir
RM := rm -f # ignore if the file doesn't exist
MV := mv
JAVAC := javac
TOUCH := touch
SED := sed
PRINTF := printf
# Define NEWLINE. See https://www.gnu.org/software/make/manual/html_node/Multi_002dLine.html
define NEWLINE


endef

# Define BUILD_TOOLS and rules to build BUILD_TOOLS
# BUILD_TOOLS is defined in the upstream ToolsJdk.gmk
SUPPORT_OUTPUTDIR := $(ANDROID_BUILD_TOP)/libcore/ojluni/src/tools/make/out
BUILD_TOOLS_JDK_DIR := $(SUPPORT_OUTPUTDIR)/jdk_tools_classes
BUILD_TOOLS_JDK := $(BUILD_TOOLS_JDK_DIR)/build/tools/spp/Spp.class
TOOL_SPP = java -cp $(BUILD_TOOLS_JDK_DIR) build.tools.spp.Spp
SPP_SRC := $(ANDROID_BUILD_TOP)/libcore/ojluni/src/tools/build/tools/spp/Spp.java

$(BUILD_TOOLS_JDK): $(SPP_SRC)
	$(MKDIR) -p $(BUILD_TOOLS_JDK_DIR)
	$(JAVAC) -d $(BUILD_TOOLS_JDK_DIR) $(SPP_SRC)

ExecuteWithLog = $(2) | tee -a $(1)
LogAll = $(foreach v,                                        \
      $(.VARIABLES), \
      $(info $(v) = $($(v))))

MakeTargetDir = mkdir -p $(dir $@)

# Define variables only needed for GensrcCharsetEncoder.gmk
GENSRC_CHARSETCODER_SRC := $(ANDROID_BUILD_TOP)/libcore/ojluni/src/main/java/java/nio

include gensrc/GensrcCharsetCoder.gmk

# Define variables only needed for GensrcBuffer.gmk
GENSRC_BUFFER_SRC := $(ANDROID_BUILD_TOP)/libcore/ojluni/src/main/java/java/nio

include gensrc/GensrcBuffer.gmk

all: \
  $(GENSRC_CHARSETCODER) \
  $(GENSRC_BUFFER)

clean:
	rm -rf $(BUILD_TOOLS_JDK_DIR)
	rm -f $(GENSRC_CHARSETCODER)
	rm -f $(GENSRC_BUFFER)
