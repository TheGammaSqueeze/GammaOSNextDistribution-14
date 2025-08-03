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

package android.content.res.cts.config.activity

import android.view.LayoutInflater

/**
 * Tests overriding configuration using the [applyOverrideConfiguration] API, but used for a
 * [LayoutInflater], so the override only applies to the inflated views.
 */
class CreateConfigInflaterContextActivity : OverrideConfigBaseActivity() {

    override fun layoutInflater() =
        LayoutInflater.from(createConfigurationContext(makeOverrideConfig()))!!

    override fun configuration() = textSmallestWidth.context.resources.configuration!!
}
