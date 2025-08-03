#
# Nanoapp/CHRE NanoPB and Pigweed RPC Makefile
#
# Include this file in your nanoapp Makefile to generate .c source and .h header
# files. ($NANOPB_EXTENSION.c and $NANOPB_EXTENSION.h if $NANOPB_EXTENSION
# is defined) for .proto files specified in the NANOPB_SRCS and PW_RPC_SRCS
# variables.
#
# The generated source files are automatically added to the COMMON_SRCS variable
# for the nanoapp build.
#
# The path to the generated header files is similarly added to the COMMON_CFLAGS.
#
# The NANOPB_OPTIONS variable can be used to supply an .options file to use when
# generating code for all .proto files. Alternatively, if an .options file has
# the same name as a .proto file, it'll be automatically picked up when generating
# code **only** for that .proto file.
#
# NANOPB_FLAGS can be used to supply additional command line arguments to the
# nanopb compiler. Note that this is global and applies to all protobuf
# generated source.
#
# NANOPB_INCLUDES may optionally be used to automatically add one or more
# include path prefixes for C/C++ source and .proto files. For example, if the
# file myprefix/proto/foo.proto is added to NANOPB_SRCS, but you'd like to use
# #include "proto/foo.pb.h" in your source (rather than myprefix/proto/foo.pb.h)
# and/or import "proto/foo.proto" in your .proto files, then set NANOPB_INCLUDES
# to myprefix.

# Environment Checks ###########################################################

HAS_PROTO_SRC = false

ifneq ($(NANOPB_SRCS),)
ifeq ($(NANOPB_PREFIX),)
$(error "NANOPB_SRCS is non-empty. You must supply a NANOPB_PREFIX environment \
         variable containing a path to the nanopb project. Example: \
         export NANOPB_PREFIX=$$HOME/path/to/nanopb/nanopb-c")
endif
HAS_PROTO_SRC = true
endif

ifneq ($(PW_RPC_SRCS),)
ifeq ($(NANOPB_PREFIX),)
$(error "PW_RPC_SRCS is non-empty. You must supply a NANOPB_PREFIX environment \
         variable containing a path to the nanopb project. Example: \
         export NANOPB_PREFIX=$$HOME/path/to/nanopb/nanopb-c")
endif
HAS_PROTO_SRC = true
endif

################################################################################
# Common #######################################################################
################################################################################

ifeq ($(PROTOC),)
PROTOC=protoc
endif

NANOPB_GEN_PATH = $(OUT)/nanopb_gen

ifeq ($(NANOPB_EXTENSION),)
NANOPB_EXTENSION = pb
else
NANOPB_GENERATOR_FLAGS = --extension=.$(NANOPB_EXTENSION)
endif

NANOPB_GEN_SRCS += $(patsubst %.proto, \
                              $(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).c, \
                              $(NANOPB_SRCS))

# Add Google proto well-known types. See https://protobuf.dev/reference/protobuf/google.protobuf/.
PROTOBUF_DIR = $(ANDROID_BUILD_TOP)/external/protobuf
COMMON_CFLAGS += -I$(NANOPB_GEN_PATH)/$(PROTOBUF_DIR)/src

################################################################################
# Common to nanopb & rpc #######################################################
################################################################################

ifeq ($(HAS_PROTO_SRC),true)
COMMON_CFLAGS += -I$(NANOPB_PREFIX)

ifneq ($(NANOPB_INCLUDE_LIBRARY),false)
COMMON_SRCS += $(NANOPB_PREFIX)/pb_common.c
COMMON_SRCS += $(NANOPB_PREFIX)/pb_decode.c
COMMON_SRCS += $(NANOPB_PREFIX)/pb_encode.c
endif

# NanoPB Compiler Flags
ifneq ($(NANOPB_INCLUDE_LIBRARY),false)
COMMON_CFLAGS += -DPB_NO_PACKED_STRUCTS=1
endif

NANOPB_PROTOC = $(NANOPB_PREFIX)/generator/protoc-gen-nanopb

endif # ifeq ($(HAS_PROTO_SRC),true)

################################################################################
# nanopb #######################################################################
################################################################################

ifneq ($(NANOPB_GEN_SRCS),)
COMMON_CFLAGS += -I$(NANOPB_GEN_PATH)
COMMON_CFLAGS += $(addprefix -I$(NANOPB_GEN_PATH)/, $(NANOPB_INCLUDES))
endif

