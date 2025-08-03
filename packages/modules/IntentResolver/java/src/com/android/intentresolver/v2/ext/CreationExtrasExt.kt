/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.v2.ext

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras

/**
 * Returns a new instance with additional [values] added to the existing default args Bundle (if
 * present), otherwise adds a new entry with a copy of this bundle.
 */
fun CreationExtras.addDefaultArgs(vararg values: Pair<String, Parcelable>): CreationExtras {
    val defaultArgs: Bundle = get(DEFAULT_ARGS_KEY) ?: Bundle()
    defaultArgs.putAll(bundleOf(*values))
    return MutableCreationExtras(this).apply { set(DEFAULT_ARGS_KEY, defaultArgs) }
}
