package fi.ct.mist.boilerplate;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

import addon.AddonReceiver;
import mist.api.Service;
import wish.request.Identity;
import wish.request.Wish;


public class MainActivity extends AppCompatActivity implements AddonReceiver.Receiver{

    public static final String TAG = "MainActivity";
    public static final String USER_IDENTITY = "fi.ct.mist.boilerplate.IDENTITY";

    Intent mistService;
    private int signalsId;
    private boolean mistRunning;

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
        mistRunning = true;

        //Create a wish signal and listen for "ok" signal to confirm that wish is running.
        signalsId = wish.request.Wish.signals(new Wish.SignalsCb() {
            @Override
            public void cb(String s) {
                if (s.equals("ok")) {
                    identityList();
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

        mistRunning = false;
    }

    private void identityList() {
        wish.request.Identity.list(new Identity.ListCb() {
            @Override
            public void cb(List<wish.Identity> list) {
                for (wish.Identity identity : list) {
                    if (identity.isPrivkey()) {
                        openAuthActivity(identity);
                        break;
                    }
                }
            }
        });
    }

    private void identityCreate(String identityString) {
        wish.request.Identity.create(identityString, new Identity.CreateCb() {
            @Override
            public void cb(wish.Identity identity) {
                openAuthActivity(identity);
            }
        });
    }

    public void saveIdentity(View view) {
        EditText editText = findViewById(R.id.editText);
        String identity = editText.getText().toString();

        if (identity.matches("")) {
            Toast.makeText(this, "You did not enter a identity", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!mistRunning) {
            Toast.makeText(this, "The Mist service is not running", Toast.LENGTH_SHORT).show();
            return;
        }
        identityCreate(identity);
    }

    private void openAuthActivity(wish.Identity identity) {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.putExtra(USER_IDENTITY, identity);
        startActivity(intent);
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
