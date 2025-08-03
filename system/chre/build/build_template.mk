#
# Build Template
#
# Invoke this template with a set of variables in order to make build targets
# for a build variant that targets a specific CPU architecture.
#

################################################################################
#
# Build Template
#
# Invoke this to instantiate a set of build targets. Two build targets are
# produced by this template that can be either used directly or depended on to
# perform post processing (ie: during a nanoapp build).
#
# TARGET_NAME_ar - An archive of the code compiled by this template.
# TARGET_NAME_so - A shared object of the code compiled by this template.
# TARGET_NAME    - A convenience target that depends on the above archive and
#                  shared object targets.
#
# Nanoapps can optionally use the NANOAPP_LATE_CFLAGS variable to provide
# compile flags, which will be added at the end of the compile command
# (for instance, it can be used to override common flags in COMMON_CFLAGS).
#
# Argument List:
#     $1  - TARGET_NAME          - The name of the target being built.
#     $2  - TARGET_CFLAGS        - The compiler flags to use for this target.
#     $3  - TARGET_CC            - The C/C++ compiler for the target variant.
#     $4  - TARGET_SO_LDFLAGS    - The linker flags to use for this target.
#     $5  - TARGET_LD            - The linker for the target variant.
#     $6  - TARGET_ARFLAGS       - The archival flags to use for this target.
#     $7  - TARGET_AR            - The archival tool for the targer variant.
#     $8  - TARGET_VARIANT_SRCS  - Source files specific to this variant.
#     $9  - TARGET_BUILD_BIN     - Build a binary. Typically this means that the
#                                  source files provided include an entry point.
#     $10 - TARGET_BIN_LDFLAGS   - Linker flags that are passed to the linker
#                                  when building an executable binary.
#     $11 - TARGET_SO_EARLY_LIBS - Link against a set of libraries when building
#                                  a shared object or binary. These are placed
#                                  before the objects produced by this build.
#     $12 - TARGET_SO_LATE_LIBS  - Link against a set of libraries when building
#                                  a shared object or binary. These are placed
#                                  after the objects produced by this build.
#     $13 - TARGET_PLATFORM_ID   - The ID of the platform that this nanoapp
#                                  build targets.
#     $14 - TARGET_ACONFIGFLAGS  - The list of aconfig flag value files specific
#                                  to this build target
#     $15 - TARGET_ADDITIONAL_LD - Additional linker for this target variant.
#
################################################################################

ifndef BUILD_TEMPLATE
define BUILD_TEMPLATE

# Target Objects ###############################################################

# Remove duplicates
COMMON_SRCS := $(sort $(COMMON_SRCS))

# Source files.
$(1)_CC_SRCS = $$(filter %.cc, $(COMMON_SRCS) $(8))
$(1)_CPP_SRCS = $$(filter %.cpp, $(COMMON_SRCS) $(8))
$(1)_C_SRCS = $$(filter %.c, $(COMMON_SRCS) $(8))
$(1)_S_SRCS = $$(filter %.S, $(COMMON_SRCS) $(8))

# Object files.
$(1)_OBJS_DIR = $(1)_objs
$(1)_CC_OBJS = $$(patsubst %.cc, $(OUT)/$$($(1)_OBJS_DIR)/%.o, \
                           $$($(1)_CC_SRCS))
$(1)_CPP_OBJS = $$(patsubst %.cpp, $(OUT)/$$($(1)_OBJS_DIR)/%.o, \
                            $$($(1)_CPP_SRCS))
$(1)_C_OBJS = $$(patsubst %.c, $(OUT)/$$($(1)_OBJS_DIR)/%.o, \
                          $$($(1)_C_SRCS))
$(1)_S_OBJS = $$(patsubst %.S, $(OUT)/$$($(1)_OBJS_DIR)/%.o, \
                          $$($(1)_S_SRCS))

