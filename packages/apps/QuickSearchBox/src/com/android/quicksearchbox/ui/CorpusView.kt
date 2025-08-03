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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.ViewDebug
import android.widget.Checkable
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.android.quicksearchbox.R

/** A corpus in the corpus selection list. */
class CorpusView : RelativeLayout, Checkable {
  private var mIcon: ImageView? = null
  private var mLabel: TextView? = null
  private var mChecked = false

  constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
  constructor(context: Context?) : super(context) {}

  @Override
  protected override fun onFinishInflate() {
    super.onFinishInflate()
    mIcon = findViewById(R.id.source_icon) as ImageView?
    mLabel = findViewById(R.id.source_label) as TextView?
  }

  fun setLabel(label: CharSequence?) {
    mLabel?.setText(label)
  }

  fun setIcon(icon: Drawable?) {
    mIcon?.setImageDrawable(icon)
  }

  @Override
  @ViewDebug.ExportedProperty
  override fun isChecked(): Boolean {
    return mChecked
  }

  @Override
  override fun setChecked(checked: Boolean) {
    if (mChecked != checked) {
      mChecked = checked
      refreshDrawableState()
    }
  }

  @Override
  override fun toggle() {
    isChecked = !mChecked
  }

  @Override
  protected override fun onCreateDrawableState(extraSpace: Int): IntArray {
    val drawableState: IntArray = super.onCreateDrawableState(extraSpace + 1)
    if (isChecked) {
      mergeDrawableStates(drawableState, CHECKED_STATE_SET)
    }
    return drawableState
  }

  companion object {
    private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
  }
}
