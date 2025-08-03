#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import gdb
import gdb.printing
import re
from abc import ABC, abstractmethod
import json

# our code now only support Python3. In python2 we need to use intptr=long. Use this typedef
# for easier support of Python2 later.
intptr = int


def check_optimized_out(to_json):
    """ A decorator for to_json method of JsonPrinters. Check if the value in the printer
    is already optimized out.
    If the value is already optimized out, return {'type': 'optimized_out'}.

    Args:
        to_json: A to_json method of a JsonPrinter.

    Returns:
        decorated to_json method
    """

    def decorator(self, *args, **kwargs):
        if self.value is None or self.value.is_optimized_out:
            return {'type': 'optimized_out'}

        return to_json(self, *args, **kwargs)
    return decorator


def check_visited(to_json):
    """ A decorator for to_json method of JsonPrinters. Check if the value in the printer
    has appeared in visited_addresses_and_types.
    If the value has appeared in visited_addresses_and_types, return {'type': 'visited',
    'ctype': str(self.value.type), 'address': self.address}

    Args:
        to_json: A to_json method of a JsonPrinter.

    Returns:
        decorated to_json method
    """

    def decorator(self):
        addresstype = self.get_address_type()
        if addresstype != None and addresstype in self.visited_addresses_and_types:
            return {
                'type': 'visited',
                'ctype': str(self.value.type),
                'address': self.address
            }
        self.visited_addresses_and_types.add(self.get_address_type())
        return to_json(self)
    return decorator


class JsonPrinter(ABC):
    """Base class for all json printers.

    Attributes:
        value:
            The value to print, note that the lifetime of printer is limited to single print command.
        visited_addresses_and_types:
            A set of all `address`_`type` string that already visited during printing.
            If the address and type is in the set and the value is a struct or pointer,
            the printer will mark 'type' as visited and no longer expand the value. Can be None.
    """

    def __init__(self, value, visited_addresses_and_types=None):
        """ Constructor. A JsonPrinter will be created for each time a variable is printed.

        Args:
            value:
                A gdb.Value object, the value to print.
            visited_addresses_and_types:
                A set of all `address`_`type` string that already visited during printing.
                If the address and type is in the set and the value is a struct or pointer,
                the printer will mark 'type' as visited and no longer expand the value. Can be None.
        """
        self.value = value
        self.visited_addresses_and_types = visited_addresses_and_types
        if self.visited_addresses_and_types == None:
            self.visited_addresses_and_types = set()

        # value may not have address attribute if it's in register
        if hasattr(self.value, 'address') and self.value.address is not None:
            self.address = hex(intptr(self.value.address))
        else:
            self.address = None

    def to_string(self):
        """ Gdb Python interface to print a gdb.Value.

        Returns:
            A string representing the json format of a gdb.Value.
        """
        return json.dumps(self.to_json(), indent=4)

    @abstractmethod
    def to_json(self):
        """ The method to be implemented. Convert a gdb.Value object into a Python json object.

        Returns:
            A Python json object.
        """
        pass

    # we use address and type to identify a printed value and avoid circular references
    def get_address_type(self):
        address_str = self.address if self.address is not None else ""
        return address_str + "_" + str(self.value.type.strip_typedefs())


class BasicTypePrinter(JsonPrinter):
    """ Printer for basic types, now supports Enum, Int, Float, and Void.

    All integer variables in C++ will be considered as Int, e.g. char, short, long. Similarly,
    float and double are all considered as Float. For enum variables, they are actually int in
    C++, gdb knows they are enum variables, but can only print them as integer.
    """

    """
    BasicTypePrinter uses this dict to print basic_types.
    """
    basic_type_json_info = {
        gdb.TYPE_CODE_ENUM: {'type': 'enum', 'value_func': lambda value: str(int(value))},
        gdb.TYPE_CODE_INT: {'type': 'int', 'value_func': lambda value: str(int(value))},
        gdb.TYPE_CODE_FLT: {'type': 'float', 'value_func': lambda value: str(float(value))},
        gdb.TYPE_CODE_VOID: {'type': 'void', 'value_func': lambda value: None},
    }

    @check_optimized_out
    @check_visited
    def to_json(self):
        """ Output format is:
        {
            'type': 'int'/'float'/'enum'/'void',
            'ctype': string format type in C++,
            'address': string format address,
            'value': string format value
        }.
        """
        type_code = self.value.type.strip_typedefs().code
        json_type = BasicTypePrinter.basic_type_json_info[type_code]["type"]
        value_func = BasicTypePrinter.basic_type_json_info[type_code]["value_func"]
        value_json = {
            'type': json_type,
            'ctype': str(self.value.type),
            'address': self.address,
            'value': value_func(self.value)
        }
        return value_json