# Automatic dependency resolution Makefiles.
$(1)_CC_DEPS = $$(patsubst %.cc, $(OUT)/$$($(1)_OBJS_DIR)/%.d, \
                           $$($(1)_CC_SRCS))
$(1)_CPP_DEPS = $$(patsubst %.cpp, $(OUT)/$$($(1)_OBJS_DIR)/%.d, \
                            $$($(1)_CPP_SRCS))
$(1)_C_DEPS = $$(patsubst %.c, $(OUT)/$$($(1)_OBJS_DIR)/%.d, \
                          $$($(1)_C_SRCS))
$(1)_S_DEPS = $$(patsubst %.S, $(OUT)/$$($(1)_OBJS_DIR)/%.d, \
                          $$($(1)_S_SRCS))

# Add object file directories.
$(1)_DIRS = $$(sort $$(dir $$($(1)_CC_OBJS) \
                           $$($(1)_CPP_OBJS) \
                           $$($(1)_C_OBJS) \
                           $$($(1)_S_OBJS)))

# Outputs ######################################################################

# Shared Object
$(1)_SO = $(OUT)/$(1)/$(OUTPUT_NAME).so

# Static Archive
$(1)_AR = $(OUT)/$(1)/$(OUTPUT_NAME).a

# Nanoapp Header
$(1)_HEADER = $$(if $(IS_NANOAPP_BUILD), $(OUT)/$(1)/$(OUTPUT_NAME).napp_header, )

# Optional Binary
$(1)_BIN = $$(if $(9), $(OUT)/$(1)/$(OUTPUT_NAME), )

# Optional token mapping
$(1)_TOKEN_MAP = $$(if $(CHRE_TOKENIZED_LOGGING_ENABLED), \
                    $(OUT)/$(1)/$(OUTPUT_NAME)_log_database.bin,)

$(1)_TOKEN_MAP_CSV = $$(if $(CHRE_TOKENIZED_LOGGING_ENABLED), \
                        $(OUT)/$(1)/$(OUTPUT_NAME)_log_database.csv,)

# Top-level Build Rule #########################################################

# Define the phony target.
.PHONY: $(1)_ar
$(1)_ar: $$($(1)_AR)

.PHONY: $(1)_so
$(1)_so: $$($(1)_SO)

.PHONY: $(1)_bin
$(1)_bin: $$($(1)_BIN)

.PHONY: $(1)_header
$(1)_header: $$($(1)_HEADER)

.PHONY: $(1)_token_map
$(1)_token_map: $$($(1)_TOKEN_MAP)

.PHONY: $(1)_flags
$(1)_flags: $$((1)_FLAGS)

.PHONY: $(1)
ifeq ($(IS_ARCHIVE_ONLY_BUILD),true)
$(1): $(1)_flags $(1)_ar $(1)_token_map
else
$(1): $(1)_flags $(1)_ar $(1)_so $(1)_bin $(1)_header $(1)_token_map
endif

# If building the runtime, simply add the archive and shared object to the all
# target. When building CHRE, it is expected that this runtime just be linked
# into another build system (or the entire runtime is built using another build
# system).
ifeq ($(IS_NANOAPP_BUILD),)
all: $(1)
endif

# Nanoapp Header Generation ####################################################

#
# Whoa there... what have we here? Some binary file generation ala bash? ಠ_ಠ
#
# The following build rule generates a nanoapp header. A nanoapp header is a
# small binary blob that is prepended to a nanoapp. Android can parse this
# blob to determine some attributes about the nanoapp, such as version and
# target hub. The layout is as follows:
#
# struct NanoAppBinaryHeader {
#   uint32_t headerVersion;        // 0x1 for this version
#   uint32_t magic;                // "NANO"
#   uint64_t appId;                // App Id, contains vendor id
#   uint32_t appVersion;           // Version of the app
#   uint32_t flags;                // Signed, encrypted, TCM-capable
#   uint64_t hwHubType;            // Which hub type is this compiled for
#   uint8_t targetChreApiMajorVersion; // CHRE API version
#   uint8_t targetChreApiMinorVersion;
#   uint8_t reserved[6];
# } __attribute__((packed));
#
# The basic idea here is to generate a hexdump formatted file and then reverse
# that hexdump into binary form. The way that is accomplished is as follows.
#
# ... Why tho?
#
# The build system has a lot of knowledge of what it is building: the name of
# the nanoapp, the version and the app ID. Marshalling this data from the
# Makefile environment into something like python or even a small C program
# is an unnecessary step.
#
# For the flags field of the struct, the following values are currently defined:
# Signed                 = 0x00000001
# Encrypted              = 0x00000002
# TCM-capable            = 0x00000004
#
# The highest order byte is reserved for platform-specific usage.

