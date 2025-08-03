/*
 * Copyright (C) 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.linkerconfig.gts.utils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.INativeDevice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LibraryListLoader {
    public static List<String> getLibrariesFromFile(INativeDevice targetDevice,
            String path, boolean expectFileExists) {
        List<String> libraries = new ArrayList<>();

        File target = null;

        try {
            target = targetDevice.pullFile(path);
        } catch (DeviceNotAvailableException e) {
            fail("There is no available device : " + e.getMessage());
        }

        assertTrue("Failed to get library list file from " + path,
                target.exists() || !expectFileExists);

        if (target.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(target))) {
                String library;
                while ((library = reader.readLine()) != null) {
                    library = library.trim();
                    if (!library.isEmpty()) {
                        libraries.add(library);
                    }
                }
            } catch (Exception e) {
                fail("Failed to read file " + path + " with error : " + e.getMessage());
            }
        }

        return libraries;
    }

    public static final List<String> STUB_LIBRARIES = Arrays.asList("libEGL.so",
            "libGLESv1_CM.so",
            "libGLESv2.so",
            "libGLESv3.so",
            "libRS.so",
            "libaaudio.so",
            "libadbd_auth.so",
            "libadbd_fs.so",
            "libandroid.so",
            "libandroid_net.so",
            "libbinder_ndk.so",
            "libc.so",
            "libcgrouprc.so",
            "libclang_rt.asan-arm-android.so",
            "libclang_rt.asan-i686-android.so",
            "libclang_rt.asan-x86_64-android.so",
            "libdl.so",
            "libdl_android.so",
            "libft2.so",
            "libincident.so",
            "liblog.so",
            "libm.so",
            "libmediametrics.so",
            "libmediandk.so",
            "libnativewindow.so",
            "libneuralnetworks_packageinfo.so",
            "libsync.so",
            "libvndksupport.so",
            "libvulkan.so",
            "libselinux.so");
}
