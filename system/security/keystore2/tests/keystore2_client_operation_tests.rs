// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use nix::unistd::{getuid, Gid, Uid};
use rustutils::users::AID_USER_OFFSET;
use std::thread;
use std::thread::JoinHandle;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Digest::Digest, ErrorCode::ErrorCode, KeyPurpose::KeyPurpose, SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    CreateOperationResponse::CreateOperationResponse, Domain::Domain,
    IKeystoreOperation::IKeystoreOperation, ResponseCode::ResponseCode,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error, run_as,
};

use crate::keystore2_client_test_utils::{
    create_signing_operation, execute_op_run_as_child, perform_sample_sign_operation,
    BarrierReached, ForcedOp, TestOutcome,
};

/// Create `max_ops` number child processes with the given context and perform an operation under each
/// child process.
///
/// # Safety
///
/// Must be called from a process with no other threads.
pub unsafe fn create_operations(
    target_ctx: &'static str,
    forced_op: ForcedOp,
    max_ops: i32,
) -> Vec<run_as::ChildHandle<TestOutcome, BarrierReached>> {
    let alias = format!("ks_op_test_key_{}", getuid());
    let base_gid = 99 * AID_USER_OFFSET + 10001;
    let base_uid = 99 * AID_USER_OFFSET + 10001;
    (0..max_ops)
        // SAFETY: The caller guarantees that there are no other threads.
        .map(|i| unsafe {
            execute_op_run_as_child(
                target_ctx,
                Domain::APP,
                key_generations::SELINUX_SHELL_NAMESPACE,
                Some(alias.to_string()),
                Uid::from_raw(base_uid + (i as u32)),
                Gid::from_raw(base_gid + (i as u32)),
                forced_op,
            )
        })
        .collect()
}

/// Executes an operation in a thread. Expect an `OPERATION_BUSY` error in case of operation
/// failure. Returns True if `OPERATION_BUSY` error is encountered otherwise returns false.
fn perform_op_busy_in_thread(op: binder::Strong<dyn IKeystoreOperation>) -> JoinHandle<bool> {
    thread::spawn(move || {
        for _n in 1..1000 {
            match key_generations::map_ks_error(op.update(b"my message")) {
                Ok(_) => continue,
                Err(e) => {
                    assert_eq!(Error::Rc(ResponseCode::OPERATION_BUSY), e);
                    return true;
                }
            }
        }
        let sig = op.finish(None, None).unwrap();
        assert!(sig.is_some());
        false
    })
}

/// This test verifies that backend service throws BACKEND_BUSY error when all
/// operations slots are full. This test creates operations in child processes and
/// collects the status of operations performed in each child proc and determines
/// whether any child proc exited with error status.
#[test]
fn keystore2_backend_busy_test() {
    const MAX_OPS: i32 = 100;
    static TARGET_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";

    // SAFETY: The test is run in a separate process with no other threads.
    let mut child_handles = unsafe { create_operations(TARGET_CTX, ForcedOp(false), MAX_OPS) };

    // Wait until all child procs notifies us to continue,
    // so that there are definitely enough operations outstanding to trigger a BACKEND_BUSY.
    for ch in child_handles.iter_mut() {
        ch.recv();
    }
    // Notify each child to resume and finish.
    for ch in child_handles.iter_mut() {
        ch.send(&BarrierReached {});
    }

    // Collect the result and validate whether backend busy has occurred.
    let mut busy_count = 0;
    for ch in child_handles.into_iter() {
        if ch.get_result() == TestOutcome::BackendBusy {
            busy_count += 1;
        }
    }
    assert!(busy_count > 0)
}

