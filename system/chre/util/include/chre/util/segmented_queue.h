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

#ifndef CHRE_UTIL_SEGMENTED_QUEUE_H_
#define CHRE_UTIL_SEGMENTED_QUEUE_H_

#include <type_traits>
#include <utility>

#include "chre/util/dynamic_vector.h"
#include "chre/util/non_copyable.h"
#include "chre/util/raw_storage.h"
#include "chre/util/unique_ptr.h"

namespace chre {
/**
 * Data structure that is similar to chre::ArrayQueue but with the ability to
 * expand dynamically. Also has segmented data storage to prevent heap
 * fragmentation.
 *
 * Note that this data structure allocates storage dynamically and might need
 * to move elements around during push_back(). It is important for ElementType
 * to have a efficient move operator
 *
 * @tparam ElementType: The type of element for this SegmentedQueue to store.
 * @tparam kBlockSize: The size of one block.
 */
template <typename ElementType, size_t kBlockSize>
class SegmentedQueue : public NonCopyable {
  using Block = ::chre::RawStorage<ElementType, kBlockSize>;
  using BlockPtr = UniquePtr<Block>;
  static_assert(kBlockSize > 0);

 public:
  /**
   * Construct a new Segmented Queue object.
   *
   * @param maxBlockCount: The maximum number of block that this queue can hold.
   * @param staticBlockCount the number of blocks that will be allocate in the
   * constructor and will only be deallocate by the destructor. Needs to be
   * bigger than zero to avoid thrashing
   */
  SegmentedQueue(size_t maxBlockCount, size_t staticBlockCount = 1);

  ~SegmentedQueue();

  /**
   * @return size_t: Number of elements that this segmented queue holds.
   */
  size_t size() {
    return mSize;
  }

  /**
   * @return size_t: How many blocks does this segmented queue contains.
   */
  size_t block_count() {
    return mRawStoragePtrs.size();
  }

  /**
   * @return size_t: Number of items that this queue can store without pushing
   * new blocks.
   */
  size_t capacity() {
    return mRawStoragePtrs.size() * kBlockSize;
  }

  /**
   * @return true: Return true if the segmented queue cannot accept new element.
   */
  bool full() {
    return mSize == kMaxBlockCount * kBlockSize;
  }

  /**
   * @return true: Return true if this segmented queue does not have any element
   * stored.
   */
  bool empty() const {
    return mSize == 0;
  }

  /**
   * Push a element to the end of the segmented queue.
   *
   * @param element: The element that will be push to the back of the queue.
   * @return false: Return false if the queue is full.
   */
  bool push_back(const ElementType &element);
  bool push_back(ElementType &&element);

  /**
   * Provide the same push API like std::queue/chre::ArrayQueue that do
   * push_back().
   *
   * @param element: The element that will be push to the back of the queue.
   * @return false: Return false if the queue is full.
   */
  bool push(const ElementType &element);
  bool push(ElementType &&element);

  /**
   * Constructs an element onto the back of the segmented queue.
   *
   * @param Arguments to the constructor of ElementType.
   * @return: Return true if the element is constructed successfully.
   */
  template <typename... Args>
  bool emplace_back(Args &&...args);

  /**
   * Obtains an element of the queue by its index.
   * It is illegal to use a index that is bigger or equal to the size of the
   * queue.
   *
   * @param index: Requested index in range [0, size()-1].
   * @return ElementType&: Reference to the element.
   */
  ElementType &operator[](size_t index);
  const ElementType &operator[](size_t index) const;

  /**
   * Obtain the last element in the queue.
   * It is illegal to call this function when empty() == true.
   *
   * @return ElementType&: Reference to the last element.
   */
  ElementType &back();
  const ElementType &back() const;

  /**
   * Obtain the first element in the queue.
   * It is illegal to call this function when empty() == true.
   *
   * @return ElementType&: Reference to the first element.
   */
  ElementType &front();
  const ElementType &front() const;

  /**
   * Remove the first element from the queue.
   * It is illegal to call this function when empty() == true.
   */
  void pop_front();

  /**
   * Provide the same pop API like std::queue/chre::ArrayQueue that do
   * pop_front(). It is illegal to call this function when empty() == true.
   */
  void pop();

