//! FFI interfaces for the GATT module. Some structs are exported so that
//! core::init can instantiate and pass them into the main loop.

use std::iter::Peekable;

use anyhow::{bail, Result};
use bt_common::init_flags::{
    always_use_private_gatt_for_debugging_is_enabled, rust_event_loop_is_enabled,
};
use cxx::UniquePtr;
pub use inner::*;
use log::{error, info, trace, warn};
use tokio::task::spawn_local;

use crate::{
    do_in_rust_thread,
    packets::{
        AttAttributeDataChild, AttAttributeDataView, AttBuilder, AttErrorCode, Serializable,
        SerializeError,
    },
};

use super::{
    arbiter::with_arbiter,
    callbacks::{GattWriteRequestType, GattWriteType, TransactionDecision},
    channel::AttTransport,
    ids::{AdvertiserId, AttHandle, ConnectionId, ServerId, TransactionId, TransportIndex},
    server::{
        gatt_database::{
            AttPermissions, GattCharacteristicWithHandle, GattDescriptorWithHandle,
            GattServiceWithHandle,
        },
        IndicationError,
    },
    GattCallbacks,
};

#[cxx::bridge]
#[allow(clippy::needless_lifetimes)]
#[allow(clippy::too_many_arguments)]
#[allow(missing_docs)]
#[allow(unsafe_op_in_unsafe_fn)]
mod inner {
    impl UniquePtr<GattServerCallbacks> {}

    #[namespace = "bluetooth"]
    extern "C++" {
        include!("bluetooth/uuid.h");
        /// A C++ UUID.
        type Uuid = crate::core::uuid::Uuid;
    }

    /// The GATT entity backing the value of a user-controlled
    /// attribute
    #[derive(Debug)]
    #[namespace = "bluetooth::gatt"]
    enum AttributeBackingType {
        /// A GATT characteristic
        #[cxx_name = "CHARACTERISTIC"]
        Characteristic = 0u32,
        /// A GATT descriptor
        #[cxx_name = "DESCRIPTOR"]
        Descriptor = 1u32,
    }

    #[namespace = "bluetooth::gatt"]
    unsafe extern "C++" {
        include!("src/gatt/ffi/gatt_shim.h");
        type AttributeBackingType;

        /// This contains the callbacks from Rust into C++ JNI needed for GATT
        type GattServerCallbacks;

        /// This callback is invoked when reading - the client
        /// must reply using SendResponse
        #[cxx_name = "OnServerRead"]
        fn on_server_read(
            self: &GattServerCallbacks,
            conn_id: u16,
            trans_id: u32,
            attr_handle: u16,
            attr_type: AttributeBackingType,
            offset: u32,
            is_long: bool,
        );

        /// This callback is invoked when writing - the client
        /// must reply using SendResponse
        #[cxx_name = "OnServerWrite"]
        fn on_server_write(
            self: &GattServerCallbacks,
            conn_id: u16,
            trans_id: u32,
            attr_handle: u16,
            attr_type: AttributeBackingType,
            offset: u32,
            need_response: bool,
            is_prepare: bool,
            value: &[u8],
        );

        /// This callback is invoked when executing / cancelling a write
        #[cxx_name = "OnExecute"]
        fn on_execute(self: &GattServerCallbacks, conn_id: u16, trans_id: u32, execute: bool);

        /// This callback is invoked when an indication has been sent and the
        /// peer device has confirmed it, or if some error occurred.
        #[cxx_name = "OnIndicationSentConfirmation"]
        fn on_indication_sent_confirmation(self: &GattServerCallbacks, conn_id: u16, status: i32);
    }

    /// What action the arbiter should take in response to an incoming packet
    #[namespace = "bluetooth::shim::arbiter"]
    enum InterceptAction {
        /// Forward the packet to the legacy stack
        #[cxx_name = "FORWARD"]
        Forward = 0u32,
        /// Discard the packet (typically because it has been intercepted)
        #[cxx_name = "DROP"]
        Drop = 1u32,
    }

    /// The type of GATT record supplied over FFI
    #[derive(Debug)]
    #[namespace = "bluetooth::gatt"]
    enum GattRecordType {
        PrimaryService,
        SecondaryService,
        IncludedService,
        Characteristic,
        Descriptor,
    }

