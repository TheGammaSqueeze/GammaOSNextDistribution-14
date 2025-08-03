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

SECTIONS {
  . = SIZEOF_HEADERS;
  .text : {
    *(.text.*)
    . = ALIGN(4096);
    exec_region_start = .;
    . += (512 * 1024);
    . = ALIGN(4096);
    exec_region_end = .;
  }
  .plt : {
    *(.plt)
  }
  /* Align following segments on the page boundary to prevent
   * next PT_LOAD segment from mapping over .plt section removing
   * executable flag from .plt. See also http://b/254823538.
   */
  . = ALIGN(4096);
  .fini_array : {
    *(.fini_array.*)
  }
  .init_array : {
    *(.ini_array.*)
  }
  .dynamic : {
    *(.dynamic)
  }
  .got : {
    *(.got)
  }
  .got.plt : {
    *(.got.plt)
  }
  /* Align the rest of segments on the page boundary to prevent
   * GNU_RELRO segment from mprotecting writable flag away
   * from them. See also http://b/261807330.
   */
  . = ALIGN(4096);
}
