# Command Line Interface for Netsim (netsim)

Usage:
* `netsim [Options] <COMMAND>`

Options:
* `-v, --verbose`: Set verbose mode
* `-p, --port <PORT>`: Set custom grpc port
* `    --vsock <VSOCK>`: Set vsock cid:port pair
* `-h, --help`: Print help information

## Commands:
* ### `version`:    Print Netsim version information
    * Usage: `netsim version`
* ### `radio`:      Control the radio state of a device
    * Usage: `netsim radio <RADIO_TYPE> <STATUS> <NAME>`
    * Arguments:
        * \<RADIO_TYPE\>:   Radio type [possible values: ble, classic, wifi, uwb]
        * \<STATUS\>:       Radio status [possible values: up, down]
        * \<NAME\>:         Device name
* ### `move`:       Set the device location
    * Usage: `netsim move <NAME> <X> <Y> [Z]`
    * Arguments:
        * \<NAME\>:         Device name
        * \<X\>:            x position of device
        * \<Y\>:            y position of device
        * [Z]:              Optional z position of device
* ### `devices`:    Display device(s) information
    * Usage: `netsim devices [OPTIONS]`
    * Options:
        * `-c, --continuous`:    Continuously print device(s) information every second
* ### `beacon`: A chip that sends advertisements at a set interval
    * Usage: `netsim beacon <COMMAND>`
    * #### Commands:
        * `create`: Create a beacon
            * Usage: `netsim beacon create <COMMAND>`
                * ##### Commands:
                    * `ble`: Create a Bluetooth low-energy beacon
                        * Usage: `netsim beacon create ble [DEVICE_NAME | DEVICE_NAME CHIP_NAME] [OPTIONS]`
                        * Arguments:
                            * \[DEVICE_NAME\]: Optional name of the device to create. A default name will be generated if not supplied
                            * \[CHIP_NAME\]: Optional name of the beacon to create within the new device. May only be specified if DEVICE_NAME is specified. A default name will be generated if not supplied
                        * Advertisement Options:
                            * `--advertise-mode <MODE>`: Set the advertise mode which controls the duration between advertisements
                                * Possible values:
                                    * `low-power`
                                    * `balanced`
                                    * `low-latency`
                                    * A number measuring duration in milliseconds
                            * `--tx-power-level <LEVEL>`: Set the beacon's transmission power level
                                * Possible values:
                                    * `ultra-low`
                                    * `low`
                                    * `medium`
                                    * `high`
                                    * A number measuring transmission power in dBm
                            * `--scannable`: Set whether the beacon will respond to scan requests
                            * `--timeout <MS>`: Limit advertising to an amount of time given in milliseconds
                        * Advertise Packet Options:
                            * `--include-device-name`: Set whether the device name should be included in the advertise packet
                            * `--include-tx-power-level`: Set whether the transmit power level should be included in the advertise packet
                            * `--manufacturer-data <DATA>`: Add manufacturer specific data to the advertise packet
        * `patch`: Modify a beacon
            * Usage: `netsim beacon patch <COMMAND>`
                * ##### Commands:
                    * `ble`: Modify a Bluetooth low-energy beacon
                        * Usage: `netsim beacon patch ble <DEVICE_NAME> <CHIP_NAME> <OPTIONS>`
                        * Arguments:
                            * \<DEVICE_NAME\>: Name of the device that contains the beacon
                            * \<CHIP_NAME\>: Name of the beacon to modify
                        * Advertisement Options:
                            * `--advertise-mode <MODE>`: Change the advertise mode which controls the duration between advertisements
                                * Possible values:
                                    * `low-power`
                                    * `balanced`
                                    * `low-latency`
                                    * A number measuring duration in milliseconds
                            * `--tx-power-level <LEVEL>`: Change the beacon's transmission power level
                                * Possible values:
                                    * `ultra-low`
                                    * `low`
                                    * `medium`
                                    * `high`
                                    * A number measuring transmission power in dBm
                            * `--scannable`: Change whether the beacon will respond to scan requests
                            * `--timeout <MS>`: Limit advertising to an amount of time given in milliseconds
                        * Advertise Packet Options:
                            * `--include-device-name`: Change whether the device name should be included in the advertise packet
                            * `--include-tx-power-level`: Change whether the transmit power level should be included in the advertise packet
                            * `--manufacturer-data <DATA>`: Change manufacturer specific data within the advertise packet
        * `remove`: Remove a beacon
            * Usage: `netsim beacon remove <DEVICE_NAME> [CHIP_NAME]`
            * Arguments:
                * \<DEVICE_NAME\>: Name of the device to remove
                * \[CHIP_NAME\]: Optional name of the beacon to remove
* ### `reset`:      Reset Netsim device scene
    * Usage: `netsim reset`
* ### `capture`:       Control the packet capture functionalities with commands: list, patch, get [aliases: pcap]
    * Usage: `netsim capture <COMMAND>`
    * #### Commands
        * `list`:   List currently available Captures (packet captures)
            * Usage: `netsim capture list [PATTERNS]...`
            * Arguments:
                * [PATTERNS]...:    Optional strings of pattern for captures to list. Possible filter fields
                                    include ID, Device Name, and Chip Kind
            * Options:
                * `-c, --continuous`:    Continuously print Capture information every second
        * `patch`:  Patch a Capture source to turn packet capture on/off
            * Usage: `netsim capture patch <STATE> [PATTERNS]...`
            * Arguments:
                * \<STATE\>:        Packet capture state [possible values: on, off]
                * [PATTERNS]...:  Optional strings of pattern for captures to patch. Possible filter fields
                                    include ID, Device Name, and Chip Kind
        * `get`:    Download the packet capture content
            * Usage: `netsim capture get [OPTIONS] [PATTERNS]...`
            * Arguments:
                * [PATTERNS]...:    Optional strings of pattern for captures to get. Possible filter fields
                                    include ID, Device Name, and Chip Kind
            * Options:
                * `-o, --location`: Directory to store downloaded capture file(s)
* ### `gui`:        Opens netsim Web UI
* ### `artifact`:   Opens netsim artifacts directory (log, pcaps)
* ### `help`:       Print this message or the help of the given subcommand(s)
