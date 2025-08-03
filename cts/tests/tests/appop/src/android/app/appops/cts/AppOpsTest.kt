package android.app.appops.cts

/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.Manifest.permission
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_DEFAULT
import android.app.AppOpsManager.MODE_ERRORED
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS
import android.app.AppOpsManager.OPSTR_FINE_LOCATION
import android.app.AppOpsManager.OPSTR_PHONE_CALL_CAMERA
import android.app.AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE
import android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE
import android.app.AppOpsManager.OPSTR_READ_CALENDAR
import android.app.AppOpsManager.OPSTR_RECORD_AUDIO
import android.app.AppOpsManager.OPSTR_WIFI_SCAN
import android.app.AppOpsManager.OPSTR_WRITE_CALENDAR
import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OnOpChangedListener
import android.app.AppOpsManager.OnOpNotedListener
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.Log
import androidx.test.InstrumentationRegistry
import androidx.test.filters.FlakyTest
import androidx.test.runner.AndroidJUnit4
import com.google.common.base.Objects
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOpsTest {
    private lateinit var mAppOps: AppOpsManager
    private lateinit var mContext: Context
    private lateinit var mOpPackageName: String
    private val mMyUid = Process.myUid()

    @get:Rule val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    companion object {
        // These permissions and opStrs must map to the same op codes.
        val permissionToOpStr = HashMap<String, String>()
        private val TIMEOUT_MS = 10000L
        private val SHELL_PACKAGE_NAME = "com.android.shell"
        private val TEST_ATTRIBUTION = "testAttribution"
        private val LOG_TAG = AppOpsTest::class.java.simpleName

        init {
            permissionToOpStr[permission.ACCESS_COARSE_LOCATION] =
                    AppOpsManager.OPSTR_COARSE_LOCATION
            permissionToOpStr[permission.ACCESS_FINE_LOCATION] =
                    AppOpsManager.OPSTR_FINE_LOCATION
            permissionToOpStr[permission.READ_CONTACTS] =
                    AppOpsManager.OPSTR_READ_CONTACTS
            permissionToOpStr[permission.WRITE_CONTACTS] =
                    AppOpsManager.OPSTR_WRITE_CONTACTS
            permissionToOpStr[permission.READ_CALL_LOG] =
                    AppOpsManager.OPSTR_READ_CALL_LOG
            permissionToOpStr[permission.WRITE_CALL_LOG] =
                    AppOpsManager.OPSTR_WRITE_CALL_LOG
            permissionToOpStr[permission.READ_CALENDAR] =
                    AppOpsManager.OPSTR_READ_CALENDAR
            permissionToOpStr[permission.WRITE_CALENDAR] =
                    AppOpsManager.OPSTR_WRITE_CALENDAR
            permissionToOpStr[permission.CALL_PHONE] =
                    AppOpsManager.OPSTR_CALL_PHONE
            permissionToOpStr[permission.READ_SMS] =
                    AppOpsManager.OPSTR_READ_SMS
            permissionToOpStr[permission.RECEIVE_SMS] =
                    AppOpsManager.OPSTR_RECEIVE_SMS
            permissionToOpStr[permission.RECEIVE_MMS] =
                    AppOpsManager.OPSTR_RECEIVE_MMS
            permissionToOpStr[permission.RECEIVE_WAP_PUSH] =
                    AppOpsManager.OPSTR_RECEIVE_WAP_PUSH
            permissionToOpStr[permission.SEND_SMS] =
                    AppOpsManager.OPSTR_SEND_SMS
            permissionToOpStr[permission.READ_SMS] =
                    AppOpsManager.OPSTR_READ_SMS
            permissionToOpStr[permission.WRITE_SETTINGS] =
                    AppOpsManager.OPSTR_WRITE_SETTINGS
            permissionToOpStr[permission.SYSTEM_ALERT_WINDOW] =
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW
            permissionToOpStr[permission.ACCESS_NOTIFICATIONS] =
                    AppOpsManager.OPSTR_ACCESS_NOTIFICATIONS
            permissionToOpStr[permission.CAMERA] =
                    AppOpsManager.OPSTR_CAMERA
            permissionToOpStr[permission.RECORD_AUDIO] =
                    AppOpsManager.OPSTR_RECORD_AUDIO
            permissionToOpStr[permission.READ_PHONE_STATE] =
                    AppOpsManager.OPSTR_READ_PHONE_STATE
            permissionToOpStr[permission.ADD_VOICEMAIL] =
                    AppOpsManager.OPSTR_ADD_VOICEMAIL
            permissionToOpStr[permission.USE_SIP] =
                    AppOpsManager.OPSTR_USE_SIP
            permissionToOpStr[permission.PROCESS_OUTGOING_CALLS] =
                    AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS
            permissionToOpStr[permission.BODY_SENSORS] =
                    AppOpsManager.OPSTR_BODY_SENSORS
            permissionToOpStr[permission.READ_CELL_BROADCASTS] =
                    AppOpsManager.OPSTR_READ_CELL_BROADCASTS
            permissionToOpStr[permission.READ_EXTERNAL_STORAGE] =
                    AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE
            permissionToOpStr[permission.WRITE_EXTERNAL_STORAGE] =
                    AppOpsManager.OPSTR_WRITE_EXTERNAL_STORAGE
            permissionToOpStr[permission.INTERACT_ACROSS_PROFILES] =
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES
        }

        val USER_SHELL_UID = UserHandle.getUid(Process.myUserHandle().identifier,
                UserHandle.getAppId(Process.SHELL_UID))
    }

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getContext()
        mAppOps = mContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        mOpPackageName = mContext.opPackageName
        assertNotNull(mAppOps)
        // Reset app ops state for this test package to the system default.
        reset(mOpPackageName)
    }

    @Test
    fun testNoteOpAndCheckOp() {
        setOpMode(mOpPackageName, OPSTR_WRITE_CALENDAR, MODE_ALLOWED)
        assertEquals(MODE_ALLOWED, mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_ALLOWED, mAppOps.noteOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_ALLOWED, mAppOps.unsafeCheckOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_ALLOWED, mAppOps.unsafeCheckOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))

        setOpMode(mOpPackageName, OPSTR_WRITE_CALENDAR, MODE_IGNORED)
        assertEquals(MODE_IGNORED, mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_IGNORED, mAppOps.noteOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_IGNORED, mAppOps.unsafeCheckOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_IGNORED, mAppOps.unsafeCheckOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))

        setOpMode(mOpPackageName, OPSTR_WRITE_CALENDAR, MODE_DEFAULT)
        assertEquals(MODE_DEFAULT, mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_DEFAULT, mAppOps.noteOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_DEFAULT, mAppOps.unsafeCheckOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_DEFAULT, mAppOps.unsafeCheckOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))

        setOpMode(mOpPackageName, OPSTR_WRITE_CALENDAR, MODE_ERRORED)
        assertEquals(MODE_ERRORED, mAppOps.noteOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_ERRORED, mAppOps.unsafeCheckOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        try {
            mAppOps.noteOp(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName)
            fail("SecurityException expected")
        } catch (expected: SecurityException) {
        }
        try {
            mAppOps.unsafeCheckOp(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName)
            fail("SecurityException expected")
        } catch (expected: SecurityException) {
        }
    }

    @Test
    fun testStartOpAndFinishOp() {
        setOpMode(mOpPackageName, OPSTR_WRITE_CALENDAR, MODE_ALLOWED)
        assertEquals(MODE_ALLOWED, mAppOps.startOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        mAppOps.finishOp(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName)
        assertEquals(MODE_ALLOWED, mAppOps.startOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        mAppOps.finishOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName)

        setOpMode(mOpPackageName, OPSTR_WRITE_CALENDAR, MODE_IGNORED)
        assertEquals(MODE_IGNORED, mAppOps.startOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_IGNORED, mAppOps.startOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))

        setOpMode(mOpPackageName, OPSTR_WRITE_CALENDAR, MODE_DEFAULT)
        assertEquals(MODE_DEFAULT, mAppOps.startOp(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        assertEquals(MODE_DEFAULT, mAppOps.startOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))

        setOpMode(mOpPackageName, OPSTR_WRITE_CALENDAR, MODE_ERRORED)
        assertEquals(MODE_ERRORED, mAppOps.startOpNoThrow(OPSTR_WRITE_CALENDAR,
                Process.myUid(), mOpPackageName))
        try {
            mAppOps.startOp(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName)
            fail("SecurityException expected")
        } catch (expected: SecurityException) {
        }
    }

    @Test
    @AppModeFull(reason = "Instant app cannot query for the shell package")
    fun overlappingActiveAttributionOps() {
        val activeChangedQueue = LinkedBlockingQueue<Boolean>()
        runWithShellPermissionIdentity {
            val activeWatcher =
                object: AppOpsManager.OnOpActiveChangedListener {
                    override fun onOpActiveChanged(op: String,
                        uid: Int,
                        packageName: String,
                        active: Boolean) {
                        if (packageName == SHELL_PACKAGE_NAME) {
                            activeChangedQueue.put(active)
                        }
                    }
                }

            mAppOps.startWatchingActive(arrayOf(OPSTR_WRITE_CALENDAR), { it.run() },
                activeWatcher)
            try {
                mAppOps.startOp(OPSTR_WRITE_CALENDAR, mMyUid, mOpPackageName, "firstAttribution",
                        null)

                assertTrue(mAppOps.isOpActive(OPSTR_WRITE_CALENDAR, USER_SHELL_UID,
                        SHELL_PACKAGE_NAME))
                var activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertTrue(activeState!!)

                mAppOps.startOp(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName,
                    "secondAttribution", null)

                assertTrue(mAppOps.isOpActive(OPSTR_WRITE_CALENDAR, USER_SHELL_UID,
                        SHELL_PACKAGE_NAME))
                var exception = try {
                    assertNotNull("Unexpected onOpChanged callback received",
                        activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    null
                } catch (e: AssertionError) {
                    e
                }
                assertNotNull(exception)

                mAppOps.finishOp(OPSTR_WRITE_CALENDAR, USER_SHELL_UID, SHELL_PACKAGE_NAME,
                    "firstAttribution")

                assertTrue(mAppOps.isOpActive(OPSTR_WRITE_CALENDAR, USER_SHELL_UID,
                        SHELL_PACKAGE_NAME))
                exception = try {
                    assertNotNull("Unexpected onOpChanged callback received",
                        activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    null
                } catch (e: AssertionError) {
                    e
                }
                assertNotNull(exception)

                mAppOps.finishOp(OPSTR_WRITE_CALENDAR, USER_SHELL_UID, SHELL_PACKAGE_NAME,
                    "secondAttribution")

                assertFalse(mAppOps.isOpActive(OPSTR_WRITE_CALENDAR, USER_SHELL_UID,
                        SHELL_PACKAGE_NAME))
                activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertFalse(activeState!!)
            } finally {
                mAppOps.stopWatchingActive(activeWatcher)
            }
        }
    }

    @Test
    @AppModeFull(reason = "Instant app cannot query for the shell package")
    fun startOpTwiceAndVerifyChangeListener() {
        runWithShellPermissionIdentity {
            val activeChangedQueue = LinkedBlockingQueue<Boolean>()
            val activeWatcher =
                    object: AppOpsManager.OnOpActiveChangedListener {
                        override fun onOpActiveChanged(op: String,
                            uid: Int,
                            packageName: String,
                            active: Boolean) {
                            if (packageName == SHELL_PACKAGE_NAME && uid == USER_SHELL_UID) {
                                activeChangedQueue.put(active)
                            }
                        }
                    }
            mAppOps.startWatchingActive(arrayOf(OPSTR_WIFI_SCAN), { it.run() },
                    activeWatcher)
            try {
                mAppOps.startOp(OPSTR_WIFI_SCAN, mMyUid, mOpPackageName, null, null)

                var activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertTrue(activeState!!)

                mAppOps.finishOp(OPSTR_WIFI_SCAN, USER_SHELL_UID, SHELL_PACKAGE_NAME, null)

                activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertFalse(activeState!!)

                mAppOps.startOp(OPSTR_WIFI_SCAN, mMyUid, mOpPackageName, null, null)
                activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertTrue(activeState!!)

                mAppOps.finishOp(OPSTR_WIFI_SCAN, USER_SHELL_UID, SHELL_PACKAGE_NAME, null)

                activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertFalse(activeState!!)
            } finally {
                mAppOps.stopWatchingActive(activeWatcher)
            }
        }
    }

    @Test
    @AppModeFull(reason = "Instant app cannot query for the shell package")
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    fun startOpTwiceAndVerifyDeviceAttributedChangeListener() {
        runWithShellPermissionIdentity {
            val activeChangedQueue = LinkedBlockingQueue<Boolean>()
            val activeWatcher =
                    object: AppOpsManager.OnOpActiveChangedListener {
                        override fun onOpActiveChanged(op: String,
                            uid: Int,
                            packageName: String,
                            active: Boolean) {}

                        override fun onOpActiveChanged(op: String,
                            uid: Int,
                            packageName: String,
                            attributionTag: String?,
                            virtualDeviceId: Int,
                            active: Boolean,
                            attributionFlags: Int,
                            attributionChainId: Int) {
                            if (packageName == SHELL_PACKAGE_NAME && uid == USER_SHELL_UID
                                && virtualDeviceId == Context.DEVICE_ID_DEFAULT) {
                                activeChangedQueue.put(active)
                            }
                        }
                    }
            mAppOps.startWatchingActive(arrayOf(OPSTR_WIFI_SCAN), { it.run() },
                    activeWatcher)
            try {
                mAppOps.startOp(OPSTR_WIFI_SCAN, mMyUid, mOpPackageName, null, null)

                var activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertTrue(activeState!!)

                mAppOps.finishOp(OPSTR_WIFI_SCAN, USER_SHELL_UID, SHELL_PACKAGE_NAME, null)

                activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertFalse(activeState!!)

                mAppOps.startOp(OPSTR_WIFI_SCAN, mMyUid, mOpPackageName, null, null)
                activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertTrue(activeState!!)

                mAppOps.finishOp(OPSTR_WIFI_SCAN, USER_SHELL_UID, SHELL_PACKAGE_NAME, null)

                activeState = activeChangedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertNotNull("Did not receive onOpChanged callback within $TIMEOUT_MS ms",
                    activeState)
                assertFalse(activeState!!)
            } finally {
                mAppOps.stopWatchingActive(activeWatcher)
            }
        }
    }

    @Test
    fun finishOpWithoutStartOp() {
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))

        mAppOps.finishOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null)
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))
    }

    @Test
    fun doubleFinishOpStartOp() {
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))

        mAppOps.startOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null, null)
        assertTrue(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))

        mAppOps.finishOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null)
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))
        mAppOps.finishOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null)
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))
    }

    @Test
    fun doubleFinishOpAfterDoubleStartOp() {
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))

        mAppOps.startOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null, null)
        assertTrue(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))
        mAppOps.startOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null, null)
        assertTrue(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))

        mAppOps.finishOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null)
        assertTrue(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))
        mAppOps.finishOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null)
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))
    }

    @Test
    fun noteOpWhileOpIsActive() {
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))

        mAppOps.startOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null, null)
        assertTrue(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))

        mAppOps.noteOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null, null)
        assertTrue(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))

        mAppOps.finishOp(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName, null)
        assertFalse(mAppOps.isOpActive(OPSTR_FINE_LOCATION, mMyUid, mOpPackageName))
    }

    @Test
    fun testCheckPackagePassesCheck() {
        mAppOps.checkPackage(Process.myUid(), mOpPackageName)
        mAppOps.checkPackage(Process.SYSTEM_UID, "android")
    }

    @Test
    fun testCheckPackageDoesntPassCheck() {
        try {
            // Package name doesn't match UID.
            mAppOps.checkPackage(Process.SYSTEM_UID, mOpPackageName)
            fail("SecurityException expected")
        } catch (expected: SecurityException) {
        }

        try {
            // Package name doesn't match UID.
            mAppOps.checkPackage(Process.myUid(), "android")
            fail("SecurityException expected")
        } catch (expected: SecurityException) {
        }

        try {
            // Package name missing
            mAppOps.checkPackage(Process.myUid(), "")
            fail("SecurityException expected")
        } catch (expected: SecurityException) {
        }
    }

    @Test
    fun testWatchingMode_receivesCallback() {
        val changedQueue = LinkedBlockingQueue<Int>()
        val onOpChangeWatcher = object: OnOpChangedListener {
            private var onOpChangedCount = 0
            override fun onOpChanged(op: String?, packageName: String?) {
                if (Objects.equal(op, OPSTR_WRITE_CALENDAR)
                    && Objects.equal(packageName, mOpPackageName)) {
                    changedQueue.put(onOpChangedCount++)
                    return
                }
                Log.w(LOG_TAG,
                    "Received unexpected onOpChanged callback for op $op packageName $packageName")
            }
        }

        try {
            runWithShellPermissionIdentity {
                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ALLOWED)
                mAppOps.startWatchingMode(OPSTR_WRITE_CALENDAR, mOpPackageName, onOpChangeWatcher)

                // After start watching, change op mode should trigger callback
                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ERRORED)
                assertEquals("onOpChanged callback count unexpected",
                    changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS), 0)

                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ALLOWED)
                assertEquals("onOpChanged callback count unexpected",
                    changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS), 1)

                // If mode doesn't change, no callback expected
                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ALLOWED)
                assertNull(changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))

                mAppOps.stopWatchingMode(onOpChangeWatcher)

                // After stop watching no callback expected when mode changes
                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ERRORED)
                assertNull(changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            }
        } finally {
            mAppOps.stopWatchingMode(onOpChangeWatcher)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    fun testWatchingMode_receivesDeviceAttributedCallback() {
        val changedQueue = LinkedBlockingQueue<Int>()
        val onOpChangeWatcher = object: OnOpChangedListener{
            private var onOpChangedCount = 0
            override fun onOpChanged(op: String?, packageName: String?) {}

            override fun onOpChanged(op: String,
                packageName: String,
                userId: Int,
                persistentDeviceId: String) {
                if (Objects.equal(op, OPSTR_WRITE_CALENDAR)
                    && Objects.equal(packageName, mOpPackageName)
                    && Objects.equal(persistentDeviceId,
                        VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT)) {
                    changedQueue.put(onOpChangedCount++)
                    return
                }
                Log.w(LOG_TAG,
                    "Received unexpected onOpChanged callback for op $op packageName $packageName")
            }
        }
        try {
            runWithShellPermissionIdentity {
                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ALLOWED)
                mAppOps.startWatchingMode(OPSTR_WRITE_CALENDAR, mOpPackageName, onOpChangeWatcher)

                // After start watching, change op mode should trigger callback
                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ERRORED)
                assertEquals("onOpChanged callback count unexpected",
                    changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    0)

                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ALLOWED)
                assertEquals("onOpChanged callback count unexpected",
                    changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS), 1)

                // If mode doesn't change, no callback expected
                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ALLOWED)
                assertNull(changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))

                mAppOps.stopWatchingMode(onOpChangeWatcher)

                // After stop watching no callback expected when mode changes
                mAppOps.setMode(OPSTR_WRITE_CALENDAR, Process.myUid(), mOpPackageName, MODE_ERRORED)
                assertNull(changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            }
        } finally {
            mAppOps.stopWatchingMode(onOpChangeWatcher)
        }
    }

    @Test
    fun startWatchingNoted_withoutExecutor_whenOpNoted_receivesCallback() {
        val notedQueue = LinkedBlockingQueue<Int>()
        val notedWatcher = object: OnOpNotedListener{
            var opNotedCount = 0
            override fun onOpNoted(op: String,
                uid: Int,
                packageName: String,
                attributionTag: String?,
                flags: Int,
                result: Int) {
                if (Objects.equal(op, OPSTR_WRITE_CALENDAR)
                    && uid == mMyUid && Objects.equal(packageName, mOpPackageName)
                    && attributionTag == null
                    && flags == OP_FLAG_SELF && result == MODE_ALLOWED) {
                    notedQueue.put(opNotedCount++)
                }
            }
        }
        try {
            mAppOps.startWatchingNoted(arrayOf(OPSTR_WRITE_CALENDAR), notedWatcher)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid, mOpPackageName,
                    /* attributionTag = */ null,
                /* message = */ null)

            assertEquals("onOpNoted callback count unexpected",
                notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                0)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid,
                mOpPackageName,
                    /* attributionTag = */ null,
                /* message = */ null)

            assertEquals("onOpNoted callback count unexpected",
                notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                1)

            mAppOps.stopWatchingNoted(notedWatcher)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid,
                mOpPackageName,
                    /* attributionTag = */ null,
                /* message = */ null)

            val exception = try {
                assertNotNull(notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                null
            } catch (e: AssertionError) {
                e
            }
            assertNotNull("Unexpected onOpNoted callback received", exception)
        } finally {
            mAppOps.stopWatchingNoted(notedWatcher)
        }
    }

    @Test
    fun startWatchingNoted_withExecutor_whenOpNoted_receivesCallback() {
        val notedQueue = LinkedBlockingQueue<Int>()
        val notedWatcher = object: OnOpNotedListener{
            var opNotedCount = 0
            override fun onOpNoted(op: String,
                uid: Int,
                packageName: String,
                attributionTag: String?,
                flags: Int,
                result: Int) {
                if (Objects.equal(op, OPSTR_WRITE_CALENDAR)
                    && uid == mMyUid && Objects.equal(packageName, mOpPackageName)
                    && Objects.equal(attributionTag, TEST_ATTRIBUTION)
                    && flags == OP_FLAG_SELF && result == MODE_ALLOWED) {
                    notedQueue.put(opNotedCount++)
                }
            }
        }
        try {
            mAppOps.startWatchingNoted(arrayOf(OPSTR_WRITE_CALENDAR), { it.run() }, notedWatcher)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid, mOpPackageName,
                TEST_ATTRIBUTION,
                /* message = */ null)

            assertEquals("onOpNoted callback count unexpected",
                notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                0)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid,
                mOpPackageName,
                TEST_ATTRIBUTION,
                /* message = */ null)

            assertEquals("onOpNoted callback count unexpected",
                notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                1)

            mAppOps.stopWatchingNoted(notedWatcher)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid,
                mOpPackageName,
                TEST_ATTRIBUTION,
                /* message = */ null)

            val exception = try {
                assertNotNull(notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                null
            } catch (e: AssertionError) {
                e
            }
            assertNotNull("Unexpected onOpNoted callback received", exception)
        } finally {
            mAppOps.stopWatchingNoted(notedWatcher)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    fun startWatchingNoted_withoutExecutor_whenOpNoted_receivesDeviceAttributedCallback() {
        val notedQueue = LinkedBlockingQueue<Int>()
        val notedWatcher = object: OnOpNotedListener{
            var opNotedCount = 0
            override fun onOpNoted(op: String,
                uid: Int,
                packageName: String,
                attributionTag: String?,
                flags: Int,
                result: Int) {}

            override fun onOpNoted(op: String,
                uid: Int,
                packageName: String,
                attributionTag: String?,
                virtualDeviceId: Int,
                flags: Int,
                result: Int) {
                if (Objects.equal(op, OPSTR_WRITE_CALENDAR)
                    && uid == mMyUid && Objects.equal(packageName, mOpPackageName)
                    && Objects.equal(attributionTag, TEST_ATTRIBUTION)
                    && virtualDeviceId == Context.DEVICE_ID_DEFAULT
                    && flags == OP_FLAG_SELF && result == MODE_ALLOWED) {
                    notedQueue.put(opNotedCount++)
                }
            }
        }

        try {
            mAppOps.startWatchingNoted(arrayOf(OPSTR_WRITE_CALENDAR), notedWatcher)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid, mOpPackageName,
                TEST_ATTRIBUTION,
                /* message = */ null)

            assertEquals("onOpNoted callback count unexpected",
                notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                0)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid,
                mOpPackageName,
                TEST_ATTRIBUTION,
                /* message = */ null)

            assertEquals("onOpNoted callback count unexpected",
                notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                1)

            mAppOps.stopWatchingNoted(notedWatcher)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid,
                mOpPackageName,
                TEST_ATTRIBUTION,
                /* message = */ null)

            val exception = try {
                assertNotNull(notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                null
            } catch (e: AssertionError) {
                e
            }
            assertNotNull("Unexpected onOpNoted callback received", exception)
        } finally {
            mAppOps.stopWatchingNoted(notedWatcher)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    fun startWatchingNoted_withExecutor_whenOpNoted_receivesDeviceAttributedCallback() {
        val notedQueue = LinkedBlockingQueue<Int>()
        val notedWatcher = object: OnOpNotedListener{
            var opNotedCount = 0
            override fun onOpNoted(op: String,
                uid: Int,
                packageName: String,
                attributionTag: String?,
                flags: Int,
                result: Int) {}

            override fun onOpNoted(op: String,
                uid: Int,
                packageName: String,
                attributionTag: String?,
                virtualDeviceId: Int,
                flags: Int,
                result: Int) {
                if (Objects.equal(op, OPSTR_WRITE_CALENDAR)
                    && uid == mMyUid && Objects.equal(packageName, mOpPackageName)
                    && attributionTag == null
                    && virtualDeviceId == Context.DEVICE_ID_DEFAULT
                    && flags == OP_FLAG_SELF && result == MODE_ALLOWED) {
                    notedQueue.put(opNotedCount++)
                }
            }
        }
        try {
            mAppOps.startWatchingNoted(arrayOf(OPSTR_WRITE_CALENDAR), { it.run() }, notedWatcher)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid, mOpPackageName,
                /* attributionTag = */ null,
                /* message = */ null)

            assertEquals("onOpNoted callback count unexpected",
                notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                0)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid,
                mOpPackageName,
                /* attributionTag = */ null,
                /* message = */ null)

            assertEquals("onOpNoted callback count unexpected",
                notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                1)

            mAppOps.stopWatchingNoted(notedWatcher)

            mAppOps.noteOp(OPSTR_WRITE_CALENDAR,
                mMyUid,
                mOpPackageName,
                /* attributionTag = */ null,
                /* message = */ null)

            val exception = try {
                assertNotNull(notedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                null
            } catch (e: AssertionError) {
                e
            }
            assertNotNull("Unexpected onOpNoted callback received", exception)
        } finally {
            mAppOps.stopWatchingNoted(notedWatcher)
        }
    }

    @Test
    fun testAllOpsHaveOpString() {
        val opStrs = HashSet<String>()
        for (opStr in AppOpsManager.getOpStrs()) {
            assertNotNull("Each app op must have an operation string defined", opStr)
            opStrs.add(opStr)
        }
        assertEquals("Not all op strings are unique", AppOpsManager.getNumOps(), opStrs.size)
    }

    @Test
    fun testOpCodesUnique() {
        val opStrs = AppOpsManager.getOpStrs()
        val opCodes = HashSet<Int>()
        for (opStr in opStrs) {
            opCodes.add(AppOpsManager.strOpToOp(opStr))
        }
        assertEquals("Not all app op codes are unique", opStrs.size, opCodes.size)
    }

    @Test
    fun testPermissionMapping() {
        for (entry in permissionToOpStr) {
            testPermissionMapping(entry.key, entry.value)
        }
    }

    private fun testPermissionMapping(permission: String, opStr: String) {
        // Do the permission => op lookups.
        val mappedOpStr = AppOpsManager.permissionToOp(permission)!!
        assertEquals(opStr, mappedOpStr)
        val opCode = AppOpsManager.strOpToOp(opStr)
        val mappedOpCode = AppOpsManager.permissionToOpCode(permission)
        assertEquals(opCode, mappedOpCode)

        // Do the op => permission lookups.
        val strMappedPermission = AppOpsManager.opToPermission(opStr)
        assertEquals(permission, strMappedPermission)
        val codeMappedPermission = AppOpsManager.opToPermission(opCode)
        assertEquals(permission, codeMappedPermission)
    }

    /**
     * Test that the app can not change the app op mode for itself.
     */
    @Test
    fun testCantSetModeForSelf() {
        try {
            val writeSmsOp = AppOpsManager.permissionToOpCode("android.permission.WRITE_SMS")
            mAppOps.setMode(writeSmsOp, Process.myUid(), mOpPackageName, AppOpsManager.MODE_ALLOWED)
            fail("Was able to set mode for self")
        } catch (expected: SecurityException) {
        }
    }

    @Test
    fun testGetOpsForPackageOpsAreLogged() {
        // This test checks if operations get logged by the system. It needs to start with a clean
        // slate, i.e. these ops can't have been logged previously for this test package. The reason
        // is that there's no API for clearing the app op logs before a test run. However, the op
        // logs are cleared when this test package is reinstalled between test runs. To make sure
        // that other test methods in this class don't affect this test method, here we use
        // operations that are not used by any other test cases.
        val mustNotBeLogged = "Operation mustn't be logged before the test runs"
        assumeTrue(mustNotBeLogged, !allowedOperationLogged(mOpPackageName, OPSTR_RECORD_AUDIO))
        assumeTrue(mustNotBeLogged, !allowedOperationLogged(mOpPackageName, OPSTR_READ_CALENDAR))

        setOpMode(mOpPackageName, OPSTR_RECORD_AUDIO, MODE_ALLOWED)
        setOpMode(mOpPackageName, OPSTR_READ_CALENDAR, MODE_ERRORED)

        // Note an op that's allowed.
        mAppOps.noteOp(OPSTR_RECORD_AUDIO, Process.myUid(), mOpPackageName)
        val mustBeLogged = "Operation must be logged"
        assertTrue(mustBeLogged, allowedOperationLogged(mOpPackageName, OPSTR_RECORD_AUDIO))

        // Note another op that's not allowed.
        mAppOps.noteOpNoThrow(OPSTR_READ_CALENDAR, Process.myUid(), mOpPackageName)
        assertTrue(mustBeLogged, allowedOperationLogged(mOpPackageName, OPSTR_RECORD_AUDIO))
        assertTrue(mustBeLogged, rejectedOperationLogged(mOpPackageName, OPSTR_READ_CALENDAR))
    }

    @Test
    fun testNonHistoricalStatePersistence() {
        // Put a package and uid level data
        runWithShellPermissionIdentity {
            mAppOps.setMode(OPSTR_PICTURE_IN_PICTURE, Process.myUid(),
                    mOpPackageName, MODE_IGNORED)
            mAppOps.setUidMode(OPSTR_PICTURE_IN_PICTURE, Process.myUid(), MODE_ERRORED)

            // Write the data to disk and read it
            mAppOps.reloadNonHistoricalState()
        }

        // Verify the uid state is preserved
        assertSame(mAppOps.unsafeCheckOpNoThrow(OPSTR_PICTURE_IN_PICTURE,
                Process.myUid(), mOpPackageName), MODE_ERRORED)

        runWithShellPermissionIdentity {
            // Clear the uid state
            mAppOps.setUidMode(OPSTR_PICTURE_IN_PICTURE, Process.myUid(),
                    AppOpsManager.opToDefaultMode(OPSTR_PICTURE_IN_PICTURE))
        }

        // Verify the package state is preserved
        assertSame(mAppOps.unsafeCheckOpNoThrow(OPSTR_PICTURE_IN_PICTURE,
                Process.myUid(), mOpPackageName), MODE_IGNORED)

        runWithShellPermissionIdentity {
            // Clear the uid state
            val defaultMode = AppOpsManager.opToDefaultMode(OPSTR_PICTURE_IN_PICTURE)
            mAppOps.setUidMode(OPSTR_PICTURE_IN_PICTURE, Process.myUid(), defaultMode)
            mAppOps.setMode(OPSTR_PICTURE_IN_PICTURE, Process.myUid(),
                    mOpPackageName, defaultMode)
        }
    }

    @Test
    fun noteOpForBadUid() {
        runWithShellPermissionIdentity {
            val mode = mAppOps.noteOpNoThrow(OPSTR_RECORD_AUDIO, Process.myUid() + 1,
                    mOpPackageName)
            assertEquals(mode, MODE_ERRORED)
        }
    }

    @Test
    fun startOpForBadUid() {
        runWithShellPermissionIdentity {
            val mode = mAppOps.startOpNoThrow(OPSTR_RECORD_AUDIO, Process.myUid() + 1,
                    mOpPackageName)
            assertEquals(mode, MODE_ERRORED)
        }
    }

    @Test
    fun checkOpForBadUid() {
        val defaultMode = AppOpsManager.opToDefaultMode(OPSTR_RECORD_AUDIO)

        runWithShellPermissionIdentity {
            mAppOps.setUidMode(OPSTR_RECORD_AUDIO, Process.myUid(), MODE_ERRORED)
            try {
                val mode = mAppOps.unsafeCheckOpNoThrow(OPSTR_RECORD_AUDIO, Process.myUid() + 1,
                        mOpPackageName)

                // For invalid uids checkOp return the default mode
                assertEquals(mode, defaultMode)
            } finally {
                // Clear the uid state
                mAppOps.setUidMode(OPSTR_RECORD_AUDIO, Process.myUid(), defaultMode)
            }
        }
    }

    @Test
    fun ensurePhoneCallOpsRestricted() {
        val pm = mContext.packageManager
        assumeTrue((pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
                pm.hasSystemFeature(PackageManager.FEATURE_TELECOM)) &&
                pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
        val micReturn = mAppOps.noteOp(OPSTR_PHONE_CALL_MICROPHONE, Process.myUid(), mOpPackageName,
                null, null)
        assertEquals(MODE_IGNORED, micReturn)
        val cameraReturn = mAppOps.noteOp(OPSTR_PHONE_CALL_CAMERA, Process.myUid(),
                mOpPackageName, null, null)
        assertEquals(MODE_IGNORED, cameraReturn)
    }

    @Test
    @FlakyTest
    fun testRestrictedSettingsOpsRead() {
        val changedQueue = LinkedBlockingQueue<Int>()
        val onOpChangeWatcher = object: OnOpChangedListener{
            var opChangedCount = 0
            override fun onOpChanged(op: String?, packageName: String?) {
                if (Objects.equal(op, OPSTR_ACCESS_RESTRICTED_SETTINGS)
                    && Objects.equal(packageName, mOpPackageName)) {
                    changedQueue.put(opChangedCount++)
                }
            }
        }

        // Apps without manage appops permission will get security exception if it tries to access
        // restricted settings ops.
        Assert.assertThrows(SecurityException::class.java) {
            mAppOps.unsafeCheckOpRawNoThrow(OPSTR_ACCESS_RESTRICTED_SETTINGS, Process.myUid(),
                    mOpPackageName)
        }
        // Apps with manage appops permission (shell) should be able to read restricted settings op
        // successfully.
        runWithShellPermissionIdentity {
            mAppOps.unsafeCheckOpRawNoThrow(OPSTR_ACCESS_RESTRICTED_SETTINGS, Process.myUid(),
                    mOpPackageName)
        }

        // Normal apps should not receive op change callback when op is changed.
        try {
            setOpMode(mOpPackageName, OPSTR_ACCESS_RESTRICTED_SETTINGS, MODE_ERRORED)

            mAppOps.startWatchingMode(OPSTR_ACCESS_RESTRICTED_SETTINGS, mOpPackageName,
                    onOpChangeWatcher)

            val exception = try {
                assertNotNull("Unexpected onOpChanged callback received",
                    changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                null
            } catch (e: AssertionError) {
                e
            }
            assertNotNull(exception)
        } finally {
            // Clean up registered watcher.
            mAppOps.stopWatchingMode(onOpChangeWatcher)
        }

        // Apps with manage ops permission (shell) should be able to receive op change callback.
        runWithShellPermissionIdentity {
            try {
                setOpMode(mOpPackageName, OPSTR_ACCESS_RESTRICTED_SETTINGS, MODE_ERRORED)

                mAppOps.startWatchingMode(OPSTR_ACCESS_RESTRICTED_SETTINGS, mOpPackageName,
                        onOpChangeWatcher)

                setOpMode(mOpPackageName, OPSTR_ACCESS_RESTRICTED_SETTINGS, MODE_ALLOWED)

                assertEquals("onOpChanged callback count unexpected",
                    changedQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    0)
            } finally {
                // Clean up registered watcher.
                mAppOps.stopWatchingMode(onOpChangeWatcher)
            }
        }
    }

    private fun runWithShellPermissionIdentity(command: () -> Unit) {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        uiAutomation.adoptShellPermissionIdentity()
        try {
            command.invoke()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }
}
