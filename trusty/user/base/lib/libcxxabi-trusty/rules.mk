LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

LIBCXXABI_DIR = external/libcxxabi

MODULE_LICENSES += $(LIBCXXABI_DIR)/NOTICE

MODULE_EXPORT_INCLUDES += $(LIBCXXABI_DIR)/include

MODULE_SDK_HEADER_INSTALL_DIR := c++/v1

# Internal libcxxabi build requires std::unexpected_handler to be defined, even
# though it is removed as of C++17. Building with LIBCPP_BUILDING_LIBRARY
# includes this required non-spec definition in the build.
MODULE_CPPFLAGS += -D_LIBCPP_BUILDING_LIBRARY

# The way we define LIBCPP_BUILDING_LIBRARY above conflicts with a definition in
# the fallback allocator. The resulting error is safe to ignore.
MODULE_COMPILEFLAGS += -Wno-macro-redefined

MODULE_COMPILEFLAGS += -D_LIBCXXABI_BUILDING_LIBRARY -D_LIBCXXABI_HAS_NO_THREADS

# Required if compiling without exceptions.
MODULE_COMPILEFLAGS += -D_LIBCXXABI_NO_EXCEPTIONS

# Required if compiling without RTTI, but also helps binary size.
MODULE_COMPILEFLAGS += -DLIBCXXABI_SILENT_TERMINATE

MODULE_SRCS := \
	$(LIBCXXABI_DIR)/src/cxa_aux_runtime.cpp \
	$(LIBCXXABI_DIR)/src/cxa_default_handlers.cpp \
	$(LIBCXXABI_DIR)/src/cxa_demangle.cpp \
	$(LIBCXXABI_DIR)/src/cxa_exception_storage.cpp \
	$(LIBCXXABI_DIR)/src/cxa_guard.cpp \
	$(LIBCXXABI_DIR)/src/cxa_handlers.cpp \
	$(LIBCXXABI_DIR)/src/cxa_unexpected.cpp \
	$(LIBCXXABI_DIR)/src/cxa_vector.cpp \
	$(LIBCXXABI_DIR)/src/cxa_virtual.cpp \
	$(LIBCXXABI_DIR)/src/stdlib_exception.cpp \
	$(LIBCXXABI_DIR)/src/stdlib_stdexcept.cpp \
	$(LIBCXXABI_DIR)/src/stdlib_typeinfo.cpp \
	$(LIBCXXABI_DIR)/src/abort_message.cpp \
	$(LIBCXXABI_DIR)/src/fallback_malloc.cpp \

# Exceptions disabled
MODULE_SRCS += \
        $(LIBCXXABI_DIR)/src/cxa_noexception.cpp \

# Files that do not compile without exceptions
# $(LIBCXXABI_DIR)/src/cxa_exception.cpp \
# $(LIBCXXABI_DIR)/src/cxa_personality.cpp \

# Files that do not compile without RTTI
# $(LIBCXXABI_DIR)/src/private_typeinfo.cpp \

MODULE_LIBRARY_DEPS := \
	trusty/user/base/lib/libstdc++-trusty

include make/library.mk
