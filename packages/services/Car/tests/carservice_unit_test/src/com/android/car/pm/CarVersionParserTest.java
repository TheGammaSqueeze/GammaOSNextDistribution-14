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

package com.android.car.pm;

import static android.car.content.pm.CarPackageManager.MANIFEST_METADATA_TARGET_CAR_VERSION;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.CarVersion;
import android.car.test.AbstractExpectableTestCase;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import org.junit.Test;

public final class CarVersionParserTest extends AbstractExpectableTestCase {

    @Test
    public void testGetTargetCarApiVersion_noMetadata() {
        ApplicationInfo info = new ApplicationInfo();
        info.targetSdkVersion  = 42;

        CarVersion apiVersion = CarVersionParser.getTargetCarVersion(info);

        assertWithMessage("parse(%s)", info).that(apiVersion).isNotNull();
        expectWithMessage("parse(%s).major", info).that(apiVersion.getMajorVersion()).isEqualTo(42);
        expectWithMessage("parse(%s).minor", info).that(apiVersion.getMinorVersion()).isEqualTo(0);
    }

    // No need to test all scenarios, as they're tested by CarApiVersionParserParseMethodTest
    @Test
    public void testGetTargetCarApiVersion_simpleCase() {
        ApplicationInfo info = new ApplicationInfo();
        info.targetSdkVersion = 666; // Set to make sure it's not used
        info.metaData = new Bundle();
        info.metaData.putString(MANIFEST_METADATA_TARGET_CAR_VERSION, "108:42");

        CarVersion apiVersion = CarVersionParser.getTargetCarVersion(info);

        assertWithMessage("getTargetCarApiVersion(%s)", info).that(apiVersion).isNotNull();
        expectWithMessage("getTargetCarApiVersion(%s).major", info)
                .that(apiVersion.getMajorVersion()).isEqualTo(108);
        expectWithMessage("getTargetCarApiVersion(%s).minor", info)
                .that(apiVersion.getMinorVersion()).isEqualTo(42);
    }
}
