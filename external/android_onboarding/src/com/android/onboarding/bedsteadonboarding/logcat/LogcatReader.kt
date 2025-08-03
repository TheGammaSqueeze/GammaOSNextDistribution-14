package com.android.onboarding.bedsteadonboarding.logcat

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Reads the logs continuously until stopped explicitly. It filters logs with tag [filterTag] from
 * logs and stores them in-memory. Logs are streamed on a separate thread.
 */
class LogcatReader(private val startClean: Boolean = true, private val filterTag: String) {

  private val LOGCAT_CLEAR_COMMAND = "logcat -c"
  private val LOGCAT_COMMAND = "logcat"
  private val TAG = "LogcatReader"
  private val filteredLogs = arrayListOf<String>()
  private val logsLock = Any() // Lock to provide thread safe access to [filteredLogs].
  private val logcatReaderExecutorService: ExecutorService = Executors.newSingleThreadExecutor()

  private var shouldReadLogs = false
  private var hasLogcatStarted = false

  private lateinit var logcatFileDescriptor: ParcelFileDescriptor

  private val logcatReaderRunnable = Runnable {
    synchronized(logsLock) {
      filteredLogs.clear()
    }
    if (startClean) {
      try {
        InstrumentationRegistry.getInstrumentation()
          .uiAutomation
          .executeShellCommand(LOGCAT_CLEAR_COMMAND)
      } catch (t: Throwable) {
        // Clearing is best effort - don't disrupt the test because we can't
        Log.e(TAG, "Error clearing logcat  $t")
      }
    }
    logcatFileDescriptor =
      InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(LOGCAT_COMMAND)
    val fis: FileInputStream = ParcelFileDescriptor.AutoCloseInputStream(logcatFileDescriptor)
    val logcatReader = BufferedReader(InputStreamReader(fis))
    shouldReadLogs = true
    hasLogcatStarted = true
    Log.i(TAG, "Started Reading Logs")
    while (shouldReadLogs) {
      logcatReader
        .readLine()
        ?.takeIf { it.contains(filterTag) }
        ?.also { synchronized(logsLock) { filteredLogs.add(it) } }
    }
  }

  /**
   * Submits [logcatReaderRunnable] on separate thread which in turn would perform the task of
   * reading logs. It will block until logs have started to be read.
   */
  fun start() {
    var unused = logcatReaderExecutorService.submit(logcatReaderRunnable)
    // Wait until logs have started to be read.
    while (!hasLogcatStarted) {
      Thread.sleep(100)
    }
  }

  /**
   * Closes the file-descriptor pointing to the logs. Clears the filtered log lines stored
   * in-memory. No more logs would be read until the runnable [LogcatReader] is started again.
   */
  fun stopReadingLogs() {
    shouldReadLogs = false
    synchronized(logsLock) {
      filteredLogs.clear()
    }
    Log.i(TAG, "Closing logcat file descriptor")
    logcatFileDescriptor.close()
    logcatReaderExecutorService.shutdown()
  }

  /** Returns the immutable set of log lines with tag = [filterTag] from logs */
  fun getFilteredLogs(): Set<String> {
    synchronized(logsLock) {
      return filteredLogs.toSet()
    }
  }

  /** Returns if the logs have started to be read. */
  fun hasLogcatStarted() = hasLogcatStarted
}
