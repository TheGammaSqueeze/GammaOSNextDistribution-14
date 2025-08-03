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

package android.sensorprivacy.cts

import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import android.net.Uri
import android.telecom.TelecomManager
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assume
import org.junit.Ignore
import org.junit.Test

@CddTest(requirement = "9.8.13/C-1-1,C-1-2")
class SensorPrivacyMicrophoneTest : SensorPrivacyBaseTest(
        MICROPHONE,
        USE_MIC_EXTRA
) {
    @Ignore
    @Test
    fun testMicShownOnPhoneCall() {
        try {
            Assume.assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            setSensor(true)
            SystemUtil.runWithShellPermissionIdentity {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + "555-5555"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }

            unblockSensorWithDialogAndAssert()
        } finally {
            SystemUtil.runWithShellPermissionIdentity {
                context.getSystemService(TelecomManager::class.java)!!.endCall()
            }
        }
    }
}
