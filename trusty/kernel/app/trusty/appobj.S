/*
 * Copyright (c) 2017, Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#if IS_64BIT
#define DATA_PTR .quad
#else
#define DATA_PTR .word
#endif

.section .task.data, "a"
.task.body.start:
.incbin USER_TASK_ELF
.task.body.end:

.section .trusty_app.manifest, "a"
.task.manifest.start:
.align 2
.incbin MANIFEST_DATA
.task.manifest.end:

.section .task.list, "a"
DATA_PTR .task.manifest.start - .
DATA_PTR .task.manifest.end - .
DATA_PTR .task.body.start - .
DATA_PTR .task.body.end - .
