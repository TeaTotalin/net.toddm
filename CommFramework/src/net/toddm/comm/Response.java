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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Todd S. Murchison
 */
public class Response implements Serializable {
	private static final long serialVersionUID = -6722104702458701972L;

	private static final Logger _Logger = LoggerFactory.getLogger(Response.class.getSimpleName());

	/** {@serial} */
	private byte[] _responseBytes;
	/** {@serial} */
	private int _responseCode;
	/** {@serial} */
	private int _requestId;
	/** {@serial} */
	private int _responseTime = -1;
	/** {@serial} */
	private Map<String, List<String>> _headers;

	private long _instanceCreationTime = System.currentTimeMillis();

	protected Response(byte[] responseBytes, Map<String, List<String>> headers, int responseCode, int requestId, int responseTime) {
		this._responseBytes = responseBytes;
		if(headers != null) {

			// Ensure that we use a Map implementation that can be serialized no matter what type of Map is passed in
			this._headers = new HashMap<String, List<String>>(headers);
		} else {
			this._headers = new HashMap<String, List<String>>();
		}
		this._responseCode = responseCode;
		this._requestId = requestId;
		this._responseTime = responseTime;
	}
	
	public byte[] getResponseBytes() {
		return(this._responseBytes);
	}

	public String getResponseBody() {
		String result = null;
		try {
			result = new String(this._responseBytes, "UTF-8");
		} catch(UnsupportedEncodingException uee) {
			_Logger.debug("Response encoding as string failed");  // No-op OK
		}
		return(result);
	}

	public Integer getResponseCode() {
		return(this._responseCode);
	}

	/**
	 * Returns <b>true</b> if this {@link Response} instance has an HTTP response 
	 * code that we consider to indicate "success". Currently this either 200 or 201.
	 */
	public boolean isSuccessful() {
		return((this._responseCode == 200) || (this._responseCode == 201));
	}

	/** Returns the unique ID of the {@link Request} that generated this {@link Response}. */
	public Integer getRequestId() {
		return(this._requestId);
	}

	/** The HTTP header values from the response. */
	public Map<String, List<String>> getHeaders() { return(this._headers); }

	/** Returns the epoch timestamp in milliseconds of when this instance of {@link Response} was created. */
	protected long getInstanceCreationTime() { return(this._instanceCreationTime); }

	//*********************************************************************************************
	// Header parsing helpers

	/** Returns the value for the 'Content-Encoding' header from the given header collection, or NULL if no value can be resolved. */
	public static String getContentEncoding(Map<String, List<String>> headers) {
		String contentEncoding = null;
		try {
            if(headers.containsKey("Content-Encoding")) {
				contentEncoding = headers.get("Content-Encoding").get(0);
			}
		} catch(Exception e) {
			_Logger.error("Failed to parse value from 'Content-Encoding' header", e);  // No-op OK
		}
		return(contentEncoding);
	}

	/**
	 * If we are able to resolve a Location value form the headers of this {@link Response} instance and parse that value to a {@link URI}
	 * then the Location value is returned, otherwise NULL is returned. This method resolves any relative redirects to absolute redirects.
	 * <p>
	 * @param request The {@link Request} that resulted in this {@link Response}. This is used for rewriting relative URLs if needed.
	 * @return The {@link URI} that the HTTP response headers indicates should be redirected to.
	 */
	public URI getLocationFromHeaders(Request request) {
		if(request == null) { throw(new IllegalArgumentException("'request' can not be NULL")); }
		URI location = null;
		try {

			if((this._headers != null) && (this._headers.containsKey("Location")) && (this._headers.get("Location") != null) && (this._headers.get("Location").size() > 0)) {
				String locationStr = this._headers.get("Location").get(0);
				location = new URI(locationStr);

				// Rewrite URI as absolute if needed
				if(locationStr.trim().startsWith("/")) {
					location = new URI(
						request.getUri().getScheme(), 
						location.getUserInfo(), 
						request.getUri().getHost(), 
						location.getPort(), 
						location.getPath(), 
						request.getUri().getQuery(), 
						location.getFragment());
				}
			}

		} catch(Exception e) {
			_Logger.error("Failed to parse value from 'Location' header", e);  // No-op OK
		}
		return(location);
	}

