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
package android.permissionui.cts.accessmicrophoneapplocationprovider

import android.app.Activity
import android.location.Criteria
import android.location.LocationManager
import android.os.Bundle

/** An activity that adds this package as a test location provider and uses microphone. */
class AddLocationProviderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attrContext = createAttributionContext("test.tag")
        val locationManager = attrContext.getSystemService(LocationManager::class.java)!!
        locationManager.addTestProvider(
            packageName,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            Criteria.POWER_LOW,
            Criteria.ACCURACY_COARSE
        )

        setResult(RESULT_OK)
        finish()
    }
}
