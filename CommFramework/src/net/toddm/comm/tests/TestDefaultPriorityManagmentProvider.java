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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.toddm.comm.CommManager;
import net.toddm.comm.DefaultPriorityManagmentProvider;
import net.toddm.comm.Priority;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.Work;

public class TestDefaultPriorityManagmentProvider extends TestCase {

	public void testPromotePriority() throws Exception {

		// Get a Work object...
		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();
		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, StartingPriority.LOW, false);

		// Get the Priority object instance created for the work
		Priority testPriority = work.getPriority();

		// Create an instance of the default priority management provider
		DefaultPriorityManagmentProvider priorityManagmentProvider = new DefaultPriorityManagmentProvider();
		Assert.assertEquals(StartingPriority.LOW.getPriorityValue(), testPriority.getValue());
		priorityManagmentProvider.promotePriority(testPriority);
		Assert.assertEquals(StartingPriority.LOW.getPriorityValue(), testPriority.getValue());
		
		// Hack the promotion interval so this unit test won't take so long
        Field field = DefaultPriorityManagmentProvider.class.getDeclaredField("_PromotionIntervalInMilliseconds");
        field.setAccessible(true);
        long promotionIntervalCache = (long)field.get(null);
        field.set(null, 10);

        // Test priority progression as we promote
        Thread.sleep(11);
		priorityManagmentProvider.promotePriority(testPriority);
		Assert.assertTrue(StartingPriority.LOW.getPriorityValue() > testPriority.getValue());

		while(testPriority.getValue() > 1) {
			int priorityCache = testPriority.getValue();
	        Thread.sleep(11);
			priorityManagmentProvider.promotePriority(testPriority);
			Assert.assertEquals(priorityCache - 1, testPriority.getValue());
		}

		// Attempt to promote too many times and ensure we don't exceed highest priority
		Assert.assertEquals(1, testPriority.getValue());
        Thread.sleep(11);
		priorityManagmentProvider.promotePriority(testPriority);
		Assert.assertEquals(1, testPriority.getValue());

		// Revert our hacked promotion interval
        field.set(null, promotionIntervalCache);
	}

	public void testPriorityComparator() throws Exception {

		// Get Priority object instances to test with by submitting work
		CommManager.Builder commManagerBuilder = new CommManager.Builder();
		CommManager commManager = commManagerBuilder.setName("TEST").create();
		Work work = commManager.enqueueWork(new URI("http://www.toddm.net/"), RequestMethod.GET, null, null, StartingPriority.LOW, false);
		Priority priorityLow = work.getPriority();
		work = commManager.enqueueWork(new URI("http://httpbin.org/status/200"), RequestMethod.GET, null, null, StartingPriority.MEDIUM, false);
		Priority priorityMedium = work.getPriority();
		work = commManager.enqueueWork(new URI("http://httpbin.org/status/201"), RequestMethod.GET, null, null, StartingPriority.HIGH, false);
		Priority priorityHigh = work.getPriority();

		// Set up a list of out of order priorities to sort
		ArrayList<Priority> priorityList = new ArrayList<Priority>();
		priorityList.add(priorityLow);
		priorityList.add(priorityHigh);
		priorityList.add(priorityMedium);

		Assert.assertEquals(StartingPriority.LOW.getPriorityValue(), priorityList.get(0).getValue());
		Assert.assertEquals(StartingPriority.HIGH.getPriorityValue(), priorityList.get(1).getValue());
		Assert.assertEquals(StartingPriority.MEDIUM.getPriorityValue(), priorityList.get(2).getValue());

		// Sort and validate
		DefaultPriorityManagmentProvider priorityManagmentProvider = new DefaultPriorityManagmentProvider();
		Collections.sort(priorityList, priorityManagmentProvider.getPriorityComparator());

		Assert.assertEquals(StartingPriority.HIGH.getPriorityValue(), priorityList.get(0).getValue());
		Assert.assertEquals(StartingPriority.MEDIUM.getPriorityValue(), priorityList.get(1).getValue());
		Assert.assertEquals(StartingPriority.LOW.getPriorityValue(), priorityList.get(2).getValue());
	}

}