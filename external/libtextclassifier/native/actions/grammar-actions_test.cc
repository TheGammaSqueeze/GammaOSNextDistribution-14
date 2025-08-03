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

#include "actions/grammar-actions.h"

#include <iostream>
#include <memory>

#include "actions/actions_model_generated.h"
#include "actions/test-utils.h"
#include "actions/types.h"
#include "utils/flatbuffers/flatbuffers.h"
#include "utils/flatbuffers/mutable.h"
#include "utils/grammar/rules_generated.h"
#include "utils/grammar/types.h"
#include "utils/grammar/utils/rules.h"
#include "utils/jvm-test-utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

using ::testing::ElementsAre;
using ::testing::IsEmpty;

using ::libtextclassifier3::grammar::LocaleShardMap;

class TestGrammarActions : public GrammarActions {
 public:
  explicit TestGrammarActions(
      const UniLib* unilib, const RulesModel_::GrammarRules* grammar_rules,
      const MutableFlatbufferBuilder* entity_data_builder = nullptr)
      : GrammarActions(unilib, grammar_rules, entity_data_builder,

                       /*smart_reply_action_type=*/"text_reply") {}
};

class GrammarActionsTest : public testing::Test {
 protected:
  struct AnnotationSpec {
    int group_id = 0;
    std::string annotation_name = "";
    bool use_annotation_match = false;
  };

  GrammarActionsTest()
      : unilib_(CreateUniLibForTesting()),
        serialized_entity_data_schema_(TestEntityDataSchema()),
        entity_data_builder_(new MutableFlatbufferBuilder(
            flatbuffers::GetRoot<reflection::Schema>(
                serialized_entity_data_schema_.data()))) {}

  void SetTokenizerOptions(
      RulesModel_::GrammarRulesT* action_grammar_rules) const {
    action_grammar_rules->tokenizer_options.reset(new ActionsTokenizerOptionsT);
    action_grammar_rules->tokenizer_options->type = TokenizationType_ICU;
    action_grammar_rules->tokenizer_options->icu_preserve_whitespace_tokens =
        false;
  }

  int AddActionSpec(const std::string& type, const std::string& response_text,
                    const std::vector<AnnotationSpec>& annotations,
                    RulesModel_::GrammarRulesT* action_grammar_rules) const {
    const int action_id = action_grammar_rules->actions.size();
    action_grammar_rules->actions.emplace_back(
        new RulesModel_::RuleActionSpecT);
    RulesModel_::RuleActionSpecT* actions_spec =
        action_grammar_rules->actions.back().get();
    actions_spec->action.reset(new ActionSuggestionSpecT);
    actions_spec->action->response_text = response_text;
    actions_spec->action->priority_score = 1.0;
    actions_spec->action->score = 1.0;
    actions_spec->action->type = type;
    // Create annotations for specified capturing groups.
    for (const AnnotationSpec& annotation : annotations) {
      actions_spec->capturing_group.emplace_back(
          new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
      actions_spec->capturing_group.back()->group_id = annotation.group_id;
      actions_spec->capturing_group.back()->annotation_name =
          annotation.annotation_name;
      actions_spec->capturing_group.back()->annotation_type =
          annotation.annotation_name;
      actions_spec->capturing_group.back()->use_annotation_match =
          annotation.use_annotation_match;
    }

    return action_id;
  }

  int AddSmartReplySpec(
      const std::string& response_text,
      RulesModel_::GrammarRulesT* action_grammar_rules) const {
    return AddActionSpec("text_reply", response_text, {}, action_grammar_rules);
  }

  int AddCapturingMatchSmartReplySpec(
      const int match_id,
      RulesModel_::GrammarRulesT* action_grammar_rules) const {
    const int action_id = action_grammar_rules->actions.size();
    action_grammar_rules->actions.emplace_back(
        new RulesModel_::RuleActionSpecT);
    RulesModel_::RuleActionSpecT* actions_spec =
        action_grammar_rules->actions.back().get();
    actions_spec->capturing_group.emplace_back(
        new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
    actions_spec->capturing_group.back()->group_id = match_id;
    actions_spec->capturing_group.back()->text_reply.reset(
        new ActionSuggestionSpecT);
    actions_spec->capturing_group.back()->text_reply->priority_score = 1.0;
    actions_spec->capturing_group.back()->text_reply->score = 1.0;
    return action_id;
  }

  int AddRuleMatch(const std::vector<int>& action_ids,
                   RulesModel_::GrammarRulesT* action_grammar_rules) const {
    const int rule_match_id = action_grammar_rules->rule_match.size();
    action_grammar_rules->rule_match.emplace_back(
        new RulesModel_::GrammarRules_::RuleMatchT);
    action_grammar_rules->rule_match.back()->action_id.insert(
        action_grammar_rules->rule_match.back()->action_id.end(),
        action_ids.begin(), action_ids.end());
    return rule_match_id;
  }

  std::unique_ptr<UniLib> unilib_;
  const std::string serialized_entity_data_schema_;
  std::unique_ptr<MutableFlatbufferBuilder> entity_data_builder_;
};

TEST_F(GrammarActionsTest, ProducesSmartReplies) {
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);

  // Create test rules.
  // Rule: ^knock knock.?$ -> "Who's there?", "Yes?"
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  rules.Add(
      "<knock>", {"<^>", "knock", "knock", ".?", "<$>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({AddSmartReplySpec("Who's there?", &action_grammar_rules),
                    AddSmartReplySpec("Yes?", &action_grammar_rules)},
                   &action_grammar_rules));
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());
  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get());

  std::vector<ActionSuggestion> result;
  EXPECT_TRUE(grammar_actions.SuggestActions(
      {/*messages=*/{{/*user_id=*/0, /*text=*/"Knock knock"}}}, &result));

  EXPECT_THAT(result,
              ElementsAre(IsSmartReply("Who's there?"), IsSmartReply("Yes?")));
}

