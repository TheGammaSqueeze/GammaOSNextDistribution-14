// Copyright (C) 2022 Google LLC
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

#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER_QUERY_VISITOR_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER_QUERY_VISITOR_H_

#include <cstdint>
#include <memory>
#include <stack>
#include <string>
#include <unordered_set>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/index.h"
#include "icing/index/iterator/doc-hit-info-iterator-filter.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree.h"
#include "icing/query/advanced_query_parser/function.h"
#include "icing/query/advanced_query_parser/pending-value.h"
#include "icing/query/query-features.h"
#include "icing/query/query-results.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-store.h"
#include "icing/tokenization/tokenizer.h"
#include "icing/transform/normalizer.h"

namespace icing {
namespace lib {

// The Visitor used to create the DocHitInfoIterator tree from the AST output by
// the parser.
class QueryVisitor : public AbstractSyntaxTreeVisitor {
 public:
  explicit QueryVisitor(Index* index,
                        const NumericIndex<int64_t>* numeric_index,
                        const DocumentStore* document_store,
                        const SchemaStore* schema_store,
                        const Normalizer* normalizer,
                        const Tokenizer* tokenizer,
                        std::string_view raw_query_text,
                        DocHitInfoIteratorFilter::Options filter_options,
                        TermMatchType::Code match_type,
                        bool needs_term_frequency_info, int64_t current_time_ms)
      : QueryVisitor(index, numeric_index, document_store, schema_store,
                     normalizer, tokenizer, raw_query_text, filter_options,
                     match_type, needs_term_frequency_info,
                     PendingPropertyRestricts(),
                     /*processing_not=*/false, current_time_ms) {}

  void VisitFunctionName(const FunctionNameNode* node) override;
  void VisitString(const StringNode* node) override;
  void VisitText(const TextNode* node) override;
  void VisitMember(const MemberNode* node) override;
  void VisitFunction(const FunctionNode* node) override;
  void VisitUnaryOperator(const UnaryOperatorNode* node) override;
  void VisitNaryOperator(const NaryOperatorNode* node) override;

  // RETURNS:
  //   - the QueryResults reflecting the AST that was visited
  //   - INVALID_ARGUMENT if the AST does not conform to supported expressions
  //   - NOT_FOUND if the AST refers to a property that does not exist
  libtextclassifier3::StatusOr<QueryResults> ConsumeResults() &&;

 private:
  // An internal class to help manage property restricts being applied at
  // different levels.
  class PendingPropertyRestricts {
   public:
    // Add another set of property restricts. Elements of new_restricts that are
    // not present in active_property_rest
    void AddValidRestricts(std::set<std::string> new_restricts);

    // Pops the most recently added set of property restricts.
    void PopRestricts() {
      if (has_active_property_restricts()) {
        pending_property_restricts_.pop_back();
      }
    }

    bool has_active_property_restricts() const {
      return !pending_property_restricts_.empty();
    }

    // The set of all property restrictions that are currently being applied.
    const std::set<std::string>& active_property_restricts() const {
      return pending_property_restricts_.back();
    }

   private:
    std::vector<std::set<std::string>> pending_property_restricts_;
  };

  explicit QueryVisitor(
      Index* index, const NumericIndex<int64_t>* numeric_index,
      const DocumentStore* document_store, const SchemaStore* schema_store,
      const Normalizer* normalizer, const Tokenizer* tokenizer,
      std::string_view raw_query_text,
      DocHitInfoIteratorFilter::Options filter_options,
      TermMatchType::Code match_type, bool needs_term_frequency_info,
      PendingPropertyRestricts pending_property_restricts, bool processing_not,
      int64_t current_time_ms)
      : index_(*index),
        numeric_index_(*numeric_index),
        document_store_(*document_store),
        schema_store_(*schema_store),
        normalizer_(*normalizer),
        tokenizer_(*tokenizer),
        raw_query_text_(raw_query_text),
        filter_options_(std::move(filter_options)),
        match_type_(match_type),
        needs_term_frequency_info_(needs_term_frequency_info),
        pending_property_restricts_(std::move(pending_property_restricts)),
        processing_not_(processing_not),
        expecting_numeric_arg_(false),
        current_time_ms_(current_time_ms) {
    RegisterFunctions();
  }