$$($(1)_HEADER): $$(OUT)/$(1) $$($(1)_DIRS)
	printf "00000000  %.8x " `$(BE_TO_LE_SCRIPT) 0x00000001` > $$@
	printf "%.8x " `$(BE_TO_LE_SCRIPT) 0x4f4e414e` >> $$@
	printf "%.16x\n" `$(BE_TO_LE_SCRIPT) $(NANOAPP_ID)` >> $$@
	printf "00000010  %.8x " `$(BE_TO_LE_SCRIPT) $(NANOAPP_VERSION)` >> $$@
	printf "%.8x " `$(BE_TO_LE_SCRIPT) $(TARGET_NANOAPP_FLAGS)` >> $$@
	printf "%.16x\n" `$(BE_TO_LE_SCRIPT) $(13)` >> $$@
	printf "00000020  %.2x " \
	    `$(BE_TO_LE_SCRIPT) $(TARGET_CHRE_API_VERSION_MAJOR)` >> $$@
	printf "%.2x " \
	    `$(BE_TO_LE_SCRIPT) $(TARGET_CHRE_API_VERSION_MINOR)` >> $$@
	printf "%.12x \n" `$(BE_TO_LE_SCRIPT) 0x000000` >> $$@
	cp $$@ $$@_ascii
	xxd -r $$@_ascii > $$@
	rm $$@_ascii

# Compile ######################################################################

$$($(1)_CPP_OBJS): $(OUT)/$$($(1)_OBJS_DIR)/%.o: %.cpp $(MAKEFILE_LIST)
	@echo " [CPP] $$<"
	$(V)$(3) $(COMMON_CXX_CFLAGS) -DCHRE_FILENAME=\"$$(notdir $$<)\" $(2) -c \
		$$< -o $$@

$$($(1)_CC_OBJS): $(OUT)/$$($(1)_OBJS_DIR)/%.o: %.cc $(MAKEFILE_LIST)
	@echo " [CC] $$<"
	$(V)$(3) $(COMMON_CXX_CFLAGS) -DCHRE_FILENAME=\"$$(notdir $$<)\" $(2) -c \
		$$< -o $$@

$$($(1)_C_OBJS): $(OUT)/$$($(1)_OBJS_DIR)/%.o: %.c $(MAKEFILE_LIST)
	@echo " [C] $$<"
	$(V)$(3) $(COMMON_C_CFLAGS) -DCHRE_FILENAME=\"$$(notdir $$<)\" $(2) -c $$< \
		-o $$@

$$($(1)_S_OBJS): $(OUT)/$$($(1)_OBJS_DIR)/%.o: %.S $(MAKEFILE_LIST)
	@echo " [AS] $$<"
	$(V)$(3) -DCHRE_FILENAME=\"$$(notdir $$<)\" $(2) -c $$< \
		-o $$@

# Archive ######################################################################

# Add common and target-specific archive flags.
$(1)_ARFLAGS = $(COMMON_ARFLAGS) \
    $(6)

$$($(1)_AR): $$($(1)_CC_OBJS) $$($(1)_CPP_OBJS) $$($(1)_C_OBJS) \
              $$($(1)_S_OBJS) | $$(OUT)/$(1) $$($(1)_DIRS)
	@echo " [AR] $$@"
	$(V)$(7) $$($(1)_ARFLAGS) $$@ $$(filter %.o, $$^)

