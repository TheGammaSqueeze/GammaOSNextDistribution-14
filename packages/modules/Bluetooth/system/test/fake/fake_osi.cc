/*
 * Copyright 2023 The Android Open Source Project
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
#include "test/fake/fake_osi.h"

#include "include/check.h"
#include "test/mock/mock_osi_alarm.h"
#include "test/mock/mock_osi_allocator.h"
#include "test/mock/mock_osi_fixed_queue.h"
#include "test/mock/mock_osi_list.h"

// Must be global to resolve the symbol within the legacy stack
struct alarm_t {
  alarm_callback_t cb;
  void* data;

  alarm_t(const char* /* name */) {
    cb = nullptr;
    data = nullptr;
  };
};

struct list_node_t {
  void* data_;
  list_node_t* next_;

  list_node_t(void* data, list_node_t* next) {
    data_ = data;
    next_ = next;
  }
};

struct list_t {
  list_node_t* head_;
  list_node_t* tail_;
  size_t length_;
  list_free_cb free_cb_;

  list_t(list_free_cb free_cb) {
    head_ = tail_ = nullptr;
    free_cb_ = free_cb;
    length_ = 0;
  }
};

struct fixed_queue_t {
  list_t* list_;
  size_t capacity_;

  fixed_queue_t(size_t capacity) {
    list_ = test::mock::osi_list::list_new(nullptr);
    capacity_ = capacity;
  }
};

