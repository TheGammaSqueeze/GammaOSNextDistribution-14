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

package android.sensorprivacy.cts.usemiccamera

import android.app.Activity
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Process
import android.sensorprivacy.cts.testapp.utils.Cam
import android.sensorprivacy.cts.testapp.utils.Mic
import android.sensorprivacy.cts.testapp.utils.openCam
import android.sensorprivacy.cts.testapp.utils.openMic
import android.util.Log

class UseMicCamera : Activity() {
    private var mic: Mic? = null
    private var cam: Cam? = null
    private lateinit var appOpsManager: AppOpsManager

    val activitiesToFinish = mutableSetOf<Activity>()

    companion object {
        val TAG = UseMicCamera::class.simpleName
        const val MIC_CAM_ACTIVITY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.action.USE_MIC_CAM"
        const val FINISH_MIC_CAM_ACTIVITY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.action.FINISH_USE_MIC_CAM"
        const val USE_MIC_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.USE_MICROPHONE"
        const val USE_CAM_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.USE_CAMERA"
        const val DELAYED_ACTIVITY_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.DELAYED_ACTIVITY"
        const val DELAYED_ACTIVITY_NEW_TASK_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.DELAYED_ACTIVITY_NEW_TASK"
        const val RETRY_CAM_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.RETRY_CAM_EXTRA"
    }

    override fun onStart() {
        super.onStart()
        val handler = Handler(mainLooper)
        appOpsManager = applicationContext.getSystemService(AppOpsManager::class.java)!!
        val useMic = intent.getBooleanExtra(USE_MIC_EXTRA, false)
        val useCam = intent.getBooleanExtra(USE_CAM_EXTRA, false)
        Log.i(TAG, "onStart: begin (useMic=$useMic, useCam=$useCam)")

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.i(TAG, "onReceive: closing camera and mic (action='${intent?.action}')")
                unregisterReceiver(this)
                if (useCam) {
                    cam?.close()
                    appOpsManager.finishOp(AppOpsManager.OPSTR_CAMERA,
                            Process.myUid(), applicationContext.packageName)
                }
                if (useMic) {
                    mic?.close()
                    appOpsManager.finishOp(AppOpsManager.OPSTR_RECORD_AUDIO,
                            Process.myUid(), applicationContext.packageName)
                }
                Log.i(TAG, "onReceive: closed camera and mic")
                finishAndRemoveTask()
            }
        }, IntentFilter(FINISH_MIC_CAM_ACTIVITY_ACTION), Context.RECEIVER_EXPORTED)

        if (useMic) {
            handler.postDelayed({
                Log.i(TAG, "onStart: opening microphone")
                mic = openMic()
            }, 5000)
        }
        if (useCam) {
            handler.postDelayed({
                Log.i(TAG, "onStart: opening camera")
                cam = openCam(this, intent.getBooleanExtra(RETRY_CAM_EXTRA, false))
            }, 5000)
        }

        if (intent.getBooleanExtra(DELAYED_ACTIVITY_EXTRA, false)) {
            handler.postDelayed({
                Log.i(TAG, "onStart: opening microphone")
                val intent = Intent(this, BlankActivity::class.java)
                if (intent.getBooleanExtra(DELAYED_ACTIVITY_NEW_TASK_EXTRA, false)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }, 2000)
        }
        Log.i(TAG, "onStart: end")
    }
}
