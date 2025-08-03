/*
 * Copyright 2020 The Android Open Source Project
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

#include <base/strings/stringprintf.h>

#include <string>

#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/idl.h"
#include "macros.h"

namespace bluetooth {
namespace dumpsys {
namespace internal {

constexpr char kPrivacyAttributeKeyword[] = "privacy";

enum PrivacyLevel {
  kPrivate = 0,
  kOpaque = 1,
  kAnonymized = 2,
  kAny = 4,
  kDefaultPrivacyLevel = kPrivate,
};

/**
 * Remove the field offset from flatbuffer table eliminating ability to
 * access value.
 *
 * @param table Table under consideration for field removeal
 * @param field_offset Virtual offset of field into table.
 */
void ScrubFromTable(flatbuffers::Table* table, flatbuffers::voffset_t field_offset);

/**
 * Overwrite ihe contents of flatbuffer string with the integer value proviced.
 * The entire size of the string will be set to the value provided.
 *
 * @param string Flatbuffer string under consideration for content changing.
 * @param value Value to overwrite the string contents.
 */
void ReplaceInString(flatbuffers::String* string, int value);

/**
 * Overwrite the contents of flatbuffer string with a hashed value.
 * The portion of the string greater than the hash value will be set to SPACE.
 * If the string is not large enough for the entire hash value, the hash
 * value will be truncated to the size of the string.
 *
 * @param string Flatbuffer string under consideration for content changing.
 */
void RandomizeInString(flatbuffers::String* string);

/**
 * Returns the privacy level name corresponding to the axtual numeric level.
 *
 * @param privacy_level PrivacyLevel
 *
 * @return Name of privacy level.
 */
const char* PrivacyLevelName(PrivacyLevel privacy_level);

/**
 * Returns the privacy level for the given field.  If there is no explicitly
 * privacy level for this field, the default privacy level is returned.
 *
 * @param field The reflection field for the schema
 *
 * @return Privacy level enumeration value
 */
PrivacyLevel FindFieldPrivacyLevel(const reflection::Field& field);

/**
 * Returns the privacy level for given privacy level keyword name.
 * If the privacy level for this field, the default privacy level is returned.
 *
 * @param name The privacy level name.
 *
 * @return Privacy level enumeration value.
 */
PrivacyLevel GetPrivacyLevelAttribute(const std::string& name);

/**
 * Find a the reflection object that corresponds to the name provided.
 * Returns nullptr is not found.
 *
 * @param objects Vector container of flatbuffer objects
 * @param name Flatbuffer string name to search
 *
 * @return Reflection object if found, nullptr otherwise.
 */
const reflection::Object* FindReflectionObject(
    const flatbuffers::Vector<flatbuffers::Offset<reflection::Object>>* objects, const flatbuffers::String* name);

/**
 * Process and filter the respective data types.
 *
 * @param field The reflection field schema.
 * @param table The mutable table data corresponding to the schema.
 * @param privacy_level The privacy level in which to filter the data.
 *
 * @return true if successfully filtered, false otherwise.
 */
bool FilterTypeBool(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level);
bool FilterTypeFloat(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level);
bool FilterTypeInteger(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level);
bool FilterTypeLong(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level);
bool FilterTypeString(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level);
bool FilterTypeStruct(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level);

inline std::string FlatbufferTypeText(const flatbuffers::BaseType& type) {
  switch (type) {
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_NONE);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_BOOL);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_CHAR);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_UCHAR);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_SHORT);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_USHORT);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_INT);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_UINT);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_LONG);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_ULONG);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_FLOAT);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_DOUBLE);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_STRING);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_VECTOR);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_STRUCT);
    CASE_RETURN_TEXT(flatbuffers::BASE_TYPE_UNION);
    default:
      return base::StringPrintf("UNKNOWN[%d]", (int)type);
  }
}

}  // namespace internal
}  // namespace dumpsys
}  // namespace bluetooth
