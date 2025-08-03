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
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.android.car.carlauncher.R
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSource.MirroringPackageData
import com.android.car.carlauncher.datasources.ControlCenterMirroringDataSourceImpl.MirroringServiceConnection
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class ControlCenterMirroringDataSourceImplTest {

    private val scope = TestScope()
    private val bgDispatcher =
        StandardTestDispatcher(scope.testScheduler, name = "Background dispatcher")
    private val resources = RuntimeEnvironment.getApplication().resources
    private var bindServiceIntent: Intent? = null
    private var bindServiceConnection: MirroringServiceConnection? = null
    private var bindServiceFlags: Int? = null
    private val bindService: (Intent, MirroringServiceConnection, Int) -> Unit =
        { intent, serviceConnection, flags ->
            bindServiceIntent = intent
            bindServiceConnection = serviceConnection
            bindServiceFlags = flags
        }

    private val unbindService: (MirroringServiceConnection) -> Unit = mock()

    private val packageManager: PackageManager = mock {
        on { resolveService(any(), anyInt()) } doReturn ResolveInfo()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getAppMirroringSession_registersService() = scope.runTest {
        val expectedBindServiceFlags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        val controlCenterMirroringDataSource = ControlCenterMirroringDataSourceImpl(
            resources,
            bindService,
            unbindService,
            packageManager,
            bgDispatcher
        )

        launch(StandardTestDispatcher(testScheduler)) {
            controlCenterMirroringDataSource.getAppMirroringSession().collect()
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).resolveService(intentCaptor.capture(), anyInt())
        assertEquals(
            ComponentName(
                resources.getString(R.string.config_msg_mirroring_service_pkg_name),
                resources.getString(R.string.config_msg_mirroring_service_class_name)
            ),
            intentCaptor.value.component
        )
        assertEquals(bindServiceIntent, intentCaptor.value)
        assertNotNull(bindServiceConnection)
        assertNotNull(bindServiceConnection?.mClientMessenger)
        assertEquals(expectedBindServiceFlags, bindServiceFlags)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getAppMirroringSession_noActiveSession_sendsNoMirroringData() = scope.runTest {
        val controlCenterMirroringDataSource = ControlCenterMirroringDataSourceImpl(
            resources,
            bindService,
            unbindService,
            packageManager,
            bgDispatcher
        )
        val flows = mutableListOf<MirroringPackageData>()

        launch(StandardTestDispatcher(testScheduler)) {
            controlCenterMirroringDataSource.getAppMirroringSession().toList(flows)
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(1, flows.size)
        assertEquals(MirroringPackageData.NO_MIRRORING, flows[0])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getAppMirroringSession_activeSession_sendsMirroringData() = scope.runTest {
        val msg = createClientMessage()
        val controlCenterMirroringDataSource = ControlCenterMirroringDataSourceImpl(
            resources,
            bindService,
            unbindService,
            packageManager,
            bgDispatcher
        )
        val flows = mutableListOf<MirroringPackageData>()

        launch(StandardTestDispatcher(testScheduler)) {
            controlCenterMirroringDataSource.getAppMirroringSession().toList(flows)
        }
        advanceUntilIdle()
        // Fake sending Mirroring messages to receiver
        val clientMessenger = bindServiceConnection?.mClientMessenger
        clientMessenger?.send(msg)
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(2, flows.size)
        assertEquals(MirroringPackageData.NO_MIRRORING, flows[0]) // Initial no mirroring packet
        assertEquals(mirroringPackageName, flows[1].packageName)
        assert(
            Intent.parseUri(mirroringRedirectUri, Intent.URI_INTENT_SCHEME)
                .filterEquals(flows[1].launchIntent)
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getAppMirroringSession_sessionEnd_sendsNoMirroring() = scope.runTest {
        // Empty packageName for no mirroring
        val msg = createClientMessage(packageName = "")
        val controlCenterMirroringDataSource = ControlCenterMirroringDataSourceImpl(
            resources,
            bindService,
            unbindService,
            packageManager,
            bgDispatcher
        )
        val flows = mutableListOf<MirroringPackageData>()

        launch(StandardTestDispatcher(testScheduler)) {
            controlCenterMirroringDataSource.getAppMirroringSession().toList(flows)
        }
        advanceUntilIdle()
        // Fake sending Mirroring messages to receiver
        val clientMessenger = bindServiceConnection?.mClientMessenger
        clientMessenger?.send(msg)
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(2, flows.size)
        assertEquals(MirroringPackageData.NO_MIRRORING, flows[0]) // Initial no mirroring packet
        assertEquals(MirroringPackageData.NO_MIRRORING, flows[1]) // Service no mirroring packet
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getAppMirroringSession_scopeClosed_shouldCleanUp() = scope.runTest {
        val expectedUnRegisterMsg = Message.obtain(
            null,
            resources.getInteger(R.integer.config_msg_unregister_mirroring_pkg_code)
        )
        var actualUnRegisterMsg: Message? = null
        val serviceMessenger = Messenger(object : Handler(Looper.myLooper()!!) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                actualUnRegisterMsg = msg
            }
        })
        val controlCenterMirroringDataSource = ControlCenterMirroringDataSourceImpl(
            resources,
            bindService,
            unbindService,
            packageManager,
            bgDispatcher
        )

        launch(StandardTestDispatcher(testScheduler)) {
            controlCenterMirroringDataSource.getAppMirroringSession().collect()
        }
        advanceUntilIdle()
        // Assign an external serviceMessenger
        bindServiceConnection?.mServiceMessenger = serviceMessenger
        coroutineContext.cancelChildren()
        advanceUntilIdle()
        ShadowLooper.runUiThreadTasks()

        assertEquals(expectedUnRegisterMsg.toString(), actualUnRegisterMsg.toString())
        assertNotNull(bindServiceConnection)
        verify(unbindService).invoke(bindServiceConnection!!)
    }

    /**
     * @param packageName name of the package under mirroring session,
     *  If Empty it signifies [MirroringPackageData.NO_MIRRORING].
     * @return [Message] to send to the Client to report mirroring session.
     */
    private fun createClientMessage(packageName: String = mirroringPackageName): Message {
        val sendMirroringPackageCode =
            resources.getInteger(R.integer.config_msg_send_mirroring_pkg_code)
        val packageNameKey = resources.getString(R.string.config_msg_mirroring_pkg_name_key)
        val redirectUriKey = resources.getString(R.string.config_msg_mirroring_redirect_uri_key)
        val msg: Message = Message.obtain(
            null,
            resources.getInteger(R.integer.config_msg_register_mirroring_pkg_code)
        )
        msg.what = sendMirroringPackageCode
        msg.obj = Bundle().apply {
            putString(packageNameKey, packageName)
            putString(redirectUriKey, mirroringRedirectUri)
        }
        return msg
    }

    companion object {
        const val mirroringPackageName = "TestPackageName"
        const val mirroringRedirectUri = "intent:#Intent;action=android.test.MIRRORING_TEST;end"
    }
}
