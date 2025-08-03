/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.permissionui.cts.appthataccessescameraandmic

import android.app.Activity
import android.app.AppOpsManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.AudioFormat.CHANNEL_IN_MONO
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.util.Log
import android.util.Size
import androidx.annotation.NonNull
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val USE_CAMERA = "use_camera"
private const val USE_MICROPHONE = "use_microphone"
private const val USE_HOTWORD = "use_hotword"
private const val FINISH_EARLY = "finish_early"
private const val USE_DURATION_MS = 10000L
private const val SAMPLE_RATE_HZ = 44100

/**
 * Activity which will, depending on the extra passed in the intent, use the camera, the microphone,
 * or both.
 */
class AccessCameraOrMicActivity : Activity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private var cameraDevice: CameraDevice? = null
    private var recorder: AudioRecord? = null
    private var appOpsManager: AppOpsManager? = null
    private var cameraFinished = false
    private var runCamera = false
    private var backupCameraOpRunning = true
    private var micFinished = false
    private var runMic = false
    private var hotwordFinished = false
    private var runHotword = false
    private var finishEarly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            throw RuntimeException(
                "Activity was recreated (perhaps due to a configuration change?) " +
                    "and this activity doesn't currently know how to gracefully handle " +
                    "configuration changes."
            )
        }
    }

    override fun onStart() {
        super.onStart()
        runCamera = intent.getBooleanExtra(USE_CAMERA, false)
        runMic = intent.getBooleanExtra(USE_MICROPHONE, false)
        runHotword = intent.getBooleanExtra(USE_HOTWORD, false)
        finishEarly = intent.getBooleanExtra(FINISH_EARLY, false)

        if (runMic) {
            useMic()
        }

        if (runCamera) {
            useCamera()
        }

        if (runHotword) {
            useHotword()
        }
    }

    override fun finish() {
        super.finish()
        cameraDevice?.close()
        cameraDevice = null
        recorder?.stop()
        recorder = null
        if (runCamera) {
            appOpsManager?.finishOp(AppOpsManager.OPSTR_CAMERA, Process.myUid(), packageName)
        }
        if (runHotword) {
            appOpsManager?.finishOp(
                AppOpsManager.OPSTR_RECORD_AUDIO_HOTWORD,
                Process.myUid(),
                packageName
            )
        }
        appOpsManager = null
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    private val stateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(@NonNull camDevice: CameraDevice) {
                cameraDevice = camDevice
                val config =
                    cameraManager!!
                        .getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val outputFormat = config!!.outputFormats[0]
                val outputSize: Size = config!!.getOutputSizes(outputFormat)[0]
                val handler = Handler(mainLooper)

                val imageReader =
                    ImageReader.newInstance(outputSize.width, outputSize.height, outputFormat, 2)

                val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(imageReader.surface)
                val captureRequest = builder.build()
                val sessionConfiguration =
                    SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        listOf(OutputConfiguration(imageReader.surface)),
                        mainExecutor,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.capture(captureRequest, null, handler)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {}

                            override fun onReady(session: CameraCaptureSession) {}
                        }
                    )

                imageReader.setOnImageAvailableListener(
                    {
                        GlobalScope.launch {
                            delay(USE_DURATION_MS)
                            if (!backupCameraOpRunning) {
                                cameraFinished = true
                                if (!runMic || micFinished) {
                                    finish()
                                }
                            }
                        }
                    },
                    handler
                )
                cameraDevice!!.createCaptureSession(sessionConfiguration)
            }

            override fun onDisconnected(@NonNull camDevice: CameraDevice) {
                Log.e("CameraMicIndicatorsPermissionTest", "camera disconnected")
                startBackupCamera(camDevice)
            }

            override fun onError(@NonNull camDevice: CameraDevice, error: Int) {
                Log.e("CameraMicIndicatorsPermissionTest", "camera error $error")
                startBackupCamera(camDevice)
            }
        }

    private fun startBackupCamera(camDevice: CameraDevice?) {
        // Something went wrong with the camera. Fallback to direct app op usage
        if (runCamera && !cameraFinished) {
            backupCameraOpRunning = true
            appOpsManager = getSystemService(AppOpsManager::class.java)
            appOpsManager?.startOpNoThrow(AppOpsManager.OPSTR_CAMERA, Process.myUid(), packageName)

            GlobalScope.launch {
                delay(USE_DURATION_MS)
                cameraFinished = true
                backupCameraOpRunning = false
                finishIfAllDone()
            }
        }
        camDevice?.close()
        if (camDevice == cameraDevice) {
            cameraDevice = null
        }
    }

    @Throws(CameraAccessException::class)
    private fun useCamera() {
        // TODO 192690992: determine why the camera manager code is flaky
        startBackupCamera(null)
        /*
        cameraManager = getSystemService(CameraManager::class.java)!!
        cameraId = cameraManager.cameraIdList[0]
        cameraManager.openCamera(cameraId, mainExecutor, stateCallback)
         */
    }

    private fun useMic() {
        val minSize =
            AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
        recorder = AudioRecord(MIC, SAMPLE_RATE_HZ, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, minSize)
        recorder?.startRecording()
        if (finishEarly) {
            appOpsManager = getSystemService(AppOpsManager::class.java)
            appOpsManager?.finishOp(AppOpsManager.OPSTR_RECORD_AUDIO, Process.myUid(), packageName)
            return
        }
        GlobalScope.launch {
            delay(USE_DURATION_MS)
            micFinished = true
            finishIfAllDone()
        }
    }

    private fun useHotword() {
        appOpsManager = getSystemService(AppOpsManager::class.java)
        appOpsManager?.startOpNoThrow(
            AppOpsManager.OPSTR_RECORD_AUDIO_HOTWORD,
            Process.myUid(),
            packageName
        )

        GlobalScope.launch {
            delay(USE_DURATION_MS)
            hotwordFinished = true
            finishIfAllDone()
        }
    }

    private fun finishIfAllDone() {
        if (
            (!runMic || micFinished) &&
                (!runCamera || cameraFinished) &&
                (!runHotword || hotwordFinished)
        ) {
            finish()
        }
    }
}
