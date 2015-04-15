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

	/** {@inheritDoc} */
	@Override
	public RetryProfile shouldRetry(Request request, Exception error) {

		// Decide if we should retry the request based on the Exception type
		boolean shouldRetry = false;
		if(	error instanceof ConnectException || 
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
			error instanceof SSLPeerUnverifiedException )
		{
			shouldRetry = true;
		}

		// TODO: Support retry for some exceptions
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public RetryProfile shouldRetry(Request request, Response response) {
		if(response == null) { throw(new IllegalArgumentException("'response' can not be NULL")); }
		
		// Support Retry-After header for 503 and 202 response codes
		if((response.getResponseCode() == 503) || (response.getResponseCode() == 202)) {

			// Default to 3 seconds as a seemingly sane, but totally random default
			long retryInSeconds = 3;

			// Extract the "Retry-After" header (only support delta in seconds for now)
			if(response.getHeaders().containsKey("Retry-After")) {
				try {
					String retryAfter = response.getHeaders().get("Retry-After").get(0);
					retryInSeconds = Long.parseLong(retryAfter);
				} catch(Exception e) {
					_Logger.error("Failed to parse value from 'Retry-After' header", e);
				}
			}
			if(_Logger.isTraceEnabled()) {
				_Logger.trace("Retrying request {} in {} seconds due to {}", new Object[] { response.getRequestId(), retryInSeconds, response.getResponseCode() });
			}
		}

		// TODO: Support retry for some responses
		return null;
	}

}
