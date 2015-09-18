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

import java.net.URI;
import java.util.List;

/** Cache providers implement this interface. */
public interface CacheProvider {

	/**
	 * Adds an entry to the cache if one with the given key does not already exist. If there is a preexisting entry with the given key 
	 * then that entry is updated with the given data.
	 * <p>
	 * @param key The unique key of the cache entry.
	 * @param value The value of the cache entry.
	 * @param ttl The Time To Live (TTL) of the cache entry.
	 * @param eTag An entity tag value for the cache entry.
	 * @param sourceUri A source URI for the cache entry.
	 * @return <b>true</b> if the add or update operation was successful, <b>false</b> otherwise.
	 */
	public boolean add(String key, String value, long ttl, String eTag, URI sourceUri);

	/**
	 * Adds an entry to the cache if one with the given key does not already exist. If there is a preexisting entry with the given key 
	 * then that entry is updated with the given data.
	 * <p>
	 * @param key The unique key of the cache entry.
	 * @param value The value of the cache entry.
	 * @param ttl The Time To Live (TTL) of the cache entry.
	 * @param eTag An entity tag value for the cache entry.
	 * @param sourceUri A source URI for the cache entry.
	 * @return <b>true</b> if the add or update operation was successful, <b>false</b> otherwise.
	 */
	public boolean add(String key, byte[] value, long ttl, String eTag, URI sourceUri);

	/**
	 * Returns the {@link CacheEntry} with the given key. If no entry is found for the given key then <b>null</b> is returned.
	 * <p>
	 * @param key The unique key of the cache entry to return.
	 * @param allowExpired If set, this method can return expired cache entries as well.
	 * @return The {@link CacheEntry} with the given key or <b>null</b> if the key is not found in the cache.
	 */
	public CacheEntry get(String key, boolean allowExpired);

	/**
	 * @param allowExpired If set, the list of entries returned will include entries that have expired.
	 * @return A list of all cache entries.
	 */
	public List<CacheEntry> getAll(boolean allowExpired);

	/**
	 * Returns the size (the total number of current entries) of the cache.
	 * @param allowExpired If set, this method will count expired cache entries as well.
	 * @return The size (the total number of current entries) of the cache.
	 */
	public int size(boolean allowExpired);

	/**
	 * Returns <b>true</b> if the cache contains an entry for the given key, <b>false</b> otherwise.
	 * @param key The unique key of the cache entry to look for.
	 * @param allowExpired If set, this method can return <b>true</b> for expired cache entries.
	 * @return <b>true</b> if the cache contains an entry for the given key.
	 */
	public boolean containsKey(String key, boolean allowExpired);

	/**
	 * Removes the cache entry with the given key, if there is one.
	 * @return <b>true</b> if the operation was successful, <b>false</b> otherwise.
	 */
	public boolean remove(String key);

	/**
	 * Removes all the entries from the cache, leaving an empty cache.
	 * @return <b>true</b> if the operation was successful, <b>false</b> otherwise.
	 */
	public boolean removeAll();

	/**
	 * Enforces a Least Recently Used cap on the cache by removing the 
	 * oldest entries if needed until the LRU cap size is reached.
	 */
	public boolean trimLru();

	/**
	 * Sets the LRU cap cache size for use when enforcing the LRU cap.
	 * Note that it is up to client code to call {@link #trimLru()} to enforce this limit.
	 * @param maxCacheSize The maximum number of entries the cache should contain after a call to {@link #trimLru()}.
	 */
	public void setLruCap(int maxCacheSize);

	/**
	 * Returns the LRU cap cache size for use when enforcing the LRU cap.
	 * Note that it is up to client code to call {@link #trimLru()} to enforce this limit.
	 */
	public int getLruCap();

}