/// This test confirms that forced operation is having high pruning power.
/// 1. Initially create regular operations such that there are enough operations outstanding
///    to trigger BACKEND_BUSY.
/// 2. Then, create a forced operation. System should be able to prune one of the regular
///    operations and create a slot for forced operation successfully.
#[test]
fn keystore2_forced_op_after_backendbusy_test() {
    const MAX_OPS: i32 = 100;
    static TARGET_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";

    // Create regular operations.
    // SAFETY: The test is run in a separate process with no other threads.
    let mut child_handles = unsafe { create_operations(TARGET_CTX, ForcedOp(false), MAX_OPS) };

    // Wait until all child procs notifies us to continue, so that there are enough
    // operations outstanding to trigger a BACKEND_BUSY.
    for ch in child_handles.iter_mut() {
        ch.recv();
    }

    // Create a forced operation.
    let auid = 99 * AID_USER_OFFSET + 10604;
    let agid = 99 * AID_USER_OFFSET + 10604;
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            key_generations::TARGET_VOLD_CTX,
            Uid::from_raw(auid),
            Gid::from_raw(agid),
            move || {
                let alias = format!("ks_prune_forced_op_key_{}", getuid());

                // To make room for this forced op, system should be able to prune one of the
                // above created regular operations and create a slot for this forced operation
                // successfully.
                create_signing_operation(
                    ForcedOp(true),
                    KeyPurpose::SIGN,
                    Digest::SHA_2_256,
                    Domain::SELINUX,
                    100,
                    Some(alias),
                )
                .expect("Client failed to create forced operation after BACKEND_BUSY state.");
            },
        );
    };

    // Notify each child to resume and finish.
    for ch in child_handles.iter_mut() {
        ch.send(&BarrierReached {});
    }

    // Collect the results of above created regular operations.
    let mut pruned_count = 0;
    let mut busy_count = 0;
    let mut _other_err = 0;
    for ch in child_handles.into_iter() {
        match ch.get_result() {
            TestOutcome::BackendBusy => {
                busy_count += 1;
            }
            TestOutcome::InvalidHandle => {
                pruned_count += 1;
            }
            _ => {
                _other_err += 1;
            }
        }
    }
    // Verify that there should be at least one backend busy has occurred while creating
    // above regular operations.
    assert!(busy_count > 0);

    // Verify that there should be at least one pruned operation which should have failed while
    // performing operation.
    assert!(pruned_count > 0);
}

/// This test confirms that forced operations can't be pruned.
///  1. Creates an initial forced operation and tries to complete the operation after BACKEND_BUSY
///     error is triggered.
///  2. Create MAX_OPS number of forced operations so that definitely enough number of operations
///     outstanding to trigger a BACKEND_BUSY.
///  3. Try to use initially created forced operation (in step #1) and able to perform the
///     operation successfully. This confirms that none of the later forced operations evicted the
///     initial forced operation.
#[test]
fn keystore2_max_forced_ops_test() {
    const MAX_OPS: i32 = 100;
    let auid = 99 * AID_USER_OFFSET + 10205;
    let agid = 99 * AID_USER_OFFSET + 10205;

    // Create initial forced operation in a child process
    // and wait for the parent to notify to perform operation.
    let alias = format!("ks_forced_op_key_{}", getuid());
    // SAFETY: The test is run in a separate process with no other threads.
    let mut first_op_handle = unsafe {
        execute_op_run_as_child(
            key_generations::TARGET_SU_CTX,
            Domain::SELINUX,
            key_generations::SELINUX_SHELL_NAMESPACE,
            Some(alias),
            Uid::from_raw(auid),
            Gid::from_raw(agid),
            ForcedOp(true),
        )
    };

    // Wait until above child proc notifies us to continue, so that there is definitely a forced
    // operation outstanding to perform a operation.
    first_op_handle.recv();

    // Create MAX_OPS number of forced operations.
    let mut child_handles =
    // SAFETY: The test is run in a separate process with no other threads.
        unsafe { create_operations(key_generations::TARGET_SU_CTX, ForcedOp(true), MAX_OPS) };

    // Wait until all child procs notifies us to continue, so that  there are enough operations
    // outstanding to trigger a BACKEND_BUSY.
    for ch in child_handles.iter_mut() {
        ch.recv();
    }

    // Notify initial created forced operation to continue performing the operations.
    first_op_handle.send(&BarrierReached {});

    // Collect initially created forced operation result and is expected to complete operation
    // successfully.
    let first_op_result = first_op_handle.get_result();
    assert_eq!(first_op_result, TestOutcome::Ok);

    // Notify each child to resume and finish.
    for ch in child_handles.iter_mut() {
        ch.send(&BarrierReached {});
    }

    // Collect the result and validate whether backend busy has occurred with MAX_OPS number
    // of forced operations.
    let busy_count = child_handles
        .into_iter()
        .map(|ch| ch.get_result())
        .filter(|r| *r == TestOutcome::BackendBusy)
        .count();
    assert!(busy_count > 0);
}

