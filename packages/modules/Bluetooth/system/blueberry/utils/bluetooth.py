from dataclasses import dataclass, field
from typing import Tuple


@dataclass(init=False)
class Address:
    address: bytes = field(default=bytes([0, 0, 0, 0, 0, 0]))

    def __init__(self, address=None):
        if not address:
            self.address = bytes([0, 0, 0, 0, 0, 0])
        elif isinstance(address, Address):
            self.address = address.address
        elif isinstance(address, str):
            self.address = bytes([int(b, 16) for b in address.split(':')])
        elif isinstance(address, bytes) and len(address) == 6:
            self.address = address
        elif isinstance(address, bytes):
            address = address.decode('utf-8')
            self.address = bytes([int(b, 16) for b in address.split(':')])
        else:
            raise Exception(f'unsupported address type: {address}')

    def from_str(address: str) -> 'Address':
        return Address(bytes([int(b, 16) for b in address.split(':')]))

    def parse(span: bytes) -> Tuple['Address', bytes]:
        assert len(span) >= 6
        return (Address(bytes(reversed(span[:6]))), span[6:])

    def parse_all(span: bytes) -> 'Address':
        assert (len(span) == 6)
        return Address(bytes(reversed(span)))

    def serialize(self) -> bytes:
        return bytes(reversed(self.address))

    def __repr__(self) -> str:
        return ':'.join([f'{b:02x}' for b in self.address])

    @property
    def size(self) -> int:
        return 6


@dataclass(init=False)
class ClassOfDevice:
    class_of_device: int = 0

    def __init__(self, class_of_device=None):
        if not class_of_device:
            self.class_of_device = 0
        elif isinstance(class_of_device, int):
            self.class_of_device = class_of_device
        elif isinstance(class_of_device, bytes):
            self.class_of_device = int.from_bytes(class_of_device, byteorder='little')
        else:
            raise Exception(f'unsupported class of device type: {class_of_device}')

    def parse(span: bytes) -> Tuple['ClassOfDevice', bytes]:
        assert len(span) >= 3
        return (ClassOfDevice(span[:3]), span[3:])

    def parse_all(span: bytes) -> 'ClassOfDevice':
        assert len(span) == 3
        return ClassOfDevice(span)

    def serialize(self) -> bytes:
        return int.to_bytes(self.class_of_device, length=3, byteorder='little')

    def __repr__(self) -> str:
        return f'{self.class_of_device:06x}'

    @property
    def size(self) -> int:
        return 3
