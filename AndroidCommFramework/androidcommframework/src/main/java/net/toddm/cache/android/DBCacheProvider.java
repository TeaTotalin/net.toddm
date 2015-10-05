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
package net.toddm.cache.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CacheException;
import net.toddm.cache.CachePriority;
import net.toddm.cache.CacheProvider;
import net.toddm.cache.LoggingProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of the {@link CacheProvider} interface that is backed by the SQLite database.
 * This caching implementation is thread safe.
 * <p/>
 *
 * @author Todd S. Murchison
 */
public class DBCacheProvider extends SQLiteOpenHelper implements CacheProvider {

    //-------------------------------------------------------------------------------
    // Enforce one instance per namespace/database
    private DBCacheProvider(Context context, String databaseName, int databaseVersion, int initialLruCap, LoggingProvider loggingProvider) {
        super(context, databaseName, null, databaseVersion);
        this._databaseName = databaseName;
        this._databaseVersion = databaseVersion;
        this._lruCap = initialLruCap;
        this._logger = loggingProvider;
        if(this._logger != null) { this._logger.debug("Using caching database '%1$s'", databaseName); }
    }

    /**
     * Returns an instance of {@link DBCacheProvider} for the given namespace. The instance will be
     * created if needed or returned if an instance has previously been created for the given namespace.
     *
     * @param context         An Android {@link Context} for use when interacting with SQLite.
     * @param namespace       The namespace of the cache that is returned.
     * @param databaseVersion The version of the database (starting at 1). If the database already exists and the version
     *                        do not match then calls to {@link #onUpgrade} on {@link #onDowngrade} will be triggered.
     * @param initialLruCap   The maximum number of entries the cache should contain after a call to {@link #trimLru()}.
     * @param loggingProvider <b>OPTIONAL</b> If NULL no logging callbacks are made otherwise the provided implementation will get log messages.
     */
    public static DBCacheProvider getInstance(Context context, String namespace, int databaseVersion, int initialLruCap, LoggingProvider loggingProvider) {
        if (context == null) { throw (new IllegalArgumentException("'context' can not be NULL")); }
        if ((namespace == null) || (namespace.length() <= 0)) { throw (new IllegalArgumentException("'namespace' can not be NULL or empty")); }
        if (initialLruCap < 0) { throw (new IllegalArgumentException("'initialLruCap' can not be negative")); }
        if (!_NamespaceToCache.containsKey(namespace)) {
            synchronized (_NamespaceToCacheLock) {
                if (!_NamespaceToCache.containsKey(namespace)) {
                    _NamespaceToCache.put(namespace, new DBCacheProvider(context, namespace, databaseVersion, initialLruCap, loggingProvider));
                }
            }
        }
        return (_NamespaceToCache.get(namespace));
    }

    private static volatile Object _NamespaceToCacheLock = new Object();
    private static Map<String, DBCacheProvider> _NamespaceToCache = new HashMap<String, DBCacheProvider>();

    private int _lruCap;

    private final LoggingProvider _logger;
    private final String _databaseName;
    private final int _databaseVersion;
    private volatile Object _databaseAccessLock = new Object();

