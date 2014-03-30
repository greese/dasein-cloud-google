package org.dasein.cloud.google.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Initially was added by George Reese in {@link  org.dasein.cloud.google.Google}
 * <p> Probably is not needed any more
 */
public final class GoogleLogger {

	private GoogleLogger() {
		throw new AssertionError();
	}

	public static @Nonnull Logger getLogger(@Nonnull Class<?> cls) {
		String pkg = getLastItem(cls.getPackage().getName());

		if (pkg.equals("google")) {
			pkg = "";
		} else {
			pkg = pkg + ".";
		}
		return LoggerFactory.getLogger("dasein.cloud.google.std." + pkg + getLastItem(cls.getName()));
	}

	public static @Nonnull Logger getWireLogger(@Nonnull Class<?> cls) {
		return LoggerFactory.getLogger("dasein.cloud.google.wire." + getLastItem(cls.getPackage().getName())
				+ "." + getLastItem(cls.getName()));
	}

	private static @Nonnull String getLastItem(@Nonnull String name) {
		int idx = name.lastIndexOf('.');
		if (idx < 0) {
			return name;
		} else if (idx == (name.length() - 1)) {
			return "";
		}
		return name.substring(idx + 1);
	}

}
