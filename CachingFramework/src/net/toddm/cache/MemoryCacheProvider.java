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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

	// The larger the weight value the less likely to evict
	@SuppressWarnings("serial")
	private static final Map<CachePriority, Double> _PriorityToWeight = new HashMap<CachePriority, Double>(3) {{
		put(CachePriority.HIGH,		1d);
		put(CachePriority.NORMAL,	0.8);
		put(CachePriority.LOW,		0.5);
	}};

	private final String _namespace;
	private final CacheEntryLastUseComparator _cacheEntryLastUseComparator = new CacheEntryLastUseComparator();
	private final ConcurrentHashMap<String, CacheEntryWithEvictionScore> _keyToEntry = new ConcurrentHashMap<String, CacheEntryWithEvictionScore>();
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
		CacheEntryWithEvictionScore newEntry = new CacheEntryWithEvictionScore(key, value, ttl, maxStale, eTag, sourceUri, priority);
		this._keyToEntry.put(this.getLookupKey(key), newEntry);
		this.updateEvictionScores(newEntry.getTimestampUsed());

		if(this._logger != null) {
			this._logger.debug("Cache entry added [key:%1$s ttl:%2$d maxStale:%3$d eTag:%4$s sourceUri:%5$s]", key, ttl, maxStale, eTag, sourceUri);
		}
		return(true);
	}

	/** {@inheritDoc} */
	@Override
	public boolean add(String key, byte[] value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {

		// The constructor used in the line below does argument validation
		CacheEntryWithEvictionScore newEntry = new CacheEntryWithEvictionScore(key, value, ttl, maxStale, eTag, sourceUri, priority);
		this._keyToEntry.put(this.getLookupKey(key), newEntry);
		this.updateEvictionScores(newEntry.getTimestampUsed());

		if(this._logger != null) {
			this._logger.debug("Cache entry added [key:%1$s ttl:%2$d maxStale:%3$d eTag:%4$s sourceUri:%5$s]", key, ttl, maxStale, eTag, sourceUri);
		}
		return(true);
	}

	/** {@inheritDoc} */
	@Override
	public CacheEntry get(String key, boolean allowExpired) {
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		CacheEntryWithEvictionScore result = this._keyToEntry.get(this.getLookupKey(key));
		if(result != null) {
			if((!allowExpired) && (result.hasExpired())) {
				return(null);
			}
			result.setTimestampUsed(System.currentTimeMillis());
			this.updateEvictionScores(result.getTimestampUsed());
		}
		return(result);
	}

	/** {@inheritDoc} */
	@Override
	public List<CacheEntry> getAll(boolean allowExpired) {
		return(this.getAll(allowExpired, true));
	}

	private List<CacheEntry> getAll(boolean allowExpired, boolean updateUsedTime) {
		long loadTime = System.currentTimeMillis();
		List<CacheEntry> results = new ArrayList<CacheEntry>(this._keyToEntry.size());
		for(CacheEntryWithEvictionScore entry : this._keyToEntry.values()) {
			if(allowExpired) {
				if(updateUsedTime) { entry.setTimestampUsed(loadTime); }
				results.add(entry);
			} else if(!entry.hasExpired()) {
				if(updateUsedTime) { entry.setTimestampUsed(loadTime); }
				results.add(entry);
			}
		}
		if((results.size() > 0) && (updateUsedTime)) {
			this.updateEvictionScores(loadTime);
		}
		return(results);
	}

	/** {@inheritDoc} */
	@Override
	public int size(boolean allowExpired) {
		if(allowExpired) {
			return(this._keyToEntry.size());
		} else {
			return(this.getAll(allowExpired, false).size());
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean containsKey(String key, boolean allowExpired) {
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		if(allowExpired) {
			return(this._keyToEntry.containsKey(this.getLookupKey(key)));
		} else {
			CacheEntryWithEvictionScore entry = this._keyToEntry.get(this.getLookupKey(key));
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

	/**
	 * {@inheritDoc}
	 * 
	 * This implementation uses "cache priority" and "last used time" to 
	 * provide eviction based on a basic normalized linear weighting algorithm:
	 * <pre>
     *   Sx = (Wx * ( (Fx - Fmin) / (Fmax - Fmin) ))
     * </pre>
	 */
	@Override
	public boolean trimLru() {
		if(this._keyToEntry.size() > this._lruCap) {

			// Sort all cache entries based on their eviction scores
			List<CacheEntryWithEvictionScore> sortedEntries = new ArrayList<CacheEntryWithEvictionScore>(this._keyToEntry.values());
			Collections.sort(sortedEntries);

			// Remove entries that are past the LRU size
			for(int i = this._lruCap; i < sortedEntries.size(); i++) {
				this.remove(sortedEntries.get(i).getKey());
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

	/**
	 * Returns the oldest "last use" time found in the cache, or the current time if the cache is empty.
	 * Time is returned as an epoch time in milliseconds.
	 */
	private long getOldestUse() {

		// Sort all cache entries based on their last use time
		long result = System.currentTimeMillis();
		if(this._keyToEntry.size() > 0) {
			List<CacheEntryWithEvictionScore> sortedEntries = new ArrayList<CacheEntryWithEvictionScore>(this._keyToEntry.values());
			Collections.sort(sortedEntries, this._cacheEntryLastUseComparator);
			result = sortedEntries.get(sortedEntries.size() - 1).getTimestampUsed();
		}
		return(result);
	}

	private void updateEvictionScores(long newestUseInCache) {
		if(this._logger != null) { this._logger.debug("######### UPDATING EVICTION SCORES #########"); }
		long oldestUseInCache = this.getOldestUse();
		for(CacheEntryWithEvictionScore entry : this._keyToEntry.values()) {
			entry.updateEvictionScore(newestUseInCache, oldestUseInCache);

			if(this._logger != null) {
				this._logger.debug("## %.12f = (%.12f * (1 + (%d / %d))) [key:%s]", 
						entry.getEvictionScore(), 
						_PriorityToWeight.get( entry.getPriority() ), 
						(entry.getTimestampUsed() - oldestUseInCache), 
						(newestUseInCache - oldestUseInCache),
						entry.getKey());
			}
		}
	}

	/**
	 * A simple sub-class of {@link CacheEntry} that provides an eviction score for the entry.
	 * Instances of this class are comparable, sortable, etc. based on their eviction scores.
	 */
	private class CacheEntryWithEvictionScore extends CacheEntry implements Comparable<CacheEntryWithEvictionScore>, Comparator<CacheEntryWithEvictionScore> {

		private double _evictionScore;

		public CacheEntryWithEvictionScore(String key, String value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {
			super(key, value, ttl, maxStale, eTag, sourceUri, priority);

			// Calculate an initial eviction score (to avoid timing problems with access to our ConcurrentHashMap later)
			this.updateEvictionScore(this.getTimestampUsed(), MemoryCacheProvider.this.getOldestUse());
		}

		public CacheEntryWithEvictionScore(String key, byte[] value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {
			super(key, value, ttl, maxStale, eTag, sourceUri, priority);

			// Calculate an initial eviction score (to avoid timing problems with access to our ConcurrentHashMap later)
			this.updateEvictionScore(this.getTimestampUsed(), MemoryCacheProvider.this.getOldestUse());
		}

		double getEvictionScore() { return(this._evictionScore); }

		/**
	     * Updates this entrie's eviction scores using a basic normalized linear weighting algorithm:
	     *   Sx = (Wx * ( (Fx -Fmin) / (Fmax - Fmin) ))
	     *   <ul>
	     *     <li>Sx is the eviction score for the current entry</li>
	     *     <li>Wx is an arbitrarily assigned weight (our priority) for the current entry</li>
	     *     <li>Fx is the value (last use timestamp) for the current entry</li>
	     *     <li>Fmax is the largest available value of F (most recent use timestamp) currently in the cache.</li>
	     *     <li>Fmin is the smallest available value of F (most recent use timestamp) currently in the cache.</li>
	     *   </ul>
		 */
		void updateEvictionScore(long newestUseInCache, long oldestUseInCache) {
			double normalizedFactor = 1d;
			if(newestUseInCache != oldestUseInCache) {
				// Because we are normalizing time we will normalize on the scaled range of time represented in the cache
				normalizedFactor = (double)(
						(double)(this.getTimestampUsed() - oldestUseInCache) / 
						(double)(newestUseInCache - oldestUseInCache)
					);
			}
			this._evictionScore = (double)( _PriorityToWeight.get(this.getPriority()) * (1 + normalizedFactor ) );
		}

		/** {@inheritDoc} */
		@Override
		public int compareTo(CacheEntryWithEvictionScore o) {
			// Returns a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
			return(this.compare(this, o));
		}

		/** {@inheritDoc} */
		@Override
		public int compare(CacheEntryWithEvictionScore entryA, CacheEntryWithEvictionScore entryB) {
			// Returns a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
			double scoreDiff = entryB.getEvictionScore() - entryA.getEvictionScore();
			if(scoreDiff < 0) {
				return(-1);
			} else if(scoreDiff > 0) {
				return(1);
			} else {
				return(0);
			}
		}
	}

}