# NanoPB Generator Setup #######################################################

NANOPB_GENERATOR_SRCS = $(NANOPB_PREFIX)/generator/proto/nanopb_pb2.py
NANOPB_GENERATOR_SRCS += $(NANOPB_PREFIX)/generator/proto/plugin_pb2.py

$(NANOPB_GENERATOR_SRCS):
	cd $(NANOPB_PREFIX)/generator/proto && $(MAKE)

ifneq ($(NANOPB_OPTIONS),)
NANOPB_OPTIONS_FLAG = --options-file=$(NANOPB_OPTIONS)
else
NANOPB_OPTIONS_FLAG =
endif

NANOPB_FLAGS += $(addprefix --proto_path=, $(abspath $(NANOPB_INCLUDES)))

# Generate NanoPB Sources ######################################################

COMMON_SRCS += $(NANOPB_GEN_SRCS)

NANOPB_PROTOC = $(NANOPB_PREFIX)/generator/protoc-gen-nanopb

$(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).c \
        $(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).h: %.proto \
                                                    %.options \
                                                    $(NANOPB_GENERATOR_SRCS)
	@echo " [NANOPB] $<"
	$(V)mkdir -p $(dir $@)
	$(V)PYTHONPATH=$(PYTHONPATH) $(PROTOC) \
	  --plugin=protoc-gen-nanopb=$(NANOPB_PROTOC) \
	  --proto_path=$(abspath $(dir $<)) \
	  $(NANOPB_FLAGS) \
	  --nanopb_out="$(NANOPB_GENERATOR_FLAGS) \
	  --options-file=$(basename $<).options:$(dir $@)" \
	  $(abspath $<)

$(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).c \
        $(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).h: %.proto \
                                                    $(NANOPB_OPTIONS) \
                                                    $(NANOPB_GENERATOR_SRCS)
	@echo " [NANOPB] $<"
	$(V)mkdir -p $(dir $@)
	$(V)PYTHONPATH=$(PYTHONPATH) $(PROTOC) \
	  --plugin=protoc-gen-nanopb=$(NANOPB_PROTOC) \
	  --proto_path=$(abspath $(dir $<)) \
	  $(NANOPB_FLAGS) \
	  --nanopb_out="$(NANOPB_GENERATOR_FLAGS) $(NANOPB_OPTIONS_FLAG):$(dir $@)" \
	  $(abspath $<)

################################################################################
# Specific to pigweed RPC ######################################################
################################################################################
ifneq ($(PW_RPC_SRCS),)

# Location of various Pigweed modules
PIGWEED_DIR = $(ANDROID_BUILD_TOP)/external/pigweed
PROTOBUF_DIR = $(ANDROID_BUILD_TOP)/external/protobuf
CHRE_PREFIX = $(ANDROID_BUILD_TOP)/system/chre
CHRE_UTIL_DIR = $(CHRE_PREFIX)/util
CHRE_API_DIR = $(CHRE_PREFIX)/chre_api
PIGWEED_CHRE_DIR=$(CHRE_PREFIX)/external/pigweed
PIGWEED_CHRE_UTIL_DIR = $(CHRE_UTIL_DIR)/pigweed

PW_RPC_GEN_PATH = $(OUT)/pw_rpc_gen

# Create proto used for header generation ######################################

PW_RPC_PROTO_GENERATOR = $(PIGWEED_DIR)/pw_protobuf_compiler/py/pw_protobuf_compiler/generate_protos.py
PW_RPC_GENERATOR_PROTO = $(PIGWEED_DIR)/pw_rpc/internal/packet.proto
PW_RPC_GENERATOR_COMPILED_PROTO = $(PW_RPC_GEN_PATH)/py/pw_rpc/internal/packet_pb2.py
PW_PROTOBUF_PROTOS = $(PIGWEED_DIR)/pw_protobuf/pw_protobuf_protos/common.proto \
	  $(PIGWEED_DIR)/pw_protobuf/pw_protobuf_protos/field_options.proto \
	  $(PIGWEED_DIR)/pw_protobuf/pw_protobuf_protos/status.proto

