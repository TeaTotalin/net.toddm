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
package net.toddm.cache.tests;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CacheProvider;
import net.toddm.cache.MemoryCacheProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import junit.framework.TestCase;

public class MainTest extends TestCase {

	private static Logger _Logger = LoggerFactory.getLogger(MainTest.class.getSimpleName());

	public void testMemoryCacheProvider() throws Exception {
		_Logger.debug("#######################################");

		// Ensure we have an empty test cache
		CacheProvider cacheProvider = new MemoryCacheProvider("TestNamespace");
		this.validateCachingFunctionality(cacheProvider);
	}

	private void validateCachingFunctionality(CacheProvider cacheProvider) throws Exception {

		cacheProvider.removeAll();
		assertEquals(0, cacheProvider.getAll(true).size());

		cacheProvider.add("key1", "value1: ┤╥,65♀635L2☻~32┐2◙⌠1j32┐53K_", 1L, null, null);
		Thread.sleep(2);
		CacheEntry entry = cacheProvider.get("key1", true);
		assertEquals(1, cacheProvider.getAll(true).size());
		assertNotNull(entry);
		assertTrue(entry.hasExpired());
		assertEquals("key1", entry.getKey());
		assertEquals(1L, (long)entry.getTtl());

		entry = cacheProvider.get("key1", false);
		assertNull(entry);

		cacheProvider.add("key2", "value2", 1000000L, null, null);
		Thread.sleep(2);
		entry = cacheProvider.get("key2", true);
		assertEquals(2, cacheProvider.getAll(true).size());
		assertNotNull(entry);
		assertFalse(entry.hasExpired());
		assertEquals("key2", entry.getKey());
		assertEquals(1000000L, (long)entry.getTtl());

		cacheProvider.add("key3", "value3", 1000000L, null, null);
		Thread.sleep(2);
		entry = cacheProvider.get("key3", true);
		assertNotNull(entry);
		assertEquals(3, cacheProvider.getAll(true).size());

		cacheProvider.add("key4", "value4", 1000000L, null, null);
		Thread.sleep(2);
		entry = cacheProvider.get("key4", true);
		assertNotNull(entry);
		assertEquals(4, cacheProvider.getAll(true).size());

		cacheProvider.remove("key3");
		entry = cacheProvider.get("key3", true);
		assertNull(entry);
		assertEquals(3, cacheProvider.getAll(true).size());

		cacheProvider.trimLru(1);
		assertEquals(1, cacheProvider.getAll(true).size());
		entry = cacheProvider.get("key4", true);
		assertNotNull(entry);

		cacheProvider.removeAll();
		assertEquals(0, cacheProvider.getAll(true).size());

		final byte[] testBytes = new byte[] { (byte)204, 113, 108, 122, 3, (byte)232, 112, 50, 17, 63 };
		cacheProvider.add("key1", testBytes, 1L, null, null);
		entry = cacheProvider.get("key1", true);
		assertEquals(1, cacheProvider.getAll(true).size());
		assertNotNull(entry);
		assertEquals("key1", entry.getKey());
		assertEquals(1L, (long)entry.getTtl());
		assertNull(entry.getStringValue());
		assertNotNull(entry.getBytesValue());
		assertEquals(testBytes.length, entry.getBytesValue().length);
		for(int i = 0; i < testBytes.length; i++) {
			assertEquals(testBytes[i], entry.getBytesValue()[i]);
		}

		cacheProvider.removeAll();
		assertEquals(0, cacheProvider.getAll(true).size());
	}

}