# Token Mapping ################################################################

$$($(1)_TOKEN_MAP): $$($(1)_AR)
	@echo " [TOKEN_MAP_GEN] $$@"
	$(V)mkdir -p $$(@D)
	$(V)$(TOKEN_MAP_GEN_CMD) $$($(1)_TOKEN_MAP) $$($(1)_AR) 2>&1
	$(V)$(TOKEN_MAP_CSV_GEN_CMD) $$($(1)_TOKEN_MAP_CSV) $$($(1)_AR) 2>&1

# Rust #########################################################################

ifeq ($(IS_BUILD_REQUIRING_RUST),)
RUST_DEPENDENCIES =
else
RUST_DEPENDENCIES = rust_archive_$(1)
endif

# Always invoke the cargo build, let cargo decide if updates are needed
.PHONY: rust_archive_$(1)
rust_archive_$(1):
	@echo " [RUST_ARCHIVE] $$@"
	$(RUST_FLAGS) cargo +nightly build -Z build-std=core,alloc \
	    --$(RUST_OPT_LEVEL) --target $(RUST_TARGET_DIR)/$(RUST_TARGET).json

# Aconfig Flag Generation ######################################################

# This must be handled in the final build_template to allow for layers of flag
# overwriting ( common > device_specific > local ).

ifeq ($(IS_BUILD_REQUIRING_FLAG_LIBRARY),)
FLAG_DEPENDENCIES =
else
FLAG_DEPENDENCIES = flagging_library_$(1)
endif

$$((1)_FLAGS): $(FLAG_DEPENDENCIES)

.PHONY: flagging_library_$(1)
flagging_library_$(1):
	@echo " [ACONFIG] $$@"
	$(ACONFIG_FLAG_BUILD_SCRIPT) $(ACONFIG_BIN) $(ACONFIG_EMB_DIR) \
	    $(COMMON_ACONFIG_FLAG_VALUES) $(TARGET_ACONFIGFLAGS)

# Link #########################################################################

$$($(1)_SO): $$($(1)_CC_DEPS) \
              $$($(1)_CPP_DEPS) $$($(1)_C_DEPS) $$($(1)_S_DEPS) \
              $$($(1)_CC_OBJS) $$($(1)_CPP_OBJS) $$($(1)_C_OBJS) \
              $$($(1)_S_OBJS) $(RUST_DEPENDENCIES) | $$(OUT)/$(1) $$($(1)_DIRS)
	$(5) $(4) -o $$@ $(11) $$(filter %.o, $$^) $(12) $(15)

$$($(1)_BIN): $$($(1)_CC_DEPS) \
               $$($(1)_CPP_DEPS) $$($(1)_C_DEPS) $$($(1)_S_DEPS) \
               $$($(1)_CC_OBJS) $$($(1)_CPP_OBJS) $$($(1)_C_OBJS) \
               $$($(1)_S_OBJS) | $$(OUT)/$(1) $$($(1)_DIRS)
	$(V)$(3) -o $$@ $(11) $$(filter %.o, $$^) $(12) $(10)

# Output Directories ###########################################################

$$($$$(1)_DIRS):
	$(V)mkdir -p $$@

$$(OUT)/$(1):
	$(V)mkdir -p $$@

# Automatic Dependency Resolution ##############################################

$$($(1)_CC_DEPS): $(OUT)/$$($(1)_OBJS_DIR)/%.d: %.cc
	$(V)mkdir -p $$(dir $$@)
	$(V)$(3) $(DEP_CFLAGS) $(COMMON_CXX_CFLAGS) \
		-DCHRE_FILENAME=\"$$(notdir $$<)\" $(2) -c $$< -o $$@

