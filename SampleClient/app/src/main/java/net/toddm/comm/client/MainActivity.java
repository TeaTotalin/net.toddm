package net.toddm.comm.client;

import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.Locale;


public class MainActivity extends ActionBarActivity {

  public static String LogTag = "SampleClient";
  private static TextView _UILog = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    _UILog = (TextView)this.findViewById(R.id.logText);
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  /** Logs the given message to LogCat at the DEBUG level and to the app UI if the text view is available. Can be called from any thread.  */
  public static void log(String msg) {
    try {
      Log.d(LogTag, msg);
      if (_UILog != null) {
        final String uiLogText = String.format(Locale.US, "%1$s%2$s%3$s", _UILog.getText(), System.getProperty("line.separator"), msg);
        if (Looper.getMainLooper().equals(Looper.myLooper())) {
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
