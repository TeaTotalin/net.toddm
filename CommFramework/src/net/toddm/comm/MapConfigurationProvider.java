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
package net.toddm.comm;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A simple implementation of the {@link ConfigurationProvider} interface that backs configuration data with an in-memory hash map. 
 * <p>
 * @author Todd S. Murchison
 */
public class MapConfigurationProvider implements ConfigurationProvider {

	private final Map<String, Object> _configMap;
	
	public MapConfigurationProvider(Map<String, Object> config) {
		if(config == null) { throw(new IllegalArgumentException("'config' can not be NULL")); }
		this._configMap = Collections.unmodifiableMap(new HashMap<String, Object>(config));
	}

	/** {@inheritDoc} */
	@Override
	public boolean contains(String name) {
		if(name == null) { throw(new IllegalArgumentException("'name' can not be NULL")); }
		return(this._configMap.containsKey(name));
	}

	/** {@inheritDoc} */
	@Override
	public Object get(String name) throws ConfigurationException {
		// The 'name' parameter is validated by the call below
		if(!this.contains(name)) { throw(new ConfigurationException(String.format(Locale.US,  "Config does not contain a value for '%1$s'", name))); }
		return(this._configMap.get(name));
	}

	/** {@inheritDoc} */
	@Override
	public String getString(String name) throws ConfigurationException {
		// The 'name' parameter and existence of the mapping is validated by the call below
		Object rawValue = this.get(name);
		if(!(rawValue instanceof String)) { throw(new ConfigurationException(String.format(Locale.US,  "Value for '%1$s' can not be cast as a String", name))); }
		return((String)rawValue);
	}

	/** {@inheritDoc} */
	@Override
	public int getInt(String name) throws ConfigurationException {
		// The 'name' parameter and existence of the mapping is validated by the call below
		Object rawValue = this.get(name);
		if(!(rawValue instanceof Integer)) { throw(new ConfigurationException(String.format(Locale.US,  "Value for '%1$s' can not be cast as an Integer", name))); }
		return((int)rawValue);
	}

	/** {@inheritDoc} */
	@Override
	public long getLong(String name) throws ConfigurationException {
		// The 'name' parameter and existence of the mapping is validated by the call below
		Object rawValue = this.get(name);
		if(!(rawValue instanceof Long)) { throw(new ConfigurationException(String.format(Locale.US,  "Value for '%1$s' can not be cast as an Long", name))); }
		return((long)rawValue);
	}

	/** {@inheritDoc} */
	@Override
	public boolean getBoolean(String name) throws ConfigurationException {
		// The 'name' parameter and existence of the mapping is validated by the call below
		Object rawValue = this.get(name);
		if(!(rawValue instanceof Boolean)) { throw(new ConfigurationException(String.format(Locale.US,  "Value for '%1$s' can not be cast as an Boolean", name))); }
		return((boolean)rawValue);
	}

}
