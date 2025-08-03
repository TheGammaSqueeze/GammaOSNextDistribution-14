
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
  /* DiagArray95 */ diag::ext_alias_declaration, diag::ext_array_size_conversion, diag::ext_auto_type_specifier, diag::ext_cxx11_enum_fixed_underlying_type, diag::ext_defaulted_deleted_function, diag::ext_enum_friend, diag::ext_enumerator_list_comma_cxx, diag::ext_explicit_conversion_functions, diag::ext_extern_template, diag::ext_for_range, diag::ext_generalized_initializer_lists, diag::ext_nested_name_spec_is_enum, diag::ext_nonclass_type_friend, diag::ext_nonstatic_member_init, diag::ext_override_control_keyword, diag::ext_ref_qualifier, diag::ext_rvalue_reference, diag::ext_scoped_enum, diag::ext_static_data_member_in_union, diag::ext_template_arg_object_internal, diag::ext_template_outside_of_template, diag::ext_template_parameter_default_in_function_template, diag::ext_typename_outside_of_template, diag::ext_unelaborated_friend_type, diag::ext_variadic_templates, -1,
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
  /* DiagArray129 */ diag::warn_cxx11_compat_binary_literal, -1,
  /* DiagArray131 */ diag::warn_cxx98_compat_alias_declaration, diag::warn_cxx98_compat_alignas, diag::warn_cxx98_compat_alignof, diag::warn_cxx98_compat_attribute, diag::warn_cxx98_compat_auto_type_specifier, diag::warn_cxx98_compat_constexpr, diag::warn_cxx98_compat_ctor_list_init, diag::warn_cxx98_compat_decltype, diag::warn_cxx98_compat_defaulted_deleted_function, diag::warn_cxx98_compat_delegating_ctor, diag::warn_cxx98_compat_empty_scalar_initializer, diag::warn_cxx98_compat_empty_sizeless_initializer, diag::warn_cxx98_compat_enum_fixed_underlying_type, diag::warn_cxx98_compat_enum_friend, diag::warn_cxx98_compat_enum_nested_name_spec, diag::warn_cxx98_compat_explicit_conversion_functions, diag::warn_cxx98_compat_for_range, diag::warn_cxx98_compat_friend_is_member, diag::warn_cxx98_compat_generalized_initializer_lists, diag::warn_cxx98_compat_goto_into_protected_scope, diag::warn_cxx98_compat_indirect_goto_in_protected_scope, diag::warn_cxx98_compat_initializer_list_init, diag::warn_cxx98_compat_inline_namespace, diag::warn_cxx98_compat_lambda, diag::warn_cxx98_compat_less_colon_colon, diag::warn_cxx98_compat_literal_operator, diag::warn_cxx98_compat_literal_ucn_control_character, diag::warn_cxx98_compat_literal_ucn_escape_basic_scs, diag::warn_cxx98_compat_noexcept_decl, diag::warn_cxx98_compat_noexcept_expr, diag::warn_cxx98_compat_non_static_member_use, diag::warn_cxx98_compat_nonclass_type_friend, diag::warn_cxx98_compat_nonstatic_member_init, diag::warn_cxx98_compat_nontrivial_union_or_anon_struct_member, diag::warn_cxx98_compat_nullptr, diag::warn_cxx98_compat_override_control_keyword, diag::warn_cxx98_compat_pass_non_pod_arg_to_vararg, diag::warn_cxx98_compat_raw_string_literal, diag::warn_cxx98_compat_ref_qualifier, diag::warn_cxx98_compat_reference_list_init, diag::warn_cxx98_compat_rvalue_reference, diag::warn_cxx98_compat_scoped_enum, diag::warn_cxx98_compat_sfinae_access_control, diag::warn_cxx98_compat_static_assert, diag::warn_cxx98_compat_static_data_member_in_union, diag::warn_cxx98_compat_switch_into_protected_scope, diag::warn_cxx98_compat_template_arg_extra_parens, diag::warn_cxx98_compat_template_arg_null, diag::warn_cxx98_compat_template_arg_object_internal, diag::warn_cxx98_compat_template_outside_of_template, diag::warn_cxx98_compat_template_parameter_default_in_function_template, diag::warn_cxx98_compat_trailing_return_type, diag::warn_cxx98_compat_two_right_angle_brackets, diag::warn_cxx98_compat_typename_outside_of_template, diag::warn_cxx98_compat_unelaborated_friend_type, diag::warn_cxx98_compat_unicode_literal, diag::warn_cxx98_compat_unicode_type, diag::warn_cxx98_compat_using_decl_constructor, diag::warn_cxx98_compat_variadic_templates, -1,
  /* DiagArray132 */ diag::warn_cxx98_compat_temp_copy, -1,
  /* DiagArray133 */ diag::warn_cxx98_compat_top_level_semi, -1,
  /* DiagArray134 */ diag::warn_cxx98_compat_template_arg_local_type, -1,
  /* DiagArray135 */ diag::warn_cxx98_compat_array_size_conversion, diag::warn_cxx98_compat_cast_fn_obj, diag::warn_cxx98_compat_empty_fnmacro_arg, diag::warn_cxx98_compat_enumerator_list_comma, diag::warn_cxx98_compat_extern_template, diag::warn_cxx98_compat_longlong, diag::warn_cxx98_compat_no_newline_eof, diag::warn_cxx98_compat_pp_line_too_big, diag::warn_cxx98_compat_variadic_macro, -1,
  /* DiagArray136 */ diag::warn_cxx98_compat_template_arg_unnamed_type, -1,
  /* DiagArray137 */ diag::ext_anonymous_union, diag::ext_c11_anonymous_struct, diag::ext_c11_feature, diag::ext_typecheck_compare_complete_incomplete_pointers, -1,
  /* DiagArray138 */ diag::warn_c2x_keyword, -1,
  /* DiagArray139 */ diag::ext_c2x_bitint_suffix, diag::ext_c2x_pp_directive, diag::ext_c_empty_initializer, diag::ext_c_label_end_of_compound_statement, diag::ext_c_nullptr, diag::ext_c_static_assert_no_message, diag::ext_parameter_name_omitted_c2x, -1,
  /* DiagArray140 */ diag::warn_c99_compat_unicode_id, diag::warn_c99_compat_unicode_literal, diag::warn_c99_keyword, diag::warn_old_implicitly_unsigned_long, -1,
  /* DiagArray141 */ diag::ext_designated_init, diag::ext_designated_init_array, diag::ext_designated_init_brace_elision, diag::ext_designated_init_mixed, diag::ext_designated_init_nested, -1,
  /* DiagArray142 */ diag::ext_aggregate_init_not_constant, diag::ext_c99_array_usage, diag::ext_c99_compound_literal, diag::ext_c99_feature, diag::ext_c99_flexible_array_member, diag::ext_c99_variable_decl_in_for_loop, diag::ext_c99_whitespace_required_after_macro_name, diag::ext_empty_fnmacro_arg, diag::ext_enumerator_list_comma_c, diag::ext_hex_constant_invalid, -1,
  /* DiagArray143 */ diag::warn_call_to_pure_virtual_member_function_from_ctor_dtor, -1,
  /* DiagArray144 */ diag::warn_called_once_gets_called_twice, diag::warn_called_once_never_called, diag::warn_called_once_never_called_when, -1,
  /* DiagArray145 */ diag::warn_cast_align, -1,
  /* DiagArray146 */ diag::warn_cast_calling_conv, -1,
  /* DiagArray147 */ diag::warn_cast_function_type, -1,
  /* DiagArray148 */ diag::warn_cast_function_type_strict, -1,
  /* DiagArray149 */ diag::warn_cast_pointer_from_sel, -1,
  /* DiagArray150 */ diag::warn_cast_qual, diag::warn_cast_qual2, -1,
  /* DiagArray151 */ diag::ext_bad_cxx_cast_qualifiers_away_incoherent, -1,
  /* DiagArray153 */ diag::warn_omp_section_is_char, diag::warn_subscript_is_char, -1,
  /* DiagArray154 */ diag::warn_drv_yc_multiple_inputs_clang_cl, diag::warn_drv_ycyu_different_arg_clang_cl, diag::warn_pp_hdrstop_filename_ignored, diag::warn_pp_macro_def_mismatch_with_pch, -1,
  /* DiagArray155 */ diag::warn_conv_to_base_not_used, diag::warn_conv_to_self_not_used, diag::warn_conv_to_void_not_used, -1,
  /* DiagArray156 */ diag::warn_pass_class_arg_to_vararg, -1,
  /* DiagArray157 */ diag::warn_cmse_nonsecure_union, -1,
  /* DiagArray158 */ diag::warn_comma_operator, -1,
  /* DiagArray159 */ diag::escaped_newline_block_comment_end, diag::ext_line_comment, diag::ext_multi_line_line_comment, diag::warn_nested_block_comment, -1,
  /* DiagArray161 */ diag::ext_typecheck_comparison_of_distinct_pointers, -1,
  /* DiagArray162 */ diag::warn_completion_handler_called_twice, diag::warn_completion_handler_never_called, diag::warn_completion_handler_never_called_when, -1,
  /* DiagArray163 */ diag::ext_complex_component_init, -1,
  /* DiagArray165 */ diag::warn_compound_token_split_by_macro, -1,
  /* DiagArray166 */ diag::warn_compound_token_split_by_whitespace, -1,
  /* DiagArray167 */ diag::ext_typecheck_cond_pointer_integer_mismatch, -1,
  /* DiagArray168 */ diag::warn_maybe_uninit_var, -1,
  /* DiagArray169 */ diag::warn_module_config_macro_undef, -1,
  /* DiagArray170 */ diag::warn_impcast_integer_precision_constant, -1,
  /* DiagArray171 */ diag::warn_is_constant_evaluated_always_true_constexpr, -1,
  /* DiagArray172 */ diag::warn_logical_instead_of_bitwise, -1,
  /* DiagArray173 */ diag::warn_cxx14_compat_constexpr_not_const, -1,
  /* DiagArray174 */ diag::warn_attr_on_unconsumable_class, diag::warn_loop_state_mismatch, diag::warn_param_return_typestate_mismatch, diag::warn_param_typestate_mismatch, diag::warn_return_typestate_for_unconsumable_type, diag::warn_return_typestate_mismatch, diag::warn_use_in_invalid_state, diag::warn_use_of_temp_in_invalid_state, -1,
  /* DiagArray175 */ diag::warn_impcast_complex_scalar, diag::warn_impcast_vector_scalar, diag::warn_opencl_generic_address_space_arg, diag::warn_template_arg_negative, diag::warn_template_arg_too_large, -1,
  /* DiagArray177 */ diag::warn_non_aligned_allocation_function, -1,
  /* DiagArray178 */ diag::warn_coroutine_handle_address_invalid_return_type, -1,
  /* DiagArray179 */ diag::warn_coroutine_promise_unhandled_exception_required_with_exceptions, -1,
  /* DiagArray180 */ diag::warn_unreachable_default, -1,
  /* DiagArray182 */ diag::warn_objc_cdirective_format_string, -1,
  /* DiagArray183 */ diag::warn_ctad_maybe_unsupported, -1,
  /* DiagArray185 */ diag::warn_ctu_incompat_triple, -1,
  /* DiagArray186 */ diag::warn_attribute_argument_n_negative, diag::warn_cuda_attr_lambda_position, diag::warn_kern_is_inline, diag::warn_kern_is_method, diag::warn_pragma_unroll_cuda_value_in_parens, -1,
  /* DiagArray187 */ diag::warn_default_atomic_custom_getter_setter, -1,
  /* DiagArray188 */ diag::ext_cxx11_attr_placement, -1,
  /* DiagArray189 */ diag::warn_dangling_variable, diag::warn_unsupported_lifetime_extension, -1,
  /* DiagArray190 */ diag::warn_dangling_else, -1,
  /* DiagArray191 */ diag::warn_bind_ref_member_to_parameter, diag::warn_dangling_member, diag::warn_init_ptr_member_to_parameter_addr, diag::warn_new_dangling_reference, -1,
  /* DiagArray192 */ diag::warn_dangling_lifetime_pointer, diag::warn_dangling_lifetime_pointer_member, -1,
  /* DiagArray193 */ diag::warn_new_dangling_initializer_list, -1,
  /* DiagArray194 */ diag::warn_drv_darwin_sdk_invalid_settings, -1,
  /* DiagArray195 */ diag::warn_pp_date_time, -1,
  /* DiagArray196 */ diag::warn_dealloc_in_category, -1,
  /* DiagArray197 */ diag::warn_debug_compression_unavailable, -1,
  /* DiagArray198 */ diag::ext_mixed_decls_code, diag::warn_mixed_decls_code, -1,
  /* DiagArray199 */ diag::warn_defaulted_comparison_deleted, diag::warn_defaulted_method_deleted, -1,
  /* DiagArray200 */ diag::warn_delegating_ctor_cycle, -1,
  /* DiagArray201 */ diag::warn_delete_abstract_non_virtual_dtor, -1,
  /* DiagArray202 */ diag::ext_delete_void_ptr_operand, diag::warn_delete_incomplete, -1,
  /* DiagArray203 */ diag::warn_delete_non_virtual_dtor, -1,
  /* DiagArray205 */ diag::ext_delimited_escape_sequence, -1,
  /* DiagArray206 */ diag::warn_deprecated_lax_vec_conv_all, -1,
  /* DiagArray207 */ diag::warn_O4_is_O3, diag::warn_access_decl_deprecated, diag::warn_deprecated_redundant_constexpr_static_def, diag::warn_drv_deprecated_arg, diag::warn_drv_treating_input_as_cxx, diag::warn_option_invalid_ocl_version, diag::warn_vector_long_decl_spec_combination, -1,
  /* DiagArray208 */ diag::warn_deprecated_altivec_src_compat, -1,
  /* DiagArray209 */ diag::warn_arith_conv_mixed_anon_enum_types_cxx20, -1,
  /* DiagArray210 */ diag::warn_depr_array_comparison, -1,
  /* DiagArray211 */ diag::warn_deprecated_noreturn_spelling, diag::warn_type_attribute_deprecated_on_decl, diag::warn_vector_mode_deprecated, -1,
  /* DiagArray212 */ diag::warn_deprecated_builtin, -1,
  /* DiagArray213 */ diag::warn_deprecated_comma_subscript, -1,
  /* DiagArray214 */ diag::warn_deprecated_copy, -1,
  /* DiagArray216 */ diag::warn_deprecated_copy_with_dtor, -1,
  /* DiagArray217 */ diag::warn_deprecated_copy_with_user_provided_copy, -1,
  /* DiagArray218 */ diag::warn_deprecated_copy_with_user_provided_dtor, -1,
  /* DiagArray219 */ diag::warn_deprecated_for_co_await, -1,
  /* DiagArray220 */ diag::warn_atl_uuid_deprecated, diag::warn_cstyle_param, diag::warn_deprecated, diag::warn_deprecated_fwdclass_message, diag::warn_deprecated_message, diag::warn_property_method_deprecated, -1,
  /* DiagArray221 */ diag::warn_exception_spec_deprecated, -1,
  /* DiagArray222 */ diag::warn_comparison_mixed_enum_types_cxx20, -1,
  /* DiagArray223 */ diag::warn_conditional_mixed_enum_types_cxx20, -1,
  /* DiagArray224 */ diag::warn_arith_conv_mixed_enum_types_cxx20, -1,
  /* DiagArray225 */ diag::warn_arith_conv_enum_float_cxx20, -1,
  /* DiagArray226 */ diag::warn_deprecated_def, diag::warn_unavailable_def, -1,
  /* DiagArray227 */ diag::warn_increment_bool, -1,
  /* DiagArray228 */ diag::warn_non_prototype_changes_behavior, diag::warn_strict_uses_without_prototype, -1,
  /* DiagArray229 */ diag::warn_objc_isa_assign, diag::warn_objc_isa_use, -1,
  /* DiagArray230 */ diag::warn_objc_pointer_masking, -1,
  /* DiagArray231 */ diag::warn_objc_pointer_masking_performSelector, -1,
  /* DiagArray232 */ diag::warn_pragma_deprecated_macro_use, -1,
  /* DiagArray233 */ diag::warn_deprecated_register, -1,
  /* DiagArray234 */ diag::warn_analyzer_deprecated_option, diag::warn_analyzer_deprecated_option_with_alternative, -1,
  /* DiagArray235 */ diag::warn_deprecated_this_capture, -1,
  /* DiagArray236 */ diag::warn_ext_int_deprecated, -1,
  /* DiagArray237 */ diag::warn_deprecated_increment_decrement_volatile, diag::warn_deprecated_simple_assign_volatile, diag::warn_deprecated_volatile_param, diag::warn_deprecated_volatile_return, diag::warn_deprecated_volatile_structured_binding, -1,
  /* DiagArray239 */ diag::warn_direct_ivar_access, -1,
  /* DiagArray240 */ diag::pp_disabled_macro_expansion, -1,
  /* DiagArray243 */ diag::warn_conflicting_param_modifiers, diag::warn_conflicting_ret_type_modifiers, -1,
  /* DiagArray245 */ diag::warn_remainder_division_by_zero, -1,
  /* DiagArray246 */ diag::warn_attribute_dll_redeclaration, -1,
  /* DiagArray247 */ diag::warn_attribute_dllexport_explicit_instantiation_decl, -1,
  /* DiagArray248 */ diag::warn_attribute_dllimport_static_field_definition, -1,
  /* DiagArray249 */ diag::warn_doc_api_container_decl_mismatch, diag::warn_doc_block_command_duplicate, diag::warn_doc_block_command_empty_paragraph, diag::warn_doc_container_decl_mismatch, diag::warn_doc_function_method_decl_mismatch, diag::warn_doc_html_start_tag_expected_ident_or_greater, diag::warn_doc_html_start_tag_expected_quoted_string, diag::warn_doc_inline_command_not_enough_arguments, diag::warn_doc_param_duplicate, diag::warn_doc_param_invalid_direction, diag::warn_doc_param_not_attached_to_a_function_decl, diag::warn_doc_param_not_found, diag::warn_doc_returns_attached_to_a_void_function, diag::warn_doc_returns_not_attached_to_a_function_decl, diag::warn_doc_tparam_duplicate, diag::warn_doc_tparam_not_attached_to_a_template_decl, diag::warn_doc_tparam_not_found, diag::warn_not_a_doxygen_trailing_member_comment, diag::warn_splice_in_doxygen_comment, diag::warn_verbatim_block_end_without_start, -1,
  /* DiagArray250 */ diag::warn_doc_deprecated_not_sync, -1,
  /* DiagArray251 */ diag::warn_doc_html_end_forbidden, diag::warn_doc_html_end_unbalanced, diag::warn_doc_html_missing_end_tag, diag::warn_doc_html_start_end_mismatch, -1,
  /* DiagArray252 */ diag::warn_doc_param_spaces_in_direction, -1,
  /* DiagArray253 */ diag::warn_correct_comment_command_name, diag::warn_unknown_comment_command_name, -1,
  /* DiagArray254 */ diag::ext_dollar_in_identifier, -1,
  /* DiagArray255 */ diag::warn_impcast_double_promotion, -1,
  /* DiagArray256 */ diag::ext_dtor_name_ambiguous, diag::ext_dtor_named_in_wrong_scope, diag::ext_qualified_dtor_named_in_lexical_scope, -1,
  /* DiagArray257 */ diag::ext_destructor_typedef_name, -1,
  /* DiagArray258 */ diag::ext_duplicate_declspec, diag::ext_warn_duplicate_declspec, diag::warn_attribute_address_multiple_identical_qualifiers, diag::warn_duplicate_declspec, -1,
  /* DiagArray259 */ diag::warn_duplicate_enum_values, -1,
  /* DiagArray260 */ diag::warn_method_param_declaration, -1,
  /* DiagArray261 */ diag::warn_duplicate_method_decl, -1,
  /* DiagArray262 */ diag::warn_duplicate_protocol_def, -1,
  /* DiagArray263 */ diag::warn_drv_dxc_missing_dxv, -1,
  /* DiagArray264 */ diag::warn_dyn_class_memaccess, -1,
  /* DiagArray265 */ diag::ext_dynamic_exception_spec, -1,
  /* DiagArray266 */ diag::warn_eagerly_load_for_standard_cplusplus_modules, -1,
  /* DiagArray268 */ diag::ext_enum_base_in_type_specifier, -1,
  /* DiagArray269 */ diag::ext_elaborated_enum_class, -1,
  /* DiagArray270 */ diag::ext_embedded_directive, -1,
  /* DiagArray271 */ diag::warn_empty_for_body, diag::warn_empty_if_body, diag::warn_empty_range_based_for_body, diag::warn_empty_switch_body, diag::warn_empty_while_body, -1,
  /* DiagArray272 */ diag::ext_decomp_decl_empty, -1,
  /* DiagArray273 */ diag::warn_empty_init_statement, -1,
  /* DiagArray274 */ diag::ext_empty_translation_unit, -1,
  /* DiagArray275 */ diag::warn_incomplete_encoded_type, -1,
  /* DiagArray277 */ diag::warn_comparison_mixed_enum_types, -1,
  /* DiagArray278 */ diag::warn_conditional_mixed_enum_types, -1,
  /* DiagArray279 */ diag::warn_comparison_of_mixed_enum_types_switch, -1,
  /* DiagArray280 */ diag::warn_constexpr_unscoped_enum_out_of_range, -1,
  /* DiagArray281 */ diag::warn_impcast_different_enum_types, -1,
  /* DiagArray282 */ diag::warn_arith_conv_mixed_enum_types, -1,
  /* DiagArray283 */ diag::warn_arith_conv_enum_float, -1,
  /* DiagArray284 */ diag::ext_enum_too_large, diag::ext_enumerator_increment_too_large, -1,
  /* DiagArray285 */ diag::warn_cdtor_function_try_handler_mem_expr, diag::warn_exception_caught_by_earlier_handler, diag::warn_throw_in_noexcept_func, -1,
  /* DiagArray286 */ diag::ext_excess_initializers, diag::ext_excess_initializers_for_sizeless_type, diag::ext_excess_initializers_in_char_array_initializer, diag::ext_initializer_string_for_char_array_too_long, -1,
  /* DiagArray287 */ diag::warn_exit_time_destructor, -1,
  /* DiagArray288 */ diag::warn_defined_in_function_type_macro, diag::warn_defined_in_object_type_macro, -1,
  /* DiagArray289 */ diag::warn_direct_initialize_call, diag::warn_direct_super_initialize_call, -1,
  /* DiagArray290 */ diag::warn_arc_strong_pointer_objc_pointer, -1,
  /* DiagArray291 */ diag::ext_export_no_name_block, diag::ext_export_no_names, -1,
  /* DiagArray292 */ diag::ext_export_using_directive, -1,
  /* DiagArray293 */ diag::warn_zero_size_struct_union_in_extern_c, -1,
  /* DiagArray294 */ diag::warn_extern_init, -1,
  /* DiagArray295 */ diag::warn_arm_interrupt_calling_convention, -1,
  /* DiagArray296 */ diag::warn_namespace_member_extra_qualification, -1,
  /* DiagArray297 */ diag::ext_extra_semi, diag::warn_extra_semi_after_mem_fn_def, -1,
  /* DiagArray298 */ diag::warn_null_statement, -1,
  /* DiagArray299 */ diag::ext_pp_extra_tokens_at_eol, diag::warn_omp_extra_tokens_at_eol, -1,
  /* DiagArray300 */ diag::warn_final_dtor_non_final_class, -1,
  /* DiagArray301 */ diag::warn_pragma_final_macro, -1,
  /* DiagArray302 */ diag::ext_clang_c_enum_fixed_underlying_type, -1,
  /* DiagArray303 */ diag::warn_fixedpoint_constant_overflow, -1,
  /* DiagArray304 */ diag::warn_flag_enum_constant_out_of_range, -1,
  /* DiagArray305 */ diag::ext_flexible_array_in_array, diag::ext_flexible_array_in_struct, -1,
  /* DiagArray306 */ diag::warn_impcast_float_integer, -1,
  /* DiagArray307 */ diag::warn_floatingpoint_eq, -1,
  /* DiagArray308 */ diag::warn_impcast_float_to_integer, diag::warn_impcast_float_to_integer_out_of_range, -1,
  /* DiagArray309 */ diag::warn_impcast_float_to_integer_zero, -1,
  /* DiagArray310 */ diag::warn_redundant_loop_iteration, diag::warn_variables_not_in_loop_body, -1,
  /* DiagArray311 */ diag::warn_format_P_no_precision, diag::warn_format_argument_needs_cast, diag::warn_format_bool_as_character, diag::warn_format_conversion_argument_type_mismatch, diag::warn_format_invalid_annotation, diag::warn_format_invalid_positional_specifier, diag::warn_format_mix_positional_nonpositional_args, diag::warn_format_nonsensical_length, diag::warn_format_string_is_wide_literal, diag::warn_format_zero_positional_specifier, diag::warn_missing_format_string, diag::warn_printf_ObjCflags_without_ObjCConversion, diag::warn_printf_asterisk_missing_arg, diag::warn_printf_asterisk_wrong_type, diag::warn_printf_empty_objc_flag, diag::warn_printf_format_string_contains_null_char, diag::warn_printf_format_string_not_null_terminated, diag::warn_printf_ignored_flag, diag::warn_printf_incomplete_specifier, diag::warn_printf_invalid_objc_flag, diag::warn_printf_narg_not_supported, diag::warn_printf_nonsensical_flag, diag::warn_printf_nonsensical_optional_amount, diag::warn_printf_positional_arg_exceeds_data_args, diag::warn_scanf_nonzero_width, diag::warn_scanf_scanlist_incomplete, -1,
  /* DiagArray312 */ diag::warn_printf_data_arg_not_used, -1,
  /* DiagArray313 */ diag::warn_printf_insufficient_data_args, -1,
  /* DiagArray314 */ diag::warn_format_invalid_conversion, -1,
  /* DiagArray315 */ diag::warn_format_non_standard, diag::warn_format_non_standard_conversion_spec, diag::warn_format_non_standard_positional_arg, -1,
  /* DiagArray316 */ diag::warn_format_nonliteral, -1,
  /* DiagArray317 */ diag::warn_format_argument_needs_cast_pedantic, diag::warn_format_conversion_argument_type_mismatch_pedantic, -1,
  /* DiagArray318 */ diag::warn_format_nonliteral_noargs, -1,
  /* DiagArray319 */ diag::warn_format_conversion_argument_type_mismatch_confusion, -1,
  /* DiagArray321 */ diag::warn_empty_format_string, -1,
  /* DiagArray323 */ diag::warn_fortify_scanf_overflow, diag::warn_fortify_source_format_overflow, diag::warn_fortify_source_overflow, diag::warn_fortify_source_size_mismatch, diag::warn_fortify_strlen_overflow, -1,
  /* DiagArray324 */ diag::warn_four_char_character_literal, -1,
  /* DiagArray325 */ diag::warn_frame_address, -1,
  /* DiagArray326 */ diag::warn_fe_backend_frame_larger_than, diag::warn_fe_frame_larger_than, -1,
  /* DiagArray328 */ diag::warn_framework_include_private_from_public, -1,
  /* DiagArray329 */ diag::warn_free_nonheap_object, -1,
  /* DiagArray330 */ diag::warn_function_def_in_objc_container, -1,
  /* DiagArray331 */ diag::warn_dispatch_body_ignored, diag::warn_multiversion_duplicate_entries, diag::warn_target_clone_duplicate_options, diag::warn_target_clone_no_impact_options, -1,
  /* DiagArray332 */ diag::warn_drv_fuse_ld_path, -1,
  /* DiagArray335 */ diag::ext_clang_diagnose_if, diag::ext_clang_enable_if, diag::ext_warn_gnu_final, diag::warn_attribute_on_function_definition, diag::warn_break_binds_to_switch, diag::warn_cleanup_ext, diag::warn_gcc_attribute_location, diag::warn_gcc_ignores_type_attr, diag::warn_gcc_requires_variadic_function, diag::warn_gcc_variable_decl_in_for_loop, diag::warn_loop_ctrl_binds_to_inner, -1,
  /* DiagArray336 */ diag::warn_global_constructor, diag::warn_global_destructor, -1,
  /* DiagArray337 */ diag::warn_drv_global_isel_incomplete, diag::warn_drv_global_isel_incomplete_opt, -1,
  /* DiagArray339 */ diag::ext_alignof_expr, -1,
  /* DiagArray340 */ diag::ext_gnu_anonymous_struct, -1,
  /* DiagArray341 */ diag::ext_array_init_parens, -1,
  /* DiagArray342 */ diag::ext_auto_type, -1,
  /* DiagArray343 */ diag::ext_binary_literal, -1,
  /* DiagArray344 */ diag::ext_gnu_case_range, -1,
  /* DiagArray345 */ diag::ext_integer_complex, -1,
  /* DiagArray346 */ diag::ext_array_init_copy, -1,
  /* DiagArray347 */ diag::ext_gnu_conditional_expr, -1,
  /* DiagArray348 */ diag::ext_gnu_array_range, diag::ext_gnu_missing_equal_designator, diag::ext_gnu_old_style_field_designator, -1,
  /* DiagArray349 */ diag::ext_empty_struct_union, diag::ext_flexible_array_empty_aggregate_gnu, diag::ext_no_named_members_in_struct_union, -1,
  /* DiagArray350 */ diag::ext_flexible_array_init, -1,
  /* DiagArray351 */ diag::ext_flexible_array_union_gnu, -1,
  /* DiagArray352 */ diag::ext_expr_not_ice, diag::ext_in_class_initializer_non_constant, diag::ext_vla_folded_to_constant, -1,
  /* DiagArray353 */ diag::ext_imaginary_constant, -1,
  /* DiagArray354 */ diag::ext_pp_include_next_directive, -1,
  /* DiagArray355 */ diag::warn_gnu_inline_cplusplus_without_extern, -1,
  /* DiagArray356 */ diag::ext_gnu_address_of_label, diag::ext_gnu_indirect_goto, -1,
  /* DiagArray357 */ diag::ext_pp_gnu_line_directive, -1,
  /* DiagArray358 */ diag::warn_gnu_null_ptr_arith, -1,
  /* DiagArray359 */ diag::ext_type_defined_in_offsetof, -1,
  /* DiagArray360 */ diag::ext_gnu_ptr_func_arith, diag::ext_gnu_subscript_void_type, diag::ext_gnu_void_ptr, -1,
  /* DiagArray361 */ diag::ext_forward_ref_enum_def, -1,
  /* DiagArray362 */ diag::ext_gnu_statement_expr, -1,
  /* DiagArray363 */ diag::ext_gnu_statement_expr_macro, -1,
  /* DiagArray364 */ diag::ext_in_class_initializer_float_type, -1,
  /* DiagArray365 */ diag::ext_string_literal_operator_template, -1,
  /* DiagArray366 */ diag::ext_typecheck_cast_to_union, -1,
  /* DiagArray367 */ diag::ext_variable_sized_type_in_struct, -1,
  /* DiagArray368 */ diag::ext_pp_line_zero, -1,
  /* DiagArray369 */ diag::ext_missing_varargs_arg, diag::ext_paste_comma, -1,
  /* DiagArray370 */ diag::warn_maybe_capture_bad_target_this_ptr, -1,
  /* DiagArray371 */ diag::warn_header_guard, -1,
  /* DiagArray372 */ diag::warn_using_directive_in_header, -1,
  /* DiagArray373 */ diag::warn_hip_omp_target_directives, -1,
  /* DiagArray374 */ diag::warn_ignored_hip_only_option, -1,
  /* DiagArray375 */ diag::ext_hlsl_access_specifiers, -1,
  /* DiagArray376 */ diag::warn_condition_is_idiomatic_assignment, -1,
  /* DiagArray377 */ diag::ext_cannot_use_trivial_abi, diag::warn_alias_to_weak_alias, diag::warn_alias_with_section, diag::warn_aligned_attr_underaligned, diag::warn_attr_abi_tag_namespace, diag::warn_attribute_after_definition_ignored, diag::warn_attribute_cmse_entry_static, diag::warn_attribute_dllexport_explicit_instantiation_def, diag::warn_attribute_has_no_effect_on_compile_time_if, diag::warn_attribute_has_no_effect_on_infinite_loop, diag::warn_attribute_iboutlet, diag::warn_attribute_ignored, diag::warn_attribute_ignored_for_field_of_type, diag::warn_attribute_ignored_no_calls_in_stmt, diag::warn_attribute_ignored_on_inline, diag::warn_attribute_ignored_on_non_definition, diag::warn_attribute_invalid_on_definition, diag::warn_attribute_no_decl, diag::warn_attribute_nonnull_no_pointers, diag::warn_attribute_nonnull_parm_no_args, diag::warn_attribute_not_on_decl, diag::warn_attribute_pointer_or_reference_only, diag::warn_attribute_pointers_only, diag::warn_attribute_precede_definition, diag::warn_attribute_return_pointers_only, diag::warn_attribute_return_pointers_refs_only, diag::warn_attribute_sentinel_named_arguments, diag::warn_attribute_sentinel_not_variadic, diag::warn_attribute_type_not_supported, diag::warn_attribute_type_not_supported_global, diag::warn_attribute_unknown_visibility, diag::warn_attribute_void_function_method, diag::warn_attribute_weak_on_field, diag::warn_attribute_weak_on_local, diag::warn_attribute_wrong_decl_type, diag::warn_attribute_wrong_decl_type_str, diag::warn_attributes_likelihood_ifstmt_conflict, diag::warn_block_literal_attributes_on_omitted_return_type, diag::warn_cconv_unsupported, diag::warn_cxx11_gnu_attribute_on_type, diag::warn_declspec_allocator_nonpointer, diag::warn_declspec_attribute_ignored, diag::warn_deprecated_anonymous_namespace, diag::warn_deprecated_ignored_on_using, diag::warn_dllimport_dropped_from_inline_function, diag::warn_duplicate_attribute, diag::warn_duplicate_attribute_exact, diag::warn_function_attribute_ignored_in_stmt, diag::warn_function_stmt_attribute_precedence, diag::warn_gc_attribute_weak_on_local, diag::warn_gnu_inline_attribute_requires_inline, diag::warn_ignored_ms_inheritance, diag::warn_ignored_objc_externally_retained, diag::warn_import_on_definition, diag::warn_internal_linkage_local_storage, diag::warn_interrupt_attribute_invalid, diag::warn_microsoft_qualifiers_ignored, diag::warn_mig_server_routine_does_not_return_kern_return_t, diag::warn_mismatched_import, diag::warn_mmap_unknown_attribute, diag::warn_nocf_check_attribute_ignored, diag::warn_noderef_on_non_pointer_or_array, diag::warn_nothrow_attribute_ignored, diag::warn_ns_attribute_wrong_parameter_type, diag::warn_ns_attribute_wrong_return_type, diag::warn_objc_direct_ignored, diag::warn_objc_direct_property_ignored, diag::warn_opencl_attr_deprecated_ignored, diag::warn_require_const_init_added_too_late, diag::warn_riscv_repeated_interrupt_attribute, diag::warn_sycl_kernel_invalid_template_param_type, diag::warn_sycl_kernel_num_of_function_params, diag::warn_sycl_kernel_num_of_template_params, diag::warn_sycl_kernel_return_type, diag::warn_transparent_union_attribute_field_size_align, diag::warn_transparent_union_attribute_floating, diag::warn_transparent_union_attribute_not_definition, diag::warn_transparent_union_attribute_zero_fields, diag::warn_type_attribute_wrong_type, diag::warn_unhandled_ms_attribute_ignored, diag::warn_unsupported_target_attribute, diag::warn_unused_result_typedef_unsupported_spelling, diag::warn_wrong_clang_attr_namespace, -1,
  /* DiagArray378 */ diag::warn_missing_sdksettings_for_availability_checking, -1,
  /* DiagArray379 */ diag::warn_drv_unsupported_opt_for_target, diag::warn_ignored_gcc_optimization, -1,
  /* DiagArray380 */ diag::warn_pragma_intrinsic_builtin, -1,
  /* DiagArray382 */ diag::warn_clause_expected_string, diag::warn_pragma_align_expected_equal, diag::warn_pragma_align_invalid_option, diag::warn_pragma_comment_ignored, diag::warn_pragma_debug_dependent_argument, diag::warn_pragma_debug_missing_argument, diag::warn_pragma_debug_missing_command, diag::warn_pragma_debug_unexpected_argument, diag::warn_pragma_debug_unexpected_command, diag::warn_pragma_debug_unknown_module, diag::warn_pragma_expected_action_or_r_paren, diag::warn_pragma_expected_colon, diag::warn_pragma_expected_colon_r_paren, diag::warn_pragma_expected_comma, diag::warn_pragma_expected_identifier, diag::warn_pragma_expected_init_seg, diag::warn_pragma_expected_integer, diag::warn_pragma_expected_lparen, diag::warn_pragma_expected_non_wide_string, diag::warn_pragma_expected_predicate, diag::warn_pragma_expected_punc, diag::warn_pragma_expected_rparen, diag::warn_pragma_expected_section_label_or_name, diag::warn_pragma_expected_section_name, diag::warn_pragma_expected_section_push_pop_or_name, diag::warn_pragma_expected_string, diag::warn_pragma_extra_tokens_at_eol, diag::warn_pragma_force_cuda_host_device_bad_arg, diag::warn_pragma_fp_ignored, diag::warn_pragma_init_seg_unsupported_target, diag::warn_pragma_invalid_action, diag::warn_pragma_invalid_argument, diag::warn_pragma_invalid_specific_action, diag::warn_pragma_missing_argument, diag::warn_pragma_ms_fenv_access, diag::warn_pragma_ms_struct, diag::warn_pragma_options_align_reset_failed, diag::warn_pragma_options_expected_align, diag::warn_pragma_pack_invalid_alignment, diag::warn_pragma_pack_malformed, diag::warn_pragma_pop_failed, diag::warn_pragma_pop_macro_no_push, diag::warn_pragma_unknown_extension, diag::warn_pragma_unsupported_action, diag::warn_pragma_unsupported_extension, diag::warn_pragma_unused_expected_var, diag::warn_pragma_unused_expected_var_arg, diag::warn_pragma_unused_undeclared_var, diag::warn_stdc_unknown_rounding_mode, -1,
  /* DiagArray383 */ diag::warn_arc_lifetime_result_type, diag::warn_block_literal_qualifiers_on_omitted_return_type, diag::warn_qual_return_type, diag::warn_typecheck_function_qualifiers_ignored, -1,
  /* DiagArray384 */ diag::warn_typecheck_reference_qualifiers, -1,
  /* DiagArray386 */ diag::warn_auto_implicit_atomic_property, diag::warn_implicit_atomic_property, -1,
  /* DiagArray387 */ diag::warn_impcast_integer_float_precision_constant, -1,
  /* DiagArray388 */ diag::warn_impcast_floating_point_to_bool, -1,
  /* DiagArray389 */ diag::ext_implicit_exception_spec_mismatch, -1,
  /* DiagArray390 */ diag::warn_unannotated_fallthrough, -1,
  /* DiagArray391 */ diag::warn_unannotated_fallthrough_per_function, -1,
  /* DiagArray392 */ diag::warn_impcast_fixed_point_range, -1,
  /* DiagArray393 */ diag::warn_impcast_float_precision, diag::warn_impcast_float_result_precision, -1,
  /* DiagArray394 */ diag::ext_implicit_function_decl_c99, diag::ext_implicit_lib_function_decl, diag::ext_implicit_lib_function_decl_c99, diag::warn_builtin_unknown, diag::warn_implicit_function_decl, -1,
  /* DiagArray395 */ diag::ext_missing_type_specifier, diag::ext_param_not_declared, diag::warn_missing_type_specifier, -1,
  /* DiagArray396 */ diag::warn_impcast_high_order_zero_bits, diag::warn_impcast_integer_precision, -1,
  /* DiagArray397 */ diag::warn_impcast_integer_float_precision, -1,
  /* DiagArray398 */ diag::warn_implicitly_retains_self, -1,
  /* DiagArray399 */ diag::ext_integer_literal_too_large_for_signed, -1,
  /* DiagArray401 */ diag::ext_pp_import_directive, -1,
  /* DiagArray402 */ diag::warn_inaccessible_base_class, -1,
  /* DiagArray403 */ diag::pp_include_next_absolute_path, -1,
  /* DiagArray404 */ diag::pp_include_next_in_primary, -1,
  /* DiagArray405 */ diag::warn_deep_exception_specs_differ, diag::warn_incompatible_exception_specs, -1,
  /* DiagArray406 */ diag::ext_typecheck_convert_incompatible_function_pointer, -1,
  /* DiagArray407 */ diag::warn_typecheck_convert_incompatible_function_pointer_strict, -1,
  /* DiagArray408 */ diag::warn_redecl_library_builtin, -1,
  /* DiagArray409 */ diag::warn_cxx_ms_struct, diag::warn_npot_ms_struct, -1,
  /* DiagArray410 */ diag::ext_typecheck_convert_incompatible_pointer, -1,
  /* DiagArray411 */ diag::ext_nested_pointer_qualifier_mismatch, diag::ext_typecheck_convert_discards_qualifiers, diag::warn_bad_cxx_cast_nested_pointer_addr_space, -1,
  /* DiagArray412 */ diag::warn_property_types_are_incompatible, -1,
  /* DiagArray413 */ diag::warn_incompatible_sysroot, -1,
  /* DiagArray414 */ diag::warn_mmap_incomplete_framework_module_declaration, -1,
  /* DiagArray415 */ diag::warn_undef_method_impl, -1,
  /* DiagArray417 */ diag::warn_implicit_decl_no_jmp_buf, -1,
  /* DiagArray418 */ diag::warn_missing_submodule, diag::warn_mmap_umbrella_dir_not_found, diag::warn_uncovered_module_header, -1,
  /* DiagArray419 */ diag::warn_redeclaration_without_attribute_prev_attribute_ignored, diag::warn_redeclaration_without_import_attribute, -1,
  /* DiagArray420 */ diag::warn_inconsistent_destructor_marked_not_override_overriding, -1,
  /* DiagArray421 */ diag::warn_inconsistent_function_marked_not_override_overriding, -1,
  /* DiagArray422 */ diag::ext_increment_bool, -1,
  /* DiagArray423 */ diag::warn_infinite_recursive_function, -1,
  /* DiagArray425 */ diag::ext_initializer_overrides, diag::warn_initializer_overrides, -1,
  /* DiagArray426 */ diag::ext_out_of_line_qualified_id_type_names_constructor, -1,
  /* DiagArray428 */ diag::warn_fe_inline_asm, -1,
  /* DiagArray429 */ diag::warn_inline_namespace_reopened_noninline, -1,
  /* DiagArray430 */ diag::ext_operator_new_delete_declared_inline, -1,
  /* DiagArray431 */ diag::warn_explicit_instantiation_after_specialization, -1,
  /* DiagArray432 */ diag::ext_typecheck_convert_int_pointer, diag::ext_typecheck_convert_pointer_int, -1,
  /* DiagArray434 */ diag::warn_enum_constant_in_bool_context, diag::warn_left_shift_in_bool_context, -1,
  /* DiagArray435 */ diag::warn_int_to_pointer_cast, -1,
  /* DiagArray436 */ diag::warn_int_to_void_pointer_cast, -1,
  /* DiagArray437 */ diag::warn_integer_constant_overflow, -1,
  /* DiagArray438 */ diag::warn_anyx86_interrupt_regsave, -1,
  /* DiagArray439 */ diag::warn_drv_missing_plugin_arg, diag::warn_drv_missing_plugin_name, diag::warn_drv_msp430_hwmult_mismatch, diag::warn_drv_msp430_hwmult_no_device, diag::warn_drv_msp430_hwmult_unsupported, diag::warn_drv_object_size_disabled_O0, diag::warn_drv_optimization_value, diag::warn_fe_backend_invalid_feature_flag, diag::warn_target_unsupported_extension, -1,
  /* DiagArray440 */ diag::ext_constexpr_function_never_constant_expr, -1,
  /* DiagArray441 */ diag::warn_iboutlet_object_type, diag::warn_iboutletcollection_property_assign, -1,
  /* DiagArray442 */ diag::warn_invalid_initializer_from_system_header, -1,
  /* DiagArray443 */ diag::warn_invalid_ios_deployment_target, -1,
  /* DiagArray444 */ diag::warn_attribute_no_builtin_invalid_builtin_name, -1,
  /* DiagArray445 */ diag::warn_falloff_noreturn_function, diag::warn_noreturn_function_has_return_expr, -1,
  /* DiagArray446 */ diag::ext_offsetof_non_pod_type, diag::ext_offsetof_non_standardlayout_type, -1,
  /* DiagArray447 */ diag::warn_drv_ps_sdk_dir, diag::warn_drv_unable_to_find_directory_expected, -1,
  /* DiagArray448 */ diag::ext_partial_spec_not_more_specialized_than_primary, -1,
  /* DiagArray450 */ diag::ext_empty_character, diag::ext_unterminated_char_or_string, -1,
  /* DiagArray451 */ diag::warn_bad_character_encoding, diag::warn_bad_string_encoding, -1,
  /* DiagArray452 */ diag::ext_pp_bad_paste_ms, -1,
  /* DiagArray453 */ diag::warn_invalid_utf8_in_comment, -1,
  /* DiagArray454 */ diag::warn_jump_out_of_seh_finally, -1,
  /* DiagArray455 */ diag::ext_keyword_as_ident, -1,
  /* DiagArray456 */ diag::warn_pp_macro_hides_keyword, -1,
  /* DiagArray457 */ diag::ext_param_promoted_not_compatible_with_prototype, -1,
  /* DiagArray458 */ diag::ext_token_used, -1,
  /* DiagArray459 */ diag::warn_parameter_size, diag::warn_return_value_size, -1,
  /* DiagArray461 */ diag::warn_fe_linking_module, -1,
  /* DiagArray462 */ diag::warn_impcast_literal_float_to_integer, diag::warn_impcast_literal_float_to_integer_out_of_range, -1,
  /* DiagArray463 */ diag::warn_float_compare_literal, diag::warn_float_overflow, diag::warn_float_underflow, -1,
  /* DiagArray464 */ diag::ext_template_arg_local_type, -1,
  /* DiagArray465 */ diag::warn_logical_not_on_lhs_of_check, -1,
  /* DiagArray466 */ diag::warn_logical_and_in_logical_or, -1,
  /* DiagArray467 */ diag::ext_c99_longlong, -1,
  /* DiagArray469 */ diag::ext_pp_macro_redef, -1,
  /* DiagArray470 */ diag::ext_main_used, diag::ext_noreturn_main, diag::ext_variadic_main, diag::warn_main_one_arg, diag::warn_main_redefined, diag::warn_main_returns_bool_literal, diag::warn_static_main, -1,
  /* DiagArray471 */ diag::ext_main_returns_nonint, -1,
  /* DiagArray472 */ diag::warn_has_warning_invalid_option, -1,
  /* DiagArray473 */ diag::ext_many_braces_around_init, -1,
  /* DiagArray474 */ diag::ext_mathematical_notation, -1,
  /* DiagArray475 */ diag::warn_max_tokens, diag::warn_max_tokens_total, -1,
  /* DiagArray476 */ diag::warn_max_unsigned_zero, -1,
  /* DiagArray477 */ diag::warn_suspicious_sizeof_memset, -1,
  /* DiagArray478 */ diag::warn_memsize_comparison, -1,
  /* DiagArray479 */ diag::warn_non_contravariant_param_types, diag::warn_non_covariant_ret_types, -1,
  /* DiagArray481 */ diag::ext_ms_abstract_keyword, -1,
  /* DiagArray482 */ diag::ext_anonymous_record_with_type, diag::ext_ms_anonymous_record, -1,
  /* DiagArray483 */ diag::ext_ms_cast_fn_obj, diag::ext_ms_impcast_fn_obj, -1,
  /* DiagArray484 */ diag::ext_charize_microsoft, -1,
  /* DiagArray485 */ diag::ext_comment_paste_microsoft, -1,
  /* DiagArray486 */ diag::ext_default_init_const, -1,
  /* DiagArray487 */ diag::ext_pp_operator_used_as_macro_name, -1,
  /* DiagArray488 */ diag::ext_param_default_argument_redefinition, -1,
  /* DiagArray489 */ diag::warn_attribute_section_drectve, -1,
  /* DiagArray490 */ diag::ext_ctrl_z_eof_microsoft, -1,
  /* DiagArray491 */ diag::ext_ms_forward_ref_enum, -1,
  /* DiagArray492 */ diag::ext_enumerator_too_large, -1,
  /* DiagArray493 */ diag::ext_ellipsis_exception_spec, diag::ext_incomplete_in_exception_spec, diag::ext_mismatched_exception_spec, diag::ext_mismatched_exception_spec_explicit_instantiation, diag::ext_override_exception_spec, -1,
  /* DiagArray494 */ diag::warn_microsoft_dependent_exists, -1,
  /* DiagArray495 */ diag::ext_ms_explicit_constructor_call, -1,
  /* DiagArray496 */ diag::warn_member_extra_qualification, -1,
  /* DiagArray497 */ diag::ext_ms_c_enum_fixed_underlying_type, -1,
  /* DiagArray498 */ diag::ext_flexible_array_empty_aggregate_ms, diag::ext_flexible_array_union_ms, -1,
  /* DiagArray499 */ diag::ext_goto_into_protected_scope, -1,
  /* DiagArray500 */ diag::ext_ms_ambiguous_direct_base, -1,
  /* DiagArray501 */ diag::ext_pp_include_search_ms, -1,
  /* DiagArray502 */ diag::ext_mutable_reference, -1,
  /* DiagArray503 */ diag::ext_pure_function_definition, -1,
  /* DiagArray504 */ diag::ext_static_non_static, -1,
  /* DiagArray505 */ diag::ext_ms_sealed_keyword, -1,
  /* DiagArray506 */ diag::ext_ms_static_assert, -1,
  /* DiagArray507 */ diag::ext_explicit_instantiation_duplicate, diag::ext_found_in_dependent_base, diag::ext_found_later_in_class, diag::ext_ms_delayed_template_argument, diag::ext_ms_deref_template_argument, diag::ext_ms_template_spec_redecl_out_of_scope, diag::ext_ms_template_type_arg_missing_typename, diag::ext_static_out_of_line, diag::ext_undeclared_unqual_id_with_dependent_base, diag::ext_unqualified_base_class, -1,
  /* DiagArray508 */ diag::ext_template_param_shadow, -1,
  /* DiagArray509 */ diag::ext_union_member_of_reference_type, -1,
  /* DiagArray510 */ diag::ext_friend_tag_redecl_outside_namespace, -1,
  /* DiagArray511 */ diag::ext_ms_using_declaration_inaccessible, -1,
  /* DiagArray512 */ diag::ext_pseudo_dtor_on_void, -1,
  /* DiagArray513 */ diag::warn_profile_data_misexpect, -1,
  /* DiagArray514 */ diag::warn_misleading_indentation, -1,
  /* DiagArray515 */ diag::warn_mismatched_delete_new, -1,
  /* DiagArray516 */ diag::warn_conflicting_param_types, -1,
  /* DiagArray517 */ diag::warn_conflicting_ret_types, -1,
  /* DiagArray518 */ diag::warn_struct_class_previous_tag_mismatch, diag::warn_struct_class_tag_mismatch, -1,
  /* DiagArray519 */ diag::warn_missing_braces, -1,
  /* DiagArray520 */ diag::ext_constinit_missing, -1,
  /* DiagArray521 */ diag::ext_no_declarators, diag::ext_standalone_specifier, diag::ext_typedef_without_a_name, diag::warn_standalone_specifier, -1,
  /* DiagArray522 */ diag::ext_missing_exception_specification, -1,
  /* DiagArray523 */ diag::warn_missing_field_initializers, -1,
  /* DiagArray526 */ diag::warn_missing_method_return_type, -1,
  /* DiagArray527 */ diag::warn_overriding_method_missing_noescape, -1,
  /* DiagArray528 */ diag::warn_suggest_noreturn_block, diag::warn_suggest_noreturn_function, -1,
  /* DiagArray529 */ diag::warn_cconv_knr, -1,
  /* DiagArray530 */ diag::warn_missing_prototype, -1,
  /* DiagArray531 */ diag::warn_missing_selector_name, -1,
  /* DiagArray532 */ diag::warn_missing_sysroot, -1,
  /* DiagArray533 */ diag::warn_missing_variable_declarations, -1,
  /* DiagArray534 */ diag::warn_assume_attribute_string_unknown_suggested, -1,
  /* DiagArray535 */ diag::remark_module_build, diag::remark_module_build_done, diag::remark_module_lock_failure, diag::remark_module_lock_timeout, -1,
  /* DiagArray536 */ diag::warn_module_conflict, diag::warn_module_system_bit_conflict, -1,
  /* DiagArray537 */ diag::warn_module_config_mismatch, -1,
  /* DiagArray538 */ diag::warn_duplicate_module_file_extension, -1,
  /* DiagArray539 */ diag::remark_module_import, -1,
  /* DiagArray540 */ diag::ext_module_import_in_extern_c, -1,
  /* DiagArray541 */ diag::remark_pp_include_directive_modular_translation, -1,
  /* DiagArray542 */ diag::remark_module_lock, -1,
  /* DiagArray543 */ diag::ext_equivalent_internal_linkage_decl_in_modules, -1,
  /* DiagArray544 */ diag::ext_module_import_not_at_top_level_noop, -1,
  /* DiagArray548 */ diag::warn_drv_msvc_not_found, -1,
  /* DiagArray549 */ diag::warn_multichar_character_literal, -1,
  /* DiagArray550 */ diag::warn_vbase_moved_multiple_times, -1,
  /* DiagArray552 */ diag::ext_anonymous_record_with_anonymous_type, -1,
  /* DiagArray554 */ diag::warn_operator_new_returns_null, -1,
  /* DiagArray555 */ diag::ext_no_newline_eof, diag::warn_no_newline_eof, -1,
  /* DiagArray556 */ diag::warn_dereference_of_noderef_type, diag::warn_dereference_of_noderef_type_no_decl, diag::warn_noderef_to_dereferenceable_pointer, -1,
  /* DiagArray558 */ diag::ext_non_c_like_anon_struct_in_typedef, -1,
  /* DiagArray560 */ diag::warn_non_literal_null_pointer, -1,
  /* DiagArray561 */ diag::warn_non_modular_include_in_framework_module, -1,
  /* DiagArray562 */ diag::warn_non_modular_include_in_module, -1,
  /* DiagArray563 */ diag::warn_cannot_pass_non_pod_arg_to_vararg, diag::warn_non_pod_vararg_with_format_string, diag::warn_second_parameter_to_va_arg_not_pod, diag::warn_second_parameter_to_va_arg_ownership_qualified, -1,
  /* DiagArray564 */ diag::warn_alignment_not_power_of_two, -1,
  /* DiagArray565 */ diag::warn_non_virtual_dtor, -1,
  /* DiagArray566 */ diag::warn_null_arg, diag::warn_null_ret, -1,
  /* DiagArray568 */ diag::pp_nonportable_path, -1,
  /* DiagArray569 */ diag::pp_nonportable_system_path, -1,
  /* DiagArray570 */ diag::warn_neon_vector_initializer_non_portable, -1,
  /* DiagArray571 */ diag::warn_cstruct_memaccess, -1,
  /* DiagArray572 */ diag::warn_nsconsumed_attribute_mismatch, -1,
  /* DiagArray573 */ diag::warn_nsreturns_retained_attribute_mismatch, -1,
  /* DiagArray574 */ diag::warn_null_in_arithmetic_operation, diag::warn_null_in_comparison_operation, -1,
  /* DiagArray575 */ diag::null_in_char_or_string, diag::null_in_file, -1,
  /* DiagArray576 */ diag::warn_impcast_null_pointer_to_integer, -1,
  /* DiagArray577 */ diag::warn_binding_null_to_reference, diag::warn_indirection_through_null, -1,
  /* DiagArray578 */ diag::warn_pointer_arith_null_ptr, -1,
  /* DiagArray579 */ diag::warn_pointer_sub_null_ptr, -1,
  /* DiagArray580 */ diag::warn_conflicting_nullability_attr_overriding_param_types, diag::warn_conflicting_nullability_attr_overriding_ret_types, diag::warn_mismatched_nullability_attr, diag::warn_null_resettable_setter, diag::warn_nullability_duplicate, -1,
  /* DiagArray581 */ diag::warn_nullability_missing, -1,
  /* DiagArray582 */ diag::warn_nullability_missing_array, -1,
  /* DiagArray583 */ diag::warn_nullability_declspec, -1,
  /* DiagArray584 */ diag::ext_nullability, -1,
  /* DiagArray585 */ diag::warn_nullability_inferred_on_nested_type, -1,
  /* DiagArray586 */ diag::warn_nullability_lost, -1,
  /* DiagArray587 */ diag::warn_autosynthesis_property_ivar_match, -1,
  /* DiagArray588 */ diag::warn_impcast_constant_value_to_objc_bool, -1,
  /* DiagArray589 */ diag::warn_objc_boxing_invalid_utf8_string, -1,
  /* DiagArray590 */ diag::warn_objc_circular_container, -1,
  /* DiagArray592 */ diag::warn_objc_designated_init_missing_super_call, diag::warn_objc_designated_init_non_designated_init_call, diag::warn_objc_designated_init_non_super_designated_init_call, diag::warn_objc_implementation_missing_designated_init_override, diag::warn_objc_secondary_init_missing_init_call, diag::warn_objc_secondary_init_super_init_call, -1,
  /* DiagArray593 */ diag::warn_nsdictionary_duplicate_key, -1,
  /* DiagArray594 */ diag::warn_superclass_variable_sized_type_not_at_end, diag::warn_variable_sized_ivar_visibility, -1,
  /* DiagArray595 */ diag::warn_forward_class_redefinition, -1,
  /* DiagArray596 */ diag::warn_ivars_in_interface, -1,
  /* DiagArray597 */ diag::warn_objc_literal_comparison, -1,
  /* DiagArray598 */ diag::warn_impcast_objective_c_literal_to_bool, diag::warn_objc_collection_literal_element, -1,
  /* DiagArray599 */ diag::warn_pp_objc_macro_redef_ignored, -1,
  /* DiagArray600 */ diag::warn_messaging_unqualified_id, -1,
  /* DiagArray601 */ diag::warn_class_method_not_found, diag::warn_class_method_not_found_with_typo, diag::warn_inst_method_not_found, diag::warn_instance_method_not_found_with_typo, diag::warn_instance_method_on_class_found, diag::warn_root_inst_method_not_found, -1,
  /* DiagArray602 */ diag::warn_missing_explicit_synthesis, -1,
  /* DiagArray603 */ diag::warn_objc_missing_super_call, -1,
  /* DiagArray604 */ diag::warn_multiple_method_decl, -1,
  /* DiagArray605 */ diag::warn_objc_property_retain_of_block, -1,
  /* DiagArray606 */ diag::warn_objc_pointer_cxx_catch_fragile, -1,
  /* DiagArray607 */ diag::warn_objc_property_assign_on_object, -1,
  /* DiagArray608 */ diag::warn_impl_required_for_class_property, diag::warn_impl_required_in_category_for_class_property, diag::warn_setter_getter_impl_required, diag::warn_setter_getter_impl_required_in_category, -1,
  /* DiagArray609 */ diag::warn_property_implicitly_mismatched, -1,
  /* DiagArray610 */ diag::warn_cocoa_naming_owned_rule, -1,
  /* DiagArray611 */ diag::warn_objc_property_default_assign_on_object, diag::warn_objc_property_no_assignment_attribute, -1,
  /* DiagArray612 */ diag::warn_autosynthesis_property_in_superclass, diag::warn_no_autosynthesis_property, diag::warn_no_autosynthesis_shared_ivar_property, -1,
  /* DiagArray613 */ diag::warn_category_method_impl_match, -1,
  /* DiagArray614 */ diag::warn_auto_synthesizing_protocol_property, -1,
  /* DiagArray615 */ diag::warn_objc_redundant_qualified_class_type, -1,
  /* DiagArray616 */ diag::warn_objc_readonly_property_has_setter, -1,
  /* DiagArray618 */ diag::warn_objc_redundant_literal_use, -1,
  /* DiagArray619 */ diag::warn_objc_root_class_missing, -1,
  /* DiagArray621 */ diag::warn_impcast_float_to_objc_signed_char_bool, -1,
  /* DiagArray622 */ diag::warn_impcast_int_to_objc_signed_char_bool, -1,
  /* DiagArray623 */ diag::warn_objc_string_literal_comparison, -1,
  /* DiagArray624 */ diag::warn_concatenated_nsarray_literal, -1,
  /* DiagArray625 */ diag::warn_objc_unsafe_perform_selector, -1,
  /* DiagArray626 */ diag::warn_odr_different_num_template_parameters, diag::warn_odr_different_template_parameter_kind, diag::warn_odr_field_type_inconsistent, diag::warn_odr_function_type_inconsistent, diag::warn_odr_ivar_type_inconsistent, diag::warn_odr_non_type_parameter_type_inconsistent, diag::warn_odr_objc_method_num_params_inconsistent, diag::warn_odr_objc_method_param_type_inconsistent, diag::warn_odr_objc_method_result_type_inconsistent, diag::warn_odr_objc_method_variadic_inconsistent, diag::warn_odr_objc_property_impl_kind_inconsistent, diag::warn_odr_objc_property_type_inconsistent, diag::warn_odr_objc_superclass_inconsistent, diag::warn_odr_objc_synthesize_ivar_inconsistent, diag::warn_odr_parameter_pack_non_pack, diag::warn_odr_tag_type_inconsistent, diag::warn_odr_variable_multiple_def, diag::warn_odr_variable_type_inconsistent, -1,
  /* DiagArray627 */ diag::warn_old_style_cast, -1,
  /* DiagArray629 */ diag::ext_opencl_ext_vector_type_rgba_selector, -1,
  /* DiagArray631 */ diag::ext_omp_attributes, -1,
  /* DiagArray632 */ diag::warn_omp_alignment_not_power_of_two, diag::warn_omp_allocate_thread_on_task_target_directive, diag::warn_omp_ctx_incompatible_property_for_selector, diag::warn_omp_ctx_incompatible_score_for_property, diag::warn_omp_ctx_incompatible_selector_for_set, diag::warn_omp_ctx_selector_without_properties, diag::warn_omp_declare_variant_ctx_mutiple_use, diag::warn_omp_declare_variant_ctx_not_a_property, diag::warn_omp_declare_variant_ctx_not_a_selector, diag::warn_omp_declare_variant_ctx_not_a_set, diag::warn_omp_declare_variant_expected, diag::warn_omp_declare_variant_string_literal_or_identifier, diag::warn_omp_linear_step_zero, diag::warn_omp_more_one_device_type_clause, diag::warn_omp_more_one_interop_type, diag::warn_omp_more_one_omp_all_memory, diag::warn_omp_unknown_assumption_clause_missing_id, diag::warn_omp_unknown_assumption_clause_without_args, diag::warn_omp_used_different_allocator, -1,
  /* DiagArray633 */ diag::ext_omp_loop_not_canonical_init, diag::warn_omp_loop_64_bit_var, -1,
  /* DiagArray634 */ diag::warn_omp_non_trivial_type_mapped, -1,
  /* DiagArray635 */ diag::warn_drv_omp_offload_target_duplicate, diag::warn_omp_declare_target_after_first_use, diag::warn_omp_not_in_target_context, -1,
  /* DiagArray636 */ diag::warn_drv_fine_grained_bitfield_accesses_ignored, diag::warn_drv_fjmc_for_elf_only, diag::warn_drv_jmc_requires_debuginfo, diag::warn_drv_moutline_atomics_unsupported_opt, diag::warn_drv_moutline_unsupported_opt, diag::warn_drv_needs_hvx, diag::warn_drv_ps_force_pic, diag::warn_drv_unsupported_diag_option_for_flang, diag::warn_drv_unsupported_longcalls, diag::warn_drv_unsupported_option_for_flang, diag::warn_drv_unsupported_option_for_offload_arch_req_feature, diag::warn_drv_unsupported_option_for_target, diag::warn_drv_unsupported_pic_with_mabicalls, diag::warn_drv_unsupported_sdata, diag::warn_target_override_arm64ec, -1,
  /* DiagArray637 */ diag::ext_typecheck_ordered_comparison_of_function_pointers, diag::warn_typecheck_ordered_comparison_of_function_pointers, -1,
  /* DiagArray638 */ diag::ext_out_of_line_declaration, -1,
  /* DiagArray639 */ diag::ext_use_out_of_scope_declaration, -1,
  /* DiagArray640 */ diag::warn_overaligned_type, -1,
  /* DiagArray642 */ diag::ext_string_too_long, -1,
  /* DiagArray643 */ diag::warn_overloaded_shift_in_comparison, -1,
  /* DiagArray644 */ diag::warn_overloaded_virtual, -1,
  /* DiagArray646 */ diag::warn_fe_override_module, -1,
  /* DiagArray647 */ diag::warn_conflicting_overriding_param_modifiers, diag::warn_conflicting_overriding_param_types, diag::warn_conflicting_overriding_ret_type_modifiers, diag::warn_conflicting_overriding_ret_types, diag::warn_conflicting_overriding_variadic, diag::warn_non_contravariant_overriding_param_types, diag::warn_non_covariant_overriding_ret_types, -1,
  /* DiagArray648 */ diag::warn_drv_overriding_flag_option, -1,
  /* DiagArray649 */ diag::warn_unnecessary_packed, -1,
  /* DiagArray650 */ diag::warn_unpacked_field, -1,
  /* DiagArray651 */ diag::warn_padded_struct_anon_field, diag::warn_padded_struct_field, diag::warn_padded_struct_size, -1,
  /* DiagArray652 */ diag::warn_condition_is_assignment, diag::warn_precedence_bitwise_rel, diag::warn_precedence_conditional, -1,
  /* DiagArray653 */ diag::warn_equality_with_extra_parens, -1,
  /* DiagArray655 */ diag::remark_fe_backend_optimization_remark, -1,
  /* DiagArray656 */ diag::remark_fe_backend_optimization_remark_analysis, diag::remark_fe_backend_optimization_remark_analysis_aliasing, diag::remark_fe_backend_optimization_remark_analysis_fpcommute, -1,
  /* DiagArray657 */ diag::warn_fe_backend_optimization_failure, -1,
  /* DiagArray658 */ diag::remark_fe_backend_optimization_remark_missed, -1,
  /* DiagArray659 */ diag::warn_module_uses_date_time, -1,
  /* DiagArray660 */ diag::ext_aggregate_init_not_constant, diag::ext_anonymous_record_with_type, diag::ext_anonymous_struct_union_qualified, diag::ext_array_size_conversion, diag::ext_auto_new_list_init, diag::ext_c99_array_usage, diag::ext_c99_compound_literal, diag::ext_c99_feature, diag::ext_c99_flexible_array_member, diag::ext_c99_variable_decl_in_for_loop, diag::ext_c_empty_initializer, diag::ext_c_nullptr, diag::ext_cast_fn_obj, diag::ext_clang_diagnose_if, diag::ext_clang_enable_if, diag::ext_cxx11_enum_fixed_underlying_type, diag::ext_designated_init, diag::ext_dtor_name_ambiguous, diag::ext_dtor_named_in_wrong_scope, diag::ext_duplicate_declspec, diag::ext_ellipsis_exception_spec, diag::ext_empty_fnmacro_arg, diag::ext_enum_value_not_int, diag::ext_enumerator_list_comma_c, diag::ext_enumerator_list_comma_cxx, diag::ext_explicit_instantiation_without_qualified_id, diag::ext_expr_not_ice, diag::ext_extern_template, diag::ext_extra_semi, diag::ext_forward_ref_enum, diag::ext_freestanding_complex, diag::ext_gnu_array_range, diag::ext_hex_constant_invalid, diag::ext_hex_literal_invalid, diag::ext_ident_list_in_param, diag::ext_in_class_initializer_non_constant, diag::ext_integer_complement_complex, diag::ext_integer_increment_complex, diag::ext_internal_in_extern_inline_quiet, diag::ext_line_comment, diag::ext_main_used, diag::ext_mixed_decls_code, diag::ext_multi_line_line_comment, diag::ext_named_variadic_macro, diag::ext_no_newline_eof, diag::ext_nonstandard_escape, diag::ext_ns_enum_attribute, diag::ext_opencl_double_without_pragma, diag::ext_pointer_to_const_ref_member_on_rvalue, diag::ext_pp_bad_vaargs_use, diag::ext_pp_comma_expr, diag::ext_pp_ident_directive, diag::ext_pp_line_too_big, diag::ext_pp_opencl_variadic_macros, diag::ext_pp_warning_directive, diag::ext_return_has_void_expr, diag::ext_rvalue_to_reference_access_ctor, diag::ext_rvalue_to_reference_temp_copy_no_viable, diag::ext_sizeof_alignof_function_type, diag::ext_sizeof_alignof_void_type, diag::ext_subscript_non_lvalue, diag::ext_thread_before, diag::ext_typecheck_addrof_void, diag::ext_typecheck_cast_nonscalar, diag::ext_typecheck_comparison_of_fptr_to_void, diag::ext_typecheck_cond_one_void, diag::ext_typecheck_convert_pointer_void_func, diag::ext_typecheck_ordered_comparison_of_pointer_and_zero, diag::ext_variadic_macro, diag::warn_defined_in_function_type_macro, diag::warn_format_conversion_argument_type_mismatch_pedantic, diag::warn_kern_is_method, diag::warn_strict_prototypes, -1,
  /* DiagArray661 */ diag::warn_opencl_unsupported_core_feature, diag::warn_pragma_extension_is_core, -1,
  /* DiagArray663 */ diag::warn_pessimizing_move_on_initialization, diag::warn_pessimizing_move_on_return, -1,
  /* DiagArray664 */ diag::ext_sizeof_alignof_function_type, diag::ext_sizeof_alignof_void_type, diag::warn_sub_ptr_zero_size_types, -1,
  /* DiagArray665 */ diag::warn_cast_nonnull_to_bool, diag::warn_impcast_pointer_to_bool, -1,
  /* DiagArray666 */ diag::warn_pointer_compare, -1,
  /* DiagArray667 */ diag::ext_typecheck_comparison_of_pointer_integer, -1,
  /* DiagArray668 */ diag::ext_typecheck_convert_incompatible_pointer_sign, -1,
  /* DiagArray669 */ diag::warn_pointer_to_enum_cast, -1,
  /* DiagArray670 */ diag::warn_pointer_to_int_cast, -1,
  /* DiagArray671 */ diag::ext_typecheck_cond_incompatible_pointers, -1,
  /* DiagArray672 */ diag::warn_poison_system_directories, -1,
  /* DiagArray673 */ diag::warn_potentially_direct_selector_expression, -1,
  /* DiagArray674 */ diag::warn_side_effects_typeid, -1,
  /* DiagArray675 */ diag::warn_pragma_attribute_unused, -1,
  /* DiagArray676 */ diag::pp_pragma_once_in_main_file, -1,
  /* DiagArray677 */ diag::warn_pragma_pack_modified_after_include, diag::warn_pragma_pack_no_pop_eof, -1,
  /* DiagArray678 */ diag::warn_pragma_pack_non_default_at_include, -1,
  /* DiagArray679 */ diag::pp_pragma_sysheader_in_main_file, -1,
  /* DiagArray680 */ diag::warn_no_support_for_eval_method_source_on_m32, diag::warn_redefine_extname_not_applied, -1,
  /* DiagArray681 */ diag::warn_cxx11_compat_constexpr_body_invalid_stmt, diag::warn_cxx11_compat_constexpr_body_multiple_return, diag::warn_cxx11_compat_constexpr_body_no_return, diag::warn_cxx11_compat_constexpr_local_var, diag::warn_cxx11_compat_constexpr_type_definition, diag::warn_cxx11_compat_decltype_auto_type_specifier, diag::warn_cxx11_compat_deduced_return_type, diag::warn_cxx11_compat_digit_separator, diag::warn_cxx11_compat_generic_lambda, diag::warn_cxx11_compat_init_capture, diag::warn_cxx11_compat_variable_template, -1,
  /* DiagArray683 */ diag::warn_cxx14_compat_class_template_argument_deduction, diag::warn_cxx14_compat_constexpr_if, diag::warn_cxx14_compat_constexpr_on_lambda, diag::warn_cxx14_compat_decomp_decl, diag::warn_cxx14_compat_fold_expression, diag::warn_cxx14_compat_init_statement, diag::warn_cxx14_compat_inline_variable, diag::warn_cxx14_compat_nested_namespace_definition, diag::warn_cxx14_compat_star_this_lambda_capture, diag::warn_cxx14_compat_static_assert_no_message, diag::warn_cxx14_compat_template_nontype_parm_auto_type, diag::warn_cxx14_compat_template_template_param_typename, diag::warn_cxx14_compat_u8_character_literal, diag::warn_cxx14_compat_using_attribute_ns, diag::warn_cxx17_compat_multi_using_declaration, diag::warn_cxx17_compat_using_declaration_pack, diag::warn_for_range_begin_end_types_differ, -1,
  /* DiagArray684 */ diag::warn_cxx14_compat_ns_enum_attribute, diag::warn_cxx17_hex_literal, -1,
  /* DiagArray685 */ diag::warn_cxx17_compat_adl_only_template_id, diag::warn_cxx17_compat_bitfield_member_init, diag::warn_cxx17_compat_capture_binding, diag::warn_cxx17_compat_constexpr_body_invalid_stmt, diag::warn_cxx17_compat_constexpr_ctor_missing_init, diag::warn_cxx17_compat_constexpr_function_try_block, diag::warn_cxx17_compat_constexpr_local_var_no_init, diag::warn_cxx17_compat_constexpr_union_ctor_no_init, diag::warn_cxx17_compat_constexpr_virtual, diag::warn_cxx17_compat_decomp_decl_spec, diag::warn_cxx17_compat_defaulted_comparison, diag::warn_cxx17_compat_defaulted_method_type_mismatch, diag::warn_cxx17_compat_equals_this_lambda_capture, diag::warn_cxx17_compat_explicit_bool, diag::warn_cxx17_compat_for_range_init_stmt, diag::warn_cxx17_compat_init_capture_pack, diag::warn_cxx17_compat_inline_nested_namespace_definition, diag::warn_cxx17_compat_lambda_def_ctor_assign, diag::warn_cxx17_compat_lambda_template_parameter_list, diag::warn_cxx17_compat_missing_varargs_arg, diag::warn_cxx17_compat_spaceship, diag::warn_cxx17_compat_template_nontype_parm_type, diag::warn_cxx17_compat_unicode_type, diag::warn_cxx17_compat_using_decl_class_member_enumerator, diag::warn_cxx17_compat_using_decl_non_member_enumerator, diag::warn_cxx17_compat_using_decl_scoped_enumerator, diag::warn_cxx17_compat_using_enum_declaration, -1,
  /* DiagArray686 */ diag::warn_cxx17_compat_designated_init, diag::warn_cxx17_compat_pointer_to_const_ref_member_on_rvalue, -1,
  /* DiagArray687 */ diag::ext_subscript_overload, diag::warn_cxx20_alias_in_init_statement, diag::warn_cxx20_compat_auto_expr, diag::warn_cxx20_compat_consteval_if, diag::warn_cxx20_compat_constexpr_body_invalid_stmt, diag::warn_cxx20_compat_constexpr_var, diag::warn_cxx20_compat_decl_attrs_on_lambda, diag::warn_cxx20_compat_label_end_of_compound_statement, diag::warn_cxx20_compat_operator_overload_static, diag::warn_cxx20_compat_size_t_suffix, diag::warn_cxx20_compat_static_lambda, diag::warn_cxx23_compat_defaulted_comparison_constexpr_mismatch, diag::warn_cxx23_compat_pp_directive, diag::warn_cxx23_compat_warning_directive, diag::warn_cxx23_delimited_escape_sequence, -1,
  /* DiagArray689 */ diag::warn_c2x_compat_bitint_suffix, diag::warn_c2x_compat_digit_separator, diag::warn_c2x_compat_empty_initializer, diag::warn_c2x_compat_keyword, diag::warn_c2x_compat_label_end_of_compound_statement, diag::warn_c2x_compat_pp_directive, diag::warn_c2x_compat_warning_directive, diag::warn_c17_compat_ellipsis_only_parameter, diag::warn_c17_compat_static_assert_no_message, -1,
  /* DiagArray691 */ diag::warn_omp51_compat_attributes, -1,
  /* DiagArray692 */ diag::ext_predef_outside_function, -1,
  /* DiagArray693 */ diag::warn_private_extern, -1,
  /* DiagArray694 */ diag::warn_use_of_private_header_outside_module, -1,
  /* DiagArray695 */ diag::warn_mmap_mismatched_private_module_name, diag::warn_mmap_mismatched_private_submodule, diag::warn_mmap_redundant_export_as, diag::warn_no_priv_submodule_use_toplevel, -1,
  /* DiagArray696 */ diag::warn_profile_data_missing, -1,
  /* DiagArray697 */ diag::warn_profile_data_out_of_date, -1,
  /* DiagArray698 */ diag::warn_profile_data_unprofiled, -1,
  /* DiagArray699 */ diag::warn_property_access_suggest, -1,
  /* DiagArray700 */ diag::warn_property_attr_mismatch, diag::warn_property_attribute, diag::warn_property_redecl_getter_mismatch, diag::warn_readonly_property, -1,
  /* DiagArray701 */ diag::warn_unimplemented_protocol_method, -1,
  /* DiagArray702 */ diag::warn_protocol_property_mismatch, -1,
  /* DiagArray703 */ diag::warn_avx_calling_convention, -1,
  /* DiagArray704 */ diag::err_func_returning_qualified_void, -1,
  /* DiagArray705 */ diag::warn_quoted_include_in_framework_header, -1,
  /* DiagArray707 */ diag::warn_for_range_ref_binds_ret_temp, -1,
  /* DiagArray708 */ diag::warn_for_range_const_ref_binds_temp_built_from_ref, diag::warn_for_range_copy, -1,
  /* DiagArray709 */ diag::warn_var_decl_not_read_only, -1,
  /* DiagArray710 */ diag::warn_auto_readonly_iboutlet_property, -1,
  /* DiagArray711 */ diag::warn_bad_receiver_type, -1,
  /* DiagArray712 */ diag::warn_receiver_forward_class, diag::warn_receiver_forward_instance, -1,
  /* DiagArray713 */ diag::ext_member_redeclared, -1,
  /* DiagArray714 */ diag::warn_consteval_if_always_true, -1,
  /* DiagArray716 */ diag::warn_redundant_move_on_return, -1,
  /* DiagArray717 */ diag::warn_redundant_parens_around_declarator, -1,
  /* DiagArray718 */ diag::ext_register_storage_class, -1,
  /* DiagArray719 */ diag::warn_reinterpret_different_from_static, -1,
  /* DiagArray720 */ diag::remark_fe_backend_plugin, -1,
  /* DiagArray722 */ diag::warn_initializer_out_of_order, diag::warn_some_initializers_out_of_order, -1,
  /* DiagArray723 */ diag::ext_designated_init_reordered, -1,
  /* DiagArray724 */ diag::warn_objc_requires_super_protocol, -1,
  /* DiagArray726 */ diag::warn_reserved_extern_symbol, -1,
  /* DiagArray727 */ diag::warn_pp_macro_is_reserved_id, -1,
  /* DiagArray728 */ diag::warn_reserved_module_name, -1,
  /* DiagArray729 */ diag::ext_ms_reserved_user_defined_literal, diag::ext_reserved_user_defined_literal, -1,
  /* DiagArray730 */ diag::warn_pragma_restrict_expansion_macro_use, -1,
  /* DiagArray731 */ diag::ext_retained_language_linkage, -1,
  /* DiagArray733 */ diag::warn_ret_addr_label, diag::warn_ret_local_temp_addr_ref, diag::warn_ret_stack_addr_ref, -1,
  /* DiagArray735 */ diag::ext_return_has_expr, diag::ext_return_missing_expr, diag::warn_falloff_nonvoid_coroutine, diag::warn_falloff_nonvoid_function, diag::warn_falloff_nonvoid_lambda, diag::warn_maybe_falloff_nonvoid_coroutine, diag::warn_maybe_falloff_nonvoid_function, diag::warn_maybe_falloff_nonvoid_lambda, diag::warn_return_missing_expr, -1,
  /* DiagArray736 */ diag::warn_return_value_udt, diag::warn_return_value_udt_incomplete, -1,
  /* DiagArray737 */ diag::ext_ovl_rewrite_equalequal_not_bool, -1,
  /* DiagArray738 */ diag::remark_cc1_round_trip_generated, -1,
  /* DiagArray739 */ diag::warn_no_dynamic_cast_with_rtti_disabled, diag::warn_no_typeid_with_rtti_disabled, -1,
  /* DiagArray740 */ diag::remark_sanitize_address_insert_extra_padding_accepted, diag::remark_sanitize_address_insert_extra_padding_rejected, -1,
  /* DiagArray741 */ diag::warn_drv_sarif_format_unstable, -1,
  /* DiagArray742 */ diag::remark_pp_search_path_usage, -1,
  /* DiagArray743 */ diag::warn_attribute_section_on_redeclaration, diag::warn_duplicate_codeseg_attribute, diag::warn_mismatched_section, -1,
  /* DiagArray744 */ diag::warn_unimplemented_selector, -1,
  /* DiagArray745 */ diag::warn_multiple_selectors, -1,
  /* DiagArray746 */ diag::warn_self_assignment_builtin, -1,
  /* DiagArray747 */ diag::warn_identity_field_assign, -1,
  /* DiagArray748 */ diag::warn_self_assignment_overloaded, -1,
  /* DiagArray749 */ diag::warn_self_move, -1,
  /* DiagArray750 */ diag::warn_semicolon_before_method_body, -1,
  /* DiagArray751 */ diag::warn_missing_sentinel, diag::warn_not_enough_argument, -1,
  /* DiagArray753 */ diag::warn_fe_serialized_diag_failure, diag::warn_fe_serialized_diag_failure_during_finalisation, diag::warn_fe_serialized_diag_merge_failure, -1,
  /* DiagArray754 */ diag::warn_decl_shadow, -1,
  /* DiagArray756 */ diag::warn_shadow_field, -1,
  /* DiagArray757 */ diag::warn_ctor_parm_shadows_field, -1,
  /* DiagArray758 */ diag::warn_modifying_shadowing_decl, -1,
  /* DiagArray759 */ diag::warn_ivar_use_hidden, -1,
  /* DiagArray760 */ diag::warn_decl_shadow_uncaptured_local, -1,
  /* DiagArray761 */ diag::warn_shift_negative, -1,
  /* DiagArray762 */ diag::warn_shift_gt_typewidth, -1,
  /* DiagArray763 */ diag::warn_shift_lhs_negative, -1,
  /* DiagArray764 */ diag::warn_addition_in_bitshift, -1,
  /* DiagArray765 */ diag::warn_shift_result_gt_typewidth, -1,
  /* DiagArray766 */ diag::warn_shift_result_sets_sign_bit, -1,
  /* DiagArray767 */ diag::warn_impcast_integer_64_32, -1,
  /* DiagArray768 */ diag::warn_mixed_sign_comparison, -1,
  /* DiagArray769 */ diag::warn_impcast_integer_sign, diag::warn_impcast_integer_sign_conditional, diag::warn_impcast_nonnegative_result, -1,
  /* DiagArray771 */ diag::warn_no_underlying_type_specified_for_enum_bitfield, -1,
  /* DiagArray772 */ diag::ext_wchar_t_sign_spec, -1,
  /* DiagArray773 */ diag::warn_impcast_single_bit_bitield_precision_constant, -1,
  /* DiagArray774 */ diag::warn_sizeof_array_param, -1,
  /* DiagArray775 */ diag::warn_sizeof_array_decay, -1,
  /* DiagArray776 */ diag::warn_division_sizeof_array, -1,
  /* DiagArray777 */ diag::warn_division_sizeof_ptr, -1,
  /* DiagArray778 */ diag::warn_sizeof_pointer_expr_memaccess, diag::warn_sizeof_pointer_type_memaccess, -1,
  /* DiagArray779 */ diag::warn_slash_u_filename, -1,
  /* DiagArray780 */ diag::warn_slh_does_not_support_asm_goto, -1,
  /* DiagArray781 */ diag::remark_sloc_usage, -1,
  /* DiagArray782 */ diag::warn_sometimes_uninit_var, -1,
  /* DiagArray783 */ diag::warn_fe_source_mgr, -1,
  /* DiagArray784 */ diag::warn_omp_declare_variant_after_emitted, diag::warn_omp_declare_variant_after_used, diag::warn_omp_declare_variant_marked_as_declare_variant, diag::warn_omp_declare_variant_score_not_constant, diag::warn_omp_nesting_simd, diag::warn_omp_unterminated_declare_target, diag::warn_pragma_omp_ignored, diag::warn_unknown_declare_variant_isa_trait, -1,
  /* DiagArray785 */ diag::warn_sampler_initializer_invalid_bits, -1,
  /* DiagArray787 */ diag::warn_stack_exhausted, -1,
  /* DiagArray788 */ diag::warn_stack_clash_protection_inline_asm, -1,
  /* DiagArray789 */ diag::ext_in_class_initializer_float_type_cxx11, -1,
  /* DiagArray790 */ diag::ext_internal_in_extern_inline, diag::ext_internal_in_extern_inline_quiet, -1,
  /* DiagArray791 */ diag::warn_static_inline_explicit_inst_ignored, -1,
  /* DiagArray792 */ diag::warn_static_local_in_extern_inline, -1,
  /* DiagArray793 */ diag::warn_static_self_reference_in_init, -1,
  /* DiagArray794 */ diag::warn_drv_libstdcxx_not_found, -1,
  /* DiagArray806 */ diag::warn_strict_potentially_direct_selector_expression, -1,
  /* DiagArray807 */ diag::warn_strict_prototypes, -1,
  /* DiagArray808 */ diag::warn_strict_multiple_method_decl, -1,
  /* DiagArray809 */ diag::warn_stringcompare, -1,
  /* DiagArray810 */ diag::warn_concatenated_literal_array_init, -1,
  /* DiagArray811 */ diag::warn_impcast_string_literal_to_bool, -1,
  /* DiagArray812 */ diag::warn_string_plus_char, -1,
  /* DiagArray813 */ diag::warn_string_plus_int, -1,
  /* DiagArray814 */ diag::warn_strlcpycat_wrong_size, -1,
  /* DiagArray815 */ diag::warn_strncat_large_size, diag::warn_strncat_src_size, diag::warn_strncat_wrong_size, -1,
  /* DiagArray816 */ diag::warn_suggest_destructor_marked_not_override_overriding, -1,
  /* DiagArray817 */ diag::warn_suggest_function_marked_not_override_overriding, -1,
  /* DiagArray818 */ diag::ext_typecheck_base_super, -1,
  /* DiagArray819 */ diag::warn_suspicious_bzero_size, -1,
  /* DiagArray821 */ diag::warn_attr_swift_name_decl_kind, diag::warn_attr_swift_name_decl_missing_params, diag::warn_attr_swift_name_function, diag::warn_attr_swift_name_getter_parameters, diag::warn_attr_swift_name_invalid_identifier, diag::warn_attr_swift_name_missing_parameters, diag::warn_attr_swift_name_multiple_selfs, diag::warn_attr_swift_name_num_params, diag::warn_attr_swift_name_setter_parameters, diag::warn_attr_swift_name_subscript_getter_newValue, diag::warn_attr_swift_name_subscript_invalid_parameter, diag::warn_attr_swift_name_subscript_setter_multiple_newValues, diag::warn_attr_swift_name_subscript_setter_no_newValue, -1,
  /* DiagArray822 */ diag::warn_case_value_overflow, diag::warn_missing_case, diag::warn_not_in_enum, -1,
  /* DiagArray823 */ diag::warn_bool_switch_condition, -1,
  /* DiagArray825 */ diag::warn_def_missing_case, -1,
  /* DiagArray826 */ diag::warn_sync_op_misaligned, -1,
  /* DiagArray827 */ diag::warn_sync_fetch_and_nand_semantics_change, -1,
  /* DiagArray829 */ diag::warn_target_clone_mixed_values, -1,
  /* DiagArray830 */ diag::warn_comparison_bitwise_always, diag::warn_comparison_bitwise_or, -1,
  /* DiagArray831 */ diag::warn_alignment_builtin_useless, diag::warn_comparison_always, -1,
  /* DiagArray832 */ diag::warn_integer_constants_in_conditional_always_true, diag::warn_left_shift_always, diag::warn_tautological_bool_compare, -1,
  /* DiagArray834 */ diag::warn_out_of_range_compare, -1,
  /* DiagArray835 */ diag::warn_tautological_compare_objc_bool, -1,
  /* DiagArray836 */ diag::warn_tautological_overlap_comparison, -1,
  /* DiagArray837 */ diag::warn_nonnull_expr_compare, diag::warn_null_pointer_compare, -1,
  /* DiagArray838 */ diag::warn_tautological_constant_compare, -1,
  /* DiagArray839 */ diag::warn_address_of_reference_null_compare, diag::warn_this_null_compare, -1,
  /* DiagArray840 */ diag::warn_unsigned_char_always_true_comparison, -1,
  /* DiagArray841 */ diag::warn_unsigned_enum_always_true_comparison, -1,
  /* DiagArray842 */ diag::warn_unsigned_always_true_comparison, -1,
  /* DiagArray843 */ diag::warn_tautological_compare_value_range, -1,
  /* DiagArray844 */ diag::warn_tcb_enforcement_violation, -1,
  /* DiagArray845 */ diag::ext_typecheck_decl_incomplete_type, -1,
  /* DiagArray847 */ diag::warn_acquired_before, diag::warn_acquired_before_after_cycle, diag::warn_cannot_resolve_lock, diag::warn_double_lock, diag::warn_expecting_lock_held_on_loop, diag::warn_expecting_locked, diag::warn_fun_excludes_mutex, diag::warn_fun_requires_lock, diag::warn_fun_requires_negative_cap, diag::warn_lock_exclusive_and_shared, diag::warn_lock_some_predecessors, diag::warn_no_unlock, diag::warn_unlock_but_no_lock, diag::warn_unlock_kind_mismatch, diag::warn_var_deref_requires_any_lock, diag::warn_var_deref_requires_lock, diag::warn_variable_requires_any_lock, diag::warn_variable_requires_lock, -1,
  /* DiagArray848 */ diag::warn_thread_attribute_argument_not_lockable, diag::warn_thread_attribute_decl_not_lockable, diag::warn_thread_attribute_decl_not_pointer, diag::warn_thread_attribute_ignored, diag::warn_thread_attribute_not_on_capability_member, diag::warn_thread_attribute_not_on_non_static_member, -1,
  /* DiagArray849 */ diag::warn_thread_safety_beta, -1,
  /* DiagArray850 */ diag::warn_acquire_requires_negative_cap, -1,
  /* DiagArray851 */ diag::warn_fun_requires_lock_precise, diag::warn_var_deref_requires_lock_precise, diag::warn_variable_requires_lock_precise, -1,
  /* DiagArray852 */ diag::warn_guarded_pass_by_reference, diag::warn_pt_guarded_pass_by_reference, -1,
  /* DiagArray853 */ diag::warn_thread_safety_verbose, -1,
  /* DiagArray854 */ diag::trigraph_converted, diag::trigraph_ends_block_comment, diag::trigraph_ignored, diag::trigraph_ignored_block_comment, -1,
  /* DiagArray856 */ diag::warn_type_safety_null_pointer_required, diag::warn_type_safety_type_mismatch, diag::warn_type_tag_for_datatype_wrong_kind, -1,
  /* DiagArray857 */ diag::ext_redefinition_of_typedef, -1,
  /* DiagArray858 */ diag::ext_typename_missing, -1,
  /* DiagArray859 */ diag::warn_fe_unable_to_open_stats_file, -1,
  /* DiagArray860 */ diag::warn_unaligned_access, -1,
  /* DiagArray861 */ diag::warn_imp_cast_drops_unaligned, -1,
  /* DiagArray862 */ diag::warn_unavailable_fwdclass_message, -1,
  /* DiagArray863 */ diag::warn_undeclared_selector, diag::warn_undeclared_selector_with_typo, -1,
  /* DiagArray864 */ diag::warn_pp_undef_identifier, -1,
  /* DiagArray865 */ diag::warn_pp_undef_prefix, -1,
  /* DiagArray866 */ diag::warn_address_of_reference_bool_conversion, diag::warn_this_bool_conversion, -1,
  /* DiagArray867 */ diag::warn_func_template_missing, -1,
  /* DiagArray868 */ diag::warn_undefined_inline, -1,
  /* DiagArray869 */ diag::warn_undefined_internal, -1,
  /* DiagArray870 */ diag::ext_undefined_internal_type, -1,
  /* DiagArray871 */ diag::warn_pointer_indirection_from_incompatible_type, diag::warn_undefined_reinterpret_cast, -1,
  /* DiagArray872 */ diag::warn_var_template_missing, -1,
  /* DiagArray873 */ diag::warn_throw_underaligned_obj, -1,
  /* DiagArray874 */ diag::warn_side_effects_unevaluated_context, -1,
  /* DiagArray875 */ diag::warn_unguarded_availability, -1,
  /* DiagArray876 */ diag::warn_unguarded_availability_new, -1,
  /* DiagArray877 */ diag::warn_delimited_ucn_empty, diag::warn_delimited_ucn_incomplete, diag::warn_ucn_escape_incomplete, diag::warn_ucn_escape_no_digits, diag::warn_ucn_escape_surrogate, diag::warn_ucn_not_valid_in_c89, diag::warn_ucn_not_valid_in_c89_literal, -1,
  /* DiagArray878 */ diag::warn_utf8_symbol_homoglyph, -1,
  /* DiagArray879 */ diag::ext_unicode_whitespace, -1,
  /* DiagArray880 */ diag::warn_utf8_symbol_zero_width, -1,
  /* DiagArray881 */ diag::warn_base_class_is_uninit, diag::warn_field_is_uninit, diag::warn_reference_field_is_uninit, diag::warn_uninit_byref_blockvar_captured_by_block, diag::warn_uninit_self_reference_in_init, diag::warn_uninit_self_reference_in_reference_init, diag::warn_uninit_var, -1,
  /* DiagArray882 */ diag::warn_uninit_const_reference, -1,
  /* DiagArray883 */ diag::warn_drv_potentially_misspelled_joined_argument, diag::warn_drv_unknown_argument_clang_cl, diag::warn_drv_unknown_argument_clang_cl_with_suggestion, -1,
  /* DiagArray884 */ diag::warn_assume_attribute_string_unknown, -1,
  /* DiagArray885 */ diag::warn_unknown_attribute_ignored, -1,
  /* DiagArray886 */ diag::warn_drv_new_cuda_version, diag::warn_drv_partially_supported_cuda_version, -1,
  /* DiagArray887 */ diag::warn_pp_invalid_directive, -1,
  /* DiagArray888 */ diag::ext_unknown_escape, -1,
  /* DiagArray889 */ diag::ext_on_off_switch_syntax, diag::ext_pragma_syntax_eod, diag::ext_stdc_pragma_ignored, diag::warn_pragma_diagnostic_cannot_pop, diag::warn_pragma_diagnostic_invalid, diag::warn_pragma_diagnostic_invalid_option, diag::warn_pragma_diagnostic_invalid_token, diag::warn_pragma_exec_charset_expected, diag::warn_pragma_exec_charset_push_invalid, diag::warn_pragma_exec_charset_spec_invalid, diag::warn_pragma_ignored, diag::warn_pragma_include_alias_expected, diag::warn_pragma_include_alias_expected_filename, diag::warn_pragma_include_alias_mismatch_angle, diag::warn_pragma_include_alias_mismatch_quote, diag::warn_pragma_warning_expected, diag::warn_pragma_warning_expected_number, diag::warn_pragma_warning_push_level, diag::warn_pragma_warning_spec_invalid, diag::warn_stdc_fenv_round_not_supported, -1,
  /* DiagArray890 */ diag::warn_unknown_sanitizer_ignored, -1,
  /* DiagArray891 */ diag::warn_pragma_diagnostic_unknown_warning, diag::warn_unknown_diag_option, diag::warn_unknown_warning_specifier, -1,
  /* DiagArray892 */ diag::ext_template_arg_unnamed_type, -1,
  /* DiagArray893 */ diag::warn_unneeded_internal_decl, diag::warn_unneeded_static_internal_decl, -1,
  /* DiagArray894 */ diag::warn_unneeded_member_function, -1,
  /* DiagArray895 */ diag::warn_unqualified_call_to_std_cast_function, -1,
  /* DiagArray896 */ diag::warn_unreachable, -1,
  /* DiagArray898 */ diag::warn_unreachable_break, -1,
  /* DiagArray899 */ diag::warn_unreachable_fallthrough_attr, -1,
  /* DiagArray900 */ diag::warn_unreachable_association, -1,
  /* DiagArray901 */ diag::warn_unreachable_loop_increment, -1,
  /* DiagArray902 */ diag::warn_unreachable_return, -1,
  /* DiagArray903 */ diag::warn_unsafe_buffer_operation, diag::warn_unsafe_buffer_variable, -1,
  /* DiagArray904 */ diag::warn_unsequenced_mod_mod, diag::warn_unsequenced_mod_use, -1,
  /* DiagArray905 */ diag::warn_drv_unsupported_float_abi_by_lib, -1,
  /* DiagArray906 */ diag::warn_target_unsupported_abs2008, diag::warn_target_unsupported_abslegacy, -1,
  /* DiagArray907 */ diag::warn_at_available_unchecked_use, -1,
  /* DiagArray908 */ diag::warn_target_unsupported_compact_branches, -1,
  /* DiagArray909 */ diag::warn_attribute_dll_instantiated_base_class, -1,
  /* DiagArray910 */ diag::warn_fe_backend_unsupported_fp_exceptions, diag::warn_fe_backend_unsupported_fp_rounding, -1,
  /* DiagArray911 */ diag::warn_template_qualified_friend_ignored, diag::warn_template_qualified_friend_unsupported, -1,
  /* DiagArray912 */ diag::warn_drv_unsupported_gpopt, -1,
  /* DiagArray913 */ diag::warn_target_unsupported_nan2008, diag::warn_target_unsupported_nanlegacy, -1,
  /* DiagArray914 */ diag::warn_drv_dwarf_version_limited_by_target, diag::warn_drv_unsupported_debug_info_opt_for_target, -1,
  /* DiagArray915 */ diag::warn_attribute_protected_visibility, -1,
  /* DiagArray916 */ diag::ext_partial_specs_not_deducible, -1,
  /* DiagArray919 */ diag::warn_unused_but_set_parameter, -1,
  /* DiagArray920 */ diag::warn_unused_but_set_variable, -1,
  /* DiagArray921 */ diag::warn_drv_diagnostics_hotness_requires_pgo, diag::warn_drv_diagnostics_misexpect_requires_pgo, diag::warn_drv_empty_joined_argument, diag::warn_drv_input_file_unused, diag::warn_drv_input_file_unused_by_cpp, diag::warn_drv_invalid_arch_name_with_suggestion, diag::warn_drv_preprocessed_input_file_unused, diag::warn_drv_unused_argument, diag::warn_drv_unused_x, diag::warn_ignored_clang_option, diag::warn_ignoring_fdiscard_for_bitcode, diag::warn_ignoring_verify_debuginfo_preserve_export, -1,
  /* DiagArray922 */ diag::warn_unused_comparison, -1,
  /* DiagArray923 */ diag::warn_unused_const_variable, -1,
  /* DiagArray924 */ diag::warn_unused_exception_param, -1,
  /* DiagArray925 */ diag::warn_unused_function, -1,
  /* DiagArray926 */ diag::warn_unused_property_expr, -1,
  /* DiagArray927 */ diag::warn_unused_label, -1,
  /* DiagArray928 */ diag::warn_unused_lambda_capture, -1,
  /* DiagArray929 */ diag::warn_unused_local_typedef, -1,
  /* DiagArray931 */ diag::pp_macro_not_used, -1,
  /* DiagArray932 */ diag::warn_unused_member_function, -1,
  /* DiagArray933 */ diag::warn_unused_parameter, -1,
  /* DiagArray934 */ diag::warn_unused_private_field, -1,
  /* DiagArray935 */ diag::warn_unused_property_backing_ivar, -1,
  /* DiagArray936 */ diag::warn_unused_result, diag::warn_unused_result_msg, -1,
  /* DiagArray937 */ diag::warn_unused_template, -1,
  /* DiagArray938 */ diag::warn_unused_call, diag::warn_unused_comma_left_operand, diag::warn_unused_constructor, diag::warn_unused_constructor_msg, diag::warn_unused_container_subscript_expr, diag::warn_unused_expr, diag::warn_unused_voidptr, -1,
  /* DiagArray939 */ diag::warn_unused_variable, -1,
  /* DiagArray940 */ diag::warn_unused_volatile, -1,
  /* DiagArray941 */ diag::warn_used_but_marked_unused, -1,
  /* DiagArray942 */ diag::warn_user_literal_reserved, -1,
  /* DiagArray943 */ diag::warn_diagnose_if_succeeded, -1,
  /* DiagArray944 */ diag::warn_second_arg_of_va_start_not_last_named_param, diag::warn_second_parameter_to_va_arg_never_compatible, diag::warn_va_start_type_is_undefined, -1,
  /* DiagArray945 */ diag::ext_named_variadic_macro, diag::ext_pp_bad_vaopt_use, diag::ext_variadic_macro, -1,
  /* DiagArray946 */ diag::warn_typecheck_vector_element_sizes_not_equal, -1,
  /* DiagArray947 */ diag::warn_incompatible_vectors, -1,
  /* DiagArray949 */ diag::warn_empty_parens_are_function_decl, diag::warn_parens_disambiguated_as_function_declaration, diag::warn_parens_disambiguated_as_variable_declaration, -1,
  /* DiagArray950 */ diag::warn_decl_in_param_list, diag::warn_redefinition_in_param_list, -1,
  /* DiagArray951 */ diag::warn_vla_used, -1,
  /* DiagArray952 */ diag::ext_vla, -1,
  /* DiagArray953 */ diag::warn_void_pointer_to_enum_cast, -1,
  /* DiagArray954 */ diag::warn_void_pointer_to_int_cast, -1,
  /* DiagArray955 */ diag::ext_typecheck_indirection_through_void_pointer, diag::ext_typecheck_indirection_through_void_pointer_cpp, -1,
  /* DiagArray957 */ diag::warn_wasm_dynamic_exception_spec_ignored, -1,
  /* DiagArray958 */ diag::warn_weak_template_vtable, -1,
  /* DiagArray959 */ diag::warn_weak_vtable, -1,
  /* DiagArray960 */ diag::ext_deprecated_string_literal_conversion, -1,
  /* DiagArray962 */ diag::warn_xor_used_as_pow, diag::warn_xor_used_as_pow_base, diag::warn_xor_used_as_pow_base_extra, -1,
  /* DiagArray963 */ diag::warn_zero_as_null_pointer_constant, -1,
  /* DiagArray964 */ diag::ext_typecheck_zero_array_size, -1,
};

