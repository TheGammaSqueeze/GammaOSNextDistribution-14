// SPDX-License-Identifier: LGPL-2.1
/*
 * Copyright (C) 2021 VMware Inc, Steven Rostedt <rostedt@goodmis.org>
 *
 * Updates:
 * Copyright (C) 2021, VMware, Tzvetomir Stoyanov <tz.stoyanov@gmail.com>
 *
 */
#include <trace-seq.h>
#include <stdlib.h>
#include <stdarg.h>
#include <ctype.h>
#include <errno.h>

#include "tracefs.h"
#include "tracefs-local.h"
#include "sqlhist-parse.h"

extern int yylex_init(void* ptr_yy_globals);
extern int yylex_init_extra(struct sqlhist_bison *sb, void* ptr_yy_globals);
extern int yylex_destroy (void * yyscanner );

struct str_hash {
	struct str_hash		*next;
	char			*str;
};

enum alias_type {
	ALIAS_EVENT,
	ALIAS_FIELD,
};

enum field_type {
	FIELD_NONE,
	FIELD_FROM,
	FIELD_TO,
};

#define for_each_field(expr, field, table) \
	for (expr = (table)->fields; expr; expr = (field)->next)

struct field {
	struct expr		*next;	/* private link list */
	const char		*system;
	const char		*event_name;
	struct tep_event	*event;
	const char		*raw;
	const char		*label;
	const char		*field;
	const char		*type;
	enum field_type		ftype;
};

struct filter {
	enum filter_type	type;
	struct expr		*lval;
	struct expr		*rval;
};

struct match {
	struct match		*next;
	struct expr		*lval;
	struct expr		*rval;
};

struct compare {
	enum compare_type	type;
	struct expr		*lval;
	struct expr		*rval;
	const char		*name;
};

enum expr_type
{
	EXPR_NUMBER,
	EXPR_STRING,
	EXPR_FIELD,
	EXPR_FILTER,
	EXPR_COMPARE,
};

struct expr {
	struct expr		*free_list;
	struct expr		*next;
	enum expr_type		type;
	int			line;
	int			idx;
	union {
		struct field	field;
		struct filter	filter;
		struct compare	compare;
		const char	*string;
		long		number;
	};
};

struct sql_table {
	struct sqlhist_bison	*sb;
	const char		*name;
	struct expr		*exprs;
	struct expr		*fields;
	struct expr		*from;
	struct expr		*to;
	struct expr		*where;
	struct expr		**next_where;
	struct match		*matches;
	struct match		**next_match;
	struct expr		*selections;
	struct expr		**next_selection;
};

__hidden int my_yyinput(void *extra, char *buf, int max)
{
	struct sqlhist_bison *sb = extra;

	if (!sb || !sb->buffer)
		return -1;

	if (sb->buffer_idx + max > sb->buffer_size)
		max = sb->buffer_size - sb->buffer_idx;

	if (max)
		memcpy(buf, sb->buffer + sb->buffer_idx, max);

	sb->buffer_idx += max;

	return max;
}

__hidden void sql_parse_error(struct sqlhist_bison *sb, const char *text,
			      const char *fmt, va_list ap)
{
	const char *buffer = sb->buffer;
	struct trace_seq s;
	int line = sb->line_no;
	int idx = sb->line_idx - strlen(text);
	int i;

	if (!buffer)
		return;

	trace_seq_init(&s);
	if (!s.buffer) {
		tracefs_warning("Error allocating internal buffer\n");
		return;
	}

	for (i = 0; line && buffer[i]; i++) {
		if (buffer[i] == '\n')
			line--;
	}
	for (; buffer[i] && buffer[i] != '\n'; i++)
		trace_seq_putc(&s, buffer[i]);
	trace_seq_putc(&s, '\n');
	for (i = idx; i > 0; i--)
		trace_seq_putc(&s, ' ');
	trace_seq_puts(&s, "^\n");
	trace_seq_printf(&s, "ERROR: '%s'\n", text);
	trace_seq_vprintf(&s, fmt, ap);

	trace_seq_terminate(&s);

	sb->parse_error_str = strdup(s.buffer);
	trace_seq_destroy(&s);
}

static void parse_error(struct sqlhist_bison *sb, const char *text,
			const char *fmt, ...)
{
	va_list ap;

	va_start(ap, fmt);
	sql_parse_error(sb, text, fmt, ap);
	va_end(ap);
}

__hidden unsigned int quick_hash(const char *str)
{
	unsigned int val = 0;
	int len = strlen(str);

	for (; len >= 4; str += 4, len -= 4) {
		val += str[0];
		val += str[1] << 8;
		val += str[2] << 16;
		val += str[3] << 24;
	}
	for (; len > 0; str++, len--)
		val += str[0] << (len * 8);

        val *= 2654435761;

        return val & ((1 << HASH_BITS) - 1);
}


static struct str_hash *find_string(struct sqlhist_bison *sb, const char *str)
{
	unsigned int key = quick_hash(str);
	struct str_hash *hash = sb->str_hash[key];

	for (; hash; hash = hash->next) {
		if (!strcmp(hash->str, str))
			return hash;
	}
	return NULL;
}

/*
 * If @str is found, then return the hash string.
 * This lets store_str() know to free str.
 */
