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
 * This caching implementation ignores {@link CacheEntry.Priority} when doing LRU eviction.
 * <p>
 * @author Todd S. Murchison
 */
public class MemoryCacheProvider implements CacheProvider {

	private static final String _DefaultNamespace = "e98fa3ee-cb8d-4e37-8b43-adb04036031a";

	private final String _namespace;
	private final CacheEntryAgeComparator _cacheEntryAgeComparator = new CacheEntryAgeComparator();
	private final ConcurrentHashMap<String, CacheEntry> _keyToEntry = new ConcurrentHashMap<String, CacheEntry>();
	private final LoggingProvider _logger;

    private int _lruCap;

	private String getLookupKey(String key) {
		return(String.format(Locale.US, "%1$s:%2$s", this._namespace, key));
	}

    /**
     * Create an instance of {@link MemoryCacheProvider} with the given namespace.
     *
     * @param namespace <b>OPTIONAL</b> If NULL then the cache instance is created in the default namespace.
     * @param initialLruCap The maximum number of entries the cache should contain after a call to {@link #trimLru()}.
	 * @param logger <b>OPTIONAL</b> If NULL no logging callbacks are made otherwise the provided implementation will get log messages.
	 */
    public MemoryCacheProvider(String namespace, int initialLruCap, LoggingProvider logger) {
        if (initialLruCap < 0) { throw (new IllegalArgumentException("'initialLruCap' can not be negative")); }
        this._namespace = (((namespace == null) || (namespace.length() <= 0)) ? _DefaultNamespace : namespace);
        this._lruCap = initialLruCap;
        this._logger = logger;
    }

	/** {@inheritDoc} */
	@Override
	public boolean add(String key, String value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {

		// The constructor used in the line below does argument validation
		this._keyToEntry.put(this.getLookupKey(key), new CacheEntry(key, value, ttl, maxStale, eTag, sourceUri, priority));
		if(this._logger != null) {
			this._logger.debug("Cache entry added [key:%1$s ttl:%2$d maxStale:%3$d eTag:%4$s sourceUri:%5$s]", key, ttl, maxStale, eTag, sourceUri);
		}
		return(true);
	}

	/** {@inheritDoc} */
	@Override
	public boolean add(String key, byte[] value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {

		// The constructor used in the line below does argument validation
		this._keyToEntry.put(this.getLookupKey(key), new CacheEntry(key, value, ttl, maxStale, eTag, sourceUri, priority));
		if(this._logger != null) {
			this._logger.debug("Cache entry added [key:%1$s ttl:%2$d maxStale:%3$d eTag:%4$s sourceUri:%5$s]", key, ttl, maxStale, eTag, sourceUri);
		}
		return(true);
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
		List<CacheEntry> results = new ArrayList<CacheEntry>(this._keyToEntry.size());
		for(CacheEntry entry : this._keyToEntry.values()) {
			if(allowExpired) {
				results.add(entry);
			} else if(!entry.hasExpired()) {
				results.add(entry);
			}
		}
		return(results);
	}

	/** {@inheritDoc} */
	@Override
	public int size(boolean allowExpired) {
		if(allowExpired) {
			return(this._keyToEntry.size());
		} else {
			return(this.getAll(allowExpired).size());
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean containsKey(String key, boolean allowExpired) {
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		if(allowExpired) {
			return(this._keyToEntry.containsKey(this.getLookupKey(key)));
		} else {
			CacheEntry entry = this._keyToEntry.get(this.getLookupKey(key));
			return((entry != null) && (!entry.hasExpired()));
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean remove(String key) {
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		this._keyToEntry.remove(this.getLookupKey(key));
		return(true);
	}

	/** {@inheritDoc} */
	@Override
	public boolean removeAll() {
		this._keyToEntry.clear();
		return(true);
	}

	/** {@inheritDoc} */
	@Override
	public boolean trimLru() {
		List<CacheEntry> entries = this.getAll(true);
		if(entries.size() > this._lruCap) {
			Collections.sort(entries, this._cacheEntryAgeComparator);
			for(int i = this._lruCap; i < entries.size(); i++) {
				this.remove(entries.get(i).getKey());
			}
		}
		return(true);
	}

	/** {@inheritDoc} */
	@Override
	public void setLruCap(int maxCacheSize) {
		this._lruCap = maxCacheSize;
	}

	/** {@inheritDoc} */
	@Override
	public int getLruCap() {
		return(this._lruCap);
	}

}
