// Copyright 2024, The Android Open Source Project
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

//! This library provides APIs for receiving, processing and replying to fastboot commands. To use
//! the library:
//!
//! 1. Provide a transport backend by implementing the `Transport` trait.
//!
//! ```
//!
//! struct FastbootTransport {}
//!
//! impl Transport<MyErrorType> for TestTransport {
//!     fn receive_packet(&mut self, out: &mut [u8]) -> Result<usize, TransportError> {
//!         todo!();
//!     }
//!
//!     fn send_packet(&mut self, packet: &[u8]) -> Result<(), TransportError> {
//!         todo!();
//!     }
//! }
//! ```
//!
//! 2. Provide a fastboot command backend by implementing the `FastbootImplementation` trait.
//!    i.e.
//!
//! ```
//!
//! struct FastbootCommand {}
//!
//! impl FastbootImplementation for FastbootTest {
//!     fn get_var(
//!         &mut self,
//!         var: &str,
//!         args: Split<char>,
//!         out: &mut [u8],
//!     ) -> Result<usize, CommandError> {
//!         todo!();
//!     }
//!
//!     ...
//! }
//!```
//!
//! 3. Construct a `Fastboot` object with a given download buffer. Pass the transport, command
//!    implementation and call the `run()` method:
//!
//! ```
//! let mut fastboot_impl: FastbootCommand = ...;
//! let mut transport: TestTransport = ...;
//! let download_buffer: &mut [u8] = ...;
//! let mut fastboot = Fastboot::new(&mut download_buffer[..]);
//! let result = fastboot.run(&mut transport, &mut fastboot_impl, &[]);
//! ```

#![cfg_attr(not(test), no_std)]

use core::fmt::{Display, Write};
use core::str::Split;

const MAX_COMMAND_SIZE: usize = 4096;
const MAX_RESPONSE_SIZE: usize = 256;
const OKAY: &'static str = "OKAY";

/// Transport errors.
pub enum TransportError {
    Others(&'static str),
}

/// Implementation for Fastboot transport interfaces.
pub trait Transport {
    /// Fetches the next fastboot packet into `out`.
    ///
    /// Returns the actual size of the packet on success.
    ///
    /// TODO(b/322540167): In the future, we may want to support using `[MaybeUninit<u8>]` as the
    /// download buffer to avoid expensive initialization at the beginning. This would require an
    /// interface where the implementation provides the buffer for us to copy instead of us.
    fn receive_packet(&mut self, out: &mut [u8]) -> Result<usize, TransportError>;

    /// Sends a fastboot packet.
    ///
    /// The method assumes `packet` is sent or at least copied to queue after it returns, where
    /// the buffer can go out of scope without affecting anything.
    fn send_packet(&mut self, packet: &[u8]) -> Result<(), TransportError>;
}

const COMMAND_ERROR_LENGTH: usize = MAX_RESPONSE_SIZE - 4;

/// `CommandError` is the return error type for methods in trait `FastbootImplementation` when
/// they fail. It will be converted into string and sent as fastboot error message "FAIL<string>".
///
/// Any type that implements `Display` trait can be converted into it. However, because fastboot
/// response message is limited to `MAX_RESPONSE_SIZE`. If the final displayed string length
/// exceeds it, the rest of the content is ignored.
#[derive(Debug)]
pub struct CommandError(FormattedBytes<[u8; COMMAND_ERROR_LENGTH]>);

impl CommandError {
    /// Converts to string.
    fn to_str(&self) -> &str {
        core::str::from_utf8(&self.0 .0[..self.0 .1]).unwrap_or("")
    }
}

impl<T: Display> From<T> for CommandError {
    fn from(val: T) -> Self {
        let mut res = CommandError(FormattedBytes([0u8; COMMAND_ERROR_LENGTH], 0));
        write!(res.0, "{}", val).unwrap();
        res
    }
}

/// Implementation for Fastboot command backends.
pub trait FastbootImplementation {
    /// Backend for `fastboot getvar ...`
    ///
    /// Gets the value of a variable specified by name and configuration represented by list of
    /// additional arguments in `args`.
    ///
    /// Variable `max-download-size`, `version` are reserved by the library.
    /// TODO(b/322540167): Figure out other reserved variables.
    fn get_var(
        &mut self,
        var: &str,
        args: Split<char>,
        out: &mut [u8],
    ) -> Result<usize, CommandError>;