static char **add_hash(struct sqlhist_bison *sb, const char *str)
{
	struct str_hash *hash;
	unsigned int key;

	if ((hash = find_string(sb, str))) {
		return &hash->str;
	}

	hash = malloc(sizeof(*hash));
	if (!hash)
		return NULL;
	key = quick_hash(str);
	hash->next = sb->str_hash[key];
	sb->str_hash[key] = hash;
	hash->str = NULL;
	return &hash->str;
}

__hidden char *store_str(struct sqlhist_bison *sb, const char *str)
{
	char **pstr = add_hash(sb, str);

	if (!pstr)
		return NULL;

	if (!(*pstr))
		*pstr = strdup(str);

	return *pstr;
}

__hidden void *add_cast(struct sqlhist_bison *sb,
			void *data, const char *type)
{
	struct expr *expr = data;
	struct field *field = &expr->field;

	field->type = type;
	return expr;
}

__hidden int add_selection(struct sqlhist_bison *sb, void *select,
			   const char *name)
{
	struct sql_table *table = sb->table;
	struct expr *expr = select;

	switch (expr->type) {
	case EXPR_FIELD:
		expr->field.label = name;
		break;
	case EXPR_COMPARE:
		expr->compare.name = name;
		break;
	case EXPR_NUMBER:
	case EXPR_STRING:
	case EXPR_FILTER:
	default:
		return -1;
	}

	if (expr->next)
		return -1;

	*table->next_selection = expr;
	table->next_selection = &expr->next;

	return 0;
}

static struct expr *find_field(struct sqlhist_bison *sb,
				const char *raw, const char *label)
{
	struct field *field;
	struct expr *expr;

	for_each_field(expr, field, sb->table) {
		field = &expr->field;

		if (!strcmp(field->raw, raw)) {
			if (label && !field->label)
				field->label = label;
			if (label && strcmp(label, field->label) != 0)
				continue;
			return expr;
		}

		if (label && !strcmp(field->raw, label)) {
			if (!field->label) {
				field->label = label;
				field->raw = raw;
			}
			return expr;
		}

		if (!field->label)
			continue;

		if (!strcmp(field->label, raw))
			return expr;

		if (label && !strcmp(field->label, label))
			return expr;
	}
	return NULL;
}

static void *create_expr(struct sqlhist_bison *sb,
			 enum expr_type type, struct expr **expr_p)
{
	struct expr *expr;

	expr = calloc(1, sizeof(*expr));
	if (!expr)
		return NULL;

	if (expr_p)
		*expr_p = expr;

	expr->free_list = sb->table->exprs;
	sb->table->exprs = expr;

	expr->type = type;
	expr->line = sb->line_no;
	expr->idx = sb->line_idx;

	switch (type) {
	case EXPR_FIELD:	return &expr->field;
	case EXPR_COMPARE:	return &expr->compare;
	case EXPR_NUMBER:	return &expr->number;
	case EXPR_STRING:	return &expr->string;
	case EXPR_FILTER:	return &expr->filter;
	}

	return NULL;
}

