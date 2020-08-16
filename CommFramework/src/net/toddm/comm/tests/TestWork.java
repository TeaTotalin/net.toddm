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
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import net.toddm.cache.CachePriority;
import net.toddm.cache.DefaultLogger;
import net.toddm.comm.CacheBehavior;
import net.toddm.comm.CommManager;
import net.toddm.comm.DefaultConfigurationProvider;
import net.toddm.comm.DependentWorkListener;
import net.toddm.comm.MapConfigurationProvider;
import net.toddm.comm.Response;
import net.toddm.comm.SubmittableWork;
import net.toddm.comm.Work;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.Work.Status;
import junit.framework.TestCase;

public class TestWork extends TestCase {

	public void testGetException() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManagerThatWillTimeout = commManagerBuilder
				.setConfigurationProvider(new MapConfigurationProvider(
					new HashMap<String, Object>() {
						private static final long serialVersionUID = 9063689608986469630L;
						{
							put(DefaultConfigurationProvider.KeyConnectTimeoutMilliseconds, 1);
							put(DefaultConfigurationProvider.KeyReadTimeoutMilliseconds, 1);
						}
					}))
				.setName("TEST")
				.setLoggingProvider(new DefaultLogger())
				.create();

		Work work = commManagerThatWillTimeout.enqueueWork(new URI("https://toddm.net/"), RequestMethod.GET, null, null, false, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        try {
        	Response response = work.get();
        	assertNull(response);
        } catch(ExecutionException e) {
        	// Depending on the timing of the get() call the socket timeout can cause this
        }

        assertNotNull(work.getException());
        assertTrue(work.getException() instanceof SocketTimeoutException);
	}

	public void testDependentWorkCallbackAllowsCurrentWork() throws Exception {

		CommManager commManager = (new CommManager.Builder())
				.setName("TEST")
				.setLoggingProvider(new DefaultLogger())
				.create();

		final SubmittableWork work1 = commManager.getWork(new URI("https://toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		final SubmittableWork work2 = commManager.getWork(new URI("http://toddm.net/art/index.html"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		final SubmittableWork work3 = commManager.getWork(new URI("http://toddm.net/ants/index.html"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		final SubmittableWork work4 = commManager.getWork(new URI("http://toddm.net/gravity/index.html"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);

		DependentWorkListener dependentWorkListener = new DependentWorkListener() {
			@Override
			public boolean onDependentWorkCompleted(Work dependentWork, Work currentWork) {
				assertTrue(
						((currentWork.getId() == work1.getId()) && (dependentWork.getId() == work2.getId())) ||
						((currentWork.getId() == work2.getId()) && (dependentWork.getId() == work3.getId())) ||
						((currentWork.getId() == work3.getId()) && (dependentWork.getId() == work4.getId())) );
				return(true);
			}
		};

		work1.setDependentWork(work2, dependentWorkListener);
		work2.setDependentWork(work3, dependentWorkListener);
		work3.setDependentWork(work4, dependentWorkListener);

		// Submitting work1 should result in the full chain of dependent work being started and processed
		Work work = commManager.enqueueWork(work1);
		Response result = work.get();
		assertNotNull(result);
		assertTrue(result.isSuccessful());
		
		Response result2 = ((Work)work2).get();
		assertNotNull(result2);
		assertTrue(result2.isSuccessful());

		Response result3 = ((Work)work3).get();
		assertNotNull(result3);
		assertTrue(result3.isSuccessful());
		
		Response result4 = ((Work)work4).get();
		assertNotNull(result4);
		assertTrue(result4.isSuccessful());
	}

	public void testDependentWorkCallbackCancelsCurrentWork() throws Exception {

		CommManager commManager = (new CommManager.Builder())
				.setName("TEST")
				.setLoggingProvider(new DefaultLogger())
				.create();

		final SubmittableWork work1 = commManager.getWork(new URI("https://toddm.net/"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		final SubmittableWork work2 = commManager.getWork(new URI("http://toddm.net/art/index.html"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);

		DependentWorkListener dependentWorkListener = new DependentWorkListener() {
			@Override
			public boolean onDependentWorkCompleted(Work dependentWork, Work currentWork) {
				assertEquals(work1.getId(), currentWork.getId());
				assertEquals(work2.getId(), dependentWork.getId());
				return(false);
			}
		};

		work1.setDependentWork(work2, dependentWorkListener);

		// Submitting work1 should result in the full chain of dependent work being started and processed
		Work work = commManager.enqueueWork(work1);
		Response result = work.get();
		assertNull(result);
		assertEquals(Status.CANCELLED, work.getState());

		Response result2 = ((Work)work2).get();
		assertNotNull(result2);
		assertTrue(result2.isSuccessful());
	}

	public void testSetDependentWorkCyclicDependence() throws Exception {

		CommManager commManager = (new CommManager.Builder())
				.setName("TEST")
				.setLoggingProvider(new DefaultLogger())
				.create();

		SubmittableWork work1 = commManager.getWork(new URI("https://toddm.net/one"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		SubmittableWork work2 = commManager.getWork(new URI("https://toddm.net/two"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		SubmittableWork work3 = commManager.getWork(new URI("https://toddm.net/three"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		SubmittableWork work4 = commManager.getWork(new URI("https://toddm.net/four"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		SubmittableWork work5 = commManager.getWork(new URI("https://toddm.net/five"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);

		work1.setDependentWork(work2, null);
		work2.setDependentWork(work3, null);
		work3.setDependentWork(work4, null);
		work4.setDependentWork(work5, null);
		try {
			work5.setDependentWork(work1, null);
			fail("Expected IllegalArgumentException due to cyclic dependence");
		} catch(IllegalArgumentException e) {
			assertEquals("Cyclic dependence detected", e.getMessage());
		}
	}

}
