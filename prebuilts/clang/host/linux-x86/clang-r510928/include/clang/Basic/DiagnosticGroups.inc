
#ifdef GET_DIAG_ARRAYS
static const int16_t DiagArrays[] = {
  /* Empty */ -1,
  /* DiagArray1 */ diag::warn_pragma_message, -1,
  /* DiagArray2 */ diag::pp_hash_warning, -1,
  /* DiagArray3 */ diag::warn_cfstring_truncated, -1,
  /* DiagArray5 */ diag::warn_independentclass_attribute, diag::warn_ptr_independentclass_attribute, -1,
  /* DiagArray6 */ diag::warn_nsobject_attribute, -1,
  /* DiagArray8 */ diag::warn_abs_too_small, diag::warn_pointer_abs, diag::warn_unsigned_abs, diag::warn_wrong_absolute_value_type, -1,
  /* DiagArray9 */ diag::warn_abstract_final_class, -1,
  /* DiagArray10 */ diag::warn_abstract_vbase_init_ignored, -1,
  /* DiagArray12 */ diag::warn_taking_address_of_packed_member, -1,
  /* DiagArray13 */ diag::ext_typecheck_addrof_temporary, -1,
  /* DiagArray15 */ diag::warn_not_xl_compatible, diag::warn_pragma_align_not_xl_compatible, -1,
  /* DiagArray16 */ diag::warn_param_mismatched_alignment, -1,
  /* DiagArray18 */ diag::warn_alloca, -1,
  /* DiagArray19 */ diag::warn_alloca_align_alignof, -1,
  /* DiagArray20 */ diag::warn_always_inline_coroutine, -1,
  /* DiagArray21 */ diag::warn_ambiguous_suitable_delete_function_found, -1,
  /* DiagArray22 */ diag::warn_misplaced_ellipsis_vararg, -1,
  /* DiagArray23 */ diag::warn_pp_ambiguous_macro, -1,
  /* DiagArray24 */ diag::ext_nested_name_member_ref_lookup_ambiguous, -1,
  /* DiagArray25 */ diag::ext_ovl_ambiguous_oper_binary_reversed, -1,
  /* DiagArray26 */ diag::warn_incompatible_analyzer_plugin_api, -1,
  /* DiagArray27 */ diag::warn_arith_conv_mixed_anon_enum_types, -1,
  /* DiagArray28 */ diag::ext_abstract_pack_declarator_parens, -1,
  /* DiagArray30 */ diag::warn_arc_bridge_cast_nonarc, -1,
  /* DiagArray31 */ diag::warn_arc_possible_repeated_use_of_weak, -1,
  /* DiagArray32 */ diag::warn_arc_object_memaccess, -1,
  /* DiagArray33 */ diag::warn_arc_perform_selector_leaks, -1,
  /* DiagArray34 */ diag::warn_arc_repeated_use_of_weak, -1,
  /* DiagArray35 */ diag::warn_arc_retain_cycle, -1,
  /* DiagArray36 */ diag::warn_arc_literal_assign, diag::warn_arc_retained_assign, diag::warn_arc_retained_property_assign, -1,
  /* DiagArray37 */ diag::warn_argument_invalid_range, -1,
  /* DiagArray38 */ diag::warn_argument_undefined_behaviour, -1,
  /* DiagArray39 */ diag::warn_array_index_exceeds_bounds, diag::warn_array_index_exceeds_max_addressable_bounds, diag::warn_array_index_precedes_bounds, diag::warn_ptr_arith_exceeds_max_addressable_bounds, diag::warn_static_array_too_small, -1,
  /* DiagArray40 */ diag::warn_ptr_arith_exceeds_bounds, diag::warn_ptr_arith_precedes_bounds, -1,
  /* DiagArray41 */ diag::warn_inconsistent_array_form, -1,
  /* DiagArray43 */ diag::warn_asm_mismatched_size_modifier, -1,
  /* DiagArray44 */ diag::warn_not_in_enum_assignment, -1,
  /* DiagArray45 */ diag::warn_assume_side_effects, -1,
  /* DiagArray47 */ diag::warn_atimport_in_framework_header, -1,
  /* DiagArray48 */ diag::warn_atomic_member_access, -1,
  /* DiagArray49 */ diag::warn_atomic_op_misaligned, diag::warn_atomic_op_oversized, -1,
  /* DiagArray50 */ diag::warn_atomic_implicit_seq_cst, -1,
  /* DiagArray51 */ diag::warn_atomic_op_has_invalid_memory_order, -1,
  /* DiagArray53 */ diag::warn_atomic_property_rule, -1,
  /* DiagArray54 */ diag::warn_attribute_packed_for_bitfield, -1,
  /* DiagArray55 */ diag::warn_fe_backend_warning_attr, -1,
  /* DiagArray57 */ diag::warn_drv_disabling_vptr_no_rtti_default, -1,
  /* DiagArray59 */ diag::ext_auto_storage_class, -1,
  /* DiagArray60 */ diag::warn_auto_var_is_id, -1,
  /* DiagArray61 */ diag::warn_availability_and_unavailable, diag::warn_availability_fuchsia_unavailable_minor, diag::warn_availability_on_static_initializer, diag::warn_availability_swift_unavailable_deprecated_only, diag::warn_availability_unknown_platform, diag::warn_availability_version_ordering, diag::warn_expected_consistent_version_separator, diag::warn_mismatched_availability, diag::warn_mismatched_availability_override, diag::warn_mismatched_availability_override_unavail, -1,
  /* DiagArray62 */ diag::warn_drv_avr_family_linking_stdlibs_not_implemented, diag::warn_drv_avr_libc_not_found, diag::warn_drv_avr_linker_section_addresses_not_implemented, diag::warn_drv_avr_mcu_not_specified, diag::warn_drv_avr_stdlib_not_linked, -1,
  /* DiagArray63 */ diag::warn_fe_backend_plugin, diag::warn_fe_backend_resource_limit, -1,
  /* DiagArray64 */ diag::backslash_newline_space, -1,
  /* DiagArray65 */ diag::warn_bad_function_cast, -1,
  /* DiagArray67 */ diag::ext_rvalue_to_reference_access_ctor, diag::ext_rvalue_to_reference_temp_copy_no_viable, -1,
  /* DiagArray68 */ diag::ext_decomp_decl_cond, -1,
  /* DiagArray69 */ diag::ext_bit_int, -1,
  /* DiagArray70 */ diag::warn_impcast_bitfield_precision_constant, -1,
  /* DiagArray71 */ diag::warn_bitfield_too_small_for_enum, diag::warn_signed_bitfield_enum_conversion, diag::warn_unsigned_bitfield_assigned_signed_enum, -1,
  /* DiagArray72 */ diag::warn_bitfield_width_exceeds_type_width, -1,
  /* DiagArray73 */ diag::warn_precedence_bitwise_conditional, -1,
  /* DiagArray74 */ diag::warn_bitwise_instead_of_logical, -1,
  /* DiagArray75 */ diag::warn_bitwise_op_in_bitwise_op, -1,
  /* DiagArray76 */ diag::warn_block_capture_autoreleasing, -1,
  /* DiagArray77 */ diag::warn_impcast_bool_to_null_pointer, -1,
  /* DiagArray79 */ diag::warn_bitwise_negation_bool, -1,
  /* DiagArray80 */ diag::warn_braces_around_init, -1,
  /* DiagArray81 */ diag::warn_incompatible_branch_protection_option, diag::warn_target_unsupported_branch_protection_attribute, diag::warn_unsupported_branch_protection, diag::warn_unsupported_branch_protection_spec, -1,
  /* DiagArray82 */ diag::warn_objc_invalid_bridge, diag::warn_objc_invalid_bridge_to_cf, -1,
  /* DiagArray83 */ diag::warn_assume_aligned_too_great, -1,
  /* DiagArray84 */ diag::ext_pp_redef_builtin_macro, diag::ext_pp_undef_builtin_macro, -1,
  /* DiagArray85 */ diag::warn_builtin_chk_overflow, -1,
  /* DiagArray86 */ diag::warn_implicit_decl_requires_sysheader, -1,
  /* DiagArray87 */ diag::warn_zero_size_struct_union_compat, -1,
  /* DiagArray91 */ diag::ext_old_implicitly_unsigned_long_cxx, diag::warn_auto_storage_class, diag::warn_cxx11_compat_user_defined_literal, diag::warn_cxx11_keyword, diag::warn_cxx11_right_shift_in_template_arg, diag::warn_explicit_instantiation_inline_0x, diag::warn_explicit_instantiation_must_be_global_0x, diag::warn_explicit_instantiation_out_of_scope_0x, diag::warn_explicit_instantiation_unqualified_wrong_namespace_0x, diag::warn_old_implicitly_unsigned_long_cxx, -1,
  /* DiagArray92 */ diag::warn_deprecated_string_literal_conversion, -1,
  /* DiagArray94 */ diag::warn_cxx11_compat_reserved_user_defined_literal, -1,
  /* DiagArray95 */ diag::ext_alias_declaration, diag::ext_array_size_conversion, diag::ext_auto_type_specifier, diag::ext_cxx11_enum_fixed_underlying_type, diag::ext_defaulted_deleted_function, diag::ext_enum_friend, diag::ext_enumerator_list_comma_cxx, diag::ext_explicit_conversion_functions, diag::ext_extern_template, diag::ext_for_range, diag::ext_generalized_initializer_lists, diag::ext_nested_name_spec_is_enum, diag::ext_nonclass_type_friend, diag::ext_nonstatic_member_init, diag::ext_override_control_keyword, diag::ext_ref_qualifier, diag::ext_rvalue_reference, diag::ext_scoped_enum, diag::ext_static_data_member_in_union, diag::ext_template_arg_object_internal, diag::ext_template_outside_of_template, diag::ext_template_parameter_default_in_function_template, diag::ext_typename_outside_of_template, diag::ext_unelaborated_friend_type, diag::ext_variadic_templates, diag::warn_ext_cxx11_attributes, -1,
  /* DiagArray96 */ diag::ext_extra_semi_cxx11, -1,
  /* DiagArray97 */ diag::ext_inline_namespace, -1,
  /* DiagArray98 */ diag::ext_cxx11_longlong, -1,
  /* DiagArray99 */ diag::ext_cce_narrowing, diag::ext_init_list_constant_narrowing, diag::ext_init_list_type_narrowing, diag::ext_init_list_variable_narrowing, diag::warn_init_list_constant_narrowing, diag::warn_init_list_type_narrowing, diag::warn_init_list_variable_narrowing, -1,
  /* DiagArray100 */ diag::ext_cxx14_attr, -1,
  /* DiagArray101 */ diag::ext_binary_literal_cxx14, -1,
  /* DiagArray104 */ diag::ext_constexpr_body_invalid_stmt, diag::ext_constexpr_body_multiple_return, diag::ext_constexpr_local_var, diag::ext_constexpr_type_definition, diag::ext_decltype_auto_type_specifier, diag::ext_init_capture, diag::ext_variable_template, -1,
  /* DiagArray105 */ diag::ext_cxx17_attr, -1,
  /* DiagArray107 */ diag::warn_cxx17_compat_exception_spec_in_signature, -1,
  /* DiagArray109 */ diag::ext_auto_new_list_init, diag::ext_constexpr_if, diag::ext_constexpr_on_lambda_cxx17, diag::ext_cxx_static_assert_no_message, diag::ext_decomp_decl, diag::ext_fold_expression, diag::ext_for_range_begin_end_types_differ, diag::ext_hex_literal_invalid, diag::ext_init_statement, diag::ext_inline_variable, diag::ext_multi_using_declaration, diag::ext_nested_namespace_definition, diag::ext_ns_enum_attribute, diag::ext_star_this_lambda_capture_cxx17, diag::ext_template_template_param_typename, diag::ext_using_attribute_ns, diag::ext_using_declaration_pack, -1,
  /* DiagArray114 */ diag::ext_cxx20_attr, -1,
  /* DiagArray115 */ diag::warn_cxx17_compat_implicit_typename, diag::warn_cxx20_compat_aggregate_init_with_ctors, diag::warn_cxx20_compat_consteval, diag::warn_cxx20_compat_constinit, diag::warn_cxx20_compat_explicit_bool, diag::warn_cxx20_compat_spaceship, diag::warn_cxx20_compat_use_of_unaddressable_function, diag::warn_cxx20_compat_utf8_string, diag::warn_cxx20_keyword, -1,
  /* DiagArray117 */ diag::ext_cxx_designated_init, -1,
  /* DiagArray118 */ diag::ext_adl_only_template_id, diag::ext_bitfield_member_init, diag::ext_capture_binding, diag::ext_constexpr_body_invalid_stmt_cxx20, diag::ext_constexpr_ctor_missing_init, diag::ext_constexpr_function_try_block_cxx20, diag::ext_constexpr_local_var_no_init, diag::ext_constexpr_union_ctor_no_init, diag::ext_decomp_decl_spec, diag::ext_defaulted_comparison, diag::ext_equals_this_lambda_capture_cxx20, diag::ext_explicit_bool, diag::ext_for_range_init_stmt, diag::ext_implicit_typename, diag::ext_init_capture_pack, diag::ext_inline_nested_namespace_definition, diag::ext_lambda_template_parameter_list, diag::ext_pointer_to_const_ref_member_on_rvalue, diag::ext_using_decl_scoped_enumerator, diag::ext_using_enum_declaration, diag::warn_cxx17_compat_aggregate_init_paren_list, -1,
  /* DiagArray119 */ diag::ext_defaulted_comparison_constexpr_mismatch, -1,
  /* DiagArray120 */ diag::err_static_lambda, diag::ext_alias_in_init_statement, diag::ext_consteval_if, diag::ext_constexpr_body_invalid_stmt_cxx23, diag::ext_constexpr_static_var, diag::ext_cxx23_pp_directive, diag::ext_cxx23_size_t_suffix, diag::ext_cxx_label_end_of_compound_statement, diag::ext_decl_attrs_on_lambda, diag::ext_lambda_missing_parens, diag::ext_operator_overload_static, -1,
  /* DiagArray121 */ diag::ext_placeholder_var_definition, -1,
  /* DiagArray132 */ diag::warn_cxx11_compat_binary_literal, -1,
  /* DiagArray134 */ diag::warn_cxx98_compat_alias_declaration, diag::warn_cxx98_compat_alignas, diag::warn_cxx98_compat_alignof, diag::warn_cxx98_compat_attribute, diag::warn_cxx98_compat_auto_type_specifier, diag::warn_cxx98_compat_constexpr, diag::warn_cxx98_compat_ctor_list_init, diag::warn_cxx98_compat_decltype, diag::warn_cxx98_compat_defaulted_deleted_function, diag::warn_cxx98_compat_delegating_ctor, diag::warn_cxx98_compat_empty_scalar_initializer, diag::warn_cxx98_compat_empty_sizeless_initializer, diag::warn_cxx98_compat_enum_fixed_underlying_type, diag::warn_cxx98_compat_enum_friend, diag::warn_cxx98_compat_enum_nested_name_spec, diag::warn_cxx98_compat_explicit_conversion_functions, diag::warn_cxx98_compat_for_range, diag::warn_cxx98_compat_friend_is_member, diag::warn_cxx98_compat_generalized_initializer_lists, diag::warn_cxx98_compat_goto_into_protected_scope, diag::warn_cxx98_compat_indirect_goto_in_protected_scope, diag::warn_cxx98_compat_initializer_list_init, diag::warn_cxx98_compat_inline_namespace, diag::warn_cxx98_compat_lambda, diag::warn_cxx98_compat_less_colon_colon, diag::warn_cxx98_compat_literal_operator, diag::warn_cxx98_compat_literal_ucn_control_character, diag::warn_cxx98_compat_literal_ucn_escape_basic_scs, diag::warn_cxx98_compat_noexcept_decl, diag::warn_cxx98_compat_noexcept_expr, diag::warn_cxx98_compat_non_static_member_use, diag::warn_cxx98_compat_nonclass_type_friend, diag::warn_cxx98_compat_nonstatic_member_init, diag::warn_cxx98_compat_nontrivial_union_or_anon_struct_member, diag::warn_cxx98_compat_nullptr, diag::warn_cxx98_compat_override_control_keyword, diag::warn_cxx98_compat_pass_non_pod_arg_to_vararg, diag::warn_cxx98_compat_raw_string_literal, diag::warn_cxx98_compat_ref_qualifier, diag::warn_cxx98_compat_reference_list_init, diag::warn_cxx98_compat_rvalue_reference, diag::warn_cxx98_compat_scoped_enum, diag::warn_cxx98_compat_sfinae_access_control, diag::warn_cxx98_compat_static_assert, diag::warn_cxx98_compat_static_data_member_in_union, diag::warn_cxx98_compat_switch_into_protected_scope, diag::warn_cxx98_compat_template_arg_extra_parens, diag::warn_cxx98_compat_template_arg_null, diag::warn_cxx98_compat_template_arg_object_internal, diag::warn_cxx98_compat_template_outside_of_template, diag::warn_cxx98_compat_template_parameter_default_in_function_template, diag::warn_cxx98_compat_trailing_return_type, diag::warn_cxx98_compat_two_right_angle_brackets, diag::warn_cxx98_compat_typename_outside_of_template, diag::warn_cxx98_compat_unelaborated_friend_type, diag::warn_cxx98_compat_unicode_literal, diag::warn_cxx98_compat_unicode_type, diag::warn_cxx98_compat_using_decl_constructor, diag::warn_cxx98_compat_variadic_templates, -1,
  /* DiagArray135 */ diag::warn_cxx98_compat_temp_copy, -1,
  /* DiagArray136 */ diag::warn_cxx98_compat_top_level_semi, -1,
  /* DiagArray137 */ diag::warn_cxx98_compat_template_arg_local_type, -1,
  /* DiagArray138 */ diag::warn_cxx98_compat_array_size_conversion, diag::warn_cxx98_compat_cast_fn_obj, diag::warn_cxx98_compat_empty_fnmacro_arg, diag::warn_cxx98_compat_enumerator_list_comma, diag::warn_cxx98_compat_extern_template, diag::warn_cxx98_compat_longlong, diag::warn_cxx98_compat_no_newline_eof, diag::warn_cxx98_compat_pp_line_too_big, diag::warn_cxx98_compat_variadic_macro, -1,
  /* DiagArray139 */ diag::warn_cxx98_compat_template_arg_unnamed_type, -1,
  /* DiagArray140 */ diag::ext_anonymous_union, diag::ext_c11_anonymous_struct, diag::ext_c11_feature, diag::ext_typecheck_compare_complete_incomplete_pointers, -1,
  /* DiagArray141 */ diag::warn_c23_keyword, -1,
  /* DiagArray142 */ diag::ext_c23_bitint_suffix, diag::ext_c23_pp_directive, diag::ext_c_empty_initializer, diag::ext_c_label_end_of_compound_statement, diag::ext_c_nullptr, diag::ext_c_static_assert_no_message, diag::ext_parameter_name_omitted_c23, diag::warn_ext_c23_attributes, -1,
  /* DiagArray145 */ diag::warn_c99_compat_unicode_id, diag::warn_c99_compat_unicode_literal, diag::warn_c99_keyword, diag::warn_old_implicitly_unsigned_long, -1,
  /* DiagArray146 */ diag::ext_designated_init, diag::ext_designated_init_array, diag::ext_designated_init_brace_elision, diag::ext_designated_init_mixed, diag::ext_designated_init_nested, -1,
  /* DiagArray147 */ diag::ext_aggregate_init_not_constant, diag::ext_c99_array_usage, diag::ext_c99_compound_literal, diag::ext_c99_feature, diag::ext_c99_flexible_array_member, diag::ext_c99_variable_decl_in_for_loop, diag::ext_c99_whitespace_required_after_macro_name, diag::ext_empty_fnmacro_arg, diag::ext_enumerator_list_comma_c, diag::ext_hex_constant_invalid, -1,
  /* DiagArray148 */ diag::warn_call_to_pure_virtual_member_function_from_ctor_dtor, -1,
  /* DiagArray149 */ diag::warn_called_once_gets_called_twice, diag::warn_called_once_never_called, diag::warn_called_once_never_called_when, -1,
  /* DiagArray150 */ diag::warn_cast_align, -1,
  /* DiagArray151 */ diag::warn_cast_calling_conv, -1,
  /* DiagArray152 */ diag::warn_cast_function_type, -1,
  /* DiagArray153 */ diag::warn_cast_function_type_strict, -1,
  /* DiagArray154 */ diag::warn_cast_pointer_from_sel, -1,
  /* DiagArray155 */ diag::warn_cast_qual, diag::warn_cast_qual2, -1,
  /* DiagArray156 */ diag::ext_bad_cxx_cast_qualifiers_away_incoherent, -1,
  /* DiagArray158 */ diag::warn_omp_section_is_char, diag::warn_subscript_is_char, -1,
  /* DiagArray159 */ diag::warn_drv_yc_multiple_inputs_clang_cl, diag::warn_drv_ycyu_different_arg_clang_cl, diag::warn_pp_hdrstop_filename_ignored, diag::warn_pp_macro_def_mismatch_with_pch, -1,
  /* DiagArray160 */ diag::warn_conv_to_base_not_used, diag::warn_conv_to_self_not_used, diag::warn_conv_to_void_not_used, -1,
  /* DiagArray161 */ diag::warn_pass_class_arg_to_vararg, -1,
  /* DiagArray162 */ diag::warn_cmse_nonsecure_union, -1,
  /* DiagArray163 */ diag::warn_comma_operator, -1,
  /* DiagArray164 */ diag::escaped_newline_block_comment_end, diag::ext_line_comment, diag::ext_multi_line_line_comment, diag::warn_nested_block_comment, -1,
  /* DiagArray166 */ diag::ext_typecheck_comparison_of_distinct_pointers, -1,
  /* DiagArray167 */ diag::warn_completion_handler_called_twice, diag::warn_completion_handler_never_called, diag::warn_completion_handler_never_called_when, -1,
  /* DiagArray168 */ diag::ext_complex_component_init, -1,
  /* DiagArray170 */ diag::warn_compound_token_split_by_macro, -1,
  /* DiagArray171 */ diag::warn_compound_token_split_by_whitespace, -1,
  /* DiagArray172 */ diag::ext_typecheck_cond_pointer_integer_mismatch, -1,
  /* DiagArray173 */ diag::warn_maybe_uninit_var, -1,
  /* DiagArray174 */ diag::warn_module_config_macro_undef, -1,
  /* DiagArray175 */ diag::warn_impcast_integer_precision_constant, -1,
  /* DiagArray176 */ diag::warn_is_constant_evaluated_always_true_constexpr, -1,
  /* DiagArray177 */ diag::warn_logical_instead_of_bitwise, -1,
  /* DiagArray178 */ diag::warn_cxx14_compat_constexpr_not_const, -1,
  /* DiagArray179 */ diag::warn_attr_on_unconsumable_class, diag::warn_loop_state_mismatch, diag::warn_param_return_typestate_mismatch, diag::warn_param_typestate_mismatch, diag::warn_return_typestate_for_unconsumable_type, diag::warn_return_typestate_mismatch, diag::warn_use_in_invalid_state, diag::warn_use_of_temp_in_invalid_state, -1,
  /* DiagArray180 */ diag::warn_impcast_complex_scalar, diag::warn_impcast_vector_scalar, diag::warn_opencl_generic_address_space_arg, diag::warn_template_arg_negative, diag::warn_template_arg_too_large, -1,
  /* DiagArray182 */ diag::warn_non_aligned_allocation_function, -1,
  /* DiagArray183 */ diag::warn_coroutine_handle_address_invalid_return_type, -1,
  /* DiagArray184 */ diag::warn_coroutine_promise_unhandled_exception_required_with_exceptions, -1,
  /* DiagArray185 */ diag::warn_unreachable_default, -1,
  /* DiagArray187 */ diag::warn_objc_cdirective_format_string, -1,
  /* DiagArray188 */ diag::warn_ctad_maybe_unsupported, -1,
  /* DiagArray190 */ diag::warn_ctu_incompat_triple, -1,
  /* DiagArray191 */ diag::warn_attribute_argument_n_negative, diag::warn_cuda_attr_lambda_position, diag::warn_kern_is_inline, diag::warn_kern_is_method, diag::warn_pragma_unroll_cuda_value_in_parens, -1,
  /* DiagArray192 */ diag::warn_default_atomic_custom_getter_setter, -1,
  /* DiagArray193 */ diag::ext_cxx11_attr_placement, -1,
  /* DiagArray194 */ diag::warn_dangling_variable, diag::warn_unsupported_lifetime_extension, -1,
  /* DiagArray195 */ diag::warn_dangling_else, -1,
  /* DiagArray196 */ diag::warn_bind_ref_member_to_parameter, diag::warn_dangling_member, diag::warn_init_ptr_member_to_parameter_addr, diag::warn_new_dangling_reference, -1,
  /* DiagArray197 */ diag::warn_dangling_lifetime_pointer, diag::warn_dangling_lifetime_pointer_member, -1,
  /* DiagArray198 */ diag::warn_new_dangling_initializer_list, -1,
  /* DiagArray199 */ diag::warn_drv_darwin_sdk_invalid_settings, -1,
  /* DiagArray200 */ diag::warn_pp_date_time, -1,
  /* DiagArray201 */ diag::warn_dealloc_in_category, -1,
  /* DiagArray202 */ diag::warn_debug_compression_unavailable, -1,
  /* DiagArray203 */ diag::ext_mixed_decls_code, diag::warn_mixed_decls_code, -1,
  /* DiagArray204 */ diag::warn_defaulted_comparison_deleted, diag::warn_defaulted_method_deleted, -1,
  /* DiagArray205 */ diag::warn_delegating_ctor_cycle, -1,
  /* DiagArray206 */ diag::warn_delete_abstract_non_virtual_dtor, -1,
  /* DiagArray207 */ diag::ext_delete_void_ptr_operand, diag::warn_delete_incomplete, -1,
  /* DiagArray208 */ diag::warn_delete_non_virtual_dtor, -1,
  /* DiagArray210 */ diag::ext_delimited_escape_sequence, -1,
  /* DiagArray211 */ diag::warn_deprecated_lax_vec_conv_all, -1,
  /* DiagArray212 */ diag::warn_O4_is_O3, diag::warn_access_decl_deprecated, diag::warn_drv_deprecated_arg, diag::warn_drv_treating_input_as_cxx, diag::warn_omp_depend_in_ordered_deprecated, diag::warn_omp_minus_in_reduction_deprecated, diag::warn_option_invalid_ocl_version, diag::warn_vector_long_decl_spec_combination, -1,
  /* DiagArray213 */ diag::warn_deprecated_altivec_src_compat, -1,
  /* DiagArray214 */ diag::warn_arith_conv_mixed_anon_enum_types_cxx20, -1,
  /* DiagArray215 */ diag::warn_depr_array_comparison, -1,
  /* DiagArray216 */ diag::warn_deprecated_noreturn_spelling, diag::warn_type_attribute_deprecated_on_decl, diag::warn_vector_mode_deprecated, -1,
  /* DiagArray217 */ diag::warn_deprecated_builtin, -1,
  /* DiagArray218 */ diag::warn_deprecated_comma_subscript, -1,
  /* DiagArray219 */ diag::warn_deprecated_copy, -1,
  /* DiagArray221 */ diag::warn_deprecated_copy_with_dtor, -1,
  /* DiagArray222 */ diag::warn_deprecated_copy_with_user_provided_copy, -1,
  /* DiagArray223 */ diag::warn_deprecated_copy_with_user_provided_dtor, -1,
  /* DiagArray224 */ diag::warn_deprecated_for_co_await, -1,
  /* DiagArray225 */ diag::warn_atl_uuid_deprecated, diag::warn_cstyle_param, diag::warn_deprecated, diag::warn_deprecated_fwdclass_message, diag::warn_deprecated_message, diag::warn_property_method_deprecated, -1,
  /* DiagArray226 */ diag::warn_exception_spec_deprecated, -1,
  /* DiagArray227 */ diag::warn_comparison_mixed_enum_types_cxx20, -1,
  /* DiagArray228 */ diag::warn_conditional_mixed_enum_types_cxx20, -1,
  /* DiagArray229 */ diag::warn_arith_conv_mixed_enum_types_cxx20, -1,
  /* DiagArray230 */ diag::warn_arith_conv_enum_float_cxx20, -1,
  /* DiagArray231 */ diag::warn_deprecated_def, diag::warn_unavailable_def, -1,
  /* DiagArray232 */ diag::warn_increment_bool, -1,
  /* DiagArray233 */ diag::warn_deprecated_literal_operator_id, -1,
  /* DiagArray234 */ diag::warn_non_prototype_changes_behavior, diag::warn_strict_uses_without_prototype, -1,
  /* DiagArray235 */ diag::warn_objc_isa_assign, diag::warn_objc_isa_use, -1,
  /* DiagArray236 */ diag::warn_objc_pointer_masking, -1,
  /* DiagArray237 */ diag::warn_objc_pointer_masking_performSelector, -1,
  /* DiagArray238 */ diag::warn_pragma_deprecated_macro_use, -1,
  /* DiagArray239 */ diag::warn_deprecated_redundant_constexpr_static_def, -1,
  /* DiagArray240 */ diag::warn_deprecated_register, -1,
  /* DiagArray241 */ diag::warn_analyzer_deprecated_option, diag::warn_analyzer_deprecated_option_with_alternative, -1,
  /* DiagArray242 */ diag::warn_deprecated_this_capture, -1,
  /* DiagArray243 */ diag::warn_ext_int_deprecated, -1,
  /* DiagArray244 */ diag::warn_deprecated_increment_decrement_volatile, diag::warn_deprecated_simple_assign_volatile, diag::warn_deprecated_volatile_param, diag::warn_deprecated_volatile_return, diag::warn_deprecated_volatile_structured_binding, -1,
  /* DiagArray246 */ diag::warn_direct_ivar_access, -1,
  /* DiagArray247 */ diag::pp_disabled_macro_expansion, -1,
  /* DiagArray250 */ diag::warn_conflicting_param_modifiers, diag::warn_conflicting_ret_type_modifiers, -1,
  /* DiagArray252 */ diag::warn_remainder_division_by_zero, -1,
  /* DiagArray253 */ diag::warn_attribute_dll_redeclaration, -1,
  /* DiagArray254 */ diag::warn_attribute_dllexport_explicit_instantiation_decl, -1,
  /* DiagArray255 */ diag::warn_attribute_dllimport_static_field_definition, -1,
  /* DiagArray256 */ diag::warn_doc_api_container_decl_mismatch, diag::warn_doc_block_command_duplicate, diag::warn_doc_block_command_empty_paragraph, diag::warn_doc_container_decl_mismatch, diag::warn_doc_function_method_decl_mismatch, diag::warn_doc_html_start_tag_expected_ident_or_greater, diag::warn_doc_html_start_tag_expected_quoted_string, diag::warn_doc_inline_command_not_enough_arguments, diag::warn_doc_param_duplicate, diag::warn_doc_param_invalid_direction, diag::warn_doc_param_not_attached_to_a_function_decl, diag::warn_doc_param_not_found, diag::warn_doc_returns_attached_to_a_void_function, diag::warn_doc_returns_not_attached_to_a_function_decl, diag::warn_doc_tparam_duplicate, diag::warn_doc_tparam_not_attached_to_a_template_decl, diag::warn_doc_tparam_not_found, diag::warn_not_a_doxygen_trailing_member_comment, diag::warn_splice_in_doxygen_comment, diag::warn_verbatim_block_end_without_start, -1,
  /* DiagArray257 */ diag::warn_doc_deprecated_not_sync, -1,
  /* DiagArray258 */ diag::warn_doc_html_end_forbidden, diag::warn_doc_html_end_unbalanced, diag::warn_doc_html_missing_end_tag, diag::warn_doc_html_start_end_mismatch, -1,
  /* DiagArray259 */ diag::warn_doc_param_spaces_in_direction, -1,
  /* DiagArray260 */ diag::warn_correct_comment_command_name, diag::warn_unknown_comment_command_name, -1,
  /* DiagArray261 */ diag::ext_dollar_in_identifier, -1,
  /* DiagArray262 */ diag::warn_impcast_double_promotion, -1,
  /* DiagArray263 */ diag::ext_dtor_name_ambiguous, diag::ext_dtor_named_in_wrong_scope, diag::ext_qualified_dtor_named_in_lexical_scope, -1,
  /* DiagArray264 */ diag::ext_destructor_typedef_name, -1,
  /* DiagArray265 */ diag::ext_duplicate_declspec, diag::ext_warn_duplicate_declspec, diag::warn_attribute_address_multiple_identical_qualifiers, diag::warn_duplicate_declspec, -1,
  /* DiagArray266 */ diag::warn_duplicate_enum_values, -1,
  /* DiagArray267 */ diag::warn_method_param_declaration, -1,
  /* DiagArray268 */ diag::warn_duplicate_method_decl, -1,
  /* DiagArray269 */ diag::warn_duplicate_protocol_def, -1,
  /* DiagArray270 */ diag::warn_drv_dxc_missing_dxv, -1,
  /* DiagArray271 */ diag::warn_dyn_class_memaccess, -1,
  /* DiagArray272 */ diag::ext_dynamic_exception_spec, -1,
  /* DiagArray273 */ diag::warn_eagerly_load_for_standard_cplusplus_modules, -1,
  /* DiagArray275 */ diag::ext_enum_base_in_type_specifier, -1,
  /* DiagArray276 */ diag::ext_elaborated_enum_class, -1,
  /* DiagArray277 */ diag::ext_embedded_directive, -1,
  /* DiagArray278 */ diag::warn_empty_for_body, diag::warn_empty_if_body, diag::warn_empty_range_based_for_body, diag::warn_empty_switch_body, diag::warn_empty_while_body, -1,
  /* DiagArray279 */ diag::ext_decomp_decl_empty, -1,
  /* DiagArray280 */ diag::warn_empty_init_statement, -1,
  /* DiagArray281 */ diag::ext_empty_translation_unit, -1,
  /* DiagArray282 */ diag::warn_incomplete_encoded_type, -1,
  /* DiagArray284 */ diag::warn_comparison_mixed_enum_types, -1,
  /* DiagArray285 */ diag::warn_conditional_mixed_enum_types, -1,
  /* DiagArray286 */ diag::warn_comparison_of_mixed_enum_types_switch, -1,
  /* DiagArray287 */ diag::warn_constexpr_unscoped_enum_out_of_range, -1,
  /* DiagArray288 */ diag::warn_impcast_different_enum_types, -1,
  /* DiagArray289 */ diag::warn_arith_conv_mixed_enum_types, -1,
  /* DiagArray290 */ diag::warn_arith_conv_enum_float, -1,
  /* DiagArray291 */ diag::ext_enum_too_large, diag::ext_enumerator_increment_too_large, -1,
  /* DiagArray292 */ diag::warn_cdtor_function_try_handler_mem_expr, diag::warn_exception_caught_by_earlier_handler, diag::warn_throw_in_noexcept_func, -1,
  /* DiagArray293 */ diag::ext_excess_initializers, diag::ext_excess_initializers_for_sizeless_type, diag::ext_excess_initializers_in_char_array_initializer, diag::ext_initializer_string_for_char_array_too_long, -1,
  /* DiagArray294 */ diag::warn_anyx86_excessive_regsave, -1,
  /* DiagArray295 */ diag::warn_exit_time_destructor, -1,
  /* DiagArray296 */ diag::warn_defined_in_function_type_macro, diag::warn_defined_in_object_type_macro, -1,
  /* DiagArray297 */ diag::warn_experimental_header_unit, -1,
  /* DiagArray298 */ diag::warn_direct_initialize_call, diag::warn_direct_super_initialize_call, -1,
  /* DiagArray299 */ diag::warn_arc_strong_pointer_objc_pointer, -1,
  /* DiagArray301 */ diag::warn_zero_size_struct_union_in_extern_c, -1,
  /* DiagArray302 */ diag::warn_extern_init, -1,
  /* DiagArray303 */ diag::warn_arm_interrupt_calling_convention, -1,
  /* DiagArray304 */ diag::warn_namespace_member_extra_qualification, -1,
  /* DiagArray305 */ diag::ext_extra_semi, diag::warn_extra_semi_after_mem_fn_def, -1,
  /* DiagArray306 */ diag::warn_null_statement, -1,
  /* DiagArray307 */ diag::ext_pp_extra_tokens_at_eol, diag::warn_omp_extra_tokens_at_eol, -1,
  /* DiagArray308 */ diag::warn_final_dtor_non_final_class, -1,
  /* DiagArray309 */ diag::warn_pragma_final_macro, -1,
  /* DiagArray310 */ diag::ext_clang_c_enum_fixed_underlying_type, -1,
  /* DiagArray311 */ diag::warn_fixedpoint_constant_overflow, -1,
  /* DiagArray312 */ diag::warn_flag_enum_constant_out_of_range, -1,
  /* DiagArray313 */ diag::ext_flexible_array_in_array, diag::ext_flexible_array_in_struct, -1,
  /* DiagArray314 */ diag::warn_impcast_float_integer, -1,
  /* DiagArray315 */ diag::warn_floatingpoint_eq, -1,
  /* DiagArray316 */ diag::warn_impcast_float_to_integer, diag::warn_impcast_float_to_integer_out_of_range, -1,
  /* DiagArray317 */ diag::warn_impcast_float_to_integer_zero, -1,
  /* DiagArray318 */ diag::warn_redundant_loop_iteration, diag::warn_variables_not_in_loop_body, -1,
  /* DiagArray319 */ diag::warn_format_P_no_precision, diag::warn_format_argument_needs_cast, diag::warn_format_bool_as_character, diag::warn_format_conversion_argument_type_mismatch, diag::warn_format_invalid_annotation, diag::warn_format_invalid_positional_specifier, diag::warn_format_mix_positional_nonpositional_args, diag::warn_format_nonsensical_length, diag::warn_format_string_is_wide_literal, diag::warn_format_zero_positional_specifier, diag::warn_missing_format_string, diag::warn_printf_ObjCflags_without_ObjCConversion, diag::warn_printf_asterisk_missing_arg, diag::warn_printf_asterisk_wrong_type, diag::warn_printf_empty_objc_flag, diag::warn_printf_format_string_contains_null_char, diag::warn_printf_format_string_not_null_terminated, diag::warn_printf_ignored_flag, diag::warn_printf_incomplete_specifier, diag::warn_printf_invalid_objc_flag, diag::warn_printf_narg_not_supported, diag::warn_printf_nonsensical_flag, diag::warn_printf_nonsensical_optional_amount, diag::warn_printf_positional_arg_exceeds_data_args, diag::warn_scanf_nonzero_width, diag::warn_scanf_scanlist_incomplete, -1,
  /* DiagArray320 */ diag::warn_printf_data_arg_not_used, -1,
  /* DiagArray321 */ diag::warn_printf_insufficient_data_args, -1,
  /* DiagArray322 */ diag::warn_format_invalid_conversion, -1,
  /* DiagArray323 */ diag::warn_format_non_standard, diag::warn_format_non_standard_conversion_spec, diag::warn_format_non_standard_positional_arg, -1,
  /* DiagArray324 */ diag::warn_format_nonliteral, -1,
  /* DiagArray325 */ diag::warn_format_argument_needs_cast_pedantic, diag::warn_format_conversion_argument_type_mismatch_pedantic, -1,
  /* DiagArray326 */ diag::warn_format_nonliteral_noargs, -1,
  /* DiagArray327 */ diag::warn_format_conversion_argument_type_mismatch_confusion, -1,
  /* DiagArray329 */ diag::warn_empty_format_string, -1,
  /* DiagArray331 */ diag::warn_fortify_scanf_overflow, diag::warn_fortify_source_format_overflow, diag::warn_fortify_source_format_truncation, diag::warn_fortify_source_overflow, diag::warn_fortify_source_size_mismatch, diag::warn_fortify_strlen_overflow, -1,
  /* DiagArray332 */ diag::warn_four_char_character_literal, -1,
  /* DiagArray333 */ diag::warn_frame_address, -1,
  /* DiagArray334 */ diag::warn_fe_backend_frame_larger_than, diag::warn_fe_frame_larger_than, -1,
  /* DiagArray336 */ diag::warn_framework_include_private_from_public, -1,
  /* DiagArray337 */ diag::warn_free_nonheap_object, -1,
  /* DiagArray338 */ diag::warn_function_def_in_objc_container, -1,
  /* DiagArray339 */ diag::warn_dispatch_body_ignored, diag::warn_multiversion_duplicate_entries, diag::warn_target_clone_duplicate_options, diag::warn_target_clone_no_impact_options, -1,
  /* DiagArray340 */ diag::warn_drv_fuse_ld_path, -1,
  /* DiagArray343 */ diag::ext_clang_diagnose_if, diag::ext_clang_enable_if, diag::ext_warn_gnu_final, diag::warn_attribute_on_function_definition, diag::warn_break_binds_to_switch, diag::warn_cleanup_ext, diag::warn_gcc_attribute_location, diag::warn_gcc_ignores_type_attr, diag::warn_gcc_requires_variadic_function, diag::warn_gcc_variable_decl_in_for_loop, diag::warn_loop_ctrl_binds_to_inner, -1,
  /* DiagArray344 */ diag::ext_generic_with_type_arg, -1,
  /* DiagArray345 */ diag::warn_global_constructor, diag::warn_global_destructor, -1,
  /* DiagArray346 */ diag::warn_drv_global_isel_incomplete, diag::warn_drv_global_isel_incomplete_opt, -1,
  /* DiagArray348 */ diag::ext_alignof_expr, -1,
  /* DiagArray349 */ diag::ext_gnu_anonymous_struct, -1,
  /* DiagArray350 */ diag::ext_array_init_parens, -1,
  /* DiagArray351 */ diag::ext_auto_type, -1,
  /* DiagArray352 */ diag::ext_binary_literal, -1,
  /* DiagArray353 */ diag::ext_gnu_case_range, -1,
  /* DiagArray354 */ diag::ext_integer_complex, -1,
  /* DiagArray355 */ diag::ext_array_init_copy, -1,
  /* DiagArray356 */ diag::ext_gnu_conditional_expr, -1,
  /* DiagArray357 */ diag::ext_gnu_array_range, diag::ext_gnu_missing_equal_designator, diag::ext_gnu_old_style_field_designator, -1,
  /* DiagArray359 */ diag::ext_empty_struct_union, diag::ext_flexible_array_empty_aggregate_gnu, diag::ext_no_named_members_in_struct_union, -1,
  /* DiagArray360 */ diag::ext_flexible_array_init, -1,
  /* DiagArray361 */ diag::ext_flexible_array_union_gnu, -1,
  /* DiagArray362 */ diag::ext_expr_not_ice, diag::ext_in_class_initializer_non_constant, diag::ext_vla_folded_to_constant, -1,
  /* DiagArray363 */ diag::ext_imaginary_constant, -1,
  /* DiagArray364 */ diag::ext_pp_include_next_directive, -1,
  /* DiagArray365 */ diag::warn_gnu_inline_cplusplus_without_extern, -1,
  /* DiagArray366 */ diag::ext_gnu_address_of_label, diag::ext_gnu_indirect_goto, -1,
  /* DiagArray367 */ diag::ext_pp_gnu_line_directive, -1,
  /* DiagArray368 */ diag::warn_gnu_null_ptr_arith, -1,
  /* DiagArray369 */ diag::ext_type_defined_in_offsetof, -1,
  /* DiagArray370 */ diag::ext_gnu_ptr_func_arith, diag::ext_gnu_subscript_void_type, diag::ext_gnu_void_ptr, -1,
  /* DiagArray371 */ diag::ext_forward_ref_enum_def, -1,
  /* DiagArray372 */ diag::ext_gnu_statement_expr, -1,
  /* DiagArray373 */ diag::ext_gnu_statement_expr_macro, -1,
  /* DiagArray374 */ diag::ext_in_class_initializer_float_type, -1,
  /* DiagArray375 */ diag::ext_string_literal_operator_template, -1,
  /* DiagArray376 */ diag::ext_typecheck_cast_to_union, -1,
  /* DiagArray377 */ diag::ext_variable_sized_type_in_struct, -1,
  /* DiagArray378 */ diag::ext_pp_line_zero, -1,
  /* DiagArray379 */ diag::ext_missing_varargs_arg, diag::ext_paste_comma, -1,
  /* DiagArray380 */ diag::warn_maybe_capture_bad_target_this_ptr, -1,
  /* DiagArray381 */ diag::warn_header_guard, -1,
  /* DiagArray382 */ diag::warn_using_directive_in_header, -1,
  /* DiagArray383 */ diag::warn_hip_omp_target_directives, -1,
  /* DiagArray384 */ diag::warn_ignored_hip_only_option, -1,
  /* DiagArray385 */ diag::ext_hlsl_access_specifiers, -1,
  /* DiagArray386 */ diag::warn_condition_is_idiomatic_assignment, -1,
  /* DiagArray387 */ diag::ext_cannot_use_trivial_abi, diag::warn_alias_to_weak_alias, diag::warn_alias_with_section, diag::warn_aligned_attr_underaligned, diag::warn_attr_abi_tag_namespace, diag::warn_attribute_after_definition_ignored, diag::warn_attribute_cmse_entry_static, diag::warn_attribute_dllexport_explicit_instantiation_def, diag::warn_attribute_has_no_effect_on_compile_time_if, diag::warn_attribute_has_no_effect_on_infinite_loop, diag::warn_attribute_iboutlet, diag::warn_attribute_ignored, diag::warn_attribute_ignored_for_field_of_type, diag::warn_attribute_ignored_no_calls_in_stmt, diag::warn_attribute_ignored_non_function_pointer, diag::warn_attribute_ignored_on_inline, diag::warn_attribute_ignored_on_non_definition, diag::warn_attribute_invalid_on_definition, diag::warn_attribute_no_decl, diag::warn_attribute_nonnull_no_pointers, diag::warn_attribute_nonnull_parm_no_args, diag::warn_attribute_not_on_decl, diag::warn_attribute_pointer_or_reference_only, diag::warn_attribute_pointers_only, diag::warn_attribute_precede_definition, diag::warn_attribute_return_pointers_only, diag::warn_attribute_return_pointers_refs_only, diag::warn_attribute_sentinel_named_arguments, diag::warn_attribute_sentinel_not_variadic, diag::warn_attribute_type_not_supported, diag::warn_attribute_type_not_supported_global, diag::warn_attribute_unknown_visibility, diag::warn_attribute_void_function_method, diag::warn_attribute_weak_on_field, diag::warn_attribute_weak_on_local, diag::warn_attribute_wrong_decl_type, diag::warn_attribute_wrong_decl_type_str, diag::warn_attributes_likelihood_ifstmt_conflict, diag::warn_block_literal_attributes_on_omitted_return_type, diag::warn_cconv_unsupported, diag::warn_cxx11_gnu_attribute_on_type, diag::warn_declspec_allocator_nonpointer, diag::warn_declspec_attribute_ignored, diag::warn_deprecated_anonymous_namespace, diag::warn_deprecated_ignored_on_using, diag::warn_dllimport_dropped_from_inline_function, diag::warn_duplicate_attribute, diag::warn_duplicate_attribute_exact, diag::warn_function_attribute_ignored_in_stmt, diag::warn_function_stmt_attribute_precedence, diag::warn_gc_attribute_weak_on_local, diag::warn_gnu_inline_attribute_requires_inline, diag::warn_ignored_ms_inheritance, diag::warn_ignored_objc_externally_retained, diag::warn_import_on_definition, diag::warn_internal_linkage_local_storage, diag::warn_interrupt_attribute_invalid, diag::warn_microsoft_qualifiers_ignored, diag::warn_mig_server_routine_does_not_return_kern_return_t, diag::warn_mismatched_import, diag::warn_mmap_unknown_attribute, diag::warn_nocf_check_attribute_ignored, diag::warn_noderef_on_non_pointer_or_array, diag::warn_nothrow_attribute_ignored, diag::warn_ns_attribute_wrong_parameter_type, diag::warn_ns_attribute_wrong_return_type, diag::warn_objc_direct_ignored, diag::warn_objc_direct_property_ignored, diag::warn_opencl_attr_deprecated_ignored, diag::warn_require_const_init_added_too_late, diag::warn_riscv_repeated_interrupt_attribute, diag::warn_sycl_kernel_invalid_template_param_type, diag::warn_sycl_kernel_num_of_function_params, diag::warn_sycl_kernel_num_of_template_params, diag::warn_sycl_kernel_return_type, diag::warn_transparent_union_attribute_field_size_align, diag::warn_transparent_union_attribute_floating, diag::warn_transparent_union_attribute_not_definition, diag::warn_transparent_union_attribute_zero_fields, diag::warn_type_attribute_wrong_type, diag::warn_unhandled_ms_attribute_ignored, diag::warn_unsupported_target_attribute, diag::warn_unused_result_typedef_unsupported_spelling, diag::warn_wrong_clang_attr_namespace, -1,
  /* DiagArray388 */ diag::warn_missing_sdksettings_for_availability_checking, -1,
  /* DiagArray389 */ diag::warn_drv_unsupported_opt_for_target, diag::warn_ignored_gcc_optimization, -1,
  /* DiagArray390 */ diag::warn_pragma_intrinsic_builtin, -1,
  /* DiagArray392 */ diag::warn_clause_expected_string, diag::warn_pragma_align_expected_equal, diag::warn_pragma_align_invalid_option, diag::warn_pragma_comment_ignored, diag::warn_pragma_debug_dependent_argument, diag::warn_pragma_debug_missing_argument, diag::warn_pragma_debug_missing_command, diag::warn_pragma_debug_unexpected_argument, diag::warn_pragma_debug_unexpected_command, diag::warn_pragma_debug_unknown_module, diag::warn_pragma_expected_action_or_r_paren, diag::warn_pragma_expected_colon, diag::warn_pragma_expected_colon_r_paren, diag::warn_pragma_expected_comma, diag::warn_pragma_expected_identifier, diag::warn_pragma_expected_init_seg, diag::warn_pragma_expected_integer, diag::warn_pragma_expected_lparen, diag::warn_pragma_expected_non_wide_string, diag::warn_pragma_expected_predicate, diag::warn_pragma_expected_punc, diag::warn_pragma_expected_rparen, diag::warn_pragma_expected_section_label_or_name, diag::warn_pragma_expected_section_name, diag::warn_pragma_expected_section_push_pop_or_name, diag::warn_pragma_expected_string, diag::warn_pragma_extra_tokens_at_eol, diag::warn_pragma_force_cuda_host_device_bad_arg, diag::warn_pragma_fp_ignored, diag::warn_pragma_init_seg_unsupported_target, diag::warn_pragma_invalid_action, diag::warn_pragma_invalid_argument, diag::warn_pragma_invalid_specific_action, diag::warn_pragma_missing_argument, diag::warn_pragma_ms_fenv_access, diag::warn_pragma_ms_struct, diag::warn_pragma_options_align_reset_failed, diag::warn_pragma_options_expected_align, diag::warn_pragma_pack_invalid_alignment, diag::warn_pragma_pack_malformed, diag::warn_pragma_pop_failed, diag::warn_pragma_pop_macro_no_push, diag::warn_pragma_unknown_extension, diag::warn_pragma_unsupported_action, diag::warn_pragma_unsupported_extension, diag::warn_pragma_unused_expected_var, diag::warn_pragma_unused_expected_var_arg, diag::warn_pragma_unused_undeclared_var, diag::warn_stdc_unknown_rounding_mode, -1,
  /* DiagArray393 */ diag::warn_arc_lifetime_result_type, diag::warn_block_literal_qualifiers_on_omitted_return_type, diag::warn_qual_return_type, diag::warn_typecheck_function_qualifiers_ignored, -1,
  /* DiagArray394 */ diag::warn_typecheck_reference_qualifiers, -1,
  /* DiagArray396 */ diag::warn_auto_implicit_atomic_property, diag::warn_implicit_atomic_property, -1,
  /* DiagArray397 */ diag::warn_impcast_integer_float_precision_constant, -1,
  /* DiagArray398 */ diag::warn_impcast_floating_point_to_bool, -1,
  /* DiagArray399 */ diag::ext_implicit_exception_spec_mismatch, -1,
  /* DiagArray400 */ diag::warn_unannotated_fallthrough, -1,
  /* DiagArray401 */ diag::warn_unannotated_fallthrough_per_function, -1,
  /* DiagArray402 */ diag::warn_impcast_fixed_point_range, -1,
  /* DiagArray403 */ diag::warn_impcast_float_precision, diag::warn_impcast_float_result_precision, -1,
  /* DiagArray404 */ diag::ext_implicit_function_decl_c99, diag::ext_implicit_lib_function_decl, diag::ext_implicit_lib_function_decl_c99, diag::warn_builtin_unknown, diag::warn_implicit_function_decl, -1,
  /* DiagArray405 */ diag::ext_missing_type_specifier, diag::ext_param_not_declared, diag::warn_missing_type_specifier, -1,
  /* DiagArray406 */ diag::warn_impcast_high_order_zero_bits, diag::warn_impcast_integer_precision, -1,
  /* DiagArray407 */ diag::warn_impcast_integer_float_precision, -1,
  /* DiagArray408 */ diag::warn_implicitly_retains_self, -1,
  /* DiagArray409 */ diag::ext_integer_literal_too_large_for_signed, -1,
  /* DiagArray411 */ diag::ext_pp_import_directive, -1,
  /* DiagArray412 */ diag::warn_inaccessible_base_class, -1,
  /* DiagArray413 */ diag::pp_include_next_absolute_path, -1,
  /* DiagArray414 */ diag::pp_include_next_in_primary, -1,
  /* DiagArray415 */ diag::warn_deep_exception_specs_differ, diag::warn_incompatible_exception_specs, -1,
  /* DiagArray416 */ diag::ext_typecheck_convert_incompatible_function_pointer, -1,
  /* DiagArray417 */ diag::warn_typecheck_convert_incompatible_function_pointer_strict, -1,
  /* DiagArray418 */ diag::warn_redecl_library_builtin, -1,
  /* DiagArray419 */ diag::warn_section_msvc_compat, -1,
  /* DiagArray420 */ diag::warn_cxx_ms_struct, diag::warn_npot_ms_struct, -1,
  /* DiagArray421 */ diag::ext_typecheck_convert_incompatible_pointer, -1,
  /* DiagArray422 */ diag::ext_nested_pointer_qualifier_mismatch, diag::ext_typecheck_convert_discards_qualifiers, diag::warn_bad_cxx_cast_nested_pointer_addr_space, -1,
  /* DiagArray423 */ diag::warn_property_types_are_incompatible, -1,
  /* DiagArray424 */ diag::warn_incompatible_sysroot, -1,
  /* DiagArray425 */ diag::warn_mmap_incomplete_framework_module_declaration, -1,
  /* DiagArray426 */ diag::warn_undef_method_impl, -1,
  /* DiagArray428 */ diag::warn_implicit_decl_no_jmp_buf, -1,
  /* DiagArray429 */ diag::warn_missing_submodule, diag::warn_mmap_umbrella_dir_not_found, diag::warn_uncovered_module_header, -1,
  /* DiagArray430 */ diag::warn_redeclaration_without_attribute_prev_attribute_ignored, diag::warn_redeclaration_without_import_attribute, -1,
  /* DiagArray431 */ diag::warn_inconsistent_destructor_marked_not_override_overriding, -1,
  /* DiagArray432 */ diag::warn_inconsistent_function_marked_not_override_overriding, -1,
  /* DiagArray433 */ diag::ext_increment_bool, -1,
  /* DiagArray434 */ diag::warn_infinite_recursive_function, -1,
  /* DiagArray436 */ diag::ext_initializer_overrides, diag::ext_initializer_union_overrides, diag::warn_initializer_overrides, -1,
  /* DiagArray437 */ diag::ext_out_of_line_qualified_id_type_names_constructor, -1,
  /* DiagArray439 */ diag::warn_fe_inline_asm, -1,
  /* DiagArray440 */ diag::warn_inline_namespace_reopened_noninline, -1,
  /* DiagArray441 */ diag::ext_operator_new_delete_declared_inline, -1,
  /* DiagArray442 */ diag::warn_explicit_instantiation_after_specialization, -1,
  /* DiagArray443 */ diag::ext_typecheck_convert_int_pointer, diag::ext_typecheck_convert_pointer_int, -1,
  /* DiagArray445 */ diag::warn_enum_constant_in_bool_context, diag::warn_left_shift_in_bool_context, -1,
  /* DiagArray446 */ diag::warn_int_to_pointer_cast, -1,
  /* DiagArray447 */ diag::warn_int_to_void_pointer_cast, -1,
  /* DiagArray448 */ diag::warn_integer_constant_overflow, -1,
  /* DiagArray449 */ diag::warn_drv_missing_plugin_arg, diag::warn_drv_missing_plugin_name, diag::warn_drv_msp430_hwmult_mismatch, diag::warn_drv_msp430_hwmult_no_device, diag::warn_drv_msp430_hwmult_unsupported, diag::warn_drv_object_size_disabled_O0, diag::warn_drv_optimization_value, diag::warn_fe_backend_invalid_feature_flag, diag::warn_fe_backend_readonly_feature_flag, diag::warn_target_unrecognized_env, diag::warn_target_unsupported_extension, -1,
  /* DiagArray450 */ diag::ext_constexpr_function_never_constant_expr, -1,
  /* DiagArray451 */ diag::warn_iboutlet_object_type, diag::warn_iboutletcollection_property_assign, -1,
  /* DiagArray452 */ diag::warn_invalid_initializer_from_system_header, -1,
  /* DiagArray453 */ diag::warn_invalid_ios_deployment_target, -1,
  /* DiagArray454 */ diag::warn_attribute_no_builtin_invalid_builtin_name, -1,
  /* DiagArray455 */ diag::warn_falloff_noreturn_function, diag::warn_noreturn_function_has_return_expr, -1,
  /* DiagArray456 */ diag::ext_offsetof_non_pod_type, diag::ext_offsetof_non_standardlayout_type, -1,
  /* DiagArray457 */ diag::warn_drv_unable_to_find_directory_expected, -1,
  /* DiagArray458 */ diag::ext_partial_spec_not_more_specialized_than_primary, -1,
  /* DiagArray460 */ diag::ext_empty_character, diag::ext_unterminated_char_or_string, -1,
  /* DiagArray461 */ diag::warn_bad_character_encoding, diag::warn_bad_string_encoding, -1,
  /* DiagArray462 */ diag::warn_static_assert_message_constexpr, -1,
  /* DiagArray463 */ diag::ext_pp_bad_paste_ms, -1,
  /* DiagArray464 */ diag::warn_unevaluated_string_prefix, -1,
  /* DiagArray465 */ diag::warn_invalid_utf8_in_comment, -1,
  /* DiagArray466 */ diag::warn_jump_out_of_seh_finally, -1,
  /* DiagArray467 */ diag::ext_keyword_as_ident, -1,
  /* DiagArray468 */ diag::warn_pp_macro_hides_keyword, -1,
  /* DiagArray469 */ diag::ext_param_promoted_not_compatible_with_prototype, -1,
  /* DiagArray470 */ diag::ext_token_used, -1,
  /* DiagArray471 */ diag::warn_parameter_size, diag::warn_return_value_size, -1,
  /* DiagArray473 */ diag::warn_fe_linking_module, -1,
  /* DiagArray474 */ diag::warn_impcast_literal_float_to_integer, diag::warn_impcast_literal_float_to_integer_out_of_range, -1,
  /* DiagArray475 */ diag::warn_float_compare_literal, diag::warn_float_overflow, diag::warn_float_underflow, -1,
  /* DiagArray476 */ diag::ext_template_arg_local_type, -1,
  /* DiagArray477 */ diag::warn_logical_not_on_lhs_of_check, -1,
  /* DiagArray478 */ diag::warn_logical_and_in_logical_or, -1,
  /* DiagArray479 */ diag::ext_c99_longlong, -1,
  /* DiagArray481 */ diag::ext_pp_macro_redef, -1,
  /* DiagArray482 */ diag::ext_main_used, diag::ext_noreturn_main, diag::ext_variadic_main, diag::warn_main_one_arg, diag::warn_main_redefined, diag::warn_main_returns_bool_literal, diag::warn_static_main, -1,
  /* DiagArray483 */ diag::ext_main_returns_nonint, -1,
  /* DiagArray484 */ diag::warn_has_warning_invalid_option, -1,
  /* DiagArray485 */ diag::ext_many_braces_around_init, -1,
  /* DiagArray486 */ diag::ext_mathematical_notation, -1,
  /* DiagArray487 */ diag::warn_max_tokens, diag::warn_max_tokens_total, -1,
  /* DiagArray488 */ diag::warn_max_unsigned_zero, -1,
  /* DiagArray489 */ diag::warn_suspicious_sizeof_memset, -1,
  /* DiagArray490 */ diag::warn_memsize_comparison, -1,
  /* DiagArray491 */ diag::warn_non_contravariant_param_types, diag::warn_non_covariant_ret_types, -1,
  /* DiagArray493 */ diag::ext_ms_abstract_keyword, -1,
  /* DiagArray494 */ diag::ext_anonymous_record_with_type, diag::ext_ms_anonymous_record, -1,
  /* DiagArray495 */ diag::ext_ms_cast_fn_obj, diag::ext_ms_impcast_fn_obj, -1,
  /* DiagArray496 */ diag::ext_charize_microsoft, -1,
  /* DiagArray497 */ diag::ext_comment_paste_microsoft, -1,
  /* DiagArray498 */ diag::ext_default_init_const, -1,
  /* DiagArray499 */ diag::ext_pp_operator_used_as_macro_name, -1,
  /* DiagArray500 */ diag::ext_param_default_argument_redefinition, -1,
  /* DiagArray501 */ diag::warn_attribute_section_drectve, -1,
  /* DiagArray502 */ diag::ext_ctrl_z_eof_microsoft, -1,
  /* DiagArray503 */ diag::ext_ms_forward_ref_enum, -1,
  /* DiagArray504 */ diag::ext_enumerator_too_large, -1,
  /* DiagArray505 */ diag::ext_ellipsis_exception_spec, diag::ext_incomplete_in_exception_spec, diag::ext_mismatched_exception_spec, diag::ext_mismatched_exception_spec_explicit_instantiation, diag::ext_override_exception_spec, -1,
  /* DiagArray506 */ diag::warn_microsoft_dependent_exists, -1,
  /* DiagArray507 */ diag::ext_ms_explicit_constructor_call, -1,
  /* DiagArray508 */ diag::warn_member_extra_qualification, -1,
  /* DiagArray509 */ diag::ext_ms_c_enum_fixed_underlying_type, -1,
  /* DiagArray510 */ diag::ext_flexible_array_empty_aggregate_ms, diag::ext_flexible_array_union_ms, -1,
  /* DiagArray511 */ diag::ext_goto_into_protected_scope, -1,
  /* DiagArray512 */ diag::ext_ms_ambiguous_direct_base, -1,
  /* DiagArray513 */ diag::ext_pp_include_search_ms, -1,
  /* DiagArray514 */ diag::ext_init_from_predefined, -1,
  /* DiagArray515 */ diag::ext_mutable_reference, -1,
  /* DiagArray516 */ diag::ext_pure_function_definition, -1,
  /* DiagArray517 */ diag::ext_static_non_static, -1,
  /* DiagArray518 */ diag::ext_ms_sealed_keyword, -1,
  /* DiagArray519 */ diag::ext_ms_static_assert, -1,
  /* DiagArray520 */ diag::ext_string_literal_from_predefined, -1,
  /* DiagArray521 */ diag::ext_explicit_instantiation_duplicate, diag::ext_found_in_dependent_base, diag::ext_found_later_in_class, diag::ext_ms_delayed_template_argument, diag::ext_ms_deref_template_argument, diag::ext_ms_template_spec_redecl_out_of_scope, diag::ext_ms_template_type_arg_missing_typename, diag::ext_static_out_of_line, diag::ext_undeclared_unqual_id_with_dependent_base, diag::ext_unqualified_base_class, -1,
  /* DiagArray522 */ diag::ext_template_param_shadow, -1,
  /* DiagArray523 */ diag::ext_union_member_of_reference_type, -1,
  /* DiagArray524 */ diag::ext_friend_tag_redecl_outside_namespace, -1,
  /* DiagArray525 */ diag::ext_ms_using_declaration_inaccessible, -1,
  /* DiagArray526 */ diag::ext_pseudo_dtor_on_void, -1,
  /* DiagArray527 */ diag::warn_profile_data_misexpect, -1,
  /* DiagArray528 */ diag::warn_misleading_indentation, -1,
  /* DiagArray529 */ diag::warn_mismatched_delete_new, -1,
  /* DiagArray530 */ diag::warn_conflicting_param_types, -1,
  /* DiagArray531 */ diag::warn_conflicting_ret_types, -1,
  /* DiagArray532 */ diag::warn_struct_class_previous_tag_mismatch, diag::warn_struct_class_tag_mismatch, -1,
  /* DiagArray533 */ diag::warn_missing_braces, -1,
  /* DiagArray534 */ diag::ext_constinit_missing, -1,
  /* DiagArray535 */ diag::ext_no_declarators, diag::ext_standalone_specifier, diag::ext_typedef_without_a_name, diag::warn_standalone_specifier, -1,
  /* DiagArray536 */ diag::ext_missing_exception_specification, -1,
  /* DiagArray537 */ diag::warn_missing_field_initializers, -1,
  /* DiagArray540 */ diag::warn_missing_method_return_type, -1,
  /* DiagArray541 */ diag::warn_drv_missing_multilib, -1,
  /* DiagArray542 */ diag::warn_overriding_method_missing_noescape, -1,
  /* DiagArray543 */ diag::warn_suggest_noreturn_block, diag::warn_suggest_noreturn_function, -1,
  /* DiagArray544 */ diag::warn_cconv_knr, -1,
  /* DiagArray545 */ diag::warn_missing_prototype, -1,
  /* DiagArray546 */ diag::warn_missing_selector_name, -1,
  /* DiagArray547 */ diag::warn_missing_sysroot, -1,
  /* DiagArray548 */ diag::warn_missing_variable_declarations, -1,
  /* DiagArray549 */ diag::warn_assume_attribute_string_unknown_suggested, -1,
  /* DiagArray550 */ diag::remark_module_build, diag::remark_module_build_done, diag::remark_module_lock_failure, diag::remark_module_lock_timeout, -1,
  /* DiagArray551 */ diag::warn_module_conflict, diag::warn_module_system_bit_conflict, -1,
  /* DiagArray552 */ diag::warn_module_config_mismatch, -1,
  /* DiagArray553 */ diag::warn_duplicate_module_file_extension, -1,
  /* DiagArray554 */ diag::remark_module_import, -1,
  /* DiagArray555 */ diag::ext_module_import_in_extern_c, -1,
  /* DiagArray556 */ diag::remark_pp_include_directive_modular_translation, -1,
  /* DiagArray557 */ diag::remark_module_lock, -1,
  /* DiagArray558 */ diag::ext_equivalent_internal_linkage_decl_in_modules, -1,
  /* DiagArray559 */ diag::ext_module_import_not_at_top_level_noop, -1,
  /* DiagArray563 */ diag::warn_drv_msvc_not_found, -1,
  /* DiagArray564 */ diag::warn_drv_multi_gpu_arch, -1,
  /* DiagArray565 */ diag::warn_multichar_character_literal, -1,
  /* DiagArray566 */ diag::warn_vbase_moved_multiple_times, -1,
  /* DiagArray568 */ diag::ext_anonymous_record_with_anonymous_type, -1,
  /* DiagArray570 */ diag::warn_operator_new_returns_null, -1,
  /* DiagArray571 */ diag::ext_no_newline_eof, diag::warn_no_newline_eof, -1,
  /* DiagArray572 */ diag::warn_dereference_of_noderef_type, diag::warn_dereference_of_noderef_type_no_decl, diag::warn_noderef_to_dereferenceable_pointer, -1,
  /* DiagArray574 */ diag::ext_non_c_like_anon_struct_in_typedef, -1,
  /* DiagArray576 */ diag::warn_non_literal_null_pointer, -1,
  /* DiagArray577 */ diag::warn_non_modular_include_in_framework_module, -1,
  /* DiagArray578 */ diag::warn_non_modular_include_in_module, -1,
  /* DiagArray579 */ diag::warn_cannot_pass_non_pod_arg_to_vararg, diag::warn_non_pod_vararg_with_format_string, diag::warn_second_parameter_to_va_arg_not_pod, diag::warn_second_parameter_to_va_arg_ownership_qualified, -1,
  /* DiagArray580 */ diag::warn_alignment_not_power_of_two, -1,
  /* DiagArray581 */ diag::warn_non_virtual_dtor, -1,
  /* DiagArray582 */ diag::warn_null_arg, diag::warn_null_ret, -1,
  /* DiagArray584 */ diag::pp_nonportable_path, -1,
  /* DiagArray585 */ diag::pp_nonportable_system_path, -1,
  /* DiagArray586 */ diag::warn_neon_vector_initializer_non_portable, -1,
  /* DiagArray587 */ diag::warn_cstruct_memaccess, -1,
  /* DiagArray588 */ diag::warn_nsconsumed_attribute_mismatch, -1,
  /* DiagArray589 */ diag::warn_nsreturns_retained_attribute_mismatch, -1,
  /* DiagArray590 */ diag::warn_null_in_arithmetic_operation, diag::warn_null_in_comparison_operation, -1,
  /* DiagArray591 */ diag::null_in_char_or_string, diag::null_in_file, -1,
  /* DiagArray592 */ diag::warn_impcast_null_pointer_to_integer, -1,
  /* DiagArray593 */ diag::warn_binding_null_to_reference, diag::warn_indirection_through_null, -1,
  /* DiagArray594 */ diag::warn_pointer_arith_null_ptr, -1,
  /* DiagArray595 */ diag::warn_pointer_sub_null_ptr, -1,
  /* DiagArray596 */ diag::warn_conflicting_nullability_attr_overriding_param_types, diag::warn_conflicting_nullability_attr_overriding_ret_types, diag::warn_mismatched_nullability_attr, diag::warn_null_resettable_setter, diag::warn_nullability_duplicate, -1,
  /* DiagArray597 */ diag::warn_nullability_missing, -1,
  /* DiagArray598 */ diag::warn_nullability_missing_array, -1,
  /* DiagArray599 */ diag::warn_nullability_declspec, -1,
  /* DiagArray600 */ diag::ext_nullability, -1,
  /* DiagArray601 */ diag::warn_nullability_inferred_on_nested_type, -1,
  /* DiagArray602 */ diag::warn_nullability_lost, -1,
  /* DiagArray603 */ diag::warn_autosynthesis_property_ivar_match, -1,
  /* DiagArray604 */ diag::warn_impcast_constant_value_to_objc_bool, -1,
  /* DiagArray605 */ diag::warn_objc_boxing_invalid_utf8_string, -1,
  /* DiagArray606 */ diag::warn_objc_circular_container, -1,
  /* DiagArray608 */ diag::warn_objc_designated_init_missing_super_call, diag::warn_objc_designated_init_non_designated_init_call, diag::warn_objc_designated_init_non_super_designated_init_call, diag::warn_objc_implementation_missing_designated_init_override, diag::warn_objc_secondary_init_missing_init_call, diag::warn_objc_secondary_init_super_init_call, -1,
  /* DiagArray609 */ diag::warn_nsdictionary_duplicate_key, -1,
  /* DiagArray610 */ diag::warn_dup_category_def, -1,
  /* DiagArray611 */ diag::warn_superclass_variable_sized_type_not_at_end, diag::warn_variable_sized_ivar_visibility, -1,
  /* DiagArray612 */ diag::warn_forward_class_redefinition, -1,
  /* DiagArray613 */ diag::warn_ivars_in_interface, -1,
  /* DiagArray614 */ diag::warn_objc_literal_comparison, -1,
  /* DiagArray615 */ diag::warn_impcast_objective_c_literal_to_bool, diag::warn_objc_collection_literal_element, -1,
  /* DiagArray616 */ diag::warn_pp_objc_macro_redef_ignored, -1,
  /* DiagArray617 */ diag::warn_messaging_unqualified_id, -1,
  /* DiagArray618 */ diag::warn_class_method_not_found, diag::warn_class_method_not_found_with_typo, diag::warn_inst_method_not_found, diag::warn_instance_method_not_found_with_typo, diag::warn_instance_method_on_class_found, diag::warn_root_inst_method_not_found, -1,
  /* DiagArray619 */ diag::warn_missing_explicit_synthesis, -1,
  /* DiagArray620 */ diag::warn_objc_missing_super_call, -1,
  /* DiagArray621 */ diag::warn_multiple_method_decl, -1,
  /* DiagArray622 */ diag::warn_objc_property_retain_of_block, -1,
  /* DiagArray623 */ diag::warn_objc_pointer_cxx_catch_fragile, -1,
  /* DiagArray624 */ diag::warn_objc_property_assign_on_object, -1,
  /* DiagArray625 */ diag::warn_impl_required_for_class_property, diag::warn_impl_required_in_category_for_class_property, diag::warn_setter_getter_impl_required, diag::warn_setter_getter_impl_required_in_category, -1,
  /* DiagArray626 */ diag::warn_property_implicitly_mismatched, -1,
  /* DiagArray627 */ diag::warn_cocoa_naming_owned_rule, -1,
  /* DiagArray628 */ diag::warn_objc_property_default_assign_on_object, diag::warn_objc_property_no_assignment_attribute, -1,
  /* DiagArray629 */ diag::warn_autosynthesis_property_in_superclass, diag::warn_no_autosynthesis_property, diag::warn_no_autosynthesis_shared_ivar_property, -1,
  /* DiagArray630 */ diag::warn_category_method_impl_match, -1,
  /* DiagArray631 */ diag::warn_auto_synthesizing_protocol_property, -1,
  /* DiagArray632 */ diag::warn_objc_redundant_qualified_class_type, -1,
  /* DiagArray633 */ diag::warn_objc_readonly_property_has_setter, -1,
  /* DiagArray635 */ diag::warn_objc_redundant_literal_use, -1,
  /* DiagArray636 */ diag::warn_objc_root_class_missing, -1,
  /* DiagArray638 */ diag::warn_impcast_float_to_objc_signed_char_bool, -1,
  /* DiagArray639 */ diag::warn_impcast_int_to_objc_signed_char_bool, -1,
  /* DiagArray640 */ diag::warn_objc_string_literal_comparison, -1,
  /* DiagArray641 */ diag::warn_concatenated_nsarray_literal, -1,
  /* DiagArray642 */ diag::warn_objc_unsafe_perform_selector, -1,
  /* DiagArray643 */ diag::warn_odr_different_num_template_parameters, diag::warn_odr_different_template_parameter_kind, diag::warn_odr_field_type_inconsistent, diag::warn_odr_function_type_inconsistent, diag::warn_odr_ivar_type_inconsistent, diag::warn_odr_non_type_parameter_type_inconsistent, diag::warn_odr_objc_method_num_params_inconsistent, diag::warn_odr_objc_method_param_type_inconsistent, diag::warn_odr_objc_method_result_type_inconsistent, diag::warn_odr_objc_method_variadic_inconsistent, diag::warn_odr_objc_property_impl_kind_inconsistent, diag::warn_odr_objc_property_type_inconsistent, diag::warn_odr_objc_superclass_inconsistent, diag::warn_odr_objc_synthesize_ivar_inconsistent, diag::warn_odr_parameter_pack_non_pack, diag::warn_odr_tag_type_inconsistent, diag::warn_odr_variable_multiple_def, diag::warn_odr_variable_type_inconsistent, -1,
  /* DiagArray644 */ diag::warn_old_style_cast, -1,
  /* DiagArray646 */ diag::ext_opencl_ext_vector_type_rgba_selector, -1,
  /* DiagArray648 */ diag::ext_omp_attributes, -1,
  /* DiagArray649 */ diag::warn_omp_alignment_not_power_of_two, diag::warn_omp_allocate_thread_on_task_target_directive, diag::warn_omp_ctx_incompatible_property_for_selector, diag::warn_omp_ctx_incompatible_score_for_property, diag::warn_omp_ctx_incompatible_selector_for_set, diag::warn_omp_ctx_selector_without_properties, diag::warn_omp_declare_variant_ctx_mutiple_use, diag::warn_omp_declare_variant_ctx_not_a_property, diag::warn_omp_declare_variant_ctx_not_a_selector, diag::warn_omp_declare_variant_ctx_not_a_set, diag::warn_omp_declare_variant_expected, diag::warn_omp_declare_variant_string_literal_or_identifier, diag::warn_omp_linear_step_zero, diag::warn_omp_more_one_device_type_clause, diag::warn_omp_more_one_interop_type, diag::warn_omp_more_one_omp_all_memory, diag::warn_omp_unknown_assumption_clause_missing_id, diag::warn_omp_unknown_assumption_clause_without_args, diag::warn_omp_used_different_allocator, -1,
  /* DiagArray650 */ diag::warn_omp_invalid_attribute_for_ompx_attributes, -1,
  /* DiagArray651 */ diag::ext_omp_loop_not_canonical_init, diag::warn_omp_loop_64_bit_var, -1,
  /* DiagArray652 */ diag::warn_omp_non_trivial_type_mapped, -1,
  /* DiagArray653 */ diag::warn_drv_omp_offload_target_duplicate, diag::warn_omp_declare_target_after_first_use, diag::warn_omp_not_in_target_context, -1,
  /* DiagArray654 */ diag::warn_drv_fine_grained_bitfield_accesses_ignored, diag::warn_drv_fjmc_for_elf_only, diag::warn_drv_jmc_requires_debuginfo, diag::warn_drv_loongarch_conflicting_implied_val, diag::warn_drv_moutline_atomics_unsupported_opt, diag::warn_drv_moutline_unsupported_opt, diag::warn_drv_needs_hvx, diag::warn_drv_ps_force_pic, diag::warn_drv_unsupported_diag_option_for_flang, diag::warn_drv_unsupported_longcalls, diag::warn_drv_unsupported_option_for_flang, diag::warn_drv_unsupported_option_for_offload_arch_req_feature, diag::warn_drv_unsupported_option_for_processor, diag::warn_drv_unsupported_option_for_target, diag::warn_drv_unsupported_pic_with_mabicalls, diag::warn_drv_unsupported_sdata, diag::warn_target_override_arm64ec, -1,
  /* DiagArray655 */ diag::ext_typecheck_ordered_comparison_of_function_pointers, diag::warn_typecheck_ordered_comparison_of_function_pointers, -1,
  /* DiagArray656 */ diag::ext_out_of_line_declaration, -1,
  /* DiagArray657 */ diag::ext_use_out_of_scope_declaration, -1,
  /* DiagArray658 */ diag::warn_overaligned_type, -1,
  /* DiagArray660 */ diag::ext_string_too_long, -1,
  /* DiagArray661 */ diag::warn_overloaded_shift_in_comparison, -1,
  /* DiagArray662 */ diag::warn_overloaded_virtual, -1,
  /* DiagArray664 */ diag::warn_fe_override_module, -1,
  /* DiagArray665 */ diag::warn_conflicting_overriding_param_modifiers, diag::warn_conflicting_overriding_param_types, diag::warn_conflicting_overriding_ret_type_modifiers, diag::warn_conflicting_overriding_ret_types, diag::warn_conflicting_overriding_variadic, diag::warn_non_contravariant_overriding_param_types, diag::warn_non_covariant_overriding_ret_types, -1,
  /* DiagArray666 */ diag::warn_drv_overriding_option, -1,
  /* DiagArray667 */ diag::warn_unnecessary_packed, -1,
  /* DiagArray668 */ diag::warn_unpacked_field, -1,
  /* DiagArray669 */ diag::warn_padded_struct_anon_field, diag::warn_padded_struct_field, diag::warn_padded_struct_size, -1,
  /* DiagArray670 */ diag::warn_condition_is_assignment, diag::warn_precedence_bitwise_rel, diag::warn_precedence_conditional, -1,
  /* DiagArray671 */ diag::warn_equality_with_extra_parens, -1,
  /* DiagArray673 */ diag::remark_fe_backend_optimization_remark, -1,
  /* DiagArray674 */ diag::remark_fe_backend_optimization_remark_analysis, diag::remark_fe_backend_optimization_remark_analysis_aliasing, diag::remark_fe_backend_optimization_remark_analysis_fpcommute, -1,
  /* DiagArray675 */ diag::warn_fe_backend_optimization_failure, -1,
  /* DiagArray676 */ diag::remark_fe_backend_optimization_remark_missed, -1,
  /* DiagArray677 */ diag::warn_module_uses_date_time, -1,
  /* DiagArray678 */ diag::ext_aggregate_init_not_constant, diag::ext_anonymous_record_with_type, diag::ext_anonymous_struct_union_qualified, diag::ext_array_size_conversion, diag::ext_auto_new_list_init, diag::ext_c99_array_usage, diag::ext_c99_compound_literal, diag::ext_c99_feature, diag::ext_c99_flexible_array_member, diag::ext_c99_variable_decl_in_for_loop, diag::ext_c_empty_initializer, diag::ext_c_nullptr, diag::ext_cast_fn_obj, diag::ext_clang_diagnose_if, diag::ext_clang_enable_if, diag::ext_cxx11_enum_fixed_underlying_type, diag::ext_designated_init, diag::ext_dtor_name_ambiguous, diag::ext_dtor_named_in_wrong_scope, diag::ext_duplicate_declspec, diag::ext_ellipsis_exception_spec, diag::ext_empty_fnmacro_arg, diag::ext_enum_value_not_int, diag::ext_enumerator_list_comma_c, diag::ext_enumerator_list_comma_cxx, diag::ext_explicit_instantiation_without_qualified_id, diag::ext_expr_not_ice, diag::ext_extern_template, diag::ext_extra_semi, diag::ext_forward_ref_enum, diag::ext_freestanding_complex, diag::ext_gnu_array_range, diag::ext_hex_constant_invalid, diag::ext_hex_literal_invalid, diag::ext_ident_list_in_param, diag::ext_in_class_initializer_non_constant, diag::ext_integer_complement_complex, diag::ext_integer_increment_complex, diag::ext_internal_in_extern_inline_quiet, diag::ext_line_comment, diag::ext_main_used, diag::ext_mixed_decls_code, diag::ext_multi_line_line_comment, diag::ext_named_variadic_macro, diag::ext_no_newline_eof, diag::ext_nonstandard_escape, diag::ext_ns_enum_attribute, diag::ext_opencl_double_without_pragma, diag::ext_pointer_to_const_ref_member_on_rvalue, diag::ext_pp_bad_vaargs_use, diag::ext_pp_comma_expr, diag::ext_pp_ident_directive, diag::ext_pp_line_too_big, diag::ext_pp_opencl_variadic_macros, diag::ext_pp_warning_directive, diag::ext_return_has_void_expr, diag::ext_rvalue_to_reference_access_ctor, diag::ext_rvalue_to_reference_temp_copy_no_viable, diag::ext_sizeof_alignof_function_type, diag::ext_sizeof_alignof_void_type, diag::ext_subscript_non_lvalue, diag::ext_thread_before, diag::ext_typecheck_addrof_void, diag::ext_typecheck_cast_nonscalar, diag::ext_typecheck_comparison_of_fptr_to_void, diag::ext_typecheck_cond_one_void, diag::ext_typecheck_convert_pointer_void_func, diag::ext_typecheck_ordered_comparison_of_pointer_and_zero, diag::ext_variadic_macro, diag::warn_defined_in_function_type_macro, diag::warn_ext_c23_attributes, diag::warn_ext_cxx11_attributes, diag::warn_format_conversion_argument_type_mismatch_pedantic, diag::warn_kern_is_method, diag::warn_strict_prototypes, -1,
  /* DiagArray679 */ diag::warn_opencl_unsupported_core_feature, diag::warn_pragma_extension_is_core, -1,
  /* DiagArray681 */ diag::warn_pessimizing_move_on_initialization, diag::warn_pessimizing_move_on_return, -1,
  /* DiagArray682 */ diag::ext_sizeof_alignof_function_type, diag::ext_sizeof_alignof_void_type, diag::warn_sub_ptr_zero_size_types, -1,
  /* DiagArray683 */ diag::warn_cast_nonnull_to_bool, diag::warn_impcast_pointer_to_bool, -1,
  /* DiagArray684 */ diag::warn_pointer_compare, -1,
  /* DiagArray685 */ diag::ext_typecheck_comparison_of_pointer_integer, -1,
  /* DiagArray686 */ diag::ext_typecheck_convert_incompatible_pointer_sign, -1,
  /* DiagArray687 */ diag::warn_pointer_to_enum_cast, -1,
  /* DiagArray688 */ diag::warn_pointer_to_int_cast, -1,
  /* DiagArray689 */ diag::ext_typecheck_cond_incompatible_pointers, -1,
  /* DiagArray690 */ diag::warn_poison_system_directories, -1,
  /* DiagArray691 */ diag::warn_potentially_direct_selector_expression, -1,
  /* DiagArray692 */ diag::warn_side_effects_typeid, -1,
  /* DiagArray693 */ diag::warn_pragma_attribute_unused, -1,
  /* DiagArray694 */ diag::pp_pragma_once_in_main_file, -1,
  /* DiagArray695 */ diag::warn_pragma_pack_modified_after_include, diag::warn_pragma_pack_no_pop_eof, -1,
  /* DiagArray696 */ diag::warn_pragma_pack_non_default_at_include, -1,
  /* DiagArray697 */ diag::pp_pragma_sysheader_in_main_file, -1,
  /* DiagArray698 */ diag::warn_no_support_for_eval_method_source_on_m32, diag::warn_redefine_extname_not_applied, -1,
  /* DiagArray699 */ diag::warn_cxx11_compat_constexpr_body_invalid_stmt, diag::warn_cxx11_compat_constexpr_body_multiple_return, diag::warn_cxx11_compat_constexpr_body_no_return, diag::warn_cxx11_compat_constexpr_local_var, diag::warn_cxx11_compat_constexpr_type_definition, diag::warn_cxx11_compat_decltype_auto_type_specifier, diag::warn_cxx11_compat_deduced_return_type, diag::warn_cxx11_compat_digit_separator, diag::warn_cxx11_compat_generic_lambda, diag::warn_cxx11_compat_init_capture, diag::warn_cxx11_compat_variable_template, -1,
  /* DiagArray701 */ diag::warn_cxx14_compat_class_template_argument_deduction, diag::warn_cxx14_compat_constexpr_if, diag::warn_cxx14_compat_constexpr_on_lambda, diag::warn_cxx14_compat_decomp_decl, diag::warn_cxx14_compat_fold_expression, diag::warn_cxx14_compat_init_statement, diag::warn_cxx14_compat_inline_variable, diag::warn_cxx14_compat_nested_namespace_definition, diag::warn_cxx14_compat_star_this_lambda_capture, diag::warn_cxx14_compat_static_assert_no_message, diag::warn_cxx14_compat_template_nontype_parm_auto_type, diag::warn_cxx14_compat_template_template_param_typename, diag::warn_cxx14_compat_u8_character_literal, diag::warn_cxx14_compat_using_attribute_ns, diag::warn_cxx17_compat_multi_using_declaration, diag::warn_cxx17_compat_using_declaration_pack, diag::warn_for_range_begin_end_types_differ, -1,
  /* DiagArray702 */ diag::warn_cxx14_compat_ns_enum_attribute, diag::warn_cxx17_hex_literal, -1,
  /* DiagArray703 */ diag::warn_cxx17_compat_adl_only_template_id, diag::warn_cxx17_compat_bitfield_member_init, diag::warn_cxx17_compat_capture_binding, diag::warn_cxx17_compat_constexpr_body_invalid_stmt, diag::warn_cxx17_compat_constexpr_ctor_missing_init, diag::warn_cxx17_compat_constexpr_function_try_block, diag::warn_cxx17_compat_constexpr_local_var_no_init, diag::warn_cxx17_compat_constexpr_union_ctor_no_init, diag::warn_cxx17_compat_constexpr_virtual, diag::warn_cxx17_compat_decomp_decl_spec, diag::warn_cxx17_compat_defaulted_comparison, diag::warn_cxx17_compat_defaulted_method_type_mismatch, diag::warn_cxx17_compat_equals_this_lambda_capture, diag::warn_cxx17_compat_explicit_bool, diag::warn_cxx17_compat_for_range_init_stmt, diag::warn_cxx17_compat_init_capture_pack, diag::warn_cxx17_compat_inline_nested_namespace_definition, diag::warn_cxx17_compat_lambda_def_ctor_assign, diag::warn_cxx17_compat_lambda_template_parameter_list, diag::warn_cxx17_compat_missing_varargs_arg, diag::warn_cxx17_compat_spaceship, diag::warn_cxx17_compat_template_nontype_parm_type, diag::warn_cxx17_compat_unicode_type, diag::warn_cxx17_compat_using_decl_class_member_enumerator, diag::warn_cxx17_compat_using_decl_non_member_enumerator, diag::warn_cxx17_compat_using_decl_scoped_enumerator, diag::warn_cxx17_compat_using_enum_declaration, -1,
  /* DiagArray704 */ diag::warn_cxx17_compat_designated_init, diag::warn_cxx17_compat_pointer_to_const_ref_member_on_rvalue, -1,
  /* DiagArray705 */ diag::ext_subscript_overload, diag::warn_cxx20_alias_in_init_statement, diag::warn_cxx20_compat_auto_expr, diag::warn_cxx20_compat_consteval_if, diag::warn_cxx20_compat_constexpr_body_invalid_stmt, diag::warn_cxx20_compat_constexpr_var, diag::warn_cxx20_compat_decl_attrs_on_lambda, diag::warn_cxx20_compat_label_end_of_compound_statement, diag::warn_cxx20_compat_operator_overload_static, diag::warn_cxx20_compat_size_t_suffix, diag::warn_cxx20_compat_static_lambda, diag::warn_cxx23_compat_defaulted_comparison_constexpr_mismatch, diag::warn_cxx23_compat_pp_directive, diag::warn_cxx23_compat_warning_directive, diag::warn_cxx23_delimited_escape_sequence, -1,
  /* DiagArray707 */ diag::warn_cxx23_placeholder_var_definition, -1,
  /* DiagArray711 */ diag::warn_c17_compat_ellipsis_only_parameter, diag::warn_c17_compat_static_assert_no_message, diag::warn_c23_compat_bitint_suffix, diag::warn_c23_compat_digit_separator, diag::warn_c23_compat_empty_initializer, diag::warn_c23_compat_keyword, diag::warn_c23_compat_label_end_of_compound_statement, diag::warn_c23_compat_literal_ucn_control_character, diag::warn_c23_compat_literal_ucn_escape_basic_scs, diag::warn_c23_compat_pp_directive, diag::warn_c23_compat_warning_directive, diag::warn_pre_c23_compat_attributes, -1,
  /* DiagArray715 */ diag::warn_omp51_compat_attributes, -1,
  /* DiagArray716 */ diag::ext_predef_outside_function, -1,
  /* DiagArray717 */ diag::warn_private_extern, -1,
  /* DiagArray718 */ diag::warn_use_of_private_header_outside_module, -1,
  /* DiagArray719 */ diag::warn_mmap_mismatched_private_module_name, diag::warn_mmap_mismatched_private_submodule, diag::warn_mmap_redundant_export_as, diag::warn_no_priv_submodule_use_toplevel, -1,
  /* DiagArray720 */ diag::warn_profile_data_missing, -1,
  /* DiagArray721 */ diag::warn_profile_data_out_of_date, -1,
  /* DiagArray722 */ diag::warn_profile_data_unprofiled, -1,
  /* DiagArray723 */ diag::warn_property_access_suggest, -1,
  /* DiagArray724 */ diag::warn_property_attr_mismatch, diag::warn_property_attribute, diag::warn_property_redecl_getter_mismatch, diag::warn_readonly_property, -1,
  /* DiagArray725 */ diag::warn_unimplemented_protocol_method, -1,
  /* DiagArray726 */ diag::warn_protocol_property_mismatch, -1,
  /* DiagArray727 */ diag::warn_avx_calling_convention, -1,
  /* DiagArray728 */ diag::err_func_returning_qualified_void, -1,
  /* DiagArray729 */ diag::warn_quoted_include_in_framework_header, -1,
  /* DiagArray731 */ diag::warn_for_range_ref_binds_ret_temp, -1,
  /* DiagArray732 */ diag::warn_for_range_const_ref_binds_temp_built_from_ref, diag::warn_for_range_copy, -1,
  /* DiagArray733 */ diag::warn_reading_std_cxx_module_by_implicit_paths, -1,
  /* DiagArray734 */ diag::warn_var_decl_not_read_only, -1,
  /* DiagArray735 */ diag::warn_auto_readonly_iboutlet_property, -1,
  /* DiagArray736 */ diag::warn_bad_receiver_type, -1,
  /* DiagArray737 */ diag::warn_receiver_forward_class, diag::warn_receiver_forward_instance, -1,
  /* DiagArray738 */ diag::ext_member_redeclared, -1,
  /* DiagArray739 */ diag::warn_consteval_if_always_true, -1,
  /* DiagArray741 */ diag::warn_redundant_move_on_return, -1,
  /* DiagArray742 */ diag::warn_redundant_parens_around_declarator, -1,
  /* DiagArray743 */ diag::ext_register_storage_class, -1,
  /* DiagArray744 */ diag::warn_reinterpret_different_from_static, -1,
  /* DiagArray745 */ diag::remark_fe_backend_plugin, -1,
  /* DiagArray747 */ diag::warn_initializer_out_of_order, diag::warn_some_initializers_out_of_order, -1,
  /* DiagArray748 */ diag::ext_designated_init_reordered, -1,
  /* DiagArray749 */ diag::warn_objc_requires_super_protocol, -1,
  /* DiagArray751 */ diag::warn_reserved_extern_symbol, -1,
  /* DiagArray752 */ diag::warn_pp_macro_is_reserved_id, -1,
  /* DiagArray753 */ diag::warn_reserved_module_name, -1,
  /* DiagArray754 */ diag::ext_ms_reserved_user_defined_literal, diag::ext_reserved_user_defined_literal, -1,
  /* DiagArray755 */ diag::warn_pragma_restrict_expansion_macro_use, -1,
  /* DiagArray756 */ diag::ext_retained_language_linkage, -1,
  /* DiagArray758 */ diag::warn_ret_addr_label, diag::warn_ret_local_temp_addr_ref, diag::warn_ret_stack_addr_ref, -1,
  /* DiagArray760 */ diag::ext_return_has_expr, diag::ext_return_missing_expr, diag::warn_falloff_nonvoid_coroutine, diag::warn_falloff_nonvoid_function, diag::warn_falloff_nonvoid_lambda, diag::warn_maybe_falloff_nonvoid_coroutine, diag::warn_maybe_falloff_nonvoid_function, diag::warn_maybe_falloff_nonvoid_lambda, diag::warn_return_missing_expr, -1,
  /* DiagArray761 */ diag::warn_return_value_udt, diag::warn_return_value_udt_incomplete, -1,
  /* DiagArray762 */ diag::ext_ovl_rewrite_equalequal_not_bool, -1,
  /* DiagArray763 */ diag::remark_cc1_round_trip_generated, -1,
  /* DiagArray764 */ diag::warn_no_dynamic_cast_with_rtti_disabled, diag::warn_no_typeid_with_rtti_disabled, -1,
  /* DiagArray765 */ diag::remark_sanitize_address_insert_extra_padding_accepted, diag::remark_sanitize_address_insert_extra_padding_rejected, -1,
  /* DiagArray766 */ diag::warn_drv_sarif_format_unstable, -1,
  /* DiagArray767 */ diag::remark_pp_search_path_usage, -1,
  /* DiagArray768 */ diag::warn_attribute_section_on_redeclaration, diag::warn_duplicate_codeseg_attribute, diag::warn_mismatched_section, -1,
  /* DiagArray769 */ diag::warn_unimplemented_selector, -1,
  /* DiagArray770 */ diag::warn_multiple_selectors, -1,
  /* DiagArray771 */ diag::warn_self_assignment_builtin, -1,
  /* DiagArray772 */ diag::warn_identity_field_assign, -1,
  /* DiagArray773 */ diag::warn_self_assignment_overloaded, -1,
  /* DiagArray774 */ diag::warn_self_move, -1,
  /* DiagArray775 */ diag::warn_semicolon_before_method_body, -1,
  /* DiagArray776 */ diag::warn_missing_sentinel, diag::warn_not_enough_argument, -1,
  /* DiagArray778 */ diag::warn_fe_serialized_diag_failure, diag::warn_fe_serialized_diag_failure_during_finalisation, diag::warn_fe_serialized_diag_merge_failure, -1,
  /* DiagArray779 */ diag::warn_decl_shadow, -1,
  /* DiagArray781 */ diag::warn_shadow_field, -1,
  /* DiagArray782 */ diag::warn_ctor_parm_shadows_field, -1,
  /* DiagArray783 */ diag::warn_modifying_shadowing_decl, -1,
  /* DiagArray784 */ diag::warn_ivar_use_hidden, -1,
  /* DiagArray785 */ diag::warn_decl_shadow_uncaptured_local, -1,
  /* DiagArray786 */ diag::warn_shift_negative, -1,
  /* DiagArray787 */ diag::warn_shift_gt_typewidth, -1,
  /* DiagArray788 */ diag::warn_shift_lhs_negative, -1,
  /* DiagArray789 */ diag::warn_addition_in_bitshift, -1,
  /* DiagArray790 */ diag::warn_shift_result_gt_typewidth, -1,
  /* DiagArray791 */ diag::warn_shift_result_sets_sign_bit, -1,
  /* DiagArray792 */ diag::warn_impcast_integer_64_32, -1,
  /* DiagArray793 */ diag::warn_mixed_sign_comparison, -1,
  /* DiagArray794 */ diag::warn_impcast_integer_sign, diag::warn_impcast_integer_sign_conditional, diag::warn_impcast_nonnegative_result, -1,
  /* DiagArray796 */ diag::warn_no_underlying_type_specified_for_enum_bitfield, -1,
  /* DiagArray797 */ diag::ext_wchar_t_sign_spec, -1,
  /* DiagArray798 */ diag::warn_impcast_single_bit_bitield_precision_constant, -1,
  /* DiagArray799 */ diag::warn_sizeof_array_param, -1,
  /* DiagArray800 */ diag::warn_sizeof_array_decay, -1,
  /* DiagArray801 */ diag::warn_division_sizeof_array, -1,
  /* DiagArray802 */ diag::warn_division_sizeof_ptr, -1,
  /* DiagArray803 */ diag::warn_sizeof_pointer_expr_memaccess, diag::warn_sizeof_pointer_type_memaccess, -1,
  /* DiagArray804 */ diag::warn_slash_u_filename, -1,
  /* DiagArray805 */ diag::warn_slh_does_not_support_asm_goto, -1,
  /* DiagArray806 */ diag::remark_sloc_usage, -1,
  /* DiagArray807 */ diag::warn_sometimes_uninit_var, -1,
  /* DiagArray808 */ diag::warn_fe_source_mgr, -1,
  /* DiagArray809 */ diag::warn_omp_declare_variant_after_emitted, diag::warn_omp_declare_variant_after_used, diag::warn_omp_declare_variant_marked_as_declare_variant, diag::warn_omp_declare_variant_score_not_constant, diag::warn_omp_nesting_simd, diag::warn_omp_unterminated_declare_target, diag::warn_pragma_omp_ignored, diag::warn_unknown_declare_variant_isa_trait, -1,
  /* DiagArray810 */ diag::warn_sampler_initializer_invalid_bits, -1,
  /* DiagArray812 */ diag::warn_stack_exhausted, -1,
  /* DiagArray813 */ diag::warn_stack_clash_protection_inline_asm, -1,
  /* DiagArray814 */ diag::ext_in_class_initializer_float_type_cxx11, -1,
  /* DiagArray815 */ diag::ext_internal_in_extern_inline, diag::ext_internal_in_extern_inline_quiet, -1,
  /* DiagArray816 */ diag::warn_static_inline_explicit_inst_ignored, -1,
  /* DiagArray817 */ diag::warn_static_local_in_extern_inline, -1,
  /* DiagArray818 */ diag::warn_static_self_reference_in_init, -1,
  /* DiagArray819 */ diag::warn_drv_libstdcxx_not_found, -1,
  /* DiagArray831 */ diag::warn_strict_potentially_direct_selector_expression, -1,
  /* DiagArray832 */ diag::warn_strict_prototypes, -1,
  /* DiagArray833 */ diag::warn_strict_multiple_method_decl, -1,
  /* DiagArray834 */ diag::warn_stringcompare, -1,
  /* DiagArray835 */ diag::warn_concatenated_literal_array_init, -1,
  /* DiagArray836 */ diag::warn_impcast_string_literal_to_bool, -1,
  /* DiagArray837 */ diag::warn_string_plus_char, -1,
  /* DiagArray838 */ diag::warn_string_plus_int, -1,
  /* DiagArray839 */ diag::warn_strlcpycat_wrong_size, -1,
  /* DiagArray840 */ diag::warn_strncat_large_size, diag::warn_strncat_src_size, diag::warn_strncat_wrong_size, -1,
  /* DiagArray841 */ diag::warn_suggest_destructor_marked_not_override_overriding, -1,
  /* DiagArray842 */ diag::warn_suggest_function_marked_not_override_overriding, -1,
  /* DiagArray843 */ diag::ext_typecheck_base_super, -1,
  /* DiagArray844 */ diag::warn_suspicious_bzero_size, -1,
  /* DiagArray846 */ diag::warn_attr_swift_name_decl_kind, diag::warn_attr_swift_name_decl_missing_params, diag::warn_attr_swift_name_function, diag::warn_attr_swift_name_getter_parameters, diag::warn_attr_swift_name_invalid_identifier, diag::warn_attr_swift_name_missing_parameters, diag::warn_attr_swift_name_multiple_selfs, diag::warn_attr_swift_name_num_params, diag::warn_attr_swift_name_setter_parameters, diag::warn_attr_swift_name_subscript_getter_newValue, diag::warn_attr_swift_name_subscript_invalid_parameter, diag::warn_attr_swift_name_subscript_setter_multiple_newValues, diag::warn_attr_swift_name_subscript_setter_no_newValue, -1,
  /* DiagArray847 */ diag::warn_case_value_overflow, diag::warn_missing_case, diag::warn_not_in_enum, -1,
  /* DiagArray848 */ diag::warn_bool_switch_condition, -1,
  /* DiagArray850 */ diag::warn_def_missing_case, -1,
  /* DiagArray851 */ diag::warn_sync_op_misaligned, -1,
  /* DiagArray852 */ diag::warn_sync_fetch_and_nand_semantics_change, -1,
  /* DiagArray854 */ diag::warn_target_clone_mixed_values, -1,
  /* DiagArray855 */ diag::warn_comparison_bitwise_always, diag::warn_comparison_bitwise_or, -1,
  /* DiagArray856 */ diag::warn_alignment_builtin_useless, diag::warn_comparison_always, -1,
  /* DiagArray857 */ diag::warn_integer_constants_in_conditional_always_true, diag::warn_left_shift_always, diag::warn_tautological_bool_compare, -1,
  /* DiagArray859 */ diag::warn_out_of_range_compare, -1,
  /* DiagArray860 */ diag::warn_tautological_negation_and_compare, diag::warn_tautological_negation_or_compare, -1,
  /* DiagArray861 */ diag::warn_tautological_compare_objc_bool, -1,
  /* DiagArray862 */ diag::warn_tautological_overlap_comparison, -1,
  /* DiagArray863 */ diag::warn_nonnull_expr_compare, diag::warn_null_pointer_compare, -1,
  /* DiagArray864 */ diag::warn_tautological_constant_compare, -1,
  /* DiagArray865 */ diag::warn_address_of_reference_null_compare, diag::warn_this_null_compare, -1,
  /* DiagArray866 */ diag::warn_unsigned_char_always_true_comparison, -1,
  /* DiagArray867 */ diag::warn_unsigned_enum_always_true_comparison, -1,
  /* DiagArray868 */ diag::warn_unsigned_always_true_comparison, -1,
  /* DiagArray869 */ diag::warn_tautological_compare_value_range, -1,
  /* DiagArray870 */ diag::warn_tcb_enforcement_violation, -1,
  /* DiagArray871 */ diag::ext_typecheck_decl_incomplete_type, -1,
  /* DiagArray873 */ diag::warn_acquired_before, diag::warn_acquired_before_after_cycle, diag::warn_cannot_resolve_lock, diag::warn_double_lock, diag::warn_expecting_lock_held_on_loop, diag::warn_expecting_locked, diag::warn_fun_excludes_mutex, diag::warn_fun_requires_lock, diag::warn_fun_requires_negative_cap, diag::warn_lock_exclusive_and_shared, diag::warn_lock_some_predecessors, diag::warn_no_unlock, diag::warn_unlock_but_no_lock, diag::warn_unlock_kind_mismatch, diag::warn_var_deref_requires_any_lock, diag::warn_var_deref_requires_lock, diag::warn_variable_requires_any_lock, diag::warn_variable_requires_lock, -1,
  /* DiagArray874 */ diag::warn_thread_attribute_argument_not_lockable, diag::warn_thread_attribute_decl_not_lockable, diag::warn_thread_attribute_decl_not_pointer, diag::warn_thread_attribute_ignored, diag::warn_thread_attribute_not_on_capability_member, diag::warn_thread_attribute_not_on_non_static_member, -1,
  /* DiagArray875 */ diag::warn_thread_safety_beta, -1,
  /* DiagArray876 */ diag::warn_acquire_requires_negative_cap, -1,
  /* DiagArray877 */ diag::warn_fun_requires_lock_precise, diag::warn_var_deref_requires_lock_precise, diag::warn_variable_requires_lock_precise, -1,
  /* DiagArray878 */ diag::warn_guarded_pass_by_reference, diag::warn_pt_guarded_pass_by_reference, -1,
  /* DiagArray879 */ diag::warn_thread_safety_verbose, -1,
  /* DiagArray880 */ diag::trigraph_converted, diag::trigraph_ends_block_comment, diag::trigraph_ignored, diag::trigraph_ignored_block_comment, -1,
  /* DiagArray882 */ diag::warn_type_safety_null_pointer_required, diag::warn_type_safety_type_mismatch, diag::warn_type_tag_for_datatype_wrong_kind, -1,
  /* DiagArray883 */ diag::ext_redefinition_of_typedef, -1,
  /* DiagArray884 */ diag::ext_typename_missing, -1,
  /* DiagArray885 */ diag::warn_fe_unable_to_open_stats_file, -1,
  /* DiagArray886 */ diag::warn_unaligned_access, -1,
  /* DiagArray887 */ diag::warn_imp_cast_drops_unaligned, -1,
  /* DiagArray888 */ diag::warn_unavailable_fwdclass_message, -1,
  /* DiagArray889 */ diag::warn_undeclared_selector, diag::warn_undeclared_selector_with_typo, -1,
  /* DiagArray890 */ diag::warn_pp_undef_identifier, -1,
  /* DiagArray891 */ diag::warn_pp_undef_prefix, -1,
  /* DiagArray892 */ diag::warn_address_of_reference_bool_conversion, diag::warn_this_bool_conversion, -1,
  /* DiagArray893 */ diag::warn_func_template_missing, -1,
  /* DiagArray894 */ diag::warn_undefined_inline, -1,
  /* DiagArray895 */ diag::warn_undefined_internal, -1,
  /* DiagArray896 */ diag::ext_undefined_internal_type, -1,
  /* DiagArray897 */ diag::warn_pointer_indirection_from_incompatible_type, diag::warn_undefined_reinterpret_cast, -1,
  /* DiagArray898 */ diag::warn_var_template_missing, -1,
  /* DiagArray899 */ diag::warn_throw_underaligned_obj, -1,
  /* DiagArray900 */ diag::warn_side_effects_unevaluated_context, -1,
  /* DiagArray901 */ diag::warn_unguarded_availability, -1,
  /* DiagArray902 */ diag::warn_unguarded_availability_new, -1,
  /* DiagArray903 */ diag::warn_delimited_ucn_empty, diag::warn_delimited_ucn_incomplete, diag::warn_ucn_escape_incomplete, diag::warn_ucn_escape_no_digits, diag::warn_ucn_escape_surrogate, diag::warn_ucn_not_valid_in_c89, diag::warn_ucn_not_valid_in_c89_literal, -1,
  /* DiagArray904 */ diag::warn_utf8_symbol_homoglyph, -1,
  /* DiagArray905 */ diag::ext_unicode_whitespace, -1,
  /* DiagArray906 */ diag::warn_utf8_symbol_zero_width, -1,
  /* DiagArray907 */ diag::warn_base_class_is_uninit, diag::warn_field_is_uninit, diag::warn_reference_field_is_uninit, diag::warn_uninit_byref_blockvar_captured_by_block, diag::warn_uninit_self_reference_in_init, diag::warn_uninit_self_reference_in_reference_init, diag::warn_uninit_var, -1,
  /* DiagArray908 */ diag::warn_uninit_const_reference, -1,
  /* DiagArray909 */ diag::warn_drv_potentially_misspelled_joined_argument, diag::warn_drv_unknown_argument_clang_cl, diag::warn_drv_unknown_argument_clang_cl_with_suggestion, -1,
  /* DiagArray910 */ diag::warn_assume_attribute_string_unknown, -1,
  /* DiagArray911 */ diag::warn_unknown_attribute_ignored, -1,
  /* DiagArray912 */ diag::warn_drv_new_cuda_version, diag::warn_drv_partially_supported_cuda_version, -1,
  /* DiagArray913 */ diag::warn_pp_invalid_directive, -1,
  /* DiagArray914 */ diag::ext_unknown_escape, -1,
  /* DiagArray915 */ diag::ext_on_off_switch_syntax, diag::ext_pragma_syntax_eod, diag::ext_stdc_pragma_ignored, diag::warn_pragma_diagnostic_cannot_pop, diag::warn_pragma_diagnostic_invalid, diag::warn_pragma_diagnostic_invalid_option, diag::warn_pragma_diagnostic_invalid_token, diag::warn_pragma_exec_charset_expected, diag::warn_pragma_exec_charset_push_invalid, diag::warn_pragma_exec_charset_spec_invalid, diag::warn_pragma_ignored, diag::warn_pragma_include_alias_expected, diag::warn_pragma_include_alias_expected_filename, diag::warn_pragma_include_alias_mismatch_angle, diag::warn_pragma_include_alias_mismatch_quote, diag::warn_pragma_warning_expected, diag::warn_pragma_warning_expected_number, diag::warn_pragma_warning_push_level, diag::warn_pragma_warning_spec_invalid, diag::warn_stdc_fenv_round_not_supported, -1,
  /* DiagArray916 */ diag::warn_unknown_sanitizer_ignored, -1,
  /* DiagArray917 */ diag::warn_pragma_diagnostic_unknown_warning, diag::warn_unknown_diag_option, diag::warn_unknown_warning_specifier, -1,
  /* DiagArray918 */ diag::ext_template_arg_unnamed_type, -1,
  /* DiagArray919 */ diag::warn_unneeded_internal_decl, diag::warn_unneeded_static_internal_decl, -1,
  /* DiagArray920 */ diag::warn_unneeded_member_function, -1,
  /* DiagArray921 */ diag::warn_unqualified_call_to_std_cast_function, -1,
  /* DiagArray922 */ diag::warn_unreachable, -1,
  /* DiagArray924 */ diag::warn_unreachable_break, -1,
  /* DiagArray925 */ diag::warn_unreachable_fallthrough_attr, -1,
  /* DiagArray926 */ diag::warn_unreachable_association, -1,
  /* DiagArray927 */ diag::warn_unreachable_loop_increment, -1,
  /* DiagArray928 */ diag::warn_unreachable_return, -1,
  /* DiagArray929 */ diag::warn_unsafe_buffer_operation, diag::warn_unsafe_buffer_variable, -1,
  /* DiagArray930 */ diag::warn_unsequenced_mod_mod, diag::warn_unsequenced_mod_use, -1,
  /* DiagArray931 */ diag::warn_drv_no_floating_point_registers, diag::warn_drv_unsupported_float_abi_by_lib, -1,
  /* DiagArray932 */ diag::warn_target_unsupported_abs2008, diag::warn_target_unsupported_abslegacy, -1,
  /* DiagArray933 */ diag::warn_at_available_unchecked_use, -1,
  /* DiagArray934 */ diag::warn_target_unsupported_compact_branches, -1,
  /* DiagArray935 */ diag::warn_attribute_dll_instantiated_base_class, -1,
  /* DiagArray936 */ diag::warn_fe_backend_unsupported_fp_exceptions, diag::warn_fe_backend_unsupported_fp_rounding, -1,
  /* DiagArray937 */ diag::warn_template_qualified_friend_ignored, diag::warn_template_qualified_friend_unsupported, -1,
  /* DiagArray938 */ diag::warn_drv_unsupported_gpopt, -1,
  /* DiagArray939 */ diag::warn_target_unsupported_nan2008, diag::warn_target_unsupported_nanlegacy, -1,
  /* DiagArray940 */ diag::warn_drv_dwarf_version_limited_by_target, diag::warn_drv_unsupported_debug_info_opt_for_target, -1,
  /* DiagArray941 */ diag::warn_attribute_protected_visibility, -1,
  /* DiagArray942 */ diag::ext_partial_specs_not_deducible, -1,
  /* DiagArray945 */ diag::warn_unused_but_set_parameter, -1,
  /* DiagArray946 */ diag::warn_unused_but_set_variable, -1,
  /* DiagArray947 */ diag::warn_drv_diagnostics_hotness_requires_pgo, diag::warn_drv_diagnostics_misexpect_requires_pgo, diag::warn_drv_empty_joined_argument, diag::warn_drv_input_file_unused, diag::warn_drv_input_file_unused_by_cpp, diag::warn_drv_invalid_arch_name_with_suggestion, diag::warn_drv_preprocessed_input_file_unused, diag::warn_drv_unused_argument, diag::warn_drv_unused_x, diag::warn_ignored_clang_option, diag::warn_ignoring_fdiscard_for_bitcode, diag::warn_ignoring_verify_debuginfo_preserve_export, -1,
  /* DiagArray948 */ diag::warn_unused_comparison, -1,
  /* DiagArray949 */ diag::warn_unused_const_variable, -1,
  /* DiagArray950 */ diag::warn_unused_exception_param, -1,
  /* DiagArray951 */ diag::warn_unused_function, -1,
  /* DiagArray952 */ diag::warn_unused_property_expr, -1,
  /* DiagArray953 */ diag::warn_unused_label, -1,
  /* DiagArray954 */ diag::warn_unused_lambda_capture, -1,
  /* DiagArray955 */ diag::warn_unused_local_typedef, -1,
  /* DiagArray957 */ diag::pp_macro_not_used, -1,
  /* DiagArray958 */ diag::warn_unused_member_function, -1,
  /* DiagArray959 */ diag::warn_unused_parameter, -1,
  /* DiagArray960 */ diag::warn_unused_private_field, -1,
  /* DiagArray961 */ diag::warn_unused_property_backing_ivar, -1,
  /* DiagArray962 */ diag::warn_unused_result, diag::warn_unused_result_msg, -1,
  /* DiagArray963 */ diag::warn_unused_template, -1,
  /* DiagArray964 */ diag::warn_unused_call, diag::warn_unused_comma_left_operand, diag::warn_unused_constructor, diag::warn_unused_constructor_msg, diag::warn_unused_container_subscript_expr, diag::warn_unused_expr, diag::warn_unused_voidptr, -1,
  /* DiagArray965 */ diag::warn_unused_variable, -1,
  /* DiagArray966 */ diag::warn_unused_volatile, -1,
  /* DiagArray967 */ diag::warn_used_but_marked_unused, -1,
  /* DiagArray968 */ diag::warn_user_literal_reserved, -1,
  /* DiagArray969 */ diag::warn_diagnose_if_succeeded, -1,
  /* DiagArray970 */ diag::warn_second_arg_of_va_start_not_last_named_param, diag::warn_second_parameter_to_va_arg_never_compatible, diag::warn_va_start_type_is_undefined, -1,
  /* DiagArray971 */ diag::ext_named_variadic_macro, diag::ext_pp_bad_vaopt_use, diag::ext_variadic_macro, -1,
  /* DiagArray972 */ diag::warn_typecheck_vector_element_sizes_not_equal, -1,
  /* DiagArray973 */ diag::warn_incompatible_vectors, -1,
  /* DiagArray975 */ diag::warn_empty_parens_are_function_decl, diag::warn_parens_disambiguated_as_function_declaration, diag::warn_parens_disambiguated_as_variable_declaration, -1,
  /* DiagArray976 */ diag::warn_decl_in_param_list, diag::warn_redefinition_in_param_list, -1,
  /* DiagArray977 */ diag::warn_vla_used, -1,
  /* DiagArray978 */ diag::ext_vla, -1,
  /* DiagArray979 */ diag::warn_void_pointer_to_enum_cast, -1,
  /* DiagArray980 */ diag::warn_void_pointer_to_int_cast, -1,
  /* DiagArray981 */ diag::ext_typecheck_indirection_through_void_pointer, -1,
  /* DiagArray983 */ diag::warn_wasm_dynamic_exception_spec_ignored, -1,
  /* DiagArray984 */ diag::warn_weak_template_vtable, -1,
  /* DiagArray985 */ diag::warn_weak_vtable, -1,
  /* DiagArray986 */ diag::ext_deprecated_string_literal_conversion, -1,
  /* DiagArray988 */ diag::warn_xor_used_as_pow, diag::warn_xor_used_as_pow_base, diag::warn_xor_used_as_pow_base_extra, -1,
  /* DiagArray989 */ diag::warn_zero_as_null_pointer_constant, -1,
  /* DiagArray990 */ diag::ext_typecheck_zero_array_size, -1,
};

