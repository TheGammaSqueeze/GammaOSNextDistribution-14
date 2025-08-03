/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */

package com.android.customization.picker.quickaffordance.domain.interactor

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import com.android.customization.picker.quickaffordance.data.repository.KeyguardQuickAffordancePickerRepository
import com.android.customization.picker.quickaffordance.shared.model.KeyguardQuickAffordancePickerAffordanceModel as AffordanceModel
import com.android.customization.picker.quickaffordance.shared.model.KeyguardQuickAffordancePickerSelectionModel as SelectionModel
import com.android.customization.picker.quickaffordance.shared.model.KeyguardQuickAffordancePickerSlotModel as SlotModel
import com.android.systemui.shared.customization.data.content.CustomizationProviderClient as Client
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow

/**
 * Single entry-point for all application state and business logic related to quick affordances on
 * the lock screen.
 */
class KeyguardQuickAffordancePickerInteractor(
    private val repository: KeyguardQuickAffordancePickerRepository,
    private val client: Client,
    private val snapshotRestorer: Provider<KeyguardQuickAffordanceSnapshotRestorer>,
) {
    /** List of slots available on the device. */
    val slots: Flow<List<SlotModel>> = repository.slots

    /** List of all available quick affordances. */
    val affordances: Flow<List<AffordanceModel>> = repository.affordances

    /** List of slot-affordance pairs, modeling what the user has currently chosen for each slot. */
    val selections: Flow<List<SelectionModel>> = repository.selections

    /**
     * Selects an affordance with the given ID for a slot with the given ID.
     *
     * Note that the maximum affordance per slot is automatically managed. If trying to select an
     * affordance for a slot that's already full, the oldest affordance is removed to make room.
     *
     * Note that if an affordance with the given ID is already selected on the slot with the given
     * ID, that affordance is moved to the newest position on the slot.
     */
    suspend fun select(slotId: String, affordanceId: String) {
        client.insertSelection(
            slotId = slotId,
            affordanceId = affordanceId,
        )

        snapshotRestorer.get().storeSnapshot()
    }

    /** Unselects all affordances from the slot with the given ID. */
    suspend fun unselectAllFromSlot(slotId: String) {
        client.deleteAllSelections(
            slotId = slotId,
        )

        snapshotRestorer.get().storeSnapshot()
    }

    /** Unselects all affordances from all slots. */
    suspend fun unselectAll() {
        client.querySlots().forEach { client.deleteAllSelections(it.id) }
    }

    /** Returns a [Drawable] for the given resource ID, from the system UI package. */
    suspend fun getAffordanceIcon(
        @DrawableRes iconResourceId: Int,
    ): Drawable {
        return client.getAffordanceIcon(iconResourceId)
    }
}
