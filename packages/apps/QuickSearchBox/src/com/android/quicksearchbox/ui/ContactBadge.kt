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

package com.android.quicksearchbox.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.QuickContactBadge

/**
 * A [QuickContactBadge] that allows setting a click listener. The base class may use
 * [View.setOnClickListener] internally, so this class adds a separate click listener field.
 */
class ContactBadge : QuickContactBadge {
  private var mExtraOnClickListener: View.OnClickListener? = null

  constructor(context: Context?) : super(context)

  constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

  constructor(
    context: Context?,
    attrs: AttributeSet?,
    defStyle: Int
  ) : super(context, attrs, defStyle)

  @Override
  override fun onClick(v: View?) {
    super.onClick(v)
    if (mExtraOnClickListener != null) {
      mExtraOnClickListener?.onClick(v)
    }
  }

  fun setExtraOnClickListener(extraOnClickListener: View.OnClickListener?) {
    mExtraOnClickListener = extraOnClickListener
  }
}
