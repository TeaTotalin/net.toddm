package net.toddm.comm.tests;

import java.net.URI;

import net.toddm.cache.CachePriority;
import net.toddm.cache.DefaultLogger;
import net.toddm.comm.CacheBehavior;
import net.toddm.comm.CommManager;
import net.toddm.comm.Response;
import net.toddm.comm.Work;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import junit.framework.TestCase;

public class TestOkHttp extends TestCase {

	public void testHttp2() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("https://www.cloudstats.me"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
		assertNotNull(work);

		Response response = work.get();
		assertNotNull(response);
		assertEquals(200, (int)response.getResponseCode());

		System.out.println("BODY: " + response.getResponseBody());
	}

}