    private static final String[] _IDColumn = new String[]{"id"};
    private static final String _DatabaseTableName = "cache";
    private static final String _DatabaseCreateSQL =
            "CREATE TABLE IF NOT EXISTS " + _DatabaseTableName + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "key TEXT NOT NULL UNIQUE, " +
                    "valueString TEXT, " +
                    "valueBytes BLOB, " +
                    "timestampCreated INTEGER NOT NULL, " +
                    "timestampModified INTEGER NOT NULL, " +
                    "ttl INTEGER NOT NULL, " +
                    "maxStale INTEGER NOT NULL, " +
                    "sourceUri TEXT, " +
                    "eTag TEXT, " +
                    "priority TEXT NOT NULL" +
                    ");";

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        synchronized (this._databaseAccessLock) {
            db.execSQL(_DatabaseCreateSQL);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        synchronized (this._databaseAccessLock) {
            if(this._logger != null) { this._logger.debug("Upgrading database from version %1$d to %2$d (dropping all data)", oldVersion, newVersion); }
            db.execSQL("DROP TABLE IF EXISTS " + _DatabaseTableName);
            db.execSQL(_DatabaseTableName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(String key, String value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {
        return (this.add(key, value, null, ttl, maxStale, eTag, sourceUri, priority));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(String key, byte[] value, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {
        return (this.add(key, null, value, ttl, maxStale, eTag, sourceUri, priority));
    }

    /**
     * Add or update a value in the cache.
     */
    private boolean add(String key, String valueString, byte[] valueBytes, long ttl, long maxStale, String eTag, URI sourceUri, CachePriority priority) {
        if ((key == null) || (key.length() <= 0)) {
            throw (new IllegalArgumentException("'key' can not be NULL or empty"));
        }

        // Set up the needed values
        ContentValues values = new ContentValues();
        values.put("key", key);
        if (valueString == null) {
            values.putNull("valueString");
        } else {
            values.put("valueString", valueString);
        }
        if (valueBytes == null) {
            values.putNull("valueBytes");
        } else {
            values.put("valueBytes", valueBytes);
        }
        values.put("ttl", ttl);
        values.put("maxStale", maxStale);
        if (eTag == null) {
            values.putNull("eTag");
        } else {
            values.put("eTag", eTag);
        }
        if (sourceUri == null) {
            values.putNull("sourceUri");
        } else {
            values.put("sourceUri", sourceUri.toString());
        }
        values.put("timestampModified", System.currentTimeMillis());
        values.put("priority", priority.name());

        try {

            synchronized (this._databaseAccessLock) {
                if (this.containsKeyInternal(key, true)) {

                    // Update an existing record
                    if(this._logger != null) { this._logger.debug("Updating cache entry '%1$s'", key); }
                    return (this.getWritableDatabase().update(_DatabaseTableName, values, "key = ?", new String[]{key}) > 0);
                } else {

                    // Insert a new record
                    if(this._logger != null) { this._logger.debug("Inserting cache entry '%1$s'", key); }
                    values.put("timestampCreated", System.currentTimeMillis());
                    return (this.getWritableDatabase().insert(_DatabaseTableName, null, values) != -1);
                }
            }

        } catch (SQLiteException e) {
            if(this._logger != null) { this._logger.error(e, "add() failed"); }
            return (false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheEntry get(String key, boolean allowExpired) {
        if ((key == null) || (key.length() <= 0)) {
            throw (new IllegalArgumentException("'key' can not be NULL or empty"));
        }
        CacheEntry result = null;
        synchronized (this._databaseAccessLock) {
            Cursor cursor = null;
            try {
                if (allowExpired) {
                    cursor = this.getReadableDatabase().query(_DatabaseTableName, null, "key = ?", new String[]{key}, null, null, null);
                } else {
                    cursor = this.getReadableDatabase().query(
                            _DatabaseTableName,
                            null,
                            "key = ?",
                            new String[]{key},
                            "id",
                            String.format(java.util.Locale.US, "(timestampModified + ttl) >= %1$d", System.currentTimeMillis()),
                            null);
                }
                if (cursor.moveToNext()) {
                    result = this.cacheEntryFromCursor(cursor);
                }
            } finally {
                try {
                    if (cursor != null) {
                        cursor.close();
                        cursor = null;
                    }
                } catch (Exception e) {
                } // No-op OK
            }
            return (result);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CacheEntry> getAll(boolean allowExpired) {
        synchronized (this._databaseAccessLock) {
            ArrayList<CacheEntry> cacheEntries = new ArrayList<CacheEntry>();
            Cursor cursor = null;
            try {
                if (allowExpired) {
                    cursor = this.getReadableDatabase().query(_DatabaseTableName, null, null, null, null, null, null);
                } else {
                    cursor = this.getReadableDatabase().query(
                            _DatabaseTableName,
                            null,
                            null,
                            null,
                            "id",
                            String.format(java.util.Locale.US, "(timestampModified + ttl) >= %1$d", System.currentTimeMillis()),
                            null);
                }
                if (cursor.moveToFirst()) {
                    do {
                        cacheEntries.add(this.cacheEntryFromCursor(cursor));
                    } while (cursor.moveToNext());
                }
            } finally {
                try {
                    if (cursor != null) {
                        cursor.close();
                        cursor = null;
                    }
                } catch (Exception e) {
                } // No-op OK
            }
            return (cacheEntries);
        }
    }

    /**
     * A convenience method that creates a {@link CacheEntry} instance from a database {@link Cursor}.
     * The {@link Cursor} must already be pointing to the relevant database record.
     */
    private CacheEntry cacheEntryFromCursor(Cursor cursor) {
        if (cursor == null) {
            throw (new IllegalArgumentException("'cursor' can not be NULL"));
        }
        if ((cursor.isBeforeFirst()) || (cursor.isAfterLast())) {
            throw (new IllegalArgumentException("'cursor' must already be pointing to a valid record"));
        }

        // Get the database values
        int id = cursor.getInt(0);                  // id
        String key = cursor.getString(1);           // key
        String valueString = null;
        if (!cursor.isNull(2)) {
            valueString = cursor.getString(2);      // valueString
        }
        byte[] valueBytes = null;
        if (!cursor.isNull(3)) {
            valueBytes = cursor.getBlob(3);         // valueBytes
        }
        long timestampCreated = cursor.getLong(4);  // timestampCreated
        long timestampModified = cursor.getLong(5); // timestampModified
        long ttl = cursor.getLong(6);               // ttl
        long maxStale = cursor.getLong(7);          // max-stale

        URI sourceUri = null;
        if (!cursor.isNull(8)) {
            String uriStr = cursor.getString(8);    // sourceUri
            if ((uriStr != null) || (uriStr.length() > 0)) {
                try {
                    sourceUri = new URI(uriStr);
                } catch (URISyntaxException e) {
                    throw (new CacheException(e));
                }
            }
        }
        String eTag = null;
        if (!cursor.isNull(9)) {
            eTag = cursor.getString(9);             // eTag
        }

        CachePriority priority = CachePriority.valueOf(cursor.getString(10));  // priority

        // Create and return the CacheEntry instance
        return (new CacheEntry(key, valueString, valueBytes, ttl, maxStale, eTag, sourceUri, timestampCreated, timestampModified, priority));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size(boolean allowExpired) {
        synchronized (this._databaseAccessLock) {
            return (this.sizeInternal(allowExpired));
        }
    }

    /**
     * Does <b>not</b> do locking and should only be used carefully from within this class.
     */
    private int sizeInternal(boolean allowExpired) {
        SQLiteStatement dbStatement = null;
        if (allowExpired) {
            dbStatement = this.getWritableDatabase().compileStatement(String.format(java.util.Locale.US, "SELECT count(*) FROM %1$s", _DatabaseTableName));
        } else {
            dbStatement = this.getWritableDatabase().compileStatement(String.format(
                    java.util.Locale.US,
                    "SELECT count(*) FROM %1$s GROUP BY id HAVING (timestampModified + ttl) >= %2$d",
                    _DatabaseTableName,
                    System.currentTimeMillis()));
        }
        try {
            return ((int) dbStatement.simpleQueryForLong());
        } catch (SQLiteDoneException e) {
            return (0);  // Indicates simpleQueryForLong() did not get a result row (i.e. no records match)
        } finally {
            try {
                dbStatement.close();
                dbStatement = null;
            } catch (Exception e) {
                if(this._logger != null) { this._logger.error(e, "SQLiteStatement.close() failed"); }  // No-op OK
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(String key, boolean allowExpired) {
        if ((key == null) || (key.length() <= 0)) {
            throw (new IllegalArgumentException("'key' can not be NULL or empty"));
        }
        synchronized (this._databaseAccessLock) {
            return (this.containsKeyInternal(key, allowExpired));
        }
    }

    /**
     * Does <b>not</b> do locking and should only be used carefully from within this class.
     */
    private boolean containsKeyInternal(String key, boolean allowExpired) {
        SQLiteStatement dbStatement = null;
        if (allowExpired) {
            dbStatement = this.getReadableDatabase().compileStatement(String.format(java.util.Locale.US, "SELECT count(*) FROM %1$s WHERE key = ?", _DatabaseTableName));
        } else {
            dbStatement = this.getReadableDatabase().compileStatement(String.format(
                    java.util.Locale.US,
                    "SELECT count(*) FROM %1$s WHERE key = ? GROUP BY id HAVING (timestampModified + ttl) >= %2$d",
                    _DatabaseTableName,
                    System.currentTimeMillis()));
        }
        try {
            dbStatement.bindString(1, key);
            return (dbStatement.simpleQueryForLong() > 0);
        } catch (SQLiteDoneException e) {
            return (false);  // Indicates simpleQueryForLong() did not get a result row (i.e. no records match) caused by the HAVING clause
        } finally {
            try {
                dbStatement.close();
                dbStatement = null;
            } catch (Exception e) {
                if(this._logger != null) { this._logger.error(e, "SQLiteStatement.close() failed"); }  // No-op OK
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(String key) {
        if ((key == null) || (key.length() <= 0)) {
            throw (new IllegalArgumentException("'key' can not be NULL or empty"));
        }
        synchronized (this._databaseAccessLock) {
            int deleteCount = this.getWritableDatabase().delete(_DatabaseTableName, "key = ?", new String[]{key});
            return (deleteCount > 0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeAll() {
        synchronized (this._databaseAccessLock) {
            this.getWritableDatabase().delete(_DatabaseTableName, null, null);
        }
        return (true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean trimLru() {
        int lruCap = this.getLruCap();
        if (lruCap < 0) {
            throw (new IllegalStateException("LRU cap can not be negative"));
        }
        synchronized (this._databaseAccessLock) {
            if (this.sizeInternal(true) > lruCap) {

                // Get the database row ID for where LRU records start
                Long id = null;
                Cursor results = null;
                try {
                    results = this.getReadableDatabase().query(_DatabaseTableName, _IDColumn, null, null, null, null, "timestampModified DESC");
                    if (results.moveToPosition(lruCap)) {
                        id = results.getLong(0);
                    } // ID is at index zero
                } finally {
                    try {
                        if (results != null) {
                            results.close();
                            results = null;
                        }
                    } catch (Exception e) {
                    } // No-op OK
                }

                // This should not be possible
                if (id == null) {
                    throw (new IllegalStateException("trimLru() failed to get row ID"));
                }

                // Delete the records that are under the LRU ID
                int count = this.getWritableDatabase().delete(_DatabaseTableName, String.format(java.util.Locale.US, "id <= %1$d", id), null);
                if(this._logger != null) { this._logger.debug("%1$d LRU entries deleted form cache", count); }
            }
        }
        return (true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLruCap(int maxCacheSize) {
        if (maxCacheSize < 0) {
            throw (new IllegalArgumentException("'maxCacheSize' can not be negative"));
        }
        this._lruCap = maxCacheSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLruCap() {
        return (this._lruCap);
    }

}