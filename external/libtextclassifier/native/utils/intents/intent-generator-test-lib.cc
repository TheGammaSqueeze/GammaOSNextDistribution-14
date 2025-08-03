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

#include <jni.h>

#include <memory>
#include <vector>

#include "utils/flatbuffers/mutable.h"
#include "utils/intents/intent-generator.h"
#include "utils/intents/remote-action-template.h"
#include "utils/java/jni-helper.h"
#include "utils/jvm-test-utils.h"
#include "utils/resources_generated.h"
#include "utils/testing/logging_event_listener.h"
#include "utils/variant.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "flatbuffers/reflection.h"

namespace libtextclassifier3 {
namespace {

using ::testing::ElementsAre;
using ::testing::IsEmpty;
using ::testing::SizeIs;

flatbuffers::DetachedBuffer BuildTestIntentFactoryModel(
    const std::string& entity_type, const std::string& generator_code) {
  // Test intent generation options.
  IntentFactoryModelT options;
  options.generator.emplace_back(new IntentFactoryModel_::IntentGeneratorT());
  options.generator.back()->type = entity_type;
  options.generator.back()->lua_template_generator = std::vector<unsigned char>(
      generator_code.data(), generator_code.data() + generator_code.size());
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(IntentFactoryModel::Pack(builder, &options));
  return builder.Release();
}

flatbuffers::DetachedBuffer BuildTestResources() {
  // Custom string resources.
  ResourcePoolT test_resources;
  test_resources.locale.emplace_back(new LanguageTagT);
  test_resources.locale.back()->language = "en";
  test_resources.locale.emplace_back(new LanguageTagT);
  test_resources.locale.back()->language = "de";

  // Add `add_calendar_event`
  test_resources.resource_entry.emplace_back(new ResourceEntryT);
  test_resources.resource_entry.back()->name = "add_calendar_event";

  // en
  test_resources.resource_entry.back()->resource.emplace_back(new ResourceT);
  test_resources.resource_entry.back()->resource.back()->content = "Schedule";
  test_resources.resource_entry.back()->resource.back()->locale.push_back(0);

  // Add `add_calendar_event_desc`
  test_resources.resource_entry.emplace_back(new ResourceEntryT);
  test_resources.resource_entry.back()->name = "add_calendar_event_desc";

  // en
  test_resources.resource_entry.back()->resource.emplace_back(new ResourceT);
  test_resources.resource_entry.back()->resource.back()->content =
      "Schedule event for selected time";
  test_resources.resource_entry.back()->resource.back()->locale.push_back(0);

  // Add `map`.
  test_resources.resource_entry.emplace_back(new ResourceEntryT);
  test_resources.resource_entry.back()->name = "map";

  // en
  test_resources.resource_entry.back()->resource.emplace_back(new ResourceT);
  test_resources.resource_entry.back()->resource.back()->content = "Map";
  test_resources.resource_entry.back()->resource.back()->locale.push_back(0);

  // de
  test_resources.resource_entry.back()->resource.emplace_back(new ResourceT);
  test_resources.resource_entry.back()->resource.back()->content = "Karte";
  test_resources.resource_entry.back()->resource.back()->locale.push_back(1);

  // Add `map_desc`.
  test_resources.resource_entry.emplace_back(new ResourceEntryT);
  test_resources.resource_entry.back()->name = "map_desc";

  // en
  test_resources.resource_entry.back()->resource.emplace_back(new ResourceT);
  test_resources.resource_entry.back()->resource.back()->content =
      "Locate selected address";
  test_resources.resource_entry.back()->resource.back()->locale.push_back(0);

  // de
  test_resources.resource_entry.back()->resource.emplace_back(new ResourceT);
  test_resources.resource_entry.back()->resource.back()->content =
      "Ausgewählte Adresse finden";
  test_resources.resource_entry.back()->resource.back()->locale.push_back(1);

  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(ResourcePool::Pack(builder, &test_resources));
  return builder.Release();
}

// Common methods for intent generator tests.
class IntentGeneratorTest : public testing::Test {
 protected:
  explicit IntentGeneratorTest()
      : jni_cache_(JniCache::Create(GetJenv())),
        resource_buffer_(BuildTestResources()),
        resources_(
            flatbuffers::GetRoot<ResourcePool>(resource_buffer_.data())) {}

