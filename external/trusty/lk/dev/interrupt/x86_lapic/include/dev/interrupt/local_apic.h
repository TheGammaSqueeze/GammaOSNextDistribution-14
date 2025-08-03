/*
 * Copyright (c) 2012-2018 LK Trusty Authors. All Rights Reserved.
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

#include <stdbool.h>
#include <sys/types.h>

/**
 * local_apic_init() -- Initialize Local APIC
 * @level: LK init level
 */
void local_apic_init(uint level);

/**
 * lapic_eoi() -- Trigger local APIC EOI
 */
void lapic_eoi(void);

/**
 * lapic_software_disable() -- Software disable local APIC
 *
 * Trusty LK initialize local APIC to handle external interrupt, local APIC
 * should be software disabled before switch back to Non-secure world if
 * local APIC will be operated by Non-secure world.
 */
void lapic_software_disable(void);

/**
 * send_self_ipi() -- Trigger self IPI via local APIC
 * @vector: vector ID to be triggered.
 */
bool send_self_ipi(uint32_t vector);
