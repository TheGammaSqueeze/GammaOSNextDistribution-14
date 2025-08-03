// This file is generated. Do not edit
// @generated

// https://github.com/Manishearth/rust-clippy/issues/702
#![allow(unknown_lints)]
#![allow(clippy::all)]
#![allow(box_pointers)]
#![allow(dead_code)]
#![allow(missing_docs)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
#![allow(non_upper_case_globals)]
#![allow(trivial_casts)]
#![allow(unsafe_code)]
#![allow(unused_imports)]
#![allow(unused_results)]

const METHOD_FRONTEND_SERVICE_GET_VERSION: ::grpcio::Method<
    super::empty::Empty,
    super::frontend::VersionResponse,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::Unary,
    name: "/netsim.frontend.FrontendService/GetVersion",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

const METHOD_FRONTEND_SERVICE_CREATE_DEVICE: ::grpcio::Method<
    super::frontend::CreateDeviceRequest,
    super::frontend::CreateDeviceResponse,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::Unary,
    name: "/netsim.frontend.FrontendService/CreateDevice",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

const METHOD_FRONTEND_SERVICE_DELETE_CHIP: ::grpcio::Method<
    super::frontend::DeleteChipRequest,
    super::empty::Empty,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::Unary,
    name: "/netsim.frontend.FrontendService/DeleteChip",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

const METHOD_FRONTEND_SERVICE_PATCH_DEVICE: ::grpcio::Method<
    super::frontend::PatchDeviceRequest,
    super::empty::Empty,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::Unary,
    name: "/netsim.frontend.FrontendService/PatchDevice",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

const METHOD_FRONTEND_SERVICE_RESET: ::grpcio::Method<super::empty::Empty, super::empty::Empty> =
    ::grpcio::Method {
        ty: ::grpcio::MethodType::Unary,
        name: "/netsim.frontend.FrontendService/Reset",
        req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
        resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    };

const METHOD_FRONTEND_SERVICE_LIST_DEVICE: ::grpcio::Method<
    super::empty::Empty,
    super::frontend::ListDeviceResponse,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::Unary,
    name: "/netsim.frontend.FrontendService/ListDevice",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

const METHOD_FRONTEND_SERVICE_SUBSCRIBE_DEVICE: ::grpcio::Method<
    super::frontend::SubscribeDeviceRequest,
    super::frontend::SubscribeDeviceResponse,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::Unary,
    name: "/netsim.frontend.FrontendService/SubscribeDevice",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

const METHOD_FRONTEND_SERVICE_PATCH_CAPTURE: ::grpcio::Method<
    super::frontend::PatchCaptureRequest,
    super::empty::Empty,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::Unary,
    name: "/netsim.frontend.FrontendService/PatchCapture",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

const METHOD_FRONTEND_SERVICE_LIST_CAPTURE: ::grpcio::Method<
    super::empty::Empty,
    super::frontend::ListCaptureResponse,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::Unary,
    name: "/netsim.frontend.FrontendService/ListCapture",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

const METHOD_FRONTEND_SERVICE_GET_CAPTURE: ::grpcio::Method<
    super::frontend::GetCaptureRequest,
    super::frontend::GetCaptureResponse,
> = ::grpcio::Method {
    ty: ::grpcio::MethodType::ServerStreaming,
    name: "/netsim.frontend.FrontendService/GetCapture",
    req_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
    resp_mar: ::grpcio::Marshaller { ser: ::grpcio::pb_ser, de: ::grpcio::pb_de },
};

#[derive(Clone)]
pub struct FrontendServiceClient {
    pub client: ::grpcio::Client,
}

impl FrontendServiceClient {
    pub fn new(channel: ::grpcio::Channel) -> Self {
        FrontendServiceClient { client: ::grpcio::Client::new(channel) }
    }

