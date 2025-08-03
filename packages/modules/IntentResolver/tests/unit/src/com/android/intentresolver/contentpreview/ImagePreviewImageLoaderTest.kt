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

package com.android.intentresolver.contentpreview

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.testing.TestLifecycleOwner
import com.android.intentresolver.any
import com.android.intentresolver.anyOrNull
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertThat
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class ImagePreviewImageLoaderTest {
    private val imageSize = Size(300, 300)
    private val uriOne = Uri.parse("content://org.package.app/image-1.png")
    private val uriTwo = Uri.parse("content://org.package.app/image-2.png")
    private val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val contentResolver =
        mock<ContentResolver> {
            whenever(loadThumbnail(any(), any(), anyOrNull())).thenReturn(bitmap)
        }
    private val lifecycleOwner = TestLifecycleOwner()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var testSubject: ImagePreviewImageLoader

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // create test subject after we've updated the lifecycle dispatcher
        testSubject =
            ImagePreviewImageLoader(
                lifecycleOwner.lifecycle.coroutineScope + dispatcher,
                imageSize.width,
                contentResolver,
                cacheSize = 1,
            )
    }

    @After
    fun cleanup() {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        Dispatchers.resetMain()
    }

    @Test
    fun prePopulate_cachesImagesUpToTheCacheSize() = runTest {
        testSubject.prePopulate(listOf(uriOne, uriTwo))

        verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
        verify(contentResolver, never()).loadThumbnail(uriTwo, imageSize, null)

        testSubject(uriOne)
        verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
    }

    @Test
    fun invoke_returnCachedImageWhenCalledTwice() = runTest {
        testSubject(uriOne)
        testSubject(uriOne)

        verify(contentResolver, times(1)).loadThumbnail(any(), any(), anyOrNull())
    }

    @Test
    fun invoke_whenInstructed_doesNotCache() = runTest {
        testSubject(uriOne, false)
        testSubject(uriOne, false)

        verify(contentResolver, times(2)).loadThumbnail(any(), any(), anyOrNull())
    }

    @Test
    fun invoke_overlappedRequests_Deduplicate() = runTest {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val testSubject =
            ImagePreviewImageLoader(
                lifecycleOwner.lifecycle.coroutineScope + dispatcher,
                imageSize.width,
                contentResolver,
                cacheSize = 1,
            )
        coroutineScope {
            launch(start = UNDISPATCHED) { testSubject(uriOne, false) }
            launch(start = UNDISPATCHED) { testSubject(uriOne, false) }
            scheduler.advanceUntilIdle()
        }

        verify(contentResolver, times(1)).loadThumbnail(any(), any(), anyOrNull())
    }

    @Test
    fun invoke_oldRecordsEvictedFromTheCache() = runTest {
        testSubject(uriOne)
        testSubject(uriTwo)
        testSubject(uriTwo)
        testSubject(uriOne)

        verify(contentResolver, times(2)).loadThumbnail(uriOne, imageSize, null)
        verify(contentResolver, times(1)).loadThumbnail(uriTwo, imageSize, null)
    }

    @Test
    fun invoke_doNotCacheNulls() = runTest {
        whenever(contentResolver.loadThumbnail(any(), any(), anyOrNull())).thenReturn(null)
        testSubject(uriOne)
        testSubject(uriOne)

        verify(contentResolver, times(2)).loadThumbnail(uriOne, imageSize, null)
    }

    @Test(expected = CancellationException::class)
    fun invoke_onClosedImageLoaderScope_throwsCancellationException() = runTest {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        testSubject(uriOne)
    }

    @Test(expected = CancellationException::class)
    fun invoke_imageLoaderScopeClosedMidflight_throwsCancellationException() = runTest {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val testSubject =
            ImagePreviewImageLoader(
                lifecycleOwner.lifecycle.coroutineScope + dispatcher,
                imageSize.width,
                contentResolver,
                cacheSize = 1,
            )
        coroutineScope {
            val deferred = async(start = UNDISPATCHED) { testSubject(uriOne, false) }
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            scheduler.advanceUntilIdle()
            deferred.await()
        }
    }

    @Test
    fun invoke_multipleCallsWithDifferentCacheInstructions_cachingPrevails() = runTest {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val testSubject =
            ImagePreviewImageLoader(
                lifecycleOwner.lifecycle.coroutineScope + dispatcher,
                imageSize.width,
                contentResolver,
                cacheSize = 1,
            )
        coroutineScope {
            launch(start = UNDISPATCHED) { testSubject(uriOne, false) }
            launch(start = UNDISPATCHED) { testSubject(uriOne, true) }
            scheduler.advanceUntilIdle()
        }
        testSubject(uriOne, true)

        verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
    }

    @Test
    fun invoke_semaphoreGuardsContentResolverCalls() = runTest {
        val contentResolver =
            mock<ContentResolver> {
                whenever(loadThumbnail(any(), any(), anyOrNull()))
                    .thenThrow(SecurityException("test"))
            }
        val acquireCount = AtomicInteger()
        val releaseCount = AtomicInteger()
        val testSemaphore =
            object : Semaphore {
                override val availablePermits: Int
                    get() = error("Unexpected invocation")

                override suspend fun acquire() {
                    acquireCount.getAndIncrement()
                }

                override fun tryAcquire(): Boolean {
                    error("Unexpected invocation")
                }

                override fun release() {
                    releaseCount.getAndIncrement()
                }
            }

        val testSubject =
            ImagePreviewImageLoader(
                lifecycleOwner.lifecycle.coroutineScope + dispatcher,
                imageSize.width,
                contentResolver,
                cacheSize = 1,
                testSemaphore,
            )
        testSubject(uriOne, false)

        verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
        assertThat(acquireCount.get()).isEqualTo(1)
        assertThat(releaseCount.get()).isEqualTo(1)
    }

    @Test
    fun invoke_semaphoreIsReleasedAfterContentResolverFailure() = runTest {
        val semaphoreDeferred = CompletableDeferred<Unit>()
        val releaseCount = AtomicInteger()
        val testSemaphore =
            object : Semaphore {
                override val availablePermits: Int
                    get() = error("Unexpected invocation")

                override suspend fun acquire() {
                    semaphoreDeferred.await()
                }

                override fun tryAcquire(): Boolean {
                    error("Unexpected invocation")
                }

                override fun release() {
                    releaseCount.getAndIncrement()
                }
            }

        val testSubject =
            ImagePreviewImageLoader(
                lifecycleOwner.lifecycle.coroutineScope + dispatcher,
                imageSize.width,
                contentResolver,
                cacheSize = 1,
                testSemaphore,
            )
        launch(start = UNDISPATCHED) { testSubject(uriOne, false) }

        verify(contentResolver, never()).loadThumbnail(any(), any(), anyOrNull())

        semaphoreDeferred.complete(Unit)

        verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
        assertThat(releaseCount.get()).isEqualTo(1)
    }

    @Test
    fun invoke_multipleSimultaneousCalls_limitOnNumberOfSimultaneousOutgoingCallsIsRespected() {
        val requestCount = 4
        val thumbnailCallsCdl = CountDownLatch(requestCount)
        val pendingThumbnailCalls = ArrayDeque<CountDownLatch>()
        val contentResolver =
            mock<ContentResolver> {
                whenever(loadThumbnail(any(), any(), anyOrNull())).thenAnswer {
                    val latch = CountDownLatch(1)
                    synchronized(pendingThumbnailCalls) { pendingThumbnailCalls.offer(latch) }
                    thumbnailCallsCdl.countDown()
                    assertTrue("Timeout waiting thumbnail calls", latch.await(1, SECONDS))
                    bitmap
                }
            }
        val name = "LoadImage"
        val maxSimultaneousRequests = 2
        val threadsStartedCdl = CountDownLatch(requestCount)
        val dispatcher = NewThreadDispatcher(name) { threadsStartedCdl.countDown() }
        val testSubject =
            ImagePreviewImageLoader(
                lifecycleOwner.lifecycle.coroutineScope + dispatcher + CoroutineName(name),
                imageSize.width,
                contentResolver,
                cacheSize = 1,
                maxSimultaneousRequests,
            )
        runTest {
            repeat(requestCount) {
                launch { testSubject(Uri.parse("content://org.pkg.app/image-$it.png")) }
            }
            yield()
            // wait for all requests to be dispatched
            assertThat(threadsStartedCdl.await(5, SECONDS)).isTrue()

            assertThat(thumbnailCallsCdl.await(100, MILLISECONDS)).isFalse()
            synchronized(pendingThumbnailCalls) {
                assertThat(pendingThumbnailCalls.size).isEqualTo(maxSimultaneousRequests)
            }

            pendingThumbnailCalls.poll()?.countDown()
            assertThat(thumbnailCallsCdl.await(100, MILLISECONDS)).isFalse()
            synchronized(pendingThumbnailCalls) {
                assertThat(pendingThumbnailCalls.size).isEqualTo(maxSimultaneousRequests)
            }

            pendingThumbnailCalls.poll()?.countDown()
            assertThat(thumbnailCallsCdl.await(100, MILLISECONDS)).isTrue()
            synchronized(pendingThumbnailCalls) {
                assertThat(pendingThumbnailCalls.size).isEqualTo(maxSimultaneousRequests)
            }
            for (cdl in pendingThumbnailCalls) {
                cdl.countDown()
            }
        }
    }
}

private class NewThreadDispatcher(
    private val coroutineName: String,
    private val launchedCallback: () -> Unit
) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Thread {
                if (coroutineName == context[CoroutineName.Key]?.name) {
                    launchedCallback()
                }
                block.run()
            }
            .start()
    }
}
