#!/usr/bin/env bash

###############
# Build and test targets for device target platform.
###############
BUILD_TARGETS=(
  //art/...
  //bionic/...
  //bootable/recovery/tools/recovery_l10n/...
  //build/...
  //cts/...
  //development/...
  //external/rust/crates/rustc-demangle-capi:librustc_demangle_static
  //frameworks/av/media/liberror:libexpectedutils_test
  //frameworks/av/media/module/foundation:libstagefright_foundation
  //frameworks/base:framework-javastream-protos
  //frameworks/base/api:merge_annotation_zips_test
  //frameworks/base/services/core:statslog-art-java-gen
  //frameworks/base/tools/aapt2:aapt2_tests
  //frameworks/base/tools/processors/immutability:ImmutabilityAnnotation
  //frameworks/native/cmds/installd:run_dex2oat_test
  //frameworks/native/libs/binder/tests:binderUtilsHostTest
  //frameworks/native/libs/fakeservicemanager:fakeservicemanager_test
  //hardware/...
  //libnativehelper/...
  //packages/modules/adb/...
  //packages/modules/common/...
  //packages/modules/CaptivePortalLogin/...
  //packages/modules/NeuralNetworks/...
  //packages/modules/Wifi/...
  //prebuilts/clang/host/linux-x86:all
  //prebuilts/build-tools/tests/...
  //prebuilts/runtime/...
  //prebuilts/rust/linux-x86/...
  //prebuilts/tools/...
  //platform_testing/...
  //system/libbase:libbase
  //system/core/libcutils:libcutils
  //system/core/libutils:libutils
  //system/unwinding/libunwindstack:libunwindstack
  //tools/apksig/...
  //tools/asuite/...
  //tools/platform-compat/...

  # TODO: b/305044271 - Fix linking error caused by fdo transition
  -//art/libartbase:all
  -//art/libdexfile:all
  # TODO(b/266459895): remove these after re-enabling libunwindstack
  -//bionic/libc/malloc_debug:libc_malloc_debug
  -//bionic/libfdtrack:libfdtrack
  -//frameworks/av/media/codec2/hal/hidl/1.0/utils:libcodec2_hidl@1.0
  -//frameworks/av/media/codec2/hal/hidl/1.1/utils:libcodec2_hidl@1.1
  -//frameworks/av/media/codec2/hal/hidl/1.2/utils:libcodec2_hidl@1.2
  -//frameworks/av/media/module/bqhelper:libstagefright_bufferqueue_helper_novndk
  -//frameworks/av/media/module/codecserviceregistrant:libmedia_codecserviceregistrant
  -//frameworks/av/services/mediacodec:mediaswcodec
  -//frameworks/native/libs/gui:libgui
  -//frameworks/native/libs/gui:libgui_bufferqueue_static
  -//frameworks/native/opengl/libs:libEGL
  -//frameworks/native/opengl/libs:libGLESv2
)

DEVICE_ONLY_TARGETS=(
  //frameworks/native/services/surfaceflinger:libSurfaceFlingerProp
  //frameworks/base/cmds/idmap2:libidmap2_policies
  //frameworks/base/core/res:framework-res
  //frameworks/ex/common:android-common
  //frameworks/native/opengl/tests/testViewport:TestViewport
)

TEST_TARGETS=(
  //build/bazel/...
  //prebuilts/clang/host/linux-x86:all
  //prebuilts/sdk:toolchains_have_all_prebuilts
)

HOST_ONLY_TEST_TARGETS=(
  //build/make/tools/aconfig:aconfig
  //frameworks/base/tools/lint/common:AndroidCommonLint
  //frameworks/base/tools/processors/immutability:ImmutabilityAnnotationProcessorHostLibrary
  //frameworks/base/tools/processors/view_inspector:libview-inspector-annotation-processor
  //tools/trebuchet:AnalyzerKt
  //tools/metalava/metalava:metalava
  # This is explicitly listed to prevent b/294514745
  //packages/modules/adb:adb_test
  # TODO (b/282953338): these tests depend on adb which is unconverted
  -//packages/modules/adb:adb_integration_test_adb
  -//packages/modules/adb:adb_integration_test_device
  # TODO - b/297952899: this test is flaky in b builds
  -//build/soong/cmd/zip2zip:zip2zip-test
)

# These targets are used to ensure that the aosp-specific rule wrappers forward
# all providers of the underlying rule.
EXAMPLE_WRAPPER_TARGETS=(
  # java_import wrapper
  //build/bazel/examples/java/com/bazel:hello_java_import
  # java_library wrapper
  //build/bazel/examples/java/com/bazel:hello_java_lib
  # kt_jvm_library wrapper
  //build/bazel/examples/java/com/bazel:some_kotlin_lib
  # android_library wrapper
  //build/bazel/examples/android_app/java/com/app:applib
  # android_binary wrapper
  //build/bazel/examples/android_app/java/com/app:app
  # aar_import wrapper
  //build/bazel/examples/android_app/java/com/app:import
)

# These targets are used for CI and are expected to be very
# unlikely to become incompatible or broken.
STABLE_BUILD_TARGETS=(
  //packages/modules/adb/crypto/tests:adb_crypto_test
  //packages/modules/adb/pairing_auth/tests:adb_pairing_auth_test
  //packages/modules/adb/pairing_connection/tests:adb_pairing_connection_test
  //packages/modules/adb/tls/tests:adb_tls_connection_test
  //packages/modules/adb:adbd_test
  //frameworks/base/api:api_fingerprint
  //packages/modules/adb/apex:com.android.adbd
  //packages/modules/NeuralNetworks/apex:com.android.neuralnetworks
  //system/timezone/apex:com.android.tzdata
  //packages/modules/NeuralNetworks/runtime:libneuralnetworks
  //packages/modules/NeuralNetworks/runtime:libneuralnetworks_static
  //system/timezone/testing/data/test1/apex:test1_com.android.tzdata
  //system/timezone/testing/data/test3/apex:test3_com.android.tzdata
  //packages/modules/adb/apex:test_com.android.adbd
  //packages/modules/NeuralNetworks/apex/testing:test_com.android.neuralnetworks
)
