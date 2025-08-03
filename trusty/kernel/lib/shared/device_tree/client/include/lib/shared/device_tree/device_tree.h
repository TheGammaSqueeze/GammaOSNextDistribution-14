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

#pragma once

#include <lib/shared/ibinder/ibinder.h>
#include <lk/compiler.h>
#include <stddef.h>
#include <stdint.h>

__BEGIN_CDECLS

// an error originating from backend functionality
const int DT_ERROR_GENERIC = 1;
// an error resulting from passing bad arguments, e.g. null pointers
const int DT_ERROR_INVALID_ARGS = 2;
// a failure to allocate a structure on the heap
const int DT_ERROR_NO_MEMORY = 3;
// a failure to find any set of nodes
const int DT_ERROR_NODE_NOT_FOUND = 4;
// a failure to find any set of properties
const int DT_ERROR_PROP_NOT_FOUND = 5;

struct device_tree_idevice_tree;
struct device_tree_inode;
struct device_tree_inode_iter;
struct device_tree_prop;
struct device_tree_iprop_iter;

/**
 * device_tree_get_service() - Connect to a device tree service
 *
 * @tree:    Pointer to the output device tree interface
 *
 * Return:   An error code reflecting success or failure
 */
int device_tree_get_service(struct device_tree_idevice_tree** tree);

/**
 * device_tree_idevice_tree_get_compatible_nodes() - Get a node iterator for
 *                                                 a given compatible string
 *
 * @self:          Pointer to the device tree interface
 * @compat_str:    The compatible string to search for
 * @iter:          Pointer to the output node iterator
 *
 * Return:         An error code reflecting success or failure
 */
int device_tree_idevice_tree_get_compatible_nodes(
        struct device_tree_idevice_tree* self,
        const char* compat_str,
        struct device_tree_inode_iter** iter);
/**
 * device_tree_idevice_tree_get_compatible_nodes_from_list() - Get a node
 * iterator for various compatible strings
 *
 * @self:               Pointer to the device tree interface
 * @compat_str_list:    Pointer to an array of compatible
 *                      strings to search for
 * @num_str:            The number of compatible strings in
 *                      `compat_str_list`
 * @iter:               Pointer to the output node iterator
 *
 * Return:              An error code reflecting success or failure
 */
int device_tree_idevice_tree_get_compatible_nodes_from_list(
        struct device_tree_idevice_tree* self,
        const char** compat_str_list,
        size_t num_str,
        struct device_tree_inode_iter** iter);

/**
 * device_tree_inode_iter_get_next_node() - Advance a node iterator
 *
 * @iter:    Pointer to the node iterator
 * @node:    Pointer to the output node
 *
 * Return:   An error code reflecting success or failure
 */
int device_tree_inode_iter_get_next_node(struct device_tree_inode_iter* iter,
                                         struct device_tree_inode** node);

/**
 * device_tree_inode_get_name() - Get a node's name
 *
 * @node:    Pointer to the node iterator
 * @name:    Pointer for the node name output. This pointer is only valid for
 *           the lifetime of the pointer to the node and will be freed when the
 *           node is freed.
 *
 * Return:   An error code reflecting success or failure
 */
int device_tree_inode_get_name(struct device_tree_inode* node,
                               const char** name);

/**
 * device_tree_inode_get_subnode() - Get a subnodes of a given node by name
 *
 * @parent:          Pointer to the parent node
 * @subnode_name:    Name of the subnode
 * @subnode:         Pointer for the output subnode
 *
 * Return:           An error code reflecting success or failure
 */
int device_tree_inode_get_subnode(struct device_tree_inode* parent,
                                  const char* subnode_name,
                                  struct device_tree_inode** subnode);

/**
 * device_tree_inode_get_subnodes() - Get an iterator over all subnodes of a
 *                                   given node
 *
 * @parent:    Pointer to the parent node
 * @iter:      Pointer for the output node iterator
 *
 * Return:     An error code reflecting success or failure
 */
int device_tree_inode_get_subnodes(struct device_tree_inode* parent,
                                   struct device_tree_inode_iter** iter);

/**
 * device_tree_inode_get_prop() - Get a node property by name
 *
 * @node:    Pointer to the node to search
 * @name:    Name of the node's property
 * @prop:    Pointer to the output property
 *
 * Return:   An error code reflecting success or failure
 */
