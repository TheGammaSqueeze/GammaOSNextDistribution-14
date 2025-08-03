#
# Platform Makefile
#

include $(CHRE_PREFIX)/external/flatbuffers/flatbuffers.mk

# Common Compiler Flags ########################################################

# Include paths.
COMMON_CFLAGS += -Iplatform/include

# SLPI-specific Compiler Flags #################################################

# Include paths.
SLPI_CFLAGS += -I$(SLPI_PREFIX)/build/ms
SLPI_CFLAGS += -I$(SLPI_PREFIX)/build/cust
SLPI_CFLAGS += -I$(SLPI_PREFIX)/core/api/debugtools
SLPI_CFLAGS += -I$(SLPI_PREFIX)/core/api/services
SLPI_CFLAGS += -I$(SLPI_PREFIX)/core/api/kernel/devcfg
SLPI_CFLAGS += -I$(SLPI_PREFIX)/core/api/kernel/qurt
SLPI_CFLAGS += -I$(SLPI_PREFIX)/core/api/dal
SLPI_CFLAGS += -I$(SLPI_PREFIX)/core/api/mproc
SLPI_CFLAGS += -I$(SLPI_PREFIX)/core/api/systemdrivers
SLPI_CFLAGS += -I$(SLPI_PREFIX)/platform/inc
SLPI_CFLAGS += -I$(SLPI_PREFIX)/platform/inc/HAP
SLPI_CFLAGS += -I$(SLPI_PREFIX)/platform/inc/a1std
SLPI_CFLAGS += -I$(SLPI_PREFIX)/platform/inc/stddef
SLPI_CFLAGS += -I$(SLPI_PREFIX)/platform/rtld/inc

SLPI_CFLAGS += -Iplatform/shared/aligned_alloc_unsupported/include
SLPI_CFLAGS += -Iplatform/shared/include
SLPI_CFLAGS += -Iplatform/slpi/include

# We use FlatBuffers in the SLPI platform layer
SLPI_CFLAGS += $(FLATBUFFERS_CFLAGS)

# SLPI still uses static event loop as oppose to heap based dynamic event loop
SLPI_CFLAGS += -DCHRE_STATIC_EVENT_LOOP

# SLPI/SEE-specific Compiler Flags #############################################

# Include paths.
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/chre/chre/src/system/chre/platform/slpi
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/core/api/kernel/libstd/stringl
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/qmimsgs/common/api
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/ssc_api/pb
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/ssc/framework/cm/inc
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/ssc/goog/api
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/ssc/inc
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/ssc/inc/internal
SLPI_SEE_CFLAGS += -I$(SLPI_PREFIX)/ssc/inc/utils/nanopb

SLPI_SEE_CFLAGS += -Iplatform/slpi/see/include

SLPI_SEE_CFLAGS += -DCHRE_SLPI_SEE

# Needed to define __SIZEOF_ATTR_THREAD in sns_osa_thread.h, included in
# sns_memmgr.h.
SLPI_SEE_CFLAGS += -DSSC_TARGET_HEXAGON

# Defined in slpi_proc/ssc/build/ssc.scons
SLPI_SEE_CFLAGS += -DPB_FIELD_16BIT

ifeq ($(IMPORT_CHRE_UTILS), true)
SLPI_SEE_CFLAGS += -DIMPORT_CHRE_UTILS
endif

# Enable accel calibration and ASH debug dump by default unless overridden
# explicitly by the environment.
ifneq ($(CHRE_ENABLE_ACCEL_CAL), false)
SLPI_SEE_CFLAGS += -DCHRE_ENABLE_ACCEL_CAL
endif

ifneq ($(CHRE_ENABLE_ASH_DEBUG_DUMP), false)
SLPI_SEE_CFLAGS += -DCHRE_ENABLE_ASH_DEBUG_DUMP
endif

# SLPI/QSH-specific Compiler Flags #############################################

