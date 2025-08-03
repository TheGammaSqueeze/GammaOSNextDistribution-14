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

package android.companion.cts.core

import android.annotation.UserIdInt
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.cts.common.AppHelper
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MAC_ADDRESS_B
import android.companion.cts.common.MAC_ADDRESS_C
import android.companion.cts.common.assertAssociations
import android.companion.cts.common.waitFor
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests if associations that belong to a package are removed when the package is uninstalled or its
 * data is cleared.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerCoreTestCases:AssociationsCleanUpTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class AssociationsCleanUpTest : CoreTestBase() {

    @Test
    fun test_associationsRemoved_onPackageDataCleared() {
        testApp.associate(MAC_ADDRESS_A)
        testApp.associate(MAC_ADDRESS_B)
        targetApp.associate(MAC_ADDRESS_C)

        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        testApp.packageName to MAC_ADDRESS_A,
                        testApp.packageName to MAC_ADDRESS_B,
                        targetApp.packageName to MAC_ADDRESS_C
                ))

        /** Clear test app's data. */
        testApp.clearData()
        assertAssociationsRemovedFor(testApp)

        /** Only Test App's associations should have been removed. Others - should remain. */
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        targetApp.packageName to MAC_ADDRESS_C
                ))
    }

    @Test
    fun test_associationsRemoved_onPackageRemoved() {
        testApp.associate(MAC_ADDRESS_A)
        testApp.associate(MAC_ADDRESS_B)
        targetApp.associate(MAC_ADDRESS_C)

        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        testApp.packageName to MAC_ADDRESS_A,
                        testApp.packageName to MAC_ADDRESS_B,
                        targetApp.packageName to MAC_ADDRESS_C
                ))

        /** Uninstall test app. */
        testApp.uninstall()
        assertAssociationsRemovedFor(testApp)

        /** Only Test App's associations should have been removed. Others - should remain. */
        assertAssociations(
                actual = withShellPermissionIdentity { cdm.allAssociations },
                expected = setOf(
                        targetApp.packageName to MAC_ADDRESS_C
                ))
    }

    private fun assertAssociationsRemovedFor(app: AppHelper) = waitFor {
        withShellPermissionIdentity {
            cdm.getAssociationForPackage(app.userId, app.packageName).isEmpty()
        }
    }.let { removed ->
        if (!removed)
            throw AssertionError("Associations for ${app.packageName} were not removed.")
    }
}

private fun CompanionDeviceManager.getAssociationForPackage(
    @UserIdInt userId: Int,
    packageName: String
): List<AssociationInfo> = allAssociations.filter { it.belongsToPackage(userId, packageName) }