    /// Backend for `fastboot getvar all`.
    ///
    /// Iterates all combinations of fastboot variable, configurations and values that need to be
    /// included in the response to `fastboot getvar all`.
    ///
    /// # Args
    ///
    ///   * `f`: A closure that takes 3 arguments: 1. variable name, 2. an array of string
    ///     arguments and 3. the corresponding variable value. Implementation should call this for
    ///     all combinations that need to be returned for `fastboot getvar all`. For example the
    ///     following implementation
    ///
    ///     fn get_var_all<F>(&mut self, f: F) -> Result<(), CommandError> {
    ///         f("partition-size", &["boot_a"], /* size string of boot_a */);
    ///         f("partition-size", &["boot_b"], /* size string of boot_b */);
    ///         f("partition-size", &["init_boot_a"], /* size string of init_boot_a */);
    ///         f("partition-size", &["init_boot_b"], /* size string of init_boot_b */);
    ///         Ok(())
    ///     }
    ///
    ///     will generates the following outputs for `fastboot getvar all`:
    ///
    ///    ...
    ///    (bootloader) partition-size:boot_a: <size of boot_a>
    ///    (bootloader) partition-size:boot_b: <size of boot_b>
    ///    (bootloader) partition-size:init_boot_a: <size of init_boot_a>
    ///    (bootloader) partition-size:init_boot_b: <size of init_boot_b>
    ///    ...
    ///
    /// TODO(b/322540167): This and `get_var()` contain duplicated logic. Investigate if there can
    /// be better solutions for doing the combination traversal.
    fn get_var_all<F>(&mut self, f: F) -> Result<(), CommandError>
    where
        F: FnMut(&str, &[&str], &str);

    // TODO(b/322540167): Add methods for other commands.
}

/// An internal convenient macro helper for `fastboot_okay` and `fastboot_fail`.
macro_rules! fastboot_msg {
    ( $arr:expr, $msg_type:expr, $( $x:expr ),* $(,)? ) => {
        {
            let mut formatted_bytes = FormattedBytes::new(&mut $arr[..]);
            write!(formatted_bytes, $msg_type).unwrap();
            write!(formatted_bytes, $($x,)*).unwrap();
            let size = formatted_bytes.size();
            &mut $arr[..size]
        }
    };
}

/// An internal convenient macro that constructs a formatted fastboot OKAY message.
macro_rules! fastboot_okay {
    ( $arr:expr, $( $x:expr ),* ) => { fastboot_msg!($arr, "OKAY", $($x,)*) };
}

/// An internal convenient macro that constructs a formatted fastboot FAIL message.
macro_rules! fastboot_fail {
    ( $arr:expr, $( $x:expr ),* ) => { fastboot_msg!($arr, "FAIL", $($x,)*) };
}

/// State of the fastboot protocol.
enum ProtocolState {
    Command,
    Download,
}

/// `Fastboot` provides methods for receiving/processing/replying fastboot commands from a
/// transport.
pub struct Fastboot<'a> {
    state: ProtocolState,
    download_buffer: &'a mut [u8],
    downloaded_size: usize,
    total_download_size: usize,
}

impl<'a> Fastboot<'a> {
    /// Creates an instance with a given download buffer.
    pub fn new(download_buffer: &'a mut [u8]) -> Self {
        Self {
            state: ProtocolState::Command,
            download_buffer: download_buffer,
            downloaded_size: 0,
            total_download_size: 0,
        }
    }

