/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ___SAMSUNG_DECON_H__
#define ___SAMSUNG_DECON_H__

enum decon_idma_type {
  IDMA_GF0 = 0,
  IDMA_GF1,
  IDMA_VG0,
  IDMA_VGF0,
  IDMA_VGS0,
  IDMA_VGRFS0,
  ODMA_WB,
  MAX_DECON_DMA_TYPE,
};

#endif
