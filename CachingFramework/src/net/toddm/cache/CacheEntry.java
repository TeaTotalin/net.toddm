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

/**
 * Represents an entry in a cache.
 * Cache entries have unique keys, values, TTLs, and creation and modification timestamps.
 * They optionally also have an ETag value and source URI.
 * 
 * @author Todd S. Murchison
 */
public class CacheEntry {

	private final String _key;
	private final String _valueString;
	private final byte[] _valueBytes;
	private final Long _ttl;
	private final String _etag;
	private final Long _maxStale;
	private final URI _sourceUri;
	private final Long _timestampCreated;
	private final Long _timestampModified;
	private Long _timestampUsed;
	private final CachePriority _priority;

	/**
	 * Creates an instance of {@link CacheEntry} from the given values.
	 * Creation and modification timestamps are set to the current system time.
	 * <p>
	 * @param key A unique key value for this cache entry.
	 * @param value The value of this cache entry (can be NULL).
	 * @param ttl The Time To Live (TTL) of this cache entry. Any value less than 1 will result in an entry that is always stale.
	 * @param maxStale The maximum amount of time, in milliseconds, that use of the entry should continue after it has expired.
	 * @param eTag An optional entity tag value. This can be NULL if your cache does not make use of ETags.
	 * @param sourceUri An optional source URI. This can be NULL if you do not make use of URIs.
	 * @param priority An indication of relative priority for this cache entry. Depending on implementation, cache providers may use this as a hint when evicting records.
	 */
	public CacheEntry(String key, String value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {

		// Validate parameters
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		if(priority == null) { throw(new IllegalArgumentException("'priority' can not be NULL")); }

		this._key = key;
		this._valueString = value;
		this._valueBytes = null;
		this._ttl = ttl;
		this._etag = eTag;
		this._maxStale = maxStale;
		this._sourceUri = sourceUri;
		this._timestampCreated = System.currentTimeMillis();
		this._timestampModified = this._timestampCreated;
		this._timestampUsed = this._timestampCreated;
		this._priority = priority;
	}

	/**
	 * Creates an instance of {@link CacheEntry} from the given values.
	 * Creation and modification timestamps are set to the current system time.
	 * <p>
	 * @param key A unique key value for this cache entry.
	 * @param value The value of this cache entry (can be NULL).
	 * @param ttl The Time To Live (TTL) of this cache entry. Any value less than 1 will result in an entry that is always stale.
	 * @param maxStale The maximum amount of time, in milliseconds, that use of the entry should continue after it has expired.
	 * @param eTag An optional entity tag value. This can be NULL if your cache does not make use of ETags.
	 * @param sourceUri An optional source URI. This can be NULL if you do not make use of URIs.
	 * @param priority An indication of relative priority for this cache entry. Depending on implementation, cache providers may use this as a hint when evicting records.
	 */
	public CacheEntry(String key, byte[] value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {

		// Validate parameters
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		if(priority == null) { throw(new IllegalArgumentException("'priority' can not be NULL")); }

		this._key = key;
		this._valueString = null;
		this._valueBytes = value;
		this._ttl = ttl;
		this._etag = eTag;
		this._maxStale = maxStale;
		this._sourceUri = sourceUri;
		this._timestampCreated = System.currentTimeMillis();
		this._timestampModified = this._timestampCreated;
		this._timestampUsed = this._timestampCreated;
		this._priority = priority;
	}

	/**
	 * This version of the constructor is for use by {@link CacheProvider} implementers that need to 
	 * deserialize instances of {@link CacheEntry} and therefore need access to setting all fields.
	 * <p>
	 * @param key A unique key value for this cache entry.
	 * @param valueString The string value of this cache entry (can be NULL).
	 * @param valueBytes The bytes value of this cache entry (can be NULL).
	 * @param ttl The Time To Live (TTL) of this cache entry. Any value less than 1 will result in an entry that is always stale.
	 * @param maxStale The maximum amount of time, in milliseconds, that use of the entry should continue after it has expired.
	 * @param eTag An optional entity tag value. This can be NULL if your cache does not make use of ETags.
	 * @param sourceUri An optional source URI. This can be NULL if you do not make use of URIs.
	 * @param timestampCreated The epoch representation of the creation timestamp for this cache entry.
	 * @param timestampModified The epoch representation of the creation timestamp for this cache entry.
	 * @param timestampUsed The epoch representation of the last time this cache entry was loaded from cache.
	 * @param priority An indication of relative priority for this cache entry. Depending on implementation, cache providers may use this as a hint when evicting records.
	 */
	public CacheEntry(
			String key, 
			String valueString, 
			byte[] valueBytes, 
			long ttl, 
			long maxStale, 
			String eTag, 
			URI sourceUri, 
			long timestampCreated, 
			long timestampModified, 
			long timestampUsed, 
			CachePriority priority) 
	{

		// Validate parameters
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }
		
		if((valueString != null) && (valueString.length() <= 0)) { valueString = null; }
		if((valueBytes != null) && (valueBytes.length <= 0)) { valueBytes = null; }
		if((valueString != null) && (valueBytes != null)) { throw(new IllegalArgumentException("A CacheEntry should only have either a string value OR a bytes value")); }

		if(timestampCreated > System.currentTimeMillis()) { throw(new IllegalArgumentException("'timestampCreated' should not be in the future")); }
		if(timestampModified > System.currentTimeMillis()) { throw(new IllegalArgumentException("'timestampModified' should not be in the future")); }
		if(priority == null) { throw(new IllegalArgumentException("'priority' can not be NULL")); }

		this._key = key;
		this._valueString = valueString;
		this._valueBytes = valueBytes;
		this._ttl = ttl;
		this._etag = eTag;
		this._maxStale = maxStale;
		this._sourceUri = sourceUri;
		this._timestampCreated = timestampCreated;
		this._timestampModified = timestampModified;
		this._timestampUsed = timestampUsed;
		this._priority = priority;
	}

	/** Returns the unique key for this cache entry. */
	public String getKey() { return(this._key); }

	/** Returns the String value for this cache entry. May return <b>null</b>. */
	public String getStringValue() { return(this._valueString); }

	/** Returns the bytes value for this cache entry. May return <b>null</b>. */
	public byte[] getBytesValue() { return(this._valueBytes); }

	/** Returns the Time To Live (TTL) for this cache entry. */
	public Long getTtl() { return(this._ttl); }

	/** Returns the ETag for this cache entry if it has one. */
	public String getEtag() { return(this._etag); }

	/** Returns the "max stale" value for this cache entry. */
	public Long getMaxStale() { return(this._maxStale); }

	/** Returns the source URI for this cache entry if it has one. */
	public URI getUri() { return(this._sourceUri); }

	/** Returns an epoch representation of the creation timestamp for this cache entry. */
	public Long getTimestampCreated() { return(this._timestampCreated); }

	/** Returns an epoch representation of the most recent time that this cache entry was updated. */
	public Long getTimestampModified() { return(this._timestampModified); }

	/** Returns an epoch representation of the most recent time that this cache entry was returned from the cache. */
	public Long getTimestampUsed() { return(this._timestampUsed); }

	/**
	 *  Updates the last time this cache entry was loaded from the cache provider.
	 * @param value An epoch representation of the most recent time that this cache entry was returned from the cache.
	 */
	void setTimestampUsed(long value) {
		this._timestampUsed = value;
	}

	/**
	 * Returns the caching priority value for this cache entry.
	 * The meaning of this value may vary based on cache provider implementation.
	 * Cache providers may use this value as a hint when evicting records.
	 */
	public CachePriority getPriority() { return(this._priority); }

	/**
	 * If this {@link CacheEntry} instance has expired, based on it's TTL value and creation 
	 * time, this method will return <b>true</b>, otherwise <b>false</b> is returned.
	 */
	public boolean hasExpired() {

		// NULL indicates never expiring
		if(this.getTtl() == null) { return(false); }

		// Check if we have overflowed long max value and if we have default to max value
		long expiresOn = this.getTimestampCreated() + this.getTtl();
		if(expiresOn < this.getTimestampCreated()) {
			expiresOn = Long.MAX_VALUE;
		}

		long now = System.currentTimeMillis();
		return(expiresOn < now);
	}

	/**
	 * If this {@link CacheEntry} instance should no longer be used, based on it's TTL value, max-stale 
	 * value, and creation time, this method will return <b>true</b>, otherwise <b>false</b> is returned.
	 * <p>
	 * Even an expired cache entry can still be considered usable as long as it's age does not yet 
	 * exceed it's TTL value plus it's max-stale value.
	 */
	public boolean hasExceededStaleUse() {

		// If we have not yet expired then we can not have exceeded stale use
		if(!this.hasExpired()) { return(false); }

		// If we have expired and have no stale use then we have exceeded
		if(this.getMaxStale() == null) { return(true); }

		// Check if we have overflowed long max value and if we have default to max value
		long staleExpiresOn = this.getTimestampCreated() + this.getTtl() + this.getMaxStale();
		if(staleExpiresOn < this.getTimestampCreated()) {
			staleExpiresOn = Long.MAX_VALUE;
		}

		long now = System.currentTimeMillis();
		return(staleExpiresOn < now);
	}

}
