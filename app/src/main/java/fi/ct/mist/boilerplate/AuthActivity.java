package fi.ct.mist.boilerplate;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.bson.BsonDocument;
import org.bson.RawBsonDocument;

import java.util.Arrays;
import java.util.List;

import mist.api.request.Mist;
import mist.node.MistNode;
import mist.node.request.Control;
import wish.Peer;
import wish.request.Identity;
import wish.request.Wish;

public class AuthActivity extends AppCompatActivity {

    private static String TAG = "chargingScenario";

    private wish.Identity userIdentity;
    private String base64Conntact = "zwAAAAVkYXRhAK8AAAAArwAAAAJhbGlhcwAVAAAAUmVnaXN0cmF0aW9uIHNlcnZpY2UABXVpZAAgAAAAAKsbj3tqfPFiDD6piG0SR9O7HDwKR9iydzvC+pufcU56BXB1YmtleQAgAAAAANPEe0YkGFbhdT5fXq6msTEnQkZGBaXBwNGrWiN9tWOuBHRyYW5zcG9ydHMAJwAAAAIwABsAAAB3aXNoOi8vMTkzLjY1LjU0LjEzMTo0MDAwMAAAAAVtZXRhAAUAAAAABQAAAAAA";
    private BsonDocument bsonConntact;
    private wish.Identity regServerConntact;
    private wish.Identity regServer;
    private wish.Peer regServerPeer;

    private int signalsId;

    private Button removeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        Intent intent = getIntent();
        userIdentity = (wish.Identity) intent.getSerializableExtra(MainActivity.USER_IDENTITY);

        TextView textView = findViewById(R.id.textView);
        textView.setText(userIdentity.getAlias());

        removeButton =findViewById(R.id.button_remove);
        parseConntact();
    }

    private void parseConntact() {
        byte[] data = Base64.decode(base64Conntact, Base64.DEFAULT);
        bsonConntact = new RawBsonDocument(data);
        regServerConntact = wish.Identity.fromBson(bsonConntact.getBinary("data").getData());
    }

    @Override
    protected void onResume() {
        super.onResume();
        removeButton.setVisibility(View.GONE);
        identityList();
        signals();
    }

    private void identityList() {
        wish.request.Identity.list(new Identity.ListCb() {
            @Override
            public void cb(List<wish.Identity> list) {
                for (wish.Identity identity : list) {
                    if (Arrays.equals(regServerConntact.getUid(), identity.getUid())) {
                        regServer = identity;
                    }
                }
                if (regServer == null) {
                    //signals();
                    friendRequest();
                } else {
                    Log.d(TAG, regServer.getAlias() + " is added to list");


                }
            }
        });
    }

    private void signals() {
        signalsId = wish.request.Wish.signals(new Wish.SignalsCb() {
            @Override
            public void cb(String s) {
                Log.d(TAG, "signals "+ s);
                if (s.equals("friendRequesteeAccepted")) {
                    identityList();
                }
                if (s.equals("connections") && regServerPeer == null) {
                    listPeers();
                }
            }
        });
    }

    private void friendRequest() {
       wish.request.Identity.friendRequest(userIdentity.getUid(), bsonConntact, new Identity.FriendRequestCb() {
           @Override
           public void cb(boolean b) {}

           @Override
           public void err(int code, String msg) {
               super.err(code, msg);
               Log.d(TAG, "friendRequest err: " + msg);
           }
       });
    }


    private void listPeers() {
        mist.api.request.Mist.listServices(new Mist.ListServicesCb() {
            @Override
            public void cb(List<Peer> list) {
                for (Peer peer : list) {
                    if (Arrays.equals(peer.getRuid(), regServer.getUid())){
                             Log.d(TAG, "model..");

                              mist.node.request.Control.read(peer, "mist.name", (new Control.ReadCb() {
                                  Peer peer;

                                  @Override
                                  public void cbString(String data) {
                                      super.cbString(data);
                                      Log.d(TAG, "name " + data);
                                      if(data.equals("Registration service MistApi")) {
                                               regServerPeer = peer;
                                                    removeButton();
                                      }
                                  }

                                  @Override
                                  public void err(int code, String msg) {
                                      super.err(code, msg);
                                      Log.d(TAG,  "read name err");
                                  }

                                  private Control.ReadCb init(Peer peer) {
                                      this.peer = peer;
                                      return this;
                                  }
                              }).init(peer));
                    }

                }
            }
        });
    }

    private void removeButton() {
        removeButton.setVisibility(View.VISIBLE);
        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mist.node.request.Control.invoke(regServerPeer, "RemoveUser", new Control.InvokeCb() {
                    @Override
                    public void cbBool(boolean value) {
                        super.cbBool(value);
                        Log.d(TAG, "removed from reg server");
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (signalsId != 0) {
            wish.request.Wish.cancel(signalsId);
        }
    }
}
