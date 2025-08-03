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
package com.android.quicksearchbox

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import com.android.quicksearchbox.util.NamedTaskExecutor

/** Abstract implementation of a source that is not backed by a searchable activity. */
abstract class AbstractInternalSource(
  context: Context?,
  uiThread: Handler?,
  iconLoader: NamedTaskExecutor
) : AbstractSource(context, uiThread, iconLoader) {
  @get:Override
  override val suggestUri: String?
    get() = null

  @Override
  override fun canRead(): Boolean {
    return true
  }

  override val defaultIntentData: String?
    get() = null

  @get:Override
  override val iconPackage: String
    get() = context!!.getPackageName()

  @get:Override
  override val queryThreshold: Int
    get() = 0

  @get:Override
  override val sourceIcon: Drawable
    get() = context?.getResources()!!.getDrawable(sourceIconResource, null)

  @get:Override
  override val sourceIconUri: Uri
    get() =
      Uri.parse(
        "android.resource://" + context!!.getPackageName().toString() + "/" + sourceIconResource
      )
  protected abstract val sourceIconResource: Int

  @Override
  override fun queryAfterZeroResults(): Boolean {
    return true
  }
}