    /// Process fastboot command/data from a given transport.
    ///
    /// # Args
    ///
    ///   * `transport`: An implementation of `Transport`
    ///   * `fb_impl`: An implementation of `FastbootImplementation`.
    ///
    /// # Returns
    ///
    ///   The method returns on any errors from calls to `transport` methods.
    pub fn run(
        &mut self,
        transport: &mut impl Transport,
        fb_impl: &mut impl FastbootImplementation,
    ) -> Result<(), TransportError> {
        loop {
            match self.state {
                ProtocolState::Command => {
                    let mut packet = [0u8; MAX_COMMAND_SIZE];
                    let cmd_size = transport.receive_packet(&mut packet[..])?;
                    if cmd_size == 0 {
                        continue;
                    }

                    let mut res = [0u8; MAX_RESPONSE_SIZE];
                    let cmd = match core::str::from_utf8(&packet[..cmd_size]) {
                        Ok(s) => s,
                        _ => {
                            transport.send_packet(fastboot_fail!(res, "Invalid Command"))?;
                            continue;
                        }
                    };
                    let mut args = cmd.split(':');
                    let Some(cmd) = args.next() else {
                        transport.send_packet(fastboot_fail!(res, "No command"))?;
                        continue;
                    };
                    match cmd {
                        "getvar" => self.get_var(args, transport, fb_impl)?,
                        "download" => self.download(args, transport, fb_impl)?,
                        _ => {
                            transport.send_packet(fastboot_fail!(res, "Command not found"))?;
                        }
                    }
                }
                ProtocolState::Download => {
                    let (_, remains) = &mut self.download_buffer[..self.total_download_size]
                        .split_at_mut(self.downloaded_size);
                    match transport.receive_packet(remains) {
                        Ok(size) if size > remains.len() => {
                            let mut res = [0u8; MAX_RESPONSE_SIZE];
                            transport.send_packet(snprintf!(
                                res,
                                "FAILMore data received then expected"
                            ))?;
                            self.total_download_size = 0;
                            self.downloaded_size = 0;
                            self.state = ProtocolState::Command;
                        }
                        Ok(size) => {
                            self.downloaded_size = self.downloaded_size.checked_add(size).unwrap();
                            if self.downloaded_size == self.total_download_size {
                                self.state = ProtocolState::Command;
                                transport.send_packet(OKAY.as_bytes())?;
                            }
                        }
                        Err(e) => {
                            self.total_download_size = 0;
                            self.downloaded_size = 0;
                            return Err(e);
                        }
                    }
                }
            }
        }
    }

    /// Method for handling "fastboot getvar ..."
    fn get_var(
        &mut self,
        mut args: Split<char>,
        transport: &mut impl Transport,
        fb_impl: &mut impl FastbootImplementation,
    ) -> Result<(), TransportError> {
        let mut res = [0u8; MAX_RESPONSE_SIZE];
        let Some(var) = args.next() else {
            return transport.send_packet(fastboot_fail!(res, "Missing variable"));
        };

        if var == "all" {
            return self.get_var_all(transport, fb_impl);
        }
        let mut val = [0u8; MAX_RESPONSE_SIZE];
        match self.get_var_str(var, args, &mut val[..], fb_impl) {
            Ok(s) => transport.send_packet(fastboot_okay!(res, "{}", s)),
            Err(e) => transport.send_packet(fastboot_fail!(res, "{}", e.to_str())),
        }
    }

