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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.toddm.cache.CachePriority;
import net.toddm.cache.CacheProvider;
import net.toddm.cache.MemoryCacheProvider;
import net.toddm.cache.DefaultLogger;
import net.toddm.comm.CacheBehavior;
import net.toddm.comm.CommManager;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.Response;
import net.toddm.comm.Work;

public class TestRequest extends TestCase {

	public void testRequest() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
		String results = response.getResponseBody();
		System.out.println(results);
		assertNotNull(results);
		assertTrue(results.length() > 0);
	}

	public void testRequestCanceling() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);
        commManager.cancel(work.getId(), true);
        assertTrue(work.isCancelled());

        long sleepTime = 100;
        while(work.isCancelled()) {
			work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
	        assertNotNull(work);
	        Thread.sleep(sleepTime);
	        commManager.cancel(work.getId(), true);

	        Method getStateMethod = work.getClass().getDeclaredMethod("getState");
	        getStateMethod.setAccessible(true);
	        Object status = getStateMethod.invoke(work);
	        assertTrue(
	        		String.format("Expected Cancelled or Done but got '%1$s'", status), 
	        		(work.isCancelled() || work.isDone()));

	        sleepTime += 100;
        }
        assertTrue(work.isDone());
	}

	public void testRequestRedirectAbsolute() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/redirect-to?url=http%3A%2F%2Fwww.toddm.net%2F"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		assertNotNull(work);

		Response response = work.get();
		assertNotNull(response);
		assertEquals(200, (int)response.getResponseCode());
		assertEquals(1, work.getRequest().getRedirectCount());
	}

	public void testRequestRedirectRelative() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/relative-redirect/3"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		assertNotNull(work);

		Response response = work.get();
		assertNotNull(response);
		assertEquals(200, (int)response.getResponseCode());
		assertEquals(3, work.getRequest().getRedirectCount());
	}

	public void testRequestWithHeaders() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Language", "en-US");
		headers.put("Content-Type", "application/x-www-form-urlencoded");

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, headers, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
		String results = response.getResponseBody();
		System.out.println(results);
		assertNotNull(results);
		assertTrue(results.length() > 0);
	}

	public void testRequestCachingHeaders() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

        // We happen to know our test request returns a TTL of 3600 seconds and an ETag value
        Long ttl = response.getTtlFromHeaders();
        assertNotNull(ttl);
        assertEquals(3600000, ttl.longValue());
        System.out.println("TTL from response headers: " + ttl);

        String eTag = response.getETagFromHeaders();
        assertNotNull(eTag);
        System.out.println("ETag from response headers: " + eTag);
	}

	public void testRequestWithCaching() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder
				.setName("TEST")
				.setCacheProvider(new MemoryCacheProvider("testCache", 20, new DefaultLogger()))
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
		String results = response.getResponseBody();
		System.out.println(results);
		assertNotNull(results);
		assertTrue(results.length() > 0);
		
		Work work2 = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work2);

        Response response2 = work2.get();
        assertNotNull(response2);
        assertEquals(200, (int)response2.getResponseCode());
		String results2 = response2.getResponseBody();
		System.out.println(results2);
		assertNotNull(results2);
		assertTrue(results2.length() > 0);

		assertEquals(work.getId(), work2.getId());
	}
	
	public void testNoCachePreventsCaching() throws Exception {

		CacheProvider cacheProvider = new MemoryCacheProvider("testCache", 20, new DefaultLogger());
		CommManager commManager = (new CommManager.Builder())
				.setName("TEST")
				.setCacheProvider(cacheProvider)
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/cache/100"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.NORMAL);
        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertTrue(cacheProvider.containsKey(Integer.toString(work.getId()), true));

		work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=no-cache"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.NORMAL);
        response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertFalse(cacheProvider.containsKey(Integer.toString(work.getId()), true));
	}

	public void testDoNotCachePreventsCaching() throws Exception {

		CacheProvider cacheProvider = new MemoryCacheProvider("testCache20w", 20, new DefaultLogger());
		CommManager commManager = (new CommManager.Builder())
				.setName("TEST")
				.setCacheProvider(cacheProvider)
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/cache/100"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.NORMAL);
        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertTrue(cacheProvider.containsKey(Integer.toString(work.getId()), true));

		work = commManager.enqueueWork(new URI("http://httpbin.org/cache/200"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.DO_NOT_CACHE);
        response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertFalse(cacheProvider.containsKey(Integer.toString(work.getId()), true));
	}

	public void testGetOnlyFromCache() throws Exception {

		CacheProvider cacheProvider = new MemoryCacheProvider("testCacheThr33", 20, new DefaultLogger());
		CommManager commManager = (new CommManager.Builder())
				.setName("TEST")
				.setCacheProvider(cacheProvider)
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/cache/500"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.GET_ONLY_FROM_CACHE);
        Response response = work.get();
        assertNull(response);
        assertFalse(cacheProvider.containsKey(Integer.toString(work.getId()), true));

		work = commManager.enqueueWork(new URI("http://httpbin.org/cache/500"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.NORMAL);
        response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertTrue(cacheProvider.containsKey(Integer.toString(work.getId()), true));
        assertFalse(response.isFromCache());
        int responseTime = response.getResponseTimeMilliseconds();

		work = commManager.enqueueWork(new URI("http://httpbin.org/cache/500"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.GET_ONLY_FROM_CACHE);
        response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertTrue(cacheProvider.containsKey(Integer.toString(work.getId()), true));
        assertTrue(response.isFromCache());
        assertEquals(responseTime, response.getResponseTimeMilliseconds());
	}

	public void testServerDirectedCache() throws Exception {

		CacheProvider cacheProvider = new MemoryCacheProvider("testCache4our", 20, new DefaultLogger());
		CommManager commManager = (new CommManager.Builder())
				.setName("TEST")
				.setCacheProvider(cacheProvider)
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=blah"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.SERVER_DIRECTED_CACHE);
        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertFalse(cacheProvider.containsKey(Integer.toString(work.getId()), true));

		work = commManager.enqueueWork(new URI("http://httpbin.org/cache/200"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.HIGH, CacheBehavior.SERVER_DIRECTED_CACHE);
        response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertTrue(cacheProvider.containsKey(Integer.toString(work.getId()), true));
	}

	public void testMaxStaleBehavior() throws Exception {

		CacheProvider cacheProvider = new MemoryCacheProvider("testCacheMaxStale", 20, new DefaultLogger());
		CommManager commManager = (new CommManager.Builder())
				.setName("TEST_MAX_STALE")
				.setCacheProvider(cacheProvider)
				.setLoggingProvider(new DefaultLogger())
				.create();

		// Confirm that new uncached work results in new work
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=public,+max-age=1,+max-stale=1"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work);
        assertEquals("net.toddm.comm.CommWork", work.getClass().getName());

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
        assertNotNull(response.getTtlFromHeaders());
        assertEquals(1000, (long)response.getTtlFromHeaders());		
        assertNotNull(response.getMaxStaleFromHeaders());
        assertEquals(1000, (long)response.getMaxStaleFromHeaders());

        // Confirm that subsequent request returns cached work based on unexpired cache entry
		work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=public,+max-age=1,+max-stale=1"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work);
        assertEquals("net.toddm.comm.CachedWork", work.getClass().getName());

        Thread.sleep(1001);

        // Confirm that subsequent request returns cached work based on expired cache entry that is still in the "stale use" window
		work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=public,+max-age=1,+max-stale=1"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work);
        assertEquals("net.toddm.comm.CachedWork", work.getClass().getName());

        // Confirm that subsequent request returns cached work rather than the new work started by the previous "stale use" window request
		work = commManager.enqueueWork(new URI("http://httpbin.org/response-headers?Cache-Control=public,+max-age=1,+max-stale=1"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work);
        assertEquals("net.toddm.comm.CachedWork", work.getClass().getName());
	}

	public void testRequestEquality() throws Exception {

		List<Work> testRequests = new ArrayList<Work>();

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();

		Work work1 = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.NORMAL);
        assertNotNull(work1);
        testRequests.add(work1);

		Work work2 = commManager.enqueueWork(new URI("http://www.toddm.net/?paramA=Apple&paramB=Baby"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work2);
        Assert.assertTrue(work1.getId() != work2.getId());
        testRequests.add(work2);

		Work work3 = commManager.enqueueWork(new URI("http://www.toddm.net/?paramB=Baby&paramA=Apple"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work3);
        Assert.assertTrue(work2.getId() == work3.getId());
        testRequests.add(work3);

		Work work4 = commManager.enqueueWork(
				new URI("http://www.toddm.net/"), 
				RequestMethod.POST, 
				new String("test POST data").getBytes("UTF-8"), 
				null, 
				true, StartingPriority.MEDIUM, 
				CachePriority.NORMAL, 
				CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work4);
        Assert.assertTrue(work1.getId() != work4.getId());
        testRequests.add(work4);
        
		Work work5 = commManager.enqueueWork(
				new URI("http://www.toddm.net/"), 
				RequestMethod.POST, 
				new String("test POST data").getBytes("UTF-8"), 
				null, 
				true, StartingPriority.MEDIUM, 
				CachePriority.NORMAL, 
				CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work5);
        Assert.assertTrue(work4.getId() == work5.getId());
        testRequests.add(work5);

		Work work6 = commManager.enqueueWork(
				new URI("http://www.toddm.net/"), 
				RequestMethod.POST, 
				new String("test POST data-").getBytes("UTF-8"), 
				null, 
				true, StartingPriority.MEDIUM, 
				CachePriority.NORMAL, 
				CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work6);
        Assert.assertTrue(work5.getId() != work6.getId());
        testRequests.add(work6);

        for(Work work : testRequests) {
	        Response response = work.get();
	        assertNotNull(response);
	        assertEquals(200, (int)response.getResponseCode());
			assertNotNull(response.getResponseBytes());
			assertTrue(response.getResponseBytes().length > 0);
        }
	}

}
