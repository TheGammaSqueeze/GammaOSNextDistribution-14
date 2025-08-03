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

package com.android.systemui

import com.android.keyguard.KeyguardBiometricLockoutLogger
import com.android.systemui.car.activity.window.ActivityWindowManager
import com.android.systemui.car.cluster.ClusterDisplayController
import com.android.systemui.car.input.DisplayInputSinkController
import com.android.systemui.car.toast.CarToastUI
import com.android.systemui.car.voicerecognition.ConnectedDeviceVoiceRecognitionNotifier
import com.android.systemui.car.window.SystemUIOverlayWindowManager
import com.android.systemui.dagger.qualifiers.PerUser
import com.android.systemui.keyguard.KeyguardViewMediator
import com.android.systemui.log.SessionTracker
import com.android.systemui.media.RingtonePlayer
import com.android.systemui.theme.ThemeOverlayController
import com.android.systemui.usb.StorageNotification
import com.android.systemui.util.NotificationChannels
import com.android.systemui.wmshell.WMShell
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds

/**
 * DEPRECATED: DO NOT ADD THINGS TO THIS FILE.
 *
 * Add a feature specific dagger module for what you are working on. Bind your CoreStartable there.
 * Include that module where it is needed.
 *
 * @deprecated
 */
@Module
abstract class CarSystemUICoreStartableModule {
    /** Empty set for @PerUser. */
    @Multibinds
    @PerUser
    abstract fun bindEmptyPerUserMap(): Map<Class<*>, CoreStartable>

    /** Inject into CarToastUI.  */
    @Binds
    @IntoMap
    @ClassKey(CarToastUI::class)
    abstract fun bindCarToastUI(service: CarToastUI): CoreStartable

    /** Inject into ClusterDisplayController.  */
    @Binds
    @IntoMap
    @ClassKey(ClusterDisplayController::class)
    abstract fun bindClusterDisplayController(service: ClusterDisplayController): CoreStartable

    /** Inject into DisplayInputSinkController. */
    @Binds
    @IntoMap
    @ClassKey(DisplayInputSinkController::class)
    abstract fun bindDisplayInputSinkController(service: DisplayInputSinkController): CoreStartable

    /** Inject into ConnectedDeviceVoiceRecognitionNotifier.  */
    @Binds
    @IntoMap
    @ClassKey(ConnectedDeviceVoiceRecognitionNotifier::class)
    abstract fun bindConnectedDeviceVoiceRecognitionNotifier(
            service: ConnectedDeviceVoiceRecognitionNotifier
    ): CoreStartable

    /** Inject into KeyguardBiometricLockoutLogger.  */
    @Binds
    @IntoMap
    @ClassKey(KeyguardBiometricLockoutLogger::class)
    abstract fun bindKeyguardBiometricLockoutLogger(
            sysui: KeyguardBiometricLockoutLogger
    ): CoreStartable

    /** Inject into KeyguardViewMediator.  */
    @Binds
    @IntoMap
    @ClassKey(KeyguardViewMediator::class)
    abstract fun bindKeyguardViewMediator(sysui: KeyguardViewMediator): CoreStartable

    /** Inject into NotificationChannels.  */
    @Binds
    @IntoMap
    @ClassKey(NotificationChannels::class)
    @PerUser
    abstract fun bindNotificationChannels(sysui: NotificationChannels): CoreStartable

    /** Inject into RingtonePlayer.  */
    @Binds
    @IntoMap
    @ClassKey(RingtonePlayer::class)
    abstract fun bind(sysui: RingtonePlayer): CoreStartable

    /** Inject into SessionTracker.  */
    @Binds
    @IntoMap
    @ClassKey(SessionTracker::class)
    abstract fun bindSessionTracker(service: SessionTracker): CoreStartable

    /** Inject into StorageNotification.  */
    @Binds
    @IntoMap
    @ClassKey(StorageNotification::class)
    abstract fun bindStorageNotification(sysui: StorageNotification): CoreStartable

    /** Inject into SystemUIOverlayWindowManager.  */
    @Binds
    @IntoMap
    @ClassKey(SystemUIOverlayWindowManager::class)
    abstract fun bindSystemUIOverlayWindowManager(
            sysui: SystemUIOverlayWindowManager
    ): CoreStartable

    /** Inject into ThemeOverlayController.  */
    @Binds
    @IntoMap
    @ClassKey(ThemeOverlayController::class)
    abstract fun bindThemeOverlayController(sysui: ThemeOverlayController): CoreStartable

    /** Inject into WMShell.  */
    @Binds
    @IntoMap
    @ClassKey(WMShell::class)
    abstract fun bindWMShell(sysui: WMShell): CoreStartable

    /** Inject into ActivityWindowManager. */
    @Binds
    @IntoMap
    @ClassKey(ActivityWindowManager::class)
    abstract fun bindActivityWindowManager(
        activityWindowManager: ActivityWindowManager
    ): CoreStartable
}
