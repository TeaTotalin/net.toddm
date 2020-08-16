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
import net.toddm.cache.CachePriority;
import net.toddm.cache.CacheProvider;
import net.toddm.cache.DefaultLogger;
import net.toddm.cache.MemoryCacheProvider;
import net.toddm.comm.CacheBehavior;
import net.toddm.comm.CommManager;
import net.toddm.comm.Response;
import net.toddm.comm.Work;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;

public class TestResponse extends TestCase {

	// TODO: Think about alternative ways to do these tests.
	// I love httpbin.org. If it ever goes away, however, I'm in trouble with these test cases.
	// Should probably build some proper unit tests (these are effectively functional tests) and use PowerMockito or something similar.

	public void testInvalidateCache() throws Exception {
		MemoryCacheProvider cache = new MemoryCacheProvider("testInvalidateCache", 20, new DefaultLogger());
		validateInvalidateCache(cache);
	}

	public static void validateInvalidateCache(CacheProvider cache) throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder
				.setName("testInvalidateCache")
				.setCacheProvider(cache)
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/cache/1000"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

        // Verify that we now have a cache entry
        String cacheKey = Integer.toString(work.getId());
        assertTrue(cache.containsKey(cacheKey, false));

        // Invalidate the cache entry and verify that it is considered expired
        commManager.invalidateCache(work.getId());
        assertTrue(cache.containsKey(cacheKey, true));
        assertFalse(cache.containsKey(cacheKey, false));
	}

	public void testPurgeCache() throws Exception {
		MemoryCacheProvider cache = new MemoryCacheProvider("testPurgeCache", 20, new DefaultLogger());
		validatePurgeCache(cache);
	}

	public static void validatePurgeCache(CacheProvider cache) throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder
				.setName("testPurgeCache")
				.setCacheProvider(cache)
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work1 = commManager.enqueueWork(new URI("http://httpbin.org/cache/1000"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work1);
        Response response = work1.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

		Work work2 = commManager.enqueueWork(new URI("http://httpbin.org/cache/2000"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work2);
        response = work2.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

        // Verify that we now have both cache entries
        String cacheKey1 = Integer.toString(work1.getId());
        assertTrue(cache.containsKey(cacheKey1, false));
        String cacheKey2 = Integer.toString(work2.getId());
        assertTrue(cache.containsKey(cacheKey2, false));

        // Purge the first entry and verify that it is no longer in the cache, but that the second entry is still in the cache
        commManager.purgeCache(work1.getId());
        assertFalse(cache.containsKey(cacheKey1, true));
        assertTrue(cache.containsKey(cacheKey2, true));

        // Purge the whole cache and verify that the remaining entry is now gone
        commManager.purgeCache();
        assertFalse(cache.containsKey(cacheKey1, true));
        assertFalse(cache.containsKey(cacheKey2, true));
	}

	public void testGetLocationFromHeadersAbsolute() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Location=https%3A%2F%2Ftoddm.net%2F"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		assertNotNull(work);

		Response response = work.get();
		assertNotNull(response);
		assertEquals(200, (int)response.getResponseCode());
		URI locationHeaderValue = response.getLocationFromHeaders(work.getRequest());
		assertNotNull(locationHeaderValue);
		assertEquals("https://toddm.net/", locationHeaderValue.toString());
	}

	public void testGetLocationFromHeadersRelative() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Location=%2Fget"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
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
		Work work = commManager.enqueueWork(new URI(String.format(Locale.US, "http://httpbin.org/response-headers?ETag=%1$s", eTag)), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
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
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/cache/100"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
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
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=public,+max-age=100,+max-stale=13"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getMaxStaleFromHeaders());
        assertEquals(13000, (long)response.getMaxStaleFromHeaders());		
	}

	public void testShouldNotCacheDueToNoCacheDirective() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=no-cache"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.NORMAL);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertTrue(response.shouldNotCacheDueToNoCacheDirective());
	}

	public void testGetRetryAfterSeconds() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Retry-After=120"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
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
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Retry-After=Fri%2C%2007%20Nov%202088%2023%3A59%3A59%20GMT"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
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
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Retry-After=Fri%2C%2007%20Nov%202014%2023%3A59%3A59%20GMT"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getRetryAfter());
        assertTrue(response.getRetryAfter() < 0);
	}

	// TODO: Find a better way to test 304, this currently requires manually examining the log after running
	public void test304Responses() throws Exception {

		MemoryCacheProvider cache = new MemoryCacheProvider("testCache", 20, new DefaultLogger());
		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder
				.setName("TEST")
				.setCacheProvider(cache)
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/cache"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

        // Update the cache TTL so it's expired
        CacheEntry cacheEntry = cache.get(Integer.toString(work.getId()), true);
        cache.add(cacheEntry.getKey(), cacheEntry.getBytesValue(), 100, 0, cacheEntry.getEtag(), cacheEntry.getUri(), cacheEntry.getPriority());

        Thread.sleep(101);

		work = commManager.enqueueWork(new URI("http://httpbin.org/cache"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work);

        response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
	}

}