# Modifies PYTHONPATH so that python can see all of pigweed's modules used by
# their protoc plugins
PW_RPC_GENERATOR_CMD = PYTHONPATH=$$PYTHONPATH:$(PW_RPC_GEN_PATH)/py:$\
  $(PIGWEED_DIR)/pw_status/py:$(PIGWEED_DIR)/pw_protobuf/py:$\
  $(PIGWEED_DIR)/pw_protobuf_compiler/py $(PYTHON)

$(PW_RPC_GENERATOR_COMPILED_PROTO): $(PW_RPC_GENERATOR_PROTO)
	@echo " [PW_RPC] $<"
	$(V)mkdir -p $(PW_RPC_GEN_PATH)/py/pw_rpc/internal
	$(V)mkdir -p $(PW_RPC_GEN_PATH)/py/pw_protobuf_codegen_protos
	$(V)mkdir -p $(PW_RPC_GEN_PATH)/py/pw_protobuf_protos
	$(V)cp -R $(PIGWEED_DIR)/pw_rpc/py/pw_rpc $(PW_RPC_GEN_PATH)/py/

	$(PROTOC) -I$(PIGWEED_DIR)/pw_protobuf/pw_protobuf_protos \
	  --experimental_allow_proto3_optional \
	  --python_out=$(PW_RPC_GEN_PATH)/py/pw_protobuf_protos \
	  $(PW_PROTOBUF_PROTOS)

	$(PROTOC) -I$(PIGWEED_DIR)/pw_protobuf/pw_protobuf_codegen_protos \
	  --experimental_allow_proto3_optional \
	  --python_out=$(PW_RPC_GEN_PATH)/py/pw_protobuf_codegen_protos \
	  $(PIGWEED_DIR)/pw_protobuf/pw_protobuf_codegen_protos/codegen_options.proto

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --out-dir=$(PW_RPC_GEN_PATH)/py/pw_rpc/internal \
	  --compile-dir=$(dir $<) --sources $(PW_RPC_GENERATOR_PROTO) \
	  --language python

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) \
	  --plugin-path=$(PIGWEED_DIR)/pw_protobuf/py/pw_protobuf/plugin.py \
	  --compile-dir=$(dir $<) --sources $(PW_RPC_GENERATOR_PROTO) \
	  --language pwpb

# Generated PW RPC Files #######################################################

PW_RPC_GEN_SRCS = $(patsubst %.proto, \
                             $(PW_RPC_GEN_PATH)/%.pb.c, \
                             $(PW_RPC_SRCS))

# Include to-be-generated files
COMMON_CFLAGS += -I$(PW_RPC_GEN_PATH)
COMMON_CFLAGS += -I$(PW_RPC_GEN_PATH)/$(PIGWEED_DIR)

# Add include paths to reference protos directly
COMMON_CFLAGS += $(addprefix -I$(PW_RPC_GEN_PATH)/, $(abspath $(dir $(PW_RPC_SRCS))))

# Add include paths to import protos
ifneq ($(PW_RPC_INCLUDE_DIRS),)
COMMON_CFLAGS += $(addprefix -I$(PW_RPC_GEN_PATH)/, $(abspath $(PW_RPC_INCLUDE_DIRS)))
endif

# Add Google proto well-known types. See https://protobuf.dev/reference/protobuf/google.protobuf/.
COMMON_CFLAGS += -I$(PW_RPC_GEN_PATH)/$(PROTOBUF_DIR)/src

COMMON_SRCS += $(PW_RPC_GEN_SRCS)

# PW RPC library ###############################################################

# Pigweed RPC include paths
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_assert/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_bytes/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_containers/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_function/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_log/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/standard_library_public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_preprocessor/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_protobuf/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_result/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_rpc/nanopb/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_rpc/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_rpc/pwpb/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_rpc/raw/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_span/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_span/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_status/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_stream/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_string/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_sync/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_toolchain/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_varint/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/third_party/fuchsia/repo/sdk/lib/fit/include
COMMON_CFLAGS += -I$(PIGWEED_DIR)/third_party/fuchsia/repo/sdk/lib/stdcompat/include

