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
 * Retry policy providers implement this interface in order to control retry behavior such as if 
 * and when a request should be retried after a network failure, retry frequencies and limits, etc.
 * <p>
 * @author Todd S. Murchison
 */
public interface RetryPolicyProvider {

	/**
	 * Called by the {@link CommManager} when network request work results in an exception.
	 * The {@link RetryProfile} instance returned is used to determine if and how the request should be retried.
	 * @param request The {@link Request} that resulted in the given {@link Exception}.
	 * @param error The {@link Exception} thrown while attempting the given {@link Request} work.
	 * @return A {@link RetryProfile} instance used to determine if and how the request should be retried.
	 */
	public RetryProfile shouldRetry(Request request, Exception error);

	/**
	 * Called by the {@link CommManager} when network request work results in a response back from the remote resource.
	 * The {@link RetryProfile} instance returned is used to determine if and how the request should be retried.
	 * @param request The {@link Request} that resulted in the given {@link Response}.
	 * @param response The {@link Response} instance resulting from the given {@link Request} work.
	 * @return A {@link RetryProfile} instance used to determine if and how the request should be retried.
	 */
	public RetryProfile shouldRetry(Request request, Response response);

}
