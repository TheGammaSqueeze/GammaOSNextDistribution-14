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
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.ContextThemeWrapper
import com.android.quicksearchbox.google.GoogleSource
import com.android.quicksearchbox.google.GoogleSuggestClient
import com.android.quicksearchbox.google.SearchBaseUrlHelper
import com.android.quicksearchbox.ui.DefaultSuggestionViewFactory
import com.android.quicksearchbox.ui.SuggestionViewFactory
import com.android.quicksearchbox.util.*
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class QsbApplication(context: Context?) {
  private val mContext: Context?

  private var mVersionCode: Long = 0
  private var mUiThreadHandler: Handler? = null
  private var mConfig: Config? = null
  private var mSettings: SearchSettings? = null
  private var mSourceTaskExecutor: NamedTaskExecutor? = null
  private var mQueryThreadFactory: ThreadFactory? = null
  private var mSuggestionsProvider: SuggestionsProvider? = null
  private var mSuggestionViewFactory: SuggestionViewFactory? = null
  private var mGoogleSource: GoogleSource? = null
  private var mVoiceSearch: VoiceSearch? = null
  private var mLogger: Logger? = null
  private var mSuggestionFormatter: SuggestionFormatter? = null
  private var mTextAppearanceFactory: TextAppearanceFactory? = null
  private var mIconLoaderExecutor: NamedTaskExecutor? = null
  private var mHttpHelper: HttpHelper? = null
  private var mSearchBaseUrlHelper: SearchBaseUrlHelper? = null
  protected val context: Context?
    get() = mContext

  // The current package should always exist, how else could we
  // run code from it?
  val versionCode: Long
    @Suppress("DEPRECATION")
    get() {
      if (mVersionCode == 0L) {
        mVersionCode =
          try {
            val pm: PackageManager? = context?.getPackageManager()
            val pkgInfo: PackageInfo? = pm?.getPackageInfo(context!!.getPackageName(), 0)
            pkgInfo!!.getLongVersionCode()
          } catch (ex: PackageManager.NameNotFoundException) {
            // The current package should always exist, how else could we
            // run code from it?
            throw RuntimeException(ex)
          }
      }
      return mVersionCode
    }

  protected fun checkThread() {
    if (Looper.myLooper() !== Looper.getMainLooper()) {
      throw IllegalStateException(
        "Accessed Application object from thread " + Thread.currentThread().getName()
      )
    }
  }

  fun close() {
    checkThread()
    if (mConfig != null) {
      mConfig!!.close()
      mConfig = null
    }
    if (mSuggestionsProvider != null) {
      mSuggestionsProvider!!.close()
      mSuggestionsProvider = null
    }
  }

  @get:Synchronized
  val mainThreadHandler: Handler?
    get() {
      if (mUiThreadHandler == null) {
        mUiThreadHandler = Handler(Looper.getMainLooper())
      }
      return mUiThreadHandler
    }

  fun runOnUiThread(action: Runnable?) {
    mainThreadHandler?.post(action!!)
  }

  @get:Synchronized
  val iconLoaderExecutor: NamedTaskExecutor?
    get() {
      if (mIconLoaderExecutor == null) {
        mIconLoaderExecutor = createIconLoaderExecutor()
      }
      return mIconLoaderExecutor
    }

  protected fun createIconLoaderExecutor(): NamedTaskExecutor {
    val iconThreadFactory: ThreadFactory = PriorityThreadFactory(Process.THREAD_PRIORITY_BACKGROUND)
    return PerNameExecutor(SingleThreadNamedTaskExecutor.factory(iconThreadFactory))
  }

  /** Indicates that construction of the QSB UI is now complete. */
  fun onStartupComplete() {}

  /** Gets the QSB configuration object. May be called from any thread. */
  @get:Synchronized
  val config: Config?
    get() {
      if (mConfig == null) {
        mConfig = createConfig()
      }
      return mConfig
    }

  protected fun createConfig(): Config {
    return Config(context)
  }

  @get:Synchronized
  val settings: SearchSettings?
    get() {
      if (mSettings == null) {
        mSettings = createSettings()
        mSettings!!.upgradeSettingsIfNeeded()
      }
      return mSettings
    }

  protected fun createSettings(): SearchSettings {
    return SearchSettingsImpl(context, config)
  }

  protected fun createExecutorFactory(numThreads: Int): Factory<Executor?> {
    val threadFactory: ThreadFactory? = queryThreadFactory
    return object : Factory<Executor?> {
      @Override
      override fun create(): Executor {
        return Executors.newFixedThreadPool(numThreads, threadFactory)
      }
    }
  }

  /** Gets the source task executor. May only be called from the main thread. */
  val sourceTaskExecutor: NamedTaskExecutor?
    get() {
      checkThread()
      if (mSourceTaskExecutor == null) {
        mSourceTaskExecutor = createSourceTaskExecutor()
      }
      return mSourceTaskExecutor
    }

  protected fun createSourceTaskExecutor(): NamedTaskExecutor {
    val queryThreadFactory: ThreadFactory? = queryThreadFactory
    return PerNameExecutor(SingleThreadNamedTaskExecutor.factory(queryThreadFactory))
  }

  /** Gets the query thread factory. May only be called from the main thread. */
  protected val queryThreadFactory: ThreadFactory?
    get() {
      checkThread()
      if (mQueryThreadFactory == null) {
        mQueryThreadFactory = createQueryThreadFactory()
      }
      return mQueryThreadFactory
    }

  protected fun createQueryThreadFactory(): ThreadFactory {
    val nameFormat = "QSB #%d"
    val priority: Int = config!!.queryThreadPriority
    return ThreadFactoryBuilder()
      .setNameFormat(nameFormat)
      .setThreadFactory(PriorityThreadFactory(priority))
      .build()
  }

  /**
   * Gets the suggestion provider.
   *
   * May only be called from the main thread.
   */
  val suggestionsProvider: SuggestionsProvider?
    get() {
      checkThread()
      if (mSuggestionsProvider == null) {
        mSuggestionsProvider = createSuggestionsProvider()
      }
      return mSuggestionsProvider
    }

  protected fun createSuggestionsProvider(): SuggestionsProvider {
    return SuggestionsProviderImpl(config!!, sourceTaskExecutor!!, mainThreadHandler, logger)
  }

  /** Gets the default suggestion view factory. May only be called from the main thread. */
  val suggestionViewFactory: SuggestionViewFactory?
    get() {
      checkThread()
      if (mSuggestionViewFactory == null) {
        mSuggestionViewFactory = createSuggestionViewFactory()
      }
      return mSuggestionViewFactory
    }

  protected fun createSuggestionViewFactory(): SuggestionViewFactory {
    return DefaultSuggestionViewFactory(context)
  }

  /** Gets the Google source. May only be called from the main thread. */
  val googleSource: GoogleSource?
    get() {
      checkThread()
      if (mGoogleSource == null) {
        mGoogleSource = createGoogleSource()
      }
      return mGoogleSource
    }

  protected fun createGoogleSource(): GoogleSource {
    return GoogleSuggestClient(context, mainThreadHandler, iconLoaderExecutor!!, config!!)
  }

  /** Gets Voice Search utilities. */
  val voiceSearch: VoiceSearch?
    get() {
      checkThread()
      if (mVoiceSearch == null) {
        mVoiceSearch = createVoiceSearch()
      }
      return mVoiceSearch
    }

  protected fun createVoiceSearch(): VoiceSearch {
    return VoiceSearch(context)
  }

  /** Gets the event logger. May only be called from the main thread. */
  val logger: Logger?
    get() {
      checkThread()
      if (mLogger == null) {
        mLogger = createLogger()
      }
      return mLogger
    }

  protected fun createLogger(): Logger {
    return EventLogLogger(context, config!!)
  }

  val suggestionFormatter: SuggestionFormatter?
    get() {
      if (mSuggestionFormatter == null) {
        mSuggestionFormatter = createSuggestionFormatter()
      }
      return mSuggestionFormatter
    }

  protected fun createSuggestionFormatter(): SuggestionFormatter {
    return LevenshteinSuggestionFormatter(textAppearanceFactory)
  }

  val textAppearanceFactory: TextAppearanceFactory?
    get() {
      if (mTextAppearanceFactory == null) {
        mTextAppearanceFactory = createTextAppearanceFactory()
      }
      return mTextAppearanceFactory
    }

  protected fun createTextAppearanceFactory(): TextAppearanceFactory {
    return TextAppearanceFactory(context)
  }

  @get:Synchronized
  val httpHelper: HttpHelper?
    get() {
      if (mHttpHelper == null) {
        mHttpHelper = createHttpHelper()
      }
      return mHttpHelper
    }

  protected fun createHttpHelper(): HttpHelper {
    return JavaNetHttpHelper(JavaNetHttpHelper.PassThroughRewriter(), config!!.userAgent)
  }

  @get:Synchronized
  val searchBaseUrlHelper: SearchBaseUrlHelper?
    get() {
      if (mSearchBaseUrlHelper == null) {
        mSearchBaseUrlHelper = createSearchBaseUrlHelper()
      }
      return mSearchBaseUrlHelper
    }

  protected fun createSearchBaseUrlHelper(): SearchBaseUrlHelper {
    // This cast to "SearchSettingsImpl" is somewhat ugly.
    return SearchBaseUrlHelper(
      context,
      httpHelper!!,
      settings!!,
      (settings as SearchSettingsImpl?)!!.searchPreferences
    )
  }

  // No point caching this, it's super cheap.
  val help: Help
    get() = // No point caching this, it's super cheap.
    Help(context, config!!)

  companion object {
    val isFroyoOrLater: Boolean
      get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO
    val isHoneycombOrLater: Boolean
      get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB

    @JvmStatic
    operator fun get(context: Context?): QsbApplication {
      return (context?.getApplicationContext() as QsbApplicationWrapper).app
    }
  }

  init {
    // the application context does not use the theme from the <application> tag
    mContext = ContextThemeWrapper(context, R.style.Theme_QuickSearchBox)
  }
}
