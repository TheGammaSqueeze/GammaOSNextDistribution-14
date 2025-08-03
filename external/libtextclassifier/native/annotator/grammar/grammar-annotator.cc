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

#include "annotator/grammar/grammar-annotator.h"

#include "annotator/feature-processor.h"
#include "annotator/grammar/utils.h"
#include "annotator/types.h"
#include "utils/base/arena.h"
#include "utils/base/logging.h"
#include "utils/normalization.h"
#include "utils/optional.h"
#include "utils/utf8/unicodetext.h"

namespace libtextclassifier3 {
namespace {

// Retrieves all capturing nodes from a parse tree.
std::unordered_map<uint16, const grammar::ParseTree*> GetCapturingNodes(
    const grammar::ParseTree* parse_tree) {
  std::unordered_map<uint16, const grammar::ParseTree*> capturing_nodes;
  for (const grammar::MappingNode* mapping_node :
       grammar::SelectAllOfType<grammar::MappingNode>(
           parse_tree, grammar::ParseTree::Type::kMapping)) {
    capturing_nodes[mapping_node->id] = mapping_node;
  }
  return capturing_nodes;
}

// Computes the selection boundaries from a parse tree.
CodepointSpan MatchSelectionBoundaries(
    const grammar::ParseTree* parse_tree,
    const GrammarModel_::RuleClassificationResult* classification) {
  if (classification->capturing_group() == nullptr) {
    // Use full match as selection span.
    return parse_tree->codepoint_span;
  }

  // Set information from capturing matches.
  CodepointSpan span{kInvalidIndex, kInvalidIndex};
  std::unordered_map<uint16, const grammar::ParseTree*> capturing_nodes =
      GetCapturingNodes(parse_tree);

  // Compute span boundaries.
  for (int i = 0; i < classification->capturing_group()->size(); i++) {
    auto it = capturing_nodes.find(i);
    if (it == capturing_nodes.end()) {
      // Capturing group is not active, skip.
      continue;
    }
    const CapturingGroup* group = classification->capturing_group()->Get(i);
    if (group->extend_selection()) {
      if (span.first == kInvalidIndex) {
        span = it->second->codepoint_span;
      } else {
        span.first = std::min(span.first, it->second->codepoint_span.first);
        span.second = std::max(span.second, it->second->codepoint_span.second);
      }
    }
  }
  return span;
}

}  // namespace

GrammarAnnotator::GrammarAnnotator(
    const UniLib* unilib, const GrammarModel* model,
    const MutableFlatbufferBuilder* entity_data_builder)
    : unilib_(*unilib),
      model_(model),
      tokenizer_(BuildTokenizer(unilib, model->tokenizer_options())),
      entity_data_builder_(entity_data_builder),
      analyzer_(unilib, model->rules(), &tokenizer_) {}

// Filters out results that do not overlap with a reference span.
std::vector<grammar::Derivation> GrammarAnnotator::OverlappingDerivations(
    const CodepointSpan& selection,
    const std::vector<grammar::Derivation>& derivations,
    const bool only_exact_overlap) const {
  std::vector<grammar::Derivation> result;
  for (const grammar::Derivation& derivation : derivations) {
    // Discard matches that do not match the selection.
    // Simple check.
    if (!SpansOverlap(selection, derivation.parse_tree->codepoint_span)) {
      continue;
    }

    // Compute exact selection boundaries (without assertions and
    // non-capturing parts).
    const CodepointSpan span = MatchSelectionBoundaries(
        derivation.parse_tree,
        model_->rule_classification_result()->Get(derivation.rule_id));
    if (!SpansOverlap(selection, span) ||
        (only_exact_overlap && span != selection)) {
      continue;
    }
    result.push_back(derivation);
  }
  return result;
}

bool GrammarAnnotator::InstantiateAnnotatedSpanFromDerivation(
    const grammar::TextContext& input_context,
    const grammar::ParseTree* parse_tree,
    const GrammarModel_::RuleClassificationResult* interpretation,
    AnnotatedSpan* result) const {
  result->span = MatchSelectionBoundaries(parse_tree, interpretation);
  ClassificationResult classification;
  if (!InstantiateClassificationFromDerivation(
          input_context, parse_tree, interpretation, &classification)) {
    return false;
  }
  result->classification.push_back(classification);
  return true;
}

// Instantiates a classification result from a rule match.
bool GrammarAnnotator::InstantiateClassificationFromDerivation(
    const grammar::TextContext& input_context,
    const grammar::ParseTree* parse_tree,
    const GrammarModel_::RuleClassificationResult* interpretation,
    ClassificationResult* classification) const {
  classification->collection = interpretation->collection_name()->str();
  classification->score = interpretation->target_classification_score();
  classification->priority_score = interpretation->priority_score();

  // Assemble entity data.
  if (entity_data_builder_ == nullptr) {
    return true;
  }
  std::unique_ptr<MutableFlatbuffer> entity_data =
      entity_data_builder_->NewRoot();
  if (interpretation->serialized_entity_data() != nullptr) {
    entity_data->MergeFromSerializedFlatbuffer(
        StringPiece(interpretation->serialized_entity_data()->data(),
                    interpretation->serialized_entity_data()->size()));
  }
  if (interpretation->entity_data() != nullptr) {
    entity_data->MergeFrom(reinterpret_cast<const flatbuffers::Table*>(
        interpretation->entity_data()));
  }

  // Populate entity data from the capturing matches.
  if (interpretation->capturing_group() != nullptr) {
    // Gather active capturing matches.
    std::unordered_map<uint16, const grammar::ParseTree*> capturing_nodes =
        GetCapturingNodes(parse_tree);

    for (int i = 0; i < interpretation->capturing_group()->size(); i++) {
      auto it = capturing_nodes.find(i);
      if (it == capturing_nodes.end()) {
        // Capturing group is not active, skip.
        continue;
      }
      const CapturingGroup* group = interpretation->capturing_group()->Get(i);

      // Add static entity data.
      if (group->serialized_entity_data() != nullptr) {
        entity_data->MergeFromSerializedFlatbuffer(
            StringPiece(interpretation->serialized_entity_data()->data(),
                        interpretation->serialized_entity_data()->size()));
      }

      // Set entity field from captured text.
      if (group->entity_field_path() != nullptr) {
        const grammar::ParseTree* capturing_match = it->second;
        UnicodeText match_text =
            input_context.Span(capturing_match->codepoint_span);
        if (group->normalization_options() != nullptr) {
          match_text = NormalizeText(unilib_, group->normalization_options(),
                                     match_text);
        }
        if (!entity_data->ParseAndSet(group->entity_field_path(),
                                      match_text.ToUTF8String())) {
          TC3_LOG(ERROR) << "Could not set entity data from capturing match.";
          return false;
        }
      }
    }
  }

  if (entity_data && entity_data->HasExplicitlySetFields()) {
    classification->serialized_entity_data = entity_data->Serialize();
  }
  return true;
}

bool GrammarAnnotator::Annotate(const std::vector<Locale>& locales,
                                const UnicodeText& text,
                                std::vector<AnnotatedSpan>* result) const {
  grammar::TextContext input_context =
      analyzer_.BuildTextContextForInput(text, locales);

  UnsafeArena arena(/*block_size=*/16 << 10);

  for (const grammar::Derivation& derivation : ValidDeduplicatedDerivations(
           analyzer_.parser().Parse(input_context, &arena))) {
    const GrammarModel_::RuleClassificationResult* interpretation =
        model_->rule_classification_result()->Get(derivation.rule_id);
    if ((interpretation->enabled_modes() & ModeFlag_ANNOTATION) == 0) {
      continue;
    }
    result->emplace_back();
    if (!InstantiateAnnotatedSpanFromDerivation(
            input_context, derivation.parse_tree, interpretation,
            &result->back())) {
      return false;
    }
  }

  return true;
}

bool GrammarAnnotator::SuggestSelection(const std::vector<Locale>& locales,
                                        const UnicodeText& text,
                                        const CodepointSpan& selection,
                                        AnnotatedSpan* result) const {
  if (!selection.IsValid() || selection.IsEmpty()) {
    return false;
  }

  grammar::TextContext input_context =
      analyzer_.BuildTextContextForInput(text, locales);

  UnsafeArena arena(/*block_size=*/16 << 10);

  const GrammarModel_::RuleClassificationResult* best_interpretation = nullptr;
  const grammar::ParseTree* best_match = nullptr;
  for (const grammar::Derivation& derivation :
       ValidDeduplicatedDerivations(OverlappingDerivations(
           selection, analyzer_.parser().Parse(input_context, &arena),
           /*only_exact_overlap=*/false))) {
    const GrammarModel_::RuleClassificationResult* interpretation =
        model_->rule_classification_result()->Get(derivation.rule_id);
    if ((interpretation->enabled_modes() & ModeFlag_SELECTION) == 0) {
      continue;
    }
    if (best_interpretation == nullptr ||
        interpretation->priority_score() >
            best_interpretation->priority_score()) {
      best_interpretation = interpretation;
      best_match = derivation.parse_tree;
    }
  }

  if (best_interpretation == nullptr) {
    return false;
  }

  return InstantiateAnnotatedSpanFromDerivation(input_context, best_match,
                                                best_interpretation, result);
}

bool GrammarAnnotator::ClassifyText(
    const std::vector<Locale>& locales, const UnicodeText& text,
    const CodepointSpan& selection,
    ClassificationResult* classification_result) const {
  if (!selection.IsValid() || selection.IsEmpty()) {
    // Nothing to do.
    return false;
  }

  grammar::TextContext input_context =
      analyzer_.BuildTextContextForInput(text, locales);

  if (const TokenSpan context_span = CodepointSpanToTokenSpan(
          input_context.tokens, selection,
          /*snap_boundaries_to_containing_tokens=*/true);
      context_span.IsValid()) {
    if (model_->context_left_num_tokens() != kInvalidIndex) {
      input_context.context_span.first =
          std::max(0, context_span.first - model_->context_left_num_tokens());
    }
    if (model_->context_right_num_tokens() != kInvalidIndex) {
      input_context.context_span.second =
          std::min(static_cast<int>(input_context.tokens.size()),
                   context_span.second + model_->context_right_num_tokens());
    }
  }

  UnsafeArena arena(/*block_size=*/16 << 10);

  const GrammarModel_::RuleClassificationResult* best_interpretation = nullptr;
  const grammar::ParseTree* best_match = nullptr;
  for (const grammar::Derivation& derivation :
       ValidDeduplicatedDerivations(OverlappingDerivations(
           selection, analyzer_.parser().Parse(input_context, &arena),
           /*only_exact_overlap=*/true))) {
    const GrammarModel_::RuleClassificationResult* interpretation =
        model_->rule_classification_result()->Get(derivation.rule_id);
    if ((interpretation->enabled_modes() & ModeFlag_CLASSIFICATION) == 0) {
      continue;
    }
    if (best_interpretation == nullptr ||
        interpretation->priority_score() >
            best_interpretation->priority_score()) {
      best_interpretation = interpretation;
      best_match = derivation.parse_tree;
    }
  }

  if (best_interpretation == nullptr) {
    return false;
  }

  return InstantiateClassificationFromDerivation(
      input_context, best_match, best_interpretation, classification_result);
}

}  // namespace libtextclassifier3
