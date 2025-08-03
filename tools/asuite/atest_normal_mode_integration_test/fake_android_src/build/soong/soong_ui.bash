#!/bin/bash -eu

# Generate module-info.json
mkdir -p $ANDROID_PRODUCT_OUT/
echo '{
  "hello_world_test": { "class": ["NATIVE_TESTS"],  "path": ["platform_testing/tests/example/native"],  "tags": ["optional"],  "installed": ["out/host/linux-x86/nativetest64/hello_world_test/hello_world_test",  "out/target/product/vsoc_x86_64/data/nativetest64/hello_world_test/hello_world_test"],  "compatibility_suites": ["general-tests"],  "auto_test_config": [true],  "module_name": "hello_world_test",  "test_config": ["out/soong/.intermediates/platform_testing/tests/example/native/hello_world_test/android_x86_64_silvermont/hello_world_test.config"],  "dependencies": ["libc",  "libc++",  "libc++demangle",  "libclang_rt.builtins",  "libdl",  "libgtest",  "libgtest_main",  "libm"],  "shared_libs": ["libc",  "libc++",  "libdl",  "libm"],  "system_shared_libs": ["libc",  "libdl",  "libm"],  "srcs": [],  "srcjars": [],  "classes_jar": [],  "test_mainline_modules": [],  "is_unit_test": "",  "data": [],  "runtime_dependencies": [],  "data_dependencies": [],  "supported_variants": ["DEVICE",  "HOST"]},
  "hello_world_test_32": { "class": ["NATIVE_TESTS"],  "path": ["platform_testing/tests/example/native"],  "tags": ["optional"],  "installed": ["out/host/linux-x86/nativetest/hello_world_test/hello_world_test",  "out/target/product/vsoc_x86_64/data/nativetest/hello_world_test/hello_world_test"],  "compatibility_suites": ["general-tests"],  "auto_test_config": [true],  "module_name": "hello_world_test",  "test_config": ["out/soong/.intermediates/platform_testing/tests/example/native/hello_world_test/android_x86_silvermont/hello_world_test.config"],  "dependencies": [],  "shared_libs": ["libc",  "libc++",  "libdl",  "libm"],  "system_shared_libs": ["libc",  "libdl",  "libm"],  "srcs": [],  "srcjars": [],  "classes_jar": [],  "test_mainline_modules": [],  "is_unit_test": "",  "data": [],  "runtime_dependencies": [],  "data_dependencies": [],  "supported_variants": ["DEVICE",  "HOST"]}
}' > $ANDROID_PRODUCT_OUT/module-info.json

# Generate deps.json
mkdir -p $OUT/soong/
touch $OUT/soong/module_bp_cc_deps.json
touch $OUT/soong/module_bp_java_deps.json
