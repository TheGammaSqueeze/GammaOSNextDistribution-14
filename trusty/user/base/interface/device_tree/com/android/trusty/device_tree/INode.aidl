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
import com.android.trusty.device_tree.IPropIterator;
import com.android.trusty.device_tree.Property;

interface INode {
     /**
      * get_name() - Get a node's name.
      *
      * Return: The node's name.
      */
     @utf8InCpp String get_name();

     /**
      * get_subnode() - Get a subnode of the given parent node.
      * @node_name: A single null-terminated string with the subnode's name.
      *
      * Return: A subnode.
      */
     INode get_subnode(in @utf8InCpp String node_name);

     /**
      * get_subnodes() - Get an iterator over the subnodes of the given parent node.
      *
      * Return: An iterator over the subnodes.
      */
     INodeIterator get_subnodes();

     /**
      * get_props() - Get an iterator over a node's properties.
      *
      * Return: An iterator over the properties.
      */
     IPropIterator get_props();

     /**
      * get_prop() - Get a node's property.
      * @prop_name: A single null-terminated string with the property's name.
      *
      * Return: The property in big-endian
      */
     Property get_prop(in @utf8InCpp String prop_name);
}
