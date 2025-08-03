// Copyright 2021, The Android Open Source Project
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

//! This module is intended for testing access control enforcement of services such as keystore2,
//! by assuming various identities with varying levels of privilege. Consequently, appropriate
//! privileges are required, or the attempt will fail causing a panic.
//! The `run_as` module provides the function `run_as`, which takes a UID, GID, an SELinux
//! context, and a closure. The return type of the closure, which is also the return type of
//! `run_as`, must implement `serde::Serialize` and `serde::Deserialize`.
//! `run_as` forks, transitions to the given identity, and executes the closure in the newly
//! forked process. If the closure returns, i.e., does not panic, the forked process exits with
//! a status of `0`, and the return value is serialized and sent through a pipe to the parent where
//! it gets deserialized and returned. The STDIO is not changed and the parent's panic handler
//! remains unchanged. So if the closure panics, the panic message is printed on the parent's STDERR
//! and the exit status is set to a non `0` value. The latter causes the parent to panic as well,
//! and if run in a test context, the test to fail.

use keystore2_selinux as selinux;
use nix::sys::wait::{waitpid, WaitStatus};
use nix::unistd::{
    close, fork, pipe as nix_pipe, read as nix_read, setgid, setuid, write as nix_write,
    ForkResult, Gid, Pid, Uid,
};
use serde::{de::DeserializeOwned, Serialize};
use std::io::{Read, Write};
use std::marker::PhantomData;
use std::os::unix::io::RawFd;

fn transition(se_context: selinux::Context, uid: Uid, gid: Gid) {
    setgid(gid).expect("Failed to set GID. This test might need more privileges.");
    setuid(uid).expect("Failed to set UID. This test might need more privileges.");

    selinux::setcon(&se_context)
        .expect("Failed to set SELinux context. This test might need more privileges.");
}

/// PipeReader is a simple wrapper around raw pipe file descriptors.
/// It takes ownership of the file descriptor and closes it on drop. It provides `read_all`, which
/// reads from the pipe into an expending vector, until no more data can be read.
struct PipeReader(RawFd);

impl Read for PipeReader {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let bytes = nix_read(self.0, buf)?;
        Ok(bytes)
    }
}

impl Drop for PipeReader {
    fn drop(&mut self) {
        close(self.0).expect("Failed to close reader pipe fd.");
    }
}

/// PipeWriter is a simple wrapper around raw pipe file descriptors.
/// It takes ownership of the file descriptor and closes it on drop. It provides `write`, which
/// writes the given buffer into the pipe, returning the number of bytes written.
struct PipeWriter(RawFd);

impl Drop for PipeWriter {
    fn drop(&mut self) {
        close(self.0).expect("Failed to close writer pipe fd.");
    }
}

impl Write for PipeWriter {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        let written = nix_write(self.0, buf)?;
        Ok(written)
    }

    fn flush(&mut self) -> std::io::Result<()> {
        // Flush is a NO-OP.
        Ok(())
    }
}

/// Denotes the sender side of a serializing channel.
pub struct ChannelWriter<T: Serialize + DeserializeOwned>(PipeWriter, PhantomData<T>);

impl<T: Serialize + DeserializeOwned> ChannelWriter<T> {
    /// Sends a serializable object to a the corresponding ChannelReader.
    /// Sending is always non blocking. Panics if any error occurs during io or serialization.
    pub fn send(&mut self, value: &T) {
        let serialized = serde_cbor::to_vec(value)
            .expect("In ChannelWriter::send: Failed to serialize to vector.");
        let size = serialized.len().to_be_bytes();
        match self.0.write(&size).expect("In ChannelWriter::send: Failed to write serialized size.")
        {
            w if w != std::mem::size_of::<usize>() => {
                panic!(
                    "In ChannelWriter::send: Failed to write serialized size. (written: {}).",
                    w
                );
            }
            _ => {}
        };
        match self
            .0
            .write(&serialized)
            .expect("In ChannelWriter::send: Failed to write serialized data.")
        {
            w if w != serialized.len() => {
                panic!(
                    "In ChannelWriter::send: Failed to write serialized data. (written: {}).",
                    w
                );
            }
            _ => {}
        };
    }
}

