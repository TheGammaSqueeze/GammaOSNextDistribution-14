How to build the prebuilt libs
=================================
1. m libtest_multi_arch_native_libs in the different arch (E.g. arm, x86)

2. Find the libs in out/target/product/{$target}/data/
E.g. out/target/product/redfin/data/nativetest/ and out/target/product/redfin/data/nativetest64/

3. Put them into the specific folders by the arch (E.g. arm, arm64, x86, x86_64)
