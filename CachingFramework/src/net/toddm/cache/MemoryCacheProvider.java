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
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of the {@link CacheProvider} interface that is backed by runtime memory.
 * This cache will not persist across different instances of a process.
 * This caching implementation is thread safe.
 * <p>
 * @author Todd S. Murchison
 */
public class MemoryCacheProvider implements CacheProvider {

	private static final String _DefaultNamespace = "e98fa3ee-cb8d-4e37-8b43-adb04036031a";

	private final String _namespace;
	private final CacheEntryAgeComparator _cacheEntryAgeComparator = new CacheEntryAgeComparator();
	private final ConcurrentHashMap<String, CacheEntry> _keyToEntry = new ConcurrentHashMap<String, CacheEntry>();
	
	private String getLookupKey(String key) {
		return(String.format(Locale.US, "%1$s:%2$s", this._namespace, key));
	}

	/**
	 * Create an instance of {@link MemoryCacheProvider} with the given namespace.
	 * @param namespace <b>OPTIONAL</b> If NULL then the cache instance is created in the default namespace.
	 */
	public MemoryCacheProvider(String namespace) {
		this._namespace = (((namespace == null) || (namespace.length() <= 0)) ? _DefaultNamespace : namespace);
	}

	/** {@inheritDoc} */
	@Override
	public void add(String key, String value, long ttl, String eTag, URI sourceUri) {

		// The constructor used in the line below does argument validation
		this._keyToEntry.put(this.getLookupKey(key), new CacheEntry(key, value, ttl, eTag, sourceUri));
	}

	/** {@inheritDoc} */
	@Override
	public void add(String key, byte[] value, long ttl, String eTag, URI sourceUri) {

		// The constructor used in the line below does argument validation
		this._keyToEntry.put(this.getLookupKey(key), new CacheEntry(key, value, ttl, eTag, sourceUri));
	}

	/** {@inheritDoc} */
	@Override
	public CacheEntry get(String key, boolean allowExpired) {
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		CacheEntry result = this._keyToEntry.get(this.getLookupKey(key));
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
				this._keyToEntry.remove(this.getLookupKey(killKey));
			}
		}
		return(results);
	}

	/** {@inheritDoc} */
	@Override
	public void remove(String key) {
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		this._keyToEntry.remove(this.getLookupKey(key));
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
