# Running dEQP with Gfxstream

## dEQP-(EGL|GLES2|GLES3)

TODO

## dEQP-VK

### Linux

1. Build Gfxstream

   ```
   cd <YOUR aosp-main DIRECTORY>
   source build/envsetup.sh
   lunch aosp_cf_x86_64_phone-trunk_staging-userdebug
   m libgfxstream_guest_vulkan_with_host

   # Edit hardware/google/gfxstream/guest/vulkan/gfxstream_icd.json 's
   # "library_path" value to point to
   # "<YOUR aosp-main DIRECTORY>/out/host/linux-86/lib64/libgfxstream_guest_vulkan_with_host.so"
   ```

1. Build dEQP

   ```
   mkdir github-deqp
   cd github-deqp
   git clone https://github.com/KhronosGroup/VK-GL-CTS .
   python3 external/fetch_sources.py
   mkdir build
   cd build
   cmake -DCMAKE_BUILD_TYPE=Debug ..
   make -j
   ```

1. Build Nested Vulkan Loader

   ```
   ./scripts/build-nested-vulkan-loader.sh

   # Run the "export" command printed in the output of the above script.
   ```

1. Run dEQP with Gfxstream ICD

   ```
   VK_LOADER_DEBUG=all \
   VK_ICD_FILENAMES=<YOUR aosp-main DIRECTORY>/hardware/google/gfxstream/guest/vulkan/gfxstream_icd.json \
   GFXSTREAM_VK_LOADER_DEBUG=all \
   GFXSTREAM_VK_ICD_FILENAMES=/etc/vulkan/icd.d/nvidia_icd.json \
   <YOUR github-deqp DIRECTORY>/build/external/vulkancts/modules/vulkan/deqp-vk \
       --deqp-case=dEQP-VK.api.smoke*
   ```