int device_tree_inode_get_prop(struct device_tree_inode* node,
                               const char* name,
                               struct device_tree_prop** prop);

/**
 * device_tree_inode_get_props() - Get an iterator over all of a node's
 *                                properties
 *
 * @node:     Pointer to the node whose properties are to be iterated over
 * @prop:     Pointer to the output property iterator
 *
 * Return:    An error code reflecting success or failure
 */
int device_tree_inode_get_props(struct device_tree_inode* node,
                                struct device_tree_iprop_iter** prop);

/**
 * device_tree_iprop_iter_get_next_prop() - Advance a property iterator
 *
 * @iter:     Pointer to the property iterator to advance
 * @prop:     Pointer to the output property
 *
 * Return:    An error code reflecting success or failure
 */
int device_tree_iprop_iter_get_next_prop(struct device_tree_iprop_iter* iter,
                                         struct device_tree_prop** prop);

/**
 * device_tree_prop_get_name() - Get a property's name
 *
 * @prop:    Pointer to property
 * @name:    Pointer to the output property name. This pointer is only valid for
 *           the lifetime of the pointer to the property and will be freed when
 *           the property is freed.
 *
 * Return:   An error code reflecting success or failure
 */
int device_tree_prop_get_name(struct device_tree_prop* prop,
                              const char** name,
                              size_t* name_len);

/**
 * device_tree_prop_get_value() - Get a property's value
 *
 * @prop:     Pointer to the property
 * @value:    Pointer to the output property value. This pointer is only valid
 *            for the lifetime of the pointer to the property and will be freed
 *            when the property is freed. The property value is a big-endian
 *            byte array. There is no alignment guarantee.
 * @size:     Pointer to a size value set by the function
 *
 * Return:    An error code reflecting success or failure
 */
int device_tree_prop_get_value(struct device_tree_prop* prop,
                               uint8_t** value,
                               size_t* size);

/**
 * device_tree_prop_get_u32() - Get a property as a uint32_t
 *
 * @prop:     Pointer to the property. The property's value must be 4 bytes, but
 *            otherwise no type-checking is done so it's the caller's
 *            responsibility to ensure that the property is an integer.
 * @value:    Pointer to the output value. The value is returned in the host's
 *            endianness, not necessarily big-endian.
 *
 * Return:    An error code reflecting success or failure.
 */
int device_tree_prop_get_u32(struct device_tree_prop* prop, uint32_t* value);

/**
 * device_tree_prop_get_u64() - Get a property as a uint64_t
 *
 * @prop:     Pointer to the property. The property's value must be 8 bytes, but
 *            otherwise no type-checking is done so it's the caller's
 *            responsibility to ensure that the property is an integer.
 * @value:    Pointer to the output value. The value is returned in the hosts
 *            endianness, not necessarily big-endian.
 *
 * Return:    An error code reflecting success or failure.
 */
int device_tree_prop_get_u64(struct device_tree_prop* prop, uint64_t* value);

/**
 * device_tree_inode_release() - Release the reference to the
 * struct device_tree_inode*.
 *
 * @self:     Pointer to the struct device_tree_inode* to be released
 *
 */
void device_tree_inode_release(struct device_tree_inode** self);

/**
 * device_tree_inode_iter_release() - Release the reference to the
 * struct device_tree_inode_iter*.
 *
 * @self:     Pointer to the struct struct device_tree_inode_iter*
 *            to be released.
 *
 */
void device_tree_inode_iter_release(struct device_tree_inode_iter** self);

/**
 * device_tree_idevice_tree_release() - Release the reference to the
 * struct device_tree_idevice_tree*.
 *
 * @self:     Pointer to the struct struct device_tree_idevice_tree*
 *            to be released.
 *
 */
void device_tree_idevice_tree_release(struct device_tree_idevice_tree** self);

/**
 * device_tree_iprop_iter_release() - Release the reference to the
 * struct device_tree_iprop_iter*.
 *
 * @self:     Pointer to the struct struct device_tree_iprop_iter*
 *            to be released.
 *
 */
void device_tree_iprop_iter_release(struct device_tree_iprop_iter** self);

/**
 * device_tree_prop_release() - Release the reference to the
 * struct device_tree_prop*.
 *
 * @self:     Pointer to the struct struct device_tree_prop*
 *            to be released.
 *
 */
void device_tree_prop_release(struct device_tree_prop** self);

__END_CDECLS