class ObjectPrinter(JsonPrinter):
    """ A Printer for objects in C++.

    The current version won't extract the dynamic/actual type of objects, and the member variable
    of a parent/child class won't be printed. We expect to support this function later.
    """

    @check_visited
    def to_json_without_expanding_base_class(self):
        """ A helper function for the to_json method.

        It tries to extract all member variables of an object without casting it into base classes.
        """
        value_json = {
            'type': 'struct',
            'ctype': str(self.value.type),
            'address': self.address,
            'fields': []
        }

        for field in self.value.type.fields():
            if not field.is_base_class:
                field_json = {
                    'field': field.name,
                    'value': None
                }

                field_printer = general_lookup_function(
                    self.value[field.name], self.visited_addresses_and_types)
                try:
                    field_json['value'] = field_printer.to_json()
                except:
                    field_json['value'] = "extract failed"

                value_json['fields'].append(field_json)

        return value_json

    @check_optimized_out
    def to_json(self, cast_to_dynamic_type=True):
        """Output format:
        {
            'type': 'struct',
            'ctype': string format type in C++,
            'address': string format address,
            'base_classes': [] # a list of value casted into each base class
            'fields': [] # a list of struct fields
        }.
        For each field in fields, its format is:
        {
            'field': string format of field name,
            'field_type': 'base_class'/'member', if it is a base class. the value will be the
            object which is casted into that base_class. Otherwise, the value is the json of
            the member variable.
            'value': json of the field
        }.
        """
        if cast_to_dynamic_type and \
                self.value.type.strip_typedefs() != self.value.dynamic_type.strip_typedefs():
            self.value = self.value.cast(self.value.dynamic_type)

        # address/type pair is set to visited after casted to dynamic type to avoid base class
        # being filtered by the visited check
        value_json = self.to_json_without_expanding_base_class()

        # if the type is visited, it's not necessary to explore its ancestors.
        if value_json["type"] != "visited":
            base_classes_list = []
            for field in self.value.type.fields():
                if field.is_base_class:
                    field_json = {
                        'base_class': field.name,
                        'value': None
                    }

                    base_class_printer = ObjectPrinter(
                        self.value.cast(field.type),
                        self.visited_addresses_and_types)
                    field_json["value"] = base_class_printer.to_json(
                        cast_to_dynamic_type=False)
                    base_classes_list.append(field_json)
            value_json['base_classes'] = base_classes_list

        return value_json


class RefAndPtrPrinter(JsonPrinter):
    """ Printer for reference and raw pointer in C++.
    """

    def __init__(self, value, visited_addresses_and_types=None):
        super().__init__(value, visited_addresses_and_types)
        self.void_ptr_re = re.compile(r"^.*void\s?\*$")

    @check_optimized_out
    @check_visited
    def to_json(self):
        """Output format:
        {
            'type': 'pointer'/"reference,
            'ctype': string format type in C++,
            'address': string format address,
            'reference': Json for a C++ object
        }.
        If the pointer is a void* pointer, reference would be "cannot extract void ptr",
        because gdb cannot extract content from a void* pointer. If the pointer is nullptr,
        reference would be "nullptr".
        """
        value_type = 'pointer' if self.value.type.code == gdb.TYPE_CODE_PTR else 'reference'
        value_json = {
            'type': value_type,
            'ctype': str(self.value.type),
            'address': self.address,
            'reference': None
        }

        # handle void pointer, dereference a void pointer will cause exception
        if self.void_ptr_re.match(str(self.value.type.strip_typedefs())) is not None:
            value_json['reference'] = "cannot extract void ptr"
            return value_json

        # handle nullptr
        if value_type == 'pointer' and int(self.value) == 0:
            value_json['reference'] = "nullptr"
            return value_json

        deref_value = self.value.referenced_value()
        deref_value_printer = general_lookup_function(
            deref_value, self.visited_addresses_and_types)
        value_json['reference'] = deref_value_printer.to_json()
        return value_json


