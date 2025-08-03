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

//! Test libsdice_policy_builder & libdice_policy. Uses `rdroidtest` attribute macro.

use ciborium::{cbor, Value};
use coset::{AsCborValue, CborSerializable, CoseKey, CoseSign1, Header, ProtectedHeader};
use dice_policy::{lookup_in_nested_container, Constraint, DicePolicy, NodeConstraints};
use dice_policy_builder::{
    policy_for_dice_chain, CertIndex, ConstraintSpec, ConstraintType, MissingAction,
    WILDCARD_FULL_ARRAY,
};
use rdroidtest::rdroidtest;

const AUTHORITY_HASH: i64 = -4670549;
const CONFIG_DESC: i64 = -4670548;
const COMPONENT_NAME: i64 = -70002;
const COMPONENT_VERSION: i64 = -70003;
const SECURITY_VERSION: i64 = -70005;
const KEY_MODE: i64 = -4670551;
const EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION: u64 = 1;

// Helper struct to encapsulate artifacts that are useful for unit tests.
struct TestArtifacts {
    // A dice chain.
    input_dice: Vec<u8>,
    // A list of ConstraintSpec that can be applied on the input_dice to get a dice policy.
    constraint_spec: Vec<ConstraintSpec>,
    // The expected dice policy if above constraint_spec is applied to input_dice.
    expected_dice_policy: DicePolicy,
    // Another dice chain, which is almost same as the input_dice, but (roughly) imitates
    // an 'updated' one, ie, some int entries are higher than corresponding entry
    // in input_chain.
    updated_input_dice: Vec<u8>,
}

impl TestArtifacts {
    // Get an example instance of TestArtifacts. This uses a hard coded, hypothetical
    // chain of certificates & a list of constraint_spec on this.
    fn get_example() -> Self {
        const EXAMPLE_NUM_1: i64 = 59765;
        const EXAMPLE_NUM_2: i64 = 59766;
        const EXAMPLE_STRING: &str = "testing_dice_policy";
        const UNCONSTRAINED_STRING: &str = "unconstrained_string";
        const ANOTHER_UNCONSTRAINED_STRING: &str = "another_unconstrained_string";
        const CONSTRAINED_ARRAY: [&str; 4] = ["Array", "IN", "LAST", "DICE_CHAIN_ENTRY"];
        let constrained_array = cbor!(CONSTRAINED_ARRAY).unwrap();

        let rot_key = Value::Bytes(CoseKey::default().to_vec().unwrap());
        let input_dice = Self::get_dice_chain_helper(
            rot_key.clone(),
            EXAMPLE_NUM_1,
            EXAMPLE_STRING,
            UNCONSTRAINED_STRING,
            constrained_array.clone(),
        );

        // Now construct constraint_spec on the input dice, note this will use the keys
        // which are also hardcoded within the get_dice_chain_helper.

        let constraint_spec = vec![
            ConstraintSpec::new(
                ConstraintType::ExactMatch,
                vec![1],
                MissingAction::Fail,
                CertIndex::All,
            ),
            // Notice how key "2" is (deliberately) absent in ConstraintSpec
            // so policy should not constrain it.
            ConstraintSpec::new(
                ConstraintType::GreaterOrEqual,
                vec![3, 100],
                MissingAction::Fail,
                CertIndex::All,
            ),
            ConstraintSpec::new(
                ConstraintType::ExactMatch,
                vec![4, WILDCARD_FULL_ARRAY],
                MissingAction::Fail,
                CertIndex::FromEnd(0), // Constrain the last dice chain entry only!
            ),
        ];
        let expected_dice_policy = DicePolicy {
            version: 1,
            node_constraints_list: Box::new([
                NodeConstraints(Box::new([Constraint::new(
                    // 1st Node -> version
                    ConstraintType::ExactMatch as u16,
                    vec![],
                    Value::from(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION),
                )
                .unwrap()])),
                NodeConstraints(Box::new([Constraint::new(
                    // 2nd Node -> root key encoding
                    ConstraintType::ExactMatch as u16,
                    vec![],
                    rot_key.clone(),
                )
                .unwrap()])),
                NodeConstraints(Box::new([
                    // 3rd Node -> DiceChainEntry 0
                    Constraint::new(
                        ConstraintType::ExactMatch as u16,
                        vec![1],
                        Value::Text(EXAMPLE_STRING.to_string()),
                    )
                    .unwrap(),
                    Constraint::new(
                        ConstraintType::GreaterOrEqual as u16,
                        vec![3, 100],
                        Value::from(EXAMPLE_NUM_1),
                    )
                    .unwrap(),
                ])),
                NodeConstraints(Box::new([
                    // 4rd Node -> DiceChainEntry 1 (Also 0th from last)
                    Constraint::new(
                        ConstraintType::ExactMatch as u16,
                        vec![1],
                        Value::Text(EXAMPLE_STRING.to_string()),
                    )
                    .unwrap(),
                    Constraint::new(
                        ConstraintType::GreaterOrEqual as u16,
                        vec![3, 100],
                        Value::from(EXAMPLE_NUM_1),
                    )
                    .unwrap(),
                    Constraint::new(
                        ConstraintType::ExactMatch as u16,
                        vec![4, 0],
                        Value::from(CONSTRAINED_ARRAY[0]),
                    )
                    .unwrap(),
                    Constraint::new(
                        ConstraintType::ExactMatch as u16,
                        vec![4, 1],
                        Value::from(CONSTRAINED_ARRAY[1]),
                    )
                    .unwrap(),
                    Constraint::new(
                        ConstraintType::ExactMatch as u16,
                        vec![4, 2],
                        Value::from(CONSTRAINED_ARRAY[2]),
                    )
                    .unwrap(),
                    Constraint::new(
                        ConstraintType::ExactMatch as u16,
                        vec![4, 3],
                        Value::from(CONSTRAINED_ARRAY[3]),
                    )
                    .unwrap(),
                ])),
            ]),
        };

        let updated_input_dice = Self::get_dice_chain_helper(
            rot_key,
            EXAMPLE_NUM_2,
            EXAMPLE_STRING,
            ANOTHER_UNCONSTRAINED_STRING,
            constrained_array,
        );
        Self { input_dice, constraint_spec, expected_dice_policy, updated_input_dice }
    }

