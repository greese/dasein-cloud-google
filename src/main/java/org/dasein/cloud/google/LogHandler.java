
package org.dasein.cloud.google;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

import javax.annotation.Nonnull;
import org.apache.log4j.Logger;

import com.google.api.client.http.HttpTransport;

public class LogHandler {
    private static java.util.logging.Logger logger;
    private LogHandler() { }

    static public void verifyInitialized() {

        if (null == logger) {
            final Logger wire = getWireLogger(HttpTransport.class);
            if (wire.isDebugEnabled()) {
                logger = java.util.logging.Logger.getLogger(HttpTransport.class.getName());
                logger.setLevel(java.util.logging.Level.CONFIG);
                logger.addHandler(new Handler() {
                    @Override public void publish( LogRecord record ) {
                        String msg = record.getMessage();
                        if (msg.startsWith("-------------- REQUEST")) {
                            String [] lines = msg.split("[\n\r]+");
                            for (String line : lines)
                                if ((line.contains("https")) || (line.contains("Content-Length")))
                                    wire.debug("--> REQUEST: " + line);
                        } else if (msg.startsWith("{"))
                            wire.debug(msg);
                        else if (msg.startsWith("Total"))
                            wire.debug("<-- RESPONSE: " + record.getMessage());
                    }
                    @Override public void flush() {}
                    @Override public void close() throws SecurityException {}
                });
            }
        }

    }

    static private @Nonnull String getLastItem(@Nonnull String name) {
        int idx = name.lastIndexOf('.');

        if( idx < 0 ) {
            return name;
        }
        else if( idx == (name.length()-1) ) {
            return "";
        }
        return name.substring(idx + 1);
    }

    static public @Nonnull Logger getWireLogger(@Nonnull Class<?> cls) {
        return Logger.getLogger("dasein.cloud.google.wire." + getLastItem(cls.getPackage().getName()) + "." + getLastItem(cls.getName()));
    }
}
