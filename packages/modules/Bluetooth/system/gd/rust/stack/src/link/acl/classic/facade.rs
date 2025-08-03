//! Classic ACL facade

use crate::link::acl::classic::AclManager;

module! {
    facade_module,
    providers {
        ClassicAclFacadeService => provide_facade,
    }
}

#[provides]
async fn provide_facade(acl: AclManager) -> ClassicAclFacadeService {
    ClassicAclFacadeService { acl }
}

pub struct ClassicAclFacadeService {
    acl: AclManager,
}

impl AclManagerFacade for ClassicAclFacadeService {
    fn create_connection(
        &mut self,
        _ctx: RpcContext<'_>,
        mut _data: ConnectionMsg,
        _sink: ServerStreamingSink<ConnectionEvent>,
    ) {
        unimplemented!();
    }

    fn cancel_connection(
        &mut self,
        _ctx: RpcContext<'_>,
        mut _data: ConnectionMsg,
        _sink: UnarySink<Empty>,
    ) {
        unimplemented!();
    }

    fn disconnect(&mut self, _ctx: RpcContext<'_>, mut _data: HandleMsg, _sink: UnarySink<Empty>) {
        unimplemented!();
    }

    fn disconnect(&mut self, _ctx: RpcContext<'_>, mut _data: PolicyMsg, _sink: UnarySink<Empty>) {
        unimplemented!();
    }

    fn authentication_requested(
        &mut self,
        _ctx: RpcContext<'_>,
        mut _data: HandleMsg,
        _sink: UnarySink<Empty>,
    ) {
        unimplemented!();
    }

    fn connection_command(
        &mut self,
        _ctx: RpcContext<'_>,
        mut _data: ConnectionCommandMsg,
        _sink: UnarySink<Empty>,
    ) {
        unimplemented!();
    }

    fn switch_role(&mut self, _ctx: RpcContext<'_>, mut _data: RoleMsg, _sink: UnarySink<Empty>) {
        unimplemented!();
    }

    fn send_acl_data(&mut self, _ctx: RpcContext<'_>, mut _data: AclData, _sink: UnarySink<Empty>) {
        unimplemented!();
    }

    fn fetch_acl_data(
        &mut self,
        _ctx: RpcContext<'_>,
        mut _data: HandleMsg,
        _sink: ServerStreamingSink<AclData>,
    ) {
        unimplemented!();
    }

    fn fetch_incoming_connection(
        &mut self,
        _ctx: RpcContext<'_>,
        mut _data: Empty,
        _sink: ServerStreamingSink<ConnectionEvent>,
    ) {
        unimplemented!();
    }
}
