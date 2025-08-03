
INCLUDE_DIRS := . ../libdrm/include/drm include ./.ci/android_headers ./tests/test_include
SYSTEM_INCLUDE_DIRS := /usr/include/libdrm

CLANG := clang++-15
CLANG_TIDY := clang-tidy-15
OUT_DIR := /tmp/drm_hwcomposer/build
SRC_DIR := .

CXXFLAGS := -fPIC -Wall -Wextra -Werror -D__ANDROID_API__=31
CXXFLAGS += -D_LIBCPP_ENABLE_THREAD_SAFETY_ANNOTATIONS
CXXFLAGS += -fvisibility-inlines-hidden -std=gnu++17 -DHWC2_USE_CPP11 -DHWC2_INCLUDE_STRINGIFICATION -fno-rtti

SKIP_FILES := \
    bufferinfo/BufferInfoMapperMetadata.cpp

TIDY_FILES_OVERRIDE := \
    bufferinfo/legacy/BufferInfoImagination.cpp:COARSE  \
    bufferinfo/legacy/BufferInfoLibdrm.cpp:COARSE       \
    bufferinfo/legacy/BufferInfoMaliHisi.cpp:COARSE     \
    bufferinfo/legacy/BufferInfoMaliMediatek.cpp:COARSE \
    bufferinfo/legacy/BufferInfoMaliMeson.cpp:COARSE    \
    bufferinfo/legacy/BufferInfoMinigbm.cpp:COARSE      \
    drm/DrmFbImporter.h:FINE                            \
    drm/DrmUnique.h:FINE                                \
    hwc2_device/DrmHwcTwo.cpp:COARSE                    \
    hwc2_device/DrmHwcTwo.h:COARSE                      \
    hwc2_device/HwcDisplay.cpp:COARSE                   \
    hwc2_device/HwcDisplay.h:COARSE                     \
    utils/log.h:FINE                                    \
    utils/properties.h:FINE                             \

TIDY_CHECKS_FINE := *                                   \
    -llvmlibc* -fuchsia-* -altera-*                     \
    -llvm-header-guard                                  \
    -cppcoreguidelines-pro-type-vararg                  \
    -hicpp-vararg                                       \
    -hicpp-signed-bitwise                               \
    -readability-identifier-length                      \

TIDY_CHECKS_NORMAL :=                                   \
    $(TIDY_CHECKS_FINE)                                 \
    -hicpp*                                             \
    -bugprone-easily-swappable-parameters               \
    -cppcoreguidelines-special-member-functions \
    -cppcoreguidelines-avoid-c-arrays \
    -cppcoreguidelines-pro-bounds-array-to-pointer-decay \
    -cppcoreguidelines-pro-bounds-constant-array-index \
    -cppcoreguidelines-avoid-magic-numbers \
    -google-readability-braces-around-statements \
    -google-readability-casting \
    -misc-non-private-member-variables-in-classes \
    -modernize-avoid-c-arrays \
    -modernize-use-nodiscard \
    -modernize-use-trailing-return-type \
    -readability-braces-around-statements \

TIDY_CHECKS_COARSE := \
    $(TIDY_CHECKS_NORMAL) \
    -cppcoreguidelines-non-private-member-variables-in-classes \
    -cppcoreguidelines-pro-bounds-pointer-arithmetic \
    -cppcoreguidelines-pro-type-cstyle-cast \
    -cppcoreguidelines-pro-type-reinterpret-cast \
    -cppcoreguidelines-pro-type-static-cast-downcast \
    -cppcoreguidelines-pro-type-union-access \
    -cppcoreguidelines-macro-usage \
    -readability-convert-member-functions-to-static \
    -readability-implicit-bool-conversion \
    -readability-identifier-naming \
    -readability-magic-numbers \

.PHONY: all build tidy clean

all: build tidy

clean:
	rm -rf $(OUT_DIR)/

# Build

BUILD_FILES_AUTO := $(shell find -L $(SRC_DIR) -not -path '*/\.*' -not -path '*/tests/test_include/*' -path '*.cpp')
SKIP_FILES_path := $(foreach file,$(SKIP_FILES),$(SRC_DIR)/$(file))