/// This test will verify the use case with the same owner(UID) requesting `n` number of operations.
/// This test confirms that when all operation slots are full and a new operation is requested,
/// an operation which is least recently used and lived longest will be pruned to make a room
/// for a new operation. Pruning strategy should prevent the operations of the other owners(UID)
/// from being pruned.
///
/// 1. Create an operation in a child process with `untrusted_app` context and wait for parent
///    notification to complete the operation.
/// 2. Let parent process create `n` number of operations such that there are enough operations
///    outstanding to trigger cannibalizing their own sibling operations.
/// 3. Sequentially try to use above created `n` number of operations and also add a new operation,
///    so that it should trigger cannibalizing one of their own sibling operations.
///    3.1 While trying to use these pruned operations an `INVALID_OPERATION_HANDLE` error is
///        expected as they are already pruned.
/// 4. Notify the child process to resume and complete the operation. It is expected to complete the
///    operation successfully.
/// 5. Try to use the latest operation of parent. It is expected to complete the operation
///    successfully.
#[test]
fn keystore2_ops_prune_test() {
    const MAX_OPS: usize = 40; // This should be at least 32 with sec_level TEE.

    static TARGET_CTX: &str = "u:r:untrusted_app:s0";
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10601;

    let uid = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    let gid = USER_ID * AID_USER_OFFSET + APPLICATION_ID;

    // Create an operation in an untrusted_app context. Wait until the parent notifies to continue.
    // Once the parent notifies, this operation is expected to be completed successfully.
    let alias = format!("ks_reg_op_key_{}", getuid());
    // SAFETY: The test is run in a separate process with no other threads.
    let mut child_handle = unsafe {
        execute_op_run_as_child(
            TARGET_CTX,
            Domain::APP,
            -1,
            Some(alias),
            Uid::from_raw(uid),
            Gid::from_raw(gid),
            ForcedOp(false),
        )
    };

    // Wait until child process notifies us to continue, so that an operation from child process is
    // outstanding to complete the operation.
    child_handle.recv();

    // Generate a key to use in below operations.
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = format!("ks_prune_op_test_key_{}", getuid());
    let key_metadata = key_generations::generate_ec_p256_signing_key(
        &sec_level,
        Domain::SELINUX,
        key_generations::SELINUX_SHELL_NAMESPACE,
        Some(alias),
        None,
    )
    .unwrap();

    // Create multiple operations in this process to trigger cannibalizing sibling operations.
    let mut ops: Vec<binder::Result<CreateOperationResponse>> = (0..MAX_OPS)
        .map(|_| {
            sec_level.createOperation(
                &key_metadata.key,
                &authorizations::AuthSetBuilder::new()
                    .purpose(KeyPurpose::SIGN)
                    .digest(Digest::SHA_2_256),
                false,
            )
        })
        .collect();

    // Sequentially try to use operation handles created above and also add a new operation.
    for vec_index in 0..MAX_OPS {
        match &ops[vec_index] {
            Ok(CreateOperationResponse { iOperation: Some(op), .. }) => {
                // Older operation handle is pruned, if we try to use that an error is expected.
                assert_eq!(
                    Err(Error::Km(ErrorCode::INVALID_OPERATION_HANDLE)),
                    key_generations::map_ks_error(op.update(b"my message"))
                );
            }
            _ => panic!("Operation should have created successfully."),
        }

        // Create a new operation, it should trigger to cannibalize one of their own sibling
        // operations.
        ops.push(
            sec_level.createOperation(
                &key_metadata.key,
                &authorizations::AuthSetBuilder::new()
                    .purpose(KeyPurpose::SIGN)
                    .digest(Digest::SHA_2_256),
                false,
            ),
        );
    }

    // Notify child process to continue the operation.
    child_handle.send(&BarrierReached {});
    assert!((child_handle.get_result() == TestOutcome::Ok), "Failed to perform an operation");

    // Try to use the latest operation created by parent, should be able to use it successfully.
    match ops.last() {
        Some(Ok(CreateOperationResponse { iOperation: Some(op), .. })) => {
            assert_eq!(Ok(()), key_generations::map_ks_error(perform_sample_sign_operation(op)));
        }
        _ => panic!("Operation should have created successfully."),
    }
}

