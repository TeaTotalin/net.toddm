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
import java.nio.charset.Charset;

import net.toddm.cache.CachePriority;
import net.toddm.cache.DefaultLogger;
import net.toddm.comm.CacheBehavior;
import net.toddm.comm.CommManager;
import net.toddm.comm.Response;
import net.toddm.comm.Work;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import junit.framework.TestCase;

public class TestGZIP extends TestCase {

	public void testGZIPResponse() throws Exception {

		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").setLoggingProvider(new DefaultLogger()).create();
		Work work = commManager.enqueueWork(new URI("http://httpbin.org/gzip"), RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.DO_NOT_CACHE);
        assertNotNull(work);

        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

		String results = response.getResponseBody();
		System.out.println(results);
		assertNotNull(results);
		assertTrue(results.length() > 0);

		// Validate that the response text is not garbage text from binary (we happen to know this request returns only ASCII characters)
		assertTrue(Charset.forName("US-ASCII").newEncoder().canEncode(results));
	}

}