  const std::shared_ptr<JniCache> jni_cache_;
  const flatbuffers::DetachedBuffer resource_buffer_;
  const ResourcePool* resources_;
};

TEST_F(IntentGeneratorTest, HandlesDefaultClassification) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("unused", "");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      /*options=*/flatbuffers::GetRoot<IntentFactoryModel>(
          intent_factory_model.data()),
      /*resources=*/resources_,
      /*jni_cache=*/jni_cache_);
  ClassificationResult classification;
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      /*device_locales=*/nullptr, classification, /*reference_time_ms_utc=*/0,
      /*text=*/"", /*selection_indices=*/{kInvalidIndex, kInvalidIndex},
      /*context=*/nullptr,
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, IsEmpty());
}

TEST_F(IntentGeneratorTest, FailsGracefully) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("test", R"lua(
return {
  {
    -- Should fail, as no app GetAndroidContext() is provided.
    data = external.android.package_name,
  }
})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  ClassificationResult classification = {"test", 1.0};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_FALSE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "test", {0, 4}, /*context=*/nullptr,
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, IsEmpty());
}

TEST_F(IntentGeneratorTest, HandlesEntityIntentGeneration) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("address", R"lua(
return {
  {
    title_without_entity = external.android.R.map,
    title_with_entity = external.entity.text,
    description = external.android.R.map_desc,
    action = "android.intent.action.VIEW",
    data = "geo:0,0?q=" ..
    external.android.urlencode(external.entity.text),
  }
})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  ClassificationResult classification = {"address", 1.0};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "333 E Wonderview Ave", {0, 20},
      GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].title_without_entity.value(), "Map");
  EXPECT_EQ(intents[0].title_with_entity.value(), "333 E Wonderview Ave");
  EXPECT_EQ(intents[0].description.value(), "Locate selected address");
  EXPECT_EQ(intents[0].action.value(), "android.intent.action.VIEW");
  EXPECT_EQ(intents[0].data.value(), "geo:0,0?q=333%20E%20Wonderview%20Ave");
}

TEST_F(IntentGeneratorTest, HandlesAddContactIntentEnabledGeneration) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("address", R"lua(
if external.enable_add_contact_intent then return {
  {
    title_without_entity = external.android.R.map,
    title_with_entity = external.entity.text,
    description = external.android.R.map_desc,
    action = "android.intent.action.VIEW",
    data = "geo:0,0?q=" ..
    external.android.urlencode(external.entity.text),
  }
} else return {} end)lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  ClassificationResult classification = {"address", 1.0};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "333 E Wonderview Ave", {0, 20},
      GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/true, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].title_without_entity.value(), "Map");
}

TEST_F(IntentGeneratorTest, HandlesAddContactIntentDisabledGeneration) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("address", R"lua(
if external.enable_add_contact_intent then return {
  {
    title_without_entity = external.android.R.map,
    title_with_entity = external.entity.text,
    description = external.android.R.map_desc,
    action = "android.intent.action.VIEW",
    data = "geo:0,0?q=" ..
    external.android.urlencode(external.entity.text),
  }
} else return {} end)lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  ClassificationResult classification = {"address", 1.0};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "333 E Wonderview Ave", {0, 20},
      GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, SizeIs(0));
}

TEST_F(IntentGeneratorTest, HandlesAddSearchIntentEnabledGeneration) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("address", R"lua(
if external.enable_search_intent then return {
  {
    title_without_entity = external.android.R.map,
    title_with_entity = external.entity.text,
    description = external.android.R.map_desc,
    action = "android.intent.action.VIEW",
    data = "geo:0,0?q=" ..
    external.android.urlencode(external.entity.text),
  }
} else return {} end)lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  ClassificationResult classification = {"address", 1.0};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "333 E Wonderview Ave", {0, 20},
      GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/true,
      &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].title_without_entity.value(), "Map");
}

