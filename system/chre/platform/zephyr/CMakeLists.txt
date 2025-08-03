# Copyright (C) 2022 The Android Open Source Project
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

if(CONFIG_CHRE)
  get_filename_component(CHRE_DIR "${ZEPHYR_CURRENT_MODULE_DIR}" ABSOLUTE)

  zephyr_library()

  set(CHRE_INCLUDE_DIRS
      "${CHRE_DIR}/chpp/include"
      "${CHRE_DIR}/chre_api/include"
      "${CHRE_DIR}/chre_api/include/chre_api"
      "${CHRE_DIR}/core/include"
      "${CHRE_DIR}/pal/include"
      "${CHRE_DIR}/platform/include"
      "${CHRE_DIR}/platform/shared/include"
      "${CHRE_DIR}/util/include"
      "${CMAKE_CURRENT_SOURCE_DIR}/include")
  zephyr_include_directories("${CHRE_INCLUDE_DIRS}")
  zephyr_library_include_directories("${CHRE_INCLUDE_DIRS}")

  zephyr_library_sources(
      "context.cc"
      "host_link.cc"
      "init.cc"
      "log_module.c"
      "memory.cc"
      "platform_nanoapp.cc"
      "power_control_manager.cc"
      "system_time.cc"
      "system_timer.cc"
      "${CHRE_DIR}/core/debug_dump_manager.cc"
      "${CHRE_DIR}/core/event.cc"
      "${CHRE_DIR}/core/event_loop.cc"
      "${CHRE_DIR}/core/event_loop_manager.cc"
      "${CHRE_DIR}/core/event_ref_queue.cc"
      "${CHRE_DIR}/core/host_comms_manager.cc"
      "${CHRE_DIR}/core/init.cc"
      "${CHRE_DIR}/core/nanoapp.cc"
      "${CHRE_DIR}/core/settings.cc"
      "${CHRE_DIR}/core/static_nanoapps.cc"
      "${CHRE_DIR}/core/timer_pool.cc"
      "${CHRE_DIR}/platform/shared/version.cc"
      "${CHRE_DIR}/platform/shared/system_time.cc"
      "${CHRE_DIR}/util/buffer_base.cc"
      "${CHRE_DIR}/util/dynamic_vector_base.cc"
  )
  zephyr_linker_sources(SECTIONS linker_chre.ld)

  # Optional audio support
  if(CONFIG_CHRE_AUDIO_SUPPORT_ENABLED)
    zephyr_compile_definitions(CHRE_AUDIO_SUPPORT_ENABLED)
    zephyr_library_sources("${CHRE_DIR}/core/audio_request_manager.cc")
  endif()

  # Optional GNSS support
  if(CONFIG_CHRE_GNSS_SUPPORT_ENABLED)
    zephyr_compile_definitions(CHRE_GNSS_SUPPORT_ENABLED)
    zephyr_library_sources("${CHRE_DIR}/core/gnss_manager.cc")
  endif()

  # Optional sensor support
  if(CONFIG_CHRE_SENSORS_SUPPORT_ENABLED)
    zephyr_compile_definitions(CHRE_SENSORS_SUPPORT_ENABLED)
    zephyr_library_sources(
        "${CHRE_DIR}/core/sensor.cc"
        "${CHRE_DIR}/core/sensor_request.cc"
        "${CHRE_DIR}/core/sensor_request_manager.cc"
        "${CHRE_DIR}/core/sensor_request_multiplexer.cc"
        "${CHRE_DIR}/core/sensor_type.cc"
        "${CHRE_DIR}/core/sensor_type_helpers.cc"
    )
  endif()

  # Optional WiFi support
  if(CONFIG_CHRE_WIFI_SUPPORT_ENABLED)
    zephyr_compile_definitions(CHRE_WIFI_SUPPORT_ENABLED)
    zephyr_library_sources(
        "${CHRE_DIR}/core/wifi_request_manager.cc"
        "${CHRE_DIR}/core/wifi_scan_request.cc"
    )
  endif()

  # Optional WWAN support
  if(CONFIG_CHRE_WWAN_SUPPORT_ENABLED)
    zephyr_compile_definitions(CHRE_WWAN_SUPPORT_ENABLED)
    zephyr_library_sources("${CHRE_DIR}/core/wwan_request_manager.cc")
  endif()

  zephyr_compile_definitions(
      CHRE_MESSAGE_TO_HOST_MAX_SIZE=${CONFIG_CHRE_MESSAGE_TO_HOST_MAX_SIZE})

  zephyr_compile_definitions(CHRE_FILENAME=__FILE__)

  # Add logging definitions
  if((NOT DEFINED CONFIG_CHRE_LOG_LEVEL) OR
     "${CONFIG_CHRE_LOG_LEVEL}" EQUAL "0")
    zephyr_compile_definitions(CHRE_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_MUTE)
  elseif("${CONFIG_CHRE_LOG_LEVEL}" EQUAL "1")
    zephyr_compile_definitions(CHRE_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_ERROR)
  elseif("${CONFIG_CHRE_LOG_LEVEL}" EQUAL "2")
    zephyr_compile_definitions(CHRE_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_WARN)
  elseif("${CONFIG_CHRE_LOG_LEVEL}" EQUAL "3")
    zephyr_compile_definitions(CHRE_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_INFO)
  elseif(("${CONFIG_CHRE_LOG_LEVEL}" EQUAL "4") OR
         ("${CONFIG_CHRE_LOG_LEVEL}" EQUAL "5"))
    # Debug and verbose are collapsed into one since Zephyr doesn't
    # differentiate the two levels
    zephyr_compile_definitions(CHRE_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_VERBOSE)
  endif()

  if(DEFINED CONFIG_CHRE_ASSERTIONS)
    zephyr_compile_definitions(CHRE_ASSERTIONS_ENABLED)
  else()
    zephyr_compile_definitions(CHRE_ASSERTIONS_DISABLED)
  endif()

  zephyr_compile_definitions(CHRE_FIRST_SUPPORTED_API_VERSION=CHRE_API_VERSION_1_5)
endif()