TEST_F(GrammarActionsTest, ProducesSmartRepliesFromCapturingMatches) {
  // Create test rules.
  // Rule: ^Text <reply> to <command>
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);

  rules.Add(
      "<scripted_reply>",
      {"<^>", "text", "<captured_reply>", "to", "<command>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({AddCapturingMatchSmartReplySpec(
                       /*match_id=*/0, &action_grammar_rules)},
                   &action_grammar_rules));

  // <command> ::= unsubscribe | cancel | confirm | receive
  rules.Add("<command>", {"unsubscribe"});
  rules.Add("<command>", {"cancel"});
  rules.Add("<command>", {"confirm"});
  rules.Add("<command>", {"receive"});

  // <reply> ::= help | stop | cancel | yes
  rules.Add("<reply>", {"help"});
  rules.Add("<reply>", {"stop"});
  rules.Add("<reply>", {"cancel"});
  rules.Add("<reply>", {"yes"});
  rules.AddValueMapping("<captured_reply>", {"<reply>"},
                        /*value=*/0);

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());
  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get());

  {
    std::vector<ActionSuggestion> result;
    EXPECT_TRUE(grammar_actions.SuggestActions(
        {/*messages=*/{{/*user_id=*/0,
                        /*text=*/"Text YES to confirm your subscription"}}},
        &result));
    EXPECT_THAT(result, ElementsAre(IsSmartReply("YES")));
  }

  {
    std::vector<ActionSuggestion> result;
    EXPECT_TRUE(grammar_actions.SuggestActions(
        {/*messages=*/{{/*user_id=*/0,
                        /*text=*/"text Stop to cancel your order"}}},
        &result));
    EXPECT_THAT(result, ElementsAre(IsSmartReply("Stop")));
  }
}

