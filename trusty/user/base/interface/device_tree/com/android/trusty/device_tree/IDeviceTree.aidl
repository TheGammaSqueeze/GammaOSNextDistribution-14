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

package com.android.trusty.device_tree;

import com.android.trusty.device_tree.INodeIterator;
import com.android.trusty.device_tree.INode;

interface IDeviceTree {

    @utf8InCpp
    const String PORT = "com.android.trusty.device_tree";

    @utf8InCpp
    const String KERNEL_PORT = "com.android.kernel.device_tree";

    const int ERROR_NONE = 0;
    const int ERROR_GENERIC = 1;
    const int ERROR_INVALID_ARGS = 2;
    const int ERROR_NO_MEMORY = 3;
    const int ERROR_NODE_NOT_FOUND = 4;
    const int ERROR_PROP_NOT_FOUND = 5;

    /**
     * get_compatible_nodes_from_list() - Get an iterator over nodes with one of
     *                                    the given compatible strings.
     * @compatible: A list of null-terminated compatible strings to search for.
     *
     * Return: An iterator over the compatible nodes.
     */
     INodeIterator get_compatible_nodes_from_list(in @utf8InCpp String[] compatible);
}
