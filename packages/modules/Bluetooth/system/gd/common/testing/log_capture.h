/*
 * Copyright 2022 The Android Open Source Project
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

#include <cstddef>
#include <future>
#include <string>

namespace bluetooth {
namespace testing {

class LogCapture {
 public:
  LogCapture();
  ~LogCapture();

  // Rewind file pointer to start of log
  // Returns a |this| pointer for chaining.  See |Find|
  LogCapture* Rewind();
  // Searches from filepointer to end of file for |to_find| string
  // Returns true if found, false otherwise
  bool Find(const std::string& to_find);
  // Reads and returns the entirety of the backing store into a string
  std::string Read();
  // Flushes contents of log capture back to |stderr|
  void Flush();
  // Synchronize buffer contents to file descriptor
  void Sync();
  // Returns the backing store size in bytes
  size_t Size() const;
  // Truncates and resets the file pointer discarding all logs up to this point
  void Reset();
  // Wait until the provided string shows up in the logs
  void WaitUntilLogContains(const std::string& text);

 private:
  std::pair<int, int> create_backing_store() const;
  bool set_non_blocking(int fd) const;
  void clean_up();

  int dup_fd_{-1};
  int fd_{-1};
  int original_stderr_fd_{-1};
};

}  // namespace testing
}  // namespace bluetooth
