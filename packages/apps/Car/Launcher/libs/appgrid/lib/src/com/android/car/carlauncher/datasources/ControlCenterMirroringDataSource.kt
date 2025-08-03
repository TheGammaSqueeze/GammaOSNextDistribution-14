/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.carlauncher.datasources

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.android.car.carlauncher.R
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSource.MirroringPackageData
import java.net.URISyntaxException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/**
 * DataSource interface tells if there is an active Mirroring-Session.
 */
interface ControlCenterMirroringDataSource {

    /**
     * @return Flow of [MirroringPackageData] which sends the active Mirroring packageName and
     *  redirect launchIntent which launches the application in the
     *  MirroringActivity
     */
    fun getAppMirroringSession(): Flow<MirroringPackageData>

    data class MirroringPackageData(
        val packageName: String,
        val launchIntent: Intent
    ) {
        companion object {
            // signifies active mirroring session
            val NO_MIRRORING = MirroringPackageData("", Intent())
        }
    }
}

/**
 * Impl of [ControlCenterMirroringDataSource] to surface all the control center mirroring session
 * All the operations in this class are non blocking.
 *
 *  The Application using this Datasource is expected to define the following configs
 *  in its resources. The implementation uses [resources] to fetch these configs.
 *  These configs should be bound application's lifecycle and are not expected to change with
 *  Activity's lifecycle events.
 *
 *  __config_msg_mirroring_service_pkg_name__: String value stating the package name of the
 *   mirroring service.
 *
 *  __config_msg_mirroring_service_class_name__: String value stating the class name of the
 *   mirroring service.
 *
 *  __config_msg_register_mirroring_pkg_code__: Integer unique key to register the service.
 *
 *  __config_msg_unregister_mirroring_pkg_code__: Integer unique key to unregister the service.
 *
 *  __config_msg_send_mirroring_pkg_code__: Integer unique key to send mirroring packet across the
 *   service.
 *
 *  __config_msg_mirroring_pkg_name_key__: String unique key to send packageName of the active
 *   mirroring session.
 *
 *  __config_msg_mirroring_redirect_uri_key__: String unique key to send the redirect uri of the
 *   mirroring activity.
 *
 * @property [resources] Application resources, not bound to activity's configuration changes.
 * @property [bindService] Function to register service.
 *  Should be provided by an Android Component owning the [Context].
 * @property [unBindService] Function to unregister the broadcast receiver.
 *  Should be provided by the Android Component owning the [Context].
 * @property [packageManager] Used to resolve the bounded Service.
 * @property [bgDispatcher] Executes all the operations on this background coroutine dispatcher.
 *
 */
