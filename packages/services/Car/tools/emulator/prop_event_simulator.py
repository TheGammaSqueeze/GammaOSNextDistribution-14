import vhal_consts_2_0 as c
from vhal_emulator import Vhal

import argparse
import json
import sys

vhal_types = c.vhal_types_2_0

def propType(con):
    return getattr(c, con)

def parseVal(val, valType):
    parserFn = {
        c.VEHICLEPROPERTYTYPE_STRING: str,
        c.VEHICLEPROPERTYTYPE_BOOLEAN: int,
        c.VEHICLEPROPERTYTYPE_INT32: int,
        c.VEHICLEPROPERTYTYPE_INT32_VEC: lambda v: map(int, v.split(',')),
        c.VEHICLEPROPERTYTYPE_INT64: int,
        c.VEHICLEPROPERTYTYPE_INT64_VEC: lambda v: map(int, v.split(',')),
        c.VEHICLEPROPERTYTYPE_FLOAT: float,
        c.VEHICLEPROPERTYTYPE_FLOAT_VEC: lambda v: map(float, v.split(',')),
        c.VEHICLEPROPERTYTYPE_BYTES: None,
        c.VEHICLEPROPERTYTYPE_MIXED: json.loads
    }[valType]
    if not parserFn:
        raise ValueError('Property value type not recognized:', valType)

    return parserFn(val)

def main():
    parser = argparse.ArgumentParser(
         description='Execute vehicle simulation to simulate actual car sceanrios.')
    parser.add_argument(
        '-s',
        type=str,
        action='store',
        dest='device',
        default=None,
        help='Device serial number. Optional')
    parser.add_argument(
        '--property',
        type=propType,
        help='Property name from vhal_consts_2_0.py, e.g. VEHICLEPROPERTY_EV_CHARGE_PORT_OPEN')
    parser.add_argument(
       '--area',
        default=0,
        type=int,
        help='Area id for the property, "0" for global')
    parser.add_argument(
       '--value',
        type=str,
        help='Property value. If the value is MIXED type, you should provide the JSON string \
              of the value, e.g. \'{"int32_values": [0, 291504647], "int64_values": [1000000], \
              "float_values": [0.0, 30, 0.1]}\' which is for fake data generation controlling \
              property in default VHAL. If the value is array, use comma to split values')
    args = parser.parse_args()
    if not args.property:
      print('Property is required. Use --help to see options.')
      sys.exit(1)

    executeCommand(args)

def executeCommand(args):
    # Create an instance of vhal class.  Need to pass the vhal_types constants.
    v = Vhal(c.vhal_types_2_0, args.device)

    # Get the property config (if desired)
    print(args.property)
    v.getConfig(args.property)

    # Get the response message to getConfig()
    reply = v.rxMsg()
    print(reply)

    value = parseVal(args.value, reply.config[0].value_type)
    v.setProperty(args.property, args.area, value)

    # Get the response message to setProperty()
    reply = v.rxMsg()
    print(reply)

if __name__=='__main__':
    main()