    /// A helper for getting the string version of a fastboot variable value.
    fn get_var_str<'s>(
        &mut self,
        var: &str,
        args: Split<char>,
        out: &'s mut [u8],
        fb_impl: &mut impl FastbootImplementation,
    ) -> Result<&'s str, CommandError> {
        if var == "max-download-size" {
            Ok(core::str::from_utf8(snprintf!(out, "{:#x}", self.download_buffer.len())).unwrap())
        } else {
            let size = fb_impl.get_var(var, args, out)?;
            Ok(core::str::from_utf8(out.get(..size).ok_or("Invalid variable size")?)
                .map_err(|_| "Value is not string")?)
        }
    }

    /// Method for handling "fastboot getvar all"
    fn get_var_all(
        &mut self,
        transport: &mut impl Transport,
        fb_impl: &mut impl FastbootImplementation,
    ) -> Result<(), TransportError> {
        let mut res = [0u8; MAX_RESPONSE_SIZE];
        let mut transport_error = Ok(());
        // A closure for constructing a string of format `INFO<var>:<args>: <value>`
        let mut process_var = |name: &str, args: &[&str], val: &str| {
            // If we run into transport errors in previous call, don't process.
            if transport_error.is_ok() {
                let mut formatted_bytes = FormattedBytes::new(&mut res);
                write!(formatted_bytes, "INFO{}", name).unwrap();
                args.iter().for_each(|arg| write!(formatted_bytes, ":{}", arg).unwrap());
                write!(formatted_bytes, ": {}", val).unwrap();
                let size = formatted_bytes.size();
                transport_error = transport.send_packet(&res[..size]);
            }
        };

        // Process the built-in "max-download-size" variable.
        let mut var_val = [0u8; MAX_RESPONSE_SIZE];
        let val = self
            .get_var_str(
                "max-download-size",
                "".split(':'), /* don't care */
                &mut var_val[..],
                fb_impl,
            )
            .unwrap();
        process_var("max-download-size", &[], val);
        match fb_impl.get_var_all(|name, args, val| process_var(name, args, val)) {
            Ok(()) => {
                transport_error?;
                transport.send_packet(fastboot_okay!(res, ""))
            }
            Err(e) => transport.send_packet(fastboot_fail!(res, "{}", e.to_str())),
        }
    }

    /// Method for handling "fastboot download:...".
    fn download(
        &mut self,
        args: Split<char>,
        transport: &mut impl Transport,
        _: &mut impl FastbootImplementation,
    ) -> Result<(), TransportError> {
        let mut res = [0u8; MAX_RESPONSE_SIZE];
        let (args, argc) = split_to_array::<1>(args);
        if argc != 1 {
            return transport.send_packet(fastboot_fail!(res, "Not enough argument"));
        }
        let size_str = args[0].strip_prefix("0x").unwrap_or(args[0]);
        let total_download_size = match usize::from_str_radix(size_str, 16) {
            Ok(size) => size,
            Err(e) => {
                return transport.send_packet(fastboot_fail!(
                    res,
                    "Invalid hex string {:?}",
                    e.kind()
                ));
            }
        };

        if total_download_size > self.download_buffer.len() {
            return transport.send_packet(fastboot_fail!(res, "Download size is too big"));
        } else if total_download_size == 0 {
            return transport.send_packet(fastboot_fail!(res, "Zero download size"));
        }

        transport.send_packet(snprintf!(res, "DATA{:#x}", total_download_size))?;
        self.total_download_size = total_download_size;
        self.downloaded_size = 0;
        self.state = ProtocolState::Download;
        Ok(())
    }
}

/// A helper data structure for writing formatted string to fixed size bytes array.
#[derive(Debug)]
pub struct FormattedBytes<T: AsMut<[u8]>>(T, usize);

impl<T: AsMut<[u8]>> FormattedBytes<T> {
    /// Create an instance.
    pub fn new(buf: T) -> Self {
        Self(buf, 0)
    }

    /// Get the size of content.
    pub fn size(&self) -> usize {
        self.1
    }

    pub fn append(&mut self, bytes: &[u8]) -> &mut [u8] {
        let buf = &mut self.0.as_mut()[self.1..];
        // Only write as much as the size of the bytes buffer. Additional write is silently
        // ignored.
        let to_write = core::cmp::min(buf.len(), bytes.len());
        buf[..to_write].clone_from_slice(&bytes[..to_write]);
        self.1 += to_write;
        &mut self.0.as_mut()[..self.1]
    }
}

impl<T: AsMut<[u8]>> core::fmt::Write for FormattedBytes<T> {
    fn write_str(&mut self, s: &str) -> core::fmt::Result {
        self.append(s.as_bytes());
        Ok(())
    }
}

/// A convenient macro that behaves similar to snprintf in C.
#[macro_export]
macro_rules! snprintf {
    ( $arr:expr, $( $x:expr ),* ) => {
        {
            let mut formatted_bytes = FormattedBytes::new(&mut $arr[..]);
            write!(formatted_bytes, $($x,)*).unwrap();
            let size = formatted_bytes.size();
            &mut $arr[..size]
        }
    };
}

