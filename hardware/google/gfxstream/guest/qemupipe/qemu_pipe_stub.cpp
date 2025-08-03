// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

extern "C" {

int qemu_pipe_open_ns(const char*, const char*, int) {
    return -1;
}

int qemu_pipe_open(const char*) {
    return -1;
}

void qemu_pipe_close(int) {
}

int qemu_pipe_read(int, void*, int) {
    return -1;
}

int qemu_pipe_write(int, const void*, int) {
    return -1;
}

int qemu_pipe_try_again(int) {
    return -1;
}

void qemu_pipe_print_error(int) {
}

}  // extern "C"