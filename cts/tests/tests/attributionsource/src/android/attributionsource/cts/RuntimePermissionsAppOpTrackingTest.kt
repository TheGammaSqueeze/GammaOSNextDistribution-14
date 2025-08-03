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
package android.attributionsource.cts

import android.Manifest
import android.app.AppOpsManager
import android.app.Instrumentation
import android.content.AttributionSource
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.content.pm.PackageManager.FEATURE_TELEPHONY
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.RemoteCallback
import android.os.SystemClock
import android.os.UserHandle
import android.permission.PermissionManager
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatcher
import org.mockito.Mockito.eq
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.intThat
import org.mockito.Mockito.isNull
import org.mockito.Mockito.mock

@AppModeFull(reason = "Instant apps cannot hold READ_CONTACTS/READ_CALENDAR/READ_SMS/READ_CALL_LOG")
class RuntimePermissionsAppOpTrackingTest {

    @get:Rule val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUpTest() {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
        SystemUtil.runWithShellPermissionIdentity {
            appOpsManager.clearHistory()
            appOpsManager.setHistoryParameters(
                    AppOpsManager.HISTORICAL_MODE_ENABLED_ACTIVE,
                    SNAPSHOT_INTERVAL_MILLIS,
                    INTERVAL_COMPRESSION_MULTIPLIER)

            appOpsManager.resetPackageOpsNoHistory(context.packageName)
            appOpsManager.resetPackageOpsNoHistory(SHELL_PACKAGE_NAME)
            appOpsManager.resetPackageOpsNoHistory(RECEIVER_PACKAGE_NAME)
            appOpsManager.resetPackageOpsNoHistory(RECEIVER2_PACKAGE_NAME)
        }
    }

