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

/**
 * An exception type thrown by the caching framework.
 * <p>
 * @author Todd S. Murchison
 */
public class CacheException extends RuntimeException {
	private static final long serialVersionUID = -7753214565977058690L;

	/** See {@link RuntimeException#RuntimeException()}. */
	public CacheException() { super(); }

	/** See {@link RuntimeException#RuntimeException(String)}. */
	public CacheException(String msg) { super(msg); }

	/** See {@link RuntimeException#RuntimeException(Throwable)}. */
	public CacheException(Throwable e) { super(e); }

	/** See {@link RuntimeException#RuntimeException(String, Throwable)}. */
	public CacheException(String msg, Throwable e) { super(msg, e); }

}
