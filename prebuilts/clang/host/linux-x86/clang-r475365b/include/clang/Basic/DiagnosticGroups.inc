
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
  /* DiagArray63 */ diag::warn_fe_backend_plugin, -1,
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
  /* DiagArray118 */ diag::ext_adl_only_template_id, diag::ext_bitfield_member_init, diag::ext_capture_binding, diag::ext_constexpr_body_invalid_stmt_cxx20, diag::ext_constexpr_ctor_missing_init, diag::ext_constexpr_function_try_block_cxx20, diag::ext_constexpr_local_var_no_init, diag::ext_constexpr_union_ctor_no_init, diag::ext_decomp_decl_spec, diag::ext_defaulted_comparison, diag::ext_equals_this_lambda_capture_cxx20, diag::ext_explicit_bool, diag::ext_for_range_init_stmt, diag::ext_implicit_typename, diag::ext_init_capture_pack, diag::ext_inline_nested_namespace_definition, diag::ext_lambda_template_parameter_list, diag::ext_pointer_to_const_ref_member_on_rvalue, diag::ext_using_decl_scoped_enumerator, diag::ext_using_enum_declaration, -1,
  /* DiagArray122 */ diag::err_call_operator_overload_static, diag::err_static_lambda, diag::ext_alias_in_init_statement, diag::ext_consteval_if, diag::ext_constexpr_body_invalid_stmt_cxx2b, diag::ext_constexpr_static_var, diag::ext_cxx2b_pp_directive, diag::ext_cxx2b_size_t_suffix, diag::ext_cxx_label_end_of_compound_statement, diag::ext_decl_attrs_on_lambda, diag::ext_lambda_missing_parens, -1,
  /* DiagArray128 */ diag::warn_cxx11_compat_binary_literal, -1,
  /* DiagArray130 */ diag::warn_cxx98_compat_alias_declaration, diag::warn_cxx98_compat_alignas, diag::warn_cxx98_compat_alignof, diag::warn_cxx98_compat_attribute, diag::warn_cxx98_compat_auto_type_specifier, diag::warn_cxx98_compat_constexpr, diag::warn_cxx98_compat_ctor_list_init, diag::warn_cxx98_compat_decltype, diag::warn_cxx98_compat_defaulted_deleted_function, diag::warn_cxx98_compat_delegating_ctor, diag::warn_cxx98_compat_empty_scalar_initializer, diag::warn_cxx98_compat_empty_sizeless_initializer, diag::warn_cxx98_compat_enum_fixed_underlying_type, diag::warn_cxx98_compat_enum_friend, diag::warn_cxx98_compat_enum_nested_name_spec, diag::warn_cxx98_compat_explicit_conversion_functions, diag::warn_cxx98_compat_for_range, diag::warn_cxx98_compat_friend_is_member, diag::warn_cxx98_compat_generalized_initializer_lists, diag::warn_cxx98_compat_goto_into_protected_scope, diag::warn_cxx98_compat_indirect_goto_in_protected_scope, diag::warn_cxx98_compat_initializer_list_init, diag::warn_cxx98_compat_inline_namespace, diag::warn_cxx98_compat_lambda, diag::warn_cxx98_compat_less_colon_colon, diag::warn_cxx98_compat_literal_operator, diag::warn_cxx98_compat_literal_ucn_control_character, diag::warn_cxx98_compat_literal_ucn_escape_basic_scs, diag::warn_cxx98_compat_noexcept_decl, diag::warn_cxx98_compat_noexcept_expr, diag::warn_cxx98_compat_non_static_member_use, diag::warn_cxx98_compat_nonclass_type_friend, diag::warn_cxx98_compat_nonstatic_member_init, diag::warn_cxx98_compat_nontrivial_union_or_anon_struct_member, diag::warn_cxx98_compat_nullptr, diag::warn_cxx98_compat_override_control_keyword, diag::warn_cxx98_compat_pass_non_pod_arg_to_vararg, diag::warn_cxx98_compat_raw_string_literal, diag::warn_cxx98_compat_ref_qualifier, diag::warn_cxx98_compat_reference_list_init, diag::warn_cxx98_compat_rvalue_reference, diag::warn_cxx98_compat_scoped_enum, diag::warn_cxx98_compat_sfinae_access_control, diag::warn_cxx98_compat_static_assert, diag::warn_cxx98_compat_static_data_member_in_union, diag::warn_cxx98_compat_switch_into_protected_scope, diag::warn_cxx98_compat_template_arg_extra_parens, diag::warn_cxx98_compat_template_arg_null, diag::warn_cxx98_compat_template_arg_object_internal, diag::warn_cxx98_compat_template_outside_of_template, diag::warn_cxx98_compat_template_parameter_default_in_function_template, diag::warn_cxx98_compat_trailing_return_type, diag::warn_cxx98_compat_two_right_angle_brackets, diag::warn_cxx98_compat_typename_outside_of_template, diag::warn_cxx98_compat_unelaborated_friend_type, diag::warn_cxx98_compat_unicode_literal, diag::warn_cxx98_compat_unicode_type, diag::warn_cxx98_compat_using_decl_constructor, diag::warn_cxx98_compat_variadic_templates, -1,
  /* DiagArray131 */ diag::warn_cxx98_compat_temp_copy, -1,
  /* DiagArray132 */ diag::warn_cxx98_compat_top_level_semi, -1,
  /* DiagArray133 */ diag::warn_cxx98_compat_template_arg_local_type, -1,
  /* DiagArray134 */ diag::warn_cxx98_compat_array_size_conversion, diag::warn_cxx98_compat_cast_fn_obj, diag::warn_cxx98_compat_empty_fnmacro_arg, diag::warn_cxx98_compat_enumerator_list_comma, diag::warn_cxx98_compat_extern_template, diag::warn_cxx98_compat_longlong, diag::warn_cxx98_compat_no_newline_eof, diag::warn_cxx98_compat_pp_line_too_big, diag::warn_cxx98_compat_variadic_macro, -1,
  /* DiagArray135 */ diag::warn_cxx98_compat_template_arg_unnamed_type, -1,
  /* DiagArray136 */ diag::ext_anonymous_union, diag::ext_c11_anonymous_struct, diag::ext_c11_feature, diag::ext_typecheck_compare_complete_incomplete_pointers, -1,
  /* DiagArray137 */ diag::warn_c2x_keyword, -1,
  /* DiagArray138 */ diag::ext_c2x_bitint_suffix, diag::ext_c2x_pp_directive, diag::ext_c_label_end_of_compound_statement, diag::ext_c_static_assert_no_message, diag::ext_parameter_name_omitted_c2x, -1,
  /* DiagArray139 */ diag::warn_c99_compat_unicode_id, diag::warn_c99_compat_unicode_literal, diag::warn_c99_keyword, diag::warn_old_implicitly_unsigned_long, -1,
  /* DiagArray140 */ diag::ext_designated_init, diag::ext_designated_init_array, diag::ext_designated_init_brace_elision, diag::ext_designated_init_mixed, diag::ext_designated_init_nested, -1,
  /* DiagArray141 */ diag::ext_aggregate_init_not_constant, diag::ext_c99_array_usage, diag::ext_c99_compound_literal, diag::ext_c99_feature, diag::ext_c99_flexible_array_member, diag::ext_c99_variable_decl_in_for_loop, diag::ext_c99_whitespace_required_after_macro_name, diag::ext_empty_fnmacro_arg, diag::ext_enumerator_list_comma_c, diag::ext_hex_constant_invalid, -1,
  /* DiagArray142 */ diag::warn_call_to_pure_virtual_member_function_from_ctor_dtor, -1,
  /* DiagArray143 */ diag::warn_called_once_gets_called_twice, diag::warn_called_once_never_called, diag::warn_called_once_never_called_when, -1,
  /* DiagArray144 */ diag::warn_cast_align, -1,
  /* DiagArray145 */ diag::warn_cast_calling_conv, -1,
  /* DiagArray146 */ diag::warn_cast_function_type, -1,
  /* DiagArray147 */ diag::warn_cast_pointer_from_sel, -1,
  /* DiagArray148 */ diag::warn_cast_qual, diag::warn_cast_qual2, -1,
  /* DiagArray149 */ diag::ext_bad_cxx_cast_qualifiers_away_incoherent, -1,
  /* DiagArray151 */ diag::warn_omp_section_is_char, diag::warn_subscript_is_char, -1,
  /* DiagArray152 */ diag::warn_drv_yc_multiple_inputs_clang_cl, diag::warn_drv_ycyu_different_arg_clang_cl, diag::warn_pp_hdrstop_filename_ignored, diag::warn_pp_macro_def_mismatch_with_pch, -1,
  /* DiagArray153 */ diag::warn_conv_to_base_not_used, diag::warn_conv_to_self_not_used, diag::warn_conv_to_void_not_used, -1,
  /* DiagArray154 */ diag::warn_pass_class_arg_to_vararg, -1,
  /* DiagArray155 */ diag::warn_cmse_nonsecure_union, -1,
  /* DiagArray156 */ diag::warn_comma_operator, -1,
  /* DiagArray157 */ diag::escaped_newline_block_comment_end, diag::ext_line_comment, diag::ext_multi_line_line_comment, diag::warn_nested_block_comment, -1,
  /* DiagArray159 */ diag::ext_typecheck_comparison_of_distinct_pointers, -1,
  /* DiagArray160 */ diag::warn_completion_handler_called_twice, diag::warn_completion_handler_never_called, diag::warn_completion_handler_never_called_when, -1,
  /* DiagArray161 */ diag::ext_complex_component_init, -1,
  /* DiagArray163 */ diag::warn_compound_token_split_by_macro, -1,
  /* DiagArray164 */ diag::warn_compound_token_split_by_whitespace, -1,
  /* DiagArray165 */ diag::ext_concept_legacy_bool_keyword, -1,
  /* DiagArray166 */ diag::ext_typecheck_cond_pointer_integer_mismatch, -1,
  /* DiagArray167 */ diag::warn_maybe_uninit_var, -1,
  /* DiagArray168 */ diag::warn_module_config_macro_undef, -1,
  /* DiagArray169 */ diag::warn_impcast_integer_precision_constant, -1,
  /* DiagArray170 */ diag::warn_is_constant_evaluated_always_true_constexpr, -1,
  /* DiagArray171 */ diag::warn_logical_instead_of_bitwise, -1,
  /* DiagArray172 */ diag::warn_cxx14_compat_constexpr_not_const, -1,
  /* DiagArray173 */ diag::warn_attr_on_unconsumable_class, diag::warn_loop_state_mismatch, diag::warn_param_return_typestate_mismatch, diag::warn_param_typestate_mismatch, diag::warn_return_typestate_for_unconsumable_type, diag::warn_return_typestate_mismatch, diag::warn_use_in_invalid_state, diag::warn_use_of_temp_in_invalid_state, -1,
  /* DiagArray174 */ diag::warn_impcast_complex_scalar, diag::warn_impcast_vector_scalar, diag::warn_opencl_generic_address_space_arg, diag::warn_template_arg_negative, diag::warn_template_arg_too_large, -1,
  /* DiagArray176 */ diag::warn_non_aligned_allocation_function, -1,
  /* DiagArray177 */ diag::warn_coroutine_handle_address_invalid_return_type, -1,
  /* DiagArray178 */ diag::warn_coroutine_promise_unhandled_exception_required_with_exceptions, -1,
  /* DiagArray179 */ diag::warn_unreachable_default, -1,
  /* DiagArray181 */ diag::warn_objc_cdirective_format_string, -1,
  /* DiagArray182 */ diag::warn_ctad_maybe_unsupported, -1,
  /* DiagArray184 */ diag::warn_ctu_incompat_triple, -1,
  /* DiagArray185 */ diag::warn_attribute_argument_n_negative, diag::warn_cuda_attr_lambda_position, diag::warn_kern_is_inline, diag::warn_kern_is_method, diag::warn_pragma_unroll_cuda_value_in_parens, -1,
  /* DiagArray186 */ diag::warn_default_atomic_custom_getter_setter, -1,
  /* DiagArray187 */ diag::ext_cxx11_attr_placement, -1,
  /* DiagArray188 */ diag::warn_dangling_variable, diag::warn_unsupported_lifetime_extension, -1,
  /* DiagArray189 */ diag::warn_dangling_else, -1,
  /* DiagArray190 */ diag::warn_bind_ref_member_to_parameter, diag::warn_dangling_member, diag::warn_init_ptr_member_to_parameter_addr, diag::warn_new_dangling_reference, -1,
  /* DiagArray191 */ diag::warn_dangling_lifetime_pointer, diag::warn_dangling_lifetime_pointer_member, -1,
  /* DiagArray192 */ diag::warn_new_dangling_initializer_list, -1,
  /* DiagArray193 */ diag::warn_drv_darwin_sdk_invalid_settings, -1,
  /* DiagArray194 */ diag::warn_pp_date_time, -1,
  /* DiagArray195 */ diag::warn_dealloc_in_category, -1,
  /* DiagArray196 */ diag::warn_debug_compression_unavailable, -1,
  /* DiagArray197 */ diag::ext_mixed_decls_code, diag::warn_mixed_decls_code, -1,
  /* DiagArray198 */ diag::warn_defaulted_comparison_deleted, diag::warn_defaulted_method_deleted, -1,
  /* DiagArray199 */ diag::warn_delegating_ctor_cycle, -1,
  /* DiagArray200 */ diag::warn_delete_abstract_non_virtual_dtor, -1,
  /* DiagArray201 */ diag::ext_delete_void_ptr_operand, diag::warn_delete_incomplete, -1,
  /* DiagArray202 */ diag::warn_delete_non_virtual_dtor, -1,
  /* DiagArray204 */ diag::ext_delimited_escape_sequence, -1,
  /* DiagArray205 */ diag::warn_deprecated_lax_vec_conv_all, -1,
  /* DiagArray206 */ diag::warn_O4_is_O3, diag::warn_access_decl_deprecated, diag::warn_deprecated_redundant_constexpr_static_def, diag::warn_drv_deprecated_arg, diag::warn_drv_treating_input_as_cxx, diag::warn_fe_concepts_ts_flag, diag::warn_option_invalid_ocl_version, diag::warn_vector_long_decl_spec_combination, -1,
  /* DiagArray207 */ diag::warn_deprecated_altivec_src_compat, -1,
  /* DiagArray208 */ diag::warn_arith_conv_mixed_anon_enum_types_cxx20, -1,
  /* DiagArray209 */ diag::warn_depr_array_comparison, -1,
  /* DiagArray210 */ diag::warn_deprecated_noreturn_spelling, diag::warn_type_attribute_deprecated_on_decl, diag::warn_vector_mode_deprecated, -1,
  /* DiagArray211 */ diag::warn_deprecated_builtin, -1,
  /* DiagArray212 */ diag::warn_deprecated_comma_subscript, -1,
  /* DiagArray213 */ diag::warn_deprecated_copy, -1,
  /* DiagArray215 */ diag::warn_deprecated_copy_with_dtor, -1,
  /* DiagArray216 */ diag::warn_deprecated_copy_with_user_provided_copy, -1,
  /* DiagArray217 */ diag::warn_deprecated_copy_with_user_provided_dtor, -1,
  /* DiagArray218 */ diag::warn_deprecated_for_co_await, -1,
  /* DiagArray219 */ diag::warn_atl_uuid_deprecated, diag::warn_cstyle_param, diag::warn_deprecated, diag::warn_deprecated_fwdclass_message, diag::warn_deprecated_message, diag::warn_property_method_deprecated, -1,
  /* DiagArray220 */ diag::warn_exception_spec_deprecated, -1,
  /* DiagArray221 */ diag::warn_comparison_mixed_enum_types_cxx20, -1,
  /* DiagArray222 */ diag::warn_conditional_mixed_enum_types_cxx20, -1,
  /* DiagArray223 */ diag::warn_arith_conv_mixed_enum_types_cxx20, -1,
  /* DiagArray224 */ diag::warn_arith_conv_enum_float_cxx20, -1,
  /* DiagArray225 */ diag::warn_deprecated_coroutine_namespace, -1,
  /* DiagArray226 */ diag::warn_deprecated_def, diag::warn_unavailable_def, -1,
  /* DiagArray227 */ diag::warn_increment_bool, -1,
  /* DiagArray228 */ diag::warn_non_prototype_changes_behavior, diag::warn_strict_uses_without_prototype, -1,
  /* DiagArray229 */ diag::warn_objc_isa_assign, diag::warn_objc_isa_use, -1,
  /* DiagArray230 */ diag::warn_objc_pointer_masking, -1,
  /* DiagArray231 */ diag::warn_objc_pointer_masking_performSelector, -1,
  /* DiagArray232 */ diag::warn_pragma_deprecated_macro_use, -1,
  /* DiagArray233 */ diag::warn_deprecated_register, -1,
  /* DiagArray234 */ diag::warn_analyzer_deprecated_option, -1,
  /* DiagArray235 */ diag::warn_deprecated_this_capture, -1,
  /* DiagArray236 */ diag::warn_ext_int_deprecated, -1,
  /* DiagArray237 */ diag::warn_deprecated_compound_assign_volatile, diag::warn_deprecated_increment_decrement_volatile, diag::warn_deprecated_simple_assign_volatile, diag::warn_deprecated_volatile_param, diag::warn_deprecated_volatile_return, diag::warn_deprecated_volatile_structured_binding, -1,
  /* DiagArray239 */ diag::warn_direct_ivar_access, -1,
  /* DiagArray240 */ diag::pp_disabled_macro_expansion, -1,
  /* DiagArray243 */ diag::warn_conflicting_param_modifiers, diag::warn_conflicting_ret_type_modifiers, -1,
  /* DiagArray245 */ diag::warn_remainder_division_by_zero, -1,
  /* DiagArray246 */ diag::warn_attribute_dll_redeclaration, -1,
  /* DiagArray247 */ diag::warn_attribute_dllexport_explicit_instantiation_decl, -1,
  /* DiagArray248 */ diag::warn_attribute_dllimport_static_field_definition, -1,
  /* DiagArray249 */ diag::warn_doc_api_container_decl_mismatch, diag::warn_doc_block_command_duplicate, diag::warn_doc_block_command_empty_paragraph, diag::warn_doc_container_decl_mismatch, diag::warn_doc_function_method_decl_mismatch, diag::warn_doc_html_start_tag_expected_ident_or_greater, diag::warn_doc_html_start_tag_expected_quoted_string, diag::warn_doc_inline_command_not_enough_arguments, diag::warn_doc_param_duplicate, diag::warn_doc_param_invalid_direction, diag::warn_doc_param_not_attached_to_a_function_decl, diag::warn_doc_param_not_found, diag::warn_doc_returns_attached_to_a_void_function, diag::warn_doc_returns_not_attached_to_a_function_decl, diag::warn_doc_tparam_duplicate, diag::warn_doc_tparam_not_attached_to_a_template_decl, diag::warn_doc_tparam_not_found, diag::warn_not_a_doxygen_trailing_member_comment, diag::warn_verbatim_block_end_without_start, -1,
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
  /* DiagArray263 */ diag::warn_dyn_class_memaccess, -1,
  /* DiagArray264 */ diag::ext_dynamic_exception_spec, -1,
  /* DiagArray266 */ diag::ext_enum_base_in_type_specifier, -1,
  /* DiagArray267 */ diag::ext_elaborated_enum_class, -1,
  /* DiagArray268 */ diag::ext_embedded_directive, -1,
  /* DiagArray269 */ diag::warn_empty_for_body, diag::warn_empty_if_body, diag::warn_empty_range_based_for_body, diag::warn_empty_switch_body, diag::warn_empty_while_body, -1,
  /* DiagArray270 */ diag::ext_decomp_decl_empty, -1,
  /* DiagArray271 */ diag::warn_empty_init_statement, -1,
  /* DiagArray272 */ diag::ext_empty_translation_unit, -1,
  /* DiagArray273 */ diag::warn_incomplete_encoded_type, -1,
  /* DiagArray275 */ diag::warn_comparison_mixed_enum_types, -1,
  /* DiagArray276 */ diag::warn_conditional_mixed_enum_types, -1,
  /* DiagArray277 */ diag::warn_comparison_of_mixed_enum_types_switch, -1,
  /* DiagArray278 */ diag::warn_constexpr_unscoped_enum_out_of_range, -1,
  /* DiagArray279 */ diag::warn_impcast_different_enum_types, -1,
  /* DiagArray280 */ diag::warn_arith_conv_mixed_enum_types, -1,
  /* DiagArray281 */ diag::warn_arith_conv_enum_float, -1,
  /* DiagArray282 */ diag::ext_enum_too_large, diag::ext_enumerator_increment_too_large, -1,
  /* DiagArray283 */ diag::warn_cdtor_function_try_handler_mem_expr, diag::warn_exception_caught_by_earlier_handler, diag::warn_throw_in_noexcept_func, -1,
  /* DiagArray284 */ diag::ext_excess_initializers, diag::ext_excess_initializers_for_sizeless_type, diag::ext_excess_initializers_in_char_array_initializer, diag::ext_initializer_string_for_char_array_too_long, -1,
  /* DiagArray285 */ diag::warn_exit_time_destructor, -1,
  /* DiagArray286 */ diag::warn_defined_in_function_type_macro, diag::warn_defined_in_object_type_macro, -1,
  /* DiagArray287 */ diag::warn_direct_initialize_call, diag::warn_direct_super_initialize_call, -1,
  /* DiagArray288 */ diag::warn_arc_strong_pointer_objc_pointer, -1,
  /* DiagArray289 */ diag::ext_export_no_name_block, diag::ext_export_no_names, -1,
  /* DiagArray290 */ diag::ext_export_using_directive, -1,
  /* DiagArray291 */ diag::warn_zero_size_struct_union_in_extern_c, -1,
  /* DiagArray292 */ diag::warn_extern_init, -1,
  /* DiagArray293 */ diag::warn_arm_interrupt_calling_convention, -1,
  /* DiagArray294 */ diag::warn_namespace_member_extra_qualification, -1,
  /* DiagArray295 */ diag::ext_extra_semi, diag::warn_extra_semi_after_mem_fn_def, -1,
  /* DiagArray296 */ diag::warn_null_statement, -1,
  /* DiagArray297 */ diag::ext_pp_extra_tokens_at_eol, diag::warn_omp_extra_tokens_at_eol, -1,
  /* DiagArray298 */ diag::warn_final_dtor_non_final_class, -1,
  /* DiagArray299 */ diag::warn_pragma_final_macro, -1,
  /* DiagArray300 */ diag::ext_clang_c_enum_fixed_underlying_type, -1,
  /* DiagArray301 */ diag::warn_fixedpoint_constant_overflow, -1,
  /* DiagArray302 */ diag::warn_flag_enum_constant_out_of_range, -1,
  /* DiagArray303 */ diag::ext_flexible_array_in_array, diag::ext_flexible_array_in_struct, -1,
  /* DiagArray304 */ diag::warn_impcast_float_integer, -1,
  /* DiagArray305 */ diag::warn_floatingpoint_eq, -1,
  /* DiagArray306 */ diag::warn_impcast_float_to_integer, diag::warn_impcast_float_to_integer_out_of_range, -1,
  /* DiagArray307 */ diag::warn_impcast_float_to_integer_zero, -1,
  /* DiagArray308 */ diag::warn_redundant_loop_iteration, diag::warn_variables_not_in_loop_body, -1,
  /* DiagArray309 */ diag::warn_format_P_no_precision, diag::warn_format_argument_needs_cast, diag::warn_format_bool_as_character, diag::warn_format_conversion_argument_type_mismatch, diag::warn_format_invalid_annotation, diag::warn_format_invalid_positional_specifier, diag::warn_format_mix_positional_nonpositional_args, diag::warn_format_nonsensical_length, diag::warn_format_string_is_wide_literal, diag::warn_format_zero_positional_specifier, diag::warn_missing_format_string, diag::warn_printf_ObjCflags_without_ObjCConversion, diag::warn_printf_asterisk_missing_arg, diag::warn_printf_asterisk_wrong_type, diag::warn_printf_empty_objc_flag, diag::warn_printf_format_string_contains_null_char, diag::warn_printf_format_string_not_null_terminated, diag::warn_printf_ignored_flag, diag::warn_printf_incomplete_specifier, diag::warn_printf_invalid_objc_flag, diag::warn_printf_narg_not_supported, diag::warn_printf_nonsensical_flag, diag::warn_printf_nonsensical_optional_amount, diag::warn_printf_positional_arg_exceeds_data_args, diag::warn_scanf_nonzero_width, diag::warn_scanf_scanlist_incomplete, -1,
  /* DiagArray310 */ diag::warn_printf_data_arg_not_used, -1,
  /* DiagArray311 */ diag::warn_printf_insufficient_data_args, -1,
  /* DiagArray312 */ diag::warn_format_invalid_conversion, -1,
  /* DiagArray313 */ diag::warn_format_non_standard, diag::warn_format_non_standard_conversion_spec, diag::warn_format_non_standard_positional_arg, -1,
  /* DiagArray314 */ diag::warn_format_nonliteral, -1,
  /* DiagArray315 */ diag::warn_format_argument_needs_cast_pedantic, diag::warn_format_conversion_argument_type_mismatch_pedantic, -1,
  /* DiagArray316 */ diag::warn_format_nonliteral_noargs, -1,
  /* DiagArray317 */ diag::warn_format_conversion_argument_type_mismatch_confusion, -1,
  /* DiagArray319 */ diag::warn_empty_format_string, -1,
  /* DiagArray321 */ diag::warn_fortify_scanf_overflow, diag::warn_fortify_source_format_overflow, diag::warn_fortify_source_overflow, diag::warn_fortify_source_size_mismatch, diag::warn_fortify_strlen_overflow, -1,
  /* DiagArray322 */ diag::warn_four_char_character_literal, -1,
  /* DiagArray323 */ diag::warn_frame_address, -1,
  /* DiagArray324 */ diag::warn_fe_backend_frame_larger_than, diag::warn_fe_frame_larger_than, -1,
  /* DiagArray326 */ diag::warn_framework_include_private_from_public, -1,
  /* DiagArray327 */ diag::warn_free_nonheap_object, -1,
  /* DiagArray328 */ diag::warn_function_def_in_objc_container, -1,
  /* DiagArray329 */ diag::warn_dispatch_body_ignored, diag::warn_multiversion_duplicate_entries, diag::warn_target_clone_duplicate_options, -1,
  /* DiagArray330 */ diag::warn_drv_fuse_ld_path, -1,
  /* DiagArray333 */ diag::ext_clang_diagnose_if, diag::ext_clang_enable_if, diag::ext_warn_gnu_final, diag::warn_attribute_on_function_definition, diag::warn_break_binds_to_switch, diag::warn_cleanup_ext, diag::warn_gcc_attribute_location, diag::warn_gcc_ignores_type_attr, diag::warn_gcc_requires_variadic_function, diag::warn_gcc_variable_decl_in_for_loop, diag::warn_loop_ctrl_binds_to_inner, -1,
  /* DiagArray334 */ diag::warn_global_constructor, diag::warn_global_destructor, -1,
  /* DiagArray335 */ diag::warn_drv_global_isel_incomplete, diag::warn_drv_global_isel_incomplete_opt, -1,
  /* DiagArray337 */ diag::ext_alignof_expr, -1,
  /* DiagArray338 */ diag::ext_gnu_anonymous_struct, -1,
  /* DiagArray339 */ diag::ext_array_init_parens, -1,
  /* DiagArray340 */ diag::ext_auto_type, -1,
  /* DiagArray341 */ diag::ext_binary_literal, -1,
  /* DiagArray342 */ diag::ext_gnu_case_range, -1,
  /* DiagArray343 */ diag::ext_integer_complex, -1,
  /* DiagArray344 */ diag::ext_array_init_copy, -1,
  /* DiagArray345 */ diag::ext_gnu_conditional_expr, -1,
  /* DiagArray346 */ diag::ext_gnu_array_range, diag::ext_gnu_missing_equal_designator, diag::ext_gnu_old_style_field_designator, -1,
  /* DiagArray347 */ diag::ext_gnu_empty_initializer, -1,
  /* DiagArray348 */ diag::ext_empty_struct_union, diag::ext_flexible_array_empty_aggregate_gnu, diag::ext_no_named_members_in_struct_union, -1,
  /* DiagArray349 */ diag::ext_flexible_array_init, -1,
  /* DiagArray350 */ diag::ext_flexible_array_union_gnu, -1,
  /* DiagArray351 */ diag::ext_expr_not_ice, diag::ext_in_class_initializer_non_constant, diag::ext_vla_folded_to_constant, -1,
  /* DiagArray352 */ diag::ext_imaginary_constant, -1,
  /* DiagArray353 */ diag::ext_pp_include_next_directive, -1,
  /* DiagArray354 */ diag::warn_gnu_inline_cplusplus_without_extern, -1,
  /* DiagArray355 */ diag::ext_gnu_address_of_label, diag::ext_gnu_indirect_goto, -1,
  /* DiagArray356 */ diag::ext_pp_gnu_line_directive, -1,
  /* DiagArray357 */ diag::warn_gnu_null_ptr_arith, -1,
  /* DiagArray358 */ diag::ext_gnu_ptr_func_arith, diag::ext_gnu_subscript_void_type, diag::ext_gnu_void_ptr, -1,
  /* DiagArray359 */ diag::ext_forward_ref_enum_def, -1,
  /* DiagArray360 */ diag::ext_gnu_statement_expr, -1,
  /* DiagArray361 */ diag::ext_gnu_statement_expr_macro, -1,
  /* DiagArray362 */ diag::ext_in_class_initializer_float_type, -1,
  /* DiagArray363 */ diag::ext_string_literal_operator_template, -1,
  /* DiagArray364 */ diag::ext_typecheck_cast_to_union, -1,
  /* DiagArray365 */ diag::ext_variable_sized_type_in_struct, -1,
  /* DiagArray366 */ diag::ext_pp_line_zero, -1,
  /* DiagArray367 */ diag::ext_missing_varargs_arg, diag::ext_paste_comma, -1,
  /* DiagArray368 */ diag::warn_maybe_capture_bad_target_this_ptr, -1,
  /* DiagArray369 */ diag::warn_header_guard, -1,
  /* DiagArray370 */ diag::warn_using_directive_in_header, -1,
  /* DiagArray371 */ diag::warn_ignored_hip_only_option, -1,
  /* DiagArray372 */ diag::ext_hlsl_access_specifiers, -1,
  /* DiagArray373 */ diag::warn_condition_is_idiomatic_assignment, -1,
  /* DiagArray374 */ diag::ext_cannot_use_trivial_abi, diag::warn_alias_to_weak_alias, diag::warn_alias_with_section, diag::warn_aligned_attr_underaligned, diag::warn_attr_abi_tag_namespace, diag::warn_attribute_after_definition_ignored, diag::warn_attribute_cmse_entry_static, diag::warn_attribute_dllexport_explicit_instantiation_def, diag::warn_attribute_has_no_effect_on_compile_time_if, diag::warn_attribute_has_no_effect_on_infinite_loop, diag::warn_attribute_iboutlet, diag::warn_attribute_ignored, diag::warn_attribute_ignored_for_field_of_type, diag::warn_attribute_ignored_no_calls_in_stmt, diag::warn_attribute_ignored_on_inline, diag::warn_attribute_ignored_on_non_definition, diag::warn_attribute_invalid_on_definition, diag::warn_attribute_no_decl, diag::warn_attribute_nonnull_no_pointers, diag::warn_attribute_nonnull_parm_no_args, diag::warn_attribute_not_on_decl, diag::warn_attribute_pointer_or_reference_only, diag::warn_attribute_pointers_only, diag::warn_attribute_precede_definition, diag::warn_attribute_return_pointers_only, diag::warn_attribute_return_pointers_refs_only, diag::warn_attribute_sentinel_named_arguments, diag::warn_attribute_sentinel_not_variadic, diag::warn_attribute_type_not_supported, diag::warn_attribute_type_not_supported_global, diag::warn_attribute_unknown_visibility, diag::warn_attribute_void_function_method, diag::warn_attribute_weak_on_field, diag::warn_attribute_weak_on_local, diag::warn_attribute_wrong_decl_type, diag::warn_attribute_wrong_decl_type_str, diag::warn_attributes_likelihood_ifstmt_conflict, diag::warn_block_literal_attributes_on_omitted_return_type, diag::warn_cconv_unsupported, diag::warn_cxx11_gnu_attribute_on_type, diag::warn_declspec_allocator_nonpointer, diag::warn_declspec_attribute_ignored, diag::warn_deprecated_anonymous_namespace, diag::warn_deprecated_ignored_on_using, diag::warn_dllimport_dropped_from_inline_function, diag::warn_duplicate_attribute, diag::warn_duplicate_attribute_exact, diag::warn_function_attribute_ignored_in_stmt, diag::warn_function_stmt_attribute_precedence, diag::warn_gc_attribute_weak_on_local, diag::warn_gnu_inline_attribute_requires_inline, diag::warn_ignored_ms_inheritance, diag::warn_ignored_objc_externally_retained, diag::warn_import_on_definition, diag::warn_internal_linkage_local_storage, diag::warn_interrupt_attribute_invalid, diag::warn_microsoft_qualifiers_ignored, diag::warn_mig_server_routine_does_not_return_kern_return_t, diag::warn_mismatched_import, diag::warn_mmap_unknown_attribute, diag::warn_nocf_check_attribute_ignored, diag::warn_noderef_on_non_pointer_or_array, diag::warn_nothrow_attribute_ignored, diag::warn_ns_attribute_wrong_parameter_type, diag::warn_ns_attribute_wrong_return_type, diag::warn_objc_direct_ignored, diag::warn_objc_direct_property_ignored, diag::warn_opencl_attr_deprecated_ignored, diag::warn_require_const_init_added_too_late, diag::warn_riscv_repeated_interrupt_attribute, diag::warn_sycl_kernel_invalid_template_param_type, diag::warn_sycl_kernel_num_of_function_params, diag::warn_sycl_kernel_num_of_template_params, diag::warn_sycl_kernel_return_type, diag::warn_transparent_union_attribute_field_size_align, diag::warn_transparent_union_attribute_floating, diag::warn_transparent_union_attribute_not_definition, diag::warn_transparent_union_attribute_zero_fields, diag::warn_type_attribute_wrong_type, diag::warn_unhandled_ms_attribute_ignored, diag::warn_unsupported_target_attribute, diag::warn_unused_result_typedef_unsupported_spelling, diag::warn_wrong_clang_attr_namespace, -1,
  /* DiagArray375 */ diag::warn_missing_sdksettings_for_availability_checking, -1,
  /* DiagArray376 */ diag::warn_drv_unsupported_opt_for_target, diag::warn_ignored_gcc_optimization, -1,
  /* DiagArray377 */ diag::warn_pragma_intrinsic_builtin, -1,
  /* DiagArray379 */ diag::warn_pragma_align_expected_equal, diag::warn_pragma_align_invalid_option, diag::warn_pragma_comment_ignored, diag::warn_pragma_debug_missing_argument, diag::warn_pragma_debug_unexpected_command, diag::warn_pragma_debug_unknown_module, diag::warn_pragma_expected_action_or_r_paren, diag::warn_pragma_expected_colon, diag::warn_pragma_expected_colon_r_paren, diag::warn_pragma_expected_comma, diag::warn_pragma_expected_identifier, diag::warn_pragma_expected_init_seg, diag::warn_pragma_expected_integer, diag::warn_pragma_expected_lparen, diag::warn_pragma_expected_non_wide_string, diag::warn_pragma_expected_predicate, diag::warn_pragma_expected_punc, diag::warn_pragma_expected_rparen, diag::warn_pragma_expected_section_label_or_name, diag::warn_pragma_expected_section_name, diag::warn_pragma_expected_section_push_pop_or_name, diag::warn_pragma_expected_string, diag::warn_pragma_extra_tokens_at_eol, diag::warn_pragma_force_cuda_host_device_bad_arg, diag::warn_pragma_fp_ignored, diag::warn_pragma_init_seg_unsupported_target, diag::warn_pragma_invalid_action, diag::warn_pragma_invalid_argument, diag::warn_pragma_invalid_specific_action, diag::warn_pragma_missing_argument, diag::warn_pragma_ms_fenv_access, diag::warn_pragma_ms_struct, diag::warn_pragma_options_align_reset_failed, diag::warn_pragma_options_expected_align, diag::warn_pragma_pack_invalid_alignment, diag::warn_pragma_pack_malformed, diag::warn_pragma_pop_failed, diag::warn_pragma_pop_macro_no_push, diag::warn_pragma_unknown_extension, diag::warn_pragma_unsupported_action, diag::warn_pragma_unsupported_extension, diag::warn_pragma_unused_expected_var, diag::warn_pragma_unused_expected_var_arg, diag::warn_pragma_unused_undeclared_var, diag::warn_stdc_unknown_rounding_mode, -1,
  /* DiagArray380 */ diag::warn_arc_lifetime_result_type, diag::warn_block_literal_qualifiers_on_omitted_return_type, diag::warn_qual_return_type, diag::warn_typecheck_function_qualifiers_ignored, -1,
  /* DiagArray381 */ diag::warn_typecheck_reference_qualifiers, -1,
  /* DiagArray383 */ diag::warn_auto_implicit_atomic_property, diag::warn_implicit_atomic_property, -1,
  /* DiagArray384 */ diag::warn_impcast_integer_float_precision_constant, -1,
  /* DiagArray385 */ diag::warn_impcast_floating_point_to_bool, -1,
  /* DiagArray386 */ diag::ext_implicit_exception_spec_mismatch, -1,
  /* DiagArray387 */ diag::warn_unannotated_fallthrough, -1,
  /* DiagArray388 */ diag::warn_unannotated_fallthrough_per_function, -1,
  /* DiagArray389 */ diag::warn_impcast_fixed_point_range, -1,
  /* DiagArray390 */ diag::warn_impcast_float_precision, diag::warn_impcast_float_result_precision, -1,
  /* DiagArray391 */ diag::ext_implicit_function_decl_c99, diag::ext_implicit_lib_function_decl, diag::ext_implicit_lib_function_decl_c99, diag::warn_builtin_unknown, diag::warn_implicit_function_decl, -1,
  /* DiagArray392 */ diag::ext_missing_type_specifier, diag::ext_param_not_declared, diag::warn_missing_type_specifier, -1,
  /* DiagArray393 */ diag::warn_impcast_high_order_zero_bits, diag::warn_impcast_integer_precision, -1,
  /* DiagArray394 */ diag::warn_impcast_integer_float_precision, -1,
  /* DiagArray395 */ diag::warn_implicitly_retains_self, -1,
  /* DiagArray396 */ diag::ext_integer_literal_too_large_for_signed, -1,
  /* DiagArray398 */ diag::ext_pp_import_directive, -1,
  /* DiagArray399 */ diag::warn_inaccessible_base_class, -1,
  /* DiagArray400 */ diag::pp_include_next_absolute_path, -1,
  /* DiagArray401 */ diag::pp_include_next_in_primary, -1,
  /* DiagArray402 */ diag::warn_deep_exception_specs_differ, diag::warn_incompatible_exception_specs, -1,
  /* DiagArray403 */ diag::ext_typecheck_convert_incompatible_function_pointer, -1,
  /* DiagArray404 */ diag::warn_redecl_library_builtin, -1,
  /* DiagArray405 */ diag::warn_cxx_ms_struct, diag::warn_npot_ms_struct, -1,
  /* DiagArray406 */ diag::ext_typecheck_convert_incompatible_pointer, -1,
  /* DiagArray407 */ diag::ext_nested_pointer_qualifier_mismatch, diag::ext_typecheck_convert_discards_qualifiers, diag::warn_bad_cxx_cast_nested_pointer_addr_space, -1,
  /* DiagArray408 */ diag::warn_property_types_are_incompatible, -1,
  /* DiagArray409 */ diag::warn_incompatible_sysroot, -1,
  /* DiagArray410 */ diag::warn_mmap_incomplete_framework_module_declaration, -1,
  /* DiagArray411 */ diag::warn_undef_method_impl, -1,
  /* DiagArray413 */ diag::warn_implicit_decl_no_jmp_buf, -1,
  /* DiagArray414 */ diag::warn_missing_submodule, diag::warn_mmap_umbrella_dir_not_found, diag::warn_uncovered_module_header, -1,
  /* DiagArray415 */ diag::warn_redeclaration_without_attribute_prev_attribute_ignored, diag::warn_redeclaration_without_import_attribute, -1,
  /* DiagArray416 */ diag::warn_inconsistent_destructor_marked_not_override_overriding, -1,
  /* DiagArray417 */ diag::warn_inconsistent_function_marked_not_override_overriding, -1,
  /* DiagArray418 */ diag::ext_increment_bool, -1,
  /* DiagArray419 */ diag::warn_infinite_recursive_function, -1,
  /* DiagArray421 */ diag::ext_initializer_overrides, diag::warn_initializer_overrides, -1,
  /* DiagArray422 */ diag::ext_out_of_line_qualified_id_type_names_constructor, -1,
  /* DiagArray424 */ diag::warn_fe_inline_asm, -1,
  /* DiagArray425 */ diag::warn_inline_namespace_reopened_noninline, -1,
  /* DiagArray426 */ diag::ext_operator_new_delete_declared_inline, -1,
  /* DiagArray427 */ diag::warn_explicit_instantiation_after_specialization, -1,
  /* DiagArray428 */ diag::ext_typecheck_convert_int_pointer, diag::ext_typecheck_convert_pointer_int, -1,
  /* DiagArray430 */ diag::warn_enum_constant_in_bool_context, diag::warn_left_shift_in_bool_context, -1,
  /* DiagArray431 */ diag::warn_int_to_pointer_cast, -1,
  /* DiagArray432 */ diag::warn_int_to_void_pointer_cast, -1,
  /* DiagArray433 */ diag::warn_integer_constant_overflow, -1,
  /* DiagArray434 */ diag::warn_anyx86_interrupt_regsave, -1,
  /* DiagArray435 */ diag::warn_drv_missing_plugin_arg, diag::warn_drv_missing_plugin_name, diag::warn_drv_msp430_hwmult_mismatch, diag::warn_drv_msp430_hwmult_no_device, diag::warn_drv_msp430_hwmult_unsupported, diag::warn_drv_object_size_disabled_O0, diag::warn_drv_optimization_value, diag::warn_fe_backend_invalid_feature_flag, diag::warn_target_unsupported_extension, -1,
  /* DiagArray436 */ diag::ext_constexpr_function_never_constant_expr, -1,
  /* DiagArray437 */ diag::warn_iboutlet_object_type, diag::warn_iboutletcollection_property_assign, -1,
  /* DiagArray438 */ diag::warn_invalid_initializer_from_system_header, -1,
  /* DiagArray439 */ diag::warn_invalid_ios_deployment_target, -1,
  /* DiagArray440 */ diag::warn_attribute_no_builtin_invalid_builtin_name, -1,
  /* DiagArray441 */ diag::warn_falloff_noreturn_function, diag::warn_noreturn_function_has_return_expr, -1,
  /* DiagArray442 */ diag::ext_offsetof_non_pod_type, diag::ext_offsetof_non_standardlayout_type, -1,
  /* DiagArray443 */ diag::warn_drv_ps_sdk_dir, diag::warn_drv_unable_to_find_directory_expected, -1,
  /* DiagArray444 */ diag::ext_partial_spec_not_more_specialized_than_primary, -1,
  /* DiagArray446 */ diag::ext_empty_character, diag::ext_unterminated_char_or_string, -1,
  /* DiagArray447 */ diag::warn_bad_character_encoding, diag::warn_bad_string_encoding, -1,
  /* DiagArray448 */ diag::ext_pp_bad_paste_ms, -1,
  /* DiagArray449 */ diag::warn_invalid_utf8_in_comment, -1,
  /* DiagArray450 */ diag::warn_jump_out_of_seh_finally, -1,
  /* DiagArray451 */ diag::ext_keyword_as_ident, -1,
  /* DiagArray452 */ diag::warn_pp_macro_hides_keyword, -1,
  /* DiagArray453 */ diag::ext_param_promoted_not_compatible_with_prototype, -1,
  /* DiagArray454 */ diag::ext_token_used, -1,
  /* DiagArray455 */ diag::warn_parameter_size, diag::warn_return_value_size, -1,
  /* DiagArray457 */ diag::warn_fe_linking_module, -1,
  /* DiagArray458 */ diag::warn_impcast_literal_float_to_integer, diag::warn_impcast_literal_float_to_integer_out_of_range, -1,
  /* DiagArray459 */ diag::warn_float_compare_literal, diag::warn_float_overflow, diag::warn_float_underflow, -1,
  /* DiagArray460 */ diag::ext_template_arg_local_type, -1,
  /* DiagArray461 */ diag::warn_logical_not_on_lhs_of_check, -1,
  /* DiagArray462 */ diag::warn_logical_and_in_logical_or, -1,
  /* DiagArray463 */ diag::ext_c99_longlong, -1,
  /* DiagArray465 */ diag::ext_pp_macro_redef, -1,
  /* DiagArray466 */ diag::ext_main_used, diag::ext_noreturn_main, diag::ext_variadic_main, diag::warn_main_one_arg, diag::warn_main_redefined, diag::warn_main_returns_bool_literal, diag::warn_static_main, -1,
  /* DiagArray467 */ diag::ext_main_returns_nonint, -1,
  /* DiagArray468 */ diag::warn_has_warning_invalid_option, -1,
  /* DiagArray469 */ diag::ext_many_braces_around_init, -1,
  /* DiagArray470 */ diag::warn_max_tokens, diag::warn_max_tokens_total, -1,
  /* DiagArray471 */ diag::warn_max_unsigned_zero, -1,
  /* DiagArray472 */ diag::warn_suspicious_sizeof_memset, -1,
  /* DiagArray473 */ diag::warn_memsize_comparison, -1,
  /* DiagArray474 */ diag::warn_non_contravariant_param_types, diag::warn_non_covariant_ret_types, -1,
  /* DiagArray476 */ diag::ext_ms_abstract_keyword, -1,
  /* DiagArray477 */ diag::ext_anonymous_record_with_type, diag::ext_ms_anonymous_record, -1,
  /* DiagArray478 */ diag::ext_ms_cast_fn_obj, diag::ext_ms_impcast_fn_obj, -1,
  /* DiagArray479 */ diag::ext_charize_microsoft, -1,
  /* DiagArray480 */ diag::ext_comment_paste_microsoft, -1,
  /* DiagArray481 */ diag::ext_default_init_const, -1,
  /* DiagArray482 */ diag::ext_pp_operator_used_as_macro_name, -1,
  /* DiagArray483 */ diag::ext_param_default_argument_redefinition, -1,
  /* DiagArray484 */ diag::warn_attribute_section_drectve, -1,
  /* DiagArray485 */ diag::ext_ctrl_z_eof_microsoft, -1,
  /* DiagArray486 */ diag::ext_ms_forward_ref_enum, -1,
  /* DiagArray487 */ diag::ext_enumerator_too_large, -1,
  /* DiagArray488 */ diag::ext_ellipsis_exception_spec, diag::ext_incomplete_in_exception_spec, diag::ext_mismatched_exception_spec, diag::ext_mismatched_exception_spec_explicit_instantiation, diag::ext_override_exception_spec, -1,
  /* DiagArray489 */ diag::warn_microsoft_dependent_exists, -1,
  /* DiagArray490 */ diag::ext_ms_explicit_constructor_call, -1,
  /* DiagArray491 */ diag::warn_member_extra_qualification, -1,
  /* DiagArray492 */ diag::ext_ms_c_enum_fixed_underlying_type, -1,
  /* DiagArray493 */ diag::ext_flexible_array_empty_aggregate_ms, diag::ext_flexible_array_union_ms, -1,
  /* DiagArray494 */ diag::ext_goto_into_protected_scope, -1,
  /* DiagArray495 */ diag::ext_ms_ambiguous_direct_base, -1,
  /* DiagArray496 */ diag::ext_pp_include_search_ms, -1,
  /* DiagArray497 */ diag::ext_mutable_reference, -1,
  /* DiagArray498 */ diag::ext_pure_function_definition, -1,
  /* DiagArray499 */ diag::ext_static_non_static, -1,
  /* DiagArray500 */ diag::ext_ms_sealed_keyword, -1,
  /* DiagArray501 */ diag::ext_ms_static_assert, -1,
  /* DiagArray502 */ diag::ext_explicit_instantiation_duplicate, diag::ext_found_in_dependent_base, diag::ext_found_later_in_class, diag::ext_ms_delayed_template_argument, diag::ext_ms_deref_template_argument, diag::ext_ms_template_spec_redecl_out_of_scope, diag::ext_ms_template_type_arg_missing_typename, diag::ext_static_out_of_line, diag::ext_undeclared_unqual_id_with_dependent_base, diag::ext_unqualified_base_class, -1,
  /* DiagArray503 */ diag::ext_template_param_shadow, -1,
  /* DiagArray504 */ diag::ext_union_member_of_reference_type, -1,
  /* DiagArray505 */ diag::ext_friend_tag_redecl_outside_namespace, -1,
  /* DiagArray506 */ diag::ext_ms_using_declaration_inaccessible, -1,
  /* DiagArray507 */ diag::ext_pseudo_dtor_on_void, -1,
  /* DiagArray508 */ diag::warn_profile_data_misexpect, -1,
  /* DiagArray509 */ diag::warn_misleading_indentation, -1,
  /* DiagArray510 */ diag::warn_mismatched_delete_new, -1,
  /* DiagArray511 */ diag::warn_conflicting_param_types, -1,
  /* DiagArray512 */ diag::warn_conflicting_ret_types, -1,
  /* DiagArray513 */ diag::warn_struct_class_previous_tag_mismatch, diag::warn_struct_class_tag_mismatch, -1,
  /* DiagArray514 */ diag::warn_missing_braces, -1,
  /* DiagArray515 */ diag::ext_constinit_missing, -1,
  /* DiagArray516 */ diag::ext_no_declarators, diag::ext_standalone_specifier, diag::ext_typedef_without_a_name, diag::warn_standalone_specifier, -1,
  /* DiagArray517 */ diag::ext_missing_exception_specification, -1,
  /* DiagArray518 */ diag::warn_missing_field_initializers, -1,
  /* DiagArray521 */ diag::warn_missing_method_return_type, -1,
  /* DiagArray522 */ diag::warn_overriding_method_missing_noescape, -1,
  /* DiagArray523 */ diag::warn_suggest_noreturn_block, diag::warn_suggest_noreturn_function, -1,
  /* DiagArray524 */ diag::warn_cconv_knr, -1,
  /* DiagArray525 */ diag::warn_missing_prototype, -1,
  /* DiagArray526 */ diag::warn_missing_selector_name, -1,
  /* DiagArray527 */ diag::warn_missing_sysroot, -1,
  /* DiagArray528 */ diag::warn_missing_variable_declarations, -1,
  /* DiagArray529 */ diag::warn_assume_attribute_string_unknown_suggested, -1,
  /* DiagArray530 */ diag::remark_module_build, diag::remark_module_build_done, diag::remark_module_lock_failure, diag::remark_module_lock_timeout, -1,
  /* DiagArray531 */ diag::warn_module_conflict, diag::warn_module_system_bit_conflict, -1,
  /* DiagArray532 */ diag::warn_module_config_mismatch, -1,
  /* DiagArray533 */ diag::warn_duplicate_module_file_extension, -1,
  /* DiagArray534 */ diag::remark_module_import, -1,
  /* DiagArray535 */ diag::ext_module_import_in_extern_c, -1,
  /* DiagArray536 */ diag::remark_pp_include_directive_modular_translation, -1,
  /* DiagArray537 */ diag::remark_module_lock, -1,
  /* DiagArray538 */ diag::ext_equivalent_internal_linkage_decl_in_modules, -1,
  /* DiagArray539 */ diag::ext_module_import_not_at_top_level_noop, -1,
  /* DiagArray543 */ diag::warn_drv_msvc_not_found, -1,
  /* DiagArray544 */ diag::warn_multichar_character_literal, -1,
  /* DiagArray545 */ diag::warn_vbase_moved_multiple_times, -1,
  /* DiagArray547 */ diag::ext_anonymous_record_with_anonymous_type, -1,
  /* DiagArray549 */ diag::warn_operator_new_returns_null, -1,
  /* DiagArray550 */ diag::ext_no_newline_eof, diag::warn_no_newline_eof, -1,
  /* DiagArray551 */ diag::warn_dereference_of_noderef_type, diag::warn_dereference_of_noderef_type_no_decl, diag::warn_noderef_to_dereferenceable_pointer, -1,
  /* DiagArray553 */ diag::ext_non_c_like_anon_struct_in_typedef, -1,
  /* DiagArray555 */ diag::warn_non_literal_null_pointer, -1,
  /* DiagArray556 */ diag::warn_non_modular_include_in_framework_module, -1,
  /* DiagArray557 */ diag::warn_non_modular_include_in_module, -1,
  /* DiagArray558 */ diag::warn_cannot_pass_non_pod_arg_to_vararg, diag::warn_non_pod_vararg_with_format_string, diag::warn_second_parameter_to_va_arg_not_pod, diag::warn_second_parameter_to_va_arg_ownership_qualified, -1,
  /* DiagArray559 */ diag::warn_alignment_not_power_of_two, -1,
  /* DiagArray560 */ diag::warn_non_virtual_dtor, -1,
  /* DiagArray561 */ diag::warn_null_arg, diag::warn_null_ret, -1,
  /* DiagArray563 */ diag::pp_nonportable_path, -1,
  /* DiagArray564 */ diag::pp_nonportable_system_path, -1,
  /* DiagArray565 */ diag::warn_neon_vector_initializer_non_portable, -1,
  /* DiagArray566 */ diag::warn_cstruct_memaccess, -1,
  /* DiagArray567 */ diag::warn_nsconsumed_attribute_mismatch, -1,
  /* DiagArray568 */ diag::warn_nsreturns_retained_attribute_mismatch, -1,
  /* DiagArray569 */ diag::warn_null_in_arithmetic_operation, diag::warn_null_in_comparison_operation, -1,
  /* DiagArray570 */ diag::null_in_char_or_string, diag::null_in_file, -1,
  /* DiagArray571 */ diag::warn_impcast_null_pointer_to_integer, -1,
  /* DiagArray572 */ diag::warn_binding_null_to_reference, diag::warn_indirection_through_null, -1,
  /* DiagArray573 */ diag::warn_pointer_arith_null_ptr, -1,
  /* DiagArray574 */ diag::warn_pointer_sub_null_ptr, -1,
  /* DiagArray575 */ diag::warn_conflicting_nullability_attr_overriding_param_types, diag::warn_conflicting_nullability_attr_overriding_ret_types, diag::warn_mismatched_nullability_attr, diag::warn_null_resettable_setter, diag::warn_nullability_duplicate, -1,
  /* DiagArray576 */ diag::warn_nullability_missing, -1,
  /* DiagArray577 */ diag::warn_nullability_missing_array, -1,
  /* DiagArray578 */ diag::warn_nullability_declspec, -1,
  /* DiagArray579 */ diag::ext_nullability, -1,
  /* DiagArray580 */ diag::warn_nullability_inferred_on_nested_type, -1,
  /* DiagArray581 */ diag::warn_nullability_lost, -1,
  /* DiagArray582 */ diag::warn_autosynthesis_property_ivar_match, -1,
  /* DiagArray583 */ diag::warn_impcast_constant_value_to_objc_bool, -1,
  /* DiagArray584 */ diag::warn_objc_boxing_invalid_utf8_string, -1,
  /* DiagArray585 */ diag::warn_objc_circular_container, -1,
  /* DiagArray587 */ diag::warn_objc_designated_init_missing_super_call, diag::warn_objc_designated_init_non_designated_init_call, diag::warn_objc_designated_init_non_super_designated_init_call, diag::warn_objc_implementation_missing_designated_init_override, diag::warn_objc_secondary_init_missing_init_call, diag::warn_objc_secondary_init_super_init_call, -1,
  /* DiagArray588 */ diag::warn_nsdictionary_duplicate_key, -1,
  /* DiagArray589 */ diag::warn_superclass_variable_sized_type_not_at_end, diag::warn_variable_sized_ivar_visibility, -1,
  /* DiagArray590 */ diag::warn_forward_class_redefinition, -1,
  /* DiagArray591 */ diag::warn_ivars_in_interface, -1,
  /* DiagArray592 */ diag::warn_objc_literal_comparison, -1,
  /* DiagArray593 */ diag::warn_impcast_objective_c_literal_to_bool, diag::warn_objc_collection_literal_element, -1,
  /* DiagArray594 */ diag::warn_pp_objc_macro_redef_ignored, -1,
  /* DiagArray595 */ diag::warn_messaging_unqualified_id, -1,
  /* DiagArray596 */ diag::warn_class_method_not_found, diag::warn_class_method_not_found_with_typo, diag::warn_inst_method_not_found, diag::warn_instance_method_not_found_with_typo, diag::warn_instance_method_on_class_found, diag::warn_root_inst_method_not_found, -1,
  /* DiagArray597 */ diag::warn_missing_explicit_synthesis, -1,
  /* DiagArray598 */ diag::warn_objc_missing_super_call, -1,
  /* DiagArray599 */ diag::warn_multiple_method_decl, -1,
  /* DiagArray600 */ diag::warn_objc_property_retain_of_block, -1,
  /* DiagArray601 */ diag::warn_objc_pointer_cxx_catch_fragile, -1,
  /* DiagArray602 */ diag::warn_objc_property_assign_on_object, -1,
  /* DiagArray603 */ diag::warn_impl_required_for_class_property, diag::warn_impl_required_in_category_for_class_property, diag::warn_setter_getter_impl_required, diag::warn_setter_getter_impl_required_in_category, -1,
  /* DiagArray604 */ diag::warn_property_implicitly_mismatched, -1,
  /* DiagArray605 */ diag::warn_cocoa_naming_owned_rule, -1,
  /* DiagArray606 */ diag::warn_objc_property_default_assign_on_object, diag::warn_objc_property_no_assignment_attribute, -1,
  /* DiagArray607 */ diag::warn_autosynthesis_property_in_superclass, diag::warn_no_autosynthesis_property, diag::warn_no_autosynthesis_shared_ivar_property, -1,
  /* DiagArray608 */ diag::warn_category_method_impl_match, -1,
  /* DiagArray609 */ diag::warn_auto_synthesizing_protocol_property, -1,
  /* DiagArray610 */ diag::warn_objc_redundant_qualified_class_type, -1,
  /* DiagArray611 */ diag::warn_objc_readonly_property_has_setter, -1,
  /* DiagArray613 */ diag::warn_objc_redundant_literal_use, -1,
  /* DiagArray614 */ diag::warn_objc_root_class_missing, -1,
  /* DiagArray616 */ diag::warn_impcast_float_to_objc_signed_char_bool, -1,
  /* DiagArray617 */ diag::warn_impcast_int_to_objc_signed_char_bool, -1,
  /* DiagArray618 */ diag::warn_objc_string_literal_comparison, -1,
  /* DiagArray619 */ diag::warn_concatenated_nsarray_literal, -1,
  /* DiagArray620 */ diag::warn_objc_unsafe_perform_selector, -1,
  /* DiagArray621 */ diag::warn_odr_different_num_template_parameters, diag::warn_odr_different_template_parameter_kind, diag::warn_odr_field_type_inconsistent, diag::warn_odr_function_type_inconsistent, diag::warn_odr_ivar_type_inconsistent, diag::warn_odr_non_type_parameter_type_inconsistent, diag::warn_odr_objc_method_num_params_inconsistent, diag::warn_odr_objc_method_param_type_inconsistent, diag::warn_odr_objc_method_result_type_inconsistent, diag::warn_odr_objc_method_variadic_inconsistent, diag::warn_odr_objc_property_impl_kind_inconsistent, diag::warn_odr_objc_property_type_inconsistent, diag::warn_odr_objc_superclass_inconsistent, diag::warn_odr_objc_synthesize_ivar_inconsistent, diag::warn_odr_parameter_pack_non_pack, diag::warn_odr_tag_type_inconsistent, diag::warn_odr_variable_multiple_def, diag::warn_odr_variable_type_inconsistent, -1,
  /* DiagArray622 */ diag::warn_old_style_cast, -1,
  /* DiagArray624 */ diag::ext_opencl_ext_vector_type_rgba_selector, -1,
  /* DiagArray626 */ diag::ext_omp_attributes, -1,
  /* DiagArray627 */ diag::warn_omp_alignment_not_power_of_two, diag::warn_omp_allocate_thread_on_task_target_directive, diag::warn_omp_ctx_incompatible_property_for_selector, diag::warn_omp_ctx_incompatible_score_for_property, diag::warn_omp_ctx_incompatible_selector_for_set, diag::warn_omp_ctx_selector_without_properties, diag::warn_omp_declare_variant_ctx_mutiple_use, diag::warn_omp_declare_variant_ctx_not_a_property, diag::warn_omp_declare_variant_ctx_not_a_selector, diag::warn_omp_declare_variant_ctx_not_a_set, diag::warn_omp_declare_variant_expected, diag::warn_omp_declare_variant_string_literal_or_identifier, diag::warn_omp_linear_step_zero, diag::warn_omp_more_one_device_type_clause, diag::warn_omp_more_one_interop_type, diag::warn_omp_more_one_omp_all_memory, diag::warn_omp_unknown_assumption_clause_missing_id, diag::warn_omp_unknown_assumption_clause_without_args, diag::warn_omp_used_different_allocator, -1,
  /* DiagArray628 */ diag::ext_omp_loop_not_canonical_init, diag::warn_omp_loop_64_bit_var, -1,
  /* DiagArray629 */ diag::warn_omp_non_trivial_type_mapped, -1,
  /* DiagArray630 */ diag::warn_drv_omp_offload_target_duplicate, diag::warn_omp_declare_target_after_first_use, diag::warn_omp_not_in_target_context, -1,
  /* DiagArray631 */ diag::warn_drv_fine_grained_bitfield_accesses_ignored, diag::warn_drv_fjmc_for_elf_only, diag::warn_drv_jmc_requires_debuginfo, diag::warn_drv_moutline_atomics_unsupported_opt, diag::warn_drv_moutline_unsupported_opt, diag::warn_drv_needs_hvx, diag::warn_drv_ps_force_pic, diag::warn_drv_unsupported_longcalls, diag::warn_drv_unsupported_option_for_offload_arch_req_feature, diag::warn_drv_unsupported_option_for_target, diag::warn_drv_unsupported_pic_with_mabicalls, diag::warn_drv_unsupported_sdata, -1,
  /* DiagArray632 */ diag::ext_typecheck_ordered_comparison_of_function_pointers, diag::warn_typecheck_ordered_comparison_of_function_pointers, -1,
  /* DiagArray633 */ diag::ext_out_of_line_declaration, -1,
  /* DiagArray634 */ diag::ext_use_out_of_scope_declaration, -1,
  /* DiagArray635 */ diag::warn_overaligned_type, -1,
  /* DiagArray637 */ diag::ext_string_too_long, -1,
  /* DiagArray638 */ diag::warn_overloaded_shift_in_comparison, -1,
  /* DiagArray639 */ diag::warn_overloaded_virtual, -1,
  /* DiagArray641 */ diag::warn_fe_override_module, -1,
  /* DiagArray642 */ diag::warn_conflicting_overriding_param_modifiers, diag::warn_conflicting_overriding_param_types, diag::warn_conflicting_overriding_ret_type_modifiers, diag::warn_conflicting_overriding_ret_types, diag::warn_conflicting_overriding_variadic, diag::warn_non_contravariant_overriding_param_types, diag::warn_non_covariant_overriding_ret_types, -1,
  /* DiagArray643 */ diag::warn_drv_overriding_flag_option, -1,
  /* DiagArray644 */ diag::warn_unnecessary_packed, -1,
  /* DiagArray645 */ diag::warn_padded_struct_anon_field, diag::warn_padded_struct_field, diag::warn_padded_struct_size, -1,
  /* DiagArray646 */ diag::warn_condition_is_assignment, diag::warn_precedence_bitwise_rel, diag::warn_precedence_conditional, -1,
  /* DiagArray647 */ diag::warn_equality_with_extra_parens, -1,
  /* DiagArray649 */ diag::remark_fe_backend_optimization_remark, -1,
  /* DiagArray650 */ diag::remark_fe_backend_optimization_remark_analysis, diag::remark_fe_backend_optimization_remark_analysis_aliasing, diag::remark_fe_backend_optimization_remark_analysis_fpcommute, -1,
  /* DiagArray651 */ diag::warn_fe_backend_optimization_failure, -1,
  /* DiagArray652 */ diag::remark_fe_backend_optimization_remark_missed, -1,
  /* DiagArray653 */ diag::warn_module_uses_date_time, -1,
  /* DiagArray654 */ diag::ext_aggregate_init_not_constant, diag::ext_anonymous_record_with_type, diag::ext_anonymous_struct_union_qualified, diag::ext_array_size_conversion, diag::ext_auto_new_list_init, diag::ext_c99_array_usage, diag::ext_c99_compound_literal, diag::ext_c99_feature, diag::ext_c99_flexible_array_member, diag::ext_c99_variable_decl_in_for_loop, diag::ext_cast_fn_obj, diag::ext_clang_diagnose_if, diag::ext_clang_enable_if, diag::ext_cxx11_enum_fixed_underlying_type, diag::ext_designated_init, diag::ext_dtor_name_ambiguous, diag::ext_dtor_named_in_wrong_scope, diag::ext_duplicate_declspec, diag::ext_ellipsis_exception_spec, diag::ext_empty_fnmacro_arg, diag::ext_enum_value_not_int, diag::ext_enumerator_list_comma_c, diag::ext_enumerator_list_comma_cxx, diag::ext_explicit_instantiation_without_qualified_id, diag::ext_expr_not_ice, diag::ext_extern_template, diag::ext_extra_semi, diag::ext_forward_ref_enum, diag::ext_freestanding_complex, diag::ext_gnu_array_range, diag::ext_hex_constant_invalid, diag::ext_hex_literal_invalid, diag::ext_ident_list_in_param, diag::ext_in_class_initializer_non_constant, diag::ext_integer_complement_complex, diag::ext_integer_increment_complex, diag::ext_internal_in_extern_inline_quiet, diag::ext_line_comment, diag::ext_main_used, diag::ext_mixed_decls_code, diag::ext_multi_line_line_comment, diag::ext_named_variadic_macro, diag::ext_no_newline_eof, diag::ext_nonstandard_escape, diag::ext_ns_enum_attribute, diag::ext_opencl_double_without_pragma, diag::ext_operator_overload_static, diag::ext_pointer_to_const_ref_member_on_rvalue, diag::ext_pp_bad_vaargs_use, diag::ext_pp_comma_expr, diag::ext_pp_ident_directive, diag::ext_pp_line_too_big, diag::ext_pp_opencl_variadic_macros, diag::ext_pp_warning_directive, diag::ext_return_has_void_expr, diag::ext_rvalue_to_reference_access_ctor, diag::ext_rvalue_to_reference_temp_copy_no_viable, diag::ext_sizeof_alignof_function_type, diag::ext_sizeof_alignof_void_type, diag::ext_subscript_non_lvalue, diag::ext_subscript_overload, diag::ext_thread_before, diag::ext_typecheck_addrof_void, diag::ext_typecheck_cast_nonscalar, diag::ext_typecheck_comparison_of_fptr_to_void, diag::ext_typecheck_cond_one_void, diag::ext_typecheck_convert_pointer_void_func, diag::ext_typecheck_ordered_comparison_of_pointer_and_zero, diag::ext_variadic_macro, diag::warn_cxx20_compat_static_lambda, diag::warn_defined_in_function_type_macro, diag::warn_format_conversion_argument_type_mismatch_pedantic, diag::warn_kern_is_method, diag::warn_strict_prototypes, -1,
  /* DiagArray655 */ diag::warn_opencl_unsupported_core_feature, diag::warn_pragma_extension_is_core, -1,
  /* DiagArray657 */ diag::warn_pessimizing_move_on_initialization, diag::warn_pessimizing_move_on_return, -1,
  /* DiagArray658 */ diag::ext_sizeof_alignof_function_type, diag::ext_sizeof_alignof_void_type, diag::warn_sub_ptr_zero_size_types, -1,
  /* DiagArray659 */ diag::warn_cast_nonnull_to_bool, diag::warn_impcast_pointer_to_bool, -1,
  /* DiagArray660 */ diag::warn_pointer_compare, -1,
  /* DiagArray661 */ diag::ext_typecheck_comparison_of_pointer_integer, -1,
  /* DiagArray662 */ diag::ext_typecheck_convert_incompatible_pointer_sign, -1,
  /* DiagArray663 */ diag::warn_pointer_to_enum_cast, -1,
  /* DiagArray664 */ diag::warn_pointer_to_int_cast, -1,
  /* DiagArray665 */ diag::ext_typecheck_cond_incompatible_pointers, -1,
  /* DiagArray666 */ diag::warn_poison_system_directories, -1,
  /* DiagArray667 */ diag::warn_potentially_direct_selector_expression, -1,
  /* DiagArray668 */ diag::warn_side_effects_typeid, -1,
  /* DiagArray669 */ diag::warn_pragma_attribute_unused, -1,
  /* DiagArray670 */ diag::pp_pragma_once_in_main_file, -1,
  /* DiagArray671 */ diag::warn_pragma_pack_modified_after_include, diag::warn_pragma_pack_no_pop_eof, -1,
  /* DiagArray672 */ diag::warn_pragma_pack_non_default_at_include, -1,
  /* DiagArray673 */ diag::pp_pragma_sysheader_in_main_file, -1,
  /* DiagArray674 */ diag::warn_no_support_for_eval_method_source_on_m32, diag::warn_redefine_extname_not_applied, -1,
  /* DiagArray675 */ diag::warn_cxx11_compat_constexpr_body_invalid_stmt, diag::warn_cxx11_compat_constexpr_body_multiple_return, diag::warn_cxx11_compat_constexpr_body_no_return, diag::warn_cxx11_compat_constexpr_local_var, diag::warn_cxx11_compat_constexpr_type_definition, diag::warn_cxx11_compat_decltype_auto_type_specifier, diag::warn_cxx11_compat_deduced_return_type, diag::warn_cxx11_compat_digit_separator, diag::warn_cxx11_compat_generic_lambda, diag::warn_cxx11_compat_init_capture, diag::warn_cxx11_compat_variable_template, -1,
  /* DiagArray677 */ diag::warn_cxx14_compat_class_template_argument_deduction, diag::warn_cxx14_compat_constexpr_if, diag::warn_cxx14_compat_constexpr_on_lambda, diag::warn_cxx14_compat_decomp_decl, diag::warn_cxx14_compat_fold_expression, diag::warn_cxx14_compat_init_statement, diag::warn_cxx14_compat_inline_variable, diag::warn_cxx14_compat_nested_namespace_definition, diag::warn_cxx14_compat_star_this_lambda_capture, diag::warn_cxx14_compat_static_assert_no_message, diag::warn_cxx14_compat_template_nontype_parm_auto_type, diag::warn_cxx14_compat_template_template_param_typename, diag::warn_cxx14_compat_u8_character_literal, diag::warn_cxx14_compat_using_attribute_ns, diag::warn_cxx17_compat_multi_using_declaration, diag::warn_cxx17_compat_using_declaration_pack, diag::warn_for_range_begin_end_types_differ, -1,
  /* DiagArray678 */ diag::warn_cxx14_compat_ns_enum_attribute, diag::warn_cxx17_hex_literal, -1,
  /* DiagArray679 */ diag::warn_cxx17_compat_adl_only_template_id, diag::warn_cxx17_compat_bitfield_member_init, diag::warn_cxx17_compat_capture_binding, diag::warn_cxx17_compat_constexpr_body_invalid_stmt, diag::warn_cxx17_compat_constexpr_ctor_missing_init, diag::warn_cxx17_compat_constexpr_function_try_block, diag::warn_cxx17_compat_constexpr_local_var_no_init, diag::warn_cxx17_compat_constexpr_union_ctor_no_init, diag::warn_cxx17_compat_constexpr_virtual, diag::warn_cxx17_compat_decomp_decl_spec, diag::warn_cxx17_compat_defaulted_comparison, diag::warn_cxx17_compat_defaulted_method_type_mismatch, diag::warn_cxx17_compat_equals_this_lambda_capture, diag::warn_cxx17_compat_explicit_bool, diag::warn_cxx17_compat_for_range_init_stmt, diag::warn_cxx17_compat_init_capture_pack, diag::warn_cxx17_compat_inline_nested_namespace_definition, diag::warn_cxx17_compat_lambda_def_ctor_assign, diag::warn_cxx17_compat_lambda_template_parameter_list, diag::warn_cxx17_compat_missing_varargs_arg, diag::warn_cxx17_compat_spaceship, diag::warn_cxx17_compat_template_nontype_parm_type, diag::warn_cxx17_compat_unicode_type, diag::warn_cxx17_compat_using_decl_class_member_enumerator, diag::warn_cxx17_compat_using_decl_non_member_enumerator, diag::warn_cxx17_compat_using_decl_scoped_enumerator, diag::warn_cxx17_compat_using_enum_declaration, -1,
  /* DiagArray680 */ diag::warn_cxx17_compat_designated_init, diag::warn_cxx17_compat_pointer_to_const_ref_member_on_rvalue, -1,
  /* DiagArray681 */ diag::ext_operator_overload_static, diag::ext_subscript_overload, diag::warn_cxx20_alias_in_init_statement, diag::warn_cxx20_compat_auto_expr, diag::warn_cxx20_compat_consteval_if, diag::warn_cxx20_compat_constexpr_body_invalid_stmt, diag::warn_cxx20_compat_constexpr_var, diag::warn_cxx20_compat_decl_attrs_on_lambda, diag::warn_cxx20_compat_label_end_of_compound_statement, diag::warn_cxx20_compat_size_t_suffix, diag::warn_cxx20_compat_static_lambda, diag::warn_cxx2b_compat_pp_directive, diag::warn_cxx2b_compat_warning_directive, diag::warn_cxx2b_delimited_escape_sequence, -1,
  /* DiagArray683 */ diag::warn_c17_compat_bit_int, diag::warn_c17_compat_static_assert_no_message, diag::warn_c2x_compat_bitint_suffix, diag::warn_c2x_compat_digit_separator, diag::warn_c2x_compat_label_end_of_compound_statement, diag::warn_c2x_compat_pp_directive, diag::warn_c2x_compat_typeof_type_specifier, diag::warn_c2x_compat_warning_directive, -1,
  /* DiagArray685 */ diag::warn_omp51_compat_attributes, -1,
  /* DiagArray686 */ diag::ext_predef_outside_function, -1,
  /* DiagArray687 */ diag::warn_private_extern, -1,
  /* DiagArray688 */ diag::warn_use_of_private_header_outside_module, -1,
  /* DiagArray689 */ diag::warn_mmap_mismatched_private_module_name, diag::warn_mmap_mismatched_private_submodule, diag::warn_mmap_redundant_export_as, diag::warn_no_priv_submodule_use_toplevel, -1,
  /* DiagArray690 */ diag::warn_profile_data_missing, -1,
  /* DiagArray691 */ diag::warn_profile_data_out_of_date, -1,
  /* DiagArray692 */ diag::warn_profile_data_unprofiled, -1,
  /* DiagArray693 */ diag::warn_property_access_suggest, -1,
  /* DiagArray694 */ diag::warn_property_attr_mismatch, diag::warn_property_attribute, diag::warn_property_redecl_getter_mismatch, diag::warn_readonly_property, -1,
  /* DiagArray695 */ diag::warn_unimplemented_protocol_method, -1,
  /* DiagArray696 */ diag::warn_protocol_property_mismatch, -1,
  /* DiagArray697 */ diag::warn_avx_calling_convention, -1,
  /* DiagArray698 */ diag::err_func_returning_qualified_void, -1,
  /* DiagArray699 */ diag::warn_quoted_include_in_framework_header, -1,
  /* DiagArray701 */ diag::warn_for_range_ref_binds_ret_temp, -1,
  /* DiagArray702 */ diag::warn_for_range_const_ref_binds_temp_built_from_ref, diag::warn_for_range_copy, -1,
  /* DiagArray703 */ diag::warn_auto_readonly_iboutlet_property, -1,
  /* DiagArray704 */ diag::warn_bad_receiver_type, -1,
  /* DiagArray705 */ diag::warn_receiver_forward_class, diag::warn_receiver_forward_instance, -1,
  /* DiagArray706 */ diag::ext_member_redeclared, -1,
  /* DiagArray707 */ diag::warn_consteval_if_always_true, -1,
  /* DiagArray709 */ diag::warn_redundant_move_on_return, -1,
  /* DiagArray710 */ diag::warn_redundant_parens_around_declarator, -1,
  /* DiagArray711 */ diag::ext_register_storage_class, -1,
  /* DiagArray712 */ diag::warn_reinterpret_different_from_static, -1,
  /* DiagArray713 */ diag::remark_fe_backend_plugin, -1,
  /* DiagArray715 */ diag::warn_initializer_out_of_order, diag::warn_some_initializers_out_of_order, -1,
  /* DiagArray716 */ diag::ext_designated_init_reordered, -1,
  /* DiagArray717 */ diag::warn_objc_requires_super_protocol, -1,
  /* DiagArray719 */ diag::warn_reserved_extern_symbol, -1,
  /* DiagArray720 */ diag::warn_pp_macro_is_reserved_id, -1,
  /* DiagArray721 */ diag::ext_ms_reserved_user_defined_literal, diag::ext_reserved_user_defined_literal, -1,
  /* DiagArray722 */ diag::warn_pragma_restrict_expansion_macro_use, -1,
  /* DiagArray723 */ diag::ext_retained_language_linkage, -1,
  /* DiagArray724 */ diag::warn_ret_addr_label, diag::warn_ret_local_temp_addr_ref, diag::warn_ret_stack_addr_ref, -1,
  /* DiagArray726 */ diag::ext_return_has_expr, diag::ext_return_missing_expr, diag::warn_falloff_nonvoid_coroutine, diag::warn_falloff_nonvoid_function, diag::warn_falloff_nonvoid_lambda, diag::warn_maybe_falloff_nonvoid_coroutine, diag::warn_maybe_falloff_nonvoid_function, diag::warn_maybe_falloff_nonvoid_lambda, diag::warn_return_missing_expr, -1,
  /* DiagArray727 */ diag::warn_return_value_udt, diag::warn_return_value_udt_incomplete, -1,
  /* DiagArray728 */ diag::ext_ovl_rewrite_equalequal_not_bool, -1,
  /* DiagArray729 */ diag::remark_cc1_round_trip_generated, -1,
  /* DiagArray730 */ diag::warn_no_dynamic_cast_with_rtti_disabled, diag::warn_no_typeid_with_rtti_disabled, -1,
  /* DiagArray731 */ diag::remark_sanitize_address_insert_extra_padding_accepted, diag::remark_sanitize_address_insert_extra_padding_rejected, -1,
  /* DiagArray732 */ diag::warn_drv_sarif_format_unstable, -1,
  /* DiagArray733 */ diag::remark_pp_search_path_usage, -1,
  /* DiagArray734 */ diag::warn_attribute_section_on_redeclaration, diag::warn_duplicate_codeseg_attribute, diag::warn_mismatched_section, -1,
  /* DiagArray735 */ diag::warn_unimplemented_selector, -1,
  /* DiagArray736 */ diag::warn_multiple_selectors, -1,
  /* DiagArray737 */ diag::warn_self_assignment_builtin, -1,
  /* DiagArray738 */ diag::warn_identity_field_assign, -1,
  /* DiagArray739 */ diag::warn_self_assignment_overloaded, -1,
  /* DiagArray740 */ diag::warn_self_move, -1,
  /* DiagArray741 */ diag::warn_semicolon_before_method_body, -1,
  /* DiagArray742 */ diag::warn_missing_sentinel, diag::warn_not_enough_argument, -1,
  /* DiagArray744 */ diag::warn_fe_serialized_diag_failure, diag::warn_fe_serialized_diag_failure_during_finalisation, diag::warn_fe_serialized_diag_merge_failure, -1,
  /* DiagArray745 */ diag::warn_decl_shadow, -1,
  /* DiagArray747 */ diag::warn_shadow_field, -1,
  /* DiagArray748 */ diag::warn_ctor_parm_shadows_field, -1,
  /* DiagArray749 */ diag::warn_modifying_shadowing_decl, -1,
  /* DiagArray750 */ diag::warn_ivar_use_hidden, -1,
  /* DiagArray751 */ diag::warn_decl_shadow_uncaptured_local, -1,
  /* DiagArray752 */ diag::warn_shift_negative, -1,
  /* DiagArray753 */ diag::warn_shift_gt_typewidth, -1,
  /* DiagArray754 */ diag::warn_shift_lhs_negative, -1,
  /* DiagArray755 */ diag::warn_addition_in_bitshift, -1,
  /* DiagArray756 */ diag::warn_shift_result_gt_typewidth, -1,
  /* DiagArray757 */ diag::warn_shift_result_sets_sign_bit, -1,
  /* DiagArray758 */ diag::warn_impcast_integer_64_32, -1,
  /* DiagArray759 */ diag::warn_mixed_sign_comparison, -1,
  /* DiagArray760 */ diag::warn_impcast_integer_sign, diag::warn_impcast_integer_sign_conditional, diag::warn_impcast_nonnegative_result, -1,
  /* DiagArray762 */ diag::warn_no_underlying_type_specified_for_enum_bitfield, -1,
  /* DiagArray763 */ diag::ext_wchar_t_sign_spec, -1,
  /* DiagArray764 */ diag::warn_impcast_single_bit_bitield_precision_constant, -1,
  /* DiagArray765 */ diag::warn_sizeof_array_param, -1,
  /* DiagArray766 */ diag::warn_sizeof_array_decay, -1,
  /* DiagArray767 */ diag::warn_division_sizeof_array, -1,
  /* DiagArray768 */ diag::warn_division_sizeof_ptr, -1,
  /* DiagArray769 */ diag::warn_sizeof_pointer_expr_memaccess, diag::warn_sizeof_pointer_type_memaccess, -1,
  /* DiagArray770 */ diag::warn_slash_u_filename, -1,
  /* DiagArray771 */ diag::warn_slh_does_not_support_asm_goto, -1,
  /* DiagArray772 */ diag::warn_sometimes_uninit_var, -1,
  /* DiagArray773 */ diag::warn_fe_source_mgr, -1,
  /* DiagArray774 */ diag::warn_omp_declare_variant_after_emitted, diag::warn_omp_declare_variant_after_used, diag::warn_omp_declare_variant_marked_as_declare_variant, diag::warn_omp_declare_variant_score_not_constant, diag::warn_omp_nesting_simd, diag::warn_omp_unterminated_declare_target, diag::warn_pragma_omp_ignored, diag::warn_unknown_declare_variant_isa_trait, -1,
  /* DiagArray775 */ diag::warn_sampler_initializer_invalid_bits, -1,
  /* DiagArray777 */ diag::warn_stack_exhausted, -1,
  /* DiagArray778 */ diag::warn_stack_clash_protection_inline_asm, -1,
  /* DiagArray779 */ diag::ext_in_class_initializer_float_type_cxx11, -1,
  /* DiagArray780 */ diag::ext_internal_in_extern_inline, diag::ext_internal_in_extern_inline_quiet, -1,
  /* DiagArray781 */ diag::warn_static_inline_explicit_inst_ignored, -1,
  /* DiagArray782 */ diag::warn_static_local_in_extern_inline, -1,
  /* DiagArray783 */ diag::warn_static_self_reference_in_init, -1,
  /* DiagArray784 */ diag::warn_drv_libstdcxx_not_found, -1,
  /* DiagArray796 */ diag::warn_strict_potentially_direct_selector_expression, -1,
  /* DiagArray797 */ diag::warn_strict_prototypes, -1,
  /* DiagArray798 */ diag::warn_strict_multiple_method_decl, -1,
  /* DiagArray799 */ diag::warn_stringcompare, -1,
  /* DiagArray800 */ diag::warn_concatenated_literal_array_init, -1,
  /* DiagArray801 */ diag::warn_impcast_string_literal_to_bool, -1,
  /* DiagArray802 */ diag::warn_string_plus_char, -1,
  /* DiagArray803 */ diag::warn_string_plus_int, -1,
  /* DiagArray804 */ diag::warn_strlcpycat_wrong_size, -1,
  /* DiagArray805 */ diag::warn_strncat_large_size, diag::warn_strncat_src_size, diag::warn_strncat_wrong_size, -1,
  /* DiagArray806 */ diag::warn_suggest_destructor_marked_not_override_overriding, -1,
  /* DiagArray807 */ diag::warn_suggest_function_marked_not_override_overriding, -1,
  /* DiagArray808 */ diag::ext_typecheck_base_super, -1,
  /* DiagArray809 */ diag::warn_suspicious_bzero_size, -1,
  /* DiagArray811 */ diag::warn_attr_swift_name_decl_kind, diag::warn_attr_swift_name_decl_missing_params, diag::warn_attr_swift_name_function, diag::warn_attr_swift_name_getter_parameters, diag::warn_attr_swift_name_invalid_identifier, diag::warn_attr_swift_name_missing_parameters, diag::warn_attr_swift_name_multiple_selfs, diag::warn_attr_swift_name_num_params, diag::warn_attr_swift_name_setter_parameters, diag::warn_attr_swift_name_subscript_getter_newValue, diag::warn_attr_swift_name_subscript_invalid_parameter, diag::warn_attr_swift_name_subscript_setter_multiple_newValues, diag::warn_attr_swift_name_subscript_setter_no_newValue, -1,
  /* DiagArray812 */ diag::warn_case_value_overflow, diag::warn_missing_case, diag::warn_not_in_enum, -1,
  /* DiagArray813 */ diag::warn_bool_switch_condition, -1,
  /* DiagArray815 */ diag::warn_def_missing_case, -1,
  /* DiagArray816 */ diag::warn_sync_fetch_and_nand_semantics_change, -1,
  /* DiagArray818 */ diag::warn_target_clone_mixed_values, -1,
  /* DiagArray819 */ diag::warn_comparison_bitwise_always, diag::warn_comparison_bitwise_or, -1,
  /* DiagArray820 */ diag::warn_alignment_builtin_useless, diag::warn_comparison_always, -1,
  /* DiagArray821 */ diag::warn_integer_constants_in_conditional_always_true, diag::warn_left_shift_always, diag::warn_tautological_bool_compare, -1,
  /* DiagArray823 */ diag::warn_out_of_range_compare, -1,
  /* DiagArray824 */ diag::warn_tautological_compare_objc_bool, -1,
  /* DiagArray825 */ diag::warn_tautological_overlap_comparison, -1,
  /* DiagArray826 */ diag::warn_nonnull_expr_compare, diag::warn_null_pointer_compare, -1,
  /* DiagArray827 */ diag::warn_tautological_constant_compare, -1,
  /* DiagArray828 */ diag::warn_address_of_reference_null_compare, diag::warn_this_null_compare, -1,
  /* DiagArray829 */ diag::warn_unsigned_char_always_true_comparison, -1,
  /* DiagArray830 */ diag::warn_unsigned_enum_always_true_comparison, -1,
  /* DiagArray831 */ diag::warn_unsigned_always_true_comparison, -1,
  /* DiagArray832 */ diag::warn_tautological_compare_value_range, -1,
  /* DiagArray833 */ diag::warn_tcb_enforcement_violation, -1,
  /* DiagArray834 */ diag::ext_typecheck_decl_incomplete_type, -1,
  /* DiagArray836 */ diag::warn_acquired_before, diag::warn_acquired_before_after_cycle, diag::warn_cannot_resolve_lock, diag::warn_double_lock, diag::warn_expecting_lock_held_on_loop, diag::warn_expecting_locked, diag::warn_fun_excludes_mutex, diag::warn_fun_requires_lock, diag::warn_fun_requires_negative_cap, diag::warn_lock_exclusive_and_shared, diag::warn_lock_some_predecessors, diag::warn_no_unlock, diag::warn_unlock_but_no_lock, diag::warn_unlock_kind_mismatch, diag::warn_var_deref_requires_any_lock, diag::warn_var_deref_requires_lock, diag::warn_variable_requires_any_lock, diag::warn_variable_requires_lock, -1,
  /* DiagArray837 */ diag::warn_thread_attribute_argument_not_lockable, diag::warn_thread_attribute_decl_not_lockable, diag::warn_thread_attribute_decl_not_pointer, diag::warn_thread_attribute_ignored, diag::warn_thread_attribute_not_on_capability_member, diag::warn_thread_attribute_not_on_non_static_member, -1,
  /* DiagArray838 */ diag::warn_thread_safety_beta, -1,
  /* DiagArray839 */ diag::warn_acquire_requires_negative_cap, -1,
  /* DiagArray840 */ diag::warn_fun_requires_lock_precise, diag::warn_var_deref_requires_lock_precise, diag::warn_variable_requires_lock_precise, -1,
  /* DiagArray841 */ diag::warn_guarded_pass_by_reference, diag::warn_pt_guarded_pass_by_reference, -1,
  /* DiagArray842 */ diag::warn_thread_safety_verbose, -1,
  /* DiagArray843 */ diag::trigraph_converted, diag::trigraph_ends_block_comment, diag::trigraph_ignored, diag::trigraph_ignored_block_comment, -1,
  /* DiagArray845 */ diag::warn_type_safety_null_pointer_required, diag::warn_type_safety_type_mismatch, diag::warn_type_tag_for_datatype_wrong_kind, -1,
  /* DiagArray846 */ diag::ext_redefinition_of_typedef, -1,
  /* DiagArray847 */ diag::ext_typename_missing, -1,
  /* DiagArray848 */ diag::warn_fe_unable_to_open_stats_file, -1,
  /* DiagArray849 */ diag::warn_unaligned_access, -1,
  /* DiagArray850 */ diag::warn_imp_cast_drops_unaligned, -1,
  /* DiagArray851 */ diag::warn_unavailable_fwdclass_message, -1,
  /* DiagArray852 */ diag::warn_undeclared_selector, diag::warn_undeclared_selector_with_typo, -1,
  /* DiagArray853 */ diag::warn_pp_undef_identifier, -1,
  /* DiagArray854 */ diag::warn_pp_undef_prefix, -1,
  /* DiagArray855 */ diag::warn_address_of_reference_bool_conversion, diag::warn_this_bool_conversion, -1,
  /* DiagArray856 */ diag::warn_func_template_missing, -1,
  /* DiagArray857 */ diag::warn_undefined_inline, -1,
  /* DiagArray858 */ diag::warn_undefined_internal, -1,
  /* DiagArray859 */ diag::ext_undefined_internal_type, -1,
  /* DiagArray860 */ diag::warn_pointer_indirection_from_incompatible_type, diag::warn_undefined_reinterpret_cast, -1,
  /* DiagArray861 */ diag::warn_var_template_missing, -1,
  /* DiagArray862 */ diag::warn_throw_underaligned_obj, -1,
  /* DiagArray863 */ diag::warn_side_effects_unevaluated_context, -1,
  /* DiagArray864 */ diag::warn_unguarded_availability, -1,
  /* DiagArray865 */ diag::warn_unguarded_availability_new, -1,
  /* DiagArray866 */ diag::warn_delimited_ucn_empty, diag::warn_delimited_ucn_incomplete, diag::warn_ucn_escape_incomplete, diag::warn_ucn_escape_no_digits, diag::warn_ucn_escape_surrogate, diag::warn_ucn_not_valid_in_c89, diag::warn_ucn_not_valid_in_c89_literal, -1,
  /* DiagArray867 */ diag::warn_utf8_symbol_homoglyph, -1,
  /* DiagArray868 */ diag::ext_unicode_whitespace, -1,
  /* DiagArray869 */ diag::warn_utf8_symbol_zero_width, -1,
  /* DiagArray870 */ diag::warn_base_class_is_uninit, diag::warn_field_is_uninit, diag::warn_reference_field_is_uninit, diag::warn_uninit_byref_blockvar_captured_by_block, diag::warn_uninit_self_reference_in_init, diag::warn_uninit_self_reference_in_reference_init, diag::warn_uninit_var, -1,
  /* DiagArray871 */ diag::warn_uninit_const_reference, -1,
  /* DiagArray872 */ diag::warn_drv_unknown_argument_clang_cl, diag::warn_drv_unknown_argument_clang_cl_with_suggestion, -1,
  /* DiagArray873 */ diag::warn_assume_attribute_string_unknown, -1,
  /* DiagArray874 */ diag::warn_unknown_attribute_ignored, -1,
  /* DiagArray875 */ diag::warn_drv_new_cuda_version, diag::warn_drv_partially_supported_cuda_version, -1,
  /* DiagArray876 */ diag::warn_pp_invalid_directive, -1,
  /* DiagArray877 */ diag::ext_unknown_escape, -1,
  /* DiagArray878 */ diag::ext_on_off_switch_syntax, diag::ext_pragma_syntax_eod, diag::ext_stdc_pragma_ignored, diag::warn_pragma_diagnostic_cannot_pop, diag::warn_pragma_diagnostic_invalid, diag::warn_pragma_diagnostic_invalid_option, diag::warn_pragma_diagnostic_invalid_token, diag::warn_pragma_exec_charset_expected, diag::warn_pragma_exec_charset_push_invalid, diag::warn_pragma_exec_charset_spec_invalid, diag::warn_pragma_ignored, diag::warn_pragma_include_alias_expected, diag::warn_pragma_include_alias_expected_filename, diag::warn_pragma_include_alias_mismatch_angle, diag::warn_pragma_include_alias_mismatch_quote, diag::warn_pragma_warning_expected, diag::warn_pragma_warning_expected_number, diag::warn_pragma_warning_push_level, diag::warn_pragma_warning_spec_invalid, diag::warn_stdc_fenv_round_not_supported, -1,
  /* DiagArray879 */ diag::warn_unknown_sanitizer_ignored, -1,
  /* DiagArray880 */ diag::warn_pragma_diagnostic_unknown_warning, diag::warn_unknown_diag_option, diag::warn_unknown_warning_specifier, -1,
  /* DiagArray881 */ diag::ext_template_arg_unnamed_type, -1,
  /* DiagArray882 */ diag::warn_unneeded_internal_decl, diag::warn_unneeded_static_internal_decl, -1,
  /* DiagArray883 */ diag::warn_unneeded_member_function, -1,
  /* DiagArray884 */ diag::warn_unqualified_call_to_std_cast_function, -1,
  /* DiagArray885 */ diag::warn_unreachable, -1,
  /* DiagArray887 */ diag::warn_unreachable_break, -1,
  /* DiagArray888 */ diag::warn_unreachable_fallthrough_attr, -1,
  /* DiagArray889 */ diag::warn_unreachable_association, -1,
  /* DiagArray890 */ diag::warn_unreachable_loop_increment, -1,
  /* DiagArray891 */ diag::warn_unreachable_return, -1,
  /* DiagArray892 */ diag::warn_unsequenced_mod_mod, diag::warn_unsequenced_mod_use, -1,
  /* DiagArray893 */ diag::warn_drv_unsupported_float_abi_by_lib, -1,
  /* DiagArray894 */ diag::warn_target_unsupported_abs2008, diag::warn_target_unsupported_abslegacy, -1,
  /* DiagArray895 */ diag::warn_at_available_unchecked_use, -1,
  /* DiagArray896 */ diag::warn_target_unsupported_compact_branches, -1,
  /* DiagArray897 */ diag::warn_attribute_dll_instantiated_base_class, -1,
  /* DiagArray898 */ diag::warn_fe_backend_unsupported_fp_exceptions, diag::warn_fe_backend_unsupported_fp_rounding, -1,
  /* DiagArray899 */ diag::warn_template_qualified_friend_ignored, diag::warn_template_qualified_friend_unsupported, -1,
  /* DiagArray900 */ diag::warn_drv_unsupported_gpopt, -1,
  /* DiagArray901 */ diag::warn_target_unsupported_nan2008, diag::warn_target_unsupported_nanlegacy, -1,
  /* DiagArray902 */ diag::warn_drv_dwarf_version_limited_by_target, diag::warn_drv_unsupported_debug_info_opt_for_target, -1,
  /* DiagArray903 */ diag::warn_attribute_protected_visibility, -1,
  /* DiagArray904 */ diag::ext_partial_specs_not_deducible, -1,
  /* DiagArray907 */ diag::warn_unused_but_set_parameter, -1,
  /* DiagArray908 */ diag::warn_unused_but_set_variable, -1,
  /* DiagArray909 */ diag::warn_drv_diagnostics_hotness_requires_pgo, diag::warn_drv_diagnostics_misexpect_requires_pgo, diag::warn_drv_empty_joined_argument, diag::warn_drv_input_file_unused, diag::warn_drv_input_file_unused_by_cpp, diag::warn_drv_invalid_arch_name_with_suggestion, diag::warn_drv_preprocessed_input_file_unused, diag::warn_drv_unused_argument, diag::warn_drv_unused_x, diag::warn_ignored_clang_option, diag::warn_ignoring_fdiscard_for_bitcode, diag::warn_ignoring_verify_debuginfo_preserve_export, -1,
  /* DiagArray910 */ diag::warn_unused_comparison, -1,
  /* DiagArray911 */ diag::warn_unused_const_variable, -1,
  /* DiagArray912 */ diag::warn_unused_exception_param, -1,
  /* DiagArray913 */ diag::warn_unused_function, -1,
  /* DiagArray914 */ diag::warn_unused_property_expr, -1,
  /* DiagArray915 */ diag::warn_unused_label, -1,
  /* DiagArray916 */ diag::warn_unused_lambda_capture, -1,
  /* DiagArray917 */ diag::warn_unused_local_typedef, -1,
  /* DiagArray919 */ diag::pp_macro_not_used, -1,
  /* DiagArray920 */ diag::warn_unused_member_function, -1,
  /* DiagArray921 */ diag::warn_unused_parameter, -1,
  /* DiagArray922 */ diag::warn_unused_private_field, -1,
  /* DiagArray923 */ diag::warn_unused_property_backing_ivar, -1,
  /* DiagArray924 */ diag::warn_unused_result, diag::warn_unused_result_msg, -1,
  /* DiagArray925 */ diag::warn_unused_template, -1,
  /* DiagArray926 */ diag::warn_unused_call, diag::warn_unused_comma_left_operand, diag::warn_unused_constructor, diag::warn_unused_constructor_msg, diag::warn_unused_container_subscript_expr, diag::warn_unused_expr, diag::warn_unused_voidptr, -1,
  /* DiagArray927 */ diag::warn_unused_variable, -1,
  /* DiagArray928 */ diag::warn_unused_volatile, -1,
  /* DiagArray929 */ diag::warn_used_but_marked_unused, -1,
  /* DiagArray930 */ diag::warn_user_literal_reserved, -1,
  /* DiagArray931 */ diag::warn_diagnose_if_succeeded, -1,
  /* DiagArray932 */ diag::warn_second_arg_of_va_start_not_last_named_param, diag::warn_second_parameter_to_va_arg_never_compatible, diag::warn_va_start_type_is_undefined, -1,
  /* DiagArray933 */ diag::ext_named_variadic_macro, diag::ext_pp_bad_vaopt_use, diag::ext_variadic_macro, -1,
  /* DiagArray934 */ diag::warn_typecheck_vector_element_sizes_not_equal, -1,
  /* DiagArray935 */ diag::warn_incompatible_vectors, -1,
  /* DiagArray937 */ diag::warn_empty_parens_are_function_decl, diag::warn_parens_disambiguated_as_function_declaration, diag::warn_parens_disambiguated_as_variable_declaration, -1,
  /* DiagArray938 */ diag::warn_decl_in_param_list, diag::warn_redefinition_in_param_list, -1,
  /* DiagArray939 */ diag::warn_vla_used, -1,
  /* DiagArray940 */ diag::ext_vla, -1,
  /* DiagArray941 */ diag::warn_void_pointer_to_enum_cast, -1,
  /* DiagArray942 */ diag::warn_void_pointer_to_int_cast, -1,
  /* DiagArray943 */ diag::ext_typecheck_indirection_through_void_pointer, -1,
  /* DiagArray945 */ diag::warn_wasm_dynamic_exception_spec_ignored, -1,
  /* DiagArray946 */ diag::warn_weak_template_vtable, -1,
  /* DiagArray947 */ diag::warn_weak_vtable, -1,
  /* DiagArray948 */ diag::ext_deprecated_string_literal_conversion, -1,
  /* DiagArray950 */ diag::warn_xor_used_as_pow, diag::warn_xor_used_as_pow_base, diag::warn_xor_used_as_pow_base_extra, -1,
  /* DiagArray951 */ diag::warn_zero_as_null_pointer_constant, -1,
  /* DiagArray952 */ diag::ext_typecheck_zero_array_size, -1,
};

