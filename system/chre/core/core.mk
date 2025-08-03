#
# Core Makefile
#

# Common Compiler Flags ########################################################

# Include paths.
COMMON_CFLAGS += -I$(CHRE_PREFIX)/core/include

# Common Source Files ##########################################################

COMMON_SRCS += $(CHRE_PREFIX)/core/debug_dump_manager.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/event.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/event_loop.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/event_loop_manager.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/event_ref_queue.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/host_comms_manager.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/host_endpoint_manager.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/init.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/log.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/nanoapp.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/settings.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/static_nanoapps.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/system_health_monitor.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/timer_pool.cc

# Optional audio support.
ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
COMMON_SRCS += $(CHRE_PREFIX)/core/audio_request_manager.cc
endif

# Optional BLE support.
ifeq ($(CHRE_BLE_SUPPORT_ENABLED), true)
COMMON_SRCS += $(CHRE_PREFIX)/core/ble_request.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/ble_request_manager.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/ble_request_multiplexer.cc
endif

# Optional GNSS support.
ifeq ($(CHRE_GNSS_SUPPORT_ENABLED), true)
COMMON_SRCS += $(CHRE_PREFIX)/core/gnss_manager.cc
endif

# Optional sensors support.
ifeq ($(CHRE_SENSORS_SUPPORT_ENABLED), true)
COMMON_SRCS += $(CHRE_PREFIX)/core/sensor.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/sensor_request.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/sensor_request_manager.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/sensor_request_multiplexer.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/sensor_type.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/sensor_type_helpers.cc
endif

# Optional Wi-Fi support.
ifeq ($(CHRE_WIFI_SUPPORT_ENABLED), true)
COMMON_SRCS += $(CHRE_PREFIX)/core/wifi_request_manager.cc
COMMON_SRCS += $(CHRE_PREFIX)/core/wifi_scan_request.cc
endif

# Optional WWAN support.
ifeq ($(CHRE_WWAN_SUPPORT_ENABLED), true)
COMMON_SRCS += $(CHRE_PREFIX)/core/wwan_request_manager.cc
endif

# Optional Telemetry support.
ifeq ($(CHRE_TELEMETRY_SUPPORT_ENABLED), true)
COMMON_SRCS += $(CHRE_PREFIX)/core/telemetry_manager.cc

COMMON_CFLAGS += -DPB_FIELD_32BIT
COMMON_CFLAGS += -DCHRE_TELEMETRY_SUPPORT_ENABLED

NANOPB_EXTENSION = nanopb

NANOPB_SRCS += $(CHRE_PREFIX)/core/chre_metrics.proto

include $(CHRE_PREFIX)/build/nanopb.mk
endif

# GoogleTest Source Files ######################################################

GOOGLETEST_SRCS += $(CHRE_PREFIX)/core/tests/audio_util_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/core/tests/ble_request_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/core/tests/memory_manager_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/core/tests/request_multiplexer_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/core/tests/sensor_request_test.cc
GOOGLETEST_SRCS += $(CHRE_PREFIX)/core/tests/wifi_scan_request_test.cc
