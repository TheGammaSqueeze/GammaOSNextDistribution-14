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

#include "actions/feature-processor.h"
#include "actions/utils.h"
#include "annotator/types.h"
#include "utils/base/arena.h"
#include "utils/base/statusor.h"
#include "utils/utf8/unicodetext.h"

namespace libtextclassifier3 {

GrammarActions::GrammarActions(
    const UniLib* unilib, const RulesModel_::GrammarRules* grammar_rules,
    const MutableFlatbufferBuilder* entity_data_builder,
    const std::string& smart_reply_action_type)
    : unilib_(*unilib),
      grammar_rules_(grammar_rules),
      tokenizer_(CreateTokenizer(grammar_rules->tokenizer_options(), unilib)),
      entity_data_builder_(entity_data_builder),
      analyzer_(unilib, grammar_rules->rules(), tokenizer_.get()),
      smart_reply_action_type_(smart_reply_action_type) {}

bool GrammarActions::InstantiateActionsFromMatch(
    const grammar::TextContext& text_context, const int message_index,
    const grammar::Derivation& derivation,
    std::vector<ActionSuggestion>* result) const {
  const RulesModel_::GrammarRules_::RuleMatch* rule_match =
      grammar_rules_->rule_match()->Get(derivation.rule_id);
  if (rule_match == nullptr || rule_match->action_id() == nullptr) {
    TC3_LOG(ERROR) << "No rule action defined.";
    return false;
  }

  // Gather active capturing matches.
  std::unordered_map<uint16, const grammar::ParseTree*> capturing_matches;
  for (const grammar::MappingNode* mapping_node :
       grammar::SelectAllOfType<grammar::MappingNode>(
           derivation.parse_tree, grammar::ParseTree::Type::kMapping)) {
    capturing_matches[mapping_node->id] = mapping_node;
  }

  // Instantiate actions from the rule match.
  for (const uint16 action_id : *rule_match->action_id()) {
    const RulesModel_::RuleActionSpec* action_spec =
        grammar_rules_->actions()->Get(action_id);
    std::vector<ActionSuggestionAnnotation> annotations;

    std::unique_ptr<MutableFlatbuffer> entity_data =
        entity_data_builder_ != nullptr ? entity_data_builder_->NewRoot()
                                        : nullptr;

    // Set information from capturing matches.
    if (action_spec->capturing_group() != nullptr) {
      for (const RulesModel_::RuleActionSpec_::RuleCapturingGroup* group :
           *action_spec->capturing_group()) {
        auto it = capturing_matches.find(group->group_id());
        if (it == capturing_matches.end()) {
          // Capturing match is not active, skip.
          continue;
        }

        const grammar::ParseTree* capturing_match = it->second;
        const UnicodeText match_text =
            text_context.Span(capturing_match->codepoint_span);
        UnicodeText normalized_match_text =
            NormalizeMatchText(unilib_, group, match_text);

        if (!MergeEntityDataFromCapturingMatch(
                group, normalized_match_text.ToUTF8String(),
                entity_data.get())) {
          TC3_LOG(ERROR)
              << "Could not merge entity data from a capturing match.";
          return false;
        }

        // Add smart reply suggestions.
        SuggestTextRepliesFromCapturingMatch(entity_data_builder_, group,
                                             normalized_match_text,
                                             smart_reply_action_type_, result);

        // Add annotation.
        ActionSuggestionAnnotation annotation;
        if (FillAnnotationFromCapturingMatch(
                /*span=*/capturing_match->codepoint_span, group,
                /*message_index=*/message_index, match_text.ToUTF8String(),
                &annotation)) {
          if (group->use_annotation_match()) {
            std::vector<const grammar::AnnotationNode*> annotations =
                grammar::SelectAllOfType<grammar::AnnotationNode>(
                    capturing_match, grammar::ParseTree::Type::kAnnotation);
            if (annotations.size() != 1) {
              TC3_LOG(ERROR) << "Could not get annotation for match.";
              return false;
            }
            annotation.entity = *annotations.front()->annotation;
          }
          annotations.push_back(std::move(annotation));
        }
      }
    }

    if (action_spec->action() != nullptr) {
      ActionSuggestion suggestion;
      suggestion.annotations = annotations;
      FillSuggestionFromSpec(action_spec->action(), entity_data.get(),
                             &suggestion);
      result->push_back(std::move(suggestion));
    }
  }
  return true;
}
bool GrammarActions::SuggestActions(
    const Conversation& conversation,
    std::vector<ActionSuggestion>* result) const {
  if (grammar_rules_->rules()->rules() == nullptr ||
      conversation.messages.back().text.empty()) {
    // Nothing to do.
    return true;
  }

  std::vector<Locale> locales;
  if (!ParseLocales(conversation.messages.back().detected_text_language_tags,
                    &locales)) {
    TC3_LOG(ERROR) << "Could not parse locales of input text.";
    return false;
  }

  const int message_index = conversation.messages.size() - 1;
  grammar::TextContext text = analyzer_.BuildTextContextForInput(
      UTF8ToUnicodeText(conversation.messages.back().text, /*do_copy=*/false),
      locales);
  text.annotations = conversation.messages.back().annotations;

  UnsafeArena arena(/*block_size=*/16 << 10);
  StatusOr<std::vector<grammar::EvaluatedDerivation>> evaluated_derivations =
      analyzer_.Parse(text, &arena);
  // TODO(b/171294882): Return the status here and below.
  if (!evaluated_derivations.ok()) {
    TC3_LOG(ERROR) << "Could not run grammar analyzer: "
                   << evaluated_derivations.status().error_message();
    return false;
  }

  for (const grammar::EvaluatedDerivation& evaluated_derivation :
       evaluated_derivations.ValueOrDie()) {
    if (!InstantiateActionsFromMatch(text, message_index, evaluated_derivation,
                                     result)) {
      TC3_LOG(ERROR) << "Could not instantiate actions from a grammar match.";
      return false;
    }
  }

  return true;
}

}  // namespace libtextclassifier3