TEST_F(IntentGeneratorTest, HandlesSearchIntentDisabledGeneration) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("address", R"lua(
if external.enable_search_intent then return {
  {
    title_without_entity = external.android.R.map,
    title_with_entity = external.entity.text,
    description = external.android.R.map_desc,
    action = "android.intent.action.VIEW",
    data = "geo:0,0?q=" ..
    external.android.urlencode(external.entity.text),
  }
} else return {} end)lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  ClassificationResult classification = {"address", 1.0};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "333 E Wonderview Ave", {0, 20},
      GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, SizeIs(0));
}

TEST_F(IntentGeneratorTest, HandlesCallbacks) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("test", R"lua(
local test = external.entity["text"]
return {
  {
    data = "encoded=" .. external.android.urlencode(test),
    category = { "test_category" },
    extra = {
      { name = "package", string_value = external.android.package_name},
      { name = "scheme",
        string_value = external.android.url_schema("https://google.com")},
      { name = "host",
        string_value = external.android.url_host("https://google.com/search")},
      { name = "permission",
        bool_value = external.android.user_restrictions["no_sms"] },
      { name = "language",
        string_value = external.android.device_locales[1].language },
      { name = "description",
        string_value = external.format("$1 $0", "hello", "world") },
    },
    request_code = external.hash(test)
  }
})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  ClassificationResult classification = {"test", 1.0};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "this is a test", {0, 14},
      GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].data.value(), "encoded=this%20is%20a%20test");
  EXPECT_THAT(intents[0].category, ElementsAre("test_category"));
  EXPECT_THAT(intents[0].extra, SizeIs(6));
  EXPECT_EQ(intents[0].extra["package"].ConstRefValue<std::string>(),
            "com.google.android.textclassifier.tests"
  );
  EXPECT_EQ(intents[0].extra["scheme"].ConstRefValue<std::string>(), "https");
  EXPECT_EQ(intents[0].extra["host"].ConstRefValue<std::string>(),
            "google.com");
  EXPECT_FALSE(intents[0].extra["permission"].Value<bool>());
  EXPECT_EQ(intents[0].extra["language"].ConstRefValue<std::string>(), "en");
  EXPECT_TRUE(intents[0].request_code.has_value());
  EXPECT_EQ(intents[0].extra["description"].ConstRefValue<std::string>(),
            "world hello");
}

TEST_F(IntentGeneratorTest, HandlesActionIntentGeneration) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("view_map", R"lua(
return {
  {
    title_without_entity = external.android.R.map,
    description = external.android.R.map_desc,
    description_with_app_name = external.android.R.map,
    action = "android.intent.action.VIEW",
    data = "geo:0,0?q=" ..
    external.android.urlencode(external.entity.annotation["location"].text),
  }
})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  Conversation conversation = {{{/*user_id=*/1, "hello there"}}};
  ActionSuggestionAnnotation annotation;
  annotation.entity = {"address", 1.0};
  annotation.span = {/*message_index=*/0,
                     /*span=*/{6, 11},
                     /*text=*/"there"};
  annotation.name = "location";
  ActionSuggestion suggestion = {/*response_text=""*/ "",
                                 /*type=*/"view_map",
                                 /*score=*/1.0,
                                 /*priority_score=*/0.0,
                                 /*annotations=*/
                                 {annotation}};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      suggestion, conversation, GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*actions_entity_data_schema=*/nullptr, &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].title_without_entity.value(), "Map");
  EXPECT_EQ(intents[0].description.value(), "Locate selected address");
  EXPECT_EQ(intents[0].description_with_app_name.value(), "Map");
  EXPECT_EQ(intents[0].action.value(), "android.intent.action.VIEW");
  EXPECT_EQ(intents[0].data.value(), "geo:0,0?q=there");
}

