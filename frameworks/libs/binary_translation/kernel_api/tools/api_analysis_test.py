#!/usr/bin/python
#
# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import copy
import unittest

import api_analysis


class APIAnalysisTests(unittest.TestCase):
  def test_guest_symbol_not_present_in_host(self):
    guest_api = \
        {
            "symbols": {"guest_only": {"type": "guest_only"}},
            "types": {"guest_only": {"kind": "incomplete"}}
        }
    host_api = {"symbols": {}, "types": {}}
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['guest_only']['is_compatible'])

  def test_compatible_int(self):
    guest_api = \
        {
            "symbols": {
                "int": {"type": "int32"}
            },
            "types": {
                "int32": {"align": 64,
                          "kind": "int",
                          "size": 32}
            }
        }
    host_api = copy.deepcopy(guest_api)
    # We allow host alignment to be less than guest one.
    # See comments in api_analysis.py for details.
    host_api['types']['int32']['align'] = 32
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertTrue(guest_api['symbols']['int']['is_compatible'])

  def test_compatible_loop_reference(self):
    guest_api = \
        {
            "symbols": {
                "pointer1": {"type": "pointer1"},
                "pointer2": {"type": "pointer2"},
            },
            "types": {
                "pointer1": {"align": 32,
                             "kind": "pointer",
                             "pointee_type": "pointer2",
                             "size": 32},
                "pointer2": {"align": 32,
                             "kind": "pointer",
                             "pointee_type": "pointer1",
                             "size": 32}
            }
        }
    host_api = copy.deepcopy(guest_api)
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertTrue(guest_api['symbols']['pointer1']['is_compatible'])
    self.assertTrue(guest_api['symbols']['pointer2']['is_compatible'])


  def test_incompatible_kind(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "incomplete"}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_size(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['size'] = 64
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_align(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['align'] = 64
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_fields_num(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "struct",
                            "is_polymorphic": False,
                            "fields": [{"offset": 0,
                                        "type": "t2"}],
                            "size": 32,
                            "align": 32},
                      "t2": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['fields'] = []
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_field_type(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "struct",
                            "is_polymorphic": False,
                            "fields": [{"offset": 0,
                                        "type": "t2"}],
                            "size": 32,
                            "align": 32},
                      "t2": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t2']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_field_offset(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "struct",
                            "is_polymorphic": False,
                            "fields": [{"offset": 0,
                                        "type": "t2"}],
                            "size": 32,
                            "align": 32},
                      "t2": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['fields'][0]["offset"] = 32
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_polymorphic(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "struct",
                            "is_polymorphic": True,
                            "fields": [],
                            "size": 32,
                            "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_func_variadic_args(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "align": 32,
                    "has_variadic_args": True,
                    "is_virtual_method": False,
                    "kind": "function",
                    "params": [],
                    "return_type": "void",
                },
                "void": {
                    "kind": "incomplete"
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_virtual_method(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "align": 32,
                    "has_variadic_args": True,
                    "is_virtual_method": True,
                    "kind": "function",
                    "params": [],
                    "return_type": "void",
                },
                "void": {
                    "kind": "incomplete"
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_func_params_num(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "align": 32,
                    "has_variadic_args": False,
                    "is_virtual_method": False,
                    "kind": "function",
                    "params": ["t2"],
                    "return_type": "t2",
                },
                "t2": {
                    "kind": "int",
                    "size": 32,
                    "align": 32
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['params'] = []
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_func_param_type(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "has_variadic_args": False,
                    "is_virtual_method": False,
                    "kind": "function",
                    "params": ["t2"],
                    "return_type": "void",
                },
                "void": {
                    "kind": "incomplete"
                },
                "t2": {
                    "kind": "int",
                    "size": 32,
                    "align": 32
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t2']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_unallowed_func_param_type(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "align": 32,
                    "has_variadic_args": False,
                    "is_virtual_method": False,
                    "kind": "function",
                    "params": ["t2"],
                    "return_type": "void",
                    "size": 0
                },
                "void": {
                    "kind": "incomplete"
                },
                # Structs are not supported in trampolines.
                "t2": {
                    "kind": "struct",
                    "is_polymorphic": False,
                    "fields": [],
                    "size": 32,
                    "align": 32
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_func_return_type(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "has_variadic_args": False,
                    "is_virtual_method": False,
                    "kind": "function",
                    "params": [],
                    "return_type": "t2",
                },
                "t2": {
                    "kind": "int",
                    "size": 32,
                    "align": 32
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t2']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_unallowed_func_return_type(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "has_variadic_args": False,
                    "is_virtual_method": False,
                    "kind": "function",
                    "params": [],
                    "return_type": "t2",
                },
                # Structs are not supported in trampolines.
                "t2": {
                    "kind": "struct",
                    "is_polymorphic": False,
                    "fields": [],
                    "size": 32,
                    "align": 32
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_pointee_type(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "pointer",
                            "pointee_type": "t2",
                            "size": 32,
                            "align": 32},
                      "t2": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t2']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_volatile_type(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "volatile",
                            "base_type" : "t2",
                            "size": 32,
                            "align": 32},
                      "t2": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t2']['kind'] = 'char8'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_restrict_type(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "restrict",
                            "base_type" : "t2",
                            "size": 32,
                            "align": 32},
                      "t2": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t2']['kind'] = 'char8'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_const_type(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "kind": "const",
                    "base_type" : "t2"
                },
                "t2": {
                    "kind": "int",
                    "size": 32,
                    "align": 32
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t2']['kind'] = 'char8'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_pointer_to_function(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "kind": "pointer",
                    "pointee_type": "func",
                    "size": 32,
                    "align": 32
                },
                "func": {
                    "has_variadic_args": False,
                    "is_virtual_method": False,
                    "kind": "function",
                    "params": [],
                    "return_type": "void",
                },
                "void": {
                    "kind": "incomplete"
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_array_element_type(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                }
            },
            "types": {
                "t": {
                    "align": 32,
                    "kind": "array",
                    "element_type": "t2",
                    "incomplete": False,
                    "size": 32
                },
                "t2": {
                    "kind": "int",
                    "size": 32,
                    "align": 32
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t2']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_incompatible_array_incompleteness(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"align": 32,
                            "kind": "array",
                            "element_type": "t2",
                            "incomplete": False,
                            "size": 32},
                      "t2": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['incomplete'] = True
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])


  def test_comparison_context(self):
    guest_api = \
        {
            "symbols": {"good_symb": {"type": "good_pointer_type"},
                        "bad_symb": {"type": "bad_pointer_type"}},
            "types": {"good_pointer_type": {"kind": "pointer",
                                            "pointee_type": "int_type",
                                            "size": 32,
                                            "align": 32},
                      "bad_pointer_type": {"kind": "pointer",
                                           "pointee_type": "int_type",
                                           "size": 32,
                                           "align": 32},
                      "int_type": {"kind": "int", "size": 32, "align": 32},
                      "fp_type": {"kind": "fp", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['bad_pointer_type']['pointee_type'] = 'fp_type'
    comparator = api_analysis.APIComparator(
        guest_api['types'], host_api['types'])
    api_analysis.mark_incompatible_api_with_comparator(
        comparator, guest_api['symbols'], host_api['symbols'])
    # Though int_type is compared against incompatible type in case of
    # 'bad_symb' the type itself may still be compatible in other contexts.
    # Thus this doesn't affect 'good_symb' compatibility.
    self.assertTrue(guest_api['symbols']['good_symb']['is_compatible'])
    self.assertFalse(guest_api['symbols']['bad_symb']['is_compatible'])
    self.assertTrue(comparator.are_types_compatible('int_type', 'int_type'))
    self.assertFalse(comparator.are_types_compatible('int_type', 'fp_type'))


  def test_loop_references(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "ref_loop_head"}},
            "types": {"ref_loop_head": {"kind": "struct",
                                        "is_polymorphic": False,
                                        "fields": [{"offset": 0,
                                                    "type": "ref_loop_body"},
                                                   {"offset": 0,
                                                    "type": "ref_post_loop"}],
                                        "size": 32,
                                        "align": 32},
                      "ref_loop_body": {"kind": "pointer",
                                        "pointee_type": "ref_loop_head",
                                        "size": 32,
                                        "align": 32},
                      "ref_post_loop": {"kind": "int",
                                        "size": 32,
                                        "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['ref_post_loop']['kind'] = 'fp'
    comparator = api_analysis.APIComparator(
        guest_api['types'], host_api['types'])
    api_analysis.mark_incompatible_api_with_comparator(
        comparator, guest_api['symbols'], host_api['symbols'])
    # 'ref_loop_body' is incompatible due to referencing 'ref_loop_head' which
    # references 'ref_post_loop' incompatible due to different type kind.
    # This is true even though reference from 'ref_loop_body' is back edge if to
    # consider graph traversal from 'symb'.
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])
    self.assertFalse(
        comparator.are_types_compatible('ref_loop_body', 'ref_loop_body'))


  def test_force_compatibility(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                },
                "symb_p": {
                    "type": "t_p"
                },
                "symb_c_p" : {
                    "type": "t_c_p"
                }
            },
            "types": {
                "t": {
                    "kind": "incomplete",
                    "force_compatible": True
                },
                "t_p" : {
                    "kind" : "pointer",
                    "pointee_type": "t",
                    "size" : 32
                },
                "t_c" : {
                    "kind" : "const",
                    "base_type": "t",
                },
                "t_c_p" : {
                    "kind" : "pointer",
                    "pointee_type": "t_c",
                    "size" : 32
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertTrue(guest_api['symbols']['symb']['is_compatible'])
    self.assertTrue(guest_api['symbols']['symb_p']['is_compatible'])
    self.assertTrue(guest_api['symbols']['symb_c_p']['is_compatible'])
    self.assertTrue(guest_api['types']['t']['useful_force_compatible'])


  def test_force_compatibility_for_referencing_incompatible(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                },
                "symb_p": {
                    "type": "t_p"
                },
                "symb_c_p" : {
                    "type": "t_c_p"
                }
            },
            "types": {
                "t": {
                    "kind": "incomplete"
                },
                "t_p" : {
                    "kind" : "pointer",
                    "pointee_type": "t",
                    "size" : 32,
                    "force_compatible": True
                },
                "t_c" : {
                    "kind" : "const",
                    "base_type": "t",
                },
                "t_c_p" : {
                    "kind" : "pointer",
                    "pointee_type": "t_c",
                    "size" : 32,
                    "force_compatible": True
                }
            }
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])
    self.assertTrue(guest_api['symbols']['symb_p']['is_compatible'])
    self.assertTrue(guest_api['symbols']['symb_c_p']['is_compatible'])
    self.assertTrue(guest_api['types']['t_p']['useful_force_compatible'])
    self.assertTrue(guest_api['types']['t_c_p']['useful_force_compatible'])

  def test_useless_force_compatibility(self):
    guest_api = \
        {
            "symbols": {
                "symb": {
                    "type": "t"
                },
            },
            "types": {
                "t": {
                    "kind": "incomplete",
                    "force_compatible": True
                },
            }
        }
    host_api = copy.deepcopy(guest_api)
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertTrue(guest_api['symbols']['symb']['is_compatible'])
    self.assertFalse(guest_api['types']['t'].get('useful_force_compatible', False))


  def test_incompatible_type_referenced_by_incompatible_type(self):
    guest_api = \
        {
            "symbols": {"symb": {"type": "t"}},
            "types": {"t": {"kind": "struct",
                            "is_polymorphic": False,
                            "fields": [{"offset": 0,
                                        "type": "t2"}],
                            "size": 32,
                            "align": 32},
                      "t2": {"kind": "int", "size": 32, "align": 32}}
        }
    host_api = copy.deepcopy(guest_api)
    host_api['types']['t']['size'] = 64
    host_api['types']['t2']['kind'] = 'fp'
    api_analysis.mark_incompatible_api(guest_api, host_api)
    self.assertFalse(guest_api['symbols']['symb']['is_compatible'])
    self.assertFalse(guest_api['types']['t']['is_compatible'])
    self.assertFalse(guest_api['types']['t2']['is_compatible'])

if __name__ == '__main__':
  unittest.main()