    /// An entry in a service definition received from JNI. See GattRecordType
    /// for possible types.
    #[namespace = "bluetooth::gatt"]
    struct GattRecord {
        uuid: Uuid,
        record_type: GattRecordType,
        attribute_handle: u16,

        properties: u8,
        extended_properties: u16,

        permissions: u16,
    }

    #[namespace = "bluetooth::shim::arbiter"]
    unsafe extern "C++" {
        include!("stack/arbiter/acl_arbiter.h");
        type InterceptAction;

        /// Register callbacks from C++ into Rust within the Arbiter
        fn StoreCallbacksFromRust(
            on_le_connect: fn(tcb_idx: u8, advertiser: u8),
            on_le_disconnect: fn(tcb_idx: u8),
            intercept_packet: fn(tcb_idx: u8, packet: Vec<u8>) -> InterceptAction,
            on_outgoing_mtu_req: fn(tcb_idx: u8),
            on_incoming_mtu_resp: fn(tcb_idx: u8, mtu: usize),
            on_incoming_mtu_req: fn(tcb_idx: u8, mtu: usize),
        );

        /// Send an outgoing packet on the specified tcb_idx
        fn SendPacketToPeer(tcb_idx: u8, packet: Vec<u8>);
    }

    #[namespace = "bluetooth::gatt"]
    extern "Rust" {
        // service management
        fn open_server(server_id: u8);
        fn close_server(server_id: u8);
        fn add_service(server_id: u8, service_records: Vec<GattRecord>);
        fn remove_service(server_id: u8, service_handle: u16);

        // att operations
        fn send_response(server_id: u8, conn_id: u16, trans_id: u32, status: u8, value: &[u8]);
        fn send_indication(_server_id: u8, handle: u16, conn_id: u16, value: &[u8]);

        // connection
        fn is_connection_isolated(conn_id: u16) -> bool;

        // arbitration
        fn associate_server_with_advertiser(server_id: u8, advertiser_id: u8);
        fn clear_advertiser(advertiser_id: u8);
    }
}

/// Implementation of GattCallbacks wrapping the corresponding C++ methods
pub struct GattCallbacksImpl(pub UniquePtr<GattServerCallbacks>);

impl GattCallbacks for GattCallbacksImpl {
    fn on_server_read(
        &self,
        conn_id: ConnectionId,
        trans_id: TransactionId,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        offset: u32,
    ) {
        trace!("on_server_read ({conn_id:?}, {trans_id:?}, {handle:?}, {attr_type:?}, {offset:?}");
        self.0.as_ref().unwrap().on_server_read(
            conn_id.0,
            trans_id.0,
            handle.0,
            attr_type,
            offset,
            offset != 0,
        );
    }

    fn on_server_write(
        &self,
        conn_id: ConnectionId,
        trans_id: TransactionId,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        write_type: GattWriteType,
        value: AttAttributeDataView,
    ) {
        trace!(
            "on_server_write ({conn_id:?}, {trans_id:?}, {handle:?}, {attr_type:?}, {write_type:?}"
        );
        self.0.as_ref().unwrap().on_server_write(
            conn_id.0,
            trans_id.0,
            handle.0,
            attr_type,
            match write_type {
                GattWriteType::Request(GattWriteRequestType::Prepare { offset }) => offset,
                _ => 0,
            },
            matches!(write_type, GattWriteType::Request { .. }),
            matches!(write_type, GattWriteType::Request(GattWriteRequestType::Prepare { .. })),
            &value.get_raw_payload().collect::<Vec<_>>(),
        );
    }

    fn on_indication_sent_confirmation(
        &self,
        conn_id: ConnectionId,
        result: Result<(), IndicationError>,
    ) {
        trace!("on_indication_sent_confirmation ({conn_id:?}, {result:?}");
        self.0.as_ref().unwrap().on_indication_sent_confirmation(
            conn_id.0,
            match result {
                Ok(()) => 0, // GATT_SUCCESS
                _ => 133,    // GATT_ERROR
            },
        )
    }

