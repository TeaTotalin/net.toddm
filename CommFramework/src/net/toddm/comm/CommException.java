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
 * An exception type thrown by the communications framework.
 * <p>
 * @author Todd S. Murchison
 */
public class CommException extends RuntimeException {
	private static final long serialVersionUID = 8605780860152295905L;

	/** See {@link RuntimeException#RuntimeException()}. */
	public CommException() { super(); }

	/** See {@link RuntimeException#RuntimeException(String)}. */
	public CommException(String msg) { super(msg); }

	/** See {@link RuntimeException#RuntimeException(Throwable)}. */
	public CommException(Throwable e) { super(e); }

	/** See {@link RuntimeException#RuntimeException(String, Throwable)}. */
	public CommException(String msg, Throwable e) { super(msg, e); }

}
