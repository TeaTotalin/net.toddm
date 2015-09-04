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

import java.util.List;
import java.util.Locale;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CacheProvider;
import net.toddm.cache.MemoryCacheProvider;
import net.toddm.cache.DefaultLogger;
import junit.framework.TestCase;

public class MainTest extends TestCase {

	public void testMemoryCacheProvider() throws Exception {
		System.out.println("#######################################");

		// Ensure we have an empty test cache
		CacheProvider cacheProvider = new MemoryCacheProvider("TestNamespace", 20, new DefaultLogger());
		validateCachingFunctionality(cacheProvider);
	}

	/**
	 * This testing method is public so that any implementers of CacheProvider can make use of it to test the correctness of the implementation.
	 * <p>
	 * @param cacheProvider An instance of an implementation of of the {@link CacheProvider} interface to be tested by this method.
	 */
	public static void validateCachingFunctionality(CacheProvider cacheProvider) throws Exception {

		cacheProvider.removeAll();
		assertEquals(0, cacheProvider.getAll(true).size());
		assertEquals(0, cacheProvider.size(true));

		cacheProvider.add("key1", "value1: ┤╥,65♀635L2☻~32┐2◙⌠1j32┐53K_", 1L, null, null);
		Thread.sleep(2);
		CacheEntry entry = cacheProvider.get("key1", true);
		assertEquals(1, cacheProvider.getAll(true).size());
		assertEquals(1, cacheProvider.size(true));
		assertNotNull(entry);
		assertTrue(entry.hasExpired());
		assertEquals("key1", entry.getKey());
		assertEquals(1L, (long)entry.getTtl());

		entry = cacheProvider.get("key1", false);
		assertNull(entry);
	    List<CacheEntry> entries = cacheProvider.getAll(false);
	    assertNotNull(entries);
	    assertEquals(0, entries.size());

		cacheProvider.add("key2", "value2", 1000000L, null, null);
		Thread.sleep(2);
		entry = cacheProvider.get("key2", true);
		assertEquals(2, cacheProvider.getAll(true).size());
		assertEquals(2, cacheProvider.size(true));
		assertNotNull(entry);
		assertFalse(entry.hasExpired());
		assertEquals("key2", entry.getKey());
		assertEquals(1000000L, (long)entry.getTtl());

		cacheProvider.add("key3", "value3", 1000000L, null, null);
		Thread.sleep(2);
		entry = cacheProvider.get("key3", true);
		assertNotNull(entry);
		assertEquals(3, cacheProvider.getAll(true).size());
		assertEquals(3, cacheProvider.size(true));

		cacheProvider.add("key4", "value4", 1000000L, null, null);
		Thread.sleep(2);
		entry = cacheProvider.get("key4", true);
		assertNotNull(entry);
		assertEquals(4, cacheProvider.getAll(true).size());
		assertEquals(4, cacheProvider.size(true));

		assertTrue(cacheProvider.containsKey("key3", true));
		cacheProvider.remove("key3");
		entry = cacheProvider.get("key3", true);
		assertNull(entry);
		assertEquals(3, cacheProvider.getAll(true).size());
		assertEquals(3, cacheProvider.size(true));
		assertFalse(cacheProvider.containsKey("key3", true));

		cacheProvider.setLruCap(1);
		cacheProvider.trimLru();
		assertEquals(1, cacheProvider.getAll(true).size());
		assertEquals(1, cacheProvider.size(true));
		entry = cacheProvider.get("key4", true);
		assertNotNull(entry);

		cacheProvider.removeAll();
		cacheProvider.add("key5", "value5", 1L, null, null);
		Thread.sleep(2);
		assertEquals(1, cacheProvider.size(true));
		assertEquals(0, cacheProvider.size(false));
		assertTrue(cacheProvider.containsKey("key5", true));
		assertFalse(cacheProvider.containsKey("key5", false));

		cacheProvider.removeAll();
		assertEquals(0, cacheProvider.getAll(true).size());
		assertEquals(0, cacheProvider.size(true));

		final byte[] testBytes = new byte[] { (byte)204, 113, 108, 122, 3, (byte)232, 112, 50, 17, 63 };
		cacheProvider.add("key1", testBytes, 1L, null, null);
		entry = cacheProvider.get("key1", true);
		assertEquals(1, cacheProvider.getAll(true).size());
		assertEquals(1, cacheProvider.size(true));
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
		assertEquals(0, cacheProvider.size(true));
	}

    /** Returns a loggable string for the given {@link Throwable} containing type, message, and stack trace information. */
    public static String getThrowableDump(Throwable throwable) {
        if(throwable == null) { throw(new IllegalArgumentException("'throwable' cannot be null")); }
        return(String.format(Locale.US,
                "%s | %s | %s",
                throwable.getClass().getName(),
                throwable.getMessage(),
                getStackTrace(throwable.getStackTrace())));
    }

    /** Returns a string dump of the provided stack trace */
    public static String getStackTrace(StackTraceElement[] stacks) {
        if(stacks == null) { throw(new IllegalArgumentException("'stacks' cannot be null")); }
        StringBuffer buffer = new StringBuffer();
        for(int i = 0; i < stacks.length; i++) {
            buffer.append(String.format(Locale.US,
                    "%1$s : %2$s : %3$s [%4$d]\n",
                    stacks[i].getFileName(),
                    stacks[i].getClassName(),
                    stacks[i].getMethodName(),
                    stacks[i].getLineNumber()));
        }
        return(buffer.toString());
    }

}
