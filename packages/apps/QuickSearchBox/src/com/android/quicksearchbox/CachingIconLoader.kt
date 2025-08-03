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

import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import com.android.quicksearchbox.util.*
import java.util.WeakHashMap

/** Icon loader that caches the results of another icon loader. */
class CachingIconLoader(private val mWrapped: IconLoader) : IconLoader {
  private val mIconCache: WeakHashMap<String, Entry>
  override fun getIcon(drawableId: String?): NowOrLater<Drawable?>? {
    if (DBG) Log.d(TAG, "getIcon($drawableId)")
    if (TextUtils.isEmpty(drawableId) || "0".equals(drawableId)) {
      return Now<Drawable>(null)
    }
    var newEntry: Entry? = null
    var drawableState: NowOrLater<Drawable.ConstantState?>?
    synchronized(this) {
      drawableState = queryCache(drawableId)
      if (drawableState == null) {
        newEntry = Entry()
        storeInIconCache(drawableId, newEntry)
      }
    }
    if (drawableState != null) {
      return object : NowOrLaterWrapper<Drawable.ConstantState?, Drawable?>(drawableState!!) {
        @Override
        override operator fun get(value: Drawable.ConstantState?): Drawable? {
          return if (value == null) null else value.newDrawable()
        }
      }
    }
    val drawable: NowOrLater<Drawable?>? = mWrapped.getIcon(drawableId)
    newEntry?.set(drawable)
    storeInIconCache(drawableId, newEntry)
    return drawable!!
  }

  override fun getIconUri(drawableId: String?): Uri? {
    return mWrapped.getIconUri(drawableId)
  }

  @Synchronized
  private fun queryCache(drawableId: String?): NowOrLater<Drawable.ConstantState?>? {
    val cached: Entry? = mIconCache.get(drawableId)
    if (DBG) {
      if (cached != null) Log.d(TAG, "Found icon in cache: $drawableId")
    }
    return cached
  }

  @Synchronized
  private fun storeInIconCache(resourceUri: String?, drawable: Entry?) {
    if (drawable != null) {
      mIconCache.put(resourceUri, drawable)
    }
  }

  private class Entry : CachedLater<Drawable.ConstantState?>(), Consumer<Drawable?> {
    private var mDrawable: NowOrLater<Drawable?>? = null
    private var mGotDrawable = false
    private var mCreateRequested = false

    @Synchronized
    fun set(drawable: NowOrLater<Drawable?>?) {
      if (mGotDrawable) throw IllegalStateException("set() may only be called once.")
      mGotDrawable = true
      mDrawable = drawable
      if (mCreateRequested) {
        later
      }
    }

    @Override
    @Synchronized
    override fun create() {
      if (!mCreateRequested) {
        mCreateRequested = true
        if (mGotDrawable) {
          later
        }
      }
    }

    private val later: Unit
      get() {
        val drawable: NowOrLater<Drawable?>? = mDrawable
        mDrawable = null
        drawable!!.getLater(this)
      }

    override fun consume(value: Drawable?): Boolean {
      store(if (value == null) null else value.getConstantState())
      return true
    }
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.CachingIconLoader"
  }

  /**
   * Creates a new caching icon loader.
   *
   * @param wrapped IconLoader whose results will be cached.
   */
  init {
    mIconCache = WeakHashMap<String, Entry>()
  }
}