TEST_F(GrammarActionsTest, ProducesAnnotationsForActions) {
  // Create test rules.
  // Rule: please dial <phone>
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);

  rules.Add(
      "<call_phone>", {"please", "dial", "<phone>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({AddActionSpec("call_phone", /*response_text=*/"",
                                  /*annotations=*/{{0 /*value*/, "phone"}},
                                  &action_grammar_rules)},
                   &action_grammar_rules));
  // phone ::= +00 00 000 00 00
  rules.AddValueMapping("<phone>",
                        {"+", "<2_digits>", "<2_digits>", "<3_digits>",
                         "<2_digits>", "<2_digits>"},
                        /*value=*/0);
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());
  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get());

  std::vector<ActionSuggestion> result;
  EXPECT_TRUE(grammar_actions.SuggestActions(
      {/*messages=*/{{/*user_id=*/0, /*text=*/"Please dial +41 79 123 45 67"}}},
      &result));

  EXPECT_THAT(result, ElementsAre(IsActionOfType("call_phone")));
  EXPECT_THAT(result.front().annotations,
              ElementsAre(IsActionSuggestionAnnotation(
                  "phone", "+41 79 123 45 67", CodepointSpan{12, 28})));
}

TEST_F(GrammarActionsTest, HandlesLocales) {
  // Create test rules.
  // Rule: ^knock knock.?$ -> "Who's there?"
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  LocaleShardMap locale_shard_map =
      LocaleShardMap::CreateLocaleShardMap({"", "fr-CH"});
  grammar::Rules rules(locale_shard_map);
  rules.Add(
      "<knock>", {"<^>", "knock", "knock", ".?", "<$>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({AddSmartReplySpec("Who's there?", &action_grammar_rules)},
                   &action_grammar_rules));
  rules.Add(
      "<toc>", {"<knock>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({AddSmartReplySpec("Qui est là?", &action_grammar_rules)},
                   &action_grammar_rules),
      /*max_whitespace_gap=*/-1,
      /*case_sensitive=*/false,
      /*shard=*/1);
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());
  // Set locales for rules.
  action_grammar_rules.rules->rules.back()->locale.emplace_back(
      new LanguageTagT);
  action_grammar_rules.rules->rules.back()->locale.back()->language = "fr";

  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get());

  // Check default.
  {
    std::vector<ActionSuggestion> result;
    EXPECT_TRUE(grammar_actions.SuggestActions(
        {/*messages=*/{{/*user_id=*/0, /*text=*/"knock knock",
                        /*reference_time_ms_utc=*/0,
                        /*reference_timezone=*/"UTC", /*annotations=*/{},
                        /*detected_text_language_tags=*/"en"}}},
        &result));

    EXPECT_THAT(result, ElementsAre(IsSmartReply("Who's there?")));
  }

  // Check fr.
  {
    std::vector<ActionSuggestion> result;
    EXPECT_TRUE(grammar_actions.SuggestActions(
        {/*messages=*/{{/*user_id=*/0, /*text=*/"knock knock",
                        /*reference_time_ms_utc=*/0,
                        /*reference_timezone=*/"UTC", /*annotations=*/{},
                        /*detected_text_language_tags=*/"fr-CH"}}},
        &result));

    EXPECT_THAT(result, ElementsAre(IsSmartReply("Who's there?"),
                                    IsSmartReply("Qui est là?")));
  }
}

TEST_F(GrammarActionsTest, HandlesAssertions) {
  // Create test rules.
  // Rule: <flight> -> Track flight.
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<carrier>", {"lx"});
  rules.Add("<carrier>", {"aa"});
  rules.Add("<flight_code>", {"<2_digits>"});
  rules.Add("<flight_code>", {"<3_digits>"});
  rules.Add("<flight_code>", {"<4_digits>"});

  // Capture flight code.
  rules.AddValueMapping("<flight>", {"<carrier>", "<flight_code>"},
                        /*value=*/0);

  // Flight: carrier + flight code and check right context.
  rules.Add(
      "<track_flight>", {"<flight>", "<context_assertion>?"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({AddActionSpec("track_flight", /*response_text=*/"",
                                  /*annotations=*/{{0 /*value*/, "flight"}},
                                  &action_grammar_rules)},
                   &action_grammar_rules));

  // Exclude matches like: LX 38.00 etc.
  rules.AddAssertion("<context_assertion>", {".?", "<digits>"},
                     /*negative=*/true);

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());

  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get());

  std::vector<ActionSuggestion> result;
  EXPECT_TRUE(grammar_actions.SuggestActions(
      {/*messages=*/{{/*user_id=*/0, /*text=*/"LX38 aa 44 LX 38.38"}}},
      &result));

  EXPECT_THAT(result, ElementsAre(IsActionOfType("track_flight"),
                                  IsActionOfType("track_flight")));
  EXPECT_THAT(result[0].annotations,
              ElementsAre(IsActionSuggestionAnnotation("flight", "LX38",
                                                       CodepointSpan{0, 4})));
  EXPECT_THAT(result[1].annotations,
              ElementsAre(IsActionSuggestionAnnotation("flight", "aa 44",
                                                       CodepointSpan{5, 10})));
}

