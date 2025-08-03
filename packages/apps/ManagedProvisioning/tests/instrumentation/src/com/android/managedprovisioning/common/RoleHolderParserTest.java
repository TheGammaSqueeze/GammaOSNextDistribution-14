/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.managedprovisioning.common;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RoleHolderParserTest {
    private static final String ROLE_HOLDER_PACKAGE = "com.example.package";
    private static final String CONFIG_PACKAGE_NAME_ONLY = ROLE_HOLDER_PACKAGE;
    private static final String CONFIG_EMPTY = "";
    private static final String CONFIG_PACKAGE_NAME_AND_CERT = "com.example.package:mycert";
    private static final String CONFIG_NULL = null;

    @Test
    public void getRoleHolderPackage_packageNameOnly_works() {
        assertThat(RoleHolderParser.getRoleHolderPackage(CONFIG_PACKAGE_NAME_ONLY))
                .isEqualTo(ROLE_HOLDER_PACKAGE);
    }

    @Test
    public void getRoleHolderPackage_packageNameAndCert_works() {
        assertThat(RoleHolderParser.getRoleHolderPackage(CONFIG_PACKAGE_NAME_AND_CERT))
                .isEqualTo(ROLE_HOLDER_PACKAGE);
    }

    @Test
    public void getRoleHolderPackage_emptyConfig_returnsNull() {
        assertThat(RoleHolderParser.getRoleHolderPackage(CONFIG_EMPTY)).isNull();
    }

    @Test
    public void getRoleHolderPackage_nullConfig_returnsNull() {
        assertThat(RoleHolderParser.getRoleHolderPackage(CONFIG_NULL)).isNull();
    }
}
