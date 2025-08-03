/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.layoutlib.bridge.android;

import org.junit.Before;

import java.io.File;
import java.net.URL;
import java.util.Locale;

import com.android.layoutlib.bridge.intensive.BridgeClient;

public class RenderTestBase extends BridgeClient {
    private static final String RESOURCE_DIR_PROPERTY = "test_res.dir";
    public static final String S_PACKAGE_NAME = "com.android.layoutlib.test.myapplication";

    public String getAppTestDir() {
        return "testApp/MyApplication";
    }

    public String getAppTestRes() {
        return getBaseResourceDir() + "/" + getAppTestDir() + "/src/main/res";
    }

    public String getAppResources() {
        return getAppTestDir() + "/src/main/res";
    }

    public String getAppTestAsset() {
        return getBaseResourceDir() + "/" + getAppTestDir() + "/src/main/assets/";
    }

    public String getAppClassesLocation() {
        return getAppTestDir()
                + "/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes/";
    }

    public String getAppGoldenDir() {
        String goldenImagePath = getAppTestDir();
        boolean isMac = System.getProperty("os.name").toLowerCase(Locale.US).contains("mac");
        if (isMac) {
            goldenImagePath += "/golden-mac/";
        } else {
            goldenImagePath += "/golden/";
        }
        return goldenImagePath;
    }

    private static String getBaseResourceDir() {
        String resourceDir = System.getProperty(RESOURCE_DIR_PROPERTY);
        if (resourceDir != null && !resourceDir.isEmpty() && new File(resourceDir).isDirectory()) {
            return resourceDir;
        }
        // resource directory not explicitly set. Fallback to the class's source location.
        try {
            URL location = RenderTestBase.class.getProtectionDomain().getCodeSource().getLocation();
            return new File(location.getPath()).exists() ? location.getPath() : null;
        } catch (NullPointerException e) {
            // Prevent a lot of null checks by just catching the exception.
            return null;
        }
    }

    @Before
    public void initPackageName() {
        setPackageName(S_PACKAGE_NAME);
    }

}