    @After
    fun tearDownTest() {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
        SystemUtil.runWithShellPermissionIdentity {
            appOpsManager.clearHistory()
            appOpsManager.resetHistoryParameters()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSelfContactsAccess() {
        testSelfAccess(ContactsContract.Contacts.CONTENT_URI,
                Manifest.permission.READ_CONTACTS)
    }

    @Test
    @Throws(Exception::class)
    fun testSelfCalendarAccess() {
        testSelfAccess(CalendarContract.Calendars.CONTENT_URI,
                Manifest.permission.READ_CALENDAR)
    }

    @Test
    @Throws(Exception::class)
    fun testSelfSmsAccess() {
        assumeNotTv()
        assumeHasTelephony()
        testSelfAccess(Telephony.Sms.CONTENT_URI,
                Manifest.permission.READ_SMS)
    }

    @Test
    @Throws(Exception::class)
    fun testSelfCallLogAccess() {
        testSelfAccess(CallLog.Calls.CONTENT_URI,
                Manifest.permission.READ_CALL_LOG)
    }

    @Throws(Exception::class)
    private fun testSelfAccess(uri: Uri, permission: String) {
        val context = createAttributionContext(ACCESSOR_ATTRIBUTION_TAG, null, null)
        val beginEndMillis = System.currentTimeMillis()
        context.contentResolver.query(uri, null, null, null)!!.close()
        val endTimeMillis = System.currentTimeMillis()

        assertNotRunningOpAccess(AppOpsManager.permissionToOp(permission)!!,
                beginEndMillis, endTimeMillis, context.attributionSource,
                /*accessorForeground*/ true, /*receiverForeground*/ false,
                /*accessorTrusted*/ true, /*accessorAccessCount*/ 1,
                /*receiverAccessCount*/ 0, /*checkAccessor*/ true,
                /*fromDatasource*/ false)
    }

    @Test
    @Throws(Exception::class)
    fun testSelfCalendarWrite() {
        testSelfWrite(CalendarContract.Calendars.CONTENT_URI,
                Manifest.permission.WRITE_CALENDAR)
    }

    @Test
    @Throws(Exception::class)
    fun testSelfCallLogWrite() {
        testSelfWrite(CallLog.Calls.CONTENT_URI,
                Manifest.permission.WRITE_CALL_LOG)
    }

    @Throws(Exception::class)
    private fun testSelfWrite(uri: Uri, permission: String) {
        val context = createAttributionContext(ACCESSOR_ATTRIBUTION_TAG, null, null)
        val beginEndMillis = System.currentTimeMillis()
        context.contentResolver.insert(uri, ContentValues())
        val endTimeMillis = System.currentTimeMillis()

        assertNotRunningOpAccess(AppOpsManager.permissionToOp(permission)!!,
                beginEndMillis, endTimeMillis, context.attributionSource,
                /*accessorForeground*/ true, /*receiverForeground*/ false,
                /*accessorTrusted*/ true, /*accessorAccessCount*/ 1,
                /*receiverAccessCount*/ 0, /*checkAccessor*/ true,
                /*fromDatasource*/ false)
    }

    @Test
    @Throws(Exception::class)
    fun testUntrustedContactsAccessAttributeToAnother() {
        testUntrustedAccessAttributeToAnother(ContactsContract.Contacts.CONTENT_URI,
                Manifest.permission.READ_CONTACTS)
    }

    @Test
    @Throws(Exception::class)
    fun testUntrustedCalendarAccessAttributeToAnother() {
        testUntrustedAccessAttributeToAnother(CalendarContract.Calendars.CONTENT_URI,
                Manifest.permission.READ_CALENDAR)
    }

    @Test
    @Throws(Exception::class)
    fun testUntrustedSmsAccessAttributeToAnother() {
        assumeNotTv()
        assumeHasTelephony()
        testUntrustedAccessAttributeToAnother(Telephony.Sms.CONTENT_URI,
                Manifest.permission.READ_SMS)
    }

    @Test
    @Throws(Exception::class)
    fun testUntrustedCallLogAccessAttributeToAnother() {
        testUntrustedAccessAttributeToAnother(CallLog.Calls.CONTENT_URI,
                Manifest.permission.READ_CALL_LOG)
    }

    @Throws(Exception::class)
    private fun testUntrustedAccessAttributeToAnother(uri: Uri, permission: String) {
        val context = createAttributionContext(ACCESSOR_ATTRIBUTION_TAG,
                RECEIVER_PACKAGE_NAME, RECEIVER_ATTRIBUTION_TAG)
        val beginEndMillis = System.currentTimeMillis()
        context.contentResolver.query(uri, null, null, null)!!.close()
        val endTimeMillis = System.currentTimeMillis()

        assertNotRunningOpAccess(AppOpsManager.permissionToOp(permission)!!,
                beginEndMillis, endTimeMillis, context.attributionSource,
                /*accessorForeground*/ true, /*receiverForeground*/ false,
                /*accessorTrusted*/ false, /*accessorAccessCount*/ 1,
                /*receiverAccessCount*/ 1, /*checkAccessor*/ false,
                /*fromDatasource*/ false)
    }

    @Test
    @Throws(Exception::class)
    fun testUntrustedContactsAccessAttributeToAnotherThroughIntermediary() {
        testUntrustedAccessAttributeToAnotherThroughIntermediary(
                ContactsContract.Contacts.CONTENT_URI,
                Manifest.permission.READ_CONTACTS)
    }

    @Test
    @Throws(Exception::class)
    fun testUntrustedCalendarAccessAttributeToAnotherThroughIntermediary() {
        testUntrustedAccessAttributeToAnotherThroughIntermediary(
                CalendarContract.Calendars.CONTENT_URI,
                Manifest.permission.READ_CALENDAR)
    }

    @Test
    @Throws(Exception::class)
    fun testUntrustedSmsAccessAttributeToAnotherThroughIntermediary() {
        assumeNotTv()
        assumeHasTelephony()
        testUntrustedAccessAttributeToAnotherThroughIntermediary(
                Telephony.Sms.CONTENT_URI,
                Manifest.permission.READ_SMS)
    }

    @Test
    @Throws(Exception::class)
    fun testUntrustedCallLogAccessAttributeToAnotherThroughIntermediary() {
        testUntrustedAccessAttributeToAnotherThroughIntermediary(
                CallLog.Calls.CONTENT_URI,
                Manifest.permission.READ_CALL_LOG)
    }

    @Throws(Exception::class)
    private fun testUntrustedAccessAttributeToAnotherThroughIntermediary(
        uri: Uri,
        permission: String
    ) {
        runWithAuxiliaryApps {
            val nextAttributionSource = startBlamedAppActivity()

            val intermediaryContext = context.createContext(ContextParams.Builder()
                    .setNextAttributionSource(nextAttributionSource)
                    .setAttributionTag(ACCESSOR_ATTRIBUTION_TAG)
                    .build())

            val beginEndMillis = System.currentTimeMillis()
            intermediaryContext.contentResolver.query(uri, null, null, null)!!.close()
            val endTimeMillis = System.currentTimeMillis()

            // Assert first stage access
            assertNotRunningOpAccess(AppOpsManager.permissionToOp(permission)!!,
                    beginEndMillis, endTimeMillis, intermediaryContext.attributionSource,
                    /*accessorForeground*/ true, /*receiverForeground*/ true,
                    /*accessorTrusted*/ false, /*accessorAccessCount*/ 1,
                    /*receiverAccessCount*/ 1, /*checkAccessor*/ false,
                    /*fromDatasource*/ false)

            // Assert second stage access
            assertNotRunningOpAccess(AppOpsManager.permissionToOp(permission)!!,
                    beginEndMillis, endTimeMillis, nextAttributionSource,
                    /*accessorForeground*/ true, /*receiverForeground*/ false,
                    /*accessorTrusted*/ false, /*accessorAccessCount*/ 1,
                    /*receiverAccessCount*/ 1, /*checkAccessor*/ false,
                    /*fromDatasource*/ false)
        }
    }

    @Test(expected = SecurityException::class)
    fun testCannotForgeAttributionSource() {
        val receiverSource = AttributionSource(context
                .packageManager.getPackageUid(RECEIVER2_PACKAGE_NAME, 0),
                RECEIVER2_PACKAGE_NAME, RECEIVER2_ATTRIBUTION_TAG, null, AttributionSource(
                context.packageManager.getPackageUid(RECEIVER_PACKAGE_NAME, 0),
                RECEIVER_PACKAGE_NAME, RECEIVER_ATTRIBUTION_TAG))
        val intermediaryContext = context.createContext(ContextParams.Builder()
            .setNextAttributionSource(receiverSource)
            .setAttributionTag(ACCESSOR_ATTRIBUTION_TAG)
            .build())
        intermediaryContext.contentResolver.query(CallLog.Calls.CONTENT_URI, null,
                null, null)!!.close()
    }

    @Test(expected = SecurityException::class)
    fun testCannotAppendToForgeAttributionSource() {
        runWithAuxiliaryApps {
            val nextAttributionSource = startBlamedAppActivity()
            val untrustedAttributionSource = AttributionSource(context
                    .packageManager.getPackageUid(RECEIVER2_PACKAGE_NAME, 0),
                    RECEIVER2_PACKAGE_NAME, RECEIVER2_ATTRIBUTION_TAG, null,
                    nextAttributionSource)
            val intermediaryContext = context.createContext(ContextParams.Builder()
                    .setNextAttributionSource(untrustedAttributionSource)
                    .setAttributionTag(ACCESSOR_ATTRIBUTION_TAG)
                    .build())
            intermediaryContext.contentResolver.query(CallLog.Calls.CONTENT_URI, null,
                    null, null)!!.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTrustedAccessContactsAttributeToAnother() {
        testTrustedAccessAttributeToAnother(ContactsContract.Contacts.CONTENT_URI,
                Manifest.permission.READ_CONTACTS)
    }

    @Test
    @Throws(Exception::class)
    fun testTrustedAccessCalendarAttributeToAnother() {
        testTrustedAccessAttributeToAnother(CalendarContract.Calendars.CONTENT_URI,
                Manifest.permission.READ_CALENDAR)
    }

    @Test
    @Throws(Exception::class)
    fun testTrustedAccessSmsAttributeToAnother() {
        assumeNotTv()
        assumeHasTelephony()
        testTrustedAccessAttributeToAnother(Telephony.Sms.CONTENT_URI,
                Manifest.permission.READ_SMS)
    }

    @Test
    @Throws(Exception::class)
    fun testTrustedAccessCallLogAttributeToAnother() {
        testTrustedAccessAttributeToAnother(CallLog.Calls.CONTENT_URI,
                Manifest.permission.READ_CALL_LOG)
    }

    @Throws(Exception::class)
    private fun testTrustedAccessAttributeToAnother(uri: Uri, permission: String) {
        val context = createAttributionContext(ACCESSOR_ATTRIBUTION_TAG,
                RECEIVER_PACKAGE_NAME, RECEIVER_ATTRIBUTION_TAG)
        val beginEndMillis = System.currentTimeMillis()
        SystemUtil.runWithShellPermissionIdentity {
            context.contentResolver.query(uri, null, null, null)!!.close()
        }
        val endTimeMillis = System.currentTimeMillis()

        // Calculate the shellUid to account for running this from a secondary user.
        val shellUid = UserHandle.getUid(Process.myUserHandle().identifier,
            UserHandle.getAppId(Process.SHELL_UID))
        // Since we use adopt the shell permission identity we need to adjust
        // the permission identity to have the shell as the accessor.
        assertNotRunningOpAccess(AppOpsManager.permissionToOp(permission)!!,
                beginEndMillis, endTimeMillis, AttributionSource(shellUid,
                SHELL_PACKAGE_NAME, ACCESSOR_ATTRIBUTION_TAG, null,
                context.attributionSource.next),
                /*accessorForeground*/ false, /*receiverForeground*/ false,
                /*accessorTrusted*/ true, /*accessorAccessCount*/ 1,
                /*receiverAccessCount*/ 1, /*checkAccessor*/ false,
                /*fromDatasource*/ false)
    }

    @Test
    fun testGetAllPackagesForAllAppOps() {
        val appOpsManager = Companion.context.getSystemService(AppOpsManager::class.java)!!
        val result = SystemUtil.runWithShellPermissionIdentity<List<AppOpsManager.PackageOps>> {
            appOpsManager.getPackagesForOps(null as Array<String>?)
        }
        assertWithMessage("Expected at least one package").that(result.size).isAtLeast(1)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    @Throws(Exception::class)
    fun testMicRecognitionInjectRecoWithoutAttribution() {
        runWithAuxiliaryApps {
            startBlamedAppActivity()

            val context = createAttributionContext(ACCESSOR_ATTRIBUTION_TAG,
                    RECEIVER_PACKAGE_NAME, RECEIVER_ATTRIBUTION_TAG)

            val listener = mock(AppOpsManager.OnOpActiveChangedListener::class.java)
            val appopsManager = context.getSystemService(AppOpsManager::class.java)
            SystemUtil.runWithShellPermissionIdentity<Unit> {
                appopsManager!!.startWatchingActive(arrayOf(AppOpsManager.OPSTR_RECORD_AUDIO),
                        context.mainExecutor, listener)
            }

            val speechStartTime = System.currentTimeMillis()
            val recognizerRef = AtomicReference<SpeechRecognizer>()
            var currentOperationComplete = CountDownLatch(1)

            instrumentation.runOnMainSync {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context,
                        ComponentName(RECEIVER2_PACKAGE_NAME, RECOGNITION_SERVICE))

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {
                        currentOperationComplete.countDown()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    override fun onError(error: Int) {}
                    override fun onResults(results: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onEndOfSpeech() {}
                })

                val recoIntent = Intent()
                recoIntent.putExtra(OPERATION, OPERATION_INJECT_RECO_WITHOUT_ATTRIBUTION)
                recognizer.startListening(recoIntent)

                recognizerRef.set(recognizer)
            }

            try {
                currentOperationComplete.await(ASYNC_OPERATION_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS)

                val op = AppOpsManager.permissionToOp(Manifest.permission.RECORD_AUDIO)!!

                assertRunningOpAccess(op, speechStartTime, System.currentTimeMillis(),
                        AttributionSource(context.packageManager.getPackageUid(
                                RECEIVER2_PACKAGE_NAME, 0), RECEIVER2_PACKAGE_NAME,
                                /*attributionTag*/ null, null, context.attributionSource),
                        /*accessorForeground*/ true, /*receiverForeground*/ true,
                        /*accessorTrusted*/ false, /*accessorAccessCount*/ 1,
                        /*receiverAccessCount*/ 1, /*checkAccessor*/ true,
                        /*fromDatasource*/ false)

                assertRunningOpAccess(op, speechStartTime, System.currentTimeMillis(),
                        context.attributionSource, /*accessorForeground*/ true,
                        /*receiverForeground*/ true, /*accessorTrusted*/ false,
                        /*accessorAccessCount*/ 0, /*receiverAccessCount*/ 1,
                        /*checkAccessor*/ false, /*fromDatasource*/ false)

                // Finish recon and check if all ops are finished
                currentOperationComplete = CountDownLatch(1)
                instrumentation.runOnMainSync { recognizerRef.get().cancel() }
                currentOperationComplete.await(ASYNC_OPERATION_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS)

                val recognizerUid = context.packageManager.getPackageUid(
                        RECEIVER2_PACKAGE_NAME, 0)

                assertNotRunningOpAccess(op, speechStartTime, System.currentTimeMillis(),
                        AttributionSource(recognizerUid, RECEIVER2_PACKAGE_NAME,
                                /*attributionTag*/ null, null, context.attributionSource),
                        /*accessorForeground*/ true, /*receiverForeground*/ true,
                        /*accessorTrusted*/ false, /*accessorAccessCount*/ 1,
                        /*receiverAccessCount*/ 1, /*checkAccessor*/ true,
                        /*fromDatasource*/ false)

                assertNotRunningOpAccess(op, speechStartTime, System.currentTimeMillis(),
                        context.attributionSource, /*accessorForeground*/ true,
                        /*receiverForeground*/ true, /*accessorTrusted*/ false,
                        /*accessorAccessCount*/ 0, /*receiverAccessCount*/ 1,
                        /*checkAccessor*/ false, /*fromDatasource*/ false)

                var attributionChainId: Int? = null
                val inOrder = inOrder(listener)
                val attributionChainIdMatcher = ArgumentMatcher<Int> {
                    if (attributionChainId == null) {
                        attributionChainId = it
                        return@ArgumentMatcher true
                    } else {
                        return@ArgumentMatcher (attributionChainId == it)
                    }
                }
                val receiverUid = context.packageManager.getPackageUid(
                        RECEIVER_PACKAGE_NAME, 0)

                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(recognizerUid), eq(RECEIVER2_PACKAGE_NAME), isNull(),
                    eq(Context.DEVICE_ID_DEFAULT), eq(true),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_ACCESSOR),
                    intThat(attributionChainIdMatcher))
                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(Process.myUid()), eq(context.packageName), eq(ACCESSOR_ATTRIBUTION_TAG),
                    eq(Context.DEVICE_ID_DEFAULT), eq(true),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_INTERMEDIARY),
                    intThat(attributionChainIdMatcher))
                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(receiverUid), eq(RECEIVER_PACKAGE_NAME), eq(RECEIVER_ATTRIBUTION_TAG),
                    eq(Context.DEVICE_ID_DEFAULT), eq(true),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_RECEIVER),
                    intThat(attributionChainIdMatcher))

                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(recognizerUid), eq(RECEIVER2_PACKAGE_NAME), isNull(),
                    eq(Context.DEVICE_ID_DEFAULT), eq(false),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_ACCESSOR),
                    intThat(attributionChainIdMatcher))
                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(Process.myUid()), eq(context.packageName), eq(ACCESSOR_ATTRIBUTION_TAG),
                    eq(Context.DEVICE_ID_DEFAULT), eq(false),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_INTERMEDIARY),
                    intThat(attributionChainIdMatcher))
                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(receiverUid), eq(RECEIVER_PACKAGE_NAME), eq(RECEIVER_ATTRIBUTION_TAG),
                    eq(Context.DEVICE_ID_DEFAULT), eq(false),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_RECEIVER),
                    intThat(attributionChainIdMatcher))
            } finally {
                // Take down the recognition service
                instrumentation.runOnMainSync { recognizerRef.get().destroy() }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    @Throws(Exception::class)
    fun testMicRecognitionMicRecoWithAttribution() {
        runWithAuxiliaryApps {
            startBlamedAppActivity()

            val context = createAttributionContext(ACCESSOR_ATTRIBUTION_TAG,
                    RECEIVER_PACKAGE_NAME, RECEIVER_ATTRIBUTION_TAG)

            val listener = mock(AppOpsManager.OnOpActiveChangedListener::class.java)
            val appopsManager = context.getSystemService(AppOpsManager::class.java)
            SystemUtil.runWithShellPermissionIdentity<Unit> {
                appopsManager!!.startWatchingActive(arrayOf(AppOpsManager.OPSTR_RECORD_AUDIO),
                        context.mainExecutor, listener)
            }

            val speechStartTime = System.currentTimeMillis()
            val recognizerRef = AtomicReference<SpeechRecognizer>()
            var currentOperationComplete = CountDownLatch(1)

            instrumentation.runOnMainSync {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context,
                        ComponentName(RECEIVER2_PACKAGE_NAME, RECOGNITION_SERVICE))

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {
                        currentOperationComplete.countDown()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    override fun onError(error: Int) {}
                    override fun onResults(results: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onEndOfSpeech() {}
                })

                val recoIntent = Intent()
                recoIntent.putExtra(OPERATION, OPERATION_MIC_RECO_WITH_ATTRIBUTION)
                recognizer.startListening(recoIntent)

                recognizerRef.set(recognizer)
            }

            try {
                currentOperationComplete.await(ASYNC_OPERATION_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS)

                val op = AppOpsManager.permissionToOp(Manifest.permission.RECORD_AUDIO)!!

                assertRunningOpAccess(op, speechStartTime, System.currentTimeMillis(),
                        AttributionSource(context.packageManager.getPackageUid(
                                RECEIVER2_PACKAGE_NAME, 0), RECEIVER2_PACKAGE_NAME,
                                /*attributionTag*/ null, null, context.attributionSource),
                        /*accessorForeground*/ true, /*receiverForeground*/ true,
                        /*accessorTrusted*/ true, /*accessorAccessCount*/ 1,
                        /*receiverAccessCount*/ 1, /*checkAccessor*/ true,
                        /*fromDatasource*/ true)

                assertRunningOpAccess(op, speechStartTime, System.currentTimeMillis(),
                        context.attributionSource, /*accessorForeground*/ true,
                        /*receiverForeground*/ true, /*accessorTrusted*/ true,
                        /*accessorAccessCount*/ 0, /*receiverAccessCount*/ 1,
                        /*checkAccessor*/ false, /*fromDatasource*/ true)

                // Finish recon and check if all ops are finished
                currentOperationComplete = CountDownLatch(1)
                instrumentation.runOnMainSync { recognizerRef.get().cancel() }
                currentOperationComplete.await(ASYNC_OPERATION_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS)

                val recognizerUid = context.packageManager.getPackageUid(
                        RECEIVER2_PACKAGE_NAME, 0)

                assertNotRunningOpAccess(op, speechStartTime, System.currentTimeMillis(),
                        AttributionSource(recognizerUid, RECEIVER2_PACKAGE_NAME,
                                /*attributionTag*/ null, null, context.attributionSource),
                        /*accessorForeground*/ true, /*receiverForeground*/ true,
                        /*accessorTrusted*/ true, /*accessorAccessCount*/ 1,
                        /*receiverAccessCount*/ 1, /*checkAccessor*/ true,
                        /*fromDatasource*/ true)

                assertNotRunningOpAccess(op, speechStartTime, System.currentTimeMillis(),
                        context.attributionSource, /*accessorForeground*/ true,
                        /*receiverForeground*/ true, /*accessorTrusted*/ true,
                        /*accessorAccessCount*/ 0, /*receiverAccessCount*/ 1,
                        /*checkAccessor*/ false, /*fromDatasource*/ true)

                var attributionChainId: Int? = null
                val inOrder = inOrder(listener)
                val attributionChainIdMatcher = ArgumentMatcher<Int> {
                    if (attributionChainId == null) {
                        attributionChainId = it
                        return@ArgumentMatcher true
                    } else {
                        return@ArgumentMatcher (attributionChainId == it)
                    }
                }
                val receiverUid = context.packageManager.getPackageUid(
                        RECEIVER_PACKAGE_NAME, 0)

                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(recognizerUid), eq(RECEIVER2_PACKAGE_NAME), isNull(),
                    eq(Context.DEVICE_ID_DEFAULT), eq(true),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_ACCESSOR or ATTRIBUTION_FLAG_TRUSTED),
                    intThat(attributionChainIdMatcher))
                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(Process.myUid()), eq(context.packageName), eq(ACCESSOR_ATTRIBUTION_TAG),
                    eq(Context.DEVICE_ID_DEFAULT), eq(true),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_INTERMEDIARY or
                    ATTRIBUTION_FLAG_TRUSTED), intThat(attributionChainIdMatcher))
                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(receiverUid), eq(RECEIVER_PACKAGE_NAME), eq(RECEIVER_ATTRIBUTION_TAG),
                    eq(Context.DEVICE_ID_DEFAULT), eq(true),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_RECEIVER or
                    ATTRIBUTION_FLAG_TRUSTED), intThat(attributionChainIdMatcher))

                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(recognizerUid), eq(RECEIVER2_PACKAGE_NAME), isNull(),
                    eq(Context.DEVICE_ID_DEFAULT), eq(false),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_ACCESSOR or ATTRIBUTION_FLAG_TRUSTED),
                    intThat(attributionChainIdMatcher))
                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(Process.myUid()), eq(context.packageName), eq(ACCESSOR_ATTRIBUTION_TAG),
                    eq(Context.DEVICE_ID_DEFAULT), eq(false),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_INTERMEDIARY or
                    ATTRIBUTION_FLAG_TRUSTED), intThat(attributionChainIdMatcher))
                inOrder.verify(listener).onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                    eq(receiverUid), eq(RECEIVER_PACKAGE_NAME), eq(RECEIVER_ATTRIBUTION_TAG),
                    eq(Context.DEVICE_ID_DEFAULT), eq(false),
                    eq(AppOpsManager.ATTRIBUTION_FLAG_RECEIVER or
                    ATTRIBUTION_FLAG_TRUSTED), intThat(attributionChainIdMatcher))
            } finally {
                // Take down the recognition service
                instrumentation.runOnMainSync { recognizerRef.get().destroy() }
            }
        }
    }

    fun runWithAuxiliaryApps(worker: () -> Unit) {
        ensureAuxiliaryAppsNotRunningAndNoResidualProcessState()
        try {
            worker.invoke()
        } finally {
            ensureAuxiliaryAppsNotRunningAndNoResidualProcessState()
        }
    }

    companion object {
        private const val ASYNC_OPERATION_TIMEOUT_MILLIS: Long = 5000 // 5 sec
        private const val INTERVAL_COMPRESSION_MULTIPLIER = 10
        private const val SNAPSHOT_INTERVAL_MILLIS: Long = 1000

        val SHELL_PACKAGE_NAME = "com.android.shell"
        val RECEIVER_PACKAGE_NAME = "android.attributionsource.cts.blamed"
        val BRING_TO_FOREGROUND_ACTIVITY =
                "android.attributionsource.cts.blamed.BringToForegroundActivity"
        val RECOGNITION_SERVICE = "android.attributionsource.cts.blamed2.MyRecognitionService"
        val REMOTE_CALLBACK = "remote_callback"
        val ATTRIBUTION_SOURCE = "attribution_source"
        val ACCESSOR_ATTRIBUTION_TAG = "accessor_attribution_tag"
        val RECEIVER2_PACKAGE_NAME = "android.attributionsource.cts.blamed2"
        val RECEIVER_ATTRIBUTION_TAG = "receiver_attribution_tag"
        val RECEIVER2_ATTRIBUTION_TAG = "receiver2_attribution_tag"

        val OPERATION = "operation"
        val OPERATION_MIC_RECO_WITH_ATTRIBUTION = "operation:mic_reco_with_attribution"
        val OPERATION_INJECT_RECO_WITHOUT_ATTRIBUTION = "operation:inject_reco_without_attribution"

        val ATTRIBUTION_FLAG_TRUSTED = 0x8

        private val context: Context
            get() = InstrumentationRegistry.getInstrumentation().getContext()

        private val instrumentation: Instrumentation
            get() = InstrumentationRegistry.getInstrumentation()

        private val isTv = context.packageManager.hasSystemFeature(FEATURE_LEANBACK)
        private val isTel = context.packageManager.hasSystemFeature(FEATURE_TELEPHONY)

        fun ensureAuxiliaryAppsNotRunningAndNoResidualProcessState() {
            SystemUtil.runShellCommand("am force-stop $RECEIVER_PACKAGE_NAME")
            SystemUtil.runShellCommand("am force-stop $RECEIVER2_PACKAGE_NAME")
            SystemClock.sleep(ASYNC_OPERATION_TIMEOUT_MILLIS)
        }

        @Throws(Exception::class)
        private fun assertRunningOpAccess(
            op: String,
            beginEndMillis: Long,
            endTimeMillis: Long,
            attributionSource: AttributionSource,
            accessorForeground: Boolean,
            receiverForeground: Boolean,
            accessorTrusted: Boolean,
            accessorAccessCount: Int,
            receiverAccessCount: Int,
            checkAccessor: Boolean,
            fromDatasource: Boolean
        ) {
            assertOpAccess(op, beginEndMillis, endTimeMillis, attributionSource,
                    accessorForeground, receiverForeground, accessorTrusted,
                    /*assertRunning*/ true, accessorAccessCount, receiverAccessCount,
                    checkAccessor, fromDatasource)
        }

        @Throws(Exception::class)
        private fun assertNotRunningOpAccess(
            op: String,
            beginEndMillis: Long,
            endTimeMillis: Long,
            attributionSource: AttributionSource,
            accessorForeground: Boolean,
            receiverForeground: Boolean,
            accessorTrusted: Boolean,
            accessorAccessCount: Int,
            receiverAccessCount: Int,
            checkAccessor: Boolean,
            fromDatasource: Boolean
        ) {
            assertOpAccess(op, beginEndMillis, endTimeMillis, attributionSource,
                    accessorForeground, receiverForeground, accessorTrusted,
                    /*assertRunning*/ false, accessorAccessCount, receiverAccessCount,
                    checkAccessor, fromDatasource)
        }

        @Throws(Exception::class)
        private fun assertOpAccess(
            op: String,
            beginEndMillis: Long,
            endTimeMillis: Long,
            attributionSource: AttributionSource,
            accessorForeground: Boolean,
            receiverForeground: Boolean,
            accessorTrusted: Boolean,
            assertRunning: Boolean,
            accessorAccessCount: Int,
            receiverAccessCount: Int,
            checkAccessor: Boolean,
            fromDatasource: Boolean
        ) {
            assertLastOpAccess(op, beginEndMillis, endTimeMillis, attributionSource,
                    accessorForeground, receiverForeground, accessorTrusted, assertRunning,
                    checkAccessor, fromDatasource)
            assertHistoricalOpAccess(op, attributionSource, accessorForeground,
                    receiverForeground, accessorTrusted, accessorAccessCount, receiverAccessCount,
                    checkAccessor, fromDatasource)
        }

        private fun assertLastOpAccess(
            op: String,
            beginEndMillis: Long,
            endTimeMillis: Long,
            attributionSource: AttributionSource,
            accessorForeground: Boolean,
            receiverForeground: Boolean,
            accessorTrusted: Boolean,
            assertRunning: Boolean,
            checkAccessor: Boolean,
            fromDatasource: Boolean
        ) {
            val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
            val allPackagesOps: MutableList<AppOpsManager.PackageOps?> = ArrayList()
            SystemUtil.runWithShellPermissionIdentity<Boolean> {
                allPackagesOps.addAll(appOpsManager.getPackagesForOps(arrayOf(op)))
            }
            if (checkAccessor) {
                assertLastAccessorOps(op, beginEndMillis, endTimeMillis, attributionSource,
                        accessorForeground, accessorTrusted, assertRunning, fromDatasource,
                        allPackagesOps)
            } else {
                assertNotLastAccessorOps(op, attributionSource, allPackagesOps)
            }
            if (attributionSource.next != null) {
                assertLastReceiverOps(op, beginEndMillis, endTimeMillis, attributionSource,
                        receiverForeground, accessorTrusted, assertRunning, allPackagesOps)
            }
        }

        @Throws(Exception::class)
        private fun assertHistoricalOpAccess(
            op: String,
            attributionSource: AttributionSource,
            accessorForeground: Boolean,
            receiverForeground: Boolean,
            accessorTrusted: Boolean,
            accessorAccessCount: Int,
            receiverAccessCount: Int,
            checkAccessor: Boolean,
            fromDatasource: Boolean
        ) {
            val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
            val request = AppOpsManager.HistoricalOpsRequest.Builder(0, Long.MAX_VALUE)
                    .setOpNames(listOf(op))
                    .build()
            val historicalOpsRef = AtomicReference<AppOpsManager.HistoricalOps>()
            val lock = ReentrantLock()
            val condition = lock.newCondition()
            SystemUtil.runWithShellPermissionIdentity {
                appOpsManager.getHistoricalOps(request, context.mainExecutor,
                        Consumer { historicalOps: AppOpsManager.HistoricalOps ->
                            historicalOpsRef.set(historicalOps)
                            lock.lock()
                            try {
                                condition.signalAll()
                            } finally {
                                lock.unlock()
                            }
                        })
            }
            lock.lock()
            try {
                condition.await(ASYNC_OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } finally {
                lock.unlock()
            }

            val historicalOps = historicalOpsRef.get()
            if (checkAccessor) {
                assertHistoricalAccessorOps(op, attributionSource, accessorForeground,
                        accessorTrusted, fromDatasource, accessorAccessCount, historicalOps)
            } else {
                assertNoHistoricalAccessorOps(op, attributionSource, historicalOps)
            }
            if (attributionSource.next != null) {
                assertHistoricalReceiverOps(op, attributionSource, receiverForeground,
                        accessorTrusted, receiverAccessCount, historicalOps)
            }
        }

        private fun assertLastAccessorOps(
            op: String,
            beginEndMillis: Long,
            endTimeMillis: Long,
            attributionSource: AttributionSource,
            accessorForeground: Boolean,
            accessorTrusted: Boolean,
            assertRunning: Boolean,
            fromDatasource: Boolean,
            allPackagesOps: List<AppOpsManager.PackageOps?>
        ) {
            val accessorPackageOps = findPackageOps(attributionSource.uid,
                    attributionSource.packageName!!, allPackagesOps)
            for (opEntry in accessorPackageOps!!.ops) {
                if (!op.equals(opEntry.opStr)) {
                    continue
                }
                val attributedOpEntry = opEntry.attributedOpEntries[
                        attributionSource.attributionTag]
                if (attributionSource.next == null) {
                    // Access for ourselves
                    assertLastAccessInRange(attributedOpEntry!!, beginEndMillis, endTimeMillis,
                            AppOpsManager.OP_FLAG_SELF, accessorForeground, assertRunning)
                } else if (accessorTrusted) {
                    // Access for others and we are trusted. If we got the data from a datasource
                    // the latter is the proxy and we proxied, otherwise we are the proxy.
                    if (fromDatasource) {
                        assertLastAccessInRange(attributedOpEntry!!, beginEndMillis, endTimeMillis,
                                AppOpsManager.OP_FLAG_TRUSTED_PROXIED, accessorForeground,
                                assertRunning)
                    } else {
                        assertLastAccessInRange(attributedOpEntry!!, beginEndMillis, endTimeMillis,
                                AppOpsManager.OP_FLAG_TRUSTED_PROXY, accessorForeground,
                                assertRunning)
                    }
                } else {
                    // Access for others and we are not trusted.
                    assertLastAccessInRange(attributedOpEntry!!, beginEndMillis, endTimeMillis,
                            AppOpsManager.OP_FLAG_UNTRUSTED_PROXY, accessorForeground,
                            assertRunning)
                }
            }
        }

        private fun assertNotLastAccessorOps(
            op: String,
            attributionSource: AttributionSource,
            allPackagesOps: List<AppOpsManager.PackageOps?>
        ) {
            val accessorPackageOps = findPackageOps(attributionSource.uid,
                    attributionSource.packageName!!, allPackagesOps) ?: return
            for (opEntry in accessorPackageOps.ops) {
                if (!op.equals(opEntry.opStr)) {
                    continue
                }
                val attributedOpEntry = opEntry.attributedOpEntries[
                        attributionSource.attributionTag]
                if (attributedOpEntry != null) {
                    assertWithMessage("Found background accessor op, did not expect to")
                        .that(attributedOpEntry.getLastAccessBackgroundTime(
                            AppOpsManager.OP_FLAG_SELF
                                or AppOpsManager.OP_FLAG_UNTRUSTED_PROXY
                                or AppOpsManager.OP_FLAG_TRUSTED_PROXY)).isEqualTo(-1)
                    assertWithMessage("Found foreground accessor op, did not expect to")
                        .that(attributedOpEntry.getLastAccessForegroundTime(
                            AppOpsManager.OP_FLAG_SELF
                                or AppOpsManager.OP_FLAG_UNTRUSTED_PROXY
                                or AppOpsManager.OP_FLAG_TRUSTED_PROXY)).isEqualTo(-1)
                }
            }
        }

        private fun assertHistoricalAccessorOps(
            op: String,
            attributionSource: AttributionSource,
            accessorForeground: Boolean,
            accessorTrusted: Boolean,
            fromDatasource: Boolean,
            assertedAccessCount: Int,
            historicalOps: AppOpsManager.HistoricalOps
        ) {
            val accessorPackageOps = findPackageOps(
                    attributionSource.uid, attributionSource.packageName!!,
                    historicalOps)
            val attributedPackageOps = accessorPackageOps?.getAttributedOps(
                    attributionSource.attributionTag)

            val attributedPackageOp = attributedPackageOps!!.getOp(op)
            if (attributionSource.next == null) {
                // Access for ourselves
                assertAccessCount(attributedPackageOp!!, AppOpsManager.OP_FLAG_SELF,
                        accessorForeground, assertedAccessCount)
            } else if (accessorTrusted) {
                // Access for others and we are trusted. If we got the data from a datasource it
                // would blame the accessor in a trusted way
                if (fromDatasource) {
                    assertAccessCount(attributedPackageOp!!, AppOpsManager.OP_FLAG_TRUSTED_PROXIED,
                            accessorForeground, assertedAccessCount)
                } else {
                    assertAccessCount(attributedPackageOp!!, AppOpsManager.OP_FLAG_TRUSTED_PROXY,
                            accessorForeground, assertedAccessCount)
                }
            } else {
                // Access for others and we are not trusted
                assertAccessCount(attributedPackageOp!!, AppOpsManager.OP_FLAG_UNTRUSTED_PROXY,
                        accessorForeground, assertedAccessCount)
            }
        }

        private fun assertNoHistoricalAccessorOps(
            op: String,
            attributionSource: AttributionSource,
            historicalOps: AppOpsManager.HistoricalOps
        ) {
            val accessorPackageOps = findPackageOps(
                    attributionSource.uid, attributionSource.packageName!!,
                    historicalOps)
            val attributedPackageOps = accessorPackageOps?.getAttributedOps(
                    attributionSource.attributionTag) ?: return
            val attributedPackageOp = attributedPackageOps.getOp(op)
            if (attributedPackageOp != null) {
                assertWithMessage("Found background historical accessor access, did not expect to")
                    .that(attributedPackageOp.getBackgroundAccessCount(
                        AppOpsManager.OP_FLAG_SELF
                            or AppOpsManager.OP_FLAG_UNTRUSTED_PROXY
                            or AppOpsManager.OP_FLAG_TRUSTED_PROXY)).isEqualTo(0)
                assertWithMessage("Found foreground historical accessor access, did not expect to")
                    .that(attributedPackageOp.getForegroundAccessCount(
                        AppOpsManager.OP_FLAG_SELF
                            or AppOpsManager.OP_FLAG_UNTRUSTED_PROXY
                            or AppOpsManager.OP_FLAG_TRUSTED_PROXY)).isEqualTo(0)
            }
        }

        private fun assertLastReceiverOps(
            op: String,
            beginTimeMillis: Long,
            endTimeMillis: Long,
            attributionSource: AttributionSource,
            receiverForeground: Boolean,
            accessorTrusted: Boolean,
            assertRunning: Boolean,
            allPackagesOps: List<AppOpsManager.PackageOps?>
        ) {
            val receiverPackageOps = findPackageOps(
                    attributionSource.next!!.uid,
                    attributionSource.next!!.packageName!!,
                    allPackagesOps)
            for (opEntry in receiverPackageOps!!.ops) {
                if (op != opEntry.opStr) {
                    continue
                }
                val attributedOpEntry = opEntry.attributedOpEntries[
                        attributionSource.next!!.attributionTag]
                val opProxyInfo: AppOpsManager.OpEventProxyInfo?
                opProxyInfo = if (accessorTrusted) {
                    // Received from a trusted accessor. If we got the data from a datasource it
                    // would blame the accessor in a trusted way
                    assertLastAccessInRange(attributedOpEntry!!, beginTimeMillis, endTimeMillis,
                            AppOpsManager.OP_FLAG_TRUSTED_PROXIED, receiverForeground,
                            assertRunning)
                    attributedOpEntry.getLastProxyInfo(AppOpsManager.OP_FLAG_TRUSTED_PROXIED)
                } else {
                    // Received from an untrusted accessor
                    assertLastAccessInRange(attributedOpEntry!!, beginTimeMillis, endTimeMillis,
                            AppOpsManager.OP_FLAG_UNTRUSTED_PROXIED, receiverForeground,
                            assertRunning)
                    attributedOpEntry.getLastProxyInfo(
                            AppOpsManager.OP_FLAG_UNTRUSTED_PROXIED)
                }
                assertWithMessage("proxy uid is not equal to expected")
                    .that(opProxyInfo!!.uid).isEqualTo(attributionSource.uid)
                assertWithMessage("proxy package name is not equal to expected")
                    .that(opProxyInfo.packageName).isEqualTo(attributionSource.packageName)

                /* Fix made to b/304983146 treats the attribution coming from shell as invalid
                 because it will not exist in shell package. Hence this change is to
                 validate them as null instead of actual values
                */
                if (attributionSource.packageName == SHELL_PACKAGE_NAME) {
                    assertWithMessage("proxy attribution tag is expected to be null")
                            .that(opProxyInfo.attributionTag).isNull()
                } else {
                    assertWithMessage("proxy attribution tag is not equal to expected")
                            .that(opProxyInfo.attributionTag)
                            .isEqualTo(attributionSource.attributionTag)
                }
            }
        }

        private fun assertHistoricalReceiverOps(
            op: String,
            attributionSource: AttributionSource,
            receiverForeground: Boolean,
            accessorTrusted: Boolean,
            assertedAccessCount: Int,
            historicalOps: AppOpsManager.HistoricalOps
        ) {
            val accessorPackageOps = findPackageOps(
                    attributionSource.next!!.uid,
                    attributionSource.next!!.packageName!!,
                    historicalOps)
            val attributedPackageOps = accessorPackageOps?.getAttributedOps(
                    attributionSource.next!!.attributionTag!!)
            val attributedPackageOp = attributedPackageOps!!.getOp(op)
            if (accessorTrusted) {
                // Received from a trusted accessor.
                    assertAccessCount(attributedPackageOp!!, AppOpsManager.OP_FLAG_TRUSTED_PROXIED,
                            receiverForeground, assertedAccessCount)
            } else {
                // Received from an untrusted accessor
                assertAccessCount(attributedPackageOp!!, AppOpsManager.OP_FLAG_UNTRUSTED_PROXIED,
                        receiverForeground, assertedAccessCount)
            }
        }

        private fun assertLastAccessInRange(
            opEntry: AppOpsManager.AttributedOpEntry,
            beginTimeMillis: Long,
            endTimeMillis: Long,
            assertedFlag: Int,
            assertForeground: Boolean,
            assertRunning: Boolean
        ) {
            assertWithMessage("Unexpected op running state")
                .that(opEntry.isRunning).isEqualTo(assertRunning)
            assertTimeInRangeIfRequired(opEntry, assertedFlag,
                    AppOpsManager.OP_FLAG_SELF,
                    assertForeground, beginTimeMillis, endTimeMillis)
            assertTimeInRangeIfRequired(opEntry, assertedFlag,
                    AppOpsManager.OP_FLAG_TRUSTED_PROXY,
                    assertForeground, beginTimeMillis, endTimeMillis)
            assertTimeInRangeIfRequired(opEntry, assertedFlag,
                    AppOpsManager.OP_FLAG_UNTRUSTED_PROXY,
                    assertForeground, beginTimeMillis, endTimeMillis)
            assertTimeInRangeIfRequired(opEntry, assertedFlag,
                    AppOpsManager.OP_FLAG_TRUSTED_PROXIED,
                    assertForeground, beginTimeMillis, endTimeMillis)
            assertTimeInRangeIfRequired(opEntry, assertedFlag,
                    AppOpsManager.OP_FLAG_UNTRUSTED_PROXIED,
                    assertForeground, beginTimeMillis, endTimeMillis)
            if (assertForeground) {
                assertWithMessage("Did not expect to find a background access").that(
                    opEntry.getLastAccessBackgroundTime(AppOpsManager.OP_FLAGS_ALL)).isEqualTo(-1)
            } else {
                assertWithMessage("Did not expect to find a foreground access").that(
                    opEntry.getLastAccessForegroundTime(AppOpsManager.OP_FLAGS_ALL)).isEqualTo(-1)
            }
        }

        private fun assertTimeInRangeIfRequired(
            opEntry: AppOpsManager.AttributedOpEntry,
            assertedFlag: Int,
            accessedFlag: Int,
            assertForeground: Boolean,
            beginTimeMillis: Long,
            endTimeMillis: Long
        ) {
            if (assertedFlag != accessedFlag) {
                return
            }
            val accessTime: Long
            accessTime = if (assertForeground) {
                opEntry.getLastAccessForegroundTime(accessedFlag)
            } else {
                opEntry.getLastAccessBackgroundTime(accessedFlag)
            }
            val typeString = if (assertForeground) "foreground" else "background"
            assertWithMessage("$typeString access time $accessTime is not after $beginTimeMillis")
                    .that(accessTime).isAtLeast(beginTimeMillis)
            assertWithMessage("$typeString access time $accessTime is not at before $endTimeMillis")
                    .that(accessTime).isAtMost(endTimeMillis)
        }

        private fun assertAccessCount(
            historicalOp: AppOpsManager.HistoricalOp,
            assertedFlag: Int,
            assertForeground: Boolean,
            assertedAccessCount: Int
        ) {
            assertAccessCountIfRequired(historicalOp, AppOpsManager.OP_FLAG_SELF,
                    assertedFlag, assertForeground, assertedAccessCount)
            assertAccessCountIfRequired(historicalOp, AppOpsManager.OP_FLAG_TRUSTED_PROXY,
                    assertedFlag, assertForeground, assertedAccessCount)
            assertAccessCountIfRequired(historicalOp, AppOpsManager.OP_FLAG_UNTRUSTED_PROXY,
                    assertedFlag, assertForeground, assertedAccessCount)
            assertAccessCountIfRequired(historicalOp, AppOpsManager.OP_FLAG_TRUSTED_PROXIED,
                    assertedFlag, assertForeground, assertedAccessCount)
            assertAccessCountIfRequired(historicalOp, AppOpsManager.OP_FLAG_UNTRUSTED_PROXIED,
                    assertedFlag, assertForeground, assertedAccessCount)
            if (assertForeground) {
                assertWithMessage("Did not expect to find a background access").that(
                    historicalOp.getBackgroundAccessCount(AppOpsManager.OP_FLAGS_ALL)).isEqualTo(0)
            } else {
                assertWithMessage("Did not expect to find a foreground access").that(
                    historicalOp.getForegroundAccessCount(AppOpsManager.OP_FLAGS_ALL)).isEqualTo(0)
            }
        }

        private fun assertAccessCountIfRequired(
            historicalOp: AppOpsManager.HistoricalOp,
            assertedFlag: Int,
            accessedFlag: Int,
            assertForeground: Boolean,
            assertedAccessCount: Int
        ) {
            if (assertedFlag != accessedFlag) {
                return
            }
            val accessCount: Long
            accessCount = if (assertForeground) {
                historicalOp.getForegroundAccessCount(accessedFlag)
            } else {
                historicalOp.getBackgroundAccessCount(accessedFlag)
            }
            assertWithMessage("Found incorrect number of accesses")
                .that(accessCount).isEqualTo(assertedAccessCount)
        }

        private fun findPackageOps(
            uid: Int,
            packageName: String,
            searchedList: List<AppOpsManager.PackageOps?>
        ): AppOpsManager.PackageOps? {
            return searchedList.stream()
                    .filter { packageOps: AppOpsManager.PackageOps? ->
                        packageOps!!.uid == uid && packageOps.packageName == packageName
                    }
                    .findAny()
                    .orElse(null)
        }

        private fun findPackageOps(
            uid: Int,
            packageName: String,
            historicalOps: AppOpsManager.HistoricalOps
        ): AppOpsManager.HistoricalPackageOps? {
            val uidOps = historicalOps.getUidOps(uid)
            return uidOps?.getPackageOps(packageName)
        }

        fun createAttributionContext(
            attributionTag: String?,
            receiverPackageName: String?,
            receiverAttributionTag: String?
        ): Context {
            val attributionParamsBuilder = ContextParams.Builder()
            if (attributionTag != null) {
                attributionParamsBuilder.setAttributionTag(attributionTag)
            }
            if (receiverPackageName != null) {
                val attributionSourceBuilder = AttributionSource.Builder(
                        context.packageManager.getPackageUid(receiverPackageName, 0))
                attributionSourceBuilder.setPackageName(receiverPackageName)
                if (receiverAttributionTag != null) {
                    attributionSourceBuilder.setAttributionTag(receiverAttributionTag)
                }
                var receiverAttributionSource = attributionSourceBuilder.build()
                SystemUtil.runWithShellPermissionIdentity {
                    receiverAttributionSource = context.getSystemService(
                            PermissionManager::class.java)!!.registerAttributionSource(
                            receiverAttributionSource)
                }
                attributionParamsBuilder.setNextAttributionSource(receiverAttributionSource)
            }
            return context.createContext(attributionParamsBuilder.build())
        }

        fun startBlamedAppActivity(): AttributionSource {
            val activityStatedLatch = CountDownLatch(1)
            val attributionSourceRef = AtomicReference<AttributionSource>()
            val intent = Intent()
            intent.setClassName(RECEIVER_PACKAGE_NAME, BRING_TO_FOREGROUND_ACTIVITY)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(REMOTE_CALLBACK, RemoteCallback {
                attributionSourceRef.set(it?.getParcelable(ATTRIBUTION_SOURCE))
                activityStatedLatch.countDown()
            })
            context.startActivity(intent)
            activityStatedLatch.await(ASYNC_OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            return attributionSourceRef.get()
        }

        private fun assumeNotTv() = assumeFalse(isTv)
        private fun assumeHasTelephony() = assumeTrue(isTel)
    }
}