	/**
	 * If we are able to resolve a Retry-After value form the headers of this {@link Response} instance then the Retry-After value 
	 * is returned, otherwise NULL is returned. The Retry-After value returned is in seconds. This method does support 'HTTP-date' 
	 * values for Retry-After, but they are interpreted in relation to the current time and a simple value in seconds is still returned.
	 */
	public Long getRetryAfter() {

		// Extract the "Retry-After" header (only support delta in seconds for now)
		Long retryInSeconds = null;
		if((this._headers != null) && (this._headers.containsKey("Retry-After")) && (this._headers.get("Retry-After").size() > 0)) {
			String retryAfter = "";
			try {

				// Attempt to parse the value as a long first
				retryAfter = this._headers.get("Retry-After").get(0);
				retryInSeconds = Long.parseLong(retryAfter);

			} catch(Exception e) {
				try {

					// Parsing as a long failed so attempt to parse the value as an HTTP-date instead.
					// The constructor in the line below is expensive, but the parse call is not thread-safe, and we don't expect to land here very often, so...
					SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
					Date httpDate = httpDateFormat.parse(retryAfter);
					retryInSeconds = ((httpDate.getTime() - System.currentTimeMillis()) / 1000);

				} catch(Exception f) {
					_Logger.error("Failed to parse value from 'Retry-After' header", f);  // No-op OK
				}
			}
		}
		return(retryInSeconds);
	}

	/**
	 * If we are able to resolve a TTL value form the Cache-Control headers of this {@link Response} instance
	 * then the TTL value is returned, otherwise NULL is returned. The TTL value returned is in milliseconds.
	 */
	public Long getTtlFromHeaders() {
		Long ttl = null;

		// Parse caching data from the headers (Cache-Control=no-cache, max-age={delta-seconds}):
		//	Example:   Cache-Control=max-age=2300, public
		if((this._headers != null) && (this._headers.containsKey("Cache-Control")) && (this._headers.get("Cache-Control") != null) && (this._headers.get("Cache-Control").size() > 0)) {
			String cacheControl = null;
			for(String value : this._headers.get("Cache-Control")) {
				cacheControl = value;
				break;
			}
			if((cacheControl != null) && (cacheControl.length() > 0)) {
				for(String cacheDirective : cacheControl.split(",")) {
					if(cacheDirective == null) { continue; }
					cacheDirective = cacheDirective.trim();

					// Check if we are not caching and if we are not then bail
					if("no-cache".equalsIgnoreCase(cacheDirective)) { return(null); }

					// Check for the max-age directive
					String[] directivePair = cacheDirective.split("=");
					if(	(directivePair.length > 1) && 
						(directivePair[0] != null) && 
						(directivePair[1] != null) && 
						("max-age".equalsIgnoreCase(directivePair[0].trim()))) 
					{
						try {
							long ttlInSeconds = Long.parseLong(directivePair[1].trim());
							if(ttlInSeconds >= 0) { ttl = ttlInSeconds * 1000; }
						} catch(NumberFormatException e) {
							_Logger.error("getTtlFromResponse() failed", e);
						}
					}
				}
			}
		}
		return(ttl);
	}

	/** If we are able to resolve an ETag value form the headers of this {@link Response} instance then the ETag value is returned, otherwise NULL is returned. */
	public String getETagFromHeaders() {
		String eTag = null;
		if((this._headers != null) && (this._headers.containsKey("ETag")) && (this._headers.get("ETag") != null) && (this._headers.get("ETag").size() > 0)) {
			eTag = this._headers.get("ETag").get(0);
		}
		return(eTag);
	}

	//*********************************************************************************************
	// Serializable implementation

	/** For the implementation of {@link Serializable}. */
	private void writeObject(java.io.ObjectOutputStream out) throws IOException {

		out.writeInt(this._responseCode);
		out.writeInt(this._requestId);
		out.writeInt(this._responseTime);
		out.writeObject(this._headers);

		// Add the byte array last if there is one
		if(this._responseBytes != null) {
			out.write(this._responseBytes);
		}
	}

	/** For the implementation of {@link Serializable}. */
	@SuppressWarnings("unchecked")
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {

		this._responseCode = in.readInt();
		this._requestId = in.readInt();
		this._responseTime = in.readInt();
		this._headers = (Map<String, List<String>>)in.readObject();

		// Any remaining bytes are the response body (that's why we add it last)
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int bytesRead = 0;
		while((bytesRead = in.read(buffer)) != -1) {
			outStream.write(buffer, 0, bytesRead);
		}
		this._responseBytes = null;
		if(outStream.size() > 0) {
			this._responseBytes = outStream.toByteArray();
		}
		
		this._instanceCreationTime = System.currentTimeMillis();
	}

}
