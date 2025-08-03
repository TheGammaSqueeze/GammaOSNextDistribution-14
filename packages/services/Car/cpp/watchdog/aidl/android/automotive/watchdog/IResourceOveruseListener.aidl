/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.automotive.watchdog;

import android.automotive.watchdog.ResourceOveruseStats;

@VintfStability
oneway interface IResourceOveruseListener {
  /**
   * Listener to get notifications on resource overuse. Called once for each resource overuse.
   *
   * @param resourceOveruseStats           Resource overuse stats only for the resource that the
   *                                       listener subscribed to and that is currently overused.
   *                                       Listener must call the methods
   *                                       {@link ResourceOveruseStats#getTag} and
   *                                       {@link ResourceOveruseStats#get<>} on the parameter to
   *                                       retrieve the type of resource overuse stats and the
   *                                       specific resource overuse stats, respectively.
   */
  void onOveruse(in ResourceOveruseStats resourceOveruseStats);
}