  bool has_pending_error() const { return !pending_error_.ok(); }

  // Creates a DocHitInfoIterator reflecting the provided term and whether the
  // prefix operator has been applied to this term. Also populates,
  // property_query_terms_map_ and query_term_iterators_ as appropriate.
  // Returns:
  //   - On success, a DocHitInfoIterator for the provided term
  //   - INVALID_ARGUMENT if unable to create an iterator for the term.
  libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
  CreateTermIterator(const QueryTerm& term);

  // Processes the PendingValue at the top of pending_values_, parses it into a
  // int64_t and pops the top.
  // Returns:
  //   - On success, the int value stored in the text at the top
  //   - INVALID_ARGUMENT if pending_values_ is empty, doesn't hold a text or
  //     can't be parsed as an int.
  libtextclassifier3::StatusOr<int64_t> PopPendingIntValue();

  // Processes the PendingValue at the top of pending_values_ and pops the top.
  // Returns:
  //   - On success, the string value stored in the text at the top and a bool
  //     indicating whether or not the string value has a prefix operator.
  //   - INVALID_ARGUMENT if pending_values_ is empty or doesn't hold a string.
  libtextclassifier3::StatusOr<QueryTerm> PopPendingStringValue();

  // Processes the PendingValue at the top of pending_values_ and pops the top.
  // Returns:
  //   - On success, the string value stored in the text at the top
  //     indicating whether or not the string value has a prefix operator.
  //   - INVALID_ARGUMENT if pending_values_ is empty or doesn't hold a text.
  libtextclassifier3::StatusOr<QueryTerm> PopPendingTextValue();

  // Processes the PendingValue at the top of pending_values_ and pops the top.
  // Returns:
  //   - On success, a DocHitInfoIterator representing for the term at the top
  //   - INVALID_ARGUMENT if pending_values_ is empty or if unable to create an
  //       iterator for the term.
  libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
  PopPendingIterator();

  // Processes all PendingValues at the top of pending_values_ until the first
  // placeholder is encounter.
  // Returns:
  //   - On success, a vector containing all DocHitInfoIterators representing
  //     the values at the top of pending_values_
  //   - INVALID_ARGUMENT if pending_values_is empty or if unable to create an
  //       iterator for any of the terms at the top of pending_values_
  libtextclassifier3::StatusOr<std::vector<std::unique_ptr<DocHitInfoIterator>>>
  PopAllPendingIterators();

  // Processes the unary operator node as a NOT operator. A NOT can have an
  // operator type of "NOT" or "MINUS"
  //
  // RETURNS:
  //   - OK on success
  //   - INVALID_ARGUMENT if any errors are encountered while processing
  //     node->child
  libtextclassifier3::Status ProcessNotOperator(const UnaryOperatorNode* node);

  // Processes the unary operator node as a negation operator. A negation
  // operator should have an operator of type "MINUS" and it's children must
  // resolve to a numeric value.
  //
  // RETURNS:
  //   - OK on success
  //   - INVALID_ARGUMENT if the node->child can't be resolved to a numeric
  //     value.
  libtextclassifier3::Status ProcessNegationOperator(
      const UnaryOperatorNode* node);

  // Processes the NumericComparator represented by node. This must be called
  // *after* this node's children have been visited. The PendingValues added by
  // this node's children will be consumed by this function and the PendingValue
  // for this node will be returned.
  // Returns:
  //   - On success, OK
  //   - INVALID_ARGUMENT if unable to retrieve string value or int value
  //   - NOT_FOUND if there is no entry in the numeric index for the property
  libtextclassifier3::Status ProcessNumericComparator(
      const NaryOperatorNode* node);

  // Processes the AND and OR operators represented by the node. This must be
  // called *after* this node's children have been visited. The PendingValues
  // added by this node's children will be consumed by this function and the
  // PendingValue for this node will be returned.
  // Returns:
  //   - On success, then PendingValue representing this node and it's children.
  //   - INVALID_ARGUMENT if unable to retrieve iterators for any of this node's
  //       children.
  libtextclassifier3::StatusOr<PendingValue> ProcessAndOperator(
      const NaryOperatorNode* node);