TEST_F(IntentGeneratorTest, HandlesTimezoneAndReferenceTime) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("test", R"lua(
local conversation = external.conversation
return {
  {
    extra = {
      { name = "timezone", string_value = conversation[#conversation].timezone },
      { name = "num_messages", int_value = #conversation },
      { name = "reference_time", long_value = conversation[#conversation].time_ms_utc }
    },
  }
})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  Conversation conversation = {
      {{/*user_id=*/0, "hello there", /*reference_time_ms_utc=*/0,
        /*reference_timezone=*/"Testing/Test"},
       {/*user_id=*/1, "general retesti", /*reference_time_ms_utc=*/1000,
        /*reference_timezone=*/"Europe/Zurich"}}};
  ActionSuggestion suggestion = {/*response_text=""*/ "",
                                 /*type=*/"test",
                                 /*score=*/1.0,
                                 /*priority_score=*/0.0,
                                 /*annotations=*/
                                 {}};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      suggestion, conversation, GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*actions_entity_data_schema=*/nullptr, &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].extra["timezone"].ConstRefValue<std::string>(),
            "Europe/Zurich");
  EXPECT_EQ(intents[0].extra["num_messages"].Value<int>(), 2);
  EXPECT_EQ(intents[0].extra["reference_time"].Value<int64>(), 1000);
}

TEST_F(IntentGeneratorTest, HandlesActionIntentGenerationMultipleAnnotations) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("create_event", R"lua(
return {
  {
    title_without_entity = external.android.R.add_calendar_event,
    description = external.android.R.add_calendar_event_desc,
    extra = {
      {name = "time", string_value =
       external.entity.annotation["time"].text},
      {name = "location",
       string_value = external.entity.annotation["location"].text},
    }
  }
})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  Conversation conversation = {{{/*user_id=*/1, "hello there at 1pm"}}};
  ActionSuggestionAnnotation location_annotation, time_annotation;
  location_annotation.entity = {"address", 1.0};
  location_annotation.span = {/*message_index=*/0,
                              /*span=*/{6, 11},
                              /*text=*/"there"};
  location_annotation.name = "location";
  time_annotation.entity = {"datetime", 1.0};
  time_annotation.span = {/*message_index=*/0,
                          /*span=*/{15, 18},
                          /*text=*/"1pm"};
  time_annotation.name = "time";
  ActionSuggestion suggestion = {/*response_text=""*/ "",
                                 /*type=*/"create_event",
                                 /*score=*/1.0,
                                 /*priority_score=*/0.0,
                                 /*annotations=*/
                                 {location_annotation, time_annotation}};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      suggestion, conversation, GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*actions_entity_data_schema=*/nullptr, &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].title_without_entity.value(), "Schedule");
  EXPECT_THAT(intents[0].extra, SizeIs(2));
  EXPECT_EQ(intents[0].extra["location"].ConstRefValue<std::string>(), "there");
  EXPECT_EQ(intents[0].extra["time"].ConstRefValue<std::string>(), "1pm");
}

TEST_F(IntentGeneratorTest,
       HandlesActionIntentGenerationMultipleAnnotationsWithIndices) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("time_range", R"lua(
return {
  {
    title_without_entity = "test",
    description = "test",
    extra = {
      {name = "from", string_value = external.entity.annotation[1].text},
      {name = "to", string_value = external.entity.annotation[2].text},
    }
  }
})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  Conversation conversation = {{{/*user_id=*/1, "from 1pm to 2pm"}}};
  ActionSuggestionAnnotation from_annotation, to_annotation;
  from_annotation.entity = {"datetime", 1.0};
  from_annotation.span = {/*message_index=*/0,
                          /*span=*/{5, 8},
                          /*text=*/"1pm"};
  to_annotation.entity = {"datetime", 1.0};
  to_annotation.span = {/*message_index=*/0,
                        /*span=*/{12, 15},
                        /*text=*/"2pm"};
  ActionSuggestion suggestion = {/*response_text=""*/ "",
                                 /*type=*/"time_range",
                                 /*score=*/1.0,
                                 /*priority_score=*/0.0,
                                 /*annotations=*/
                                 {from_annotation, to_annotation}};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      suggestion, conversation, GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*actions_entity_data_schema=*/nullptr, &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_THAT(intents[0].extra, SizeIs(2));
  EXPECT_EQ(intents[0].extra["from"].ConstRefValue<std::string>(), "1pm");
  EXPECT_EQ(intents[0].extra["to"].ConstRefValue<std::string>(), "2pm");
}

