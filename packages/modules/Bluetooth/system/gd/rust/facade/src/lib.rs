//! Bluetooth testing root facade service

use bluetooth_rs::hal::facade::HciHalFacadeService;
use bluetooth_rs::hci::controller_facade::ControllerFacadeService;
use bluetooth_rs::hci::facade::HciFacadeService;
use bluetooth_rs::Stack;
use bt_facade_proto::rootservice::*;
use bt_facade_proto::rootservice_grpc::{create_root_facade, RootFacade};
use futures::executor::block_on;
use grpcio::*;
use std::sync::Arc;
use tokio::runtime::Runtime;
use tokio::sync::mpsc::{channel, Sender};
use tokio::sync::oneshot;

/// Bluetooth testing root facade service
#[derive(Clone)]
pub struct RootFacadeService {
    rt: Arc<Runtime>,
    manager: FacadeServiceManager,
}

impl RootFacadeService {
    /// Create a new instance of the root facade service
    pub fn create(
        rt: Arc<Runtime>,
        grpc_port: u16,
        rootcanal_port: Option<u16>,
        snoop_path: Option<String>,
    ) -> grpcio::Service {
        create_root_facade(Self {
            rt: rt.clone(),
            manager: FacadeServiceManager::create(rt, grpc_port, rootcanal_port, snoop_path),
        })
    }
}

impl RootFacade for RootFacadeService {
    fn start_stack(
        &mut self,
        _ctx: RpcContext<'_>,
        req: StartStackRequest,
        sink: UnarySink<StartStackResponse>,
    ) {
        self.rt.block_on(self.manager.start(req)).unwrap();
        sink.success(StartStackResponse::default());
    }

    fn stop_stack(
        &mut self,
        _ctx: RpcContext<'_>,
        _req: StopStackRequest,
        sink: UnarySink<StopStackResponse>,
    ) {
        self.rt.block_on(self.manager.stop()).unwrap();
        sink.success(StopStackResponse::default());
    }
}

#[derive(Debug)]
enum LifecycleCommand {
    Start { req: StartStackRequest, done: oneshot::Sender<()> },
    Stop { done: oneshot::Sender<()> },
}

#[derive(Clone)]
struct FacadeServiceManager {
    lifecycle_tx: Sender<LifecycleCommand>,
}

struct FacadeServer {
    server: Server,
    stack: Stack,
}

impl FacadeServer {
    async fn start(stack: Stack, req: StartStackRequest, grpc_port: u16) -> Self {
        let mut services = Vec::new();
        match req.module_under_test.unwrap() {
            BluetoothModule::HAL => {
                services.push(stack.get_grpc::<HciHalFacadeService>().await);
            }
            BluetoothModule::HCI | BluetoothModule::HCI_INTERFACES => {
                services.push(stack.get_grpc::<HciFacadeService>().await);
                services.push(stack.get_grpc::<ControllerFacadeService>().await);
            }
            _ => unimplemented!(),
        }

        let env = Arc::new(Environment::new(2));
        let mut builder = ServerBuilder::new(env);
        for service in services {
            builder = builder.register_service(service);
        }

        let mut server = builder.build().unwrap();
        let addr = format!("0.0.0.0:{}", grpc_port);
        let creds = ServerCredentials::insecure();
        server.add_listening_port(addr, creds).unwrap();
        server.start();

        Self { server, stack }
    }

    async fn stop(&mut self) {
        self.server.shutdown().await.unwrap();
        self.stack.stop().await;
    }
}

/// Result type
type Result<T> = std::result::Result<T, Box<dyn std::error::Error + Send + Sync>>;

impl FacadeServiceManager {
    fn create(
        rt: Arc<Runtime>,
        grpc_port: u16,
        rootcanal_port: Option<u16>,
        snoop_path: Option<String>,
    ) -> Self {
        let (tx, mut rx) = channel::<LifecycleCommand>(1);
        let local_rt = rt.clone();
        rt.spawn(async move {
            let mut server: Option<FacadeServer> = None;
            while let Some(cmd) = rx.recv().await {
                match cmd {
                    LifecycleCommand::Start { req, done } => {
                        let stack = Stack::new(local_rt.clone()).await;
                        stack.set_rootcanal_port(rootcanal_port).await;
                        stack.configure_snoop(snoop_path.clone()).await;
                        server = Some(FacadeServer::start(stack, req, grpc_port).await);
                        done.send(()).unwrap();
                    }
                    LifecycleCommand::Stop { done } => {
                        if let Some(s) = &mut server {
                            block_on(s.stop());
                            server = None;
                        }
                        done.send(()).unwrap();
                    }
                }
            }
        });

        Self { lifecycle_tx: tx }
    }

    async fn start(&self, req: StartStackRequest) -> Result<()> {
        let (tx, rx) = oneshot::channel();
        self.lifecycle_tx.send(LifecycleCommand::Start { req, done: tx }).await?;
        rx.await?;
        Ok(())
    }

    async fn stop(&self) -> Result<()> {
        let (tx, rx) = oneshot::channel();
        self.lifecycle_tx.send(LifecycleCommand::Stop { done: tx }).await?;
        rx.await?;
        Ok(())
    }
}