class ControlCenterMirroringDataSourceImpl(
    private val resources: Resources,
    private val bindService: (Intent, MirroringServiceConnection, flags: Int) -> Unit,
    private val unBindService: (MirroringServiceConnection) -> Unit,
    private val packageManager: PackageManager,
    private val bgDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ControlCenterMirroringDataSource {

    /**
     * @return Flow of [MirroringPackageData] reporting current active mirroring session.
     *
     * Note: The producer sends an [MirroringPackageData.NO_MIRRORING] initially.
     * This immediately tells the collector that there are no changes as of now with packages.
     *
     * When the scope in which this flow is collected is closed/canceled
     * [unBindService] is triggered.
     */
    override fun getAppMirroringSession(): Flow<MirroringPackageData> {
        return callbackFlow {
            // Send empty mirroring packet to signify that no mirroring is ongoing
            trySend(MirroringPackageData.NO_MIRRORING)
            if (Looper.myLooper() == null) {
                Looper.prepare()
            }
            val looper: Looper =
                Looper.myLooper().takeIf { it != null } ?: Looper.getMainLooper().also {
                    Log.w(
                        TAG,
                        "Current thread looper for mirroring session is null, fallback to " +
                                "MainLooper"
                    )
                }
            val clientMessenger = getReceiverMessenger(looper, this)
            val serviceConnection = getMirroringConnectionService(clientMessenger, this)
            registerReceiver(serviceConnection, this)

            awaitClose {
                unregisterReceiver(serviceConnection)
                // If MainLooper do not quit it. MainLooper always stays alive.
                if (looper != Looper.getMainLooper()) {
                    looper.quitSafely()
                }
            }
        }.flowOn(bgDispatcher).conflate()
    }

    private fun getReceiverMessenger(
        looper: Looper,
        producerScope: ProducerScope<MirroringPackageData>
    ): Messenger {
        return Messenger(object : Handler(looper) {
            private val senderMirroringPkgCode =
                resources.getInteger(R.integer.config_msg_send_mirroring_pkg_code)
            private val mirroringPkgNameKey =
                resources.getString(R.string.config_msg_mirroring_pkg_name_key)
            private val mirroringRedirectUriKey =
                resources.getString(R.string.config_msg_mirroring_redirect_uri_key)

            override fun handleMessage(msg: Message) {
                if (msg.what != senderMirroringPkgCode) {
                    super.handleMessage(msg)
                    return
                }
                val bundle = msg.obj as Bundle
                val mirroringPackageName = bundle.getString(mirroringPkgNameKey)
                if (mirroringPackageName.isNullOrEmpty()) {
                    producerScope.trySend(MirroringPackageData.NO_MIRRORING)
                    return
                }
                try {
                    val mirroringIntentRedirect = Intent.parseUri(
                        bundle.getString(mirroringRedirectUriKey),
                        Intent.URI_INTENT_SCHEME
                    )
                    producerScope.trySend(
                        MirroringPackageData(
                            mirroringPackageName,
                            mirroringIntentRedirect
                        )
                    )
                } catch (e: URISyntaxException) {
                    Log.d(TAG, "Error parsing mirroring redirect intent $e")
                }
            }
        })
    }

    abstract class MirroringServiceConnection : ServiceConnection {
        var mServiceMessenger: Messenger? = null
        var mClientMessenger: Messenger? = null
    }

    private fun getMirroringConnectionService(
        clientMessenger: Messenger,
        producerScope: ProducerScope<MirroringPackageData>
    ): MirroringServiceConnection {
        return object : MirroringServiceConnection() {
            init {
                mClientMessenger = clientMessenger
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mServiceMessenger = Messenger(service)
                val msg: Message = Message.obtain(
                    null,
                    resources.getInteger(R.integer.config_msg_register_mirroring_pkg_code)
                )
                msg.replyTo = mClientMessenger
                try {
                    mServiceMessenger?.send(msg)
                } catch (e: RemoteException) {
                    Log.d(TAG, "Exception sending message to mirroring service: $e")
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                producerScope.cancel("Mirroring Service disconnected")
            }
        }
    }

    private fun registerReceiver(
        mirroringConnectionService: MirroringServiceConnection,
        producerScope: ProducerScope<MirroringPackageData>
    ) {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                resources.getString(R.string.config_msg_mirroring_service_pkg_name),
                resources.getString(R.string.config_msg_mirroring_service_class_name)
            )
            if (packageManager.resolveService(intent, 0) != null) {
                bindService(
                    intent,
                    mirroringConnectionService,
                    Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error binding to mirroring service: $e")
            producerScope.close(e)
        }
    }

    private fun unregisterReceiver(mirroringConnectionService: MirroringServiceConnection) {
        val msg = Message.obtain(
            null,
            resources.getInteger(R.integer.config_msg_unregister_mirroring_pkg_code)
        )
        msg.replyTo = mirroringConnectionService.mClientMessenger
        try {
            mirroringConnectionService.mServiceMessenger?.send(msg)
        } catch (e: RemoteException) {
            Log.d(TAG, "Exception unregistering mirroring service $e")
        }
        if (mirroringConnectionService.mServiceMessenger != null) {
            unBindService(mirroringConnectionService)
        }
    }

    companion object {
        val TAG: String = ControlCenterMirroringDataSourceImpl::class.java.simpleName
    }
}
