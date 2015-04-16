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
 * Returned by {@link RetryPolicyProvider} implementers to describe needed 
 * retry work (indicate if a retry should be attempted and how long to wait).
 * <p>
 * @author Todd S. Murchison
 */
public class RetryProfile {

	private final boolean _shouldRetry;
	private final long _retryAfterMilliseconds;

	/** Constructs an instance of {@link RetryProfile} with the given values. */
	protected RetryProfile(boolean shouldRetry, long retryAfterMilliseconds) {
		this._shouldRetry = shouldRetry;
		this._retryAfterMilliseconds = retryAfterMilliseconds;
	}

	/** Returns a flag indicating if it is recommended to retry a {@link Request}. */
	public boolean shouldRetry() { return(this._shouldRetry); }

	/** Returns the number of milliseconds that it is recommended to wait before retrying a {@link Request}. */
	public long getRetryAfterMilliseconds() { return(this._retryAfterMilliseconds); }

}
