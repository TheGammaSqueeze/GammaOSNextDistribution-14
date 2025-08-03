/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_POD_NER_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_POD_NER_H_

#if defined TC3_POD_NER_ANNOTATOR_FLAG_DEFINED
#include "annotator/pod_ner/pod-ner-flag-defined.h"
#else
#if defined TC3_POD_NER_ANNOTATOR_IMPL
#include "annotator/pod_ner/pod-ner-impl.h"
#elif defined TC3_POD_NER_ANNOTATOR_DUMMY
#include "annotator/pod_ner/pod-ner-dummy.h"
#else
#error No POD NER implementation specified.
#endif
#endif  // TC3_POD_NER_ANNOTATOR_FLAG_DEFINED

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_POD_NER_POD_NER_H_
