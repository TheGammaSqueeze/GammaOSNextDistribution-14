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

package com.android.quicksearchbox.util

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.util.Log

/** General utilities. */
object Util {

  private const val TAG = "QSB.Util"

  fun <A> setOfFirstN(list: List<A>, n: Int): Set<A> {
    val end: Int = Math.min(list.size, n)
    val set: HashSet<A> = hashSetOf()
    for (i in 0 until end) {
      set.add(list[i])
    }
    return set
  }

  fun getResourceUri(packageContext: Context?, res: Int): Uri? {
    return try {
      val resources: Resources? = packageContext?.getResources()
      getResourceUri(resources, packageContext?.getPackageName(), res)
    } catch (e: Resources.NotFoundException) {
      Log.e(TAG, "Resource not found: " + res + " in " + packageContext?.getPackageName())
      null
    }
  }

  fun getResourceUri(context: Context?, appInfo: ApplicationInfo?, res: Int): Uri? {
    return try {
      val resources: Resources? =
        context?.getPackageManager()?.getResourcesForApplication(appInfo!!)
      getResourceUri(resources, appInfo?.packageName, res)
    } catch (e: PackageManager.NameNotFoundException) {
      Log.e(TAG, "Resources not found for " + appInfo?.packageName)
      null
    } catch (e: Resources.NotFoundException) {
      Log.e(TAG, "Resource not found: " + res + " in " + appInfo?.packageName)
      null
    }
  }

  @Throws(Resources.NotFoundException::class)
  private fun getResourceUri(resources: Resources?, appPkg: String?, res: Int): Uri {
    val resPkg: String? = resources?.getResourcePackageName(res)
    val type: String? = resources?.getResourceTypeName(res)
    val name: String? = resources?.getResourceEntryName(res)
    return makeResourceUri(appPkg, resPkg, type, name)
  }

  private fun makeResourceUri(appPkg: String?, resPkg: String?, type: String?, name: String?): Uri {
    val uriBuilder: Uri.Builder = Uri.Builder()
    uriBuilder.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
    uriBuilder.encodedAuthority(appPkg)
    uriBuilder.appendEncodedPath(type)
    if (appPkg != resPkg) {
      uriBuilder.appendEncodedPath("$resPkg:$name")
    } else {
      uriBuilder.appendEncodedPath(name)
    }
    return uriBuilder.build()
  }
}