static const int16_t DiagSubGroups[] = {
  /* Empty */ -1,
  /* DiagSubGroup0 */ 303, -1,
  /* DiagSubGroup4 */ 17, 303, -1,
  /* DiagSubGroup11 */ 683, 834, 863, -1,
  /* DiagSubGroup17 */ 560, 670, 847, 848, 528, 668, -1,
  /* DiagSubGroup27 */ 214, -1,
  /* DiagSubGroup29 */ 36, 35, 32, -1,
  /* DiagSubGroup34 */ 31, -1,
  /* DiagSubGroup42 */ 43, -1,
  /* DiagSubGroup52 */ 396, 192, -1,
  /* DiagSubGroup56 */ 911, 387, -1,
  /* DiagSubGroup66 */ 101, 132, 352, -1,
  /* DiagSubGroup67 */ 135, -1,
  /* DiagSubGroup70 */ 798, -1,
  /* DiagSubGroup77 */ 683, 892, -1,
  /* DiagSubGroup78 */ 77, -1,
  /* DiagSubGroup79 */ 74, -1,
  /* DiagSubGroup88 */ 91, -1,
  /* DiagSubGroup89 */ 95, -1,
  /* DiagSubGroup90 */ 99, -1,
  /* DiagSubGroup91 */ 99, 94, 92, 699, 701, 703, 705, -1,
  /* DiagSubGroup93 */ 91, 700, 702, 704, 706, -1,
  /* DiagSubGroup95 */ 96, 97, 98, -1,
  /* DiagSubGroup102 */ 701, 703, 705, -1,
  /* DiagSubGroup103 */ 102, 702, 704, 706, -1,
  /* DiagSubGroup104 */ 101, 100, -1,
  /* DiagSubGroup106 */ 240, 232, 107, 703, 705, -1,
  /* DiagSubGroup108 */ 106, 704, 706, -1,
  /* DiagSubGroup109 */ 105, -1,
  /* DiagSubGroup110 */ 104, -1,
  /* DiagSubGroup111 */ 106, -1,
  /* DiagSubGroup112 */ 107, -1,
  /* DiagSubGroup113 */ 109, -1,
  /* DiagSubGroup115 */ 705, -1,
  /* DiagSubGroup116 */ 115, 706, -1,
  /* DiagSubGroup118 */ 117, 114, -1,
  /* DiagSubGroup122 */ 115, -1,
  /* DiagSubGroup123 */ 116, -1,
  /* DiagSubGroup124 */ 118, -1,
  /* DiagSubGroup125 */ 120, -1,
  /* DiagSubGroup126 */ 121, -1,
  /* DiagSubGroup127 */ 703, -1,
  /* DiagSubGroup128 */ 704, -1,
  /* DiagSubGroup129 */ 701, -1,
  /* DiagSubGroup130 */ 702, -1,
  /* DiagSubGroup131 */ 699, -1,
  /* DiagSubGroup133 */ 700, -1,
  /* DiagSubGroup134 */ 137, 139, 699, 701, 703, 705, -1,
  /* DiagSubGroup138 */ 134, 135, 136, 700, 702, 704, 706, -1,
  /* DiagSubGroup143 */ 141, -1,
  /* DiagSubGroup144 */ 142, -1,
  /* DiagSubGroup146 */ 117, -1,
  /* DiagSubGroup147 */ 146, -1,
  /* DiagSubGroup149 */ 167, -1,
  /* DiagSubGroup152 */ 153, -1,
  /* DiagSubGroup161 */ 579, -1,
  /* DiagSubGroup165 */ 164, -1,
  /* DiagSubGroup169 */ 170, 171, -1,
  /* DiagSubGroup175 */ 70, 604, -1,
  /* DiagSubGroup180 */ 77, 175, 288, 71, 314, 792, 443, 406, 403, 474, 576, 592, 615, 794, 836, -1,
  /* DiagSubGroup181 */ 592, -1,
  /* DiagSubGroup183 */ 184, 224, 20, 182, -1,
  /* DiagSubGroup186 */ 2, -1,
  /* DiagSubGroup194 */ 196, 198, 197, 758, -1,
  /* DiagSubGroup209 */ 208, 206, -1,
  /* DiagSubGroup212 */ 214, 215, 216, 218, 219, 221, 225, 226, 227, 228, 229, 230, 217, 232, 233, 238, 240, 242, 243, 244, 245, 239, -1,
  /* DiagSubGroup219 */ 222, -1,
  /* DiagSubGroup220 */ 221, -1,
  /* DiagSubGroup221 */ 223, -1,
  /* DiagSubGroup236 */ 237, -1,
  /* DiagSubGroup245 */ 92, -1,
  /* DiagSubGroup251 */ 252, -1,
  /* DiagSubGroup256 */ 258, 257, -1,
  /* DiagSubGroup259 */ 260, -1,
  /* DiagSubGroup272 */ 226, -1,
  /* DiagSubGroup274 */ 581, -1,
  /* DiagSubGroup283 */ 307, -1,
  /* DiagSubGroup284 */ 286, 227, -1,
  /* DiagSubGroup285 */ 228, -1,
  /* DiagSubGroup288 */ 289, 290, 285, -1,
  /* DiagSubGroup289 */ 229, -1,
  /* DiagSubGroup290 */ 230, -1,
  /* DiagSubGroup303 */ 219, 537, 393, 436, 775, 540, 793, 959, 945, 594, 595, 280, 835, 340, -1,
  /* DiagSubGroup305 */ 136, 96, -1,
  /* DiagSubGroup306 */ 280, -1,
  /* DiagSubGroup314 */ 316, 317, -1,
  /* DiagSubGroup319 */ 320, 329, 582, 326, 328, 322, 321, -1,
  /* DiagSubGroup330 */ 324, 326, 328, -1,
  /* DiagSubGroup335 */ 334, -1,
  /* DiagSubGroup339 */ 854, -1,
  /* DiagSubGroup341 */ 100, 105, 114, -1,
  /* DiagSubGroup347 */ 348, 349, 351, 352, 353, 354, 355, 356, 357, 359, 978, 360, 361, 362, 363, 364, 366, 367, 368, 369, 370, 738, 371, 372, 374, 375, 376, 377, 990, 378, 379, -1,
  /* DiagSubGroup372 */ 373, -1,
  /* DiagSubGroup392 */ 390, 391, -1,
  /* DiagSubGroup393 */ 394, -1,
  /* DiagSubGroup395 */ 404, 405, -1,
  /* DiagSubGroup400 */ 401, -1,
  /* DiagSubGroup403 */ 407, 638, -1,
  /* DiagSubGroup406 */ 639, -1,
  /* DiagSubGroup407 */ 397, -1,
  /* DiagSubGroup421 */ 422, 416, -1,
  /* DiagSubGroup427 */ 429, 578, -1,
  /* DiagSubGroup433 */ 232, -1,
  /* DiagSubGroup444 */ 443, -1,
  /* DiagSubGroup446 */ 447, -1,
  /* DiagSubGroup449 */ 389, -1,
  /* DiagSubGroup476 */ 137, -1,
  /* DiagSubGroup479 */ 98, -1,
  /* DiagSubGroup480 */ 318, 730, -1,
  /* DiagSubGroup492 */ 496, 501, 513, 499, 509, 518, 493, 524, 505, 525, 515, 516, 523, 507, 504, 500, 521, 517, 503, 511, 510, 508, 495, 498, 526, 494, 497, 502, 519, 514, 520, 430, -1,
  /* DiagSubGroup521 */ 522, -1,
  /* DiagSubGroup560 */ 41, 79, 158, 164, 209, 319, 318, 333, 395, 434, 445, 532, 533, 561, 565, 732, 746, 760, 771, 774, 799, 800, 838, 856, 880, 907, 915, 943, 982, 620, 608, 611, 662, 717, 154, 301, 969, -1,
  /* DiagSubGroup561 */ 681, 741, 759, 774, -1,
  /* DiagSubGroup562 */ 513, -1,
  /* DiagSubGroup567 */ 99, -1,
  /* DiagSubGroup573 */ 107, -1,
  /* DiagSubGroup575 */ 793, 180, 475, -1,
  /* DiagSubGroup578 */ 577, -1,
  /* DiagSubGroup594 */ 368, -1,
  /* DiagSubGroup597 */ 598, -1,
  /* DiagSubGroup607 */ 634, -1,
  /* DiagSubGroup614 */ 640, -1,
  /* DiagSubGroup634 */ 635, -1,
  /* DiagSubGroup637 */ 639, 638, 604, 861, -1,
  /* DiagSubGroup647 */ 809, 649, 651, 653, 652, 648, 650, -1,
  /* DiagSubGroup653 */ 652, -1,
  /* DiagSubGroup663 */ 436, -1,
  /* DiagSubGroup667 */ 668, -1,
  /* DiagSubGroup670 */ 478, 477, 73, 75, 789, 661, 671, 195, -1,
  /* DiagSubGroup672 */ 901, -1,
  /* DiagSubGroup678 */ 140, 96, 98, 100, 101, 105, 114, 117, 313, 341, 349, 351, 352, 353, 354, 355, 356, 359, 360, 361, 363, 364, 366, 367, 368, 369, 370, 371, 372, 376, 378, 379, 468, 479, 496, 497, 499, 502, 504, 509, 510, 517, 660, 978, 990, 261, 470, 465, 210, 411, 277, 281, 600, 310, 344, 69, 896, 756, 168, 568, 119, -1,
  /* DiagSubGroup680 */ 238, 481, 84, 755, 309, -1,
  /* DiagSubGroup682 */ 370, -1,
  /* DiagSubGroup687 */ 979, -1,
  /* DiagSubGroup688 */ 687, 980, -1,
  /* DiagSubGroup695 */ 696, -1,
  /* DiagSubGroup698 */ 915, 392, 693, 695, -1,
  /* DiagSubGroup700 */ 699, 132, -1,
  /* DiagSubGroup702 */ 701, -1,
  /* DiagSubGroup704 */ 703, -1,
  /* DiagSubGroup706 */ 705, -1,
  /* DiagSubGroup708 */ 707, -1,
  /* DiagSubGroup709 */ 707, -1,
  /* DiagSubGroup710 */ 708, -1,
  /* DiagSubGroup712 */ 711, -1,
  /* DiagSubGroup713 */ 711, -1,
  /* DiagSubGroup714 */ 712, -1,
  /* DiagSubGroup730 */ 732, 731, -1,
  /* DiagSubGroup743 */ 240, -1,
  /* DiagSubGroup746 */ 747, 748, -1,
  /* DiagSubGroup750 */ 752, -1,
  /* DiagSubGroup751 */ 752, 753, 968, -1,
  /* DiagSubGroup754 */ 94, -1,
  /* DiagSubGroup757 */ 758, -1,
  /* DiagSubGroup760 */ 761, -1,
  /* DiagSubGroup769 */ 770, -1,
  /* DiagSubGroup771 */ 773, 772, -1,
  /* DiagSubGroup777 */ 930, -1,
  /* DiagSubGroup779 */ 783, 784, -1,
  /* DiagSubGroup780 */ 779, 782, 785, 781, -1,
  /* DiagSubGroup782 */ 783, -1,
  /* DiagSubGroup811 */ 810, -1,
  /* DiagSubGroup814 */ 374, -1,
  /* DiagSubGroup831 */ 691, -1,
  /* DiagSubGroup832 */ 234, -1,
  /* DiagSubGroup845 */ 803, 271, 587, 489, 844, -1,
  /* DiagSubGroup856 */ 857, 863, 862, 855, 865, 861, 860, -1,
  /* DiagSubGroup857 */ 859, -1,
  /* DiagSubGroup858 */ 881, 869, -1,
  /* DiagSubGroup872 */ 874, 873, 877, 878, -1,
  /* DiagSubGroup881 */ 864, 868, 866, 867, -1,
  /* DiagSubGroup900 */ 692, -1,
  /* DiagSubGroup901 */ 902, -1,
  /* DiagSubGroup907 */ 807, 818, 908, -1,
  /* DiagSubGroup918 */ 139, -1,
  /* DiagSubGroup922 */ 927, 925, 926, -1,
  /* DiagSubGroup923 */ 922, 924, 928, -1,
  /* DiagSubGroup943 */ 944, 951, 953, 960, 954, 955, 964, 965, 946, 961, -1,
  /* DiagSubGroup951 */ 919, -1,
  /* DiagSubGroup956 */ 955, -1,
  /* DiagSubGroup958 */ 920, -1,
  /* DiagSubGroup963 */ 919, -1,
  /* DiagSubGroup964 */ 948, 962, 900, -1,
  /* DiagSubGroup965 */ 949, -1,
  /* DiagSubGroup974 */ 973, -1,
  /* DiagSubGroup977 */ 978, -1,
  /* DiagSubGroup980 */ 979, -1,
  /* DiagSubGroup986 */ 245, -1,
  /* DiagSubGroup987 */ 986, -1,
};

