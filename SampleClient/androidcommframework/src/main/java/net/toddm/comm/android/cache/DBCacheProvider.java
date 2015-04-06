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
package net.toddm.comm.android.cache;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CacheProvider;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link CacheProvider} interface that is backed by the SQLite database.
 * This caching implementation is thread safe.
 * <p>
 * @author Todd S. Murchison
 */
public class DBCacheProvider extends SQLiteOpenHelper implements CacheProvider {

  private static final Logger _Logger = LoggerFactory.getLogger(DBCacheProvider.class.getSimpleName());

  //-------------------------------------------------------------------------------
  // Enforce one instance per namespace/database
  private DBCacheProvider(Context context, String databaseName, int databaseVersion) {
    super(context, databaseName, null, databaseVersion);
    this._databaseName = databaseName;
    this._databaseVersion = databaseVersion;
    _Logger.trace("Using caching database '%1$s'", databaseName);
  }

  public static DBCacheProvider getInstance(Context context, String namespace, int databaseVersion) {
    if(context == null) { throw(new IllegalArgumentException("'context' can not be NULL")); }
    if((namespace == null) || (namespace.length() <= 0)) { throw(new IllegalArgumentException("'namespace' can not be NULL or empty")); }
    if(!_NamespaceToCache.containsKey(namespace)) {
      synchronized(_NamespaceToCacheLock) {
        if(!_NamespaceToCache.containsKey(namespace)) {
          _NamespaceToCache.put(namespace, new DBCacheProvider(context, namespace, databaseVersion));
        }
      }
    }
    return(_NamespaceToCache.get(namespace));
  }

  private static volatile Object _NamespaceToCacheLock = new Object();
  private static Map<String, DBCacheProvider> _NamespaceToCache = new HashMap<String, DBCacheProvider>();

  private final String _databaseName;
  private final int _databaseVersion;

  private volatile Object _databaseAccessLock = new Object();
  private static final String _DatabaseTableName = "cache";
  private static final String _DatabaseCreateSQL =
      "CREATE TABLE IF NOT EXISTS " + _DatabaseTableName + " (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "name TEXT NOT NULL UNIQUE, " +
        "valueString TEXT, " +
        "valueBytes BLOB, " +
        "timestampCreated INTEGER NOT NULL, " +
        "timestampModified INTEGER NOT NULL, " +
        "ttl INTEGER NOT NULL, " +
        "uri TEXT, " +
        "etag TEXT" +
      ");";

  /** {@inheritDoc} */
  @Override
  public void onCreate(SQLiteDatabase db) {
    synchronized(this._databaseAccessLock) {
      db.execSQL(_DatabaseCreateSQL);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    synchronized(this._databaseAccessLock) {
      _Logger.info("Upgrading database from version %1$d to %2$d (dropping all data)", oldVersion, newVersion);
      db.execSQL("DROP TABLE IF EXISTS " + _DatabaseTableName);
      db.execSQL(_DatabaseTableName);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void add(String key, String value, long ttl, String eTag, URI sourceUri) {

  }

  /** {@inheritDoc} */
  @Override
  public void add(String key, byte[] value, long ttl, String eTag, URI sourceUri) {

  }

  /** {@inheritDoc} */
  @Override
  public CacheEntry get(String key, boolean allowExpired) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public List<CacheEntry> getAll(boolean allowExpired) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void remove(String key) {

  }

  /** {@inheritDoc} */
  @Override
  public void removeAll() {

  }

  /** {@inheritDoc} */
  @Override
  public void trimLru(int maxEntries) {

  }

}
