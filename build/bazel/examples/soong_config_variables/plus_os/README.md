# Soong config variable plus arch/os demonstration

## bazel build

b run //build/bazel/examples/soong_config_variables/plus_os:build.bazel.examples.soong_config_variables.plus_os

## Soong build

`m build.bazel.examples.soong_config_variables.plus_os`

Host location: `out/host/linux-x86/bin/build.bazel.examples.soong_config_variables.plus_os`

Device location: `$(ANDROID_PRODUCT_OUT)/system/bin/build.bazel.examples.soong_config_variables.plus_os`