# Include paths.
SLPI_QSH_CFLAGS += -I$(SLPI_PREFIX)/config/cust
SLPI_QSH_CFLAGS += -I$(SLPI_PREFIX)/qsh/qsh_nanoapp/inc
SLPI_QSH_CFLAGS += -Iplatform/slpi/see/include

ifeq ($(CHRE_USE_BUFFERED_LOGGING), true)
SLPI_QSH_CFLAGS += -DCHRE_USE_BUFFERED_LOGGING
endif

# Define CHRE_SLPI_SEE for the few components that are still shared between QSH
# and SEE.
SLPI_QSH_CFLAGS += -DCHRE_SLPI_SEE

# SLPI-specific Source Files ###################################################

SLPI_SRCS += platform/shared/assert.cc
SLPI_SRCS += platform/shared/chre_api_audio.cc
SLPI_SRCS += platform/shared/chre_api_core.cc
SLPI_SRCS += platform/shared/chre_api_gnss.cc
SLPI_SRCS += platform/shared/chre_api_re.cc
SLPI_SRCS += platform/shared/chre_api_user_settings.cc
SLPI_SRCS += platform/shared/chre_api_version.cc
SLPI_SRCS += platform/shared/chre_api_wifi.cc
SLPI_SRCS += platform/shared/chre_api_wwan.cc
SLPI_SRCS += platform/shared/host_link.cc
SLPI_SRCS += platform/shared/host_protocol_chre.cc
SLPI_SRCS += platform/shared/host_protocol_common.cc
SLPI_SRCS += platform/shared/memory_manager.cc
SLPI_SRCS += platform/shared/nanoapp_abort.cc
SLPI_SRCS += platform/shared/nanoapp_load_manager.cc
SLPI_SRCS += platform/shared/nanoapp/nanoapp_dso_util.cc
SLPI_SRCS += platform/shared/pal_system_api.cc
SLPI_SRCS += platform/shared/platform_debug_dump_manager.cc
SLPI_SRCS += platform/shared/system_time.cc
SLPI_SRCS += platform/shared/version.cc
SLPI_SRCS += platform/slpi/chre_api_re.cc
SLPI_SRCS += platform/slpi/fatal_error.cc
SLPI_SRCS += platform/slpi/host_link.cc
SLPI_SRCS += platform/slpi/init.cc
SLPI_SRCS += platform/slpi/memory.cc
SLPI_SRCS += platform/slpi/memory_manager.cc
SLPI_SRCS += platform/slpi/platform_nanoapp.cc
SLPI_SRCS += platform/slpi/platform_pal.cc
SLPI_SRCS += platform/slpi/platform_sensor_type_helpers.cc
SLPI_SRCS += platform/slpi/system_time.cc
SLPI_SRCS += platform/slpi/system_time_util.cc
SLPI_SRCS += platform/slpi/system_timer.cc

# Optional audio support.
ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
SLPI_SRCS += platform/slpi/platform_audio.cc
endif

# Optional GNSS support.
ifeq ($(CHRE_GNSS_SUPPORT_ENABLED), true)
SLPI_SRCS += platform/shared/platform_gnss.cc
endif

# Optional Wi-Fi support.
ifeq ($(CHRE_WIFI_SUPPORT_ENABLED), true)
SLPI_SRCS += platform/shared/platform_wifi.cc
endif

# Optional WWAN support.
ifeq ($(CHRE_WWAN_SUPPORT_ENABLED), true)
SLPI_SRCS += platform/shared/platform_wwan.cc
endif

# SLPI/SEE-specific Source Files ###############################################

# Optional sensors support.
ifeq ($(CHRE_SENSORS_SUPPORT_ENABLED), true)
SLPI_SEE_SRCS += platform/slpi/see/platform_sensor.cc
SLPI_SEE_SRCS += platform/slpi/see/platform_sensor_manager.cc
ifneq ($(IMPORT_CHRE_UTILS), true)
SLPI_SEE_SRCS += platform/slpi/see/see_cal_helper.cc
SLPI_SEE_SRCS += platform/slpi/see/see_helper.cc
endif

