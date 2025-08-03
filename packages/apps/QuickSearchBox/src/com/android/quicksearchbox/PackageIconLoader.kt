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

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.android.quicksearchbox.util.*
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Loads icons from other packages.
 *
 * Code partly stolen from [ContentResolver] and android.app.SuggestionsAdapter.
 */
class PackageIconLoader(
  context: Context?,
  packageName: String?,
  uiThread: Handler?,
  iconLoaderExecutor: NamedTaskExecutor
) : IconLoader {

  private val mContext: Context?

  private val mPackageName: String?

  private var mPackageContext: Context? = null

  private val mUiThread: Handler?

  private val mIconLoaderExecutor: NamedTaskExecutor

  private fun ensurePackageContext(): Boolean {
    if (mPackageContext == null) {
      mPackageContext =
        try {
          mContext?.createPackageContext(mPackageName, Context.CONTEXT_RESTRICTED)
        } catch (ex: PackageManager.NameNotFoundException) {
          // This should only happen if the app has just be uninstalled
          Log.e(TAG, "Application not found " + mPackageName)
          return false
        }
    }
    return true
  }

  override fun getIcon(drawableId: String?): NowOrLater<Drawable?>? {
    if (DBG) Log.d(TAG, "getIcon($drawableId)")
    if (TextUtils.isEmpty(drawableId) || "0" == drawableId) {
      return Now<Drawable>(null)
    }
    if (!ensurePackageContext()) {
      return Now<Drawable>(null)
    }
    var drawable: NowOrLater<Drawable?>?
    try {
      // First, see if it's just an integer
      val resourceId: Int = drawableId!!.toInt()
      // If so, find it by resource ID
      val icon: Drawable? = ContextCompat.getDrawable(mPackageContext!!, resourceId)
      drawable = Now(icon)
    } catch (nfe: NumberFormatException) {
      // It's not an integer, use it as a URI
      val uri: Uri = Uri.parse(drawableId)
      if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) {
        // load all resources synchronously, to reduce UI flickering
        drawable = Now(getDrawable(uri))
      } else {
        drawable = IconLaterTask(uri)
      }
    } catch (nfe: Resources.NotFoundException) {
      // It was an integer, but it couldn't be found, bail out
      Log.w(TAG, "Icon resource not found: $drawableId")
      drawable = Now(null)
    }
    return drawable
  }

  override fun getIconUri(drawableId: String?): Uri? {
    if (TextUtils.isEmpty(drawableId) || "0" == drawableId) {
      return null
    }
    return if (!ensurePackageContext()) null
    else
      try {
        val resourceId: Int = drawableId!!.toInt()
        Util.getResourceUri(mPackageContext, resourceId)
      } catch (nfe: NumberFormatException) {
        Uri.parse(drawableId)
      }
  }

  /**
   * Gets a drawable by URI.
   *
   * @return A drawable, or `null` if the drawable could not be loaded.
   */
  private fun getDrawable(uri: Uri): Drawable? {
    return try {
      val scheme: String? = uri.getScheme()
      if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
        // Load drawables through Resources, to get the source density information
        val r: OpenResourceIdResult = getResourceId(uri)
        try {
          ContextCompat.getDrawable(mPackageContext!!, r.id)
        } catch (ex: Resources.NotFoundException) {
          throw FileNotFoundException("Resource does not exist: $uri")
        }
      } else {
        // Let the ContentResolver handle content and file URIs.
        val stream: InputStream =
          mPackageContext!!.getContentResolver().openInputStream(uri)
            ?: throw FileNotFoundException("Failed to open $uri")
        try {
          Drawable.createFromStream(stream, null)
        } finally {
          try {
            stream.close()
          } catch (ex: IOException) {
            Log.e(TAG, "Error closing icon stream for $uri", ex)
          }
        }
      }
    } catch (fnfe: FileNotFoundException) {
      Log.w(TAG, "Icon not found: " + uri + ", " + fnfe.message)
      null
    }
  }

  /** A resource identified by the [Resources] that contains it, and a resource id. */
  private inner class OpenResourceIdResult {
    @JvmField var r: Resources? = null

    @JvmField var id = 0
  }

  /** Resolves an android.resource URI to a [Resources] and a resource id. */
  @Throws(FileNotFoundException::class)
  private fun getResourceId(uri: Uri): OpenResourceIdResult {
    val authority: String? = uri.getAuthority()
    val r: Resources? =
      if (TextUtils.isEmpty(authority)) {
        throw FileNotFoundException("No authority: $uri")
      } else {
        try {
          mPackageContext?.getPackageManager()?.getResourcesForApplication(authority!!)
        } catch (ex: NameNotFoundException) {
          throw FileNotFoundException("Failed to get resources: $ex")
        }
      }
    val path: List<String> = uri.getPathSegments() ?: throw FileNotFoundException("No path: $uri")
    val id: Int =
      when (path.size) {
        1 -> {
          try {
            Integer.parseInt(path[0])
          } catch (e: NumberFormatException) {
            throw FileNotFoundException("Single path segment is not a resource ID: $uri")
          }
        }
        2 -> {
          r!!.getIdentifier(path[1], path[0], authority)
        }
        else -> {
          throw FileNotFoundException("More than two path segments: $uri")
        }
      }
    if (id == 0) {
      throw FileNotFoundException("No resource found for: $uri")
    }
    val res = OpenResourceIdResult()
    res.r = r
    res.id = id
    return res
  }

  private inner class IconLaterTask(iconUri: Uri) : CachedLater<Drawable?>(), NamedTask {
    private val mUri: Uri

    @Override
    override fun create() {
      mIconLoaderExecutor.execute(this)
    }

    @Override
    override fun run() {
      val icon: Drawable? = icon
      mUiThread?.post(
        object : Runnable {
          override fun run() {
            store(icon)
          }
        }
      )
    }

    @get:Override
    override val name: String?
      get() = mPackageName

    // we're making a call into another package, which could throw any exception.
    // Make sure it doesn't crash QSB
    private val icon: Drawable?
      get() =
        try {
          getDrawable(mUri)
        } catch (t: Throwable) {
          // we're making a call into another package, which could throw any exception.
          // Make sure it doesn't crash QSB
          Log.e(TAG, "Failed to load icon $mUri", t)
          null
        }

    init {
      mUri = iconUri
    }
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.PackageIconLoader"
  }

  /**
   * Creates a new icon loader.
   *
   * @param context The QSB application context.
   * @param packageName The name of the package from which the icons will be loaded.
   * ```
   *        Resource IDs without an explicit package will be resolved against the package
   *        of this context.
   * ```
   */
  init {
    mContext = context
    mPackageName = packageName
    mUiThread = uiThread
    mIconLoaderExecutor = iconLoaderExecutor
  }
}