static const char DiagGroupNames[] = {
    "\000\020#pragma-messages\t#warnings\020CFString-literal\003CL4\032Indep"
    "endentClass-attribute\022NSObject-attribute\003abi\016absolute-value\024"
    "abstract-final-class\023abstract-vbase-init\007address\030address-of-pa"
    "cked-member\024address-of-temporary\020aggregate-return\naix-compat\016"
    "align-mismatch\003all\006alloca\031alloca-with-align-alignof\027always-"
    "inline-coroutine\020ambiguous-delete\022ambiguous-ellipsis\017ambiguous"
    "-macro\031ambiguous-member-template\033ambiguous-reversed-operator\034a"
    "nalyzer-incompatible-plugin\031anon-enum-enum-conversion\025anonymous-p"
    "ack-parens\003arc%arc-bridge-casts-disallowed-in-nonarc\036arc-maybe-re"
    "peated-use-of-weak\025arc-non-pod-memaccess\031arc-performSelector-leak"
    "s\030arc-repeated-use-of-weak\021arc-retain-cycles\032arc-unsafe-retain"
    "ed-assign\026argument-outside-range\034argument-undefined-behaviour\014"
    "array-bounds\037array-bounds-pointer-arithmetic\017array-parameter\003a"
    "sm\022asm-operand-widths\013assign-enum\006assume\013at-protocol\034ati"
    "mport-in-framework-header\015atomic-access\020atomic-alignment\027atomi"
    "c-implicit-seq-cst\026atomic-memory-ordering\021atomic-properties*atomi"
    "c-property-with-user-defined-accessor\035attribute-packed-for-bitfield\021"
    "attribute-warning\nattributes\033auto-disable-vptr-sanitizer\013auto-im"
    "port\022auto-storage-class\013auto-var-id\014availability\030avr-rtlib-"
    "linking-quirks\016backend-plugin\030backslash-newline-escape\021bad-fun"
    "ction-cast\016binary-literal\026bind-to-temporary-copy\024binding-in-co"
    "ndition\021bit-int-extension\034bitfield-constant-conversion\030bitfiel"
    "d-enum-conversion\016bitfield-width\037bitwise-conditional-parentheses\032"
    "bitwise-instead-of-logical\026bitwise-op-parentheses\033block-capture-a"
    "utoreleasing\017bool-conversion\020bool-conversions\016bool-operation\022"
    "braced-scalar-init\021branch-protection\013bridge-cast builtin-assume-a"
    "ligned-alignment\027builtin-macro-redefined\027builtin-memcpy-chk-size\027"
    "builtin-requires-header\nc++-compat\014c++0x-compat\020c++0x-extensions"
    "\017c++0x-narrowing\014c++11-compat(c++11-compat-deprecated-writable-st"
    "rings\025c++11-compat-pedantic*c++11-compat-reserved-user-defined-liter"
    "al\020c++11-extensions\020c++11-extra-semi\026c++11-inline-namespace\017"
    "c++11-long-long\017c++11-narrowing\032c++14-attribute-extensions\024c++"
    "14-binary-literal\014c++14-compat\025c++14-compat-pedantic\020c++14-ext"
    "ensions\032c++17-attribute-extensions\014c++17-compat\025c++17-compat-m"
    "angling\025c++17-compat-pedantic\020c++17-extensions\020c++1y-extension"
    "s\014c++1z-compat\025c++1z-compat-mangling\020c++1z-extensions\032c++20"
    "-attribute-extensions\014c++20-compat\025c++20-compat-pedantic\020c++20"
    "-designator\020c++20-extensions$c++23-default-comp-relaxed-constexpr\020"
    "c++23-extensions\020c++26-extensions\014c++2a-compat\025c++2a-compat-pe"
    "dantic\020c++2a-extensions\020c++2b-extensions\020c++2c-extensions\036c"
    "++98-c++11-c++14-c++17-compat'c++98-c++11-c++14-c++17-compat-pedantic\030"
    "c++98-c++11-c++14-compat!c++98-c++11-c++14-compat-pedantic\022c++98-c++"
    "11-compat!c++98-c++11-compat-binary-literal\033c++98-c++11-compat-pedan"
    "tic\014c++98-compat#c++98-compat-bind-to-temporary-copy\027c++98-compat"
    "-extra-semi%c++98-compat-local-type-template-args\025c++98-compat-pedan"
    "tic'c++98-compat-unnamed-type-template-args\016c11-extensions\nc23-comp"
    "at\016c23-extensions\nc2x-compat\016c2x-extensions\nc99-compat\016c99-d"
    "esignator\016c99-extensions#call-to-pure-virtual-from-ctor-dtor\025call"
    "ed-once-parameter\ncast-align\027cast-calling-convention\022cast-functi"
    "on-type\031cast-function-type-strict\020cast-of-sel-type\tcast-qual\023"
    "cast-qual-unrelated\nchar-align\017char-subscripts\014clang-cl-pch\020c"
    "lass-conversion\015class-varargs\017cmse-union-leak\005comma\007comment"
    "\010comments\036compare-distinct-pointer-types\022completion-handler\026"
    "complex-component-init\024compound-token-split\035compound-token-split-"
    "by-macro\035compound-token-split-by-space\031conditional-type-mismatch\031"
    "conditional-uninitialized\015config-macros\023constant-conversion\022co"
    "nstant-evaluated\030constant-logical-operand\023constexpr-not-const\010"
    "consumed\nconversion\017conversion-null$coro-non-aligned-allocation-fun"
    "ction\tcoroutine%coroutine-missing-unhandled-exception\026covered-switc"
    "h-default\003cpp\030cstring-format-directive\026ctad-maybe-unsupported\021"
    "ctor-dtor-privacy\003ctu\013cuda-compat\030custom-atomic-properties\027"
    "cxx-attribute-extension\010dangling\015dangling-else\016dangling-field\014"
    "dangling-gsl\031dangling-initializer-list\023darwin-sdk-settings\tdate-"
    "time\023dealloc-in-category\035debug-compression-unavailable\033declara"
    "tion-after-statement\032defaulted-function-deleted\026delegating-ctor-c"
    "ycles delete-abstract-non-virtual-dtor\021delete-incomplete$delete-non-"
    "abstract-non-virtual-dtor\027delete-non-virtual-dtor#delimited-escape-s"
    "equence-extension\032deprecate-lax-vec-conv-all\ndeprecated\035deprecat"
    "ed-altivec-src-compat$deprecated-anon-enum-enum-conversion\030deprecate"
    "d-array-compare\025deprecated-attributes\023deprecated-builtins\032depr"
    "ecated-comma-subscript\017deprecated-copy\024deprecated-copy-dtor\031de"
    "precated-copy-with-dtor'deprecated-copy-with-user-provided-copy'depreca"
    "ted-copy-with-user-provided-dtor\024deprecated-coroutine\027deprecated-"
    "declarations!deprecated-dynamic-exception-spec\027deprecated-enum-compa"
    "re#deprecated-enum-compare-conditional\037deprecated-enum-enum-conversi"
    "on deprecated-enum-float-conversion\032deprecated-implementations\031de"
    "precated-increment-bool\033deprecated-literal-operator\030deprecated-no"
    "n-prototype\031deprecated-objc-isa-usage%deprecated-objc-pointer-intros"
    "pection5deprecated-objc-pointer-introspection-performSelector\021deprec"
    "ated-pragma)deprecated-redundant-constexpr-static-def\023deprecated-reg"
    "ister\037deprecated-static-analyzer-flag\027deprecated-this-capture\017"
    "deprecated-type\023deprecated-volatile\033deprecated-writable-strings\022"
    "direct-ivar-access\030disabled-macro-expansion\025disabled-optimization"
    "\014discard-qual\034distributed-object-modifiers\013div-by-zero\020divi"
    "sion-by-zero\036dll-attribute-on-redeclaration%dllexport-explicit-insta"
    "ntiation-decl\032dllimport-static-field-def\015documentation\035documen"
    "tation-deprecated-sync\022documentation-html\026documentation-pedantic\035"
    "documentation-unknown-command\036dollar-in-identifier-extension\020doub"
    "le-promotion\tdtor-name\014dtor-typedef\030duplicate-decl-specifier\016"
    "duplicate-enum\024duplicate-method-arg\026duplicate-method-match\022dup"
    "licate-protocol\017dxil-validation\027dynamic-class-memaccess\026dynami"
    "c-exception-spec\034eager-load-cxx-named-modules\006effc++\024elaborate"
    "d-enum-base\025elaborated-enum-class\022embedded-directive\nempty-body\023"
    "empty-decomposition\017empty-init-stmt\026empty-translation-unit\013enc"
    "ode-type\014endif-labels\014enum-compare\030enum-compare-conditional\023"
    "enum-compare-switch\031enum-constexpr-conversion\017enum-conversion\024"
    "enum-enum-conversion\025enum-float-conversion\016enum-too-large\nexcept"
    "ions\023excess-initializers\021excessive-regsave\025exit-time-destructo"
    "rs\024expansion-to-defined\031experimental-header-units\030explicit-ini"
    "tialize-call\027explicit-ownership-type\016export-unnamed\017extern-c-c"
    "ompat\022extern-initializer\005extra\023extra-qualification\nextra-semi"
    "\017extra-semi-stmt\014extra-tokens\032final-dtor-non-final-class\013fi"
    "nal-macro\024fixed-enum-extension\024fixed-point-overflow\tflag-enum\031"
    "flexible-array-extensions\020float-conversion\013float-equal\031float-o"
    "verflow-conversion\025float-zero-conversion\021for-loop-analysis\006for"
    "mat\021format-extra-args\030format-insufficient-args\030format-invalid-"
    "specifier\016format-non-iso\021format-nonliteral\017format-pedantic\017"
    "format-security\025format-type-confusion\nformat-y2k\022format-zero-len"
    "gth\010format=2\016fortify-source\023four-char-constants\015frame-addre"
    "ss\021frame-larger-than\022frame-larger-than=%framework-include-private"
    "-from-public\023free-nonheap-object\036function-def-in-objc-container\025"
    "function-multiversion\014fuse-ld-path\033future-attribute-extensions\015"
    "future-compat\ngcc-compat\026generic-type-extension\023global-construct"
    "ors\013global-isel\003gnu\026gnu-alignof-expression\024gnu-anonymous-st"
    "ruct\033gnu-array-member-paren-init\015gnu-auto-type\022gnu-binary-lite"
    "ral\016gnu-case-range\023gnu-complex-integer gnu-compound-literal-initi"
    "alizer\037gnu-conditional-omitted-operand\016gnu-designator\025gnu-empt"
    "y-initializer\020gnu-empty-struct\036gnu-flexible-array-initializer\037"
    "gnu-flexible-array-union-member\024gnu-folding-constant\026gnu-imaginar"
    "y-constant\020gnu-include-next\035gnu-inline-cpp-without-extern\022gnu-"
    "label-as-value\017gnu-line-marker\033gnu-null-pointer-arithmetic\027gnu"
    "-offsetof-extensions\021gnu-pointer-arith\023gnu-redeclared-enum\030gnu"
    "-statement-expression-gnu-statement-expression-from-macro-expansion\025"
    "gnu-static-float-init$gnu-string-literal-operator-template\016gnu-union"
    "-cast\"gnu-variable-sized-type-not-at-end\027gnu-zero-line-directive!gn"
    "u-zero-variadic-macro-arguments\024gpu-maybe-wrong-side\014header-guard"
    "\016header-hygiene\031hip-omp-target-directives\010hip-only\017hlsl-ext"
    "ensions\025idiomatic-parentheses\022ignored-attributes)ignored-availabi"
    "lity-without-sdk-settings\035ignored-optimization-argument\030ignored-p"
    "ragma-intrinsic\027ignored-pragma-optimize\017ignored-pragmas\022ignore"
    "d-qualifiers\034ignored-reference-qualifiers\010implicit\032implicit-at"
    "omic-properties#implicit-const-int-float-conversion*implicit-conversion"
    "-floating-point-to-bool implicit-exception-spec-mismatch\024implicit-fa"
    "llthrough!implicit-fallthrough-per-function\037implicit-fixed-point-con"
    "version\031implicit-float-conversion\035implicit-function-declaration\014"
    "implicit-int\027implicit-int-conversion\035implicit-int-float-conversio"
    "n\024implicit-retain-self\033implicitly-unsigned-literal\006import&impo"
    "rt-preprocessor-directive-pedantic\021inaccessible-base\032include-next"
    "-absolute-path\033include-next-outside-header\033incompatible-exception"
    "-spec#incompatible-function-pointer-types*incompatible-function-pointer"
    "-types-strict\"incompatible-library-redeclaration\036incompatible-ms-pr"
    "agma-section\026incompatible-ms-struct\032incompatible-pointer-types.in"
    "compatible-pointer-types-discards-qualifiers\032incompatible-property-t"
    "ype\024incompatible-sysroot'incomplete-framework-module-declaration\031"
    "incomplete-implementation\021incomplete-module\035incomplete-setjmp-dec"
    "laration\023incomplete-umbrella\026inconsistent-dllimport(inconsistent-"
    "missing-destructor-override\035inconsistent-missing-override\016increme"
    "nt-bool\022infinite-recursion\tinit-self\025initializer-overrides\023in"
    "jected-class-name\006inline\ninline-asm#inline-namespace-reopened-nonin"
    "line\021inline-new-delete\"instantiation-after-specialization\016int-co"
    "nversion\017int-conversions\023int-in-bool-context\023int-to-pointer-ca"
    "st\030int-to-void-pointer-cast\020integer-overflow\035invalid-command-l"
    "ine-argument\021invalid-constexpr\020invalid-iboutlet&invalid-initializ"
    "er-from-system-header\035invalid-ios-deployment-target\030invalid-no-bu"
    "iltin-names\020invalid-noreturn\020invalid-offsetof invalid-or-nonexist"
    "ent-directory\036invalid-partial-specialization\013invalid-pch\020inval"
    "id-pp-token\027invalid-source-encoding\035invalid-static-assert-message"
    "\023invalid-token-paste\032invalid-unevaluated-string\014invalid-utf8\020"
    "jump-seh-finally\016keyword-compat\015keyword-macro\026knr-promoted-par"
    "ameter\030language-extension-token\023large-by-value-copy\006liblto\017"
    "linker-warnings\022literal-conversion\015literal-range\030local-type-te"
    "mplate-args\027logical-not-parentheses\026logical-op-parentheses\tlong-"
    "long\015loop-analysis\017macro-redefined\004main\020main-return-type\027"
    "malformed-warning-check\036many-braces-around-scalar-init*mathematical-"
    "notation-identifier-extension\nmax-tokens\021max-unsigned-zero\026memse"
    "t-transposed-args\022memsize-comparison\021method-signatures\tmicrosoft"
    "\022microsoft-abstract\022microsoft-anon-tag\016microsoft-cast\021micro"
    "soft-charize\027microsoft-comment-paste\024microsoft-const-init\023micr"
    "osoft-cpp-macro\"microsoft-default-arg-redefinition\031microsoft-drectv"
    "e-section\025microsoft-end-of-file microsoft-enum-forward-reference\024"
    "microsoft-enum-value\030microsoft-exception-spec\020microsoft-exists#mi"
    "crosoft-explicit-constructor-call\035microsoft-extra-qualification\024m"
    "icrosoft-fixed-enum\030microsoft-flexible-array\016microsoft-goto\033mi"
    "crosoft-inaccessible-base\021microsoft-include\036microsoft-init-from-p"
    "redefined\033microsoft-mutable-reference\031microsoft-pure-definition\032"
    "microsoft-redeclare-static\020microsoft-sealed\027microsoft-static-asse"
    "rt(microsoft-string-literal-from-predefined\022microsoft-template\031mi"
    "crosoft-template-shadow microsoft-union-member-reference\034microsoft-u"
    "nqualified-friend\024microsoft-using-decl\032microsoft-void-pseudo-dtor"
    "\tmisexpect\026misleading-indentation\025mismatched-new-delete\032misma"
    "tched-parameter-types\027mismatched-return-types\017mismatched-tags\016"
    "missing-braces\021missing-constinit\024missing-declarations\026missing-"
    "exception-spec\032missing-field-initializers\030missing-format-attribut"
    "e\024missing-include-dirs\032missing-method-return-type\020missing-mult"
    "ilib\020missing-noescape\020missing-noreturn\030missing-prototype-for-c"
    "c\022missing-prototypes\025missing-selector-name\017missing-sysroot\035"
    "missing-variable-declarations\025misspelled-assumption\014module-build\017"
    "module-conflict\033module-file-config-mismatch\025module-file-extension"
    "\015module-import\031module-import-in-extern-c\032module-include-transl"
    "ation\013module-lock\"modules-ambiguous-internal-linkage\037modules-imp"
    "ort-nested-redundant\004most\004move\014msvc-include\016msvc-not-found\t"
    "multi-gpu\tmultichar\023multiple-move-vbase\tnarrowing\021nested-anon-t"
    "ypes\016nested-externs\020new-returns-null\013newline-eof\007noderef\015"
    "noexcept-type\031non-c-typedef-for-linkage\007non-gcc\033non-literal-nu"
    "ll-conversion'non-modular-include-in-framework-module\035non-modular-in"
    "clude-in-module\017non-pod-varargs\032non-power-of-two-alignment\020non"
    "-virtual-dtor\007nonnull\025nonportable-cfstrings\030nonportable-includ"
    "e-path\037nonportable-system-include-path!nonportable-vector-initializa"
    "tion\024nontrivial-memaccess\023nsconsumed-mismatch\022nsreturns-mismat"
    "ch\017null-arithmetic\016null-character\017null-conversion\020null-dere"
    "ference\027null-pointer-arithmetic\030null-pointer-subtraction\013nulla"
    "bility\030nullability-completeness\"nullability-completeness-on-arrays\024"
    "nullability-declspec\025nullability-extension#nullability-inferred-on-n"
    "ested-type\036nullable-to-nonnull-conversion+objc-autosynthesis-propert"
    "y-ivar-name-match\035objc-bool-constant-conversion\013objc-boxing\027ob"
    "jc-circular-container\016objc-cocoa-api\034objc-designated-initializers"
    "\036objc-dictionary-duplicate-keys\"objc-duplicate-category-definition\023"
    "objc-flexible-array\037objc-forward-class-redefinition\024objc-interfac"
    "e-ivars\024objc-literal-compare\027objc-literal-conversion\027objc-macr"
    "o-redefinition\021objc-messaging-id\022objc-method-access\037objc-missi"
    "ng-property-synthesis\030objc-missing-super-calls\032objc-multiple-meth"
    "od-names\"objc-noncopy-retain-block-property\032objc-nonunified-excepti"
    "ons#objc-property-assign-on-object-type\034objc-property-implementation"
    "\037objc-property-implicit-mismatch*objc-property-matches-cocoa-ownersh"
    "ip-rule\032objc-property-no-attribute\027objc-property-synthesis#objc-p"
    "rotocol-method-implementation objc-protocol-property-synthesis\030objc-"
    "protocol-qualifiers\"objc-readonly-with-setter-property\026objc-redunda"
    "nt-api-use\032objc-redundant-literal-use\017objc-root-class\025objc-sig"
    "ned-char-bool/objc-signed-char-bool-implicit-float-conversion-objc-sign"
    "ed-char-bool-implicit-int-conversion\023objc-string-compare\031objc-str"
    "ing-concatenation\034objc-unsafe-perform-selector\003odr\016old-style-c"
    "ast\024old-style-definition\027opencl-unsupported-rgba\006openmp\024ope"
    "nmp-51-extensions\016openmp-clauses\021openmp-extensions\020openmp-loop"
    "-form\016openmp-mapping\015openmp-target\016option-ignored!ordered-comp"
    "are-function-pointers\027out-of-line-declaration\025out-of-scope-functi"
    "on\014over-aligned\010overflow\022overlength-strings\037overloaded-shif"
    "t-op-parentheses\022overloaded-virtual\015override-init\017override-mod"
    "ule\032overriding-method-mismatch\021overriding-option\006packed\016pac"
    "ked-non-pod\006padded\013parentheses\024parentheses-equality\024partial"
    "-availability\004pass\015pass-analysis\013pass-failed\013pass-missed\015"
    "pch-date-time\010pedantic\026pedantic-core-features\017pedantic-macros\020"
    "pessimizing-move\015pointer-arith\027pointer-bool-conversion\017pointer"
    "-compare\027pointer-integer-compare\014pointer-sign\024pointer-to-enum-"
    "cast\023pointer-to-int-cast\025pointer-type-mismatch\031poison-system-d"
    "irectories\033potentially-direct-selector potentially-evaluated-express"
    "ion\026pragma-clang-attribute\032pragma-once-outside-header\013pragma-p"
    "ack\036pragma-pack-suspicious-include#pragma-system-header-outside-head"
    "er\007pragmas\020pre-c++14-compat\031pre-c++14-compat-pedantic\020pre-c"
    "++17-compat\031pre-c++17-compat-pedantic\020pre-c++20-compat\031pre-c++"
    "20-compat-pedantic\020pre-c++23-compat\031pre-c++23-compat-pedantic\020"
    "pre-c++26-compat\031pre-c++26-compat-pedantic\020pre-c++2c-compat\031pr"
    "e-c++2c-compat-pedantic\016pre-c23-compat\027pre-c23-compat-pedantic\016"
    "pre-c2x-compat\027pre-c2x-compat-pedantic\024pre-openmp-51-compat&prede"
    "fined-identifier-outside-function\016private-extern\016private-header\016"
    "private-module\025profile-instr-missing\031profile-instr-out-of-date\030"
    "profile-instr-unprofiled\032property-access-dot-syntax\033property-attr"
    "ibute-mismatch\010protocol%protocol-property-synthesis-ambiguity\005psa"
    "bi\032qualified-void-return-type\"quoted-include-in-framework-header\023"
    "range-loop-analysis\031range-loop-bind-reference\024range-loop-construc"
    "t\027read-modules-implicitly\017read-only-types\032readonly-iboutlet-pr"
    "operty\015receiver-expr\026receiver-forward-class\027redeclared-class-m"
    "ember\026redundant-consteval-if\017redundant-decls\016redundant-move\020"
    "redundant-parens\010register\026reinterpret-base-class\025remark-backen"
    "d-plugin\007reorder\014reorder-ctor\021reorder-init-list\030requires-su"
    "per-attribute\021reserved-id-macro\023reserved-identifier\031reserved-m"
    "acro-identifier\032reserved-module-identifier\035reserved-user-defined-"
    "literal\022restrict-expansion\031retained-language-linkage\021return-lo"
    "cal-addr\024return-stack-address\017return-std-move\013return-type\025r"
    "eturn-type-c-linkage\020rewrite-not-bool\023round-trip-cc1-args\004rtti"
    "\020sanitize-address\025sarif-format-unstable\021search-path-usage\007s"
    "ection\010selector\026selector-type-mismatch\013self-assign\021self-ass"
    "ign-field\026self-assign-overloaded\tself-move\034semicolon-before-meth"
    "od-body\010sentinel\016sequence-point\026serialized-diagnostics\006shad"
    "ow\nshadow-all\014shadow-field\033shadow-field-in-constructor$shadow-fi"
    "eld-in-constructor-modified\013shadow-ivar\027shadow-uncaptured-local\024"
    "shift-count-negative\024shift-count-overflow\024shift-negative-value\024"
    "shift-op-parentheses\016shift-overflow\023shift-sign-overflow\020shorte"
    "n-64-to-32\014sign-compare\017sign-conversion\nsign-promo\024signed-enu"
    "m-bitfield\025signed-unsigned-wchar'single-bit-bitfield-constant-conver"
    "sion\025sizeof-array-argument\022sizeof-array-decay\020sizeof-array-div"
    "\022sizeof-pointer-div\030sizeof-pointer-memaccess\020slash-u-filename\014"
    "slh-asm-goto\nsloc-usage\027sometimes-uninitialized\nsource-mgr\022sour"
    "ce-uses-openmp\013spir-compat\014spirv-compat\017stack-exhausted\017sta"
    "ck-protector\021static-float-init\020static-in-inline$static-inline-exp"
    "licit-instantiation\026static-local-in-inline\020static-self-init\023st"
    "dlibcxx-not-found\017strict-aliasing\021strict-aliasing=0\021strict-ali"
    "asing=1\021strict-aliasing=2\017strict-overflow\021strict-overflow=0\021"
    "strict-overflow=1\021strict-overflow=2\021strict-overflow=3\021strict-o"
    "verflow=4\021strict-overflow=5\"strict-potentially-direct-selector\021s"
    "trict-prototypes\025strict-selector-match\016string-compare\024string-c"
    "oncatenation\021string-conversion\020string-plus-char\017string-plus-in"
    "t\024strlcpy-strlcat-size\014strncat-size\033suggest-destructor-overrid"
    "e\020suggest-override\033super-class-method-mismatch\020suspicious-bzer"
    "o\024suspicious-memaccess\024swift-name-attribute\006switch\013switch-b"
    "ool\016switch-default\013switch-enum\016sync-alignment%sync-fetch-and-n"
    "and-semantics-changed\005synth\036target-clones-mixed-specifiers\034tau"
    "tological-bitwise-compare\024tautological-compare\035tautological-const"
    "ant-compare&tautological-constant-in-range-compare*tautological-constan"
    "t-out-of-range-compare\035tautological-negation-compare\036tautological"
    "-objc-bool-compare\034tautological-overlap-compare\034tautological-poin"
    "ter-compare\037tautological-type-limit-compare\036tautological-undefine"
    "d-compare'tautological-unsigned-char-zero-compare'tautological-unsigned"
    "-enum-zero-compare\"tautological-unsigned-zero-compare tautological-val"
    "ue-range-compare\017tcb-enforcement$tentative-definition-incomplete-typ"
    "e\015thread-safety\026thread-safety-analysis\030thread-safety-attribute"
    "s\022thread-safety-beta\026thread-safety-negative\025thread-safety-prec"
    "ise\027thread-safety-reference\025thread-safety-verbose\ttrigraphs\013t"
    "ype-limits\013type-safety\024typedef-redefinition\020typename-missing\031"
    "unable-to-open-stats-file\020unaligned-access!unaligned-qualifier-impli"
    "cit-cast\030unavailable-declarations\023undeclared-selector\005undef\014"
    "undef-prefix\031undefined-bool-conversion\027undefined-func-template\020"
    "undefined-inline\022undefined-internal\027undefined-internal-type\032un"
    "defined-reinterpret-cast\026undefined-var-template\035underaligned-exce"
    "ption-object\026unevaluated-expression\026unguarded-availability\032ung"
    "uarded-availability-new\007unicode\021unicode-homoglyph\022unicode-whit"
    "espace\022unicode-zero-width\015uninitialized\035uninitialized-const-re"
    "ference\020unknown-argument\022unknown-assumption\022unknown-attributes"
    "\024unknown-cuda-version\022unknown-directives\027unknown-escape-sequen"
    "ce\017unknown-pragmas\022unknown-sanitizers\026unknown-warning-option\032"
    "unnamed-type-template-args\035unneeded-internal-declaration\030unneeded"
    "-member-function\031unqualified-std-cast-call\020unreachable-code\033un"
    "reachable-code-aggressive\026unreachable-code-break\034unreachable-code"
    "-fallthrough\036unreachable-code-generic-assoc\037unreachable-code-loop"
    "-increment\027unreachable-code-return\023unsafe-buffer-usage\013unseque"
    "nced\017unsupported-abi\017unsupported-abs\036unsupported-availability-"
    "guard\016unsupported-cb#unsupported-dll-base-class-template\036unsuppor"
    "ted-floating-point-opt\022unsupported-friend\021unsupported-gpopt\017un"
    "supported-nan\026unsupported-target-opt\026unsupported-visibility\037un"
    "usable-partial-specialization\006unused\017unused-argument\030unused-bu"
    "t-set-parameter\027unused-but-set-variable\034unused-command-line-argum"
    "ent\021unused-comparison\025unused-const-variable\032unused-exception-p"
    "arameter\017unused-function\032unused-getter-return-value\014unused-lab"
    "el\025unused-lambda-capture\024unused-local-typedef\025unused-local-typ"
    "edefs\015unused-macros\026unused-member-function\020unused-parameter\024"
    "unused-private-field\024unused-property-ivar\015unused-result\017unused"
    "-template\014unused-value\017unused-variable\026unused-volatile-lvalue\026"
    "used-but-marked-unused\025user-defined-literals\025user-defined-warning"
    "s\007varargs\017variadic-macros\015vec-elem-size\021vector-conversion\022"
    "vector-conversions\014vexing-parse\nvisibility\003vla\015vla-extension\031"
    "void-pointer-to-enum-cast\030void-pointer-to-int-cast\024void-ptr-deref"
    "erence\025volatile-register-var\023wasm-exception-spec\025weak-template"
    "-vtables\014weak-vtables\020writable-strings\015write-strings\017xor-us"
    "ed-as-pow\035zero-as-null-pointer-constant\021zero-length-array"};

