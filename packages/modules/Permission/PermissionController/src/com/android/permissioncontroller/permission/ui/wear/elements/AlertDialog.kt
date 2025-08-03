/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.wear.elements

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import kotlinx.coroutines.launch

/**
 * This component is an alternative to [Alert], providing the following:
 * - a convenient way of passing a title and a message;
 * - default positive and negative buttons;
 * - wrapped in a [Dialog];
 */
@Composable
fun AlertDialog(
    message: String,
    iconRes: Int? = null,
    onCancelButtonClick: () -> Unit,
    onOKButtonClick: () -> Unit,
    showDialog: Boolean,
    scalingLazyListState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    title: String = "",
    okButtonContentDescription: String = stringResource(android.R.string.ok),
    cancelButtonContentDescription: String = stringResource(android.R.string.cancel)
) {
    Dialog(
        showDialog = showDialog,
        onDismissRequest = onCancelButtonClick,
        scrollState = scalingLazyListState,
        modifier = modifier
    ) {
        Alert(
            title = title,
            iconRes = iconRes,
            body = message,
            scrollState = scalingLazyListState,
            onCancelButtonClick = onCancelButtonClick,
            onOKButtonClick = onOKButtonClick,
            okButtonContentDescription = okButtonContentDescription,
            cancelButtonContentDescription = cancelButtonContentDescription
        )
    }
}

/**
 * This component is an alternative to [Alert], providing the following:
 * - a convenient way of passing a title and a message;
 * - default one button;
 * - wrapped in a [Dialog];
 */
@Composable
fun SingleButtonAlertDialog(
    message: String,
    iconRes: Int? = null,
    onButtonClick: () -> Unit,
    showDialog: Boolean,
    scalingLazyListState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    title: String = "",
    buttonContentDescription: String = stringResource(android.R.string.ok)
) {
    Dialog(
        showDialog = showDialog,
        onDismissRequest = {},
        scrollState = scalingLazyListState,
        modifier = modifier
    ) {
        SingleButtonAlert(
            title = title,
            iconRes = iconRes,
            body = message,
            scrollState = scalingLazyListState,
            onButtonClick = onButtonClick,
            buttonContentDescription = buttonContentDescription
        )
    }
}

@Composable
internal fun Alert(
    title: String,
    iconRes: Int? = null,
    body: String,
    scrollState: ScalingLazyListState,
    onCancelButtonClick: () -> Unit,
    onOKButtonClick: () -> Unit,
    okButtonContentDescription: String,
    cancelButtonContentDescription: String
) {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    Alert(
        modifier =
            Modifier.onRotaryScrollEvent {
                    coroutineScope.launch {
                        scrollState.scrollBy(it.verticalScrollPixels)
                        scrollState.animateScrollBy(0f)
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
        contentPadding = DefaultContentPadding(),
        scrollState = scrollState,
        title = { AlertTitleText(title) },
        icon = { AlertIcon(iconRes) },
        content = { AlertBodyText(body) },
        negativeButton = { NegativeButton(onCancelButtonClick, cancelButtonContentDescription) },
        positiveButton = { PositiveButton(onOKButtonClick, okButtonContentDescription) }
    )
    RequestFocusOnResume(focusRequester = focusRequester)
}

@Composable
private fun RequestFocusOnResume(focusRequester: FocusRequester) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(state = Lifecycle.State.RESUMED) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun SingleButtonAlert(
    title: String,
    iconRes: Int? = null,
    body: String,
    scrollState: ScalingLazyListState,
    isOkButton: Boolean = true,
    onButtonClick: () -> Unit,
    buttonContentDescription: String,
) {
    Alert(
        contentPadding = DefaultContentPadding(),
        title = { AlertTitleText(title) },
        scrollState = scrollState,
        icon = { AlertIcon(iconRes) },
        message = { AlertBodyText(body) }
    ) {
        item {
            if (isOkButton) {
                PositiveButton(onButtonClick, buttonContentDescription)
            } else {
                NegativeButton(onButtonClick, buttonContentDescription)
            }
        }
    }
}

@Composable private fun DefaultContentPadding() = PaddingValues(top = 24.dp, bottom = 24.dp)

@Composable
private fun AlertTitleText(title: String) =
    Text(
        text = title,
        color = MaterialTheme.colors.onBackground,
        textAlign = TextAlign.Center,
        maxLines = 3,
        style = MaterialTheme.typography.title3
    )

@Composable
private fun AlertIcon(iconRes: Int?) =
    if (iconRes != null && iconRes != 0) {
        Icon(painter = painterResource(iconRes), contentDescription = null)
    } else {
        null
    }

@Composable
private fun AlertBodyText(body: String) =
    Text(
        text = body,
        color = MaterialTheme.colors.onBackground,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.body2
    )

@Composable
private fun PositiveButton(onClick: () -> Unit, contentDescription: String) =
    Button(
        imageVector = Icons.Default.Check,
        contentDescription = contentDescription,
        onClick = onClick
    )

@Composable
private fun NegativeButton(onClick: () -> Unit, contentDescription: String) =
    Button(
        imageVector = Icons.Default.Close,
        contentDescription = contentDescription,
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors()
    )