SLPI_SEE_SRCS += platform/shared/chre_api_sensor.cc
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_client.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_suid.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_cal.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_physical_sensor_test.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_proximity.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_remote_proc_state.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_resampler.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_std.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_std_sensor.pb.c
SLPI_SEE_SRCS += $(SLPI_PREFIX)/ssc_api/pb/sns_std_type.pb.c

SLPI_SEE_QSK_SRCS += $(SLPI_PREFIX)/chre/chre/src/system/chre/platform/slpi/sns_qmi_client_alt.c
SLPI_SEE_QMI_SRCS += $(SLPI_PREFIX)/chre/chre/src/system/chre/platform/slpi/sns_qmi_client.c
endif

SLPI_SEE_SRCS += platform/slpi/see/power_control_manager.cc

ifneq ($(IMPORT_CHRE_UTILS), true)
SLPI_SEE_SRCS += platform/slpi/see/island_vote_client.cc
endif

# SLPI/QSH-specific Source Files ###############################################

SLPI_QSH_SRCS += platform/slpi/see/island_vote_client.cc
SLPI_QSH_SRCS += platform/slpi/see/power_control_manager.cc
SLPI_QSH_SRCS += platform/slpi/qsh/qsh_proto_shim.cc

ifeq ($(CHRE_USE_BUFFERED_LOGGING), true)
SLPI_QSH_SRCS += platform/shared/log_buffer.cc
SLPI_QSH_SRCS += platform/shared/log_buffer_manager.cc
SLPI_QSH_SRCS += platform/slpi/log_buffer_manager.cc
endif


# Simulator-specific Compiler Flags ############################################

SIM_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/include
SIM_CFLAGS += -Iplatform/linux/sim/include

# Simulator-specific Source Files ##############################################

SIM_SRCS += platform/linux/chre_api_re.cc
SIM_SRCS += platform/linux/context.cc
SIM_SRCS += platform/linux/fatal_error.cc
SIM_SRCS += platform/linux/host_link.cc
SIM_SRCS += platform/linux/memory.cc
SIM_SRCS += platform/linux/memory_manager.cc
SIM_SRCS += platform/linux/platform_debug_dump_manager.cc
SIM_SRCS += platform/linux/platform_log.cc
SIM_SRCS += platform/linux/platform_pal.cc
SIM_SRCS += platform/linux/power_control_manager.cc
SIM_SRCS += platform/linux/system_time.cc
SIM_SRCS += platform/linux/system_timer.cc
SIM_SRCS += platform/linux/platform_nanoapp.cc
SIM_SRCS += platform/linux/task_util/task.cc
SIM_SRCS += platform/linux/task_util/task_manager.cc
SIM_SRCS += platform/shared/chre_api_audio.cc
SIM_SRCS += platform/shared/chre_api_ble.cc
SIM_SRCS += platform/shared/chre_api_core.cc
SIM_SRCS += platform/shared/chre_api_gnss.cc
SIM_SRCS += platform/shared/chre_api_re.cc
SIM_SRCS += platform/shared/chre_api_sensor.cc
SIM_SRCS += platform/shared/chre_api_user_settings.cc
SIM_SRCS += platform/shared/chre_api_version.cc
SIM_SRCS += platform/shared/chre_api_wifi.cc
SIM_SRCS += platform/shared/chre_api_wwan.cc
SIM_SRCS += platform/shared/memory_manager.cc
SIM_SRCS += platform/shared/nanoapp_abort.cc
SIM_SRCS += platform/shared/nanoapp/nanoapp_dso_util.cc
SIM_SRCS += platform/shared/pal_system_api.cc
SIM_SRCS += platform/shared/system_time.cc
SIM_SRCS += platform/shared/version.cc

# Optional audio support.
ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
SIM_SRCS += platform/linux/pal_audio.cc
endif

# Optional BLE support.
ifeq ($(CHRE_BLE_SUPPORT_ENABLED), true)
SIM_SRCS += platform/linux/pal_ble.cc
SIM_SRCS += platform/shared/platform_ble.cc
endif

