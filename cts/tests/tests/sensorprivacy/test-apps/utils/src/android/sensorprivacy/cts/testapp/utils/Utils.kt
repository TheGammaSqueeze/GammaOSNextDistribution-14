package android.sensorprivacy.cts.testapp.utils

import android.app.AppOpsManager
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.util.Size
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

private const val SAMPLING_RATE = 8000

private const val RETRY_TIMEOUT = 5000L
private const val TAG = "SensorPrivacyTestAppUtils"

fun openMic(): Mic {
    val audioRecord = AudioRecord.Builder()
            .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(SAMPLING_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            .setBufferSizeInBytes(
                    AudioRecord.getMinBufferSize(SAMPLING_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT) * 10)
            .build()

    audioRecord?.startRecording()

    return Mic(audioRecord)
}

fun openCam(context: Context): Cam {
    return openCam(context, false)
}

fun openCam(context: Context, retryCam: Boolean): Cam {
    return openCam(context, retryCam, 0, 0)
}

fun openCam(
    context: Context,
    retryCam: Boolean,
    cameraOpenRetryCountRO: Int,
    cameraMaxOpenRetryRO: Int
): Cam {
    var cameraOpenRetryCount = cameraOpenRetryCountRO
    var cameraMaxOpenRetry = cameraMaxOpenRetryRO

    val cameraManager = context.getSystemService(CameraManager::class.java)!!
    val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!

    val cameraId = cameraManager!!.cameraIdList[0]
    val config = cameraManager!!.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val outputFormat = config!!.outputFormats[0]
    val outputSize: Size = config!!.getOutputSizes(outputFormat)[0]
    val handlerThread = HandlerThread("CameraThread")
    handlerThread.start()
    val handler = Handler(handlerThread.looper)
    val executor = Executor {
        runnable ->
        run {
            handler.post(runnable)
        }
    }

    var cameraDevice: CompletableFuture<CameraDevice> = CompletableFuture()

    // Retry camera connection because external cameras are disconnected
    // if sensor privacy is enabled (b/182204067)
    val isExternalCamera = (cameraManager!!.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.LENS_FACING)
            == CameraCharacteristics.LENS_FACING_EXTERNAL)
    if (retryCam && isExternalCamera) {
        cameraMaxOpenRetry = 1
    }

    val cameraDeviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cD: CameraDevice) {
            val imageReader = ImageReader.newInstance(
                    outputSize.width, outputSize.height, outputFormat, 2)

            val builder = cD.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder.addTarget(imageReader.surface)
            val captureRequest = builder.build()
            val sessionConfiguration = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(OutputConfiguration(imageReader.surface)),
                    executor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest, null, handler)
                            appOpsManager.startOpNoThrow(AppOpsManager.OPSTR_CAMERA,
                                    Process.myUid(), context.opPackageName)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    })

            cD.createCaptureSession(sessionConfiguration)
            cameraDevice.complete(cD)
            cameraOpenRetryCount = 0
        }

        override fun onDisconnected(cD: CameraDevice) {
        }

        override fun onError(cD: CameraDevice, i: Int) {
            // Retry once after timeout if cause is ERROR_CAMERA_DISABLED because it may
            // be triggered if camera mute is not supported and sensor privacy is enabled
            if (i == ERROR_CAMERA_DISABLED && cameraOpenRetryCount < cameraMaxOpenRetry) {
                cD.close()
                cameraOpenRetryCount++
                cameraDevice.complete(cD)
                handler.postDelayed({
                    openCam(context, retryCam, cameraOpenRetryCount, cameraMaxOpenRetry)
                }, RETRY_TIMEOUT)
            }
        }
    }

    try {
        cameraManager!!.openCamera(cameraId, executor, cameraDeviceCallback)
    } catch (e: android.hardware.camera2.CameraAccessException) {
        Log.e(TAG, "openCamera: " + e)
    }

    return Cam(cameraDevice.join(), handlerThread)
}

class Mic(val audioRecord: AudioRecord) {
    fun close() {
        audioRecord.stop()
    }
}

class Cam(val cameraDevice: CameraDevice?, val thread: Thread) {
    fun close() {
        cameraDevice?.close()
        thread.interrupt()
    }
}