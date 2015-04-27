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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.toddm.cache.CacheEntry;
import net.toddm.cache.MemoryCacheProvider;
import net.toddm.comm.CommManager;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.Response;
import net.toddm.comm.Work;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestMain extends TestCase {

	private static Logger _Logger = LoggerFactory.getLogger(TestMain.class.getSimpleName());

	public void testRequest() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
		String results = response.getResponseBody();
        _Logger.trace(results);
		assertNotNull(results);
		assertTrue(results.length() > 0);
	}
	
	public void testRequestRedirectAbsolute() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/redirect-to?url=http%3A%2F%2Fwww.toddm.net%2F"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
		assertNotNull(work);

		Response response = work.get();
		assertNotNull(response);
		assertEquals(200, (int)response.getResponseCode());
		assertEquals(1, work.getRequest().getRedirectCount());
	}

	public void testRequestRedirectRelative() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/relative-redirect/3"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
		assertNotNull(work);

		Response response = work.get();
		assertNotNull(response);
		assertEquals(200, (int)response.getResponseCode());
		assertEquals(3, work.getRequest().getRedirectCount());
	}

	public void testRequestWithHeaders() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();
		
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Language", "en-US");
		headers.put("Content-Type", "application/x-www-form-urlencoded");

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, headers, StartingPriority.MEDIUM, false);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
		String results = response.getResponseBody();
        _Logger.trace(results);
		assertNotNull(results);
		assertTrue(results.length() > 0);
	}

	public void testRequestCachingHeaders() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

        // We happen to know our test request returns a TTL of 3600 seconds and an ETag value
        Long ttl = response.getTtlFromHeaders();
        assertNotNull(ttl);
        assertEquals(3600000, ttl.longValue());
        _Logger.trace("TTL from response headers: " + ttl);

        String eTag = response.getETagFromHeaders();
        assertNotNull(eTag);
        _Logger.trace("ETag from response headers: " + eTag);
	}

	// TODO: Find a better way to test 304, this currently requires manually examining the log after running
	public void test304Responses() throws Exception {

		MemoryCacheProvider cache = new MemoryCacheProvider("testCache");
		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder
				.setName("TEST")
				.setCacheProvider(cache)
				.create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/cache"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, true);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

        // Update the cache TTL so it's expired
        CacheEntry cacheEntry = cache.get(Integer.toString(work.getId()), true);
        cache.add(cacheEntry.getKey(), cacheEntry.getBytesValue(), 100, cacheEntry.getEtag(), cacheEntry.getUri());

        Thread.sleep(101);

		work = commManager.enqueueWork(new URI("http://httpbin.org/cache"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, true);
        assertNotNull(work);

        response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
	}

	public void testRequestWithCaching() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder
				.setName("TEST")
				.setCacheProvider(new MemoryCacheProvider("testCache"))
				.create();

		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, true);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
		String results = response.getResponseBody();
        _Logger.trace(results);
		assertNotNull(results);
		assertTrue(results.length() > 0);
		
		Work work2 = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, true);
        assertNotNull(work2);

        Response response2 = work2.get();
        assertNotNull(response2);
        assertEquals(200, (int)response2.getResponseCode());
		String results2 = response2.getResponseBody();
        _Logger.trace(results2);
		assertNotNull(results2);
		assertTrue(results2.length() > 0);

		assertEquals(work.getId(), work2.getId());
	}

	public void testRequestEquality() throws Exception {

		List<Work> testRequests = new ArrayList<Work>();

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();

		Work work1 = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work1);
        testRequests.add(work1);

		Work work2 = commManager.enqueueWork(new URI("http://www.toddm.net/?paramA=Apple&paramB=Baby"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work2);
        Assert.assertTrue(work1.getId() != work2.getId());
        testRequests.add(work2);

		Work work3 = commManager.enqueueWork(new URI("http://www.toddm.net/?paramB=Baby&paramA=Apple"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work3);
        Assert.assertTrue(work2.getId() == work3.getId());
        testRequests.add(work3);

		Work work4 = commManager.enqueueWork(
				new URI("http://www.toddm.net/"), 
				RequestMethod.POST, 
				new String("test POST data").getBytes("UTF-8"), 
				null, 
				StartingPriority.MEDIUM, 
				false);
        assertNotNull(work4);
        Assert.assertTrue(work1.getId() != work4.getId());
        testRequests.add(work4);
        
		Work work5 = commManager.enqueueWork(
				new URI("http://www.toddm.net/"), 
				RequestMethod.POST, 
				new String("test POST data").getBytes("UTF-8"), 
				null, 
				StartingPriority.MEDIUM, 
				false);
        assertNotNull(work5);
        Assert.assertTrue(work4.getId() == work5.getId());
        testRequests.add(work5);

		Work work6 = commManager.enqueueWork(
				new URI("http://www.toddm.net/"), 
				RequestMethod.POST, 
				new String("test POST data-").getBytes("UTF-8"), 
				null, 
				StartingPriority.MEDIUM, 
				false);
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
