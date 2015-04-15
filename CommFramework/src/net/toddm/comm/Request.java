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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Todd S. Murchison
 */
public class Request {

	// TODO: Store the end-point for a request (the URI) in a set of unique values.
	// This allows us to maintain a redirect location history and guards against cyclical redirection.
	
	// TODO: Enforce a maximum total number of redirects allowed

	/** HTTP request methods supported by the comm framework. */
	public enum RequestMethod {
		/** Standard HTTP GET */
		GET, 
		/** Standard HTTP POST */
		POST
		//HEAD, 
		//OPTIONS, 
		//PUT, 
		//DELETE, 
		//TRACE
	}

	// TODO: Think about redirect support.
	// Do we want to change the URI of an existing request while maintaining the ID or do we want to generate a new Request instance?
	private URI _normalizedUri;
	private final RequestMethod _method;
	private final byte[] _postData;
	private final Map<String, String> _headers;
	private final Map<String, List<String>> _queryParameters;
	private final Integer _id;
	private final boolean _cachingAllowed;

	private int _retryCountFromFailure = 0;
	private int _retryCountFromResponse = 0;

	/** 
	 * Creates an instance of {@link Request}.
	 * <p>
	 * @param uri The URI for the resource to request.
	 * @param method The HTTP method for the request ({@link RequestMethod}).
	 * @param postData <b>OPTIONAL</b>. Can be NULL. A map of data to be sent as the POST body content. The values should NOT yet be encoded.
	 * @param headers A {@link Map} of any HTTP header values that should be sent as part of the request.
	 * @param cachingAllowed If set, the CommManager is allowed to cache the results of this request.
	 */
	protected Request(
			URI uri, 
			RequestMethod method, 
			byte[] postData, 
			Map<String, String> headers, 
			boolean cachingAllowed) 
	{

		// Validate parameters
		if(uri == null) { throw(new IllegalArgumentException("'uri' can not be NULL")); }
		if(method == null) { throw(new IllegalArgumentException("'method' can not be NULL")); }
		if((postData != null) && (postData.length > 0) && (!Request.RequestMethod.POST.equals(method))) {
			throw(new IllegalArgumentException("'method' must be 'POST' when 'postData' is provided"));
		}

		// Set members
		this._method = method;
		this._normalizedUri = uri.normalize();
		this._cachingAllowed = cachingAllowed;

		// Used for hash code, ID, etc. Use extreme caution if changing.
		this._queryParameters = Request.parseQueryParameters(this._normalizedUri, "UTF-8");
		this._postData = postData;
		this._headers = headers;

		// Calculate the ID for this request
		this._id = this.calculateId();
	}

	/** Returns the HTTP method of this {@link Request} instance. */
	public RequestMethod getMethod() {
		return(this._method);
	}

	/** Returns the POST data of this {@link Request} instance or <b>null</b> if there is no POST data. */
	public byte[] getPostData() {
		return(this._postData);
	}

	/** Returns the headers of this {@link Request} instance or <b>null</b> if there are none. */
	public Map<String, String> getHeaders() {
		return(this._headers);
	}

	/** If <b>true</b> the results of this {@link Request} instance can participate in caching. */
	public boolean isCachingAllowed() {
		return(this._cachingAllowed);
	}

	/** Returns the URI of this {@link Request} instance. */
	public URI getUri() {
		return(this._normalizedUri);
	}

	/**
	 * The ID of this {@link Request}. Requests have the same ID and are considered equal if their 
	 * URLs are the same, regardless of parameter order, and their POST bodies are the same.
	 */
	public int getId() {
		if(this._id == null) { throw(new IllegalStateException("The ID has not been calculated yet")); }
		return(this._id.intValue());
	}

	/** Increments the count of the total number of times this request has been retried due to a failure (socket timeout, etc.). */
	protected void incrementRetryCountFromFailure() {
		this._retryCountFromFailure++;
	}

	/** Increments the count of the total number of times this request has been retried due to a response (503 response code, etc.). */
	protected void incrementRetryCountFromResponse() {
		this._retryCountFromResponse++;
	}

	/** Returns the total number of times this request has been retried due to a failure (socket timeout, etc.). */
	public int getRetryCountFromFailure() { return(this._retryCountFromFailure); }

	/** Returns the total number of times this request has been retried due to a response (503 response code, etc.). */
	public int getRetryCountFromResponse() { return(this._retryCountFromResponse); }

