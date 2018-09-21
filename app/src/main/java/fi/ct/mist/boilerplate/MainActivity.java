package fi.ct.mist.boilerplate;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;


import addon.AddonReceiver;
import mist.api.Service;
import wish.request.Identity;


public class MainActivity extends AppCompatActivity implements AddonReceiver.Receiver{

    public static final String TAG = "MainActivity";
    public static final String USER_IDENTITY = "fi.ct.mist.boilerplate.IDENTITY";

    Intent mistService;
    private boolean mistRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        synchronized (this) {
            if (!mistRunning) {
                mistService = new Intent(this, Service.class);
                AddonReceiver mistReceiver = new AddonReceiver(this);
                mistService.putExtra("receiver", mistReceiver);
                startService(mistService);
            }
        }

    }

    @Override
    public void onConnected() {
        synchronized (this) {
            mistRunning = true;
        }
        identityList();
    }


    @Override
    public void onDisconnected() {
        synchronized (this) {
            mistRunning = false;
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (this) {
            if (mistRunning) {
                this.stopService(mistService);
            }
        }
    }
}
