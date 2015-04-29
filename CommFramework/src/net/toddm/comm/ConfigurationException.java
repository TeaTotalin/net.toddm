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
package net.toddm.comm;

/**
 * An exception type thrown by configuration providers.
 * <p>
 * @author Todd S. Murchison
 */
public class ConfigurationException extends RuntimeException {
	private static final long serialVersionUID = -5806421321038017661L;

	/** See {@link RuntimeException#RuntimeException()}. */
	public ConfigurationException() { super(); }

	/** See {@link RuntimeException#RuntimeException(String)}. */
	public ConfigurationException(String msg) { super(msg); }

	/** See {@link RuntimeException#RuntimeException(Throwable)}. */
	public ConfigurationException(Throwable e) { super(e); }

	/** See {@link RuntimeException#RuntimeException(String, Throwable)}. */
	public ConfigurationException(String msg, Throwable e) { super(msg, e); }

}