	/**
	 * The hash code of this {@link Request}. Requests have the same hash code and are considered equal 
	 * if their URLs are the same, regardless of parameter order, and their POST bodies are the same.
	 */
	@Override
	public int hashCode() {
		return(this.getId());
	}

	/**
	 * Returns TRUE if this {@link Request} instance is equivalent to the given {@link Request} instance. Requests are 
	 * considered equal if their URLs are the same, regardless of parameter order, and their POST bodies are the same.
	 */
	@Override
	public boolean equals(Object request) {
		if(request == null) { throw(new IllegalArgumentException("'request' can not be NULL")); }
		if(!(request instanceof Request)) { return(false); }
		return(this.hashCode() == request.hashCode());
	}

	/**
	 * Calculates and returns the unique ID value for this Request.
	 * <p>
	 * The ID of a {@link Request} instances is based on the URL of the request and POST body (if there is one), therefore 
	 * two requests with the same URL values and POST bodies will also have the same ID values and will be considered equal.
	 * Query parameter <b>order</b> is not considered.
	 */
	private int calculateId() {

		// Deterministically build a text BLOB to hash for this request's ID
		StringBuilder idSourceBuffer = new StringBuilder();
		idSourceBuffer.append(this._normalizedUri.getScheme());
		idSourceBuffer.append(this._normalizedUri.getHost());
		idSourceBuffer.append(this._normalizedUri.getPath());

		// _queryParameters is a TreeMap, so parameters are already in a consistent, sorted order
		for(String key : this._queryParameters.keySet()) {
			idSourceBuffer.append(key);
			for(String value : this._queryParameters.get(key)) {
				idSourceBuffer.append(value);
			}
		}

		// If we have any POST or anchor data, add it
		idSourceBuffer.append(this._normalizedUri.getFragment());
		if((this._postData != null) && (this._postData.length > 0)) {

			// Base64 encode the post data bytes to make some safe text
			idSourceBuffer.append(Base64.encode(this._postData));
		}

		// Although it simply should never be possible to have a Request instance without a URI we will go ahead
		// and use the fact that we are also including the port number of the request in the ID hash to support
		// not failing here if we have nothing else.
		String idSourceText = idSourceBuffer.toString();
		if((idSourceText == null) || (idSourceText.length() <= 0)) {
			return(this._normalizedUri.getPort());
		} else {
			return(idSourceText.hashCode() + this._normalizedUri.getPort());
		}
	}

	/**
	 * A bit of boiler plate URL query parameter parsing. Handles multiple occurrences of the same parameter name. Handles 
	 * parameters with no value (value is returned as NULL). Results of this method have been correctly URL decoded.
	 * <p>
	 * This method is here to avoid needing an external library (such as <i>org.apache.http</i>, etc.) for maximum compatibility.
	 * <p>
	 * {@link UnsupportedEncodingException} is recast as {@link RuntimeException} to avoid Java's largely useless Exception declarations. 
	 * <p>
	 * @param uri The URI to parse query parameters from.
	 * @param encoding The encoding to use when URL decoding text (example: "UTF-8").
	 */
	private static TreeMap<String, List<String>> parseQueryParameters(URI uri, String encoding) {
		if(uri == null) { throw(new IllegalArgumentException("'uri' can not be NULL")); }
		if((encoding == null) || (encoding.length() <= 0)) { throw(new IllegalArgumentException("'encoding' can not be NULL or empty")); }
		try {

			TreeMap<String, List<String>> queryPairs = new TreeMap<String, List<String>>();
			String rawQuery = uri.getRawQuery();
			if((rawQuery != null) && (rawQuery.length() > 0)) {
				String[] pairs = rawQuery.split("&");
				for(String pair : pairs) {
					int equalsIndex = pair.indexOf("=");
					String key = equalsIndex > 0 ? URLDecoder.decode(pair.substring(0, equalsIndex), encoding) : pair;
					if(!queryPairs.containsKey(key)) {
						queryPairs.put(key, new LinkedList<String>());
					}
					String value = ((equalsIndex > 0) && (pair.length() > equalsIndex + 1)) ? 
										URLDecoder.decode(pair.substring(equalsIndex + 1), encoding) : 
										null;
					queryPairs.get(key).add(value);
				}
			}
			return(queryPairs);

		} catch(UnsupportedEncodingException e) {
			throw(new RuntimeException(e));
		}
	}

}
