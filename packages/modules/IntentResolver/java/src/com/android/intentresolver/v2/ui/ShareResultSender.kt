/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.v2.ui

import android.app.Activity
import android.app.compat.CompatChanges
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.service.chooser.ChooserResult
import android.service.chooser.ChooserResult.CHOOSER_RESULT_COPY
import android.service.chooser.ChooserResult.CHOOSER_RESULT_EDIT
import android.service.chooser.ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT
import android.service.chooser.ChooserResult.CHOOSER_RESULT_UNKNOWN
import android.service.chooser.ChooserResult.ResultType
import android.util.Log
import com.android.intentresolver.inject.Background
import com.android.intentresolver.inject.ChooserServiceFlags
import com.android.intentresolver.inject.Main
import com.android.intentresolver.v2.ui.model.ShareAction
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ShareResultSender"

/** Reports the result of a share to another process across binder, via an [IntentSender] */
interface ShareResultSender {
    /** Reports user selection of an activity to launch from the provided choices. */
    fun onComponentSelected(component: ComponentName, directShare: Boolean)

    /** Reports user invocation of a built-in system action. See [ShareAction]. */
    fun onActionSelected(action: ShareAction)
}

@AssistedFactory
interface ShareResultSenderFactory {
    fun create(callerUid: Int, chosenComponentSender: IntentSender): ShareResultSenderImpl
}

/** Dispatches Intents via IntentSender */
fun interface IntentSenderDispatcher {
    fun dispatchIntent(intentSender: IntentSender, intent: Intent)
}

class ShareResultSenderImpl(
    private val flags: ChooserServiceFlags,
    @Main private val scope: CoroutineScope,
    @Background val backgroundDispatcher: CoroutineDispatcher,
    private val callerUid: Int,
    private val resultSender: IntentSender,
    private val intentDispatcher: IntentSenderDispatcher
) : ShareResultSender {
    @AssistedInject
    constructor(
        @ActivityContext context: Context,
        flags: ChooserServiceFlags,
        @Main scope: CoroutineScope,
        @Background backgroundDispatcher: CoroutineDispatcher,
        @Assisted callerUid: Int,
        @Assisted chosenComponentSender: IntentSender,
    ) : this(
        flags,
        scope,
        backgroundDispatcher,
        callerUid,
        chosenComponentSender,
        IntentSenderDispatcher { sender, intent -> sender.dispatchIntent(context, intent) }
    )

    override fun onComponentSelected(component: ComponentName, directShare: Boolean) {
        Log.i(TAG, "onComponentSelected: $component directShare=$directShare")
        scope.launch {
            val intent = createChosenComponentIntent(component, directShare)
            intentDispatcher.dispatchIntent(resultSender, intent)
        }
    }

    override fun onActionSelected(action: ShareAction) {
        Log.i(TAG, "onActionSelected: $action")
        scope.launch {
            if (flags.enableChooserResult() && chooserResultSupported(callerUid)) {
                @ResultType val chosenAction = shareActionToChooserResult(action)
                val intent: Intent = createSelectedActionIntent(chosenAction)
                intentDispatcher.dispatchIntent(resultSender, intent)
            } else {
                Log.i(TAG, "Not sending SelectedAction")
            }
        }
    }

    private suspend fun createChosenComponentIntent(
        component: ComponentName,
        direct: Boolean,
    ): Intent {
        // Add extra with component name for backwards compatibility.
        val intent: Intent = Intent().putExtra(Intent.EXTRA_CHOSEN_COMPONENT, component)

        // Add ChooserResult value for Android V+
        if (flags.enableChooserResult() && chooserResultSupported(callerUid)) {
            intent.putExtra(
                Intent.EXTRA_CHOOSER_RESULT,
                ChooserResult(CHOOSER_RESULT_SELECTED_COMPONENT, component, direct)
            )
        } else {
            Log.i(TAG, "Not including ${Intent.EXTRA_CHOOSER_RESULT}")
        }
        return intent
    }

    @ResultType
    private fun shareActionToChooserResult(action: ShareAction) =
        when (action) {
            ShareAction.SYSTEM_COPY -> CHOOSER_RESULT_COPY
            ShareAction.SYSTEM_EDIT -> CHOOSER_RESULT_EDIT
            ShareAction.APPLICATION_DEFINED -> CHOOSER_RESULT_UNKNOWN
        }

    private fun createSelectedActionIntent(@ResultType result: Int): Intent {
        return Intent().putExtra(Intent.EXTRA_CHOOSER_RESULT, ChooserResult(result, null, false))
    }

    private suspend fun chooserResultSupported(uid: Int): Boolean {
        return withContext(backgroundDispatcher) {
            // background -> Binder call to system_server
            CompatChanges.isChangeEnabled(ChooserResult.SEND_CHOOSER_RESULT, uid)
        }
    }
}

private fun IntentSender.dispatchIntent(context: Context, intent: Intent) {
    try {
        sendIntent(
            /* context = */ context,
            /* code = */ Activity.RESULT_OK,
            /* intent = */ intent,
            /* onFinished = */ null,
            /* handler = */ null
        )
    } catch (e: IntentSender.SendIntentException) {
        Log.e(TAG, "Failed to send intent to IntentSender", e)
    }
}
