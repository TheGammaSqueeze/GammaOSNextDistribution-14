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
package com.android.systemui.tv.dagger

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.PowerExemptionManager
import com.android.internal.logging.UiEventLogger
import com.android.keyguard.KeyguardViewController
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.systemui.Dependency
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.dagger.ReferenceSystemUIModule
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.ui.viewmodel.ConnectingDisplayViewModel
import com.android.systemui.dock.DockManager
import com.android.systemui.dock.DockManagerImpl
import com.android.systemui.doze.DozeHost
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.media.dialog.MediaOutputDialogFactory
import com.android.systemui.media.muteawait.MediaMuteAwaitConnectionCli
import com.android.systemui.media.nearby.NearbyMediaDevicesManager
import com.android.systemui.navigationbar.gestural.GestureModule
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.qs.QSFactory
import com.android.systemui.power.dagger.PowerModule
import com.android.systemui.privacy.MediaProjectionPrivacyItemMonitor
import com.android.systemui.privacy.PrivacyItemMonitor
import com.android.systemui.qs.dagger.QSModule
import com.android.systemui.qs.tileimpl.QSFactoryImpl
import com.android.systemui.screenshot.ReferenceScreenshotModule
import com.android.systemui.settings.MultiUserUtilsModule
import com.android.systemui.settings.UserTracker
import com.android.systemui.shade.ShadeEmptyImplModule
import com.android.systemui.statusbar.KeyboardShortcutsModule
import com.android.systemui.statusbar.NotificationListener
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.NotificationLockscreenUserManagerImpl
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.statusbar.events.StatusBarEventsModule
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.phone.DozeServiceHost
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import com.android.systemui.statusbar.policy.AospPolicyModule
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.statusbar.policy.DeviceProvisionedControllerImpl
import com.android.systemui.statusbar.policy.HeadsUpEmptyImplModule
import com.android.systemui.statusbar.policy.IndividualSensorPrivacyController
import com.android.systemui.statusbar.policy.IndividualSensorPrivacyControllerImpl
import com.android.systemui.statusbar.policy.SensorPrivacyController
import com.android.systemui.statusbar.policy.SensorPrivacyControllerImpl
import com.android.systemui.tv.hdmi.HdmiModule
import com.android.systemui.tv.media.TvMediaOutputDialogActivity
import com.android.systemui.tv.media.TvMediaOutputDialogFactory
import com.android.systemui.tv.notifications.TvNotificationHandler
import com.android.systemui.tv.notifications.TvNotificationsModule
import com.android.systemui.tv.privacy.PrivacyModule
import com.android.systemui.tv.sensorprivacy.TvSensorPrivacyModule
import com.android.systemui.tv.shade.TvNotificationShadeWindowController
import com.android.systemui.tv.usb.TvUsbDebuggingModule
import com.android.systemui.unfold.SysUIUnfoldStartableModule
import com.android.systemui.volume.dagger.VolumeModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import javax.inject.Named
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * A TV specific version of [ReferenceSystemUIModule].
 *
 * Code here should be specific to the TV variant of SystemUI and will not be included in other
 * variants of SystemUI.
 */
@Module(
    includes = [
    AospPolicyModule::class,
    ConnectingDisplayViewModel.StartableModule::class,
    GestureModule::class,
    HdmiModule::class,
    HeadsUpEmptyImplModule::class,
    KeyboardShortcutsModule::class,
    MediaMuteAwaitConnectionCli.StartableModule::class,
    MultiUserUtilsModule::class,
    NearbyMediaDevicesManager.StartableModule::class,
    PowerModule::class,
    PrivacyModule::class,
    QSModule::class,
    ReferenceScreenshotModule::class,
    ShadeEmptyImplModule::class,
    StatusBarEventsModule::class,
    SysUIUnfoldStartableModule::class,
    TvNotificationsModule::class,
    TvSensorPrivacyModule::class,
    TvUsbDebuggingModule::class,
    VolumeModule::class,
]
)
abstract class TvSystemUIModule {
    @Binds
    abstract fun bindNotificationLockscreenUserManager(
            notificationLockscreenUserManager: NotificationLockscreenUserManagerImpl
    ): NotificationLockscreenUserManager

