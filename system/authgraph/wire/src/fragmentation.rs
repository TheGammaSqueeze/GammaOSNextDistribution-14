// Copyright 2024 Google LLC
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

//! Helpers for message fragmentation and reassembly.

use alloc::borrow::Cow;
use alloc::vec::Vec;

/// Prefix byte indicating more data is to come.
const PREFIX_MORE_TO_COME: u8 = 0xcc; // 'ccontinues'
/// Prefix byte indicating that this is the final fragment.
const PREFIX_FINAL_FRAGMENT: u8 = 0xdd; // 'ddone'

/// Empty placeholder message indicating more data is due.
pub const PLACEHOLDER_MORE_TO_COME: &[u8] = &[PREFIX_MORE_TO_COME];

/// Helper to emit a single message in fragments.
pub struct Fragmenter<'a> {
    data: &'a [u8],
    max_size: usize,
}

impl<'a> Fragmenter<'a> {
    /// Create a fragmentation iterator for the given data.
    pub fn new(data: &'a [u8], max_size: usize) -> Fragmenter<'a> {
        assert!(max_size > 1);
        Self { data, max_size }
    }
}

impl<'a> Iterator for Fragmenter<'a> {
    type Item = Vec<u8>;
    fn next(&mut self) -> Option<Self::Item> {
        if self.data.is_empty() {
            None
        } else {
            let consume = core::cmp::min(self.max_size - 1, self.data.len());
            let marker =
                if consume < self.data.len() { PREFIX_MORE_TO_COME } else { PREFIX_FINAL_FRAGMENT };
            let mut result = Vec::with_capacity(consume + 1);
            result.push(marker);
            result.extend_from_slice(&self.data[..consume]);
            self.data = &self.data[consume..];
            Some(result)
        }
    }
}

/// Buffer to accumulate fragmented messages.
#[derive(Default)]
pub struct Reassembler(Vec<u8>);

impl Reassembler {
    /// Accumulate message data, possibly resulting in a complete message.
    pub fn accumulate<'a>(&mut self, frag: &'a [u8]) -> Option<Cow<'a, [u8]>> {
        let (more, content) = Self::split_msg(frag);
        if more {
            // More to come, so accumulate this data and return empty response.
            self.0.extend_from_slice(content);
            None
        } else if self.0.is_empty() {
            // For shorter messages (the mainline case) we can directly pass through the single
            // message's content.
            Some(Cow::Borrowed(content))
        } else {
            // Process the accumulated full request as an owned vector
            let mut full_req = core::mem::take(&mut self.0);
            full_req.extend_from_slice(content);
            Some(Cow::Owned(full_req))
        }
    }

    /// Split a message into an indication of whether more data is to come, and the content.
    ///
    /// # Panics
    ///
    /// This function panics if the provided message fragment has an unexpected message prefix.
    fn split_msg(data: &[u8]) -> (bool, &[u8]) {
        if data.is_empty() {
            (false, data)
        } else {
            match data[0] {
                PREFIX_MORE_TO_COME => (true, &data[1..]),
                PREFIX_FINAL_FRAGMENT => (false, &data[1..]),
                _ => panic!("data fragment with incorrect prefix"),
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::string::{String, ToString};
    use alloc::vec;
    use core::cell::RefCell;

    #[test]
    fn test_fragmentation() {
        let tests = [
            (
                "a0a1a2a3a4a5a6a7a8a9",
                2,
                vec![
                    "cca0", "cca1", "cca2", "cca3", "cca4", "cca5", "cca6", "cca7", "cca8", "dda9",
                ],
            ),
            ("a0a1a2a3a4a5a6a7a8a9", 5, vec!["cca0a1a2a3", "cca4a5a6a7", "dda8a9"]),
            ("a0a1a2a3a4a5a6a7a8a9", 9, vec!["cca0a1a2a3a4a5a6a7", "dda8a9"]),
            ("a0a1a2a3a4a5a6a7a8a9", 80, vec!["dda0a1a2a3a4a5a6a7a8a9"]),
        ];
        for (input, max_size, want) in &tests {
            let data = hex::decode(input).unwrap();
            let fragmenter = Fragmenter::new(&data, *max_size);
            let got: Vec<String> = fragmenter.map(hex::encode).collect();
            let want: Vec<String> = want.iter().map(|s| s.to_string()).collect();
            assert_eq!(got, want, "for input {input} max_size {max_size}");
        }
    }

    #[test]
    #[should_panic]
    fn test_reassembly_wrong_prefix() {
        // Failure case: unexpected marker byte
        let mut pending = Reassembler::default();
        let _ = pending.accumulate(&[0x00, 0x01, 0x02, 0x03]);
    }

    #[test]
    fn test_reassembly() {
        let tests = [
            // Single messages
            (vec!["dd"], ""),
            (vec!["dd0000"], "0000"),
            (vec!["dd010203"], "010203"),
            // Multipart messages.
            (vec!["cc0102", "dd0304"], "01020304"),
            (vec!["cc01", "cc02", "dd0304"], "01020304"),
            (vec!["cc", "cc02", "dd0304"], "020304"),
            // Failure case: empty message (no marker byte)
            (vec![], ""),
        ];
        for (frags, want) in &tests {
            let mut done = false;
            let mut pending = Reassembler::default();
            for frag in frags {
                assert!(!done, "left over fragments found");
                let frag = hex::decode(frag).unwrap();
                let result = pending.accumulate(&frag);
                if let Some(got) = result {
                    assert_eq!(&hex::encode(got), want, "for input {frags:?}");
                    done = true;
                }
            }
        }
    }

    #[test]
    fn test_fragmentation_reassembly() {
        let input = "a0a1a2a3a4a5a6a7a8a9b0b1b2b3b4b5b6b7b8b9c0c1c2c3c4c5c6c7c8c9";
        let data = hex::decode(input).unwrap();
        for max_size in 2..data.len() + 2 {
            let fragmenter = Fragmenter::new(&data, max_size);
            let mut done = false;
            let mut pending = Reassembler::default();
            for frag in fragmenter {
                assert!(!done, "left over fragments found");
                let result = pending.accumulate(&frag);
                if let Some(got) = result {
                    assert_eq!(&hex::encode(got), input, "for max_size {max_size}");
                    done = true;
                }
            }
            assert!(done);
        }
    }

    #[test]
    fn test_ta_fragmentation_wrapper() {
        // Simulate a `send()` standalone function for responses.
        let rsp_reassembler = RefCell::new(Reassembler::default());
        let full_rsp: RefCell<Option<Vec<u8>>> = RefCell::new(None);
        let send = |data: &[u8]| {
            if let Some(msg) = rsp_reassembler.borrow_mut().accumulate(data) {
                *full_rsp.borrow_mut() = Some(msg.to_vec());
            }
        };

        // Simulate a TA.
        struct Ta {
            pending_req: Reassembler,
            max_size: usize,
        }
        impl Ta {
            // Request fragment, response fragments emitted via `send`.
            fn process_fragment<S: Fn(&[u8])>(&mut self, req_frag: &[u8], send: S) {
                // Accumulate request fragments until able to feed complete request to `process`.
                if let Some(full_req) = self.pending_req.accumulate(req_frag) {
                    // Full request message is available, invoke the callback to get a full
                    // response.
                    let full_rsp = self.process(&full_req);
                    for rsp_frag in Fragmenter::new(&full_rsp, self.max_size) {
                        send(&rsp_frag);
                    }
                }
            }
            // Full request to full response.
            fn process(&self, req: &[u8]) -> Vec<u8> {
                // Simulate processing a request by echoing it back as a response.
                req.to_vec()
            }
        }

        let req = "a0a1a2a3a4a5a6a7a8a9b0b1b2b3b4b5b6b7b8b9c0c1c2c3c4c5c6c7c8c9";
        let req_data = hex::decode(req).unwrap();
        for max_size in 2..req_data.len() + 2 {
            let mut ta = Ta { pending_req: Default::default(), max_size };
            // Simulate multiple fragmented messages arriving at the TA.
            for _msg_idx in 0..3 {
                // Reset the received-response buffer.
                *rsp_reassembler.borrow_mut() = Reassembler::default();
                *full_rsp.borrow_mut() = None;

                for req_frag in Fragmenter::new(&req_data, max_size) {
                    assert!(full_rsp.borrow().is_none(), "left over fragments found");
                    ta.process_fragment(&req_frag, send);
                }
                // After all the request fragments have been sent in, expect to have a complete
                // response.
                if let Some(rsp) = full_rsp.borrow().as_ref() {
                    assert_eq!(hex::encode(rsp), req);
                } else {
                    panic!("no response received");
                }
            }
        }
    }
}
