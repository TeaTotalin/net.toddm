package net.toddm.cache;

/** An enumeration of all possible caching priorities a {@link CacheEntry} instance can have. */
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
