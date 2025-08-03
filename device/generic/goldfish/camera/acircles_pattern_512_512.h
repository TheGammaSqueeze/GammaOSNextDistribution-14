/*
 * Copyright (C) 2023 The Android Open Source Project
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


constexpr unsigned kAcirclesPatternWidth = 512;
constexpr unsigned kAcirclesPatternHeight = 512;

/* acircles_pattern: 512x512 5 color RLE:
 *          nnnnnn00 - color4
 * nnnnnnnn nnnnnn10 - color4
 *          nnnnnCC1 - colorCC
 */
extern const unsigned char kAcirclesPatternRLE[8686];