#define __create_expr(var, type, ENUM, expr)			\
	do {							\
		var = (type *)create_expr(sb, EXPR_##ENUM, expr);	\
	} while(0)

#define create_field(var, expr)				\
	__create_expr(var, struct field, FIELD, expr)

#define create_filter(var, expr)			\
	__create_expr(var, struct filter, FILTER, expr)

#define create_compare(var, expr)				\
	__create_expr(var, struct compare, COMPARE, expr)

#define create_string(var, expr)			\
	__create_expr(var, const char *, STRING, expr)

#define create_number(var, expr)			\
	__create_expr(var, long, NUMBER, expr)

__hidden void *add_field(struct sqlhist_bison *sb,
			 const char *field_name, const char *label)
{
	struct sql_table *table = sb->table;
	struct expr *expr;
	struct field *field;

	expr = find_field(sb, field_name, label);
	if (expr)
		return expr;

	create_field(field, &expr);

	field->next = table->fields;
	table->fields = expr;

	field->raw = field_name;
	field->label = label;

	return expr;
}

__hidden void *add_filter(struct sqlhist_bison *sb,
			  void *A, void *B, enum filter_type op)
{
	struct filter *filter;
	struct expr *expr;

	create_filter(filter, &expr);

	filter->lval = A;
	filter->rval = B;

	filter->type = op;

	return expr;
}

__hidden int add_match(struct sqlhist_bison *sb, void *A, void *B)
{
	struct sql_table *table = sb->table;
	struct match *match;

	match = calloc(1, sizeof(*match));
	if (!match)
		return -1;

	match->lval = A;
	match->rval = B;

	*table->next_match = match;
	table->next_match = &match->next;

	return 0;
}
__hidden void *add_compare(struct sqlhist_bison *sb,
			   void *A, void *B, enum compare_type type)
{
	struct compare *compare;
	struct expr *expr;

	create_compare(compare, &expr);

	compare = &expr->compare;
	compare->lval = A;
	compare->rval = B;
	compare->type = type;

	return expr;
}

__hidden int add_where(struct sqlhist_bison *sb, void *item)
{
	struct expr *expr = item;
	struct sql_table *table = sb->table;

	if (expr->type != EXPR_FILTER)
		return -1;

	*table->next_where = expr;
	table->next_where = &expr->next;

	if (expr->next)
		return -1;

	return 0;
}

__hidden int add_from(struct sqlhist_bison *sb, void *item)
{
	struct expr *expr = item;

	if (expr->type != EXPR_FIELD)
		return -1;

	sb->table->from = expr;

	return 0;
}

__hidden int add_to(struct sqlhist_bison *sb, void *item)
{
	struct expr *expr = item;

	if (expr->type != EXPR_FIELD)
		return -1;

	sb->table->to = expr;

	return 0;
}

__hidden void *add_string(struct sqlhist_bison *sb, const char *str)
{
	struct expr *expr;
	const char **str_p;

	create_string(str_p, &expr);
	*str_p = str;
	return expr;
}

__hidden void *add_number(struct sqlhist_bison *sb, long val)
{
	struct expr *expr;
	long *num;

	create_number(num, &expr);
	*num = val;
	return expr;
}

__hidden int table_start(struct sqlhist_bison *sb)
{
	struct sql_table *table;

	table = calloc(1, sizeof(*table));
	if (!table)
		return -ENOMEM;

	table->sb = sb;
	sb->table = table;

	table->next_where = &table->where;
	table->next_match = &table->matches;
	table->next_selection = &table->selections;

	return 0;
}

static int test_event_exists(struct tep_handle *tep,
			     struct sqlhist_bison *sb,
			     struct expr *expr, struct tep_event **pevent)
{
	struct field *field = &expr->field;
	const char *system = field->system;
	const char *event = field->event_name;

	if (!field->event)
		field->event = tep_find_event_by_name(tep, system, event);
	if (pevent)
		*pevent = field->event;

	if (field->event)
		return 0;

	sb->line_no = expr->line;
	sb->line_idx = expr->idx;

	parse_error(sb, field->raw, "event not found\n");
	return -1;
}

static int test_field_exists(struct tep_handle *tep,
			     struct sqlhist_bison *sb,
			     struct expr *expr)
{
	struct field *field = &expr->field;
	struct tep_format_field *tfield;
	char *field_name;
	const char *p;

	if (!field->event) {
		if (test_event_exists(tep, sb, expr, NULL))
			return -1;
	}

	/* The field could have a conversion */
	p = strchr(field->field, '.');
	if (p)
		field_name = strndup(field->field, p - field->field);
	else
		field_name = strdup(field->field);

	if (!field_name)
		return -1;

	if (!strcmp(field_name, TRACEFS_TIMESTAMP) ||
	    !strcmp(field->field, TRACEFS_TIMESTAMP_USECS))
		tfield = (void *)1L;
	else
		tfield = tep_find_any_field(field->event, field_name);
	free(field_name);

	if (!tfield && (!strcmp(field->field, "COMM") || !strcmp(field->field, "comm")))
		tfield = (void *)1L;

	if (tfield)
		return 0;

	sb->line_no = expr->line;
	sb->line_idx = expr->idx;

	parse_error(sb, field->raw,
		    "Field '%s' not part of event %s\n",
		    field->field, field->event_name);
	return -1;
}

static int update_vars(struct tep_handle *tep,
		       struct sql_table *table,
		       struct expr *expr)
{
	struct sqlhist_bison *sb = table->sb;
	struct field *event_field = &expr->field;
	enum field_type ftype = FIELD_NONE;
	struct tep_event *event;
	struct field *field;
	const char *label;
	const char *raw = event_field->raw;
	const char *event_name;
	const char *system;
	const char *p;
	int label_len = 0, event_len, system_len;

	if (expr == table->to)
		ftype = FIELD_TO;
	else if (expr == table->from)
		ftype = FIELD_FROM;

	p = strchr(raw, '.');
	if (p) {
		char *str;

		str = strndup(raw, p - raw);
		if (!str)
			return -1;
		event_field->system = store_str(sb, str);
		free(str);
		if (!event_field->system)
			return -1;
		p++;
	} else {
		p = raw;
	}

	event_field->event_name = store_str(sb, p);
	if (!event_field->event_name)
		return -1;

	if (test_event_exists(tep, sb, expr, &event))
		return -1;

	if (!event_field->system)
		event_field->system = store_str(sb, event->system);

	if (!event_field->system)
		return -1;

	label = event_field->label;
	if (label)
		label_len = strlen(label);

	system = event_field->system;
	system_len = strlen(system);

	event_name = event_field->event_name;
	event_len = strlen(event_name);

	for_each_field(expr, field, table) {
		int len;

		field = &expr->field;

		if (field->event)
			continue;

		raw = field->raw;

		/*
		 * The field could be:
		 *     system.event.field...
		 *     event.field...
		 *     label.field...
		 * We check label first.
		 */

		len = label_len;
		if (label && !strncmp(raw, label, len) &&
		    raw[len] == '.') {
			/* Label matches and takes precedence */
			goto found;
		}

		if (!strncmp(raw, system, system_len) &&
		    raw[system_len] == '.') {
			raw += system_len + 1;
			/* Check the event portion next */
		}

		len = event_len;
		if (strncmp(raw, event_name, len) ||
		    raw[len] != '.') {
			/* Does not match */
			continue;
		}
 found:
		field->system = system;
		field->event_name = event_name;
		field->event = event;
		field->field = raw + len + 1;
		field->ftype = ftype;

		if (!strcmp(field->field, "TIMESTAMP"))
			field->field = store_str(sb, TRACEFS_TIMESTAMP);
		if (!strcmp(field->field, "TIMESTAMP_USECS"))
			field->field = store_str(sb, TRACEFS_TIMESTAMP_USECS);
		if (test_field_exists(tep, sb, expr))
			return -1;
	}

	return 0;
}

/*
 * Called when there's a FROM but no JOIN(to), which means that the
 * selections can be fields and not mention the event itself.
 */
static int update_fields(struct tep_handle *tep,
			 struct sql_table *table,
			 struct expr *expr)
{
	struct field *event_field = &expr->field;
	struct sqlhist_bison *sb = table->sb;
	struct tep_format_field *tfield;
	struct tep_event *event;
	struct field *field;
	const char *p;
	int len;

	/* First update fields with aliases an such and add event */
	update_vars(tep, table, expr);

	/*
	 * If event is not found, the creation of the synth will
	 * add a proper error, so return "success".
	*/
	if (!event_field->event)
		return 0;

	event = event_field->event;

	for_each_field(expr, field, table) {
		const char *field_name;

		field = &expr->field;

		if (field->event)
			continue;

		field_name = field->raw;

		p = strchr(field_name, '.');
		if (p) {
			len = p - field_name;
			p = strndup(field_name, len);
			if (!p)
				return -1;
			field_name = store_str(sb, p);
			if (!field_name)
				return -1;
			free((char *)p);
		}

		tfield = tep_find_any_field(event, field_name);
		/* Let it error properly later */
		if (!tfield)
			continue;

		field->system = event_field->system;
		field->event_name = event_field->event_name;
		field->event = event;
		field->field = field_name;
	}

	return 0;
}

static int match_error(struct sqlhist_bison *sb, struct match *match,
		       struct field *lmatch, struct field *rmatch)
{
	struct field *lval = &match->lval->field;
	struct field *rval = &match->rval->field;
	struct field *field;
	struct expr *expr;

	if (lval->system != lmatch->system ||
	    lval->event != lmatch->event) {
		expr = match->lval;
		field = lval;
	} else {
		expr = match->rval;
		field = rval;
	}

	sb->line_no = expr->line;
	sb->line_idx = expr->idx;

	parse_error(sb, field->raw,
		    "'%s' and '%s' must be a field for each event: '%s' and '%s'\n",
		    lval->raw, rval->raw, sb->table->to->field.raw,
		    sb->table->from->field.raw);

	return -1;
}

static int test_match(struct sql_table *table, struct match *match)
{
	struct field *lval, *rval;
	struct field *to, *from;

	if (!match->lval || !match->rval)
		return -1;

	if (match->lval->type != EXPR_FIELD || match->rval->type != EXPR_FIELD)
		return -1;

	to = &table->to->field;
	from = &table->from->field;

	lval = &match->lval->field;
	rval = &match->rval->field;

	/*
	 * Note, strings are stored in the string store, so all
	 * duplicate strings are the same value, and we can use
	 * normal "==" and "!=" instead of strcmp().
	 *
	 * Either lval == to and rval == from
	 * or lval == from and rval == to.
	 */
	if ((lval->system != to->system) ||
	    (lval->event != to->event)) {
		if ((rval->system != to->system) ||
		    (rval->event != to->event) ||
		    (lval->system != from->system) ||
		    (lval->event != from->event))
			return match_error(table->sb, match, from, to);
	} else {
		if ((rval->system != from->system) ||
		    (rval->event != from->event) ||
		    (lval->system != to->system) ||
		    (lval->event != to->event))
			return match_error(table->sb, match, to, from);
	}
	return 0;
}

static void assign_match(const char *system, const char *event,
			 struct match *match,
			 const char **start_match, const char **end_match)
{
	struct field *lval, *rval;

	lval = &match->lval->field;
	rval = &match->rval->field;

	if (lval->system == system &&
	    lval->event_name == event) {
		*start_match = lval->field;
		*end_match = rval->field;
	} else {
		*start_match = rval->field;
		*end_match = lval->field;
	}
}

static int build_compare(struct tracefs_synth *synth,
			 const char *system, const char *event,
			 struct compare *compare)
{
	const char *start_field;
	const char *end_field;
	struct field *lval, *rval;
	enum tracefs_synth_calc calc;
	int ret;

	if (!compare->name)
		return -1;

	lval = &compare->lval->field;
	rval = &compare->rval->field;

	if (lval->system == system &&
	    lval->event_name == event) {
		start_field = lval->field;
		end_field = rval->field;
		calc = TRACEFS_SYNTH_DELTA_START;
	} else {
		start_field = rval->field;
		end_field = lval->field;
		calc = TRACEFS_SYNTH_DELTA_END;
	}

	if (compare->type == COMPARE_ADD)
		calc = TRACEFS_SYNTH_ADD;

	ret = tracefs_synth_add_compare_field(synth, start_field,
					      end_field, calc,
					      compare->name);
	return ret;
}

static int verify_filter_error(struct sqlhist_bison *sb, struct expr *expr,
			       const char *event)
{
	struct field *field = &expr->field;

	sb->line_no = expr->line;
	sb->line_idx = expr->idx;

	parse_error(sb, field->raw,
		    "event '%s' can not be grouped or '||' together with '%s'\n"
		    "All filters between '&&' must be for the same event\n",
		    field->event, event);
	return -1;
}

static int do_verify_filter(struct sqlhist_bison *sb, struct filter *filter,
			    const char **system, const char **event,
			    enum field_type *ftype)
{
	int ret;

	if (filter->type == FILTER_OR ||
	    filter->type == FILTER_AND) {
		ret = do_verify_filter(sb, &filter->lval->filter, system, event, ftype);
		if (ret)
			return ret;
		return do_verify_filter(sb, &filter->rval->filter, system, event, ftype);
	}
	if (filter->type == FILTER_GROUP ||
	    filter->type == FILTER_NOT_GROUP) {
		return do_verify_filter(sb, &filter->lval->filter, system, event, ftype);
	}

	/*
	 * system and event will be NULL until we find the left most
	 * node. Then assign it, and compare on the way back up.
	 */
	if (!*system && !*event) {
		*system = filter->lval->field.system;
		*event = filter->lval->field.event_name;
		*ftype = filter->lval->field.ftype;
		return 0;
	}

	if (filter->lval->field.system != *system ||
	    filter->lval->field.event_name != *event)
		return verify_filter_error(sb, filter->lval, *event);

	return 0;
}

static int verify_filter(struct sqlhist_bison *sb, struct filter *filter,
			 const char **system, const char **event,
			 enum field_type *ftype)
{
	int ret;

	switch (filter->type) {
	case FILTER_OR:
	case FILTER_AND:
	case FILTER_GROUP:
	case FILTER_NOT_GROUP:
		break;
	default:
		return do_verify_filter(sb, filter, system, event, ftype);
	}

	ret = do_verify_filter(sb, &filter->lval->filter, system, event, ftype);
	if (ret)
		return ret;

	switch (filter->type) {
	case FILTER_OR:
	case FILTER_AND:
		return do_verify_filter(sb, &filter->rval->filter, system, event, ftype);
	default:
		return 0;
	}
}

static int test_field_exists(struct tep_handle *tep, struct sqlhist_bison *sb,
			     struct expr *expr);

static void filter_compare_error(struct tep_handle *tep,
				 struct sqlhist_bison *sb,
				 struct expr *expr)
{
	struct field *field = &expr->field;

	switch (errno) {
	case ENODEV:
	case EBADE:
		break;
	case EINVAL:
		parse_error(sb, field->raw, "Invalid compare\n");
		break;
	default:
		parse_error(sb, field->raw, "System error?\n");
		return;
	}

	/* ENODEV means that an event or field does not exist */
	if (errno == ENODEV) {
		if (test_field_exists(tep, sb, expr))
			return;
		if (test_field_exists(tep, sb, expr))
			return;
		return;
	}

	/* fields exist, but values are not compatible */
	sb->line_no = expr->line;
	sb->line_idx = expr->idx;

	parse_error(sb, field->raw,
		    "Field '%s' is not compatible to be compared with the given value\n",
		    field->field);
}

static void filter_error(struct tep_handle *tep,
			 struct sqlhist_bison *sb, struct expr *expr)
{
	struct filter *filter = &expr->filter;

	sb->line_no = expr->line;
	sb->line_idx = expr->idx;

	switch (filter->type) {
	case FILTER_NOT_GROUP:
	case FILTER_GROUP:
	case FILTER_OR:
	case FILTER_AND:
		break;
	default:
		filter_compare_error(tep, sb, filter->lval);
		return;
	}

	sb->line_no = expr->line;
	sb->line_idx = expr->idx;

	parse_error(sb, "", "Problem with filter entry?\n");
}

static int build_filter(struct tep_handle *tep, struct sqlhist_bison *sb,
			struct tracefs_synth *synth,
			bool start, struct expr *expr, bool *started)
{
	int (*append_filter)(struct tracefs_synth *synth,
			     enum tracefs_filter type,
			     const char *field,
			     enum tracefs_compare compare,
			     const char *val);
	struct filter *filter = &expr->filter;
	enum tracefs_compare cmp;
	const char *val;
	int and_or = TRACEFS_FILTER_AND;
	char num[64];
	int ret;

	if (start)
		append_filter = tracefs_synth_append_start_filter;
	else
		append_filter = tracefs_synth_append_end_filter;

	if (started && *started) {
		ret = append_filter(synth, and_or, NULL, 0, NULL);
		ret = append_filter(synth, TRACEFS_FILTER_OPEN_PAREN,
				    NULL, 0, NULL);
	}

	switch (filter->type) {
	case FILTER_NOT_GROUP:
		ret = append_filter(synth, TRACEFS_FILTER_NOT,
				    NULL, 0, NULL);
		if (ret < 0)
			goto out;
		/* Fall through */
	case FILTER_GROUP:
		ret = append_filter(synth, TRACEFS_FILTER_OPEN_PAREN,
				    NULL, 0, NULL);
		if (ret < 0)
			goto out;
		ret = build_filter(tep, sb, synth, start, filter->lval, NULL);
		if (ret < 0)
			goto out;
		ret = append_filter(synth, TRACEFS_FILTER_CLOSE_PAREN,
				    NULL, 0, NULL);
		goto out;

	case FILTER_OR:
		and_or = TRACEFS_FILTER_OR;
		/* Fall through */
	case FILTER_AND:
		ret = build_filter(tep, sb, synth, start, filter->lval, NULL);
		if (ret < 0)
			goto out;
		ret = append_filter(synth, and_or, NULL, 0, NULL);

		if (ret)
			goto out;
		ret = build_filter(tep, sb, synth, start, filter->rval, NULL);
		goto out;
	default:
		break;
	}

	switch (filter->rval->type) {
	case EXPR_NUMBER:
		sprintf(num, "%ld", filter->rval->number);
		val = num;
		break;
	case EXPR_STRING:
		val = filter->rval->string;
		break;
	default:
		break;
	}

	switch (filter->type) {
	case FILTER_EQ:		cmp = TRACEFS_COMPARE_EQ; break;
	case FILTER_NE:		cmp = TRACEFS_COMPARE_NE; break;
	case FILTER_LE:		cmp = TRACEFS_COMPARE_LE; break;
	case FILTER_LT:		cmp = TRACEFS_COMPARE_LT; break;
	case FILTER_GE:		cmp = TRACEFS_COMPARE_GE; break;
	case FILTER_GT:		cmp = TRACEFS_COMPARE_GT; break;
	case FILTER_BIN_AND:	cmp = TRACEFS_COMPARE_AND; break;
	case FILTER_STR_CMP:	cmp = TRACEFS_COMPARE_RE; break;
	default:
		tracefs_warning("Error invalid filter type '%d'", filter->type);
		return ERANGE;
	}

	ret = append_filter(synth, TRACEFS_FILTER_COMPARE,
			    filter->lval->field.field, cmp, val);

	if (ret)
		filter_error(tep, sb, expr);
 out:
	if (!ret && started) {
		if (*started)
			ret = append_filter(synth, TRACEFS_FILTER_CLOSE_PAREN,
					    NULL, 0, NULL);
		*started = true;
	}
	return ret;
}

static void *field_match_error(struct tep_handle *tep, struct sqlhist_bison *sb,
			       struct match *match)
{
	switch (errno) {
	case ENODEV:
	case EBADE:
		break;
	default:
		/* System error */
		return NULL;
	}

	/* ENODEV means that an event or field does not exist */
	if (errno == ENODEV) {
		if (test_field_exists(tep, sb, match->lval))
			return NULL;
		if (test_field_exists(tep, sb, match->rval))
			return NULL;
		return NULL;
	}

	/* fields exist, but values are not compatible */
	sb->line_no = match->lval->line;
	sb->line_idx = match->lval->idx;

	parse_error(sb, match->lval->field.raw,
		    "Field '%s' is not compatible to match field '%s'\n",
		    match->lval->field.raw, match->rval->field.raw);
	return NULL;
}

static void *synth_init_error(struct tep_handle *tep, struct sql_table *table)
{
	struct sqlhist_bison *sb = table->sb;
	struct match *match = table->matches;

	switch (errno) {
	case ENODEV:
	case EBADE:
		break;
	default:
		/* System error */
		return NULL;
	}

	/* ENODEV could mean that start or end events do not exist */
	if (errno == ENODEV) {
		if (test_event_exists(tep, sb, table->from, NULL))
			return NULL;
		if (test_event_exists(tep, sb, table->to, NULL))
			return NULL;
	}

	return field_match_error(tep, sb, match);
}

static void selection_error(struct tep_handle *tep,
			    struct sqlhist_bison *sb, struct expr *expr)
{
	/* We just care about event not existing */
	if (errno != ENODEV)
		return;

	test_field_exists(tep, sb, expr);
}

static void compare_error(struct tep_handle *tep,
			    struct sqlhist_bison *sb, struct expr *expr)
{
	struct compare *compare = &expr->compare;

	if (!compare->name) {
		sb->line_no = expr->line;
		sb->line_idx = expr->idx + strlen("no name");

		parse_error(sb, "no name",
		    "Field calculations must be labeled 'AS name'\n");
	}

	switch (errno) {
	case ENODEV:
	case EBADE:
		break;
	default:
		/* System error */
		return;
	}

	/* ENODEV means that an event or field does not exist */
	if (errno == ENODEV) {
		if (test_field_exists(tep, sb, compare->lval))
			return;
		if (test_field_exists(tep, sb, compare->rval))
			return;
		return;
	}

	/* fields exist, but values are not compatible */
	sb->line_no = compare->lval->line;
	sb->line_idx = compare->lval->idx;

	parse_error(sb, compare->lval->field.raw,
		    "'%s' is not compatible to compare with '%s'\n",
		    compare->lval->field.raw, compare->rval->field.raw);
}

static void compare_no_to_error(struct sqlhist_bison *sb, struct expr *expr)
{
	struct compare *compare = &expr->compare;

	sb->line_no = compare->lval->line;
	sb->line_idx = compare->lval->idx;

	parse_error(sb, compare->lval->field.raw,
		    "Simple SQL (without JOIN/ON) do not allow comparisons\n",
		    compare->lval->field.raw, compare->rval->field.raw);
}

static void where_no_to_error(struct sqlhist_bison *sb, struct expr *expr,
			      const char *from_event, const char *event)
{
	while (expr) {
		switch (expr->filter.type) {
		case FILTER_OR:
		case FILTER_AND:
		case FILTER_GROUP:
		case FILTER_NOT_GROUP:
			expr = expr->filter.lval;
			continue;
		default:
			break;
		}
		break;
	}
	sb->line_no = expr->filter.lval->line;
	sb->line_idx = expr->filter.lval->idx;

	parse_error(sb, expr->filter.lval->field.raw,
		    "Event '%s' does not match FROM event '%s'\n",
		    event, from_event);
}

static int verify_field_type(struct tep_handle *tep,
			     struct sqlhist_bison *sb,
			     struct expr *expr, int *cnt)
{
	struct field *field = &expr->field;
	struct tep_event *event;
	struct tep_format_field *tfield;
	char *type;
	int ret;
	int i;

	if (!field->type)
		return 0;

	sb->line_no = expr->line;
	sb->line_idx = expr->idx;

	event = tep_find_event_by_name(tep, field->system, field->event_name);
	if (!event) {
		parse_error(sb, field->raw,
			    "Event '%s' not found\n",
			    field->event_name ? : "(null)");
		return -1;
	}

	tfield = tep_find_any_field(event, field->field);
	if (!tfield) {
		parse_error(sb, field->raw,
			    "Field '%s' not part of event '%s'\n",
			    field->field ? : "(null)", field->event);
		return -1;
	}

	type = strdup(field->type);
	if (!type)
		return -1;

	if (!strcmp(type, TRACEFS_HIST_COUNTER) ||
	    !strcmp(type, "_COUNTER_")) {
		ret = HIST_COUNTER_TYPE;
		if (tfield->flags & (TEP_FIELD_IS_STRING | TEP_FIELD_IS_ARRAY)) {
			parse_error(sb, field->raw,
				    "'%s' is a string, and counters may only be used with numbers\n");
			ret = -1;
		}
		goto out;
	}

	for (i = 0; type[i]; i++)
		type[i] = tolower(type[i]);

	if (!strcmp(type, "hex")) {
		if (tfield->flags & (TEP_FIELD_IS_STRING | TEP_FIELD_IS_ARRAY))
			goto fail_type;
		ret = TRACEFS_HIST_KEY_HEX;
	} else if (!strcmp(type, "sym")) {
		if (tfield->flags & (TEP_FIELD_IS_STRING | TEP_FIELD_IS_ARRAY))
			goto fail_type;
		ret = TRACEFS_HIST_KEY_SYM;
	} else if (!strcmp(type, "sym-offset")) {
		if (tfield->flags & (TEP_FIELD_IS_STRING | TEP_FIELD_IS_ARRAY))
			goto fail_type;
		ret = TRACEFS_HIST_KEY_SYM_OFFSET;
	} else if (!strcmp(type, "syscall")) {
		if (tfield->flags & (TEP_FIELD_IS_STRING | TEP_FIELD_IS_ARRAY))
			goto fail_type;
		ret = TRACEFS_HIST_KEY_SYSCALL;
	} else if (!strcmp(type, "execname") ||
		   !strcmp(type, "comm")) {
		ret = TRACEFS_HIST_KEY_EXECNAME;
		if (strcmp(field->field, "common_pid")) {
			parse_error(sb, field->raw,
				    "'%s' is only allowed for common_pid\n",
				    type);
			ret = -1;
		}
	} else if (!strcmp(type, "log") ||
		   !strcmp(type, "log2")) {
		if (tfield->flags & (TEP_FIELD_IS_STRING | TEP_FIELD_IS_ARRAY))
			goto fail_type;
		ret = TRACEFS_HIST_KEY_LOG;
	} else if (!strncmp(type, "buckets", 7)) {
		if (type[7] != '=' || !isdigit(type[8])) {
			parse_error(sb, field->raw,
				    "buckets type must have '=[number]' after it\n");
			ret = -1;
			goto out;
		}
		*cnt = atoi(&type[8]);
		if (tfield->flags & (TEP_FIELD_IS_STRING | TEP_FIELD_IS_ARRAY))
			goto fail_type;
		ret = TRACEFS_HIST_KEY_BUCKETS;
	} else {
		parse_error(sb, field->raw,
			    "Cast of '%s' to unknown type '%s'\n",
			    field->raw, type);
		ret = -1;
	}
 out:
	free(type);
	return ret;
 fail_type:
	parse_error(sb, field->raw,
		    "Field '%s' cast to '%s' but is of type %s\n",
		    field->field, type, tfield->flags & TEP_FIELD_IS_STRING ?
		    "string" : "array");
	free(type);
	return -1;
}

static struct tracefs_synth *build_synth(struct tep_handle *tep,
					 const char *name,
					 struct sql_table *table)
{
	struct tracefs_synth *synth;
	struct field *field;
	struct match *match;
	struct expr *expr;
	const char *start_system;
	const char *start_event;
	const char *end_system;
	const char *end_event;
	const char *start_match;
	const char *end_match;
	bool started_start = false;
	bool started_end = false;
	bool non_val = false;
	int ret;

	if (!table->from)
		return NULL;

	/* This could be a simple SQL statement to only build a histogram */
	if (!table->to) {
		ret = update_fields(tep, table, table->from);
		if (ret < 0)
			return NULL;

		start_system = table->from->field.system;
		start_event = table->from->field.event_name;

		synth = synth_init_from(tep, start_system, start_event);
		if (!synth)
			return synth_init_error(tep, table);
		goto hist_only;
	}

	ret = update_vars(tep, table, table->from);
	if (ret < 0)
		return NULL;

	ret = update_vars(tep, table, table->to);
	if (ret < 0)
		return NULL;

	start_system = table->from->field.system;
	start_event = table->from->field.event_name;

	match = table->matches;
	if (!match)
		return NULL;

	ret = test_match(table, match);
	if (ret < 0)
		return NULL;

	end_system = table->to->field.system;
	end_event = table->to->field.event_name;

	assign_match(start_system, start_event, match,
		     &start_match, &end_match);

	synth = tracefs_synth_alloc(tep, name, start_system,
				    start_event, end_system, end_event,
				    start_match, end_match, NULL);
	if (!synth)
		return synth_init_error(tep, table);

	for (match = match->next; match; match = match->next) {
		ret = test_match(table, match);
		if (ret < 0)
			goto free;

		assign_match(start_system, start_event, match,
			     &start_match, &end_match);

		ret = tracefs_synth_add_match_field(synth,
						    start_match,
						    end_match, NULL);
		if (ret < 0) {
			field_match_error(tep, table->sb, match);
			goto free;
		}
	}

 hist_only:
	/* table->to may be NULL here */

	for (expr = table->selections; expr; expr = expr->next) {
		if (expr->type == EXPR_FIELD) {
			ret = -1;
			field = &expr->field;
			if (field->ftype != FIELD_TO &&
			    field->system == start_system &&
			    field->event_name == start_event) {
				int type;
				int cnt = 0;
				type = verify_field_type(tep, table->sb, expr, &cnt);
				if (type < 0)
					goto free;
				if (type != HIST_COUNTER_TYPE)
					non_val = true;
				ret = synth_add_start_field(synth,
						field->field, field->label,
						type, cnt);
			} else if (table->to) {
				ret = tracefs_synth_add_end_field(synth,
						field->field, field->label);
			}
			if (ret < 0) {
				selection_error(tep, table->sb, expr);
				goto free;
			}
			continue;
		}

		if (!table->to) {
			compare_no_to_error(table->sb, expr);
			goto free;
		}

		if (expr->type != EXPR_COMPARE)
			goto free;

		ret = build_compare(synth, start_system, end_system,
				    &expr->compare);
		if (ret < 0) {
			compare_error(tep, table->sb, expr);
			goto free;
		}
	}

	if (!non_val && !table->to) {
		table->sb->line_no = 0;
		table->sb->line_idx = 10;
		parse_error(table->sb, "CAST",
			    "Not all SELECT items can be of type _COUNTER_\n");
		goto free;
	}

	for (expr = table->where; expr; expr = expr->next) {
		const char *filter_system = NULL;
		const char *filter_event = NULL;
		enum field_type ftype = FIELD_NONE;
		bool *started;
		bool start;

		ret = verify_filter(table->sb, &expr->filter, &filter_system,
				    &filter_event, &ftype);
		if (ret < 0)
			goto free;

		start = filter_system == start_system &&
			filter_event == start_event &&
			ftype != FIELD_TO;

		if (start)
			started = &started_start;
		else if (!table->to) {
			where_no_to_error(table->sb, expr, start_event,
					  filter_event);
			goto free;
		} else
			started = &started_end;

		ret = build_filter(tep, table->sb, synth, start, expr, started);
		if (ret < 0)
			goto free;
	}

	return synth;
 free:
	tracefs_synth_free(synth);
	return NULL;
}

static void free_sql_table(struct sql_table *table)
{
	struct match *match;
	struct expr *expr;

	if (!table)
		return;

	while ((expr = table->exprs)) {
		table->exprs = expr->free_list;
		free(expr);
	}

	while ((match = table->matches)) {
		table->matches = match->next;
		free(match);
	}

	free(table);
}

static void free_str_hash(struct str_hash **hash)
{
	struct str_hash *item;
	int i;

	for (i = 0; i < 1 << HASH_BITS; i++) {
		while ((item = hash[i])) {
			hash[i] = item->next;
			free(item->str);
			free(item);
		}
	}
}

static void free_sb(struct sqlhist_bison *sb)
{
	free_sql_table(sb->table);
	free_str_hash(sb->str_hash);
	free(sb->parse_error_str);
}

struct tracefs_synth *tracefs_sql(struct tep_handle *tep, const char *name,
				  const char *sql_buffer, char **err)
{
	struct tracefs_synth *synth = NULL;
	struct sqlhist_bison sb;
	int ret;

	if (!tep || !sql_buffer) {
		errno = EINVAL;
		return NULL;
	}

	memset(&sb, 0, sizeof(sb));

	sb.buffer = sql_buffer;
	sb.buffer_size = strlen(sql_buffer);
	sb.buffer_idx = 0;

	ret = yylex_init_extra(&sb, &sb.scanner);
	if (ret < 0) {
		yylex_destroy(sb.scanner);
		return NULL;
	}

	ret = tracefs_parse(&sb);
	yylex_destroy(sb.scanner);

	if (ret)
		goto free;

	synth = build_synth(tep, name, sb.table);

 free:
	if (!synth) {
		if (sb.parse_error_str && err) {
			*err = sb.parse_error_str;
			sb.parse_error_str = NULL;
		}
	}
	free_sb(&sb);
	return synth;
}
