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

//! A “DICE policy” is a format for setting constraints on a DICE chain. A DICE chain policy
//! verifier takes a policy and a DICE chain, and returns a boolean indicating whether the
//! DICE chain meets the constraints set out on a policy.
//!
//! This forms the foundation of Dice Policy aware Authentication (DPA-Auth), where the server
//! authenticates a client by comparing its dice chain against a set policy.
//!
//! Another use is "sealing", where clients can use an appropriately constructed dice policy to
//! seal a secret. Unsealing is only permitted if dice chain of the component requesting unsealing
//! complies with the policy.
//!
//! A typical policy will assert things like:
//! # DK_pub must have this value
//! # The DICE chain must be exactly five certificates long
//! # authorityHash in the third certificate must have this value
//! securityVersion in the fourth certificate must be an integer greater than 8
//!
//! These constraints used to express policy are (for now) limited to following 2 types:
//! 1. Exact Match: useful for enforcing rules like authority hash should be exactly equal.
//! 2. Greater than or equal to: Useful for setting policies that seal
//! Anti-rollback protected entities (should be accessible to versions >= present).
//!
//! Dice Policy CDDL (keep in sync with DicePolicy.cddl):
//!
//! dicePolicy = [
//! 1, ; dice policy version
//! + nodeConstraintList ; for each entry in dice chain
//! ]
//!
//! nodeConstraintList = [
//!     * nodeConstraint
//! ]
//!
//! ; We may add a hashConstraint item later
//! nodeConstraint = exactMatchConstraint / geConstraint
//!
//! exactMatchConstraint = [1, keySpec, value]
//! geConstraint = [2, keySpec, int]
//!
//! keySpec = [value+]
//!
//! value = bool / int / tstr / bstr

use ciborium::Value;
use coset::{AsCborValue, CborSerializable, CoseError, CoseError::UnexpectedItem, CoseSign1};
use std::borrow::Cow;
use std::iter::zip;

type Error = String;

/// Version of the Dice policy spec
pub const DICE_POLICY_VERSION: u64 = 1;
/// Identifier for `exactMatchConstraint` as per spec
pub const EXACT_MATCH_CONSTRAINT: u16 = 1;
/// Identifier for `geConstraint` as per spec
pub const GREATER_OR_EQUAL_CONSTRAINT: u16 = 2;

/// Given an Android dice chain, check if it matches the given policy. This method returns
/// Ok(()) in case of successful match, otherwise returns error in case of failure.
pub fn chain_matches_policy(dice_chain: &[u8], policy: &[u8]) -> Result<(), Error> {
    DicePolicy::from_slice(policy)
        .map_err(|e| format!("DicePolicy decoding failed {e:?}"))?
        .matches_dice_chain(dice_chain)
        .map_err(|e| format!("DicePolicy matching failed {e:?}"))?;
    Ok(())
}

// TODO(b/291238565): (nested_)key & value type should be (bool/int/tstr/bstr). Status quo, only
// integer (nested_)key is supported.
// and maybe convert it into struct.
/// Each constraint (on a dice node) is a tuple: (ConstraintType, constraint_path, value)
/// This is Rust equivalent of `nodeConstraint` from CDDL above. Keep in sync!
#[derive(Clone, Debug, PartialEq)]
pub struct Constraint(u16, Vec<i64>, Value);

impl Constraint {
    /// Construct a new Constraint
    pub fn new(constraint_type: u16, path: Vec<i64>, value: Value) -> Result<Self, Error> {
        if constraint_type != EXACT_MATCH_CONSTRAINT
            && constraint_type != GREATER_OR_EQUAL_CONSTRAINT
        {
            return Err(format!("Invalid Constraint type: {constraint_type}"));
        }
        Ok(Self(constraint_type, path, value))
    }
}

impl AsCborValue for Constraint {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let [constrained_type, constraint_path, val] = value
            .into_array()
            .map_err(|_| UnexpectedItem("-", "Array"))?
            .try_into()
            .map_err(|_| UnexpectedItem("Array", "Array of size 3"))?;
        let constrained_type: u16 = value_to_integer(&constrained_type)?
            .try_into()
            .map_err(|_| UnexpectedItem("Integer", "u16"))?;
        let path_res: Vec<i64> = constraint_path
            .into_array()
            .map_err(|_| UnexpectedItem("-", "Array"))?
            .iter()
            .map(value_to_integer)
            .collect::<Result<_, _>>()?;
        Ok(Self(constrained_type, path_res, val))
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(Value::Array(vec![
            Value::from(self.0),
            Value::Array(self.1.into_iter().map(Value::from).collect()),
            self.2,
        ]))
    }
}