    fn on_execute(
        &self,
        conn_id: ConnectionId,
        trans_id: TransactionId,
        decision: TransactionDecision,
    ) {
        trace!("on_execute ({conn_id:?}, {trans_id:?}, {decision:?}");
        self.0.as_ref().unwrap().on_execute(
            conn_id.0,
            trans_id.0,
            match decision {
                TransactionDecision::Execute => true,
                TransactionDecision::Cancel => false,
            },
        )
    }
}

/// Implementation of AttTransport wrapping the corresponding C++ method
pub struct AttTransportImpl();

impl AttTransport for AttTransportImpl {
    fn send_packet(
        &self,
        tcb_idx: TransportIndex,
        packet: AttBuilder,
    ) -> Result<(), SerializeError> {
        SendPacketToPeer(tcb_idx.0, packet.to_vec()?);
        Ok(())
    }
}

fn open_server(server_id: u8) {
    if !rust_event_loop_is_enabled() {
        return;
    }

    let server_id = ServerId(server_id);

    do_in_rust_thread(move |modules| {
        if always_use_private_gatt_for_debugging_is_enabled() {
            modules
                .gatt_module
                .get_isolation_manager()
                .associate_server_with_advertiser(server_id, AdvertiserId(0))
        }
        if let Err(err) = modules.gatt_module.open_gatt_server(server_id) {
            error!("{err:?}")
        }
    })
}

fn close_server(server_id: u8) {
    if !rust_event_loop_is_enabled() {
        return;
    }

    let server_id = ServerId(server_id);

    do_in_rust_thread(move |modules| {
        if let Err(err) = modules.gatt_module.close_gatt_server(server_id) {
            error!("{err:?}")
        }
    })
}

fn consume_descriptors<'a>(
    records: &mut Peekable<impl Iterator<Item = &'a GattRecord>>,
) -> Vec<GattDescriptorWithHandle> {
    let mut out = vec![];
    while let Some(GattRecord { uuid, attribute_handle, permissions, .. }) =
        records.next_if(|record| record.record_type == GattRecordType::Descriptor)
    {
        let mut att_permissions = AttPermissions::empty();
        att_permissions.set(AttPermissions::READABLE, permissions & 0x01 != 0);
        att_permissions.set(AttPermissions::WRITABLE_WITH_RESPONSE, permissions & 0x10 != 0);

        out.push(GattDescriptorWithHandle {
            handle: AttHandle(*attribute_handle),
            type_: *uuid,
            permissions: att_permissions,
        })
    }
    out
}

fn records_to_service(service_records: &[GattRecord]) -> Result<GattServiceWithHandle> {
    let mut characteristics = vec![];
    let mut service_handle_uuid = None;

    let mut service_records = service_records.iter().peekable();

    while let Some(record) = service_records.next() {
        match record.record_type {
            GattRecordType::PrimaryService => {
                if service_handle_uuid.is_some() {
                    bail!("got service registration but with duplicate primary service! {service_records:?}".to_string());
                }
                service_handle_uuid = Some((record.attribute_handle, record.uuid));
            }
            GattRecordType::Characteristic => {
                characteristics.push(GattCharacteristicWithHandle {
                    handle: AttHandle(record.attribute_handle),
                    type_: record.uuid,
                    permissions: AttPermissions::from_bits_truncate(record.properties),
                    descriptors: consume_descriptors(&mut service_records),
                });
            }
            GattRecordType::Descriptor => {
                bail!("Got unexpected descriptor outside of characteristic declaration")
            }
            _ => {
                warn!("ignoring unsupported database entry of type {:?}", record.record_type)
            }
        }
    }

    let Some((handle, uuid)) = service_handle_uuid else {
        bail!("got service registration but with no primary service! {characteristics:?}".to_string())
    };

    Ok(GattServiceWithHandle { handle: AttHandle(handle), type_: uuid, characteristics })
}

fn add_service(server_id: u8, service_records: Vec<GattRecord>) {
    if !rust_event_loop_is_enabled() {
        return;
    }

    // marshal into the form expected by GattModule
    let server_id = ServerId(server_id);

    match records_to_service(&service_records) {
        Ok(service) => {
            let handle = service.handle;
            do_in_rust_thread(move |modules| {
                let ok = modules.gatt_module.register_gatt_service(
                    server_id,
                    service.clone(),
                    modules.gatt_incoming_callbacks.get_datastore(server_id),
                );
                match ok {
                    Ok(_) => info!(
                        "successfully registered service for server {server_id:?} with handle {handle:?} (service={service:?})"
                    ),
                    Err(err) => error!(
                        "failed to register GATT service for server {server_id:?} with error: {err},  (service={service:?})"
                    ),
                }
            });
        }
        Err(err) => {
            error!("failed to register service for server {server_id:?}, err: {err:?}")
        }
    }
}

