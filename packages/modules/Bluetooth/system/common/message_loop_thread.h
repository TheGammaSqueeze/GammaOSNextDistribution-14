/*
 * Copyright 2018 The Android Open Source Project
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

#pragma once

#include <base/functional/bind.h>
#include <base/location.h>
#include <base/run_loop.h>
#include <base/threading/platform_thread.h>
#include <unistd.h>

#include <future>
#include <string>
#include <thread>

#include "abstract_message_loop.h"
#include "common/contextual_callback.h"
#include "common/i_postable_context.h"

namespace bluetooth {

namespace common {

/**
 * An interface to various thread related functionality
 */
class MessageLoopThread final : public IPostableContext {
 public:
  /**
   * Create a message loop thread with name. Thread won't be running until
   * StartUp is called.
   *
   * @param thread_name name of this worker thread
   */
  explicit MessageLoopThread(const std::string& thread_name);

  MessageLoopThread(const MessageLoopThread&) = delete;
  MessageLoopThread& operator=(const MessageLoopThread&) = delete;

  /**
   * Destroys the message loop thread automatically when it goes out of scope
   */
  ~MessageLoopThread();

  /**
   * Start the underlying thread. Blocks until all thread infrastructure is
   * setup. IsRunning() and DoInThread() should return true after this call.
   * Blocks until the thread is successfully started.
   *
   * Repeated call to this method will only start this thread once
   */
  void StartUp();

  /**
   * Post a task to run on this thread
   *
   * @param from_here location where this task is originated
   * @param task task created through base::Bind()
   * @return true if task is successfully scheduled, false if task cannot be
   * scheduled
   */
  bool DoInThread(const base::Location& from_here, base::OnceClosure task);

  /**
   * Shutdown the current thread as if it is never started. IsRunning() and
   * DoInThread() will return false after this call. Blocks until the thread is
   * joined and freed. This thread can be re-started again using StartUp()
   *
   * Repeated call to this method will only stop this thread once
   *
   * NOTE: Should never be called on the thread itself to avoid deadlock
   */
  void ShutDown();

  /**
   * Get the current thread ID returned by PlatformThread::CurrentId()
   *
   * On Android platform, this value should be the same as the tid logged by
   * logcat, which is returned by gettid(). On other platform, this thread id
   * may have different meanings. Therefore, this ID is only good for logging
   * and thread comparison purpose
   *
   * @return this thread's ID
   */
  base::PlatformThreadId GetThreadId() const;

  /**
   * Get this thread's name set in constructor
   *
   * @return this thread's name set in constructor
   */
  std::string GetName() const;

  /**
   * Get a string representation of this thread
   *
   * @return a string representation of this thread
   */
  std::string ToString() const;

  /**
   * Check if this thread is running
   *
   * @return true iff this thread is running and is able to do task
   */
  bool IsRunning() const;

  /**
   * Attempt to make scheduling for this thread real time
   *
   * @return true on success, false otherwise
   */
  bool EnableRealTimeScheduling();

  /**
   * Return the weak pointer to this object. This can be useful when posting
   * delayed tasks to this MessageLoopThread using Timer.
   */
  base::WeakPtr<MessageLoopThread> GetWeakPtr();

  /**
   * Return the message loop for this thread. Accessing raw message loop is not
   * recommended as message loop can be freed internally.
   *
   * @return message loop associated with this thread, nullptr if thread is not
   * running
   */
  btbase::AbstractMessageLoop* message_loop() const;

  /**
   * Post a task to run on this thread after a specified delay. If the task
   * needs to be cancelable before it's run, use base::CancelableClosure type
   * for task closure. For example:
   * <code>
   * base::CancelableClosure cancelable_task;
   * cancelable_task.Reset(base::Bind(...)); // bind the task
   * same_thread->DoInThreadDelayed(FROM_HERE,
   *                                cancelable_task.callback(), delay);
   * ...
   * // Cancel the task closure
   * same_thread->DoInThread(FROM_HERE,
   *                         base::Bind(&base::CancelableClosure::Cancel,
   *                                    base::Unretained(&cancelable_task)));
   * </code>
   *
   * Warning: base::CancelableClosure objects must be created on, posted to,
   * cancelled on, and destroyed on the same thread.
   *
   * @param from_here location where this task is originated
   * @param task task created through base::Bind()
   * @param delay delay for the task to be executed
   * @return true if task is successfully scheduled, false if task cannot be
   * scheduled
   */
  bool DoInThreadDelayed(const base::Location& from_here,
                         base::OnceClosure task,
                         std::chrono::microseconds delay);
  /**
   * Wrapper around DoInThread without a location.
   */
  void Post(base::OnceClosure closure) override;

  template <typename Functor, typename... Args>
  auto BindOnce(Functor&& functor, Args&&... args) {
    return common::ContextualOnceCallback(
        common::BindOnce(std::forward<Functor>(functor),
                         std::forward<Args>(args)...),
        this);
  }

  template <typename Functor, typename T, typename... Args>
  auto BindOnceOn(T* obj, Functor&& functor, Args&&... args) {
    return common::ContextualOnceCallback(
        common::BindOnce(std::forward<Functor>(functor),
                         common::Unretained(obj), std::forward<Args>(args)...),
        this);
  }

  template <typename Functor, typename... Args>
  auto Bind(Functor&& functor, Args&&... args) {
    return common::ContextualCallback(
        common::Bind(std::forward<Functor>(functor),
                     std::forward<Args>(args)...),
        this);
  }

  template <typename Functor, typename T, typename... Args>
  auto BindOn(T* obj, Functor&& functor, Args&&... args) {
    return common::ContextualCallback(
        common::Bind(std::forward<Functor>(functor), common::Unretained(obj),
                     std::forward<Args>(args)...),
        this);
  }

 private:
  /**
   * Static method to run the thread
   *
   * This is used instead of a C++ lambda because of the use of std::shared_ptr
   *
   * @param context needs to be a pointer to an instance of MessageLoopThread
   * @param start_up_promise a std::promise that is used to notify calling
   * thread the completion of message loop start-up
   */
  static void RunThread(MessageLoopThread* context,
                        std::promise<void> start_up_promise);

  /**
   * Actual method to run the thread, blocking until ShutDown() is called
   *
   * @param start_up_promise a std::promise that is used to notify calling
   * thread the completion of message loop start-up
   */
  void Run(std::promise<void> start_up_promise);

  mutable std::recursive_mutex api_mutex_;
  const std::string thread_name_;
  btbase::AbstractMessageLoop* message_loop_;
  base::RunLoop* run_loop_;
  std::thread* thread_;
  base::PlatformThreadId thread_id_;
  // Linux specific abstractions
  pid_t linux_tid_;
  base::WeakPtrFactory<MessageLoopThread> weak_ptr_factory_;
  bool shutting_down_;
};

inline std::ostream& operator<<(std::ostream& os,
                                const bluetooth::common::MessageLoopThread& a) {
  os << a.ToString();
  return os;
}

}  // namespace common

}  // namespace bluetooth
