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

import android.content.BroadcastReceiver
import com.android.systemui.GuestResetOrExitSessionReceiver
import com.android.systemui.media.dialog.MediaOutputDialogReceiver
import com.android.systemui.volume.VolumePanelDialogReceiver
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** BroadcastReceivers that are injectable should go here. */
@Module
abstract class TvBroadcastReceiverBinder {
  @Binds
  @IntoMap
  @ClassKey(MediaOutputDialogReceiver::class)
  abstract fun bindMediaOutputDialogReceiver(
      broadcastReceiver: MediaOutputDialogReceiver
  ): BroadcastReceiver

  @Binds
  @IntoMap
  @ClassKey(VolumePanelDialogReceiver::class)
  abstract fun bindVolumePanelDialogReceiver(
      broadcastReceiver: VolumePanelDialogReceiver
  ): BroadcastReceiver

  @Binds
  @IntoMap
  @ClassKey(GuestResetOrExitSessionReceiver::class)
  abstract fun bindGuestResetOrExitSessionReceiver(
      broadcastReceiver: GuestResetOrExitSessionReceiver
  ): BroadcastReceiver
}
