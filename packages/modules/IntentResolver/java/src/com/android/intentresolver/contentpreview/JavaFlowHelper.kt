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

@file:JvmName("JavaFlowHelper")

package com.android.intentresolver.contentpreview

import com.android.intentresolver.widget.ScrollableImagePreviewView.Preview
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

internal fun mapFileIntoToPreview(
    flow: Flow<FileInfo>,
    typeClassifier: MimeTypeClassifier,
    editAction: Runnable?
): Flow<Preview> =
    flow
        .filter { it.previewUri != null }
        .map { fileInfo ->
            Preview(
                ContentPreviewUi.getPreviewType(typeClassifier, fileInfo.mimeType),
                requireNotNull(fileInfo.previewUri),
                editAction
            )
        }

internal fun <T> collectToList(
    clientScope: CoroutineScope,
    flow: Flow<T>,
    callback: Consumer<List<T>>
) {
    clientScope.launch { callback.accept(flow.toList()) }
}
