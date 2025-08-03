/*
 * Copyright (c) 2020 Google Inc. All rights reserved
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

#pragma once

#include <lib/trusty/trusty_app.h>
#include <uapi/err.h>

/**
 * struct pc_symbol_info - symbol information about an instruction address
 * @symbol: name of the function
 * @offset: offset of the instruction within @symbol
 * @size: size of the @symbol
 */
struct pc_symbol_info {
    const char* symbol;
    uintptr_t offset;
    uintptr_t size;
};

/**
 * trusty_app_symbolize() - find symbol closest to a given instruction address
 * @app: app containing the instruction
 * @pc: instruction address being symbolized
 * @info: pointer to a struct pc_symbol_info to be filled out
 *
 * Return: NO_ERROR on success, ERR_NOT_FOUND otherwise
 */
int trusty_app_symbolize(struct trusty_app* app,
                         uintptr_t pc,
                         struct pc_symbol_info* info);
