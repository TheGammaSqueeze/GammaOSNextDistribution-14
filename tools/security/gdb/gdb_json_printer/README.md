# gdb-json-pretty-printer
This is a printer that prints variables in json format.
## Install in GDB
Copy the content in gdbinit into your ~/.gdbinit, replace the path <path_to_gdb_json_printer> by the actual path of gdb_json_printer directory.
## Run tests
First, in the gdb_json_printer directory, compile each example program with O0 optimization
```
g++ test_examples/basic_types.cpp -O0 -g -o test_examples/basic_types
g++ test_examples/objects1.cpp -O0 -g -o test_examples/objects1
g++ test_examples/objects2.cpp -O0 -g -o test_examples/objects2
g++ test_examples/objects.cpp -O0 -g -o test_examples/objects
g++ test_examples/array.cpp -O0 -g -o test_examples/array
g++ test_examples/reference.cpp -O0 -g -o test_examples/reference
```
second, run gdb in the gdb_json_printer directory:
```
gdb
```
finally, source the test script:
```
source test/gdb_json_printer_test.py
```
## printing format
```
Pointer := {
  type: 'pointer',
  ctype: ctype for pointer,
  address: Address,
  reference: Struct
}

Struct := {
  type: 'struct',
  ctype: ctype for struct,
  address: Address,
  fields: StructField[]
}

StructField := {
  field: name of field,
  field_type: 'base_class' or 'argument'
  value: Value
}

Int := {
  type: 'int',
  ctype: ctype for int,
  address: Address,
  value: number
}

Float := {
  type: 'float',
  ctype: 'float' or 'double',
  address: Address,
  value: number
}

Enum := {
  type: 'enum',
  ctype: ctype for enum,
  address: Address,
  value: number
}

Visit variable := {
  type: 'visited',
  ctype: ctype for struct,
  address: Address,
}

```

## problem to solve
* support for print parent/child class members.
* the printer treats all arrays as pointers now. We expected it to have ability to extract array/buffer length.
* the printer prints smart pointers into a super complex json block. Maybe we need some specific printer for these common objects.