namespace test {
namespace fake {

static list_node_t* list_free_node_(list_t* l, list_node_t* node) {
  CHECK(l);
  CHECK(node);

  auto next = node->next_;

  if (l->free_cb_) l->free_cb_(node->data_);
  delete node;
  --l->length_;
  return next;
}

FakeOsi::FakeOsi() {
  test::mock::osi_alarm::alarm_free.body = [](alarm_t* alarm) {
    if (alarm) {
      delete alarm;
    }
  };

  test::mock::osi_alarm::alarm_new.body = [](const char* name) {
    return new alarm_t(name);
  };

  test::mock::osi_alarm::alarm_set_on_mloop.body =
      [](alarm_t* alarm, uint64_t /* interval_ms */, alarm_callback_t cb,
         void* data) {
        alarm->cb = cb;
        alarm->data = data;
      };

  test::mock::osi_alarm::alarm_cancel.body = [](alarm_t* alarm) {
    if (alarm) {
      alarm->cb = nullptr;
      alarm->data = nullptr;
    }
  };

  test::mock::osi_allocator::osi_calloc.body = [](size_t size) {
    return calloc(1UL, size);
  };
  test::mock::osi_allocator::osi_free.body = [](void* ptr) { free(ptr); };
  test::mock::osi_allocator::osi_free_and_reset.body = [](void** ptr) {
    free(*ptr);
    *ptr = nullptr;
  };
  test::mock::osi_allocator::osi_malloc.body = [](size_t size) {
    return malloc(size);
  };

  test::mock::osi_list::list_new.body = [](list_free_cb callback) {
    return new list_t(callback);
  };

  test::mock::osi_list::list_free.body = [](list_t* l) {
    CHECK(l);
    test::mock::osi_list::list_clear(l);
    delete l;
  };
  test::mock::osi_list::list_is_empty.body = [](const list_t* l) {
    return test::mock::osi_list::list_length(l) == 0;
  };
  test::mock::osi_list::list_foreach.body =
      [](const list_t* l, list_iter_cb callback, void* context) {
        CHECK(l);
        for (auto node = l->head_; node;) {
          auto next = node->next_;
          if (!callback(node->data_, context)) return node;
          node = next;
        }
        return (list_node_t*)nullptr;
      };
  test::mock::osi_list::list_contains.body = [](const list_t* l,
                                                const void* data) {
    auto node = test::mock::osi_list::list_foreach(
        l, [](void* data, void* context) { return data != context; },
        const_cast<void*>(data));
    return node;
  };
  test::mock::osi_list::list_length.body = [](const list_t* l) {
    CHECK(l);
    return l->length_;
  };
  test::mock::osi_list::list_front.body = [](const list_t* l) {
    CHECK(l);
    CHECK(l->head_);
    return l->head_->data_;
  };
  test::mock::osi_list::list_back.body = [](const list_t* l) {
    CHECK(l);
    CHECK(l->tail_);
    return l->tail_->data_;
  };
  test::mock::osi_list::list_back_node.body = [](const list_t* l) {
    CHECK(l);
    return l->tail_;
  };

  test::mock::osi_list::list_insert_after.body =
      [](list_t* l, list_node_t* prev_node, void* data) {
        CHECK(l);
        CHECK(prev_node);
        CHECK(data);
        auto node = new list_node_t(data, prev_node->next_);
        prev_node->next_ = node;
        if (l->tail_ == prev_node) l->tail_ = node;
        ++l->length_;
        return true;
      };
  test::mock::osi_list::list_prepend.body = [](list_t* l, void* data) {
    CHECK(l);
    CHECK(data);

    auto node = new list_node_t(data, l->head_);
    l->head_ = node;
    if (l->tail_ == NULL) l->tail_ = l->head_;
    ++l->length_;
    return true;
  };
  test::mock::osi_list::list_append.body = [](list_t* l, void* data) {
    CHECK(l);
    CHECK(data);

    auto node = new list_node_t(data, nullptr);
    if (l->tail_) {
      l->tail_->next_ = node;
      l->tail_ = node;
    } else {
      l->head_ = l->tail_ = node;
    }
    ++l->length_;
    return true;
  };
  test::mock::osi_list::list_remove.body = [](list_t* l, void* data) {
    CHECK(l);
    CHECK(data);

    if (test::mock::osi_list::list_is_empty(l)) return false;

    if (l->head_->data_ == data) {
      auto next = list_free_node_(l, l->head_);
      if (l->tail_ == l->head_) l->tail_ = next;
      l->head_ = next;
      return true;
    }

    for (auto prev = l->head_, node = l->head_->next_; node;
         prev = node, node = node->next_)
      if (node->data_ == data) {
        prev->next_ = list_free_node_(l, node);
        if (l->tail_ == node) l->tail_ = prev;
        return true;
      }

    return false;
  };
  test::mock::osi_list::list_clear.body = [](list_t* l) {
    CHECK(l);
    for (auto node = l->head_; node;) {
      node = list_free_node_(l, node);
    }
    l->head_ = NULL;
    l->tail_ = NULL;
    l->length_ = 0;
    return;
  };

  test::mock::osi_list::list_begin.body = [](const list_t* l) {
    CHECK(l);
    return l->head_;
  };
  test::mock::osi_list::list_end.body = [](const list_t* l) {
    CHECK(l);
    return l->tail_;
  };
  test::mock::osi_list::list_next.body = [](const list_node_t* node) {
    CHECK(node);
    return node->next_;
  };
  test::mock::osi_list::list_node.body = [](const list_node_t* node) {
    CHECK(node);
    return node->data_;
  };

  test::mock::osi_fixed_queue::fixed_queue_new.body = [](size_t capacity) {
    return new fixed_queue_t(capacity);
  };
  test::mock::osi_fixed_queue::fixed_queue_flush.body =
      [](fixed_queue_t* q, fixed_queue_free_cb cb) {
        if (q) {
          if (cb) {
            test::mock::osi_list::list_foreach(
                q->list_,
                [](void* data, void* cb) {
                  reinterpret_cast<fixed_queue_free_cb>(cb)(data);
                  return true;
                },
                reinterpret_cast<void*>(cb));
          }
          test::mock::osi_list::list_clear(q->list_);
        }
      };
  test::mock::osi_fixed_queue::fixed_queue_free.body =
      [](fixed_queue_t* q, fixed_queue_free_cb free_cb) {
        if (q) {
          test::mock::osi_fixed_queue::fixed_queue_flush(q, free_cb);
          delete q->list_;
          delete q;
        }
      };
  test::mock::osi_fixed_queue::fixed_queue_enqueue.body = [](fixed_queue_t* q,
                                                             void* data) {
    if (q) test::mock::osi_list::list_append(q->list_, data);
  };
  test::mock::osi_fixed_queue::fixed_queue_dequeue.body = [](fixed_queue_t* q) {
    void* ret = nullptr;
    if (q) {
      ret = test::mock::osi_list::list_front(q->list_);
      test::mock::osi_list::list_remove(q->list_, ret);
    }
    return ret;
  };

  test::mock::osi_fixed_queue::fixed_queue_length.body = [](fixed_queue_t* q) {
    return q ? test::mock::osi_list::list_length(q->list_) : 0;
  };

  test::mock::osi_fixed_queue::fixed_queue_is_empty.body =
      [](fixed_queue_t* q) {
        return test::mock::osi_fixed_queue::fixed_queue_length(q) == 0;
      };

  test::mock::osi_fixed_queue::fixed_queue_capacity.body =
      [](fixed_queue_t* q) { return q ? q->capacity_ : 0; };

  test::mock::osi_fixed_queue::fixed_queue_try_enqueue.body =
      [](fixed_queue_t* q, void* data) {
        test::mock::osi_fixed_queue::fixed_queue_enqueue(q, data);
        return true;
      };
  test::mock::osi_fixed_queue::fixed_queue_try_dequeue.body =
      [](fixed_queue_t* q) {
        void* ret = nullptr;
        if (q && !test::mock::osi_fixed_queue::fixed_queue_is_empty(q)) {
          ret = test::mock::osi_fixed_queue::fixed_queue_dequeue(q);
        }
        return ret;
      };
  test::mock::osi_fixed_queue::fixed_queue_try_peek_first.body =
      [](fixed_queue_t* q) {
        return (q && !test::mock::osi_list::list_is_empty(q->list_))
                   ? test::mock::osi_list::list_front(q->list_)
                   : nullptr;
      };

  test::mock::osi_fixed_queue::fixed_queue_try_peek_last.body =
      [](fixed_queue_t* q) {
        return (q && !test::mock::osi_list::list_is_empty(q->list_))
                   ? test::mock::osi_list::list_back(q->list_)
                   : nullptr;
      };

  test::mock::osi_fixed_queue::fixed_queue_get_list.body =
      [](fixed_queue_t* q) { return q ? q->list_ : nullptr; };

  test::mock::osi_fixed_queue::fixed_queue_try_remove_from_queue.body =
      [](fixed_queue_t* /* q */, void* /* data */) {
        // not implemented
        abort();
        return nullptr;
      };

  test::mock::osi_fixed_queue::fixed_queue_get_enqueue_fd.body =
      [](const fixed_queue_t* /* q */) {
        // not implemented
        abort();
        return 0;
      };

  test::mock::osi_fixed_queue::fixed_queue_get_dequeue_fd.body =
      [](const fixed_queue_t* /* q */) {
        // not implemented
        abort();
        return 0;
      };

  test::mock::osi_fixed_queue::fixed_queue_register_dequeue.body =
      [](fixed_queue_t* /* q */, reactor_t* /* reactor */,
         fixed_queue_cb /* ready_cb */, void* /* context */) {
        // not implemented
        abort();
      };
  test::mock::osi_fixed_queue::fixed_queue_unregister_dequeue.body =
      [](fixed_queue_t* /* q */) {
        // not implemented
        abort();
      };
}

FakeOsi::~FakeOsi() {
  test::mock::osi_alarm::alarm_free = {};
  test::mock::osi_alarm::alarm_new = {};

  test::mock::osi_allocator::osi_calloc = {};
  test::mock::osi_allocator::osi_free = {};
  test::mock::osi_allocator::osi_free_and_reset = {};
  test::mock::osi_allocator::osi_malloc = {};

  test::mock::osi_list::list_new = {};
  test::mock::osi_list::list_free = {};
  test::mock::osi_list::list_is_empty = {};
  test::mock::osi_list::list_foreach = {};
  test::mock::osi_list::list_contains = {};
  test::mock::osi_list::list_length = {};
  test::mock::osi_list::list_front = {};
  test::mock::osi_list::list_back = {};
  test::mock::osi_list::list_back_node = {};
  test::mock::osi_list::list_insert_after = {};
  test::mock::osi_list::list_prepend = {};
  test::mock::osi_list::list_append = {};
  test::mock::osi_list::list_remove = {};
  test::mock::osi_list::list_clear = {};
  test::mock::osi_list::list_begin = {};
  test::mock::osi_list::list_end = {};
  test::mock::osi_list::list_next = {};
  test::mock::osi_list::list_node = {};

  test::mock::osi_fixed_queue::fixed_queue_new = {};
  test::mock::osi_fixed_queue::fixed_queue_flush = {};
  test::mock::osi_fixed_queue::fixed_queue_free = {};
  test::mock::osi_fixed_queue::fixed_queue_enqueue = {};
  test::mock::osi_fixed_queue::fixed_queue_dequeue = {};
  test::mock::osi_fixed_queue::fixed_queue_length = {};
  test::mock::osi_fixed_queue::fixed_queue_is_empty = {};
  test::mock::osi_fixed_queue::fixed_queue_capacity = {};
  test::mock::osi_fixed_queue::fixed_queue_try_enqueue = {};
  test::mock::osi_fixed_queue::fixed_queue_try_dequeue = {};
  test::mock::osi_fixed_queue::fixed_queue_try_peek_first = {};
  test::mock::osi_fixed_queue::fixed_queue_try_peek_last = {};
  test::mock::osi_fixed_queue::fixed_queue_try_remove_from_queue = {};
  test::mock::osi_fixed_queue::fixed_queue_get_list = {};
  test::mock::osi_fixed_queue::fixed_queue_get_enqueue_fd = {};
  test::mock::osi_fixed_queue::fixed_queue_get_dequeue_fd = {};
  test::mock::osi_fixed_queue::fixed_queue_register_dequeue = {};
  test::mock::osi_fixed_queue::fixed_queue_unregister_dequeue = {};
}

}  // namespace fake
}  // namespace test
