//! Utility modules and functions for facade services.

pub mod converters {
    use bt_topshim::btif::BluetoothProperty;
    use bt_topshim_facade_protobuf::facade::EventData;

    pub fn bluetooth_property_to_event_data(property: BluetoothProperty) -> (String, EventData) {
        match property {
            BluetoothProperty::BdName(name) => {
                (String::from("BdName"), event_data_from_string(name))
            }
            BluetoothProperty::BdAddr(address) => {
                (String::from("BdAddr"), event_data_from_string(address.to_string()))
            }
            BluetoothProperty::Uuids(uuids) => {
                let mut event = EventData::new();
                for uuid in uuids {
                    event.data.push(format!("{:?}", uuid));
                }
                (String::from("Uuids"), event)
            }
            BluetoothProperty::AdapterScanMode(mode) => {
                (String::from("AdapterScanMode"), event_data_from_string(format!("{:?}", mode)))
            }
            BluetoothProperty::LocalIoCaps(caps) => {
                (String::from("LocalIoCaps"), event_data_from_string(format!("{:?}", caps)))
            }
            _ => (String::from("skip"), EventData::new()),
        }
    }

    pub fn event_data_from_string(data: String) -> EventData {
        let mut event = EventData::new();
        event.data.push(data);
        event
    }
}
