/*
 * Copyright 2024 The Android Open Source Project
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

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.service.chooser.ChooserAction
import android.service.chooser.ChooserTarget
import android.util.Log
import android.util.SparseArray
import java.io.Closeable
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow.DROP_LATEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "PayloadToggleInteractor"

@OptIn(ExperimentalCoroutinesApi::class)
class PayloadToggleInteractor(
    // TODO: a single-thread dispatcher is currently expected. iterate on the synchronization logic.
    private val scope: CoroutineScope,
    private val initiallySharedUris: List<Uri>,
    private val focusedUriIdx: Int,
    private val mimeTypeClassifier: MimeTypeClassifier,
    private val cursorReaderProvider: suspend () -> CursorReader,
    private val uriMetadataReader: (Uri) -> FileInfo,
    private val targetIntentModifier: (List<Item>) -> Intent,
    private val selectionCallback: (Intent) -> ShareouselUpdate?,
) {
    private var cursorDataRef = CompletableDeferred<CursorData?>()
    private val records = LinkedList<Record>()
    private val prevPageLoadingGate = AtomicBoolean(true)
    private val nextPageLoadingGate = AtomicBoolean(true)
    private val notifySelectionJobRef = AtomicReference<Job?>()
    private val emptyState =
        State(
            emptyList(),
            hasMoreItemsBefore = false,
            hasMoreItemsAfter = false,
            allowSelectionChange = false
        )

    private val stateFlowSource = MutableStateFlow(emptyState)

    val customActions =
        MutableSharedFlow<List<ChooserAction>>(replay = 1, onBufferOverflow = DROP_LATEST)

    val stateFlow: Flow<State>
        get() = stateFlowSource.filter { it !== emptyState }

    val targetPosition: Flow<Int> = stateFlow.map { it.targetPos }
    val previewKeys: Flow<List<Item>> = stateFlow.map { it.items }

    fun getKey(item: Any): Int = (item as Item).key

    fun selected(key: Item): Flow<Boolean> = (key as Record).isSelected

    fun previewUri(key: Item): Flow<Uri?> = flow { emit(key.previewUri) }

    fun previewInteractor(key: Any): PayloadTogglePreviewInteractor {
        val state = stateFlowSource.value
        if (state === emptyState) {
            Log.wtf(TAG, "Requesting item preview before any item has been published")
        } else {
            if (state.hasMoreItemsBefore && key === state.items.firstOrNull()) {
                loadMorePreviousItems()
            }
            if (state.hasMoreItemsAfter && key == state.items.lastOrNull()) {
                loadMoreNextItems()
            }
        }
        return PayloadTogglePreviewInteractor(key as Item, this)
    }

    init {
        scope
            .launch { awaitCancellation() }
            .invokeOnCompletion {
                cursorDataRef.cancel()
                runCatching {
                        if (cursorDataRef.isCompleted && !cursorDataRef.isCancelled) {
                            cursorDataRef.getCompleted()
                        } else {
                            null
                        }
                    }
                    .getOrNull()
                    ?.reader
                    ?.close()
            }
    }

    fun start() {
        scope.launch {
            val cursorReader = cursorReaderProvider()
            val selectedItems =
                initiallySharedUris.map { uri ->
                    val fileInfo = uriMetadataReader(uri)
                    Record(
                        0, // artificial key for the pending record, it should not be used anywhere
                        uri,
                        fileInfo.previewUri,
                        fileInfo.mimeType,
                    )
                }
            val cursorData =
                CursorData(
                    cursorReader,
                    SelectionTracker(selectedItems, focusedUriIdx, cursorReader.count) { uri },
                )
            if (cursorDataRef.complete(cursorData)) {
                doLoadMorePreviousItems()
                val startPos = records.size
                doLoadMoreNextItems()
                prevPageLoadingGate.set(false)
                nextPageLoadingGate.set(false)
                publishSnapshot(startPos)
            } else {
                cursorReader.close()
            }
        }
    }

    fun loadMorePreviousItems() {
        invokeAsyncIfNotRunning(prevPageLoadingGate) {
            doLoadMorePreviousItems()
            publishSnapshot()
        }
    }

    fun loadMoreNextItems() {
        invokeAsyncIfNotRunning(nextPageLoadingGate) {
            doLoadMoreNextItems()
            publishSnapshot()
        }
    }

    fun setSelected(item: Item, isSelected: Boolean) {
        val record = item as Record
        scope.launch {
            val (_, selectionTracker) = waitForCursorData() ?: return@launch
            if (selectionTracker.setItemSelection(record.key, record, isSelected)) {
                val targetIntent = targetIntentModifier(selectionTracker.getSelection())
                val newJob = scope.launch { notifySelectionChanged(targetIntent) }
                notifySelectionJobRef.getAndSet(newJob)?.cancel()
                record.isSelected.value = selectionTracker.isItemSelected(record.key)
            }
        }
    }

    private fun invokeAsyncIfNotRunning(guardingFlag: AtomicBoolean, block: suspend () -> Unit) {
        if (guardingFlag.compareAndSet(false, true)) {
            scope.launch { block() }.invokeOnCompletion { guardingFlag.set(false) }
        }
    }

    private suspend fun doLoadMorePreviousItems() {
        val (reader, selectionTracker) = waitForCursorData() ?: return
        if (!reader.hasMoreBefore) return

        val newItems = reader.readPageBefore().toItems()
        selectionTracker.onStartItemsAdded(newItems)
        for (i in newItems.size() - 1 downTo 0) {
            records.add(
                0,
                (newItems.valueAt(i) as Record).apply {
                    isSelected.value = selectionTracker.isItemSelected(key)
                }
            )
        }
        if (!reader.hasMoreBefore && !reader.hasMoreAfter) {
            val pendingItems = selectionTracker.getPendingItems()
            val newRecords =
                pendingItems.foldIndexed(SparseArray<Item>()) { idx, acc, item ->
                    assert(item is Record) { "Unexpected pending item type: ${item.javaClass}" }
                    val rec = item as Record
                    val key = idx - pendingItems.size
                    acc.append(
                        key,
                        Record(
                            key,
                            rec.uri,
                            rec.previewUri,
                            rec.mimeType,
                            rec.mimeType?.mimeTypeToItemType() ?: ItemType.File
                        )
                    )
                    acc
                }

            selectionTracker.onStartItemsAdded(newRecords)
            for (i in (newRecords.size() - 1) downTo 0) {
                records.add(0, (newRecords.valueAt(i) as Record).apply { isSelected.value = true })
            }
        }
    }

    private suspend fun doLoadMoreNextItems() {
        val (reader, selectionTracker) = waitForCursorData() ?: return
        if (!reader.hasMoreAfter) return

        val newItems = reader.readPageAfter().toItems()
        selectionTracker.onEndItemsAdded(newItems)
        for (i in 0 until newItems.size()) {
            val key = newItems.keyAt(i)
            records.add(
                (newItems.valueAt(i) as Record).apply {
                    isSelected.value = selectionTracker.isItemSelected(key)
                }
            )
        }
        if (!reader.hasMoreBefore && !reader.hasMoreAfter) {
            val items =
                selectionTracker.getPendingItems().let { items ->
                    items.foldIndexed(SparseArray<Item>(items.size)) { i, acc, item ->
                        val key = reader.count + i
                        val record = item as Record
                        acc.append(
                            key,
                            Record(key, record.uri, record.previewUri, record.mimeType, record.type)
                        )
                        acc
                    }
                }
            selectionTracker.onEndItemsAdded(items)
            for (i in 0 until items.size()) {
                records.add((items.valueAt(i) as Record).apply { isSelected.value = true })
            }
        }
    }

    private fun SparseArray<Uri>.toItems(): SparseArray<Item> {
        val items = SparseArray<Item>(size())
        for (i in 0 until size()) {
            val key = keyAt(i)
            val uri = valueAt(i)
            val fileInfo = uriMetadataReader(uri)
            items.append(
                key,
                Record(
                    key,
                    uri,
                    fileInfo.previewUri,
                    fileInfo.mimeType,
                    fileInfo.mimeType?.mimeTypeToItemType() ?: ItemType.File
                )
            )
        }
        return items
    }

    private suspend fun waitForCursorData() = cursorDataRef.await()

    private fun notifySelectionChanged(targetIntent: Intent) {
        selectionCallback(targetIntent)?.customActions?.let { customActions.tryEmit(it) }
    }

    private suspend fun publishSnapshot(startPos: Int = -1) {
        val (reader, _) = waitForCursorData() ?: return
        // TODO: publish a view into the list as it can only grow on each side thus a view won't be
        // invalidated
        val items = ArrayList<Item>(records)
        stateFlowSource.emit(
            State(
                items,
                reader.hasMoreBefore,
                reader.hasMoreAfter,
                allowSelectionChange = true,
                targetPos = startPos,
            )
        )
    }

    private fun String.mimeTypeToItemType(): ItemType =
        when {
            mimeTypeClassifier.isImageType(this) -> ItemType.Image
            mimeTypeClassifier.isVideoType(this) -> ItemType.Video
            else -> ItemType.File
        }

    class State(
        val items: List<Item>,
        val hasMoreItemsBefore: Boolean,
        val hasMoreItemsAfter: Boolean,
        val allowSelectionChange: Boolean,
        val targetPos: Int = -1,
    )

    sealed interface Item {
        val key: Int
        val uri: Uri
        val previewUri: Uri?
        val mimeType: String?
        val type: ItemType
    }

    enum class ItemType {
        Image,
        Video,
        File,
    }

    private class Record(
        override val key: Int,
        override val uri: Uri,
        override val previewUri: Uri? = uri,
        override val mimeType: String?,
        override val type: ItemType = ItemType.Image,
    ) : Item {
        val isSelected = MutableStateFlow(false)
    }

    data class ShareouselUpdate(
        // for all properties, null value means no change
        val customActions: List<ChooserAction>? = null,
        val modifyShareAction: ChooserAction? = null,
        val alternateIntents: List<Intent>? = null,
        val callerTargets: List<ChooserTarget>? = null,
        val refinementIntentSender: IntentSender? = null,
    )

    private data class CursorData(
        val reader: CursorReader,
        val selectionTracker: SelectionTracker<Item>,
    )

    interface CursorReader : Closeable {
        val count: Int
        val hasMoreBefore: Boolean
        val hasMoreAfter: Boolean

        fun readPageAfter(): SparseArray<Uri>

        fun readPageBefore(): SparseArray<Uri>
    }
}

class PayloadTogglePreviewInteractor(
    private val item: PayloadToggleInteractor.Item,
    private val interactor: PayloadToggleInteractor,
) {
    fun setSelected(selected: Boolean) {
        interactor.setSelected(item, selected)
    }

    val previewUri: Flow<Uri?>
        get() = interactor.previewUri(item)

    val selected: Flow<Boolean>
        get() = interactor.selected(item)

    val key
        get() = item.key
}
