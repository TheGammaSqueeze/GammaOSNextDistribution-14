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
 */

package com.android.intentresolver.widget

import android.content.res.Resources.ID_NULL
import android.graphics.drawable.Drawable

interface ActionRow {
    fun setActions(actions: List<Action>)

    class Action @JvmOverloads constructor(
        // TODO: apparently, IDs set to this field are used in unit tests only; evaluate whether we
        //  get rid of them
        val id: Int = ID_NULL,
        val label: CharSequence?,
        val icon: Drawable?,
        val onClicked: Runnable,
    )
}