BUILD_FILES := $(subst ./,,$(filter-out $(SKIP_FILES_path),$(BUILD_FILES_AUTO)))

_OBJ := $(BUILD_FILES:.cpp=.o)
OBJ  := $(patsubst %,$(OUT_DIR)/%,$(_OBJ))

DEPS := $(patsubst %.cpp,$(OUT_DIR)/%.d,$(BUILD_FILES))

build: $(OBJ)

CXXARGS := $(foreach dir,$(INCLUDE_DIRS),-I$(SRC_DIR)/$(dir)) $(foreach dir,$(SYSTEM_INCLUDE_DIRS),-I$(dir)) $(CXXFLAGS)

$(OUT_DIR)/%.o: $(SRC_DIR)/%.cpp
	mkdir -p $(dir $@)
	$(CLANG) $< $(CXXARGS) -c -o $@

$(OUT_DIR)/%.d: $(SRC_DIR)/%.cpp
	mkdir -p $(dir $@)
	$(CLANG) $(CXXARGS) $< -MM -MT $(OUT_DIR)/$(patsubst %.cpp,%.o,$<) -o $@

# TIDY
TIDY_FILES_AUTO := $(shell find -L $(SRC_DIR) -not -path '*/\.*' -not -path '*/tests/test_include/*' \( -path '*.cpp' -o -path '*.h' \))

TIDY_FILES_AUTO_filtered := $(filter-out $(SKIP_FILES_path),$(TIDY_FILES_AUTO))

TIDY_FILES_OVERRIDE_path := $(foreach pair,$(TIDY_FILES_OVERRIDE),$(SRC_DIR)/$(pair))

TIDY_FILES_OVERRIDE_name_only := $(foreach pair,$(TIDY_FILES_OVERRIDE_path),$(word 1, $(subst :, ,$(pair))))

TIDY_FILES := $(sort $(TIDY_FILES_AUTO_filtered) $(TIDY_FILES_OVERRIDE_name_only))

space := $(subst ,, )
comma := ,

TIDY_ARGS_NONE := --checks="-*,llvm-include-order"
TIDY_ARGS_     := --checks="-*,llvm-include-order"
TIDY_ARGS_FINE := --checks="$(subst $(space),$(comma),$(strip $(TIDY_CHECKS_FINE)))"
TIDY_ARGS_NORMAL := --checks="$(subst $(space),$(comma),$(strip $(TIDY_CHECKS_NORMAL)))"
TIDY_ARGS_COARSE := --checks="$(subst $(space),$(comma),$(strip $(TIDY_CHECKS_COARSE)))"

define process-tidy

_TARG := $(OUT_DIR)/$1.tidy.ts
_DEP := $(SRC_DIR)/$1

TIDY_DEPS += $(_TARG)

TIDY_LEVEL_1 := $$(strip $$(foreach pair,$$(TIDY_FILES_OVERRIDE_path),$$(if $$(filter $$(word 1, $$(subst :, ,$$(pair))),$1),$$(word 2, $$(subst :, ,$$(pair))),)))

TIDY_LEVEL_2 := $$(if $$(TIDY_LEVEL_1),$$(TIDY_LEVEL_1),NORMAL)

TIDY_ARGS := $$(TIDY_ARGS_$$(TIDY_LEVEL_2))

$$(_TARG): _DEP := $$(_DEP)
$$(_TARG): _TARG := $$(_TARG)
$$(_TARG): TIDY_ARGS := $$(TIDY_ARGS)
$$(_TARG): $$(_DEP)
	mkdir -p $$(dir $$(_TARG))
	$$(CLANG_TIDY) $$(_DEP) $$(TIDY_ARGS) -- -x c++ $$(CXXARGS) -Wno-pragma-once-outside-header
	touch $$(_TARG)

endef

$(foreach file,$(TIDY_FILES),$(eval $(call process-tidy,$(file))))

tidy: $(TIDY_DEPS)

ifneq ($(MAKECMDGOALS), clean)
-include $(DEPS)
endif