class ExtractFailedPrinter(JsonPrinter):
    """ Printer for the case that cannot be extracted by current printer.
    """

    def to_json(self):
        """ output format: {'type': 'extract failed'}
        """
        return {'type': 'extract failed'}


class OptimizedOutPrinter(JsonPrinter):
    """ Printer for the case that a variable is already optimized out.
    """

    def to_json(self):
        """ Output format: {'type': 'optimized out'}.
        """
        return {'type': 'optimized out'}


class StackArrayPrinter(JsonPrinter):
    """ Printer for the arrays in C++.

    Note that this printer works only for C++ arrays(with type= T[]). It cannot
    print out buffers on heap.
    Output format:
    {
        'type': 'array',
        'element_type': string format type of array elements in C++,
        'values': A list of Value json indiciating each element in array
    }.
    """
    @check_optimized_out
    @check_visited
    def to_json(self):
        total_size = self.value.type.sizeof
        element_size = self.value.type.target().sizeof
        element_count = total_size // element_size
        stack_array_json = {
            'type': 'array',
            'element_type': str(self.value.type.target()),
            'values': []
        }
        for idx in range(element_count):
            element_json = general_lookup_function(
                self.value[idx], self.visited_addresses_and_types).to_json()
            stack_array_json['values'].append(element_json)
        return stack_array_json


""" The table indiciating which printer should be called given a gdb value.
"""
gdb_type_printer_map = {
    gdb.TYPE_CODE_PTR: RefAndPtrPrinter,
    gdb.TYPE_CODE_ARRAY: StackArrayPrinter,
    gdb.TYPE_CODE_STRUCT: ObjectPrinter,
    gdb.TYPE_CODE_UNION: ObjectPrinter,
    gdb.TYPE_CODE_ENUM: BasicTypePrinter,
    gdb.TYPE_CODE_FLAGS: None,
    gdb.TYPE_CODE_FUNC: None,
    gdb.TYPE_CODE_INT: BasicTypePrinter,
    gdb.TYPE_CODE_FLT: BasicTypePrinter,
    gdb.TYPE_CODE_VOID: BasicTypePrinter,
    gdb.TYPE_CODE_SET: None,    # not exist in C++
    gdb.TYPE_CODE_RANGE: None,    # not exist in C++?
    # not exist in C++, in C++, string is not a built-in type
    gdb.TYPE_CODE_STRING: None,
    gdb.TYPE_CODE_BITSTRING: None,  # deprecated
    gdb.TYPE_CODE_ERROR: None,
    gdb.TYPE_CODE_METHOD: None,
    gdb.TYPE_CODE_METHODPTR: None,
    gdb.TYPE_CODE_MEMBERPTR: None,
    gdb.TYPE_CODE_REF: RefAndPtrPrinter,
    gdb.TYPE_CODE_RVALUE_REF: None,
    gdb.TYPE_CODE_CHAR: None,   # char is an integer in C++
    gdb.TYPE_CODE_BOOL: None,   # bool is actually char in C++
    gdb.TYPE_CODE_COMPLEX: None,    # not exist in C++
    gdb.TYPE_CODE_TYPEDEF: None,
    gdb.TYPE_CODE_NAMESPACE: None,
    gdb.TYPE_CODE_DECFLOAT: None,    # not exist in C++
    gdb.TYPE_CODE_INTERNAL_FUNCTION: None
}


def general_lookup_function(value, visited_addresses_and_types=None):
    """ The actual printer installed, it will select JsonPrinter based on the gdb value given.
    """
    if value.is_optimized_out:
        return OptimizedOutPrinter(value)
    type_code = value.type.strip_typedefs().code
    if type_code not in gdb_type_printer_map or gdb_type_printer_map[type_code] is None:
        return ExtractFailedPrinter(value)

    # TODO: add regex match and specific printer for some type here? such as shared_ptr

    return gdb_type_printer_map[type_code](value, visited_addresses_and_types)


def register_printers():
    """ Call this function in your ~/.gdbinit to register the printer into gdb.
    """
    gdb.pretty_printers.append(general_lookup_function)