#endif // GET_DIAG_ARRAYS


#ifdef DIAG_ENTRY
DIAG_ENTRY(anonymous_61 /*  */, 0, 0, /* DiagSubGroup0 */ 1, R"()")
DIAG_ENTRY(PoundPragmaMessage /* #pragma-messages */, 1, /* DiagArray1 */ 1, 0, R"()")
DIAG_ENTRY(PoundWarning /* #warnings */, 18, /* DiagArray2 */ 3, 0, R"()")
DIAG_ENTRY(anonymous_273 /* CFString-literal */, 28, /* DiagArray3 */ 5, 0, R"()")
DIAG_ENTRY(anonymous_60 /* CL4 */, 45, 0, /* DiagSubGroup4 */ 3, R"()")
DIAG_ENTRY(IndependentClassAttribute /* IndependentClass-attribute */, 49, /* DiagArray5 */ 7, 0, R"()")
DIAG_ENTRY(NSobjectAttribute /* NSObject-attribute */, 76, /* DiagArray6 */ 10, 0, R"()")
DIAG_ENTRY(anonymous_0 /* abi */, 95, 0, 0, R"()")
DIAG_ENTRY(AbsoluteValue /* absolute-value */, 99, /* DiagArray8 */ 12, 0, R"()")
DIAG_ENTRY(AbstractFinalClass /* abstract-final-class */, 114, /* DiagArray9 */ 17, 0, R"()")
DIAG_ENTRY(anonymous_266 /* abstract-vbase-init */, 135, /* DiagArray10 */ 19, 0, R"()")
DIAG_ENTRY(anonymous_59 /* address */, 155, 0, /* DiagSubGroup11 */ 6, R"()")
DIAG_ENTRY(anonymous_244 /* address-of-packed-member */, 163, /* DiagArray12 */ 21, 0, R"()")
DIAG_ENTRY(AddressOfTemporary /* address-of-temporary */, 188, /* DiagArray13 */ 23, 0, R"()")
DIAG_ENTRY(anonymous_2 /* aggregate-return */, 209, 0, 0, R"()")
DIAG_ENTRY(AIXCompat /* aix-compat */, 226, /* DiagArray15 */ 25, 0, R"()")
DIAG_ENTRY(anonymous_245 /* align-mismatch */, 237, /* DiagArray16 */ 28, 0, R"()")
DIAG_ENTRY(All /* all */, 252, 0, /* DiagSubGroup17 */ 10, R"()")
DIAG_ENTRY(anonymous_190 /* alloca */, 256, /* DiagArray18 */ 30, 0, R"()")
DIAG_ENTRY(anonymous_191 /* alloca-with-align-alignof */, 263, /* DiagArray19 */ 32, 0, R"()")
DIAG_ENTRY(AlwaysInlineCoroutine /* always-inline-coroutine */, 289, /* DiagArray20 */ 34, 0, R"()")
DIAG_ENTRY(anonymous_251 /* ambiguous-delete */, 313, /* DiagArray21 */ 36, 0, R"()")
DIAG_ENTRY(anonymous_145 /* ambiguous-ellipsis */, 330, /* DiagArray22 */ 38, 0, R"()")
DIAG_ENTRY(AmbiguousMacro /* ambiguous-macro */, 349, /* DiagArray23 */ 40, 0, R"()")
DIAG_ENTRY(AmbigMemberTemplate /* ambiguous-member-template */, 365, /* DiagArray24 */ 42, 0, R"()")
DIAG_ENTRY(anonymous_211 /* ambiguous-reversed-operator */, 391, /* DiagArray25 */ 44, 0, R"()")
DIAG_ENTRY(anonymous_102 /* analyzer-incompatible-plugin */, 419, /* DiagArray26 */ 46, 0, R"()")
DIAG_ENTRY(AnonEnumEnumConversion /* anon-enum-enum-conversion */, 448, /* DiagArray27 */ 48, /* DiagSubGroup27 */ 17, R"()")
DIAG_ENTRY(anonymous_146 /* anonymous-pack-parens */, 474, /* DiagArray28 */ 50, 0, R"()")
DIAG_ENTRY(AutomaticReferenceCounting /* arc */, 496, 0, /* DiagSubGroup29 */ 19, R"()")
DIAG_ENTRY(anonymous_144 /* arc-bridge-casts-disallowed-in-nonarc */, 500, /* DiagArray30 */ 52, 0, R"()")
DIAG_ENTRY(ARCRepeatedUseOfWeakMaybe /* arc-maybe-repeated-use-of-weak */, 538, /* DiagArray31 */ 54, 0, R"()")
DIAG_ENTRY(ARCNonPodMemAccess /* arc-non-pod-memaccess */, 569, /* DiagArray32 */ 56, 0, R"()")
DIAG_ENTRY(anonymous_174 /* arc-performSelector-leaks */, 591, /* DiagArray33 */ 58, 0, R"()")
DIAG_ENTRY(ARCRepeatedUseOfWeak /* arc-repeated-use-of-weak */, 617, /* DiagArray34 */ 60, /* DiagSubGroup34 */ 23, R"()")
DIAG_ENTRY(ARCRetainCycles /* arc-retain-cycles */, 642, /* DiagArray35 */ 62, 0, R"()")
DIAG_ENTRY(ARCUnsafeRetainedAssign /* arc-unsafe-retained-assign */, 660, /* DiagArray36 */ 64, 0, R"()")
DIAG_ENTRY(anonymous_275 /* argument-outside-range */, 687, /* DiagArray37 */ 68, 0, R"()")
DIAG_ENTRY(anonymous_276 /* argument-undefined-behaviour */, 710, /* DiagArray38 */ 70, 0, R"()")
DIAG_ENTRY(ArrayBounds /* array-bounds */, 739, /* DiagArray39 */ 72, 0, R"()")
DIAG_ENTRY(ArrayBoundsPointerArithmetic /* array-bounds-pointer-arithmetic */, 752, /* DiagArray40 */ 78, 0, R"()")
DIAG_ENTRY(ArrayParameter /* array-parameter */, 784, /* DiagArray41 */ 81, 0, R"()")
DIAG_ENTRY(ASM /* asm */, 800, 0, /* DiagSubGroup42 */ 25, R"()")
DIAG_ENTRY(ASMOperandWidths /* asm-operand-widths */, 804, /* DiagArray43 */ 83, 0, R"()")
DIAG_ENTRY(anonymous_274 /* assign-enum */, 823, /* DiagArray44 */ 85, 0, R"()")
DIAG_ENTRY(anonymous_161 /* assume */, 835, /* DiagArray45 */ 87, 0, R"()")
DIAG_ENTRY(anonymous_58 /* at-protocol */, 842, 0, 0, R"()")
DIAG_ENTRY(FrameworkHdrAtImport /* atimport-in-framework-header */, 854, /* DiagArray47 */ 89, 0, R"()")
DIAG_ENTRY(anonymous_233 /* atomic-access */, 883, /* DiagArray48 */ 91, 0, R"()")
DIAG_ENTRY(AtomicAlignment /* atomic-alignment */, 897, /* DiagArray49 */ 93, 0, R"()")
DIAG_ENTRY(anonymous_259 /* atomic-implicit-seq-cst */, 914, /* DiagArray50 */ 96, 0, R"()")
DIAG_ENTRY(anonymous_258 /* atomic-memory-ordering */, 938, /* DiagArray51 */ 98, 0, R"()")
DIAG_ENTRY(AtomicProperties /* atomic-properties */, 961, 0, /* DiagSubGroup52 */ 27, R"()")
DIAG_ENTRY(anonymous_169 /* atomic-property-with-user-defined-accessor */, 979, /* DiagArray53 */ 100, 0, R"()")
DIAG_ENTRY(anonymous_207 /* attribute-packed-for-bitfield */, 1022, /* DiagArray54 */ 102, 0, R"()")
DIAG_ENTRY(BackendWarningAttributes /* attribute-warning */, 1052, /* DiagArray55 */ 104, 0, R"()")
DIAG_ENTRY(Attributes /* attributes */, 1070, 0, /* DiagSubGroup56 */ 30, R"()")
DIAG_ENTRY(AutoDisableVptrSanitizer /* auto-disable-vptr-sanitizer */, 1081, /* DiagArray57 */ 106, 0, R"()")
DIAG_ENTRY(anonymous_3 /* auto-import */, 1109, 0, 0, R"()")
DIAG_ENTRY(anonymous_142 /* auto-storage-class */, 1121, /* DiagArray59 */ 108, 0, R"()")
DIAG_ENTRY(anonymous_187 /* auto-var-id */, 1140, /* DiagArray60 */ 110, 0, R"()")
DIAG_ENTRY(Availability /* availability */, 1152, /* DiagArray61 */ 112, 0, R"()")
DIAG_ENTRY(AVRRtlibLinkingQuirks /* avr-rtlib-linking-quirks */, 1165, /* DiagArray62 */ 123, 0, R"()")
DIAG_ENTRY(BackendPlugin /* backend-plugin */, 1190, /* DiagArray63 */ 129, 0, R"()")
DIAG_ENTRY(anonymous_105 /* backslash-newline-escape */, 1205, /* DiagArray64 */ 132, 0, R"()")
DIAG_ENTRY(BadFunctionCast /* bad-function-cast */, 1230, /* DiagArray65 */ 134, 0, R"()")
DIAG_ENTRY(BinaryLiteral /* binary-literal */, 1248, 0, /* DiagSubGroup66 */ 33, R"()")
DIAG_ENTRY(BindToTemporaryCopy /* bind-to-temporary-copy */, 1263, /* DiagArray67 */ 136, /* DiagSubGroup67 */ 37, R"()")
DIAG_ENTRY(anonymous_156 /* binding-in-condition */, 1286, /* DiagArray68 */ 139, 0, R"()")
DIAG_ENTRY(anonymous_149 /* bit-int-extension */, 1307, /* DiagArray69 */ 141, 0, R"()")
DIAG_ENTRY(BitFieldConstantConversion /* bitfield-constant-conversion */, 1325, /* DiagArray70 */ 143, /* DiagSubGroup70 */ 39, R"()")
DIAG_ENTRY(BitFieldEnumConversion /* bitfield-enum-conversion */, 1354, /* DiagArray71 */ 145, 0, R"()")
DIAG_ENTRY(BitFieldWidth /* bitfield-width */, 1379, /* DiagArray72 */ 149, 0, R"()")
DIAG_ENTRY(BitwiseConditionalParentheses /* bitwise-conditional-parentheses */, 1394, /* DiagArray73 */ 151, 0, R"()")
DIAG_ENTRY(BitwiseInsteadOfLogical /* bitwise-instead-of-logical */, 1426, /* DiagArray74 */ 153, 0, R"()")
DIAG_ENTRY(BitwiseOpParentheses /* bitwise-op-parentheses */, 1453, /* DiagArray75 */ 155, 0, R"()")
DIAG_ENTRY(BlockCaptureAutoReleasing /* block-capture-autoreleasing */, 1476, /* DiagArray76 */ 157, 0, R"()")
DIAG_ENTRY(BoolConversion /* bool-conversion */, 1504, /* DiagArray77 */ 159, /* DiagSubGroup77 */ 41, R"()")
DIAG_ENTRY(anonymous_66 /* bool-conversions */, 1520, 0, /* DiagSubGroup78 */ 44, R"()")
DIAG_ENTRY(BoolOperation /* bool-operation */, 1537, /* DiagArray79 */ 161, /* DiagSubGroup79 */ 46, R"()")
DIAG_ENTRY(anonymous_228 /* braced-scalar-init */, 1552, /* DiagArray80 */ 163, 0, R"()")
DIAG_ENTRY(BranchProtection /* branch-protection */, 1571, /* DiagArray81 */ 165, 0, R"()")
DIAG_ENTRY(ObjCBridge /* bridge-cast */, 1589, /* DiagArray82 */ 170, 0, R"()")
DIAG_ENTRY(anonymous_193 /* builtin-assume-aligned-alignment */, 1601, /* DiagArray83 */ 173, 0, R"()")
DIAG_ENTRY(BuiltinMacroRedefined /* builtin-macro-redefined */, 1634, /* DiagArray84 */ 175, 0, R"()")
DIAG_ENTRY(anonymous_162 /* builtin-memcpy-chk-size */, 1658, /* DiagArray85 */ 178, 0, R"()")
DIAG_ENTRY(BuiltinRequiresHeader /* builtin-requires-header */, 1682, /* DiagArray86 */ 180, 0, R"()")
DIAG_ENTRY(CXXCompat /* c++-compat */, 1706, /* DiagArray87 */ 182, 0, R"()")
DIAG_ENTRY(anonymous_25 /* c++0x-compat */, 1717, 0, /* DiagSubGroup88 */ 48, R"()")
DIAG_ENTRY(anonymous_70 /* c++0x-extensions */, 1730, 0, /* DiagSubGroup89 */ 50, R"()")
DIAG_ENTRY(anonymous_23 /* c++0x-narrowing */, 1747, 0, /* DiagSubGroup90 */ 52, R"()")
DIAG_ENTRY(CXX11Compat /* c++11-compat */, 1763, /* DiagArray91 */ 184, /* DiagSubGroup91 */ 54, R"()")
DIAG_ENTRY(CXX11CompatDeprecatedWritableStr /* c++11-compat-deprecated-writable-strings */, 1776, /* DiagArray92 */ 195, 0, R"()")
DIAG_ENTRY(CXX11CompatPedantic /* c++11-compat-pedantic */, 1817, 0, /* DiagSubGroup93 */ 62, R"()")
DIAG_ENTRY(CXX11CompatReservedUserDefinedLiteral /* c++11-compat-reserved-user-defined-literal */, 1839, /* DiagArray94 */ 197, 0, R"()")
DIAG_ENTRY(CXX11 /* c++11-extensions */, 1882, /* DiagArray95 */ 199, /* DiagSubGroup95 */ 68, R"()")
DIAG_ENTRY(CXX11ExtraSemi /* c++11-extra-semi */, 1899, /* DiagArray96 */ 226, 0, R"()")
DIAG_ENTRY(CXX11InlineNamespace /* c++11-inline-namespace */, 1916, /* DiagArray97 */ 228, 0, R"()")
DIAG_ENTRY(CXX11LongLong /* c++11-long-long */, 1939, /* DiagArray98 */ 230, 0, R"()")
DIAG_ENTRY(CXX11Narrowing /* c++11-narrowing */, 1955, /* DiagArray99 */ 232, 0, R"()")
DIAG_ENTRY(CXX14Attrs /* c++14-attribute-extensions */, 1971, /* DiagArray100 */ 240, 0, R"()")
DIAG_ENTRY(CXX14BinaryLiteral /* c++14-binary-literal */, 1998, /* DiagArray101 */ 242, 0, R"()")
DIAG_ENTRY(CXX14Compat /* c++14-compat */, 2019, 0, /* DiagSubGroup102 */ 72, R"()")
DIAG_ENTRY(CXX14CompatPedantic /* c++14-compat-pedantic */, 2032, 0, /* DiagSubGroup103 */ 76, R"()")
DIAG_ENTRY(CXX14 /* c++14-extensions */, 2054, /* DiagArray104 */ 244, /* DiagSubGroup104 */ 81, R"()")
DIAG_ENTRY(CXX17Attrs /* c++17-attribute-extensions */, 2071, /* DiagArray105 */ 252, 0, R"()")
DIAG_ENTRY(CXX17Compat /* c++17-compat */, 2098, 0, /* DiagSubGroup106 */ 84, R"()")
DIAG_ENTRY(CXX17CompatMangling /* c++17-compat-mangling */, 2111, /* DiagArray107 */ 254, 0, R"()")
DIAG_ENTRY(CXX17CompatPedantic /* c++17-compat-pedantic */, 2133, 0, /* DiagSubGroup108 */ 90, R"()")
DIAG_ENTRY(CXX17 /* c++17-extensions */, 2155, /* DiagArray109 */ 256, /* DiagSubGroup109 */ 94, R"()")
DIAG_ENTRY(anonymous_71 /* c++1y-extensions */, 2172, 0, /* DiagSubGroup110 */ 96, R"()")
DIAG_ENTRY(anonymous_26 /* c++1z-compat */, 2189, 0, /* DiagSubGroup111 */ 98, R"()")
DIAG_ENTRY(anonymous_12 /* c++1z-compat-mangling */, 2202, 0, /* DiagSubGroup112 */ 100, R"()")
DIAG_ENTRY(anonymous_72 /* c++1z-extensions */, 2224, 0, /* DiagSubGroup113 */ 102, R"()")
DIAG_ENTRY(CXX20Attrs /* c++20-attribute-extensions */, 2241, /* DiagArray114 */ 274, 0, R"()")
DIAG_ENTRY(CXX20Compat /* c++20-compat */, 2268, /* DiagArray115 */ 276, /* DiagSubGroup115 */ 104, R"()")
DIAG_ENTRY(CXX20CompatPedantic /* c++20-compat-pedantic */, 2281, 0, /* DiagSubGroup116 */ 106, R"()")
DIAG_ENTRY(CXX20Designator /* c++20-designator */, 2303, /* DiagArray117 */ 286, 0, R"()")
DIAG_ENTRY(CXX20 /* c++20-extensions */, 2320, /* DiagArray118 */ 288, /* DiagSubGroup118 */ 109, R"()")
DIAG_ENTRY(anonymous_271 /* c++23-default-comp-relaxed-constexpr */, 2337, /* DiagArray119 */ 310, 0, R"()")
DIAG_ENTRY(CXX23 /* c++23-extensions */, 2374, /* DiagArray120 */ 312, 0, R"()")
DIAG_ENTRY(CXX26 /* c++26-extensions */, 2391, /* DiagArray121 */ 324, 0, R"()")
DIAG_ENTRY(anonymous_27 /* c++2a-compat */, 2408, 0, /* DiagSubGroup122 */ 112, R"()")
DIAG_ENTRY(anonymous_28 /* c++2a-compat-pedantic */, 2421, 0, /* DiagSubGroup123 */ 114, R"()")
DIAG_ENTRY(anonymous_73 /* c++2a-extensions */, 2443, 0, /* DiagSubGroup124 */ 116, R"()")
DIAG_ENTRY(anonymous_74 /* c++2b-extensions */, 2460, 0, /* DiagSubGroup125 */ 118, R"()")
DIAG_ENTRY(anonymous_75 /* c++2c-extensions */, 2477, 0, /* DiagSubGroup126 */ 120, R"()")
DIAG_ENTRY(anonymous_19 /* c++98-c++11-c++14-c++17-compat */, 2494, 0, /* DiagSubGroup127 */ 122, R"()")
DIAG_ENTRY(anonymous_20 /* c++98-c++11-c++14-c++17-compat-pedantic */, 2525, 0, /* DiagSubGroup128 */ 124, R"()")
DIAG_ENTRY(anonymous_17 /* c++98-c++11-c++14-compat */, 2565, 0, /* DiagSubGroup129 */ 126, R"()")
DIAG_ENTRY(anonymous_18 /* c++98-c++11-c++14-compat-pedantic */, 2590, 0, /* DiagSubGroup130 */ 128, R"()")
DIAG_ENTRY(anonymous_15 /* c++98-c++11-compat */, 2624, 0, /* DiagSubGroup131 */ 130, R"()")
DIAG_ENTRY(CXXPre14CompatBinaryLiteral /* c++98-c++11-compat-binary-literal */, 2643, /* DiagArray132 */ 326, 0, R"()")
DIAG_ENTRY(anonymous_16 /* c++98-c++11-compat-pedantic */, 2677, 0, /* DiagSubGroup133 */ 132, R"()")
DIAG_ENTRY(CXX98Compat /* c++98-compat */, 2705, /* DiagArray134 */ 328, /* DiagSubGroup134 */ 134, R"()")
DIAG_ENTRY(CXX98CompatBindToTemporaryCopy /* c++98-compat-bind-to-temporary-copy */, 2718, /* DiagArray135 */ 388, 0, R"()")
DIAG_ENTRY(CXX98CompatExtraSemi /* c++98-compat-extra-semi */, 2754, /* DiagArray136 */ 390, 0, R"()")
DIAG_ENTRY(CXX98CompatLocalTypeTemplateArgs /* c++98-compat-local-type-template-args */, 2778, /* DiagArray137 */ 392, 0, R"()")
DIAG_ENTRY(CXX98CompatPedantic /* c++98-compat-pedantic */, 2816, /* DiagArray138 */ 394, /* DiagSubGroup138 */ 141, R"()")
DIAG_ENTRY(CXX98CompatUnnamedTypeTemplateArgs /* c++98-compat-unnamed-type-template-args */, 2838, /* DiagArray139 */ 404, 0, R"()")
DIAG_ENTRY(C11 /* c11-extensions */, 2878, /* DiagArray140 */ 406, 0, R"()")
DIAG_ENTRY(C23Compat /* c23-compat */, 2893, /* DiagArray141 */ 411, 0, R"()")
DIAG_ENTRY(C23 /* c23-extensions */, 2904, /* DiagArray142 */ 413, 0, R"()")
DIAG_ENTRY(anonymous_4 /* c2x-compat */, 2919, 0, /* DiagSubGroup143 */ 149, R"()")
DIAG_ENTRY(anonymous_76 /* c2x-extensions */, 2930, 0, /* DiagSubGroup144 */ 151, R"()")
DIAG_ENTRY(C99Compat /* c99-compat */, 2945, /* DiagArray145 */ 422, 0, R"()")
DIAG_ENTRY(C99Designator /* c99-designator */, 2956, /* DiagArray146 */ 427, /* DiagSubGroup146 */ 153, R"()")
DIAG_ENTRY(C99 /* c99-extensions */, 2971, /* DiagArray147 */ 433, /* DiagSubGroup147 */ 155, R"()")
DIAG_ENTRY(PureVirtualCallFromCtorDtor /* call-to-pure-virtual-from-ctor-dtor */, 2986, /* DiagArray148 */ 444, 0, R"()")
DIAG_ENTRY(CalledOnceParameter /* called-once-parameter */, 3022, /* DiagArray149 */ 446, /* DiagSubGroup149 */ 157, R"()")
DIAG_ENTRY(CastAlign /* cast-align */, 3044, /* DiagArray150 */ 450, 0, R"()")
DIAG_ENTRY(anonymous_261 /* cast-calling-convention */, 3055, /* DiagArray151 */ 452, 0, R"()")
DIAG_ENTRY(CastFunctionType /* cast-function-type */, 3079, /* DiagArray152 */ 454, /* DiagSubGroup152 */ 159, R"()")
DIAG_ENTRY(CastFunctionTypeStrict /* cast-function-type-strict */, 3098, /* DiagArray153 */ 456, 0, R"()")
DIAG_ENTRY(SelTypeCast /* cast-of-sel-type */, 3124, /* DiagArray154 */ 458, 0, R"()")
DIAG_ENTRY(CastQual /* cast-qual */, 3141, /* DiagArray155 */ 460, 0, R"()")
DIAG_ENTRY(anonymous_249 /* cast-qual-unrelated */, 3151, /* DiagArray156 */ 463, 0, R"()")
DIAG_ENTRY(anonymous_5 /* char-align */, 3171, 0, 0, R"()")
DIAG_ENTRY(CharSubscript /* char-subscripts */, 3182, /* DiagArray158 */ 465, 0, R"()")
DIAG_ENTRY(ClangClPch /* clang-cl-pch */, 3198, /* DiagArray159 */ 468, 0, R"()")
DIAG_ENTRY(ClassConversion /* class-conversion */, 3211, /* DiagArray160 */ 473, 0, R"()")
DIAG_ENTRY(ClassVarargs /* class-varargs */, 3228, /* DiagArray161 */ 477, /* DiagSubGroup161 */ 161, R"()")
DIAG_ENTRY(anonymous_194 /* cmse-union-leak */, 3242, /* DiagArray162 */ 479, 0, R"()")
DIAG_ENTRY(anonymous_151 /* comma */, 3258, /* DiagArray163 */ 481, 0, R"()")
DIAG_ENTRY(Comment /* comment */, 3264, /* DiagArray164 */ 483, 0, R"()")
DIAG_ENTRY(anonymous_64 /* comments */, 3272, 0, /* DiagSubGroup165 */ 163, R"()")
DIAG_ENTRY(CompareDistinctPointerType /* compare-distinct-pointer-types */, 3281, /* DiagArray166 */ 488, 0, R"()")
DIAG_ENTRY(CompletionHandler /* completion-handler */, 3312, /* DiagArray167 */ 490, 0, R"()")
DIAG_ENTRY(anonymous_230 /* complex-component-init */, 3331, /* DiagArray168 */ 494, 0, R"()")
DIAG_ENTRY(CompoundTokenSplit /* compound-token-split */, 3354, 0, /* DiagSubGroup169 */ 165, R"()")
DIAG_ENTRY(CompoundTokenSplitByMacro /* compound-token-split-by-macro */, 3375, /* DiagArray170 */ 496, 0, R"()")
DIAG_ENTRY(CompoundTokenSplitBySpace /* compound-token-split-by-space */, 3405, /* DiagArray171 */ 498, 0, R"()")
DIAG_ENTRY(anonymous_263 /* conditional-type-mismatch */, 3435, /* DiagArray172 */ 500, 0, R"()")
DIAG_ENTRY(UninitializedMaybe /* conditional-uninitialized */, 3461, /* DiagArray173 */ 502, 0, R"()")
DIAG_ENTRY(ConfigMacros /* config-macros */, 3487, /* DiagArray174 */ 504, 0, R"()")
DIAG_ENTRY(ConstantConversion /* constant-conversion */, 3501, /* DiagArray175 */ 506, /* DiagSubGroup175 */ 168, R"()")
DIAG_ENTRY(anonymous_83 /* constant-evaluated */, 3521, /* DiagArray176 */ 508, 0, R"()")
DIAG_ENTRY(anonymous_240 /* constant-logical-operand */, 3540, /* DiagArray177 */ 510, 0, R"()")
DIAG_ENTRY(anonymous_185 /* constexpr-not-const */, 3565, /* DiagArray178 */ 512, 0, R"()")
DIAG_ENTRY(Consumed /* consumed */, 3585, /* DiagArray179 */ 514, 0, R"()")
DIAG_ENTRY(Conversion /* conversion */, 3594, /* DiagArray180 */ 523, /* DiagSubGroup180 */ 171, R"()")
DIAG_ENTRY(anonymous_65 /* conversion-null */, 3605, 0, /* DiagSubGroup181 */ 187, R"()")
DIAG_ENTRY(CoroNonAlignedAllocationFunction /* coro-non-aligned-allocation-function */, 3621, /* DiagArray182 */ 529, 0, R"()")
DIAG_ENTRY(Coroutine /* coroutine */, 3658, /* DiagArray183 */ 531, /* DiagSubGroup183 */ 189, R"()")
DIAG_ENTRY(CoroutineMissingUnhandledException /* coroutine-missing-unhandled-exception */, 3668, /* DiagArray184 */ 533, 0, R"()")
DIAG_ENTRY(CoveredSwitchDefault /* covered-switch-default */, 3706, /* DiagArray185 */ 535, 0, R"()")
DIAG_ENTRY(anonymous_63 /* cpp */, 3729, 0, /* DiagSubGroup186 */ 194, R"()")
DIAG_ENTRY(ObjCCStringFormat /* cstring-format-directive */, 3733, /* DiagArray187 */ 537, 0, R"()")
DIAG_ENTRY(CTADMaybeUnsupported /* ctad-maybe-unsupported */, 3758, /* DiagArray188 */ 539, 0, R"()")
DIAG_ENTRY(anonymous_6 /* ctor-dtor-privacy */, 3781, 0, 0, R"()")
DIAG_ENTRY(CrossTU /* ctu */, 3799, /* DiagArray190 */ 541, 0, R"()")
DIAG_ENTRY(CudaCompat /* cuda-compat */, 3803, /* DiagArray191 */ 543, 0, R"()")
DIAG_ENTRY(CustomAtomic /* custom-atomic-properties */, 3815, /* DiagArray192 */ 549, 0, R"()")
DIAG_ENTRY(anonymous_147 /* cxx-attribute-extension */, 3840, /* DiagArray193 */ 551, 0, R"()")
DIAG_ENTRY(Dangling /* dangling */, 3864, /* DiagArray194 */ 553, /* DiagSubGroup194 */ 196, R"()")
DIAG_ENTRY(DanglingElse /* dangling-else */, 3873, /* DiagArray195 */ 556, 0, R"()")
DIAG_ENTRY(DanglingField /* dangling-field */, 3887, /* DiagArray196 */ 558, 0, R"()")
DIAG_ENTRY(DanglingGsl /* dangling-gsl */, 3902, /* DiagArray197 */ 563, 0, R"()")
DIAG_ENTRY(DanglingInitializerList /* dangling-initializer-list */, 3915, /* DiagArray198 */ 566, 0, R"()")
DIAG_ENTRY(anonymous_96 /* darwin-sdk-settings */, 3941, /* DiagArray199 */ 568, 0, R"()")
DIAG_ENTRY(anonymous_131 /* date-time */, 3961, /* DiagArray200 */ 570, 0, R"()")
DIAG_ENTRY(DeallocInCategory /* dealloc-in-category */, 3971, /* DiagArray201 */ 572, 0, R"()")
DIAG_ENTRY(anonymous_93 /* debug-compression-unavailable */, 3991, /* DiagArray202 */ 574, 0, R"()")
DIAG_ENTRY(DeclarationAfterStatement /* declaration-after-statement */, 4021, /* DiagArray203 */ 576, 0, R"()")
DIAG_ENTRY(DefaultedFunctionDeleted /* defaulted-function-deleted */, 4049, /* DiagArray204 */ 579, 0, R"()")
DIAG_ENTRY(DelegatingCtorCycles /* delegating-ctor-cycles */, 4076, /* DiagArray205 */ 582, 0, R"()")
DIAG_ENTRY(DeleteAbstractNonVirtualDtor /* delete-abstract-non-virtual-dtor */, 4099, /* DiagArray206 */ 584, 0, R"()")
DIAG_ENTRY(DeleteIncomplete /* delete-incomplete */, 4132, /* DiagArray207 */ 586, 0, R"()")
DIAG_ENTRY(DeleteNonAbstractNonVirtualDtor /* delete-non-abstract-non-virtual-dtor */, 4150, /* DiagArray208 */ 589, 0, R"()")
DIAG_ENTRY(DeleteNonVirtualDtor /* delete-non-virtual-dtor */, 4187, 0, /* DiagSubGroup209 */ 201, R"()")
DIAG_ENTRY(anonymous_113 /* delimited-escape-sequence-extension */, 4211, /* DiagArray210 */ 591, 0, R"()")
DIAG_ENTRY(anonymous_253 /* deprecate-lax-vec-conv-all */, 4247, /* DiagArray211 */ 593, 0, R"()")
DIAG_ENTRY(Deprecated /* deprecated */, 4274, /* DiagArray212 */ 595, /* DiagSubGroup212 */ 204, R"()")
DIAG_ENTRY(anonymous_252 /* deprecated-altivec-src-compat */, 4285, /* DiagArray213 */ 604, 0, R"()")
DIAG_ENTRY(DeprecatedAnonEnumEnumConversion /* deprecated-anon-enum-enum-conversion */, 4315, /* DiagArray214 */ 606, 0, R"()")
DIAG_ENTRY(DeprecatedArrayCompare /* deprecated-array-compare */, 4352, /* DiagArray215 */ 608, 0, R"()")
DIAG_ENTRY(DeprecatedAttributes /* deprecated-attributes */, 4377, /* DiagArray216 */ 610, 0, R"()")
DIAG_ENTRY(DeprecatedBuiltins /* deprecated-builtins */, 4399, /* DiagArray217 */ 614, 0, R"()")
DIAG_ENTRY(DeprecatedCommaSubscript /* deprecated-comma-subscript */, 4419, /* DiagArray218 */ 616, 0, R"()")
DIAG_ENTRY(DeprecatedCopy /* deprecated-copy */, 4446, /* DiagArray219 */ 618, /* DiagSubGroup219 */ 227, R"()")
DIAG_ENTRY(anonymous_7 /* deprecated-copy-dtor */, 4462, 0, /* DiagSubGroup220 */ 229, R"()")
DIAG_ENTRY(DeprecatedCopyWithDtor /* deprecated-copy-with-dtor */, 4483, /* DiagArray221 */ 620, /* DiagSubGroup221 */ 231, R"()")
DIAG_ENTRY(DeprecatedCopyWithUserProvidedCopy /* deprecated-copy-with-user-provided-copy */, 4509, /* DiagArray222 */ 622, 0, R"()")
DIAG_ENTRY(DeprecatedCopyWithUserProvidedDtor /* deprecated-copy-with-user-provided-dtor */, 4549, /* DiagArray223 */ 624, 0, R"()")
DIAG_ENTRY(DeprecatedCoroutine /* deprecated-coroutine */, 4589, /* DiagArray224 */ 626, 0, R"()")
DIAG_ENTRY(DeprecatedDeclarations /* deprecated-declarations */, 4610, /* DiagArray225 */ 628, 0, R"()")
DIAG_ENTRY(DeprecatedDynamicExceptionSpec /* deprecated-dynamic-exception-spec */, 4634, /* DiagArray226 */ 635, 0, R"()")
DIAG_ENTRY(DeprecatedEnumCompare /* deprecated-enum-compare */, 4668, /* DiagArray227 */ 637, 0, R"()")
DIAG_ENTRY(DeprecatedEnumCompareConditional /* deprecated-enum-compare-conditional */, 4692, /* DiagArray228 */ 639, 0, R"()")
DIAG_ENTRY(DeprecatedEnumEnumConversion /* deprecated-enum-enum-conversion */, 4728, /* DiagArray229 */ 641, 0, R"()")
DIAG_ENTRY(DeprecatedEnumFloatConversion /* deprecated-enum-float-conversion */, 4760, /* DiagArray230 */ 643, 0, R"()")
DIAG_ENTRY(DeprecatedImplementations /* deprecated-implementations */, 4793, /* DiagArray231 */ 645, 0, R"()")
DIAG_ENTRY(DeprecatedIncrementBool /* deprecated-increment-bool */, 4820, /* DiagArray232 */ 648, 0, R"()")
DIAG_ENTRY(DeprecatedLiteralOperator /* deprecated-literal-operator */, 4846, /* DiagArray233 */ 650, 0, R"()")
DIAG_ENTRY(DeprecatedNonPrototype /* deprecated-non-prototype */, 4874, /* DiagArray234 */ 652, 0, R"()")
DIAG_ENTRY(DeprecatedObjCIsaUsage /* deprecated-objc-isa-usage */, 4899, /* DiagArray235 */ 655, 0, R"()")
DIAG_ENTRY(ObjCPointerIntrospect /* deprecated-objc-pointer-introspection */, 4925, /* DiagArray236 */ 658, /* DiagSubGroup236 */ 233, R"()")
DIAG_ENTRY(ObjCPointerIntrospectPerformSelector /* deprecated-objc-pointer-introspection-performSelector */, 4963, /* DiagArray237 */ 660, 0, R"()")
DIAG_ENTRY(DeprecatedPragma /* deprecated-pragma */, 5017, /* DiagArray238 */ 662, 0, R"()")
DIAG_ENTRY(DeprecatedRedundantConstexprStaticDef /* deprecated-redundant-constexpr-static-def */, 5035, /* DiagArray239 */ 664, 0, R"()")
DIAG_ENTRY(DeprecatedRegister /* deprecated-register */, 5077, /* DiagArray240 */ 666, 0, R"()")
DIAG_ENTRY(DeprecatedStaticAnalyzerFlag /* deprecated-static-analyzer-flag */, 5097, /* DiagArray241 */ 668, 0, R"()")
DIAG_ENTRY(DeprecatedThisCapture /* deprecated-this-capture */, 5129, /* DiagArray242 */ 671, 0, R"()")
DIAG_ENTRY(DeprecatedType /* deprecated-type */, 5153, /* DiagArray243 */ 673, 0, R"()")
DIAG_ENTRY(DeprecatedVolatile /* deprecated-volatile */, 5169, /* DiagArray244 */ 675, 0, R"()")
DIAG_ENTRY(DeprecatedWritableStr /* deprecated-writable-strings */, 5189, 0, /* DiagSubGroup245 */ 235, R"()")
DIAG_ENTRY(anonymous_280 /* direct-ivar-access */, 5217, /* DiagArray246 */ 681, 0, R"()")
DIAG_ENTRY(anonymous_122 /* disabled-macro-expansion */, 5236, /* DiagArray247 */ 683, 0, R"()")
DIAG_ENTRY(anonymous_9 /* disabled-optimization */, 5261, 0, 0, R"()")
DIAG_ENTRY(anonymous_10 /* discard-qual */, 5283, 0, 0, R"()")
DIAG_ENTRY(DistributedObjectModifiers /* distributed-object-modifiers */, 5296, /* DiagArray250 */ 685, 0, R"()")
DIAG_ENTRY(anonymous_11 /* div-by-zero */, 5325, 0, /* DiagSubGroup251 */ 237, R"()")
DIAG_ENTRY(DivZero /* division-by-zero */, 5337, /* DiagArray252 */ 688, 0, R"()")
DIAG_ENTRY(anonymous_195 /* dll-attribute-on-redeclaration */, 5354, /* DiagArray253 */ 690, 0, R"()")
DIAG_ENTRY(DllexportExplicitInstantiationDecl /* dllexport-explicit-instantiation-decl */, 5385, /* DiagArray254 */ 692, 0, R"()")
DIAG_ENTRY(anonymous_196 /* dllimport-static-field-def */, 5423, /* DiagArray255 */ 694, 0, R"()")
DIAG_ENTRY(Documentation /* documentation */, 5450, /* DiagArray256 */ 696, /* DiagSubGroup256 */ 239, R"()")
DIAG_ENTRY(DocumentationDeprecatedSync /* documentation-deprecated-sync */, 5464, /* DiagArray257 */ 717, 0, R"()")
DIAG_ENTRY(DocumentationHTML /* documentation-html */, 5494, /* DiagArray258 */ 719, 0, R"()")
DIAG_ENTRY(DocumentationPedantic /* documentation-pedantic */, 5513, /* DiagArray259 */ 724, /* DiagSubGroup259 */ 242, R"()")
DIAG_ENTRY(DocumentationUnknownCommand /* documentation-unknown-command */, 5536, /* DiagArray260 */ 726, 0, R"()")
DIAG_ENTRY(anonymous_106 /* dollar-in-identifier-extension */, 5566, /* DiagArray261 */ 729, 0, R"()")
DIAG_ENTRY(DoublePromotion /* double-promotion */, 5597, /* DiagArray262 */ 731, 0, R"()")
DIAG_ENTRY(DtorName /* dtor-name */, 5614, /* DiagArray263 */ 733, 0, R"()")
DIAG_ENTRY(anonymous_184 /* dtor-typedef */, 5624, /* DiagArray264 */ 737, 0, R"()")
DIAG_ENTRY(DuplicateDeclSpecifier /* duplicate-decl-specifier */, 5637, /* DiagArray265 */ 739, 0, R"()")
DIAG_ENTRY(anonymous_150 /* duplicate-enum */, 5662, /* DiagArray266 */ 744, 0, R"()")
DIAG_ENTRY(DuplicateArgDecl /* duplicate-method-arg */, 5677, /* DiagArray267 */ 746, 0, R"()")
DIAG_ENTRY(MethodDuplicate /* duplicate-method-match */, 5698, /* DiagArray268 */ 748, 0, R"()")
DIAG_ENTRY(anonymous_164 /* duplicate-protocol */, 5721, /* DiagArray269 */ 750, 0, R"()")
DIAG_ENTRY(DXILValidation /* dxil-validation */, 5740, /* DiagArray270 */ 752, 0, R"()")
DIAG_ENTRY(DynamicClassMemaccess /* dynamic-class-memaccess */, 5756, /* DiagArray271 */ 754, 0, R"()")
DIAG_ENTRY(DynamicExceptionSpec /* dynamic-exception-spec */, 5780, /* DiagArray272 */ 756, /* DiagSubGroup272 */ 244, R"()")
DIAG_ENTRY(anonymous_288 /* eager-load-cxx-named-modules */, 5803, /* DiagArray273 */ 758, 0, R"()")
DIAG_ENTRY(anonymous_39 /* effc++ */, 5832, 0, /* DiagSubGroup274 */ 246, R"()")
DIAG_ENTRY(anonymous_137 /* elaborated-enum-base */, 5839, /* DiagArray275 */ 760, 0, R"()")
DIAG_ENTRY(anonymous_138 /* elaborated-enum-class */, 5860, /* DiagArray276 */ 762, 0, R"()")
DIAG_ENTRY(anonymous_128 /* embedded-directive */, 5882, /* DiagArray277 */ 764, 0, R"()")
DIAG_ENTRY(EmptyBody /* empty-body */, 5901, /* DiagArray278 */ 766, 0, R"()")
DIAG_ENTRY(anonymous_143 /* empty-decomposition */, 5912, /* DiagArray279 */ 772, 0, R"()")
DIAG_ENTRY(EmptyInitStatement /* empty-init-stmt */, 5932, /* DiagArray280 */ 774, 0, R"()")
DIAG_ENTRY(anonymous_134 /* empty-translation-unit */, 5948, /* DiagArray281 */ 776, 0, R"()")
DIAG_ENTRY(anonymous_199 /* encode-type */, 5971, /* DiagArray282 */ 778, 0, R"()")
DIAG_ENTRY(anonymous_62 /* endif-labels */, 5983, 0, /* DiagSubGroup283 */ 248, R"()")
DIAG_ENTRY(EnumCompare /* enum-compare */, 5996, /* DiagArray284 */ 780, /* DiagSubGroup284 */ 250, R"()")
DIAG_ENTRY(EnumCompareConditional /* enum-compare-conditional */, 6009, /* DiagArray285 */ 782, /* DiagSubGroup285 */ 253, R"()")
DIAG_ENTRY(EnumCompareSwitch /* enum-compare-switch */, 6034, /* DiagArray286 */ 784, 0, R"()")
DIAG_ENTRY(anonymous_82 /* enum-constexpr-conversion */, 6054, /* DiagArray287 */ 786, 0, R"()")
DIAG_ENTRY(EnumConversion /* enum-conversion */, 6080, /* DiagArray288 */ 788, /* DiagSubGroup288 */ 255, R"()")
DIAG_ENTRY(EnumEnumConversion /* enum-enum-conversion */, 6096, /* DiagArray289 */ 790, /* DiagSubGroup289 */ 259, R"()")
DIAG_ENTRY(EnumFloatConversion /* enum-float-conversion */, 6117, /* DiagArray290 */ 792, /* DiagSubGroup290 */ 261, R"()")
DIAG_ENTRY(EnumTooLarge /* enum-too-large */, 6139, /* DiagArray291 */ 794, 0, R"()")
DIAG_ENTRY(Exceptions /* exceptions */, 6154, /* DiagArray292 */ 797, 0, R"()")
DIAG_ENTRY(ExcessInitializers /* excess-initializers */, 6165, /* DiagArray293 */ 801, 0, R"()")
DIAG_ENTRY(anonymous_153 /* excessive-regsave */, 6185, /* DiagArray294 */ 806, 0, R"()")
DIAG_ENTRY(ExitTimeDestructors /* exit-time-destructors */, 6203, /* DiagArray295 */ 808, 0, R"()")
DIAG_ENTRY(ExpansionToDefined /* expansion-to-defined */, 6225, /* DiagArray296 */ 810, 0, R"()")
DIAG_ENTRY(anonymous_284 /* experimental-header-units */, 6246, /* DiagArray297 */ 813, 0, R"()")
DIAG_ENTRY(ExplicitInitializeCall /* explicit-initialize-call */, 6272, /* DiagArray298 */ 815, 0, R"()")
DIAG_ENTRY(anonymous_232 /* explicit-ownership-type */, 6297, /* DiagArray299 */ 818, 0, R"()")
DIAG_ENTRY(ExportUnnamed /* export-unnamed */, 6321, 0, 0, R"()")
DIAG_ENTRY(ExternCCompat /* extern-c-compat */, 6336, /* DiagArray301 */ 820, 0, R"()")
DIAG_ENTRY(anonymous_227 /* extern-initializer */, 6352, /* DiagArray302 */ 822, 0, R"()")
DIAG_ENTRY(Extra /* extra */, 6371, /* DiagArray303 */ 824, /* DiagSubGroup303 */ 263, R"()")
DIAG_ENTRY(anonymous_241 /* extra-qualification */, 6377, /* DiagArray304 */ 826, 0, R"()")
DIAG_ENTRY(ExtraSemi /* extra-semi */, 6397, /* DiagArray305 */ 828, /* DiagSubGroup305 */ 278, R"()")
DIAG_ENTRY(ExtraSemiStmt /* extra-semi-stmt */, 6408, /* DiagArray306 */ 831, /* DiagSubGroup306 */ 281, R"()")
DIAG_ENTRY(ExtraTokens /* extra-tokens */, 6424, /* DiagArray307 */ 833, 0, R"()")
DIAG_ENTRY(FinalDtorNonFinalClass /* final-dtor-non-final-class */, 6437, /* DiagArray308 */ 836, 0, R"()")
DIAG_ENTRY(FinalMacro /* final-macro */, 6464, /* DiagArray309 */ 838, 0, R"()")
DIAG_ENTRY(anonymous_136 /* fixed-enum-extension */, 6476, /* DiagArray310 */ 840, 0, R"()")
DIAG_ENTRY(anonymous_81 /* fixed-point-overflow */, 6497, /* DiagArray311 */ 842, 0, R"()")
DIAG_ENTRY(FlagEnum /* flag-enum */, 6518, /* DiagArray312 */ 844, 0, R"()")
DIAG_ENTRY(FlexibleArrayExtensions /* flexible-array-extensions */, 6528, /* DiagArray313 */ 846, 0, R"()")
DIAG_ENTRY(FloatConversion /* float-conversion */, 6554, /* DiagArray314 */ 849, /* DiagSubGroup314 */ 283, R"()")
DIAG_ENTRY(anonymous_234 /* float-equal */, 6571, /* DiagArray315 */ 851, 0, R"()")
DIAG_ENTRY(FloatOverflowConversion /* float-overflow-conversion */, 6583, /* DiagArray316 */ 853, 0, R"()")
DIAG_ENTRY(FloatZeroConversion /* float-zero-conversion */, 6609, /* DiagArray317 */ 856, 0, R"()")
DIAG_ENTRY(ForLoopAnalysis /* for-loop-analysis */, 6631, /* DiagArray318 */ 858, 0, R"()")
DIAG_ENTRY(Format /* format */, 6649, /* DiagArray319 */ 861, /* DiagSubGroup319 */ 286, R"()")
DIAG_ENTRY(FormatExtraArgs /* format-extra-args */, 6656, /* DiagArray320 */ 888, 0, R"()")
DIAG_ENTRY(FormatInsufficientArgs /* format-insufficient-args */, 6674, /* DiagArray321 */ 890, 0, R"()")
DIAG_ENTRY(FormatInvalidSpecifier /* format-invalid-specifier */, 6699, /* DiagArray322 */ 892, 0, R"()")
DIAG_ENTRY(FormatNonStandard /* format-non-iso */, 6724, /* DiagArray323 */ 894, 0, R"()")
DIAG_ENTRY(FormatNonLiteral /* format-nonliteral */, 6739, /* DiagArray324 */ 898, 0, R"()")
DIAG_ENTRY(FormatPedantic /* format-pedantic */, 6757, /* DiagArray325 */ 900, 0, R"()")
DIAG_ENTRY(FormatSecurity /* format-security */, 6773, /* DiagArray326 */ 903, 0, R"()")
DIAG_ENTRY(FormatTypeConfusion /* format-type-confusion */, 6789, /* DiagArray327 */ 905, 0, R"()")
DIAG_ENTRY(FormatY2K /* format-y2k */, 6811, 0, 0, R"()")
DIAG_ENTRY(FormatZeroLength /* format-zero-length */, 6822, /* DiagArray329 */ 907, 0, R"()")
DIAG_ENTRY(Format2 /* format=2 */, 6841, 0, /* DiagSubGroup330 */ 294, R"()")
DIAG_ENTRY(FortifySource /* fortify-source */, 6850, /* DiagArray331 */ 909, 0, R"()")
DIAG_ENTRY(FourByteMultiChar /* four-char-constants */, 6865, /* DiagArray332 */ 916, 0, R"()")
DIAG_ENTRY(FrameAddress /* frame-address */, 6885, /* DiagArray333 */ 918, 0, R"()")
DIAG_ENTRY(BackendFrameLargerThan /* frame-larger-than */, 6899, /* DiagArray334 */ 920, 0, R"(More fine grained information about the stack layout is available by adding the
`-Rpass-analysis=stack-frame-layout` command-line flag to the compiler
invocation.

The diagnostic information can be saved to a file in a machine readable format,
like YAML by adding the `-foptimization-record-file=<file>` command-line flag.

Results can be filtered by function name by passing
`-mllvm -filter-print-funcs=foo`, where `foo` is the target function's name.

   .. code-block:: console

      clang -c a.cpp -Rpass-analysis=stack-frame-layout -mllvm -filter-print-funcs=foo

   .. code-block:: console

      clang -c a.cpp -Rpass-analysis=stack-frame-layout -foptimization-record-file=<file>)")
