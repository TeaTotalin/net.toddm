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
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.toddm.cache.MemoryCacheProvider;
import net.toddm.comm.CommManager;
import net.toddm.comm.DefaultPriorityManagmentProvider;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.Response;
import net.toddm.comm.ResponseCachingUtility;
import net.toddm.comm.Work;
import junit.framework.Assert;
import junit.framework.TestCase;

public class MainTest extends TestCase {

	private static Logger _Logger = LoggerFactory.getLogger(MainTest.class.getSimpleName());

	public void testMakeTestRequest() throws Exception {

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
	
	public void testMakeTestRequestWithHeaders() throws Exception {

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
        Long ttl = ResponseCachingUtility.getTtlFromResponse(response);
        assertNotNull(ttl);
        assertEquals(3600000, ttl.longValue());
        _Logger.trace("TTL from response headers: " + ttl);

        String eTag = ResponseCachingUtility.getETagFromResponse(response);
        assertNotNull(eTag);
        _Logger.trace("ETag from response headers: " + eTag);
	}

	public void testRequestsWithCaching() throws Exception {

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
	
	public void testPriorityQueuing() throws Exception {
		
		// TODO: This test case SUCKS.  Update to something better when I have time.
		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		final CommManager commManager = commManagerBuilder
				.setName("TEST")
				.setPriorityManagmentProvider(new DefaultPriorityManagmentProvider())
				.create();

		// Queue up a bunch of work with our-of-order priorities
		int requestCount = 30;
		final ConcurrentLinkedQueue<Work> finishedWork = new ConcurrentLinkedQueue<Work>();
		final ConcurrentLinkedQueue<Work> failedWork = new ConcurrentLinkedQueue<Work>();
		for(int i = 0; i < requestCount; i++) {
			final int iFinal = i;
			(new Thread(new Runnable() {
				@Override
				public void run() {
					Work work = null;
					try {
						StartingPriority priority = StartingPriority.LOW;
						if(iFinal < 10) {
							priority = StartingPriority.HIGH;
						} else if( (iFinal % 2) == 0 ) {
							priority = StartingPriority.MEDIUM;
						}
						String url = String.format("https://raw.githubusercontent.com/eneko/data-repository/master/data/words.txt?arg=%1$d", iFinal);
						work = commManager.enqueueWork(new URI(url), RequestMethod.GET, null, null, priority, false);
						work.get();
						finishedWork.add(work);
					} catch(Exception e) {
						e.printStackTrace();
						failedWork.add(work);
					}
				}
			})).start();
		}

		// Wait for all the test requests to finish
		int maxWaitCount = 60;
		while(((finishedWork.size() + failedWork.size()) < requestCount) && (maxWaitCount > 0)) {
			Thread.sleep(1500);
			maxWaitCount--;
		}
		_Logger.trace("finishedWork.size:%1$d failedWork.size:%2$d", finishedWork.size(), failedWork.size());
		if((finishedWork.size() + failedWork.size()) < requestCount) {
			Assert.fail("Not all test requests finished in time!");
		}

		// Ensure the requests finished in an order consistent with their priorities
		Work previousWork = null;
		for(Work work : finishedWork) {
			if(previousWork != null) {
				Assert.assertTrue(previousWork.getPriority().getValue() <= work.getPriority().getValue());
			}
			previousWork = work;
		}
	}

}
