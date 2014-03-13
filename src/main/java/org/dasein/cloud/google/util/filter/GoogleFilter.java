package org.dasein.cloud.google.util.filter;

import com.google.common.base.Predicate;

/**
 * TODO: draft version
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