# Optional GNSS support.
ifeq ($(CHRE_GNSS_SUPPORT_ENABLED), true)
SIM_SRCS += platform/linux/pal_gnss.cc
SIM_SRCS += platform/shared/platform_gnss.cc
endif

# Optional sensor support.
ifeq ($(CHRE_SENSORS_SUPPORT_ENABLED), true)
SIM_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/sensor_pal/include
SIM_SRCS += platform/linux/pal_sensor.cc
SIM_SRCS += platform/shared/sensor_pal/platform_sensor.cc
SIM_SRCS += platform/shared/sensor_pal/platform_sensor_manager.cc
SIM_SRCS += platform/shared/sensor_pal/platform_sensor_type_helpers.cc
endif

# Optional Wi-Fi support.
ifeq ($(CHRE_WIFI_SUPPORT_ENABLED), true)
ifeq ($(CHRE_WIFI_NAN_SUPPORT_ENABLED), true)
SIM_SRCS += platform/linux/pal_nan.cc
endif
SIM_SRCS += platform/linux/pal_wifi.cc
SIM_SRCS += platform/shared/platform_wifi.cc
endif

# Optional WWAN support.
ifeq ($(CHRE_WWAN_SUPPORT_ENABLED), true)
SIM_SRCS += platform/linux/pal_wwan.cc
SIM_SRCS += platform/shared/platform_wwan.cc
endif

# Linux-specific Compiler Flags ################################################

GOOGLE_X86_LINUX_CFLAGS += -Iplatform/linux/include

# Linux-specific Source Files ##################################################

GOOGLE_X86_LINUX_SRCS += platform/linux/init.cc
GOOGLE_X86_LINUX_SRCS += platform/linux/assert.cc
GOOGLE_X86_LINUX_SRCS += platform/linux/task_util/task.cc
GOOGLE_X86_LINUX_SRCS += platform/linux/task_util/task_manager.cc
GOOGLE_X86_LINUX_SRCS += platform/shared/nanoapp_abort.cc

# Optional audio support.
ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
GOOGLE_X86_LINUX_SRCS += platform/linux/sim/audio_source.cc
GOOGLE_X86_LINUX_SRCS += platform/linux/sim/platform_audio.cc
endif

# Optional WiFi NAN support
ifeq ($(CHRE_WIFI_NAN_SUPPORT_ENABLED), true)
GOOGLE_X86_LINUX_SRCS += platform/linux/pal_nan.cc
endif

# Android-specific Compiler Flags ##############################################

# Add the Android include search path for Android-specific header files.
GOOGLE_ARM64_ANDROID_CFLAGS += -Iplatform/android/include

# Add in host sources to allow the executable to both be a socket server and
# CHRE implementation.
GOOGLE_ARM64_ANDROID_CFLAGS += -I$(ANDROID_BUILD_TOP)/system/libbase/include
GOOGLE_ARM64_ANDROID_CFLAGS += -I$(ANDROID_BUILD_TOP)/system/core/libcutils/include
GOOGLE_ARM64_ANDROID_CFLAGS += -I$(ANDROID_BUILD_TOP)/system/core/libutils/include
GOOGLE_ARM64_ANDROID_CFLAGS += -I$(ANDROID_BUILD_TOP)/system/logging/liblog/include
GOOGLE_ARM64_ANDROID_CFLAGS += -Ihost/common/include

# Also add the linux sources to fall back to the default Linux implementation.
GOOGLE_ARM64_ANDROID_CFLAGS += -Iplatform/linux/include

# We use FlatBuffers in the Android simulator
GOOGLE_ARM64_ANDROID_CFLAGS += -I$(FLATBUFFERS_PATH)/include

# Android-specific Source Files ################################################

ANDROID_CUTILS_TOP = $(ANDROID_BUILD_TOP)/system/core/libcutils
ANDROID_LOG_TOP = $(ANDROID_BUILD_TOP)/system/logging/liblog

