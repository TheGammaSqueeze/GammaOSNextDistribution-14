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

import com.android.cts.packagemanager.verify.domain.java.DomainUtils
import com.android.tradefed.device.ITestDevice
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

internal object DomainVerificationHostUtils {

    fun ITestDevice.installApkResource(
        tempFolder: TemporaryFolder,
        apkName: DomainUtils.ApkName,
        reinstall: Boolean = false,
        vararg extraArgs: String
    ) = installPackage(
        copyResourceToHostFile(apkName.value, tempFolder.newFile()),
        reinstall,
        *extraArgs
    )

    fun copyResourceToHostFile(javaResourceName: String, file: File): File {
        javaClass.classLoader!!.getResource(javaResourceName)!!.openStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }
}
