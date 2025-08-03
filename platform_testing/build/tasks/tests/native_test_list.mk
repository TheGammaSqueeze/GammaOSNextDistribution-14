# Copyright (C) 2016 The Android Open Source Project
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

native_tests := \
    aaudio_test_mmap_path \
    adbd_test \
    android_logger_test_src_lib \
    android_logger_test_tests_config_log_level \
    android_logger_test_tests_default_init \
    android_logger_test_tests_multiple_init \
    anyhow_test_src_lib \
    anyhow_test_tests_test_autotrait \
    anyhow_test_tests_test_boxed \
    anyhow_test_tests_test_chain \
    anyhow_test_tests_test_context \
    anyhow_test_tests_test_convert \
    anyhow_test_tests_test_downcast \
    anyhow_test_tests_test_fmt \
    anyhow_test_tests_test_macros \
    anyhow_test_tests_test_repr \
    anyhow_test_tests_test_source \
    audio_health_tests \
    bionic-unit-tests \
    bionic-unit-tests-static \
    bluetooth_test_common \
    bootstat_tests \
    boringssl_crypto_test \
    boringssl_ssl_test \
    bsdiff_unittest \
    buffer_hub-test \
    bugreportz_test \
    bytes_test_tests_test_buf \
    bytes_test_tests_test_buf_mut \
    bytes_test_tests_test_bytes \
    bytes_test_tests_test_bytes_odd_alloc \
    bytes_test_tests_test_bytes_vec_alloc \
    bytes_test_tests_test_chain \
    bytes_test_tests_test_debug \
    bytes_test_tests_test_iter \
    bytes_test_tests_test_reader \
    bytes_test_tests_test_take \
    camera_client_test \
    cesu8_test_src_lib \
    clatd_test \
    confirmationui_invocation_test \
    debuggerd_test \
    doh_ffi_test \
    doh_unit_test \
    dumpstate_test \
    dumpstate_test_fixture \
    dumpsys_test \
    gpuservice_unittest \
    gwp_asan_unittest \
    hello_world_test \
    hwui_unit_tests \
    incident_helper_test \
    incidentd_test \
    inputflinger_tests \
    installd_cache_test \
    installd_dexopt_test \
    installd_file_test \
    installd_otapreopt_test \
    installd_service_test \
    installd_utils_test \
    jni_test_src_lib \
    keystore2_crypto_test_rust \
    keystore2_selinux_test \
    keystore2_test \
    lazy_static_test_tests_test \
    libandroidfw_tests \
    libappfuse_test \
    libbase_test \
    libbinder_rs-internal_test \
    libbpf_android_test \
    libcutils_test \
    libcutils_test_static \
    libgui_test \
    libhidl_test \
    libinput_tests \
    libjavacore-unit-tests \
    liblog-unit-tests \
    libminijail_unittest_gtest \
    libnativehelper_tests \
    libnetworkstats_test \
    libprocinfo_test \
    libtextclassifier_tests-tplus \
    libtextclassifier_tests-sminus \
    libsurfaceflinger_unittest \
    libunwindstack_unit_test \
    libuwb_core_tests \
    libuwb_uci_jni_rust_tests \
    libuwb_uci_packet_tests \
    libuci_hal_android_tests \
    libvintf_test \
    linker-unit-tests \
    logcat-unit-tests \
    logd-unit-tests \
    logger_device_unit_tests \
    kernel-config-unit-tests \
    malloc_debug_unit_tests \
    memory_replay_tests \
    memunreachable_test \
    minadbd_test \
    minikin_tests \
    mj_system_unittest_gtest \
    mj_util_unittest_gtest \
    mtp_ffs_handle_test \
    netd_integration_test \
    netd_unit_test \
    netdutils_test \
    nfc_test_utils \
    num-traits_test_src_lib \
    num-traits_test_tests_cast \
    perfetto_integrationtests \
    posix_async_io_test \
    prioritydumper_test \
    puffin_unittest \
    quiche_device_test_src_lib \
    recovery_unit_test \
    resolv_gold_test \
    resolv_integration_test \
    resolv_unit_test \
    ring_test_src_lib \
    ring_test_tests_aead_tests \
    ring_test_tests_agreement_tests \
    ring_test_tests_constant_time_tests \
    ring_test_tests_digest_tests \
    ring_test_tests_ecdsa_tests \
    ring_test_tests_ed25519_tests \
    ring_test_tests_hkdf_tests \
    ring_test_tests_hmac_tests \
    ring_test_tests_pbkdf2_tests \
    ring_test_tests_quic_tests \
    ring_test_tests_rand_tests \
    ring_test_tests_rsa_tests \
    ring_test_tests_signature_tests \
    scrape_mmap_addr \
    simpleperf_cpu_hotplug_test \
    simpleperf_unit_test \
    statsd_test \
    syscall_filter_unittest_gtest \
    time-unit-tests \
    tokio_test_tests__require_full \
    tokio_test_tests_buffered \
    tokio_test_tests_io_async_fd \
    tokio_test_tests_io_async_read \
    tokio_test_tests_io_chain \
    tokio_test_tests_io_copy \
    tokio_test_tests_io_copy_bidirectional \
    tokio_test_tests_io_driver \
    tokio_test_tests_io_driver_drop \
    tokio_test_tests_io_lines \
    tokio_test_tests_io_mem_stream \
    tokio_test_tests_io_read \
    tokio_test_tests_io_read_buf \
    tokio_test_tests_io_read_exact \
    tokio_test_tests_io_read_line \
    tokio_test_tests_io_read_to_end \
    tokio_test_tests_io_read_to_string \
    tokio_test_tests_io_read_until \
    tokio_test_tests_io_split \
    tokio_test_tests_io_take \
    tokio_test_tests_io_write \
    tokio_test_tests_io_write_all \
    tokio_test_tests_io_write_buf \
    tokio_test_tests_io_write_int \
    tokio_test_tests_macros_join \
    tokio_test_tests_macros_pin \
    tokio_test_tests_macros_select \
    tokio_test_tests_macros_test \
    tokio_test_tests_macros_try_join \
    tokio_test_tests_net_bind_resource \
    tokio_test_tests_net_lookup_host \
    tokio_test_tests_no_rt \
    tokio_test_tests_process_kill_on_drop \
    tokio_test_tests_rt_basic \
    tokio_test_tests_rt_common \
    tokio_test_tests_rt_threaded \
    tokio_test_tests_sync_barrier \
    tokio_test_tests_sync_broadcast \
    tokio_test_tests_sync_errors \
    tokio_test_tests_sync_mutex \
    tokio_test_tests_sync_mutex_owned \
    tokio_test_tests_sync_notify \
    tokio_test_tests_sync_oneshot \
    tokio_test_tests_sync_rwlock \
    tokio_test_tests_sync_semaphore \
    tokio_test_tests_sync_semaphore_owned \
    tokio_test_tests_sync_watch \
    tokio_test_tests_task_abort \
    tokio_test_tests_task_blocking \
    tokio_test_tests_task_local \
    tokio_test_tests_task_local_set \
    tokio_test_tests_tcp_accept \
    tokio_test_tests_tcp_connect \
    tokio_test_tests_tcp_echo \
    tokio_test_tests_tcp_into_split \
    tokio_test_tests_tcp_into_std \
    tokio_test_tests_tcp_peek \
    tokio_test_tests_tcp_shutdown \
    tokio_test_tests_tcp_socket \
    tokio_test_tests_tcp_split \
    tokio_test_tests_time_rt \
    tokio_test_tests_udp \
    tokio_test_tests_uds_cred \
    tokio_test_tests_uds_split \
    tokio-test_test_tests_block_on \
    tokio-test_test_tests_io \
    tokio-test_test_tests_macros \
    unicode-xid_test_src_lib \
    update_engine_unittests \
    url_test_tests_data \
    url_test_tests_unit \
    vintf_object_test \
    wificond_unit_test \
    ziparchive-tests \
    GraphicBuffer_test \
    NeuralNetworksTest_mt_static \
    NeuralNetworksTest_operations \
    NeuralNetworksTest_static \
    NeuralNetworksTest_utils \
    SurfaceFlinger_test \
    lmkd_unit_test

ifeq ($(BOARD_IS_AUTOMOTIVE), true)
native_tests += \
    libwatchdog_test \
    evsmanagerd_test \
    sv_2d_session_tests \
    sv_3d_session_tests
endif

ifneq ($(strip $(BOARD_PERFSETUP_SCRIPT)),)
native_tests += perf-setup
endif
