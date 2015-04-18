package net.toddm.comm.tests;

import java.net.URI;

import net.toddm.comm.CommManager;
import net.toddm.comm.Response;
import net.toddm.comm.Work;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import junit.framework.TestCase;

public class TestSSL extends TestCase {

	public void testGoodCert() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();
		Work work = commManager.enqueueWork(new URI("https://httpbin.org/status/200"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());
	}

	public void testBadCert() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();
		Work work = commManager.enqueueWork(new URI("https://www.toddm.net/"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work);

        Response response = work.get();
        assertNull(response);
	}

}
