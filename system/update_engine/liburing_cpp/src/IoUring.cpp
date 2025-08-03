//
// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <asm-generic/errno-base.h>
#include <liburing_cpp/IoUring.h>
#include <string.h>

#include <algorithm>
#include <iostream>
#include <memory>

#include "liburing.h"
#include "liburing_cpp/IoUringCQE.h"

namespace io_uring_cpp {

template <typename T>
bool IsZeroInitialized(const T& val) {
  auto begin = reinterpret_cast<const char*>(&val);
  auto end = begin + sizeof(val);
  return std::all_of(begin, end, [](const auto& a) { return a == 0; });
}

class IoUring final : public IoUringInterface {
 public:
  ~IoUring() override {
    if (!IsZeroInitialized(ring)) {
      if (buffer_registered_) {
        UnregisterBuffers();
      }
      if (files_registered_) {
        UnregisterFiles();
      }
      io_uring_queue_exit(&ring);
    }
  }
  IoUring(const IoUring&) = delete;
  IoUring(IoUring&& rhs) {
    ring = rhs.ring;
    memset(&rhs.ring, 0, sizeof(rhs.ring));
  }
  IoUring& operator=(IoUring&& rhs) {
    std::swap(ring, rhs.ring);
    return *this;
  }
  Errno RegisterBuffers(const struct iovec* iovecs,
                        size_t iovec_size) override {
    const auto ret =
        Errno(io_uring_register_buffers(&ring, iovecs, iovec_size));
    buffer_registered_ = ret.IsOk();
    return ret;
  }

  Errno UnregisterBuffers() override {
    const auto ret = Errno(io_uring_unregister_buffers(&ring));
    buffer_registered_ = !ret.IsOk();
    return ret;
  }

  Errno RegisterFiles(const int* files, size_t files_size) override {
    const auto ret = Errno(io_uring_register_files(&ring, files, files_size));
    files_registered_ = ret.IsOk();
    return ret;
  }

  Errno UnregisterFiles() {
    const auto ret = Errno(io_uring_unregister_files(&ring));
    files_registered_ = !ret.IsOk();
    return ret;
  }

  IoUringSQE PrepRead(int fd, void* buf, unsigned nbytes,
                      uint64_t offset) override {
    auto sqe = io_uring_get_sqe(&ring);
    if (sqe == nullptr) {
      return IoUringSQE{nullptr};
    }
    io_uring_prep_read(sqe, fd, buf, nbytes, offset);
    return IoUringSQE{static_cast<void*>(sqe)};
  }
  IoUringSQE PrepWrite(int fd, const void* buf, unsigned nbytes,
                       uint64_t offset) override {
    auto sqe = io_uring_get_sqe(&ring);
    if (sqe == nullptr) {
      return IoUringSQE{nullptr};
    }
    io_uring_prep_write(sqe, fd, buf, nbytes, offset);
    return IoUringSQE{static_cast<void*>(sqe)};
  }

  size_t SQELeft() const override { return io_uring_sq_space_left(&ring); }
  size_t SQEReady() const override { return io_uring_sq_ready(&ring); }

  IoUringSubmitResult Submit() override {
    return IoUringSubmitResult{io_uring_submit(&ring)};
  }

  IoUringSubmitResult SubmitAndWait(size_t completions) override {
    return IoUringSubmitResult{io_uring_submit_and_wait(&ring, completions)};
  }

  Result<Errno, std::vector<IoUringCQE>> PopCQE(
      const unsigned int count) override {
    std::vector<io_uring_cqe*> cqe_ptrs;
    cqe_ptrs.resize(count);
    const auto ret = io_uring_wait_cqe_nr(&ring, cqe_ptrs.data(), count);
    if (ret != 0) {
      return {Errno(ret)};
    }
    const auto filled = io_uring_peek_batch_cqe(&ring, cqe_ptrs.data(), count);
    if (filled != count) {
      return {Errno(EAGAIN)};
    }
    std::vector<IoUringCQE> cqes;
    cqes.reserve(count);
    for (const auto& cqe : cqe_ptrs) {
      if (cqe == nullptr) {
        return {Errno(EAGAIN)};
      }
      cqes.push_back(IoUringCQE(cqe->res, cqe->flags, cqe->user_data));
      io_uring_cqe_seen(&ring, cqe);
    }
    return {cqes};
  }

  Result<Errno, IoUringCQE> PopCQE() override {
    struct io_uring_cqe* ptr{};
    const auto ret = io_uring_wait_cqe(&ring, &ptr);
    if (ret != 0) {
      return {Errno(ret)};
    }
    const auto cqe = IoUringCQE(ptr->res, ptr->flags, ptr->user_data);
    io_uring_cqe_seen(&ring, ptr);
    return {cqe};
  }

  Result<Errno, IoUringCQE> PeekCQE() override {
    struct io_uring_cqe* ptr{};
    const auto ret = io_uring_peek_cqe(&ring, &ptr);
    if (ret != 0) {
      return {Errno(ret)};
    }
    return {IoUringCQE(ptr->res, ptr->flags, ptr->user_data)};
  }

  IoUring(struct io_uring r) : ring(r) {}

 private:
  struct io_uring ring {};
  bool buffer_registered_ = false;
  bool files_registered_ = false;
  std::atomic<size_t> request_id_{};
};

const char* Errno::ErrMsg() {
  if (error_code == 0) {
    return nullptr;
  }
  return strerror(error_code);
}

std::ostream& operator<<(std::ostream& out, Errno err) {
  out << err.ErrCode() << ", " << err.ErrMsg();
  return out;
}

std::unique_ptr<IoUringInterface> IoUringInterface::CreateLinuxIoUring(
    int queue_depth, int flags) {
  struct io_uring ring {};
  const auto err = io_uring_queue_init(queue_depth, &ring, flags);
  if (err) {
    errno = -err;
    return {};
  }
  return std::unique_ptr<IoUringInterface>(new IoUring(ring));
}

}  // namespace io_uring_cpp
