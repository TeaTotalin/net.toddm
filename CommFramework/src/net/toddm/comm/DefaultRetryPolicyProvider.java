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

import java.net.BindException;
import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple implementation of {@link RetryPolicyProvider} that provides basic support for 503 and 202 based retries 
 * and error retries for a sub-set of exceptions where attempting the request again later seems to make sense.
 * <p>
 * @author Todd S. Murchison
 */
public class DefaultRetryPolicyProvider implements RetryPolicyProvider {

	private static final Logger _Logger = LoggerFactory.getLogger(DefaultRetryPolicyProvider.class.getSimpleName());

	/** Maximum number of times that this policy will recommend retrying a request the has failed due to error. */
	private static final int _MaxErrorRetries = 5;

	/** Maximum number of times that this policy will recommend retrying a request the has been told by the response to retry. */
	private static final int _MaxResponseRetries = 5;

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation provides basic support for rapid Exception based retry. If the provided {@link Exception} is a type 
	 * that may be caused by transiient network blips then this implementation recommends retry after a short 3 second interval.
	 */
	@Override
	public RetryProfile shouldRetry(Request request, Exception error) {
		if(request == null) { throw(new IllegalArgumentException("'request' can not be NULL")); }
		if(error == null) { throw(new IllegalArgumentException("'error' can not be NULL")); }

		// Decide if we should retry the request based on the number of retries already attempted and the Exception type
		boolean shouldRetry = false;
		if(	(request.getRetryCountFromFailure() < _MaxErrorRetries) && 
			(	error instanceof ConnectException || 
				error instanceof SocketException || 
				error instanceof SocketTimeoutException || 
				error instanceof UnknownHostException || 
				error instanceof BindException || 
				error instanceof NoRouteToHostException || 
				error instanceof PortUnreachableException || 
				error instanceof UnknownServiceException || 
				error instanceof HttpRetryException || 
				error instanceof ProtocolException || 
				error instanceof SSLException || 
				error instanceof SSLHandshakeException || 
				error instanceof SSLProtocolException || 
				error instanceof SSLKeyException || 
				error instanceof SSLPeerUnverifiedException ) )
		{
			shouldRetry = true;
			_Logger.trace("Recommending request {} be retried in 3 seconds due to {}", request.getId(), error.getClass().getSimpleName());
		}

		// For Exception cases we will use a rapid retry interval of 3 seconds (hope for transient network blip)
		return(new RetryProfile(shouldRetry, 3000));
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation provides basic support for 503 and 202 response codes.
	 * If no 'Retry-After' header is found a default retry interval of 5 seconds is used.
	 */
	@Override
	public RetryProfile shouldRetry(Request request, Response response) {
		if(request == null) { throw(new IllegalArgumentException("'request' can not be NULL")); }
		if(response == null) { throw(new IllegalArgumentException("'response' can not be NULL")); }

		// Support Retry-After header for 503 and 202 response codes
		boolean shouldRetry = false;
		long retryInSeconds = 5;  // Default to 5 seconds as a random but seemingly sane default
		if(	(request.getRetryCountFromResponse() < _MaxResponseRetries) && 
			(	(response.getResponseCode() == 503) || 
				(response.getResponseCode() == 202)) ) 
		{

			// Extract the "Retry-After" header (only support delta in seconds for now)
			shouldRetry = true;
			if(response.getHeaders().containsKey("Retry-After")) {
				try {
					String retryAfter = response.getHeaders().get("Retry-After").get(0);
					retryInSeconds = Long.parseLong(retryAfter);
				} catch(Exception e) {
					_Logger.error("Failed to parse value from 'Retry-After' header", e);  // No-op OK
				}
			}
			if(_Logger.isTraceEnabled()) {
				_Logger.trace("Recommending request {} be retried in {} seconds due to {}", new Object[] { request.getId(), retryInSeconds, response.getResponseCode() });
			}
		}

		// Convert the retry interval to milliseconds and return
		return(new RetryProfile(shouldRetry, retryInSeconds * 1000));
	}

}
