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
package net.toddm.cache;

/**
 * An enumeration of all possible caching priorities a {@link CacheEntry} instance can have.
 * <p>
 * @author Todd S. Murchison
 */
public enum CachePriority {

	/**
	 * Indicates to a caching provider that the related data has a low caching priority. 
	 * Depending on provider implementation, entries with this priority level may be more likely to be evicted from the cache when LRU thresholds are reached.
	 */
	LOW,
	
	/** Indicates to a caching provider that the related data has a normal (most common) caching priority. */
	NORMAL,
	
	/**
	 * Indicates to a caching provider that the related data has a high caching priority.
	 * Depending on provider implementation, entries with this priority level are less likely to be evicted from the cache when LRU thresholds are reached.
	 */
	HIGH

}
