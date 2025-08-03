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

package android.companion.cts.common

import android.Manifest
import android.annotation.CallSuper
import android.annotation.UserIdInt
import android.app.Instrumentation
import android.app.UiAutomation
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.MacAddress
import android.os.ParcelUuid
import android.os.Process
import android.os.SystemClock.sleep
import android.os.SystemClock.uptimeMillis
import android.os.UserHandle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import java.io.IOException
import java.util.Locale
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
import org.junit.Before

/**
 * A base class for CompanionDeviceManager [Tests][org.junit.Test] to extend.
 */
abstract class TestBase {
    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val uiAutomation: UiAutomation = instrumentation.uiAutomation

    protected val context: Context = instrumentation.context
    protected val userId = context.userId
    protected val targetPackageName = instrumentation.targetContext.packageName
    protected val targetUserId = instrumentation.targetContext.userId

    protected val targetApp = AppHelper(instrumentation, userId, targetPackageName)

    protected val pm: PackageManager by lazy { context.packageManager!! }
    private val hasCompanionDeviceSetupFeature by lazy {
        pm.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
    }

    protected val cdm: CompanionDeviceManager by lazy {
        context.getSystemService(CompanionDeviceManager::class.java)!!
    }

    private val locationManager = context.getSystemService(LocationManager::class.java)!!

    // CDM discovery requires location is enabled, enable the location if it was disabled.
    private var locationWasEnabled: Boolean = false
    private var userHandle: UserHandle = Process.myUserHandle()

    @Before
    fun base_setUp() {
        assumeTrue(hasCompanionDeviceSetupFeature)

        // Remove all existing associations (for the user).
        assertEmpty(withShellPermissionIdentity {
            cdm.disassociateAll()
            cdm.allAssociations
        })

        // Make sure CompanionDeviceServices are not bound.
        assertValidCompanionDeviceServicesUnbind()
        // Enable location if it was disabled.
        enableLocation()
        setUp()
    }

    @After
    fun base_tearDown() {
        if (!hasCompanionDeviceSetupFeature) return

        tearDown()

        // Remove all existing associations (for the user).
        withShellPermissionIdentity { cdm.disassociateAll() }
        // Disable the location if it was disabled.
        disableLocation()
    }

    @CallSuper
    protected open fun setUp() {}

    @CallSuper
    protected open fun tearDown() {}

