/*
* Copyright (C) 2022 The Android Open Source Project
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

use alloc::vec::Vec;
use log::{error, info};
use tipc::{
    ConnectResult, Deserialize, Handle, Manager, MessageResult, PortCfg, Serialize, Serializer,
    Service, TipcError, Uuid,
};

//Some constants that are useful to be predefined
const HELLO_TRUSTY_PORT_NAME: &str = "com.android.trusty.hello";
const HELLO_TRUSTY_MAX_MSG_SIZE: usize = 100;
const MESSAGE_OFFSET: usize = 2;
const TAG_STRING: u8 = 1;
const TAG_ERROR: u8 = 2;

// We need to define a struct that implements the Service trait
struct HelloWorldService;

// Tags can either identify a String in a method, or an error.
enum Tag {
    TagString,
    TagError,
}

// We need to define a struct that implements Deserialize and Serialize traits
// Messages sent have a 1 byte tag, 1 byte length, and the actual message itself.
struct HelloWorldMessage {
    tag: Tag,
    length: u8,
    message: Vec<u8>,
}

// Providing a convenient way to translate u8 to a Tag
// We use this when deserializing
impl From<u8> for Tag {
    fn from(item: u8) -> Self {
        match item {
            TAG_STRING => Tag::TagString,
            _ => Tag::TagError,
        }
    }
}

// Providing a Deserialize implementation is necessary for the
// the manager to automatically deserialize messages in on_message()
impl Deserialize for HelloWorldMessage {
    type Error = TipcError;
    const MAX_SERIALIZED_SIZE: usize = HELLO_TRUSTY_MAX_MSG_SIZE;
    // The deserialization creates a HelloWorldMessage to be sent back to the service to handle.
    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, TipcError> {
        if bytes.len() < 2 {
            log::error!("The message is too short!");
            return Err(TipcError::InvalidData);
        } else if bytes.len() - MESSAGE_OFFSET != bytes[1].into() {
            log::error!("The serialized length does not match the actual length!");
            return Err(TipcError::InvalidData);
        }
        // The first 2 bytes are tag and length, so extracting the message requires an offset.
        let deserializedmessage = HelloWorldMessage {
            tag: Tag::from(bytes[0]),
            length: bytes[1],
            message: bytes[MESSAGE_OFFSET..].to_vec(),
        };
        Ok(deserializedmessage)
    }
}

// Providing a serialize implementation is necessary for the
// the handle to send messages in on_message()

impl<'s> Serialize<'s> for HelloWorldMessage {
    // The serialize converts a HelloWorldMessage into a slice for the service to send.
    // The first two bytes represent the tag and length, which we serialize first. The
    // message is serialized with a different method.
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        unsafe {
            serializer.serialize_as_bytes(match self.tag {
                Tag::TagString => &TAG_STRING,
                Tag::TagError => &TAG_ERROR,
            })?;
            serializer.serialize_as_bytes(&self.length)?;
        }
        serializer.serialize_bytes(&self.message.as_slice())
    }
}

// An implementation of the Service trait for a struct, included in the instantiation
// of the Manager. The implementation of the Service essentially tells the Manager
// how to handle incoming connections and messages.
impl Service for HelloWorldService {
    // Associates the Connection with a specific struct
    type Connection = ();
    // Associates the Message with a specific struct
    type Message = HelloWorldMessage;

    // This method is called whenever a client connects.
    // It should return Ok(Some(Connection)) if the connection is to be accepted.
    fn on_connect(
        &self,
        _port: &PortCfg,
        _handle: &Handle,
        _peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        info!("Connection to the Rust service!");
        Ok(ConnectResult::Accept(()))
    }
    // This method is called when the service receives a message.
    // The manager handles the deserialization into msg, which is passed to this callback.
    fn on_message(
        &self,
        _connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        // msg holds our deserialized HelloWorldMessage struct. Here, we want to get
        // actual message out to create a response.
        let inputmsg = std::str::from_utf8(&msg.message).map_err(|e| {
            error!("Failed to convert message to valid UTF-8: {:?}", e);
            TipcError::InvalidData
        })?;
        // Creating the response string based on the input string.
        let outputmsg = format!("Hello, {}!", inputmsg);

        // We send the message via handle, the client connection to the service.
        // Handle contains the method send, which will automatically serialize given
        // a serialization implementation, and send the message.
        handle.send(&HelloWorldMessage {
            tag: Tag::TagString,
            length: outputmsg.len().try_into().map_err(|e| {
                error!("Length of message is too long!: {:?}", e);
                TipcError::InvalidData
            })?,
            message: outputmsg.as_bytes().to_vec(),
        })?;
        // We keep the connection open by returning Ok(MaintainConnection).
        // Returning an Ok(CloseConnection) or an Err(_) will close the connection.
        Ok(MessageResult::MaintainConnection)
    }
}

// Essentially the main function, it sets up the port and manager.
// It immediately starts the service event loop afterwards.
pub fn init_and_start_loop() -> Result<(), TipcError> {
    // Allows the use of logging macros such as info!, debug!, error!
    trusty_log::init();
    info!("Hello from the Rust Hello World TA");

    // Instantiates a new port configuration. It describes a service port path, among other
    // options. Here, we're allowing other secure (Trusty) clients to connect, as well as
    // setting the maximumm message length for this port.
    let cfg = PortCfg::new(HELLO_TRUSTY_PORT_NAME)
        .map_err(|e| {
            error!("Could not create port config: {:?}", e);
            TipcError::UnknownError
        })?
        .allow_ta_connect()
        .msg_max_size(HELLO_TRUSTY_MAX_MSG_SIZE as u32);

    // Instantiates our service. Services handle IPC messages for specific ports.
    let service = HelloWorldService {};
    // Incoming bytes from the manager will be received here.
    let buffer = [0u8; HELLO_TRUSTY_MAX_MSG_SIZE];

    // The manager handles the IPC event loop. Given our port configuration, buffer, and
    // service, it forwards incoming connections & messages to the service.
    //
    // <_, _, 1, 1> means we define a Manager with a single port, and max one connection.
    Manager::<_, _, 1, 1>::new(service, cfg, buffer)
        .map_err(|e| {
            error!("Could not create Rust Hello World Service manager: {:?}", e);
            TipcError::UnknownError
        })?
        // The event loop waits for connections/messages,
        // then dispatches them to the service to be handled
        .run_event_loop()
}

// Our testing suite is comprised of a simple connection test and one that is similar
// to the original Hello World test-app TA, that exercises the actual TA.
#[cfg(test)]
mod tests {
    use super::*;
    use test::{expect, expect_eq};
    use trusty_std::ffi::{CString, FallibleCString};
    //This line is needed in order to run the unit tests in Trusty
    test::init!();

    //This test simply attempts a connection, by creating a connection to our TA port.
    #[test]
    fn connection_test() {
        let port = CString::try_new(HELLO_TRUSTY_PORT_NAME).unwrap();
        let _session = Handle::connect(port.as_c_str()).unwrap();
    }
    // This test is like the original test-app TA, sending "Hello",
    // and expecting to see "Hello, World" back.
    #[test]
    fn hello_world_test() {
        //Setting up the connection to the TA.
        let port = CString::try_new(HELLO_TRUSTY_PORT_NAME).unwrap();
        let session = Handle::connect(port.as_c_str()).unwrap();
        //Creating the input bytes, and sending it to the TA.
        let inputstring = "World";
        let test_message = HelloWorldMessage {
            tag: Tag::TagString,
            length: inputstring.len().try_into().unwrap(),
            message: inputstring.as_bytes().to_vec(),
        };
        session.send(&test_message).unwrap();
        //Receiving the response and checking that that it matches "Hello, World".
        let buf = &mut [0; HELLO_TRUSTY_MAX_MSG_SIZE as usize];
        let response: Result<HelloWorldMessage, _> = session.recv(buf);
        expect!(response.is_ok(), "The message should be able to be sent.");
        let deserializedstr = std::str::from_utf8(&buf[2..])
            .expect("Not a valid UTF-8 string!")
            .trim_matches(char::from(0));
        expect_eq!(
            deserializedstr,
            "Hello, World!",
            "Testing that {} would return Hello, {}!",
            inputstring,
            inputstring
        );
    }
}
