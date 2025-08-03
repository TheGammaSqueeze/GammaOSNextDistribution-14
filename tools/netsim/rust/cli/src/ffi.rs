//! Frontend-client library for rust.
///
/// Rust to C++ Grpc frontend.proto for Windows, linux and mac.
///
/// This can be replaced with grpcio native implementation when the
/// Windows build works.

/// Wrapper struct for application defined ClientResponseReader
pub struct ClientResponseReader {
    /// Delegated handler for reading responses
    pub handler: Box<dyn ClientResponseReadable>,
}

/// Delegating functions to handler
impl ClientResponseReader {
    fn handle_chunk(&self, chunk: &[u8]) {
        self.handler.handle_chunk(chunk);
    }
    fn handle_error(&self, error_code: u32, error_message: &str) {
        self.handler.handle_error(error_code, error_message);
    }
}

/// Trait for ClientResponseReader handler functions
pub trait ClientResponseReadable {
    /// Process each chunk of streaming response
    fn handle_chunk(&self, chunk: &[u8]);
    /// Process errors in response
    fn handle_error(&self, error_code: u32, error_message: &str);
}

#[cxx::bridge(namespace = "netsim::frontend")]
#[allow(missing_docs)]
#[allow(unsafe_op_in_unsafe_fn)]
pub mod frontend_client_ffi {
    // Shared enum GrpcMethod
    #[derive(Debug, PartialEq, Eq)]
    pub enum GrpcMethod {
        GetVersion,
        CreateDevice,
        DeleteChip,
        PatchDevice,
        ListDevice,
        Reset,
        ListCapture,
        PatchCapture,
        GetCapture,
    }

    extern "Rust" {
        type ClientResponseReader;
        fn handle_chunk(&self, chunk: &[u8]);
        fn handle_error(&self, error_code: u32, error_message: &str);
    }

    // C++ types and signatures exposed to Rust.
    unsafe extern "C++" {
        include!("frontend/frontend_client.h");

        type FrontendClient;
        type ClientResult;

        #[allow(dead_code)]
        #[rust_name = "new_frontend_client"]
        pub fn NewFrontendClient(server: &CxxString) -> UniquePtr<FrontendClient>;

        #[allow(dead_code)]
        #[rust_name = "get_capture"]
        pub fn GetCapture(
            self: &FrontendClient,
            request: &Vec<u8>,
            client_reader: &ClientResponseReader,
        ) -> UniquePtr<ClientResult>;

        #[allow(dead_code)]
        #[rust_name = "send_grpc"]
        pub fn SendGrpc(
            self: &FrontendClient,
            grpc_method: &GrpcMethod,
            request: &Vec<u8>,
        ) -> UniquePtr<ClientResult>;

        #[allow(dead_code)]
        #[rust_name = "is_ok"]
        pub fn IsOk(self: &ClientResult) -> bool;

        #[allow(dead_code)]
        #[rust_name = "err"]
        pub fn Err(self: &ClientResult) -> String;

        #[allow(dead_code)]
        #[rust_name = "byte_vec"]
        pub fn ByteVec(self: &ClientResult) -> &CxxVector<u8>;

    }
}
