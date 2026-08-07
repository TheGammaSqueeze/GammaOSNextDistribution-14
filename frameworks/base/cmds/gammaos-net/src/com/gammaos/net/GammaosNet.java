/*
 * GammaosNet - small CLI that drives WifiManager / BluetoothAdapter from
 * a system-signed process, so the gammaos-nano menu can switch between
 * saved Wi-Fi networks, scan for Bluetooth devices, and create/remove
 * bonds. AOSP 14's `cmd wifi` and `cmd bluetooth_manager` do not expose
 * these operations, so we implement them here against the same Manager
 * APIs that packages/apps/Settings uses.
 */
package com.gammaos.net;

import android.app.ActivityThread;
import android.content.Context;
import android.os.Looper;

public final class GammaosNet {

    public static void main(String[] args) {
        if (args.length == 0 || "help".equals(args[0]) || "-h".equals(args[0])
                || "--help".equals(args[0])) {
            printHelp();
            System.exit(args.length == 0 ? 1 : 0);
            return;
        }
        Looper.prepareMainLooper();
        ActivityThread.initializeMainlineModules();
        Context ctx = ActivityThread.systemMain().getSystemContext();
        int rc = 2;
        try {
            switch (args[0]) {
                case "wifi":
                    rc = WifiSubcommand.run(ctx, args);
                    break;
                case "bt":
                case "bluetooth":
                    rc = BtSubcommand.run(ctx, args);
                    break;
                case "usb":
                    rc = UsbSubcommand.run(ctx, args);
                    break;
                default:
                    printHelp();
                    rc = 2;
            }
        } catch (Throwable t) {
            System.err.println("gammaos-net: " + t);
            t.printStackTrace(System.err);
            rc = 3;
        }
        System.exit(rc);
    }

    private static void printHelp() {
        System.err.println("gammaos-net - GammaOS Nano network helper\n"
                + "\n"
                + "Usage:\n"
                + "  gammaos-net wifi connect-saved <netId>\n"
                + "      Connect to the saved Wi-Fi network with the given id.\n"
                + "\n"
                + "  gammaos-net bt scan [seconds]\n"
                + "      Run Bluetooth discovery for the given number of\n"
                + "      seconds (default 8) and print discovered devices as\n"
                + "      ADDR<TAB>NAME<TAB>RSSI<TAB>COD<TAB>BOND_STATE.\n"
                + "\n"
                + "  gammaos-net bt pair <address>\n"
                + "      Initiate bonding with <address> and wait for it to\n"
                + "      reach BONDED (or FAIL). Auto-accepts just-works /\n"
                + "      numeric pairing confirmation prompts.\n"
                + "\n"
                + "  gammaos-net bt unpair <address>\n"
                + "      Remove the bond for <address>.\n"
                + "\n"
                + "  gammaos-net bt list-bonded\n"
                + "      Print bonded devices as ADDR<TAB>NAME<TAB>COD.\n"
                + "\n"
                + "  gammaos-net usb <functions>\n"
                + "      Switch the USB gadget functions via UsbManager\n"
                + "      (mtp / ptp / rndis, or none/empty = charge only).\n");
    }
}