$$($(1)_CPP_DEPS): $(OUT)/$$($(1)_OBJS_DIR)/%.d: %.cpp
	$(V)mkdir -p $$(dir $$@)
	$(V)$(3) $(DEP_CFLAGS) $(COMMON_CXX_CFLAGS) \
		-DCHRE_FILENAME=\"$$(notdir $$<)\" $(2) -c $$< -o $$@

$$($(1)_C_DEPS): $(OUT)/$$($(1)_OBJS_DIR)/%.d: %.c
	$(V)mkdir -p $$(dir $$@)
	$(V)$(3) $(DEP_CFLAGS) $(COMMON_C_CFLAGS) \
		-DCHRE_FILENAME=\"$$(notdir $$<)\" $(2) -c $$< -o $$@

$$($(1)_S_DEPS): $(OUT)/$$($(1)_OBJS_DIR)/%.d: %.S
	$(V)mkdir -p $$(dir $$@)
	$(V)$(3) $(DEP_CFLAGS) \
		-DCHRE_FILENAME=\"$$(notdir $$<)\" $(2) -c $$< -o $$@

# Include generated dependency files if they are in the requested build target.
# This avoids dependency generation from occuring for a debug target when a
# non-debug target is requested.
ifneq ($(filter $(1) all, $(MAKECMDGOALS)),)
-include $$(patsubst %.o, %.d, $$($(1)_CC_DEPS))
-include $$(patsubst %.o, %.d, $$($(1)_CPP_DEPS))
-include $$(patsubst %.o, %.d, $$($(1)_C_DEPS))
-include $$(patsubst %.o, %.d, $$($(1)_S_DEPS))
endif

endef
endif

# Template Invocation ##########################################################

TARGET_CFLAGS_LOCAL = $(TARGET_CFLAGS)
TARGET_CFLAGS_LOCAL += -DCHRE_PLATFORM_ID=$(TARGET_PLATFORM_ID)

# Default the nanoapp header flag values to signed if not overidden.
TARGET_NANOAPP_FLAGS ?= 0x00000001
$(eval $(call BUILD_TEMPLATE,$(TARGET_NAME), \
                             $(COMMON_CFLAGS) $(TARGET_CFLAGS_LOCAL) \
                                 $(NANOAPP_LATE_CFLAGS), \
                             $(TARGET_CC), \
                             $(TARGET_SO_LDFLAGS), \
                             $(TARGET_LD), \
                             $(TARGET_ARFLAGS), \
                             $(TARGET_AR), \
                             $(TARGET_VARIANT_SRCS), \
                             $(TARGET_BUILD_BIN), \
                             $(TARGET_BIN_LDFLAGS), \
                             $(TARGET_SO_EARLY_LIBS), \
                             $(TARGET_SO_LATE_LIBS), \
                             $(TARGET_PLATFORM_ID), \
                             $(TARGET_ACONFIGFLAGS), \
                             $(TARGET_ADDITIONAL_LD)))

# Debug Template Invocation ####################################################

$(eval $(call BUILD_TEMPLATE,$(TARGET_NAME)_debug, \
                             $(COMMON_CFLAGS) $(COMMON_DEBUG_CFLAGS) \
                                 $(TARGET_CFLAGS_LOCAL) $(TARGET_DEBUG_CFLAGS) \
                                 $(NANOAPP_LATE_CFLAGS), \
                             $(TARGET_CC), \
                             $(TARGET_SO_LDFLAGS), \
                             $(TARGET_LD), \
                             $(TARGET_ARFLAGS), \
                             $(TARGET_AR), \
                             $(TARGET_VARIANT_SRCS), \
                             $(TARGET_BUILD_BIN), \
                             $(TARGET_BIN_LDFLAGS), \
                             $(TARGET_SO_EARLY_LIBS), \
                             $(TARGET_SO_LATE_LIBS), \
                             $(TARGET_PLATFORM_ID), \
                             $(TARGET_ACONFIGFLAGS), \
                             $(TARGET_ADDITIONAL_LD)))