  // Processes the OR operator represented by the node. This must be called
  // *after* this node's children have been visited. The PendingValues added by
  // this node's children will be consumed by this function and the PendingValue
  // for this node will be returned.
  // Returns:
  //   - On success, then PendingValue representing this node and it's children.
  //   - INVALID_ARGUMENT if unable to retrieve iterators for any of this node's
  //       children.
  libtextclassifier3::StatusOr<PendingValue> ProcessOrOperator(
      const NaryOperatorNode* node);

  // Populates registered_functions with the currently supported set of
  // functions.
  void RegisterFunctions();

  // Implementation of `search` custom function in the query language.
  // Returns:
  //   - a PendingValue holding the DocHitInfoIterator reflecting the query
  //     provided to SearchFunction
  //   - any errors returned by Lexer::ExtractTokens, Parser::ConsumeQuery or
  //     QueryVisitor::ConsumeResults.
  libtextclassifier3::StatusOr<PendingValue> SearchFunction(
      std::vector<PendingValue>&& args);

  // Implementation of the propertyDefined(property_path) custom function.
  // Returns:
  //   - a Pending Value holding a DocHitIterator that returns hits for all
  //     documents whose schema types have defined the property specified by
  //     property_path.
  //   - any errors returned by Lexer::ExtractTokens
  libtextclassifier3::StatusOr<PendingValue> PropertyDefinedFunction(
      std::vector<PendingValue>&& args);

  // Implementation of the hasProperty(property_path) custom function.
  // Returns:
  //   - a Pending Value holding a DocHitIterator that returns hits for all
  //     documents that have the property specified by property_path.
  //   - any errors returned by Lexer::ExtractTokens
  libtextclassifier3::StatusOr<PendingValue> HasPropertyFunction(
      std::vector<PendingValue>&& args);

  // Handles a NaryOperatorNode where the operator is HAS (':') and pushes an
  // iterator with the proper section filter applied. If the current property
  // restriction represented by pending_property_restricts and the first child
  // of this node is unsatisfiable (ex. `prop1:(prop2:foo)`), then a NONE
  // iterator is returned immediately and subtree represented by the second
  // child is not traversed.
  //
  // Returns:
  //  - OK on success
  //  - INVALID_ARGUMENT node does not have exactly two children or the two
  //    children cannot be resolved to a MEMBER or an iterator respectively.
  libtextclassifier3::Status ProcessHasOperator(const NaryOperatorNode* node);

  // Returns the correct match type to apply based on both the match type and
  // whether the prefix operator is currently present.
  TermMatchType::Code GetTermMatchType(bool is_prefix) const {
    return (is_prefix) ? TermMatchType::PREFIX : match_type_;
  }

  std::stack<PendingValue> pending_values_;
  libtextclassifier3::Status pending_error_;

  // A map from function name to Function instance.
  std::unordered_map<std::string, Function> registered_functions_;

  SectionRestrictQueryTermsMap property_query_terms_map_;

  QueryTermIteratorsMap query_term_iterators_;
  // Set of features invoked in the query.
  std::unordered_set<Feature> features_;

  Index& index_;                                // Does not own!
  const NumericIndex<int64_t>& numeric_index_;  // Does not own!
  const DocumentStore& document_store_;         // Does not own!
  const SchemaStore& schema_store_;             // Does not own!
  const Normalizer& normalizer_;                // Does not own!
  const Tokenizer& tokenizer_;                  // Does not own!

  std::string_view raw_query_text_;
  DocHitInfoIteratorFilter::Options filter_options_;
  TermMatchType::Code match_type_;
  // Whether or not term_frequency information is needed. This affects:
  //  - how DocHitInfoIteratorTerms are constructed
  //  - whether the QueryTermIteratorsMap is populated in the QueryResults.
  bool needs_term_frequency_info_;

  // The stack of property restricts currently being processed by the visitor.
  PendingPropertyRestricts pending_property_restricts_;
  bool processing_not_;

  // Whether we are in the midst of processing a subtree that is expected to
  // resolve to a numeric argument.
  bool expecting_numeric_arg_;

  int64_t current_time_ms_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER_QUERY_VISITOR_H_