DIAG_ENTRY(anonymous_78 /* frame-larger-than= */, 6917, 0, /* DiagSubGroup335 */ 298, R"()")
DIAG_ENTRY(FrameworkIncludePrivateFromPublic /* framework-include-private-from-public */, 6936, /* DiagArray336 */ 923, 0, R"()")
DIAG_ENTRY(FreeNonHeapObject /* free-nonheap-object */, 6974, /* DiagArray337 */ 925, 0, R"()")
DIAG_ENTRY(FunctionDefInObjCContainer /* function-def-in-objc-container */, 6994, /* DiagArray338 */ 927, 0, R"()")
DIAG_ENTRY(FunctionMultiVersioning /* function-multiversion */, 7025, /* DiagArray339 */ 929, /* DiagSubGroup339 */ 300, R"()")
DIAG_ENTRY(FUseLdPath /* fuse-ld-path */, 7047, /* DiagArray340 */ 934, 0, R"()")
DIAG_ENTRY(FutureAttrs /* future-attribute-extensions */, 7060, 0, /* DiagSubGroup341 */ 302, R"()")
DIAG_ENTRY(FutureCompat /* future-compat */, 7088, 0, 0, R"()")
DIAG_ENTRY(GccCompat /* gcc-compat */, 7102, /* DiagArray343 */ 936, 0, R"()")
DIAG_ENTRY(anonymous_140 /* generic-type-extension */, 7113, /* DiagArray344 */ 948, 0, R"()")
DIAG_ENTRY(GlobalConstructors /* global-constructors */, 7136, /* DiagArray345 */ 950, 0, R"()")
DIAG_ENTRY(GlobalISel /* global-isel */, 7156, /* DiagArray346 */ 953, 0, R"()")
DIAG_ENTRY(GNU /* gnu */, 7168, 0, /* DiagSubGroup347 */ 306, R"()")
DIAG_ENTRY(GNUAlignofExpression /* gnu-alignof-expression */, 7172, /* DiagArray348 */ 956, 0, R"()")
DIAG_ENTRY(GNUAnonymousStruct /* gnu-anonymous-struct */, 7195, /* DiagArray349 */ 958, 0, R"()")
DIAG_ENTRY(anonymous_243 /* gnu-array-member-paren-init */, 7216, /* DiagArray350 */ 960, 0, R"()")
DIAG_ENTRY(GNUAutoType /* gnu-auto-type */, 7244, /* DiagArray351 */ 962, 0, R"()")
DIAG_ENTRY(GNUBinaryLiteral /* gnu-binary-literal */, 7258, /* DiagArray352 */ 964, 0, R"()")
DIAG_ENTRY(GNUCaseRange /* gnu-case-range */, 7277, /* DiagArray353 */ 966, 0, R"()")
DIAG_ENTRY(GNUComplexInteger /* gnu-complex-integer */, 7292, /* DiagArray354 */ 968, 0, R"()")
DIAG_ENTRY(GNUCompoundLiteralInitializer /* gnu-compound-literal-initializer */, 7312, /* DiagArray355 */ 970, 0, R"()")
DIAG_ENTRY(GNUConditionalOmittedOperand /* gnu-conditional-omitted-operand */, 7345, /* DiagArray356 */ 972, 0, R"()")
DIAG_ENTRY(GNUDesignator /* gnu-designator */, 7377, /* DiagArray357 */ 974, 0, R"()")
DIAG_ENTRY(anonymous_1 /* gnu-empty-initializer */, 7392, 0, 0, R"()")
DIAG_ENTRY(GNUEmptyStruct /* gnu-empty-struct */, 7414, /* DiagArray359 */ 978, 0, R"()")
DIAG_ENTRY(GNUFlexibleArrayInitializer /* gnu-flexible-array-initializer */, 7431, /* DiagArray360 */ 982, 0, R"()")
DIAG_ENTRY(GNUFlexibleArrayUnionMember /* gnu-flexible-array-union-member */, 7462, /* DiagArray361 */ 984, 0, R"()")
DIAG_ENTRY(GNUFoldingConstant /* gnu-folding-constant */, 7494, /* DiagArray362 */ 986, 0, R"()")
DIAG_ENTRY(GNUImaginaryConstant /* gnu-imaginary-constant */, 7515, /* DiagArray363 */ 990, 0, R"()")
DIAG_ENTRY(GNUIncludeNext /* gnu-include-next */, 7538, /* DiagArray364 */ 992, 0, R"()")
DIAG_ENTRY(anonymous_200 /* gnu-inline-cpp-without-extern */, 7555, /* DiagArray365 */ 994, 0, R"()")
DIAG_ENTRY(GNULabelsAsValue /* gnu-label-as-value */, 7585, /* DiagArray366 */ 996, 0, R"()")
DIAG_ENTRY(GNULineMarker /* gnu-line-marker */, 7604, /* DiagArray367 */ 999, 0, R"()")
DIAG_ENTRY(GNUNullPointerArithmetic /* gnu-null-pointer-arithmetic */, 7620, /* DiagArray368 */ 1001, 0, R"()")
DIAG_ENTRY(GNUOffsetofExtensions /* gnu-offsetof-extensions */, 7648, /* DiagArray369 */ 1003, 0, R"()")
DIAG_ENTRY(GNUPointerArith /* gnu-pointer-arith */, 7672, /* DiagArray370 */ 1005, 0, R"()")
DIAG_ENTRY(GNURedeclaredEnum /* gnu-redeclared-enum */, 7690, /* DiagArray371 */ 1009, 0, R"()")
DIAG_ENTRY(GNUStatementExpression /* gnu-statement-expression */, 7710, /* DiagArray372 */ 1011, /* DiagSubGroup372 */ 338, R"()")
DIAG_ENTRY(GNUStatementExpressionFromMacroExpansion /* gnu-statement-expression-from-macro-expansion */, 7735, /* DiagArray373 */ 1013, 0, R"()")
DIAG_ENTRY(GNUStaticFloatInit /* gnu-static-float-init */, 7781, /* DiagArray374 */ 1015, 0, R"()")
DIAG_ENTRY(GNUStringLiteralOperatorTemplate /* gnu-string-literal-operator-template */, 7803, /* DiagArray375 */ 1017, 0, R"()")
DIAG_ENTRY(GNUUnionCast /* gnu-union-cast */, 7840, /* DiagArray376 */ 1019, 0, R"()")
DIAG_ENTRY(GNUVariableSizedTypeNotAtEnd /* gnu-variable-sized-type-not-at-end */, 7855, /* DiagArray377 */ 1021, 0, R"()")
DIAG_ENTRY(GNUZeroLineDirective /* gnu-zero-line-directive */, 7890, /* DiagArray378 */ 1023, 0, R"()")
DIAG_ENTRY(GNUZeroVariadicMacroArguments /* gnu-zero-variadic-macro-arguments */, 7914, /* DiagArray379 */ 1025, 0, R"()")
DIAG_ENTRY(anonymous_260 /* gpu-maybe-wrong-side */, 7948, /* DiagArray380 */ 1028, 0, R"()")
DIAG_ENTRY(anonymous_133 /* header-guard */, 7969, /* DiagArray381 */ 1030, 0, R"()")
DIAG_ENTRY(HeaderHygiene /* header-hygiene */, 7982, /* DiagArray382 */ 1032, 0, R"()")
DIAG_ENTRY(HIPOpenMPOffloading /* hip-omp-target-directives */, 7997, /* DiagArray383 */ 1034, 0, R"()")
DIAG_ENTRY(HIPOnly /* hip-only */, 8023, /* DiagArray384 */ 1036, 0, R"()")
DIAG_ENTRY(HLSLExtension /* hlsl-extensions */, 8032, /* DiagArray385 */ 1038, 0, R"()")
DIAG_ENTRY(anonymous_255 /* idiomatic-parentheses */, 8048, /* DiagArray386 */ 1040, 0, R"()")
DIAG_ENTRY(IgnoredAttributes /* ignored-attributes */, 8070, /* DiagArray387 */ 1042, 0, R"()")
DIAG_ENTRY(anonymous_203 /* ignored-availability-without-sdk-settings */, 8089, /* DiagArray388 */ 1127, 0, R"()")
DIAG_ENTRY(IgnoredOptimizationArgument /* ignored-optimization-argument */, 8131, /* DiagArray389 */ 1129, 0, R"()")
DIAG_ENTRY(IgnoredPragmaIntrinsic /* ignored-pragma-intrinsic */, 8161, /* DiagArray390 */ 1132, 0, R"()")
DIAG_ENTRY(IgnoredPragmaOptimize /* ignored-pragma-optimize */, 8186, 0, 0, R"()")
DIAG_ENTRY(IgnoredPragmas /* ignored-pragmas */, 8210, /* DiagArray392 */ 1134, /* DiagSubGroup392 */ 340, R"()")
DIAG_ENTRY(IgnoredQualifiers /* ignored-qualifiers */, 8226, /* DiagArray393 */ 1184, /* DiagSubGroup393 */ 343, R"()")
DIAG_ENTRY(IgnoredReferenceQualifiers /* ignored-reference-qualifiers */, 8245, /* DiagArray394 */ 1189, 0, R"()")
DIAG_ENTRY(Implicit /* implicit */, 8274, 0, /* DiagSubGroup395 */ 345, R"()")
DIAG_ENTRY(ImplicitAtomic /* implicit-atomic-properties */, 8283, /* DiagArray396 */ 1191, 0, R"()")
DIAG_ENTRY(ImplicitConstIntFloatConversion /* implicit-const-int-float-conversion */, 8310, /* DiagArray397 */ 1194, 0, R"()")
DIAG_ENTRY(ImplicitConversionFloatingPointToBool /* implicit-conversion-floating-point-to-bool */, 8346, /* DiagArray398 */ 1196, 0, R"()")
DIAG_ENTRY(anonymous_272 /* implicit-exception-spec-mismatch */, 8389, /* DiagArray399 */ 1198, 0, R"()")
DIAG_ENTRY(ImplicitFallthrough /* implicit-fallthrough */, 8422, /* DiagArray400 */ 1200, /* DiagSubGroup400 */ 348, R"()")
DIAG_ENTRY(ImplicitFallthroughPerFunction /* implicit-fallthrough-per-function */, 8443, /* DiagArray401 */ 1202, 0, R"()")
DIAG_ENTRY(ImplicitFixedPointConversion /* implicit-fixed-point-conversion */, 8477, /* DiagArray402 */ 1204, 0, R"()")
DIAG_ENTRY(ImplicitFloatConversion /* implicit-float-conversion */, 8509, /* DiagArray403 */ 1206, /* DiagSubGroup403 */ 350, R"()")
DIAG_ENTRY(ImplicitFunctionDeclare /* implicit-function-declaration */, 8535, /* DiagArray404 */ 1209, 0, R"()")
DIAG_ENTRY(ImplicitInt /* implicit-int */, 8565, /* DiagArray405 */ 1215, 0, R"()")
DIAG_ENTRY(ImplicitIntConversion /* implicit-int-conversion */, 8578, /* DiagArray406 */ 1219, /* DiagSubGroup406 */ 353, R"()")
DIAG_ENTRY(ImplicitIntFloatConversion /* implicit-int-float-conversion */, 8602, /* DiagArray407 */ 1222, /* DiagSubGroup407 */ 355, R"()")
DIAG_ENTRY(anonymous_175 /* implicit-retain-self */, 8632, /* DiagArray408 */ 1224, 0, R"()")
DIAG_ENTRY(ImplicitlyUnsignedLiteral /* implicitly-unsigned-literal */, 8653, /* DiagArray409 */ 1226, 0, R"()")
DIAG_ENTRY(anonymous_30 /* import */, 8681, 0, 0, R"()")
DIAG_ENTRY(anonymous_127 /* import-preprocessor-directive-pedantic */, 8688, /* DiagArray411 */ 1228, 0, R"()")
DIAG_ENTRY(anonymous_268 /* inaccessible-base */, 8727, /* DiagArray412 */ 1230, 0, R"()")
DIAG_ENTRY(anonymous_117 /* include-next-absolute-path */, 8745, /* DiagArray413 */ 1232, 0, R"()")
DIAG_ENTRY(anonymous_116 /* include-next-outside-header */, 8772, /* DiagArray414 */ 1234, 0, R"()")
DIAG_ENTRY(IncompatibleExceptionSpec /* incompatible-exception-spec */, 8800, /* DiagArray415 */ 1236, 0, R"()")
DIAG_ENTRY(IncompatibleFunctionPointerTypes /* incompatible-function-pointer-types */, 8828, /* DiagArray416 */ 1239, 0, R"()")
DIAG_ENTRY(anonymous_257 /* incompatible-function-pointer-types-strict */, 8864, /* DiagArray417 */ 1241, 0, R"()")
DIAG_ENTRY(anonymous_158 /* incompatible-library-redeclaration */, 8907, /* DiagArray418 */ 1243, 0, R"()")
DIAG_ENTRY(IncompatibleMSPragmaSection /* incompatible-ms-pragma-section */, 8942, /* DiagArray419 */ 1245, 0, R"()")
DIAG_ENTRY(IncompatibleMSStruct /* incompatible-ms-struct */, 8973, /* DiagArray420 */ 1247, 0, R"()")
DIAG_ENTRY(IncompatiblePointerTypes /* incompatible-pointer-types */, 8996, /* DiagArray421 */ 1250, /* DiagSubGroup421 */ 357, R"()")
DIAG_ENTRY(IncompatiblePointerTypesDiscardsQualifiers /* incompatible-pointer-types-discards-qualifiers */, 9023, /* DiagArray422 */ 1252, 0, R"()")
DIAG_ENTRY(anonymous_165 /* incompatible-property-type */, 9070, /* DiagArray423 */ 1256, 0, R"()")
DIAG_ENTRY(anonymous_92 /* incompatible-sysroot */, 9097, /* DiagArray424 */ 1258, 0, R"()")
DIAG_ENTRY(IncompleteFrameworkModuleDeclaration /* incomplete-framework-module-declaration */, 9118, /* DiagArray425 */ 1260, 0, R"()")
DIAG_ENTRY(anonymous_167 /* incomplete-implementation */, 9158, /* DiagArray426 */ 1262, 0, R"()")
DIAG_ENTRY(IncompleteModule /* incomplete-module */, 9184, 0, /* DiagSubGroup427 */ 360, R"()")
DIAG_ENTRY(anonymous_157 /* incomplete-setjmp-declaration */, 9202, /* DiagArray428 */ 1264, 0, R"()")
DIAG_ENTRY(IncompleteUmbrella /* incomplete-umbrella */, 9232, /* DiagArray429 */ 1266, 0, R"()")
DIAG_ENTRY(MicrosoftInconsistentDllImport /* inconsistent-dllimport */, 9252, /* DiagArray430 */ 1270, 0, R"()")
DIAG_ENTRY(CXX11WarnInconsistentOverrideDestructor /* inconsistent-missing-destructor-override */, 9275, /* DiagArray431 */ 1273, 0, R"()")
DIAG_ENTRY(CXX11WarnInconsistentOverrideMethod /* inconsistent-missing-override */, 9316, /* DiagArray432 */ 1275, 0, R"()")
DIAG_ENTRY(IncrementBool /* increment-bool */, 9346, /* DiagArray433 */ 1277, /* DiagSubGroup433 */ 363, R"()")
DIAG_ENTRY(InfiniteRecursion /* infinite-recursion */, 9361, /* DiagArray434 */ 1279, 0, R"()")
DIAG_ENTRY(anonymous_31 /* init-self */, 9380, 0, 0, R"()")
DIAG_ENTRY(InitializerOverrides /* initializer-overrides */, 9390, /* DiagArray436 */ 1281, 0, R"()")
DIAG_ENTRY(anonymous_183 /* injected-class-name */, 9412, /* DiagArray437 */ 1285, 0, R"()")
DIAG_ENTRY(anonymous_32 /* inline */, 9432, 0, 0, R"()")
DIAG_ENTRY(BackendInlineAsm /* inline-asm */, 9439, /* DiagArray439 */ 1287, 0, R"()")
DIAG_ENTRY(InlineNamespaceReopenedNoninline /* inline-namespace-reopened-noninline */, 9450, /* DiagArray440 */ 1289, 0, R"()")
DIAG_ENTRY(anonymous_269 /* inline-new-delete */, 9486, /* DiagArray441 */ 1291, 0, R"()")
DIAG_ENTRY(anonymous_216 /* instantiation-after-specialization */, 9504, /* DiagArray442 */ 1293, 0, R"()")
DIAG_ENTRY(IntConversion /* int-conversion */, 9539, /* DiagArray443 */ 1295, 0, R"()")
DIAG_ENTRY(anonymous_67 /* int-conversions */, 9554, 0, /* DiagSubGroup444 */ 365, R"()")
DIAG_ENTRY(IntInBoolContext /* int-in-bool-context */, 9570, /* DiagArray445 */ 1298, 0, R"()")
DIAG_ENTRY(IntToPointerCast /* int-to-pointer-cast */, 9590, /* DiagArray446 */ 1301, /* DiagSubGroup446 */ 367, R"()")
DIAG_ENTRY(IntToVoidPointerCast /* int-to-void-pointer-cast */, 9610, /* DiagArray447 */ 1303, 0, R"()")
DIAG_ENTRY(anonymous_80 /* integer-overflow */, 9635, /* DiagArray448 */ 1305, 0, R"()")
DIAG_ENTRY(InvalidCommandLineArgument /* invalid-command-line-argument */, 9652, /* DiagArray449 */ 1307, /* DiagSubGroup449 */ 369, R"()")
DIAG_ENTRY(anonymous_186 /* invalid-constexpr */, 9682, /* DiagArray450 */ 1319, 0, R"()")
DIAG_ENTRY(ObjCInvalidIBOutletProperty /* invalid-iboutlet */, 9700, /* DiagArray451 */ 1321, 0, R"()")
DIAG_ENTRY(anonymous_197 /* invalid-initializer-from-system-header */, 9717, /* DiagArray452 */ 1324, 0, R"()")
DIAG_ENTRY(InvalidIOSDeploymentTarget /* invalid-ios-deployment-target */, 9756, /* DiagArray453 */ 1326, 0, R"()")
DIAG_ENTRY(anonymous_209 /* invalid-no-builtin-names */, 9786, /* DiagArray454 */ 1328, 0, R"()")
DIAG_ENTRY(InvalidNoreturn /* invalid-noreturn */, 9811, /* DiagArray455 */ 1330, 0, R"()")
DIAG_ENTRY(InvalidOffsetof /* invalid-offsetof */, 9828, /* DiagArray456 */ 1333, 0, R"()")
DIAG_ENTRY(InvalidOrNonExistentDirectory /* invalid-or-nonexistent-directory */, 9845, /* DiagArray457 */ 1336, 0, R"()")
DIAG_ENTRY(anonymous_214 /* invalid-partial-specialization */, 9878, /* DiagArray458 */ 1338, 0, R"()")
DIAG_ENTRY(anonymous_33 /* invalid-pch */, 9909, 0, 0, R"()")
DIAG_ENTRY(InvalidPPToken /* invalid-pp-token */, 9921, /* DiagArray460 */ 1340, 0, R"()")
DIAG_ENTRY(InvalidSourceEncoding /* invalid-source-encoding */, 9938, /* DiagArray461 */ 1343, 0, R"()")
DIAG_ENTRY(anonymous_178 /* invalid-static-assert-message */, 9962, /* DiagArray462 */ 1346, 0, R"()")
DIAG_ENTRY(anonymous_130 /* invalid-token-paste */, 9992, /* DiagArray463 */ 1348, 0, R"()")
DIAG_ENTRY(anonymous_115 /* invalid-unevaluated-string */, 10012, /* DiagArray464 */ 1350, 0, R"()")
DIAG_ENTRY(anonymous_108 /* invalid-utf8 */, 10039, /* DiagArray465 */ 1352, 0, R"()")
DIAG_ENTRY(anonymous_254 /* jump-seh-finally */, 10052, /* DiagArray466 */ 1354, 0, R"()")
DIAG_ENTRY(KeywordCompat /* keyword-compat */, 10069, /* DiagArray467 */ 1356, 0, R"()")
DIAG_ENTRY(KeywordAsMacro /* keyword-macro */, 10084, /* DiagArray468 */ 1358, 0, R"()")
DIAG_ENTRY(KNRPromotedParameter /* knr-promoted-parameter */, 10098, /* DiagArray469 */ 1360, 0, R"()")
DIAG_ENTRY(anonymous_107 /* language-extension-token */, 10121, /* DiagArray470 */ 1362, 0, R"()")
DIAG_ENTRY(LargeByValueCopy /* large-by-value-copy */, 10146, /* DiagArray471 */ 1364, 0, R"()")
DIAG_ENTRY(LibLTO /* liblto */, 10166, 0, 0, R"()")
DIAG_ENTRY(LinkerWarnings /* linker-warnings */, 10173, /* DiagArray473 */ 1367, 0, R"()")
DIAG_ENTRY(LiteralConversion /* literal-conversion */, 10189, /* DiagArray474 */ 1369, 0, R"()")
DIAG_ENTRY(LiteralRange /* literal-range */, 10208, /* DiagArray475 */ 1372, 0, R"()")
DIAG_ENTRY(LocalTypeTemplateArgs /* local-type-template-args */, 10222, /* DiagArray476 */ 1376, /* DiagSubGroup476 */ 371, R"()")
DIAG_ENTRY(LogicalNotParentheses /* logical-not-parentheses */, 10247, /* DiagArray477 */ 1378, 0, R"()")
DIAG_ENTRY(LogicalOpParentheses /* logical-op-parentheses */, 10271, /* DiagArray478 */ 1380, 0, R"()")
DIAG_ENTRY(LongLong /* long-long */, 10294, /* DiagArray479 */ 1382, /* DiagSubGroup479 */ 373, R"()")
DIAG_ENTRY(LoopAnalysis /* loop-analysis */, 10304, 0, /* DiagSubGroup480 */ 375, R"()")
DIAG_ENTRY(MacroRedefined /* macro-redefined */, 10318, /* DiagArray481 */ 1384, 0, R"()")
DIAG_ENTRY(Main /* main */, 10334, /* DiagArray482 */ 1386, 0, R"()")
DIAG_ENTRY(MainReturnType /* main-return-type */, 10339, /* DiagArray483 */ 1394, 0, R"()")
DIAG_ENTRY(MalformedWarningCheck /* malformed-warning-check */, 10356, /* DiagArray484 */ 1396, 0, R"()")
DIAG_ENTRY(anonymous_229 /* many-braces-around-scalar-init */, 10380, /* DiagArray485 */ 1398, 0, R"()")
DIAG_ENTRY(anonymous_112 /* mathematical-notation-identifier-extension */, 10411, /* DiagArray486 */ 1400, 0, R"()")
DIAG_ENTRY(MaxTokens /* max-tokens */, 10454, /* DiagArray487 */ 1402, 0, R"(The warning is issued if the number of pre-processor tokens exceeds
the token limit, which can be set in three ways:

1. As a limit at a specific point in a file, using the ``clang max_tokens_here``
   pragma:

   .. code-block:: c++

      #pragma clang max_tokens_here 1234

2. As a per-translation unit limit, using the ``-fmax-tokens=`` command-line
   flag:

   .. code-block:: console

      clang -c a.cpp -fmax-tokens=1234

3. As a per-translation unit limit using the ``clang max_tokens_total`` pragma,
   which works like and overrides the ``-fmax-tokens=`` flag:

   .. code-block:: c++

      #pragma clang max_tokens_total 1234

These limits can be helpful in limiting code growth through included files.

Setting a token limit of zero means no limit.

Note that the warning is disabled by default, so -Wmax-tokens must be used
in addition with the pragmas or -fmax-tokens flag to get any warnings.)")
DIAG_ENTRY(MaxUnsignedZero /* max-unsigned-zero */, 10465, /* DiagArray488 */ 1405, 0, R"()")
DIAG_ENTRY(MemsetTransposedArgs /* memset-transposed-args */, 10483, /* DiagArray489 */ 1407, 0, R"()")
DIAG_ENTRY(anonymous_160 /* memsize-comparison */, 10506, /* DiagArray490 */ 1409, 0, R"()")
DIAG_ENTRY(MethodSignatures /* method-signatures */, 10525, /* DiagArray491 */ 1411, 0, R"()")
DIAG_ENTRY(Microsoft /* microsoft */, 10543, 0, /* DiagSubGroup492 */ 378, R"()")
DIAG_ENTRY(MicrosoftAbstract /* microsoft-abstract */, 10553, /* DiagArray493 */ 1414, 0, R"()")
DIAG_ENTRY(MicrosoftAnonTag /* microsoft-anon-tag */, 10572, /* DiagArray494 */ 1416, 0, R"()")
DIAG_ENTRY(MicrosoftCast /* microsoft-cast */, 10591, /* DiagArray495 */ 1419, 0, R"()")
DIAG_ENTRY(MicrosoftCharize /* microsoft-charize */, 10606, /* DiagArray496 */ 1422, 0, R"()")
DIAG_ENTRY(MicrosoftCommentPaste /* microsoft-comment-paste */, 10624, /* DiagArray497 */ 1424, 0, R"()")
DIAG_ENTRY(MicrosoftConstInit /* microsoft-const-init */, 10648, /* DiagArray498 */ 1426, 0, R"()")
DIAG_ENTRY(MicrosoftCppMacro /* microsoft-cpp-macro */, 10669, /* DiagArray499 */ 1428, 0, R"()")
DIAG_ENTRY(MicrosoftDefaultArgRedefinition /* microsoft-default-arg-redefinition */, 10689, /* DiagArray500 */ 1430, 0, R"()")
DIAG_ENTRY(MicrosoftDrectveSection /* microsoft-drectve-section */, 10724, /* DiagArray501 */ 1432, 0, R"()")
DIAG_ENTRY(MicrosoftEndOfFile /* microsoft-end-of-file */, 10750, /* DiagArray502 */ 1434, 0, R"()")
DIAG_ENTRY(MicrosoftEnumForwardReference /* microsoft-enum-forward-reference */, 10772, /* DiagArray503 */ 1436, 0, R"()")
DIAG_ENTRY(MicrosoftEnumValue /* microsoft-enum-value */, 10805, /* DiagArray504 */ 1438, 0, R"()")
DIAG_ENTRY(MicrosoftExceptionSpec /* microsoft-exception-spec */, 10826, /* DiagArray505 */ 1440, 0, R"()")
DIAG_ENTRY(anonymous_139 /* microsoft-exists */, 10851, /* DiagArray506 */ 1446, 0, R"()")
DIAG_ENTRY(MicrosoftExplicitConstructorCall /* microsoft-explicit-constructor-call */, 10868, /* DiagArray507 */ 1448, 0, R"()")
DIAG_ENTRY(MicrosoftExtraQualification /* microsoft-extra-qualification */, 10904, /* DiagArray508 */ 1450, 0, R"()")
DIAG_ENTRY(MicrosoftFixedEnum /* microsoft-fixed-enum */, 10934, /* DiagArray509 */ 1452, 0, R"()")
DIAG_ENTRY(MicrosoftFlexibleArray /* microsoft-flexible-array */, 10955, /* DiagArray510 */ 1454, 0, R"()")
DIAG_ENTRY(MicrosoftGoto /* microsoft-goto */, 10980, /* DiagArray511 */ 1457, 0, R"()")
DIAG_ENTRY(MicrosoftInaccessibleBase /* microsoft-inaccessible-base */, 10995, /* DiagArray512 */ 1459, 0, R"()")
DIAG_ENTRY(MicrosoftInclude /* microsoft-include */, 11023, /* DiagArray513 */ 1461, 0, R"()")
DIAG_ENTRY(MicrosoftInitFromPredefined /* microsoft-init-from-predefined */, 11041, /* DiagArray514 */ 1463, 0, R"()")
DIAG_ENTRY(MicrosoftMutableReference /* microsoft-mutable-reference */, 11072, /* DiagArray515 */ 1465, 0, R"()")
DIAG_ENTRY(MicrosoftPureDefinition /* microsoft-pure-definition */, 11100, /* DiagArray516 */ 1467, 0, R"()")
DIAG_ENTRY(MicrosoftRedeclareStatic /* microsoft-redeclare-static */, 11126, /* DiagArray517 */ 1469, 0, R"()")
DIAG_ENTRY(MicrosoftSealed /* microsoft-sealed */, 11153, /* DiagArray518 */ 1471, 0, R"()")
DIAG_ENTRY(MicrosoftStaticAssert /* microsoft-static-assert */, 11170, /* DiagArray519 */ 1473, 0, R"()")
DIAG_ENTRY(MicrosoftStringLiteralFromPredefined /* microsoft-string-literal-from-predefined */, 11194, /* DiagArray520 */ 1475, 0, R"()")
DIAG_ENTRY(MicrosoftTemplate /* microsoft-template */, 11235, /* DiagArray521 */ 1477, /* DiagSubGroup521 */ 411, R"()")
DIAG_ENTRY(MicrosoftTemplateShadow /* microsoft-template-shadow */, 11254, /* DiagArray522 */ 1488, 0, R"()")
DIAG_ENTRY(MicrosoftUnionMemberReference /* microsoft-union-member-reference */, 11280, /* DiagArray523 */ 1490, 0, R"()")
DIAG_ENTRY(MicrosoftUnqualifiedFriend /* microsoft-unqualified-friend */, 11313, /* DiagArray524 */ 1492, 0, R"()")
DIAG_ENTRY(MicrosoftUsingDecl /* microsoft-using-decl */, 11342, /* DiagArray525 */ 1494, 0, R"()")
DIAG_ENTRY(MicrosoftVoidPseudoDtor /* microsoft-void-pseudo-dtor */, 11363, /* DiagArray526 */ 1496, 0, R"()")
DIAG_ENTRY(MisExpect /* misexpect */, 11390, /* DiagArray527 */ 1498, 0, R"()")
DIAG_ENTRY(MisleadingIndentation /* misleading-indentation */, 11400, /* DiagArray528 */ 1500, 0, R"()")
DIAG_ENTRY(anonymous_250 /* mismatched-new-delete */, 11423, /* DiagArray529 */ 1502, 0, R"()")
DIAG_ENTRY(MismatchedParameterTypes /* mismatched-parameter-types */, 11445, /* DiagArray530 */ 1504, 0, R"()")
DIAG_ENTRY(MismatchedReturnTypes /* mismatched-return-types */, 11472, /* DiagArray531 */ 1506, 0, R"()")
DIAG_ENTRY(MismatchedTags /* mismatched-tags */, 11496, /* DiagArray532 */ 1508, 0, R"()")
DIAG_ENTRY(MissingBraces /* missing-braces */, 11512, /* DiagArray533 */ 1511, 0, R"()")
DIAG_ENTRY(anonymous_265 /* missing-constinit */, 11527, /* DiagArray534 */ 1513, 0, R"()")
DIAG_ENTRY(MissingDeclarations /* missing-declarations */, 11545, /* DiagArray535 */ 1515, 0, R"()")
DIAG_ENTRY(anonymous_182 /* missing-exception-spec */, 11566, /* DiagArray536 */ 1520, 0, R"()")
DIAG_ENTRY(MissingFieldInitializers /* missing-field-initializers */, 11589, /* DiagArray537 */ 1522, 0, R"()")
DIAG_ENTRY(anonymous_34 /* missing-format-attribute */, 11616, 0, 0, R"()")
DIAG_ENTRY(anonymous_35 /* missing-include-dirs */, 11641, 0, 0, R"()")
DIAG_ENTRY(MissingMethodReturnType /* missing-method-return-type */, 11662, /* DiagArray540 */ 1524, 0, R"()")
DIAG_ENTRY(anonymous_99 /* missing-multilib */, 11689, /* DiagArray541 */ 1526, 0, R"()")
DIAG_ENTRY(MissingNoEscape /* missing-noescape */, 11706, /* DiagArray542 */ 1528, 0, R"()")
DIAG_ENTRY(MissingNoreturn /* missing-noreturn */, 11723, /* DiagArray543 */ 1530, 0, R"()")
DIAG_ENTRY(anonymous_201 /* missing-prototype-for-cc */, 11740, /* DiagArray544 */ 1533, 0, R"()")
DIAG_ENTRY(anonymous_218 /* missing-prototypes */, 11765, /* DiagArray545 */ 1535, 0, R"()")
DIAG_ENTRY(anonymous_141 /* missing-selector-name */, 11784, /* DiagArray546 */ 1537, 0, R"()")
DIAG_ENTRY(anonymous_91 /* missing-sysroot */, 11806, /* DiagArray547 */ 1539, 0, R"()")
DIAG_ENTRY(anonymous_219 /* missing-variable-declarations */, 11822, /* DiagArray548 */ 1541, 0, R"()")
DIAG_ENTRY(MisspelledAssumption /* misspelled-assumption */, 11852, /* DiagArray549 */ 1543, 0, R"()")
DIAG_ENTRY(ModuleBuild /* module-build */, 11874, /* DiagArray550 */ 1545, 0, R"()")
DIAG_ENTRY(ModuleConflict /* module-conflict */, 11887, /* DiagArray551 */ 1550, 0, R"()")
DIAG_ENTRY(anonymous_103 /* module-file-config-mismatch */, 11903, /* DiagArray552 */ 1553, 0, R"()")
DIAG_ENTRY(ModuleFileExtension /* module-file-extension */, 11931, /* DiagArray553 */ 1555, 0, R"()")
DIAG_ENTRY(ModuleImport /* module-import */, 11953, /* DiagArray554 */ 1557, 0, R"()")
DIAG_ENTRY(anonymous_282 /* module-import-in-extern-c */, 11967, /* DiagArray555 */ 1559, 0, R"()")
DIAG_ENTRY(ModuleIncludeDirectiveTranslation /* module-include-translation */, 11993, /* DiagArray556 */ 1561, 0, R"()")
DIAG_ENTRY(ModuleLock /* module-lock */, 12020, /* DiagArray557 */ 1563, 0, R"()")
DIAG_ENTRY(anonymous_285 /* modules-ambiguous-internal-linkage */, 12032, /* DiagArray558 */ 1565, 0, R"()")
DIAG_ENTRY(anonymous_283 /* modules-import-nested-redundant */, 12067, /* DiagArray559 */ 1567, 0, R"()")
DIAG_ENTRY(Most /* most */, 12099, 0, /* DiagSubGroup560 */ 413, R"()")
DIAG_ENTRY(Move /* move */, 12104, 0, /* DiagSubGroup561 */ 451, R"()")
DIAG_ENTRY(anonymous_77 /* msvc-include */, 12109, 0, /* DiagSubGroup562 */ 456, R"()")
DIAG_ENTRY(anonymous_95 /* msvc-not-found */, 12122, /* DiagArray563 */ 1569, 0, R"()")
DIAG_ENTRY(MultiGPU /* multi-gpu */, 12137, /* DiagArray564 */ 1571, 0, R"()")
DIAG_ENTRY(MultiChar /* multichar */, 12147, /* DiagArray565 */ 1573, 0, R"()")
DIAG_ENTRY(anonymous_270 /* multiple-move-vbase */, 12157, /* DiagArray566 */ 1575, 0, R"()")
DIAG_ENTRY(anonymous_24 /* narrowing */, 12177, 0, /* DiagSubGroup567 */ 458, R"()")
DIAG_ENTRY(anonymous_267 /* nested-anon-types */, 12187, /* DiagArray568 */ 1577, 0, R"()")
DIAG_ENTRY(anonymous_36 /* nested-externs */, 12205, 0, 0, R"()")
DIAG_ENTRY(OperatorNewReturnsNull /* new-returns-null */, 12220, /* DiagArray570 */ 1579, 0, R"()")
DIAG_ENTRY(NewlineEOF /* newline-eof */, 12237, /* DiagArray571 */ 1581, 0, R"()")
DIAG_ENTRY(NoDeref /* noderef */, 12249, /* DiagArray572 */ 1584, 0, R"()")
DIAG_ENTRY(NoexceptType /* noexcept-type */, 12257, 0, /* DiagSubGroup573 */ 460, R"()")
DIAG_ENTRY(anonymous_163 /* non-c-typedef-for-linkage */, 12271, /* DiagArray574 */ 1588, 0, R"()")
DIAG_ENTRY(NonGCC /* non-gcc */, 12297, 0, /* DiagSubGroup575 */ 462, R"()")
DIAG_ENTRY(NonLiteralNullConversion /* non-literal-null-conversion */, 12305, /* DiagArray576 */ 1590, 0, R"()")
DIAG_ENTRY(NonModularIncludeInFrameworkModule /* non-modular-include-in-framework-module */, 12333, /* DiagArray577 */ 1592, 0, R"()")
DIAG_ENTRY(NonModularIncludeInModule /* non-modular-include-in-module */, 12373, /* DiagArray578 */ 1594, /* DiagSubGroup578 */ 466, R"()")
DIAG_ENTRY(NonPODVarargs /* non-pod-varargs */, 12403, /* DiagArray579 */ 1596, 0, R"()")
DIAG_ENTRY(anonymous_192 /* non-power-of-two-alignment */, 12419, /* DiagArray580 */ 1601, 0, R"()")
DIAG_ENTRY(NonVirtualDtor /* non-virtual-dtor */, 12446, /* DiagArray581 */ 1603, 0, R"()")
DIAG_ENTRY(NonNull /* nonnull */, 12463, /* DiagArray582 */ 1605, 0, R"()")
DIAG_ENTRY(anonymous_38 /* nonportable-cfstrings */, 12471, 0, 0, R"()")
DIAG_ENTRY(anonymous_118 /* nonportable-include-path */, 12493, /* DiagArray584 */ 1608, 0, R"()")
DIAG_ENTRY(anonymous_119 /* nonportable-system-include-path */, 12518, /* DiagArray585 */ 1610, 0, R"()")
DIAG_ENTRY(anonymous_277 /* nonportable-vector-initialization */, 12550, /* DiagArray586 */ 1612, 0, R"()")
DIAG_ENTRY(NonTrivialMemaccess /* nontrivial-memaccess */, 12584, /* DiagArray587 */ 1614, 0, R"()")
DIAG_ENTRY(NSConsumedMismatch /* nsconsumed-mismatch */, 12605, /* DiagArray588 */ 1616, 0, R"()")
DIAG_ENTRY(NSReturnsMismatch /* nsreturns-mismatch */, 12625, /* DiagArray589 */ 1618, 0, R"()")
DIAG_ENTRY(NullArithmetic /* null-arithmetic */, 12644, /* DiagArray590 */ 1620, 0, R"()")
DIAG_ENTRY(NullCharacter /* null-character */, 12660, /* DiagArray591 */ 1623, 0, R"()")
DIAG_ENTRY(NullConversion /* null-conversion */, 12675, /* DiagArray592 */ 1626, 0, R"()")
DIAG_ENTRY(NullDereference /* null-dereference */, 12691, /* DiagArray593 */ 1628, 0, R"()")
DIAG_ENTRY(NullPointerArithmetic /* null-pointer-arithmetic */, 12708, /* DiagArray594 */ 1631, /* DiagSubGroup594 */ 468, R"()")
DIAG_ENTRY(NullPointerSubtraction /* null-pointer-subtraction */, 12732, /* DiagArray595 */ 1633, 0, R"()")
DIAG_ENTRY(Nullability /* nullability */, 12757, /* DiagArray596 */ 1635, 0, R"()")
DIAG_ENTRY(NullabilityCompleteness /* nullability-completeness */, 12769, /* DiagArray597 */ 1641, /* DiagSubGroup597 */ 470, R"()")
DIAG_ENTRY(NullabilityCompletenessOnArrays /* nullability-completeness-on-arrays */, 12794, /* DiagArray598 */ 1643, 0, R"()")
DIAG_ENTRY(NullabilityDeclSpec /* nullability-declspec */, 12829, /* DiagArray599 */ 1645, 0, R"()")
DIAG_ENTRY(anonymous_135 /* nullability-extension */, 12850, /* DiagArray600 */ 1647, 0, R"()")
DIAG_ENTRY(NullabilityInferredOnNestedType /* nullability-inferred-on-nested-type */, 12872, /* DiagArray601 */ 1649, 0, R"()")
DIAG_ENTRY(NullableToNonNullConversion /* nullable-to-nonnull-conversion */, 12908, /* DiagArray602 */ 1651, 0, R"()")
DIAG_ENTRY(anonymous_172 /* objc-autosynthesis-property-ivar-name-match */, 12939, /* DiagArray603 */ 1653, 0, R"()")
DIAG_ENTRY(ObjCBoolConstantConversion /* objc-bool-constant-conversion */, 12983, /* DiagArray604 */ 1655, 0, R"()")
DIAG_ENTRY(ObjCBoxing /* objc-boxing */, 13013, /* DiagArray605 */ 1657, 0, R"()")
DIAG_ENTRY(anonymous_247 /* objc-circular-container */, 13025, /* DiagArray606 */ 1659, 0, R"()")
DIAG_ENTRY(ObjCCocoaAPI /* objc-cocoa-api */, 13049, 0, /* DiagSubGroup607 */ 472, R"()")
DIAG_ENTRY(ObjCDesignatedInit /* objc-designated-initializers */, 13064, /* DiagArray608 */ 1661, 0, R"()")
DIAG_ENTRY(anonymous_189 /* objc-dictionary-duplicate-keys */, 13093, /* DiagArray609 */ 1668, 0, R"()")
DIAG_ENTRY(anonymous_87 /* objc-duplicate-category-definition */, 13124, /* DiagArray610 */ 1670, 0, R"()")
DIAG_ENTRY(ObjCFlexibleArray /* objc-flexible-array */, 13159, /* DiagArray611 */ 1672, 0, R"()")
DIAG_ENTRY(anonymous_225 /* objc-forward-class-redefinition */, 13179, /* DiagArray612 */ 1675, 0, R"()")
DIAG_ENTRY(anonymous_226 /* objc-interface-ivars */, 13211, /* DiagArray613 */ 1677, 0, R"()")
DIAG_ENTRY(ObjCLiteralComparison /* objc-literal-compare */, 13232, /* DiagArray614 */ 1679, /* DiagSubGroup614 */ 474, R"()")
DIAG_ENTRY(ObjCLiteralConversion /* objc-literal-conversion */, 13253, /* DiagArray615 */ 1681, 0, R"()")
DIAG_ENTRY(anonymous_126 /* objc-macro-redefinition */, 13277, /* DiagArray616 */ 1684, 0, R"()")
DIAG_ENTRY(anonymous_177 /* objc-messaging-id */, 13301, /* DiagArray617 */ 1686, 0, R"()")
DIAG_ENTRY(MethodAccess /* objc-method-access */, 13319, /* DiagArray618 */ 1688, 0, R"()")
DIAG_ENTRY(anonymous_173 /* objc-missing-property-synthesis */, 13338, /* DiagArray619 */ 1695, 0, R"()")
DIAG_ENTRY(ObjCMissingSuperCalls /* objc-missing-super-calls */, 13370, /* DiagArray620 */ 1697, 0, R"()")
DIAG_ENTRY(ObjCMultipleMethodNames /* objc-multiple-method-names */, 13395, /* DiagArray621 */ 1699, 0, R"()")
DIAG_ENTRY(ObjCRetainBlockProperty /* objc-noncopy-retain-block-property */, 13422, /* DiagArray622 */ 1701, 0, R"()")
DIAG_ENTRY(ObjCNonUnifiedException /* objc-nonunified-exceptions */, 13457, /* DiagArray623 */ 1703, 0, R"()")
DIAG_ENTRY(ObjCPropertyAssignOnObjectType /* objc-property-assign-on-object-type */, 13484, /* DiagArray624 */ 1705, 0, R"()")
DIAG_ENTRY(ObjCPropertyImpl /* objc-property-implementation */, 13520, /* DiagArray625 */ 1707, 0, R"()")
DIAG_ENTRY(anonymous_168 /* objc-property-implicit-mismatch */, 13549, /* DiagArray626 */ 1712, 0, R"()")
DIAG_ENTRY(anonymous_170 /* objc-property-matches-cocoa-ownership-rule */, 13581, /* DiagArray627 */ 1714, 0, R"()")
DIAG_ENTRY(ObjCPropertyNoAttribute /* objc-property-no-attribute */, 13624, /* DiagArray628 */ 1716, 0, R"()")
DIAG_ENTRY(ObjCNoPropertyAutoSynthesis /* objc-property-synthesis */, 13651, /* DiagArray629 */ 1719, 0, R"()")
DIAG_ENTRY(ObjCProtocolMethodImpl /* objc-protocol-method-implementation */, 13675, /* DiagArray630 */ 1723, 0, R"()")
DIAG_ENTRY(anonymous_171 /* objc-protocol-property-synthesis */, 13711, /* DiagArray631 */ 1725, 0, R"()")
DIAG_ENTRY(ObjCProtocolQualifiers /* objc-protocol-qualifiers */, 13744, /* DiagArray632 */ 1727, 0, R"()")
DIAG_ENTRY(ObjCReadonlyPropertyHasSetter /* objc-readonly-with-setter-property */, 13769, /* DiagArray633 */ 1729, 0, R"()")
DIAG_ENTRY(ObjCRedundantAPIUse /* objc-redundant-api-use */, 13804, 0, /* DiagSubGroup634 */ 476, R"()")
DIAG_ENTRY(ObjCRedundantLiteralUse /* objc-redundant-literal-use */, 13827, /* DiagArray635 */ 1731, 0, R"()")
DIAG_ENTRY(ObjCRootClass /* objc-root-class */, 13854, /* DiagArray636 */ 1733, 0, R"()")
DIAG_ENTRY(ObjCSignedCharBool /* objc-signed-char-bool */, 13870, 0, /* DiagSubGroup637 */ 478, R"()")
DIAG_ENTRY(ObjCSignedCharBoolImplicitFloatConversion /* objc-signed-char-bool-implicit-float-conversion */, 13892, /* DiagArray638 */ 1735, 0, R"()")
DIAG_ENTRY(ObjCSignedCharBoolImplicitIntConversion /* objc-signed-char-bool-implicit-int-conversion */, 13940, /* DiagArray639 */ 1737, 0, R"()")
DIAG_ENTRY(ObjCStringComparison /* objc-string-compare */, 13986, /* DiagArray640 */ 1739, 0, R"()")
DIAG_ENTRY(ObjCStringConcatenation /* objc-string-concatenation */, 14006, /* DiagArray641 */ 1741, 0, R"()")
DIAG_ENTRY(anonymous_248 /* objc-unsafe-perform-selector */, 14032, /* DiagArray642 */ 1743, 0, R"()")
DIAG_ENTRY(ODR /* odr */, 14061, /* DiagArray643 */ 1745, 0, R"()")
DIAG_ENTRY(OldStyleCast /* old-style-cast */, 14065, /* DiagArray644 */ 1764, 0, R"()")
DIAG_ENTRY(anonymous_40 /* old-style-definition */, 14080, 0, 0, R"()")
DIAG_ENTRY(OpenCLUnsupportedRGBA /* opencl-unsupported-rgba */, 14101, /* DiagArray646 */ 1766, 0, R"()")
DIAG_ENTRY(OpenMP /* openmp */, 14125, 0, /* DiagSubGroup647 */ 483, R"()")
DIAG_ENTRY(OpenMP51Ext /* openmp-51-extensions */, 14132, /* DiagArray648 */ 1768, 0, R"()")
DIAG_ENTRY(OpenMPClauses /* openmp-clauses */, 14153, /* DiagArray649 */ 1770, 0, R"()")
DIAG_ENTRY(OpenMPExtensions /* openmp-extensions */, 14168, /* DiagArray650 */ 1790, 0, R"()")
DIAG_ENTRY(OpenMPLoopForm /* openmp-loop-form */, 14186, /* DiagArray651 */ 1792, 0, R"()")
DIAG_ENTRY(OpenMPMapping /* openmp-mapping */, 14203, /* DiagArray652 */ 1795, 0, R"()")
DIAG_ENTRY(OpenMPTarget /* openmp-target */, 14218, /* DiagArray653 */ 1797, /* DiagSubGroup653 */ 491, R"()")
DIAG_ENTRY(OptionIgnored /* option-ignored */, 14232, /* DiagArray654 */ 1801, 0, R"()")
DIAG_ENTRY(OrderedCompareFunctionPointers /* ordered-compare-function-pointers */, 14247, /* DiagArray655 */ 1819, 0, R"()")
DIAG_ENTRY(OutOfLineDeclaration /* out-of-line-declaration */, 14281, /* DiagArray656 */ 1822, 0, R"()")
DIAG_ENTRY(anonymous_155 /* out-of-scope-function */, 14305, /* DiagArray657 */ 1824, 0, R"()")
DIAG_ENTRY(OveralignedType /* over-aligned */, 14327, /* DiagArray658 */ 1826, 0, R"()")
DIAG_ENTRY(anonymous_41 /* overflow */, 14340, 0, 0, R"()")
DIAG_ENTRY(OverlengthStrings /* overlength-strings */, 14349, /* DiagArray660 */ 1828, 0, R"()")
DIAG_ENTRY(OverloadedShiftOpParentheses /* overloaded-shift-op-parentheses */, 14368, /* DiagArray661 */ 1830, 0, R"()")
DIAG_ENTRY(OverloadedVirtual /* overloaded-virtual */, 14400, /* DiagArray662 */ 1832, 0, R"()")
DIAG_ENTRY(anonymous_37 /* override-init */, 14419, 0, /* DiagSubGroup663 */ 493, R"()")
DIAG_ENTRY(anonymous_100 /* override-module */, 14433, /* DiagArray664 */ 1834, 0, R"()")
DIAG_ENTRY(OverridingMethodMismatch /* overriding-method-mismatch */, 14449, /* DiagArray665 */ 1836, 0, R"()")
DIAG_ENTRY(anonymous_90 /* overriding-option */, 14476, /* DiagArray666 */ 1844, 0, R"()")
DIAG_ENTRY(Packed /* packed */, 14494, /* DiagArray667 */ 1846, /* DiagSubGroup667 */ 495, R"()")
DIAG_ENTRY(PackedNonPod /* packed-non-pod */, 14501, /* DiagArray668 */ 1848, 0, R"()")
DIAG_ENTRY(Padded /* padded */, 14516, /* DiagArray669 */ 1850, 0, R"()")
DIAG_ENTRY(Parentheses /* parentheses */, 14523, /* DiagArray670 */ 1854, /* DiagSubGroup670 */ 497, R"()")
DIAG_ENTRY(ParenthesesOnEquality /* parentheses-equality */, 14535, /* DiagArray671 */ 1858, 0, R"()")
DIAG_ENTRY(anonymous_8 /* partial-availability */, 14556, 0, /* DiagSubGroup672 */ 506, R"()")
DIAG_ENTRY(BackendOptimizationRemark /* pass */, 14577, /* DiagArray673 */ 1860, 0, R"()")
DIAG_ENTRY(BackendOptimizationRemarkAnalysis /* pass-analysis */, 14582, /* DiagArray674 */ 1862, 0, R"()")
DIAG_ENTRY(BackendOptimizationFailure /* pass-failed */, 14596, /* DiagArray675 */ 1866, 0, R"()")
DIAG_ENTRY(BackendOptimizationRemarkMissed /* pass-missed */, 14608, /* DiagArray676 */ 1868, 0, R"()")
DIAG_ENTRY(anonymous_290 /* pch-date-time */, 14620, /* DiagArray677 */ 1870, 0, R"()")
DIAG_ENTRY(Pedantic /* pedantic */, 14634, /* DiagArray678 */ 1872, /* DiagSubGroup678 */ 508, R"()")
DIAG_ENTRY(OpenCLCoreFeaturesDiagGroup /* pedantic-core-features */, 14643, /* DiagArray679 */ 1948, 0, R"()")
DIAG_ENTRY(PedanticMacros /* pedantic-macros */, 14666, 0, /* DiagSubGroup680 */ 570, R"()")
DIAG_ENTRY(PessimizingMove /* pessimizing-move */, 14682, /* DiagArray681 */ 1951, 0, R"()")
DIAG_ENTRY(PointerArith /* pointer-arith */, 14699, /* DiagArray682 */ 1954, /* DiagSubGroup682 */ 576, R"()")
DIAG_ENTRY(PointerBoolConversion /* pointer-bool-conversion */, 14713, /* DiagArray683 */ 1958, 0, R"()")
DIAG_ENTRY(anonymous_204 /* pointer-compare */, 14737, /* DiagArray684 */ 1961, 0, R"()")
DIAG_ENTRY(anonymous_246 /* pointer-integer-compare */, 14753, /* DiagArray685 */ 1963, 0, R"()")
DIAG_ENTRY(anonymous_256 /* pointer-sign */, 14777, /* DiagArray686 */ 1965, 0, R"()")
DIAG_ENTRY(PointerToEnumCast /* pointer-to-enum-cast */, 14790, /* DiagArray687 */ 1967, /* DiagSubGroup687 */ 578, R"()")
DIAG_ENTRY(PointerToIntCast /* pointer-to-int-cast */, 14811, /* DiagArray688 */ 1969, /* DiagSubGroup688 */ 580, R"()")
DIAG_ENTRY(anonymous_262 /* pointer-type-mismatch */, 14831, /* DiagArray689 */ 1971, 0, R"()")
DIAG_ENTRY(anonymous_89 /* poison-system-directories */, 14853, /* DiagArray690 */ 1973, 0, R"()")
DIAG_ENTRY(ObjCPotentiallyDirectSelector /* potentially-direct-selector */, 14879, /* DiagArray691 */ 1975, 0, R"()")
DIAG_ENTRY(PotentiallyEvaluatedExpression /* potentially-evaluated-expression */, 14907, /* DiagArray692 */ 1977, 0, R"()")
DIAG_ENTRY(PragmaClangAttribute /* pragma-clang-attribute */, 14940, /* DiagArray693 */ 1979, 0, R"()")
DIAG_ENTRY(anonymous_120 /* pragma-once-outside-header */, 14963, /* DiagArray694 */ 1981, 0, R"()")
DIAG_ENTRY(PragmaPack /* pragma-pack */, 14990, /* DiagArray695 */ 1983, /* DiagSubGroup695 */ 583, R"()")
DIAG_ENTRY(PragmaPackSuspiciousInclude /* pragma-pack-suspicious-include */, 15002, /* DiagArray696 */ 1986, 0, R"()")
DIAG_ENTRY(anonymous_121 /* pragma-system-header-outside-header */, 15033, /* DiagArray697 */ 1988, 0, R"()")
DIAG_ENTRY(Pragmas /* pragmas */, 15069, /* DiagArray698 */ 1990, /* DiagSubGroup698 */ 585, R"()")
DIAG_ENTRY(CXXPre14Compat /* pre-c++14-compat */, 15077, /* DiagArray699 */ 1993, 0, R"()")
DIAG_ENTRY(CXXPre14CompatPedantic /* pre-c++14-compat-pedantic */, 15094, 0, /* DiagSubGroup700 */ 590, R"()")
DIAG_ENTRY(CXXPre17Compat /* pre-c++17-compat */, 15120, /* DiagArray701 */ 2005, 0, R"()")
DIAG_ENTRY(CXXPre17CompatPedantic /* pre-c++17-compat-pedantic */, 15137, /* DiagArray702 */ 2023, /* DiagSubGroup702 */ 593, R"()")
DIAG_ENTRY(CXXPre20Compat /* pre-c++20-compat */, 15163, /* DiagArray703 */ 2026, 0, R"()")
DIAG_ENTRY(CXXPre20CompatPedantic /* pre-c++20-compat-pedantic */, 15180, /* DiagArray704 */ 2054, /* DiagSubGroup704 */ 595, R"()")
DIAG_ENTRY(CXXPre23Compat /* pre-c++23-compat */, 15206, /* DiagArray705 */ 2057, 0, R"()")
DIAG_ENTRY(CXXPre23CompatPedantic /* pre-c++23-compat-pedantic */, 15223, 0, /* DiagSubGroup706 */ 597, R"()")
DIAG_ENTRY(CXXPre26Compat /* pre-c++26-compat */, 15249, /* DiagArray707 */ 2073, 0, R"()")
DIAG_ENTRY(CXXPre26CompatPedantic /* pre-c++26-compat-pedantic */, 15266, 0, /* DiagSubGroup708 */ 599, R"()")
DIAG_ENTRY(anonymous_21 /* pre-c++2c-compat */, 15292, 0, /* DiagSubGroup709 */ 601, R"()")
DIAG_ENTRY(anonymous_22 /* pre-c++2c-compat-pedantic */, 15309, 0, /* DiagSubGroup710 */ 603, R"()")
DIAG_ENTRY(CPre23Compat /* pre-c23-compat */, 15335, /* DiagArray711 */ 2075, 0, R"()")
DIAG_ENTRY(CPre23CompatPedantic /* pre-c23-compat-pedantic */, 15350, 0, /* DiagSubGroup712 */ 605, R"()")
DIAG_ENTRY(anonymous_13 /* pre-c2x-compat */, 15374, 0, /* DiagSubGroup713 */ 607, R"()")
DIAG_ENTRY(anonymous_14 /* pre-c2x-compat-pedantic */, 15389, 0, /* DiagSubGroup714 */ 609, R"()")
DIAG_ENTRY(OpenMPPre51Compat /* pre-openmp-51-compat */, 15413, /* DiagArray715 */ 2088, 0, R"()")
DIAG_ENTRY(anonymous_152 /* predefined-identifier-outside-function */, 15434, /* DiagArray716 */ 2090, 0, R"()")
DIAG_ENTRY(PrivateExtern /* private-extern */, 15473, /* DiagArray717 */ 2092, 0, R"()")
DIAG_ENTRY(anonymous_132 /* private-header */, 15488, /* DiagArray718 */ 2094, 0, R"()")
DIAG_ENTRY(PrivateModule /* private-module */, 15503, /* DiagArray719 */ 2096, 0, R"()")
DIAG_ENTRY(ProfileInstrMissing /* profile-instr-missing */, 15518, /* DiagArray720 */ 2101, 0, R"()")
DIAG_ENTRY(ProfileInstrOutOfDate /* profile-instr-out-of-date */, 15540, /* DiagArray721 */ 2103, 0, R"()")
DIAG_ENTRY(ProfileInstrUnprofiled /* profile-instr-unprofiled */, 15566, /* DiagArray722 */ 2105, 0, R"()")
DIAG_ENTRY(PropertyAccessDotSyntax /* property-access-dot-syntax */, 15591, /* DiagArray723 */ 2107, 0, R"()")
DIAG_ENTRY(PropertyAttr /* property-attribute-mismatch */, 15618, /* DiagArray724 */ 2109, 0, R"()")
DIAG_ENTRY(Protocol /* protocol */, 15646, /* DiagArray725 */ 2114, 0, R"()")
DIAG_ENTRY(anonymous_166 /* protocol-property-synthesis-ambiguity */, 15655, /* DiagArray726 */ 2116, 0, R"()")
DIAG_ENTRY(anonymous_104 /* psabi */, 15693, /* DiagArray727 */ 2118, 0, R"()")
DIAG_ENTRY(anonymous_231 /* qualified-void-return-type */, 15699, /* DiagArray728 */ 2120, 0, R"()")
DIAG_ENTRY(FrameworkHdrQuotedInclude /* quoted-include-in-framework-header */, 15726, /* DiagArray729 */ 2122, 0, R"()")
DIAG_ENTRY(RangeLoopAnalysis /* range-loop-analysis */, 15761, 0, /* DiagSubGroup730 */ 611, R"()")
DIAG_ENTRY(RangeLoopBindReference /* range-loop-bind-reference */, 15781, /* DiagArray731 */ 2124, 0, R"()")
DIAG_ENTRY(RangeLoopConstruct /* range-loop-construct */, 15807, /* DiagArray732 */ 2126, 0, R"()")
DIAG_ENTRY(anonymous_289 /* read-modules-implicitly */, 15828, /* DiagArray733 */ 2129, 0, R"()")
DIAG_ENTRY(ReadOnlyPlacementChecks /* read-only-types */, 15852, /* DiagArray734 */ 2131, 0, R"()")
DIAG_ENTRY(anonymous_176 /* readonly-iboutlet-property */, 15868, /* DiagArray735 */ 2133, 0, R"()")
DIAG_ENTRY(ObjCReceiver /* receiver-expr */, 15895, /* DiagArray736 */ 2135, 0, R"()")
DIAG_ENTRY(ForwardClassReceiver /* receiver-forward-class */, 15909, /* DiagArray737 */ 2137, 0, R"()")
DIAG_ENTRY(RedeclaredClassMember /* redeclared-class-member */, 15932, /* DiagArray738 */ 2140, 0, R"()")
DIAG_ENTRY(anonymous_179 /* redundant-consteval-if */, 15956, /* DiagArray739 */ 2142, 0, R"()")
DIAG_ENTRY(anonymous_42 /* redundant-decls */, 15979, 0, 0, R"()")
DIAG_ENTRY(RedundantMove /* redundant-move */, 15995, /* DiagArray741 */ 2144, 0, R"()")
DIAG_ENTRY(anonymous_154 /* redundant-parens */, 16010, /* DiagArray742 */ 2146, 0, R"()")
DIAG_ENTRY(Register /* register */, 16027, /* DiagArray743 */ 2148, /* DiagSubGroup743 */ 614, R"()")
DIAG_ENTRY(ReinterpretBaseClass /* reinterpret-base-class */, 16036, /* DiagArray744 */ 2150, 0, R"()")
DIAG_ENTRY(RemarkBackendPlugin /* remark-backend-plugin */, 16059, /* DiagArray745 */ 2152, 0, R"()")
DIAG_ENTRY(Reorder /* reorder */, 16081, 0, /* DiagSubGroup746 */ 616, R"()")
DIAG_ENTRY(ReorderCtor /* reorder-ctor */, 16089, /* DiagArray747 */ 2154, 0, R"()")
DIAG_ENTRY(ReorderInitList /* reorder-init-list */, 16102, /* DiagArray748 */ 2157, 0, R"()")
DIAG_ENTRY(anonymous_210 /* requires-super-attribute */, 16120, /* DiagArray749 */ 2159, 0, R"()")
DIAG_ENTRY(ReservedIdAsMacroAlias /* reserved-id-macro */, 16145, 0, /* DiagSubGroup750 */ 619, R"()")
DIAG_ENTRY(ReservedIdentifier /* reserved-identifier */, 16163, /* DiagArray751 */ 2161, /* DiagSubGroup751 */ 621, R"()")
DIAG_ENTRY(ReservedIdAsMacro /* reserved-macro-identifier */, 16183, /* DiagArray752 */ 2163, 0, R"()")
DIAG_ENTRY(ReservedModuleIdentifier /* reserved-module-identifier */, 16209, /* DiagArray753 */ 2165, 0, R"()")
DIAG_ENTRY(ReservedUserDefinedLiteral /* reserved-user-defined-literal */, 16236, /* DiagArray754 */ 2167, /* DiagSubGroup754 */ 625, R"()")
DIAG_ENTRY(RestrictExpansionMacro /* restrict-expansion */, 16266, /* DiagArray755 */ 2170, 0, R"()")
DIAG_ENTRY(anonymous_224 /* retained-language-linkage */, 16285, /* DiagArray756 */ 2172, 0, R"()")
DIAG_ENTRY(anonymous_29 /* return-local-addr */, 16311, 0, /* DiagSubGroup757 */ 627, R"()")
DIAG_ENTRY(ReturnStackAddress /* return-stack-address */, 16329, /* DiagArray758 */ 2174, 0, R"()")
DIAG_ENTRY(ReturnStdMove /* return-std-move */, 16350, 0, 0, R"()")
DIAG_ENTRY(ReturnType /* return-type */, 16366, /* DiagArray760 */ 2178, /* DiagSubGroup760 */ 629, R"()")
DIAG_ENTRY(ReturnTypeCLinkage /* return-type-c-linkage */, 16378, /* DiagArray761 */ 2188, 0, R"()")
DIAG_ENTRY(anonymous_212 /* rewrite-not-bool */, 16400, /* DiagArray762 */ 2191, 0, R"()")
DIAG_ENTRY(RoundTripCC1Args /* round-trip-cc1-args */, 16417, /* DiagArray763 */ 2193, 0, R"()")
DIAG_ENTRY(RTTI /* rtti */, 16437, /* DiagArray764 */ 2195, 0, R"()")
DIAG_ENTRY(SanitizeAddressRemarks /* sanitize-address */, 16442, /* DiagArray765 */ 2198, 0, R"()")
DIAG_ENTRY(anonymous_98 /* sarif-format-unstable */, 16459, /* DiagArray766 */ 2201, 0, R"()")
DIAG_ENTRY(UsedSearchPath /* search-path-usage */, 16481, /* DiagArray767 */ 2203, 0, R"()")
DIAG_ENTRY(Section /* section */, 16499, /* DiagArray768 */ 2205, 0, R"()")
DIAG_ENTRY(Selector /* selector */, 16507, /* DiagArray769 */ 2209, /* DiagSubGroup769 */ 631, R"()")
DIAG_ENTRY(SelectorTypeMismatch /* selector-type-mismatch */, 16516, /* DiagArray770 */ 2211, 0, R"()")
DIAG_ENTRY(SelfAssignment /* self-assign */, 16539, /* DiagArray771 */ 2213, /* DiagSubGroup771 */ 633, R"()")
DIAG_ENTRY(SelfAssignmentField /* self-assign-field */, 16551, /* DiagArray772 */ 2215, 0, R"()")
DIAG_ENTRY(SelfAssignmentOverloaded /* self-assign-overloaded */, 16569, /* DiagArray773 */ 2217, 0, R"()")
DIAG_ENTRY(SelfMove /* self-move */, 16592, /* DiagArray774 */ 2219, 0, R"()")
DIAG_ENTRY(SemiBeforeMethodBody /* semicolon-before-method-body */, 16602, /* DiagArray775 */ 2221, 0, R"()")
DIAG_ENTRY(Sentinel /* sentinel */, 16631, /* DiagArray776 */ 2223, 0, R"()")
DIAG_ENTRY(anonymous_46 /* sequence-point */, 16640, 0, /* DiagSubGroup777 */ 636, R"()")
DIAG_ENTRY(SerializedDiagnostics /* serialized-diagnostics */, 16655, /* DiagArray778 */ 2226, 0, R"()")
DIAG_ENTRY(Shadow /* shadow */, 16678, /* DiagArray779 */ 2230, /* DiagSubGroup779 */ 638, R"()")
DIAG_ENTRY(ShadowAll /* shadow-all */, 16685, 0, /* DiagSubGroup780 */ 641, R"()")
DIAG_ENTRY(ShadowField /* shadow-field */, 16696, /* DiagArray781 */ 2232, 0, R"()")
DIAG_ENTRY(ShadowFieldInConstructor /* shadow-field-in-constructor */, 16709, /* DiagArray782 */ 2234, /* DiagSubGroup782 */ 646, R"()")
DIAG_ENTRY(ShadowFieldInConstructorModified /* shadow-field-in-constructor-modified */, 16737, /* DiagArray783 */ 2236, 0, R"()")
DIAG_ENTRY(ShadowIvar /* shadow-ivar */, 16774, /* DiagArray784 */ 2238, 0, R"()")
DIAG_ENTRY(ShadowUncapturedLocal /* shadow-uncaptured-local */, 16786, /* DiagArray785 */ 2240, 0, R"()")
DIAG_ENTRY(anonymous_236 /* shift-count-negative */, 16810, /* DiagArray786 */ 2242, 0, R"()")
DIAG_ENTRY(anonymous_237 /* shift-count-overflow */, 16831, /* DiagArray787 */ 2244, 0, R"()")
DIAG_ENTRY(anonymous_235 /* shift-negative-value */, 16852, /* DiagArray788 */ 2246, 0, R"()")
DIAG_ENTRY(ShiftOpParentheses /* shift-op-parentheses */, 16873, /* DiagArray789 */ 2248, 0, R"()")
DIAG_ENTRY(anonymous_238 /* shift-overflow */, 16894, /* DiagArray790 */ 2250, 0, R"()")
DIAG_ENTRY(anonymous_239 /* shift-sign-overflow */, 16909, /* DiagArray791 */ 2252, 0, R"()")
DIAG_ENTRY(Shorten64To32 /* shorten-64-to-32 */, 16929, /* DiagArray792 */ 2254, 0, R"()")
DIAG_ENTRY(SignCompare /* sign-compare */, 16946, /* DiagArray793 */ 2256, 0, R"()")
DIAG_ENTRY(SignConversion /* sign-conversion */, 16959, /* DiagArray794 */ 2258, 0, R"()")
DIAG_ENTRY(anonymous_43 /* sign-promo */, 16975, 0, 0, R"()")
DIAG_ENTRY(SignedEnumBitfield /* signed-enum-bitfield */, 16986, /* DiagArray796 */ 2262, 0, R"()")
DIAG_ENTRY(anonymous_279 /* signed-unsigned-wchar */, 17007, /* DiagArray797 */ 2264, 0, R"()")
DIAG_ENTRY(SingleBitBitFieldConstantConversion /* single-bit-bitfield-constant-conversion */, 17029, /* DiagArray798 */ 2266, 0, R"()")
DIAG_ENTRY(SizeofArrayArgument /* sizeof-array-argument */, 17069, /* DiagArray799 */ 2268, 0, R"()")
DIAG_ENTRY(SizeofArrayDecay /* sizeof-array-decay */, 17091, /* DiagArray800 */ 2270, 0, R"()")
DIAG_ENTRY(anonymous_206 /* sizeof-array-div */, 17110, /* DiagArray801 */ 2272, 0, R"()")
DIAG_ENTRY(anonymous_205 /* sizeof-pointer-div */, 17127, /* DiagArray802 */ 2274, 0, R"()")
DIAG_ENTRY(SizeofPointerMemaccess /* sizeof-pointer-memaccess */, 17146, /* DiagArray803 */ 2276, 0, R"()")
DIAG_ENTRY(anonymous_94 /* slash-u-filename */, 17171, /* DiagArray804 */ 2279, 0, R"()")
DIAG_ENTRY(anonymous_86 /* slh-asm-goto */, 17188, /* DiagArray805 */ 2281, 0, R"()")
DIAG_ENTRY(anonymous_88 /* sloc-usage */, 17201, /* DiagArray806 */ 2283, 0, R"()")
DIAG_ENTRY(UninitializedSometimes /* sometimes-uninitialized */, 17212, /* DiagArray807 */ 2285, 0, R"()")
DIAG_ENTRY(BackendSourceMgr /* source-mgr */, 17236, /* DiagArray808 */ 2287, 0, R"()")
DIAG_ENTRY(SourceUsesOpenMP /* source-uses-openmp */, 17247, /* DiagArray809 */ 2289, 0, R"()")
DIAG_ENTRY(SpirCompat /* spir-compat */, 17266, /* DiagArray810 */ 2298, 0, R"()")
DIAG_ENTRY(anonymous_79 /* spirv-compat */, 17278, 0, /* DiagSubGroup811 */ 648, R"()")
DIAG_ENTRY(anonymous_84 /* stack-exhausted */, 17291, /* DiagArray812 */ 2300, 0, R"()")
DIAG_ENTRY(anonymous_85 /* stack-protector */, 17307, /* DiagArray813 */ 2302, 0, R"()")
DIAG_ENTRY(StaticFloatInit /* static-float-init */, 17323, /* DiagArray814 */ 2304, /* DiagSubGroup814 */ 650, R"()")
DIAG_ENTRY(StaticInInline /* static-in-inline */, 17341, /* DiagArray815 */ 2306, 0, R"()")
DIAG_ENTRY(anonymous_148 /* static-inline-explicit-instantiation */, 17358, /* DiagArray816 */ 2309, 0, R"()")
DIAG_ENTRY(StaticLocalInInline /* static-local-in-inline */, 17395, /* DiagArray817 */ 2311, 0, R"()")
DIAG_ENTRY(UninitializedStaticSelfInit /* static-self-init */, 17418, /* DiagArray818 */ 2313, 0, R"()")
DIAG_ENTRY(anonymous_97 /* stdlibcxx-not-found */, 17435, /* DiagArray819 */ 2315, 0, R"()")
DIAG_ENTRY(anonymous_50 /* strict-aliasing */, 17455, 0, 0, R"()")
DIAG_ENTRY(anonymous_47 /* strict-aliasing=0 */, 17471, 0, 0, R"()")
DIAG_ENTRY(anonymous_48 /* strict-aliasing=1 */, 17489, 0, 0, R"()")
DIAG_ENTRY(anonymous_49 /* strict-aliasing=2 */, 17507, 0, 0, R"()")
DIAG_ENTRY(anonymous_57 /* strict-overflow */, 17525, 0, 0, R"()")
DIAG_ENTRY(anonymous_51 /* strict-overflow=0 */, 17541, 0, 0, R"()")
DIAG_ENTRY(anonymous_52 /* strict-overflow=1 */, 17559, 0, 0, R"()")
DIAG_ENTRY(anonymous_53 /* strict-overflow=2 */, 17577, 0, 0, R"()")
DIAG_ENTRY(anonymous_54 /* strict-overflow=3 */, 17595, 0, 0, R"()")
DIAG_ENTRY(anonymous_55 /* strict-overflow=4 */, 17613, 0, 0, R"()")
DIAG_ENTRY(anonymous_56 /* strict-overflow=5 */, 17631, 0, 0, R"()")
DIAG_ENTRY(ObjCStrictPotentiallyDirectSelector /* strict-potentially-direct-selector */, 17649, /* DiagArray831 */ 2317, /* DiagSubGroup831 */ 652, R"()")
DIAG_ENTRY(StrictPrototypes /* strict-prototypes */, 17684, /* DiagArray832 */ 2319, /* DiagSubGroup832 */ 654, R"()")
DIAG_ENTRY(StrictSelector /* strict-selector-match */, 17702, /* DiagArray833 */ 2321, 0, R"()")
DIAG_ENTRY(StringCompare /* string-compare */, 17724, /* DiagArray834 */ 2323, 0, R"()")
DIAG_ENTRY(StringConcatation /* string-concatenation */, 17739, /* DiagArray835 */ 2325, 0, R"()")
DIAG_ENTRY(StringConversion /* string-conversion */, 17760, /* DiagArray836 */ 2327, 0, R"()")
DIAG_ENTRY(StringPlusChar /* string-plus-char */, 17778, /* DiagArray837 */ 2329, 0, R"()")
DIAG_ENTRY(StringPlusInt /* string-plus-int */, 17795, /* DiagArray838 */ 2331, 0, R"()")
DIAG_ENTRY(anonymous_159 /* strlcpy-strlcat-size */, 17811, /* DiagArray839 */ 2333, 0, R"()")
DIAG_ENTRY(StrncatSize /* strncat-size */, 17832, /* DiagArray840 */ 2335, 0, R"()")
DIAG_ENTRY(CXX11WarnSuggestOverrideDestructor /* suggest-destructor-override */, 17845, /* DiagArray841 */ 2339, 0, R"()")
DIAG_ENTRY(CXX11WarnSuggestOverride /* suggest-override */, 17873, /* DiagArray842 */ 2341, 0, R"()")
DIAG_ENTRY(SuperSubClassMismatch /* super-class-method-mismatch */, 17890, /* DiagArray843 */ 2343, 0, R"()")
DIAG_ENTRY(SuspiciousBzero /* suspicious-bzero */, 17918, /* DiagArray844 */ 2345, 0, R"()")
DIAG_ENTRY(SuspiciousMemaccess /* suspicious-memaccess */, 17935, 0, /* DiagSubGroup845 */ 656, R"()")
DIAG_ENTRY(SwiftNameAttribute /* swift-name-attribute */, 17956, /* DiagArray846 */ 2347, 0, R"()")
DIAG_ENTRY(Switch /* switch */, 17977, /* DiagArray847 */ 2361, 0, R"()")
DIAG_ENTRY(SwitchBool /* switch-bool */, 17984, /* DiagArray848 */ 2365, 0, R"()")
DIAG_ENTRY(anonymous_44 /* switch-default */, 17996, 0, 0, R"()")
DIAG_ENTRY(SwitchEnum /* switch-enum */, 18011, /* DiagArray850 */ 2367, 0, R"()")
DIAG_ENTRY(SyncAlignment /* sync-alignment */, 18023, /* DiagArray851 */ 2369, 0, R"()")
DIAG_ENTRY(anonymous_278 /* sync-fetch-and-nand-semantics-changed */, 18038, /* DiagArray852 */ 2371, 0, R"()")
DIAG_ENTRY(anonymous_45 /* synth */, 18076, 0, 0, R"()")
DIAG_ENTRY(TargetClonesMixedSpecifiers /* target-clones-mixed-specifiers */, 18082, /* DiagArray854 */ 2373, 0, R"()")
DIAG_ENTRY(TautologicalBitwiseCompare /* tautological-bitwise-compare */, 18113, /* DiagArray855 */ 2375, 0, R"()")
DIAG_ENTRY(TautologicalCompare /* tautological-compare */, 18142, /* DiagArray856 */ 2378, /* DiagSubGroup856 */ 662, R"()")
DIAG_ENTRY(TautologicalConstantCompare /* tautological-constant-compare */, 18163, /* DiagArray857 */ 2381, /* DiagSubGroup857 */ 670, R"()")
DIAG_ENTRY(TautologicalInRangeCompare /* tautological-constant-in-range-compare */, 18193, 0, /* DiagSubGroup858 */ 672, R"()")
DIAG_ENTRY(TautologicalOutOfRangeCompare /* tautological-constant-out-of-range-compare */, 18232, /* DiagArray859 */ 2385, 0, R"()")
DIAG_ENTRY(TautologicalNegationCompare /* tautological-negation-compare */, 18275, /* DiagArray860 */ 2387, 0, R"()")
DIAG_ENTRY(TautologicalObjCBoolCompare /* tautological-objc-bool-compare */, 18305, /* DiagArray861 */ 2390, 0, R"()")
DIAG_ENTRY(TautologicalOverlapCompare /* tautological-overlap-compare */, 18336, /* DiagArray862 */ 2392, 0, R"()")
DIAG_ENTRY(TautologicalPointerCompare /* tautological-pointer-compare */, 18365, /* DiagArray863 */ 2394, 0, R"()")
DIAG_ENTRY(TautologicalTypeLimitCompare /* tautological-type-limit-compare */, 18394, /* DiagArray864 */ 2397, 0, R"()")
DIAG_ENTRY(TautologicalUndefinedCompare /* tautological-undefined-compare */, 18426, /* DiagArray865 */ 2399, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedCharZeroCompare /* tautological-unsigned-char-zero-compare */, 18457, /* DiagArray866 */ 2402, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedEnumZeroCompare /* tautological-unsigned-enum-zero-compare */, 18497, /* DiagArray867 */ 2404, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedZeroCompare /* tautological-unsigned-zero-compare */, 18537, /* DiagArray868 */ 2406, 0, R"()")
DIAG_ENTRY(TautologicalValueRangeCompare /* tautological-value-range-compare */, 18572, /* DiagArray869 */ 2408, 0, R"()")
DIAG_ENTRY(anonymous_287 /* tcb-enforcement */, 18605, /* DiagArray870 */ 2410, 0, R"()")
DIAG_ENTRY(anonymous_242 /* tentative-definition-incomplete-type */, 18621, /* DiagArray871 */ 2412, 0, R"()")
DIAG_ENTRY(ThreadSafety /* thread-safety */, 18658, 0, /* DiagSubGroup872 */ 675, R"()")
DIAG_ENTRY(ThreadSafetyAnalysis /* thread-safety-analysis */, 18672, /* DiagArray873 */ 2414, 0, R"()")
DIAG_ENTRY(ThreadSafetyAttributes /* thread-safety-attributes */, 18695, /* DiagArray874 */ 2433, 0, R"()")
DIAG_ENTRY(ThreadSafetyBeta /* thread-safety-beta */, 18720, /* DiagArray875 */ 2440, 0, R"()")
DIAG_ENTRY(ThreadSafetyNegative /* thread-safety-negative */, 18739, /* DiagArray876 */ 2442, 0, R"()")
DIAG_ENTRY(ThreadSafetyPrecise /* thread-safety-precise */, 18762, /* DiagArray877 */ 2444, 0, R"()")
DIAG_ENTRY(ThreadSafetyReference /* thread-safety-reference */, 18784, /* DiagArray878 */ 2448, 0, R"()")
DIAG_ENTRY(ThreadSafetyVerbose /* thread-safety-verbose */, 18808, /* DiagArray879 */ 2451, 0, R"()")
DIAG_ENTRY(Trigraphs /* trigraphs */, 18830, /* DiagArray880 */ 2453, 0, R"()")
DIAG_ENTRY(TypeLimits /* type-limits */, 18840, 0, /* DiagSubGroup881 */ 680, R"()")
DIAG_ENTRY(TypeSafety /* type-safety */, 18852, /* DiagArray882 */ 2458, 0, R"()")
DIAG_ENTRY(anonymous_223 /* typedef-redefinition */, 18864, /* DiagArray883 */ 2462, 0, R"()")
DIAG_ENTRY(anonymous_217 /* typename-missing */, 18885, /* DiagArray884 */ 2464, 0, R"()")
DIAG_ENTRY(anonymous_101 /* unable-to-open-stats-file */, 18902, /* DiagArray885 */ 2466, 0, R"()")
DIAG_ENTRY(UnalignedAccess /* unaligned-access */, 18928, /* DiagArray886 */ 2468, 0, R"()")
DIAG_ENTRY(anonymous_281 /* unaligned-qualifier-implicit-cast */, 18945, /* DiagArray887 */ 2470, 0, R"()")
DIAG_ENTRY(UnavailableDeclarations /* unavailable-declarations */, 18979, /* DiagArray888 */ 2472, 0, R"()")
DIAG_ENTRY(UndeclaredSelector /* undeclared-selector */, 19004, /* DiagArray889 */ 2474, 0, R"()")
DIAG_ENTRY(anonymous_124 /* undef */, 19024, /* DiagArray890 */ 2477, 0, R"()")
DIAG_ENTRY(anonymous_125 /* undef-prefix */, 19030, /* DiagArray891 */ 2479, 0, R"()")
DIAG_ENTRY(UndefinedBoolConversion /* undefined-bool-conversion */, 19043, /* DiagArray892 */ 2481, 0, R"()")
DIAG_ENTRY(UndefinedFuncTemplate /* undefined-func-template */, 19069, /* DiagArray893 */ 2484, 0, R"()")
DIAG_ENTRY(anonymous_222 /* undefined-inline */, 19093, /* DiagArray894 */ 2486, 0, R"()")
DIAG_ENTRY(anonymous_220 /* undefined-internal */, 19110, /* DiagArray895 */ 2488, 0, R"()")
DIAG_ENTRY(anonymous_221 /* undefined-internal-type */, 19129, /* DiagArray896 */ 2490, 0, R"()")
DIAG_ENTRY(UndefinedReinterpretCast /* undefined-reinterpret-cast */, 19153, /* DiagArray897 */ 2492, 0, R"()")
DIAG_ENTRY(UndefinedVarTemplate /* undefined-var-template */, 19180, /* DiagArray898 */ 2495, 0, R"()")
DIAG_ENTRY(UnderalignedExceptionObject /* underaligned-exception-object */, 19203, /* DiagArray899 */ 2497, 0, R"()")
DIAG_ENTRY(UnevaluatedExpression /* unevaluated-expression */, 19233, /* DiagArray900 */ 2499, /* DiagSubGroup900 */ 685, R"()")
DIAG_ENTRY(UnguardedAvailability /* unguarded-availability */, 19256, /* DiagArray901 */ 2501, /* DiagSubGroup901 */ 687, R"()")
DIAG_ENTRY(UnguardedAvailabilityNew /* unguarded-availability-new */, 19279, /* DiagArray902 */ 2503, 0, R"()")
DIAG_ENTRY(Unicode /* unicode */, 19306, /* DiagArray903 */ 2505, 0, R"()")
DIAG_ENTRY(anonymous_110 /* unicode-homoglyph */, 19314, /* DiagArray904 */ 2513, 0, R"()")
DIAG_ENTRY(anonymous_109 /* unicode-whitespace */, 19332, /* DiagArray905 */ 2515, 0, R"()")
DIAG_ENTRY(anonymous_111 /* unicode-zero-width */, 19351, /* DiagArray906 */ 2517, 0, R"()")
DIAG_ENTRY(Uninitialized /* uninitialized */, 19370, /* DiagArray907 */ 2519, /* DiagSubGroup907 */ 689, R"()")
DIAG_ENTRY(UninitializedConstReference /* uninitialized-const-reference */, 19384, /* DiagArray908 */ 2527, 0, R"()")
DIAG_ENTRY(UnknownArgument /* unknown-argument */, 19414, /* DiagArray909 */ 2529, 0, R"()")
DIAG_ENTRY(UnknownAssumption /* unknown-assumption */, 19431, /* DiagArray910 */ 2533, 0, R"()")
DIAG_ENTRY(UnknownAttributes /* unknown-attributes */, 19450, /* DiagArray911 */ 2535, 0, R"()")
DIAG_ENTRY(CudaUnknownVersion /* unknown-cuda-version */, 19469, /* DiagArray912 */ 2537, 0, R"()")
DIAG_ENTRY(anonymous_129 /* unknown-directives */, 19490, /* DiagArray913 */ 2540, 0, R"()")
DIAG_ENTRY(anonymous_114 /* unknown-escape-sequence */, 19509, /* DiagArray914 */ 2542, 0, R"()")
DIAG_ENTRY(UnknownPragmas /* unknown-pragmas */, 19533, /* DiagArray915 */ 2544, 0, R"()")
DIAG_ENTRY(UnknownSanitizers /* unknown-sanitizers */, 19549, /* DiagArray916 */ 2565, 0, R"()")
DIAG_ENTRY(UnknownWarningOption /* unknown-warning-option */, 19568, /* DiagArray917 */ 2567, 0, R"()")
DIAG_ENTRY(UnnamedTypeTemplateArgs /* unnamed-type-template-args */, 19591, /* DiagArray918 */ 2571, /* DiagSubGroup918 */ 693, R"()")
DIAG_ENTRY(UnneededInternalDecl /* unneeded-internal-declaration */, 19618, /* DiagArray919 */ 2573, 0, R"()")
DIAG_ENTRY(UnneededMemberFunction /* unneeded-member-function */, 19648, /* DiagArray920 */ 2576, 0, R"()")
DIAG_ENTRY(anonymous_213 /* unqualified-std-cast-call */, 19673, /* DiagArray921 */ 2578, 0, R"()")
DIAG_ENTRY(UnreachableCode /* unreachable-code */, 19699, /* DiagArray922 */ 2580, /* DiagSubGroup922 */ 695, R"()")
DIAG_ENTRY(UnreachableCodeAggressive /* unreachable-code-aggressive */, 19716, 0, /* DiagSubGroup923 */ 699, R"()")
DIAG_ENTRY(UnreachableCodeBreak /* unreachable-code-break */, 19744, /* DiagArray924 */ 2582, 0, R"()")
DIAG_ENTRY(UnreachableCodeFallthrough /* unreachable-code-fallthrough */, 19767, /* DiagArray925 */ 2584, 0, R"()")
DIAG_ENTRY(UnreachableCodeGenericAssoc /* unreachable-code-generic-assoc */, 19796, /* DiagArray926 */ 2586, 0, R"()")
DIAG_ENTRY(UnreachableCodeLoopIncrement /* unreachable-code-loop-increment */, 19827, /* DiagArray927 */ 2588, 0, R"()")
DIAG_ENTRY(UnreachableCodeReturn /* unreachable-code-return */, 19859, /* DiagArray928 */ 2590, 0, R"()")
DIAG_ENTRY(UnsafeBufferUsage /* unsafe-buffer-usage */, 19883, /* DiagArray929 */ 2592, 0, R"()")
DIAG_ENTRY(Unsequenced /* unsequenced */, 19903, /* DiagArray930 */ 2595, 0, R"()")
DIAG_ENTRY(UnsupportedABI /* unsupported-abi */, 19915, /* DiagArray931 */ 2598, 0, R"()")
DIAG_ENTRY(UnsupportedAbs /* unsupported-abs */, 19931, /* DiagArray932 */ 2601, 0, R"()")
DIAG_ENTRY(anonymous_202 /* unsupported-availability-guard */, 19947, /* DiagArray933 */ 2604, 0, R"()")
DIAG_ENTRY(UnsupportedCB /* unsupported-cb */, 19978, /* DiagArray934 */ 2606, 0, R"()")
DIAG_ENTRY(anonymous_198 /* unsupported-dll-base-class-template */, 19993, /* DiagArray935 */ 2608, 0, R"()")
DIAG_ENTRY(UnsupportedFPOpt /* unsupported-floating-point-opt */, 20029, /* DiagArray936 */ 2610, 0, R"()")
DIAG_ENTRY(UnsupportedFriend /* unsupported-friend */, 20060, /* DiagArray937 */ 2613, 0, R"()")
DIAG_ENTRY(UnsupportedGPOpt /* unsupported-gpopt */, 20079, /* DiagArray938 */ 2616, 0, R"()")
DIAG_ENTRY(UnsupportedNan /* unsupported-nan */, 20097, /* DiagArray939 */ 2618, 0, R"()")
DIAG_ENTRY(UnsupportedTargetOpt /* unsupported-target-opt */, 20113, /* DiagArray940 */ 2621, 0, R"()")
DIAG_ENTRY(anonymous_208 /* unsupported-visibility */, 20136, /* DiagArray941 */ 2624, 0, R"()")
DIAG_ENTRY(anonymous_215 /* unusable-partial-specialization */, 20159, /* DiagArray942 */ 2626, 0, R"()")
DIAG_ENTRY(Unused /* unused */, 20191, 0, /* DiagSubGroup943 */ 703, R"()")
DIAG_ENTRY(UnusedArgument /* unused-argument */, 20198, 0, 0, R"()")
DIAG_ENTRY(UnusedButSetParameter /* unused-but-set-parameter */, 20214, /* DiagArray945 */ 2628, 0, R"()")
DIAG_ENTRY(UnusedButSetVariable /* unused-but-set-variable */, 20239, /* DiagArray946 */ 2630, 0, R"()")
DIAG_ENTRY(UnusedCommandLineArgument /* unused-command-line-argument */, 20263, /* DiagArray947 */ 2632, 0, R"()")
DIAG_ENTRY(UnusedComparison /* unused-comparison */, 20292, /* DiagArray948 */ 2645, 0, R"()")
DIAG_ENTRY(UnusedConstVariable /* unused-const-variable */, 20310, /* DiagArray949 */ 2647, 0, R"()")
DIAG_ENTRY(UnusedExceptionParameter /* unused-exception-parameter */, 20332, /* DiagArray950 */ 2649, 0, R"()")
DIAG_ENTRY(UnusedFunction /* unused-function */, 20359, /* DiagArray951 */ 2651, /* DiagSubGroup951 */ 714, R"()")
DIAG_ENTRY(UnusedGetterReturnValue /* unused-getter-return-value */, 20375, /* DiagArray952 */ 2653, 0, R"()")
DIAG_ENTRY(UnusedLabel /* unused-label */, 20402, /* DiagArray953 */ 2655, 0, R"()")
DIAG_ENTRY(UnusedLambdaCapture /* unused-lambda-capture */, 20415, /* DiagArray954 */ 2657, 0, R"()")
DIAG_ENTRY(UnusedLocalTypedef /* unused-local-typedef */, 20437, /* DiagArray955 */ 2659, 0, R"()")
DIAG_ENTRY(anonymous_69 /* unused-local-typedefs */, 20458, 0, /* DiagSubGroup956 */ 716, R"()")
DIAG_ENTRY(anonymous_123 /* unused-macros */, 20480, /* DiagArray957 */ 2661, 0, R"()")
DIAG_ENTRY(UnusedMemberFunction /* unused-member-function */, 20494, /* DiagArray958 */ 2663, /* DiagSubGroup958 */ 718, R"()")
DIAG_ENTRY(UnusedParameter /* unused-parameter */, 20517, /* DiagArray959 */ 2665, 0, R"()")
DIAG_ENTRY(UnusedPrivateField /* unused-private-field */, 20534, /* DiagArray960 */ 2667, 0, R"()")
DIAG_ENTRY(UnusedPropertyIvar /* unused-property-ivar */, 20555, /* DiagArray961 */ 2669, 0, R"()")
DIAG_ENTRY(UnusedResult /* unused-result */, 20576, /* DiagArray962 */ 2671, 0, R"()")
DIAG_ENTRY(UnusedTemplate /* unused-template */, 20590, /* DiagArray963 */ 2674, /* DiagSubGroup963 */ 720, R"()")
DIAG_ENTRY(UnusedValue /* unused-value */, 20606, /* DiagArray964 */ 2676, /* DiagSubGroup964 */ 722, R"()")
DIAG_ENTRY(UnusedVariable /* unused-variable */, 20619, /* DiagArray965 */ 2684, /* DiagSubGroup965 */ 726, R"()")
DIAG_ENTRY(anonymous_264 /* unused-volatile-lvalue */, 20635, /* DiagArray966 */ 2686, 0, R"()")
DIAG_ENTRY(UsedButMarkedUnused /* used-but-marked-unused */, 20658, /* DiagArray967 */ 2688, 0, R"()")
DIAG_ENTRY(UserDefinedLiterals /* user-defined-literals */, 20681, /* DiagArray968 */ 2690, 0, R"()")
DIAG_ENTRY(UserDefinedWarnings /* user-defined-warnings */, 20703, /* DiagArray969 */ 2692, 0, R"()")
DIAG_ENTRY(Varargs /* varargs */, 20725, /* DiagArray970 */ 2694, 0, R"()")
DIAG_ENTRY(VariadicMacros /* variadic-macros */, 20733, /* DiagArray971 */ 2698, 0, R"()")
DIAG_ENTRY(anonymous_188 /* vec-elem-size */, 20749, /* DiagArray972 */ 2702, 0, R"()")
DIAG_ENTRY(VectorConversion /* vector-conversion */, 20763, /* DiagArray973 */ 2704, 0, R"()")
DIAG_ENTRY(anonymous_68 /* vector-conversions */, 20781, 0, /* DiagSubGroup974 */ 728, R"()")
DIAG_ENTRY(VexingParse /* vexing-parse */, 20800, /* DiagArray975 */ 2706, 0, R"()")
DIAG_ENTRY(Visibility /* visibility */, 20813, /* DiagArray976 */ 2710, 0, R"()")
DIAG_ENTRY(VLA /* vla */, 20824, /* DiagArray977 */ 2713, /* DiagSubGroup977 */ 730, R"()")
DIAG_ENTRY(VLAExtension /* vla-extension */, 20828, /* DiagArray978 */ 2715, 0, R"()")
DIAG_ENTRY(VoidPointerToEnumCast /* void-pointer-to-enum-cast */, 20842, /* DiagArray979 */ 2717, 0, R"()")
DIAG_ENTRY(VoidPointerToIntCast /* void-pointer-to-int-cast */, 20868, /* DiagArray980 */ 2719, /* DiagSubGroup980 */ 732, R"()")
DIAG_ENTRY(VoidPointerDeref /* void-ptr-dereference */, 20893, /* DiagArray981 */ 2721, 0, R"()")
DIAG_ENTRY(VolatileRegisterVar /* volatile-register-var */, 20914, 0, 0, R"()")
DIAG_ENTRY(WebAssemblyExceptionSpec /* wasm-exception-spec */, 20936, /* DiagArray983 */ 2723, 0, R"()")
DIAG_ENTRY(anonymous_181 /* weak-template-vtables */, 20956, /* DiagArray984 */ 2725, 0, R"()")
DIAG_ENTRY(anonymous_180 /* weak-vtables */, 20978, /* DiagArray985 */ 2727, 0, R"()")
DIAG_ENTRY(WritableStrings /* writable-strings */, 20991, /* DiagArray986 */ 2729, /* DiagSubGroup986 */ 734, R"()")
DIAG_ENTRY(GCCWriteStrings /* write-strings */, 21008, 0, /* DiagSubGroup987 */ 736, R"(**Note:** enabling this warning in C will change the semantic behavior of the
program by treating all string literals as having type ``const char *``
instead of ``char *``. This can cause unexpected behaviors with type-sensitive
constructs like ``_Generic``.)")
DIAG_ENTRY(XorUsedAsPow /* xor-used-as-pow */, 21022, /* DiagArray988 */ 2731, 0, R"()")
DIAG_ENTRY(anonymous_286 /* zero-as-null-pointer-constant */, 21038, /* DiagArray989 */ 2735, 0, R"()")
DIAG_ENTRY(ZeroLengthArray /* zero-length-array */, 21068, /* DiagArray990 */ 2737, 0, R"()")
#endif // DIAG_ENTRY


