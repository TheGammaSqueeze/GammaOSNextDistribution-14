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

package com.android.captiveportallogin

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.ServiceTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.captiveportallogin.DownloadService.DOWNLOAD_ABORTED_REASON_FILE_TOO_LARGE
import com.android.captiveportallogin.DownloadService.DownloadServiceBinder
import com.android.captiveportallogin.DownloadService.ProgressCallback
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify

private val TEST_FILESIZE = 1_000_000 // 1MB
private val TEST_USERAGENT = "Test UserAgent"
private val TEST_URL = "https://test.download.example.com/myfile"
private val NOTIFICATION_SHADE_TYPE = "com.android.systemui:id/notification_stack_scroller"

// Test text file registered in the test manifest to be opened by a test activity
private val TEST_TEXT_FILE_EXTENSION = "testtxtfile"
private val TEST_TEXT_FILE_TYPE = "text/vnd.captiveportallogin.testtxtfile"

private val TEST_TIMEOUT_MS = 10_000L
// Timeout for notifications before trying to find it via scrolling
private val NOTIFICATION_NO_SCROLL_TIMEOUT_MS = 1000L

// Maximum number of scrolls from the top to attempt to find notifications in the notification shade
private val NOTIFICATION_SCROLL_COUNT = 30
// Swipe in a vertically centered area of 20% of the screen height (40% margin
// top/down): small swipes on notifications avoid dismissing the notification shade
private val NOTIFICATION_SCROLL_DEAD_ZONE_PERCENT = .4
// Steps for each scroll in the notification shade (controls the scrolling speed).
// Each scroll is a series of cursor moves between multiple points on a line. The delay between each
// point is hard-coded, so the number of points (steps) controls how long the scroll takes.
private val NOTIFICATION_SCROLL_STEPS = 5
private val NOTIFICATION_SCROLL_POLL_MS = 100L

private val TEST_WIFI_CONFIG_TYPE = "application/x-wifi-config"

@Rule
val mServiceRule = ServiceTestRule()

@RunWith(AndroidJUnit4::class)
@SmallTest
class DownloadServiceTest {
    private val connection = mock(HttpURLConnection::class.java)

    private val context by lazy { getInstrumentation().context }
    private val resources by lazy { context.resources }
    private val device by lazy { UiDevice.getInstance(getInstrumentation()) }

    // Test network that can be parceled in intents while mocking the connection
    class TestNetwork(private val privateDnsBypass: Boolean = false)
        : Network(43, privateDnsBypass) {
        companion object {
            // Subclasses of parcelable classes need to define a CREATOR field of their own (which
            // hides the one of the parent class), otherwise the CREATOR field of the parent class
            // would be used when unparceling and createFromParcel would return an instance of the
            // parent class.
            @JvmField
            val CREATOR = object : Parcelable.Creator<TestNetwork> {
                override fun createFromParcel(source: Parcel?) = TestNetwork()
                override fun newArray(size: Int) = emptyArray<TestNetwork>()
            }

            /**
             * Test [URLConnection] to be returned by all [TestNetwork] instances when
             * [openConnection] is called.
             *
             * This can be set to a mock connection, and is a static to allow [TestNetwork]s to be
             * parceled and unparceled without losing their mock configuration.
             */
            internal var sTestConnection: HttpURLConnection? = null
        }

        override fun getPrivateDnsBypassingCopy(): Network {
            // Note that the privateDnsBypass flag is not kept when parceling/unparceling: this
            // mirrors the real behavior of that flag in Network.
            // The test relies on this to verify that after setting privateDnsBypass to true,
            // the TestNetwork is not parceled / unparceled, which would clear the flag both
            // for TestNetwork or for a real Network and be a bug.
            return TestNetwork(privateDnsBypass = true)
        }

        override fun openConnection(url: URL?): URLConnection {
            // Verify that this network was created with privateDnsBypass = true, and was not
            // parceled / unparceled afterwards (which would have cleared the flag).
            assertTrue(privateDnsBypass,
                    "Captive portal downloads should be done on a network bypassing private DNS")
            return sTestConnection ?: throw IllegalStateException(
                    "Mock URLConnection not initialized")
        }
    }

    /**
     * A test InputStream returning generated data.
     *
     * Reading this stream is not thread-safe: it should only be read by one thread at a time.
     */
    private class TestInputStream(private var available: Int = 0) : InputStream() {
        // position / available are only accessed in the reader thread
        private var position = 0

        private val nextAvailableQueue = SynchronousQueue<Int>()

