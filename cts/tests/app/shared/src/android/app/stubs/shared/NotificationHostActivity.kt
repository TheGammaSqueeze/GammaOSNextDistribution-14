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
package android.app.stubs.shared

import android.R
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.RemoteViews

class NotificationHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val views = (intent.getParcelableExtra(EXTRA_REMOTE_VIEWS) as RemoteViews?)!!
        val height = intent.getIntExtra(EXTRA_HEIGHT, LayoutParams.WRAP_CONTENT)
        setContentView(FrameLayout(this).also {
            val child = views.apply(this, it)
            it.id = R.id.content
            it.addView(child, LayoutParams(LayoutParams.MATCH_PARENT, height))
        })
    }

    val notificationRoot: View
        get() = requireViewById<FrameLayout>(R.id.content).getChildAt(0)

    companion object {
        const val EXTRA_REMOTE_VIEWS = "remote_views"
        const val EXTRA_HEIGHT = "height"
    }
}