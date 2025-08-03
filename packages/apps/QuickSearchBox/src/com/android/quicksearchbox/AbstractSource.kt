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

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.android.quicksearchbox.util.NamedTaskExecutor
import com.android.quicksearchbox.util.NowOrLater

/** Abstract suggestion source implementation. */
abstract class AbstractSource(
  context: Context?,
  uiThread: Handler?,
  iconLoader: NamedTaskExecutor
) : Source {
  private val mContext: Context?
  private val mUiThread: Handler?
  private var mIconLoader: IconLoader? = null
  private val mIconLoaderExecutor: NamedTaskExecutor
  protected val context: Context?
    get() = mContext
  protected val iconLoader: IconLoader?
    get() {
      if (mIconLoader == null) {
        val iconPackage = iconPackage
        mIconLoader =
          CachingIconLoader(
            PackageIconLoader(mContext, iconPackage, mUiThread, mIconLoaderExecutor)
          )
      }
      return mIconLoader
    }
  protected abstract val iconPackage: String

  @Override
  override fun getIcon(drawableId: String?): NowOrLater<Drawable?>? {
    return iconLoader?.getIcon(drawableId)
  }

  @Override
  override fun getIconUri(drawableId: String?): Uri? {
    return iconLoader?.getIconUri(drawableId)
  }

  @Override
  override fun createSearchIntent(query: String?, appData: Bundle?): Intent? {
    return createSourceSearchIntent(intentComponent, query, appData)
  }

  protected fun createVoiceWebSearchIntent(appData: Bundle?): Intent? {
    return QsbApplication.get(mContext).voiceSearch?.createVoiceWebSearchIntent(appData)
  }

  override fun getRoot(): Source {
    return this
  }

  @Override
  override fun equals(other: Any?): Boolean {
    if (other is Source) {
      val s: Source = other.getRoot()
      if (s::class == this::class) {
        return s.name.equals(name)
      }
    }
    return false
  }

  @Override
  override fun hashCode(): Int {
    return name.hashCode()
  }

  @Override
  override fun toString(): String {
    return "Source{name=" + name.toString() + "}"
  }

  companion object {
    private const val TAG = "QSB.AbstractSource"

    @JvmStatic
    fun createSourceSearchIntent(
      activity: ComponentName?,
      query: String?,
      appData: Bundle?
    ): Intent? {
      if (activity == null) {
        Log.w(TAG, "Tried to create search intent with no target activity")
        return null
      }
      val intent = Intent(Intent.ACTION_SEARCH)
      intent.setComponent(activity)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      // We need CLEAR_TOP to avoid reusing an old task that has other activities
      // on top of the one we want.
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      intent.putExtra(SearchManager.USER_QUERY, query)
      intent.putExtra(SearchManager.QUERY, query)
      if (appData != null) {
        intent.putExtra(SearchManager.APP_DATA, appData)
      }
      return intent
    }
  }

  init {
    mContext = context
    mUiThread = uiThread
    mIconLoaderExecutor = iconLoader
  }
}