    protected fun <T> withShellPermissionIdentity(
        vararg permissions: String,
        block: () -> T
    ): T {
        if (permissions.isNotEmpty()) {
            uiAutomation.adoptShellPermissionIdentity(*permissions)
        } else {
            uiAutomation.adoptShellPermissionIdentity()
        }

        try {
            return block()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    protected fun createSelfManagedAssociation(
        displayName: String,
        onAssociationCreatedAction: ((AssociationInfo) -> Unit)? = null
    ): Int {
        val callback = RecordingCallback(onAssociationCreatedAction = onAssociationCreatedAction)
        val request: AssociationRequest = AssociationRequest.Builder()
                .setSelfManaged(true)
                .setDisplayName(displayName)
                .build()
        callback.assertInvokedByActions {
            withShellPermissionIdentity(Manifest.permission.REQUEST_COMPANION_SELF_MANAGED) {
                cdm.associate(request, SIMPLE_EXECUTOR, callback)
            }
        }

        val callbackInvocation = callback.invocations.first()
        assertIs<RecordingCallback.OnAssociationCreated>(callbackInvocation)
        return callbackInvocation.associationInfo.id
    }

    protected fun runShellCommand(cmd: String) = instrumentation.runShellCommand(cmd)

    private fun CompanionDeviceManager.disassociateAll() =
            allAssociations.forEach { disassociate(it.id) }

    protected fun setSystemPropertyDuration(duration: Duration, systemPropertyTag: String) =
        instrumentation.setSystemProp(
            systemPropertyTag,
            duration.inWholeMilliseconds.toString()
        )

    private fun enableLocation() {
        locationWasEnabled = locationManager.isLocationEnabledForUser(userHandle)
        if (!locationWasEnabled) {
            withShellPermissionIdentity {
                locationManager.setLocationEnabledForUser(true, userHandle)
            }
        }
    }

    private fun disableLocation() {
        if (!locationWasEnabled) {
            withShellPermissionIdentity {
                locationManager.setLocationEnabledForUser(false, userHandle)
            }
        }
    }

    fun simulateDeviceEvent(associationId: Int, event: Int) =
            runShellCommand("cmd companiondevice simulate-device-event $associationId $event")

    fun simulateDeviceUuidEvent(uuid: ParcelUuid, event: Int) =
            runShellCommand(
                    "cmd companiondevice simulate-device-uuid-event " +
                    "$uuid $targetPackageName $userId $event"
            )
}

const val TAG = "CtsCompanionDeviceManagerTestCases"

/** See [com.android.server.companion.CompanionDeviceServiceConnector.UNBIND_POST_DELAY_MS]. */
private val UNBIND_DELAY_DURATION = 5.seconds

fun <T> assumeThat(message: String, obj: T, assumption: (T) -> Boolean) {
    if (!assumption(obj)) throw AssumptionViolatedException(message)
}

fun assertApplicationBinds(cdm: CompanionDeviceManager) {
    assertTrue {
        waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
            cdm.isCompanionApplicationBound
        }
    }
}

fun assertApplicationUnbinds(cdm: CompanionDeviceManager) {
    assertTrue {
        waitFor(timeout = 1.seconds.plus(UNBIND_DELAY_DURATION), interval = 100.milliseconds) {
            !cdm.isCompanionApplicationBound
        }
    }
}

fun assertApplicationRemainsBound(cdm: CompanionDeviceManager) {
    assertFalse {
        waitFor(timeout = 3.seconds.plus(UNBIND_DELAY_DURATION), interval = 100.milliseconds) {
            !cdm.isCompanionApplicationBound
        }
    }
}

fun <T> assertEmpty(list: Collection<T>) = assertTrue("Collection is not empty") { list.isEmpty() }

fun assertAssociations(
    actual: List<AssociationInfo>,
    expected: Set<Pair<String, MacAddress?>>
) = assertEquals(actual = actual.map {
    it.packageName to it.deviceMacAddress }.toSet(), expected = expected)

fun assertSelfManagedAssociations(
    actual: List<AssociationInfo>,
    expected: Set<Pair<String, Int>>
) = assertEquals(actual = actual.map { it.packageName to it.id }.toSet(), expected = expected)

/**
 * Assert that CDM binds valid CompanionDeviceServices, both primary and secondary.
 * Use when services are expected to switch its state to "bound".
 */
fun assertValidCompanionDeviceServicesBind() =
        assertTrue("Both valid CompanionDeviceServices - Primary and Secondary - should bind") {
            waitFor(timeout = 1.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound && SecondaryCompanionService.isBound
            }
        }

/**
 * Assert both primary and secondary CompanionDeviceServices stay bound.
 * Use when services are expected to be in "bound" state already.
 */
fun assertValidCompanionDeviceServicesRemainBound() =
        assertFalse("Both valid CompanionDeviceServices should stay bound") {
            waitFor(timeout = 3.seconds.plus(UNBIND_DELAY_DURATION), interval = 100.milliseconds) {
                !PrimaryCompanionService.isBound || !SecondaryCompanionService.isBound
            }
        }

/**
 * Assert that CDM unbinds valid CompanionDeviceServices, both primary and secondary.
 * Use when services are expected to switch its state to "unbound".
 */
fun assertValidCompanionDeviceServicesUnbind() =
        assertTrue("CompanionDeviceServices should not bind") {
            waitFor(timeout = 1.seconds.plus(UNBIND_DELAY_DURATION), interval = 100.milliseconds) {
                !PrimaryCompanionService.isBound && !SecondaryCompanionService.isBound
            }
        }

/**
 * Assert that neither primary nor secondary CompanionDeviceService is bound.
 * Use when services are expected to be in "unbound" state already.
 */
fun assertValidCompanionDeviceServicesRemainUnbound() =
        assertFalse("CompanionDeviceServices should not be bound") {
            waitFor(timeout = 3.seconds, interval = 100.milliseconds) {
                PrimaryCompanionService.isBound || SecondaryCompanionService.isBound
            }
        }

/**
 * Assert that CDM did not bind invalid CompanionDeviceServices
 * (i.e. missing permission or intent-filter).
 */
fun assertInvalidCompanionDeviceServicesNotBound() =
        assertFalse(
                "CompanionDeviceServices that do not require " +
                "BIND_COMPANION_DEVICE_SERVICE permission or do not declare an intent-filter for " +
                "\"android.companion.CompanionDeviceService\" action should not be bound"
        ) {
            MissingPermissionCompanionService.isBound ||
                    MissingIntentFilterActionCompanionService.isBound
    }

/**
 * Assert that device (dis)appearance detection callback is only triggered for the primary
 * CompanionDeviceService and not on any of the non-primary or invalid CompanionDeviceServices.
 */
fun assertOnlyPrimaryCompanionDeviceServiceNotified(associationId: Int, appeared: Boolean) {
    val snapshotSecondary = HashSet(SecondaryCompanionService.connectedDevices)
    val snapshotUnauthorized = HashSet(MissingPermissionCompanionService.connectedDevices)
    val snapshotInvalid = HashSet(MissingIntentFilterActionCompanionService.connectedDevices)

    // Check that the primary CompanionDeviceService received onDevice(Dis)Appeared() callback
    if (appeared) {
        PrimaryCompanionService.waitAssociationToAppear(associationId)
        assertContains(PrimaryCompanionService.associationIdsForConnectedDevices, associationId)
    } else {
        PrimaryCompanionService.waitAssociationToDisappear(associationId)
        assertFalse(
                PrimaryCompanionService.associationIdsForConnectedDevices.contains(associationId)
        )
    }

    // ... while neither the non-primary nor incorrectly defined CompanionDeviceServices -
    // have NOT. (Give it 1 more second.)
    sleepFor(1.seconds)
    assertContentEquals(snapshotSecondary, SecondaryCompanionService.connectedDevices)
    assertContentEquals(snapshotUnauthorized, MissingPermissionCompanionService.connectedDevices)
    assertContentEquals(snapshotInvalid, MissingIntentFilterActionCompanionService.connectedDevices)
}

/**
 * @return whether the condition was met before time ran out.
 */
fun waitFor(
    timeout: Duration = 10.seconds,
    interval: Duration = 1.seconds,
    condition: () -> Boolean
): Boolean {
    val startTime = uptimeMillis()
    while (!condition()) {
        if (uptimeMillis() - startTime > timeout.inWholeMilliseconds) return false
        sleep(interval.inWholeMilliseconds)
    }
    return true
}

fun <R> waitForResult(
    timeout: Duration = 10.seconds,
    interval: Duration = 1.seconds,
    block: () -> R
): R? {
    val startTime = uptimeMillis()
    while (true) {
        val result: R = block()
        if (result != null) return result
        sleep(interval.inWholeMilliseconds)
        if (uptimeMillis() - startTime > timeout.inWholeMilliseconds) return null
    }
}

fun Instrumentation.runShellCommand(cmd: String): String {
    Log.i(TAG, "Running shell command: '$cmd'")
    try {
        val out = SystemUtil.runShellCommand(this, cmd)
        Log.i(TAG, "Out:\n$out")
        return out
    } catch (e: IOException) {
        Log.e(TAG, "Error running shell command: $cmd")
        throw e
    }
}

fun Instrumentation.setSystemProp(name: String, value: String) =
        runShellCommand("setprop $name $value")

fun MacAddress.toUpperCaseString() = toString().uppercase(Locale.ROOT)

fun sleepFor(duration: Duration) = sleep(duration.inWholeMilliseconds)

fun killProcess(name: String) {
    val pids = SystemUtil.runShellCommand("pgrep $name").trim().split("\\s+".toRegex())
    for (pid: String in pids) {
        // Make sure that it is the intended process before killing it.
        val process = SystemUtil.runShellCommand("ps $pid")
        if (process.contains("android.companion.cts.multiprocess")) {
            Process.killProcess(Integer.valueOf(pid))
        }
    }
}

fun getAssociationForPackage(
        @UserIdInt userId: Int,
        packageName: String,
        macAddress: MacAddress,
        cdm: CompanionDeviceManager
): AssociationInfo = cdm.allAssociations.find {
    it.belongsToPackage(userId, packageName) && it.deviceMacAddress == macAddress
} ?: fail("Association for u$userId/$packageName linked to address $macAddress does not exist")