static const int16_t DiagSubGroups[] = {
  /* Empty */ -1,
  /* DiagSubGroup0 */ 295, -1,
  /* DiagSubGroup4 */ 17, 295, -1,
  /* DiagSubGroup11 */ 665, 809, 837, -1,
  /* DiagSubGroup17 */ 545, 652, 822, 823, 514, -1,
  /* DiagSubGroup27 */ 209, -1,
  /* DiagSubGroup29 */ 36, 35, 32, -1,
  /* DiagSubGroup34 */ 31, -1,
  /* DiagSubGroup42 */ 43, -1,
  /* DiagSubGroup52 */ 386, 187, -1,
  /* DiagSubGroup56 */ 885, 377, -1,
  /* DiagSubGroup66 */ 101, 129, 343, -1,
  /* DiagSubGroup67 */ 132, -1,
  /* DiagSubGroup70 */ 773, -1,
  /* DiagSubGroup77 */ 665, 866, -1,
  /* DiagSubGroup78 */ 77, -1,
  /* DiagSubGroup79 */ 74, -1,
  /* DiagSubGroup88 */ 91, -1,
  /* DiagSubGroup89 */ 95, -1,
  /* DiagSubGroup90 */ 99, -1,
  /* DiagSubGroup91 */ 99, 94, 92, 681, 683, 685, 687, -1,
  /* DiagSubGroup93 */ 91, 682, 684, 686, 688, -1,
  /* DiagSubGroup95 */ 96, 97, 98, -1,
  /* DiagSubGroup102 */ 683, 685, 687, -1,
  /* DiagSubGroup103 */ 102, 684, 686, 688, -1,
  /* DiagSubGroup104 */ 101, 100, -1,
  /* DiagSubGroup106 */ 233, 227, 107, 685, 687, -1,
  /* DiagSubGroup108 */ 106, 686, 688, -1,
  /* DiagSubGroup109 */ 105, -1,
  /* DiagSubGroup110 */ 104, -1,
  /* DiagSubGroup111 */ 106, -1,
  /* DiagSubGroup112 */ 107, -1,
  /* DiagSubGroup113 */ 109, -1,
  /* DiagSubGroup115 */ 687, -1,
  /* DiagSubGroup116 */ 115, 688, -1,
  /* DiagSubGroup118 */ 117, 114, -1,
  /* DiagSubGroup121 */ 115, -1,
  /* DiagSubGroup122 */ 116, -1,
  /* DiagSubGroup123 */ 118, -1,
  /* DiagSubGroup124 */ 685, -1,
  /* DiagSubGroup125 */ 686, -1,
  /* DiagSubGroup126 */ 683, -1,
  /* DiagSubGroup127 */ 684, -1,
  /* DiagSubGroup128 */ 681, -1,
  /* DiagSubGroup130 */ 682, -1,
  /* DiagSubGroup131 */ 134, 136, 681, 683, 685, 687, -1,
  /* DiagSubGroup135 */ 131, 132, 133, 682, 684, 686, 688, -1,
  /* DiagSubGroup141 */ 117, -1,
  /* DiagSubGroup142 */ 141, -1,
  /* DiagSubGroup144 */ 162, -1,
  /* DiagSubGroup147 */ 148, -1,
  /* DiagSubGroup156 */ 563, -1,
  /* DiagSubGroup160 */ 159, -1,
  /* DiagSubGroup164 */ 165, 166, -1,
  /* DiagSubGroup170 */ 70, 588, -1,
  /* DiagSubGroup175 */ 77, 170, 281, 71, 306, 767, 432, 396, 393, 462, 560, 576, 598, 769, 811, -1,
  /* DiagSubGroup176 */ 576, -1,
  /* DiagSubGroup178 */ 179, 219, 20, 177, -1,
  /* DiagSubGroup181 */ 2, -1,
  /* DiagSubGroup189 */ 191, 193, 192, 733, -1,
  /* DiagSubGroup204 */ 203, 201, -1,
  /* DiagSubGroup207 */ 209, 210, 211, 213, 214, 216, 220, 221, 222, 223, 224, 225, 212, 227, 232, 233, 235, 236, 237, 238, -1,
  /* DiagSubGroup214 */ 217, -1,
  /* DiagSubGroup215 */ 216, -1,
  /* DiagSubGroup216 */ 218, -1,
  /* DiagSubGroup230 */ 231, -1,
  /* DiagSubGroup238 */ 92, -1,
  /* DiagSubGroup244 */ 245, -1,
  /* DiagSubGroup249 */ 251, 250, -1,
  /* DiagSubGroup252 */ 253, -1,
  /* DiagSubGroup265 */ 221, -1,
  /* DiagSubGroup267 */ 565, -1,
  /* DiagSubGroup276 */ 299, -1,
  /* DiagSubGroup277 */ 279, 222, -1,
  /* DiagSubGroup278 */ 223, -1,
  /* DiagSubGroup281 */ 282, 283, 278, -1,
  /* DiagSubGroup282 */ 224, -1,
  /* DiagSubGroup283 */ 225, -1,
  /* DiagSubGroup295 */ 214, 523, 383, 425, 750, 526, 768, 933, 919, 578, 579, 273, 810, 332, -1,
  /* DiagSubGroup297 */ 133, 96, -1,
  /* DiagSubGroup298 */ 273, -1,
  /* DiagSubGroup306 */ 308, 309, -1,
  /* DiagSubGroup311 */ 312, 321, 566, 318, 320, 314, 313, -1,
  /* DiagSubGroup322 */ 316, 318, 320, -1,
  /* DiagSubGroup327 */ 326, -1,
  /* DiagSubGroup331 */ 829, -1,
  /* DiagSubGroup333 */ 100, 105, 114, -1,
  /* DiagSubGroup338 */ 339, 340, 342, 343, 344, 345, 346, 347, 348, 349, 952, 350, 351, 352, 353, 354, 356, 357, 358, 359, 360, 713, 361, 362, 364, 365, 366, 367, 964, 368, 369, -1,
  /* DiagSubGroup362 */ 363, -1,
  /* DiagSubGroup382 */ 380, 381, -1,
  /* DiagSubGroup383 */ 384, -1,
  /* DiagSubGroup385 */ 394, 395, -1,
  /* DiagSubGroup390 */ 391, -1,
  /* DiagSubGroup393 */ 397, 621, -1,
  /* DiagSubGroup396 */ 622, -1,
  /* DiagSubGroup397 */ 387, -1,
  /* DiagSubGroup410 */ 411, 406, -1,
  /* DiagSubGroup416 */ 418, 562, -1,
  /* DiagSubGroup422 */ 227, -1,
  /* DiagSubGroup433 */ 432, -1,
  /* DiagSubGroup435 */ 436, -1,
  /* DiagSubGroup439 */ 379, -1,
  /* DiagSubGroup464 */ 134, -1,
  /* DiagSubGroup467 */ 98, -1,
  /* DiagSubGroup468 */ 310, 706, -1,
  /* DiagSubGroup480 */ 484, 489, 501, 487, 497, 505, 481, 510, 493, 511, 502, 503, 509, 495, 492, 488, 507, 504, 491, 499, 498, 496, 483, 486, 512, 482, 485, 490, 506, 419, -1,
  /* DiagSubGroup507 */ 508, -1,
  /* DiagSubGroup545 */ 41, 79, 153, 159, 204, 311, 310, 325, 385, 423, 434, 518, 519, 546, 549, 708, 721, 735, 746, 749, 774, 775, 813, 831, 854, 881, 889, 917, 956, 603, 592, 594, 644, 693, 149, 293, 943, -1,
  /* DiagSubGroup546 */ 663, 716, 734, 749, -1,
  /* DiagSubGroup547 */ 501, -1,
  /* DiagSubGroup551 */ 99, -1,
  /* DiagSubGroup557 */ 107, -1,
  /* DiagSubGroup559 */ 768, 175, 463, -1,
  /* DiagSubGroup562 */ 561, -1,
  /* DiagSubGroup578 */ 358, -1,
  /* DiagSubGroup581 */ 582, -1,
  /* DiagSubGroup591 */ 617, -1,
  /* DiagSubGroup597 */ 623, -1,
  /* DiagSubGroup617 */ 618, -1,
  /* DiagSubGroup620 */ 622, 621, 588, 835, -1,
  /* DiagSubGroup630 */ 784, 632, 633, 635, 634, 631, -1,
  /* DiagSubGroup635 */ 634, -1,
  /* DiagSubGroup645 */ 425, -1,
  /* DiagSubGroup649 */ 650, -1,
  /* DiagSubGroup652 */ 466, 465, 73, 75, 764, 643, 653, 190, -1,
  /* DiagSubGroup654 */ 875, -1,
  /* DiagSubGroup660 */ 137, 96, 98, 100, 101, 105, 114, 117, 305, 333, 340, 342, 343, 344, 345, 346, 347, 349, 350, 351, 353, 354, 356, 357, 358, 359, 360, 361, 362, 366, 368, 369, 456, 467, 484, 485, 487, 490, 492, 497, 498, 504, 642, 952, 964, 254, 458, 453, 205, 401, 270, 274, 584, 302, 69, 870, 731, 163, 552, 119, -1,
  /* DiagSubGroup662 */ 232, 469, 84, 730, 301, -1,
  /* DiagSubGroup664 */ 360, -1,
  /* DiagSubGroup669 */ 953, -1,
  /* DiagSubGroup670 */ 669, 954, -1,
  /* DiagSubGroup677 */ 678, -1,
  /* DiagSubGroup680 */ 889, 382, 675, 677, -1,
  /* DiagSubGroup682 */ 681, 129, -1,
  /* DiagSubGroup684 */ 683, -1,
  /* DiagSubGroup686 */ 685, -1,
  /* DiagSubGroup688 */ 687, -1,
  /* DiagSubGroup690 */ 689, -1,
  /* DiagSubGroup706 */ 708, 707, -1,
  /* DiagSubGroup718 */ 233, -1,
  /* DiagSubGroup721 */ 722, 723, -1,
  /* DiagSubGroup725 */ 727, -1,
  /* DiagSubGroup726 */ 727, 728, -1,
  /* DiagSubGroup729 */ 94, -1,
  /* DiagSubGroup732 */ 733, -1,
  /* DiagSubGroup735 */ 736, -1,
  /* DiagSubGroup744 */ 745, -1,
  /* DiagSubGroup746 */ 748, 747, -1,
  /* DiagSubGroup752 */ 904, -1,
  /* DiagSubGroup754 */ 758, 759, -1,
  /* DiagSubGroup755 */ 754, 757, 760, 756, -1,
  /* DiagSubGroup757 */ 758, -1,
  /* DiagSubGroup786 */ 785, -1,
  /* DiagSubGroup789 */ 364, -1,
  /* DiagSubGroup806 */ 673, -1,
  /* DiagSubGroup807 */ 228, -1,
  /* DiagSubGroup820 */ 778, 264, 571, 477, 819, -1,
  /* DiagSubGroup831 */ 832, 837, 836, 830, 839, 835, -1,
  /* DiagSubGroup832 */ 834, -1,
  /* DiagSubGroup833 */ 855, 843, -1,
  /* DiagSubGroup846 */ 848, 847, 851, 852, -1,
  /* DiagSubGroup855 */ 838, 842, 840, 841, -1,
  /* DiagSubGroup874 */ 674, -1,
  /* DiagSubGroup875 */ 876, -1,
  /* DiagSubGroup881 */ 782, 793, 882, -1,
  /* DiagSubGroup892 */ 136, -1,
  /* DiagSubGroup896 */ 901, 899, 900, -1,
  /* DiagSubGroup897 */ 896, 898, 902, -1,
  /* DiagSubGroup917 */ 918, 925, 927, 934, 928, 929, 938, 939, 920, 935, -1,
  /* DiagSubGroup925 */ 893, -1,
  /* DiagSubGroup930 */ 929, -1,
  /* DiagSubGroup932 */ 894, -1,
  /* DiagSubGroup937 */ 893, -1,
  /* DiagSubGroup938 */ 922, 936, 874, -1,
  /* DiagSubGroup939 */ 923, -1,
  /* DiagSubGroup948 */ 947, -1,
  /* DiagSubGroup951 */ 952, -1,
  /* DiagSubGroup954 */ 953, -1,
  /* DiagSubGroup960 */ 238, -1,
  /* DiagSubGroup961 */ 960, -1,
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
    "c++23-extensions\014c++2a-compat\025c++2a-compat-pedantic\020c++2a-exte"
    "nsions\036c++98-c++11-c++14-c++17-compat'c++98-c++11-c++14-c++17-compat"
    "-pedantic\030c++98-c++11-c++14-compat!c++98-c++11-c++14-compat-pedantic"
    "\022c++98-c++11-compat!c++98-c++11-compat-binary-literal\033c++98-c++11"
    "-compat-pedantic\014c++98-compat#c++98-compat-bind-to-temporary-copy\027"
    "c++98-compat-extra-semi%c++98-compat-local-type-template-args\025c++98-"
    "compat-pedantic'c++98-compat-unnamed-type-template-args\016c11-extensio"
    "ns\nc2x-compat\016c2x-extensions\nc99-compat\016c99-designator\016c99-e"
    "xtensions#call-to-pure-virtual-from-ctor-dtor\025called-once-parameter\n"
    "cast-align\027cast-calling-convention\022cast-function-type\031cast-fun"
    "ction-type-strict\020cast-of-sel-type\tcast-qual\023cast-qual-unrelated"
    "\nchar-align\017char-subscripts\014clang-cl-pch\020class-conversion\015"
    "class-varargs\017cmse-union-leak\005comma\007comment\010comments\036com"
    "pare-distinct-pointer-types\022completion-handler\026complex-component-"
    "init\024compound-token-split\035compound-token-split-by-macro\035compou"
    "nd-token-split-by-space\031conditional-type-mismatch\031conditional-uni"
    "nitialized\015config-macros\023constant-conversion\022constant-evaluate"
    "d\030constant-logical-operand\023constexpr-not-const\010consumed\nconve"
    "rsion\017conversion-null$coro-non-aligned-allocation-function\tcoroutin"
    "e%coroutine-missing-unhandled-exception\026covered-switch-default\003cp"
    "p\030cstring-format-directive\026ctad-maybe-unsupported\021ctor-dtor-pr"
    "ivacy\003ctu\013cuda-compat\030custom-atomic-properties\027cxx-attribut"
    "e-extension\010dangling\015dangling-else\016dangling-field\014dangling-"
    "gsl\031dangling-initializer-list\023darwin-sdk-settings\tdate-time\023d"
    "ealloc-in-category\035debug-compression-unavailable\033declaration-afte"
    "r-statement\032defaulted-function-deleted\026delegating-ctor-cycles del"
    "ete-abstract-non-virtual-dtor\021delete-incomplete$delete-non-abstract-"
    "non-virtual-dtor\027delete-non-virtual-dtor#delimited-escape-sequence-e"
    "xtension\032deprecate-lax-vec-conv-all\ndeprecated\035deprecated-altive"
    "c-src-compat$deprecated-anon-enum-enum-conversion\030deprecated-array-c"
    "ompare\025deprecated-attributes\023deprecated-builtins\032deprecated-co"
    "mma-subscript\017deprecated-copy\024deprecated-copy-dtor\031deprecated-"
    "copy-with-dtor'deprecated-copy-with-user-provided-copy'deprecated-copy-"
    "with-user-provided-dtor\024deprecated-coroutine\027deprecated-declarati"
    "ons!deprecated-dynamic-exception-spec\027deprecated-enum-compare#deprec"
    "ated-enum-compare-conditional\037deprecated-enum-enum-conversion deprec"
    "ated-enum-float-conversion\032deprecated-implementations\031deprecated-"
    "increment-bool\030deprecated-non-prototype\031deprecated-objc-isa-usage"
    "%deprecated-objc-pointer-introspection5deprecated-objc-pointer-introspe"
    "ction-performSelector\021deprecated-pragma\023deprecated-register\037de"
    "precated-static-analyzer-flag\027deprecated-this-capture\017deprecated-"
    "type\023deprecated-volatile\033deprecated-writable-strings\022direct-iv"
    "ar-access\030disabled-macro-expansion\025disabled-optimization\014disca"
    "rd-qual\034distributed-object-modifiers\013div-by-zero\020division-by-z"
    "ero\036dll-attribute-on-redeclaration%dllexport-explicit-instantiation-"
    "decl\032dllimport-static-field-def\015documentation\035documentation-de"
    "precated-sync\022documentation-html\026documentation-pedantic\035docume"
    "ntation-unknown-command\036dollar-in-identifier-extension\020double-pro"
    "motion\tdtor-name\014dtor-typedef\030duplicate-decl-specifier\016duplic"
    "ate-enum\024duplicate-method-arg\026duplicate-method-match\022duplicate"
    "-protocol\017dxil-validation\027dynamic-class-memaccess\026dynamic-exce"
    "ption-spec\034eager-load-cxx-named-modules\006effc++\024elaborated-enum"
    "-base\025elaborated-enum-class\022embedded-directive\nempty-body\023emp"
    "ty-decomposition\017empty-init-stmt\026empty-translation-unit\013encode"
    "-type\014endif-labels\014enum-compare\030enum-compare-conditional\023en"
    "um-compare-switch\031enum-constexpr-conversion\017enum-conversion\024en"
    "um-enum-conversion\025enum-float-conversion\016enum-too-large\nexceptio"
    "ns\023excess-initializers\025exit-time-destructors\024expansion-to-defi"
    "ned\030explicit-initialize-call\027explicit-ownership-type\016export-un"
    "named\026export-using-directive\017extern-c-compat\022extern-initialize"
    "r\005extra\023extra-qualification\nextra-semi\017extra-semi-stmt\014ext"
    "ra-tokens\032final-dtor-non-final-class\013final-macro\024fixed-enum-ex"
    "tension\024fixed-point-overflow\tflag-enum\031flexible-array-extensions"
    "\020float-conversion\013float-equal\031float-overflow-conversion\025flo"
    "at-zero-conversion\021for-loop-analysis\006format\021format-extra-args\030"
    "format-insufficient-args\030format-invalid-specifier\016format-non-iso\021"
    "format-nonliteral\017format-pedantic\017format-security\025format-type-"
    "confusion\nformat-y2k\022format-zero-length\010format=2\016fortify-sour"
    "ce\023four-char-constants\015frame-address\021frame-larger-than\022fram"
    "e-larger-than=%framework-include-private-from-public\023free-nonheap-ob"
    "ject\036function-def-in-objc-container\025function-multiversion\014fuse"
    "-ld-path\033future-attribute-extensions\015future-compat\ngcc-compat\023"
    "global-constructors\013global-isel\003gnu\026gnu-alignof-expression\024"
    "gnu-anonymous-struct\033gnu-array-member-paren-init\015gnu-auto-type\022"
    "gnu-binary-literal\016gnu-case-range\023gnu-complex-integer gnu-compoun"
    "d-literal-initializer\037gnu-conditional-omitted-operand\016gnu-designa"
    "tor\020gnu-empty-struct\036gnu-flexible-array-initializer\037gnu-flexib"
    "le-array-union-member\024gnu-folding-constant\026gnu-imaginary-constant"
    "\020gnu-include-next\035gnu-inline-cpp-without-extern\022gnu-label-as-v"
    "alue\017gnu-line-marker\033gnu-null-pointer-arithmetic\027gnu-offsetof-"
    "extensions\021gnu-pointer-arith\023gnu-redeclared-enum\030gnu-statement"
    "-expression-gnu-statement-expression-from-macro-expansion\025gnu-static"
    "-float-init$gnu-string-literal-operator-template\016gnu-union-cast\"gnu"
    "-variable-sized-type-not-at-end\027gnu-zero-line-directive!gnu-zero-var"
    "iadic-macro-arguments\024gpu-maybe-wrong-side\014header-guard\016header"
    "-hygiene\031hip-omp-target-directives\010hip-only\017hlsl-extensions\025"
    "idiomatic-parentheses\022ignored-attributes)ignored-availability-withou"
    "t-sdk-settings\035ignored-optimization-argument\030ignored-pragma-intri"
    "nsic\027ignored-pragma-optimize\017ignored-pragmas\022ignored-qualifier"
    "s\034ignored-reference-qualifiers\010implicit\032implicit-atomic-proper"
    "ties#implicit-const-int-float-conversion*implicit-conversion-floating-p"
    "oint-to-bool implicit-exception-spec-mismatch\024implicit-fallthrough!i"
    "mplicit-fallthrough-per-function\037implicit-fixed-point-conversion\031"
    "implicit-float-conversion\035implicit-function-declaration\014implicit-"
    "int\027implicit-int-conversion\035implicit-int-float-conversion\024impl"
    "icit-retain-self\033implicitly-unsigned-literal\006import&import-prepro"
    "cessor-directive-pedantic\021inaccessible-base\032include-next-absolute"
    "-path\033include-next-outside-header\033incompatible-exception-spec#inc"
    "ompatible-function-pointer-types*incompatible-function-pointer-types-st"
    "rict\"incompatible-library-redeclaration\026incompatible-ms-struct\032i"
    "ncompatible-pointer-types.incompatible-pointer-types-discards-qualifier"
    "s\032incompatible-property-type\024incompatible-sysroot'incomplete-fram"
    "ework-module-declaration\031incomplete-implementation\021incomplete-mod"
    "ule\035incomplete-setjmp-declaration\023incomplete-umbrella\026inconsis"
    "tent-dllimport(inconsistent-missing-destructor-override\035inconsistent"
    "-missing-override\016increment-bool\022infinite-recursion\tinit-self\025"
    "initializer-overrides\023injected-class-name\006inline\ninline-asm#inli"
    "ne-namespace-reopened-noninline\021inline-new-delete\"instantiation-aft"
    "er-specialization\016int-conversion\017int-conversions\023int-in-bool-c"
    "ontext\023int-to-pointer-cast\030int-to-void-pointer-cast\020integer-ov"
    "erflow\031interrupt-service-routine\035invalid-command-line-argument\021"
    "invalid-constexpr\020invalid-iboutlet&invalid-initializer-from-system-h"
    "eader\035invalid-ios-deployment-target\030invalid-no-builtin-names\020i"
    "nvalid-noreturn\020invalid-offsetof invalid-or-nonexistent-directory\036"
    "invalid-partial-specialization\013invalid-pch\020invalid-pp-token\027in"
    "valid-source-encoding\023invalid-token-paste\014invalid-utf8\020jump-se"
    "h-finally\016keyword-compat\015keyword-macro\026knr-promoted-parameter\030"
    "language-extension-token\023large-by-value-copy\006liblto\017linker-war"
    "nings\022literal-conversion\015literal-range\030local-type-template-arg"
    "s\027logical-not-parentheses\026logical-op-parentheses\tlong-long\015lo"
    "op-analysis\017macro-redefined\004main\020main-return-type\027malformed"
    "-warning-check\036many-braces-around-scalar-init*mathematical-notation-"
    "identifier-extension\nmax-tokens\021max-unsigned-zero\026memset-transpo"
    "sed-args\022memsize-comparison\021method-signatures\tmicrosoft\022micro"
    "soft-abstract\022microsoft-anon-tag\016microsoft-cast\021microsoft-char"
    "ize\027microsoft-comment-paste\024microsoft-const-init\023microsoft-cpp"
    "-macro\"microsoft-default-arg-redefinition\031microsoft-drectve-section"
    "\025microsoft-end-of-file microsoft-enum-forward-reference\024microsoft"
    "-enum-value\030microsoft-exception-spec\020microsoft-exists#microsoft-e"
    "xplicit-constructor-call\035microsoft-extra-qualification\024microsoft-"
    "fixed-enum\030microsoft-flexible-array\016microsoft-goto\033microsoft-i"
    "naccessible-base\021microsoft-include\033microsoft-mutable-reference\031"
    "microsoft-pure-definition\032microsoft-redeclare-static\020microsoft-se"
    "aled\027microsoft-static-assert\022microsoft-template\031microsoft-temp"
    "late-shadow microsoft-union-member-reference\034microsoft-unqualified-f"
    "riend\024microsoft-using-decl\032microsoft-void-pseudo-dtor\tmisexpect\026"
    "misleading-indentation\025mismatched-new-delete\032mismatched-parameter"
    "-types\027mismatched-return-types\017mismatched-tags\016missing-braces\021"
    "missing-constinit\024missing-declarations\026missing-exception-spec\032"
    "missing-field-initializers\030missing-format-attribute\024missing-inclu"
    "de-dirs\032missing-method-return-type\020missing-noescape\020missing-no"
    "return\030missing-prototype-for-cc\022missing-prototypes\025missing-sel"
    "ector-name\017missing-sysroot\035missing-variable-declarations\025missp"
    "elled-assumption\014module-build\017module-conflict\033module-file-conf"
    "ig-mismatch\025module-file-extension\015module-import\031module-import-"
    "in-extern-c\032module-include-translation\013module-lock\"modules-ambig"
    "uous-internal-linkage\037modules-import-nested-redundant\004most\004mov"
    "e\014msvc-include\016msvc-not-found\tmultichar\023multiple-move-vbase\t"
    "narrowing\021nested-anon-types\016nested-externs\020new-returns-null\013"
    "newline-eof\007noderef\015noexcept-type\031non-c-typedef-for-linkage\007"
    "non-gcc\033non-literal-null-conversion'non-modular-include-in-framework"
    "-module\035non-modular-include-in-module\017non-pod-varargs\032non-powe"
    "r-of-two-alignment\020non-virtual-dtor\007nonnull\025nonportable-cfstri"
    "ngs\030nonportable-include-path\037nonportable-system-include-path!nonp"
    "ortable-vector-initialization\024nontrivial-memaccess\023nsconsumed-mis"
    "match\022nsreturns-mismatch\017null-arithmetic\016null-character\017nul"
    "l-conversion\020null-dereference\027null-pointer-arithmetic\030null-poi"
    "nter-subtraction\013nullability\030nullability-completeness\"nullabilit"
    "y-completeness-on-arrays\024nullability-declspec\025nullability-extensi"
    "on#nullability-inferred-on-nested-type\036nullable-to-nonnull-conversio"
    "n+objc-autosynthesis-property-ivar-name-match\035objc-bool-constant-con"
    "version\013objc-boxing\027objc-circular-container\016objc-cocoa-api\034"
    "objc-designated-initializers\036objc-dictionary-duplicate-keys\023objc-"
    "flexible-array\037objc-forward-class-redefinition\024objc-interface-iva"
    "rs\024objc-literal-compare\027objc-literal-conversion\027objc-macro-red"
    "efinition\021objc-messaging-id\022objc-method-access\037objc-missing-pr"
    "operty-synthesis\030objc-missing-super-calls\032objc-multiple-method-na"
    "mes\"objc-noncopy-retain-block-property\032objc-nonunified-exceptions#o"
    "bjc-property-assign-on-object-type\034objc-property-implementation\037o"
    "bjc-property-implicit-mismatch*objc-property-matches-cocoa-ownership-ru"
    "le\032objc-property-no-attribute\027objc-property-synthesis#objc-protoc"
    "ol-method-implementation objc-protocol-property-synthesis\030objc-proto"
    "col-qualifiers\"objc-readonly-with-setter-property\026objc-redundant-ap"
    "i-use\032objc-redundant-literal-use\017objc-root-class\025objc-signed-c"
    "har-bool/objc-signed-char-bool-implicit-float-conversion-objc-signed-ch"
    "ar-bool-implicit-int-conversion\023objc-string-compare\031objc-string-c"
    "oncatenation\034objc-unsafe-perform-selector\003odr\016old-style-cast\024"
    "old-style-definition\027opencl-unsupported-rgba\006openmp\024openmp-51-"
    "extensions\016openmp-clauses\020openmp-loop-form\016openmp-mapping\015o"
    "penmp-target\016option-ignored!ordered-compare-function-pointers\027out"
    "-of-line-declaration\025out-of-scope-function\014over-aligned\010overfl"
    "ow\022overlength-strings\037overloaded-shift-op-parentheses\022overload"
    "ed-virtual\015override-init\017override-module\032overriding-method-mis"
    "match\023overriding-t-option\006packed\016packed-non-pod\006padded\013p"
    "arentheses\024parentheses-equality\024partial-availability\004pass\015p"
    "ass-analysis\013pass-failed\013pass-missed\015pch-date-time\010pedantic"
    "\026pedantic-core-features\017pedantic-macros\020pessimizing-move\015po"
    "inter-arith\027pointer-bool-conversion\017pointer-compare\027pointer-in"
    "teger-compare\014pointer-sign\024pointer-to-enum-cast\023pointer-to-int"
    "-cast\025pointer-type-mismatch\031poison-system-directories\033potentia"
    "lly-direct-selector potentially-evaluated-expression\026pragma-clang-at"
    "tribute\032pragma-once-outside-header\013pragma-pack\036pragma-pack-sus"
    "picious-include#pragma-system-header-outside-header\007pragmas\020pre-c"
    "++14-compat\031pre-c++14-compat-pedantic\020pre-c++17-compat\031pre-c++"
    "17-compat-pedantic\020pre-c++20-compat\031pre-c++20-compat-pedantic\020"
    "pre-c++23-compat\031pre-c++23-compat-pedantic\016pre-c2x-compat\027pre-"
    "c2x-compat-pedantic\024pre-openmp-51-compat&predefined-identifier-outsi"
    "de-function\016private-extern\016private-header\016private-module\025pr"
    "ofile-instr-missing\031profile-instr-out-of-date\030profile-instr-unpro"
    "filed\032property-access-dot-syntax\033property-attribute-mismatch\010p"
    "rotocol%protocol-property-synthesis-ambiguity\005psabi\032qualified-voi"
    "d-return-type\"quoted-include-in-framework-header\023range-loop-analysi"
    "s\031range-loop-bind-reference\024range-loop-construct\017read-only-typ"
    "es\032readonly-iboutlet-property\015receiver-expr\026receiver-forward-c"
    "lass\027redeclared-class-member\026redundant-consteval-if\017redundant-"
    "decls\016redundant-move\020redundant-parens\010register\026reinterpret-"
    "base-class\025remark-backend-plugin\007reorder\014reorder-ctor\021reord"
    "er-init-list\030requires-super-attribute\021reserved-id-macro\023reserv"
    "ed-identifier\031reserved-macro-identifier\032reserved-module-identifie"
    "r\035reserved-user-defined-literal\022restrict-expansion\031retained-la"
    "nguage-linkage\021return-local-addr\024return-stack-address\017return-s"
    "td-move\013return-type\025return-type-c-linkage\020rewrite-not-bool\023"
    "round-trip-cc1-args\004rtti\020sanitize-address\025sarif-format-unstabl"
    "e\021search-path-usage\007section\010selector\026selector-type-mismatch"
    "\013self-assign\021self-assign-field\026self-assign-overloaded\tself-mo"
    "ve\034semicolon-before-method-body\010sentinel\016sequence-point\026ser"
    "ialized-diagnostics\006shadow\nshadow-all\014shadow-field\033shadow-fie"
    "ld-in-constructor$shadow-field-in-constructor-modified\013shadow-ivar\027"
    "shadow-uncaptured-local\024shift-count-negative\024shift-count-overflow"
    "\024shift-negative-value\024shift-op-parentheses\016shift-overflow\023s"
    "hift-sign-overflow\020shorten-64-to-32\014sign-compare\017sign-conversi"
    "on\nsign-promo\024signed-enum-bitfield\025signed-unsigned-wchar'single-"
    "bit-bitfield-constant-conversion\025sizeof-array-argument\022sizeof-arr"
    "ay-decay\020sizeof-array-div\022sizeof-pointer-div\030sizeof-pointer-me"
    "maccess\020slash-u-filename\014slh-asm-goto\nsloc-usage\027sometimes-un"
    "initialized\nsource-mgr\022source-uses-openmp\013spir-compat\014spirv-c"
    "ompat\017stack-exhausted\017stack-protector\021static-float-init\020sta"
    "tic-in-inline$static-inline-explicit-instantiation\026static-local-in-i"
    "nline\020static-self-init\023stdlibcxx-not-found\017strict-aliasing\021"
    "strict-aliasing=0\021strict-aliasing=1\021strict-aliasing=2\017strict-o"
    "verflow\021strict-overflow=0\021strict-overflow=1\021strict-overflow=2\021"
    "strict-overflow=3\021strict-overflow=4\021strict-overflow=5\"strict-pot"
    "entially-direct-selector\021strict-prototypes\025strict-selector-match\016"
    "string-compare\024string-concatenation\021string-conversion\020string-p"
    "lus-char\017string-plus-int\024strlcpy-strlcat-size\014strncat-size\033"
    "suggest-destructor-override\020suggest-override\033super-class-method-m"
    "ismatch\020suspicious-bzero\024suspicious-memaccess\024swift-name-attri"
    "bute\006switch\013switch-bool\016switch-default\013switch-enum\016sync-"
    "alignment%sync-fetch-and-nand-semantics-changed\005synth\036target-clon"
    "es-mixed-specifiers\034tautological-bitwise-compare\024tautological-com"
    "pare\035tautological-constant-compare&tautological-constant-in-range-co"
    "mpare*tautological-constant-out-of-range-compare\036tautological-objc-b"
    "ool-compare\034tautological-overlap-compare\034tautological-pointer-com"
    "pare\037tautological-type-limit-compare\036tautological-undefined-compa"
    "re'tautological-unsigned-char-zero-compare'tautological-unsigned-enum-z"
    "ero-compare\"tautological-unsigned-zero-compare tautological-value-rang"
    "e-compare\017tcb-enforcement$tentative-definition-incomplete-type\015th"
    "read-safety\026thread-safety-analysis\030thread-safety-attributes\022th"
    "read-safety-beta\026thread-safety-negative\025thread-safety-precise\027"
    "thread-safety-reference\025thread-safety-verbose\ttrigraphs\013type-lim"
    "its\013type-safety\024typedef-redefinition\020typename-missing\031unabl"
    "e-to-open-stats-file\020unaligned-access!unaligned-qualifier-implicit-c"
    "ast\030unavailable-declarations\023undeclared-selector\005undef\014unde"
    "f-prefix\031undefined-bool-conversion\027undefined-func-template\020und"
    "efined-inline\022undefined-internal\027undefined-internal-type\032undef"
    "ined-reinterpret-cast\026undefined-var-template\035underaligned-excepti"
    "on-object\026unevaluated-expression\026unguarded-availability\032unguar"
    "ded-availability-new\007unicode\021unicode-homoglyph\022unicode-whitesp"
    "ace\022unicode-zero-width\015uninitialized\035uninitialized-const-refer"
    "ence\020unknown-argument\022unknown-assumption\022unknown-attributes\024"
    "unknown-cuda-version\022unknown-directives\027unknown-escape-sequence\017"
    "unknown-pragmas\022unknown-sanitizers\026unknown-warning-option\032unna"
    "med-type-template-args\035unneeded-internal-declaration\030unneeded-mem"
    "ber-function\031unqualified-std-cast-call\020unreachable-code\033unreac"
    "hable-code-aggressive\026unreachable-code-break\034unreachable-code-fal"
    "lthrough\036unreachable-code-generic-assoc\037unreachable-code-loop-inc"
    "rement\027unreachable-code-return\023unsafe-buffer-usage\013unsequenced"
    "\017unsupported-abi\017unsupported-abs\036unsupported-availability-guar"
    "d\016unsupported-cb#unsupported-dll-base-class-template\036unsupported-"
    "floating-point-opt\022unsupported-friend\021unsupported-gpopt\017unsupp"
    "orted-nan\026unsupported-target-opt\026unsupported-visibility\037unusab"
    "le-partial-specialization\006unused\017unused-argument\030unused-but-se"
    "t-parameter\027unused-but-set-variable\034unused-command-line-argument\021"
    "unused-comparison\025unused-const-variable\032unused-exception-paramete"
    "r\017unused-function\032unused-getter-return-value\014unused-label\025u"
    "nused-lambda-capture\024unused-local-typedef\025unused-local-typedefs\015"
    "unused-macros\026unused-member-function\020unused-parameter\024unused-p"
    "rivate-field\024unused-property-ivar\015unused-result\017unused-templat"
    "e\014unused-value\017unused-variable\026unused-volatile-lvalue\026used-"
    "but-marked-unused\025user-defined-literals\025user-defined-warnings\007"
    "varargs\017variadic-macros\015vec-elem-size\021vector-conversion\022vec"
    "tor-conversions\014vexing-parse\nvisibility\003vla\015vla-extension\031"
    "void-pointer-to-enum-cast\030void-pointer-to-int-cast\024void-ptr-deref"
    "erence\025volatile-register-var\023wasm-exception-spec\025weak-template"
    "-vtables\014weak-vtables\020writable-strings\015write-strings\017xor-us"
    "ed-as-pow\035zero-as-null-pointer-constant\021zero-length-array"};