        /**
         * Set how many bytes are available now without blocking.
         *
         * This is to be set on a thread controlling the amount of data that is available, while
         * a reader thread may be trying to read the data.
         *
         * The reader thread will block until this value is increased, and if the reader is not yet
         * waiting for the data to be made available, this method will block until it is.
         */
        fun setAvailable(newAvailable: Int) {
            assertTrue(nextAvailableQueue.offer(newAvailable.coerceIn(0, TEST_FILESIZE),
                    TEST_TIMEOUT_MS, MILLISECONDS),
                    "Timed out waiting for TestInputStream to be read")
        }

        override fun read(): Int {
            throw NotImplementedError("read() should be unused")
        }

        /**
         * Attempt to read [len] bytes at offset [off].
         *
         * This will block until some data is available if no data currently is (so this method
         * never returns 0 if [len] > 0).
         */
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= TEST_FILESIZE) return -1 // End of stream

            while (available <= position) {
                available = nextAvailableQueue.take()
            }

            // Read the requested bytes (but not more than available).
            val remaining = available - position
            val readLen = min(len, remaining)
            for (i in 0 until readLen) {
                b[off + i] = (position % 256).toByte()
                position++
            }

            return readLen
        }
    }

    @Before
    fun setUp() {
        TestNetwork.sTestConnection = connection
        doReturn(200).`when`(connection).responseCode
        doReturn(TEST_FILESIZE.toLong()).`when`(connection).contentLengthLong

        ActivityScenario.launch(RequestDismissKeyguardActivity::class.java)
    }

    private fun assumeCanDisplayNotifications() {
        val isTvUi = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_TELEVISION) != 0
        // See https://tv.withgoogle.com/patterns/notifications.html
        assumeFalse("TVs don't display notifications", isTvUi)
    }

    /**
     * Create a temporary, empty file that can be used to read/write data for testing.
     */
    private fun createTestFile(extension: String = ".png"): File {
        // The test file provider uses the files dir (not cache dir or external files dir or...), as
        // declared in its file_paths XML referenced from the manifest.
        val testFilePath = File(context.getFilesDir(),
                CaptivePortalLoginActivity.FILE_PROVIDER_DOWNLOAD_PATH)
        testFilePath.mkdir()
        // Do not use File.createTempFile, as it generates very long filenames that may not
        // fit in notifications, making it difficult to find the right notification.
        // currentTimeMillis would generally be 13 digits. Use the bottom 8 to fit the filename and
        // a bit more text, even on very small screens (320 dp, minimum CDD size).
        var index = System.currentTimeMillis().rem(100_000_000)
        while (true) {
            val file = File(testFilePath, "tmp$index$extension")
            if (!file.exists()) {
                file.createNewFile()
                return file
            }
            index++
        }
    }

    /**
     * Make a file URI based on a file on disk, using a [FileProvider] that is registered for the
     * test app.
     */
    private fun makeFileUri(testFile: File) = FileProvider.getUriForFile(
            context,
            // File provider registered in the test manifest
            "com.android.captiveportallogin.tests.fileprovider",
            testFile)

    @Test
    fun testDownloadFile() {
        assumeCanDisplayNotifications()

        val inputStream1 = TestInputStream()
        doReturn(inputStream1).`when`(connection).inputStream

        val testFile1 = createTestFile()
        val testFile2 = createTestFile()
        assertNotEquals(testFile1.name, testFile2.name)
        openNotificationShade()

        // Queue both downloads immediately: they should be started in order
        val binder = bindService(makeDownloadCompleteCallback())
        startDownloadTask(binder, testFile1, TEST_TEXT_FILE_TYPE)
        startDownloadTask(binder, testFile2, TEST_TEXT_FILE_TYPE)

        verify(connection, timeout(TEST_TIMEOUT_MS)).inputStream
        val dlText1 = resources.getString(R.string.downloading_paramfile, testFile1.name)

        findNotification(UiSelector().textContains(dlText1))

        // Allow download to progress to 1%
        assertEquals(0, TEST_FILESIZE % 100)
        assertTrue(TEST_FILESIZE / 100 > 0)
        inputStream1.setAvailable(TEST_FILESIZE / 100)

        // Setup the connection for the next download with indeterminate progress
        val inputStream2 = TestInputStream()
        doReturn(inputStream2).`when`(connection).inputStream
        doReturn(-1L).`when`(connection).contentLengthLong

        // Allow the first download to finish
        inputStream1.setAvailable(TEST_FILESIZE)
        verify(connection, timeout(TEST_TIMEOUT_MS)).disconnect()

        FileInputStream(testFile1).use {
            assertSameContents(it, TestInputStream(TEST_FILESIZE))
        }

        testFile1.delete()

        // The second download should have started: make some data available
        inputStream2.setAvailable(TEST_FILESIZE / 100)

        // A notification should be shown for the second download with indeterminate progress
        val dlText2 = resources.getString(R.string.downloading_paramfile, testFile2.name)
        findNotification(UiSelector().textContains(dlText2))

        // Allow the second download to finish
        inputStream2.setAvailable(TEST_FILESIZE)
        verify(connection, timeout(TEST_TIMEOUT_MS).times(2)).disconnect()

        FileInputStream(testFile2).use {
            assertSameContents(it, TestInputStream(TEST_FILESIZE))
        }

        testFile2.delete()
    }

    fun makeDownloadCompleteCallback(
        directlyOpenCompleteFuture: CompletableFuture<Boolean> = CompletableFuture<Boolean>(),
        downloadCompleteFuture: CompletableFuture<Boolean> = CompletableFuture<Boolean>(),
        downloadAbortedFuture: CompletableFuture<Boolean> = CompletableFuture<Boolean>(),
        expectReason: Int = -1
    ): ServiceConnection {
        // Test callback to receive download completed callback.
        return object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName) {}
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val callback = object : ProgressCallback {
                    override fun onDownloadComplete(
                        inputFile: Uri,
                        mimeType: String,
                        downloadId: Int,
                        success: Boolean
                    ) {
                        if (TEST_WIFI_CONFIG_TYPE.equals(mimeType)) {
                            directlyOpenCompleteFuture.complete(success)
                        } else {
                            downloadCompleteFuture.complete(success)
                        }
                    }

                    override fun onDownloadAborted(downloadId: Int, reason: Int) {
                        if (expectReason == reason) downloadAbortedFuture.complete(true)
                    }
                }

                (binder as DownloadServiceBinder).setProgressCallback(callback)
            }
        }
    }

    @Test
    fun testDirectlyOpenMimeType_fileSizeTooLarge() {
        val inputStream1 = TestInputStream()
        doReturn(inputStream1).`when`(connection).inputStream
        getInstrumentation().waitForIdleSync()
        val outCfgFile = createTestDirectlyOpenFile()
        val downloadAbortedFuture = CompletableFuture<Boolean>()
        val mTestServiceConn = makeDownloadCompleteCallback(
                downloadAbortedFuture = downloadAbortedFuture,
                expectReason = DOWNLOAD_ABORTED_REASON_FILE_TOO_LARGE)

        try {
            val binder = bindService(mTestServiceConn)
            startDownloadTask(binder, outCfgFile, TEST_WIFI_CONFIG_TYPE)
            inputStream1.setAvailable(TEST_FILESIZE)
            // File size 1_000_000 is bigger than the limit(100_000). Download is expected to be
            // aborted. Verify callback called when the download is complete.
            assertTrue(downloadAbortedFuture.get(TEST_TIMEOUT_MS, MILLISECONDS))
        } finally {
            mServiceRule.unbindService()
        }
    }

    @Test
    fun testDirectlyOpenMimeType_cancelTask() {
        val inputStream1 = TestInputStream()
        doReturn(inputStream1).`when`(connection).inputStream

        val outCfgFile = createTestDirectlyOpenFile()
        val outTextFile = createTestFile(extension = ".$TEST_TEXT_FILE_EXTENSION")

        val directlyOpenCompleteFuture = CompletableFuture<Boolean>()
        val otherCompleteFuture = CompletableFuture<Boolean>()
        val testServiceConn = makeDownloadCompleteCallback(
                directlyOpenCompleteFuture = directlyOpenCompleteFuture,
                downloadCompleteFuture = otherCompleteFuture)

        try {
            val binder = bindService(testServiceConn)
            // Start directly open task first then follow with a generic one
            val directlydlId = startDownloadTask(binder, outCfgFile, TEST_WIFI_CONFIG_TYPE)
            startDownloadTask(binder, outTextFile, TEST_TEXT_FILE_TYPE)

            inputStream1.setAvailable(TEST_FILESIZE / 100)
            // Cancel directly open task. The directly open task should result in a failed download
            // complete. The cancel intent should not affect the other download task.
            binder.cancelTask(directlydlId)
            inputStream1.setAvailable(TEST_FILESIZE)
            assertFalse(directlyOpenCompleteFuture.get(TEST_TIMEOUT_MS, MILLISECONDS))
            assertTrue(otherCompleteFuture.get(TEST_TIMEOUT_MS, MILLISECONDS))
        } finally {
            mServiceRule.unbindService()
        }
    }

    private fun createTestDirectlyOpenFile() = createTestFile(extension = ".wificonfig")

    private fun bindService(serviceConn: ServiceConnection): DownloadServiceBinder {
        val binder = mServiceRule.bindService(Intent(context, DownloadService::class.java),
                serviceConn, Context.BIND_AUTO_CREATE) as DownloadServiceBinder
        assertNotNull(binder)
        return binder
    }

    private fun startDownloadTask(binder: DownloadServiceBinder, file: File, mimeType: String):
            Int {
        return binder.requestDownload(
                TestNetwork(),
                TEST_USERAGENT,
                TEST_URL,
                file.name,
                makeFileUri(file),
                context,
                mimeType)
    }

    @Test
    fun testTapDoneNotification() {
        assumeCanDisplayNotifications()

        val fileContents = "Test file contents"
        val bis = ByteArrayInputStream(fileContents.toByteArray(StandardCharsets.UTF_8))
        doReturn(bis).`when`(connection).inputStream

        // The test extension is handled by OpenTextFileActivity in the test package
        val testFile = createTestFile(extension = ".$TEST_TEXT_FILE_EXTENSION")
        openNotificationShade()

        val binder = bindService(makeDownloadCompleteCallback())
        startDownloadTask(binder, testFile, TEST_TEXT_FILE_TYPE)

        // The download completed notification has the filename as contents, and
        // R.string.download_completed as title. Find the contents using the filename as exact match
        val note = findNotification(UiSelector().text(testFile.name))
        note.click()

        // OpenTextFileActivity opens the file and shows contents
        assertTrue(device.wait(Until.hasObject(By.text(fileContents)), TEST_TIMEOUT_MS))
    }

    private fun openNotificationShade() {
        device.wakeUp()
        device.openNotification()
        assertTrue(device.wait(Until.hasObject(By.res(NOTIFICATION_SHADE_TYPE)), TEST_TIMEOUT_MS))
    }

    private fun findNotification(selector: UiSelector): UiObject {
        val shadeScroller = UiScrollable(UiSelector().resourceId(NOTIFICATION_SHADE_TYPE))
                .setSwipeDeadZonePercentage(NOTIFICATION_SCROLL_DEAD_ZONE_PERCENT)

        // Optimistically wait for the notification without scrolling (scrolling is slow)
        val note = shadeScroller.getChild(selector)
        if (note.waitForExists(NOTIFICATION_NO_SCROLL_TIMEOUT_MS)) return note

        val limit = System.currentTimeMillis() + TEST_TIMEOUT_MS
        while (System.currentTimeMillis() < limit) {
            // Similar to UiScrollable.scrollIntoView, but do not scroll up before going down (it
            // could open the quick settings), and control the scroll steps (with a large swipe
            // dead zone, scrollIntoView uses too many steps by default and is very slow).
            for (i in 0 until NOTIFICATION_SCROLL_COUNT) {
                val canScrollFurther = shadeScroller.scrollForward(NOTIFICATION_SCROLL_STEPS)
                if (note.exists()) return note
                // Scrolled to the end, or scrolled too much and closed the shade
                if (!canScrollFurther || !shadeScroller.exists()) break
            }

            // Go back to the top: close then reopen the notification shade.
            // Do not scroll up, as it could open quick settings (and would be slower).
            device.pressHome()
            assertTrue(shadeScroller.waitUntilGone(TEST_TIMEOUT_MS))
            openNotificationShade()

            Thread.sleep(NOTIFICATION_SCROLL_POLL_MS)
        }
        fail("Notification with selector $selector not found")
    }

    /**
     * Verify that two [InputStream] have the same content by reading them until the end of stream.
     */
    private fun assertSameContents(s1: InputStream, s2: InputStream) {
        val buffer1 = ByteArray(1000)
        val buffer2 = ByteArray(1000)
        while (true) {
            // Read one chunk from s1
            val read1 = s1.read(buffer1, 0, buffer1.size)
            if (read1 < 0) break

            // Read a chunk of the same size from s2
            var read2 = 0
            while (read2 < read1) {
                s2.read(buffer2, read2, read1 - read2).also {
                    assertFalse(it < 0, "Stream 2 is shorter than stream 1")
                    read2 += it
                }
            }
            assertEquals(buffer1.take(read1), buffer2.take(read1))
        }
        assertEquals(-1, s2.read(buffer2, 0, 1), "Stream 2 is longer than stream 1")
    }

    /**
     * Activity that reads a file specified as [Uri] in its start [Intent], and displays the file
     * contents on screen by reading the file as UTF-8 text.
     *
     * The activity is registered in the manifest as a receiver for VIEW intents with a
     * ".testtxtfile" URI.
     */
    class OpenTextFileActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val testFile = intent.data ?: fail("This activity expects a file")
            val fileStream = contentResolver.openInputStream(testFile)
                    ?: fail("Could not open file InputStream")
            val contents = InputStreamReader(fileStream, StandardCharsets.UTF_8).use {
                it.readText()
            }

            val view = TextView(this)
            view.text = contents
            setContentView(view)
        }
    }
}