fn remove_service(server_id: u8, service_handle: u16) {
    if !rust_event_loop_is_enabled() {
        return;
    }

    let server_id = ServerId(server_id);
    let service_handle = AttHandle(service_handle);
    do_in_rust_thread(move |modules| {
        let ok = modules.gatt_module.unregister_gatt_service(server_id, service_handle);
        match ok {
            Ok(_) => info!(
                "successfully removed service {service_handle:?} for server {server_id:?}"
            ),
            Err(err) => error!(
                "failed to remove GATT service {service_handle:?} for server {server_id:?} with error: {err}"
            ),
        }
    })
}

fn is_connection_isolated(conn_id: u16) -> bool {
    if !rust_event_loop_is_enabled() {
        return false;
    }

    with_arbiter(|arbiter| arbiter.is_connection_isolated(ConnectionId(conn_id).get_tcb_idx()))
}

fn send_response(_server_id: u8, conn_id: u16, trans_id: u32, status: u8, value: &[u8]) {
    if !rust_event_loop_is_enabled() {
        return;
    }

    // TODO(aryarahul): fixup error codes to allow app-specific values (i.e. don't
    // make it an enum in PDL)
    let value = if status == 0 {
        Ok(AttAttributeDataChild::RawData(value.to_vec().into_boxed_slice()))
    } else {
        Err(AttErrorCode::try_from(status).unwrap_or(AttErrorCode::UNLIKELY_ERROR))
    };

    trace!("send_response {conn_id:?}, {trans_id:?}, {:?}", value.as_ref().err());

    do_in_rust_thread(move |modules| {
        match modules.gatt_incoming_callbacks.send_response(
            ConnectionId(conn_id),
            TransactionId(trans_id),
            value,
        ) {
            Ok(()) => { /* no-op */ }
            Err(err) => warn!("{err:?}"),
        }
    })
}

fn send_indication(_server_id: u8, handle: u16, conn_id: u16, value: &[u8]) {
    if !rust_event_loop_is_enabled() {
        return;
    }

    let handle = AttHandle(handle);
    let conn_id = ConnectionId(conn_id);
    let value = AttAttributeDataChild::RawData(value.into());

    trace!("send_indication {handle:?}, {conn_id:?}");

    do_in_rust_thread(move |modules| {
        let Some(bearer) = modules.gatt_module.get_bearer(conn_id.get_tcb_idx()) else {
            error!("connection {conn_id:?} does not exist");
            return;
        };
        let pending_indication = bearer.send_indication(handle, value);
        let gatt_outgoing_callbacks = modules.gatt_outgoing_callbacks.clone();
        spawn_local(async move {
            gatt_outgoing_callbacks
                .on_indication_sent_confirmation(conn_id, pending_indication.await);
        });
    })
}

fn associate_server_with_advertiser(server_id: u8, advertiser_id: u8) {
    if !rust_event_loop_is_enabled() {
        return;
    }

    let server_id = ServerId(server_id);
    let advertiser_id = AdvertiserId(advertiser_id);
    do_in_rust_thread(move |modules| {
        modules
            .gatt_module
            .get_isolation_manager()
            .associate_server_with_advertiser(server_id, advertiser_id);
    })
}

fn clear_advertiser(advertiser_id: u8) {
    if !rust_event_loop_is_enabled() {
        return;
    }

    let advertiser_id = AdvertiserId(advertiser_id);

    do_in_rust_thread(move |modules| {
        modules.gatt_module.get_isolation_manager().clear_advertiser(advertiser_id);
    })
}

#[cfg(test)]
mod test {
    use super::*;

    const SERVICE_HANDLE: AttHandle = AttHandle(1);
    const SERVICE_UUID: Uuid = Uuid::new(0x1234);