    @Binds
    @SysUISingleton
    abstract fun bindQSFactory(qsFactoryImpl: QSFactoryImpl): QSFactory

    @Binds
    abstract fun bindDockManager(dockManager: DockManagerImpl): DockManager

    @Binds
    abstract fun bindKeyguardViewController(
            statusBarKeyguardViewManager: StatusBarKeyguardViewManager
    ): KeyguardViewController

    @Binds
    abstract fun bindNotificationShadeController(
            notificationShadeWindowController: TvNotificationShadeWindowController
    ): NotificationShadeWindowController

    @OptIn(ExperimentalCoroutinesApi::class)
    @Binds
    abstract fun provideDozeHost(dozeServiceHost: DozeServiceHost): DozeHost

    /**
     * Binds [MediaProjectionPrivacyItemMonitor] into the set of [PrivacyItemMonitor].
     */
    @Binds
    @IntoSet
    abstract fun bindMediaProjectionPrivacyItemMonitor(
            mediaProjectionPrivacyItemMonitor: MediaProjectionPrivacyItemMonitor
    ): PrivacyItemMonitor

    @Binds
    @IntoMap
    @ClassKey(TvMediaOutputDialogActivity::class)
    abstract fun provideTvMediaOutputDialogActivity(
            tvMediaOutputDialogActivity: TvMediaOutputDialogActivity
    ): Activity

    companion object {
        @SysUISingleton
        @Provides
        @Named(Dependency.LEAK_REPORT_EMAIL_NAME)
        fun provideLeakReportEmail(): String = ""

        @Provides
        @SysUISingleton
        fun provideSensorPrivacyController(
                sensorPrivacyManager: SensorPrivacyManager
        ): SensorPrivacyController =
                SensorPrivacyControllerImpl(sensorPrivacyManager).apply { init() }

        @Provides
        @SysUISingleton
        fun provideIndividualSensorPrivacyController(
                sensorPrivacyManager: SensorPrivacyManager
        ): IndividualSensorPrivacyController =
                IndividualSensorPrivacyControllerImpl(sensorPrivacyManager).apply { init() }

        @SysUISingleton
        @Provides
        @Named(Dependency.ALLOW_NOTIFICATION_LONG_PRESS_NAME)
        fun provideAllowNotificationLongPress(): Boolean = true

        @SysUISingleton
        @Provides
        fun providesDeviceProvisionedController(
                deviceProvisionedController: DeviceProvisionedControllerImpl
        ): DeviceProvisionedController {
            deviceProvisionedController.init()
            return deviceProvisionedController
        }

        @Provides
        @SysUISingleton
        fun provideTvNotificationHandler(
                notificationListener: NotificationListener
        ): TvNotificationHandler = TvNotificationHandler(notificationListener)

        @Provides
        fun provideMediaOutputDialogFactory(
            context: Context,
            mediaSessionManager: MediaSessionManager,
            localBluetoothManager: LocalBluetoothManager?,
            activityStarter: ActivityStarter,
            broadcastSender: BroadcastSender,
            notifCollection: CommonNotifCollection,
            uiEventLogger: UiEventLogger,
            dialogTransitionAnimator: DialogTransitionAnimator,
            nearbyMediaDevicesManager: NearbyMediaDevicesManager,
            audioManager: AudioManager,
            powerExemptionManager: PowerExemptionManager,
            keyguardManager: KeyguardManager,
            featureFlags: FeatureFlags,
            userTracker: UserTracker,
            ): MediaOutputDialogFactory =
                TvMediaOutputDialogFactory(context, mediaSessionManager, localBluetoothManager,
                        activityStarter, broadcastSender, notifCollection, uiEventLogger,
                        dialogTransitionAnimator, nearbyMediaDevicesManager, audioManager,
                        powerExemptionManager, keyguardManager, featureFlags, userTracker)
    }
}
