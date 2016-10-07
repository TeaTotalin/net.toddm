package net.toddm.comm.android;

import android.app.Application;
import android.test.ApplicationTestCase;
import android.text.TextUtils;

import junit.framework.Assert;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CachePriority;
import net.toddm.cache.DefaultLogger;
import net.toddm.cache.android.DBCacheProvider;
import net.toddm.comm.CacheBehavior;
import net.toddm.comm.CommManager;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.Work;

import java.net.URI;
import java.util.ArrayList;

public class TestCommManager extends ApplicationTestCase<Application> {
    public TestCommManager() { super(Application.class); }

    /**
     * Tests that relevant timestamps and algorithms that use those timestamps are correct and produce the expected
     * TTL and "stale use" behavior.
     */
    public void testTTLAndStaleUse() throws Exception {

        // Build our test cache instance
        DBCacheProvider cache = DBCacheProvider.getInstance(this.getContext(), "testTTLAndStaleUseCache", 1, 20, new DefaultLogger());

        // Build our test Comm Framework instance
        CommManager.Builder commManagerBuilder = new CommManager.Builder();
        CommManager commManager = commManagerBuilder
                .setName("testTTLAndStaleUse")
                .setCacheProvider(cache)
                .setLoggingProvider(new DefaultLogger())
                .create();

        // Configure a request that will respond with a "Cache-Control" header specifying a "max age" of 3 seconds and a "max stale" of 3 seconds
        URI testUri = new URI("http://httpbin.org/response-headers?Cache-Control=private,%20max-age=3,%20max-stale=3");

        // Make the test request once every second for 7 seconds
        ArrayList<Work> works = new ArrayList<>();
        for(int i = 0; i < 7; i++) {
            works.add(commManager.enqueueWork(testUri, RequestMethod.GET, null, null, true, StartingPriority.MEDIUM, CachePriority.NORMAL, CacheBehavior.SERVER_DIRECTED_CACHE));
            Thread.sleep(1000);
        }

        // Wait for all 7 test requests to finish and then pull the current cache entry for the test request
        String cacheKey = null;
        for(Work work : works) {
            if(TextUtils.isEmpty(cacheKey)) {
                cacheKey = Integer.toString(work.getId());
            }
            work.get();
        }
        CacheEntry entry = cache.get(cacheKey, true);

        // At this point the result cache entry will have stopped being usable after 6 seconds, the 7th request
        // should have caused it to update the cache entry's TTL and "max stale" values, and the cache entry should
        // now claim that it has not expired or exceeded stale use.
        Assert.assertNotNull(entry);
        Assert.assertFalse(entry.hasExpired());
        Assert.assertFalse(entry.hasExceededStaleUse());
    }

}
