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

#ifndef __SAMSUNG_DECON_ZUMA_H_
#define __SAMSUNG_DECON_ZUMA_H_

enum decon_idma_type {
    IDMA_GFS0 = 0,
    IDMA_VGRFS0,
    IDMA_GFS1,
    IDMA_VGRFS1,
    IDMA_GFS2,
    IDMA_VGRFS2,
    IDMA_GFS3,
    IDMA_GFS4,
    IDMA_VGRFS3,
    IDMA_GFS5,
    IDMA_VGRFS4,
    IDMA_GFS6,
    IDMA_VGRFS5,
    IDMA_GFS7,
    ODMA_WB,
    MAX_DECON_DMA_TYPE,
};

#endif // __SAMSUNG_DECON_ZUMA_H_
