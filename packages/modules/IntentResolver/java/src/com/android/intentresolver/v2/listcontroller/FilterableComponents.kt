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

package com.android.intentresolver.v2.listcontroller

import android.content.ComponentName
import com.android.intentresolver.ChooserRequestParameters

/** A class that is able to identify components that should be hidden from the user. */
interface FilterableComponents {
    /** Whether this component should hidden from the user. */
    fun isComponentFiltered(name: ComponentName): Boolean
}

/** A class that never filters components. */
class NoComponentFiltering : FilterableComponents {
    override fun isComponentFiltered(name: ComponentName): Boolean = false
}

/** A class that filters components by chooser request filter. */
class ChooserRequestFilteredComponents(
    private val chooserRequestParameters: ChooserRequestParameters,
) : FilterableComponents {
    override fun isComponentFiltered(name: ComponentName): Boolean =
        chooserRequestParameters.filteredComponentNames.contains(name)
}