#ifdef GET_CATEGORY_TABLE
CATEGORY("", DiagCat_None)
CATEGORY("Lexical or Preprocessor Issue", DiagCat_Lexical_or_Preprocessor_Issue)
CATEGORY("Semantic Issue", DiagCat_Semantic_Issue)
CATEGORY("Lambda Issue", DiagCat_Lambda_Issue)
CATEGORY("Parse Issue", DiagCat_Parse_Issue)
CATEGORY("ARC Semantic Issue", DiagCat_ARC_Semantic_Issue)
CATEGORY("ARC and @properties", DiagCat_ARC_and__properties)
CATEGORY("ARC Casting Rules", DiagCat_ARC_Casting_Rules)
CATEGORY("ARC Parse Issue", DiagCat_ARC_Parse_Issue)
CATEGORY("ARC Weak References", DiagCat_ARC_Weak_References)
CATEGORY("ARC Restrictions", DiagCat_ARC_Restrictions)
CATEGORY("OpenMP Issue", DiagCat_OpenMP_Issue)
CATEGORY("Inline Assembly Issue", DiagCat_Inline_Assembly_Issue)
CATEGORY("AST Deserialization Issue", DiagCat_AST_Deserialization_Issue)
CATEGORY("Modules Issue", DiagCat_Modules_Issue)
CATEGORY("Coroutines Issue", DiagCat_Coroutines_Issue)
CATEGORY("Concepts Issue", DiagCat_Concepts_Issue)
CATEGORY("Dependency Directive Source Scanner Issue", DiagCat_Dependency_Directive_Source_Scanner_Issue)
CATEGORY("Backend Issue", DiagCat_Backend_Issue)
CATEGORY("SourceMgr Reported Issue", DiagCat_SourceMgr_Reported_Issue)
CATEGORY("Related Result Type Issue", DiagCat_Related_Result_Type_Issue)
CATEGORY("AST Serialization Issue", DiagCat_AST_Serialization_Issue)
CATEGORY("Nullability Issue", DiagCat_Nullability_Issue)
CATEGORY("Generics Issue", DiagCat_Generics_Issue)
CATEGORY("User-Defined Issue", DiagCat_User_Defined_Issue)
CATEGORY("Refactoring Invocation Issue", DiagCat_Refactoring_Invocation_Issue)
CATEGORY("VTable ABI Issue", DiagCat_VTable_ABI_Issue)
CATEGORY("Value Conversion Issue", DiagCat_Value_Conversion_Issue)
CATEGORY("Documentation Issue", DiagCat_Documentation_Issue)
CATEGORY("ARC Retain Cycle", DiagCat_ARC_Retain_Cycle)
CATEGORY("Deprecations", DiagCat_Deprecations)
CATEGORY("Format String Issue", DiagCat_Format_String_Issue)
CATEGORY("Cocoa API Issue", DiagCat_Cocoa_API_Issue)
CATEGORY("#pragma message Directive", DiagCat__pragma_message_Directive)
CATEGORY("Instrumentation Issue", DiagCat_Instrumentation_Issue)
CATEGORY("Unused Entity Issue", DiagCat_Unused_Entity_Issue)
#endif // GET_CATEGORY_TABLE

