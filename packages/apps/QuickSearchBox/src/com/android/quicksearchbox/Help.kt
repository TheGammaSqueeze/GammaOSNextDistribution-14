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
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

/** Handles app help. */
class Help(context: Context?, config: Config) {
  private val mContext: Context?
  private val mConfig: Config
  fun addHelpMenuItem(menu: Menu, activityName: String?) {
    addHelpMenuItem(menu, activityName, false)
  }

  fun addHelpMenuItem(menu: Menu, activityName: String?, showAsAction: Boolean) {
    val helpIntent: Intent? = getHelpIntent(activityName)
    if (helpIntent != null) {
      val inflater = MenuInflater(mContext)
      inflater.inflate(R.menu.help, menu)
      val item: MenuItem = menu.findItem(R.id.menu_help)
      item.setIntent(helpIntent)
      if (showAsAction) {
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
      }
    }
  }

  private fun getHelpIntent(activityName: String?): Intent? {
    val helpUrl: Uri = mConfig.getHelpUrl(activityName) ?: return null
    return Intent(Intent.ACTION_VIEW, helpUrl)
  }

  init {
    mContext = context
    mConfig = config
  }
}
