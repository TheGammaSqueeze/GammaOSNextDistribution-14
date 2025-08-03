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

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView

/** Activity that looks like a dialog window. */
abstract class DialogActivity : Activity() {
  @JvmField protected var mTitleView: TextView? = null

  @JvmField protected var mContentFrame: FrameLayout? = null

  @Override
  protected override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    getWindow().requestFeature(Window.FEATURE_NO_TITLE)
    setContentView(R.layout.dialog_activity)
    mTitleView = findViewById(R.id.alertTitle) as TextView?
    mContentFrame = findViewById(R.id.content) as FrameLayout?
  }

  fun setHeading(titleRes: Int) {
    mTitleView?.setText(titleRes)
  }

  fun setHeading(title: CharSequence?) {
    mTitleView?.setText(title)
  }

  fun setDialogContent(layoutRes: Int) {
    mContentFrame?.removeAllViews()
    getLayoutInflater().inflate(layoutRes, mContentFrame)
  }

  fun setDialogContent(content: View?) {
    mContentFrame?.removeAllViews()
    mContentFrame?.addView(content)
  }
}
