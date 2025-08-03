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

import dalvik.system.VMRuntime;

public class Main {
    public static void main(String[] args) {
        VMRuntime.getRuntime().setTargetSdkVersion(0);
        try {
            Class classValueClass = Class.forName("java.lang.ClassValue");
        } catch (ClassNotFoundException ignored) {
            throw new Error(
                    "java.lang.ClassValue should be available when targetSdkLevel is not set");
        }

        VMRuntime.getRuntime().setTargetSdkVersion(34);

        try {
            Class classValueClass = Class.forName("java.lang.ClassValue");
        } catch (ClassNotFoundException ignored) {
            throw new Error("java.lang.ClassValue should be available on targetSdkLevel 34");
        }

        VMRuntime.getRuntime().setTargetSdkVersion(33);
        try {
            Class classValueClass = Class.forName("java.lang.ClassValue");
            throw new Error("Was able to find " + classValueClass + " on targetSdkLevel 33");
        } catch (ClassNotFoundException expected) {
            if (!expected.getMessage().contains("java.lang.ClassValue")) {
                throw new Error("Thrown exception should contain class name, but was: " + expected);
            }
        }
    }
}
