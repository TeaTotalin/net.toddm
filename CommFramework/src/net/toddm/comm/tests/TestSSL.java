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
import java.util.HashMap;

import net.toddm.comm.CommManager;
import net.toddm.comm.DefaultConfigurationProvider;
import net.toddm.comm.MapConfigurationProvider;
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
		CommManager commManagerNoCerts = commManagerBuilder
				.setConfigurationProvider(new MapConfigurationProvider(
					new HashMap<String, Object>() {
						private static final long serialVersionUID = 9063689608986469630L;
						{
							put(DefaultConfigurationProvider.KeyDisableSSLCertChecking, true);
						}
					}))
				.setName("TEST")
				.create();

		Work work = commManagerNoCerts.enqueueWork(new URI("https://testssl-expire.disig.sk/index.en.html"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work);
        Response response = work.get();
        assertNotNull(response);
        assertEquals(200, (int)response.getResponseCode());

		CommManager commManagerWithCerts = commManagerBuilder
				.setConfigurationProvider(new MapConfigurationProvider(
					new HashMap<String, Object>() {
						private static final long serialVersionUID = 9063689608986469630L;
						{
							put(DefaultConfigurationProvider.KeyDisableSSLCertChecking, false);
						}
					}))
				.create();

		work = commManagerWithCerts.enqueueWork(new URI("https://testssl-expire.disig.sk/index.en.html"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
        assertNotNull(work);
        response = work.get();
        assertNull(response);
	}

}