static const int16_t DiagSubGroups[] = {
  /* Empty */ -1,
  /* DiagSubGroup0 */ 293, -1,
  /* DiagSubGroup4 */ 17, 293, -1,
  /* DiagSubGroup11 */ 659, 799, 826, -1,
  /* DiagSubGroup17 */ 540, 646, 812, 813, 509, -1,
  /* DiagSubGroup27 */ 208, -1,
  /* DiagSubGroup29 */ 36, 35, 32, -1,
  /* DiagSubGroup34 */ 31, -1,
  /* DiagSubGroup42 */ 43, -1,
  /* DiagSubGroup52 */ 383, 186, -1,
  /* DiagSubGroup56 */ 874, 374, -1,
  /* DiagSubGroup66 */ 101, 128, 341, -1,
  /* DiagSubGroup67 */ 131, -1,
  /* DiagSubGroup70 */ 764, -1,
  /* DiagSubGroup77 */ 659, 855, -1,
  /* DiagSubGroup78 */ 77, -1,
  /* DiagSubGroup79 */ 74, -1,
  /* DiagSubGroup88 */ 91, -1,
  /* DiagSubGroup89 */ 95, -1,
  /* DiagSubGroup90 */ 99, -1,
  /* DiagSubGroup91 */ 99, 94, 92, 675, 677, 679, 681, -1,
  /* DiagSubGroup93 */ 91, 676, 678, 680, 682, -1,
  /* DiagSubGroup95 */ 96, 97, 98, -1,
  /* DiagSubGroup102 */ 677, 679, 681, -1,
  /* DiagSubGroup103 */ 102, 678, 680, 682, -1,
  /* DiagSubGroup104 */ 101, 100, -1,
  /* DiagSubGroup106 */ 233, 227, 107, 679, 681, -1,
  /* DiagSubGroup108 */ 106, 680, 682, -1,
  /* DiagSubGroup109 */ 105, -1,
  /* DiagSubGroup110 */ 104, -1,
  /* DiagSubGroup111 */ 106, -1,
  /* DiagSubGroup112 */ 107, -1,
  /* DiagSubGroup113 */ 109, -1,
  /* DiagSubGroup115 */ 681, -1,
  /* DiagSubGroup116 */ 115, 682, -1,
  /* DiagSubGroup118 */ 117, 114, -1,
  /* DiagSubGroup119 */ 115, -1,
  /* DiagSubGroup120 */ 116, -1,
  /* DiagSubGroup121 */ 118, -1,
  /* DiagSubGroup123 */ 679, -1,
  /* DiagSubGroup124 */ 680, -1,
  /* DiagSubGroup125 */ 677, -1,
  /* DiagSubGroup126 */ 678, -1,
  /* DiagSubGroup127 */ 675, -1,
  /* DiagSubGroup129 */ 676, -1,
  /* DiagSubGroup130 */ 133, 135, 675, 677, 679, 681, -1,
  /* DiagSubGroup134 */ 130, 131, 132, 676, 678, 680, 682, -1,
  /* DiagSubGroup140 */ 117, -1,
  /* DiagSubGroup141 */ 140, -1,
  /* DiagSubGroup143 */ 160, -1,
  /* DiagSubGroup154 */ 558, -1,
  /* DiagSubGroup158 */ 157, -1,
  /* DiagSubGroup162 */ 163, 164, -1,
  /* DiagSubGroup169 */ 70, 583, -1,
  /* DiagSubGroup174 */ 77, 169, 279, 71, 304, 758, 428, 393, 390, 458, 555, 571, 593, 760, 801, -1,
  /* DiagSubGroup175 */ 571, -1,
  /* DiagSubGroup177 */ 178, 218, 20, 176, -1,
  /* DiagSubGroup180 */ 2, -1,
  /* DiagSubGroup188 */ 190, 192, 191, 724, -1,
  /* DiagSubGroup203 */ 202, 200, -1,
  /* DiagSubGroup206 */ 208, 209, 210, 212, 213, 215, 219, 220, 221, 222, 223, 224, 211, 227, 232, 233, 235, 236, 237, 238, -1,
  /* DiagSubGroup213 */ 216, -1,
  /* DiagSubGroup214 */ 215, -1,
  /* DiagSubGroup215 */ 217, -1,
  /* DiagSubGroup218 */ 225, -1,
  /* DiagSubGroup230 */ 231, -1,
  /* DiagSubGroup238 */ 92, -1,
  /* DiagSubGroup244 */ 245, -1,
  /* DiagSubGroup249 */ 251, 250, -1,
  /* DiagSubGroup252 */ 253, -1,
  /* DiagSubGroup264 */ 220, -1,
  /* DiagSubGroup265 */ 560, -1,
  /* DiagSubGroup274 */ 297, -1,
  /* DiagSubGroup275 */ 277, 221, -1,
  /* DiagSubGroup276 */ 222, -1,
  /* DiagSubGroup279 */ 280, 281, 276, -1,
  /* DiagSubGroup280 */ 223, -1,
  /* DiagSubGroup281 */ 224, -1,
  /* DiagSubGroup293 */ 213, 518, 380, 421, 741, 521, 759, 921, 907, 573, 574, 271, 800, 330, -1,
  /* DiagSubGroup295 */ 132, 96, -1,
  /* DiagSubGroup296 */ 271, -1,
  /* DiagSubGroup304 */ 306, 307, -1,
  /* DiagSubGroup309 */ 310, 319, 561, 316, 318, 312, 311, -1,
  /* DiagSubGroup320 */ 314, 316, 318, -1,
  /* DiagSubGroup325 */ 324, -1,
  /* DiagSubGroup329 */ 818, -1,
  /* DiagSubGroup331 */ 100, 105, 114, -1,
  /* DiagSubGroup336 */ 337, 338, 340, 341, 342, 343, 344, 345, 346, 347, 348, 940, 349, 350, 351, 352, 353, 355, 356, 357, 358, 706, 359, 360, 362, 363, 364, 365, 952, 366, 367, -1,
  /* DiagSubGroup360 */ 361, -1,
  /* DiagSubGroup379 */ 377, 378, -1,
  /* DiagSubGroup380 */ 381, -1,
  /* DiagSubGroup382 */ 391, 392, -1,
  /* DiagSubGroup387 */ 388, -1,
  /* DiagSubGroup390 */ 394, 616, -1,
  /* DiagSubGroup393 */ 617, -1,
  /* DiagSubGroup394 */ 384, -1,
  /* DiagSubGroup406 */ 407, 403, -1,
  /* DiagSubGroup412 */ 414, 557, -1,
  /* DiagSubGroup418 */ 227, -1,
  /* DiagSubGroup429 */ 428, -1,
  /* DiagSubGroup431 */ 432, -1,
  /* DiagSubGroup435 */ 376, -1,
  /* DiagSubGroup460 */ 133, -1,
  /* DiagSubGroup463 */ 98, -1,
  /* DiagSubGroup464 */ 308, 700, -1,
  /* DiagSubGroup475 */ 479, 484, 496, 482, 492, 500, 476, 505, 488, 506, 497, 498, 504, 490, 487, 483, 502, 499, 486, 494, 493, 491, 478, 481, 507, 477, 480, 485, 501, 415, -1,
  /* DiagSubGroup502 */ 503, -1,
  /* DiagSubGroup540 */ 41, 79, 151, 157, 203, 309, 308, 323, 382, 419, 430, 513, 514, 541, 544, 702, 714, 726, 737, 740, 765, 766, 803, 820, 843, 870, 878, 905, 944, 598, 587, 589, 639, 687, 147, 291, 931, -1,
  /* DiagSubGroup541 */ 657, 709, 725, 740, -1,
  /* DiagSubGroup542 */ 496, -1,
  /* DiagSubGroup546 */ 99, -1,
  /* DiagSubGroup552 */ 107, -1,
  /* DiagSubGroup554 */ 759, 174, 459, -1,
  /* DiagSubGroup557 */ 556, -1,
  /* DiagSubGroup573 */ 357, -1,
  /* DiagSubGroup576 */ 577, -1,
  /* DiagSubGroup586 */ 612, -1,
  /* DiagSubGroup592 */ 618, -1,
  /* DiagSubGroup612 */ 613, -1,
  /* DiagSubGroup615 */ 617, 616, 583, 824, -1,
  /* DiagSubGroup625 */ 774, 627, 628, 630, 629, 626, -1,
  /* DiagSubGroup630 */ 629, -1,
  /* DiagSubGroup640 */ 421, -1,
  /* DiagSubGroup646 */ 462, 461, 73, 75, 755, 638, 647, 189, -1,
  /* DiagSubGroup648 */ 864, -1,
  /* DiagSubGroup654 */ 136, 96, 98, 100, 101, 105, 114, 117, 303, 331, 338, 340, 341, 342, 343, 344, 345, 347, 348, 349, 350, 352, 353, 355, 356, 357, 358, 359, 360, 364, 366, 367, 452, 463, 479, 480, 482, 485, 487, 492, 493, 499, 637, 940, 952, 204, 398, 268, 272, 579, 300, 69, 859, 723, 161, 547, 254, 454, 449, -1,
  /* DiagSubGroup656 */ 232, 465, 84, 722, 299, -1,
  /* DiagSubGroup658 */ 358, -1,
  /* DiagSubGroup663 */ 941, -1,
  /* DiagSubGroup664 */ 663, 942, -1,
  /* DiagSubGroup671 */ 672, -1,
  /* DiagSubGroup674 */ 878, 379, 669, 671, -1,
  /* DiagSubGroup676 */ 675, 128, -1,
  /* DiagSubGroup678 */ 677, -1,
  /* DiagSubGroup680 */ 679, -1,
  /* DiagSubGroup682 */ 681, -1,
  /* DiagSubGroup684 */ 683, -1,
  /* DiagSubGroup700 */ 702, 701, -1,
  /* DiagSubGroup711 */ 233, -1,
  /* DiagSubGroup714 */ 715, 716, -1,
  /* DiagSubGroup718 */ 720, -1,
  /* DiagSubGroup719 */ 720, -1,
  /* DiagSubGroup721 */ 94, -1,
  /* DiagSubGroup726 */ 727, -1,
  /* DiagSubGroup735 */ 736, -1,
  /* DiagSubGroup737 */ 739, 738, -1,
  /* DiagSubGroup743 */ 892, -1,
  /* DiagSubGroup745 */ 749, 750, -1,
  /* DiagSubGroup746 */ 745, 748, 751, 747, -1,
  /* DiagSubGroup748 */ 749, -1,
  /* DiagSubGroup776 */ 775, -1,
  /* DiagSubGroup779 */ 362, -1,
  /* DiagSubGroup796 */ 667, -1,
  /* DiagSubGroup797 */ 228, -1,
  /* DiagSubGroup810 */ 769, 263, 566, 472, 809, -1,
  /* DiagSubGroup820 */ 821, 826, 825, 819, 828, 824, -1,
  /* DiagSubGroup821 */ 823, -1,
  /* DiagSubGroup822 */ 844, 832, -1,
  /* DiagSubGroup835 */ 837, 836, 840, 841, -1,
  /* DiagSubGroup844 */ 827, 831, 829, 830, -1,
  /* DiagSubGroup863 */ 668, -1,
  /* DiagSubGroup864 */ 865, -1,
  /* DiagSubGroup870 */ 772, 783, 871, -1,
  /* DiagSubGroup881 */ 135, -1,
  /* DiagSubGroup885 */ 890, 888, 889, -1,
  /* DiagSubGroup886 */ 885, 887, 891, -1,
  /* DiagSubGroup905 */ 906, 913, 915, 922, 916, 917, 926, 927, 908, 923, -1,
  /* DiagSubGroup913 */ 882, -1,
  /* DiagSubGroup918 */ 917, -1,
  /* DiagSubGroup920 */ 883, -1,
  /* DiagSubGroup925 */ 882, -1,
  /* DiagSubGroup926 */ 910, 924, 863, -1,
  /* DiagSubGroup927 */ 911, -1,
  /* DiagSubGroup936 */ 935, -1,
  /* DiagSubGroup939 */ 940, -1,
  /* DiagSubGroup942 */ 941, -1,
  /* DiagSubGroup948 */ 238, -1,
  /* DiagSubGroup949 */ 948, -1,
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
    "-designator\020c++20-extensions\014c++2a-compat\025c++2a-compat-pedanti"
    "c\020c++2a-extensions\020c++2b-extensions\036c++98-c++11-c++14-c++17-co"
    "mpat'c++98-c++11-c++14-c++17-compat-pedantic\030c++98-c++11-c++14-compa"
    "t!c++98-c++11-c++14-compat-pedantic\022c++98-c++11-compat!c++98-c++11-c"
    "ompat-binary-literal\033c++98-c++11-compat-pedantic\014c++98-compat#c++"
    "98-compat-bind-to-temporary-copy\027c++98-compat-extra-semi%c++98-compa"
    "t-local-type-template-args\025c++98-compat-pedantic'c++98-compat-unname"
    "d-type-template-args\016c11-extensions\nc2x-compat\016c2x-extensions\nc"
    "99-compat\016c99-designator\016c99-extensions#call-to-pure-virtual-from"
    "-ctor-dtor\025called-once-parameter\ncast-align\027cast-calling-convent"
    "ion\022cast-function-type\020cast-of-sel-type\tcast-qual\023cast-qual-u"
    "nrelated\nchar-align\017char-subscripts\014clang-cl-pch\020class-conver"
    "sion\015class-varargs\017cmse-union-leak\005comma\007comment\010comment"
    "s\036compare-distinct-pointer-types\022completion-handler\026complex-co"
    "mponent-init\024compound-token-split\035compound-token-split-by-macro\035"
    "compound-token-split-by-space\022concepts-ts-compat\031conditional-type"
    "-mismatch\031conditional-uninitialized\015config-macros\023constant-con"
    "version\022constant-evaluated\030constant-logical-operand\023constexpr-"
    "not-const\010consumed\nconversion\017conversion-null$coro-non-aligned-a"
    "llocation-funciton\tcoroutine%coroutine-missing-unhandled-exception\026"
    "covered-switch-default\003cpp\030cstring-format-directive\026ctad-maybe"
    "-unsupported\021ctor-dtor-privacy\003ctu\013cuda-compat\030custom-atomi"
    "c-properties\027cxx-attribute-extension\010dangling\015dangling-else\016"
    "dangling-field\014dangling-gsl\031dangling-initializer-list\023darwin-s"
    "dk-settings\tdate-time\023dealloc-in-category\035debug-compression-unav"
    "ailable\033declaration-after-statement\032defaulted-function-deleted\026"
    "delegating-ctor-cycles delete-abstract-non-virtual-dtor\021delete-incom"
    "plete$delete-non-abstract-non-virtual-dtor\027delete-non-virtual-dtor#d"
    "elimited-escape-sequence-extension\032deprecate-lax-vec-conv-all\ndepre"
    "cated\035deprecated-altivec-src-compat$deprecated-anon-enum-enum-conver"
    "sion\030deprecated-array-compare\025deprecated-attributes\023deprecated"
    "-builtins\032deprecated-comma-subscript\017deprecated-copy\024deprecate"
    "d-copy-dtor\031deprecated-copy-with-dtor'deprecated-copy-with-user-prov"
    "ided-copy'deprecated-copy-with-user-provided-dtor\024deprecated-corouti"
    "ne\027deprecated-declarations!deprecated-dynamic-exception-spec\027depr"
    "ecated-enum-compare#deprecated-enum-compare-conditional\037deprecated-e"
    "num-enum-conversion deprecated-enum-float-conversion!deprecated-experim"
    "ental-coroutine\032deprecated-implementations\031deprecated-increment-b"
    "ool\030deprecated-non-prototype\031deprecated-objc-isa-usage%deprecated"
    "-objc-pointer-introspection5deprecated-objc-pointer-introspection-perfo"
    "rmSelector\021deprecated-pragma\023deprecated-register\037deprecated-st"
    "atic-analyzer-flag\027deprecated-this-capture\017deprecated-type\023dep"
    "recated-volatile\033deprecated-writable-strings\022direct-ivar-access\030"
    "disabled-macro-expansion\025disabled-optimization\014discard-qual\034di"
    "stributed-object-modifiers\013div-by-zero\020division-by-zero\036dll-at"
    "tribute-on-redeclaration%dllexport-explicit-instantiation-decl\032dllim"
    "port-static-field-def\015documentation\035documentation-deprecated-sync"
    "\022documentation-html\026documentation-pedantic\035documentation-unkno"
    "wn-command\036dollar-in-identifier-extension\020double-promotion\tdtor-"
    "name\014dtor-typedef\030duplicate-decl-specifier\016duplicate-enum\024d"
    "uplicate-method-arg\026duplicate-method-match\022duplicate-protocol\027"
    "dynamic-class-memaccess\026dynamic-exception-spec\006effc++\024elaborat"
    "ed-enum-base\025elaborated-enum-class\022embedded-directive\nempty-body"
    "\023empty-decomposition\017empty-init-stmt\026empty-translation-unit\013"
    "encode-type\014endif-labels\014enum-compare\030enum-compare-conditional"
    "\023enum-compare-switch\031enum-constexpr-conversion\017enum-conversion"
    "\024enum-enum-conversion\025enum-float-conversion\016enum-too-large\nex"
    "ceptions\023excess-initializers\025exit-time-destructors\024expansion-t"
    "o-defined\030explicit-initialize-call\027explicit-ownership-type\016exp"
    "ort-unnamed\026export-using-directive\017extern-c-compat\022extern-init"
    "ializer\005extra\023extra-qualification\nextra-semi\017extra-semi-stmt\014"
    "extra-tokens\032final-dtor-non-final-class\013final-macro\024fixed-enum"
    "-extension\024fixed-point-overflow\tflag-enum\031flexible-array-extensi"
    "ons\020float-conversion\013float-equal\031float-overflow-conversion\025"
    "float-zero-conversion\021for-loop-analysis\006format\021format-extra-ar"
    "gs\030format-insufficient-args\030format-invalid-specifier\016format-no"
    "n-iso\021format-nonliteral\017format-pedantic\017format-security\025for"
    "mat-type-confusion\nformat-y2k\022format-zero-length\010format=2\016for"
    "tify-source\023four-char-constants\015frame-address\021frame-larger-tha"
    "n\022frame-larger-than=%framework-include-private-from-public\023free-n"
    "onheap-object\036function-def-in-objc-container\025function-multiversio"
    "n\014fuse-ld-path\033future-attribute-extensions\015future-compat\ngcc-"
    "compat\023global-constructors\013global-isel\003gnu\026gnu-alignof-expr"
    "ession\024gnu-anonymous-struct\033gnu-array-member-paren-init\015gnu-au"
    "to-type\022gnu-binary-literal\016gnu-case-range\023gnu-complex-integer "
    "gnu-compound-literal-initializer\037gnu-conditional-omitted-operand\016"
    "gnu-designator\025gnu-empty-initializer\020gnu-empty-struct\036gnu-flex"
    "ible-array-initializer\037gnu-flexible-array-union-member\024gnu-foldin"
    "g-constant\026gnu-imaginary-constant\020gnu-include-next\035gnu-inline-"
    "cpp-without-extern\022gnu-label-as-value\017gnu-line-marker\033gnu-null"
    "-pointer-arithmetic\021gnu-pointer-arith\023gnu-redeclared-enum\030gnu-"
    "statement-expression-gnu-statement-expression-from-macro-expansion\025g"
    "nu-static-float-init$gnu-string-literal-operator-template\016gnu-union-"
    "cast\"gnu-variable-sized-type-not-at-end\027gnu-zero-line-directive!gnu"
    "-zero-variadic-macro-arguments\024gpu-maybe-wrong-side\014header-guard\016"
    "header-hygiene\010hip-only\017hlsl-extensions\025idiomatic-parentheses\022"
    "ignored-attributes)ignored-availability-without-sdk-settings\035ignored"
    "-optimization-argument\030ignored-pragma-intrinsic\027ignored-pragma-op"
    "timize\017ignored-pragmas\022ignored-qualifiers\034ignored-reference-qu"
    "alifiers\010implicit\032implicit-atomic-properties#implicit-const-int-f"
    "loat-conversion*implicit-conversion-floating-point-to-bool implicit-exc"
    "eption-spec-mismatch\024implicit-fallthrough!implicit-fallthrough-per-f"
    "unction\037implicit-fixed-point-conversion\031implicit-float-conversion"
    "\035implicit-function-declaration\014implicit-int\027implicit-int-conve"
    "rsion\035implicit-int-float-conversion\024implicit-retain-self\033impli"
    "citly-unsigned-literal\006import&import-preprocessor-directive-pedantic"
    "\021inaccessible-base\032include-next-absolute-path\033include-next-out"
    "side-header\033incompatible-exception-spec#incompatible-function-pointe"
    "r-types\"incompatible-library-redeclaration\026incompatible-ms-struct\032"
    "incompatible-pointer-types.incompatible-pointer-types-discards-qualifie"
    "rs\032incompatible-property-type\024incompatible-sysroot'incomplete-fra"
    "mework-module-declaration\031incomplete-implementation\021incomplete-mo"
    "dule\035incomplete-setjmp-declaration\023incomplete-umbrella\026inconsi"
    "stent-dllimport(inconsistent-missing-destructor-override\035inconsisten"
    "t-missing-override\016increment-bool\022infinite-recursion\tinit-self\025"
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
    "-warning-check\036many-braces-around-scalar-init\nmax-tokens\021max-uns"
    "igned-zero\026memset-transposed-args\022memsize-comparison\021method-si"
    "gnatures\tmicrosoft\022microsoft-abstract\022microsoft-anon-tag\016micr"
    "osoft-cast\021microsoft-charize\027microsoft-comment-paste\024microsoft"
    "-const-init\023microsoft-cpp-macro\"microsoft-default-arg-redefinition\031"
    "microsoft-drectve-section\025microsoft-end-of-file microsoft-enum-forwa"
    "rd-reference\024microsoft-enum-value\030microsoft-exception-spec\020mic"
    "rosoft-exists#microsoft-explicit-constructor-call\035microsoft-extra-qu"
    "alification\024microsoft-fixed-enum\030microsoft-flexible-array\016micr"
    "osoft-goto\033microsoft-inaccessible-base\021microsoft-include\033micro"
    "soft-mutable-reference\031microsoft-pure-definition\032microsoft-redecl"
    "are-static\020microsoft-sealed\027microsoft-static-assert\022microsoft-"
    "template\031microsoft-template-shadow microsoft-union-member-reference\034"
    "microsoft-unqualified-friend\024microsoft-using-decl\032microsoft-void-"
    "pseudo-dtor\tmisexpect\026misleading-indentation\025mismatched-new-dele"
    "te\032mismatched-parameter-types\027mismatched-return-types\017mismatch"
    "ed-tags\016missing-braces\021missing-constinit\024missing-declarations\026"
    "missing-exception-spec\032missing-field-initializers\030missing-format-"
    "attribute\024missing-include-dirs\032missing-method-return-type\020miss"
    "ing-noescape\020missing-noreturn\030missing-prototype-for-cc\022missing"
    "-prototypes\025missing-selector-name\017missing-sysroot\035missing-vari"
    "able-declarations\025misspelled-assumption\014module-build\017module-co"
    "nflict\033module-file-config-mismatch\025module-file-extension\015modul"
    "e-import\031module-import-in-extern-c\032module-include-translation\013"
    "module-lock\"modules-ambiguous-internal-linkage\037modules-import-neste"
    "d-redundant\004most\004move\014msvc-include\016msvc-not-found\tmulticha"
    "r\023multiple-move-vbase\tnarrowing\021nested-anon-types\016nested-exte"
    "rns\020new-returns-null\013newline-eof\007noderef\015noexcept-type\031n"
    "on-c-typedef-for-linkage\007non-gcc\033non-literal-null-conversion'non-"
    "modular-include-in-framework-module\035non-modular-include-in-module\017"
    "non-pod-varargs\032non-power-of-two-alignment\020non-virtual-dtor\007no"
    "nnull\025nonportable-cfstrings\030nonportable-include-path\037nonportab"
    "le-system-include-path!nonportable-vector-initialization\024nontrivial-"
    "memaccess\023nsconsumed-mismatch\022nsreturns-mismatch\017null-arithmet"
    "ic\016null-character\017null-conversion\020null-dereference\027null-poi"
    "nter-arithmetic\030null-pointer-subtraction\013nullability\030nullabili"
    "ty-completeness\"nullability-completeness-on-arrays\024nullability-decl"
    "spec\025nullability-extension#nullability-inferred-on-nested-type\036nu"
    "llable-to-nonnull-conversion+objc-autosynthesis-property-ivar-name-matc"
    "h\035objc-bool-constant-conversion\013objc-boxing\027objc-circular-cont"
    "ainer\016objc-cocoa-api\034objc-designated-initializers\036objc-diction"
    "ary-duplicate-keys\023objc-flexible-array\037objc-forward-class-redefin"
    "ition\024objc-interface-ivars\024objc-literal-compare\027objc-literal-c"
    "onversion\027objc-macro-redefinition\021objc-messaging-id\022objc-metho"
    "d-access\037objc-missing-property-synthesis\030objc-missing-super-calls"
    "\032objc-multiple-method-names\"objc-noncopy-retain-block-property\032o"
    "bjc-nonunified-exceptions#objc-property-assign-on-object-type\034objc-p"
    "roperty-implementation\037objc-property-implicit-mismatch*objc-property"
    "-matches-cocoa-ownership-rule\032objc-property-no-attribute\027objc-pro"
    "perty-synthesis#objc-protocol-method-implementation objc-protocol-prope"
    "rty-synthesis\030objc-protocol-qualifiers\"objc-readonly-with-setter-pr"
    "operty\026objc-redundant-api-use\032objc-redundant-literal-use\017objc-"
    "root-class\025objc-signed-char-bool/objc-signed-char-bool-implicit-floa"
    "t-conversion-objc-signed-char-bool-implicit-int-conversion\023objc-stri"
    "ng-compare\031objc-string-concatenation\034objc-unsafe-perform-selector"
    "\003odr\016old-style-cast\024old-style-definition\027opencl-unsupported"
    "-rgba\006openmp\024openmp-51-extensions\016openmp-clauses\020openmp-loo"
    "p-form\016openmp-mapping\015openmp-target\016option-ignored!ordered-com"
    "pare-function-pointers\027out-of-line-declaration\025out-of-scope-funct"
    "ion\014over-aligned\010overflow\022overlength-strings\037overloaded-shi"
    "ft-op-parentheses\022overloaded-virtual\015override-init\017override-mo"
    "dule\032overriding-method-mismatch\023overriding-t-option\006packed\006"
    "padded\013parentheses\024parentheses-equality\024partial-availability\004"
    "pass\015pass-analysis\013pass-failed\013pass-missed\015pch-date-time\010"
    "pedantic\026pedantic-core-features\017pedantic-macros\020pessimizing-mo"
    "ve\015pointer-arith\027pointer-bool-conversion\017pointer-compare\027po"
    "inter-integer-compare\014pointer-sign\024pointer-to-enum-cast\023pointe"
    "r-to-int-cast\025pointer-type-mismatch\031poison-system-directories\033"
    "potentially-direct-selector potentially-evaluated-expression\026pragma-"
    "clang-attribute\032pragma-once-outside-header\013pragma-pack\036pragma-"
    "pack-suspicious-include#pragma-system-header-outside-header\007pragmas\020"
    "pre-c++14-compat\031pre-c++14-compat-pedantic\020pre-c++17-compat\031pr"
    "e-c++17-compat-pedantic\020pre-c++20-compat\031pre-c++20-compat-pedanti"
    "c\020pre-c++2b-compat\031pre-c++2b-compat-pedantic\016pre-c2x-compat\027"
    "pre-c2x-compat-pedantic\024pre-openmp-51-compat&predefined-identifier-o"
    "utside-function\016private-extern\016private-header\016private-module\025"
    "profile-instr-missing\031profile-instr-out-of-date\030profile-instr-unp"
    "rofiled\032property-access-dot-syntax\033property-attribute-mismatch\010"
    "protocol%protocol-property-synthesis-ambiguity\005psabi\032qualified-vo"
    "id-return-type\"quoted-include-in-framework-header\023range-loop-analys"
    "is\031range-loop-bind-reference\024range-loop-construct\032readonly-ibo"
    "utlet-property\015receiver-expr\026receiver-forward-class\027redeclared"
    "-class-member\026redundant-consteval-if\017redundant-decls\016redundant"
    "-move\020redundant-parens\010register\026reinterpret-base-class\025rema"
    "rk-backend-plugin\007reorder\014reorder-ctor\021reorder-init-list\030re"
    "quires-super-attribute\021reserved-id-macro\023reserved-identifier\031r"
    "eserved-macro-identifier\035reserved-user-defined-literal\022restrict-e"
    "xpansion\031retained-language-linkage\024return-stack-address\017return"
    "-std-move\013return-type\025return-type-c-linkage\020rewrite-not-bool\023"
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
    "maccess\020slash-u-filename\014slh-asm-goto\027sometimes-uninitialized\n"
    "source-mgr\022source-uses-openmp\013spir-compat\014spirv-compat\017stac"
    "k-exhausted\017stack-protector\021static-float-init\020static-in-inline"
    "$static-inline-explicit-instantiation\026static-local-in-inline\020stat"
    "ic-self-init\023stdlibcxx-not-found\017strict-aliasing\021strict-aliasi"
    "ng=0\021strict-aliasing=1\021strict-aliasing=2\017strict-overflow\021st"
    "rict-overflow=0\021strict-overflow=1\021strict-overflow=2\021strict-ove"
    "rflow=3\021strict-overflow=4\021strict-overflow=5\"strict-potentially-d"
    "irect-selector\021strict-prototypes\025strict-selector-match\016string-"
    "compare\024string-concatenation\021string-conversion\020string-plus-cha"
    "r\017string-plus-int\024strlcpy-strlcat-size\014strncat-size\033suggest"
    "-destructor-override\020suggest-override\033super-class-method-mismatch"
    "\020suspicious-bzero\024suspicious-memaccess\024swift-name-attribute\006"
    "switch\013switch-bool\016switch-default\013switch-enum%sync-fetch-and-n"
    "and-semantics-changed\005synth\036target-clones-mixed-specifiers\034tau"
    "tological-bitwise-compare\024tautological-compare\035tautological-const"
    "ant-compare&tautological-constant-in-range-compare*tautological-constan"
    "t-out-of-range-compare\036tautological-objc-bool-compare\034tautologica"
    "l-overlap-compare\034tautological-pointer-compare\037tautological-type-"
    "limit-compare\036tautological-undefined-compare'tautological-unsigned-c"
    "har-zero-compare'tautological-unsigned-enum-zero-compare\"tautological-"
    "unsigned-zero-compare tautological-value-range-compare\017tcb-enforceme"
    "nt$tentative-definition-incomplete-type\015thread-safety\026thread-safe"
    "ty-analysis\030thread-safety-attributes\022thread-safety-beta\026thread"
    "-safety-negative\025thread-safety-precise\027thread-safety-reference\025"
    "thread-safety-verbose\ttrigraphs\013type-limits\013type-safety\024typed"
    "ef-redefinition\020typename-missing\031unable-to-open-stats-file\020una"
    "ligned-access!unaligned-qualifier-implicit-cast\030unavailable-declarat"
    "ions\023undeclared-selector\005undef\014undef-prefix\031undefined-bool-"
    "conversion\027undefined-func-template\020undefined-inline\022undefined-"
    "internal\027undefined-internal-type\032undefined-reinterpret-cast\026un"
    "defined-var-template\035underaligned-exception-object\026unevaluated-ex"
    "pression\026unguarded-availability\032unguarded-availability-new\007uni"
    "code\021unicode-homoglyph\022unicode-whitespace\022unicode-zero-width\015"
    "uninitialized\035uninitialized-const-reference\020unknown-argument\022u"
    "nknown-assumption\022unknown-attributes\024unknown-cuda-version\022unkn"
    "own-directives\027unknown-escape-sequence\017unknown-pragmas\022unknown"
    "-sanitizers\026unknown-warning-option\032unnamed-type-template-args\035"
    "unneeded-internal-declaration\030unneeded-member-function\031unqualifie"
    "d-std-cast-call\020unreachable-code\033unreachable-code-aggressive\026u"
    "nreachable-code-break\034unreachable-code-fallthrough\036unreachable-co"
    "de-generic-assoc\037unreachable-code-loop-increment\027unreachable-code"
    "-return\013unsequenced\017unsupported-abi\017unsupported-abs\036unsuppo"
    "rted-availability-guard\016unsupported-cb#unsupported-dll-base-class-te"
    "mplate\036unsupported-floating-point-opt\022unsupported-friend\021unsup"
    "ported-gpopt\017unsupported-nan\026unsupported-target-opt\026unsupporte"
    "d-visibility\037unusable-partial-specialization\006unused\017unused-arg"
    "ument\030unused-but-set-parameter\027unused-but-set-variable\034unused-"
    "command-line-argument\021unused-comparison\025unused-const-variable\032"
    "unused-exception-parameter\017unused-function\032unused-getter-return-v"
    "alue\014unused-label\025unused-lambda-capture\024unused-local-typedef\025"
    "unused-local-typedefs\015unused-macros\026unused-member-function\020unu"
    "sed-parameter\024unused-private-field\024unused-property-ivar\015unused"
    "-result\017unused-template\014unused-value\017unused-variable\026unused"
    "-volatile-lvalue\026used-but-marked-unused\025user-defined-literals\025"
    "user-defined-warnings\007varargs\017variadic-macros\015vec-elem-size\021"
    "vector-conversion\022vector-conversions\014vexing-parse\nvisibility\003"
    "vla\015vla-extension\031void-pointer-to-enum-cast\030void-pointer-to-in"
    "t-cast\024void-ptr-dereference\025volatile-register-var\023wasm-excepti"
    "on-spec\025weak-template-vtables\014weak-vtables\020writable-strings\015"
    "write-strings\017xor-used-as-pow\035zero-as-null-pointer-constant\021ze"
    "ro-length-array"};