/// Represents the receiving and of a serializing channel.
pub struct ChannelReader<T>(PipeReader, PhantomData<T>);

impl<T: Serialize + DeserializeOwned> ChannelReader<T> {
    /// Receives a serializable object from the corresponding ChannelWriter.
    /// Receiving blocks until an object of type T has been read from the channel.
    /// Panics if an error occurs during io or deserialization.
    pub fn recv(&mut self) -> T {
        let mut size_buffer = [0u8; std::mem::size_of::<usize>()];
        match self.0.read(&mut size_buffer).expect("In ChannelReader::recv: Failed to read size.") {
            r if r != size_buffer.len() => {
                panic!("In ChannelReader::recv: Failed to read size. Insufficient data: {}", r);
            }
            _ => {}
        };
        let size = usize::from_be_bytes(size_buffer);
        let mut data_buffer = vec![0u8; size];
        match self
            .0
            .read(&mut data_buffer)
            .expect("In ChannelReader::recv: Failed to read serialized data.")
        {
            r if r != data_buffer.len() => {
                panic!(
                    "In ChannelReader::recv: Failed to read serialized data. Insufficient data: {}",
                    r
                );
            }
            _ => {}
        };

        serde_cbor::from_slice(&data_buffer)
            .expect("In ChannelReader::recv: Failed to deserialize data.")
    }
}

fn pipe() -> Result<(PipeReader, PipeWriter), nix::Error> {
    let (read_fd, write_fd) = nix_pipe()?;
    Ok((PipeReader(read_fd), PipeWriter(write_fd)))
}

fn pipe_channel<T>() -> Result<(ChannelReader<T>, ChannelWriter<T>), nix::Error>
where
    T: Serialize + DeserializeOwned,
{
    let (reader, writer) = pipe()?;
    Ok((
        ChannelReader::<T>(reader, Default::default()),
        ChannelWriter::<T>(writer, Default::default()),
    ))
}

/// Handle for handling child processes.
pub struct ChildHandle<R: Serialize + DeserializeOwned, M: Serialize + DeserializeOwned> {
    pid: Pid,
    result_reader: ChannelReader<R>,
    cmd_writer: ChannelWriter<M>,
    response_reader: ChannelReader<M>,
    exit_status: Option<WaitStatus>,
}

impl<R: Serialize + DeserializeOwned, M: Serialize + DeserializeOwned> ChildHandle<R, M> {
    /// Send a command message to the child.
    pub fn send(&mut self, data: &M) {
        self.cmd_writer.send(data)
    }

    /// Receive a response from the child.
    pub fn recv(&mut self) -> M {
        self.response_reader.recv()
    }

    /// Get child result. Panics if the child did not exit with status 0 or if a serialization
    /// error occurred.
    pub fn get_result(mut self) -> R {
        let status =
            waitpid(self.pid, None).expect("ChildHandle::wait: Failed while waiting for child.");
        match status {
            WaitStatus::Exited(pid, 0) => {
                // Child exited successfully.
                // Read the result from the pipe.
                self.exit_status = Some(WaitStatus::Exited(pid, 0));
                self.result_reader.recv()
            }
            WaitStatus::Exited(pid, c) => {
                panic!("Child did not exit as expected: {:?}", WaitStatus::Exited(pid, c));
            }
            status => {
                panic!("Child did not exit at all: {:?}", status);
            }
        }
    }
}

impl<R: Serialize + DeserializeOwned, M: Serialize + DeserializeOwned> Drop for ChildHandle<R, M> {
    fn drop(&mut self) {
        if self.exit_status.is_none() {
            panic!("Child result not checked.")
        }
    }
}

