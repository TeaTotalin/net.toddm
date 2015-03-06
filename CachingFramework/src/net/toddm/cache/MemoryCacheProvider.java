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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of the {@link CacheProvider} interface that is backed by runtime memory.
 * This cache will not persist across different instances of a process.
 * This caching implementation is thread safe.
 * <p>
 * @author Todd S. Murchison
 */
public class MemoryCacheProvider implements CacheProvider {

	private final ConcurrentHashMap<String, CacheEntry> _keyToEntry = new ConcurrentHashMap<String, CacheEntry>();
	private final CacheEntryAgeComparator _cacheEntryAgeComparator = new CacheEntryAgeComparator();

	/** {@inheritDoc} */
	@Override
	public void add(String key, String value, long ttl, String eTag, URI sourceUri) {

		// The constructor used in the line below does argument validation
		this._keyToEntry.put(key, new CacheEntry(key, value, ttl, eTag, sourceUri));
	}

	/** {@inheritDoc} */
	@Override
	public void add(String key, byte[] value, long ttl, String eTag, URI sourceUri) {

		// The constructor used in the line below does argument validation
		this._keyToEntry.put(key, new CacheEntry(key, value, ttl, eTag, sourceUri));
	}

	/** {@inheritDoc} */
	@Override
	public CacheEntry get(String key, boolean allowExpired) {
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		CacheEntry result = this._keyToEntry.get(key);
		if((result != null) && (!allowExpired) && (result.hasExpired())) {
			return(null);
		}
		return(result);
	}

	/** {@inheritDoc} */
	@Override
	public List<CacheEntry> getAll(boolean allowExpired) {
		List<CacheEntry> results = new ArrayList<CacheEntry>(this._keyToEntry.values());
		if(!allowExpired) {
			List<String> keysToKill = new ArrayList<String>(this._keyToEntry.size());
			for(CacheEntry entry : this._keyToEntry.values()) {
				if(entry.hasExpired()) {
					keysToKill.add(entry.getKey());
				}
			}
			for(String killKey : keysToKill) {
				this._keyToEntry.remove(killKey);
			}
		}
		return(results);
	}

	/** {@inheritDoc} */
	@Override
	public void remove(String key) {
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		this._keyToEntry.remove(key);
	}

	/** {@inheritDoc} */
	@Override
	public void removeAll() {
		this._keyToEntry.clear();
	}

	/** {@inheritDoc} */
	@Override
	public void trimLru(int maxEntries) {
		List<CacheEntry> entries = this.getAll(true);
		if(entries.size() <= maxEntries) { return; }
		Collections.sort(entries, this._cacheEntryAgeComparator);
		for(int i = maxEntries; i < entries.size(); i++) {
			this.remove(entries.get(i).getKey());
		}
	}

}
