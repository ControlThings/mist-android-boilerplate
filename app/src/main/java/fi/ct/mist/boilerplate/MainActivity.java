package fi.ct.mist.boilerplate;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import addon.AddonReceiver;
import mist.api.Service;
import mist.api.request.Mist;
import wish.request.Wish;


public class MainActivity extends AppCompatActivity implements AddonReceiver.Receiver{

    public static final String TAG = "MainActivity";

    Intent mistService;
    private int signalsId;

    /**
     * This method is called when the Activity is created. For Mist applications, we set up the
     * system for tracking application state (in foreground, in background). After that we create
     * the Mist service, instructing that this object's onConnected and onDisconnected methods
     * should be called when the Mist service starts and stops.
     * We don't start the service yet, that will be done only when app lifecycle is determined to be "in the foreground".
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //create a Lifecycle listener for the app so we can stop mist when the app goes in background
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppLifecycleListener());

        //create the mist service
        mistService = new Intent(this, Service.class);
        AddonReceiver mistReceiver = new AddonReceiver(this);
        mistService.putExtra("receiver", mistReceiver);
    }

    /**
     * This method is called when MistApi is ready to be used.
     */
    @Override
    public void onConnected() {

        //Create a wish signal and listen for "ok" signal to confirm that wish is running.
        signalsId = wish.request.Wish.signals(new Wish.SignalsCb() {
            @Override
            public void cb(String s) {
                if (s.equals("ok")) {

                    //Send request for wish version number
                    wish.request.Wish.version(new Wish.VersionCb() {
                        @Override
                        public void cb(String s) {
                            Log.d(TAG, "wish version: " + s);
                            TextView textView = (TextView) findViewById(R.id.WishVersion);
                            textView.setText("Wish version: " + s);
                        }
                    });

                    //Send request for mist version number
                    mist.api.request.Mist.version(new Mist.VersionCb() {
                        @Override
                        public void cb(String s) {
                            Log.d(TAG, "Mist version: " + s);
                            TextView textView = (TextView) findViewById(R.id.MistVersion);
                            textView.setText("Mist version: " + s);
                        }
                    });
                }
            }
        });
    }

    /**
     * This method is called when MistApi is no longer available. In this function you can for
     * example reset the application state, but you cannot make any MistApi commands anymore.
     */
    @Override
    public void onDisconnected() {
        //mist is disconnected
    }

    class AppLifecycleListener implements LifecycleObserver {

        /**
         * This function is called when the app moves into the foreground.
         * You should start the Mist service here, but you should wait for onConnected() to be called, before making any MistApi requests.
         */
        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        public void onMoveToForeground() {
            // app moved to foreground

            //Start the mist Service
            startService(mistService);
        }


        /**
         * This function is called when the app no longer is in the foreground.
         * This is the correct place to unsubscribe from any MistApi events (signals, control.follow...) that you might have registered.
         * After you have stopped Mist service, you cannot send any MistApi commands anymore.
         */
        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        public void onMoveToBackground() {
            // app moved to background

            /* Cancel the signals request */
            if (signalsId != 0) {
                wish.request.Wish.cancel(signalsId);
            }

            //Stop the mist Service
            stopService(mistService);
        }
    }

}
