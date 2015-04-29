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

/**
 * Configuration providers implement this interface in order to expose configuration data to the comm framework.
 * <p>
 * @author Todd S. Murchison
 */
public interface ConfigurationProvider {

	/** Returns <b>true</b> if a value with the given name exists in the configuration data, <b>false</b> otherwise. */
	public boolean contains(String name);

	/**
	 * Returns an {@link Object} representation of the configuration value with the given name.
	 * If no such value exists a {@link ConfigurationException} is thrown.
	 */
	public Object get(String name) throws ConfigurationException;

	/**
	 * Returns a {@link String} representation of the configuration value with the given name.
	 * If no such value exists or the value can not be interpreted as a String a {@link ConfigurationException} is thrown.
	 */
	public String getString(String name) throws ConfigurationException;

	/**
	 * Returns an integer representation of the configuration value with the given name.
	 * If no such value exists or the value can not be interpreted as an int a {@link ConfigurationException} is thrown.
	 */
	public int getInt(String name) throws ConfigurationException;

	/**
	 * Returns a long representation of the configuration value with the given name.
	 * If no such value exists or the value can not be interpreted as a long a {@link ConfigurationException} is thrown.
	 */
	public long getLong(String name) throws ConfigurationException;

	/**
	 * Returns a boolean representation of the configuration value with the given name.
	 * If no such value exists or the value can not be interpreted as a boolean a {@link ConfigurationException} is thrown.
	 */
	public boolean getBoolean(String name) throws ConfigurationException;

}
