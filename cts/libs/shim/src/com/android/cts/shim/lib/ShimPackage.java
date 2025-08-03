/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.shim.lib;

/**
 * The constants about shim package.
 */
public class ShimPackage {

    /**
     * Package name of the privileged CTS shim apk
     */
    public static final String PRIVILEGED_SHIM_PACKAGE_NAME = "com.android.cts.priv.ctsshim";

    /**
     * Package name of the system CTS shim apk
     */
    public static final String SHIM_PACKAGE_NAME = "com.android.cts.ctsshim";

    /**
     * Package name of the system CTS shim apex
     */
    public static final String SHIM_APEX_PACKAGE_NAME = "com.android.apex.cts.shim";

    /**
     * Package name of the non pre-installed CTS shim apex
     */
    public static final String NOT_PRE_INSTALL_APEX_PACKAGE_NAME =
            "com.android.apex.cts.shim_not_pre_installed";

    /**
     * Package name of the CTS shim apex that has the different package name
     */
    public static final String DIFFERENT_APEX_PACKAGE_NAME = "com.android.apex.cts.shim.different";
}
