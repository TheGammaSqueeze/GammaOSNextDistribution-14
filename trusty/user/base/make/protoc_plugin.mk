# Copyright (c) 2022, Google, Inc. All rights reserved
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

# Invoke a protoc command with a custom plugin for protobuf-driven code
# generation and build a library from the generated sources.
#
# args:
# MODULE : module name (required)
# MODULE_PROTOC_PLUGIN: path to a python protoc plugin (required)
# MODULE_PROTOC_PLUGIN_FLAGS: optional flags for the custom plugin
#                             shared via env variable
# MODULE_PROTOS: list of PROTO files
# MODULE_PROTO_PACKAGE: a path that matches the directory structure of
#                       the PROTO package utilized in the module.

PROTOC_TOOL := $(if $(wildcard out/host/linux-x86/bin/aprotoc),out/host/linux-x86/bin/aprotoc,prebuilts/libprotobuf/bin/protoc)

ifeq ($(MODULE_PROTOC_PLUGIN),)
$(error No MODULE_PROTOC_PLUGIN provided for $(MODULE))
endif

# WARNING: this implies all sources are under the same package.
# TODO(b/259511922): support multiple packages.
MODULE_SRCS := $(call TOBUILDDIR,$(patsubst %.proto,%.c,$(MODULE_PROTOS)))
MODULE_PROTO_OUT_DIR := $(sort $(dir $(subst $(MODULE_PROTO_PACKAGE),,$(MODULE_SRCS))))

# TODO: support multiple, disparate packages;
# the output directory for the tool should be at the root of
# the package path.
$(MODULE_SRCS): PROTOC_TOOL := $(PROTOC_TOOL)
$(MODULE_SRCS): MODULE_PROTOC_PLUGIN := $(MODULE_PROTOC_PLUGIN)
$(MODULE_SRCS): MODULE_PROTOC_PLUGIN_FLAGS := $(MODULE_PROTOC_PLUGIN_FLAGS)
$(MODULE_SRCS): MODULE_PROTO_PACKAGE := $(MODULE_PROTO_PACKAGE)
$(MODULE_SRCS): MODULE_PROTO_OUT_DIR := $(MODULE_PROTO_OUT_DIR)
$(MODULE_SRCS): $(BUILDDIR)/%.c: %.proto $(MODULE_PROTOC_PLUGIN)
	@$(MKDIR)
	@echo generating $@ from PROTO
	$(NOECHO)$(PROTOC_TOOL) \
		--plugin=protoc-gen-custom-plugin=$(MODULE_PROTOC_PLUGIN) \
		--custom-plugin_out=$(MODULE_PROTO_OUT_DIR) \
		--custom-plugin_opt=pkg:$(MODULE_PROTO_PACKAGE),$(MODULE_PROTOC_PLUGIN_FLAGS) \
		$<

MODULE_EXPORT_INCLUDES += $(MODULE_PROTO_OUT_DIR)/include

# Ensure that all auto-generated code, including headers, is
# emitted before downstream dependencies
MODULE_EXPORT_SRCDEPS += $(MODULE_SRCS)

# Build the PROTO module into a library
include make/library.mk

MODULE_PROTOS :=
PROTOC_TOOL :=
MODULE_PROTO_OUT_DIR :=
MODULE_PROTOC_PLUGIN :=
MODULE_PROTOC_PLUGIN_FLAGS :=
MODULE_PROTO_PACKAGE :=
