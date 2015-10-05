package net.toddm.comm;

/** An enumeration of caching behaviors supported by the Comm Manager. */
public enum CacheBehavior {

	/** Indicates to the Comm Manager that normal caching behavior should be used. */
	NORMAL,

	/** Indicates to the Comm Manager that the related data should not be cached. */
	DO_NOT_CACHE,

	/** Indicates to the Comm Manager that the related request should not result in a network request and should be serviced only from cache. */
	GET_ONLY_FROM_CACHE

}
