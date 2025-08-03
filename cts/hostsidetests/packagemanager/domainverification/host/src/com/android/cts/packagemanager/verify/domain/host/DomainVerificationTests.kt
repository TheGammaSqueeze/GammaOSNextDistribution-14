/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.packagemanager.verify.domain.host

import com.android.cts.packagemanager.verify.domain.host.DomainVerificationHostUtils.installApkResource
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.CALLING_PKG_NAME
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_APK_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_APK_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_BASE
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class DomainVerificationTests : BaseHostJUnit4Test() {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Before
    @After
    fun uninstall() {
        device.uninstallPackage(DECLARING_PKG_NAME_1)
        device.uninstallPackage(DECLARING_PKG_NAME_2)
    }

    @Test
    fun declaredDomainSet() {
        device.installApkResource(tempFolder, DECLARING_PKG_APK_1, extraArgs = arrayOf("-t"))
        runDeviceTests(DeviceTestRunOptions(DECLARING_PKG_NAME_1).apply {
            // The base name is used as the code package does not change with
            // the manifest rename that splits the packages into 1 and 2 variants.
            testClassName = "$DECLARING_PKG_NAME_BASE.DomainVerificationDeclaringAppTests"
            testMethodName = "verifyOwnDomains"
        })
    }

    @Test
    fun verifyDomains() {
        device.installApkResource(tempFolder, DECLARING_PKG_APK_1, extraArgs = arrayOf("-t"))
        device.installApkResource(tempFolder, DECLARING_PKG_APK_2, extraArgs = arrayOf("-t"))
        runDeviceTests(DeviceTestRunOptions(CALLING_PKG_NAME).apply {
            testClassName = "$CALLING_PKG_NAME.DomainVerificationCallingAppTests"
        })
    }
}