TEST_F(IntentGeneratorTest, HandlesResources) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("address", R"lua(
return {
  {
    title_without_entity = external.android.R.map,
    description = external.android.R.map_desc,
    action = "android.intent.action.VIEW",
    data = "geo:0,0?q=" ..
    external.android.urlencode(external.entity.text),
  }
})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      resources_, jni_cache_);
  ClassificationResult classification = {"address", 1.0};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "de-DE").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "333 E Wonderview Ave", {0, 20},
      GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].title_without_entity.value(), "Karte");
  EXPECT_EQ(intents[0].description.value(), "Ausgewählte Adresse finden");
  EXPECT_EQ(intents[0].action.value(), "android.intent.action.VIEW");
  EXPECT_EQ(intents[0].data.value(), "geo:0,0?q=333%20E%20Wonderview%20Ave");
}

TEST_F(IntentGeneratorTest, HandlesIteration) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("iteration_test", R"lua(
local extra = {{ name = "length", int_value = #external.entity.annotation }}
for annotation_id, annotation in pairs(external.entity.annotation) do
  table.insert(extra,
    { name = annotation.name,
      string_value = annotation.text })
end
return {{ extra = extra }})lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  Conversation conversation = {{{/*user_id=*/1, "hello there"}}};
  ActionSuggestionAnnotation location_annotation;
  location_annotation.entity = {"address", 1.0};
  location_annotation.span = {/*message_index=*/0,
                              /*span=*/{6, 11},
                              /*text=*/"there"};
  location_annotation.name = "location";
  ActionSuggestionAnnotation greeting_annotation;
  greeting_annotation.entity = {"greeting", 1.0};
  greeting_annotation.span = {/*message_index=*/0,
                              /*span=*/{0, 5},
                              /*text=*/"hello"};
  greeting_annotation.name = "greeting";
  ActionSuggestion suggestion = {/*response_text=""*/ "",
                                 /*type=*/"iteration_test",
                                 /*score=*/1.0,
                                 /*priority_score=*/0.0,
                                 /*annotations=*/
                                 {location_annotation, greeting_annotation}};
  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      suggestion, conversation, GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*actions_entity_data_schema=*/nullptr, &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_EQ(intents[0].extra["length"].Value<int>(), 2);
  EXPECT_EQ(intents[0].extra["location"].ConstRefValue<std::string>(), "there");
  EXPECT_EQ(intents[0].extra["greeting"].ConstRefValue<std::string>(), "hello");
}

TEST_F(IntentGeneratorTest, HandlesEntityDataLookups) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("fake", R"lua(
local person = external.entity.person
return {
  {
    title_without_entity = "Add to contacts",
    extra = {
      {name = "name", string_value = string.lower(person.name)},
      {name = "encoded_phone", string_value = external.android.urlencode(person.phone)},
      {name = "age", int_value = person.age_years},
    }
  }
})lua");

  // Create fake entity data schema meta data.
  // Cannot use object oriented API here as that is not available for the
  // reflection schema.
  flatbuffers::FlatBufferBuilder schema_builder;
  std::vector<flatbuffers::Offset<reflection::Field>> person_fields = {
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("name"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::String),
          /*id=*/0,
          /*offset=*/4),
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("phone"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::String),
          /*id=*/1,
          /*offset=*/6),
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("age_years"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::Int),
          /*id=*/2,
          /*offset=*/8),
  };
  std::vector<flatbuffers::Offset<reflection::Field>> entity_data_fields = {
      reflection::CreateField(
          schema_builder,
          /*name=*/schema_builder.CreateString("person"),
          /*type=*/
          reflection::CreateType(schema_builder,
                                 /*base_type=*/reflection::Obj,
                                 /*element=*/reflection::None,
                                 /*index=*/1),
          /*id=*/0,
          /*offset=*/4)};
  std::vector<flatbuffers::Offset<reflection::Enum>> enums;
  std::vector<flatbuffers::Offset<reflection::Object>> objects = {
      reflection::CreateObject(
          schema_builder,
          /*name=*/schema_builder.CreateString("EntityData"),
          /*fields=*/
          schema_builder.CreateVectorOfSortedTables(&entity_data_fields)),
      reflection::CreateObject(
          schema_builder,
          /*name=*/schema_builder.CreateString("person"),
          /*fields=*/
          schema_builder.CreateVectorOfSortedTables(&person_fields))};
  schema_builder.Finish(reflection::CreateSchema(
      schema_builder, schema_builder.CreateVectorOfSortedTables(&objects),
      schema_builder.CreateVectorOfSortedTables(&enums),
      /*(unused) file_ident=*/0,
      /*(unused) file_ext=*/0,
      /*root_table*/ objects[0]));
  const reflection::Schema* entity_data_schema =
      flatbuffers::GetRoot<reflection::Schema>(
          schema_builder.GetBufferPointer());

  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);

  ClassificationResult classification = {"fake", 1.0};

  // Build test entity data.
  MutableFlatbufferBuilder entity_data_builder(entity_data_schema);
  std::unique_ptr<MutableFlatbuffer> entity_data_buffer =
      entity_data_builder.NewRoot();
  MutableFlatbuffer* person = entity_data_buffer->Mutable("person");
  person->Set("name", "Kenobi");
  person->Set("phone", "1 800 HIGHGROUND");
  person->Set("age_years", 38);
  classification.serialized_entity_data = entity_data_buffer->Serialize();

  std::vector<RemoteActionTemplate> intents;
  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "highground", {0, 10}, GetAndroidContext(),
      /*annotations_entity_data_schema=*/entity_data_schema,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));
  EXPECT_THAT(intents, SizeIs(1));
  EXPECT_THAT(intents[0].extra, SizeIs(3));
  EXPECT_EQ(intents[0].extra["name"].ConstRefValue<std::string>(), "kenobi");
  EXPECT_EQ(intents[0].extra["encoded_phone"].ConstRefValue<std::string>(),
            "1%20800%20HIGHGROUND");
  EXPECT_EQ(intents[0].extra["age"].Value<int>(), 38);
}

