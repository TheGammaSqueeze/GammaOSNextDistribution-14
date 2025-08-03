package com.dsi.ant.hidl;

// A subset of the message constants from ant lib that are needed
// by the hal service.
final class Mesg {
    // Offsets within message structure.
    static final int ID_OFFSET = 1;
    static final int DATA_OFFSET = 2;

    // Message ids.
    static final byte BROADCAST_DATA_ID =           (byte) 0x4E;
    static final byte ACKNOWLEDGED_DATA_ID =        (byte) 0x4F;
    static final byte BURST_DATA_ID =               (byte) 0x50;
    static final byte EXT_BROADCAST_DATA_ID =       (byte) 0x5D;
    static final byte EXT_ACKNOWLEDGED_DATA_ID =    (byte) 0x5E;
    static final byte EXT_BURST_DATA_ID =           (byte) 0x5F;
    static final byte ADV_BURST_DATA_ID =           (byte) 0x72;
    static final byte FLOW_CONTROL_ID =             (byte) 0xC9;

    // Any other value means stop.
    static final byte FLOW_CONTROL_GO =             (byte) 0x00;
}
