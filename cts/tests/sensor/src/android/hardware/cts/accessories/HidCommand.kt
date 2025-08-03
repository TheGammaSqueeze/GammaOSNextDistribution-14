package android.hardware.cts.accessories

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.HidResultData
import com.android.cts.input.InputJsonParser
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

abstract class HidCommand {
    private var deviceId: Int? = null
    private lateinit var parser: InputJsonParser
    private lateinit var inputstream: InputStream
    private lateinit var outputStream: OutputStream
    private lateinit var reader: JsonReader
    private lateinit var resultThread: Thread
    private lateinit var handlerThread: HandlerThread

    @JvmField
    var handler: Handler? = null
    private lateinit var instrumentation: Instrumentation
    protected abstract fun processResults(results: HidResultData)

    private fun setupPipes() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        val ui: UiAutomation = instrumentation.getUiAutomation()
        val pipes: Array<ParcelFileDescriptor> = ui.executeShellCommandRw(HidCommand.shellCommand)
        inputstream = ParcelFileDescriptor.AutoCloseInputStream(pipes[0])
        outputStream = ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])
        try {
            reader = JsonReader(InputStreamReader(inputstream, "UTF-8"))
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(e)
        }
        reader.setLenient(true)
    }

    /**
     * Register a device using the provided json resource.
     *
     * @param deviceRegister The raw file that contains the json command
     */
    fun registerDevice(registerCommand: Int) {
        parser = InputJsonParser(
            InstrumentationRegistry.getInstrumentation().getTargetContext()
        )
        setupPipes()
        deviceId = parser.readDeviceId(registerCommand)
        writeCommands(parser.readRegisterCommand(registerCommand).toByteArray())
        handlerThread = HandlerThread("HidCommandThread")
        handlerThread.start()
        handler = Handler(handlerThread.getLooper())
        resultThread = Thread {
            try {
                while (reader.peek() !== JsonToken.END_DOCUMENT) {
                    processResults(readResults())
                }
            } catch (ex: IOException) {
                Log.w(TAG, "Exiting JSON Result reader. $ex")
            }
        }
        resultThread.start()
    }

    protected fun setGetReportResponse(resultData: HidResultData) {
        val result: StringBuilder = StringBuilder()
        result.append("[")
        for (reportData in resultData.reportData) {
            result.append(String.format("0x%02x,", reportData))
        }
        var report = result.toString()
        report = report.substring(0, report.length - 1) + "]"
        val json = JSONObject()
        try {
            json.put("command", "set_get_report_response")
            json.put("id", deviceId)
            json.put("report", JSONArray(report))
        } catch (e: JSONException) {
            throw RuntimeException("Could not process HID report: $report")
        }
        writeCommands(json.toString().toByteArray())
    }

    private fun readResults(): HidResultData {
        val result: HidResultData = HidResultData()
        try {
            reader.beginObject()
            while (reader.hasNext()) {
                val fieldName: String = reader.nextName()
                if (fieldName == "eventId") {
                    result.eventId = java.lang.Byte.decode(reader.nextString())
                }
                if (fieldName == "deviceId") {
                    result.deviceId = Integer.decode(reader.nextString())
                }
                if (fieldName == "reportType") {
                    result.reportType = java.lang.Byte.decode(reader.nextString())
                }
                if (fieldName == "reportData") {
                    result.reportData = readData()
                }
            }
            reader.endObject()
        } catch (ex: IOException) {
            Log.w(TAG, "Exiting JSON Result reader. $ex")
        }
        return result
    }

    protected fun sendSetReportReply(success: Boolean) {
        val json = JSONObject()
        try {
            json.put("command", "send_set_report_reply")
            json.put("id", deviceId)
            json.put("success", success.toString())
        } catch (e: JSONException) {
            throw RuntimeException("Could not process reply.")
        }
        writeCommands(json.toString().toByteArray())
    }

    @Throws(IOException::class)
    private fun readData(): ByteArray {
        val dataList = ArrayList<Int>()
        try {
            reader.beginArray()
            while (reader.hasNext()) {
                dataList.add(Integer.decode(reader.nextString()))
            }
            reader.endArray()
        } catch (e: IllegalStateException) {
            reader.endArray()
            throw IllegalStateException("Encountered malformed data.", e)
        } catch (e: NumberFormatException) {
            reader.endArray()
            throw IllegalStateException("Encountered malformed data.", e)
        }
        val rawData = ByteArray(dataList.size)
        for (i in dataList.indices) {
            val d = dataList[i]
            check(d and 0xFF == d) { "Invalid data, all values must be byte-sized" }
            rawData[i] = d.toByte()
        }
        return rawData
    }

    protected fun sendHidReport(report: String) {
        val json = JSONObject()
        try {
            json.put("command", "report")
            json.put("id", deviceId)
            json.put("report", JSONArray(report))
        } catch (e: JSONException) {
            throw RuntimeException("Could not process HID report: $report")
        }
        writeCommands(json.toString().toByteArray())
    }

    private fun writeCommands(bytes: ByteArray?) {
        try {
            outputStream.write(bytes)
            outputStream.flush()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun closeDevice() {
        inputstream.close()
        outputStream.close()
    }

    companion object {
        private const val TAG = "HidCommand"

        // hid executable expects "-" argument to read from stdin instead of a file
        val shellCommand = "hid -"
    }
}