/// Run the given closure in a new process running with the new identity given as
/// `uid`, `gid`, and `se_context`. Parent process will run without waiting for child status.
///
/// # Safety
/// run_as_child runs the given closure in the client branch of fork. And it uses non
/// async signal safe API. This means that calling this function in a multi threaded program
/// yields undefined behavior in the child. As of this writing, it is safe to call this function
/// from a Rust device test, because every test itself is spawned as a separate process.
///
/// # Safety Binder
/// It is okay for the closure to use binder services, however, this does not work
/// if the parent initialized libbinder already. So do not use binder outside of the closure
/// in your test.
pub unsafe fn run_as_child<F, R, M>(
    se_context: &str,
    uid: Uid,
    gid: Gid,
    f: F,
) -> Result<ChildHandle<R, M>, nix::Error>
where
    R: Serialize + DeserializeOwned,
    M: Serialize + DeserializeOwned,
    F: 'static + Send + FnOnce(&mut ChannelReader<M>, &mut ChannelWriter<M>) -> R,
{
    let se_context =
        selinux::Context::new(se_context).expect("Unable to construct selinux::Context.");
    let (result_reader, mut result_writer) = pipe_channel().expect("Failed to create pipe.");
    let (mut cmd_reader, cmd_writer) = pipe_channel().expect("Failed to create cmd pipe.");
    let (response_reader, mut response_writer) =
        pipe_channel().expect("Failed to create cmd pipe.");

    // SAFETY: Our caller guarantees that the process only has a single thread, so calling
    // non-async-signal-safe functions in the child is in fact safe.
    match unsafe { fork() } {
        Ok(ForkResult::Parent { child, .. }) => {
            drop(response_writer);
            drop(cmd_reader);
            drop(result_writer);

            Ok(ChildHandle::<R, M> {
                pid: child,
                result_reader,
                response_reader,
                cmd_writer,
                exit_status: None,
            })
        }
        Ok(ForkResult::Child) => {
            drop(cmd_writer);
            drop(response_reader);
            drop(result_reader);

            // This will panic on error or insufficient privileges.
            transition(se_context, uid, gid);

            // Run the closure.
            let result = f(&mut cmd_reader, &mut response_writer);

            // Serialize the result of the closure.
            result_writer.send(&result);

            // Set exit status to `0`.
            std::process::exit(0);
        }
        Err(errno) => {
            panic!("Failed to fork: {:?}", errno);
        }
    }
}