/// Try to create forced operations with various contexts -
///   - untrusted_app
///   - system_server
///   - priv_app
/// `PERMISSION_DENIED` error response is expected.
#[test]
fn keystore2_forced_op_perm_denied_test() {
    static TARGET_CTXS: &[&str] =
        &["u:r:untrusted_app:s0", "u:r:system_server:s0", "u:r:priv_app:s0"];
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10601;

    let uid = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    let gid = USER_ID * AID_USER_OFFSET + APPLICATION_ID;

    for context in TARGET_CTXS.iter() {
        // SAFETY: The test is run in a separate process with no other threads.
        unsafe {
            run_as::run_as(context, Uid::from_raw(uid), Gid::from_raw(gid), move || {
                let alias = format!("ks_app_forced_op_test_key_{}", getuid());
                let result = key_generations::map_ks_error(create_signing_operation(
                    ForcedOp(true),
                    KeyPurpose::SIGN,
                    Digest::SHA_2_256,
                    Domain::APP,
                    -1,
                    Some(alias),
                ));
                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::PERMISSION_DENIED), result.unwrap_err());
            });
        }
    }
}

/// Try to create a forced operation with `vold` context.
/// Should be able to create forced operation with `vold` context successfully.
#[test]
fn keystore2_forced_op_success_test() {
    static TARGET_CTX: &str = "u:r:vold:s0";
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10601;

    let uid = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    let gid = USER_ID * AID_USER_OFFSET + APPLICATION_ID;

    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(TARGET_CTX, Uid::from_raw(uid), Gid::from_raw(gid), move || {
            let alias = format!("ks_vold_forced_op_key_{}", getuid());
            create_signing_operation(
                ForcedOp(true),
                KeyPurpose::SIGN,
                Digest::SHA_2_256,
                Domain::SELINUX,
                key_generations::SELINUX_VOLD_NAMESPACE,
                Some(alias),
            )
            .expect("Client with vold context failed to create forced operation.");
        });
    }
}

/// Create an operation and try to use this operation handle in multiple threads to perform
/// operations. Test should fail to perform an operation with an error response `OPERATION_BUSY`
/// when multiple threads try to access the operation handle at same time.
#[test]
fn keystore2_op_fails_operation_busy() {
    let op_response = create_signing_operation(
        ForcedOp(false),
        KeyPurpose::SIGN,
        Digest::SHA_2_256,
        Domain::APP,
        -1,
        Some("op_busy_alias_test_key".to_string()),
    )
    .unwrap();

    let op: binder::Strong<dyn IKeystoreOperation> = op_response.iOperation.unwrap();

    let th_handle_1 = perform_op_busy_in_thread(op.clone());
    let th_handle_2 = perform_op_busy_in_thread(op);

    let result1 = th_handle_1.join().unwrap();
    let result2 = th_handle_2.join().unwrap();

    assert!(result1 || result2);
}