TEST_F(GrammarActionsTest, SetsFixedEntityData) {
  // Create test rules.
  // Rule: ^hello there$
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);

  // Create smart reply and static entity data.
  const int spec_id =
      AddSmartReplySpec("General Kenobi!", &action_grammar_rules);
  std::unique_ptr<MutableFlatbuffer> entity_data =
      entity_data_builder_->NewRoot();
  entity_data->Set("person", "Kenobi");
  action_grammar_rules.actions[spec_id]->action->serialized_entity_data =
      entity_data->Serialize();
  action_grammar_rules.actions[spec_id]->action->entity_data.reset(
      new ActionsEntityDataT);
  action_grammar_rules.actions[spec_id]->action->entity_data->text =
      "I have the high ground.";

  rules.Add(
      "<greeting>", {"<^>", "hello", "there", "<$>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({spec_id}, &action_grammar_rules));
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());
  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get(),
                                     entity_data_builder_.get());

  std::vector<ActionSuggestion> result;
  EXPECT_TRUE(grammar_actions.SuggestActions(
      {/*messages=*/{{/*user_id=*/0, /*text=*/"Hello there"}}}, &result));

  // Check the produces smart replies.
  EXPECT_THAT(result, ElementsAre(IsSmartReply("General Kenobi!")));

  // Check entity data.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          result[0].serialized_entity_data.data()));
  EXPECT_THAT(
      entity->GetPointer<const flatbuffers::String*>(/*field=*/4)->str(),
      "I have the high ground.");
  EXPECT_THAT(
      entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
      "Kenobi");
}

