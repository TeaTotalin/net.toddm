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
	private final URI _sourceUri;
	private final Long _timestampCreated;
	private final Long _timestampModified;

	/**
	 * Creates an instance of {@link CacheEntry} from the given values.
	 * Creation and modification timestamps are set to the current system time.
	 * <p>
	 * @param key A unique key value for this cache entry.
	 * @param value The value of this cache entry (can be NULL).
	 * @param ttl The Time To Live (TTL) of this cache entry. Any value less than 1 will result in an entry that is always stale.
	 * @param eTag An optional entity tag value. This can be NULL if your cache does not make use of ETags.
	 * @param sourceUri An optional source URI. This can be NULL if you do not make use of URIs.
	 */
	protected CacheEntry(String key, String value, long ttl, String eTag, URI sourceUri) {

		// Validate parameters
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }

		this._key = key;
		this._valueString = value;
		this._valueBytes = null;
		this._ttl = ttl;
		this._etag = eTag;
		this._sourceUri = sourceUri;
		this._timestampCreated = System.currentTimeMillis();
		this._timestampModified = this._timestampCreated;
	}

	/**
	 * Creates an instance of {@link CacheEntry} from the given values.
	 * Creation and modification timestamps are set to the current system time.
	 * <p>
	 * @param key A unique key value for this cache entry.
	 * @param value The value of this cache entry (can be NULL).
	 * @param ttl The Time To Live (TTL) of this cache entry. Any value less than 1 will result in an entry that is always stale.
	 * @param eTag An optional entity tag value. This can be NULL if your cache does not make use of ETags.
	 * @param sourceUri An optional source URI. This can be NULL if you do not make use of URIs.
	 */
	protected CacheEntry(String key, byte[] value, long ttl, String eTag, URI sourceUri) {

		// Validate parameters
		if((key == null) || (key.length() <= 0)) { throw(new IllegalArgumentException("'key' can not be NULL or empty")); }

		this._key = key;
		this._valueString = null;
		this._valueBytes = value;
		this._ttl = ttl;
		this._etag = eTag;
		this._sourceUri = sourceUri;
		this._timestampCreated = System.currentTimeMillis();
		this._timestampModified = this._timestampCreated;
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

	/** Returns the source URI for this cache entry if it has one. */
	public URI getUri() { return(this._sourceUri); }

	/** Returns an epoch representation of the creation timestamp for this cache entry. */
	public Long getTimestampCreated() { return(this._timestampCreated); }

	/** Returns an epoch representation of the most recent time that this cache entry was updated. */
	public Long getTimestampModified() { return(this._timestampModified); }

	/**
	 * If this {@link CacheEntry} instance has expired, based on it's TTL value and last 
	 * modified time, this method will return <b>true</b>, otherwise <b>false</b> is returned.
	 */
	public boolean hasExpired() {
		if((this.getTtl() == null) || (this.getTtl() == Long.MAX_VALUE)) {
			// Max value and NULL both indicate never expiring, but we don't want to break below
			return(false);
		}
		long now = System.currentTimeMillis();
		long expiresOn = this.getTimestampModified() + this.getTtl();
		return(expiresOn < now);
	}

}