  /**
   * Removes an element from the queue by given index.
   *
   * @param index: Index of the item that will be removed.
   * @return false: Returns false if index >= size().
   */
  bool remove(size_t index);

  /**
   * Function used to decide if an element in the queue matches a certain
   * condition.
   *
   * @see removeMatchesFromBack and searchMatches.
   */
  using MatchingFunction =
      typename std::conditional<std::is_pointer<ElementType>::value ||
                                    std::is_fundamental<ElementType>::value,
                                bool(ElementType), bool(ElementType &)>::type;

  using FreeFunction =
      typename std::conditional<std::is_pointer<ElementType>::value ||
                                    std::is_fundamental<ElementType>::value,
                                void(ElementType, void *),
                                void(ElementType &, void *)>::type;
  /**
   * Removes maxNumOfElementsRemoved of elements that satisfies matchFunc.
   *
   * If the queue has fewer items that matches the condition than
   * maxNumOfElementsRemoved, it will remove all matching items and return the
   * number of items that it actually removed.
   *
   * @param matchFunc                Function used to decide if an item should
   *                                 be removed. Should return true if this
   *                                 item needs to be removed.
   * @param maxNumOfElementsRemoved  Number of elements to remove, also the
   *                                 size of removedElements. It is not
   *                                 guaranteed that the actual number of items
   *                                 removed will equal to this parameter since
   *                                 it will depend on the number of items that
   *                                 matches the condition.
   * @param removedElements          Stores the pointers that has been
   *                                 removed. This cannot be a nullptr.
   * @param freeFunction             Function to execute before the matched item
   *                                 is removed. If not supplied, the destructor
   *                                 of the element will be invoked.
   * @param extraDataForFreeFunction Additional data that freeFunction will
   *                                 need.
   *
   * @return                         The number of pointers that is passed
   *                                 out. Returns SIZE_MAX if removedElement is
   *                                 a nullptr as error.
   */
  size_t removeMatchedFromBack(MatchingFunction *matchFunction,
                               size_t maxNumOfElementsRemoved,
                               FreeFunction *freeFunction = nullptr,
                               void *extraDataForFreeFunction = nullptr);

 private:
  /**
   * Push a new block to the end of storage to add storage space.
   * The total block count after push cannot exceed kMaxBlockCount.
   *
   * @return true: Return true if a new block can be added.
   */
  bool pushOneBlock();

  /**
   * Insert one block to the underlying storage.
   * The total block count after push cannot exceed kMaxBlockCount.
   *
   * @param blockIndex: The index to insert a block at.
   * @return true: Return true if a new block can be added.
   */
  bool insertBlock(size_t blockIndex);

  /**
   * Move count number of elements from srcIndex to destIndex.
   * Note that index here refers to absolute index that starts from the head of
   * the DynamicVector.
   *
   * @param srcIndex: The index of the first element to be moved.
   * @param destIndex: The index of the destination to place the first moved
   * element, absoluteIndexToRelative(srcIndex) needs to be bigger than
   * absoluteIndexToRelative(destIndex).
   * @param count: Number of element to move, it is illegal to call with count >
   * size.
   */

  void moveElements(size_t srcIndex, size_t destIndex, size_t count);

  /**
   * Clear the element in gapIndex, pull all elements behind forward
   * to fill the gap and update mTail accordingly.
   *
   * @param gapIndex relative index of the gap.
   */
  void pullForward(size_t gapIndex);

  /**
   * Clear the element in gapIndex, pull all elements before backward
   * to fill the gap and update mHead accordingly.
   *
   * @param gapIndex relative index of the gap.
   */
  void pullBackward(size_t gapIndex);

  /**
   * Move a movable item from srcIndex to destIndex. Note that index here refers
   * to absolute index that starts from the head of the DynamicVector.
   *
   * @param srcIndex: Index to the block that has the source element.
   * @param destIndex: Index to the start of the destination block.
   */
  void doMove(size_t srcIndex, size_t destIndex, std::true_type);

  /**
   * Move a non-movable item from srcIndex to destIndex. Note that index here
   * refers to absolute index that starts from the head of the DynamicVector.
   *
   * @param srcIndex: Index to the block that has the source element.
   * @param destIndex: Index to the start of the destination block.
   */
  void doMove(size_t srcIndex, size_t destIndex, std::false_type);

