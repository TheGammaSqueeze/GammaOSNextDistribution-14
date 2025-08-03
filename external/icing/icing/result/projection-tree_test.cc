// Copyright (C) 2019 Google LLC
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

#include "icing/result/projection-tree.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/proto/search.pb.h"
#include "icing/schema/schema-store.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::SizeIs;

TEST(ProjectionTreeTest, CreateEmptyFieldMasks) {
  ProjectionTree tree({});
  EXPECT_THAT(tree.root().name, IsEmpty());
  EXPECT_THAT(tree.root().children, IsEmpty());
}

TEST(ProjectionTreeTest, CreateTreeTopLevel) {
  SchemaStore::ExpandedTypePropertyMask type_field_mask{"", {"subject"}};

  ProjectionTree tree(type_field_mask);
  EXPECT_THAT(tree.root().name, IsEmpty());
  ASSERT_THAT(tree.root().children, SizeIs(1));
  EXPECT_THAT(tree.root().children.at(0).name, Eq("subject"));
  EXPECT_THAT(tree.root().children.at(0).children, IsEmpty());
}

TEST(ProjectionTreeTest, CreateTreeMultipleTopLevel) {
  SchemaStore::ExpandedTypePropertyMask type_field_mask{"",
                                                        {"subject", "body"}};

  ProjectionTree tree(type_field_mask);
  EXPECT_THAT(tree.root().name, IsEmpty());
  ASSERT_THAT(tree.root().children, SizeIs(2));

  const ProjectionTree::Node* child0 = &tree.root().children.at(0);
  const ProjectionTree::Node* child1 = &tree.root().children.at(1);
  if (child0->name != "subject") {
    std::swap(child0, child1);
  }

  EXPECT_THAT(child0->name, Eq("subject"));
  EXPECT_THAT(child0->children, IsEmpty());
  EXPECT_THAT(child1->name, Eq("body"));
  EXPECT_THAT(child1->children, IsEmpty());
}

TEST(ProjectionTreeTest, CreateTreeNested) {
  SchemaStore::ExpandedTypePropertyMask type_field_mask{
      "", {"subject.body", "body"}};

  ProjectionTree tree(type_field_mask);
  EXPECT_THAT(tree.root().name, IsEmpty());
  ASSERT_THAT(tree.root().children, SizeIs(2));

  const ProjectionTree::Node* child0 = &tree.root().children.at(0);
  const ProjectionTree::Node* child1 = &tree.root().children.at(1);
  if (child0->name != "subject.body") {
    std::swap(child0, child1);
  }

  EXPECT_THAT(child0->name, Eq("subject"));
  ASSERT_THAT(child0->children, SizeIs(1));
  EXPECT_THAT(child0->children.at(0).name, Eq("body"));
  EXPECT_THAT(child0->children.at(0).children, IsEmpty());
  EXPECT_THAT(child1->name, Eq("body"));
  EXPECT_THAT(child1->children, IsEmpty());
}

TEST(ProjectionTreeTest, CreateTreeNestedSharedNode) {
  SchemaStore::ExpandedTypePropertyMask type_field_mask{
      "", {"sender.name.first", "sender.emailAddress"}};

  ProjectionTree tree(type_field_mask);
  EXPECT_THAT(tree.root().name, IsEmpty());
  ASSERT_THAT(tree.root().children, SizeIs(1));
  EXPECT_THAT(tree.root().children.at(0).name, Eq("sender"));
  ASSERT_THAT(tree.root().children.at(0).children, SizeIs(2));

  const ProjectionTree::Node* child0_child0 =
      &tree.root().children.at(0).children.at(0);
  const ProjectionTree::Node* child0_child1 =
      &tree.root().children.at(0).children.at(1);
  if (child0_child0->name != "name") {
    std::swap(child0_child0, child0_child1);
  }

  EXPECT_THAT(child0_child0->name, Eq("name"));
  ASSERT_THAT(child0_child0->children, SizeIs(1));
  EXPECT_THAT(child0_child0->children.at(0).name, Eq("first"));
  EXPECT_THAT(child0_child0->children.at(0).children, IsEmpty());
  EXPECT_THAT(child0_child1->name, Eq("emailAddress"));
  EXPECT_THAT(child0_child1->children, IsEmpty());
}

}  // namespace

}  // namespace lib
}  // namespace icing
