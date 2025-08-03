package com.android.car.docklib.events

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.DockInterface
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DockPackageChangeReceiverTest {
    private companion object {
        private const val TEST_UID = 10
        private const val TEST_PKG_NAME = "TEST_PKG_NAME"
    }

    private val dockInterfaceMock = mock<DockInterface> {}
    private val packageManagerMock = mock<PackageManager> {}
    private val contextMock = mock<Context> {
        on { packageManager } doReturn packageManagerMock
    }
    private val uriMock = mock<Uri> {}
    private val intentMock = mock<Intent> {
        on { data } doReturn uriMock
    }
    private val dockPackageChangeReceiver = DockPackageChangeReceiver(dockInterfaceMock)

    @Test
    fun onReceive_uidNotSent_noop() {
        // since getIntExtra cannot return null value, return the default value passed in as arg
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt()))
            .then { it.getArgument<Int>(1) }

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verifyNoMoreInteractions(dockInterfaceMock)
    }

    @Test
    fun onReceive_pkgNotSend_noop() {
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt())).thenReturn(TEST_UID)
        whenever(uriMock.schemeSpecificPart).thenReturn(null)

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verifyNoMoreInteractions(dockInterfaceMock)
    }

    @Test
    fun onReceive_actionPackageRemoved_replacing_noop() {
        whenever(intentMock.action).thenReturn(Intent.ACTION_PACKAGE_REMOVED)
        whenever(intentMock.getBooleanExtra(eq(Intent.EXTRA_REPLACING), anyBoolean()))
            .thenReturn(true)
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt())).thenReturn(TEST_UID)
        whenever(uriMock.schemeSpecificPart).thenReturn(TEST_PKG_NAME)

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verifyNoMoreInteractions(dockInterfaceMock)
    }

    @Test
    fun onReceive_actionPackageRemoved_notReplacing_packageRemovedCalled() {
        whenever(intentMock.action).thenReturn(Intent.ACTION_PACKAGE_REMOVED)
        whenever(intentMock.getBooleanExtra(eq(Intent.EXTRA_REPLACING), anyBoolean()))
            .thenReturn(false)
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt())).thenReturn(TEST_UID)
        whenever(uriMock.schemeSpecificPart).thenReturn(TEST_PKG_NAME)

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verify(dockInterfaceMock).packageRemoved(eq(TEST_PKG_NAME))
    }

    @Test
    fun onReceive_actionPackageAdded_replacing_noop() {
        whenever(intentMock.action).thenReturn(Intent.ACTION_PACKAGE_ADDED)
        whenever(intentMock.getBooleanExtra(eq(Intent.EXTRA_REPLACING), anyBoolean()))
            .thenReturn(true)
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt())).thenReturn(TEST_UID)
        whenever(uriMock.schemeSpecificPart).thenReturn(TEST_PKG_NAME)

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verifyNoMoreInteractions(dockInterfaceMock)
    }

    @Test
    fun onReceive_actionPackageAdded_notReplacing_packageAddedCalled() {
        whenever(intentMock.action).thenReturn(Intent.ACTION_PACKAGE_ADDED)
        whenever(intentMock.getBooleanExtra(eq(Intent.EXTRA_REPLACING), anyBoolean()))
            .thenReturn(false)
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt())).thenReturn(TEST_UID)
        whenever(uriMock.schemeSpecificPart).thenReturn(TEST_PKG_NAME)

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verify(dockInterfaceMock).packageAdded(eq(TEST_PKG_NAME))
    }

    @Test
    fun onReceive_actionPackageChanged_isEnabled_noop() {
        whenever(intentMock.action).thenReturn(Intent.ACTION_PACKAGE_CHANGED)
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt())).thenReturn(TEST_UID)
        whenever(uriMock.schemeSpecificPart).thenReturn(TEST_PKG_NAME)
        whenever(packageManagerMock.getApplicationEnabledSetting(eq(TEST_PKG_NAME)))
            .thenReturn(COMPONENT_ENABLED_STATE_ENABLED)

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verifyNoMoreInteractions(dockInterfaceMock)
    }

    @Test
    fun onReceive_actionPackageChanged_isDisabled_packageRemovedCalled() {
        whenever(intentMock.action).thenReturn(Intent.ACTION_PACKAGE_CHANGED)
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt())).thenReturn(TEST_UID)
        whenever(uriMock.schemeSpecificPart).thenReturn(TEST_PKG_NAME)
        whenever(packageManagerMock.getApplicationEnabledSetting(eq(TEST_PKG_NAME)))
            .thenReturn(COMPONENT_ENABLED_STATE_DISABLED)

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verify(dockInterfaceMock).packageRemoved(eq(TEST_PKG_NAME))
    }

    @Test
    fun onReceive_actionPackageChanged_isDisabledForUser_packageRemovedCalled() {
        whenever(intentMock.action).thenReturn(Intent.ACTION_PACKAGE_CHANGED)
        whenever(intentMock.getIntExtra(eq(Intent.EXTRA_UID), anyInt())).thenReturn(TEST_UID)
        whenever(uriMock.schemeSpecificPart).thenReturn(TEST_PKG_NAME)
        whenever(packageManagerMock.getApplicationEnabledSetting(eq(TEST_PKG_NAME)))
            .thenReturn(COMPONENT_ENABLED_STATE_DISABLED_USER)

        dockPackageChangeReceiver.onReceive(contextMock, intentMock)

        verify(dockInterfaceMock).packageRemoved(eq(TEST_PKG_NAME))
    }
}
