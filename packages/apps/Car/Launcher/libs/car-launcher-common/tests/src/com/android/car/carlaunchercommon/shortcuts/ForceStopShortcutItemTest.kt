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

package com.android.car.carlaunchercommon.shortcuts

import android.app.ActivityManager
import android.app.AlertDialog
import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.widget.Toast
import com.android.car.ui.AlertDialogBuilder
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ForceStopShortcutItemTest {
    private val resourcesMock = mock<Resources> {
        on { getString(anyInt(), anyString()) } doReturn "testResourceString"
    }
    private val activityManagerMock = mock<ActivityManager> {}
    private val contextMock = mock<Context> {
        on { resources } doReturn resourcesMock
        on { getSystemService(eq(ActivityManager::class.java)) } doReturn activityManagerMock
    }
    private val toastMock = mock<Toast> {}
    private val carMediaManagerMock = mock<CarMediaManager> {}
    private val alertDialogMock = mock<AlertDialog> {}
    private val alertDialogBuilderMock = mock<AlertDialogBuilder> {
        on { setTitle(anyInt()) } doReturn it
        on { setMessage(anyInt()) } doReturn it
        on { setPositiveButton(anyInt(), any<DialogInterface.OnClickListener>()) } doReturn it
        on {
            setNegativeButton(anyInt(), nullable(DialogInterface.OnClickListener::class.java))
        } doReturn it
        on { create() } doReturn alertDialogMock
    }

    @Test
    fun forceStop_nonMediaApp_forceStopPackageCalled() {
        val packageName = "com.example.app"

        createForceStopShortcutItem(
            contextMock,
            packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(packageName, displayName = "testDisplayName")

        verify(activityManagerMock).forceStopPackage(eq(packageName))
    }

    @Test
    fun forceStop_nonMediaApp_shouldNotChangeMediaSource() {
        createForceStopShortcutItem(
            contextMock,
            packageName = "com.example.app",
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(packageName = "com.example.app", displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock, Mockito.never())
            .setMediaSource(any<ComponentName>(), anyInt())
    }

    @Test
    fun forceStop_activeBrowseMediaSrc_foundLastBrowseSrc_forceStopPackageCalled() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val lastBrowsedMediaComponent =
            ComponentName("lastBrowsedMediaPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE))
        ).doReturn(listOf(forceStoppedComponent, lastBrowsedMediaComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(activityManagerMock).forceStopPackage(forceStoppedComponent.packageName)
    }

    @Test
    fun forceStop_activeBrowseMediaSrc_foundLastBrowseSrc_browseMediaSrcSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val lastBrowsedMediaComponent =
            ComponentName("lastBrowsedMediaPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE))
        ).doReturn(listOf(forceStoppedComponent, lastBrowsedMediaComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock).setMediaSource(
            lastBrowsedMediaComponent,
            CarMediaManager.MEDIA_SOURCE_MODE_BROWSE
        )
    }

    @Test
    fun forceStop_activeBrowseMediaSrc_foundLastBrowseSrc_playbackMediaSrcNotSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val lastBrowsedMediaComponent =
            ComponentName("lastBrowsedMediaPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE))
        ).doReturn(listOf(forceStoppedComponent, lastBrowsedMediaComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock, never()).setMediaSource(
            any<ComponentName>(),
            eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
        )
    }

    @Test
    fun forceStop_activeBrowseMediaSrc_noLastBrowseSrc_browseMediaSrcSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val mediaComponent = ComponentName("mediaComponent", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE))
        ).doReturn(listOf(forceStoppedComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf(forceStoppedComponent, mediaComponent)
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock).setMediaSource(
            mediaComponent,
            CarMediaManager.MEDIA_SOURCE_MODE_BROWSE
        )
    }

    @Test
    fun forceStop_activeBrowseMediaSrc_noLastBrowseSrc_noMediaService_browseMediaSrcNotSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE))
        ).doReturn(listOf(forceStoppedComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock, Mockito.never()).setMediaSource(
            any<ComponentName>(),
            anyInt()
        )
    }

    @Test
    fun forceStop_activePlaybackMediaSrc_lastPlaybackSrcFound_forceStopPackageCalled() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val lastPlaybackMediaComponent =
            ComponentName("lastPlaybackMediaPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK))
        ).doReturn(listOf(forceStoppedComponent, lastPlaybackMediaComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(activityManagerMock).forceStopPackage(forceStoppedComponent.packageName)
    }

    @Test
    fun forceStop_activePlaybackMediaSrc_lastPlaybackSrcFound_playbackMediaSrcSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val lastPlaybackMediaComponent =
            ComponentName("lastPlaybackMediaPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK))
        ).doReturn(listOf(forceStoppedComponent, lastPlaybackMediaComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock).setMediaSource(
            lastPlaybackMediaComponent,
            CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK
        )
    }

    @Test
    fun forceStop_activePlaybackMediaSrc_lastPlaybackSrcFound_browseMediaSrcNotSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val lastPlaybackMediaComponent =
            ComponentName("lastPlaybackMediaPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK))
        ).doReturn(listOf(forceStoppedComponent, lastPlaybackMediaComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock, never()).setMediaSource(
            any<ComponentName>(),
            eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)
        )
    }

    @Test
    fun forceStop_activePlaybackMediaSrc_noLastPlaybackSrc_playbackMediaSrcSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val mediaComponent = ComponentName("mediaComponent", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK))
        ).doReturn(listOf(forceStoppedComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf(forceStoppedComponent, mediaComponent)
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock).setMediaSource(
            mediaComponent,
            CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK
        )
    }

    @Test
    fun forceStop_activePlaybackMediaSrc_noLastPlaybackSrc_noMediaService_playbackMediaSrcNotSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK))
        ).doReturn(listOf(forceStoppedComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(ComponentName("otherPkg", "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock, Mockito.never()).setMediaSource(
            any<ComponentName>(),
            anyInt()
        )
    }

    @Test
    fun forceStop_activeBrowseAndPlaybackMediaSrc_foundLastSrcs_browseAndPlaybackMediaSrcSet() {
        val forceStoppedComponent =
            ComponentName("forceStoppedPkg", "testClassName")
        val lastPlaybackMediaComponent =
            ComponentName("lastPlaybackMediaPkg", "testClassName")
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE))
        ).doReturn(listOf(forceStoppedComponent, lastPlaybackMediaComponent))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(forceStoppedComponent)
        whenever(
            carMediaManagerMock.getLastMediaSources(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK))
        ).doReturn(listOf(forceStoppedComponent, lastPlaybackMediaComponent))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedComponent.packageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedComponent.packageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock).setMediaSource(
            lastPlaybackMediaComponent,
            CarMediaManager.MEDIA_SOURCE_MODE_BROWSE
        )
        Mockito.verify(carMediaManagerMock).setMediaSource(
            lastPlaybackMediaComponent,
            CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK
        )
    }

    @Test
    fun forceStop_notActiveMediaBrowseNorPlaybackSource_forceStopPackageCalled() {
        val forceStoppedPackageName = "forceStoppedPackageName"
        val activeMediaPackageName = "activeMediaPackageName"
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(ComponentName(activeMediaPackageName, "testClassName"))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(ComponentName(activeMediaPackageName, "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedPackageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedPackageName, displayName = "testDisplayName")

        Mockito.verify(activityManagerMock).forceStopPackage(forceStoppedPackageName)
    }

    @Test
    fun forceStop_notActiveMediaBrowseNorPlaybackSource_doesNotChangeMediaSource() {
        val forceStoppedPackageName = "forceStoppedPackageName"
        val activeMediaPackageName = "activeMediaPackageName"
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)))
            .doReturn(ComponentName(activeMediaPackageName, "testClassName"))
        whenever(carMediaManagerMock.getMediaSource(eq(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)))
            .doReturn(ComponentName(activeMediaPackageName, "testClassName"))

        createForceStopShortcutItem(
            contextMock,
            forceStoppedPackageName,
            carMediaManagerMock,
            mediaServiceComponents = setOf()
        ).forceStop(forceStoppedPackageName, displayName = "testDisplayName")

        Mockito.verify(carMediaManagerMock, Mockito.never()).setMediaSource(
            any<ComponentName>(),
            anyInt()
        )
    }

    private fun createForceStopShortcutItem(
        context: Context,
        packageName: String,
        carMediaManager: CarMediaManager?,
        mediaServiceComponents: Set<ComponentName>

    ): ForceStopShortcutItem {
        return object : ForceStopShortcutItem(
            context,
            packageName,
            displayName = "testDisplayName",
            carMediaManager,
            mediaServiceComponents
        ) {
            override fun getAlertDialogBuilder(context: Context) = alertDialogBuilderMock
            override fun createToast(context: Context, text: CharSequence, duration: Int) =
                toastMock
        }
    }
}