/// A helper for converting a Split into an array.
///
/// Returns a tuple of array and actual number of arguments.
fn split_to_array<const MAX_ARGS: usize>(mut split: Split<char>) -> ([&str; MAX_ARGS], usize) {
    let mut args: [&str; MAX_ARGS] = [""; MAX_ARGS];
    let mut num = 0usize;
    args.iter_mut().zip(split.by_ref()).for_each(|(dst, src)| {
        *dst = src;
        num += 1;
    });
    (args, num)
}

#[cfg(test)]
mod test {
    use super::*;
    use std::collections::{BTreeMap, VecDeque};

    struct FastbootTest {
        // A mapping from (variable name, argument) to variable value.
        vars: BTreeMap<(&'static str, &'static [&'static str]), &'static str>,
    }

    impl FastbootImplementation for FastbootTest {
        fn get_var(
            &mut self,
            var: &str,
            args: Split<char>,
            out: &mut [u8],
        ) -> Result<usize, CommandError> {
            let args = args.collect::<Vec<_>>();
            match self.vars.get(&(var, &args[..])) {
                Some(v) => {
                    out[..v.len()].clone_from_slice(v.as_bytes());
                    Ok(v.len())
                }
                _ => Err("Not Found".into()),
            }
        }

        fn get_var_all<F>(&mut self, mut f: F) -> Result<(), CommandError>
        where
            F: FnMut(&str, &[&str], &str),
        {
            for ((var, config), value) in &self.vars {
                f(var, config, value);
            }
            Ok(())
        }
    }

    struct TestTransport {
        in_queue: VecDeque<Vec<u8>>,
        out_queue: VecDeque<Vec<u8>>,
    }

    impl TestTransport {
        fn new() -> Self {
            Self { in_queue: VecDeque::new(), out_queue: VecDeque::new() }
        }

        fn add_input(&mut self, packet: &[u8]) {
            self.in_queue.push_back(packet.into());
        }
    }

    impl Transport for TestTransport {
        fn receive_packet(&mut self, out: &mut [u8]) -> Result<usize, TransportError> {
            match self.in_queue.pop_front() {
                Some(v) => {
                    let size = core::cmp::min(out.len(), v.len());
                    out[..size].clone_from_slice(&v[..size]);
                    // Returns the input length so that we can test bogus download size.
                    Ok(v.len())
                }
                _ => Err(TransportError::Others("No more data")),
            }
        }

        fn send_packet(&mut self, packet: &[u8]) -> Result<(), TransportError> {
            self.out_queue.push_back(packet.into());
            Ok(())
        }
    }

    #[test]
    fn test_non_exist_command() {
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::new() };
        let mut download_buffer = vec![0u8; 1024];
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        transport.add_input(b"non_exist");
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(transport.out_queue, [b"FAILCommand not found"]);
    }

    #[test]
    fn test_non_ascii_command_string() {
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::new() };
        let mut download_buffer = vec![0u8; 1024];
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        transport.add_input(b"\xff\xff\xff");
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(transport.out_queue, [b"FAILInvalid Command"]);
    }