/// List of all constraints on a dice node.
/// This is Rust equivalent of `nodeConstraintList` in the CDDL above. Keep in sync!
#[derive(Clone, Debug, PartialEq)]
pub struct NodeConstraints(pub Box<[Constraint]>);

impl AsCborValue for NodeConstraints {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let res: Vec<Constraint> = value
            .into_array()
            .map_err(|_| UnexpectedItem("-", "Array"))?
            .into_iter()
            .map(Constraint::from_cbor_value)
            .collect::<Result<_, _>>()?;
        if res.is_empty() {
            return Err(UnexpectedItem("Empty array", "Non empty array"));
        }
        Ok(Self(res.into_boxed_slice()))
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        let res: Vec<Value> = self
            .0
            .into_vec()
            .into_iter()
            .map(Constraint::to_cbor_value)
            .collect::<Result<_, _>>()?;
        Ok(Value::Array(res))
    }
}

/// This is Rust equivalent of `dicePolicy` in the CDDL above. Keep in sync!
#[derive(Clone, Debug, PartialEq)]
pub struct DicePolicy {
    /// Dice policy version
    pub version: u64,
    /// List of `NodeConstraints`, one for each node of Dice chain.
    pub node_constraints_list: Box<[NodeConstraints]>,
}

impl AsCborValue for DicePolicy {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let mut arr = value.into_array().map_err(|_| UnexpectedItem("-", "Array"))?;
        if arr.len() < 2 {
            return Err(UnexpectedItem("Array", "Array with at least 2 elements"));
        }
        let (version, node_cons_list) = (value_to_integer(arr.first().unwrap())?, arr.split_off(1));
        let version: u64 = version.try_into().map_err(|_| UnexpectedItem("-", "u64"))?;
        let node_cons_list: Vec<NodeConstraints> = node_cons_list
            .into_iter()
            .map(NodeConstraints::from_cbor_value)
            .collect::<Result<_, _>>()?;
        Ok(Self { version, node_constraints_list: node_cons_list.into_boxed_slice() })
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        let mut res: Vec<Value> = Vec::with_capacity(1 + self.node_constraints_list.len());
        res.push(Value::from(self.version));
        for node_cons in self.node_constraints_list.into_vec() {
            res.push(node_cons.to_cbor_value()?)
        }
        Ok(Value::Array(res))
    }
}

impl CborSerializable for DicePolicy {}

impl DicePolicy {
    /// Dice chain policy verifier - Compare the input dice chain against this Dice policy.
    /// The method returns Ok() if the dice chain meets the constraints set in Dice policy,
    /// otherwise returns error in case of mismatch.
    /// TODO(b/291238565) Create a separate error module for DicePolicy mismatches.
    pub fn matches_dice_chain(&self, dice_chain: &[u8]) -> Result<(), Error> {
        let dice_chain = deserialize_cbor_array(dice_chain)?;
        check_is_explicit_key_dice_chain(&dice_chain)?;
        if dice_chain.len() != self.node_constraints_list.len() {
            return Err(format!(
                "Dice chain size({}) does not match policy({})",
                dice_chain.len(),
                self.node_constraints_list.len()
            ));
        }

        for (n, (dice_node, node_constraints)) in
            zip(dice_chain, self.node_constraints_list.iter()).enumerate()
        {
            let dice_node_payload = if n <= 1 {
                // 1st & 2nd dice node of Explicit-key DiceCertChain format are
                // EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION & DiceCertChainInitialPayload. The rest are
                // DiceChainEntry which is a CoseSign1.
                dice_node
            } else {
                payload_value_from_cose_sign(dice_node)
                    .map_err(|e| format!("Unable to get Cose payload at {n}: {e:?}"))?
            };
            check_constraints_on_node(node_constraints, &dice_node_payload)
                .map_err(|e| format!("Mismatch found at {n}: {e:?}"))?;
        }
        Ok(())
    }
}

fn check_constraints_on_node(
    node_constraints: &NodeConstraints,
    dice_node: &Value,
) -> Result<(), Error> {
    for constraint in node_constraints.0.iter() {
        check_constraint_on_node(constraint, dice_node)?;
    }
    Ok(())
}

