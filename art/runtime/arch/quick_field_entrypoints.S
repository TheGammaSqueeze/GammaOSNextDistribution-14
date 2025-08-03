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

// Note: Functions `art{Get,Set}<Kind>{Static,Instance}FromCompiledCode` are
// defined with a macro in runtime/entrypoints/quick/quick_field_entrypoints.cc.

.macro GENERATE_STATIC_FIELD_GETTERS
ONE_ARG_DOWNCALL art_quick_get_boolean_static, \
                 artGetBooleanStaticFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
ONE_ARG_DOWNCALL art_quick_get_byte_static, \
                 artGetByteStaticFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
ONE_ARG_DOWNCALL art_quick_get_char_static, \
                 artGetCharStaticFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
ONE_ARG_DOWNCALL art_quick_get_short_static, \
                 artGetShortStaticFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
ONE_ARG_DOWNCALL art_quick_get32_static, \
                 artGet32StaticFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
ONE_ARG_DOWNCALL art_quick_get_obj_static, \
                 artGetObjStaticFromCompiledCode, \
                 RETURN_REF_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
ONE_ARG_DOWNCALL art_quick_get64_static, \
                 artGet64StaticFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
.endm

.macro GENERATE_INSTANCE_FIELD_GETTERS
TWO_ARG_DOWNCALL art_quick_get_boolean_instance, \
                 artGetBooleanInstanceFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
TWO_ARG_DOWNCALL art_quick_get_byte_instance, \
                 artGetByteInstanceFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
TWO_ARG_DOWNCALL art_quick_get_char_instance, \
                 artGetCharInstanceFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
TWO_ARG_DOWNCALL art_quick_get_short_instance, \
                 artGetShortInstanceFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
TWO_ARG_DOWNCALL art_quick_get32_instance, \
                 artGet32InstanceFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
TWO_ARG_DOWNCALL art_quick_get_obj_instance, \
                 artGetObjInstanceFromCompiledCode, \
                 RETURN_REF_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
TWO_ARG_DOWNCALL art_quick_get64_instance, \
                 artGet64InstanceFromCompiledCode, \
                 RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
.endm

.macro GENERATE_STATIC_FIELD_SETTERS emit64 = 1
TWO_ARG_DOWNCALL art_quick_set8_static, \
                 artSet8StaticFromCompiledCode, \
                 RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
TWO_ARG_DOWNCALL art_quick_set16_static, \
                 artSet16StaticFromCompiledCode, \
                 RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
TWO_ARG_DOWNCALL art_quick_set32_static, \
                 artSet32StaticFromCompiledCode, \
                 RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
TWO_ARG_DOWNCALL art_quick_set_obj_static, \
                 artSetObjStaticFromCompiledCode, \
                 RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
.if \emit64
TWO_ARG_DOWNCALL art_quick_set64_static, \
                 artSet64StaticFromCompiledCode, \
                 RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
.endif
.endm

.macro GENERATE_INSTANCE_FIELD_SETTERS emit64 = 1
THREE_ARG_DOWNCALL art_quick_set8_instance, \
                   artSet8InstanceFromCompiledCode, \
                   RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
THREE_ARG_DOWNCALL art_quick_set16_instance, \
                   artSet16InstanceFromCompiledCode, \
                   RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
THREE_ARG_DOWNCALL art_quick_set32_instance, \
                   artSet32InstanceFromCompiledCode, \
                   RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
THREE_ARG_DOWNCALL art_quick_set_obj_instance, \
                   artSetObjInstanceFromCompiledCode, \
                   RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
.if \emit64
THREE_ARG_DOWNCALL art_quick_set64_instance, \
                   artSet64InstanceFromCompiledCode, \
                   RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
.endif
.endm

.macro GENERATE_FIELD_ENTRYPOINTS
    GENERATE_STATIC_FIELD_GETTERS
    GENERATE_INSTANCE_FIELD_GETTERS
    GENERATE_STATIC_FIELD_SETTERS
    GENERATE_INSTANCE_FIELD_SETTERS
.endm