GOOGLE_ARM64_ANDROID_SRCS += $(ANDROID_CUTILS_TOP)/sockets_unix.cpp
GOOGLE_ARM64_ANDROID_SRCS += $(ANDROID_CUTILS_TOP)/android_get_control_file.cpp
GOOGLE_ARM64_ANDROID_SRCS += $(ANDROID_CUTILS_TOP)/socket_local_server_unix.cpp
GOOGLE_ARM64_ANDROID_SRCS += $(ANDROID_CUTILS_TOP)/socket_local_client_unix.cpp
GOOGLE_ARM64_ANDROID_SRCS += $(ANDROID_LOG_TOP)/logd_reader.c

GOOGLE_ARM64_ANDROID_SRCS += platform/android/init.cc
GOOGLE_ARM64_ANDROID_SRCS += platform/android/host_link.cc
GOOGLE_ARM64_ANDROID_SRCS += platform/shared/host_protocol_common.cc
GOOGLE_ARM64_ANDROID_SRCS += platform/shared/nanoapp_abort.cc
GOOGLE_ARM64_ANDROID_SRCS += host/common/host_protocol_host.cc
GOOGLE_ARM64_ANDROID_SRCS += host/common/socket_server.cc

# Optional audio support.
ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
GOOGLE_ARM64_ANDROID_SRCS += platform/android/platform_audio.cc
endif

# GoogleTest Compiler Flags ####################################################

GOOGLETEST_CFLAGS += $(FLATBUFFERS_CFLAGS)

# The order here is important so that the googletest target prefers shared,
# linux and then SLPI.
GOOGLETEST_CFLAGS += -Iplatform/shared/include
GOOGLETEST_CFLAGS += -Iplatform/linux/include
GOOGLETEST_CFLAGS += -Iplatform/slpi/include
GOOGLETEST_CFLAGS += -Iplatform/shared/pw_trace/include

# GoogleTest Source Files ######################################################

GOOGLETEST_COMMON_SRCS += platform/linux/assert.cc
GOOGLETEST_COMMON_SRCS += platform/linux/sim/audio_source.cc
GOOGLETEST_COMMON_SRCS += platform/linux/sim/platform_audio.cc
GOOGLETEST_COMMON_SRCS += platform/linux/tests/task_test.cc
GOOGLETEST_COMMON_SRCS += platform/linux/tests/task_manager_test.cc
GOOGLETEST_COMMON_SRCS += platform/tests/log_buffer_test.cc
GOOGLETEST_COMMON_SRCS += platform/tests/trace_test.cc
GOOGLETEST_COMMON_SRCS += platform/shared/log_buffer.cc
GOOGLETEST_COMMON_SRCS += platform/shared/nanoapp_abort.cc
ifeq ($(CHRE_WIFI_NAN_SUPPORT_ENABLED), true)
GOOGLETEST_COMMON_SRCS += platform/linux/pal_nan.cc
endif

# EmbOS specific compiler flags
EMBOS_CFLAGS += -I$(CHRE_PREFIX)/platform/embos/include
EMBOS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/aligned_alloc_unsupported/include
EMBOS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/include
EMBOS_CFLAGS += $(FLATBUFFERS_CFLAGS)

# The IAR flavor of EmbOS's RTOS.h includes an intrinsics.h header for
# optimized enabling and disabling interrupts. We add an empty header to that
# name in the path below, and let the linker deal with finding the symbol.
EMBOS_CFLAGS += -I$(CHRE_PREFIX)/platform/embos/include/chre/embos

EMBOS_SRCS += $(CHRE_PREFIX)/platform/arm/nanoapp_loader.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/embos/context.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/embos/init.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/embos/memory.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/embos/memory_manager.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/embos/system_timer.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/assert.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_audio.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_ble.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_core.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_gnss.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_re.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_user_settings.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_version.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_wifi.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_wwan.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/host_protocol_chre.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/host_protocol_common.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/dlfcn.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/dram_vote_client.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/memory_manager.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/pal_system_api.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/pal_sensor_stub.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/platform_debug_dump_manager.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/system_time.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/version.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp_abort.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp/nanoapp_dso_util.cc
EMBOS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp_loader.cc