    const CHARACTERISTIC_HANDLE: AttHandle = AttHandle(2);
    const CHARACTERISTIC_UUID: Uuid = Uuid::new(0x5678);

    const DESCRIPTOR_UUID: Uuid = Uuid::new(0x4321);
    const ANOTHER_DESCRIPTOR_UUID: Uuid = Uuid::new(0x5432);

    const ANOTHER_CHARACTERISTIC_HANDLE: AttHandle = AttHandle(10);
    const ANOTHER_CHARACTERISTIC_UUID: Uuid = Uuid::new(0x9ABC);

    fn make_service_record(uuid: Uuid, handle: AttHandle) -> GattRecord {
        GattRecord {
            uuid,
            record_type: GattRecordType::PrimaryService,
            attribute_handle: handle.0,
            properties: 0,
            extended_properties: 0,
            permissions: 0,
        }
    }

    fn make_characteristic_record(uuid: Uuid, handle: AttHandle, properties: u8) -> GattRecord {
        GattRecord {
            uuid,
            record_type: GattRecordType::Characteristic,
            attribute_handle: handle.0,
            properties,
            extended_properties: 0,
            permissions: 0,
        }
    }

    fn make_descriptor_record(uuid: Uuid, handle: AttHandle, permissions: u16) -> GattRecord {
        GattRecord {
            uuid,
            record_type: GattRecordType::Descriptor,
            attribute_handle: handle.0,
            properties: 0,
            extended_properties: 0,
            permissions,
        }
    }

    #[test]
    fn test_empty_records() {
        let res = records_to_service(&[]);
        assert!(res.is_err());
    }

    #[test]
    fn test_primary_service() {
        let service =
            records_to_service(&[make_service_record(SERVICE_UUID, SERVICE_HANDLE)]).unwrap();

        assert_eq!(service.handle, SERVICE_HANDLE);
        assert_eq!(service.type_, SERVICE_UUID);
        assert_eq!(service.characteristics.len(), 0);
    }

    #[test]
    fn test_dupe_primary_service() {
        let res = records_to_service(&[
            make_service_record(SERVICE_UUID, SERVICE_HANDLE),
            make_service_record(SERVICE_UUID, SERVICE_HANDLE),
        ]);

        assert!(res.is_err());
    }

    #[test]
    fn test_service_with_single_characteristic() {
        let service = records_to_service(&[
            make_service_record(SERVICE_UUID, SERVICE_HANDLE),
            make_characteristic_record(CHARACTERISTIC_UUID, CHARACTERISTIC_HANDLE, 0),
        ])
        .unwrap();

        assert_eq!(service.handle, SERVICE_HANDLE);
        assert_eq!(service.type_, SERVICE_UUID);

        assert_eq!(service.characteristics.len(), 1);
        assert_eq!(service.characteristics[0].handle, CHARACTERISTIC_HANDLE);
        assert_eq!(service.characteristics[0].type_, CHARACTERISTIC_UUID);
    }

    #[test]
    fn test_multiple_characteristics() {
        let service = records_to_service(&[
            make_service_record(SERVICE_UUID, SERVICE_HANDLE),
            make_characteristic_record(CHARACTERISTIC_UUID, CHARACTERISTIC_HANDLE, 0),
            make_characteristic_record(
                ANOTHER_CHARACTERISTIC_UUID,
                ANOTHER_CHARACTERISTIC_HANDLE,
                0,
            ),
        ])
        .unwrap();

        assert_eq!(service.characteristics.len(), 2);
        assert_eq!(service.characteristics[0].handle, CHARACTERISTIC_HANDLE);
        assert_eq!(service.characteristics[0].type_, CHARACTERISTIC_UUID);
        assert_eq!(service.characteristics[1].handle, ANOTHER_CHARACTERISTIC_HANDLE);
        assert_eq!(service.characteristics[1].type_, ANOTHER_CHARACTERISTIC_UUID);
    }

    #[test]
    fn test_characteristic_readable_property() {
        let service = records_to_service(&[
            make_service_record(SERVICE_UUID, SERVICE_HANDLE),
            make_characteristic_record(CHARACTERISTIC_UUID, CHARACTERISTIC_HANDLE, 0x02),
        ])
        .unwrap();

        assert_eq!(service.characteristics[0].permissions, AttPermissions::READABLE);
    }

