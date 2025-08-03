/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.appops.cts

import android.app.AppOpsManager
import android.app.AppOpsManager.MAX_PRIORITY_UID_STATE
import android.app.AppOpsManager.MIN_PRIORITY_UID_STATE
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.OPSTR_WIFI_SCAN
import android.app.AppOpsManager.OP_FLAGS_ALL
import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXY
import android.app.AppOpsManager.OP_FLAG_UNTRUSTED_PROXIED
import android.app.AppOpsManager.UID_STATE_TOP
import android.content.Intent
import android.content.Intent.ACTION_INSTALL_PACKAGE
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val BACKGROUND_PACKAGE = "android.app.appops.cts.appinbackground"
private const val SHELL_PACKAGE_NAME = "com.android.shell"

class AppOpEventCollectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!

    private val myUid = Process.myUid()
    private val shellUid = UserHandle.getUid(Process.myUserHandle().identifier,
            UserHandle.getAppId(Process.SHELL_UID))
    private val myPackage = context.packageName
    private val otherPkg: String
    private val otherUid: Int
    private val firstTag = "firstProxyAttribution"
    private val secondTag = "secondProxyAttribution"

    init {
    // Find another app to blame
    val otherAppInfo = context.packageManager
        .resolveActivity(Intent(ACTION_INSTALL_PACKAGE).addCategory(Intent.CATEGORY_DEFAULT)
            .setDataAndType(Uri.parse("content://com.example/foo.apk"),
                "application/vnd.android.package-archive"), 0)
        ?.activityInfo?.applicationInfo

        assumeNotNull(otherAppInfo)
        otherPkg = otherAppInfo!!.packageName
        otherUid = otherAppInfo.uid
    }

    // Start an activity to make sure this app counts as being in the foreground
    @Rule
    @JvmField
    var activityRule = ActivityTestRule(UidStateForceActivity::class.java)

    @Before
    fun wakeScreenUp() {
        val uiDevice = UiDevice.getInstance(instrumentation)
        uiDevice.wakeUp()
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    @Before
    fun makeSureTimeStampsAreDistinct() {
        sleep(1)
    }

    @Test
    fun ensureCorrectOpStr() {
        appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        val opEntry = getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
        assertThat(opEntry.opStr).isEqualTo(OPSTR_WIFI_SCAN)
    }

    @Test
    fun switchUidStateWhileOpsAreRunning() {
        val before = System.currentTimeMillis()

        // Start twice to also test switching uid state with nested starts running
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)

        val beforeUidChange = System.currentTimeMillis()
        sleep(1)

        assertThat(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
            .getLastAccessTime(MAX_PRIORITY_UID_STATE, UID_STATE_TOP, OP_FLAGS_ALL))
            .isIn(before..beforeUidChange)

        try {
            activityRule.activity.finish()
            UidStateForceActivity.waitForDestroyed()

            eventually {
                // The system remembers the time before and after the uid change as separate events
                assertThat(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
                    .getLastAccessTime(UID_STATE_TOP + 1, MIN_PRIORITY_UID_STATE,
                        OP_FLAGS_ALL)).isAtLeast(beforeUidChange)
            }
        } finally {
            appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)
            appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)
        }
    }

    @Test
    fun noteWithAttributionAndCheckOpEntries() {
        val before = System.currentTimeMillis()
        appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG, null)
        val after = System.currentTimeMillis()

        val opEntry = getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
        val attributionOpEntry = opEntry.attributedOpEntries[TEST_ATTRIBUTION_TAG]!!

        assertThat(attributionOpEntry.getLastAccessForegroundTime(OP_FLAG_SELF)).isIn(before..after)
        assertThat(opEntry.getLastAccessForegroundTime(OP_FLAG_SELF)).isIn(before..after)

        // Access should should also show up in the combined state for all op-flags
        assertThat(attributionOpEntry.getLastAccessForegroundTime(OP_FLAGS_ALL)).isIn(before..after)
        assertThat(opEntry.getLastAccessForegroundTime(OP_FLAGS_ALL)).isIn(before..after)

        // Foreground access should should also show up in the combined state for fg and bg
        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(before..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(before..after)
        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAGS_ALL)).isIn(before..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAGS_ALL)).isIn(before..after)

        // The access was in foreground, hence there is no background access
        assertThat(attributionOpEntry.getLastAccessBackgroundTime(OP_FLAG_SELF)).isLessThan(before)
        assertThat(opEntry.getLastAccessBackgroundTime(OP_FLAG_SELF)).isLessThan(before)
        assertThat(attributionOpEntry.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isLessThan(before)
        assertThat(opEntry.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isLessThan(before)

        // The access was for a attribution, hence there is no access for the default attribution
        if (null in opEntry.attributedOpEntries) {
            assertThat(opEntry.attributedOpEntries[null]!!
                .getLastAccessForegroundTime(OP_FLAG_SELF)).isLessThan(before)
        }

        // The access does not show up for other op-flags
        assertThat(
            attributionOpEntry.getLastAccessForegroundTime(OP_FLAGS_ALL and OP_FLAG_SELF.inv())
        ).isLessThan(before)
        assertThat(opEntry.getLastAccessForegroundTime(OP_FLAGS_ALL and OP_FLAG_SELF.inv()))
            .isLessThan(before)
    }

    @AppModeFull(reason = "instant apps cannot see other packages")
    @Test
    fun noteInBackgroundWithAttributionAndCheckOpEntries() {
        val uid = context.packageManager.getPackageUid(BACKGROUND_PACKAGE, 0)

        val before = System.currentTimeMillis()
        assertThat(
            runWithShellPermissionIdentity {
                appOpsManager.noteOp(
                    OPSTR_WIFI_SCAN, uid, BACKGROUND_PACKAGE, TEST_ATTRIBUTION_TAG, null
                )
            }
        ).isEqualTo(AppOpsManager.MODE_ALLOWED)
        val after = System.currentTimeMillis()

        val opEntry = getOpEntry(uid, BACKGROUND_PACKAGE, OPSTR_WIFI_SCAN)!!
        val attributionOpEntry = opEntry.attributedOpEntries[TEST_ATTRIBUTION_TAG]!!

        assertThat(attributionOpEntry.getLastAccessBackgroundTime(OP_FLAG_SELF)).isIn(before..after)
        assertThat(opEntry.getLastAccessBackgroundTime(OP_FLAG_SELF)).isIn(before..after)

        // Access should should also show up in the combined state for all op-flags
        assertThat(attributionOpEntry.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isIn(before..after)
        assertThat(opEntry.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isIn(before..after)

        // Background access should should also show up in the combined state for fg and bg
        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(before..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(before..after)
        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAGS_ALL)).isIn(before..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAGS_ALL)).isIn(before..after)

        // The access was in background, hence there is no foreground access
        assertThat(attributionOpEntry.getLastAccessForegroundTime(OP_FLAG_SELF)).isLessThan(before)
        assertThat(opEntry.getLastAccessForegroundTime(OP_FLAG_SELF)).isLessThan(before)
        assertThat(attributionOpEntry.getLastAccessForegroundTime(OP_FLAGS_ALL)).isLessThan(before)
        assertThat(opEntry.getLastAccessForegroundTime(OP_FLAGS_ALL)).isLessThan(before)

        // The access was for a attribution, hence there is no access for the default attribution
        if (null in opEntry.attributedOpEntries) {
            assertThat(opEntry.attributedOpEntries[null]!!
                .getLastAccessBackgroundTime(OP_FLAG_SELF)).isLessThan(before)
        }

        // The access does not show up for other op-flags
        assertThat(
            attributionOpEntry.getLastAccessBackgroundTime(OP_FLAGS_ALL and OP_FLAG_SELF.inv())
        ).isLessThan(before)
        assertThat(opEntry.getLastAccessBackgroundTime(OP_FLAGS_ALL and OP_FLAG_SELF.inv()))
            .isLessThan(before)
    }

    @Test
    fun noteSelfAndTrustedAccessAndCheckOpEntries() {
        val before = System.currentTimeMillis()

        // Using the shell identity causes a trusted proxy note
        val afterTrusted = AtomicLong()
        runWithShellPermissionIdentity {
            appOpsManager.noteProxyOp(OPSTR_WIFI_SCAN, myPackage, myUid, null, null)
            afterTrusted.set(System.currentTimeMillis())
            appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        }

        // Make sure timestamps are distinct
        sleep(1)

        val after = System.currentTimeMillis()

        val opEntry = getOpEntry(shellUid, SHELL_PACKAGE_NAME, OPSTR_WIFI_SCAN)!!
        val attributionOpEntry = opEntry.attributedOpEntries[null]!!

        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAG_TRUSTED_PROXY))
            .isIn(before..afterTrusted.get())
        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAG_SELF))
                .isIn(afterTrusted.get()..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAG_TRUSTED_PROXY))
                .isIn(before..afterTrusted.get())
        assertThat(opEntry.getLastAccessTime(OP_FLAG_SELF))
                .isIn(afterTrusted.get()..after)

        // When asked for any flags, the second access overrides the first
        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAGS_ALL))
                .isIn(afterTrusted.get()..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAGS_ALL))
                .isIn(afterTrusted.get()..after)
    }

    @Test
    fun noteForTwoAttributionsCheckOpEntries() {
        val before = System.currentTimeMillis()
        appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, firstTag, null)
        val afterFirst = System.currentTimeMillis()

        // Make sure timestamps are distinct
        sleep(1)

        // self note
        appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, secondTag, null)
        val after = System.currentTimeMillis()

        val opEntry = getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
        val firstAttributionOpEntry = opEntry.attributedOpEntries[firstTag]!!
        val secondAttributionOpEntry = opEntry.attributedOpEntries[secondTag]!!

        assertThat(firstAttributionOpEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(before..afterFirst)
        assertThat(secondAttributionOpEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(afterFirst..after)

        // When asked for any attribution, the second access overrides the first
        assertThat(opEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(afterFirst..after)
    }

    @AppModeFull(reason = "instant apps cannot see other packages")
    @Test
    fun noteFromTwoProxiesAndVerifyProxyInfo() {
        // Using the shell identity causes a trusted proxy note
        runWithShellPermissionIdentity {
            context.createAttributionContext(firstTag)
                .getSystemService(AppOpsManager::class.java)!!
                .noteProxyOp(OPSTR_WIFI_SCAN, otherPkg, otherUid, null, null)
        }

        // Make sure timestamps are distinct
        sleep(1)

        // untrusted proxy note
        context.createAttributionContext(secondTag)
            .getSystemService(AppOpsManager::class.java)!!
            .noteProxyOp(OPSTR_WIFI_SCAN, otherPkg, otherUid, null, null)

        val opEntry = getOpEntry(otherUid, otherPkg, OPSTR_WIFI_SCAN)!!
        val attributionOpEntry = opEntry.attributedOpEntries[null]!!

        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.packageName)
            .isEqualTo(SHELL_PACKAGE_NAME)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.packageName)
            .isEqualTo(SHELL_PACKAGE_NAME)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.uid)
            .isEqualTo(shellUid)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.uid).isEqualTo(
                shellUid)

        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.packageName)
            .isEqualTo(myPackage)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.packageName)
            .isEqualTo(myPackage)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.uid)
            .isEqualTo(myUid)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.uid).isEqualTo(myUid)

        // Expecting null instead of firstTag as the tag is not declared in other app (b/304983146)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.attributionTag)
            .isNull()
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.attributionTag)
            .isEqualTo(secondTag)

        // If asked for all op-flags the second attribution overrides the first
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAGS_ALL)?.attributionTag)
            .isEqualTo(secondTag)
    }

    @AppModeFull(reason = "instant apps cannot see other packages")
    @Test
    fun startStopTrustedProxyVerifyRunningAndTime() {
        val beforeTrusted = System.currentTimeMillis()
        // Make sure timestamps are distinct
        sleep(1)

        lateinit var firstAttrManager: AppOpsManager
        // Using the shell identity causes a trusted proxy op
        runWithShellPermissionIdentity {
            firstAttrManager = context.createAttributionContext(firstTag)!!
                .getSystemService(AppOpsManager::class.java)!!
            val start = firstAttrManager.startProxyOpNoThrow(OPSTR_WIFI_SCAN, otherUid, otherPkg,
                    null, null)
            assertThat(start).isEqualTo(MODE_ALLOWED)
            sleep(1)
        }

        with(getOpEntry(otherUid, otherPkg, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.isRunning).isTrue()
            assertThat(attributedOpEntries[null]?.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)!!
                .packageName).isEqualTo(SHELL_PACKAGE_NAME)
            // Expecting null instead of firstTag as the tag is not declared in the
            // other app (b/304983146)
            assertThat(attributedOpEntries[null]?.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)!!
                .attributionTag).isNull()
            assertThat(isRunning).isTrue()
        }

        with(getOpEntry(shellUid, SHELL_PACKAGE_NAME, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[firstTag]!!.isRunning).isTrue()
            assertThat(attributedOpEntries[firstTag]!!
                .getLastProxyInfo(OP_FLAGS_ALL)).isNull()
        }

        runWithShellPermissionIdentity {
            firstAttrManager.finishProxyOp(OPSTR_WIFI_SCAN, otherUid, otherPkg, null)
        }
        sleep(1)
        val afterTrusted = System.currentTimeMillis()

        val opEntry = getOpEntry(otherUid, otherPkg, OPSTR_WIFI_SCAN)!!
        val attributionOpEntry = opEntry.attributedOpEntries[null]!!
        assertThat(attributionOpEntry.isRunning).isFalse()
        assertThat(opEntry.isRunning).isFalse()
        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAG_TRUSTED_PROXIED))
            .isIn(beforeTrusted..afterTrusted)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.packageName)
            .isEqualTo(SHELL_PACKAGE_NAME)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.packageName)
            .isEqualTo(SHELL_PACKAGE_NAME)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.uid)
            .isEqualTo(shellUid)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.uid).isEqualTo(
                shellUid)
        // Expecting null instead of firstTag as the tag is not declared in other app (b/304983146)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.attributionTag)
            .isNull()
    }

    @AppModeFull(reason = "instant apps cannot see other packages")
    @Test
    fun startStopUntrustedProxyVerifyRunningAndTime() {
        val beforeUntrusted = System.currentTimeMillis()
        // Make sure timestamps are distinct
        sleep(1)

        // Untrusted proxy op
        val secondAttrManager = context.createAttributionContext(secondTag)!!
            .getSystemService(AppOpsManager::class.java)!!
        secondAttrManager.startProxyOpNoThrow(OPSTR_WIFI_SCAN, otherUid, otherPkg, null, null)
        with(getOpEntry(otherUid, otherPkg, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]?.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)!!
                .packageName).isEqualTo(myPackage)
            assertThat(attributedOpEntries[null]?.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)!!
                .attributionTag).isEqualTo(secondTag)
        }

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[secondTag]!!.isRunning).isTrue()
            assertThat(attributedOpEntries[secondTag]!!
                .getLastProxyInfo(OP_FLAGS_ALL)).isNull()
        }

        secondAttrManager.finishProxyOp(OPSTR_WIFI_SCAN, otherUid, otherPkg, null)
        sleep(1)
        val afterUntrusted = System.currentTimeMillis()

        val opEntry = getOpEntry(otherUid, otherPkg, OPSTR_WIFI_SCAN)!!
        val attributionOpEntry = opEntry.attributedOpEntries[null]!!

        assertThat(attributionOpEntry.isRunning).isFalse()
        assertThat(opEntry.isRunning).isFalse()
        assertThat(attributionOpEntry.getLastAccessTime(OP_FLAG_UNTRUSTED_PROXIED))
            .isIn(beforeUntrusted..afterUntrusted)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.packageName)
            .isEqualTo(myPackage)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.packageName)
            .isEqualTo(myPackage)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.uid)
            .isEqualTo(myUid)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.uid).isEqualTo(myUid)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.attributionTag)
            .isEqualTo(secondTag)
    }

    @AppModeFull(reason = "instant apps cannot see other packages")
    @Test
    fun startStopTrustedAndUntrustedProxyVerifyProxyInfo() {
        lateinit var firstAttrManager: AppOpsManager
        // Using the shell identity causes a trusted proxy op
        runWithShellPermissionIdentity {
            firstAttrManager = context.createAttributionContext(firstTag)!!
                .getSystemService(AppOpsManager::class.java)!!
            val start = firstAttrManager.startProxyOpNoThrow(OPSTR_WIFI_SCAN, otherUid, otherPkg,
                    null, null)
            sleep(1)
            firstAttrManager.finishProxyOp(OPSTR_WIFI_SCAN, otherUid, otherPkg, null)
            sleep(1)
        }

        // Untrusted proxy op
        val secondAttrManager = context.createAttributionContext(secondTag)!!
            .getSystemService(AppOpsManager::class.java)!!
        secondAttrManager.startProxyOpNoThrow(OPSTR_WIFI_SCAN, otherUid, otherPkg, null, null)

        sleep(1)
        secondAttrManager.finishProxyOp(OPSTR_WIFI_SCAN, otherUid, otherPkg, null)

        val opEntry = getOpEntry(otherUid, otherPkg, OPSTR_WIFI_SCAN)!!
        val attributionOpEntry = opEntry.attributedOpEntries[null]!!
        assertThat(attributionOpEntry.isRunning).isFalse()
        assertThat(opEntry.isRunning).isFalse()

        // Expecting null instead of firstTag as the tag is not declared in other app (b/304983146)
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.attributionTag)
                .isNull()
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.attributionTag)
            .isEqualTo(secondTag)

        // If asked for all op-flags the second attribution overrides the first
        assertThat(attributionOpEntry.getLastProxyInfo(OP_FLAGS_ALL)?.attributionTag)
            .isEqualTo(secondTag)
    }

    @Test
    fun startProxyOpThrowsSecurityException() {
        lateinit var firstAttrManager: AppOpsManager
        var exception: SecurityException? = null
        try {
            runWithShellPermissionIdentity {
                firstAttrManager = context.createAttributionContext(firstTag)!!
                        .getSystemService(AppOpsManager::class.java)!!
                val start = firstAttrManager.startProxyOp(OPSTR_WIFI_SCAN, Process.INVALID_UID,
                        otherPkg, null, null)
            }
        } catch (e: SecurityException) {
            exception = e
        }
        assertThat(exception).isNotNull()
    }

    @Test
    fun startStopMultipleOpsAndVerifyIsRunning() {
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.isRunning).isTrue()
            attributedOpEntries[TEST_ATTRIBUTION_TAG]?.let { assertThat(it.isRunning).isFalse() }
            assertThat(isRunning).isTrue()
        }

        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG, null)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.isRunning).isTrue()
            assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!.isRunning).isTrue()
            assertThat(isRunning).isTrue()
        }

        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG, null)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.isRunning).isTrue()
            assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!.isRunning).isTrue()
            assertThat(isRunning).isTrue()
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.isRunning).isTrue()
            assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!.isRunning).isTrue()
            assertThat(isRunning).isTrue()
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.isRunning).isTrue()
            assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!.isRunning).isFalse()
            assertThat(isRunning).isTrue()
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.isRunning).isFalse()
            assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!.isRunning).isFalse()
            assertThat(isRunning).isFalse()
        }
    }

    @Test
    fun startStopMultipleOpsAndVerifyLastAccess() {
        val beforeNullAttributionStart = System.currentTimeMillis()
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        val afterNullAttributionStart = System.currentTimeMillis()

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.getLastAccessTime(OP_FLAGS_ALL))
                .isIn(beforeNullAttributionStart..afterNullAttributionStart)
            attributedOpEntries[TEST_ATTRIBUTION_TAG]?.let {
                assertThat(it.getLastAccessTime(OP_FLAGS_ALL)).isAtMost(beforeNullAttributionStart)
            }
            assertThat(getLastAccessTime(OP_FLAGS_ALL))
                .isIn(beforeNullAttributionStart..afterNullAttributionStart)
        }

        val beforeFirstAttributionStart = System.currentTimeMillis()
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG, null)
        val afterFirstAttributionStart = System.currentTimeMillis()

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.getLastAccessTime(OP_FLAGS_ALL))
                .isIn(beforeNullAttributionStart..afterNullAttributionStart)
            assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!.getLastAccessTime(OP_FLAGS_ALL))
                .isIn(beforeFirstAttributionStart..afterFirstAttributionStart)
            assertThat(getLastAccessTime(OP_FLAGS_ALL))
                .isIn(beforeFirstAttributionStart..afterFirstAttributionStart)
        }

        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG, null)

        // Nested startOps do _not_ count as another access
        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.getLastAccessTime(OP_FLAGS_ALL))
                .isIn(beforeNullAttributionStart..afterNullAttributionStart)
            assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!.getLastAccessTime(OP_FLAGS_ALL))
                .isIn(beforeFirstAttributionStart..afterFirstAttributionStart)
            assertThat(getLastAccessTime(OP_FLAGS_ALL))
                .isIn(beforeFirstAttributionStart..afterFirstAttributionStart)
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG)
        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG)
        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)
    }

    @Test
    fun startStopMultipleOpsAndVerifyDuration() {
        val beforeNullAttrStart = SystemClock.elapsedRealtime()
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        val afterNullAttrStart = SystemClock.elapsedRealtime()

        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(attributedOpEntries[null]!!.getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterNullAttrStart..afterGetOp - beforeNullAttrStart)
                assertThat(getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterNullAttrStart..afterGetOp - beforeNullAttrStart)
            }
        }

        val beforeAttrStart = SystemClock.elapsedRealtime()
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG, null)
        val afterAttrStart = SystemClock.elapsedRealtime()

        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(attributedOpEntries[null]!!.getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterNullAttrStart..afterGetOp - beforeNullAttrStart)
                assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!
                    .getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterAttrStart..afterGetOp - beforeAttrStart)

                // The last duration is the duration of the last started attribution
                assertThat(getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterAttrStart..afterGetOp - beforeAttrStart)
            }
        }

        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG, null)

        // Nested startOps do _not_ start another duration counting, hence the nested
        // startOp and finishOp calls have no affect
        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(attributedOpEntries[null]!!.getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterNullAttrStart..afterGetOp - beforeNullAttrStart)
                assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!
                    .getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterAttrStart..afterGetOp - beforeAttrStart)
                assertThat(getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterAttrStart..afterGetOp - beforeAttrStart)
            }
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG)

        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(attributedOpEntries[null]!!.getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterNullAttrStart..afterGetOp - beforeNullAttrStart)
                assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!
                    .getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterAttrStart..afterGetOp - beforeAttrStart)
                assertThat(getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterAttrStart..afterGetOp - beforeAttrStart)
            }
        }

        val beforeAttrStop = SystemClock.elapsedRealtime()
        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_ATTRIBUTION_TAG)
        sleep(1)
        val afterAttrStop = SystemClock.elapsedRealtime()

        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(attributedOpEntries[null]!!.getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeGetOp - afterNullAttrStart..afterGetOp - beforeNullAttrStart)
                assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!
                    .getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeAttrStop - afterAttrStart..afterAttrStop - beforeAttrStart)
                assertThat(getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeAttrStop - afterAttrStart..afterAttrStop - beforeAttrStart)
            }
        }

        val beforeNullAttrStop = SystemClock.elapsedRealtime()
        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)
        val afterNullAttrStop = SystemClock.elapsedRealtime()

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(attributedOpEntries[null]!!.getLastDuration(OP_FLAGS_ALL))
                .isIn(beforeNullAttrStop -
                    afterNullAttrStart..afterNullAttrStop - beforeNullAttrStart)
            assertThat(attributedOpEntries[TEST_ATTRIBUTION_TAG]!!.getLastDuration(OP_FLAGS_ALL))
                .isIn(beforeAttrStop - afterAttrStart..afterAttrStop - beforeAttrStart)
            assertThat(getLastDuration(OP_FLAGS_ALL))
                .isIn(beforeAttrStop - afterAttrStart..afterAttrStop - beforeAttrStart)
        }
    }
}
