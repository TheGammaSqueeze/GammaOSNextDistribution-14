/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.v2

import android.service.chooser.ChooserAction
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi
import com.android.intentresolver.contentpreview.MutableActionFactory
import com.android.intentresolver.widget.ActionRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A wrapper around [ChooserActionFactory] that provides observable custom actions */
class ChooserMutableActionFactory(
    private val actionFactory: ChooserActionFactory,
) : MutableActionFactory, ChooserContentPreviewUi.ActionFactory by actionFactory {
    private val customActions =
        MutableStateFlow<List<ActionRow.Action>>(actionFactory.createCustomActions())

    override val customActionsFlow: Flow<List<ActionRow.Action>>
        get() = customActions

    override fun updateCustomActions(actions: List<ChooserAction>) {
        customActions.tryEmit(mapChooserActions(actions))
    }

    override fun createCustomActions(): List<ActionRow.Action> = customActions.value

    private fun mapChooserActions(chooserActions: List<ChooserAction>): List<ActionRow.Action> =
        buildList(chooserActions.size) {
            chooserActions.forEachIndexed { i, chooserAction ->
                val actionRow =
                    actionFactory.createCustomAction(chooserAction) {
                        actionFactory.logCustomAction(i)
                    }
                if (actionRow != null) {
                    add(actionRow)
                }
            }
        }
}
