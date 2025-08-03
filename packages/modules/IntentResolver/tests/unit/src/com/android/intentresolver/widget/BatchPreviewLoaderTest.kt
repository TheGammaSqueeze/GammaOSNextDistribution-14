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

package com.android.intentresolver.widget

import android.graphics.Bitmap
import android.net.Uri
import com.android.intentresolver.captureMany
import com.android.intentresolver.mock
import com.android.intentresolver.widget.ScrollableImagePreviewView.BatchPreviewLoader
import com.android.intentresolver.widget.ScrollableImagePreviewView.Preview
import com.android.intentresolver.widget.ScrollableImagePreviewView.PreviewType
import com.android.intentresolver.withArgCaptor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class BatchPreviewLoaderTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(dispatcher)
    private val onCompletion = mock<() -> Unit>()
    private val onUpdate = mock<(List<Preview>) -> Unit>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        testScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun test_allImagesWithinViewPort_oneUpdate() {
        val imageLoader = TestImageLoader(testScope)
        val uriOne = createUri(1)
        val uriTwo = createUri(2)
        imageLoader.setUriLoadingOrder(succeed(uriTwo), succeed(uriOne))
        val testSubject =
            BatchPreviewLoader(
                imageLoader,
                previews(uriOne, uriTwo),
                totalItemCount = 2,
                onUpdate,
                onCompletion
            )
        testSubject.loadAspectRatios(200) { _, _, _ -> 100 }
        dispatcher.scheduler.advanceUntilIdle()

        verify(onCompletion, times(1)).invoke()
        val list = withArgCaptor { verify(onUpdate, times(1)).invoke(capture()) }.map { it.uri }
        assertThat(list).containsExactly(uriOne, uriTwo).inOrder()
    }

    @Test
    fun test_allImagesWithinViewPortOneFailed_failedPreviewIsNotUpdated() {
        val imageLoader = TestImageLoader(testScope)
        val uriOne = createUri(1)
        val uriTwo = createUri(2)
        val uriThree = createUri(3)
        imageLoader.setUriLoadingOrder(succeed(uriThree), fail(uriTwo), succeed(uriOne))
        val testSubject =
            BatchPreviewLoader(
                imageLoader,
                previews(uriOne, uriTwo, uriThree),
                totalItemCount = 3,
                onUpdate,
                onCompletion
            )
        testSubject.loadAspectRatios(200) { _, _, _ -> 100 }
        dispatcher.scheduler.advanceUntilIdle()

        verify(onCompletion, times(1)).invoke()
        val list = withArgCaptor { verify(onUpdate, times(1)).invoke(capture()) }.map { it.uri }
        assertThat(list).containsExactly(uriOne, uriThree).inOrder()
    }

    @Test
    fun test_imagesLoadedNotInOrder_updatedInOrder() {
        val imageLoader = TestImageLoader(testScope)
        val uris = Array(10) { createUri(it) }
        val loadingOrder =
            Array(uris.size) { i ->
                val uriIdx =
                    when {
                        i % 2 == 1 -> i - 1
                        i % 2 == 0 && i < uris.size - 1 -> i + 1
                        else -> i
                    }
                succeed(uris[uriIdx])
            }
        imageLoader.setUriLoadingOrder(*loadingOrder)
        val testSubject =
            BatchPreviewLoader(imageLoader, previews(*uris), uris.size, onUpdate, onCompletion)
        testSubject.loadAspectRatios(200) { _, _, _ -> 100 }
        dispatcher.scheduler.advanceUntilIdle()

        verify(onCompletion, times(1)).invoke()
        val list =
            captureMany { verify(onUpdate, atLeast(1)).invoke(capture()) }
                .fold(ArrayList<Preview>()) { acc, update -> acc.apply { addAll(update) } }
                .map { it.uri }
        assertThat(list).containsExactly(*uris).inOrder()
    }

    @Test
    fun test_imagesLoadedNotInOrderSomeFailed_updatedInOrder() {
        val imageLoader = TestImageLoader(testScope)
        val uris = Array(10) { createUri(it) }
        val loadingOrder =
            Array(uris.size) { i ->
                val uriIdx =
                    when {
                        i % 2 == 1 -> i - 1
                        i % 2 == 0 && i < uris.size - 1 -> i + 1
                        else -> i
                    }
                if (uriIdx % 2 == 0) fail(uris[uriIdx]) else succeed(uris[uriIdx])
            }
        val expectedUris = Array(uris.size / 2) { createUri(it * 2 + 1) }
        imageLoader.setUriLoadingOrder(*loadingOrder)
        val testSubject =
            BatchPreviewLoader(imageLoader, previews(*uris), uris.size, onUpdate, onCompletion)
        testSubject.loadAspectRatios(200) { _, _, _ -> 100 }
        dispatcher.scheduler.advanceUntilIdle()

        verify(onCompletion, times(1)).invoke()
        val list =
            captureMany { verify(onUpdate, atLeast(1)).invoke(capture()) }
                .fold(ArrayList<Preview>()) { acc, update -> acc.apply { addAll(update) } }
                .map { it.uri }
        assertThat(list).containsExactly(*expectedUris).inOrder()
    }

    private fun createUri(idx: Int): Uri = Uri.parse("content://org.pkg.app/image-$idx.png")

    private fun fail(uri: Uri) = uri to false
    private fun succeed(uri: Uri) = uri to true
    private fun previews(vararg uris: Uri) =
        uris
            .fold(ArrayList<Preview>(uris.size)) { acc, uri ->
                acc.apply { add(Preview(PreviewType.Image, uri, editAction = null)) }
            }
            .asFlow()
}

private class TestImageLoader(scope: CoroutineScope) : suspend (Uri, Boolean) -> Bitmap? {
    private val loadingOrder = ArrayDeque<Pair<Uri, Boolean>>()
    private val pendingRequests = LinkedHashMap<Uri, CompletableDeferred<Bitmap?>>()
    private val flow = MutableSharedFlow<Unit>(replay = 1)
    private val bitmap by lazy { Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888) }

    init {
        scope.launch {
            flow.collect {
                while (true) {
                    val (nextUri, isLoaded) = loadingOrder.firstOrNull() ?: break
                    val deferred = pendingRequests.remove(nextUri) ?: break
                    loadingOrder.removeFirst()
                    deferred.complete(if (isLoaded) bitmap else null)
                }
                if (loadingOrder.isEmpty()) {
                    pendingRequests.forEach { (uri, deferred) -> deferred.complete(bitmap) }
                    pendingRequests.clear()
                }
            }
        }
    }

    fun setUriLoadingOrder(vararg uris: Pair<Uri, Boolean>) {
        loadingOrder.clear()
        loadingOrder.addAll(uris)
    }

    override suspend fun invoke(uri: Uri, cache: Boolean): Bitmap? {
        val deferred = pendingRequests.getOrPut(uri) { CompletableDeferred() }
        flow.tryEmit(Unit)
        return deferred.await()
    }
}