# Exynos specific compiler flags
EXYNOS_CFLAGS += -I$(CHRE_PREFIX)/platform/exynos/include
EXYNOS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/audio_pal/include

EXYNOS_SRCS += $(CHRE_PREFIX)/platform/exynos/chre_api_re.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/shared/host_link.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/exynos/host_link.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/exynos/memory.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/exynos/platform_cache_management.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/exynos/platform_nanoapp.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/exynos/platform_pal.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/exynos/power_control_manager.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/exynos/system_time.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp_abort.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp_load_manager.cc

EXYNOS_SRCS += $(FLATBUFFERS_SRCS)

# Optional sensors support
ifeq ($(CHRE_SENSORS_SUPPORT_ENABLED), true)
EXYNOS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/sensor_pal/include
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_sensor.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/shared/sensor_pal/platform_sensor_manager.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/shared/sensor_pal/platform_sensor.cc
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/shared/sensor_pal/platform_sensor_type_helpers.cc
endif

ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
EXYNOS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/audio_pal/include
EXYNOS_SRCS += $(CHRE_PREFIX)/platform/shared/audio_pal/platform_audio.cc
endif

# ARM specific compiler flags
ARM_CFLAGS += -I$(CHRE_PREFIX)/platform/arm/include

# Tinysys Configurations ######################################################

# Tinysys sources
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/authentication.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/chre_api_re.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/chre_init.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/condition_variable_base.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/host_cpu_update.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/host_link.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/log_buffer_manager.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/memory.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/platform_cache_management.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/platform_pal.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/stdlib_wrapper.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/system_time.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/system_timer.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/power_control_manager.cc

# Freertos sources
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/freertos/context.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/freertos/init.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/freertos/platform_nanoapp.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/freertos/memory_manager.cc

# RISCV
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/riscv/nanoapp_loader.cc

# Shared sources
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/assert.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_audio.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_ble.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_core.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_gnss.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_re.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_user_settings.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_version.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_wifi.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_wwan.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/dram_vote_client.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/dlfcn.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/host_link.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/host_protocol_chre.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/host_protocol_common.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/log_buffer.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/log_buffer_manager.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/memory_manager.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp_abort.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp_load_manager.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp_loader.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/pal_system_api.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/platform_debug_dump_manager.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/system_time.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/version.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/nanoapp/nanoapp_dso_util.cc
TINYSYS_SRCS += $(MBEDTLS_SRCS)

ifeq ($(CHRE_BLE_SUPPORT_ENABLED), true)
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/platform_ble.cc
endif

ifeq ($(CHRE_SENSORS_SUPPORT_ENABLED), true)
TINYSYS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/sensor_pal/include
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/chre_api_sensor.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/sensor_pal/platform_sensor_manager.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/sensor_pal/platform_sensor.cc
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/shared/sensor_pal/platform_sensor_type_helpers.cc
endif

ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
TINYSYS_SRCS += $(CHRE_PREFIX)/platform/tinysys/platform_audio.cc
endif

# Compiler flags

# Variables
TINYSYS_PLATFORM = mt6985

# CHRE include paths
TINYSYS_CFLAGS += -I$(CHRE_PREFIX)/platform/tinysys/include
TINYSYS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/include
TINYSYS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/aligned_alloc_unsupported/include
TINYSYS_CFLAGS += -I$(CHRE_PREFIX)/platform/freertos/include
TINYSYS_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/include/chre/platform/shared/libc

TINYSYS_CFLAGS += $(FLATBUFFERS_CFLAGS)
TINYSYS_CFLAGS += $(MBEDTLS_CFLAGS)

TINYSYS_CFLAGS += -DCFG_DRAM_HEAP_SUPPORT
TINYSYS_CFLAGS += -DCHRE_LOADER_ARCH=EM_RISCV
TINYSYS_CFLAGS += -DCHRE_NANOAPP_LOAD_ALIGNMENT=4096
