package fi.ct.mist.boilerplate;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.braintreepayments.cardform.OnCardFormSubmitListener;
import com.braintreepayments.cardform.OnCardFormValidListener;
import com.braintreepayments.cardform.view.CardForm;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.RawBsonDocument;

import java.util.Arrays;
import java.util.List;

import mist.api.request.Mist;
import mist.node.request.Control;
import wish.Peer;
import wish.request.Identity;
import wish.request.Wish;

public class AuthActivity extends AppCompatActivity {

    private static String TAG = "chargingScenario";

    private wish.Identity userIdentity;
    private String base64Conntact = "yQAAAAVkYXRhAKkAAAAAqQAAAAJhbGlhcwAPAAAASmVwcGUgKGxlbm92bykABXVpZAAgAAAAAJvhFd0KJE2fwMkWKSbD4j7ZZ+Q1OQSSBXS5r9F0RnW+BXB1YmtleQAgAAAAAMQb+Jb5EjnIXUZ+Ci4F5NhiF6nQugYABeUukCUMnkqABHRyYW5zcG9ydHMAJwAAAAIwABsAAAB3aXNoOi8vMTkzLjY1LjU0LjEzMTo0MDAwMAAAAAVtZXRhAAUAAAAABQAAAAAA";
    private BsonDocument bsonConntact;
    private wish.Identity regServerConntact;
    private wish.Identity regServer;
    private wish.Peer regServerPeer;

    private int wishSignalsId;
    private int mistSignalsId;

    private CardForm cardForm;
    private Button cardButton;
    private Button removeButton;

    private String paymentApiVersion = "0.1.0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        Intent intent = getIntent();
        userIdentity = (wish.Identity) intent.getSerializableExtra(MainActivity.USER_IDENTITY);

        TextView textView = findViewById(R.id.textView);
        textView.setText(userIdentity.getAlias());

        cardForm = findViewById(R.id.card_form);
        cardButton = findViewById(R.id.button_credit_card);
        removeButton = findViewById(R.id.button_remove);
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
        wishSignals();
        mistSignals();
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
                    //wishSignals();
                    friendRequest();
                } else {
                    Log.d(TAG, regServer.getAlias() + " is added to list");


                }
            }
        });
    }

    private void wishSignals() {
        wishSignalsId = wish.request.Wish.signals(new Wish.SignalsCb() {

            @Override
            public void cb(String s) {
              //  Log.d(TAG, "wishSignals "+ s);
                if (s.equals("friendRequesteeAccepted")) {
                    identityList();
                }
            }
        });
    }

    private void mistSignals() {
        mistSignalsId = mist.api.request.Mist.signals(new Mist.SignalsCb() {
            @Override
            public void cb(String s, BsonDocument bsonDocument) {
              // Log.d(TAG, "mist Signals: " + s);
               if(s.equals("peers") && regServerPeer == null && regServer != null) {
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

                    Log.d(TAG, "list peers.. "+ peer.toString());

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
                                          creditCard();
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

    private void creditCard() {
        cardForm.cardRequired(true)
                .expirationRequired(true)
                .cvvRequired(true)
                .setup(this);
        cardForm.setOnCardFormValidListener(new OnCardFormValidListener() {
            @Override
            public void onCardFormValid(boolean valid) {
                Log.d(TAG, "formValid " + valid);
                if (valid) {
                    cardButton.setVisibility(View.VISIBLE);
                } else {
                    cardButton.setVisibility(View.INVISIBLE);
                }
            }
        });
        cardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                BsonDocument payment = new BsonDocument();
                payment.append("type", new BsonString("creditCard"));
                payment.append("ccNumber", new BsonString(cardForm.getCardNumber()));
                payment.append("ccExpiry", new BsonString(cardForm.getExpirationMonth() + '/' + cardForm.getExpirationYear().substring(2,4)));
                payment.append("ccCvc", new BsonString(cardForm.getCvv()));

                BsonDocument bsonDocument = new BsonDocument();
                bsonDocument.append("version", new BsonString(paymentApiVersion));
                bsonDocument.append("payment", payment);

                Log.d(TAG, "cert " + bsonDocument.toJson());

                mist.node.request.Control.invoke(regServerPeer, "getCertificate", bsonDocument ,new Control.InvokeCb() {
                    @Override
                    public void cbDocument(BsonDocument value) {
                        super.cbDocument(value);
                        Log.d(TAG, "cert cb " + value.toJson());
                    }
                });

            }
        });
    }

    private void removeButton() {
        removeButton.setVisibility(View.VISIBLE);
        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mist.node.request.Control.invoke(regServerPeer, "removeUser", new Control.InvokeCb() {
                    @Override
                    public void cbBool(boolean value) {
                        super.cbBool(value);
                        if (value) {
                            wish.request.Identity.remove(regServer.getUid(), new Identity.RemoveCb() {
                                @Override
                                public void cb(boolean b) {
                                    regServer = null;
                                    regServerPeer = null;
                                    removeButton.setVisibility(View.INVISIBLE);
                                    cardForm.setVisibility(View.INVISIBLE);
                                    cardButton.setVisibility(View.INVISIBLE);
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wishSignalsId != 0) {
            wish.request.Wish.cancel(wishSignalsId);
        }
        if (mistSignalsId != 0) {
            mist.api.request.Mist.cancel(mistSignalsId);
        }
    }
}
