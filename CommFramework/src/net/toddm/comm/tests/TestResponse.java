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
package net.toddm.comm.tests;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import junit.framework.TestCase;
import net.toddm.cache.CacheEntry;
import net.toddm.cache.DefaultLogger;
import net.toddm.comm.CommManager;
import net.toddm.comm.Response;
import net.toddm.comm.Work;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;

public class TestResponse extends TestCase {

	// TODO: Think about alternative ways to do these tests.
	// I love httpbin.org. If it ever goes away, however, I'm in trouble with these test cases.

	public void testGetLocationFromHeadersAbsolute() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Location=http%3A%2F%2Fwww.toddm.net%2F"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, CacheEntry.Priority.DO_NOT_CACHE);
		assertNotNull(work);

		Response response = work.get();
		assertNotNull(response);
		assertEquals(200, (int)response.getResponseCode());
		URI locationHeaderValue = response.getLocationFromHeaders(work.getRequest());
		assertNotNull(locationHeaderValue);
		assertEquals("http://www.toddm.net/", locationHeaderValue.toString());
	}

	public void testGetLocationFromHeadersRelative() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Location=%2Fget"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, CacheEntry.Priority.DO_NOT_CACHE);
		assertNotNull(work);

		Response response = work.get();
		assertNotNull(response);
		assertEquals(200, (int)response.getResponseCode());
		URI locationHeaderValue = response.getLocationFromHeaders(work.getRequest());
		assertNotNull(locationHeaderValue);

		// Our URI rewrite preserves the query string from the original request, so...
		assertEquals("http://httpbin.org/get?Location=/get", locationHeaderValue.toString());
	}

	public void testGetETagFromHeaders() throws Exception {

		String eTag = UUID.randomUUID().toString();

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI(String.format(Locale.US, "http://httpbin.org/response-headers?ETag=%1$s", eTag)), RequestMethod.GET, null, null, StartingPriority.MEDIUM, CacheEntry.Priority.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getETagFromHeaders());
        assertEquals(eTag, response.getETagFromHeaders());
	}

	public void testGetTtlFromHeaders() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/cache/100"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, CacheEntry.Priority.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getTtlFromHeaders());
        assertEquals(100000, (long)response.getTtlFromHeaders());		
	}

	public void testGetMaxStaleFromHeaders() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=public,+max-age=100,+max-stale=13"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, CacheEntry.Priority.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getMaxStaleFromHeaders());
        assertEquals(13000, (long)response.getMaxStaleFromHeaders());		
	}

	public void testGetRetryAfterSeconds() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Retry-After=120"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, CacheEntry.Priority.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getRetryAfter());
        assertEquals(120, (long)response.getRetryAfter());
	}

	public void testGetRetryAfterHTTPDateInTheFuture() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Retry-After=Fri%2C%2007%20Nov%202088%2023%3A59%3A59%20GMT"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, CacheEntry.Priority.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getRetryAfter());
        assertTrue(response.getRetryAfter() > 0);
	}

	public void testGetRetryAfterHTTPDateInThePast() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Retry-After=Fri%2C%2007%20Nov%202014%2023%3A59%3A59%20GMT"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, CacheEntry.Priority.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getRetryAfter());
        assertTrue(response.getRetryAfter() < 0);
	}

}