    pub fn get_version_opt(
        &self,
        req: &super::empty::Empty,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::frontend::VersionResponse> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_GET_VERSION, req, opt)
    }

    pub fn get_version(
        &self,
        req: &super::empty::Empty,
    ) -> ::grpcio::Result<super::frontend::VersionResponse> {
        self.get_version_opt(req, ::grpcio::CallOption::default())
    }

    pub fn get_version_async_opt(
        &self,
        req: &super::empty::Empty,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::VersionResponse>> {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_GET_VERSION, req, opt)
    }

    pub fn get_version_async(
        &self,
        req: &super::empty::Empty,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::VersionResponse>> {
        self.get_version_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn create_device_opt(
        &self,
        req: &super::frontend::CreateDeviceRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::frontend::CreateDeviceResponse> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_CREATE_DEVICE, req, opt)
    }

    pub fn create_device(
        &self,
        req: &super::frontend::CreateDeviceRequest,
    ) -> ::grpcio::Result<super::frontend::CreateDeviceResponse> {
        self.create_device_opt(req, ::grpcio::CallOption::default())
    }

    pub fn create_device_async_opt(
        &self,
        req: &super::frontend::CreateDeviceRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::CreateDeviceResponse>>
    {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_CREATE_DEVICE, req, opt)
    }

    pub fn create_device_async(
        &self,
        req: &super::frontend::CreateDeviceRequest,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::CreateDeviceResponse>>
    {
        self.create_device_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn delete_chip_opt(
        &self,
        req: &super::frontend::DeleteChipRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::empty::Empty> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_DELETE_CHIP, req, opt)
    }

    pub fn delete_chip(
        &self,
        req: &super::frontend::DeleteChipRequest,
    ) -> ::grpcio::Result<super::empty::Empty> {
        self.delete_chip_opt(req, ::grpcio::CallOption::default())
    }

    pub fn delete_chip_async_opt(
        &self,
        req: &super::frontend::DeleteChipRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::empty::Empty>> {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_DELETE_CHIP, req, opt)
    }

    pub fn delete_chip_async(
        &self,
        req: &super::frontend::DeleteChipRequest,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::empty::Empty>> {
        self.delete_chip_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn patch_device_opt(
        &self,
        req: &super::frontend::PatchDeviceRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::empty::Empty> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_PATCH_DEVICE, req, opt)
    }

    pub fn patch_device(
        &self,
        req: &super::frontend::PatchDeviceRequest,
    ) -> ::grpcio::Result<super::empty::Empty> {
        self.patch_device_opt(req, ::grpcio::CallOption::default())
    }

    pub fn patch_device_async_opt(
        &self,
        req: &super::frontend::PatchDeviceRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::empty::Empty>> {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_PATCH_DEVICE, req, opt)
    }

    pub fn patch_device_async(
        &self,
        req: &super::frontend::PatchDeviceRequest,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::empty::Empty>> {
        self.patch_device_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn reset_opt(
        &self,
        req: &super::empty::Empty,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::empty::Empty> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_RESET, req, opt)
    }

    pub fn reset(&self, req: &super::empty::Empty) -> ::grpcio::Result<super::empty::Empty> {
        self.reset_opt(req, ::grpcio::CallOption::default())
    }

    pub fn reset_async_opt(
        &self,
        req: &super::empty::Empty,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::empty::Empty>> {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_RESET, req, opt)
    }

    pub fn reset_async(
        &self,
        req: &super::empty::Empty,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::empty::Empty>> {
        self.reset_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn list_device_opt(
        &self,
        req: &super::empty::Empty,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::frontend::ListDeviceResponse> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_LIST_DEVICE, req, opt)
    }

    pub fn list_device(
        &self,
        req: &super::empty::Empty,
    ) -> ::grpcio::Result<super::frontend::ListDeviceResponse> {
        self.list_device_opt(req, ::grpcio::CallOption::default())
    }

    pub fn list_device_async_opt(
        &self,
        req: &super::empty::Empty,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::ListDeviceResponse>> {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_LIST_DEVICE, req, opt)
    }

    pub fn list_device_async(
        &self,
        req: &super::empty::Empty,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::ListDeviceResponse>> {
        self.list_device_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn subscribe_device_opt(
        &self,
        req: &super::frontend::SubscribeDeviceRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::frontend::SubscribeDeviceResponse> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_SUBSCRIBE_DEVICE, req, opt)
    }

    pub fn subscribe_device(
        &self,
        req: &super::frontend::SubscribeDeviceRequest,
    ) -> ::grpcio::Result<super::frontend::SubscribeDeviceResponse> {
        self.subscribe_device_opt(req, ::grpcio::CallOption::default())
    }

    pub fn subscribe_device_async_opt(
        &self,
        req: &super::frontend::SubscribeDeviceRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::SubscribeDeviceResponse>>
    {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_SUBSCRIBE_DEVICE, req, opt)
    }

    pub fn subscribe_device_async(
        &self,
        req: &super::frontend::SubscribeDeviceRequest,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::SubscribeDeviceResponse>>
    {
        self.subscribe_device_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn patch_capture_opt(
        &self,
        req: &super::frontend::PatchCaptureRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::empty::Empty> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_PATCH_CAPTURE, req, opt)
    }

    pub fn patch_capture(
        &self,
        req: &super::frontend::PatchCaptureRequest,
    ) -> ::grpcio::Result<super::empty::Empty> {
        self.patch_capture_opt(req, ::grpcio::CallOption::default())
    }

    pub fn patch_capture_async_opt(
        &self,
        req: &super::frontend::PatchCaptureRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::empty::Empty>> {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_PATCH_CAPTURE, req, opt)
    }

    pub fn patch_capture_async(
        &self,
        req: &super::frontend::PatchCaptureRequest,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::empty::Empty>> {
        self.patch_capture_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn list_capture_opt(
        &self,
        req: &super::empty::Empty,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<super::frontend::ListCaptureResponse> {
        self.client.unary_call(&METHOD_FRONTEND_SERVICE_LIST_CAPTURE, req, opt)
    }

    pub fn list_capture(
        &self,
        req: &super::empty::Empty,
    ) -> ::grpcio::Result<super::frontend::ListCaptureResponse> {
        self.list_capture_opt(req, ::grpcio::CallOption::default())
    }

    pub fn list_capture_async_opt(
        &self,
        req: &super::empty::Empty,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::ListCaptureResponse>> {
        self.client.unary_call_async(&METHOD_FRONTEND_SERVICE_LIST_CAPTURE, req, opt)
    }

    pub fn list_capture_async(
        &self,
        req: &super::empty::Empty,
    ) -> ::grpcio::Result<::grpcio::ClientUnaryReceiver<super::frontend::ListCaptureResponse>> {
        self.list_capture_async_opt(req, ::grpcio::CallOption::default())
    }

    pub fn get_capture_opt(
        &self,
        req: &super::frontend::GetCaptureRequest,
        opt: ::grpcio::CallOption,
    ) -> ::grpcio::Result<::grpcio::ClientSStreamReceiver<super::frontend::GetCaptureResponse>>
    {
        self.client.server_streaming(&METHOD_FRONTEND_SERVICE_GET_CAPTURE, req, opt)
    }

    pub fn get_capture(
        &self,
        req: &super::frontend::GetCaptureRequest,
    ) -> ::grpcio::Result<::grpcio::ClientSStreamReceiver<super::frontend::GetCaptureResponse>>
    {
        self.get_capture_opt(req, ::grpcio::CallOption::default())
    }
    pub fn spawn<F>(&self, f: F)
    where
        F: ::std::future::Future<Output = ()> + Send + 'static,
    {
        self.client.spawn(f)
    }
}

pub trait FrontendService {
    fn get_version(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::empty::Empty,
        sink: ::grpcio::UnarySink<super::frontend::VersionResponse>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn create_device(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::frontend::CreateDeviceRequest,
        sink: ::grpcio::UnarySink<super::frontend::CreateDeviceResponse>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn delete_chip(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::frontend::DeleteChipRequest,
        sink: ::grpcio::UnarySink<super::empty::Empty>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn patch_device(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::frontend::PatchDeviceRequest,
        sink: ::grpcio::UnarySink<super::empty::Empty>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn reset(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::empty::Empty,
        sink: ::grpcio::UnarySink<super::empty::Empty>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn list_device(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::empty::Empty,
        sink: ::grpcio::UnarySink<super::frontend::ListDeviceResponse>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn subscribe_device(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::frontend::SubscribeDeviceRequest,
        sink: ::grpcio::UnarySink<super::frontend::SubscribeDeviceResponse>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn patch_capture(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::frontend::PatchCaptureRequest,
        sink: ::grpcio::UnarySink<super::empty::Empty>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn list_capture(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::empty::Empty,
        sink: ::grpcio::UnarySink<super::frontend::ListCaptureResponse>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
    fn get_capture(
        &mut self,
        ctx: ::grpcio::RpcContext,
        _req: super::frontend::GetCaptureRequest,
        sink: ::grpcio::ServerStreamingSink<super::frontend::GetCaptureResponse>,
    ) {
        grpcio::unimplemented_call!(ctx, sink)
    }
}

pub fn create_frontend_service<S: FrontendService + Send + Clone + 'static>(
    s: S,
) -> ::grpcio::Service {
    let mut builder = ::grpcio::ServiceBuilder::new();
    let mut instance = s.clone();
    builder = builder
        .add_unary_handler(&METHOD_FRONTEND_SERVICE_GET_VERSION, move |ctx, req, resp| {
            instance.get_version(ctx, req, resp)
        });
    let mut instance = s.clone();
    builder = builder
        .add_unary_handler(&METHOD_FRONTEND_SERVICE_CREATE_DEVICE, move |ctx, req, resp| {
            instance.create_device(ctx, req, resp)
        });
    let mut instance = s.clone();
    builder = builder
        .add_unary_handler(&METHOD_FRONTEND_SERVICE_DELETE_CHIP, move |ctx, req, resp| {
            instance.delete_chip(ctx, req, resp)
        });
    let mut instance = s.clone();
    builder = builder
        .add_unary_handler(&METHOD_FRONTEND_SERVICE_PATCH_DEVICE, move |ctx, req, resp| {
            instance.patch_device(ctx, req, resp)
        });
    let mut instance = s.clone();
    builder = builder.add_unary_handler(&METHOD_FRONTEND_SERVICE_RESET, move |ctx, req, resp| {
        instance.reset(ctx, req, resp)
    });
    let mut instance = s.clone();
    builder = builder
        .add_unary_handler(&METHOD_FRONTEND_SERVICE_LIST_DEVICE, move |ctx, req, resp| {
            instance.list_device(ctx, req, resp)
        });
    let mut instance = s.clone();
    builder = builder
        .add_unary_handler(&METHOD_FRONTEND_SERVICE_SUBSCRIBE_DEVICE, move |ctx, req, resp| {
            instance.subscribe_device(ctx, req, resp)
        });
    let mut instance = s.clone();
    builder = builder
        .add_unary_handler(&METHOD_FRONTEND_SERVICE_PATCH_CAPTURE, move |ctx, req, resp| {
            instance.patch_capture(ctx, req, resp)
        });
    let mut instance = s.clone();
    builder = builder
        .add_unary_handler(&METHOD_FRONTEND_SERVICE_LIST_CAPTURE, move |ctx, req, resp| {
            instance.list_capture(ctx, req, resp)
        });
    let mut instance = s;
    builder = builder.add_server_streaming_handler(
        &METHOD_FRONTEND_SERVICE_GET_CAPTURE,
        move |ctx, req, resp| instance.get_capture(ctx, req, resp),
    );
    builder.build()
}
