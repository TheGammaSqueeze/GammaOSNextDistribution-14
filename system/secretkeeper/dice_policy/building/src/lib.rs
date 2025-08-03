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

//! This library supports constructing Dice Policies on a Dice chains, enabling various ways to
//! specify the constraints. This adheres to the Dice Policy spec at DicePolicy.cddl & works with
//! the rust structs exported by libdice_policy.

#![allow(missing_docs)] // Sadly needed due to use of enumn::N

use ciborium::Value;
use dice_policy::{
    check_is_explicit_key_dice_chain, deserialize_cbor_array, lookup_in_nested_container,
    payload_value_from_cose_sign, Constraint, DicePolicy, NodeConstraints, DICE_POLICY_VERSION,
    EXACT_MATCH_CONSTRAINT, GREATER_OR_EQUAL_CONSTRAINT,
};
use enumn::N;
use itertools::Either;
use std::borrow::Cow;
use std::iter::once;

type Error = String;

/// Supported version of dice policy spec.
pub const SUPPORTED_DICE_POLICY_VERSION: u64 = 1;
/// Wildcard key to specify all indexes of an array in a nested container. The library only
/// supports 1 Wildcard key per `ConstraintSpec`.
pub const WILDCARD_FULL_ARRAY: i64 = -1;
/// Constraint Types supported in Dice policy.
#[repr(u16)]
#[non_exhaustive]
#[derive(Clone, Copy, Debug, PartialEq, N)]
pub enum ConstraintType {
    /// Enforce exact match criteria, indicating the policy should match
    /// if the dice chain has exact same specified values.
    ExactMatch = EXACT_MATCH_CONSTRAINT,
    /// Enforce Greater than or equal to criteria. When applied on security_version, this
    /// can be useful to set policy that matches dice chains with same or upgraded images.
    GreaterOrEqual = GREATER_OR_EQUAL_CONSTRAINT,
}

/// ConstraintSpec is used to specify which constraint type to apply and on which all paths in a
/// DiceChainEntry.
/// See documentation of `policy_for_dice_chain()` for examples.
#[derive(Clone)]
pub struct ConstraintSpec {
    constraint_type: ConstraintType,
    // `path` is essentially a series of indexes (of map keys or array positions) to locate a
    // particular value nested inside a DiceChainEntry.
    path: Vec<i64>,
    // If set to `Ignore`, the constraint is skipped if the `path` is missing in a
    // DiceChainEntry while constructing a policy. Note that this does not affect policy
    // matching.
    if_path_missing: MissingAction,
    // Which DiceChainEntry to apply the constraint on.
    target_entry: CertIndex,
}

impl ConstraintSpec {
    /// Constraint the value corresponding to `path`. `constraint_type` specifies how and
    /// `if_path_missing` specifies what to do if the `path` is missing in a DiceChainEntry.
    pub fn new(
        constraint_type: ConstraintType,
        path: Vec<i64>,
        if_path_missing: MissingAction,
        target_entry: CertIndex,
    ) -> Self {
        Self { constraint_type, path, if_path_missing, target_entry }
    }

    fn has_wildcard_entry(&self) -> bool {
        self.path.contains(&WILDCARD_FULL_ARRAY)
    }

    // Expand the ConstrainSpec into an iterable list. This expand to 1 or more specs depending
    // on presence of Wildcard entry.
    fn expand(
        &self,
        node_payload: &Value,
    ) -> impl Iterator<Item = Result<Cow<ConstraintSpec>, Error>> {
        if self.has_wildcard_entry() {
            expand_wildcard_entry(self, node_payload).map_or_else(
                |e| Either::Left(once(Err(e))),
                |cs| Either::Right(cs.into_iter().map(|c| Ok(Cow::Owned(c)))),
            )
        } else {
            Either::Left(once(Ok(Cow::Borrowed(self))))
        }
    }
}

