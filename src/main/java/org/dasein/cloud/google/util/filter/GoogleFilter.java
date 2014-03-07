package org.dasein.cloud.google.util.filter;

import com.google.common.base.Predicate;

/**
 * According to google documentation searching can be done using the following expression, which must contain the following:
 *
 * <field_name> <comparison_string> <literal_string>
 *
 * <field_name>: The name of the field you want to compare. The field name must be valid for the type of resource being filtered. Only
 * atomic field types are supported (string, number, boolean). Array and object fields are not currently supported.
 * <comparison_string>: The comparison string, either eq (equals) or ne (not equals).
 * <literal_string>: The literal string value to filter to. The literal value must
 * be valid for the type of field (string, number, boolean). For string fields, the literal value is interpreted as a regular expression
 * using RE2 syntax. The literal value must match the entire field. For example, when filtering instances, name eq my_instance won't work,
 * but name eq .*my_instance will work.
 *
 * @author igoonich
 * @since 05.03.2014
 */
public abstract class GoogleFilter<T> implements Predicate<T> {

	public boolean isStringFilter() {
		return false;
	}

	public String asFilterString() {
		throw new UnsupportedOperationException("Filter [" + getClass().getName() + "] cannot be converted to google filter");
	}

}