TEST_F(GrammarActionsTest, SetsEntityDataFromCapturingMatches) {
  // Create test rules.
  // Rule: ^hello there$
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);

  // Create smart reply and static entity data.
  const int spec_id =
      AddSmartReplySpec("General Kenobi!", &action_grammar_rules);
  std::unique_ptr<MutableFlatbuffer> entity_data =
      entity_data_builder_->NewRoot();
  entity_data->Set("person", "Kenobi");
  action_grammar_rules.actions[spec_id]->action->serialized_entity_data =
      entity_data->Serialize();

  // Specify results for capturing matches.
  const int greeting_match_id = 0;
  const int location_match_id = 1;
  {
    action_grammar_rules.actions[spec_id]->capturing_group.emplace_back(
        new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
    RulesModel_::RuleActionSpec_::RuleCapturingGroupT* group =
        action_grammar_rules.actions[spec_id]->capturing_group.back().get();
    group->group_id = greeting_match_id;
    group->entity_field.reset(new FlatbufferFieldPathT);
    group->entity_field->field.emplace_back(new FlatbufferFieldT);
    group->entity_field->field.back()->field_name = "greeting";
  }
  {
    action_grammar_rules.actions[spec_id]->capturing_group.emplace_back(
        new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
    RulesModel_::RuleActionSpec_::RuleCapturingGroupT* group =
        action_grammar_rules.actions[spec_id]->capturing_group.back().get();
    group->group_id = location_match_id;
    group->entity_field.reset(new FlatbufferFieldPathT);
    group->entity_field->field.emplace_back(new FlatbufferFieldT);
    group->entity_field->field.back()->field_name = "location";
  }

  rules.Add("<location>", {"there"});
  rules.Add("<location>", {"here"});
  rules.AddValueMapping("<captured_location>", {"<location>"},
                        /*value=*/location_match_id);
  rules.AddValueMapping("<greeting>", {"hello", "<captured_location>"},
                        /*value=*/greeting_match_id);
  rules.Add(
      "<test>", {"<^>", "<greeting>", "<$>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({spec_id}, &action_grammar_rules));
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());
  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get(),
                                     entity_data_builder_.get());

  std::vector<ActionSuggestion> result;
  EXPECT_TRUE(grammar_actions.SuggestActions(
      {/*messages=*/{{/*user_id=*/0, /*text=*/"Hello there"}}}, &result));

  // Check the produces smart replies.
  EXPECT_THAT(result, ElementsAre(IsSmartReply("General Kenobi!")));

  // Check entity data.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          result[0].serialized_entity_data.data()));
  EXPECT_THAT(
      entity->GetPointer<const flatbuffers::String*>(/*field=*/4)->str(),
      "Hello there");
  EXPECT_THAT(
      entity->GetPointer<const flatbuffers::String*>(/*field=*/6)->str(),
      "there");
  EXPECT_THAT(
      entity->GetPointer<const flatbuffers::String*>(/*field=*/8)->str(),
      "Kenobi");
}

TEST_F(GrammarActionsTest, SetsFixedEntityDataFromCapturingGroups) {
  // Create test rules.
  // Rule: ^hello there$
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);

  // Create smart reply.
  const int spec_id =
      AddSmartReplySpec("General Kenobi!", &action_grammar_rules);
  action_grammar_rules.actions[spec_id]->capturing_group.emplace_back(
      new RulesModel_::RuleActionSpec_::RuleCapturingGroupT);
  RulesModel_::RuleActionSpec_::RuleCapturingGroupT* group =
      action_grammar_rules.actions[spec_id]->capturing_group.back().get();
  group->group_id = 0;
  group->entity_data.reset(new ActionsEntityDataT);
  group->entity_data->text = "You are a bold one.";

  rules.AddValueMapping("<greeting>", {"<^>", "hello", "there", "<$>"},
                        /*value=*/0);
  rules.Add(
      "<test>", {"<greeting>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({spec_id}, &action_grammar_rules));
  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());
  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get(),
                                     entity_data_builder_.get());

  std::vector<ActionSuggestion> result;
  EXPECT_TRUE(grammar_actions.SuggestActions(
      {/*messages=*/{{/*user_id=*/0, /*text=*/"Hello there"}}}, &result));

  // Check the produces smart replies.
  EXPECT_THAT(result, ElementsAre(IsSmartReply("General Kenobi!")));

  // Check entity data.
  const flatbuffers::Table* entity =
      flatbuffers::GetAnyRoot(reinterpret_cast<const unsigned char*>(
          result[0].serialized_entity_data.data()));
  EXPECT_THAT(
      entity->GetPointer<const flatbuffers::String*>(/*field=*/4)->str(),
      "You are a bold one.");
}

