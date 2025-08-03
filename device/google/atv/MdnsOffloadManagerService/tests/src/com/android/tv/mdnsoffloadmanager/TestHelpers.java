package com.android.tv.mdnsoffloadmanager;

import static org.junit.Assert.assertEquals;

import android.content.Intent;
import android.net.LinkProperties;
import android.os.PowerManager;

import device.google.atv.mdns_offload.IMdnsOffloadManager.OffloadServiceInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestHelpers {

    static final OffloadServiceInfo SERVICE_ATV
        = makeOffloadServiceInfo("", "atv", "somedevice", new byte[]{
            0, 0, 0, 0,             // Id, Flags
            0, 0, 0, 1, 0, 0, 0, 0, // Header section, 1 answer

            // Data 1:
            3, 'a', 't', 'v', 0x00, // "atv."
            0x00, 0x01,             // Type A
            (byte) 0x80, 0x01,      // Cache flush: True, class: in
            0, 0, 0, 5,             // TTL 5sec
            0, 4,                   // Data with size 4
            100, 80, 40, 20         // IP: 100.80.40.20
        });

    static final OffloadServiceInfo SERVICE_AIRPLAY
        = makeOffloadServiceInfo("", "airplay", "somedevice", new byte[]{
            0, 0, 0, 0,             // Id, Flags
            0, 0, 0, 1, 0, 0, 0, 0, // Header section, 1 answer

            // Data 1:
            7, 'a', 'i', 'r', 'p', 'l', 'a', 'y', 0x00, // "airplay."
            0x00, 0x01,             // Type A
            (byte) 0x80, 0x01,      // Cache flush: True, class: in
            0, 0, 0, 5,             // TTL 5sec
            0, 4,                   // Data with size 4
            100, 80, 40, 20         // IP: 100.80.40.20
        });

    static final OffloadServiceInfo SERVICE_GTV
        = makeOffloadServiceInfo("gtv", "atv", "somedevice", new byte[]{
            0, 0, 0, 0,             // Id, Flags
            0, 0, 0, 2, 0, 0, 0, 0, // Header section, 2 answers

            // Data 1:
            3, 'a', 't', 'v', 0x00, // "atv."
            0x00, 0x01,             // Type A
            (byte) 0x80, 0x01,      // Cache flush: True, class: in
            0, 0, 0, 5,             // TTL 5sec
            0, 4,                   // Data with size 4
            100, 80, 40, 20,        // IP: 100.80.40.20

            // Data 2:
            3, 'g', 't', 'v',       // "gtv."
            (byte) 0b11000000, 12,  // [ptr->] "atv."
            0x00, 16,               // Type TXT
            (byte) 0x80, 0x01,      // Cache flush: True, class: in
            0, 0, 0, 5,             // TTL 5sec
            0, 3,                   // Data with size 3
            'i', 's', 'o'           // "iso"
        });

    static final OffloadServiceInfo SERVICE_GOOGLECAST
        = makeOffloadServiceInfo("_googlecast", "_tcp", "tv-abc", new byte[]{
            0, 0, 0, 0,             // Id, Flags
            0, 0, 0, 2, 0, 0, 0, 0, // Header section, 2 answers

            // Data 1:
            11, '_', 'g', 'o', 'o', 'g', 'l', 'e', 'c', 'a', 's', 't', // "_googlecast."
            4, '_', 't', 'c', 'p',  // "_tcp."
            5, 'l', 'o', 'c', 'a', 'l', 0x00,  // "local."
            0x00, 0x0c,             // Type PTR
            (byte) 0x80, 0x01,      // Cache flush: True, class: in
            0, 0, 0, 5,             // TTL 5sec
            0, 9,                   // Data with size 9
            6, 't', 'v', '-', 'a', 'b', 'c', // "tv-abc."
            (byte) 0b11000000, 29,  // [ptr->] "local."

            // Data 2:
            (byte) 0b11000000, 46,  // [ptr->] "tv-abc.local."
            0x00, 0x01,             // Type A
            (byte) 0x80, 0x01,      // Cache flush: True, class: in
            0, 0, 0, 5,             // TTL 5sec
            0, 4,                   // Data with size 4
            100, 80, 40, 20,        // IP: 100.80.40.20
        });

    static OffloadServiceInfo makeOffloadServiceInfo(String serviceName, String serviceType,
        String deviceHostName, byte[] rawOffloadPacket) {
        OffloadServiceInfo serviceInfo = new OffloadServiceInfo();
        serviceInfo.serviceName = serviceName;
        serviceInfo.serviceType = serviceType;
        serviceInfo.deviceHostName = deviceHostName;
        serviceInfo.rawOffloadPacket = rawOffloadPacket;
        return serviceInfo;
    }

    static LinkProperties makeLinkProperties(String interfaceName) {
        LinkProperties linkProperties = new LinkProperties();
        linkProperties.setInterfaceName(interfaceName);
        return linkProperties;
    }

    static Intent makeIntent(String action) {
        Intent intent = new Intent();
        if (action != null) {
            intent.setAction(action);
        }
        return intent;
    }

    static PowerManager.LowPowerStandbyPolicy makeLowPowerStandbyPolicy(String... exemptPackages) {
        return new PowerManager.LowPowerStandbyPolicy(
                "placeholder", Set.of(exemptPackages), 0, Set.of());
    }

    static void verifyOffloadedServices(
            FakeMdnsOffloadService offloadService,
            String networkInterface,
            OffloadServiceInfo... expectedServices) {
        List<byte[]> expectedPackets = Arrays.stream(expectedServices)
                .map(service -> service.rawOffloadPacket)
                .collect(Collectors.toList());
        List<byte[]> offloadedPackets = offloadService
                .getOffloadData(networkInterface)
                .offloadedRecords
                .stream()
                .map(protocolData -> protocolData.rawOffloadPacket)
                .collect(Collectors.toList());
        assertEquals(expectedPackets, offloadedPackets);
    }

    static void verifyPassthroughQNames(
            FakeMdnsOffloadService offloadService,
            String networkInterface,
            String... qNames) {
        assertEquals(
                List.of(qNames),
                offloadService.getOffloadData(networkInterface).passthroughQNames);
    }
}