TEST_F(IntentGeneratorTest, ReadExtras) {
  flatbuffers::DetachedBuffer intent_factory_model =
      BuildTestIntentFactoryModel("test", R"lua(
        return {
          {
            extra = {
              { name = "languages", string_array_value = {"en", "zh"}},
              { name = "scores", float_array_value = {0.6, 0.4}},
              { name = "ints", int_array_value = {7, 2, 1}},
              { name = "bundle",
              named_variant_array_value =
              {
                { name = "inner_string", string_value = "a" },
                { name = "inner_int", int_value = 42 }
              }
            }
          }
        }}
  )lua");
  std::unique_ptr<IntentGenerator> generator = IntentGenerator::Create(
      flatbuffers::GetRoot<IntentFactoryModel>(intent_factory_model.data()),
      /*resources=*/resources_, jni_cache_);
  const ClassificationResult classification = {"test", 1.0};
  std::vector<RemoteActionTemplate> intents;

  EXPECT_TRUE(generator->GenerateIntents(
      JniHelper::NewStringUTF(GetJenv(), "en-US").ValueOrDie().get(),
      classification,
      /*reference_time_ms_utc=*/0, "test", {0, 4}, GetAndroidContext(),
      /*annotations_entity_data_schema=*/nullptr,
      /*enable_add_contact_intent=*/false, /*enable_search_intent=*/false,
      &intents));

  EXPECT_THAT(intents, SizeIs(1));
  RemoteActionTemplate intent = intents[0];
  EXPECT_THAT(intent.extra, SizeIs(4));
  EXPECT_THAT(
      intent.extra["languages"].ConstRefValue<std::vector<std::string>>(),
      ElementsAre("en", "zh"));
  EXPECT_THAT(intent.extra["scores"].ConstRefValue<std::vector<float>>(),
              ElementsAre(0.6, 0.4));
  EXPECT_THAT(intent.extra["ints"].ConstRefValue<std::vector<int>>(),
              ElementsAre(7, 2, 1));
  const std::map<std::string, Variant>& map =
      intent.extra["bundle"].ConstRefValue<std::map<std::string, Variant>>();
  EXPECT_THAT(map, SizeIs(2));
  EXPECT_EQ(map.at("inner_string").ConstRefValue<std::string>(), "a");
  EXPECT_EQ(map.at("inner_int").Value<int>(), 42);
}

}  // namespace
}  // namespace libtextclassifier3
