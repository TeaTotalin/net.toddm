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
package net.toddm.comm.android;

import android.app.Application;
import android.test.ApplicationTestCase;

import junit.framework.Assert;

import net.toddm.comm.android.cache.DBCacheProvider;

/**
 * Implements unit tests specific to the <B>Android</B> Communications Framework
 * extension of the <i>net.toddm.comm</i> Communications Framework.
 * <p>
 * @author Todd S. Murchison
 */
public class AndroidCommFrameworkTest extends ApplicationTestCase<Application> {
  public AndroidCommFrameworkTest() { super(Application.class); }

  public void testDBCacheProvider() throws Exception {

    // Make use of the caching framework testing validation method to help validate our database cache provider implementation
    DBCacheProvider cache = DBCacheProvider.getInstance(this.getContext(), "test_namespace", 1);
    net.toddm.cache.tests.MainTest.validateCachingFunctionality(cache);
  }

  public void testDBCacheProviderAdditional() throws Exception {

    // Do any additional testing of the DBCacheProvider not covered by the standard validateCachingFunctionality() method (see above)
    DBCacheProvider cache = DBCacheProvider.getInstance(this.getContext(), "test_namespace", 1);
    cache.removeAll();
    Assert.assertEquals(0, cache.size(true));
    Assert.assertEquals(0, cache.size(false));
    cache.add("test_key", "test_value", 100, null, null);
    Assert.assertEquals(1, cache.size(true));
    Assert.assertEquals(1, cache.size(false));
    Thread.sleep(101);
    Assert.assertEquals(1, cache.size(true));
    Assert.assertEquals(0, cache.size(false));
  }

}