#endif // GET_DIAG_ARRAYS


#ifdef DIAG_ENTRY
DIAG_ENTRY(anonymous_55 /*  */, 0, 0, /* DiagSubGroup0 */ 1, R"()")
DIAG_ENTRY(PoundPragmaMessage /* #pragma-messages */, 1, /* DiagArray1 */ 1, 0, R"()")
DIAG_ENTRY(PoundWarning /* #warnings */, 18, /* DiagArray2 */ 3, 0, R"()")
DIAG_ENTRY(anonymous_260 /* CFString-literal */, 28, /* DiagArray3 */ 5, 0, R"()")
DIAG_ENTRY(anonymous_54 /* CL4 */, 45, 0, /* DiagSubGroup4 */ 3, R"()")
DIAG_ENTRY(IndependentClassAttribute /* IndependentClass-attribute */, 49, /* DiagArray5 */ 7, 0, R"()")
DIAG_ENTRY(NSobjectAttribute /* NSObject-attribute */, 76, /* DiagArray6 */ 10, 0, R"()")
DIAG_ENTRY(anonymous_0 /* abi */, 95, 0, 0, R"()")
DIAG_ENTRY(AbsoluteValue /* absolute-value */, 99, /* DiagArray8 */ 12, 0, R"()")
DIAG_ENTRY(AbstractFinalClass /* abstract-final-class */, 114, /* DiagArray9 */ 17, 0, R"()")
DIAG_ENTRY(anonymous_253 /* abstract-vbase-init */, 135, /* DiagArray10 */ 19, 0, R"()")
DIAG_ENTRY(anonymous_53 /* address */, 155, 0, /* DiagSubGroup11 */ 6, R"()")
DIAG_ENTRY(anonymous_231 /* address-of-packed-member */, 163, /* DiagArray12 */ 21, 0, R"()")
DIAG_ENTRY(AddressOfTemporary /* address-of-temporary */, 188, /* DiagArray13 */ 23, 0, R"()")
DIAG_ENTRY(anonymous_1 /* aggregate-return */, 209, 0, 0, R"()")
DIAG_ENTRY(AIXCompat /* aix-compat */, 226, /* DiagArray15 */ 25, 0, R"()")
DIAG_ENTRY(anonymous_232 /* align-mismatch */, 237, /* DiagArray16 */ 28, 0, R"()")
DIAG_ENTRY(All /* all */, 252, 0, /* DiagSubGroup17 */ 10, R"()")
DIAG_ENTRY(anonymous_177 /* alloca */, 256, /* DiagArray18 */ 30, 0, R"()")
DIAG_ENTRY(anonymous_178 /* alloca-with-align-alignof */, 263, /* DiagArray19 */ 32, 0, R"()")
DIAG_ENTRY(AlwaysInlineCoroutine /* always-inline-coroutine */, 289, /* DiagArray20 */ 34, 0, R"()")
DIAG_ENTRY(anonymous_238 /* ambiguous-delete */, 313, /* DiagArray21 */ 36, 0, R"()")
DIAG_ENTRY(anonymous_133 /* ambiguous-ellipsis */, 330, /* DiagArray22 */ 38, 0, R"()")
DIAG_ENTRY(AmbiguousMacro /* ambiguous-macro */, 349, /* DiagArray23 */ 40, 0, R"()")
DIAG_ENTRY(AmbigMemberTemplate /* ambiguous-member-template */, 365, /* DiagArray24 */ 42, 0, R"()")
DIAG_ENTRY(anonymous_198 /* ambiguous-reversed-operator */, 391, /* DiagArray25 */ 44, 0, R"()")
DIAG_ENTRY(anonymous_92 /* analyzer-incompatible-plugin */, 419, /* DiagArray26 */ 46, 0, R"()")
DIAG_ENTRY(AnonEnumEnumConversion /* anon-enum-enum-conversion */, 448, /* DiagArray27 */ 48, /* DiagSubGroup27 */ 16, R"()")
DIAG_ENTRY(anonymous_134 /* anonymous-pack-parens */, 474, /* DiagArray28 */ 50, 0, R"()")
DIAG_ENTRY(AutomaticReferenceCounting /* arc */, 496, 0, /* DiagSubGroup29 */ 18, R"()")
DIAG_ENTRY(anonymous_132 /* arc-bridge-casts-disallowed-in-nonarc */, 500, /* DiagArray30 */ 52, 0, R"()")
DIAG_ENTRY(ARCRepeatedUseOfWeakMaybe /* arc-maybe-repeated-use-of-weak */, 538, /* DiagArray31 */ 54, 0, R"()")
DIAG_ENTRY(ARCNonPodMemAccess /* arc-non-pod-memaccess */, 569, /* DiagArray32 */ 56, 0, R"()")
DIAG_ENTRY(anonymous_162 /* arc-performSelector-leaks */, 591, /* DiagArray33 */ 58, 0, R"()")
DIAG_ENTRY(ARCRepeatedUseOfWeak /* arc-repeated-use-of-weak */, 617, /* DiagArray34 */ 60, /* DiagSubGroup34 */ 22, R"()")
DIAG_ENTRY(ARCRetainCycles /* arc-retain-cycles */, 642, /* DiagArray35 */ 62, 0, R"()")
DIAG_ENTRY(ARCUnsafeRetainedAssign /* arc-unsafe-retained-assign */, 660, /* DiagArray36 */ 64, 0, R"()")
DIAG_ENTRY(anonymous_262 /* argument-outside-range */, 687, /* DiagArray37 */ 68, 0, R"()")
DIAG_ENTRY(anonymous_263 /* argument-undefined-behaviour */, 710, /* DiagArray38 */ 70, 0, R"()")
DIAG_ENTRY(ArrayBounds /* array-bounds */, 739, /* DiagArray39 */ 72, 0, R"()")
DIAG_ENTRY(ArrayBoundsPointerArithmetic /* array-bounds-pointer-arithmetic */, 752, /* DiagArray40 */ 78, 0, R"()")
DIAG_ENTRY(ArrayParameter /* array-parameter */, 784, /* DiagArray41 */ 81, 0, R"()")
DIAG_ENTRY(ASM /* asm */, 800, 0, /* DiagSubGroup42 */ 24, R"()")
DIAG_ENTRY(ASMOperandWidths /* asm-operand-widths */, 804, /* DiagArray43 */ 83, 0, R"()")
DIAG_ENTRY(anonymous_261 /* assign-enum */, 823, /* DiagArray44 */ 85, 0, R"()")
DIAG_ENTRY(anonymous_149 /* assume */, 835, /* DiagArray45 */ 87, 0, R"()")
DIAG_ENTRY(anonymous_52 /* at-protocol */, 842, 0, 0, R"()")
DIAG_ENTRY(FrameworkHdrAtImport /* atimport-in-framework-header */, 854, /* DiagArray47 */ 89, 0, R"()")
DIAG_ENTRY(anonymous_220 /* atomic-access */, 883, /* DiagArray48 */ 91, 0, R"()")
DIAG_ENTRY(AtomicAlignment /* atomic-alignment */, 897, /* DiagArray49 */ 93, 0, R"()")
DIAG_ENTRY(anonymous_246 /* atomic-implicit-seq-cst */, 914, /* DiagArray50 */ 96, 0, R"()")
DIAG_ENTRY(anonymous_245 /* atomic-memory-ordering */, 938, /* DiagArray51 */ 98, 0, R"()")
DIAG_ENTRY(AtomicProperties /* atomic-properties */, 961, 0, /* DiagSubGroup52 */ 26, R"()")
DIAG_ENTRY(anonymous_157 /* atomic-property-with-user-defined-accessor */, 979, /* DiagArray53 */ 100, 0, R"()")
DIAG_ENTRY(anonymous_194 /* attribute-packed-for-bitfield */, 1022, /* DiagArray54 */ 102, 0, R"()")
DIAG_ENTRY(BackendWarningAttributes /* attribute-warning */, 1052, /* DiagArray55 */ 104, 0, R"()")
DIAG_ENTRY(Attributes /* attributes */, 1070, 0, /* DiagSubGroup56 */ 29, R"()")
DIAG_ENTRY(AutoDisableVptrSanitizer /* auto-disable-vptr-sanitizer */, 1081, /* DiagArray57 */ 106, 0, R"()")
DIAG_ENTRY(anonymous_2 /* auto-import */, 1109, 0, 0, R"()")
DIAG_ENTRY(anonymous_130 /* auto-storage-class */, 1121, /* DiagArray59 */ 108, 0, R"()")
DIAG_ENTRY(anonymous_174 /* auto-var-id */, 1140, /* DiagArray60 */ 110, 0, R"()")
DIAG_ENTRY(Availability /* availability */, 1152, /* DiagArray61 */ 112, 0, R"()")
DIAG_ENTRY(AVRRtlibLinkingQuirks /* avr-rtlib-linking-quirks */, 1165, /* DiagArray62 */ 123, 0, R"()")
DIAG_ENTRY(BackendPlugin /* backend-plugin */, 1190, /* DiagArray63 */ 129, 0, R"()")
DIAG_ENTRY(anonymous_95 /* backslash-newline-escape */, 1205, /* DiagArray64 */ 132, 0, R"()")
DIAG_ENTRY(BadFunctionCast /* bad-function-cast */, 1230, /* DiagArray65 */ 134, 0, R"()")
DIAG_ENTRY(BinaryLiteral /* binary-literal */, 1248, 0, /* DiagSubGroup66 */ 32, R"()")
DIAG_ENTRY(BindToTemporaryCopy /* bind-to-temporary-copy */, 1263, /* DiagArray67 */ 136, /* DiagSubGroup67 */ 36, R"()")
DIAG_ENTRY(anonymous_144 /* binding-in-condition */, 1286, /* DiagArray68 */ 139, 0, R"()")
DIAG_ENTRY(anonymous_137 /* bit-int-extension */, 1307, /* DiagArray69 */ 141, 0, R"()")
DIAG_ENTRY(BitFieldConstantConversion /* bitfield-constant-conversion */, 1325, /* DiagArray70 */ 143, /* DiagSubGroup70 */ 38, R"()")
DIAG_ENTRY(BitFieldEnumConversion /* bitfield-enum-conversion */, 1354, /* DiagArray71 */ 145, 0, R"()")
DIAG_ENTRY(BitFieldWidth /* bitfield-width */, 1379, /* DiagArray72 */ 149, 0, R"()")
DIAG_ENTRY(BitwiseConditionalParentheses /* bitwise-conditional-parentheses */, 1394, /* DiagArray73 */ 151, 0, R"()")
DIAG_ENTRY(BitwiseInsteadOfLogical /* bitwise-instead-of-logical */, 1426, /* DiagArray74 */ 153, 0, R"()")
DIAG_ENTRY(BitwiseOpParentheses /* bitwise-op-parentheses */, 1453, /* DiagArray75 */ 155, 0, R"()")
DIAG_ENTRY(BlockCaptureAutoReleasing /* block-capture-autoreleasing */, 1476, /* DiagArray76 */ 157, 0, R"()")
DIAG_ENTRY(BoolConversion /* bool-conversion */, 1504, /* DiagArray77 */ 159, /* DiagSubGroup77 */ 40, R"()")
DIAG_ENTRY(anonymous_60 /* bool-conversions */, 1520, 0, /* DiagSubGroup78 */ 43, R"()")
DIAG_ENTRY(BoolOperation /* bool-operation */, 1537, /* DiagArray79 */ 161, /* DiagSubGroup79 */ 45, R"()")
DIAG_ENTRY(anonymous_215 /* braced-scalar-init */, 1552, /* DiagArray80 */ 163, 0, R"()")
DIAG_ENTRY(BranchProtection /* branch-protection */, 1571, /* DiagArray81 */ 165, 0, R"()")
DIAG_ENTRY(ObjCBridge /* bridge-cast */, 1589, /* DiagArray82 */ 170, 0, R"()")
DIAG_ENTRY(anonymous_180 /* builtin-assume-aligned-alignment */, 1601, /* DiagArray83 */ 173, 0, R"()")
DIAG_ENTRY(BuiltinMacroRedefined /* builtin-macro-redefined */, 1634, /* DiagArray84 */ 175, 0, R"()")
DIAG_ENTRY(anonymous_150 /* builtin-memcpy-chk-size */, 1658, /* DiagArray85 */ 178, 0, R"()")
DIAG_ENTRY(BuiltinRequiresHeader /* builtin-requires-header */, 1682, /* DiagArray86 */ 180, 0, R"()")
DIAG_ENTRY(CXXCompat /* c++-compat */, 1706, /* DiagArray87 */ 182, 0, R"()")
DIAG_ENTRY(anonymous_19 /* c++0x-compat */, 1717, 0, /* DiagSubGroup88 */ 47, R"()")
DIAG_ENTRY(anonymous_64 /* c++0x-extensions */, 1730, 0, /* DiagSubGroup89 */ 49, R"()")
DIAG_ENTRY(anonymous_17 /* c++0x-narrowing */, 1747, 0, /* DiagSubGroup90 */ 51, R"()")
DIAG_ENTRY(CXX11Compat /* c++11-compat */, 1763, /* DiagArray91 */ 184, /* DiagSubGroup91 */ 53, R"()")
DIAG_ENTRY(CXX11CompatDeprecatedWritableStr /* c++11-compat-deprecated-writable-strings */, 1776, /* DiagArray92 */ 195, 0, R"()")
DIAG_ENTRY(CXX11CompatPedantic /* c++11-compat-pedantic */, 1817, 0, /* DiagSubGroup93 */ 61, R"()")
DIAG_ENTRY(CXX11CompatReservedUserDefinedLiteral /* c++11-compat-reserved-user-defined-literal */, 1839, /* DiagArray94 */ 197, 0, R"()")
DIAG_ENTRY(CXX11 /* c++11-extensions */, 1882, /* DiagArray95 */ 199, /* DiagSubGroup95 */ 67, R"()")
DIAG_ENTRY(CXX11ExtraSemi /* c++11-extra-semi */, 1899, /* DiagArray96 */ 225, 0, R"()")
DIAG_ENTRY(CXX11InlineNamespace /* c++11-inline-namespace */, 1916, /* DiagArray97 */ 227, 0, R"()")
DIAG_ENTRY(CXX11LongLong /* c++11-long-long */, 1939, /* DiagArray98 */ 229, 0, R"()")
DIAG_ENTRY(CXX11Narrowing /* c++11-narrowing */, 1955, /* DiagArray99 */ 231, 0, R"()")
DIAG_ENTRY(CXX14Attrs /* c++14-attribute-extensions */, 1971, /* DiagArray100 */ 239, 0, R"()")
DIAG_ENTRY(CXX14BinaryLiteral /* c++14-binary-literal */, 1998, /* DiagArray101 */ 241, 0, R"()")
DIAG_ENTRY(CXX14Compat /* c++14-compat */, 2019, 0, /* DiagSubGroup102 */ 71, R"()")
DIAG_ENTRY(CXX14CompatPedantic /* c++14-compat-pedantic */, 2032, 0, /* DiagSubGroup103 */ 75, R"()")
DIAG_ENTRY(CXX14 /* c++14-extensions */, 2054, /* DiagArray104 */ 243, /* DiagSubGroup104 */ 80, R"()")
DIAG_ENTRY(CXX17Attrs /* c++17-attribute-extensions */, 2071, /* DiagArray105 */ 251, 0, R"()")
DIAG_ENTRY(CXX17Compat /* c++17-compat */, 2098, 0, /* DiagSubGroup106 */ 83, R"()")
DIAG_ENTRY(CXX17CompatMangling /* c++17-compat-mangling */, 2111, /* DiagArray107 */ 253, 0, R"()")
DIAG_ENTRY(CXX17CompatPedantic /* c++17-compat-pedantic */, 2133, 0, /* DiagSubGroup108 */ 89, R"()")
DIAG_ENTRY(CXX17 /* c++17-extensions */, 2155, /* DiagArray109 */ 255, /* DiagSubGroup109 */ 93, R"()")
DIAG_ENTRY(anonymous_65 /* c++1y-extensions */, 2172, 0, /* DiagSubGroup110 */ 95, R"()")
DIAG_ENTRY(anonymous_20 /* c++1z-compat */, 2189, 0, /* DiagSubGroup111 */ 97, R"()")
DIAG_ENTRY(anonymous_10 /* c++1z-compat-mangling */, 2202, 0, /* DiagSubGroup112 */ 99, R"()")
DIAG_ENTRY(anonymous_66 /* c++1z-extensions */, 2224, 0, /* DiagSubGroup113 */ 101, R"()")
DIAG_ENTRY(CXX20Attrs /* c++20-attribute-extensions */, 2241, /* DiagArray114 */ 273, 0, R"()")
DIAG_ENTRY(CXX20Compat /* c++20-compat */, 2268, /* DiagArray115 */ 275, /* DiagSubGroup115 */ 103, R"()")
DIAG_ENTRY(CXX20CompatPedantic /* c++20-compat-pedantic */, 2281, 0, /* DiagSubGroup116 */ 105, R"()")
DIAG_ENTRY(CXX20Designator /* c++20-designator */, 2303, /* DiagArray117 */ 285, 0, R"()")
DIAG_ENTRY(CXX20 /* c++20-extensions */, 2320, /* DiagArray118 */ 287, /* DiagSubGroup118 */ 108, R"()")
DIAG_ENTRY(anonymous_258 /* c++23-default-comp-relaxed-constexpr */, 2337, /* DiagArray119 */ 309, 0, R"()")
DIAG_ENTRY(CXX23 /* c++23-extensions */, 2374, /* DiagArray120 */ 311, 0, R"()")
DIAG_ENTRY(anonymous_21 /* c++2a-compat */, 2391, 0, /* DiagSubGroup121 */ 111, R"()")
DIAG_ENTRY(anonymous_22 /* c++2a-compat-pedantic */, 2404, 0, /* DiagSubGroup122 */ 113, R"()")
DIAG_ENTRY(anonymous_67 /* c++2a-extensions */, 2426, 0, /* DiagSubGroup123 */ 115, R"()")
DIAG_ENTRY(anonymous_15 /* c++98-c++11-c++14-c++17-compat */, 2443, 0, /* DiagSubGroup124 */ 117, R"()")
DIAG_ENTRY(anonymous_16 /* c++98-c++11-c++14-c++17-compat-pedantic */, 2474, 0, /* DiagSubGroup125 */ 119, R"()")
DIAG_ENTRY(anonymous_13 /* c++98-c++11-c++14-compat */, 2514, 0, /* DiagSubGroup126 */ 121, R"()")
DIAG_ENTRY(anonymous_14 /* c++98-c++11-c++14-compat-pedantic */, 2539, 0, /* DiagSubGroup127 */ 123, R"()")
DIAG_ENTRY(anonymous_11 /* c++98-c++11-compat */, 2573, 0, /* DiagSubGroup128 */ 125, R"()")
DIAG_ENTRY(CXXPre14CompatBinaryLiteral /* c++98-c++11-compat-binary-literal */, 2592, /* DiagArray129 */ 323, 0, R"()")
DIAG_ENTRY(anonymous_12 /* c++98-c++11-compat-pedantic */, 2626, 0, /* DiagSubGroup130 */ 127, R"()")
DIAG_ENTRY(CXX98Compat /* c++98-compat */, 2654, /* DiagArray131 */ 325, /* DiagSubGroup131 */ 129, R"()")
DIAG_ENTRY(CXX98CompatBindToTemporaryCopy /* c++98-compat-bind-to-temporary-copy */, 2667, /* DiagArray132 */ 385, 0, R"()")
DIAG_ENTRY(CXX98CompatExtraSemi /* c++98-compat-extra-semi */, 2703, /* DiagArray133 */ 387, 0, R"()")
DIAG_ENTRY(CXX98CompatLocalTypeTemplateArgs /* c++98-compat-local-type-template-args */, 2727, /* DiagArray134 */ 389, 0, R"()")
DIAG_ENTRY(CXX98CompatPedantic /* c++98-compat-pedantic */, 2765, /* DiagArray135 */ 391, /* DiagSubGroup135 */ 136, R"()")
DIAG_ENTRY(CXX98CompatUnnamedTypeTemplateArgs /* c++98-compat-unnamed-type-template-args */, 2787, /* DiagArray136 */ 401, 0, R"()")
DIAG_ENTRY(C11 /* c11-extensions */, 2827, /* DiagArray137 */ 403, 0, R"()")
DIAG_ENTRY(C2xCompat /* c2x-compat */, 2842, /* DiagArray138 */ 408, 0, R"()")
DIAG_ENTRY(C2x /* c2x-extensions */, 2853, /* DiagArray139 */ 410, 0, R"()")
DIAG_ENTRY(C99Compat /* c99-compat */, 2868, /* DiagArray140 */ 418, 0, R"()")
DIAG_ENTRY(C99Designator /* c99-designator */, 2879, /* DiagArray141 */ 423, /* DiagSubGroup141 */ 144, R"()")
DIAG_ENTRY(C99 /* c99-extensions */, 2894, /* DiagArray142 */ 429, /* DiagSubGroup142 */ 146, R"()")
DIAG_ENTRY(PureVirtualCallFromCtorDtor /* call-to-pure-virtual-from-ctor-dtor */, 2909, /* DiagArray143 */ 440, 0, R"()")
DIAG_ENTRY(CalledOnceParameter /* called-once-parameter */, 2945, /* DiagArray144 */ 442, /* DiagSubGroup144 */ 148, R"()")
DIAG_ENTRY(CastAlign /* cast-align */, 2967, /* DiagArray145 */ 446, 0, R"()")
DIAG_ENTRY(anonymous_248 /* cast-calling-convention */, 2978, /* DiagArray146 */ 448, 0, R"()")
DIAG_ENTRY(CastFunctionType /* cast-function-type */, 3002, /* DiagArray147 */ 450, /* DiagSubGroup147 */ 150, R"()")
DIAG_ENTRY(CastFunctionTypeStrict /* cast-function-type-strict */, 3021, /* DiagArray148 */ 452, 0, R"()")
DIAG_ENTRY(SelTypeCast /* cast-of-sel-type */, 3047, /* DiagArray149 */ 454, 0, R"()")
DIAG_ENTRY(CastQual /* cast-qual */, 3064, /* DiagArray150 */ 456, 0, R"()")
DIAG_ENTRY(anonymous_236 /* cast-qual-unrelated */, 3074, /* DiagArray151 */ 459, 0, R"()")
DIAG_ENTRY(anonymous_3 /* char-align */, 3094, 0, 0, R"()")
DIAG_ENTRY(CharSubscript /* char-subscripts */, 3105, /* DiagArray153 */ 461, 0, R"()")
DIAG_ENTRY(ClangClPch /* clang-cl-pch */, 3121, /* DiagArray154 */ 464, 0, R"()")
DIAG_ENTRY(ClassConversion /* class-conversion */, 3134, /* DiagArray155 */ 469, 0, R"()")
DIAG_ENTRY(ClassVarargs /* class-varargs */, 3151, /* DiagArray156 */ 473, /* DiagSubGroup156 */ 152, R"()")
DIAG_ENTRY(anonymous_181 /* cmse-union-leak */, 3165, /* DiagArray157 */ 475, 0, R"()")
DIAG_ENTRY(anonymous_139 /* comma */, 3181, /* DiagArray158 */ 477, 0, R"()")
DIAG_ENTRY(Comment /* comment */, 3187, /* DiagArray159 */ 479, 0, R"()")
DIAG_ENTRY(anonymous_58 /* comments */, 3195, 0, /* DiagSubGroup160 */ 154, R"()")
DIAG_ENTRY(CompareDistinctPointerType /* compare-distinct-pointer-types */, 3204, /* DiagArray161 */ 484, 0, R"()")
DIAG_ENTRY(CompletionHandler /* completion-handler */, 3235, /* DiagArray162 */ 486, 0, R"()")
DIAG_ENTRY(anonymous_217 /* complex-component-init */, 3254, /* DiagArray163 */ 490, 0, R"()")
DIAG_ENTRY(CompoundTokenSplit /* compound-token-split */, 3277, 0, /* DiagSubGroup164 */ 156, R"()")
DIAG_ENTRY(CompoundTokenSplitByMacro /* compound-token-split-by-macro */, 3298, /* DiagArray165 */ 492, 0, R"()")
DIAG_ENTRY(CompoundTokenSplitBySpace /* compound-token-split-by-space */, 3328, /* DiagArray166 */ 494, 0, R"()")
DIAG_ENTRY(anonymous_250 /* conditional-type-mismatch */, 3358, /* DiagArray167 */ 496, 0, R"()")
DIAG_ENTRY(UninitializedMaybe /* conditional-uninitialized */, 3384, /* DiagArray168 */ 498, 0, R"()")
DIAG_ENTRY(ConfigMacros /* config-macros */, 3410, /* DiagArray169 */ 500, 0, R"()")
DIAG_ENTRY(ConstantConversion /* constant-conversion */, 3424, /* DiagArray170 */ 502, /* DiagSubGroup170 */ 159, R"()")
DIAG_ENTRY(anonymous_74 /* constant-evaluated */, 3444, /* DiagArray171 */ 504, 0, R"()")
DIAG_ENTRY(anonymous_227 /* constant-logical-operand */, 3463, /* DiagArray172 */ 506, 0, R"()")
DIAG_ENTRY(anonymous_172 /* constexpr-not-const */, 3488, /* DiagArray173 */ 508, 0, R"()")
DIAG_ENTRY(Consumed /* consumed */, 3508, /* DiagArray174 */ 510, 0, R"()")
DIAG_ENTRY(Conversion /* conversion */, 3517, /* DiagArray175 */ 519, /* DiagSubGroup175 */ 162, R"()")
DIAG_ENTRY(anonymous_59 /* conversion-null */, 3528, 0, /* DiagSubGroup176 */ 178, R"()")
DIAG_ENTRY(CoroNonAlignedAllocationFunction /* coro-non-aligned-allocation-function */, 3544, /* DiagArray177 */ 525, 0, R"()")
DIAG_ENTRY(Coroutine /* coroutine */, 3581, /* DiagArray178 */ 527, /* DiagSubGroup178 */ 180, R"()")
DIAG_ENTRY(CoroutineMissingUnhandledException /* coroutine-missing-unhandled-exception */, 3591, /* DiagArray179 */ 529, 0, R"()")
DIAG_ENTRY(CoveredSwitchDefault /* covered-switch-default */, 3629, /* DiagArray180 */ 531, 0, R"()")
DIAG_ENTRY(anonymous_57 /* cpp */, 3652, 0, /* DiagSubGroup181 */ 185, R"()")
DIAG_ENTRY(ObjCCStringFormat /* cstring-format-directive */, 3656, /* DiagArray182 */ 533, 0, R"()")
DIAG_ENTRY(CTADMaybeUnsupported /* ctad-maybe-unsupported */, 3681, /* DiagArray183 */ 535, 0, R"()")
DIAG_ENTRY(anonymous_4 /* ctor-dtor-privacy */, 3704, 0, 0, R"()")
DIAG_ENTRY(CrossTU /* ctu */, 3722, /* DiagArray185 */ 537, 0, R"()")
DIAG_ENTRY(CudaCompat /* cuda-compat */, 3726, /* DiagArray186 */ 539, 0, R"()")
DIAG_ENTRY(CustomAtomic /* custom-atomic-properties */, 3738, /* DiagArray187 */ 545, 0, R"()")
DIAG_ENTRY(anonymous_135 /* cxx-attribute-extension */, 3763, /* DiagArray188 */ 547, 0, R"()")
DIAG_ENTRY(Dangling /* dangling */, 3787, /* DiagArray189 */ 549, /* DiagSubGroup189 */ 187, R"()")
DIAG_ENTRY(DanglingElse /* dangling-else */, 3796, /* DiagArray190 */ 552, 0, R"()")
DIAG_ENTRY(DanglingField /* dangling-field */, 3810, /* DiagArray191 */ 554, 0, R"()")
DIAG_ENTRY(DanglingGsl /* dangling-gsl */, 3825, /* DiagArray192 */ 559, 0, R"()")
DIAG_ENTRY(DanglingInitializerList /* dangling-initializer-list */, 3838, /* DiagArray193 */ 562, 0, R"()")
DIAG_ENTRY(anonymous_87 /* darwin-sdk-settings */, 3864, /* DiagArray194 */ 564, 0, R"()")
DIAG_ENTRY(anonymous_120 /* date-time */, 3884, /* DiagArray195 */ 566, 0, R"()")
DIAG_ENTRY(DeallocInCategory /* dealloc-in-category */, 3894, /* DiagArray196 */ 568, 0, R"()")
DIAG_ENTRY(anonymous_84 /* debug-compression-unavailable */, 3914, /* DiagArray197 */ 570, 0, R"()")
DIAG_ENTRY(DeclarationAfterStatement /* declaration-after-statement */, 3944, /* DiagArray198 */ 572, 0, R"()")
DIAG_ENTRY(DefaultedFunctionDeleted /* defaulted-function-deleted */, 3972, /* DiagArray199 */ 575, 0, R"()")
DIAG_ENTRY(DelegatingCtorCycles /* delegating-ctor-cycles */, 3999, /* DiagArray200 */ 578, 0, R"()")
DIAG_ENTRY(DeleteAbstractNonVirtualDtor /* delete-abstract-non-virtual-dtor */, 4022, /* DiagArray201 */ 580, 0, R"()")
DIAG_ENTRY(DeleteIncomplete /* delete-incomplete */, 4055, /* DiagArray202 */ 582, 0, R"()")
DIAG_ENTRY(DeleteNonAbstractNonVirtualDtor /* delete-non-abstract-non-virtual-dtor */, 4073, /* DiagArray203 */ 585, 0, R"()")
DIAG_ENTRY(DeleteNonVirtualDtor /* delete-non-virtual-dtor */, 4110, 0, /* DiagSubGroup204 */ 192, R"()")
DIAG_ENTRY(anonymous_103 /* delimited-escape-sequence-extension */, 4134, /* DiagArray205 */ 587, 0, R"()")
DIAG_ENTRY(anonymous_240 /* deprecate-lax-vec-conv-all */, 4170, /* DiagArray206 */ 589, 0, R"()")
DIAG_ENTRY(Deprecated /* deprecated */, 4197, /* DiagArray207 */ 591, /* DiagSubGroup207 */ 195, R"()")
DIAG_ENTRY(anonymous_239 /* deprecated-altivec-src-compat */, 4208, /* DiagArray208 */ 599, 0, R"()")
DIAG_ENTRY(DeprecatedAnonEnumEnumConversion /* deprecated-anon-enum-enum-conversion */, 4238, /* DiagArray209 */ 601, 0, R"()")
DIAG_ENTRY(DeprecatedArrayCompare /* deprecated-array-compare */, 4275, /* DiagArray210 */ 603, 0, R"()")
DIAG_ENTRY(DeprecatedAttributes /* deprecated-attributes */, 4300, /* DiagArray211 */ 605, 0, R"()")
DIAG_ENTRY(DeprecatedBuiltins /* deprecated-builtins */, 4322, /* DiagArray212 */ 609, 0, R"()")
DIAG_ENTRY(DeprecatedCommaSubscript /* deprecated-comma-subscript */, 4342, /* DiagArray213 */ 611, 0, R"()")
DIAG_ENTRY(DeprecatedCopy /* deprecated-copy */, 4369, /* DiagArray214 */ 613, /* DiagSubGroup214 */ 216, R"()")
DIAG_ENTRY(anonymous_5 /* deprecated-copy-dtor */, 4385, 0, /* DiagSubGroup215 */ 218, R"()")
DIAG_ENTRY(DeprecatedCopyWithDtor /* deprecated-copy-with-dtor */, 4406, /* DiagArray216 */ 615, /* DiagSubGroup216 */ 220, R"()")
DIAG_ENTRY(DeprecatedCopyWithUserProvidedCopy /* deprecated-copy-with-user-provided-copy */, 4432, /* DiagArray217 */ 617, 0, R"()")
DIAG_ENTRY(DeprecatedCopyWithUserProvidedDtor /* deprecated-copy-with-user-provided-dtor */, 4472, /* DiagArray218 */ 619, 0, R"()")
DIAG_ENTRY(DeprecatedCoroutine /* deprecated-coroutine */, 4512, /* DiagArray219 */ 621, 0, R"()")
DIAG_ENTRY(DeprecatedDeclarations /* deprecated-declarations */, 4533, /* DiagArray220 */ 623, 0, R"()")
DIAG_ENTRY(DeprecatedDynamicExceptionSpec /* deprecated-dynamic-exception-spec */, 4557, /* DiagArray221 */ 630, 0, R"()")
DIAG_ENTRY(DeprecatedEnumCompare /* deprecated-enum-compare */, 4591, /* DiagArray222 */ 632, 0, R"()")
DIAG_ENTRY(DeprecatedEnumCompareConditional /* deprecated-enum-compare-conditional */, 4615, /* DiagArray223 */ 634, 0, R"()")
DIAG_ENTRY(DeprecatedEnumEnumConversion /* deprecated-enum-enum-conversion */, 4651, /* DiagArray224 */ 636, 0, R"()")
DIAG_ENTRY(DeprecatedEnumFloatConversion /* deprecated-enum-float-conversion */, 4683, /* DiagArray225 */ 638, 0, R"()")
DIAG_ENTRY(DeprecatedImplementations /* deprecated-implementations */, 4716, /* DiagArray226 */ 640, 0, R"()")
DIAG_ENTRY(DeprecatedIncrementBool /* deprecated-increment-bool */, 4743, /* DiagArray227 */ 643, 0, R"()")
DIAG_ENTRY(DeprecatedNonPrototype /* deprecated-non-prototype */, 4769, /* DiagArray228 */ 645, 0, R"()")
DIAG_ENTRY(DeprecatedObjCIsaUsage /* deprecated-objc-isa-usage */, 4794, /* DiagArray229 */ 648, 0, R"()")
DIAG_ENTRY(ObjCPointerIntrospect /* deprecated-objc-pointer-introspection */, 4820, /* DiagArray230 */ 651, /* DiagSubGroup230 */ 222, R"()")
DIAG_ENTRY(ObjCPointerIntrospectPerformSelector /* deprecated-objc-pointer-introspection-performSelector */, 4858, /* DiagArray231 */ 653, 0, R"()")
DIAG_ENTRY(DeprecatedPragma /* deprecated-pragma */, 4912, /* DiagArray232 */ 655, 0, R"()")
DIAG_ENTRY(DeprecatedRegister /* deprecated-register */, 4930, /* DiagArray233 */ 657, 0, R"()")
DIAG_ENTRY(DeprecatedStaticAnalyzerFlag /* deprecated-static-analyzer-flag */, 4950, /* DiagArray234 */ 659, 0, R"()")
DIAG_ENTRY(DeprecatedThisCapture /* deprecated-this-capture */, 4982, /* DiagArray235 */ 662, 0, R"()")
DIAG_ENTRY(DeprecatedType /* deprecated-type */, 5006, /* DiagArray236 */ 664, 0, R"()")
DIAG_ENTRY(DeprecatedVolatile /* deprecated-volatile */, 5022, /* DiagArray237 */ 666, 0, R"()")
DIAG_ENTRY(DeprecatedWritableStr /* deprecated-writable-strings */, 5042, 0, /* DiagSubGroup238 */ 224, R"()")
DIAG_ENTRY(anonymous_267 /* direct-ivar-access */, 5070, /* DiagArray239 */ 672, 0, R"()")
DIAG_ENTRY(anonymous_111 /* disabled-macro-expansion */, 5089, /* DiagArray240 */ 674, 0, R"()")
DIAG_ENTRY(anonymous_7 /* disabled-optimization */, 5114, 0, 0, R"()")
DIAG_ENTRY(anonymous_8 /* discard-qual */, 5136, 0, 0, R"()")
DIAG_ENTRY(DistributedObjectModifiers /* distributed-object-modifiers */, 5149, /* DiagArray243 */ 676, 0, R"()")
DIAG_ENTRY(anonymous_9 /* div-by-zero */, 5178, 0, /* DiagSubGroup244 */ 226, R"()")
DIAG_ENTRY(DivZero /* division-by-zero */, 5190, /* DiagArray245 */ 679, 0, R"()")
DIAG_ENTRY(anonymous_182 /* dll-attribute-on-redeclaration */, 5207, /* DiagArray246 */ 681, 0, R"()")
DIAG_ENTRY(DllexportExplicitInstantiationDecl /* dllexport-explicit-instantiation-decl */, 5238, /* DiagArray247 */ 683, 0, R"()")
DIAG_ENTRY(anonymous_183 /* dllimport-static-field-def */, 5276, /* DiagArray248 */ 685, 0, R"()")
DIAG_ENTRY(Documentation /* documentation */, 5303, /* DiagArray249 */ 687, /* DiagSubGroup249 */ 228, R"()")
DIAG_ENTRY(DocumentationDeprecatedSync /* documentation-deprecated-sync */, 5317, /* DiagArray250 */ 708, 0, R"()")
DIAG_ENTRY(DocumentationHTML /* documentation-html */, 5347, /* DiagArray251 */ 710, 0, R"()")
DIAG_ENTRY(DocumentationPedantic /* documentation-pedantic */, 5366, /* DiagArray252 */ 715, /* DiagSubGroup252 */ 231, R"()")
DIAG_ENTRY(DocumentationUnknownCommand /* documentation-unknown-command */, 5389, /* DiagArray253 */ 717, 0, R"()")
DIAG_ENTRY(anonymous_96 /* dollar-in-identifier-extension */, 5419, /* DiagArray254 */ 720, 0, R"()")
DIAG_ENTRY(DoublePromotion /* double-promotion */, 5450, /* DiagArray255 */ 722, 0, R"()")
DIAG_ENTRY(DtorName /* dtor-name */, 5467, /* DiagArray256 */ 724, 0, R"()")
DIAG_ENTRY(anonymous_171 /* dtor-typedef */, 5477, /* DiagArray257 */ 728, 0, R"()")
DIAG_ENTRY(DuplicateDeclSpecifier /* duplicate-decl-specifier */, 5490, /* DiagArray258 */ 730, 0, R"()")
DIAG_ENTRY(anonymous_138 /* duplicate-enum */, 5515, /* DiagArray259 */ 735, 0, R"()")
DIAG_ENTRY(DuplicateArgDecl /* duplicate-method-arg */, 5530, /* DiagArray260 */ 737, 0, R"()")
DIAG_ENTRY(MethodDuplicate /* duplicate-method-match */, 5551, /* DiagArray261 */ 739, 0, R"()")
DIAG_ENTRY(anonymous_152 /* duplicate-protocol */, 5574, /* DiagArray262 */ 741, 0, R"()")
DIAG_ENTRY(DXILValidation /* dxil-validation */, 5593, /* DiagArray263 */ 743, 0, R"()")
DIAG_ENTRY(DynamicClassMemaccess /* dynamic-class-memaccess */, 5609, /* DiagArray264 */ 745, 0, R"()")
DIAG_ENTRY(DynamicExceptionSpec /* dynamic-exception-spec */, 5633, /* DiagArray265 */ 747, /* DiagSubGroup265 */ 233, R"()")
DIAG_ENTRY(anonymous_275 /* eager-load-cxx-named-modules */, 5656, /* DiagArray266 */ 749, 0, R"()")
DIAG_ENTRY(anonymous_33 /* effc++ */, 5685, 0, /* DiagSubGroup267 */ 235, R"()")
DIAG_ENTRY(anonymous_126 /* elaborated-enum-base */, 5692, /* DiagArray268 */ 751, 0, R"()")
DIAG_ENTRY(anonymous_127 /* elaborated-enum-class */, 5713, /* DiagArray269 */ 753, 0, R"()")
DIAG_ENTRY(anonymous_117 /* embedded-directive */, 5735, /* DiagArray270 */ 755, 0, R"()")
DIAG_ENTRY(EmptyBody /* empty-body */, 5754, /* DiagArray271 */ 757, 0, R"()")
DIAG_ENTRY(anonymous_131 /* empty-decomposition */, 5765, /* DiagArray272 */ 763, 0, R"()")
DIAG_ENTRY(EmptyInitStatement /* empty-init-stmt */, 5785, /* DiagArray273 */ 765, 0, R"()")
DIAG_ENTRY(anonymous_123 /* empty-translation-unit */, 5801, /* DiagArray274 */ 767, 0, R"()")
DIAG_ENTRY(anonymous_186 /* encode-type */, 5824, /* DiagArray275 */ 769, 0, R"()")
DIAG_ENTRY(anonymous_56 /* endif-labels */, 5836, 0, /* DiagSubGroup276 */ 237, R"()")
DIAG_ENTRY(EnumCompare /* enum-compare */, 5849, /* DiagArray277 */ 771, /* DiagSubGroup277 */ 239, R"()")
DIAG_ENTRY(EnumCompareConditional /* enum-compare-conditional */, 5862, /* DiagArray278 */ 773, /* DiagSubGroup278 */ 242, R"()")
DIAG_ENTRY(EnumCompareSwitch /* enum-compare-switch */, 5887, /* DiagArray279 */ 775, 0, R"()")
DIAG_ENTRY(anonymous_73 /* enum-constexpr-conversion */, 5907, /* DiagArray280 */ 777, 0, R"()")
DIAG_ENTRY(EnumConversion /* enum-conversion */, 5933, /* DiagArray281 */ 779, /* DiagSubGroup281 */ 244, R"()")
DIAG_ENTRY(EnumEnumConversion /* enum-enum-conversion */, 5949, /* DiagArray282 */ 781, /* DiagSubGroup282 */ 248, R"()")
DIAG_ENTRY(EnumFloatConversion /* enum-float-conversion */, 5970, /* DiagArray283 */ 783, /* DiagSubGroup283 */ 250, R"()")
DIAG_ENTRY(EnumTooLarge /* enum-too-large */, 5992, /* DiagArray284 */ 785, 0, R"()")
DIAG_ENTRY(Exceptions /* exceptions */, 6007, /* DiagArray285 */ 788, 0, R"()")
DIAG_ENTRY(ExcessInitializers /* excess-initializers */, 6018, /* DiagArray286 */ 792, 0, R"()")
DIAG_ENTRY(ExitTimeDestructors /* exit-time-destructors */, 6038, /* DiagArray287 */ 797, 0, R"()")
DIAG_ENTRY(ExpansionToDefined /* expansion-to-defined */, 6060, /* DiagArray288 */ 799, 0, R"()")
DIAG_ENTRY(ExplicitInitializeCall /* explicit-initialize-call */, 6081, /* DiagArray289 */ 802, 0, R"()")
DIAG_ENTRY(anonymous_219 /* explicit-ownership-type */, 6106, /* DiagArray290 */ 805, 0, R"()")
DIAG_ENTRY(ExportUnnamed /* export-unnamed */, 6130, /* DiagArray291 */ 807, 0, R"()")
DIAG_ENTRY(anonymous_271 /* export-using-directive */, 6145, /* DiagArray292 */ 810, 0, R"()")
DIAG_ENTRY(ExternCCompat /* extern-c-compat */, 6168, /* DiagArray293 */ 812, 0, R"()")
DIAG_ENTRY(anonymous_214 /* extern-initializer */, 6184, /* DiagArray294 */ 814, 0, R"()")
DIAG_ENTRY(Extra /* extra */, 6203, /* DiagArray295 */ 816, /* DiagSubGroup295 */ 252, R"()")
DIAG_ENTRY(anonymous_228 /* extra-qualification */, 6209, /* DiagArray296 */ 818, 0, R"()")
DIAG_ENTRY(ExtraSemi /* extra-semi */, 6229, /* DiagArray297 */ 820, /* DiagSubGroup297 */ 267, R"()")
DIAG_ENTRY(ExtraSemiStmt /* extra-semi-stmt */, 6240, /* DiagArray298 */ 823, /* DiagSubGroup298 */ 270, R"()")
DIAG_ENTRY(ExtraTokens /* extra-tokens */, 6256, /* DiagArray299 */ 825, 0, R"()")
DIAG_ENTRY(FinalDtorNonFinalClass /* final-dtor-non-final-class */, 6269, /* DiagArray300 */ 828, 0, R"()")
DIAG_ENTRY(FinalMacro /* final-macro */, 6296, /* DiagArray301 */ 830, 0, R"()")
DIAG_ENTRY(anonymous_125 /* fixed-enum-extension */, 6308, /* DiagArray302 */ 832, 0, R"()")
DIAG_ENTRY(anonymous_72 /* fixed-point-overflow */, 6329, /* DiagArray303 */ 834, 0, R"()")
DIAG_ENTRY(FlagEnum /* flag-enum */, 6350, /* DiagArray304 */ 836, 0, R"()")
DIAG_ENTRY(FlexibleArrayExtensions /* flexible-array-extensions */, 6360, /* DiagArray305 */ 838, 0, R"()")
DIAG_ENTRY(FloatConversion /* float-conversion */, 6386, /* DiagArray306 */ 841, /* DiagSubGroup306 */ 272, R"()")
DIAG_ENTRY(anonymous_221 /* float-equal */, 6403, /* DiagArray307 */ 843, 0, R"()")
DIAG_ENTRY(FloatOverflowConversion /* float-overflow-conversion */, 6415, /* DiagArray308 */ 845, 0, R"()")
DIAG_ENTRY(FloatZeroConversion /* float-zero-conversion */, 6441, /* DiagArray309 */ 848, 0, R"()")
DIAG_ENTRY(ForLoopAnalysis /* for-loop-analysis */, 6463, /* DiagArray310 */ 850, 0, R"()")
DIAG_ENTRY(Format /* format */, 6481, /* DiagArray311 */ 853, /* DiagSubGroup311 */ 275, R"()")
DIAG_ENTRY(FormatExtraArgs /* format-extra-args */, 6488, /* DiagArray312 */ 880, 0, R"()")
DIAG_ENTRY(FormatInsufficientArgs /* format-insufficient-args */, 6506, /* DiagArray313 */ 882, 0, R"()")
DIAG_ENTRY(FormatInvalidSpecifier /* format-invalid-specifier */, 6531, /* DiagArray314 */ 884, 0, R"()")
DIAG_ENTRY(FormatNonStandard /* format-non-iso */, 6556, /* DiagArray315 */ 886, 0, R"()")
DIAG_ENTRY(FormatNonLiteral /* format-nonliteral */, 6571, /* DiagArray316 */ 890, 0, R"()")
DIAG_ENTRY(FormatPedantic /* format-pedantic */, 6589, /* DiagArray317 */ 892, 0, R"()")
DIAG_ENTRY(FormatSecurity /* format-security */, 6605, /* DiagArray318 */ 895, 0, R"()")
DIAG_ENTRY(FormatTypeConfusion /* format-type-confusion */, 6621, /* DiagArray319 */ 897, 0, R"()")
DIAG_ENTRY(FormatY2K /* format-y2k */, 6643, 0, 0, R"()")
DIAG_ENTRY(FormatZeroLength /* format-zero-length */, 6654, /* DiagArray321 */ 899, 0, R"()")
DIAG_ENTRY(Format2 /* format=2 */, 6673, 0, /* DiagSubGroup322 */ 283, R"()")
DIAG_ENTRY(FortifySource /* fortify-source */, 6682, /* DiagArray323 */ 901, 0, R"()")
DIAG_ENTRY(FourByteMultiChar /* four-char-constants */, 6697, /* DiagArray324 */ 907, 0, R"()")
DIAG_ENTRY(FrameAddress /* frame-address */, 6717, /* DiagArray325 */ 909, 0, R"()")
DIAG_ENTRY(BackendFrameLargerThan /* frame-larger-than */, 6731, /* DiagArray326 */ 911, 0, R"(More fine grained information about the stack layout is available by adding the
`-Rpass-analysis=stack-frame-layout` command-line flag to the compiler
invocation.

The diagnostic information can be saved to a file in a machine readable format,
like YAML by adding the `-foptimization-record-file=<file>` command-line flag.

Results can be filtered by function name by passing 
`-mllvm -filter-print-funcs=foo`, where `foo` is the target function's name.

   .. code-block: console
      clang -c a.cpp -Rpass-analysis=stack-frame-layout -mllvm -filter-print-funcs=foo

   .. code-block: console
      clang -c a.cpp -Rpass-analysis=stack-frame-layout -foptimization-record-file=<file>)")