  /**
   * Calculate the index with respect to mHead to absolute index with respect to
   * the start of the storage dynamic vector.
   *
   * @param index: Relative index in the range [0, mSize - 1].
   * @return size_t: The offset index in range [0, capacity() - 1].
   */
  size_t relativeIndexToAbsolute(size_t index);

  /**
   * Prepare push by pushing new blocks if needed and update mTail to point at
   * the right index.
   *
   * @return false: Return false if the queue is already full.
   */
  bool prepareForPush();

  /**
   * Add 1 to the index if index is not at the end of the data storage. If so,
   * wraps around to 0.
   *
   * @param index: Original index.
   * @return size_t: New index after calculation.
   */
  size_t advanceOrWrapAround(size_t index);

  /**
   * Subtract k steps to the index and wrap around if needed.
   *
   * @param index Original index.
   * @param steps Number of steps that it needs to be subtracted.
   * @return      New index after calculation.
   */
  size_t subtractOrWrapAround(size_t index, size_t steps);

  /**
   * Locate the data reference by absolute index.
   * Note that this function does not check if the address belongs to
   * this queue.
   *
   * @param index: The absolute index to find that data.
   * @return ElementType&: Reference to the data.
   */
  ElementType &locateDataAddress(size_t index);

  /**
   * Removes all the elements of the queue.
   */
  void clear();

  /**
   * Remove and destroy an object by the given index.
   * Note that this function does not change any pointer nor fill the gap
   * after removing.
   *
   * @param index: The absolute index for the item that will be removed.
   */
  void doRemove(size_t index);

  /**
   * Calculate the index with respect to the start of the storage to relevant
   * index with respect to mHead.
   *
   * @param index: Absolute index in the range [0, capacity() - 1].
   * @return size_t: Relative index in the range [0, mSize - 1].
   */
  size_t absoluteIndexToRelative(size_t index);

  /**
   * Resets the current queue to its initial state if the queue is empty.
   * It is illegal to call this function if the queue is not empty.
   */
  void resetEmptyQueue();

  /**
   * Search the queue backwards to find foundIndicesLen of elements that matches
   * a certain condition and return them by foundIndices. If the queue does not
   * have enough elements, foundIndices will only be filled with the number that
   * matches the condition.
   *
   * @param matchFunc          Function used to decide if an item should be
   *                           returned. Should return true if this item need
   *                           to be returned.
   * @param foundIndicesLen    Length of foundIndices indicating how many index
   *                           is targeted.
   * @param foundIndices       Indices that contains the element that matches
   *                           the condition. Note that the index is
   *                           returned in reversed order, i.e. the first
   *                           element will contain the index closest to the
   *                           end.
   * @return                   the number of element that matches.
   */
  size_t searchMatches(MatchingFunction *matchFunc, size_t foundIndicesLen,
                       size_t foundIndices[]);

  /**
   * Move elements in this queue to fill the gaps.
   *
   * @param gapCount   Number of holes.
   * @param gapIndices Indices that are empty. Need to be reverse order (first
   *                   index is closest to the end of the queue).
   */
  void fillGaps(size_t gapCount, const size_t gapIndices[]);

  // TODO(b/258771255): See if we can change the container to
  // ArrayQueue<UniquePtr<Block>> to minimize block moving during push_back.
  //! The data storage of this segmented queue.
  DynamicVector<UniquePtr<Block>> mRawStoragePtrs;

  //! Records how many items are in this queue.
  size_t mSize = 0;

  //! The maximum block count this queue can hold.
  const size_t kMaxBlockCount;

  //! How many blocks allocated in constructor.
  const size_t kStaticBlockCount;

  //! The offset of the first element of the queue starting from the start of
  //! the DynamicVector.
  size_t mHead = 0;

  // TODO(b/258828257): Modify initialization logic to make it work when
  // kStaticBlockCount = 0
  //! The offset of the last element of the queue starting from the start of the
  //! DynamicVector. Initialize it to the end of container for a easier
  //! implementation of push_back().
  size_t mTail = kBlockSize * kStaticBlockCount - 1;
};

}  // namespace chre

#include "chre/util/segmented_queue_impl.h"

#endif  // CHRE_UTIL_SEGMENTED_QUEUE_H_