# Pigweed RPC sources
COMMON_SRCS += $(PIGWEED_DIR)/pw_assert_log/assert_log.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_containers/intrusive_list.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_protobuf/decoder.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_protobuf/encoder.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_protobuf/stream_decoder.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/call.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/channel.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/channel_list.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/client.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/client_call.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/endpoint.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/packet.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/server.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/server_call.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/service.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/nanopb/common.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/nanopb/method.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/nanopb/server_reader_writer.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/pwpb/server_reader_writer.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_status/status.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_stream/memory_stream.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_varint/stream.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_varint/varint_c.c
COMMON_SRCS += $(PIGWEED_DIR)/pw_varint/varint.cc
# Pigweed configuration
COMMON_CFLAGS += -DPW_RPC_USE_GLOBAL_MUTEX=0
COMMON_CFLAGS += -DPW_RPC_YIELD_MODE=PW_RPC_YIELD_MODE_BUSY_LOOP

# Enable closing a client stream.
COMMON_CFLAGS += -DPW_RPC_COMPLETION_REQUEST_CALLBACK

# Use dynamic channel allocation
COMMON_CFLAGS += -DPW_RPC_DYNAMIC_ALLOCATION
COMMON_CFLAGS += -DPW_RPC_DYNAMIC_CONTAINER\(type\)="chre::DynamicVector<type>"
COMMON_CFLAGS += -DPW_RPC_DYNAMIC_CONTAINER_INCLUDE='"chre/util/dynamic_vector.h"'

# Add CHRE Pigweed util sources since nanoapps should always use these
COMMON_SRCS += $(PIGWEED_CHRE_UTIL_DIR)/chre_channel_output.cc
COMMON_SRCS += $(PIGWEED_CHRE_UTIL_DIR)/rpc_client.cc
COMMON_SRCS += $(PIGWEED_CHRE_UTIL_DIR)/rpc_helper.cc
COMMON_SRCS += $(PIGWEED_CHRE_UTIL_DIR)/rpc_server.cc
COMMON_SRCS += $(CHRE_UTIL_DIR)/nanoapp/callbacks.cc
COMMON_SRCS += $(CHRE_UTIL_DIR)/dynamic_vector_base.cc

# CHRE Pigweed overrides
COMMON_CFLAGS += -I$(PIGWEED_CHRE_DIR)/pw_log_nanoapp/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_CHRE_DIR)/pw_assert_nanoapp/public_overrides

# Generate PW RPC headers ######################################################

$(PW_RPC_GEN_PATH)/%.pb.c \
        $(PW_RPC_GEN_PATH)/%.pb.h \
        $(PW_RPC_GEN_PATH)/%.rpc.pb.h \
        $(PW_RPC_GEN_PATH)/%.raw_rpc.pb.h: %.proto \
                                           %.options \
                                           $(NANOPB_GENERATOR_SRCS) \
                                           $(PW_RPC_GENERATOR_COMPILED_PROTO)
	@echo " [PW_RPC] $<"
	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(NANOPB_PROTOC) \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language nanopb \
	  --sources $<

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_protobuf/py/pw_protobuf/plugin.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language pwpb \
	  --sources $<

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_rpc/py/pw_rpc/plugin_nanopb.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language nanopb_rpc \
	  --sources $<

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_rpc/py/pw_rpc/plugin_raw.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language raw_rpc \
	  --sources $<

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_rpc/py/pw_rpc/plugin_pwpb.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language pwpb_rpc \
	  --sources $<

$(PW_RPC_GEN_PATH)/%.pb.c \
        $(PW_RPC_GEN_PATH)/%.pb.h \
        $(PW_RPC_GEN_PATH)/%.rpc.pb.h \
        $(PW_RPC_GEN_PATH)/%.raw_rpc.pb.h: %.proto \
                                           $(NANOPB_OPTIONS) \
                                           $(NANOPB_GENERATOR_SRCS) \
                                           $(PW_RPC_GENERATOR_COMPILED_PROTO)
	@echo " [PW_RPC] $<"
	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(NANOPB_PROTOC) \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language nanopb \
	  --sources $<

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_protobuf/py/pw_protobuf/plugin.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language pwpb \
	  --sources $<

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_rpc/py/pw_rpc/plugin_nanopb.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language nanopb_rpc \
	  --sources $<

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_rpc/py/pw_rpc/plugin_raw.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language raw_rpc \
	  --sources $<

	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_rpc/py/pw_rpc/plugin_pwpb.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language pwpb_rpc \
	  --sources $<

endif # ifneq ($(PW_RPC_SRCS),)
