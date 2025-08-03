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

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevicePicker
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore.Images.Media
import android.provider.MediaStore.MediaColumns
import androidx.test.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import pandora.OppGrpc.OppImplBase
import pandora.OppProto.*

private const val TAG = "PandoraOpp"

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Opp(val context: Context) : OppImplBase(), Closeable {
    private val IMAGE_FILE_NAME = "OPP_TEST_IMAGE.bmp"
    private val INCOMING_FILE_TITLE = "Incoming file"
    private val INCOMING_FILE_ACCEPT_BTN = "ACCEPT"
    private val INCOMING_FILE_WAIT_TIMEOUT = 2000L
    private val flow: Flow<Intent>
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter
    private var uiDevice: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    init {
        createImageFile()

        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND)
        flow = intentFlow(context, intentFilter, scope).shareIn(scope, SharingStarted.Eagerly)
    }

    override fun close() {
        val file =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                IMAGE_FILE_NAME
            )

        if (file.exists()) {
            file.delete()
        }
    }

    override fun openRfcommChannel(
        request: OpenRfcommChannelRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        grpcUnary<Empty>(scope, responseObserver) {
            val bluetoothDevice = request.address.toBluetoothDevice(bluetoothAdapter)
            sendFile(bluetoothDevice)
            Empty.getDefaultInstance()
        }
    }

    override fun openL2capChannel(
        request: OpenL2capChannelRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        grpcUnary<Empty>(scope, responseObserver) {
            val bluetoothDevice = request.address.toBluetoothDevice(bluetoothAdapter)
            sendFile(bluetoothDevice)
            Empty.getDefaultInstance()
        }
    }

    override fun acceptPutOperation(
        request: Empty,
        responseObserver: StreamObserver<AcceptPutOperationResponse>
    ) {
        grpcUnary<AcceptPutOperationResponse>(scope, responseObserver) {
            acceptIncomingFile()
            AcceptPutOperationResponse.newBuilder().setStatus(PutStatus.ACCEPTED).build()
        }
    }

    fun acceptIncomingFile() {
        uiDevice
            .wait(Until.findObject(By.text(INCOMING_FILE_TITLE)), INCOMING_FILE_WAIT_TIMEOUT)
            .click()
        uiDevice.wait(
            Until.hasObject(By.text(INCOMING_FILE_ACCEPT_BTN)),
            INCOMING_FILE_WAIT_TIMEOUT
        )
        uiDevice.waitForIdle()
        uiDevice
            .wait(Until.findObject(By.text(INCOMING_FILE_ACCEPT_BTN)), INCOMING_FILE_WAIT_TIMEOUT)
            .click()
    }

    private suspend fun sendFile(bluetoothDevice: BluetoothDevice) {
        initiateSendFile(getImageId(IMAGE_FILE_NAME), "image/bmp")
        waitBluetoothDevice(bluetoothDevice)
        val intent = Intent(BluetoothDevicePicker.ACTION_DEVICE_SELECTED)
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, bluetoothDevice)
        context.sendBroadcast(intent)
    }

    suspend private fun waitBluetoothDevice(bluetoothDevice: BluetoothDevice) {
        bluetoothAdapter.startDiscovery()
        flow
            .filter {
                it.action == BluetoothDevice.ACTION_FOUND &&
                    it.getBluetoothDeviceExtra() == bluetoothDevice
            }
            .first()
        bluetoothAdapter.cancelDiscovery()
    }

    private fun initiateSendFile(imageId: Long, type: String) {
        val contentUri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, imageId)

        try {
            var sendingIntent = Intent(Intent.ACTION_SEND)
            sendingIntent.setType(type)
            val activity =
                context.packageManager!!
                    .queryIntentActivities(
                        sendingIntent,
                        PackageManager.ResolveInfoFlags.of(
                            PackageManager.MATCH_DEFAULT_ONLY.toLong()
                        )
                    )
                    .filter { it!!.loadLabel(context.packageManager) == "Bluetooth" }
                    .first()
                    .activityInfo
            sendingIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            sendingIntent.setComponent(
                ComponentName(activity.applicationInfo.packageName, activity.name)
            )
            sendingIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
            context.startActivity(sendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createImageFile() {
        val bitmapImage = Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888)
        val file =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                IMAGE_FILE_NAME
            )
        var fileOutputStream: FileOutputStream? = null

        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()
        try {
            fileOutputStream = FileOutputStream(file)
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getImageId(fileName: String): Long {
        val selection = MediaColumns.DISPLAY_NAME + "=?"
        val selectionArgs = arrayOf(fileName)
        val cursor =
            context
                .getContentResolver()
                .query(Media.EXTERNAL_CONTENT_URI, null, selection, selectionArgs, null)

        cursor?.use {
            it.let {
                it.moveToFirst()
                return it.getLong(it.getColumnIndexOrThrow(Media._ID))
            }
        }
        return 0L
    }
}
