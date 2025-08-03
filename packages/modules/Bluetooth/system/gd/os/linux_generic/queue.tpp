/*
 * Copyright 2019 The Android Open Source Project
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

template <typename T>
Queue<T>::Queue(size_t capacity) : enqueue_(capacity), dequeue_(0){};

template <typename T>
Queue<T>::~Queue() {
  ASSERT_LOG(enqueue_.handler_ == nullptr, "Enqueue is not unregistered");
  ASSERT_LOG(dequeue_.handler_ == nullptr, "Dequeue is not unregistered");
};

template <typename T>
void Queue<T>::RegisterEnqueue(Handler* handler, EnqueueCallback callback) {
  std::lock_guard<std::mutex> lock(mutex_);
  ASSERT(enqueue_.handler_ == nullptr);
  ASSERT(enqueue_.reactable_ == nullptr);
  enqueue_.handler_ = handler;
  enqueue_.reactable_ = enqueue_.handler_->thread_->GetReactor()->Register(
      enqueue_.reactive_semaphore_.GetFd(),
      base::Bind(&Queue<T>::EnqueueCallbackInternal, base::Unretained(this), std::move(callback)),
      base::Closure());
}

template <typename T>
void Queue<T>::UnregisterEnqueue() {
  Reactor* reactor = nullptr;
  Reactor::Reactable* to_unregister = nullptr;
  bool wait_for_unregister = false;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    ASSERT(enqueue_.reactable_ != nullptr);
    reactor = enqueue_.handler_->thread_->GetReactor();
    wait_for_unregister = (!enqueue_.handler_->thread_->IsSameThread());
    to_unregister = enqueue_.reactable_;
    enqueue_.reactable_ = nullptr;
    enqueue_.handler_ = nullptr;
  }
  reactor->Unregister(to_unregister);
  if (wait_for_unregister) {
    reactor->WaitForUnregisteredReactable(std::chrono::milliseconds(1000));
  }
}

template <typename T>
void Queue<T>::RegisterDequeue(Handler* handler, DequeueCallback callback) {
  std::lock_guard<std::mutex> lock(mutex_);
  ASSERT(dequeue_.handler_ == nullptr);
  ASSERT(dequeue_.reactable_ == nullptr);
  dequeue_.handler_ = handler;
  dequeue_.reactable_ = dequeue_.handler_->thread_->GetReactor()->Register(
      dequeue_.reactive_semaphore_.GetFd(), callback, base::Closure());
}

template <typename T>
void Queue<T>::UnregisterDequeue() {
  Reactor* reactor = nullptr;
  Reactor::Reactable* to_unregister = nullptr;
  bool wait_for_unregister = false;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    ASSERT(dequeue_.reactable_ != nullptr);
    reactor = dequeue_.handler_->thread_->GetReactor();
    wait_for_unregister = (!dequeue_.handler_->thread_->IsSameThread());
    to_unregister = dequeue_.reactable_;
    dequeue_.reactable_ = nullptr;
    dequeue_.handler_ = nullptr;
  }
  reactor->Unregister(to_unregister);
  if (wait_for_unregister) {
    reactor->WaitForUnregisteredReactable(std::chrono::milliseconds(1000));
  }
}

template <typename T>
std::unique_ptr<T> Queue<T>::TryDequeue() {
  std::lock_guard<std::mutex> lock(mutex_);

  if (queue_.empty()) {
    return nullptr;
  }

  dequeue_.reactive_semaphore_.Decrease();

  std::unique_ptr<T> data = std::move(queue_.front());
  queue_.pop();

  enqueue_.reactive_semaphore_.Increase();

  return data;
}

template <typename T>
void Queue<T>::EnqueueCallbackInternal(EnqueueCallback callback) {
  std::unique_ptr<T> data = callback.Run();
  ASSERT(data != nullptr);
  std::lock_guard<std::mutex> lock(mutex_);
  enqueue_.reactive_semaphore_.Decrease();
  queue_.push(std::move(data));
  dequeue_.reactive_semaphore_.Increase();
}