    #[test]
    fn test_get_var_max_download_size() {
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::new() };
        let mut download_buffer = vec![0u8; 1024];
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        transport.add_input(b"getvar:max-download-size");
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(transport.out_queue, [b"OKAY0x400"]);
    }

    #[test]
    fn test_get_var() {
        let vars: [((&str, &[&str]), &str); 4] = [
            (("var_0", &[]), "val_0"),
            (("var_1", &["a", "b"]), "val_1_a_b"),
            (("var_1", &["c", "d"]), "val_1_c_d"),
            (("var_2", &["e", "f"]), "val_2_e_f"),
        ];
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::from(vars) };

        let mut download_buffer = vec![0u8; 1024];
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        transport.add_input(b"getvar:var_0");
        transport.add_input(b"getvar:var_1:a:b");
        transport.add_input(b"getvar:var_1:c:d");
        transport.add_input(b"getvar:var_1"); // Not Found
        transport.add_input(b"getvar:var_2:e:f");
        transport.add_input(b"getvar:var_3"); // Not Found
        transport.add_input(b"getvar"); // Not Found

        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(
            transport.out_queue,
            VecDeque::<Vec<u8>>::from([
                b"OKAYval_0".into(),
                b"OKAYval_1_a_b".into(),
                b"OKAYval_1_c_d".into(),
                b"FAILNot Found".into(),
                b"OKAYval_2_e_f".into(),
                b"FAILNot Found".into(),
                b"FAILMissing variable".into(),
            ])
        );
    }

    #[test]
    fn test_get_var_all() {
        let vars: [((&str, &[&str]), &str); 4] = [
            (("var_0", &[]), "val_0"),
            (("var_1", &["a", "b"]), "val_1_a_b"),
            (("var_1", &["c", "d"]), "val_1_c_d"),
            (("var_2", &["e", "f"]), "val_2_e_f"),
        ];
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::from(vars) };

        let mut download_buffer = vec![0u8; 1024];
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        transport.add_input(b"getvar:all");
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(
            transport.out_queue,
            VecDeque::<Vec<u8>>::from([
                b"INFOmax-download-size: 0x400".into(),
                b"INFOvar_0: val_0".into(),
                b"INFOvar_1:a:b: val_1_a_b".into(),
                b"INFOvar_1:c:d: val_1_c_d".into(),
                b"INFOvar_2:e:f: val_2_e_f".into(),
                b"OKAY".into(),
            ])
        );
    }

    #[test]
    fn test_download() {
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::new() };
        let mut download_buffer = vec![0u8; 1024];
        let download_content: Vec<u8> =
            (0..download_buffer.len()).into_iter().map(|v| v as u8).collect();
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        // Splits download into two batches.
        let (first, second) = download_content.as_slice().split_at(download_content.len() / 2);
        transport.add_input(format!("download:{:#x}", download_content.len()).as_bytes());
        transport.add_input(first);
        transport.add_input(second);
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(
            transport.out_queue,
            VecDeque::<Vec<u8>>::from([b"DATA0x400".into(), b"OKAY".into(),])
        );
        assert_eq!(download_buffer, download_content);
    }

    #[test]
    fn test_download_not_enough_args() {
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::new() };
        let mut download_buffer = vec![0u8; 1024];
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        transport.add_input(b"download");
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(transport.out_queue, [b"FAILNot enough argument"]);
    }

    #[test]
    fn test_download_invalid_hex_string() {
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::new() };
        let mut download_buffer = vec![0u8; 1024];
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        transport.add_input(b"download:hhh");
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(transport.out_queue.len(), 1);
        assert!(transport.out_queue[0].starts_with(b"FAILInvalid hex string"));
    }

    fn test_download_size(download_buffer_size: usize, download_size: usize, msg: &str) {
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::new() };
        let mut download_buffer = vec![0u8; download_buffer_size];
        let mut transport = TestTransport::new();
        transport.add_input(format!("download:{:#x}", download_size).as_bytes());
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(transport.out_queue, VecDeque::<Vec<u8>>::from([msg.as_bytes().into()]));
    }

    #[test]
    fn test_download_download_size_too_big() {
        test_download_size(1024, 1025, "FAILDownload size is too big");
    }

    #[test]
    fn test_download_zero_download_size() {
        test_download_size(1024, 0, "FAILZero download size");
    }

    #[test]
    fn test_download_more_than_expected() {
        let mut fastboot_impl = FastbootTest { vars: BTreeMap::new() };
        let mut download_buffer = vec![0u8; 1024];
        let download_content: Vec<u8> = vec![0u8; download_buffer.len()];
        let mut fastboot = Fastboot::new(&mut download_buffer[..]);
        let mut transport = TestTransport::new();
        transport.add_input(format!("download:{:#x}", download_content.len() - 1).as_bytes());
        transport.add_input(&download_content[..]);
        // State should be reset to command state.
        transport.add_input(b"getvar:max-download-size");
        let _ = fastboot.run(&mut transport, &mut fastboot_impl);
        assert_eq!(
            transport.out_queue,
            VecDeque::<Vec<u8>>::from([
                b"DATA0x3ff".into(),
                b"FAILMore data received then expected".into(),
                b"OKAY0x400".into(),
            ])
        );
    }
}
