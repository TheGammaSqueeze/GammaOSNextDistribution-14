/*
 * Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
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

#include <stdint.h>

/**
 * struct pci_type0_config - Type 00h Configuration Space Header
 * @vendor_id:              The manufacturer of the device identifier.
 * @device_id:              The particular device identifier.
 * @command:                Control over a device's ability to generate and
 *                          respond to PCI cycles.
 * @status:                 Record status information for PCI bus related
 *                          events.
 * @revision_id:            Device specific revision identifier.
 * @class_code:             Identify the generic function of the device.
 * @cache_line_size:        Specify the system cacheline size.
 * @latency_timer:          The value of the Latency Timer for this PCI bus
 *                          master in units of PCI bus clocks.
 * @header_type:            Identify the layout of the second part of the
 *                          predefined header.
 * @bist:                   Control and status of Built-in Self Test.
 * @base_addr_reg0:         Base Address register 0
 * @base_addr_reg1:         Base Address register 1
 * @base_addr_reg2:         Base Address register 2
 * @base_addr_reg3:         Base Address register 3
 * @base_addr_reg4:         Base Address register 4
 * @base_addr_reg5:         Base Address register 5
 * @cardbus_cis_pointer:    Used by those devices that want to share silicon
 *                          between CardBus and PCI.
 * @subsystem_vendor_id:    Vendor of the add-in card or subsystem.
 * @subsystem_id:           Vendor specific identifier.
 * @expansion_rom_base:     Base address and size information for expansion ROM.
 * @capabilities_pointer:   Point to a linked list of new capabilities
 *                          implemented by this device.
 * @rsvd:                   Reserved.
 * @interrupt_line:         Communicate interrupt line routing information.
 * @interrupt_pin:          Interrupt pin the device (or device function) uses.
 * @min_gnt:                Specify how long a burst period the device needs
 *                          assuming a clock rate of 33 MHz.
 * @max_lat:                Specify how often the device needs to gain access
 *                          to the PCI bus.
 */
struct pci_type0_config {
    uint16_t vendor_id;
    uint16_t device_id;
    uint16_t command;
    uint16_t status;
    uint8_t revision_id;
    uint8_t class_code[3];
    uint8_t cache_line_size;
    uint8_t latency_timer;
    uint8_t header_type;
    uint8_t bist;
    uint32_t base_addr_reg0;
    uint32_t base_addr_reg1;
    uint32_t base_addr_reg2;
    uint32_t base_addr_reg3;
    uint32_t base_addr_reg4;
    uint32_t base_addr_reg5;
    uint32_t cardbus_cis_pointer;
    uint16_t subsystem_vendor_id;
    uint16_t subsystem_id;
    uint32_t expansion_rom_base;
    uint8_t capabilities_pointer;
    uint8_t rsvd[7];
    uint8_t interrupt_line;
    uint8_t interrupt_pin;
    uint8_t min_gnt;
    uint8_t max_lat;
};

/*
 * Memory Space bit in Command Register.
 *  Control a device's response to Memory Space access.
 */
#define CMD_MEM_SPACE_BIT_POSITION 1

/*
 * Capabilites bit in Status Register.
 *  This optional ready-only bit indicates whether or not this device
 *  implements the pointer for a New Capabilities linked list at
 *  offset 34h.
 */
#define STATUS_CAP_LIST_BIT_POSITION 4
