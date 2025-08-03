/*
 * Copyright (C) 2023 The Android Open Source Project
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

package libcore.tools.analyzer.openjdk;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Util functions for the host and runtime environment in AOSP.
 */
public class AndroidHostEnvUtil {
    public static Path getAndroidBuildTop() {
        return pathFromEnvOrThrow("ANDROID_BUILD_TOP");
    }

    private static Path pathFromEnvOrThrow(String name) {
        String envValue = getEnvOrThrow(name);
        Path result = Paths.get(envValue);
        if (!result.toFile().exists()) {
            throw new IllegalStateException("For " + name + ", path doesn't exist: " + result);
        }
        return result;
    }

    private static String getEnvOrThrow(String name) {
        String result = System.getenv(name);
        if (result == null) {
            throw new IllegalStateException("Environment variable undefined: " + name);
        }
        return result;
    }

    static Path parseInputClasspath(String classpath) {
        switch (classpath) {
            case "oj":
                return getAndroidBuildTop().resolve(
                        "out/soong/.intermediates/libcore/core-oj/android_common_apex31/"
                                + "javac/core-oj.jar");
            case "8":
                return getAndroidBuildTop().resolve(
                        "prebuilts/jdk/jdk8/linux-x86/jre/lib/rt.jar");
            case "9":
            case "11":
            case "17":
            case "21":
                return getAndroidBuildTop().resolve(
                        "prebuilts/jdk/jdk" + classpath + "/linux-x86/jmods/java.base.jmod");
            default:
                return Path.of(classpath);
        }
    }
}
