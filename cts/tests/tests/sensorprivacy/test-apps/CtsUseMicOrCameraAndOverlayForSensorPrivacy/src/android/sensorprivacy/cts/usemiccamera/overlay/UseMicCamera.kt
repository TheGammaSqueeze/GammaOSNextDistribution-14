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

package android.sensorprivacy.cts.usemiccamera.overlay

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

class UseMicCamera : Activity() {
    private var mic: Mic? = null
    private var cam: Cam? = null
    private lateinit var appOpsManager: AppOpsManager

    companion object {
        const val MIC_CAM_OVERLAY_ACTIVITY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.overlay.action.USE_MIC_CAM"
        const val SHOW_OVERLAY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.action.SHOW_OVERLAY_ACTION"
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

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                unregisterReceiver(this)
                mic?.close()
                cam?.close()
                appOpsManager.finishOp(AppOpsManager.OPSTR_CAMERA,
                        Process.myUid(), applicationContext.packageName)
                finishAndRemoveTask()
                Runtime.getRuntime().exit(0)
            }
        }, IntentFilter(FINISH_MIC_CAM_ACTIVITY_ACTION), Context.RECEIVER_EXPORTED)

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val intent = Intent(this@UseMicCamera, OverlayActivity::class.java)
                if (intent.getBooleanExtra(DELAYED_ACTIVITY_NEW_TASK_EXTRA, false)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }, IntentFilter(SHOW_OVERLAY_ACTION), Context.RECEIVER_EXPORTED)

        val useMic = intent.getBooleanExtra(USE_MIC_EXTRA, false)
        val useCam = intent.getBooleanExtra(USE_CAM_EXTRA, false)
        if (useMic) {
            handler.postDelayed({ mic = openMic() }, 5000)
        }
        if (useCam) {
            handler.postDelayed({
                cam = openCam(this, intent.getBooleanExtra(UseMicCamera.RETRY_CAM_EXTRA, false))
            }, 5000)
        }
    }
}
