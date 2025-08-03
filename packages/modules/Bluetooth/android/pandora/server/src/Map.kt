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

package com.android.pandora

import android.content.Context
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import pandora.MapGrpc.MapImplBase
import pandora.MapProto.*

private const val TAG = "PandoraMap"

class Map(context: Context) : MapImplBase() {
    private val DEFAULT_MESSAGE_LEN = 130

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    private var telephonyManager = context.getSystemService(TelephonyManager::class.java)!!

    override fun sendSMS(request: Empty, responseObserver: StreamObserver<Empty>) {
        grpcUnary<Empty>(scope, responseObserver) {
            val smsManager = SmsManager.getDefault()
            val defaultSmsSub = SubscriptionManager.getDefaultSmsSubscriptionId()
            telephonyManager = telephonyManager.createForSubscriptionId(defaultSmsSub)
            val avdPhoneNumber = telephonyManager.getLine1Number()

            smsManager.sendTextMessage(
                avdPhoneNumber,
                avdPhoneNumber,
                generateAlphanumericString(DEFAULT_MESSAGE_LEN),
                null,
                null
            )
            Empty.getDefaultInstance()
        }
    }
}
