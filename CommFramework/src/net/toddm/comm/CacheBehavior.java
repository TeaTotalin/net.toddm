package net.toddm.comm;

/** An enumeration of caching behaviors supported by the Comm Manager. */
public enum CacheBehavior {

	/**
	 * Indicates to the Comm Manager that normal caching behavior should be used. If the response includes cache control headers they will be honored. 
	 * If the response does not include cache control directives then the response is cached without a TTL and will only get evicted if it reaches an LRU limit.
	 */
	NORMAL,

	/** Indicates to the Comm Manager that the related data should not be cached. */
	DO_NOT_CACHE,

	/** Indicates to the Comm Manager that the related request should not result in a network request and should be serviced only from cache. */
	GET_ONLY_FROM_CACHE,

	/**
	 * Indicates to the Comm Manager that the related data should only be cached if there are response headers defining cache behavior.
	 * If the response does not include cache control directives then the response is not cached.
	 */
	SERVER_DIRECTED_CACHE

}