#endif // GET_DIAG_ARRAYS


#ifdef DIAG_ENTRY
DIAG_ENTRY(anonymous_54 /*  */, 0, 0, /* DiagSubGroup0 */ 1, R"()")
DIAG_ENTRY(PoundPragmaMessage /* #pragma-messages */, 1, /* DiagArray1 */ 1, 0, R"()")
DIAG_ENTRY(PoundWarning /* #warnings */, 18, /* DiagArray2 */ 3, 0, R"()")
DIAG_ENTRY(anonymous_257 /* CFString-literal */, 28, /* DiagArray3 */ 5, 0, R"()")
DIAG_ENTRY(anonymous_53 /* CL4 */, 45, 0, /* DiagSubGroup4 */ 3, R"()")
DIAG_ENTRY(IndependentClassAttribute /* IndependentClass-attribute */, 49, /* DiagArray5 */ 7, 0, R"()")
DIAG_ENTRY(NSobjectAttribute /* NSObject-attribute */, 76, /* DiagArray6 */ 10, 0, R"()")
DIAG_ENTRY(anonymous_0 /* abi */, 95, 0, 0, R"()")
DIAG_ENTRY(AbsoluteValue /* absolute-value */, 99, /* DiagArray8 */ 12, 0, R"()")
DIAG_ENTRY(AbstractFinalClass /* abstract-final-class */, 114, /* DiagArray9 */ 17, 0, R"()")
DIAG_ENTRY(anonymous_251 /* abstract-vbase-init */, 135, /* DiagArray10 */ 19, 0, R"()")
DIAG_ENTRY(anonymous_52 /* address */, 155, 0, /* DiagSubGroup11 */ 6, R"()")
DIAG_ENTRY(anonymous_230 /* address-of-packed-member */, 163, /* DiagArray12 */ 21, 0, R"()")
DIAG_ENTRY(AddressOfTemporary /* address-of-temporary */, 188, /* DiagArray13 */ 23, 0, R"()")
DIAG_ENTRY(anonymous_1 /* aggregate-return */, 209, 0, 0, R"()")
DIAG_ENTRY(AIXCompat /* aix-compat */, 226, /* DiagArray15 */ 25, 0, R"()")
DIAG_ENTRY(anonymous_231 /* align-mismatch */, 237, /* DiagArray16 */ 28, 0, R"()")
DIAG_ENTRY(All /* all */, 252, 0, /* DiagSubGroup17 */ 10, R"()")
DIAG_ENTRY(anonymous_175 /* alloca */, 256, /* DiagArray18 */ 30, 0, R"()")
DIAG_ENTRY(anonymous_176 /* alloca-with-align-alignof */, 263, /* DiagArray19 */ 32, 0, R"()")
DIAG_ENTRY(AlwaysInlineCoroutine /* always-inline-coroutine */, 289, /* DiagArray20 */ 34, 0, R"()")
DIAG_ENTRY(anonymous_237 /* ambiguous-delete */, 313, /* DiagArray21 */ 36, 0, R"()")
DIAG_ENTRY(anonymous_130 /* ambiguous-ellipsis */, 330, /* DiagArray22 */ 38, 0, R"()")
DIAG_ENTRY(AmbiguousMacro /* ambiguous-macro */, 349, /* DiagArray23 */ 40, 0, R"()")
DIAG_ENTRY(AmbigMemberTemplate /* ambiguous-member-template */, 365, /* DiagArray24 */ 42, 0, R"()")
DIAG_ENTRY(anonymous_196 /* ambiguous-reversed-operator */, 391, /* DiagArray25 */ 44, 0, R"()")
DIAG_ENTRY(anonymous_90 /* analyzer-incompatible-plugin */, 419, /* DiagArray26 */ 46, 0, R"()")
DIAG_ENTRY(AnonEnumEnumConversion /* anon-enum-enum-conversion */, 448, /* DiagArray27 */ 48, /* DiagSubGroup27 */ 16, R"()")
DIAG_ENTRY(anonymous_131 /* anonymous-pack-parens */, 474, /* DiagArray28 */ 50, 0, R"()")
DIAG_ENTRY(AutomaticReferenceCounting /* arc */, 496, 0, /* DiagSubGroup29 */ 18, R"()")
DIAG_ENTRY(anonymous_129 /* arc-bridge-casts-disallowed-in-nonarc */, 500, /* DiagArray30 */ 52, 0, R"()")
DIAG_ENTRY(ARCRepeatedUseOfWeakMaybe /* arc-maybe-repeated-use-of-weak */, 538, /* DiagArray31 */ 54, 0, R"()")
DIAG_ENTRY(ARCNonPodMemAccess /* arc-non-pod-memaccess */, 569, /* DiagArray32 */ 56, 0, R"()")
DIAG_ENTRY(anonymous_160 /* arc-performSelector-leaks */, 591, /* DiagArray33 */ 58, 0, R"()")
DIAG_ENTRY(ARCRepeatedUseOfWeak /* arc-repeated-use-of-weak */, 617, /* DiagArray34 */ 60, /* DiagSubGroup34 */ 22, R"()")
DIAG_ENTRY(ARCRetainCycles /* arc-retain-cycles */, 642, /* DiagArray35 */ 62, 0, R"()")
DIAG_ENTRY(ARCUnsafeRetainedAssign /* arc-unsafe-retained-assign */, 660, /* DiagArray36 */ 64, 0, R"()")
DIAG_ENTRY(anonymous_259 /* argument-outside-range */, 687, /* DiagArray37 */ 68, 0, R"()")
DIAG_ENTRY(anonymous_260 /* argument-undefined-behaviour */, 710, /* DiagArray38 */ 70, 0, R"()")
DIAG_ENTRY(ArrayBounds /* array-bounds */, 739, /* DiagArray39 */ 72, 0, R"()")
DIAG_ENTRY(ArrayBoundsPointerArithmetic /* array-bounds-pointer-arithmetic */, 752, /* DiagArray40 */ 78, 0, R"()")
DIAG_ENTRY(ArrayParameter /* array-parameter */, 784, /* DiagArray41 */ 81, 0, R"()")
DIAG_ENTRY(ASM /* asm */, 800, 0, /* DiagSubGroup42 */ 24, R"()")
DIAG_ENTRY(ASMOperandWidths /* asm-operand-widths */, 804, /* DiagArray43 */ 83, 0, R"()")
DIAG_ENTRY(anonymous_258 /* assign-enum */, 823, /* DiagArray44 */ 85, 0, R"()")
DIAG_ENTRY(anonymous_147 /* assume */, 835, /* DiagArray45 */ 87, 0, R"()")
DIAG_ENTRY(anonymous_51 /* at-protocol */, 842, 0, 0, R"()")
DIAG_ENTRY(FrameworkHdrAtImport /* atimport-in-framework-header */, 854, /* DiagArray47 */ 89, 0, R"()")
DIAG_ENTRY(anonymous_218 /* atomic-access */, 883, /* DiagArray48 */ 91, 0, R"()")
DIAG_ENTRY(AtomicAlignment /* atomic-alignment */, 897, /* DiagArray49 */ 93, 0, R"()")
DIAG_ENTRY(anonymous_244 /* atomic-implicit-seq-cst */, 914, /* DiagArray50 */ 96, 0, R"()")
DIAG_ENTRY(anonymous_243 /* atomic-memory-ordering */, 938, /* DiagArray51 */ 98, 0, R"()")
DIAG_ENTRY(AtomicProperties /* atomic-properties */, 961, 0, /* DiagSubGroup52 */ 26, R"()")
DIAG_ENTRY(anonymous_155 /* atomic-property-with-user-defined-accessor */, 979, /* DiagArray53 */ 100, 0, R"()")
DIAG_ENTRY(anonymous_192 /* attribute-packed-for-bitfield */, 1022, /* DiagArray54 */ 102, 0, R"()")
DIAG_ENTRY(BackendWarningAttributes /* attribute-warning */, 1052, /* DiagArray55 */ 104, 0, R"()")
DIAG_ENTRY(Attributes /* attributes */, 1070, 0, /* DiagSubGroup56 */ 29, R"()")
DIAG_ENTRY(AutoDisableVptrSanitizer /* auto-disable-vptr-sanitizer */, 1081, /* DiagArray57 */ 106, 0, R"()")
DIAG_ENTRY(anonymous_2 /* auto-import */, 1109, 0, 0, R"()")
DIAG_ENTRY(anonymous_127 /* auto-storage-class */, 1121, /* DiagArray59 */ 108, 0, R"()")
DIAG_ENTRY(anonymous_172 /* auto-var-id */, 1140, /* DiagArray60 */ 110, 0, R"()")
DIAG_ENTRY(Availability /* availability */, 1152, /* DiagArray61 */ 112, 0, R"()")
DIAG_ENTRY(AVRRtlibLinkingQuirks /* avr-rtlib-linking-quirks */, 1165, /* DiagArray62 */ 123, 0, R"()")
DIAG_ENTRY(BackendPlugin /* backend-plugin */, 1190, /* DiagArray63 */ 129, 0, R"()")
DIAG_ENTRY(anonymous_93 /* backslash-newline-escape */, 1205, /* DiagArray64 */ 131, 0, R"()")
DIAG_ENTRY(BadFunctionCast /* bad-function-cast */, 1230, /* DiagArray65 */ 133, 0, R"()")
DIAG_ENTRY(BinaryLiteral /* binary-literal */, 1248, 0, /* DiagSubGroup66 */ 32, R"()")
DIAG_ENTRY(BindToTemporaryCopy /* bind-to-temporary-copy */, 1263, /* DiagArray67 */ 135, /* DiagSubGroup67 */ 36, R"()")
DIAG_ENTRY(anonymous_142 /* binding-in-condition */, 1286, /* DiagArray68 */ 138, 0, R"()")
DIAG_ENTRY(anonymous_134 /* bit-int-extension */, 1307, /* DiagArray69 */ 140, 0, R"()")
DIAG_ENTRY(BitFieldConstantConversion /* bitfield-constant-conversion */, 1325, /* DiagArray70 */ 142, /* DiagSubGroup70 */ 38, R"()")
DIAG_ENTRY(BitFieldEnumConversion /* bitfield-enum-conversion */, 1354, /* DiagArray71 */ 144, 0, R"()")
DIAG_ENTRY(BitFieldWidth /* bitfield-width */, 1379, /* DiagArray72 */ 148, 0, R"()")
DIAG_ENTRY(BitwiseConditionalParentheses /* bitwise-conditional-parentheses */, 1394, /* DiagArray73 */ 150, 0, R"()")
DIAG_ENTRY(BitwiseInsteadOfLogical /* bitwise-instead-of-logical */, 1426, /* DiagArray74 */ 152, 0, R"()")
DIAG_ENTRY(BitwiseOpParentheses /* bitwise-op-parentheses */, 1453, /* DiagArray75 */ 154, 0, R"()")
DIAG_ENTRY(BlockCaptureAutoReleasing /* block-capture-autoreleasing */, 1476, /* DiagArray76 */ 156, 0, R"()")
DIAG_ENTRY(BoolConversion /* bool-conversion */, 1504, /* DiagArray77 */ 158, /* DiagSubGroup77 */ 40, R"()")
DIAG_ENTRY(anonymous_59 /* bool-conversions */, 1520, 0, /* DiagSubGroup78 */ 43, R"()")
DIAG_ENTRY(BoolOperation /* bool-operation */, 1537, /* DiagArray79 */ 160, /* DiagSubGroup79 */ 45, R"()")
DIAG_ENTRY(anonymous_213 /* braced-scalar-init */, 1552, /* DiagArray80 */ 162, 0, R"()")
DIAG_ENTRY(BranchProtection /* branch-protection */, 1571, /* DiagArray81 */ 164, 0, R"()")
DIAG_ENTRY(ObjCBridge /* bridge-cast */, 1589, /* DiagArray82 */ 169, 0, R"()")
DIAG_ENTRY(anonymous_178 /* builtin-assume-aligned-alignment */, 1601, /* DiagArray83 */ 172, 0, R"()")
DIAG_ENTRY(BuiltinMacroRedefined /* builtin-macro-redefined */, 1634, /* DiagArray84 */ 174, 0, R"()")
DIAG_ENTRY(anonymous_148 /* builtin-memcpy-chk-size */, 1658, /* DiagArray85 */ 177, 0, R"()")
DIAG_ENTRY(BuiltinRequiresHeader /* builtin-requires-header */, 1682, /* DiagArray86 */ 179, 0, R"()")
DIAG_ENTRY(CXXCompat /* c++-compat */, 1706, /* DiagArray87 */ 181, 0, R"()")
DIAG_ENTRY(anonymous_19 /* c++0x-compat */, 1717, 0, /* DiagSubGroup88 */ 47, R"()")
DIAG_ENTRY(anonymous_63 /* c++0x-extensions */, 1730, 0, /* DiagSubGroup89 */ 49, R"()")
DIAG_ENTRY(anonymous_17 /* c++0x-narrowing */, 1747, 0, /* DiagSubGroup90 */ 51, R"()")
DIAG_ENTRY(CXX11Compat /* c++11-compat */, 1763, /* DiagArray91 */ 183, /* DiagSubGroup91 */ 53, R"()")
DIAG_ENTRY(CXX11CompatDeprecatedWritableStr /* c++11-compat-deprecated-writable-strings */, 1776, /* DiagArray92 */ 194, 0, R"()")
DIAG_ENTRY(CXX11CompatPedantic /* c++11-compat-pedantic */, 1817, 0, /* DiagSubGroup93 */ 61, R"()")
DIAG_ENTRY(CXX11CompatReservedUserDefinedLiteral /* c++11-compat-reserved-user-defined-literal */, 1839, /* DiagArray94 */ 196, 0, R"()")
DIAG_ENTRY(CXX11 /* c++11-extensions */, 1882, /* DiagArray95 */ 198, /* DiagSubGroup95 */ 67, R"()")
DIAG_ENTRY(CXX11ExtraSemi /* c++11-extra-semi */, 1899, /* DiagArray96 */ 224, 0, R"()")
DIAG_ENTRY(CXX11InlineNamespace /* c++11-inline-namespace */, 1916, /* DiagArray97 */ 226, 0, R"()")
DIAG_ENTRY(CXX11LongLong /* c++11-long-long */, 1939, /* DiagArray98 */ 228, 0, R"()")
DIAG_ENTRY(CXX11Narrowing /* c++11-narrowing */, 1955, /* DiagArray99 */ 230, 0, R"()")
DIAG_ENTRY(CXX14Attrs /* c++14-attribute-extensions */, 1971, /* DiagArray100 */ 238, 0, R"()")
DIAG_ENTRY(CXX14BinaryLiteral /* c++14-binary-literal */, 1998, /* DiagArray101 */ 240, 0, R"()")
DIAG_ENTRY(CXX14Compat /* c++14-compat */, 2019, 0, /* DiagSubGroup102 */ 71, R"()")
DIAG_ENTRY(CXX14CompatPedantic /* c++14-compat-pedantic */, 2032, 0, /* DiagSubGroup103 */ 75, R"()")
DIAG_ENTRY(CXX14 /* c++14-extensions */, 2054, /* DiagArray104 */ 242, /* DiagSubGroup104 */ 80, R"()")
DIAG_ENTRY(CXX17Attrs /* c++17-attribute-extensions */, 2071, /* DiagArray105 */ 250, 0, R"()")
DIAG_ENTRY(CXX17Compat /* c++17-compat */, 2098, 0, /* DiagSubGroup106 */ 83, R"()")
DIAG_ENTRY(CXX17CompatMangling /* c++17-compat-mangling */, 2111, /* DiagArray107 */ 252, 0, R"()")
DIAG_ENTRY(CXX17CompatPedantic /* c++17-compat-pedantic */, 2133, 0, /* DiagSubGroup108 */ 89, R"()")
DIAG_ENTRY(CXX17 /* c++17-extensions */, 2155, /* DiagArray109 */ 254, /* DiagSubGroup109 */ 93, R"()")
DIAG_ENTRY(anonymous_64 /* c++1y-extensions */, 2172, 0, /* DiagSubGroup110 */ 95, R"()")
DIAG_ENTRY(anonymous_20 /* c++1z-compat */, 2189, 0, /* DiagSubGroup111 */ 97, R"()")
DIAG_ENTRY(anonymous_10 /* c++1z-compat-mangling */, 2202, 0, /* DiagSubGroup112 */ 99, R"()")
DIAG_ENTRY(anonymous_65 /* c++1z-extensions */, 2224, 0, /* DiagSubGroup113 */ 101, R"()")
DIAG_ENTRY(CXX20Attrs /* c++20-attribute-extensions */, 2241, /* DiagArray114 */ 272, 0, R"()")
DIAG_ENTRY(CXX20Compat /* c++20-compat */, 2268, /* DiagArray115 */ 274, /* DiagSubGroup115 */ 103, R"()")
DIAG_ENTRY(CXX20CompatPedantic /* c++20-compat-pedantic */, 2281, 0, /* DiagSubGroup116 */ 105, R"()")
DIAG_ENTRY(CXX20Designator /* c++20-designator */, 2303, /* DiagArray117 */ 284, 0, R"()")
DIAG_ENTRY(CXX20 /* c++20-extensions */, 2320, /* DiagArray118 */ 286, /* DiagSubGroup118 */ 108, R"()")
DIAG_ENTRY(anonymous_21 /* c++2a-compat */, 2337, 0, /* DiagSubGroup119 */ 111, R"()")
DIAG_ENTRY(anonymous_22 /* c++2a-compat-pedantic */, 2350, 0, /* DiagSubGroup120 */ 113, R"()")
DIAG_ENTRY(anonymous_66 /* c++2a-extensions */, 2372, 0, /* DiagSubGroup121 */ 115, R"()")
DIAG_ENTRY(CXX2b /* c++2b-extensions */, 2389, /* DiagArray122 */ 307, 0, R"()")
DIAG_ENTRY(anonymous_15 /* c++98-c++11-c++14-c++17-compat */, 2406, 0, /* DiagSubGroup123 */ 117, R"()")
DIAG_ENTRY(anonymous_16 /* c++98-c++11-c++14-c++17-compat-pedantic */, 2437, 0, /* DiagSubGroup124 */ 119, R"()")
DIAG_ENTRY(anonymous_13 /* c++98-c++11-c++14-compat */, 2477, 0, /* DiagSubGroup125 */ 121, R"()")
DIAG_ENTRY(anonymous_14 /* c++98-c++11-c++14-compat-pedantic */, 2502, 0, /* DiagSubGroup126 */ 123, R"()")
DIAG_ENTRY(anonymous_11 /* c++98-c++11-compat */, 2536, 0, /* DiagSubGroup127 */ 125, R"()")
DIAG_ENTRY(CXXPre14CompatBinaryLiteral /* c++98-c++11-compat-binary-literal */, 2555, /* DiagArray128 */ 319, 0, R"()")
DIAG_ENTRY(anonymous_12 /* c++98-c++11-compat-pedantic */, 2589, 0, /* DiagSubGroup129 */ 127, R"()")
DIAG_ENTRY(CXX98Compat /* c++98-compat */, 2617, /* DiagArray130 */ 321, /* DiagSubGroup130 */ 129, R"()")
DIAG_ENTRY(CXX98CompatBindToTemporaryCopy /* c++98-compat-bind-to-temporary-copy */, 2630, /* DiagArray131 */ 381, 0, R"()")
DIAG_ENTRY(CXX98CompatExtraSemi /* c++98-compat-extra-semi */, 2666, /* DiagArray132 */ 383, 0, R"()")
DIAG_ENTRY(CXX98CompatLocalTypeTemplateArgs /* c++98-compat-local-type-template-args */, 2690, /* DiagArray133 */ 385, 0, R"()")
DIAG_ENTRY(CXX98CompatPedantic /* c++98-compat-pedantic */, 2728, /* DiagArray134 */ 387, /* DiagSubGroup134 */ 136, R"()")
DIAG_ENTRY(CXX98CompatUnnamedTypeTemplateArgs /* c++98-compat-unnamed-type-template-args */, 2750, /* DiagArray135 */ 397, 0, R"()")
DIAG_ENTRY(C11 /* c11-extensions */, 2790, /* DiagArray136 */ 399, 0, R"()")
DIAG_ENTRY(C2xCompat /* c2x-compat */, 2805, /* DiagArray137 */ 404, 0, R"()")
DIAG_ENTRY(C2x /* c2x-extensions */, 2816, /* DiagArray138 */ 406, 0, R"()")
DIAG_ENTRY(C99Compat /* c99-compat */, 2831, /* DiagArray139 */ 412, 0, R"()")
DIAG_ENTRY(C99Designator /* c99-designator */, 2842, /* DiagArray140 */ 417, /* DiagSubGroup140 */ 144, R"()")
DIAG_ENTRY(C99 /* c99-extensions */, 2857, /* DiagArray141 */ 423, /* DiagSubGroup141 */ 146, R"()")
DIAG_ENTRY(PureVirtualCallFromCtorDtor /* call-to-pure-virtual-from-ctor-dtor */, 2872, /* DiagArray142 */ 434, 0, R"()")
DIAG_ENTRY(CalledOnceParameter /* called-once-parameter */, 2908, /* DiagArray143 */ 436, /* DiagSubGroup143 */ 148, R"()")
DIAG_ENTRY(CastAlign /* cast-align */, 2930, /* DiagArray144 */ 440, 0, R"()")
DIAG_ENTRY(anonymous_246 /* cast-calling-convention */, 2941, /* DiagArray145 */ 442, 0, R"()")
DIAG_ENTRY(CastFunctionType /* cast-function-type */, 2965, /* DiagArray146 */ 444, 0, R"()")
DIAG_ENTRY(SelTypeCast /* cast-of-sel-type */, 2984, /* DiagArray147 */ 446, 0, R"()")
DIAG_ENTRY(CastQual /* cast-qual */, 3001, /* DiagArray148 */ 448, 0, R"()")
DIAG_ENTRY(anonymous_235 /* cast-qual-unrelated */, 3011, /* DiagArray149 */ 451, 0, R"()")
DIAG_ENTRY(anonymous_3 /* char-align */, 3031, 0, 0, R"()")
DIAG_ENTRY(CharSubscript /* char-subscripts */, 3042, /* DiagArray151 */ 453, 0, R"()")
DIAG_ENTRY(ClangClPch /* clang-cl-pch */, 3058, /* DiagArray152 */ 456, 0, R"()")
DIAG_ENTRY(ClassConversion /* class-conversion */, 3071, /* DiagArray153 */ 461, 0, R"()")
DIAG_ENTRY(ClassVarargs /* class-varargs */, 3088, /* DiagArray154 */ 465, /* DiagSubGroup154 */ 150, R"()")
DIAG_ENTRY(anonymous_179 /* cmse-union-leak */, 3102, /* DiagArray155 */ 467, 0, R"()")
DIAG_ENTRY(anonymous_137 /* comma */, 3118, /* DiagArray156 */ 469, 0, R"()")
DIAG_ENTRY(Comment /* comment */, 3124, /* DiagArray157 */ 471, 0, R"()")
DIAG_ENTRY(anonymous_57 /* comments */, 3132, 0, /* DiagSubGroup158 */ 152, R"()")
DIAG_ENTRY(CompareDistinctPointerType /* compare-distinct-pointer-types */, 3141, /* DiagArray159 */ 476, 0, R"()")
DIAG_ENTRY(CompletionHandler /* completion-handler */, 3172, /* DiagArray160 */ 478, 0, R"()")
DIAG_ENTRY(anonymous_215 /* complex-component-init */, 3191, /* DiagArray161 */ 482, 0, R"()")
DIAG_ENTRY(CompoundTokenSplit /* compound-token-split */, 3214, 0, /* DiagSubGroup162 */ 154, R"()")
DIAG_ENTRY(CompoundTokenSplitByMacro /* compound-token-split-by-macro */, 3235, /* DiagArray163 */ 484, 0, R"()")
DIAG_ENTRY(CompoundTokenSplitBySpace /* compound-token-split-by-space */, 3265, /* DiagArray164 */ 486, 0, R"()")
DIAG_ENTRY(anonymous_135 /* concepts-ts-compat */, 3295, /* DiagArray165 */ 488, 0, R"()")
DIAG_ENTRY(anonymous_248 /* conditional-type-mismatch */, 3314, /* DiagArray166 */ 490, 0, R"()")
DIAG_ENTRY(UninitializedMaybe /* conditional-uninitialized */, 3340, /* DiagArray167 */ 492, 0, R"()")
DIAG_ENTRY(ConfigMacros /* config-macros */, 3366, /* DiagArray168 */ 494, 0, R"()")
DIAG_ENTRY(ConstantConversion /* constant-conversion */, 3380, /* DiagArray169 */ 496, /* DiagSubGroup169 */ 157, R"()")
DIAG_ENTRY(anonymous_73 /* constant-evaluated */, 3400, /* DiagArray170 */ 498, 0, R"()")
DIAG_ENTRY(anonymous_225 /* constant-logical-operand */, 3419, /* DiagArray171 */ 500, 0, R"()")
DIAG_ENTRY(anonymous_170 /* constexpr-not-const */, 3444, /* DiagArray172 */ 502, 0, R"()")
DIAG_ENTRY(Consumed /* consumed */, 3464, /* DiagArray173 */ 504, 0, R"()")
DIAG_ENTRY(Conversion /* conversion */, 3473, /* DiagArray174 */ 513, /* DiagSubGroup174 */ 160, R"()")
DIAG_ENTRY(anonymous_58 /* conversion-null */, 3484, 0, /* DiagSubGroup175 */ 176, R"()")
DIAG_ENTRY(CoroNonAlignedAllocationFunction /* coro-non-aligned-allocation-funciton */, 3500, /* DiagArray176 */ 519, 0, R"()")
DIAG_ENTRY(Coroutine /* coroutine */, 3537, /* DiagArray177 */ 521, /* DiagSubGroup177 */ 178, R"()")
DIAG_ENTRY(CoroutineMissingUnhandledException /* coroutine-missing-unhandled-exception */, 3547, /* DiagArray178 */ 523, 0, R"()")
DIAG_ENTRY(CoveredSwitchDefault /* covered-switch-default */, 3585, /* DiagArray179 */ 525, 0, R"()")
DIAG_ENTRY(anonymous_56 /* cpp */, 3608, 0, /* DiagSubGroup180 */ 183, R"()")
DIAG_ENTRY(ObjCCStringFormat /* cstring-format-directive */, 3612, /* DiagArray181 */ 527, 0, R"()")
DIAG_ENTRY(CTADMaybeUnsupported /* ctad-maybe-unsupported */, 3637, /* DiagArray182 */ 529, 0, R"()")
DIAG_ENTRY(anonymous_4 /* ctor-dtor-privacy */, 3660, 0, 0, R"()")
DIAG_ENTRY(CrossTU /* ctu */, 3678, /* DiagArray184 */ 531, 0, R"()")
DIAG_ENTRY(CudaCompat /* cuda-compat */, 3682, /* DiagArray185 */ 533, 0, R"()")
DIAG_ENTRY(CustomAtomic /* custom-atomic-properties */, 3694, /* DiagArray186 */ 539, 0, R"()")
DIAG_ENTRY(anonymous_132 /* cxx-attribute-extension */, 3719, /* DiagArray187 */ 541, 0, R"()")
DIAG_ENTRY(Dangling /* dangling */, 3743, /* DiagArray188 */ 543, /* DiagSubGroup188 */ 185, R"()")
DIAG_ENTRY(DanglingElse /* dangling-else */, 3752, /* DiagArray189 */ 546, 0, R"()")
DIAG_ENTRY(DanglingField /* dangling-field */, 3766, /* DiagArray190 */ 548, 0, R"()")
DIAG_ENTRY(DanglingGsl /* dangling-gsl */, 3781, /* DiagArray191 */ 553, 0, R"()")
DIAG_ENTRY(DanglingInitializerList /* dangling-initializer-list */, 3794, /* DiagArray192 */ 556, 0, R"()")
DIAG_ENTRY(anonymous_85 /* darwin-sdk-settings */, 3820, /* DiagArray193 */ 558, 0, R"()")
DIAG_ENTRY(anonymous_117 /* date-time */, 3840, /* DiagArray194 */ 560, 0, R"()")
DIAG_ENTRY(DeallocInCategory /* dealloc-in-category */, 3850, /* DiagArray195 */ 562, 0, R"()")
DIAG_ENTRY(anonymous_82 /* debug-compression-unavailable */, 3870, /* DiagArray196 */ 564, 0, R"()")
DIAG_ENTRY(DeclarationAfterStatement /* declaration-after-statement */, 3900, /* DiagArray197 */ 566, 0, R"()")
DIAG_ENTRY(DefaultedFunctionDeleted /* defaulted-function-deleted */, 3928, /* DiagArray198 */ 569, 0, R"()")
DIAG_ENTRY(DelegatingCtorCycles /* delegating-ctor-cycles */, 3955, /* DiagArray199 */ 572, 0, R"()")
DIAG_ENTRY(DeleteAbstractNonVirtualDtor /* delete-abstract-non-virtual-dtor */, 3978, /* DiagArray200 */ 574, 0, R"()")
DIAG_ENTRY(DeleteIncomplete /* delete-incomplete */, 4011, /* DiagArray201 */ 576, 0, R"()")
DIAG_ENTRY(DeleteNonAbstractNonVirtualDtor /* delete-non-abstract-non-virtual-dtor */, 4029, /* DiagArray202 */ 579, 0, R"()")
DIAG_ENTRY(DeleteNonVirtualDtor /* delete-non-virtual-dtor */, 4066, 0, /* DiagSubGroup203 */ 190, R"()")
DIAG_ENTRY(anonymous_100 /* delimited-escape-sequence-extension */, 4090, /* DiagArray204 */ 581, 0, R"()")
DIAG_ENTRY(anonymous_239 /* deprecate-lax-vec-conv-all */, 4126, /* DiagArray205 */ 583, 0, R"()")
DIAG_ENTRY(Deprecated /* deprecated */, 4153, /* DiagArray206 */ 585, /* DiagSubGroup206 */ 193, R"()")
DIAG_ENTRY(anonymous_238 /* deprecated-altivec-src-compat */, 4164, /* DiagArray207 */ 594, 0, R"()")
DIAG_ENTRY(DeprecatedAnonEnumEnumConversion /* deprecated-anon-enum-enum-conversion */, 4194, /* DiagArray208 */ 596, 0, R"()")
DIAG_ENTRY(DeprecatedArrayCompare /* deprecated-array-compare */, 4231, /* DiagArray209 */ 598, 0, R"()")
DIAG_ENTRY(DeprecatedAttributes /* deprecated-attributes */, 4256, /* DiagArray210 */ 600, 0, R"()")
DIAG_ENTRY(DeprecatedBuiltins /* deprecated-builtins */, 4278, /* DiagArray211 */ 604, 0, R"()")
DIAG_ENTRY(DeprecatedCommaSubscript /* deprecated-comma-subscript */, 4298, /* DiagArray212 */ 606, 0, R"()")
DIAG_ENTRY(DeprecatedCopy /* deprecated-copy */, 4325, /* DiagArray213 */ 608, /* DiagSubGroup213 */ 214, R"()")
DIAG_ENTRY(anonymous_5 /* deprecated-copy-dtor */, 4341, 0, /* DiagSubGroup214 */ 216, R"()")
DIAG_ENTRY(DeprecatedCopyWithDtor /* deprecated-copy-with-dtor */, 4362, /* DiagArray215 */ 610, /* DiagSubGroup215 */ 218, R"()")
DIAG_ENTRY(DeprecatedCopyWithUserProvidedCopy /* deprecated-copy-with-user-provided-copy */, 4388, /* DiagArray216 */ 612, 0, R"()")
DIAG_ENTRY(DeprecatedCopyWithUserProvidedDtor /* deprecated-copy-with-user-provided-dtor */, 4428, /* DiagArray217 */ 614, 0, R"()")
DIAG_ENTRY(DeprecatedCoroutine /* deprecated-coroutine */, 4468, /* DiagArray218 */ 616, /* DiagSubGroup218 */ 220, R"()")
DIAG_ENTRY(DeprecatedDeclarations /* deprecated-declarations */, 4489, /* DiagArray219 */ 618, 0, R"()")
DIAG_ENTRY(DeprecatedDynamicExceptionSpec /* deprecated-dynamic-exception-spec */, 4513, /* DiagArray220 */ 625, 0, R"()")
DIAG_ENTRY(DeprecatedEnumCompare /* deprecated-enum-compare */, 4547, /* DiagArray221 */ 627, 0, R"()")
DIAG_ENTRY(DeprecatedEnumCompareConditional /* deprecated-enum-compare-conditional */, 4571, /* DiagArray222 */ 629, 0, R"()")
DIAG_ENTRY(DeprecatedEnumEnumConversion /* deprecated-enum-enum-conversion */, 4607, /* DiagArray223 */ 631, 0, R"()")
DIAG_ENTRY(DeprecatedEnumFloatConversion /* deprecated-enum-float-conversion */, 4639, /* DiagArray224 */ 633, 0, R"()")
DIAG_ENTRY(DeprecatedExperimentalCoroutine /* deprecated-experimental-coroutine */, 4672, /* DiagArray225 */ 635, 0, R"()")
DIAG_ENTRY(DeprecatedImplementations /* deprecated-implementations */, 4706, /* DiagArray226 */ 637, 0, R"()")
DIAG_ENTRY(DeprecatedIncrementBool /* deprecated-increment-bool */, 4733, /* DiagArray227 */ 640, 0, R"()")
DIAG_ENTRY(DeprecatedNonPrototype /* deprecated-non-prototype */, 4759, /* DiagArray228 */ 642, 0, R"()")
DIAG_ENTRY(DeprecatedObjCIsaUsage /* deprecated-objc-isa-usage */, 4784, /* DiagArray229 */ 645, 0, R"()")
DIAG_ENTRY(ObjCPointerIntrospect /* deprecated-objc-pointer-introspection */, 4810, /* DiagArray230 */ 648, /* DiagSubGroup230 */ 222, R"()")
DIAG_ENTRY(ObjCPointerIntrospectPerformSelector /* deprecated-objc-pointer-introspection-performSelector */, 4848, /* DiagArray231 */ 650, 0, R"()")
DIAG_ENTRY(DeprecatedPragma /* deprecated-pragma */, 4902, /* DiagArray232 */ 652, 0, R"()")
DIAG_ENTRY(DeprecatedRegister /* deprecated-register */, 4920, /* DiagArray233 */ 654, 0, R"()")
DIAG_ENTRY(DeprecatedStaticAnalyzerFlag /* deprecated-static-analyzer-flag */, 4940, /* DiagArray234 */ 656, 0, R"()")
DIAG_ENTRY(DeprecatedThisCapture /* deprecated-this-capture */, 4972, /* DiagArray235 */ 658, 0, R"()")
DIAG_ENTRY(DeprecatedType /* deprecated-type */, 4996, /* DiagArray236 */ 660, 0, R"()")
DIAG_ENTRY(DeprecatedVolatile /* deprecated-volatile */, 5012, /* DiagArray237 */ 662, 0, R"()")
DIAG_ENTRY(DeprecatedWritableStr /* deprecated-writable-strings */, 5032, 0, /* DiagSubGroup238 */ 224, R"()")
DIAG_ENTRY(anonymous_264 /* direct-ivar-access */, 5060, /* DiagArray239 */ 669, 0, R"()")
DIAG_ENTRY(anonymous_108 /* disabled-macro-expansion */, 5079, /* DiagArray240 */ 671, 0, R"()")
DIAG_ENTRY(anonymous_7 /* disabled-optimization */, 5104, 0, 0, R"()")
DIAG_ENTRY(anonymous_8 /* discard-qual */, 5126, 0, 0, R"()")
DIAG_ENTRY(DistributedObjectModifiers /* distributed-object-modifiers */, 5139, /* DiagArray243 */ 673, 0, R"()")
DIAG_ENTRY(anonymous_9 /* div-by-zero */, 5168, 0, /* DiagSubGroup244 */ 226, R"()")
DIAG_ENTRY(DivZero /* division-by-zero */, 5180, /* DiagArray245 */ 676, 0, R"()")
DIAG_ENTRY(anonymous_180 /* dll-attribute-on-redeclaration */, 5197, /* DiagArray246 */ 678, 0, R"()")
DIAG_ENTRY(DllexportExplicitInstantiationDecl /* dllexport-explicit-instantiation-decl */, 5228, /* DiagArray247 */ 680, 0, R"()")
DIAG_ENTRY(anonymous_181 /* dllimport-static-field-def */, 5266, /* DiagArray248 */ 682, 0, R"()")
DIAG_ENTRY(Documentation /* documentation */, 5293, /* DiagArray249 */ 684, /* DiagSubGroup249 */ 228, R"()")
DIAG_ENTRY(DocumentationDeprecatedSync /* documentation-deprecated-sync */, 5307, /* DiagArray250 */ 704, 0, R"()")
DIAG_ENTRY(DocumentationHTML /* documentation-html */, 5337, /* DiagArray251 */ 706, 0, R"()")
DIAG_ENTRY(DocumentationPedantic /* documentation-pedantic */, 5356, /* DiagArray252 */ 711, /* DiagSubGroup252 */ 231, R"()")
DIAG_ENTRY(DocumentationUnknownCommand /* documentation-unknown-command */, 5379, /* DiagArray253 */ 713, 0, R"()")
DIAG_ENTRY(anonymous_94 /* dollar-in-identifier-extension */, 5409, /* DiagArray254 */ 716, 0, R"()")
DIAG_ENTRY(DoublePromotion /* double-promotion */, 5440, /* DiagArray255 */ 718, 0, R"()")
DIAG_ENTRY(DtorName /* dtor-name */, 5457, /* DiagArray256 */ 720, 0, R"()")
DIAG_ENTRY(anonymous_169 /* dtor-typedef */, 5467, /* DiagArray257 */ 724, 0, R"()")
DIAG_ENTRY(DuplicateDeclSpecifier /* duplicate-decl-specifier */, 5480, /* DiagArray258 */ 726, 0, R"()")
DIAG_ENTRY(anonymous_136 /* duplicate-enum */, 5505, /* DiagArray259 */ 731, 0, R"()")
DIAG_ENTRY(DuplicateArgDecl /* duplicate-method-arg */, 5520, /* DiagArray260 */ 733, 0, R"()")
DIAG_ENTRY(MethodDuplicate /* duplicate-method-match */, 5541, /* DiagArray261 */ 735, 0, R"()")
DIAG_ENTRY(anonymous_150 /* duplicate-protocol */, 5564, /* DiagArray262 */ 737, 0, R"()")
DIAG_ENTRY(DynamicClassMemaccess /* dynamic-class-memaccess */, 5583, /* DiagArray263 */ 739, 0, R"()")
DIAG_ENTRY(DynamicExceptionSpec /* dynamic-exception-spec */, 5607, /* DiagArray264 */ 741, /* DiagSubGroup264 */ 233, R"()")
DIAG_ENTRY(anonymous_32 /* effc++ */, 5630, 0, /* DiagSubGroup265 */ 235, R"()")
DIAG_ENTRY(anonymous_123 /* elaborated-enum-base */, 5637, /* DiagArray266 */ 743, 0, R"()")
DIAG_ENTRY(anonymous_124 /* elaborated-enum-class */, 5658, /* DiagArray267 */ 745, 0, R"()")
DIAG_ENTRY(anonymous_114 /* embedded-directive */, 5680, /* DiagArray268 */ 747, 0, R"()")
DIAG_ENTRY(EmptyBody /* empty-body */, 5699, /* DiagArray269 */ 749, 0, R"()")
DIAG_ENTRY(anonymous_128 /* empty-decomposition */, 5710, /* DiagArray270 */ 755, 0, R"()")
DIAG_ENTRY(EmptyInitStatement /* empty-init-stmt */, 5730, /* DiagArray271 */ 757, 0, R"()")
DIAG_ENTRY(anonymous_120 /* empty-translation-unit */, 5746, /* DiagArray272 */ 759, 0, R"()")
DIAG_ENTRY(anonymous_184 /* encode-type */, 5769, /* DiagArray273 */ 761, 0, R"()")
DIAG_ENTRY(anonymous_55 /* endif-labels */, 5781, 0, /* DiagSubGroup274 */ 237, R"()")
DIAG_ENTRY(EnumCompare /* enum-compare */, 5794, /* DiagArray275 */ 763, /* DiagSubGroup275 */ 239, R"()")
DIAG_ENTRY(EnumCompareConditional /* enum-compare-conditional */, 5807, /* DiagArray276 */ 765, /* DiagSubGroup276 */ 242, R"()")
DIAG_ENTRY(EnumCompareSwitch /* enum-compare-switch */, 5832, /* DiagArray277 */ 767, 0, R"()")
DIAG_ENTRY(anonymous_72 /* enum-constexpr-conversion */, 5852, /* DiagArray278 */ 769, 0, R"()")
DIAG_ENTRY(EnumConversion /* enum-conversion */, 5878, /* DiagArray279 */ 771, /* DiagSubGroup279 */ 244, R"()")
DIAG_ENTRY(EnumEnumConversion /* enum-enum-conversion */, 5894, /* DiagArray280 */ 773, /* DiagSubGroup280 */ 248, R"()")
DIAG_ENTRY(EnumFloatConversion /* enum-float-conversion */, 5915, /* DiagArray281 */ 775, /* DiagSubGroup281 */ 250, R"()")
DIAG_ENTRY(EnumTooLarge /* enum-too-large */, 5937, /* DiagArray282 */ 777, 0, R"()")
DIAG_ENTRY(Exceptions /* exceptions */, 5952, /* DiagArray283 */ 780, 0, R"()")
DIAG_ENTRY(ExcessInitializers /* excess-initializers */, 5963, /* DiagArray284 */ 784, 0, R"()")
DIAG_ENTRY(ExitTimeDestructors /* exit-time-destructors */, 5983, /* DiagArray285 */ 789, 0, R"()")
DIAG_ENTRY(ExpansionToDefined /* expansion-to-defined */, 6005, /* DiagArray286 */ 791, 0, R"()")
DIAG_ENTRY(ExplicitInitializeCall /* explicit-initialize-call */, 6026, /* DiagArray287 */ 794, 0, R"()")
DIAG_ENTRY(anonymous_217 /* explicit-ownership-type */, 6051, /* DiagArray288 */ 797, 0, R"()")
DIAG_ENTRY(ExportUnnamed /* export-unnamed */, 6075, /* DiagArray289 */ 799, 0, R"()")
DIAG_ENTRY(anonymous_268 /* export-using-directive */, 6090, /* DiagArray290 */ 802, 0, R"()")
DIAG_ENTRY(ExternCCompat /* extern-c-compat */, 6113, /* DiagArray291 */ 804, 0, R"()")
DIAG_ENTRY(anonymous_212 /* extern-initializer */, 6129, /* DiagArray292 */ 806, 0, R"()")
DIAG_ENTRY(Extra /* extra */, 6148, /* DiagArray293 */ 808, /* DiagSubGroup293 */ 252, R"()")
DIAG_ENTRY(anonymous_226 /* extra-qualification */, 6154, /* DiagArray294 */ 810, 0, R"()")
DIAG_ENTRY(ExtraSemi /* extra-semi */, 6174, /* DiagArray295 */ 812, /* DiagSubGroup295 */ 267, R"()")
DIAG_ENTRY(ExtraSemiStmt /* extra-semi-stmt */, 6185, /* DiagArray296 */ 815, /* DiagSubGroup296 */ 270, R"()")
DIAG_ENTRY(ExtraTokens /* extra-tokens */, 6201, /* DiagArray297 */ 817, 0, R"()")
DIAG_ENTRY(FinalDtorNonFinalClass /* final-dtor-non-final-class */, 6214, /* DiagArray298 */ 820, 0, R"()")
DIAG_ENTRY(FinalMacro /* final-macro */, 6241, /* DiagArray299 */ 822, 0, R"()")
DIAG_ENTRY(anonymous_122 /* fixed-enum-extension */, 6253, /* DiagArray300 */ 824, 0, R"()")
DIAG_ENTRY(anonymous_71 /* fixed-point-overflow */, 6274, /* DiagArray301 */ 826, 0, R"()")
DIAG_ENTRY(FlagEnum /* flag-enum */, 6295, /* DiagArray302 */ 828, 0, R"()")
DIAG_ENTRY(FlexibleArrayExtensions /* flexible-array-extensions */, 6305, /* DiagArray303 */ 830, 0, R"()")
DIAG_ENTRY(FloatConversion /* float-conversion */, 6331, /* DiagArray304 */ 833, /* DiagSubGroup304 */ 272, R"()")
DIAG_ENTRY(anonymous_219 /* float-equal */, 6348, /* DiagArray305 */ 835, 0, R"()")
DIAG_ENTRY(FloatOverflowConversion /* float-overflow-conversion */, 6360, /* DiagArray306 */ 837, 0, R"()")
DIAG_ENTRY(FloatZeroConversion /* float-zero-conversion */, 6386, /* DiagArray307 */ 840, 0, R"()")
DIAG_ENTRY(ForLoopAnalysis /* for-loop-analysis */, 6408, /* DiagArray308 */ 842, 0, R"()")
DIAG_ENTRY(Format /* format */, 6426, /* DiagArray309 */ 845, /* DiagSubGroup309 */ 275, R"()")
DIAG_ENTRY(FormatExtraArgs /* format-extra-args */, 6433, /* DiagArray310 */ 872, 0, R"()")
DIAG_ENTRY(FormatInsufficientArgs /* format-insufficient-args */, 6451, /* DiagArray311 */ 874, 0, R"()")
DIAG_ENTRY(FormatInvalidSpecifier /* format-invalid-specifier */, 6476, /* DiagArray312 */ 876, 0, R"()")
DIAG_ENTRY(FormatNonStandard /* format-non-iso */, 6501, /* DiagArray313 */ 878, 0, R"()")
DIAG_ENTRY(FormatNonLiteral /* format-nonliteral */, 6516, /* DiagArray314 */ 882, 0, R"()")
DIAG_ENTRY(FormatPedantic /* format-pedantic */, 6534, /* DiagArray315 */ 884, 0, R"()")
DIAG_ENTRY(FormatSecurity /* format-security */, 6550, /* DiagArray316 */ 887, 0, R"()")
DIAG_ENTRY(FormatTypeConfusion /* format-type-confusion */, 6566, /* DiagArray317 */ 889, 0, R"()")
DIAG_ENTRY(FormatY2K /* format-y2k */, 6588, 0, 0, R"()")
DIAG_ENTRY(FormatZeroLength /* format-zero-length */, 6599, /* DiagArray319 */ 891, 0, R"()")
DIAG_ENTRY(Format2 /* format=2 */, 6618, 0, /* DiagSubGroup320 */ 283, R"()")
DIAG_ENTRY(FortifySource /* fortify-source */, 6627, /* DiagArray321 */ 893, 0, R"()")
DIAG_ENTRY(FourByteMultiChar /* four-char-constants */, 6642, /* DiagArray322 */ 899, 0, R"()")
DIAG_ENTRY(FrameAddress /* frame-address */, 6662, /* DiagArray323 */ 901, 0, R"()")
DIAG_ENTRY(BackendFrameLargerThan /* frame-larger-than */, 6676, /* DiagArray324 */ 903, 0, R"()")
DIAG_ENTRY(anonymous_68 /* frame-larger-than= */, 6694, 0, /* DiagSubGroup325 */ 287, R"()")
DIAG_ENTRY(FrameworkIncludePrivateFromPublic /* framework-include-private-from-public */, 6713, /* DiagArray326 */ 906, 0, R"()")
DIAG_ENTRY(FreeNonHeapObject /* free-nonheap-object */, 6751, /* DiagArray327 */ 908, 0, R"()")
DIAG_ENTRY(FunctionDefInObjCContainer /* function-def-in-objc-container */, 6771, /* DiagArray328 */ 910, 0, R"()")
DIAG_ENTRY(FunctionMultiVersioning /* function-multiversion */, 6802, /* DiagArray329 */ 912, /* DiagSubGroup329 */ 289, R"()")
DIAG_ENTRY(FUseLdPath /* fuse-ld-path */, 6824, /* DiagArray330 */ 916, 0, R"()")
DIAG_ENTRY(FutureAttrs /* future-attribute-extensions */, 6837, 0, /* DiagSubGroup331 */ 291, R"()")
DIAG_ENTRY(FutureCompat /* future-compat */, 6865, 0, 0, R"()")
DIAG_ENTRY(GccCompat /* gcc-compat */, 6879, /* DiagArray333 */ 918, 0, R"()")
DIAG_ENTRY(GlobalConstructors /* global-constructors */, 6890, /* DiagArray334 */ 930, 0, R"()")
DIAG_ENTRY(GlobalISel /* global-isel */, 6910, /* DiagArray335 */ 933, 0, R"()")
DIAG_ENTRY(GNU /* gnu */, 6922, 0, /* DiagSubGroup336 */ 295, R"()")
DIAG_ENTRY(GNUAlignofExpression /* gnu-alignof-expression */, 6926, /* DiagArray337 */ 936, 0, R"()")
DIAG_ENTRY(GNUAnonymousStruct /* gnu-anonymous-struct */, 6949, /* DiagArray338 */ 938, 0, R"()")
DIAG_ENTRY(anonymous_228 /* gnu-array-member-paren-init */, 6970, /* DiagArray339 */ 940, 0, R"()")
DIAG_ENTRY(GNUAutoType /* gnu-auto-type */, 6998, /* DiagArray340 */ 942, 0, R"()")
DIAG_ENTRY(GNUBinaryLiteral /* gnu-binary-literal */, 7012, /* DiagArray341 */ 944, 0, R"()")
DIAG_ENTRY(GNUCaseRange /* gnu-case-range */, 7031, /* DiagArray342 */ 946, 0, R"()")
DIAG_ENTRY(GNUComplexInteger /* gnu-complex-integer */, 7046, /* DiagArray343 */ 948, 0, R"()")
DIAG_ENTRY(GNUCompoundLiteralInitializer /* gnu-compound-literal-initializer */, 7066, /* DiagArray344 */ 950, 0, R"()")
DIAG_ENTRY(GNUConditionalOmittedOperand /* gnu-conditional-omitted-operand */, 7099, /* DiagArray345 */ 952, 0, R"()")
DIAG_ENTRY(GNUDesignator /* gnu-designator */, 7131, /* DiagArray346 */ 954, 0, R"()")
DIAG_ENTRY(GNUEmptyInitializer /* gnu-empty-initializer */, 7146, /* DiagArray347 */ 958, 0, R"()")
DIAG_ENTRY(GNUEmptyStruct /* gnu-empty-struct */, 7168, /* DiagArray348 */ 960, 0, R"()")
DIAG_ENTRY(GNUFlexibleArrayInitializer /* gnu-flexible-array-initializer */, 7185, /* DiagArray349 */ 964, 0, R"()")
DIAG_ENTRY(GNUFlexibleArrayUnionMember /* gnu-flexible-array-union-member */, 7216, /* DiagArray350 */ 966, 0, R"()")
DIAG_ENTRY(GNUFoldingConstant /* gnu-folding-constant */, 7248, /* DiagArray351 */ 968, 0, R"()")
DIAG_ENTRY(GNUImaginaryConstant /* gnu-imaginary-constant */, 7269, /* DiagArray352 */ 972, 0, R"()")
DIAG_ENTRY(GNUIncludeNext /* gnu-include-next */, 7292, /* DiagArray353 */ 974, 0, R"()")
DIAG_ENTRY(anonymous_185 /* gnu-inline-cpp-without-extern */, 7309, /* DiagArray354 */ 976, 0, R"()")
DIAG_ENTRY(GNULabelsAsValue /* gnu-label-as-value */, 7339, /* DiagArray355 */ 978, 0, R"()")
DIAG_ENTRY(GNULineMarker /* gnu-line-marker */, 7358, /* DiagArray356 */ 981, 0, R"()")
DIAG_ENTRY(GNUNullPointerArithmetic /* gnu-null-pointer-arithmetic */, 7374, /* DiagArray357 */ 983, 0, R"()")
DIAG_ENTRY(GNUPointerArith /* gnu-pointer-arith */, 7402, /* DiagArray358 */ 985, 0, R"()")
DIAG_ENTRY(GNURedeclaredEnum /* gnu-redeclared-enum */, 7420, /* DiagArray359 */ 989, 0, R"()")
DIAG_ENTRY(GNUStatementExpression /* gnu-statement-expression */, 7440, /* DiagArray360 */ 991, /* DiagSubGroup360 */ 327, R"()")
DIAG_ENTRY(GNUStatementExpressionFromMacroExpansion /* gnu-statement-expression-from-macro-expansion */, 7465, /* DiagArray361 */ 993, 0, R"()")
DIAG_ENTRY(GNUStaticFloatInit /* gnu-static-float-init */, 7511, /* DiagArray362 */ 995, 0, R"()")
DIAG_ENTRY(GNUStringLiteralOperatorTemplate /* gnu-string-literal-operator-template */, 7533, /* DiagArray363 */ 997, 0, R"()")
DIAG_ENTRY(GNUUnionCast /* gnu-union-cast */, 7570, /* DiagArray364 */ 999, 0, R"()")
DIAG_ENTRY(GNUVariableSizedTypeNotAtEnd /* gnu-variable-sized-type-not-at-end */, 7585, /* DiagArray365 */ 1001, 0, R"()")
DIAG_ENTRY(GNUZeroLineDirective /* gnu-zero-line-directive */, 7620, /* DiagArray366 */ 1003, 0, R"()")
DIAG_ENTRY(GNUZeroVariadicMacroArguments /* gnu-zero-variadic-macro-arguments */, 7644, /* DiagArray367 */ 1005, 0, R"()")
DIAG_ENTRY(anonymous_245 /* gpu-maybe-wrong-side */, 7678, /* DiagArray368 */ 1008, 0, R"()")
DIAG_ENTRY(anonymous_119 /* header-guard */, 7699, /* DiagArray369 */ 1010, 0, R"()")
DIAG_ENTRY(HeaderHygiene /* header-hygiene */, 7712, /* DiagArray370 */ 1012, 0, R"()")
DIAG_ENTRY(HIPOnly /* hip-only */, 7727, /* DiagArray371 */ 1014, 0, R"()")
DIAG_ENTRY(HLSLExtension /* hlsl-extensions */, 7736, /* DiagArray372 */ 1016, 0, R"()")
DIAG_ENTRY(anonymous_241 /* idiomatic-parentheses */, 7752, /* DiagArray373 */ 1018, 0, R"()")
DIAG_ENTRY(IgnoredAttributes /* ignored-attributes */, 7774, /* DiagArray374 */ 1020, 0, R"()")
DIAG_ENTRY(anonymous_188 /* ignored-availability-without-sdk-settings */, 7793, /* DiagArray375 */ 1104, 0, R"()")
DIAG_ENTRY(IgnoredOptimizationArgument /* ignored-optimization-argument */, 7835, /* DiagArray376 */ 1106, 0, R"()")
DIAG_ENTRY(IgnoredPragmaIntrinsic /* ignored-pragma-intrinsic */, 7865, /* DiagArray377 */ 1109, 0, R"()")
DIAG_ENTRY(IgnoredPragmaOptimize /* ignored-pragma-optimize */, 7890, 0, 0, R"()")
DIAG_ENTRY(IgnoredPragmas /* ignored-pragmas */, 7914, /* DiagArray379 */ 1111, /* DiagSubGroup379 */ 329, R"()")
DIAG_ENTRY(IgnoredQualifiers /* ignored-qualifiers */, 7930, /* DiagArray380 */ 1157, /* DiagSubGroup380 */ 332, R"()")
DIAG_ENTRY(IgnoredReferenceQualifiers /* ignored-reference-qualifiers */, 7949, /* DiagArray381 */ 1162, 0, R"()")
DIAG_ENTRY(Implicit /* implicit */, 7978, 0, /* DiagSubGroup382 */ 334, R"()")
DIAG_ENTRY(ImplicitAtomic /* implicit-atomic-properties */, 7987, /* DiagArray383 */ 1164, 0, R"()")
DIAG_ENTRY(ImplicitConstIntFloatConversion /* implicit-const-int-float-conversion */, 8014, /* DiagArray384 */ 1167, 0, R"()")
DIAG_ENTRY(ImplicitConversionFloatingPointToBool /* implicit-conversion-floating-point-to-bool */, 8050, /* DiagArray385 */ 1169, 0, R"()")
DIAG_ENTRY(anonymous_256 /* implicit-exception-spec-mismatch */, 8093, /* DiagArray386 */ 1171, 0, R"()")
DIAG_ENTRY(ImplicitFallthrough /* implicit-fallthrough */, 8126, /* DiagArray387 */ 1173, /* DiagSubGroup387 */ 337, R"()")
DIAG_ENTRY(ImplicitFallthroughPerFunction /* implicit-fallthrough-per-function */, 8147, /* DiagArray388 */ 1175, 0, R"()")
DIAG_ENTRY(ImplicitFixedPointConversion /* implicit-fixed-point-conversion */, 8181, /* DiagArray389 */ 1177, 0, R"()")
DIAG_ENTRY(ImplicitFloatConversion /* implicit-float-conversion */, 8213, /* DiagArray390 */ 1179, /* DiagSubGroup390 */ 339, R"()")
DIAG_ENTRY(ImplicitFunctionDeclare /* implicit-function-declaration */, 8239, /* DiagArray391 */ 1182, 0, R"()")
DIAG_ENTRY(ImplicitInt /* implicit-int */, 8269, /* DiagArray392 */ 1188, 0, R"()")
DIAG_ENTRY(ImplicitIntConversion /* implicit-int-conversion */, 8282, /* DiagArray393 */ 1192, /* DiagSubGroup393 */ 342, R"()")
DIAG_ENTRY(ImplicitIntFloatConversion /* implicit-int-float-conversion */, 8306, /* DiagArray394 */ 1195, /* DiagSubGroup394 */ 344, R"()")
DIAG_ENTRY(anonymous_161 /* implicit-retain-self */, 8336, /* DiagArray395 */ 1197, 0, R"()")
DIAG_ENTRY(ImplicitlyUnsignedLiteral /* implicitly-unsigned-literal */, 8357, /* DiagArray396 */ 1199, 0, R"()")
DIAG_ENTRY(anonymous_23 /* import */, 8385, 0, 0, R"()")
DIAG_ENTRY(anonymous_113 /* import-preprocessor-directive-pedantic */, 8392, /* DiagArray398 */ 1201, 0, R"()")
DIAG_ENTRY(anonymous_253 /* inaccessible-base */, 8431, /* DiagArray399 */ 1203, 0, R"()")
DIAG_ENTRY(anonymous_103 /* include-next-absolute-path */, 8449, /* DiagArray400 */ 1205, 0, R"()")
DIAG_ENTRY(anonymous_102 /* include-next-outside-header */, 8476, /* DiagArray401 */ 1207, 0, R"()")
DIAG_ENTRY(IncompatibleExceptionSpec /* incompatible-exception-spec */, 8504, /* DiagArray402 */ 1209, 0, R"()")
DIAG_ENTRY(IncompatibleFunctionPointerTypes /* incompatible-function-pointer-types */, 8532, /* DiagArray403 */ 1212, 0, R"()")
DIAG_ENTRY(anonymous_144 /* incompatible-library-redeclaration */, 8568, /* DiagArray404 */ 1214, 0, R"()")
DIAG_ENTRY(IncompatibleMSStruct /* incompatible-ms-struct */, 8603, /* DiagArray405 */ 1216, 0, R"()")
DIAG_ENTRY(IncompatiblePointerTypes /* incompatible-pointer-types */, 8626, /* DiagArray406 */ 1219, /* DiagSubGroup406 */ 346, R"()")
DIAG_ENTRY(IncompatiblePointerTypesDiscardsQualifiers /* incompatible-pointer-types-discards-qualifiers */, 8653, /* DiagArray407 */ 1221, 0, R"()")
DIAG_ENTRY(anonymous_151 /* incompatible-property-type */, 8700, /* DiagArray408 */ 1225, 0, R"()")
DIAG_ENTRY(anonymous_81 /* incompatible-sysroot */, 8727, /* DiagArray409 */ 1227, 0, R"()")
DIAG_ENTRY(IncompleteFrameworkModuleDeclaration /* incomplete-framework-module-declaration */, 8748, /* DiagArray410 */ 1229, 0, R"()")
DIAG_ENTRY(anonymous_153 /* incomplete-implementation */, 8788, /* DiagArray411 */ 1231, 0, R"()")
DIAG_ENTRY(IncompleteModule /* incomplete-module */, 8814, 0, /* DiagSubGroup412 */ 349, R"()")
DIAG_ENTRY(anonymous_143 /* incomplete-setjmp-declaration */, 8832, /* DiagArray413 */ 1233, 0, R"()")
DIAG_ENTRY(IncompleteUmbrella /* incomplete-umbrella */, 8862, /* DiagArray414 */ 1235, 0, R"()")
DIAG_ENTRY(MicrosoftInconsistentDllImport /* inconsistent-dllimport */, 8882, /* DiagArray415 */ 1239, 0, R"()")
DIAG_ENTRY(CXX11WarnInconsistentOverrideDestructor /* inconsistent-missing-destructor-override */, 8905, /* DiagArray416 */ 1242, 0, R"()")
DIAG_ENTRY(CXX11WarnInconsistentOverrideMethod /* inconsistent-missing-override */, 8946, /* DiagArray417 */ 1244, 0, R"()")
DIAG_ENTRY(IncrementBool /* increment-bool */, 8976, /* DiagArray418 */ 1246, /* DiagSubGroup418 */ 352, R"()")
DIAG_ENTRY(InfiniteRecursion /* infinite-recursion */, 8991, /* DiagArray419 */ 1248, 0, R"()")
DIAG_ENTRY(anonymous_24 /* init-self */, 9010, 0, 0, R"()")
DIAG_ENTRY(InitializerOverrides /* initializer-overrides */, 9020, /* DiagArray421 */ 1250, 0, R"()")
DIAG_ENTRY(anonymous_168 /* injected-class-name */, 9042, /* DiagArray422 */ 1253, 0, R"()")
DIAG_ENTRY(anonymous_25 /* inline */, 9062, 0, 0, R"()")
DIAG_ENTRY(BackendInlineAsm /* inline-asm */, 9069, /* DiagArray424 */ 1255, 0, R"()")
DIAG_ENTRY(InlineNamespaceReopenedNoninline /* inline-namespace-reopened-noninline */, 9080, /* DiagArray425 */ 1257, 0, R"()")
DIAG_ENTRY(anonymous_254 /* inline-new-delete */, 9116, /* DiagArray426 */ 1259, 0, R"()")
DIAG_ENTRY(anonymous_201 /* instantiation-after-specialization */, 9134, /* DiagArray427 */ 1261, 0, R"()")
DIAG_ENTRY(IntConversion /* int-conversion */, 9169, /* DiagArray428 */ 1263, 0, R"()")
DIAG_ENTRY(anonymous_60 /* int-conversions */, 9184, 0, /* DiagSubGroup429 */ 354, R"()")
DIAG_ENTRY(IntInBoolContext /* int-in-bool-context */, 9200, /* DiagArray430 */ 1266, 0, R"()")
DIAG_ENTRY(IntToPointerCast /* int-to-pointer-cast */, 9220, /* DiagArray431 */ 1269, /* DiagSubGroup431 */ 356, R"()")
DIAG_ENTRY(IntToVoidPointerCast /* int-to-void-pointer-cast */, 9240, /* DiagArray432 */ 1271, 0, R"()")
DIAG_ENTRY(anonymous_70 /* integer-overflow */, 9265, /* DiagArray433 */ 1273, 0, R"()")
DIAG_ENTRY(anonymous_139 /* interrupt-service-routine */, 9282, /* DiagArray434 */ 1275, 0, R"()")
DIAG_ENTRY(InvalidCommandLineArgument /* invalid-command-line-argument */, 9308, /* DiagArray435 */ 1277, /* DiagSubGroup435 */ 358, R"()")
DIAG_ENTRY(anonymous_171 /* invalid-constexpr */, 9338, /* DiagArray436 */ 1287, 0, R"()")
DIAG_ENTRY(ObjCInvalidIBOutletProperty /* invalid-iboutlet */, 9356, /* DiagArray437 */ 1289, 0, R"()")
DIAG_ENTRY(anonymous_182 /* invalid-initializer-from-system-header */, 9373, /* DiagArray438 */ 1292, 0, R"()")
DIAG_ENTRY(InvalidIOSDeploymentTarget /* invalid-ios-deployment-target */, 9412, /* DiagArray439 */ 1294, 0, R"()")
DIAG_ENTRY(anonymous_194 /* invalid-no-builtin-names */, 9442, /* DiagArray440 */ 1296, 0, R"()")
DIAG_ENTRY(InvalidNoreturn /* invalid-noreturn */, 9467, /* DiagArray441 */ 1298, 0, R"()")
DIAG_ENTRY(InvalidOffsetof /* invalid-offsetof */, 9484, /* DiagArray442 */ 1301, 0, R"()")
DIAG_ENTRY(InvalidOrNonExistentDirectory /* invalid-or-nonexistent-directory */, 9501, /* DiagArray443 */ 1304, 0, R"()")
DIAG_ENTRY(anonymous_199 /* invalid-partial-specialization */, 9534, /* DiagArray444 */ 1307, 0, R"()")
DIAG_ENTRY(anonymous_26 /* invalid-pch */, 9565, 0, 0, R"()")
DIAG_ENTRY(InvalidPPToken /* invalid-pp-token */, 9577, /* DiagArray446 */ 1309, 0, R"()")
DIAG_ENTRY(InvalidSourceEncoding /* invalid-source-encoding */, 9594, /* DiagArray447 */ 1312, 0, R"()")
DIAG_ENTRY(anonymous_116 /* invalid-token-paste */, 9618, /* DiagArray448 */ 1315, 0, R"()")
DIAG_ENTRY(anonymous_96 /* invalid-utf8 */, 9638, /* DiagArray449 */ 1317, 0, R"()")
DIAG_ENTRY(anonymous_240 /* jump-seh-finally */, 9651, /* DiagArray450 */ 1319, 0, R"()")
DIAG_ENTRY(KeywordCompat /* keyword-compat */, 9668, /* DiagArray451 */ 1321, 0, R"()")
DIAG_ENTRY(KeywordAsMacro /* keyword-macro */, 9683, /* DiagArray452 */ 1323, 0, R"()")
DIAG_ENTRY(KNRPromotedParameter /* knr-promoted-parameter */, 9697, /* DiagArray453 */ 1325, 0, R"()")
DIAG_ENTRY(anonymous_95 /* language-extension-token */, 9720, /* DiagArray454 */ 1327, 0, R"()")
DIAG_ENTRY(LargeByValueCopy /* large-by-value-copy */, 9745, /* DiagArray455 */ 1329, 0, R"()")
DIAG_ENTRY(LibLTO /* liblto */, 9765, 0, 0, R"()")
DIAG_ENTRY(LinkerWarnings /* linker-warnings */, 9772, /* DiagArray457 */ 1332, 0, R"()")
DIAG_ENTRY(LiteralConversion /* literal-conversion */, 9788, /* DiagArray458 */ 1334, 0, R"()")
DIAG_ENTRY(LiteralRange /* literal-range */, 9807, /* DiagArray459 */ 1337, 0, R"()")
DIAG_ENTRY(LocalTypeTemplateArgs /* local-type-template-args */, 9821, /* DiagArray460 */ 1341, /* DiagSubGroup460 */ 360, R"()")
DIAG_ENTRY(LogicalNotParentheses /* logical-not-parentheses */, 9846, /* DiagArray461 */ 1343, 0, R"()")
DIAG_ENTRY(LogicalOpParentheses /* logical-op-parentheses */, 9870, /* DiagArray462 */ 1345, 0, R"()")
DIAG_ENTRY(LongLong /* long-long */, 9893, /* DiagArray463 */ 1347, /* DiagSubGroup463 */ 362, R"()")
DIAG_ENTRY(LoopAnalysis /* loop-analysis */, 9903, 0, /* DiagSubGroup464 */ 364, R"()")
DIAG_ENTRY(MacroRedefined /* macro-redefined */, 9917, /* DiagArray465 */ 1349, 0, R"()")
DIAG_ENTRY(Main /* main */, 9933, /* DiagArray466 */ 1351, 0, R"()")
DIAG_ENTRY(MainReturnType /* main-return-type */, 9938, /* DiagArray467 */ 1359, 0, R"()")
DIAG_ENTRY(MalformedWarningCheck /* malformed-warning-check */, 9955, /* DiagArray468 */ 1361, 0, R"()")
DIAG_ENTRY(anonymous_214 /* many-braces-around-scalar-init */, 9979, /* DiagArray469 */ 1363, 0, R"()")
DIAG_ENTRY(MaxTokens /* max-tokens */, 10010, /* DiagArray470 */ 1365, 0, R"(The warning is issued if the number of pre-processor tokens exceeds
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
DIAG_ENTRY(MaxUnsignedZero /* max-unsigned-zero */, 10021, /* DiagArray471 */ 1368, 0, R"()")
DIAG_ENTRY(MemsetTransposedArgs /* memset-transposed-args */, 10039, /* DiagArray472 */ 1370, 0, R"()")
DIAG_ENTRY(anonymous_146 /* memsize-comparison */, 10062, /* DiagArray473 */ 1372, 0, R"()")
DIAG_ENTRY(MethodSignatures /* method-signatures */, 10081, /* DiagArray474 */ 1374, 0, R"()")
DIAG_ENTRY(Microsoft /* microsoft */, 10099, 0, /* DiagSubGroup475 */ 367, R"()")
DIAG_ENTRY(MicrosoftAbstract /* microsoft-abstract */, 10109, /* DiagArray476 */ 1377, 0, R"()")
DIAG_ENTRY(MicrosoftAnonTag /* microsoft-anon-tag */, 10128, /* DiagArray477 */ 1379, 0, R"()")
DIAG_ENTRY(MicrosoftCast /* microsoft-cast */, 10147, /* DiagArray478 */ 1382, 0, R"()")
DIAG_ENTRY(MicrosoftCharize /* microsoft-charize */, 10162, /* DiagArray479 */ 1385, 0, R"()")
DIAG_ENTRY(MicrosoftCommentPaste /* microsoft-comment-paste */, 10180, /* DiagArray480 */ 1387, 0, R"()")
DIAG_ENTRY(MicrosoftConstInit /* microsoft-const-init */, 10204, /* DiagArray481 */ 1389, 0, R"()")
DIAG_ENTRY(MicrosoftCppMacro /* microsoft-cpp-macro */, 10225, /* DiagArray482 */ 1391, 0, R"()")
DIAG_ENTRY(MicrosoftDefaultArgRedefinition /* microsoft-default-arg-redefinition */, 10245, /* DiagArray483 */ 1393, 0, R"()")
DIAG_ENTRY(MicrosoftDrectveSection /* microsoft-drectve-section */, 10280, /* DiagArray484 */ 1395, 0, R"()")
DIAG_ENTRY(MicrosoftEndOfFile /* microsoft-end-of-file */, 10306, /* DiagArray485 */ 1397, 0, R"()")
DIAG_ENTRY(MicrosoftEnumForwardReference /* microsoft-enum-forward-reference */, 10328, /* DiagArray486 */ 1399, 0, R"()")
DIAG_ENTRY(MicrosoftEnumValue /* microsoft-enum-value */, 10361, /* DiagArray487 */ 1401, 0, R"()")
DIAG_ENTRY(MicrosoftExceptionSpec /* microsoft-exception-spec */, 10382, /* DiagArray488 */ 1403, 0, R"()")
DIAG_ENTRY(anonymous_125 /* microsoft-exists */, 10407, /* DiagArray489 */ 1409, 0, R"()")
DIAG_ENTRY(MicrosoftExplicitConstructorCall /* microsoft-explicit-constructor-call */, 10424, /* DiagArray490 */ 1411, 0, R"()")
DIAG_ENTRY(MicrosoftExtraQualification /* microsoft-extra-qualification */, 10460, /* DiagArray491 */ 1413, 0, R"()")
DIAG_ENTRY(MicrosoftFixedEnum /* microsoft-fixed-enum */, 10490, /* DiagArray492 */ 1415, 0, R"()")
DIAG_ENTRY(MicrosoftFlexibleArray /* microsoft-flexible-array */, 10511, /* DiagArray493 */ 1417, 0, R"()")
DIAG_ENTRY(MicrosoftGoto /* microsoft-goto */, 10536, /* DiagArray494 */ 1420, 0, R"()")
DIAG_ENTRY(MicrosoftInaccessibleBase /* microsoft-inaccessible-base */, 10551, /* DiagArray495 */ 1422, 0, R"()")
DIAG_ENTRY(MicrosoftInclude /* microsoft-include */, 10579, /* DiagArray496 */ 1424, 0, R"()")
DIAG_ENTRY(MicrosoftMutableReference /* microsoft-mutable-reference */, 10597, /* DiagArray497 */ 1426, 0, R"()")
DIAG_ENTRY(MicrosoftPureDefinition /* microsoft-pure-definition */, 10625, /* DiagArray498 */ 1428, 0, R"()")
DIAG_ENTRY(MicrosoftRedeclareStatic /* microsoft-redeclare-static */, 10651, /* DiagArray499 */ 1430, 0, R"()")
DIAG_ENTRY(MicrosoftSealed /* microsoft-sealed */, 10678, /* DiagArray500 */ 1432, 0, R"()")
DIAG_ENTRY(MicrosoftStaticAssert /* microsoft-static-assert */, 10695, /* DiagArray501 */ 1434, 0, R"()")
DIAG_ENTRY(MicrosoftTemplate /* microsoft-template */, 10719, /* DiagArray502 */ 1436, /* DiagSubGroup502 */ 398, R"()")
DIAG_ENTRY(MicrosoftTemplateShadow /* microsoft-template-shadow */, 10738, /* DiagArray503 */ 1447, 0, R"()")
DIAG_ENTRY(MicrosoftUnionMemberReference /* microsoft-union-member-reference */, 10764, /* DiagArray504 */ 1449, 0, R"()")
DIAG_ENTRY(MicrosoftUnqualifiedFriend /* microsoft-unqualified-friend */, 10797, /* DiagArray505 */ 1451, 0, R"()")
DIAG_ENTRY(MicrosoftUsingDecl /* microsoft-using-decl */, 10826, /* DiagArray506 */ 1453, 0, R"()")
DIAG_ENTRY(MicrosoftVoidPseudoDtor /* microsoft-void-pseudo-dtor */, 10847, /* DiagArray507 */ 1455, 0, R"()")
DIAG_ENTRY(MisExpect /* misexpect */, 10874, /* DiagArray508 */ 1457, 0, R"()")
DIAG_ENTRY(MisleadingIndentation /* misleading-indentation */, 10884, /* DiagArray509 */ 1459, 0, R"()")
DIAG_ENTRY(anonymous_236 /* mismatched-new-delete */, 10907, /* DiagArray510 */ 1461, 0, R"()")
DIAG_ENTRY(MismatchedParameterTypes /* mismatched-parameter-types */, 10929, /* DiagArray511 */ 1463, 0, R"()")
DIAG_ENTRY(MismatchedReturnTypes /* mismatched-return-types */, 10956, /* DiagArray512 */ 1465, 0, R"()")
DIAG_ENTRY(MismatchedTags /* mismatched-tags */, 10980, /* DiagArray513 */ 1467, 0, R"()")
DIAG_ENTRY(MissingBraces /* missing-braces */, 10996, /* DiagArray514 */ 1470, 0, R"()")
DIAG_ENTRY(anonymous_250 /* missing-constinit */, 11011, /* DiagArray515 */ 1472, 0, R"()")
DIAG_ENTRY(MissingDeclarations /* missing-declarations */, 11029, /* DiagArray516 */ 1474, 0, R"()")
DIAG_ENTRY(anonymous_167 /* missing-exception-spec */, 11050, /* DiagArray517 */ 1479, 0, R"()")
DIAG_ENTRY(MissingFieldInitializers /* missing-field-initializers */, 11073, /* DiagArray518 */ 1481, 0, R"()")
DIAG_ENTRY(anonymous_27 /* missing-format-attribute */, 11100, 0, 0, R"()")
DIAG_ENTRY(anonymous_28 /* missing-include-dirs */, 11125, 0, 0, R"()")
DIAG_ENTRY(MissingMethodReturnType /* missing-method-return-type */, 11146, /* DiagArray521 */ 1483, 0, R"()")
DIAG_ENTRY(MissingNoEscape /* missing-noescape */, 11173, /* DiagArray522 */ 1485, 0, R"()")
DIAG_ENTRY(MissingNoreturn /* missing-noreturn */, 11190, /* DiagArray523 */ 1487, 0, R"()")
DIAG_ENTRY(anonymous_186 /* missing-prototype-for-cc */, 11207, /* DiagArray524 */ 1490, 0, R"()")
DIAG_ENTRY(anonymous_203 /* missing-prototypes */, 11232, /* DiagArray525 */ 1492, 0, R"()")
DIAG_ENTRY(anonymous_126 /* missing-selector-name */, 11251, /* DiagArray526 */ 1494, 0, R"()")
DIAG_ENTRY(anonymous_80 /* missing-sysroot */, 11273, /* DiagArray527 */ 1496, 0, R"()")
DIAG_ENTRY(anonymous_204 /* missing-variable-declarations */, 11289, /* DiagArray528 */ 1498, 0, R"()")
DIAG_ENTRY(MisspelledAssumption /* misspelled-assumption */, 11319, /* DiagArray529 */ 1500, 0, R"()")
DIAG_ENTRY(ModuleBuild /* module-build */, 11341, /* DiagArray530 */ 1502, 0, R"()")
DIAG_ENTRY(ModuleConflict /* module-conflict */, 11354, /* DiagArray531 */ 1507, 0, R"()")
DIAG_ENTRY(anonymous_91 /* module-file-config-mismatch */, 11370, /* DiagArray532 */ 1510, 0, R"()")
DIAG_ENTRY(ModuleFileExtension /* module-file-extension */, 11398, /* DiagArray533 */ 1512, 0, R"()")
DIAG_ENTRY(ModuleImport /* module-import */, 11420, /* DiagArray534 */ 1514, 0, R"()")
DIAG_ENTRY(anonymous_266 /* module-import-in-extern-c */, 11434, /* DiagArray535 */ 1516, 0, R"()")
DIAG_ENTRY(ModuleIncludeDirectiveTranslation /* module-include-translation */, 11460, /* DiagArray536 */ 1518, 0, R"()")
DIAG_ENTRY(ModuleLock /* module-lock */, 11487, /* DiagArray537 */ 1520, 0, R"()")
DIAG_ENTRY(anonymous_269 /* modules-ambiguous-internal-linkage */, 11499, /* DiagArray538 */ 1522, 0, R"()")
DIAG_ENTRY(anonymous_267 /* modules-import-nested-redundant */, 11534, /* DiagArray539 */ 1524, 0, R"()")
DIAG_ENTRY(Most /* most */, 11566, 0, /* DiagSubGroup540 */ 400, R"()")
DIAG_ENTRY(Move /* move */, 11571, 0, /* DiagSubGroup541 */ 438, R"()")
DIAG_ENTRY(anonymous_67 /* msvc-include */, 11576, 0, /* DiagSubGroup542 */ 443, R"()")
DIAG_ENTRY(anonymous_84 /* msvc-not-found */, 11589, /* DiagArray543 */ 1526, 0, R"()")
DIAG_ENTRY(MultiChar /* multichar */, 11604, /* DiagArray544 */ 1528, 0, R"()")
DIAG_ENTRY(anonymous_255 /* multiple-move-vbase */, 11614, /* DiagArray545 */ 1530, 0, R"()")
DIAG_ENTRY(anonymous_18 /* narrowing */, 11634, 0, /* DiagSubGroup546 */ 445, R"()")
DIAG_ENTRY(anonymous_252 /* nested-anon-types */, 11644, /* DiagArray547 */ 1532, 0, R"()")
DIAG_ENTRY(anonymous_29 /* nested-externs */, 11662, 0, 0, R"()")
DIAG_ENTRY(OperatorNewReturnsNull /* new-returns-null */, 11677, /* DiagArray549 */ 1534, 0, R"()")
DIAG_ENTRY(NewlineEOF /* newline-eof */, 11694, /* DiagArray550 */ 1536, 0, R"()")
DIAG_ENTRY(NoDeref /* noderef */, 11706, /* DiagArray551 */ 1539, 0, R"()")
DIAG_ENTRY(NoexceptType /* noexcept-type */, 11714, 0, /* DiagSubGroup552 */ 447, R"()")
DIAG_ENTRY(anonymous_149 /* non-c-typedef-for-linkage */, 11728, /* DiagArray553 */ 1543, 0, R"()")
DIAG_ENTRY(NonGCC /* non-gcc */, 11754, 0, /* DiagSubGroup554 */ 449, R"()")
DIAG_ENTRY(NonLiteralNullConversion /* non-literal-null-conversion */, 11762, /* DiagArray555 */ 1545, 0, R"()")
DIAG_ENTRY(NonModularIncludeInFrameworkModule /* non-modular-include-in-framework-module */, 11790, /* DiagArray556 */ 1547, 0, R"()")
DIAG_ENTRY(NonModularIncludeInModule /* non-modular-include-in-module */, 11830, /* DiagArray557 */ 1549, /* DiagSubGroup557 */ 453, R"()")
DIAG_ENTRY(NonPODVarargs /* non-pod-varargs */, 11860, /* DiagArray558 */ 1551, 0, R"()")
DIAG_ENTRY(anonymous_177 /* non-power-of-two-alignment */, 11876, /* DiagArray559 */ 1556, 0, R"()")
DIAG_ENTRY(NonVirtualDtor /* non-virtual-dtor */, 11903, /* DiagArray560 */ 1558, 0, R"()")
DIAG_ENTRY(NonNull /* nonnull */, 11920, /* DiagArray561 */ 1560, 0, R"()")
DIAG_ENTRY(anonymous_31 /* nonportable-cfstrings */, 11928, 0, 0, R"()")
DIAG_ENTRY(anonymous_104 /* nonportable-include-path */, 11950, /* DiagArray563 */ 1563, 0, R"()")
DIAG_ENTRY(anonymous_105 /* nonportable-system-include-path */, 11975, /* DiagArray564 */ 1565, 0, R"()")
DIAG_ENTRY(anonymous_261 /* nonportable-vector-initialization */, 12007, /* DiagArray565 */ 1567, 0, R"()")
DIAG_ENTRY(NonTrivialMemaccess /* nontrivial-memaccess */, 12041, /* DiagArray566 */ 1569, 0, R"()")
DIAG_ENTRY(NSConsumedMismatch /* nsconsumed-mismatch */, 12062, /* DiagArray567 */ 1571, 0, R"()")
DIAG_ENTRY(NSReturnsMismatch /* nsreturns-mismatch */, 12082, /* DiagArray568 */ 1573, 0, R"()")
DIAG_ENTRY(NullArithmetic /* null-arithmetic */, 12101, /* DiagArray569 */ 1575, 0, R"()")
DIAG_ENTRY(NullCharacter /* null-character */, 12117, /* DiagArray570 */ 1578, 0, R"()")
DIAG_ENTRY(NullConversion /* null-conversion */, 12132, /* DiagArray571 */ 1581, 0, R"()")
DIAG_ENTRY(NullDereference /* null-dereference */, 12148, /* DiagArray572 */ 1583, 0, R"()")
DIAG_ENTRY(NullPointerArithmetic /* null-pointer-arithmetic */, 12165, /* DiagArray573 */ 1586, /* DiagSubGroup573 */ 455, R"()")
DIAG_ENTRY(NullPointerSubtraction /* null-pointer-subtraction */, 12189, /* DiagArray574 */ 1588, 0, R"()")
DIAG_ENTRY(Nullability /* nullability */, 12214, /* DiagArray575 */ 1590, 0, R"()")
DIAG_ENTRY(NullabilityCompleteness /* nullability-completeness */, 12226, /* DiagArray576 */ 1596, /* DiagSubGroup576 */ 457, R"()")
DIAG_ENTRY(NullabilityCompletenessOnArrays /* nullability-completeness-on-arrays */, 12251, /* DiagArray577 */ 1598, 0, R"()")
DIAG_ENTRY(NullabilityDeclSpec /* nullability-declspec */, 12286, /* DiagArray578 */ 1600, 0, R"()")
DIAG_ENTRY(anonymous_121 /* nullability-extension */, 12307, /* DiagArray579 */ 1602, 0, R"()")
DIAG_ENTRY(NullabilityInferredOnNestedType /* nullability-inferred-on-nested-type */, 12329, /* DiagArray580 */ 1604, 0, R"()")
DIAG_ENTRY(NullableToNonNullConversion /* nullable-to-nonnull-conversion */, 12365, /* DiagArray581 */ 1606, 0, R"()")
DIAG_ENTRY(anonymous_158 /* objc-autosynthesis-property-ivar-name-match */, 12396, /* DiagArray582 */ 1608, 0, R"()")
DIAG_ENTRY(ObjCBoolConstantConversion /* objc-bool-constant-conversion */, 12440, /* DiagArray583 */ 1610, 0, R"()")
DIAG_ENTRY(ObjCBoxing /* objc-boxing */, 12470, /* DiagArray584 */ 1612, 0, R"()")
DIAG_ENTRY(anonymous_233 /* objc-circular-container */, 12482, /* DiagArray585 */ 1614, 0, R"()")
DIAG_ENTRY(ObjCCocoaAPI /* objc-cocoa-api */, 12506, 0, /* DiagSubGroup586 */ 459, R"()")
DIAG_ENTRY(ObjCDesignatedInit /* objc-designated-initializers */, 12521, /* DiagArray587 */ 1616, 0, R"()")
DIAG_ENTRY(anonymous_174 /* objc-dictionary-duplicate-keys */, 12550, /* DiagArray588 */ 1623, 0, R"()")
DIAG_ENTRY(ObjCFlexibleArray /* objc-flexible-array */, 12581, /* DiagArray589 */ 1625, 0, R"()")
DIAG_ENTRY(anonymous_210 /* objc-forward-class-redefinition */, 12601, /* DiagArray590 */ 1628, 0, R"()")
DIAG_ENTRY(anonymous_211 /* objc-interface-ivars */, 12633, /* DiagArray591 */ 1630, 0, R"()")
DIAG_ENTRY(ObjCLiteralComparison /* objc-literal-compare */, 12654, /* DiagArray592 */ 1632, /* DiagSubGroup592 */ 461, R"()")
DIAG_ENTRY(ObjCLiteralConversion /* objc-literal-conversion */, 12675, /* DiagArray593 */ 1634, 0, R"()")
DIAG_ENTRY(anonymous_112 /* objc-macro-redefinition */, 12699, /* DiagArray594 */ 1637, 0, R"()")
DIAG_ENTRY(anonymous_163 /* objc-messaging-id */, 12723, /* DiagArray595 */ 1639, 0, R"()")
DIAG_ENTRY(MethodAccess /* objc-method-access */, 12741, /* DiagArray596 */ 1641, 0, R"()")
DIAG_ENTRY(anonymous_159 /* objc-missing-property-synthesis */, 12760, /* DiagArray597 */ 1648, 0, R"()")
DIAG_ENTRY(ObjCMissingSuperCalls /* objc-missing-super-calls */, 12792, /* DiagArray598 */ 1650, 0, R"()")
DIAG_ENTRY(ObjCMultipleMethodNames /* objc-multiple-method-names */, 12817, /* DiagArray599 */ 1652, 0, R"()")
DIAG_ENTRY(ObjCRetainBlockProperty /* objc-noncopy-retain-block-property */, 12844, /* DiagArray600 */ 1654, 0, R"()")
DIAG_ENTRY(ObjCNonUnifiedException /* objc-nonunified-exceptions */, 12879, /* DiagArray601 */ 1656, 0, R"()")
DIAG_ENTRY(ObjCPropertyAssignOnObjectType /* objc-property-assign-on-object-type */, 12906, /* DiagArray602 */ 1658, 0, R"()")
DIAG_ENTRY(ObjCPropertyImpl /* objc-property-implementation */, 12942, /* DiagArray603 */ 1660, 0, R"()")
DIAG_ENTRY(anonymous_154 /* objc-property-implicit-mismatch */, 12971, /* DiagArray604 */ 1665, 0, R"()")
DIAG_ENTRY(anonymous_156 /* objc-property-matches-cocoa-ownership-rule */, 13003, /* DiagArray605 */ 1667, 0, R"()")
DIAG_ENTRY(ObjCPropertyNoAttribute /* objc-property-no-attribute */, 13046, /* DiagArray606 */ 1669, 0, R"()")
DIAG_ENTRY(ObjCNoPropertyAutoSynthesis /* objc-property-synthesis */, 13073, /* DiagArray607 */ 1672, 0, R"()")
DIAG_ENTRY(ObjCProtocolMethodImpl /* objc-protocol-method-implementation */, 13097, /* DiagArray608 */ 1676, 0, R"()")
DIAG_ENTRY(anonymous_157 /* objc-protocol-property-synthesis */, 13133, /* DiagArray609 */ 1678, 0, R"()")
DIAG_ENTRY(ObjCProtocolQualifiers /* objc-protocol-qualifiers */, 13166, /* DiagArray610 */ 1680, 0, R"()")
DIAG_ENTRY(ObjCReadonlyPropertyHasSetter /* objc-readonly-with-setter-property */, 13191, /* DiagArray611 */ 1682, 0, R"()")
DIAG_ENTRY(ObjCRedundantAPIUse /* objc-redundant-api-use */, 13226, 0, /* DiagSubGroup612 */ 463, R"()")
DIAG_ENTRY(ObjCRedundantLiteralUse /* objc-redundant-literal-use */, 13249, /* DiagArray613 */ 1684, 0, R"()")
DIAG_ENTRY(ObjCRootClass /* objc-root-class */, 13276, /* DiagArray614 */ 1686, 0, R"()")
DIAG_ENTRY(ObjCSignedCharBool /* objc-signed-char-bool */, 13292, 0, /* DiagSubGroup615 */ 465, R"()")
DIAG_ENTRY(ObjCSignedCharBoolImplicitFloatConversion /* objc-signed-char-bool-implicit-float-conversion */, 13314, /* DiagArray616 */ 1688, 0, R"()")
DIAG_ENTRY(ObjCSignedCharBoolImplicitIntConversion /* objc-signed-char-bool-implicit-int-conversion */, 13362, /* DiagArray617 */ 1690, 0, R"()")
DIAG_ENTRY(ObjCStringComparison /* objc-string-compare */, 13408, /* DiagArray618 */ 1692, 0, R"()")
DIAG_ENTRY(ObjCStringConcatenation /* objc-string-concatenation */, 13428, /* DiagArray619 */ 1694, 0, R"()")
DIAG_ENTRY(anonymous_234 /* objc-unsafe-perform-selector */, 13454, /* DiagArray620 */ 1696, 0, R"()")
DIAG_ENTRY(ODR /* odr */, 13483, /* DiagArray621 */ 1698, 0, R"()")
DIAG_ENTRY(OldStyleCast /* old-style-cast */, 13487, /* DiagArray622 */ 1717, 0, R"()")
DIAG_ENTRY(anonymous_33 /* old-style-definition */, 13502, 0, 0, R"()")
DIAG_ENTRY(OpenCLUnsupportedRGBA /* opencl-unsupported-rgba */, 13523, /* DiagArray624 */ 1719, 0, R"()")
DIAG_ENTRY(OpenMP /* openmp */, 13547, 0, /* DiagSubGroup625 */ 470, R"()")
DIAG_ENTRY(OpenMP51Ext /* openmp-51-extensions */, 13554, /* DiagArray626 */ 1721, 0, R"()")
DIAG_ENTRY(OpenMPClauses /* openmp-clauses */, 13575, /* DiagArray627 */ 1723, 0, R"()")
DIAG_ENTRY(OpenMPLoopForm /* openmp-loop-form */, 13590, /* DiagArray628 */ 1743, 0, R"()")
DIAG_ENTRY(OpenMPMapping /* openmp-mapping */, 13607, /* DiagArray629 */ 1746, 0, R"()")
DIAG_ENTRY(OpenMPTarget /* openmp-target */, 13622, /* DiagArray630 */ 1748, /* DiagSubGroup630 */ 477, R"()")
DIAG_ENTRY(OptionIgnored /* option-ignored */, 13636, /* DiagArray631 */ 1752, 0, R"()")
DIAG_ENTRY(OrderedCompareFunctionPointers /* ordered-compare-function-pointers */, 13651, /* DiagArray632 */ 1765, 0, R"()")
DIAG_ENTRY(OutOfLineDeclaration /* out-of-line-declaration */, 13685, /* DiagArray633 */ 1768, 0, R"()")
DIAG_ENTRY(anonymous_141 /* out-of-scope-function */, 13709, /* DiagArray634 */ 1770, 0, R"()")
DIAG_ENTRY(OveralignedType /* over-aligned */, 13731, /* DiagArray635 */ 1772, 0, R"()")
DIAG_ENTRY(anonymous_34 /* overflow */, 13744, 0, 0, R"()")
DIAG_ENTRY(OverlengthStrings /* overlength-strings */, 13753, /* DiagArray637 */ 1774, 0, R"()")
DIAG_ENTRY(OverloadedShiftOpParentheses /* overloaded-shift-op-parentheses */, 13772, /* DiagArray638 */ 1776, 0, R"()")
DIAG_ENTRY(OverloadedVirtual /* overloaded-virtual */, 13804, /* DiagArray639 */ 1778, 0, R"()")
DIAG_ENTRY(anonymous_30 /* override-init */, 13823, 0, /* DiagSubGroup640 */ 479, R"()")
DIAG_ENTRY(anonymous_88 /* override-module */, 13837, /* DiagArray641 */ 1780, 0, R"()")
DIAG_ENTRY(OverridingMethodMismatch /* overriding-method-mismatch */, 13853, /* DiagArray642 */ 1782, 0, R"()")
DIAG_ENTRY(anonymous_79 /* overriding-t-option */, 13880, /* DiagArray643 */ 1790, 0, R"()")
DIAG_ENTRY(Packed /* packed */, 13900, /* DiagArray644 */ 1792, 0, R"()")
DIAG_ENTRY(Padded /* padded */, 13907, /* DiagArray645 */ 1794, 0, R"()")
DIAG_ENTRY(Parentheses /* parentheses */, 13914, /* DiagArray646 */ 1798, /* DiagSubGroup646 */ 481, R"()")
DIAG_ENTRY(ParenthesesOnEquality /* parentheses-equality */, 13926, /* DiagArray647 */ 1802, 0, R"()")
DIAG_ENTRY(anonymous_6 /* partial-availability */, 13947, 0, /* DiagSubGroup648 */ 490, R"()")
DIAG_ENTRY(BackendOptimizationRemark /* pass */, 13968, /* DiagArray649 */ 1804, 0, R"()")
DIAG_ENTRY(BackendOptimizationRemarkAnalysis /* pass-analysis */, 13973, /* DiagArray650 */ 1806, 0, R"()")
DIAG_ENTRY(BackendOptimizationFailure /* pass-failed */, 13987, /* DiagArray651 */ 1810, 0, R"()")
DIAG_ENTRY(BackendOptimizationRemarkMissed /* pass-missed */, 13999, /* DiagArray652 */ 1812, 0, R"()")
DIAG_ENTRY(anonymous_272 /* pch-date-time */, 14011, /* DiagArray653 */ 1814, 0, R"()")
DIAG_ENTRY(Pedantic /* pedantic */, 14025, /* DiagArray654 */ 1816, /* DiagSubGroup654 */ 492, R"()")
DIAG_ENTRY(OpenCLCoreFeaturesDiagGroup /* pedantic-core-features */, 14034, /* DiagArray655 */ 1891, 0, R"()")
DIAG_ENTRY(PedanticMacros /* pedantic-macros */, 14057, 0, /* DiagSubGroup656 */ 552, R"()")
DIAG_ENTRY(PessimizingMove /* pessimizing-move */, 14073, /* DiagArray657 */ 1894, 0, R"()")
DIAG_ENTRY(PointerArith /* pointer-arith */, 14090, /* DiagArray658 */ 1897, /* DiagSubGroup658 */ 558, R"()")
DIAG_ENTRY(PointerBoolConversion /* pointer-bool-conversion */, 14104, /* DiagArray659 */ 1901, 0, R"()")
DIAG_ENTRY(anonymous_189 /* pointer-compare */, 14128, /* DiagArray660 */ 1904, 0, R"()")
DIAG_ENTRY(anonymous_232 /* pointer-integer-compare */, 14144, /* DiagArray661 */ 1906, 0, R"()")
DIAG_ENTRY(anonymous_242 /* pointer-sign */, 14168, /* DiagArray662 */ 1908, 0, R"()")
DIAG_ENTRY(PointerToEnumCast /* pointer-to-enum-cast */, 14181, /* DiagArray663 */ 1910, /* DiagSubGroup663 */ 560, R"()")
DIAG_ENTRY(PointerToIntCast /* pointer-to-int-cast */, 14202, /* DiagArray664 */ 1912, /* DiagSubGroup664 */ 562, R"()")
DIAG_ENTRY(anonymous_247 /* pointer-type-mismatch */, 14222, /* DiagArray665 */ 1914, 0, R"()")
DIAG_ENTRY(anonymous_77 /* poison-system-directories */, 14244, /* DiagArray666 */ 1916, 0, R"()")
DIAG_ENTRY(ObjCPotentiallyDirectSelector /* potentially-direct-selector */, 14270, /* DiagArray667 */ 1918, 0, R"()")
DIAG_ENTRY(PotentiallyEvaluatedExpression /* potentially-evaluated-expression */, 14298, /* DiagArray668 */ 1920, 0, R"()")
DIAG_ENTRY(PragmaClangAttribute /* pragma-clang-attribute */, 14331, /* DiagArray669 */ 1922, 0, R"()")
DIAG_ENTRY(anonymous_106 /* pragma-once-outside-header */, 14354, /* DiagArray670 */ 1924, 0, R"()")
DIAG_ENTRY(PragmaPack /* pragma-pack */, 14381, /* DiagArray671 */ 1926, /* DiagSubGroup671 */ 565, R"()")
DIAG_ENTRY(PragmaPackSuspiciousInclude /* pragma-pack-suspicious-include */, 14393, /* DiagArray672 */ 1929, 0, R"()")
DIAG_ENTRY(anonymous_107 /* pragma-system-header-outside-header */, 14424, /* DiagArray673 */ 1931, 0, R"()")
DIAG_ENTRY(Pragmas /* pragmas */, 14460, /* DiagArray674 */ 1933, /* DiagSubGroup674 */ 567, R"()")
DIAG_ENTRY(CXXPre14Compat /* pre-c++14-compat */, 14468, /* DiagArray675 */ 1936, 0, R"()")
DIAG_ENTRY(CXXPre14CompatPedantic /* pre-c++14-compat-pedantic */, 14485, 0, /* DiagSubGroup676 */ 572, R"()")
DIAG_ENTRY(CXXPre17Compat /* pre-c++17-compat */, 14511, /* DiagArray677 */ 1948, 0, R"()")
DIAG_ENTRY(CXXPre17CompatPedantic /* pre-c++17-compat-pedantic */, 14528, /* DiagArray678 */ 1966, /* DiagSubGroup678 */ 575, R"()")
DIAG_ENTRY(CXXPre20Compat /* pre-c++20-compat */, 14554, /* DiagArray679 */ 1969, 0, R"()")
DIAG_ENTRY(CXXPre20CompatPedantic /* pre-c++20-compat-pedantic */, 14571, /* DiagArray680 */ 1997, /* DiagSubGroup680 */ 577, R"()")
DIAG_ENTRY(CXXPre2bCompat /* pre-c++2b-compat */, 14597, /* DiagArray681 */ 2000, 0, R"()")
DIAG_ENTRY(CXXPre2bCompatPedantic /* pre-c++2b-compat-pedantic */, 14614, 0, /* DiagSubGroup682 */ 579, R"()")
DIAG_ENTRY(CPre2xCompat /* pre-c2x-compat */, 14640, /* DiagArray683 */ 2015, 0, R"()")
DIAG_ENTRY(CPre2xCompatPedantic /* pre-c2x-compat-pedantic */, 14655, 0, /* DiagSubGroup684 */ 581, R"()")
DIAG_ENTRY(OpenMPPre51Compat /* pre-openmp-51-compat */, 14679, /* DiagArray685 */ 2024, 0, R"()")
DIAG_ENTRY(anonymous_138 /* predefined-identifier-outside-function */, 14700, /* DiagArray686 */ 2026, 0, R"()")
DIAG_ENTRY(PrivateExtern /* private-extern */, 14739, /* DiagArray687 */ 2028, 0, R"()")
DIAG_ENTRY(anonymous_118 /* private-header */, 14754, /* DiagArray688 */ 2030, 0, R"()")
DIAG_ENTRY(PrivateModule /* private-module */, 14769, /* DiagArray689 */ 2032, 0, R"()")
DIAG_ENTRY(ProfileInstrMissing /* profile-instr-missing */, 14784, /* DiagArray690 */ 2037, 0, R"()")
DIAG_ENTRY(ProfileInstrOutOfDate /* profile-instr-out-of-date */, 14806, /* DiagArray691 */ 2039, 0, R"()")
DIAG_ENTRY(ProfileInstrUnprofiled /* profile-instr-unprofiled */, 14832, /* DiagArray692 */ 2041, 0, R"()")
DIAG_ENTRY(PropertyAccessDotSyntax /* property-access-dot-syntax */, 14857, /* DiagArray693 */ 2043, 0, R"()")
DIAG_ENTRY(PropertyAttr /* property-attribute-mismatch */, 14884, /* DiagArray694 */ 2045, 0, R"()")
DIAG_ENTRY(Protocol /* protocol */, 14912, /* DiagArray695 */ 2050, 0, R"()")
DIAG_ENTRY(anonymous_152 /* protocol-property-synthesis-ambiguity */, 14921, /* DiagArray696 */ 2052, 0, R"()")
DIAG_ENTRY(anonymous_92 /* psabi */, 14959, /* DiagArray697 */ 2054, 0, R"()")
DIAG_ENTRY(anonymous_216 /* qualified-void-return-type */, 14965, /* DiagArray698 */ 2056, 0, R"()")
DIAG_ENTRY(FrameworkHdrQuotedInclude /* quoted-include-in-framework-header */, 14992, /* DiagArray699 */ 2058, 0, R"()")
DIAG_ENTRY(RangeLoopAnalysis /* range-loop-analysis */, 15027, 0, /* DiagSubGroup700 */ 583, R"()")
DIAG_ENTRY(RangeLoopBindReference /* range-loop-bind-reference */, 15047, /* DiagArray701 */ 2060, 0, R"()")
DIAG_ENTRY(RangeLoopConstruct /* range-loop-construct */, 15073, /* DiagArray702 */ 2062, 0, R"()")
DIAG_ENTRY(anonymous_162 /* readonly-iboutlet-property */, 15094, /* DiagArray703 */ 2065, 0, R"()")
DIAG_ENTRY(ObjCReceiver /* receiver-expr */, 15121, /* DiagArray704 */ 2067, 0, R"()")
DIAG_ENTRY(ForwardClassReceiver /* receiver-forward-class */, 15135, /* DiagArray705 */ 2069, 0, R"()")
DIAG_ENTRY(RedeclaredClassMember /* redeclared-class-member */, 15158, /* DiagArray706 */ 2072, 0, R"()")
DIAG_ENTRY(anonymous_164 /* redundant-consteval-if */, 15182, /* DiagArray707 */ 2074, 0, R"()")
DIAG_ENTRY(anonymous_35 /* redundant-decls */, 15205, 0, 0, R"()")
DIAG_ENTRY(RedundantMove /* redundant-move */, 15221, /* DiagArray709 */ 2076, 0, R"()")
DIAG_ENTRY(anonymous_140 /* redundant-parens */, 15236, /* DiagArray710 */ 2078, 0, R"()")
DIAG_ENTRY(Register /* register */, 15253, /* DiagArray711 */ 2080, /* DiagSubGroup711 */ 586, R"()")
DIAG_ENTRY(ReinterpretBaseClass /* reinterpret-base-class */, 15262, /* DiagArray712 */ 2082, 0, R"()")
DIAG_ENTRY(RemarkBackendPlugin /* remark-backend-plugin */, 15285, /* DiagArray713 */ 2084, 0, R"()")
DIAG_ENTRY(Reorder /* reorder */, 15307, 0, /* DiagSubGroup714 */ 588, R"()")
DIAG_ENTRY(ReorderCtor /* reorder-ctor */, 15315, /* DiagArray715 */ 2086, 0, R"()")
DIAG_ENTRY(ReorderInitList /* reorder-init-list */, 15328, /* DiagArray716 */ 2089, 0, R"()")
DIAG_ENTRY(anonymous_195 /* requires-super-attribute */, 15346, /* DiagArray717 */ 2091, 0, R"()")
DIAG_ENTRY(ReservedIdAsMacroAlias /* reserved-id-macro */, 15371, 0, /* DiagSubGroup718 */ 591, R"()")
DIAG_ENTRY(ReservedIdentifier /* reserved-identifier */, 15389, /* DiagArray719 */ 2093, /* DiagSubGroup719 */ 593, R"()")
DIAG_ENTRY(ReservedIdAsMacro /* reserved-macro-identifier */, 15409, /* DiagArray720 */ 2095, 0, R"()")
DIAG_ENTRY(ReservedUserDefinedLiteral /* reserved-user-defined-literal */, 15435, /* DiagArray721 */ 2097, /* DiagSubGroup721 */ 595, R"()")
DIAG_ENTRY(RestrictExpansionMacro /* restrict-expansion */, 15465, /* DiagArray722 */ 2100, 0, R"()")
DIAG_ENTRY(anonymous_209 /* retained-language-linkage */, 15484, /* DiagArray723 */ 2102, 0, R"()")
DIAG_ENTRY(ReturnStackAddress /* return-stack-address */, 15510, /* DiagArray724 */ 2104, 0, R"()")
DIAG_ENTRY(ReturnStdMove /* return-std-move */, 15531, 0, 0, R"()")
DIAG_ENTRY(ReturnType /* return-type */, 15547, /* DiagArray726 */ 2108, /* DiagSubGroup726 */ 597, R"()")
DIAG_ENTRY(ReturnTypeCLinkage /* return-type-c-linkage */, 15559, /* DiagArray727 */ 2118, 0, R"()")
DIAG_ENTRY(anonymous_197 /* rewrite-not-bool */, 15581, /* DiagArray728 */ 2121, 0, R"()")
DIAG_ENTRY(RoundTripCC1Args /* round-trip-cc1-args */, 15598, /* DiagArray729 */ 2123, 0, R"()")
DIAG_ENTRY(RTTI /* rtti */, 15618, /* DiagArray730 */ 2125, 0, R"()")
DIAG_ENTRY(SanitizeAddressRemarks /* sanitize-address */, 15623, /* DiagArray731 */ 2128, 0, R"()")
DIAG_ENTRY(anonymous_87 /* sarif-format-unstable */, 15640, /* DiagArray732 */ 2131, 0, R"()")
DIAG_ENTRY(UsedSearchPath /* search-path-usage */, 15662, /* DiagArray733 */ 2133, 0, R"()")
DIAG_ENTRY(Section /* section */, 15680, /* DiagArray734 */ 2135, 0, R"()")
DIAG_ENTRY(Selector /* selector */, 15688, /* DiagArray735 */ 2139, /* DiagSubGroup735 */ 599, R"()")
DIAG_ENTRY(SelectorTypeMismatch /* selector-type-mismatch */, 15697, /* DiagArray736 */ 2141, 0, R"()")
DIAG_ENTRY(SelfAssignment /* self-assign */, 15720, /* DiagArray737 */ 2143, /* DiagSubGroup737 */ 601, R"()")
DIAG_ENTRY(SelfAssignmentField /* self-assign-field */, 15732, /* DiagArray738 */ 2145, 0, R"()")
DIAG_ENTRY(SelfAssignmentOverloaded /* self-assign-overloaded */, 15750, /* DiagArray739 */ 2147, 0, R"()")
DIAG_ENTRY(SelfMove /* self-move */, 15773, /* DiagArray740 */ 2149, 0, R"()")
DIAG_ENTRY(SemiBeforeMethodBody /* semicolon-before-method-body */, 15783, /* DiagArray741 */ 2151, 0, R"()")
DIAG_ENTRY(Sentinel /* sentinel */, 15812, /* DiagArray742 */ 2153, 0, R"()")
DIAG_ENTRY(anonymous_39 /* sequence-point */, 15821, 0, /* DiagSubGroup743 */ 604, R"()")
DIAG_ENTRY(SerializedDiagnostics /* serialized-diagnostics */, 15836, /* DiagArray744 */ 2156, 0, R"()")
DIAG_ENTRY(Shadow /* shadow */, 15859, /* DiagArray745 */ 2160, /* DiagSubGroup745 */ 606, R"()")
DIAG_ENTRY(ShadowAll /* shadow-all */, 15866, 0, /* DiagSubGroup746 */ 609, R"()")
DIAG_ENTRY(ShadowField /* shadow-field */, 15877, /* DiagArray747 */ 2162, 0, R"()")
DIAG_ENTRY(ShadowFieldInConstructor /* shadow-field-in-constructor */, 15890, /* DiagArray748 */ 2164, /* DiagSubGroup748 */ 614, R"()")
DIAG_ENTRY(ShadowFieldInConstructorModified /* shadow-field-in-constructor-modified */, 15918, /* DiagArray749 */ 2166, 0, R"()")
DIAG_ENTRY(ShadowIvar /* shadow-ivar */, 15955, /* DiagArray750 */ 2168, 0, R"()")
DIAG_ENTRY(ShadowUncapturedLocal /* shadow-uncaptured-local */, 15967, /* DiagArray751 */ 2170, 0, R"()")
DIAG_ENTRY(anonymous_221 /* shift-count-negative */, 15991, /* DiagArray752 */ 2172, 0, R"()")
DIAG_ENTRY(anonymous_222 /* shift-count-overflow */, 16012, /* DiagArray753 */ 2174, 0, R"()")
DIAG_ENTRY(anonymous_220 /* shift-negative-value */, 16033, /* DiagArray754 */ 2176, 0, R"()")
DIAG_ENTRY(ShiftOpParentheses /* shift-op-parentheses */, 16054, /* DiagArray755 */ 2178, 0, R"()")
DIAG_ENTRY(anonymous_223 /* shift-overflow */, 16075, /* DiagArray756 */ 2180, 0, R"()")
DIAG_ENTRY(anonymous_224 /* shift-sign-overflow */, 16090, /* DiagArray757 */ 2182, 0, R"()")
DIAG_ENTRY(Shorten64To32 /* shorten-64-to-32 */, 16110, /* DiagArray758 */ 2184, 0, R"()")
DIAG_ENTRY(SignCompare /* sign-compare */, 16127, /* DiagArray759 */ 2186, 0, R"()")
DIAG_ENTRY(SignConversion /* sign-conversion */, 16140, /* DiagArray760 */ 2188, 0, R"()")
DIAG_ENTRY(anonymous_36 /* sign-promo */, 16156, 0, 0, R"()")
DIAG_ENTRY(SignedEnumBitfield /* signed-enum-bitfield */, 16167, /* DiagArray762 */ 2192, 0, R"()")
DIAG_ENTRY(anonymous_263 /* signed-unsigned-wchar */, 16188, /* DiagArray763 */ 2194, 0, R"()")
DIAG_ENTRY(SingleBitBitFieldConstantConversion /* single-bit-bitfield-constant-conversion */, 16210, /* DiagArray764 */ 2196, 0, R"()")
DIAG_ENTRY(SizeofArrayArgument /* sizeof-array-argument */, 16250, /* DiagArray765 */ 2198, 0, R"()")
DIAG_ENTRY(SizeofArrayDecay /* sizeof-array-decay */, 16272, /* DiagArray766 */ 2200, 0, R"()")
DIAG_ENTRY(anonymous_191 /* sizeof-array-div */, 16291, /* DiagArray767 */ 2202, 0, R"()")
DIAG_ENTRY(anonymous_190 /* sizeof-pointer-div */, 16308, /* DiagArray768 */ 2204, 0, R"()")
DIAG_ENTRY(SizeofPointerMemaccess /* sizeof-pointer-memaccess */, 16327, /* DiagArray769 */ 2206, 0, R"()")
DIAG_ENTRY(anonymous_83 /* slash-u-filename */, 16352, /* DiagArray770 */ 2209, 0, R"()")
DIAG_ENTRY(anonymous_76 /* slh-asm-goto */, 16369, /* DiagArray771 */ 2211, 0, R"()")
DIAG_ENTRY(UninitializedSometimes /* sometimes-uninitialized */, 16382, /* DiagArray772 */ 2213, 0, R"()")
DIAG_ENTRY(BackendSourceMgr /* source-mgr */, 16406, /* DiagArray773 */ 2215, 0, R"()")
DIAG_ENTRY(SourceUsesOpenMP /* source-uses-openmp */, 16417, /* DiagArray774 */ 2217, 0, R"()")
DIAG_ENTRY(SpirCompat /* spir-compat */, 16436, /* DiagArray775 */ 2226, 0, R"()")
DIAG_ENTRY(anonymous_69 /* spirv-compat */, 16448, 0, /* DiagSubGroup776 */ 616, R"()")
DIAG_ENTRY(anonymous_74 /* stack-exhausted */, 16461, /* DiagArray777 */ 2228, 0, R"()")
DIAG_ENTRY(anonymous_75 /* stack-protector */, 16477, /* DiagArray778 */ 2230, 0, R"()")
DIAG_ENTRY(StaticFloatInit /* static-float-init */, 16493, /* DiagArray779 */ 2232, /* DiagSubGroup779 */ 618, R"()")
DIAG_ENTRY(StaticInInline /* static-in-inline */, 16511, /* DiagArray780 */ 2234, 0, R"()")
DIAG_ENTRY(anonymous_133 /* static-inline-explicit-instantiation */, 16528, /* DiagArray781 */ 2237, 0, R"()")
DIAG_ENTRY(StaticLocalInInline /* static-local-in-inline */, 16565, /* DiagArray782 */ 2239, 0, R"()")
DIAG_ENTRY(UninitializedStaticSelfInit /* static-self-init */, 16588, /* DiagArray783 */ 2241, 0, R"()")
DIAG_ENTRY(anonymous_86 /* stdlibcxx-not-found */, 16605, /* DiagArray784 */ 2243, 0, R"()")
DIAG_ENTRY(anonymous_43 /* strict-aliasing */, 16625, 0, 0, R"()")
DIAG_ENTRY(anonymous_40 /* strict-aliasing=0 */, 16641, 0, 0, R"()")
DIAG_ENTRY(anonymous_41 /* strict-aliasing=1 */, 16659, 0, 0, R"()")
DIAG_ENTRY(anonymous_42 /* strict-aliasing=2 */, 16677, 0, 0, R"()")
DIAG_ENTRY(anonymous_50 /* strict-overflow */, 16695, 0, 0, R"()")
DIAG_ENTRY(anonymous_44 /* strict-overflow=0 */, 16711, 0, 0, R"()")
DIAG_ENTRY(anonymous_45 /* strict-overflow=1 */, 16729, 0, 0, R"()")
DIAG_ENTRY(anonymous_46 /* strict-overflow=2 */, 16747, 0, 0, R"()")
DIAG_ENTRY(anonymous_47 /* strict-overflow=3 */, 16765, 0, 0, R"()")
DIAG_ENTRY(anonymous_48 /* strict-overflow=4 */, 16783, 0, 0, R"()")
DIAG_ENTRY(anonymous_49 /* strict-overflow=5 */, 16801, 0, 0, R"()")
DIAG_ENTRY(ObjCStrictPotentiallyDirectSelector /* strict-potentially-direct-selector */, 16819, /* DiagArray796 */ 2245, /* DiagSubGroup796 */ 620, R"()")
DIAG_ENTRY(StrictPrototypes /* strict-prototypes */, 16854, /* DiagArray797 */ 2247, /* DiagSubGroup797 */ 622, R"()")
DIAG_ENTRY(StrictSelector /* strict-selector-match */, 16872, /* DiagArray798 */ 2249, 0, R"()")
DIAG_ENTRY(StringCompare /* string-compare */, 16894, /* DiagArray799 */ 2251, 0, R"()")
DIAG_ENTRY(StringConcatation /* string-concatenation */, 16909, /* DiagArray800 */ 2253, 0, R"()")
DIAG_ENTRY(StringConversion /* string-conversion */, 16930, /* DiagArray801 */ 2255, 0, R"()")
DIAG_ENTRY(StringPlusChar /* string-plus-char */, 16948, /* DiagArray802 */ 2257, 0, R"()")
DIAG_ENTRY(StringPlusInt /* string-plus-int */, 16965, /* DiagArray803 */ 2259, 0, R"()")
DIAG_ENTRY(anonymous_145 /* strlcpy-strlcat-size */, 16981, /* DiagArray804 */ 2261, 0, R"()")
DIAG_ENTRY(StrncatSize /* strncat-size */, 17002, /* DiagArray805 */ 2263, 0, R"()")
DIAG_ENTRY(CXX11WarnSuggestOverrideDestructor /* suggest-destructor-override */, 17015, /* DiagArray806 */ 2267, 0, R"()")
DIAG_ENTRY(CXX11WarnSuggestOverride /* suggest-override */, 17043, /* DiagArray807 */ 2269, 0, R"()")
DIAG_ENTRY(SuperSubClassMismatch /* super-class-method-mismatch */, 17060, /* DiagArray808 */ 2271, 0, R"()")
DIAG_ENTRY(SuspiciousBzero /* suspicious-bzero */, 17088, /* DiagArray809 */ 2273, 0, R"()")
DIAG_ENTRY(SuspiciousMemaccess /* suspicious-memaccess */, 17105, 0, /* DiagSubGroup810 */ 624, R"()")
DIAG_ENTRY(SwiftNameAttribute /* swift-name-attribute */, 17126, /* DiagArray811 */ 2275, 0, R"()")
DIAG_ENTRY(Switch /* switch */, 17147, /* DiagArray812 */ 2289, 0, R"()")
DIAG_ENTRY(SwitchBool /* switch-bool */, 17154, /* DiagArray813 */ 2293, 0, R"()")
DIAG_ENTRY(anonymous_37 /* switch-default */, 17166, 0, 0, R"()")
DIAG_ENTRY(SwitchEnum /* switch-enum */, 17181, /* DiagArray815 */ 2295, 0, R"()")
DIAG_ENTRY(anonymous_262 /* sync-fetch-and-nand-semantics-changed */, 17193, /* DiagArray816 */ 2297, 0, R"()")
DIAG_ENTRY(anonymous_38 /* synth */, 17231, 0, 0, R"()")
DIAG_ENTRY(TargetClonesMixedSpecifiers /* target-clones-mixed-specifiers */, 17237, /* DiagArray818 */ 2299, 0, R"()")
DIAG_ENTRY(TautologicalBitwiseCompare /* tautological-bitwise-compare */, 17268, /* DiagArray819 */ 2301, 0, R"()")
DIAG_ENTRY(TautologicalCompare /* tautological-compare */, 17297, /* DiagArray820 */ 2304, /* DiagSubGroup820 */ 630, R"()")
DIAG_ENTRY(TautologicalConstantCompare /* tautological-constant-compare */, 17318, /* DiagArray821 */ 2307, /* DiagSubGroup821 */ 637, R"()")
DIAG_ENTRY(TautologicalInRangeCompare /* tautological-constant-in-range-compare */, 17348, 0, /* DiagSubGroup822 */ 639, R"()")
DIAG_ENTRY(TautologicalOutOfRangeCompare /* tautological-constant-out-of-range-compare */, 17387, /* DiagArray823 */ 2311, 0, R"()")
DIAG_ENTRY(TautologicalObjCBoolCompare /* tautological-objc-bool-compare */, 17430, /* DiagArray824 */ 2313, 0, R"()")
DIAG_ENTRY(TautologicalOverlapCompare /* tautological-overlap-compare */, 17461, /* DiagArray825 */ 2315, 0, R"()")
DIAG_ENTRY(TautologicalPointerCompare /* tautological-pointer-compare */, 17490, /* DiagArray826 */ 2317, 0, R"()")
DIAG_ENTRY(TautologicalTypeLimitCompare /* tautological-type-limit-compare */, 17519, /* DiagArray827 */ 2320, 0, R"()")
DIAG_ENTRY(TautologicalUndefinedCompare /* tautological-undefined-compare */, 17551, /* DiagArray828 */ 2322, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedCharZeroCompare /* tautological-unsigned-char-zero-compare */, 17582, /* DiagArray829 */ 2325, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedEnumZeroCompare /* tautological-unsigned-enum-zero-compare */, 17622, /* DiagArray830 */ 2327, 0, R"()")
DIAG_ENTRY(TautologicalUnsignedZeroCompare /* tautological-unsigned-zero-compare */, 17662, /* DiagArray831 */ 2329, 0, R"()")
DIAG_ENTRY(TautologicalValueRangeCompare /* tautological-value-range-compare */, 17697, /* DiagArray832 */ 2331, 0, R"()")
DIAG_ENTRY(anonymous_271 /* tcb-enforcement */, 17730, /* DiagArray833 */ 2333, 0, R"()")
DIAG_ENTRY(anonymous_227 /* tentative-definition-incomplete-type */, 17746, /* DiagArray834 */ 2335, 0, R"()")
DIAG_ENTRY(ThreadSafety /* thread-safety */, 17783, 0, /* DiagSubGroup835 */ 642, R"()")
DIAG_ENTRY(ThreadSafetyAnalysis /* thread-safety-analysis */, 17797, /* DiagArray836 */ 2337, 0, R"()")
DIAG_ENTRY(ThreadSafetyAttributes /* thread-safety-attributes */, 17820, /* DiagArray837 */ 2356, 0, R"()")
DIAG_ENTRY(ThreadSafetyBeta /* thread-safety-beta */, 17845, /* DiagArray838 */ 2363, 0, R"()")
DIAG_ENTRY(ThreadSafetyNegative /* thread-safety-negative */, 17864, /* DiagArray839 */ 2365, 0, R"()")
DIAG_ENTRY(ThreadSafetyPrecise /* thread-safety-precise */, 17887, /* DiagArray840 */ 2367, 0, R"()")
DIAG_ENTRY(ThreadSafetyReference /* thread-safety-reference */, 17909, /* DiagArray841 */ 2371, 0, R"()")
DIAG_ENTRY(ThreadSafetyVerbose /* thread-safety-verbose */, 17933, /* DiagArray842 */ 2374, 0, R"()")
DIAG_ENTRY(Trigraphs /* trigraphs */, 17955, /* DiagArray843 */ 2376, 0, R"()")
DIAG_ENTRY(TypeLimits /* type-limits */, 17965, 0, /* DiagSubGroup844 */ 647, R"()")
DIAG_ENTRY(TypeSafety /* type-safety */, 17977, /* DiagArray845 */ 2381, 0, R"()")
DIAG_ENTRY(anonymous_208 /* typedef-redefinition */, 17989, /* DiagArray846 */ 2385, 0, R"()")
DIAG_ENTRY(anonymous_202 /* typename-missing */, 18010, /* DiagArray847 */ 2387, 0, R"()")
DIAG_ENTRY(anonymous_89 /* unable-to-open-stats-file */, 18027, /* DiagArray848 */ 2389, 0, R"()")
DIAG_ENTRY(UnalignedAccess /* unaligned-access */, 18053, /* DiagArray849 */ 2391, 0, R"()")
DIAG_ENTRY(anonymous_265 /* unaligned-qualifier-implicit-cast */, 18070, /* DiagArray850 */ 2393, 0, R"()")
DIAG_ENTRY(UnavailableDeclarations /* unavailable-declarations */, 18104, /* DiagArray851 */ 2395, 0, R"()")
DIAG_ENTRY(UndeclaredSelector /* undeclared-selector */, 18129, /* DiagArray852 */ 2397, 0, R"()")
DIAG_ENTRY(anonymous_110 /* undef */, 18149, /* DiagArray853 */ 2400, 0, R"()")
DIAG_ENTRY(anonymous_111 /* undef-prefix */, 18155, /* DiagArray854 */ 2402, 0, R"()")
DIAG_ENTRY(UndefinedBoolConversion /* undefined-bool-conversion */, 18168, /* DiagArray855 */ 2404, 0, R"()")
DIAG_ENTRY(UndefinedFuncTemplate /* undefined-func-template */, 18194, /* DiagArray856 */ 2407, 0, R"()")
DIAG_ENTRY(anonymous_207 /* undefined-inline */, 18218, /* DiagArray857 */ 2409, 0, R"()")
DIAG_ENTRY(anonymous_205 /* undefined-internal */, 18235, /* DiagArray858 */ 2411, 0, R"()")
DIAG_ENTRY(anonymous_206 /* undefined-internal-type */, 18254, /* DiagArray859 */ 2413, 0, R"()")
DIAG_ENTRY(UndefinedReinterpretCast /* undefined-reinterpret-cast */, 18278, /* DiagArray860 */ 2415, 0, R"()")
DIAG_ENTRY(UndefinedVarTemplate /* undefined-var-template */, 18305, /* DiagArray861 */ 2418, 0, R"()")
DIAG_ENTRY(UnderalignedExceptionObject /* underaligned-exception-object */, 18328, /* DiagArray862 */ 2420, 0, R"()")
DIAG_ENTRY(UnevaluatedExpression /* unevaluated-expression */, 18358, /* DiagArray863 */ 2422, /* DiagSubGroup863 */ 652, R"()")
DIAG_ENTRY(UnguardedAvailability /* unguarded-availability */, 18381, /* DiagArray864 */ 2424, /* DiagSubGroup864 */ 654, R"()")
DIAG_ENTRY(UnguardedAvailabilityNew /* unguarded-availability-new */, 18404, /* DiagArray865 */ 2426, 0, R"()")
DIAG_ENTRY(Unicode /* unicode */, 18431, /* DiagArray866 */ 2428, 0, R"()")
DIAG_ENTRY(anonymous_98 /* unicode-homoglyph */, 18439, /* DiagArray867 */ 2436, 0, R"()")
DIAG_ENTRY(anonymous_97 /* unicode-whitespace */, 18457, /* DiagArray868 */ 2438, 0, R"()")
DIAG_ENTRY(anonymous_99 /* unicode-zero-width */, 18476, /* DiagArray869 */ 2440, 0, R"()")
DIAG_ENTRY(Uninitialized /* uninitialized */, 18495, /* DiagArray870 */ 2442, /* DiagSubGroup870 */ 656, R"()")
DIAG_ENTRY(UninitializedConstReference /* uninitialized-const-reference */, 18509, /* DiagArray871 */ 2450, 0, R"()")
DIAG_ENTRY(UnknownArgument /* unknown-argument */, 18539, /* DiagArray872 */ 2452, 0, R"()")
DIAG_ENTRY(UnknownAssumption /* unknown-assumption */, 18556, /* DiagArray873 */ 2455, 0, R"()")
DIAG_ENTRY(UnknownAttributes /* unknown-attributes */, 18575, /* DiagArray874 */ 2457, 0, R"()")
DIAG_ENTRY(CudaUnknownVersion /* unknown-cuda-version */, 18594, /* DiagArray875 */ 2459, 0, R"()")
DIAG_ENTRY(anonymous_115 /* unknown-directives */, 18615, /* DiagArray876 */ 2462, 0, R"()")
DIAG_ENTRY(anonymous_101 /* unknown-escape-sequence */, 18634, /* DiagArray877 */ 2464, 0, R"()")
DIAG_ENTRY(UnknownPragmas /* unknown-pragmas */, 18658, /* DiagArray878 */ 2466, 0, R"()")
DIAG_ENTRY(UnknownSanitizers /* unknown-sanitizers */, 18674, /* DiagArray879 */ 2487, 0, R"()")
DIAG_ENTRY(UnknownWarningOption /* unknown-warning-option */, 18693, /* DiagArray880 */ 2489, 0, R"()")
DIAG_ENTRY(UnnamedTypeTemplateArgs /* unnamed-type-template-args */, 18716, /* DiagArray881 */ 2493, /* DiagSubGroup881 */ 660, R"()")
DIAG_ENTRY(UnneededInternalDecl /* unneeded-internal-declaration */, 18743, /* DiagArray882 */ 2495, 0, R"()")
DIAG_ENTRY(UnneededMemberFunction /* unneeded-member-function */, 18773, /* DiagArray883 */ 2498, 0, R"()")
DIAG_ENTRY(anonymous_198 /* unqualified-std-cast-call */, 18798, /* DiagArray884 */ 2500, 0, R"()")
DIAG_ENTRY(UnreachableCode /* unreachable-code */, 18824, /* DiagArray885 */ 2502, /* DiagSubGroup885 */ 662, R"()")
DIAG_ENTRY(UnreachableCodeAggressive /* unreachable-code-aggressive */, 18841, 0, /* DiagSubGroup886 */ 666, R"()")
DIAG_ENTRY(UnreachableCodeBreak /* unreachable-code-break */, 18869, /* DiagArray887 */ 2504, 0, R"()")
DIAG_ENTRY(UnreachableCodeFallthrough /* unreachable-code-fallthrough */, 18892, /* DiagArray888 */ 2506, 0, R"()")
DIAG_ENTRY(UnreachableCodeGenericAssoc /* unreachable-code-generic-assoc */, 18921, /* DiagArray889 */ 2508, 0, R"()")
DIAG_ENTRY(UnreachableCodeLoopIncrement /* unreachable-code-loop-increment */, 18952, /* DiagArray890 */ 2510, 0, R"()")
DIAG_ENTRY(UnreachableCodeReturn /* unreachable-code-return */, 18984, /* DiagArray891 */ 2512, 0, R"()")
DIAG_ENTRY(Unsequenced /* unsequenced */, 19008, /* DiagArray892 */ 2514, 0, R"()")
DIAG_ENTRY(anonymous_78 /* unsupported-abi */, 19020, /* DiagArray893 */ 2517, 0, R"()")
DIAG_ENTRY(UnsupportedAbs /* unsupported-abs */, 19036, /* DiagArray894 */ 2519, 0, R"()")
DIAG_ENTRY(anonymous_187 /* unsupported-availability-guard */, 19052, /* DiagArray895 */ 2522, 0, R"()")
DIAG_ENTRY(UnsupportedCB /* unsupported-cb */, 19083, /* DiagArray896 */ 2524, 0, R"()")
DIAG_ENTRY(anonymous_183 /* unsupported-dll-base-class-template */, 19098, /* DiagArray897 */ 2526, 0, R"()")
DIAG_ENTRY(UnsupportedFPOpt /* unsupported-floating-point-opt */, 19134, /* DiagArray898 */ 2528, 0, R"()")
DIAG_ENTRY(UnsupportedFriend /* unsupported-friend */, 19165, /* DiagArray899 */ 2531, 0, R"()")
DIAG_ENTRY(UnsupportedGPOpt /* unsupported-gpopt */, 19184, /* DiagArray900 */ 2534, 0, R"()")
DIAG_ENTRY(UnsupportedNan /* unsupported-nan */, 19202, /* DiagArray901 */ 2536, 0, R"()")
DIAG_ENTRY(UnsupportedTargetOpt /* unsupported-target-opt */, 19218, /* DiagArray902 */ 2539, 0, R"()")
DIAG_ENTRY(anonymous_193 /* unsupported-visibility */, 19241, /* DiagArray903 */ 2542, 0, R"()")
DIAG_ENTRY(anonymous_200 /* unusable-partial-specialization */, 19264, /* DiagArray904 */ 2544, 0, R"()")
DIAG_ENTRY(Unused /* unused */, 19296, 0, /* DiagSubGroup905 */ 670, R"()")
DIAG_ENTRY(UnusedArgument /* unused-argument */, 19303, 0, 0, R"()")
DIAG_ENTRY(UnusedButSetParameter /* unused-but-set-parameter */, 19319, /* DiagArray907 */ 2546, 0, R"()")
DIAG_ENTRY(UnusedButSetVariable /* unused-but-set-variable */, 19344, /* DiagArray908 */ 2548, 0, R"()")
DIAG_ENTRY(UnusedCommandLineArgument /* unused-command-line-argument */, 19368, /* DiagArray909 */ 2550, 0, R"()")
DIAG_ENTRY(UnusedComparison /* unused-comparison */, 19397, /* DiagArray910 */ 2563, 0, R"()")
DIAG_ENTRY(UnusedConstVariable /* unused-const-variable */, 19415, /* DiagArray911 */ 2565, 0, R"()")
DIAG_ENTRY(UnusedExceptionParameter /* unused-exception-parameter */, 19437, /* DiagArray912 */ 2567, 0, R"()")
DIAG_ENTRY(UnusedFunction /* unused-function */, 19464, /* DiagArray913 */ 2569, /* DiagSubGroup913 */ 681, R"()")
DIAG_ENTRY(UnusedGetterReturnValue /* unused-getter-return-value */, 19480, /* DiagArray914 */ 2571, 0, R"()")
DIAG_ENTRY(UnusedLabel /* unused-label */, 19507, /* DiagArray915 */ 2573, 0, R"()")
DIAG_ENTRY(UnusedLambdaCapture /* unused-lambda-capture */, 19520, /* DiagArray916 */ 2575, 0, R"()")
DIAG_ENTRY(UnusedLocalTypedef /* unused-local-typedef */, 19542, /* DiagArray917 */ 2577, 0, R"()")
DIAG_ENTRY(anonymous_62 /* unused-local-typedefs */, 19563, 0, /* DiagSubGroup918 */ 683, R"()")
DIAG_ENTRY(anonymous_109 /* unused-macros */, 19585, /* DiagArray919 */ 2579, 0, R"()")
DIAG_ENTRY(UnusedMemberFunction /* unused-member-function */, 19599, /* DiagArray920 */ 2581, /* DiagSubGroup920 */ 685, R"()")
DIAG_ENTRY(UnusedParameter /* unused-parameter */, 19622, /* DiagArray921 */ 2583, 0, R"()")
DIAG_ENTRY(UnusedPrivateField /* unused-private-field */, 19639, /* DiagArray922 */ 2585, 0, R"()")
DIAG_ENTRY(UnusedPropertyIvar /* unused-property-ivar */, 19660, /* DiagArray923 */ 2587, 0, R"()")
DIAG_ENTRY(UnusedResult /* unused-result */, 19681, /* DiagArray924 */ 2589, 0, R"()")
DIAG_ENTRY(UnusedTemplate /* unused-template */, 19695, /* DiagArray925 */ 2592, /* DiagSubGroup925 */ 687, R"()")
DIAG_ENTRY(UnusedValue /* unused-value */, 19711, /* DiagArray926 */ 2594, /* DiagSubGroup926 */ 689, R"()")
DIAG_ENTRY(UnusedVariable /* unused-variable */, 19724, /* DiagArray927 */ 2602, /* DiagSubGroup927 */ 693, R"()")
DIAG_ENTRY(anonymous_249 /* unused-volatile-lvalue */, 19740, /* DiagArray928 */ 2604, 0, R"()")
DIAG_ENTRY(UsedButMarkedUnused /* used-but-marked-unused */, 19763, /* DiagArray929 */ 2606, 0, R"()")
DIAG_ENTRY(UserDefinedLiterals /* user-defined-literals */, 19786, /* DiagArray930 */ 2608, 0, R"()")
DIAG_ENTRY(UserDefinedWarnings /* user-defined-warnings */, 19808, /* DiagArray931 */ 2610, 0, R"()")
DIAG_ENTRY(Varargs /* varargs */, 19830, /* DiagArray932 */ 2612, 0, R"()")
DIAG_ENTRY(VariadicMacros /* variadic-macros */, 19838, /* DiagArray933 */ 2616, 0, R"()")
DIAG_ENTRY(anonymous_173 /* vec-elem-size */, 19854, /* DiagArray934 */ 2620, 0, R"()")
DIAG_ENTRY(VectorConversion /* vector-conversion */, 19868, /* DiagArray935 */ 2622, 0, R"()")
DIAG_ENTRY(anonymous_61 /* vector-conversions */, 19886, 0, /* DiagSubGroup936 */ 695, R"()")
DIAG_ENTRY(VexingParse /* vexing-parse */, 19905, /* DiagArray937 */ 2624, 0, R"()")
DIAG_ENTRY(Visibility /* visibility */, 19918, /* DiagArray938 */ 2628, 0, R"()")
DIAG_ENTRY(VLA /* vla */, 19929, /* DiagArray939 */ 2631, /* DiagSubGroup939 */ 697, R"()")
DIAG_ENTRY(VLAExtension /* vla-extension */, 19933, /* DiagArray940 */ 2633, 0, R"()")
DIAG_ENTRY(VoidPointerToEnumCast /* void-pointer-to-enum-cast */, 19947, /* DiagArray941 */ 2635, 0, R"()")
DIAG_ENTRY(VoidPointerToIntCast /* void-pointer-to-int-cast */, 19973, /* DiagArray942 */ 2637, /* DiagSubGroup942 */ 699, R"()")
DIAG_ENTRY(anonymous_229 /* void-ptr-dereference */, 19998, /* DiagArray943 */ 2639, 0, R"()")
DIAG_ENTRY(VolatileRegisterVar /* volatile-register-var */, 20019, 0, 0, R"()")
DIAG_ENTRY(WebAssemblyExceptionSpec /* wasm-exception-spec */, 20041, /* DiagArray945 */ 2641, 0, R"()")
DIAG_ENTRY(anonymous_166 /* weak-template-vtables */, 20061, /* DiagArray946 */ 2643, 0, R"()")
DIAG_ENTRY(anonymous_165 /* weak-vtables */, 20083, /* DiagArray947 */ 2645, 0, R"()")
DIAG_ENTRY(WritableStrings /* writable-strings */, 20096, /* DiagArray948 */ 2647, /* DiagSubGroup948 */ 701, R"()")
DIAG_ENTRY(GCCWriteStrings /* write-strings */, 20113, 0, /* DiagSubGroup949 */ 703, R"()")
DIAG_ENTRY(XorUsedAsPow /* xor-used-as-pow */, 20127, /* DiagArray950 */ 2649, 0, R"()")
DIAG_ENTRY(anonymous_270 /* zero-as-null-pointer-constant */, 20143, /* DiagArray951 */ 2653, 0, R"()")
DIAG_ENTRY(ZeroLengthArray /* zero-length-array */, 20173, /* DiagArray952 */ 2655, 0, R"()")
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