DIAG_ENTRY(anonymous_69 /* frame-larger-than= */, 6749, 0, /* DiagSubGroup327 */ 287, R"()")
DIAG_ENTRY(FrameworkIncludePrivateFromPublic /* framework-include-private-from-public */, 6768, /* DiagArray328 */ 914, 0, R"()")
DIAG_ENTRY(FreeNonHeapObject /* free-nonheap-object */, 6806, /* DiagArray329 */ 916, 0, R"()")
DIAG_ENTRY(FunctionDefInObjCContainer /* function-def-in-objc-container */, 6826, /* DiagArray330 */ 918, 0, R"()")
DIAG_ENTRY(FunctionMultiVersioning /* function-multiversion */, 6857, /* DiagArray331 */ 920, /* DiagSubGroup331 */ 289, R"()")
DIAG_ENTRY(FUseLdPath /* fuse-ld-path */, 6879, /* DiagArray332 */ 925, 0, R"()")
DIAG_ENTRY(FutureAttrs /* future-attribute-extensions */, 6892, 0, /* DiagSubGroup333 */ 291, R"()")
DIAG_ENTRY(FutureCompat /* future-compat */, 6920, 0, 0, R"()")
DIAG_ENTRY(GccCompat /* gcc-compat */, 6934, /* DiagArray335 */ 927, 0, R"()")
DIAG_ENTRY(GlobalConstructors /* global-constructors */, 6945, /* DiagArray336 */ 939, 0, R"()")
DIAG_ENTRY(GlobalISel /* global-isel */, 6965, /* DiagArray337 */ 942, 0, R"()")
DIAG_ENTRY(GNU /* gnu */, 6977, 0, /* DiagSubGroup338 */ 295, R"()")
DIAG_ENTRY(GNUAlignofExpression /* gnu-alignof-expression */, 6981, /* DiagArray339 */ 945, 0, R"()")
DIAG_ENTRY(GNUAnonymousStruct /* gnu-anonymous-struct */, 7004, /* DiagArray340 */ 947, 0, R"()")
DIAG_ENTRY(anonymous_230 /* gnu-array-member-paren-init */, 7025, /* DiagArray341 */ 949, 0, R"()")
DIAG_ENTRY(GNUAutoType /* gnu-auto-type */, 7053, /* DiagArray342 */ 951, 0, R"()")
DIAG_ENTRY(GNUBinaryLiteral /* gnu-binary-literal */, 7067, /* DiagArray343 */ 953, 0, R"()")
DIAG_ENTRY(GNUCaseRange /* gnu-case-range */, 7086, /* DiagArray344 */ 955, 0, R"()")
DIAG_ENTRY(GNUComplexInteger /* gnu-complex-integer */, 7101, /* DiagArray345 */ 957, 0, R"()")
DIAG_ENTRY(GNUCompoundLiteralInitializer /* gnu-compound-literal-initializer */, 7121, /* DiagArray346 */ 959, 0, R"()")
DIAG_ENTRY(GNUConditionalOmittedOperand /* gnu-conditional-omitted-operand */, 7154, /* DiagArray347 */ 961, 0, R"()")
DIAG_ENTRY(GNUDesignator /* gnu-designator */, 7186, /* DiagArray348 */ 963, 0, R"()")
DIAG_ENTRY(GNUEmptyStruct /* gnu-empty-struct */, 7201, /* DiagArray349 */ 967, 0, R"()")
DIAG_ENTRY(GNUFlexibleArrayInitializer /* gnu-flexible-array-initializer */, 7218, /* DiagArray350 */ 971, 0, R"()")
DIAG_ENTRY(GNUFlexibleArrayUnionMember /* gnu-flexible-array-union-member */, 7249, /* DiagArray351 */ 973, 0, R"()")
DIAG_ENTRY(GNUFoldingConstant /* gnu-folding-constant */, 7281, /* DiagArray352 */ 975, 0, R"()")
DIAG_ENTRY(GNUImaginaryConstant /* gnu-imaginary-constant */, 7302, /* DiagArray353 */ 979, 0, R"()")
DIAG_ENTRY(GNUIncludeNext /* gnu-include-next */, 7325, /* DiagArray354 */ 981, 0, R"()")
DIAG_ENTRY(anonymous_187 /* gnu-inline-cpp-without-extern */, 7342, /* DiagArray355 */ 983, 0, R"()")
DIAG_ENTRY(GNULabelsAsValue /* gnu-label-as-value */, 7372, /* DiagArray356 */ 985, 0, R"()")
DIAG_ENTRY(GNULineMarker /* gnu-line-marker */, 7391, /* DiagArray357 */ 988, 0, R"()")
DIAG_ENTRY(GNUNullPointerArithmetic /* gnu-null-pointer-arithmetic */, 7407, /* DiagArray358 */ 990, 0, R"()")
DIAG_ENTRY(GNUOffsetofExtensions /* gnu-offsetof-extensions */, 7435, /* DiagArray359 */ 992, 0, R"()")
DIAG_ENTRY(GNUPointerArith /* gnu-pointer-arith */, 7459, /* DiagArray360 */ 994, 0, R"()")
DIAG_ENTRY(GNURedeclaredEnum /* gnu-redeclared-enum */, 7477, /* DiagArray361 */ 998, 0, R"()")
DIAG_ENTRY(GNUStatementExpression /* gnu-statement-expression */, 7497, /* DiagArray362 */ 1000, /* DiagSubGroup362 */ 327, R"()")
DIAG_ENTRY(GNUStatementExpressionFromMacroExpansion /* gnu-statement-expression-from-macro-expansion */, 7522, /* DiagArray363 */ 1002, 0, R"()")
DIAG_ENTRY(GNUStaticFloatInit /* gnu-static-float-init */, 7568, /* DiagArray364 */ 1004, 0, R"()")
DIAG_ENTRY(GNUStringLiteralOperatorTemplate /* gnu-string-literal-operator-template */, 7590, /* DiagArray365 */ 1006, 0, R"()")
DIAG_ENTRY(GNUUnionCast /* gnu-union-cast */, 7627, /* DiagArray366 */ 1008, 0, R"()")
DIAG_ENTRY(GNUVariableSizedTypeNotAtEnd /* gnu-variable-sized-type-not-at-end */, 7642, /* DiagArray367 */ 1010, 0, R"()")
DIAG_ENTRY(GNUZeroLineDirective /* gnu-zero-line-directive */, 7677, /* DiagArray368 */ 1012, 0, R"()")
DIAG_ENTRY(GNUZeroVariadicMacroArguments /* gnu-zero-variadic-macro-arguments */, 7701, /* DiagArray369 */ 1014, 0, R"()")
DIAG_ENTRY(anonymous_247 /* gpu-maybe-wrong-side */, 7735, /* DiagArray370 */ 1017, 0, R"()")
DIAG_ENTRY(anonymous_122 /* header-guard */, 7756, /* DiagArray371 */ 1019, 0, R"()")
DIAG_ENTRY(HeaderHygiene /* header-hygiene */, 7769, /* DiagArray372 */ 1021, 0, R"()")
DIAG_ENTRY(HIPOpenMPOffloading /* hip-omp-target-directives */, 7784, /* DiagArray373 */ 1023, 0, R"()")
DIAG_ENTRY(HIPOnly /* hip-only */, 7810, /* DiagArray374 */ 1025, 0, R"()")
DIAG_ENTRY(HLSLExtension /* hlsl-extensions */, 7819, /* DiagArray375 */ 1027, 0, R"()")
DIAG_ENTRY(anonymous_242 /* idiomatic-parentheses */, 7835, /* DiagArray376 */ 1029, 0, R"()")
DIAG_ENTRY(IgnoredAttributes /* ignored-attributes */, 7857, /* DiagArray377 */ 1031, 0, R"()")
DIAG_ENTRY(anonymous_190 /* ignored-availability-without-sdk-settings */, 7876, /* DiagArray378 */ 1115, 0, R"()")
DIAG_ENTRY(IgnoredOptimizationArgument /* ignored-optimization-argument */, 7918, /* DiagArray379 */ 1117, 0, R"()")
DIAG_ENTRY(IgnoredPragmaIntrinsic /* ignored-pragma-intrinsic */, 7948, /* DiagArray380 */ 1120, 0, R"()")
DIAG_ENTRY(IgnoredPragmaOptimize /* ignored-pragma-optimize */, 7973, 0, 0, R"()")
DIAG_ENTRY(IgnoredPragmas /* ignored-pragmas */, 7997, /* DiagArray382 */ 1122, /* DiagSubGroup382 */ 329, R"()")
DIAG_ENTRY(IgnoredQualifiers /* ignored-qualifiers */, 8013, /* DiagArray383 */ 1172, /* DiagSubGroup383 */ 332, R"()")
DIAG_ENTRY(IgnoredReferenceQualifiers /* ignored-reference-qualifiers */, 8032, /* DiagArray384 */ 1177, 0, R"()")
DIAG_ENTRY(Implicit /* implicit */, 8061, 0, /* DiagSubGroup385 */ 334, R"()")
DIAG_ENTRY(ImplicitAtomic /* implicit-atomic-properties */, 8070, /* DiagArray386 */ 1179, 0, R"()")
DIAG_ENTRY(ImplicitConstIntFloatConversion /* implicit-const-int-float-conversion */, 8097, /* DiagArray387 */ 1182, 0, R"()")
DIAG_ENTRY(ImplicitConversionFloatingPointToBool /* implicit-conversion-floating-point-to-bool */, 8133, /* DiagArray388 */ 1184, 0, R"()")
DIAG_ENTRY(anonymous_259 /* implicit-exception-spec-mismatch */, 8176, /* DiagArray389 */ 1186, 0, R"()")
DIAG_ENTRY(ImplicitFallthrough /* implicit-fallthrough */, 8209, /* DiagArray390 */ 1188, /* DiagSubGroup390 */ 337, R"()")
DIAG_ENTRY(ImplicitFallthroughPerFunction /* implicit-fallthrough-per-function */, 8230, /* DiagArray391 */ 1190, 0, R"()")
DIAG_ENTRY(ImplicitFixedPointConversion /* implicit-fixed-point-conversion */, 8264, /* DiagArray392 */ 1192, 0, R"()")
DIAG_ENTRY(ImplicitFloatConversion /* implicit-float-conversion */, 8296, /* DiagArray393 */ 1194, /* DiagSubGroup393 */ 339, R"()")
DIAG_ENTRY(ImplicitFunctionDeclare /* implicit-function-declaration */, 8322, /* DiagArray394 */ 1197, 0, R"()")
DIAG_ENTRY(ImplicitInt /* implicit-int */, 8352, /* DiagArray395 */ 1203, 0, R"()")
DIAG_ENTRY(ImplicitIntConversion /* implicit-int-conversion */, 8365, /* DiagArray396 */ 1207, /* DiagSubGroup396 */ 342, R"()")
DIAG_ENTRY(ImplicitIntFloatConversion /* implicit-int-float-conversion */, 8389, /* DiagArray397 */ 1210, /* DiagSubGroup397 */ 344, R"()")
DIAG_ENTRY(anonymous_163 /* implicit-retain-self */, 8419, /* DiagArray398 */ 1212, 0, R"()")
DIAG_ENTRY(ImplicitlyUnsignedLiteral /* implicitly-unsigned-literal */, 8440, /* DiagArray399 */ 1214, 0, R"()")
DIAG_ENTRY(anonymous_24 /* import */, 8468, 0, 0, R"()")
DIAG_ENTRY(anonymous_116 /* import-preprocessor-directive-pedantic */, 8475, /* DiagArray401 */ 1216, 0, R"()")
DIAG_ENTRY(anonymous_255 /* inaccessible-base */, 8514, /* DiagArray402 */ 1218, 0, R"()")
DIAG_ENTRY(anonymous_106 /* include-next-absolute-path */, 8532, /* DiagArray403 */ 1220, 0, R"()")
DIAG_ENTRY(anonymous_105 /* include-next-outside-header */, 8559, /* DiagArray404 */ 1222, 0, R"()")
DIAG_ENTRY(IncompatibleExceptionSpec /* incompatible-exception-spec */, 8587, /* DiagArray405 */ 1224, 0, R"()")
DIAG_ENTRY(IncompatibleFunctionPointerTypes /* incompatible-function-pointer-types */, 8615, /* DiagArray406 */ 1227, 0, R"()")
DIAG_ENTRY(anonymous_244 /* incompatible-function-pointer-types-strict */, 8651, /* DiagArray407 */ 1229, 0, R"()")
DIAG_ENTRY(anonymous_146 /* incompatible-library-redeclaration */, 8694, /* DiagArray408 */ 1231, 0, R"()")
DIAG_ENTRY(IncompatibleMSStruct /* incompatible-ms-struct */, 8729, /* DiagArray409 */ 1233, 0, R"()")
DIAG_ENTRY(IncompatiblePointerTypes /* incompatible-pointer-types */, 8752, /* DiagArray410 */ 1236, /* DiagSubGroup410 */ 346, R"()")
DIAG_ENTRY(IncompatiblePointerTypesDiscardsQualifiers /* incompatible-pointer-types-discards-qualifiers */, 8779, /* DiagArray411 */ 1238, 0, R"()")
DIAG_ENTRY(anonymous_153 /* incompatible-property-type */, 8826, /* DiagArray412 */ 1242, 0, R"()")
DIAG_ENTRY(anonymous_83 /* incompatible-sysroot */, 8853, /* DiagArray413 */ 1244, 0, R"()")
DIAG_ENTRY(IncompleteFrameworkModuleDeclaration /* incomplete-framework-module-declaration */, 8874, /* DiagArray414 */ 1246, 0, R"()")
DIAG_ENTRY(anonymous_155 /* incomplete-implementation */, 8914, /* DiagArray415 */ 1248, 0, R"()")
DIAG_ENTRY(IncompleteModule /* incomplete-module */, 8940, 0, /* DiagSubGroup416 */ 349, R"()")
DIAG_ENTRY(anonymous_145 /* incomplete-setjmp-declaration */, 8958, /* DiagArray417 */ 1250, 0, R"()")
DIAG_ENTRY(IncompleteUmbrella /* incomplete-umbrella */, 8988, /* DiagArray418 */ 1252, 0, R"()")
DIAG_ENTRY(MicrosoftInconsistentDllImport /* inconsistent-dllimport */, 9008, /* DiagArray419 */ 1256, 0, R"()")
DIAG_ENTRY(CXX11WarnInconsistentOverrideDestructor /* inconsistent-missing-destructor-override */, 9031, /* DiagArray420 */ 1259, 0, R"()")
DIAG_ENTRY(CXX11WarnInconsistentOverrideMethod /* inconsistent-missing-override */, 9072, /* DiagArray421 */ 1261, 0, R"()")
DIAG_ENTRY(IncrementBool /* increment-bool */, 9102, /* DiagArray422 */ 1263, /* DiagSubGroup422 */ 352, R"()")
DIAG_ENTRY(InfiniteRecursion /* infinite-recursion */, 9117, /* DiagArray423 */ 1265, 0, R"()")
DIAG_ENTRY(anonymous_25 /* init-self */, 9136, 0, 0, R"()")
DIAG_ENTRY(InitializerOverrides /* initializer-overrides */, 9146, /* DiagArray425 */ 1267, 0, R"()")
DIAG_ENTRY(anonymous_170 /* injected-class-name */, 9168, /* DiagArray426 */ 1270, 0, R"()")
DIAG_ENTRY(anonymous_26 /* inline */, 9188, 0, 0, R"()")
DIAG_ENTRY(BackendInlineAsm /* inline-asm */, 9195, /* DiagArray428 */ 1272, 0, R"()")
DIAG_ENTRY(InlineNamespaceReopenedNoninline /* inline-namespace-reopened-noninline */, 9206, /* DiagArray429 */ 1274, 0, R"()")
DIAG_ENTRY(anonymous_256 /* inline-new-delete */, 9242, /* DiagArray430 */ 1276, 0, R"()")
DIAG_ENTRY(anonymous_203 /* instantiation-after-specialization */, 9260, /* DiagArray431 */ 1278, 0, R"()")
DIAG_ENTRY(IntConversion /* int-conversion */, 9295, /* DiagArray432 */ 1280, 0, R"()")
DIAG_ENTRY(anonymous_61 /* int-conversions */, 9310, 0, /* DiagSubGroup433 */ 354, R"()")
DIAG_ENTRY(IntInBoolContext /* int-in-bool-context */, 9326, /* DiagArray434 */ 1283, 0, R"()")
DIAG_ENTRY(IntToPointerCast /* int-to-pointer-cast */, 9346, /* DiagArray435 */ 1286, /* DiagSubGroup435 */ 356, R"()")
DIAG_ENTRY(IntToVoidPointerCast /* int-to-void-pointer-cast */, 9366, /* DiagArray436 */ 1288, 0, R"()")
DIAG_ENTRY(anonymous_71 /* integer-overflow */, 9391, /* DiagArray437 */ 1290, 0, R"()")
DIAG_ENTRY(anonymous_141 /* interrupt-service-routine */, 9408, /* DiagArray438 */ 1292, 0, R"()")
DIAG_ENTRY(InvalidCommandLineArgument /* invalid-command-line-argument */, 9434, /* DiagArray439 */ 1294, /* DiagSubGroup439 */ 358, R"()")
DIAG_ENTRY(anonymous_173 /* invalid-constexpr */, 9464, /* DiagArray440 */ 1304, 0, R"()")
DIAG_ENTRY(ObjCInvalidIBOutletProperty /* invalid-iboutlet */, 9482, /* DiagArray441 */ 1306, 0, R"()")
DIAG_ENTRY(anonymous_184 /* invalid-initializer-from-system-header */, 9499, /* DiagArray442 */ 1309, 0, R"()")
DIAG_ENTRY(InvalidIOSDeploymentTarget /* invalid-ios-deployment-target */, 9538, /* DiagArray443 */ 1311, 0, R"()")
DIAG_ENTRY(anonymous_196 /* invalid-no-builtin-names */, 9568, /* DiagArray444 */ 1313, 0, R"()")
DIAG_ENTRY(InvalidNoreturn /* invalid-noreturn */, 9593, /* DiagArray445 */ 1315, 0, R"()")
DIAG_ENTRY(InvalidOffsetof /* invalid-offsetof */, 9610, /* DiagArray446 */ 1318, 0, R"()")
DIAG_ENTRY(InvalidOrNonExistentDirectory /* invalid-or-nonexistent-directory */, 9627, /* DiagArray447 */ 1321, 0, R"()")
DIAG_ENTRY(anonymous_201 /* invalid-partial-specialization */, 9660, /* DiagArray448 */ 1324, 0, R"()")
DIAG_ENTRY(anonymous_27 /* invalid-pch */, 9691, 0, 0, R"()")
DIAG_ENTRY(InvalidPPToken /* invalid-pp-token */, 9703, /* DiagArray450 */ 1326, 0, R"()")
DIAG_ENTRY(InvalidSourceEncoding /* invalid-source-encoding */, 9720, /* DiagArray451 */ 1329, 0, R"()")
DIAG_ENTRY(anonymous_119 /* invalid-token-paste */, 9744, /* DiagArray452 */ 1332, 0, R"()")
DIAG_ENTRY(anonymous_98 /* invalid-utf8 */, 9764, /* DiagArray453 */ 1334, 0, R"()")
DIAG_ENTRY(anonymous_241 /* jump-seh-finally */, 9777, /* DiagArray454 */ 1336, 0, R"()")
DIAG_ENTRY(KeywordCompat /* keyword-compat */, 9794, /* DiagArray455 */ 1338, 0, R"()")
DIAG_ENTRY(KeywordAsMacro /* keyword-macro */, 9809, /* DiagArray456 */ 1340, 0, R"()")
DIAG_ENTRY(KNRPromotedParameter /* knr-promoted-parameter */, 9823, /* DiagArray457 */ 1342, 0, R"()")
DIAG_ENTRY(anonymous_97 /* language-extension-token */, 9846, /* DiagArray458 */ 1344, 0, R"()")
DIAG_ENTRY(LargeByValueCopy /* large-by-value-copy */, 9871, /* DiagArray459 */ 1346, 0, R"()")
DIAG_ENTRY(LibLTO /* liblto */, 9891, 0, 0, R"()")
DIAG_ENTRY(LinkerWarnings /* linker-warnings */, 9898, /* DiagArray461 */ 1349, 0, R"()")
DIAG_ENTRY(LiteralConversion /* literal-conversion */, 9914, /* DiagArray462 */ 1351, 0, R"()")
DIAG_ENTRY(LiteralRange /* literal-range */, 9933, /* DiagArray463 */ 1354, 0, R"()")
DIAG_ENTRY(LocalTypeTemplateArgs /* local-type-template-args */, 9947, /* DiagArray464 */ 1358, /* DiagSubGroup464 */ 360, R"()")
DIAG_ENTRY(LogicalNotParentheses /* logical-not-parentheses */, 9972, /* DiagArray465 */ 1360, 0, R"()")
DIAG_ENTRY(LogicalOpParentheses /* logical-op-parentheses */, 9996, /* DiagArray466 */ 1362, 0, R"()")
DIAG_ENTRY(LongLong /* long-long */, 10019, /* DiagArray467 */ 1364, /* DiagSubGroup467 */ 362, R"()")
DIAG_ENTRY(LoopAnalysis /* loop-analysis */, 10029, 0, /* DiagSubGroup468 */ 364, R"()")
DIAG_ENTRY(MacroRedefined /* macro-redefined */, 10043, /* DiagArray469 */ 1366, 0, R"()")
DIAG_ENTRY(Main /* main */, 10059, /* DiagArray470 */ 1368, 0, R"()")
DIAG_ENTRY(MainReturnType /* main-return-type */, 10064, /* DiagArray471 */ 1376, 0, R"()")
DIAG_ENTRY(MalformedWarningCheck /* malformed-warning-check */, 10081, /* DiagArray472 */ 1378, 0, R"()")
DIAG_ENTRY(anonymous_216 /* many-braces-around-scalar-init */, 10105, /* DiagArray473 */ 1380, 0, R"()")
DIAG_ENTRY(anonymous_102 /* mathematical-notation-identifier-extension */, 10136, /* DiagArray474 */ 1382, 0, R"()")
DIAG_ENTRY(MaxTokens /* max-tokens */, 10179, /* DiagArray475 */ 1384, 0, R"(The warning is issued if the number of pre-processor tokens exceeds
the token limit, which can be set in three ways:

1. As a limit at a specific point in a file, using the ``clang max_tokens_here``
   pragma:

   .. code-block: c++
      #pragma clang max_tokens_here 1234

2. As a per-translation unit limit, using the ``-fmax-tokens=`` command-line
   flag:

   .. code-block: console
      clang -c a.cpp -fmax-tokens=1234

3. As a per-translation unit limit using the ``clang max_tokens_total`` pragma,
   which works like and overrides the ``-fmax-tokens=`` flag:

   .. code-block: c++
      #pragma clang max_tokens_total 1234

These limits can be helpful in limiting code growth through included files.

Setting a token limit of zero means no limit.

Note that the warning is disabled by default, so -Wmax-tokens must be used
in addition with the pragmas or -fmax-tokens flag to get any warnings.)")
DIAG_ENTRY(MaxUnsignedZero /* max-unsigned-zero */, 10190, /* DiagArray476 */ 1387, 0, R"()")
DIAG_ENTRY(MemsetTransposedArgs /* memset-transposed-args */, 10208, /* DiagArray477 */ 1389, 0, R"()")
DIAG_ENTRY(anonymous_148 /* memsize-comparison */, 10231, /* DiagArray478 */ 1391, 0, R"()")
DIAG_ENTRY(MethodSignatures /* method-signatures */, 10250, /* DiagArray479 */ 1393, 0, R"()")
DIAG_ENTRY(Microsoft /* microsoft */, 10268, 0, /* DiagSubGroup480 */ 367, R"()")
DIAG_ENTRY(MicrosoftAbstract /* microsoft-abstract */, 10278, /* DiagArray481 */ 1396, 0, R"()")
DIAG_ENTRY(MicrosoftAnonTag /* microsoft-anon-tag */, 10297, /* DiagArray482 */ 1398, 0, R"()")
DIAG_ENTRY(MicrosoftCast /* microsoft-cast */, 10316, /* DiagArray483 */ 1401, 0, R"()")
DIAG_ENTRY(MicrosoftCharize /* microsoft-charize */, 10331, /* DiagArray484 */ 1404, 0, R"()")
DIAG_ENTRY(MicrosoftCommentPaste /* microsoft-comment-paste */, 10349, /* DiagArray485 */ 1406, 0, R"()")
DIAG_ENTRY(MicrosoftConstInit /* microsoft-const-init */, 10373, /* DiagArray486 */ 1408, 0, R"()")
DIAG_ENTRY(MicrosoftCppMacro /* microsoft-cpp-macro */, 10394, /* DiagArray487 */ 1410, 0, R"()")
DIAG_ENTRY(MicrosoftDefaultArgRedefinition /* microsoft-default-arg-redefinition */, 10414, /* DiagArray488 */ 1412, 0, R"()")
DIAG_ENTRY(MicrosoftDrectveSection /* microsoft-drectve-section */, 10449, /* DiagArray489 */ 1414, 0, R"()")
DIAG_ENTRY(MicrosoftEndOfFile /* microsoft-end-of-file */, 10475, /* DiagArray490 */ 1416, 0, R"()")
DIAG_ENTRY(MicrosoftEnumForwardReference /* microsoft-enum-forward-reference */, 10497, /* DiagArray491 */ 1418, 0, R"()")
DIAG_ENTRY(MicrosoftEnumValue /* microsoft-enum-value */, 10530, /* DiagArray492 */ 1420, 0, R"()")
DIAG_ENTRY(MicrosoftExceptionSpec /* microsoft-exception-spec */, 10551, /* DiagArray493 */ 1422, 0, R"()")
DIAG_ENTRY(anonymous_128 /* microsoft-exists */, 10576, /* DiagArray494 */ 1428, 0, R"()")
DIAG_ENTRY(MicrosoftExplicitConstructorCall /* microsoft-explicit-constructor-call */, 10593, /* DiagArray495 */ 1430, 0, R"()")
DIAG_ENTRY(MicrosoftExtraQualification /* microsoft-extra-qualification */, 10629, /* DiagArray496 */ 1432, 0, R"()")
DIAG_ENTRY(MicrosoftFixedEnum /* microsoft-fixed-enum */, 10659, /* DiagArray497 */ 1434, 0, R"()")
DIAG_ENTRY(MicrosoftFlexibleArray /* microsoft-flexible-array */, 10680, /* DiagArray498 */ 1436, 0, R"()")
DIAG_ENTRY(MicrosoftGoto /* microsoft-goto */, 10705, /* DiagArray499 */ 1439, 0, R"()")
DIAG_ENTRY(MicrosoftInaccessibleBase /* microsoft-inaccessible-base */, 10720, /* DiagArray500 */ 1441, 0, R"()")
DIAG_ENTRY(MicrosoftInclude /* microsoft-include */, 10748, /* DiagArray501 */ 1443, 0, R"()")
DIAG_ENTRY(MicrosoftMutableReference /* microsoft-mutable-reference */, 10766, /* DiagArray502 */ 1445, 0, R"()")
DIAG_ENTRY(MicrosoftPureDefinition /* microsoft-pure-definition */, 10794, /* DiagArray503 */ 1447, 0, R"()")
DIAG_ENTRY(MicrosoftRedeclareStatic /* microsoft-redeclare-static */, 10820, /* DiagArray504 */ 1449, 0, R"()")
DIAG_ENTRY(MicrosoftSealed /* microsoft-sealed */, 10847, /* DiagArray505 */ 1451, 0, R"()")
DIAG_ENTRY(MicrosoftStaticAssert /* microsoft-static-assert */, 10864, /* DiagArray506 */ 1453, 0, R"()")
DIAG_ENTRY(MicrosoftTemplate /* microsoft-template */, 10888, /* DiagArray507 */ 1455, /* DiagSubGroup507 */ 398, R"()")
DIAG_ENTRY(MicrosoftTemplateShadow /* microsoft-template-shadow */, 10907, /* DiagArray508 */ 1466, 0, R"()")
DIAG_ENTRY(MicrosoftUnionMemberReference /* microsoft-union-member-reference */, 10933, /* DiagArray509 */ 1468, 0, R"()")
DIAG_ENTRY(MicrosoftUnqualifiedFriend /* microsoft-unqualified-friend */, 10966, /* DiagArray510 */ 1470, 0, R"()")
DIAG_ENTRY(MicrosoftUsingDecl /* microsoft-using-decl */, 10995, /* DiagArray511 */ 1472, 0, R"()")
DIAG_ENTRY(MicrosoftVoidPseudoDtor /* microsoft-void-pseudo-dtor */, 11016, /* DiagArray512 */ 1474, 0, R"()")
DIAG_ENTRY(MisExpect /* misexpect */, 11043, /* DiagArray513 */ 1476, 0, R"()")
DIAG_ENTRY(MisleadingIndentation /* misleading-indentation */, 11053, /* DiagArray514 */ 1478, 0, R"()")
DIAG_ENTRY(anonymous_237 /* mismatched-new-delete */, 11076, /* DiagArray515 */ 1480, 0, R"()")
DIAG_ENTRY(MismatchedParameterTypes /* mismatched-parameter-types */, 11098, /* DiagArray516 */ 1482, 0, R"()")
DIAG_ENTRY(MismatchedReturnTypes /* mismatched-return-types */, 11125, /* DiagArray517 */ 1484, 0, R"()")
DIAG_ENTRY(MismatchedTags /* mismatched-tags */, 11149, /* DiagArray518 */ 1486, 0, R"()")
DIAG_ENTRY(MissingBraces /* missing-braces */, 11165, /* DiagArray519 */ 1489, 0, R"()")
DIAG_ENTRY(anonymous_252 /* missing-constinit */, 11180, /* DiagArray520 */ 1491, 0, R"()")
DIAG_ENTRY(MissingDeclarations /* missing-declarations */, 11198, /* DiagArray521 */ 1493, 0, R"()")
DIAG_ENTRY(anonymous_169 /* missing-exception-spec */, 11219, /* DiagArray522 */ 1498, 0, R"()")
DIAG_ENTRY(MissingFieldInitializers /* missing-field-initializers */, 11242, /* DiagArray523 */ 1500, 0, R"()")
DIAG_ENTRY(anonymous_28 /* missing-format-attribute */, 11269, 0, 0, R"()")
DIAG_ENTRY(anonymous_29 /* missing-include-dirs */, 11294, 0, 0, R"()")
DIAG_ENTRY(MissingMethodReturnType /* missing-method-return-type */, 11315, /* DiagArray526 */ 1502, 0, R"()")
DIAG_ENTRY(MissingNoEscape /* missing-noescape */, 11342, /* DiagArray527 */ 1504, 0, R"()")
DIAG_ENTRY(MissingNoreturn /* missing-noreturn */, 11359, /* DiagArray528 */ 1506, 0, R"()")
DIAG_ENTRY(anonymous_188 /* missing-prototype-for-cc */, 11376, /* DiagArray529 */ 1509, 0, R"()")
DIAG_ENTRY(anonymous_205 /* missing-prototypes */, 11401, /* DiagArray530 */ 1511, 0, R"()")
DIAG_ENTRY(anonymous_129 /* missing-selector-name */, 11420, /* DiagArray531 */ 1513, 0, R"()")
DIAG_ENTRY(anonymous_82 /* missing-sysroot */, 11442, /* DiagArray532 */ 1515, 0, R"()")
DIAG_ENTRY(anonymous_206 /* missing-variable-declarations */, 11458, /* DiagArray533 */ 1517, 0, R"()")
DIAG_ENTRY(MisspelledAssumption /* misspelled-assumption */, 11488, /* DiagArray534 */ 1519, 0, R"()")
DIAG_ENTRY(ModuleBuild /* module-build */, 11510, /* DiagArray535 */ 1521, 0, R"()")
DIAG_ENTRY(ModuleConflict /* module-conflict */, 11523, /* DiagArray536 */ 1526, 0, R"()")
DIAG_ENTRY(anonymous_93 /* module-file-config-mismatch */, 11539, /* DiagArray537 */ 1529, 0, R"()")
DIAG_ENTRY(ModuleFileExtension /* module-file-extension */, 11567, /* DiagArray538 */ 1531, 0, R"()")
DIAG_ENTRY(ModuleImport /* module-import */, 11589, /* DiagArray539 */ 1533, 0, R"()")
DIAG_ENTRY(anonymous_269 /* module-import-in-extern-c */, 11603, /* DiagArray540 */ 1535, 0, R"()")
DIAG_ENTRY(ModuleIncludeDirectiveTranslation /* module-include-translation */, 11629, /* DiagArray541 */ 1537, 0, R"()")
DIAG_ENTRY(ModuleLock /* module-lock */, 11656, /* DiagArray542 */ 1539, 0, R"()")
DIAG_ENTRY(anonymous_272 /* modules-ambiguous-internal-linkage */, 11668, /* DiagArray543 */ 1541, 0, R"()")
DIAG_ENTRY(anonymous_270 /* modules-import-nested-redundant */, 11703, /* DiagArray544 */ 1543, 0, R"()")
DIAG_ENTRY(Most /* most */, 11735, 0, /* DiagSubGroup545 */ 400, R"()")
DIAG_ENTRY(Move /* move */, 11740, 0, /* DiagSubGroup546 */ 438, R"()")
DIAG_ENTRY(anonymous_68 /* msvc-include */, 11745, 0, /* DiagSubGroup547 */ 443, R"()")
DIAG_ENTRY(anonymous_86 /* msvc-not-found */, 11758, /* DiagArray548 */ 1545, 0, R"()")
DIAG_ENTRY(MultiChar /* multichar */, 11773, /* DiagArray549 */ 1547, 0, R"()")
DIAG_ENTRY(anonymous_257 /* multiple-move-vbase */, 11783, /* DiagArray550 */ 1549, 0, R"()")
DIAG_ENTRY(anonymous_18 /* narrowing */, 11803, 0, /* DiagSubGroup551 */ 445, R"()")
DIAG_ENTRY(anonymous_254 /* nested-anon-types */, 11813, /* DiagArray552 */ 1551, 0, R"()")
DIAG_ENTRY(anonymous_30 /* nested-externs */, 11831, 0, 0, R"()")
DIAG_ENTRY(OperatorNewReturnsNull /* new-returns-null */, 11846, /* DiagArray554 */ 1553, 0, R"()")
DIAG_ENTRY(NewlineEOF /* newline-eof */, 11863, /* DiagArray555 */ 1555, 0, R"()")
DIAG_ENTRY(NoDeref /* noderef */, 11875, /* DiagArray556 */ 1558, 0, R"()")
DIAG_ENTRY(NoexceptType /* noexcept-type */, 11883, 0, /* DiagSubGroup557 */ 447, R"()")
DIAG_ENTRY(anonymous_151 /* non-c-typedef-for-linkage */, 11897, /* DiagArray558 */ 1562, 0, R"()")
DIAG_ENTRY(NonGCC /* non-gcc */, 11923, 0, /* DiagSubGroup559 */ 449, R"()")
DIAG_ENTRY(NonLiteralNullConversion /* non-literal-null-conversion */, 11931, /* DiagArray560 */ 1564, 0, R"()")
DIAG_ENTRY(NonModularIncludeInFrameworkModule /* non-modular-include-in-framework-module */, 11959, /* DiagArray561 */ 1566, 0, R"()")
DIAG_ENTRY(NonModularIncludeInModule /* non-modular-include-in-module */, 11999, /* DiagArray562 */ 1568, /* DiagSubGroup562 */ 453, R"()")
DIAG_ENTRY(NonPODVarargs /* non-pod-varargs */, 12029, /* DiagArray563 */ 1570, 0, R"()")
DIAG_ENTRY(anonymous_179 /* non-power-of-two-alignment */, 12045, /* DiagArray564 */ 1575, 0, R"()")
DIAG_ENTRY(NonVirtualDtor /* non-virtual-dtor */, 12072, /* DiagArray565 */ 1577, 0, R"()")
DIAG_ENTRY(NonNull /* nonnull */, 12089, /* DiagArray566 */ 1579, 0, R"()")
DIAG_ENTRY(anonymous_32 /* nonportable-cfstrings */, 12097, 0, 0, R"()")
DIAG_ENTRY(anonymous_107 /* nonportable-include-path */, 12119, /* DiagArray568 */ 1582, 0, R"()")
DIAG_ENTRY(anonymous_108 /* nonportable-system-include-path */, 12144, /* DiagArray569 */ 1584, 0, R"()")
DIAG_ENTRY(anonymous_264 /* nonportable-vector-initialization */, 12176, /* DiagArray570 */ 1586, 0, R"()")
DIAG_ENTRY(NonTrivialMemaccess /* nontrivial-memaccess */, 12210, /* DiagArray571 */ 1588, 0, R"()")
DIAG_ENTRY(NSConsumedMismatch /* nsconsumed-mismatch */, 12231, /* DiagArray572 */ 1590, 0, R"()")
DIAG_ENTRY(NSReturnsMismatch /* nsreturns-mismatch */, 12251, /* DiagArray573 */ 1592, 0, R"()")
DIAG_ENTRY(NullArithmetic /* null-arithmetic */, 12270, /* DiagArray574 */ 1594, 0, R"()")
DIAG_ENTRY(NullCharacter /* null-character */, 12286, /* DiagArray575 */ 1597, 0, R"()")
DIAG_ENTRY(NullConversion /* null-conversion */, 12301, /* DiagArray576 */ 1600, 0, R"()")
DIAG_ENTRY(NullDereference /* null-dereference */, 12317, /* DiagArray577 */ 1602, 0, R"()")
DIAG_ENTRY(NullPointerArithmetic /* null-pointer-arithmetic */, 12334, /* DiagArray578 */ 1605, /* DiagSubGroup578 */ 455, R"()")
DIAG_ENTRY(NullPointerSubtraction /* null-pointer-subtraction */, 12358, /* DiagArray579 */ 1607, 0, R"()")
DIAG_ENTRY(Nullability /* nullability */, 12383, /* DiagArray580 */ 1609, 0, R"()")
DIAG_ENTRY(NullabilityCompleteness /* nullability-completeness */, 12395, /* DiagArray581 */ 1615, /* DiagSubGroup581 */ 457, R"()")
DIAG_ENTRY(NullabilityCompletenessOnArrays /* nullability-completeness-on-arrays */, 12420, /* DiagArray582 */ 1617, 0, R"()")
DIAG_ENTRY(NullabilityDeclSpec /* nullability-declspec */, 12455, /* DiagArray583 */ 1619, 0, R"()")
DIAG_ENTRY(anonymous_124 /* nullability-extension */, 12476, /* DiagArray584 */ 1621, 0, R"()")
DIAG_ENTRY(NullabilityInferredOnNestedType /* nullability-inferred-on-nested-type */, 12498, /* DiagArray585 */ 1623, 0, R"()")
DIAG_ENTRY(NullableToNonNullConversion /* nullable-to-nonnull-conversion */, 12534, /* DiagArray586 */ 1625, 0, R"()")
DIAG_ENTRY(anonymous_160 /* objc-autosynthesis-property-ivar-name-match */, 12565, /* DiagArray587 */ 1627, 0, R"()")
DIAG_ENTRY(ObjCBoolConstantConversion /* objc-bool-constant-conversion */, 12609, /* DiagArray588 */ 1629, 0, R"()")
DIAG_ENTRY(ObjCBoxing /* objc-boxing */, 12639, /* DiagArray589 */ 1631, 0, R"()")
DIAG_ENTRY(anonymous_234 /* objc-circular-container */, 12651, /* DiagArray590 */ 1633, 0, R"()")
DIAG_ENTRY(ObjCCocoaAPI /* objc-cocoa-api */, 12675, 0, /* DiagSubGroup591 */ 459, R"()")
DIAG_ENTRY(ObjCDesignatedInit /* objc-designated-initializers */, 12690, /* DiagArray592 */ 1635, 0, R"()")
DIAG_ENTRY(anonymous_176 /* objc-dictionary-duplicate-keys */, 12719, /* DiagArray593 */ 1642, 0, R"()")
DIAG_ENTRY(ObjCFlexibleArray /* objc-flexible-array */, 12750, /* DiagArray594 */ 1644, 0, R"()")
DIAG_ENTRY(anonymous_212 /* objc-forward-class-redefinition */, 12770, /* DiagArray595 */ 1647, 0, R"()")
DIAG_ENTRY(anonymous_213 /* objc-interface-ivars */, 12802, /* DiagArray596 */ 1649, 0, R"()")
DIAG_ENTRY(ObjCLiteralComparison /* objc-literal-compare */, 12823, /* DiagArray597 */ 1651, /* DiagSubGroup597 */ 461, R"()")
DIAG_ENTRY(ObjCLiteralConversion /* objc-literal-conversion */, 12844, /* DiagArray598 */ 1653, 0, R"()")
DIAG_ENTRY(anonymous_115 /* objc-macro-redefinition */, 12868, /* DiagArray599 */ 1656, 0, R"()")
DIAG_ENTRY(anonymous_165 /* objc-messaging-id */, 12892, /* DiagArray600 */ 1658, 0, R"()")
DIAG_ENTRY(MethodAccess /* objc-method-access */, 12910, /* DiagArray601 */ 1660, 0, R"()")
DIAG_ENTRY(anonymous_161 /* objc-missing-property-synthesis */, 12929, /* DiagArray602 */ 1667, 0, R"()")
DIAG_ENTRY(ObjCMissingSuperCalls /* objc-missing-super-calls */, 12961, /* DiagArray603 */ 1669, 0, R"()")
DIAG_ENTRY(ObjCMultipleMethodNames /* objc-multiple-method-names */, 12986, /* DiagArray604 */ 1671, 0, R"()")
DIAG_ENTRY(ObjCRetainBlockProperty /* objc-noncopy-retain-block-property */, 13013, /* DiagArray605 */ 1673, 0, R"()")
DIAG_ENTRY(ObjCNonUnifiedException /* objc-nonunified-exceptions */, 13048, /* DiagArray606 */ 1675, 0, R"()")
DIAG_ENTRY(ObjCPropertyAssignOnObjectType /* objc-property-assign-on-object-type */, 13075, /* DiagArray607 */ 1677, 0, R"()")
DIAG_ENTRY(ObjCPropertyImpl /* objc-property-implementation */, 13111, /* DiagArray608 */ 1679, 0, R"()")
DIAG_ENTRY(anonymous_156 /* objc-property-implicit-mismatch */, 13140, /* DiagArray609 */ 1684, 0, R"()")
DIAG_ENTRY(anonymous_158 /* objc-property-matches-cocoa-ownership-rule */, 13172, /* DiagArray610 */ 1686, 0, R"()")
DIAG_ENTRY(ObjCPropertyNoAttribute /* objc-property-no-attribute */, 13215, /* DiagArray611 */ 1688, 0, R"()")
DIAG_ENTRY(ObjCNoPropertyAutoSynthesis /* objc-property-synthesis */, 13242, /* DiagArray612 */ 1691, 0, R"()")
DIAG_ENTRY(ObjCProtocolMethodImpl /* objc-protocol-method-implementation */, 13266, /* DiagArray613 */ 1695, 0, R"()")
DIAG_ENTRY(anonymous_159 /* objc-protocol-property-synthesis */, 13302, /* DiagArray614 */ 1697, 0, R"()")
DIAG_ENTRY(ObjCProtocolQualifiers /* objc-protocol-qualifiers */, 13335, /* DiagArray615 */ 1699, 0, R"()")
DIAG_ENTRY(ObjCReadonlyPropertyHasSetter /* objc-readonly-with-setter-property */, 13360, /* DiagArray616 */ 1701, 0, R"()")
DIAG_ENTRY(ObjCRedundantAPIUse /* objc-redundant-api-use */, 13395, 0, /* DiagSubGroup617 */ 463, R"()")
DIAG_ENTRY(ObjCRedundantLiteralUse /* objc-redundant-literal-use */, 13418, /* DiagArray618 */ 1703, 0, R"()")
DIAG_ENTRY(ObjCRootClass /* objc-root-class */, 13445, /* DiagArray619 */ 1705, 0, R"()")
DIAG_ENTRY(ObjCSignedCharBool /* objc-signed-char-bool */, 13461, 0, /* DiagSubGroup620 */ 465, R"()")
DIAG_ENTRY(ObjCSignedCharBoolImplicitFloatConversion /* objc-signed-char-bool-implicit-float-conversion */, 13483, /* DiagArray621 */ 1707, 0, R"()")
DIAG_ENTRY(ObjCSignedCharBoolImplicitIntConversion /* objc-signed-char-bool-implicit-int-conversion */, 13531, /* DiagArray622 */ 1709, 0, R"()")
DIAG_ENTRY(ObjCStringComparison /* objc-string-compare */, 13577, /* DiagArray623 */ 1711, 0, R"()")
DIAG_ENTRY(ObjCStringConcatenation /* objc-string-concatenation */, 13597, /* DiagArray624 */ 1713, 0, R"()")
DIAG_ENTRY(anonymous_235 /* objc-unsafe-perform-selector */, 13623, /* DiagArray625 */ 1715, 0, R"()")
DIAG_ENTRY(ODR /* odr */, 13652, /* DiagArray626 */ 1717, 0, R"()")
DIAG_ENTRY(OldStyleCast /* old-style-cast */, 13656, /* DiagArray627 */ 1736, 0, R"()")
DIAG_ENTRY(anonymous_34 /* old-style-definition */, 13671, 0, 0, R"()")
DIAG_ENTRY(OpenCLUnsupportedRGBA /* opencl-unsupported-rgba */, 13692, /* DiagArray629 */ 1738, 0, R"()")
DIAG_ENTRY(OpenMP /* openmp */, 13716, 0, /* DiagSubGroup630 */ 470, R"()")
DIAG_ENTRY(OpenMP51Ext /* openmp-51-extensions */, 13723, /* DiagArray631 */ 1740, 0, R"()")
DIAG_ENTRY(OpenMPClauses /* openmp-clauses */, 13744, /* DiagArray632 */ 1742, 0, R"()")
DIAG_ENTRY(OpenMPLoopForm /* openmp-loop-form */, 13759, /* DiagArray633 */ 1762, 0, R"()")
DIAG_ENTRY(OpenMPMapping /* openmp-mapping */, 13776, /* DiagArray634 */ 1765, 0, R"()")
DIAG_ENTRY(OpenMPTarget /* openmp-target */, 13791, /* DiagArray635 */ 1767, /* DiagSubGroup635 */ 477, R"()")
DIAG_ENTRY(OptionIgnored /* option-ignored */, 13805, /* DiagArray636 */ 1771, 0, R"()")
DIAG_ENTRY(OrderedCompareFunctionPointers /* ordered-compare-function-pointers */, 13820, /* DiagArray637 */ 1787, 0, R"()")
DIAG_ENTRY(OutOfLineDeclaration /* out-of-line-declaration */, 13854, /* DiagArray638 */ 1790, 0, R"()")
DIAG_ENTRY(anonymous_143 /* out-of-scope-function */, 13878, /* DiagArray639 */ 1792, 0, R"()")
DIAG_ENTRY(OveralignedType /* over-aligned */, 13900, /* DiagArray640 */ 1794, 0, R"()")
DIAG_ENTRY(anonymous_35 /* overflow */, 13913, 0, 0, R"()")
DIAG_ENTRY(OverlengthStrings /* overlength-strings */, 13922, /* DiagArray642 */ 1796, 0, R"()")
DIAG_ENTRY(OverloadedShiftOpParentheses /* overloaded-shift-op-parentheses */, 13941, /* DiagArray643 */ 1798, 0, R"()")
DIAG_ENTRY(OverloadedVirtual /* overloaded-virtual */, 13973, /* DiagArray644 */ 1800, 0, R"()")
DIAG_ENTRY(anonymous_31 /* override-init */, 13992, 0, /* DiagSubGroup645 */ 479, R"()")
DIAG_ENTRY(anonymous_90 /* override-module */, 14006, /* DiagArray646 */ 1802, 0, R"()")
DIAG_ENTRY(OverridingMethodMismatch /* overriding-method-mismatch */, 14022, /* DiagArray647 */ 1804, 0, R"()")
DIAG_ENTRY(anonymous_81 /* overriding-t-option */, 14049, /* DiagArray648 */ 1812, 0, R"()")
DIAG_ENTRY(Packed /* packed */, 14069, /* DiagArray649 */ 1814, /* DiagSubGroup649 */ 481, R"()")
DIAG_ENTRY(PackedNonPod /* packed-non-pod */, 14076, /* DiagArray650 */ 1816, 0, R"()")
DIAG_ENTRY(Padded /* padded */, 14091, /* DiagArray651 */ 1818, 0, R"()")
DIAG_ENTRY(Parentheses /* parentheses */, 14098, /* DiagArray652 */ 1822, /* DiagSubGroup652 */ 483, R"()")
DIAG_ENTRY(ParenthesesOnEquality /* parentheses-equality */, 14110, /* DiagArray653 */ 1826, 0, R"()")
DIAG_ENTRY(anonymous_6 /* partial-availability */, 14131, 0, /* DiagSubGroup654 */ 492, R"()")
DIAG_ENTRY(BackendOptimizationRemark /* pass */, 14152, /* DiagArray655 */ 1828, 0, R"()")
DIAG_ENTRY(BackendOptimizationRemarkAnalysis /* pass-analysis */, 14157, /* DiagArray656 */ 1830, 0, R"()")
DIAG_ENTRY(BackendOptimizationFailure /* pass-failed */, 14171, /* DiagArray657 */ 1834, 0, R"()")
DIAG_ENTRY(BackendOptimizationRemarkMissed /* pass-missed */, 14183, /* DiagArray658 */ 1836, 0, R"()")
DIAG_ENTRY(anonymous_276 /* pch-date-time */, 14195, /* DiagArray659 */ 1838, 0, R"()")
DIAG_ENTRY(Pedantic /* pedantic */, 14209, /* DiagArray660 */ 1840, /* DiagSubGroup660 */ 494, R"()")
DIAG_ENTRY(OpenCLCoreFeaturesDiagGroup /* pedantic-core-features */, 14218, /* DiagArray661 */ 1914, 0, R"()")
DIAG_ENTRY(PedanticMacros /* pedantic-macros */, 14241, 0, /* DiagSubGroup662 */ 555, R"()")
DIAG_ENTRY(PessimizingMove /* pessimizing-move */, 14257, /* DiagArray663 */ 1917, 0, R"()")
DIAG_ENTRY(PointerArith /* pointer-arith */, 14274, /* DiagArray664 */ 1920, /* DiagSubGroup664 */ 561, R"()")
DIAG_ENTRY(PointerBoolConversion /* pointer-bool-conversion */, 14288, /* DiagArray665 */ 1924, 0, R"()")
DIAG_ENTRY(anonymous_191 /* pointer-compare */, 14312, /* DiagArray666 */ 1927, 0, R"()")
DIAG_ENTRY(anonymous_233 /* pointer-integer-compare */, 14328, /* DiagArray667 */ 1929, 0, R"()")
DIAG_ENTRY(anonymous_243 /* pointer-sign */, 14352, /* DiagArray668 */ 1931, 0, R"()")
DIAG_ENTRY(PointerToEnumCast /* pointer-to-enum-cast */, 14365, /* DiagArray669 */ 1933, /* DiagSubGroup669 */ 563, R"()")
DIAG_ENTRY(PointerToIntCast /* pointer-to-int-cast */, 14386, /* DiagArray670 */ 1935, /* DiagSubGroup670 */ 565, R"()")
DIAG_ENTRY(anonymous_249 /* pointer-type-mismatch */, 14406, /* DiagArray671 */ 1937, 0, R"()")
DIAG_ENTRY(anonymous_79 /* poison-system-directories */, 14428, /* DiagArray672 */ 1939, 0, R"()")
DIAG_ENTRY(ObjCPotentiallyDirectSelector /* potentially-direct-selector */, 14454, /* DiagArray673 */ 1941, 0, R"()")
DIAG_ENTRY(PotentiallyEvaluatedExpression /* potentially-evaluated-expression */, 14482, /* DiagArray674 */ 1943, 0, R"()")
DIAG_ENTRY(PragmaClangAttribute /* pragma-clang-attribute */, 14515, /* DiagArray675 */ 1945, 0, R"()")
DIAG_ENTRY(anonymous_109 /* pragma-once-outside-header */, 14538, /* DiagArray676 */ 1947, 0, R"()")
DIAG_ENTRY(PragmaPack /* pragma-pack */, 14565, /* DiagArray677 */ 1949, /* DiagSubGroup677 */ 568, R"()")
DIAG_ENTRY(PragmaPackSuspiciousInclude /* pragma-pack-suspicious-include */, 14577, /* DiagArray678 */ 1952, 0, R"()")
DIAG_ENTRY(anonymous_110 /* pragma-system-header-outside-header */, 14608, /* DiagArray679 */ 1954, 0, R"()")
DIAG_ENTRY(Pragmas /* pragmas */, 14644, /* DiagArray680 */ 1956, /* DiagSubGroup680 */ 570, R"()")
DIAG_ENTRY(CXXPre14Compat /* pre-c++14-compat */, 14652, /* DiagArray681 */ 1959, 0, R"()")
DIAG_ENTRY(CXXPre14CompatPedantic /* pre-c++14-compat-pedantic */, 14669, 0, /* DiagSubGroup682 */ 575, R"()")
DIAG_ENTRY(CXXPre17Compat /* pre-c++17-compat */, 14695, /* DiagArray683 */ 1971, 0, R"()")
DIAG_ENTRY(CXXPre17CompatPedantic /* pre-c++17-compat-pedantic */, 14712, /* DiagArray684 */ 1989, /* DiagSubGroup684 */ 578, R"()")
DIAG_ENTRY(CXXPre20Compat /* pre-c++20-compat */, 14738, /* DiagArray685 */ 1992, 0, R"()")
DIAG_ENTRY(CXXPre20CompatPedantic /* pre-c++20-compat-pedantic */, 14755, /* DiagArray686 */ 2020, /* DiagSubGroup686 */ 580, R"()")
DIAG_ENTRY(CXXPre23Compat /* pre-c++23-compat */, 14781, /* DiagArray687 */ 2023, 0, R"()")
DIAG_ENTRY(CXXPre23CompatPedantic /* pre-c++23-compat-pedantic */, 14798, 0, /* DiagSubGroup688 */ 582, R"()")
DIAG_ENTRY(CPre2xCompat /* pre-c2x-compat */, 14824, /* DiagArray689 */ 2039, 0, R"()")
DIAG_ENTRY(CPre2xCompatPedantic /* pre-c2x-compat-pedantic */, 14839, 0, /* DiagSubGroup690 */ 584, R"()")
DIAG_ENTRY(OpenMPPre51Compat /* pre-openmp-51-compat */, 14863, /* DiagArray691 */ 2049, 0, R"()")
DIAG_ENTRY(anonymous_140 /* predefined-identifier-outside-function */, 14884, /* DiagArray692 */ 2051, 0, R"()")
DIAG_ENTRY(PrivateExtern /* private-extern */, 14923, /* DiagArray693 */ 2053, 0, R"()")
DIAG_ENTRY(anonymous_121 /* private-header */, 14938, /* DiagArray694 */ 2055, 0, R"()")
DIAG_ENTRY(PrivateModule /* private-module */, 14953, /* DiagArray695 */ 2057, 0, R"()")
DIAG_ENTRY(ProfileInstrMissing /* profile-instr-missing */, 14968, /* DiagArray696 */ 2062, 0, R"()")
DIAG_ENTRY(ProfileInstrOutOfDate /* profile-instr-out-of-date */, 14990, /* DiagArray697 */ 2064, 0, R"()")
DIAG_ENTRY(ProfileInstrUnprofiled /* profile-instr-unprofiled */, 15016, /* DiagArray698 */ 2066, 0, R"()")
DIAG_ENTRY(PropertyAccessDotSyntax /* property-access-dot-syntax */, 15041, /* DiagArray699 */ 2068, 0, R"()")
DIAG_ENTRY(PropertyAttr /* property-attribute-mismatch */, 15068, /* DiagArray700 */ 2070, 0, R"()")
DIAG_ENTRY(Protocol /* protocol */, 15096, /* DiagArray701 */ 2075, 0, R"()")
DIAG_ENTRY(anonymous_154 /* protocol-property-synthesis-ambiguity */, 15105, /* DiagArray702 */ 2077, 0, R"()")
DIAG_ENTRY(anonymous_94 /* psabi */, 15143, /* DiagArray703 */ 2079, 0, R"()")
DIAG_ENTRY(anonymous_218 /* qualified-void-return-type */, 15149, /* DiagArray704 */ 2081, 0, R"()")
DIAG_ENTRY(FrameworkHdrQuotedInclude /* quoted-include-in-framework-header */, 15176, /* DiagArray705 */ 2083, 0, R"()")
DIAG_ENTRY(RangeLoopAnalysis /* range-loop-analysis */, 15211, 0, /* DiagSubGroup706 */ 586, R"()")
DIAG_ENTRY(RangeLoopBindReference /* range-loop-bind-reference */, 15231, /* DiagArray707 */ 2085, 0, R"()")
DIAG_ENTRY(RangeLoopConstruct /* range-loop-construct */, 15257, /* DiagArray708 */ 2087, 0, R"()")
DIAG_ENTRY(ReadOnlyPlacementChecks /* read-only-types */, 15278, /* DiagArray709 */ 2090, 0, R"()")
DIAG_ENTRY(anonymous_164 /* readonly-iboutlet-property */, 15294, /* DiagArray710 */ 2092, 0, R"()")
DIAG_ENTRY(ObjCReceiver /* receiver-expr */, 15321, /* DiagArray711 */ 2094, 0, R"()")
DIAG_ENTRY(ForwardClassReceiver /* receiver-forward-class */, 15335, /* DiagArray712 */ 2096, 0, R"()")
DIAG_ENTRY(RedeclaredClassMember /* redeclared-class-member */, 15358, /* DiagArray713 */ 2099, 0, R"()")
DIAG_ENTRY(anonymous_166 /* redundant-consteval-if */, 15382, /* DiagArray714 */ 2101, 0, R"()")
DIAG_ENTRY(anonymous_36 /* redundant-decls */, 15405, 0, 0, R"()")
DIAG_ENTRY(RedundantMove /* redundant-move */, 15421, /* DiagArray716 */ 2103, 0, R"()")
DIAG_ENTRY(anonymous_142 /* redundant-parens */, 15436, /* DiagArray717 */ 2105, 0, R"()")
DIAG_ENTRY(Register /* register */, 15453, /* DiagArray718 */ 2107, /* DiagSubGroup718 */ 589, R"()")
DIAG_ENTRY(ReinterpretBaseClass /* reinterpret-base-class */, 15462, /* DiagArray719 */ 2109, 0, R"()")
DIAG_ENTRY(RemarkBackendPlugin /* remark-backend-plugin */, 15485, /* DiagArray720 */ 2111, 0, R"()")
DIAG_ENTRY(Reorder /* reorder */, 15507, 0, /* DiagSubGroup721 */ 591, R"()")
DIAG_ENTRY(ReorderCtor /* reorder-ctor */, 15515, /* DiagArray722 */ 2113, 0, R"()")
DIAG_ENTRY(ReorderInitList /* reorder-init-list */, 15528, /* DiagArray723 */ 2116, 0, R"()")
DIAG_ENTRY(anonymous_197 /* requires-super-attribute */, 15546, /* DiagArray724 */ 2118, 0, R"()")
DIAG_ENTRY(ReservedIdAsMacroAlias /* reserved-id-macro */, 15571, 0, /* DiagSubGroup725 */ 594, R"()")
DIAG_ENTRY(ReservedIdentifier /* reserved-identifier */, 15589, /* DiagArray726 */ 2120, /* DiagSubGroup726 */ 596, R"()")
DIAG_ENTRY(ReservedIdAsMacro /* reserved-macro-identifier */, 15609, /* DiagArray727 */ 2122, 0, R"()")
DIAG_ENTRY(ReservedModuleIdentifier /* reserved-module-identifier */, 15635, /* DiagArray728 */ 2124, 0, R"()")
DIAG_ENTRY(ReservedUserDefinedLiteral /* reserved-user-defined-literal */, 15662, /* DiagArray729 */ 2126, /* DiagSubGroup729 */ 599, R"()")
DIAG_ENTRY(RestrictExpansionMacro /* restrict-expansion */, 15692, /* DiagArray730 */ 2129, 0, R"()")
DIAG_ENTRY(anonymous_211 /* retained-language-linkage */, 15711, /* DiagArray731 */ 2131, 0, R"()")
DIAG_ENTRY(anonymous_23 /* return-local-addr */, 15737, 0, /* DiagSubGroup732 */ 601, R"()")
DIAG_ENTRY(ReturnStackAddress /* return-stack-address */, 15755, /* DiagArray733 */ 2133, 0, R"()")
DIAG_ENTRY(ReturnStdMove /* return-std-move */, 15776, 0, 0, R"()")
DIAG_ENTRY(ReturnType /* return-type */, 15792, /* DiagArray735 */ 2137, /* DiagSubGroup735 */ 603, R"()")
DIAG_ENTRY(ReturnTypeCLinkage /* return-type-c-linkage */, 15804, /* DiagArray736 */ 2147, 0, R"()")
DIAG_ENTRY(anonymous_199 /* rewrite-not-bool */, 15826, /* DiagArray737 */ 2150, 0, R"()")
DIAG_ENTRY(RoundTripCC1Args /* round-trip-cc1-args */, 15843, /* DiagArray738 */ 2152, 0, R"()")
DIAG_ENTRY(RTTI /* rtti */, 15863, /* DiagArray739 */ 2154, 0, R"()")
DIAG_ENTRY(SanitizeAddressRemarks /* sanitize-address */, 15868, /* DiagArray740 */ 2157, 0, R"()")
DIAG_ENTRY(anonymous_89 /* sarif-format-unstable */, 15885, /* DiagArray741 */ 2160, 0, R"()")
DIAG_ENTRY(UsedSearchPath /* search-path-usage */, 15907, /* DiagArray742 */ 2162, 0, R"()")
DIAG_ENTRY(Section /* section */, 15925, /* DiagArray743 */ 2164, 0, R"()")
DIAG_ENTRY(Selector /* selector */, 15933, /* DiagArray744 */ 2168, /* DiagSubGroup744 */ 605, R"()")
DIAG_ENTRY(SelectorTypeMismatch /* selector-type-mismatch */, 15942, /* DiagArray745 */ 2170, 0, R"()")
DIAG_ENTRY(SelfAssignment /* self-assign */, 15965, /* DiagArray746 */ 2172, /* DiagSubGroup746 */ 607, R"()")
DIAG_ENTRY(SelfAssignmentField /* self-assign-field */, 15977, /* DiagArray747 */ 2174, 0, R"()")
DIAG_ENTRY(SelfAssignmentOverloaded /* self-assign-overloaded */, 15995, /* DiagArray748 */ 2176, 0, R"()")
DIAG_ENTRY(SelfMove /* self-move */, 16018, /* DiagArray749 */ 2178, 0, R"()")
DIAG_ENTRY(SemiBeforeMethodBody /* semicolon-before-method-body */, 16028, /* DiagArray750 */ 2180, 0, R"()")
DIAG_ENTRY(Sentinel /* sentinel */, 16057, /* DiagArray751 */ 2182, 0, R"()")
DIAG_ENTRY(anonymous_40 /* sequence-point */, 16066, 0, /* DiagSubGroup752 */ 610, R"()")
DIAG_ENTRY(SerializedDiagnostics /* serialized-diagnostics */, 16081, /* DiagArray753 */ 2185, 0, R"()")
DIAG_ENTRY(Shadow /* shadow */, 16104, /* DiagArray754 */ 2189, /* DiagSubGroup754 */ 612, R"()")
DIAG_ENTRY(ShadowAll /* shadow-all */, 16111, 0, /* DiagSubGroup755 */ 615, R"()")
DIAG_ENTRY(ShadowField /* shadow-field */, 16122, /* DiagArray756 */ 2191, 0, R"()")
DIAG_ENTRY(ShadowFieldInConstructor /* shadow-field-in-constructor */, 16135, /* DiagArray757 */ 2193, /* DiagSubGroup757 */ 620, R"()")
DIAG_ENTRY(ShadowFieldInConstructorModified /* shadow-field-in-constructor-modified */, 16163, /* DiagArray758 */ 2195, 0, R"()")
DIAG_ENTRY(ShadowIvar /* shadow-ivar */, 16200, /* DiagArray759 */ 2197, 0, R"()")
DIAG_ENTRY(ShadowUncapturedLocal /* shadow-uncaptured-local */, 16212, /* DiagArray760 */ 2199, 0, R"()")
DIAG_ENTRY(anonymous_223 /* shift-count-negative */, 16236, /* DiagArray761 */ 2201, 0, R"()")
DIAG_ENTRY(anonymous_224 /* shift-count-overflow */, 16257, /* DiagArray762 */ 2203, 0, R"()")
DIAG_ENTRY(anonymous_222 /* shift-negative-value */, 16278, /* DiagArray763 */ 2205, 0, R"()")
DIAG_ENTRY(ShiftOpParentheses /* shift-op-parentheses */, 16299, /* DiagArray764 */ 2207, 0, R"()")
DIAG_ENTRY(anonymous_225 /* shift-overflow */, 16320, /* DiagArray765 */ 2209, 0, R"()")
DIAG_ENTRY(anonymous_226 /* shift-sign-overflow */, 16335, /* DiagArray766 */ 2211, 0, R"()")
DIAG_ENTRY(Shorten64To32 /* shorten-64-to-32 */, 16355, /* DiagArray767 */ 2213, 0, R"()")
DIAG_ENTRY(SignCompare /* sign-compare */, 16372, /* DiagArray768 */ 2215, 0, R"()")
DIAG_ENTRY(SignConversion /* sign-conversion */, 16385, /* DiagArray769 */ 2217, 0, R"()")
DIAG_ENTRY(anonymous_37 /* sign-promo */, 16401, 0, 0, R"()")
DIAG_ENTRY(SignedEnumBitfield /* signed-enum-bitfield */, 16412, /* DiagArray771 */ 2221, 0, R"()")
DIAG_ENTRY(anonymous_266 /* signed-unsigned-wchar */, 16433, /* DiagArray772 */ 2223, 0, R"()")
DIAG_ENTRY(SingleBitBitFieldConstantConversion /* single-bit-bitfield-constant-conversion */, 16455, /* DiagArray773 */ 2225, 0, R"()")
DIAG_ENTRY(SizeofArrayArgument /* sizeof-array-argument */, 16495, /* DiagArray774 */ 2227, 0, R"()")
DIAG_ENTRY(SizeofArrayDecay /* sizeof-array-decay */, 16517, /* DiagArray775 */ 2229, 0, R"()")
DIAG_ENTRY(anonymous_193 /* sizeof-array-div */, 16536, /* DiagArray776 */ 2231, 0, R"()")
DIAG_ENTRY(anonymous_192 /* sizeof-pointer-div */, 16553, /* DiagArray777 */ 2233, 0, R"()")
DIAG_ENTRY(SizeofPointerMemaccess /* sizeof-pointer-memaccess */, 16572, /* DiagArray778 */ 2235, 0, R"()")
DIAG_ENTRY(anonymous_85 /* slash-u-filename */, 16597, /* DiagArray779 */ 2238, 0, R"()")
DIAG_ENTRY(anonymous_77 /* slh-asm-goto */, 16614, /* DiagArray780 */ 2240, 0, R"()")
DIAG_ENTRY(anonymous_78 /* sloc-usage */, 16627, /* DiagArray781 */ 2242, 0, R"()")
DIAG_ENTRY(UninitializedSometimes /* sometimes-uninitialized */, 16638, /* DiagArray782 */ 2244, 0, R"()")
DIAG_ENTRY(BackendSourceMgr /* source-mgr */, 16662, /* DiagArray783 */ 2246, 0, R"()")
DIAG_ENTRY(SourceUsesOpenMP /* source-uses-openmp */, 16673, /* DiagArray784 */ 2248, 0, R"()")
DIAG_ENTRY(SpirCompat /* spir-compat */, 16692, /* DiagArray785 */ 2257, 0, R"()")
DIAG_ENTRY(anonymous_70 /* spirv-compat */, 16704, 0, /* DiagSubGroup786 */ 622, R"()")
DIAG_ENTRY(anonymous_75 /* stack-exhausted */, 16717, /* DiagArray787 */ 2259, 0, R"()")
DIAG_ENTRY(anonymous_76 /* stack-protector */, 16733, /* DiagArray788 */ 2261, 0, R"()")
DIAG_ENTRY(StaticFloatInit /* static-float-init */, 16749, /* DiagArray789 */ 2263, /* DiagSubGroup789 */ 624, R"()")
DIAG_ENTRY(StaticInInline /* static-in-inline */, 16767, /* DiagArray790 */ 2265, 0, R"()")
DIAG_ENTRY(anonymous_136 /* static-inline-explicit-instantiation */, 16784, /* DiagArray791 */ 2268, 0, R"()")
DIAG_ENTRY(StaticLocalInInline /* static-local-in-inline */, 16821, /* DiagArray792 */ 2270, 0, R"()")
DIAG_ENTRY(UninitializedStaticSelfInit /* static-self-init */, 16844, /* DiagArray793 */ 2272, 0, R"()")
DIAG_ENTRY(anonymous_88 /* stdlibcxx-not-found */, 16861, /* DiagArray794 */ 2274, 0, R"()")
DIAG_ENTRY(anonymous_44 /* strict-aliasing */, 16881, 0, 0, R"()")
DIAG_ENTRY(anonymous_41 /* strict-aliasing=0 */, 16897, 0, 0, R"()")
DIAG_ENTRY(anonymous_42 /* strict-aliasing=1 */, 16915, 0, 0, R"()")
DIAG_ENTRY(anonymous_43 /* strict-aliasing=2 */, 16933, 0, 0, R"()")
DIAG_ENTRY(anonymous_51 /* strict-overflow */, 16951, 0, 0, R"()")
DIAG_ENTRY(anonymous_45 /* strict-overflow=0 */, 16967, 0, 0, R"()")
DIAG_ENTRY(anonymous_46 /* strict-overflow=1 */, 16985, 0, 0, R"()")
DIAG_ENTRY(anonymous_47 /* strict-overflow=2 */, 17003, 0, 0, R"()")
DIAG_ENTRY(anonymous_48 /* strict-overflow=3 */, 17021, 0, 0, R"()")
DIAG_ENTRY(anonymous_49 /* strict-overflow=4 */, 17039, 0, 0, R"()")
DIAG_ENTRY(anonymous_50 /* strict-overflow=5 */, 17057, 0, 0, R"()")
DIAG_ENTRY(ObjCStrictPotentiallyDirectSelector /* strict-potentially-direct-selector */, 17075, /* DiagArray806 */ 2276, /* DiagSubGroup806 */ 626, R"()")
DIAG_ENTRY(StrictPrototypes /* strict-prototypes */, 17110, /* DiagArray807 */ 2278, /* DiagSubGroup807 */ 628, R"()")
DIAG_ENTRY(StrictSelector /* strict-selector-match */, 17128, /* DiagArray808 */ 2280, 0, R"()")
DIAG_ENTRY(StringCompare /* string-compare */, 17150, /* DiagArray809 */ 2282, 0, R"()")
DIAG_ENTRY(StringConcatation /* string-concatenation */, 17165, /* DiagArray810 */ 2284, 0, R"()")
DIAG_ENTRY(StringConversion /* string-conversion */, 17186, /* DiagArray811 */ 2286, 0, R"()")
DIAG_ENTRY(StringPlusChar /* string-plus-char */, 17204, /* DiagArray812 */ 2288, 0, R"()")
DIAG_ENTRY(StringPlusInt /* string-plus-int */, 17221, /* DiagArray813 */ 2290, 0, R"()")
DIAG_ENTRY(anonymous_147 /* strlcpy-strlcat-size */, 17237, /* DiagArray814 */ 2292, 0, R"()")
DIAG_ENTRY(StrncatSize /* strncat-size */, 17258, /* DiagArray815 */ 2294, 0, R"()")
DIAG_ENTRY(CXX11WarnSuggestOverrideDestructor /* suggest-destructor-override */, 17271, /* DiagArray816 */ 2298, 0, R"()")
DIAG_ENTRY(CXX11WarnSuggestOverride /* suggest-override */, 17299, /* DiagArray817 */ 2300, 0, R"()")
DIAG_ENTRY(SuperSubClassMismatch /* super-class-method-mismatch */, 17316, /* DiagArray818 */ 2302, 0, R"()")
DIAG_ENTRY(SuspiciousBzero /* suspicious-bzero */, 17344, /* DiagArray819 */ 2304, 0, R"()")
DIAG_ENTRY(SuspiciousMemaccess /* suspicious-memaccess */, 17361, 0, /* DiagSubGroup820 */ 630, R"()")
DIAG_ENTRY(SwiftNameAttribute /* swift-name-attribute */, 17382, /* DiagArray821 */ 2306, 0, R"()")
DIAG_ENTRY(Switch /* switch */, 17403, /* DiagArray822 */ 2320, 0, R"()")
DIAG_ENTRY(SwitchBool /* switch-bool */, 17410, /* DiagArray823 */ 2324, 0, R"()")
DIAG_ENTRY(anonymous_38 /* switch-default */, 17422, 0, 0, R"()")
DIAG_ENTRY(SwitchEnum /* switch-enum */, 17437, /* DiagArray825 */ 2326, 0, R"()")
DIAG_ENTRY(SyncAlignment /* sync-alignment */, 17449, /* DiagArray826 */ 2328, 0, R"()")
DIAG_ENTRY(anonymous_265 /* sync-fetch-and-nand-semantics-changed */, 17464, /* DiagArray827 */ 2330, 0, R"()")
DIAG_ENTRY(anonymous_39 /* synth */, 17502, 0, 0, R"()")
DIAG_ENTRY(TargetClonesMixedSpecifiers /* target-clones-mixed-specifiers */, 17508, /* DiagArray829 */ 2332, 0, R"()")
DIAG_ENTRY(TautologicalBitwiseCompare /* tautological-bitwise-compare */, 17539, /* DiagArray830 */ 2334, 0, R"()")
DIAG_ENTRY(TautologicalCompare /* tautological-compare */, 17568, /* DiagArray831 */ 2337, /* DiagSubGroup831 */ 636, R"()")
DIAG_ENTRY(TautologicalConstantCompare /* tautological-constant-compare */, 17589, /* DiagArray832 */ 2340, /* DiagSubGroup832 */ 643, R"()")
DIAG_ENTRY(TautologicalInRangeCompare /* tautological-constant-in-range-compare */, 17619, 0, /* DiagSubGroup833 */ 645, R"()")
DIAG_ENTRY(TautologicalOutOfRangeCompare /* tautological-constant-out-of-range-compare */, 17658, /* DiagArray834 */ 2344, 0, R"()")
DIAG_ENTRY(TautologicalObjCBoolCompare /* tautological-objc-bool-compare */, 17701, /* DiagArray835 */ 2346, 0, R"()")
DIAG_ENTRY(TautologicalOverlapCompare /* tautological-overlap-compare */, 17732, /* DiagArray836 */ 2348, 0, R"()")
DIAG_ENTRY(TautologicalPointerCompare /* tautological-pointer-compare */, 17761, /* DiagArray837 */ 2350, 0, R"()")
DIAG_ENTRY(TautologicalTypeLimitCompare /* tautological-type-limit-compare */, 17790, /* DiagArray838 */ 2353, 0, R"()")
DIAG_ENTRY(TautologicalUndefinedCompare /* tautological-undefined-compare */, 17822, /* DiagArray839 */ 2355, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedCharZeroCompare /* tautological-unsigned-char-zero-compare */, 17853, /* DiagArray840 */ 2358, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedEnumZeroCompare /* tautological-unsigned-enum-zero-compare */, 17893, /* DiagArray841 */ 2360, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedZeroCompare /* tautological-unsigned-zero-compare */, 17933, /* DiagArray842 */ 2362, 0, R"()")
DIAG_ENTRY(TautologicalValueRangeCompare /* tautological-value-range-compare */, 17968, /* DiagArray843 */ 2364, 0, R"()")
DIAG_ENTRY(anonymous_274 /* tcb-enforcement */, 18001, /* DiagArray844 */ 2366, 0, R"()")
DIAG_ENTRY(anonymous_229 /* tentative-definition-incomplete-type */, 18017, /* DiagArray845 */ 2368, 0, R"()")
DIAG_ENTRY(ThreadSafety /* thread-safety */, 18054, 0, /* DiagSubGroup846 */ 648, R"()")
DIAG_ENTRY(ThreadSafetyAnalysis /* thread-safety-analysis */, 18068, /* DiagArray847 */ 2370, 0, R"()")
DIAG_ENTRY(ThreadSafetyAttributes /* thread-safety-attributes */, 18091, /* DiagArray848 */ 2389, 0, R"()")
DIAG_ENTRY(ThreadSafetyBeta /* thread-safety-beta */, 18116, /* DiagArray849 */ 2396, 0, R"()")
DIAG_ENTRY(ThreadSafetyNegative /* thread-safety-negative */, 18135, /* DiagArray850 */ 2398, 0, R"()")
DIAG_ENTRY(ThreadSafetyPrecise /* thread-safety-precise */, 18158, /* DiagArray851 */ 2400, 0, R"()")
DIAG_ENTRY(ThreadSafetyReference /* thread-safety-reference */, 18180, /* DiagArray852 */ 2404, 0, R"()")
DIAG_ENTRY(ThreadSafetyVerbose /* thread-safety-verbose */, 18204, /* DiagArray853 */ 2407, 0, R"()")
DIAG_ENTRY(Trigraphs /* trigraphs */, 18226, /* DiagArray854 */ 2409, 0, R"()")
DIAG_ENTRY(TypeLimits /* type-limits */, 18236, 0, /* DiagSubGroup855 */ 653, R"()")
DIAG_ENTRY(TypeSafety /* type-safety */, 18248, /* DiagArray856 */ 2414, 0, R"()")
DIAG_ENTRY(anonymous_210 /* typedef-redefinition */, 18260, /* DiagArray857 */ 2418, 0, R"()")
DIAG_ENTRY(anonymous_204 /* typename-missing */, 18281, /* DiagArray858 */ 2420, 0, R"()")
DIAG_ENTRY(anonymous_91 /* unable-to-open-stats-file */, 18298, /* DiagArray859 */ 2422, 0, R"()")
DIAG_ENTRY(UnalignedAccess /* unaligned-access */, 18324, /* DiagArray860 */ 2424, 0, R"()")
DIAG_ENTRY(anonymous_268 /* unaligned-qualifier-implicit-cast */, 18341, /* DiagArray861 */ 2426, 0, R"()")
DIAG_ENTRY(UnavailableDeclarations /* unavailable-declarations */, 18375, /* DiagArray862 */ 2428, 0, R"()")
DIAG_ENTRY(UndeclaredSelector /* undeclared-selector */, 18400, /* DiagArray863 */ 2430, 0, R"()")
DIAG_ENTRY(anonymous_113 /* undef */, 18420, /* DiagArray864 */ 2433, 0, R"()")
DIAG_ENTRY(anonymous_114 /* undef-prefix */, 18426, /* DiagArray865 */ 2435, 0, R"()")
DIAG_ENTRY(UndefinedBoolConversion /* undefined-bool-conversion */, 18439, /* DiagArray866 */ 2437, 0, R"()")
DIAG_ENTRY(UndefinedFuncTemplate /* undefined-func-template */, 18465, /* DiagArray867 */ 2440, 0, R"()")
DIAG_ENTRY(anonymous_209 /* undefined-inline */, 18489, /* DiagArray868 */ 2442, 0, R"()")
DIAG_ENTRY(anonymous_207 /* undefined-internal */, 18506, /* DiagArray869 */ 2444, 0, R"()")
DIAG_ENTRY(anonymous_208 /* undefined-internal-type */, 18525, /* DiagArray870 */ 2446, 0, R"()")
DIAG_ENTRY(UndefinedReinterpretCast /* undefined-reinterpret-cast */, 18549, /* DiagArray871 */ 2448, 0, R"()")
DIAG_ENTRY(UndefinedVarTemplate /* undefined-var-template */, 18576, /* DiagArray872 */ 2451, 0, R"()")
DIAG_ENTRY(UnderalignedExceptionObject /* underaligned-exception-object */, 18599, /* DiagArray873 */ 2453, 0, R"()")
DIAG_ENTRY(UnevaluatedExpression /* unevaluated-expression */, 18629, /* DiagArray874 */ 2455, /* DiagSubGroup874 */ 658, R"()")
DIAG_ENTRY(UnguardedAvailability /* unguarded-availability */, 18652, /* DiagArray875 */ 2457, /* DiagSubGroup875 */ 660, R"()")
DIAG_ENTRY(UnguardedAvailabilityNew /* unguarded-availability-new */, 18675, /* DiagArray876 */ 2459, 0, R"()")
DIAG_ENTRY(Unicode /* unicode */, 18702, /* DiagArray877 */ 2461, 0, R"()")
DIAG_ENTRY(anonymous_100 /* unicode-homoglyph */, 18710, /* DiagArray878 */ 2469, 0, R"()")
DIAG_ENTRY(anonymous_99 /* unicode-whitespace */, 18728, /* DiagArray879 */ 2471, 0, R"()")
DIAG_ENTRY(anonymous_101 /* unicode-zero-width */, 18747, /* DiagArray880 */ 2473, 0, R"()")
DIAG_ENTRY(Uninitialized /* uninitialized */, 18766, /* DiagArray881 */ 2475, /* DiagSubGroup881 */ 662, R"()")
DIAG_ENTRY(UninitializedConstReference /* uninitialized-const-reference */, 18780, /* DiagArray882 */ 2483, 0, R"()")
DIAG_ENTRY(UnknownArgument /* unknown-argument */, 18810, /* DiagArray883 */ 2485, 0, R"()")
DIAG_ENTRY(UnknownAssumption /* unknown-assumption */, 18827, /* DiagArray884 */ 2489, 0, R"()")
DIAG_ENTRY(UnknownAttributes /* unknown-attributes */, 18846, /* DiagArray885 */ 2491, 0, R"()")
DIAG_ENTRY(CudaUnknownVersion /* unknown-cuda-version */, 18865, /* DiagArray886 */ 2493, 0, R"()")
DIAG_ENTRY(anonymous_118 /* unknown-directives */, 18886, /* DiagArray887 */ 2496, 0, R"()")
DIAG_ENTRY(anonymous_104 /* unknown-escape-sequence */, 18905, /* DiagArray888 */ 2498, 0, R"()")
DIAG_ENTRY(UnknownPragmas /* unknown-pragmas */, 18929, /* DiagArray889 */ 2500, 0, R"()")
DIAG_ENTRY(UnknownSanitizers /* unknown-sanitizers */, 18945, /* DiagArray890 */ 2521, 0, R"()")
DIAG_ENTRY(UnknownWarningOption /* unknown-warning-option */, 18964, /* DiagArray891 */ 2523, 0, R"()")
DIAG_ENTRY(UnnamedTypeTemplateArgs /* unnamed-type-template-args */, 18987, /* DiagArray892 */ 2527, /* DiagSubGroup892 */ 666, R"()")
DIAG_ENTRY(UnneededInternalDecl /* unneeded-internal-declaration */, 19014, /* DiagArray893 */ 2529, 0, R"()")
DIAG_ENTRY(UnneededMemberFunction /* unneeded-member-function */, 19044, /* DiagArray894 */ 2532, 0, R"()")
DIAG_ENTRY(anonymous_200 /* unqualified-std-cast-call */, 19069, /* DiagArray895 */ 2534, 0, R"()")
DIAG_ENTRY(UnreachableCode /* unreachable-code */, 19095, /* DiagArray896 */ 2536, /* DiagSubGroup896 */ 668, R"()")
DIAG_ENTRY(UnreachableCodeAggressive /* unreachable-code-aggressive */, 19112, 0, /* DiagSubGroup897 */ 672, R"()")
DIAG_ENTRY(UnreachableCodeBreak /* unreachable-code-break */, 19140, /* DiagArray898 */ 2538, 0, R"()")
DIAG_ENTRY(UnreachableCodeFallthrough /* unreachable-code-fallthrough */, 19163, /* DiagArray899 */ 2540, 0, R"()")
DIAG_ENTRY(UnreachableCodeGenericAssoc /* unreachable-code-generic-assoc */, 19192, /* DiagArray900 */ 2542, 0, R"()")
DIAG_ENTRY(UnreachableCodeLoopIncrement /* unreachable-code-loop-increment */, 19223, /* DiagArray901 */ 2544, 0, R"()")
DIAG_ENTRY(UnreachableCodeReturn /* unreachable-code-return */, 19255, /* DiagArray902 */ 2546, 0, R"()")
DIAG_ENTRY(UnsafeBufferUsage /* unsafe-buffer-usage */, 19279, /* DiagArray903 */ 2548, 0, R"()")
DIAG_ENTRY(Unsequenced /* unsequenced */, 19299, /* DiagArray904 */ 2551, 0, R"()")
DIAG_ENTRY(anonymous_80 /* unsupported-abi */, 19311, /* DiagArray905 */ 2554, 0, R"()")
DIAG_ENTRY(UnsupportedAbs /* unsupported-abs */, 19327, /* DiagArray906 */ 2556, 0, R"()")
DIAG_ENTRY(anonymous_189 /* unsupported-availability-guard */, 19343, /* DiagArray907 */ 2559, 0, R"()")
DIAG_ENTRY(UnsupportedCB /* unsupported-cb */, 19374, /* DiagArray908 */ 2561, 0, R"()")
DIAG_ENTRY(anonymous_185 /* unsupported-dll-base-class-template */, 19389, /* DiagArray909 */ 2563, 0, R"()")
DIAG_ENTRY(UnsupportedFPOpt /* unsupported-floating-point-opt */, 19425, /* DiagArray910 */ 2565, 0, R"()")
DIAG_ENTRY(UnsupportedFriend /* unsupported-friend */, 19456, /* DiagArray911 */ 2568, 0, R"()")
DIAG_ENTRY(UnsupportedGPOpt /* unsupported-gpopt */, 19475, /* DiagArray912 */ 2571, 0, R"()")
DIAG_ENTRY(UnsupportedNan /* unsupported-nan */, 19493, /* DiagArray913 */ 2573, 0, R"()")
DIAG_ENTRY(UnsupportedTargetOpt /* unsupported-target-opt */, 19509, /* DiagArray914 */ 2576, 0, R"()")
DIAG_ENTRY(anonymous_195 /* unsupported-visibility */, 19532, /* DiagArray915 */ 2579, 0, R"()")
DIAG_ENTRY(anonymous_202 /* unusable-partial-specialization */, 19555, /* DiagArray916 */ 2581, 0, R"()")
DIAG_ENTRY(Unused /* unused */, 19587, 0, /* DiagSubGroup917 */ 676, R"()")
DIAG_ENTRY(UnusedArgument /* unused-argument */, 19594, 0, 0, R"()")
DIAG_ENTRY(UnusedButSetParameter /* unused-but-set-parameter */, 19610, /* DiagArray919 */ 2583, 0, R"()")
DIAG_ENTRY(UnusedButSetVariable /* unused-but-set-variable */, 19635, /* DiagArray920 */ 2585, 0, R"()")
DIAG_ENTRY(UnusedCommandLineArgument /* unused-command-line-argument */, 19659, /* DiagArray921 */ 2587, 0, R"()")
DIAG_ENTRY(UnusedComparison /* unused-comparison */, 19688, /* DiagArray922 */ 2600, 0, R"()")
DIAG_ENTRY(UnusedConstVariable /* unused-const-variable */, 19706, /* DiagArray923 */ 2602, 0, R"()")
DIAG_ENTRY(UnusedExceptionParameter /* unused-exception-parameter */, 19728, /* DiagArray924 */ 2604, 0, R"()")
DIAG_ENTRY(UnusedFunction /* unused-function */, 19755, /* DiagArray925 */ 2606, /* DiagSubGroup925 */ 687, R"()")
DIAG_ENTRY(UnusedGetterReturnValue /* unused-getter-return-value */, 19771, /* DiagArray926 */ 2608, 0, R"()")
DIAG_ENTRY(UnusedLabel /* unused-label */, 19798, /* DiagArray927 */ 2610, 0, R"()")
DIAG_ENTRY(UnusedLambdaCapture /* unused-lambda-capture */, 19811, /* DiagArray928 */ 2612, 0, R"()")
DIAG_ENTRY(UnusedLocalTypedef /* unused-local-typedef */, 19833, /* DiagArray929 */ 2614, 0, R"()")
DIAG_ENTRY(anonymous_63 /* unused-local-typedefs */, 19854, 0, /* DiagSubGroup930 */ 689, R"()")
DIAG_ENTRY(anonymous_112 /* unused-macros */, 19876, /* DiagArray931 */ 2616, 0, R"()")
DIAG_ENTRY(UnusedMemberFunction /* unused-member-function */, 19890, /* DiagArray932 */ 2618, /* DiagSubGroup932 */ 691, R"()")
DIAG_ENTRY(UnusedParameter /* unused-parameter */, 19913, /* DiagArray933 */ 2620, 0, R"()")
DIAG_ENTRY(UnusedPrivateField /* unused-private-field */, 19930, /* DiagArray934 */ 2622, 0, R"()")
DIAG_ENTRY(UnusedPropertyIvar /* unused-property-ivar */, 19951, /* DiagArray935 */ 2624, 0, R"()")
DIAG_ENTRY(UnusedResult /* unused-result */, 19972, /* DiagArray936 */ 2626, 0, R"()")
DIAG_ENTRY(UnusedTemplate /* unused-template */, 19986, /* DiagArray937 */ 2629, /* DiagSubGroup937 */ 693, R"()")
DIAG_ENTRY(UnusedValue /* unused-value */, 20002, /* DiagArray938 */ 2631, /* DiagSubGroup938 */ 695, R"()")
DIAG_ENTRY(UnusedVariable /* unused-variable */, 20015, /* DiagArray939 */ 2639, /* DiagSubGroup939 */ 699, R"()")
DIAG_ENTRY(anonymous_251 /* unused-volatile-lvalue */, 20031, /* DiagArray940 */ 2641, 0, R"()")
DIAG_ENTRY(UsedButMarkedUnused /* used-but-marked-unused */, 20054, /* DiagArray941 */ 2643, 0, R"()")
DIAG_ENTRY(UserDefinedLiterals /* user-defined-literals */, 20077, /* DiagArray942 */ 2645, 0, R"()")
DIAG_ENTRY(UserDefinedWarnings /* user-defined-warnings */, 20099, /* DiagArray943 */ 2647, 0, R"()")
DIAG_ENTRY(Varargs /* varargs */, 20121, /* DiagArray944 */ 2649, 0, R"()")
DIAG_ENTRY(VariadicMacros /* variadic-macros */, 20129, /* DiagArray945 */ 2653, 0, R"()")
DIAG_ENTRY(anonymous_175 /* vec-elem-size */, 20145, /* DiagArray946 */ 2657, 0, R"()")
DIAG_ENTRY(VectorConversion /* vector-conversion */, 20159, /* DiagArray947 */ 2659, 0, R"()")
DIAG_ENTRY(anonymous_62 /* vector-conversions */, 20177, 0, /* DiagSubGroup948 */ 701, R"()")
DIAG_ENTRY(VexingParse /* vexing-parse */, 20196, /* DiagArray949 */ 2661, 0, R"()")
DIAG_ENTRY(Visibility /* visibility */, 20209, /* DiagArray950 */ 2665, 0, R"()")
DIAG_ENTRY(VLA /* vla */, 20220, /* DiagArray951 */ 2668, /* DiagSubGroup951 */ 703, R"()")
DIAG_ENTRY(VLAExtension /* vla-extension */, 20224, /* DiagArray952 */ 2670, 0, R"()")
DIAG_ENTRY(VoidPointerToEnumCast /* void-pointer-to-enum-cast */, 20238, /* DiagArray953 */ 2672, 0, R"()")
DIAG_ENTRY(VoidPointerToIntCast /* void-pointer-to-int-cast */, 20264, /* DiagArray954 */ 2674, /* DiagSubGroup954 */ 705, R"()")
DIAG_ENTRY(VoidPointerDeref /* void-ptr-dereference */, 20289, /* DiagArray955 */ 2676, 0, R"()")
DIAG_ENTRY(VolatileRegisterVar /* volatile-register-var */, 20310, 0, 0, R"()")
DIAG_ENTRY(WebAssemblyExceptionSpec /* wasm-exception-spec */, 20332, /* DiagArray957 */ 2679, 0, R"()")
DIAG_ENTRY(anonymous_168 /* weak-template-vtables */, 20352, /* DiagArray958 */ 2681, 0, R"()")
DIAG_ENTRY(anonymous_167 /* weak-vtables */, 20374, /* DiagArray959 */ 2683, 0, R"()")
DIAG_ENTRY(WritableStrings /* writable-strings */, 20387, /* DiagArray960 */ 2685, /* DiagSubGroup960 */ 707, R"()")
DIAG_ENTRY(GCCWriteStrings /* write-strings */, 20404, 0, /* DiagSubGroup961 */ 709, R"(**Note:** enabling this warning in C will change the semantic behavior of the
program by treating all string literals as having type ``const char *``
instead of ``char *``. This can cause unexpected behaviors with type-sensitive
constructs like ``_Generic``.)")
DIAG_ENTRY(XorUsedAsPow /* xor-used-as-pow */, 20418, /* DiagArray962 */ 2687, 0, R"()")
DIAG_ENTRY(anonymous_273 /* zero-as-null-pointer-constant */, 20434, /* DiagArray963 */ 2691, 0, R"()")
DIAG_ENTRY(ZeroLengthArray /* zero-length-array */, 20464, /* DiagArray964 */ 2693, 0, R"()")
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

