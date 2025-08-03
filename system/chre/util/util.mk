#
# Util Makefile
#

# Common Compiler Flags ########################################################

# Include paths.
COMMON_CFLAGS += -I$(CHRE_PREFIX)/util/include

# Common Source Files ##########################################################

COMMON_SRCS += $(CHRE_PREFIX)/util/buffer_base.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/dynamic_vector_base.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/intrusive_list_base.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/audio.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/ble.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/callbacks.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/debug.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/string.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/nanoapp/wifi.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/system/ble_util.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/system/event_callbacks.cc
COMMON_SRCS += $(CHRE_PREFIX)/util/system/debug_dump.cc

# GoogleTest Source Files ######################################################

GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/array_queue_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/atomic_spsc_queue_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/blocking_queue_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/buffer_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/copyable_fixed_size_vector_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/debug_dump_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/dynamic_vector_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/fixed_size_vector_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/heap_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/intrusive_list_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/lock_guard_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/memory_pool_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/optional_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/priority_queue_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/raw_storage_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/ref_base_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/segmented_queue_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/shared_ptr_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/singleton_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/stats_container_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/synchronized_expandable_memory_pool_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/synchronized_memory_pool_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/time_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/util/tests/unique_ptr_test.cc

# Pigweed Source Files #########################################################

PIGWEED_UTIL_SRCS += $(CHRE_PREFIX)/util/pigweed/chre_channel_output.cc
