// ***************************************************************************
// *  Copyright 2015 Todd S. Murchison
// *
// *  Licensed under the Apache License, Version 2.0 (the "License");
// *  you may not use this file except in compliance with the License.
// *  You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// *  Unless required by applicable law or agreed to in writing, software
// *  distributed under the License is distributed on an "AS IS" BASIS,
// *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// *  See the License for the specific language governing permissions and
// *  limitations under the License.
// ***************************************************************************
package net.toddm.cache;

import java.util.Locale;

/**
 * A simple {@link LoggingProvider} implementation that sends log messages to {@link System#out} and {@link System#err}.
 * 
 * @author Todd S. Murchison
 */
public class DefaultLogger implements LoggingProvider {

	@Override
	public void info(String msg, Object... msgArgs) {
		System.out.println(String.format(Locale.US, msg, msgArgs));
	}
	@Override
	public void debug(String msg, Object... msgArgs) {
		System.out.println(String.format(Locale.US, msg, msgArgs));
	}
	@Override
	public void error(String msg, Object... msgArgs) {
		System.err.println(String.format(Locale.US, msg, msgArgs));
	}
	@Override
	public void error(Throwable t, String msg, Object... msgArgs) {
		System.err.println(String.format(Locale.US,  "%1$s\r\n%2$s", String.format(Locale.US,  msg, msgArgs), getThrowableDump(t)));
	}

    /** Returns a loggable string for the given {@link Throwable} containing type, message, and stack trace information. */
    public static String getThrowableDump(Throwable throwable) {
        if(throwable == null) { throw(new IllegalArgumentException("'throwable' cannot be null")); }
        return(String.format(Locale.US,
                "%s | %s | %s",
                throwable.getClass().getName(),
                throwable.getMessage(),
                getStackTrace(throwable.getStackTrace())));
    }

    /** Returns a string dump of the provided stack trace */
    public static String getStackTrace(StackTraceElement[] stacks) {
        if(stacks == null) { throw(new IllegalArgumentException("'stacks' cannot be null")); }
        StringBuffer buffer = new StringBuffer();
        for(int i = 0; i < stacks.length; i++) {
            buffer.append(String.format(Locale.US,
                    "%1$s : %2$s : %3$s [%4$d]\n",
                    stacks[i].getFileName(),
                    stacks[i].getClassName(),
                    stacks[i].getMethodName(),
                    stacks[i].getLineNumber()));
        }
        return(buffer.toString());
    }

}
