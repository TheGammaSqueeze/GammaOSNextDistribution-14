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
package com.android.intentresolver.shortcuts

import android.app.prediction.AppPredictionContext
import android.app.prediction.AppPredictionManager
import android.app.prediction.AppPredictor
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.os.UserHandle

// TODO(b/123088566) Share these in a better way.
private const val APP_PREDICTION_SHARE_UI_SURFACE = "share"
private const val APP_PREDICTION_SHARE_TARGET_QUERY_PACKAGE_LIMIT = 20
private const val APP_PREDICTION_INTENT_FILTER_KEY = "intent_filter"
private const val SHARED_TEXT_KEY = "shared_text"

/**
 * A factory to create an AppPredictor instance for a profile, if available.
 * @param context, application context
 * @param sharedText, a shared text associated with the Chooser's target intent
 * (see [android.content.Intent.EXTRA_TEXT]).
 * Will be mapped to app predictor's "shared_text" parameter.
 * @param targetIntentFilter, an IntentFilter to match direct share targets against.
 * Will be mapped app predictor's "intent_filter" parameter.
 */
class AppPredictorFactory(
    private val context: Context,
    private val sharedText: String?,
    private val targetIntentFilter: IntentFilter?,
    private val appPredictionAvailable: Boolean,
) {
    /**
     * Creates an AppPredictor instance for a profile or `null` if app predictor is not available.
     */
    fun create(userHandle: UserHandle): AppPredictor? {
        if (!appPredictionAvailable) return null
        val contextAsUser = context.createContextAsUser(userHandle, 0 /* flags */)
        val extras = Bundle().apply {
            putParcelable(APP_PREDICTION_INTENT_FILTER_KEY, targetIntentFilter)
            putString(SHARED_TEXT_KEY, sharedText)
        }
        val appPredictionContext = AppPredictionContext.Builder(contextAsUser)
            .setUiSurface(APP_PREDICTION_SHARE_UI_SURFACE)
            .setPredictedTargetCount(APP_PREDICTION_SHARE_TARGET_QUERY_PACKAGE_LIMIT)
            .setExtras(extras)
            .build()
        return contextAsUser.getSystemService(AppPredictionManager::class.java)
            ?.createAppPredictionSession(appPredictionContext)
    }
}
