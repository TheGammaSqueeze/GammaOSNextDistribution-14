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

#[cfg(test)]
mod tests {
    use tipc::{Handle, TipcError};
    use trusty_std::ffi::{CString, FallibleCString};

    test::init!();

    #[test]
    fn test_access_policy_unauthorized() {
        if !cfg!(kmr_enabled) {
            test::skip!("KeyMint Rust TA not configured");
        }

        /// Port that handles secure world messages
        const KM_SEC_TIPC_SRV_PORT: &str = "com.android.trusty.keymaster.secure";

        let port2 = CString::try_new(KM_SEC_TIPC_SRV_PORT).unwrap();
        let err1 = Handle::connect(port2.as_c_str()).expect_err(
            "An error is expected because the uuid of this test app is
                          not in the allowed uuid list of the keymint access policy.",
        );
        assert_eq!(err1, TipcError::SystemError(trusty_sys::Error::ChannelClosed));
    }
}
