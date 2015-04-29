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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.toddm.comm.ConfigurationException;
import net.toddm.comm.ConfigurationProvider;
import net.toddm.comm.MapConfigurationProvider;
import junit.framework.TestCase;

public class TestConfigurationProvider extends TestCase {

	public void testMapConfigurationProvider() throws Exception {
		Map<String, Object> config = new HashMap<String, Object>();
		config.put("key_object", UUID.randomUUID());
		config.put("key_string", "string value");
		config.put("key_int", 13);
		config.put("key_long", 13L);
		config.put("key_boolean", true);
		MapConfigurationProvider configProvider = new MapConfigurationProvider(config);
		this.validateConfigurationProvider(configProvider);
	}

	/**
	 * Can be used to do basic validation of any {@link ConfigurationProvider} implementation. To use simply populate your test instance like so:
	 * <ul>
	 * 		<li>config.put("key_object", UUID.randomUUID());
	 * 		<li>config.put("key_string", "string value");
	 * 		<li>config.put("key_int", 13);
	 * 		<li>config.put("key_long", 13L);
	 * 		<li>config.put("key_boolean", true);
	 * </ul>
	 */
	private void validateConfigurationProvider(ConfigurationProvider configProvider) {

		try {
			configProvider.get("missing_key");
			fail("Getting a non existent key did not throw a ConfigurationException");
		} catch(ConfigurationException e) {}

		try {
			configProvider.getInt("key_string");
			fail("Getting a String as an int did not throw a ConfigurationException");
		} catch(ConfigurationException e) {}

		try {
			configProvider.getString("key_int");
			fail("Getting an int as a String did not throw a ConfigurationException");
		} catch(ConfigurationException e) {}

		try {
			configProvider.getString("key_long");
			fail("Getting a long as a String did not throw a ConfigurationException");
		} catch(ConfigurationException e) {}

		try {
			configProvider.getString("key_boolean");
			fail("Getting a boolean as a String did not throw a ConfigurationException");
		} catch(ConfigurationException e) {}

		Object result = configProvider.get("key_object");
		assertNotNull(result);
		assertTrue(result instanceof UUID);
		result = configProvider.get("key_string");
		assertNotNull(result);
		assertTrue(result instanceof String);
		result = configProvider.get("key_int");
		assertNotNull(result);
		assertTrue(result instanceof Integer);
		result = configProvider.get("key_long");
		assertNotNull(result);
		assertTrue(result instanceof Long);
		result = configProvider.get("key_boolean");
		assertNotNull(result);
		assertTrue(result instanceof Boolean);

		String resultStr = configProvider.getString("key_string");
		assertNotNull(resultStr);
		assertEquals("string value", resultStr);
		int resultInt = configProvider.getInt("key_int");
		assertEquals(13, resultInt);
		long resultLong = configProvider.getLong("key_long");
		assertEquals(13L, resultLong);
		boolean resultBool = configProvider.getBoolean("key_boolean");
		assertTrue(resultBool);
	}

}