    // Helper method method to generate a dice chain with a given rot_key.
    // Other arguments are ad-hoc values in the nested map. Callers use these to
    // construct appropriate constrains in dice policies.
    fn get_dice_chain_helper(
        rot_key: Value,
        version: i64,
        constrained_string: &str,
        unconstrained_string: &str,
        constrained_array: Value,
    ) -> Vec<u8> {
        let nested_payload = cbor!({
            100 => version
        })
        .unwrap();

        let payload0 = cbor!({
            1 => constrained_string,
            2 => unconstrained_string,
            3 => Value::Bytes(nested_payload.clone().to_vec().unwrap()),
        })
        .unwrap();
        let payload0 = payload0.clone().to_vec().unwrap();
        let dice_chain_entry0 = CoseSign1 {
            protected: ProtectedHeader::default(),
            unprotected: Header::default(),
            payload: Some(payload0),
            signature: b"ddef".to_vec(),
        }
        .to_cbor_value()
        .unwrap();

        let payload1 = cbor!({
            1 => constrained_string,
            2 => unconstrained_string,
            3 => Value::Bytes(nested_payload.to_vec().unwrap()),
            4 => constrained_array
        })
        .unwrap();
        let payload1 = payload1.clone().to_vec().unwrap();
        let dice_chain_entry1 = CoseSign1 {
            protected: ProtectedHeader::default(),
            unprotected: Header::default(),
            payload: Some(payload1),
            signature: b"ddef".to_vec(),
        }
        .to_cbor_value()
        .unwrap();
        let input_dice = Value::Array(vec![
            Value::from(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION),
            rot_key,
            dice_chain_entry0,
            dice_chain_entry1,
        ]);

        input_dice.to_vec().unwrap()
    }
}

#[rdroidtest]
fn policy_structure_check() {
    let example = TestArtifacts::get_example();
    let policy = policy_for_dice_chain(&example.input_dice, &example.constraint_spec).unwrap();

    // Assert policy is exactly as expected!
    assert_eq!(policy, example.expected_dice_policy);
}

#[rdroidtest]
fn policy_enc_dec_test() {
    // An example dice policy
    let dice_policy: DicePolicy = TestArtifacts::get_example().expected_dice_policy;
    // Encode & then decode back!
    let dice_policy_enc_dec =
        DicePolicy::from_slice(&dice_policy.clone().to_vec().unwrap()).unwrap();
    assert_eq!(dice_policy, dice_policy_enc_dec);
}

#[rdroidtest]
fn policy_matches_original_dice_chain() {
    let example = TestArtifacts::get_example();
    policy_for_dice_chain(&example.input_dice, &example.constraint_spec)
        .unwrap()
        .matches_dice_chain(&example.input_dice)
        .unwrap();
}

#[rdroidtest]
fn policy_matches_updated_dice_chain() {
    let example = TestArtifacts::get_example();
    policy_for_dice_chain(&example.input_dice, &example.constraint_spec)
        .unwrap()
        .matches_dice_chain(&example.updated_input_dice)
        .unwrap();
}