/// Run the given closure in a new process running with the new identity given as
/// `uid`, `gid`, and `se_context`.
///
/// # Safety
/// run_as runs the given closure in the client branch of fork. And it uses non
/// async signal safe API. This means that calling this function in a multi threaded program
/// yields undefined behavior in the child. As of this writing, it is safe to call this function
/// from a Rust device test, because every test itself is spawned as a separate process.
///
/// # Safety Binder
/// It is okay for the closure to use binder services, however, this does not work
/// if the parent initialized libbinder already. So do not use binder outside of the closure
/// in your test.
pub unsafe fn run_as<F, R>(se_context: &str, uid: Uid, gid: Gid, f: F) -> R
where
    R: Serialize + DeserializeOwned,
    F: 'static + Send + FnOnce() -> R,
{
    let se_context =
        selinux::Context::new(se_context).expect("Unable to construct selinux::Context.");
    let (mut reader, mut writer) = pipe_channel::<R>().expect("Failed to create pipe.");

    // SAFETY: Our caller guarantees that the process only has a single thread, so calling
    // non-async-signal-safe functions in the child is in fact safe.
    match unsafe { fork() } {
        Ok(ForkResult::Parent { child, .. }) => {
            drop(writer);
            let status = waitpid(child, None).expect("Failed while waiting for child.");
            if let WaitStatus::Exited(_, 0) = status {
                // Child exited successfully.
                // Read the result from the pipe.
                // let serialized_result =
                //     reader.read_all().expect("Failed to read result from child.");

                // Deserialize the result and return it.
                reader.recv()
            } else {
                panic!("Child did not exit as expected {:?}", status);
            }
        }
        Ok(ForkResult::Child) => {
            // This will panic on error or insufficient privileges.
            transition(se_context, uid, gid);

            // Run the closure.
            let result = f();

            // Serialize the result of the closure.
            writer.send(&result);

            // Set exit status to `0`.
            std::process::exit(0);
        }
        Err(errno) => {
            panic!("Failed to fork: {:?}", errno);
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use keystore2_selinux as selinux;
    use nix::unistd::{getgid, getuid};
    use serde::{Deserialize, Serialize};

    /// This test checks that the closure does not produce an exit status of `0` when run inside a
    /// test and the closure panics. This would mask test failures as success.
    #[test]
    #[should_panic]
    fn test_run_as_panics_on_closure_panic() {
        // Safety: run_as must be called from a single threaded process.
        // This device test is run as a separate single threaded process.
        unsafe {
            run_as(selinux::getcon().unwrap().to_str().unwrap(), getuid(), getgid(), || {
                panic!("Closure must panic.")
            })
        };
    }

    static TARGET_UID: Uid = Uid::from_raw(10020);
    static TARGET_GID: Gid = Gid::from_raw(10020);
    static TARGET_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";

    /// Tests that the closure is running as the target identity.
    #[test]
    fn test_transition_to_untrusted_app() {
        // Safety: run_as must be called from a single threaded process.
        // This device test is run as a separate single threaded process.
        unsafe {
            run_as(TARGET_CTX, TARGET_UID, TARGET_GID, || {
                assert_eq!(TARGET_UID, getuid());
                assert_eq!(TARGET_GID, getgid());
                assert_eq!(TARGET_CTX, selinux::getcon().unwrap().to_str().unwrap());
            })
        };
    }

    #[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
    struct SomeResult {
        a: u32,
        b: u64,
        c: String,
    }

    #[test]
    fn test_serialized_result() {
        let test_result = SomeResult {
            a: 5,
            b: 0xffffffffffffffff,
            c: "supercalifragilisticexpialidocious".to_owned(),
        };
        let test_result_clone = test_result.clone();
        // Safety: run_as must be called from a single threaded process.
        // This device test is run as a separate single threaded process.
        let result = unsafe { run_as(TARGET_CTX, TARGET_UID, TARGET_GID, || test_result_clone) };
        assert_eq!(test_result, result);
    }

    #[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
    enum PingPong {
        Ping,
        Pong,
    }

    /// Tests that closure is running under given user identity and communicates with calling
    /// process using pipe.
    #[test]
    fn test_run_as_child() {
        let test_result = SomeResult {
            a: 5,
            b: 0xffffffffffffffff,
            c: "supercalifragilisticexpialidocious".to_owned(),
        };
        let test_result_clone = test_result.clone();

        // Safety: run_as_child must be called from a single threaded process.
        // This device test is run as a separate single threaded process.
        let mut child_handle: ChildHandle<SomeResult, PingPong> = unsafe {
            run_as_child(TARGET_CTX, TARGET_UID, TARGET_GID, |cmd_reader, response_writer| {
                assert_eq!(TARGET_UID, getuid());
                assert_eq!(TARGET_GID, getgid());
                assert_eq!(TARGET_CTX, selinux::getcon().unwrap().to_str().unwrap());

                let ping: PingPong = cmd_reader.recv();
                assert_eq!(ping, PingPong::Ping);

                response_writer.send(&PingPong::Pong);

                let ping: PingPong = cmd_reader.recv();
                assert_eq!(ping, PingPong::Ping);
                let pong: PingPong = cmd_reader.recv();
                assert_eq!(pong, PingPong::Pong);

                response_writer.send(&PingPong::Pong);
                response_writer.send(&PingPong::Ping);

                test_result_clone
            })
            .unwrap()
        };

        // Send one ping.
        child_handle.send(&PingPong::Ping);

        // Expect one pong.
        let pong = child_handle.recv();
        assert_eq!(pong, PingPong::Pong);

        // Send ping and pong.
        child_handle.send(&PingPong::Ping);
        child_handle.send(&PingPong::Pong);

        // Expect pong and ping.
        let pong = child_handle.recv();
        assert_eq!(pong, PingPong::Pong);
        let ping = child_handle.recv();
        assert_eq!(ping, PingPong::Ping);

        assert_eq!(child_handle.get_result(), test_result);
    }
}
