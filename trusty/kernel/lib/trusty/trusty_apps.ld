/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
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

/* should be included in all lk linker scripts in the .data segment */
SECTIONS {
.__trusty_app_list : {
__trusty_app_list_start = .;
KEEP(*(.task.list .task.list.*))
__trusty_app_list_end = .;
}
}
INSERT AFTER .rodata;

SECTIONS {
.__trusty_app : {
/* pre-built secure apps get inserted here */
. = ALIGN(0x1000);
KEEP(*(.task.data .task.data.*))
. = ALIGN(0x1000);
}
}
INSERT AFTER .rodata;

SECTIONS {
.__manifest_data : {
/* pre-built secure apps manifest get inserted here */
KEEP(*(.trusty_app.manifest .trusty_app.manifest.*))
}
}
INSERT AFTER .rodata;