#[rdroidtest]
fn policy_mismatch_downgraded_dice_chain() {
    let example = TestArtifacts::get_example();
    assert!(
        policy_for_dice_chain(&example.updated_input_dice, &example.constraint_spec)
            .unwrap()
            .matches_dice_chain(&example.input_dice)
            .is_err(),
        "The (downgraded) dice chain matched the policy constructed out of the 'updated'\
            dice chain!!"
    );
}

#[rdroidtest]
fn policy_constructional_required_constraint() {
    let mut example = TestArtifacts::get_example();
    // The constraint_path [9,8,7] is not present in example.input_dice
    example.constraint_spec.push(ConstraintSpec::new(
        ConstraintType::ExactMatch,
        vec![9, 8, 7],
        MissingAction::Fail,
        CertIndex::All,
    ));
    assert!(
        policy_for_dice_chain(&example.input_dice, &example.constraint_spec).is_err(),
        "DicePolicy creation should've failed on Required constraint"
    );
}

#[rdroidtest]
fn policy_constructional_optional_constraint() {
    let mut example = TestArtifacts::get_example();
    // The constraint_path [9,8,7] is not present in example.input_dice
    example.constraint_spec.push(ConstraintSpec::new(
        ConstraintType::ExactMatch,
        vec![9, 8, 7],
        MissingAction::Ignore,
        CertIndex::All,
    ));
    // However for optional constraint, policy construction should succeed!
    _ = policy_for_dice_chain(&example.input_dice, &example.constraint_spec).unwrap()
}

#[rdroidtest]
fn policy_dice_size_is_same() {
    // This is the number of certs in compos bcc (including the first ROT)
    // To analyze a bcc use hwtrust tool from /tools/security/remote_provisioning/hwtrust
    // `hwtrust --verbose dice-chain [path]/composbcc`
    const COMPOS_CHAIN_SIZE_EXPLICIT_KEY: usize = 6;
    let input_dice = include_bytes!("./testdata/compos_chain_explicit");
    let constraint_spec = [
        ConstraintSpec::new(
            ConstraintType::ExactMatch,
            vec![AUTHORITY_HASH],
            MissingAction::Fail,
            CertIndex::All,
        ),
        ConstraintSpec::new(
            ConstraintType::ExactMatch,
            vec![KEY_MODE],
            MissingAction::Fail,
            CertIndex::All,
        ),
        ConstraintSpec::new(
            ConstraintType::ExactMatch,
            vec![CONFIG_DESC, COMPONENT_NAME],
            MissingAction::Fail,
            CertIndex::All,
        ),
        ConstraintSpec::new(
            ConstraintType::GreaterOrEqual,
            vec![CONFIG_DESC, COMPONENT_VERSION],
            MissingAction::Ignore,
            CertIndex::All,
        ),
        ConstraintSpec::new(
            ConstraintType::GreaterOrEqual,
            vec![CONFIG_DESC, SECURITY_VERSION],
            MissingAction::Ignore,
            CertIndex::All,
        ),
    ];
    let policy = policy_for_dice_chain(input_dice, &constraint_spec).unwrap();
    assert_eq!(policy.node_constraints_list.len(), COMPOS_CHAIN_SIZE_EXPLICIT_KEY);
    // Check that original dice chain matches the policy.
    policy.matches_dice_chain(input_dice).unwrap();
}

#[rdroidtest]
fn policy_matcher_builder_in_sync() {
    // DicePolicy builder & matcher are closely coupled.
    assert_eq!(
        dice_policy::DICE_POLICY_VERSION,
        dice_policy_builder::SUPPORTED_DICE_POLICY_VERSION
    );
}
#[rdroidtest]
fn lookup_in_nested_container_test() {
    const TARGET: &str = "TARGET";
    let nested_container = cbor!({ // MAP
        1 => 5,
        2 => cbor!([                // ARRAY
            "Index0",
            "Index1",
            cbor!({                 // Map
                1 => 5,
                2 => cbor!([        // ARRAY
                    "Index0",
                    "Index1",
                    TARGET,
                ]).unwrap(),
            }).unwrap(),
        ]).unwrap(),
    })
    .unwrap();
    let path_present = vec![2, 2, 2, 2];
    let path_missing1 = vec![2, 2, 2, 3];
    let path_missing2 = vec![2, 2, 3];
    assert_eq!(
        lookup_in_nested_container(&nested_container, &path_present).unwrap(),
        Some(Value::from(TARGET))
    );
    assert_eq!(lookup_in_nested_container(&nested_container, &path_missing1).unwrap(), None);
    assert_eq!(lookup_in_nested_container(&nested_container, &path_missing2).unwrap(), None);
}

rdroidtest::test_main!();
