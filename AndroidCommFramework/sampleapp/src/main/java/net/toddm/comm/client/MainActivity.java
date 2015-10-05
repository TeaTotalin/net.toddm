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
package net.toddm.comm.client;

import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CachePriority;
import net.toddm.cache.DefaultLogger;
import net.toddm.cache.LoggingProvider;
import net.toddm.comm.CacheBehavior;
import net.toddm.comm.CommManager;
import net.toddm.comm.Priority;
import net.toddm.comm.Request;
import net.toddm.comm.Response;
import net.toddm.comm.Work;
import net.toddm.cache.android.DBCacheProvider;

import java.net.URI;
import java.util.Locale;

/**
 * The main entry point Activity of a very simple sample app that uses the Android Communications Framework (net.toddm.comm.android).
 * <p>
 * @author Todd S. Murchison
 */
public class MainActivity extends ActionBarActivity {

  public static String LogTag = "SampleClient";
  private static TextView _UILog = null;

  private CommManager _commManager = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    try {
      _UILog = (TextView) this.findViewById(R.id.logText);
      log("Starting tests...");

      // Make a comm manager instance
      LoggingProvider logger = new DefaultLogger();
      CommManager.Builder commManagerBuilder = new CommManager.Builder();
      this._commManager = commManagerBuilder
          .setName("SampleAppMainCommManager")
          .setCacheProvider(DBCacheProvider.getInstance(this, "comm_results_cache", 1, 100, logger))
          .setLoggingProvider(logger)
          .create();

      // Wire up the test button
      ((Button)this.findViewById(R.id.buttonTestRequest)).setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {

            // Don't do blocking work on the UI thread
            (new Thread(new Runnable() {
              @Override
              public void run() {

                // This is a blocking method
                MainActivity.this.makeTestRequest();
              }
            }, "TestRequestThread")).start();
          }
        }
      );

    } catch(Exception e) {
      log(String.format(Locale.US, "Error: check LogCat [%1$s]", e.getMessage()));
      e.printStackTrace();
    }
  }

  /**
   * This method is <b>blocking</b>.
   * Makes a test request and logs some info about the results.
   */
  private void makeTestRequest() {
    try {

      // Submit the test request to the comm manager and wait for the response
      Work work = this._commManager.enqueueWork(new URI("http://www.toddm.net/"), Request.RequestMethod.GET, null, null, Priority.StartingPriority.MEDIUM, CachePriority.LOW, CacheBehavior.NORMAL);
      Response response = work.get();
      if(response.getResponseCode() != 200) {
        log(String.format(Locale.US, "Test request failed [response code: %1$d]", response.getResponseCode()));
      } else {

        // Log some info about the results
        String results = response.getResponseBody();
        log(String.format(Locale.US, "Received %1$d character response", results.length()));
        StringBuilder headersLogText = new StringBuilder("Response headers:");
        headersLogText.append(System.getProperty("line.separator"));
        for(String key : response.getHeaders().keySet()) {
          headersLogText.append("   ");
          headersLogText.append(key);
          headersLogText.append(" = ");
          for(String value : response.getHeaders().get(key)) {
            headersLogText.append(value);
            headersLogText.append(" ");
          }
          headersLogText.append(System.getProperty("line.separator"));
        }
        log(headersLogText.toString());
      }

    } catch(Exception e) {
      log(String.format(Locale.US, "Error: check LogCat [%1$s]", e.getMessage()));
      e.printStackTrace();
    }
  }

  /** Logs the given message to LogCat at the DEBUG level and to the app UI if the text view is available. Can be called from any thread.  */
  public static void log(String msg) {
    try {
      Log.d(LogTag, msg);
      if(_UILog != null) {
        final String uiLogText = String.format(Locale.US, "%1$s%2$s%3$s", _UILog.getText(), System.getProperty("line.separator"), msg);
        if(!Looper.getMainLooper().equals(Looper.myLooper())) {
          _UILog.post(new Runnable() {
            @Override
            public void run() { try { _UILog.setText(uiLogText); } catch (Exception e) { e.printStackTrace(); } }
          });
        } else {
          _UILog.setText(uiLogText);
        }
      }
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

}