fn check_constraint_on_node(constraint: &Constraint, dice_node: &Value) -> Result<(), Error> {
    let Constraint(cons_type, path, value_in_constraint) = constraint;
    let value_in_node = lookup_in_nested_container(dice_node, path)?
        .ok_or(format!("Value not found for constraint_path {path:?})"))?;
    match *cons_type {
        EXACT_MATCH_CONSTRAINT => {
            if value_in_node != *value_in_constraint {
                return Err(format!(
                    "Policy mismatch. Expected {value_in_constraint:?}; found {value_in_node:?}"
                ));
            }
        }
        GREATER_OR_EQUAL_CONSTRAINT => {
            let value_in_node = value_in_node
                .as_integer()
                .ok_or("Mismatch type: expected a CBOR integer".to_string())?;
            let value_min = value_in_constraint
                .as_integer()
                .ok_or("Mismatch type: expected a CBOR integer".to_string())?;
            if value_in_node < value_min {
                return Err(format!(
                    "Policy mismatch. Expected >= {value_min:?}; found {value_in_node:?}"
                ));
            }
        }
        cons_type => return Err(format!("Unexpected constraint type {cons_type:?}")),
    };
    Ok(())
}

/// Lookup value corresponding to constraint path in nested container.
/// This function recursively calls itself.
/// The depth of recursion is limited by the size of constraint_path.
pub fn lookup_in_nested_container(
    container: &Value,
    constraint_path: &[i64],
) -> Result<Option<Value>, Error> {
    if constraint_path.is_empty() {
        return Ok(Some(container.clone()));
    }
    let explicit_container = get_container_from_value(container)?;
    lookup_value_in_container(&explicit_container, constraint_path[0])
        .map_or_else(|| Ok(None), |val| lookup_in_nested_container(val, &constraint_path[1..]))
}

fn get_container_from_value(container: &Value) -> Result<Container, Error> {
    match container {
        // Value can be Map/Array/Encoded Map. Encoded Arrays are not yet supported (or required).
        // Note: Encoded Map is used for Configuration descriptor entry in DiceChainEntryPayload.
        Value::Bytes(b) => Value::from_slice(b)
            .map_err(|e| format!("{e:?}"))?
            .into_map()
            .map(|m| Container::Map(Cow::Owned(m)))
            .map_err(|e| format!("Expected a CBOR map: {:?}", e)),
        Value::Map(map) => Ok(Container::Map(Cow::Borrowed(map))),
        Value::Array(array) => Ok(Container::Array(array)),
        _ => Err(format!("Expected an array/map/bytes {container:?}")),
    }
}

#[derive(Clone)]
enum Container<'a> {
    Map(Cow<'a, Vec<(Value, Value)>>),
    Array(&'a Vec<Value>),
}

fn lookup_value_in_container<'a>(container: &'a Container<'a>, key: i64) -> Option<&'a Value> {
    match container {
        Container::Array(array) => array.get(key as usize),
        Container::Map(map) => {
            let key = Value::Integer(key.into());
            let mut val = None;
            for (k, v) in map.iter() {
                if k == &key {
                    val = Some(v);
                    break;
                }
            }
            val
        }
    }
}

/// This library only works with Explicit-key DiceCertChain format. Further we require it to have
/// at least 1 DiceChainEntry. Note that this is a lightweight check so that we fail early for
/// legacy chains.
pub fn check_is_explicit_key_dice_chain(dice_chain: &[Value]) -> Result<(), Error> {
    if matches!(dice_chain, [Value::Integer(_version), Value::Bytes(_public_key), _entry, ..]) {
        Ok(())
    } else {
        Err("Chain is not in explicit key format".to_string())
    }
}

/// Extract the payload from the COSE Sign
pub fn payload_value_from_cose_sign(cbor: Value) -> Result<Value, Error> {
    let sign1 = CoseSign1::from_cbor_value(cbor)
        .map_err(|e| format!("Error extracting CoseSign1: {e:?}"))?;
    match sign1.payload {
        None => Err("Missing payload".to_string()),
        Some(payload) => Value::from_slice(&payload).map_err(|e| format!("{e:?}")),
    }
}

/// Decode a CBOR array
pub fn deserialize_cbor_array(cbor_array_bytes: &[u8]) -> Result<Vec<Value>, Error> {
    let cbor_array = Value::from_slice(cbor_array_bytes)
        .map_err(|e| format!("Unable to decode top-level CBOR: {e:?}"))?;
    let cbor_array =
        cbor_array.into_array().map_err(|e| format!("Expected an array found: {e:?}"))?;
    Ok(cbor_array)
}

// Useful to convert [`ciborium::Value`] to integer. Note we already downgrade the returned
// integer to i64 for convenience. Value::Integer is capable of storing bigger numbers.
fn value_to_integer(value: &Value) -> Result<i64, CoseError> {
    let num = value
        .as_integer()
        .ok_or(CoseError::UnexpectedItem("-", "Integer"))?
        .try_into()
        .map_err(|_| CoseError::UnexpectedItem("Integer", "i64"))?;
    Ok(num)
}
