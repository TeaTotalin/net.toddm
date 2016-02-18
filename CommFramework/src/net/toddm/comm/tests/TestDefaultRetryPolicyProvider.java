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

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.TestCase;
import net.toddm.cache.CachePriority;
import net.toddm.cache.DefaultLogger;
import net.toddm.comm.CommManager;
import net.toddm.comm.DefaultRetryPolicyProvider;
import net.toddm.comm.DependentWorkListener;
import net.toddm.comm.Priority;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.CacheBehavior;
import net.toddm.comm.Response;
import net.toddm.comm.RetryProfile;
import net.toddm.comm.SubmittableWork;
import net.toddm.comm.Work;

public class TestDefaultRetryPolicyProvider extends TestCase {

	public void testShouldRetryOnError() throws Exception {
		
		DefaultRetryPolicyProvider retryPolicyProvider = new DefaultRetryPolicyProvider(new DefaultLogger());

		// Test that an idempotent request will get retried for SocketTimeoutException
		RetryProfile retryProfile = retryPolicyProvider.shouldRetry(new WorkStub(), new SocketTimeoutException());
		assertTrue(retryProfile.shouldRetry());
		assertEquals(3000, retryProfile.getRetryAfterMilliseconds());

		// Test that an idempotent request will NOT get retried for NullPointerException
		retryProfile = retryPolicyProvider.shouldRetry(new WorkStub(), new NullPointerException());
		assertFalse(retryProfile.shouldRetry());

		// Test that requests flagged as not idempotent do not get retried on error
		retryProfile = retryPolicyProvider.shouldRetry(new WorkStub(false), new SocketTimeoutException());
		assertFalse(retryProfile.shouldRetry());
	}

	public void testShouldRetryOnResponse() throws Exception {

		DefaultRetryPolicyProvider retryPolicyProvider = new DefaultRetryPolicyProvider(new DefaultLogger());

		RetryProfile retryProfile = retryPolicyProvider.shouldRetry(new WorkStub(), new ResponseStub(302, null));
		assertFalse(retryProfile.shouldRetry());

		retryProfile = retryPolicyProvider.shouldRetry(new WorkStub(), new ResponseStub(503, 13L));
		assertTrue(retryProfile.shouldRetry());
		assertEquals(13000L, retryProfile.getRetryAfterMilliseconds());  // Header is in seconds, interval is in milliseconds

		retryProfile = retryPolicyProvider.shouldRetry(new WorkStub(), new ResponseStub(202, 14L));
		assertTrue(retryProfile.shouldRetry());
		assertEquals(14000L, retryProfile.getRetryAfterMilliseconds());  // Header is in seconds, interval is in milliseconds
	}

	/** Use http://httpbin.org/ to simulate specific response shapes for testing */
	public void test503Handling() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/status/503"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(503, (int)response.getResponseCode());
        assertEquals(5, work.getRequest().getRetryCountFromResponse());
	}

	/** Use http://httpbin.org/ to simulate specific response shapes for testing */
	public void test202Handling() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();

		Work work = commManager.enqueueWork(new URI("http://httpbin.org/status/202"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(202, (int)response.getResponseCode());
        assertEquals(5, work.getRequest().getRetryCountFromResponse());
	}

	/** A testing stub class for {@link Response}. */
	private class ResponseStub extends Response {
		private static final long serialVersionUID = 7455101257115334805L;
		public ResponseStub(int responseCode, Long retryAfterSeconds) throws URISyntaxException {
			super(null, null, responseCode, 1, 1, new DefaultLogger());
			if(retryAfterSeconds != null) {
				ArrayList<String> retryAfterHeaderValue = new ArrayList<String>();
				retryAfterHeaderValue.add(Long.toString(retryAfterSeconds));
				this.getHeaders().put("Retry-After", retryAfterHeaderValue);
			}
		}
	};

	/** A testing stub class for {@link Request}. */
	private class RequestStub extends Request {
		public RequestStub() throws URISyntaxException {
			super(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, true);
		}
		public RequestStub(boolean isIdempotent) throws URISyntaxException {
			super(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, isIdempotent);
		}
	};
	
	private class WorkStub implements Work {

		private final Request request;
		private final Response response = new ResponseStub(302, null);
		WorkStub() throws URISyntaxException {
			this(true);
		}
		WorkStub(boolean isIdempotent) throws URISyntaxException {
			request = new RequestStub(isIdempotent);
		}

		@Override
		public Status getState() { return(Status.COMPLETED); }
		@Override
		public int getId() { return(request.getId()); }
		@Override
		public Response get() throws InterruptedException, ExecutionException { return(response); }
		@Override
		public Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return(response); }
		@Override
		public Request getRequest() { return(request); }
		@Override
		public Priority getRequestPriority() { return(new Priority(this, Priority.StartingPriority.MEDIUM)); }
		@Override
		public CachePriority getCachingPriority() { return(CachePriority.NORMAL); }
		@Override
		public CacheBehavior getCachingBehavior() { return(CacheBehavior.NORMAL); }
		@Override
		public boolean isDone() { return(true); }
		@Override
		public boolean isCancelled() { return(false); }
		@Override
		public void setDependentWork(SubmittableWork dependentWork, DependentWorkListener dependentWorkListener) {}
		@Override
		public Exception getException() { return(null); }
	}

}
