// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <gtest/gtest.h>

#include <ditto/shared_variables.h>

using dittosuite::SharedVariables;

TEST(SharedVariablesTest, SharedVariablesTestSetSingle) {
  std::list<int> thread_ids;
  thread_ids.push_back(0);
  auto key = SharedVariables::GetKey(thread_ids, "variable_name");

  SharedVariables::Variant shared_variable;
  shared_variable = 1;

  SharedVariables::Set(key, shared_variable);
  ASSERT_EQ(SharedVariables::Get(key), shared_variable);
}

TEST(SharedVariablesTest, SharedVariablesTestSetMultipleSameNames) {
  std::list<int> thread_ids;
  thread_ids.push_back(1);
  auto first_key = SharedVariables::GetKey(thread_ids, "first_variable_name");
  auto second_key = SharedVariables::GetKey(thread_ids, "first_variable_name");

  ASSERT_EQ(first_key, second_key);

  SharedVariables::Variant shared_variable;
  shared_variable = 1;
  SharedVariables::Set(first_key, shared_variable);

  ASSERT_EQ(SharedVariables::Get(first_key), shared_variable);
  ASSERT_EQ(SharedVariables::Get(second_key), shared_variable);

  shared_variable = 2;
  SharedVariables::Set(second_key, shared_variable);

  ASSERT_EQ(SharedVariables::Get(first_key), shared_variable);
  ASSERT_EQ(SharedVariables::Get(second_key), shared_variable);
}

TEST(SharedVariablesTest, SharedVariablesTestSetMultipleDifferentNames) {
  std::list<int> thread_ids;
  thread_ids.push_back(2);
  auto first_key = SharedVariables::GetKey(thread_ids, "first_variable_name");
  auto second_key = SharedVariables::GetKey(thread_ids, "second_variable_name");

  ASSERT_NE(first_key, second_key);

  SharedVariables::Variant first_shared_variable = 1;
  SharedVariables::Set(first_key, first_shared_variable);

  ASSERT_EQ(SharedVariables::Get(first_key), first_shared_variable);

  SharedVariables::Variant second_shared_variable = 2;
  SharedVariables::Set(second_key, second_shared_variable);

  ASSERT_EQ(SharedVariables::Get(first_key), first_shared_variable);
  ASSERT_EQ(SharedVariables::Get(second_key), second_shared_variable);
}

TEST(SharedVariablesTest, DifferentKeyForDifferentThread) {
  std::list<int> first_thread_ids;
  first_thread_ids.push_back(3);
  auto first_key = SharedVariables::GetKey(first_thread_ids, "variable_name");

  std::list<int> second_thread_ids;
  second_thread_ids.push_back(4);
  auto second_key = SharedVariables::GetKey(second_thread_ids, "variable_name");

  ASSERT_NE(first_key, second_key);
}

TEST(SharedVariablesTest, SameKeyForChildThread) {
  std::list<int> thread_ids;
  thread_ids.push_back(5);

  auto first_key = SharedVariables::GetKey(thread_ids, "variable_name");

  thread_ids.push_back(6);
  auto second_key = SharedVariables::GetKey(thread_ids, "variable_name");

  ASSERT_EQ(first_key, second_key);
}

TEST(SharedVariablesTest, SameKeyForGrandchildThread) {
  std::list<int> thread_ids;
  thread_ids.push_back(7);

  auto first_key = SharedVariables::GetKey(thread_ids, "variable_name");

  thread_ids.push_back(8);
  thread_ids.push_back(9);
  auto second_key = SharedVariables::GetKey(thread_ids, "variable_name");

  ASSERT_EQ(first_key, second_key);
}
