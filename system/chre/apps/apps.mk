#
# Apps Makefile
#

# Common Compiler Flags ########################################################

COMMON_CFLAGS += -Iapps/include

# App makefiles ################################################################

ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
include apps/audio_world/audio_world.mk
endif

ifeq ($(CHRE_BLE_SUPPORT_ENABLED), true)
include apps/ble_world/ble_world.mk
endif

ifeq ($(CHRE_GNSS_SUPPORT_ENABLED), true)
include apps/gnss_world/gnss_world.mk
endif

ifeq ($(CHRE_WIFI_SUPPORT_ENABLED), true)
include apps/wifi_world/wifi_world.mk
endif

ifeq ($(CHRE_WWAN_SUPPORT_ENABLED), true)
include apps/wwan_world/wwan_world.mk
endif

include apps/debug_dump_world/debug_dump_world.mk
include apps/hello_world/hello_world.mk
include apps/host_awake_world/host_awake_world.mk
include apps/message_world/message_world.mk
include apps/sensor_world/sensor_world.mk
include apps/spammer/spammer.mk
include apps/timer_world/timer_world.mk
include apps/unload_tester/unload_tester.mk