/// Indicates index of a DiceChainEntry in the chain. Note this covers only DiceChainEntries in
/// ExplicitKeyDiceCertChain, ie, this does not include the first 2 dice nodes.
#[derive(Clone, PartialEq, Eq)]
pub enum CertIndex {
    /// All Entries
    All,
    /// Specify the index relative to the end of the chain. FromEnd(0) => last DiceChainEntry
    FromEnd(usize),
}

fn is_target_node(node_pos_from_end: usize, spec: &ConstraintSpec) -> bool {
    spec.target_entry == CertIndex::All
        || spec.target_entry == CertIndex::FromEnd(node_pos_from_end)
}

// Position of the first index marked `WILDCARD_FULL_ARRAY` in path.
fn wildcard_position(path: &[i64]) -> Result<usize, Error> {
    let Some(pos) = path.iter().position(|&key| key == WILDCARD_FULL_ARRAY) else {
        return Err("InvalidInput: Path does not have wildcard key".to_string());
    };
    Ok(pos)
}

// Size of array at specified `path` in the nested container.
fn size_of_array_in_nested_container(container: &Value, path: &[i64]) -> Result<usize, Error> {
    if let Some(Value::Array(array)) = lookup_in_nested_container(container, path)? {
        Ok(array.len())
    } else {
        Err("InternalError: Expected nested array not found".to_string())
    }
}

// Expand `ConstraintSpec` containing `WILDCARD_FULL_ARRAY` to entries with path covering each
// array index.
fn expand_wildcard_entry(
    spec: &ConstraintSpec,
    target_node: &Value,
) -> Result<Vec<ConstraintSpec>, Error> {
    let pos = wildcard_position(&spec.path)?;
    // Notice depth of the array in nested container is same as the position in path.
    let size = size_of_array_in_nested_container(target_node, &spec.path[..pos])?;
    Ok((0..size as i64).map(|i| replace_key(spec, pos, i)).collect())
}

// Get a ConstraintSpec same as `spec` except for changing the key at `pos` in its `path`
// This method panics if `pos` in out of index of path.
fn replace_key(spec: &ConstraintSpec, pos: usize, new_key: i64) -> ConstraintSpec {
    let mut spec: ConstraintSpec = spec.clone();
    spec.path[pos] = new_key;
    spec
}

/// Used to define a `ConstraintSpec` requirement. This allows client to set behavior of
/// policy construction in case the `path` is missing in a DiceChainEntry.
#[derive(Clone, Eq, PartialEq)]
pub enum MissingAction {
    /// Indicates that a constraint `path` is required in each DiceChainEntry. Fail if missing!
    Fail,
    /// Indicates that a constraint `path` may be missing in some DiceChainEntry. Ignore!
    Ignore,
}

