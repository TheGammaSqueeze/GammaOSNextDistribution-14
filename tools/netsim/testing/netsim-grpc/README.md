# Netsim python gRPC library

This contains a python library to interact with Netsim gRPC frontend service

## Development

If you wish to do development you can create a virtual environment and
generate python files from .proto files by running:

     . ./configure.sh

## Usage

NetsimClient() is the Netsim gRPC Frontend Client Service connecting to netsim daemon.
Users should use a NetsimClient to interact with netsim daemon by calling the provided APIs.

```python
from netsim import netsim

netsim_client = netsim.NetsimClient()
devices = netsim_client.get_devices()
```

The currently supported APIs include:
- `get_version()`: Get the version of the netsim daemon.
- `get_devices()`: Get detailed information for all devices connected to netsim daemon.
- `set_position()`: Set the position and/or orientation of the specified device.
- `set_radio()`: Set the specified radio chip's state of the specified device.
- `reset()`: Reset all devices.

## Adding dependencies

Configure will use the local python interpreter, which does not
have TLS support, so all the package must be made available locally!

If you need to add a package, make the source package available under the
repo directory. The easiest way to do this is:

     pip3 install pip2pi
     pip3 download  --no-binary ":all:"  my-package=1.2.3 -d repo
     dir2pi repo