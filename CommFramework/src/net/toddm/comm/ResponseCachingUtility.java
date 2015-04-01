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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A static utility class that provides helper methods handy when caching request responses.
 * <p>
 * @author Todd S. Murchison
 */
public final class ResponseCachingUtility {
	private ResponseCachingUtility() {}

	private static final Logger _Logger = LoggerFactory.getLogger(ResponseCachingUtility.class.getSimpleName());

	/**
	 * If we are able to resolve a TTL value form the given {@link Response} Cache-Control headers 
	 * then the TTL value is returned, otherwise NULL is returned. The TTL value returned is in milliseconds.
	 */
	public static Long getTtlFromResponse(Response response) {
		Long ttl = null;
		if((response != null) && (response.getHeaders() != null)) {

			// Parse caching data from the headers (Cache-Control=no-cache, max-age={delta-seconds}):
			//	Example:   Cache-Control=max-age=2300, public
			if((response.getHeaders().containsKey("Cache-Control")) && (response.getHeaders().get("Cache-Control") != null) && (response.getHeaders().get("Cache-Control").size() > 0)) {
				String cacheControl = null;
				for(String value : response.getHeaders().get("Cache-Control")) {
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
								_Logger.error("ResponseCachingUtility.getTtlFromResponse() failed", e);
							}
						}
					}
				}
			}
        }
		return(ttl);
	}

	/** If we are able to resolve an ETag value form the given {@link Response} headers then the ETag value is returned, otherwise NULL is returned. */
	public static String getETagFromResponse(Response response) {		
		String eTag = null;
		if((response != null) && (response.getHeaders() != null)) {
			if((response.getHeaders().containsKey("ETag")) && (response.getHeaders().get("ETag") != null) && (response.getHeaders().get("ETag").size() > 0)) {
				eTag = response.getHeaders().get("ETag").get(0);
			}
		}
		return(eTag);
	}

}