TEST_F(GrammarActionsTest, ProducesActionsWithAnnotations) {
  // Create test rules.
  // Rule: please dial <phone>
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add(
      "<call_phone>", {"please", "dial", "<phone>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({AddActionSpec("call_phone", /*response_text=*/"",
                                  /*annotations=*/
                                  {{0 /*value*/, "phone",
                                    /*use_annotation_match=*/true}},
                                  &action_grammar_rules)},
                   &action_grammar_rules));
  rules.AddValueMapping("<phone>", {"<phone_annotation>"},
                        /*value=*/0);

  grammar::Ir ir = rules.Finalize(
      /*predefined_nonterminals=*/{"<phone_annotation>"});
  ir.Serialize(/*include_debug_information=*/false,
               action_grammar_rules.rules.get());

  // Map "phone" annotation to "<phone_annotation>" nonterminal.
  action_grammar_rules.rules->nonterminals->annotation_nt.emplace_back(
      new grammar::RulesSet_::Nonterminals_::AnnotationNtEntryT);
  action_grammar_rules.rules->nonterminals->annotation_nt.back()->key = "phone";
  action_grammar_rules.rules->nonterminals->annotation_nt.back()->value =
      ir.GetNonterminalForName("<phone_annotation>");

  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get());

  std::vector<ActionSuggestion> result;

  // Sanity check that no result are produced when no annotations are provided.
  EXPECT_TRUE(grammar_actions.SuggestActions(
      {/*messages=*/{{/*user_id=*/0, /*text=*/"Please dial +41 79 123 45 67"}}},
      &result));
  EXPECT_THAT(result, IsEmpty());

  EXPECT_TRUE(grammar_actions.SuggestActions(
      {/*messages=*/{
          {/*user_id=*/0,
           /*text=*/"Please dial +41 79 123 45 67",
           /*reference_time_ms_utc=*/0,
           /*reference_timezone=*/"UTC",
           /*annotations=*/
           {{CodepointSpan{12, 28}, {ClassificationResult{"phone", 1.0}}}}}}},
      &result));
  EXPECT_THAT(result, ElementsAre(IsActionOfType("call_phone")));
  EXPECT_THAT(result.front().annotations,
              ElementsAre(IsActionSuggestionAnnotation(
                  "phone", "+41 79 123 45 67", CodepointSpan{12, 28})));
}

TEST_F(GrammarActionsTest, HandlesExclusions) {
  // Create test rules.
  RulesModel_::GrammarRulesT action_grammar_rules;
  SetTokenizerOptions(&action_grammar_rules);
  action_grammar_rules.rules.reset(new grammar::RulesSetT);

  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({""});
  grammar::Rules rules(locale_shard_map);
  rules.Add("<excluded>", {"be", "safe"});
  rules.AddWithExclusion("<tokens_but_not_excluded>", {"<token>", "<token>"},
                         /*excluded_nonterminal=*/"<excluded>");

  rules.Add(
      "<set_reminder>",
      {"do", "not", "forget", "to", "<tokens_but_not_excluded>"},
      /*callback=*/
      static_cast<grammar::CallbackId>(grammar::DefaultCallback::kRootRule),
      /*callback_param=*/
      AddRuleMatch({AddActionSpec("set_reminder", /*response_text=*/"",
                                  /*annotations=*/
                                  {}, &action_grammar_rules)},
                   &action_grammar_rules));

  rules.Finalize().Serialize(/*include_debug_information=*/false,
                             action_grammar_rules.rules.get());
  OwnedFlatbuffer<RulesModel_::GrammarRules, std::string> model(
      PackFlatbuffer<RulesModel_::GrammarRules>(&action_grammar_rules));
  TestGrammarActions grammar_actions(unilib_.get(), model.get(),
                                     entity_data_builder_.get());

  {
    std::vector<ActionSuggestion> result;
    EXPECT_TRUE(grammar_actions.SuggestActions(
        {/*messages=*/{
            {/*user_id=*/0, /*text=*/"do not forget to bring milk"}}},
        &result));
    EXPECT_THAT(result, ElementsAre(IsActionOfType("set_reminder")));
  }

  {
    std::vector<ActionSuggestion> result;
    EXPECT_TRUE(grammar_actions.SuggestActions(
        {/*messages=*/{{/*user_id=*/0, /*text=*/"do not forget to be there!"}}},
        &result));
    EXPECT_THAT(result, ElementsAre(IsActionOfType("set_reminder")));
  }

  {
    std::vector<ActionSuggestion> result;
    EXPECT_TRUE(grammar_actions.SuggestActions(
        {/*messages=*/{
            {/*user_id=*/0, /*text=*/"do not forget to buy safe or vault!"}}},
        &result));
    EXPECT_THAT(result, ElementsAre(IsActionOfType("set_reminder")));
  }

  {
    std::vector<ActionSuggestion> result;
    EXPECT_TRUE(grammar_actions.SuggestActions(
        {/*messages=*/{{/*user_id=*/0, /*text=*/"do not forget to be safe!"}}},
        &result));
    EXPECT_THAT(result, IsEmpty());
  }
}

}  // namespace
}  // namespace libtextclassifier3
