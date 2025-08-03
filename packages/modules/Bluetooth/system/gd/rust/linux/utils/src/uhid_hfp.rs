//! This library provides UHID for HFP to interact with WebHID.

use bt_topshim::topstack;
use log::debug;
use std::convert::TryFrom;
use std::fs::{File, OpenOptions};
use std::io::{self, Read, Write};
use std::os::unix::fs::OpenOptionsExt;
use std::path::Path;
pub use uhid_virt::OutputEvent;
use uhid_virt::{Bus, CreateParams, InputEvent, StreamError, UHID_EVENT_SIZE};

pub const BLUETOOTH_TELEPHONY_UHID_REPORT_ID: u8 = 1;
pub const UHID_INPUT_HOOK_SWITCH: u8 = 1 << 0;
pub const UHID_INPUT_PHONE_MUTE: u8 = 1 << 1;
pub const UHID_OUTPUT_NONE: u8 = 0;
pub const UHID_OUTPUT_RING: u8 = 1 << 0;
pub const UHID_OUTPUT_OFF_HOOK: u8 = 1 << 1;
pub const UHID_OUTPUT_MUTE: u8 = 1 << 2;

const RDESC: [u8; 55] = [
    0x05,
    0x0B, // Usage Page (Telephony)
    0x09,
    0x05, // Usage (Headset)
    0xA1,
    0x01, // Collection (Application)
    0x85,
    BLUETOOTH_TELEPHONY_UHID_REPORT_ID, //   Report ID (1)
    0x05,
    0x0B, //   Usage Page (Telephony)
    0x15,
    0x00, //   Logical Minimum (0)
    0x25,
    0x01, //   Logical Maximum (1)
    0x09,
    0x20, //   Usage (Hook Switch)
    0x09,
    0x2f, //   Usage (Phone Mute)
    0x75,
    0x01, //   Report Size (1)
    0x95,
    0x02, //   Report Count (2)
    0x81,
    0x23, //   Input
    0x75,
    0x01, //   Report Size (1)
    0x95,
    0x06, //   Report Count (6)
    0x81,
    0x01, //   Input
    0x05,
    0x08, //   Usage Page (LEDs)
    0x15,
    0x00, //   Logical Minimum (0)
    0x25,
    0x01, //   Logical Maximum (1)
    0x09,
    0x18, //   Usage (Ring)
    0x09,
    0x17, //   Usage (Off-Hook)
    0x09,
    0x09, //   Usage (Mute)
    0x75,
    0x01, //   Report Size (1)
    0x95,
    0x03, //   Report Count (3)
    0x91,
    0x22, //   Output
    0x75,
    0x01, //   Report Size (1)
    0x95,
    0x05, //   Report Count (5)
    0x91,
    0x01, //   Output
    0xC0, // End Collection
];

pub struct UHidHfp {
    handle: File,
}

impl UHidHfp {
    pub fn create<F>(
        adapter_addr: String,
        remote_addr: String,
        remote_name: String,
        output_callback: F,
    ) -> UHidHfp
    where
        F: Fn(OutputEvent) + std::marker::Send + 'static,
    {
        let rd_data = RDESC.to_vec();
        let create_params = CreateParams {
            name: remote_name,
            phys: adapter_addr,
            uniq: remote_addr.clone(),
            bus: Bus::BLUETOOTH,
            vendor: 0,
            product: 0,
            version: 0,
            country: 0,
            rd_data,
        };

        let create_event: [u8; UHID_EVENT_SIZE] = InputEvent::Create(create_params).into();
        let mut options = OpenOptions::new();
        options.read(true);
        options.write(true);
        if cfg!(unix) {
            options.custom_flags(libc::O_RDWR | libc::O_CLOEXEC);
        }
        let mut uhid_writer = options.open(Path::new("/dev/uhid")).unwrap();
        let mut uhid_reader = uhid_writer.try_clone().unwrap();
        uhid_writer.write_all(&create_event).unwrap();

        topstack::get_runtime().spawn_blocking(move || {
            let mut event = [0u8; UHID_EVENT_SIZE];
            debug!("UHID: reading loop start");
            loop {
                match uhid_reader.read_exact(&mut event) {
                    Err(e) => {
                        log::error!("UHID: Read error: {:?}", e);
                        break;
                    }
                    Ok(_) => (),
                }
                match OutputEvent::try_from(event) {
                    Ok(m) => {
                        match m {
                            OutputEvent::Stop => break,
                            _ => (),
                        };

                        output_callback(m);
                    }
                    Err(e) => {
                        match e {
                            StreamError::Io(e) => log::error!("UHID: IO error: {}", e),
                            StreamError::UnknownEventType(e) => {
                                log::error!("UHID: Unknown event type: {}", e)
                            }
                        }
                        break;
                    }
                }
            }
            debug!("UHID: reading loop completed");
        });

        UHidHfp { handle: uhid_writer }
    }

    pub fn destroy(&mut self) -> io::Result<()> {
        let destroy_event: [u8; UHID_EVENT_SIZE] = InputEvent::Destroy.into();
        self.handle.write_all(&destroy_event)
    }

    pub fn send_input(&mut self, report: u8) -> io::Result<()> {
        let data: [u8; 2] = [BLUETOOTH_TELEPHONY_UHID_REPORT_ID, report];
        let input_event: [u8; UHID_EVENT_SIZE] = InputEvent::Input { data: &data }.into();
        self.handle.write_all(&input_event)
    }
}