/// Construct a dice policy on a given dice chain.
/// This can be used by clients to construct a policy to seal secrets.
/// Constraints on all but (first & second) dice nodes is applied using constraint_spec
/// argument. For the first and second node (version and root public key), the constraint is
/// ExactMatch of the whole node.
///
/// # Arguments
/// `dice_chain`: The serialized CBOR encoded Dice chain, adhering to Explicit-key
/// DiceCertChain format. See definition at ExplicitKeyDiceCertChain.cddl
///
/// `constraint_spec`: List of constraints to be applied on dice node.
/// Each constraint is a ConstraintSpec object.
///
/// Note: Dice node is treated as a nested structure (map or array) (& so the lookup is done in
/// that fashion).
///
/// Examples of constraint_spec:
///  1. For exact_match on auth_hash & greater_or_equal on security_version
///    constraint_spec =[
///     (ConstraintType::ExactMatch, vec![AUTHORITY_HASH]),
///     (ConstraintType::GreaterOrEqual, vec![CONFIG_DESC, COMPONENT_NAME]),
///    ];
///
/// 2. For hypothetical (and highly simplified) dice chain:
///
///    [1, ROT_KEY, [{1 : 'a', 2 : {200 : 5, 201 : 'b'}}]]
///    The following can be used
///    constraint_spec =[
///     ConstraintSpec(ConstraintType::ExactMatch, vec![1]),         // exact_matches value 'a'
///     ConstraintSpec(ConstraintType::GreaterOrEqual, vec![2, 200]),// matches any value >= 5
///    ];
pub fn policy_for_dice_chain(
    explicit_key_dice_chain: &[u8],
    constraint_spec: &[ConstraintSpec],
) -> Result<DicePolicy, Error> {
    let dice_chain = deserialize_cbor_array(explicit_key_dice_chain)?;
    check_is_explicit_key_dice_chain(&dice_chain)?;
    let mut constraints_list: Vec<NodeConstraints> = Vec::with_capacity(dice_chain.len());
    // 1st & 2nd dice node of Explicit-key DiceCertChain format are
    // EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION & DiceCertChainInitialPayload. The rest are
    // DiceChainEntry which is a CoseSign1.
    let chain_entries_len = dice_chain.len() - 2;
    let mut it = dice_chain.into_iter();
    constraints_list.push(NodeConstraints(Box::new([Constraint::new(
        ConstraintType::ExactMatch as u16,
        Vec::new(),
        it.next().unwrap(),
    )?])));

    constraints_list.push(NodeConstraints(Box::new([Constraint::new(
        ConstraintType::ExactMatch as u16,
        Vec::new(),
        it.next().unwrap(),
    )?])));
    for (i, chain_entry) in it.enumerate() {
        let entry = payload_value_from_cose_sign(chain_entry)
            .map_err(|e| format!("Unable to get Cose payload at {i}: {e:?}"))?;
        let node_pos_from_end = chain_entries_len - i - 1;
        constraints_list.push(
            constraints_on_dice_node(entry, constraint_spec, node_pos_from_end)
                .map_err(|e| format!("Unable to get constraints for payload at {i}: {e:?}"))?,
        );
    }

    Ok(DicePolicy {
        version: DICE_POLICY_VERSION,
        node_constraints_list: constraints_list.into_boxed_slice(),
    })
}

// Take the ['node_payload'] of a dice node & construct the [`NodeConstraints`] on it. If the value
// corresponding to the a [`constraint_spec`] is not present in payload & iff it is marked
// `MissingAction::Ignore`, the corresponding constraint will be missing from the NodeConstraints.
// Not all constraint_spec applies to all DiceChainEntries. `pos_from_end` is the index of the
// DiceChainEntry relative to end & that is used to skip constraint_spec if not applicable.
fn constraints_on_dice_node(
    node_payload: Value,
    constraint_spec: &[ConstraintSpec],
    pos_from_end: usize,
) -> Result<NodeConstraints, Error> {
    let mut node_constraints: Vec<Constraint> = Vec::new();
    // Some constraint spec may have wildcard entries, expand those!
    let constraint_spec = constraint_spec
        .iter()
        .filter(|c| is_target_node(pos_from_end, c))
        .flat_map(|c| c.expand(&node_payload))
        .collect::<Result<Vec<_>, _>>()?;
    for constraint_item in constraint_spec {
        if let Some(constraint) = constraint_on_dice_node(&node_payload, &constraint_item)? {
            node_constraints.push(constraint);
        }
    }
    Ok(NodeConstraints(node_constraints.into_boxed_slice()))
}

fn constraint_on_dice_node(
    node_payload: &Value,
    constraint_spec: &ConstraintSpec,
) -> Result<Option<Constraint>, Error> {
    if constraint_spec.path.is_empty() {
        return Err("Expected non-empty key spec".to_string());
    }
    let constraint = match lookup_in_nested_container(node_payload, &constraint_spec.path)? {
        Some(val) => Some(Constraint::new(
            constraint_spec.constraint_type as u16,
            constraint_spec.path.to_vec(),
            val,
        )?),
        None => {
            if constraint_spec.if_path_missing == MissingAction::Ignore {
                log::warn!("Value not found for {:?}, - skipping!", constraint_spec.path);
                None
            } else {
                return Err(format!(
                    "Value not found for : {:?}, constraint\
                     spec is marked to fail on missing path",
                    constraint_spec.path
                ));
            }
        }
    };
    Ok(constraint)
}