    #[test]
    fn test_characteristic_writable_property() {
        let service = records_to_service(&[
            make_service_record(SERVICE_UUID, SERVICE_HANDLE),
            make_characteristic_record(CHARACTERISTIC_UUID, CHARACTERISTIC_HANDLE, 0x08),
        ])
        .unwrap();

        assert_eq!(service.characteristics[0].permissions, AttPermissions::WRITABLE_WITH_RESPONSE);
    }

    #[test]
    fn test_characteristic_readable_and_writable_property() {
        let service = records_to_service(&[
            make_service_record(SERVICE_UUID, SERVICE_HANDLE),
            make_characteristic_record(CHARACTERISTIC_UUID, CHARACTERISTIC_HANDLE, 0x02 | 0x08),
        ])
        .unwrap();

        assert_eq!(
            service.characteristics[0].permissions,
            AttPermissions::READABLE | AttPermissions::WRITABLE_WITH_RESPONSE
        );
    }

    #[test]
    fn test_multiple_descriptors() {
        let service = records_to_service(&[
            make_service_record(SERVICE_UUID, AttHandle(1)),
            make_characteristic_record(CHARACTERISTIC_UUID, AttHandle(2), 0),
            make_descriptor_record(DESCRIPTOR_UUID, AttHandle(3), 0),
            make_descriptor_record(ANOTHER_DESCRIPTOR_UUID, AttHandle(4), 0),
        ])
        .unwrap();

        assert_eq!(service.characteristics[0].descriptors.len(), 2);
        assert_eq!(service.characteristics[0].descriptors[0].handle, AttHandle(3));
        assert_eq!(service.characteristics[0].descriptors[0].type_, DESCRIPTOR_UUID);
        assert_eq!(service.characteristics[0].descriptors[1].handle, AttHandle(4));
        assert_eq!(service.characteristics[0].descriptors[1].type_, ANOTHER_DESCRIPTOR_UUID);
    }

    #[test]
    fn test_descriptor_permissions() {
        let service = records_to_service(&[
            make_service_record(SERVICE_UUID, AttHandle(1)),
            make_characteristic_record(CHARACTERISTIC_UUID, AttHandle(2), 0),
            make_descriptor_record(DESCRIPTOR_UUID, AttHandle(3), 0x01),
            make_descriptor_record(DESCRIPTOR_UUID, AttHandle(4), 0x10),
            make_descriptor_record(DESCRIPTOR_UUID, AttHandle(5), 0x11),
        ])
        .unwrap();

        assert_eq!(service.characteristics[0].descriptors[0].permissions, AttPermissions::READABLE);
        assert_eq!(
            service.characteristics[0].descriptors[1].permissions,
            AttPermissions::WRITABLE_WITH_RESPONSE
        );
        assert_eq!(
            service.characteristics[0].descriptors[2].permissions,
            AttPermissions::READABLE | AttPermissions::WRITABLE_WITH_RESPONSE
        );
    }

    #[test]
    fn test_descriptors_multiple_characteristics() {
        let service = records_to_service(&[
            make_service_record(SERVICE_UUID, AttHandle(1)),
            make_characteristic_record(CHARACTERISTIC_UUID, AttHandle(2), 0),
            make_descriptor_record(DESCRIPTOR_UUID, AttHandle(3), 0),
            make_characteristic_record(CHARACTERISTIC_UUID, AttHandle(4), 0),
            make_descriptor_record(DESCRIPTOR_UUID, AttHandle(5), 0),
        ])
        .unwrap();

        assert_eq!(service.characteristics[0].descriptors.len(), 1);
        assert_eq!(service.characteristics[0].descriptors[0].handle, AttHandle(3));
        assert_eq!(service.characteristics[1].descriptors.len(), 1);
        assert_eq!(service.characteristics[1].descriptors[0].handle, AttHandle(5));
    }

    #[test]
    fn test_unexpected_descriptor() {
        let res = records_to_service(&[
            make_service_record(SERVICE_UUID, AttHandle(1)),
            make_descriptor_record(DESCRIPTOR_UUID, AttHandle(3), 0),
        ]);

        assert!(res.is_err());
    }
}
