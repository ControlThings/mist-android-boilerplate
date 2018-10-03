package fi.ct.mist.boilerplate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.braintreepayments.cardform.OnCardFormValidListener;
import com.braintreepayments.cardform.view.CardForm;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.RawBsonDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import mist.api.request.Mist;
import mist.node.request.Control;
import wish.Peer;
import wish.request.Connection;
import wish.request.Identity;
import wish.request.Wish;

public class AuthActivity extends AppCompatActivity {

    private static String TAG = "chargingScenario";

    private wish.Identity userIdentity;

    private String regServerBase64 = "zwAAAAVkYXRhAK8AAAAArwAAAAJhbGlhcwAVAAAAUmVnaXN0cmF0aW9uIHNlcnZpY2UABXVpZAAgAAAAAJVYB25c14NOaWekjDMoE8Q2HI4GdCTvqYU7RU7K3c4qBXB1YmtleQAgAAAAAMN1xw6jgiH1vWEyvgrtDf93SvCqjjFT2LgTOJdMqcDLBHRyYW5zcG9ydHMAJwAAAAIwABsAAAB3aXNoOi8vMTkzLjY1LjU0LjEzMTo0MDAwMAAAAAVtZXRhAAUAAAAABQAAAAAA";
    private BsonDocument regServerBson;
    private wish.Identity regServerContact;
    private wish.Identity regServer;
    private wish.Peer regServerPeer;


    private String chargingBase64 = "zQAAAAVkYXRhAK0AAAAArQAAAAJhbGlhcwATAAAAT01JIHR1bm5lbCBzZXJ2aWNlAAV1aWQAIAAAAAAuk/qkCszRRFA/UKj419i77wak7LlQP2IDyrS9KII6TgVwdWJrZXkAIAAAAAAtrCeUIooqJOMExTmfueNqOhViCtFrn9X0nyKRgHcA3wR0cmFuc3BvcnRzACcAAAACMAAbAAAAd2lzaDovLzE5My42NS41NC4xMzE6NDAwMDAAAAAFbWV0YQAFAAAAAAUAAAAAAA==";
    private BsonDocument chargingBson;
    private wish.Identity chargingContact;
    private wish.Identity charging;
    private wish.Peer chargingPeer;
    private BsonDocument certificate;

    private int wishSignalsId;
    private int mistSignalsId;

    private CardForm cardForm;
    private Button cardButton;
    private Button chargingButton;
    private Button forgetRegServerButton;
    private Button forgetChargingButton;
    private Button omiButton;
    private Button deleteCertButton;

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
        chargingButton = findViewById(R.id.button_qr);
        forgetRegServerButton = findViewById(R.id.button_forget_reg_server);
        forgetChargingButton = findViewById(R.id.button_forget_charging);
        omiButton = findViewById(R.id.omi_button);

        deleteCertButton = findViewById(R.id.button_cert_delete);
        deleteCertButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteCert();
            }
        });

        regServerBson = getBsonContact(regServerBase64);
        regServerContact = getContact(regServerBson);

        chargingBson = getBsonContact(chargingBase64);
        chargingContact = getContact(chargingBson);
    }

    private BsonDocument getBsonContact(String base64Conntact) {
        byte[] data = Base64.decode(base64Conntact, Base64.DEFAULT);
        return new RawBsonDocument(data);
    }

    private wish.Identity getContact(BsonDocument bsonConntact) {
        return wish.Identity.fromBson(bsonConntact.getBinary("data").getData());
    }

    @Override
    protected void onResume() {
        super.onResume();
        forgetRegServerButton.setVisibility(View.GONE);
        forgetChargingButton.setVisibility(View.GONE);
        omiButton.setVisibility(View.GONE);
        identityList();
        wishSignals();
        mistSignals();
        certificate = readCert();
        if(certificate != null) {
            deleteCertButton.setVisibility(View.VISIBLE);
        }

        wish.request.Connection.checkConnections(new Connection.CheckConnectionsCb() {
            @Override
            public void cb(boolean b) {}
        });
    }

    private void identityList() {
        wish.request.Identity.list(new Identity.ListCb() {
            @Override
            public void cb(List<wish.Identity> list) {
                for (wish.Identity identity : list) {
                    if (Arrays.equals(regServerContact.getUid(), identity.getUid())) {
                        regServer = identity;
                    }
                    if (Arrays.equals(chargingContact.getUid(), identity.getUid())) {
                        charging = identity;
                    }
                }
                if (regServer == null) {
                    //wishSignals();
                    friendRequest();
                }
            }
        });
    }

    private void wishSignals() {
        wishSignalsId = wish.request.Wish.signals(new Wish.SignalsCb() {

            @Override
            public void cb(String s) {
              //  Log.d(TAG, "wishSignals "+ s);
                if (s.equals("friendRequesteeAccepted") ) {
                    wish.request.Connection.checkConnections(new Connection.CheckConnectionsCb() {
                        @Override
                        public void cb(boolean b) {}
                    });
                    identityList();
                }
            }
        });
    }

    private void mistSignals() {
        if (mistSignalsId != 0) {
            mist.api.request.Mist.cancel(mistSignalsId);
        }
        mistSignalsId = mist.api.request.Mist.signals(new Mist.SignalsCb() {
            @Override
            public void cb(String s, BsonDocument bsonDocument) {
              // Log.d(TAG, "mist Signals: " + s);
               if(s.equals("peers")) {
                   listPeers();
               }
            }
        });
    }

    private void friendRequest() {
       wish.request.Identity.friendRequest(userIdentity.getUid(), regServerBson, new Identity.FriendRequestCb() {
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
                    if (regServer != null && Arrays.equals(peer.getRuid(), regServer.getUid()) || charging != null && Arrays.equals(peer.getRuid(), charging.getUid())){

                              mist.node.request.Control.read(peer, "mist.class", (new Control.ReadCb() {
                                  Peer peer;

                                  @Override
                                  public void cbString(String data) {
                                      super.cbString(data);
                                      if(data.equals("eu.biotope-project.reg-service") && regServerPeer == null) {
                                          regServerPeer = peer;
                                          ForgetRegServerButton();
                                          if (certificate == null) {
                                              creditCard();
                                          } else {
                                              ChargingButton();
                                          }
                                          if (mistSignalsId != 0) {
                                              mist.api.request.Mist.cancel(mistSignalsId);
                                          }
                                      }else if(data.equals("eu.biotope-project.charging-service") && chargingPeer == null) {
                                          chargingPeer = peer;
                                          ForgetChargingButton();
                                          chargingButton.setVisibility(View.INVISIBLE);
                                          if (mistSignalsId != 0) {
                                              mist.api.request.Mist.cancel(mistSignalsId);
                                          }
                                          OmiButton();
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
                if (valid) {
                    cardButton.setVisibility(View.VISIBLE);
                    hideKeyboard();
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

                mist.node.request.Control.invoke(regServerPeer, "getCertificate", bsonDocument ,new Control.InvokeCb() {
                    @Override
                    public void cbDocument(BsonDocument value) {
                        super.cbDocument(value);
                        certificate = value;
                        saveCert(certificate);
                        cardForm.setVisibility(View.INVISIBLE);
                        cardButton.setVisibility(View.INVISIBLE);
                        ChargingButton();
                    }
                });
            }
        });
    }

    private void ChargingButton() {
        chargingButton.setVisibility(View.VISIBLE);
        chargingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mistSignals();
                wish.request.Identity.friendRequest(userIdentity.getUid(), chargingBson, certificate, new Identity.FriendRequestCb() {
                    @Override
                    public void cb(boolean b) {}
                });
            }
        });
    }

    private void OmiButton() {
        omiButton.setVisibility(View.VISIBLE);
        final String omiMsg = "<InfoItem name='ActivateCharging'></InfoItem>";
        omiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mist.node.request.Control.invoke(chargingPeer, "omi", omiMsg, new Control.InvokeCb() {
                    @Override
                    public void cbByte(byte[] value) {
                        super.cbByte(value);
                    }

                    @Override
                    public void cbBool(boolean value) {
                        super.cbBool(value);
                        if (value) {
                            Toast.makeText(AuthActivity.this, "OMI request successfully sent!",
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void err(int code, String msg) {
                        super.err(code, msg);
                            Toast.makeText(AuthActivity.this, "OMI request error! " + msg ,
                                    Toast.LENGTH_LONG).show();
                            Log.d(TAG, "OMI request error! " + msg);
                    }
                });
            }
        });
    }

    private void ForgetRegServerButton() {
        forgetRegServerButton.setVisibility(View.VISIBLE);
        forgetRegServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mist.node.request.Control.invoke(regServerPeer, "forgetUser", new Control.InvokeCb() {
                    @Override
                    public void cbBool(boolean value) {
                        super.cbBool(value);
                        if (value) {
                            wish.request.Identity.remove(regServer.getUid(), new Identity.RemoveCb() {
                                @Override
                                public void cb(boolean b) {
                                    regServer = null;
                                    regServerPeer = null;
                                    forgetRegServerButton.setVisibility(View.INVISIBLE);
                                    cardForm.setVisibility(View.INVISIBLE);
                                    cardButton.setVisibility(View.INVISIBLE);
                                    chargingButton.setVisibility(View.INVISIBLE);
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void ForgetChargingButton() {
        forgetChargingButton.setVisibility(View.VISIBLE);
        forgetChargingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mist.node.request.Control.invoke(chargingPeer, "forgetUser", new Control.InvokeCb() {
                    @Override
                    public void cbBool(boolean value) {
                        super.cbBool(value);
                        if (value) {
                            wish.request.Identity.remove(charging.getUid(), new Identity.RemoveCb() {
                                @Override
                                public void cb(boolean b) {
                                    charging = null;
                                    chargingPeer = null;
                                    forgetChargingButton.setVisibility(View.INVISIBLE);
                                    chargingButton.setVisibility(View.INVISIBLE);
                                    omiButton.setVisibility(View.INVISIBLE);
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

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void saveCert(BsonDocument cert) {
        try {

            FileOutputStream file = openFileOutput("cert", Context.MODE_PRIVATE);
            ObjectOutputStream object = new ObjectOutputStream(file);

            object.writeObject(cert);
            object.close();
            file.close();

            deleteCertButton.setVisibility(View.VISIBLE);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found");
        } catch (IOException e) {
            Log.d(TAG, "Error initializing stream");
        }
    }

    private BsonDocument readCert() {

        try {

            FileInputStream file = openFileInput("cert");
            ObjectInputStream object = new ObjectInputStream(file);
            BsonDocument cert = (BsonDocument) object.readObject();
            object.close();
            file.close();

            return cert;

        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found");
        } catch (IOException e) {
            Log.d(TAG, "Error initializing stream");
        }  catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void deleteCert() {
        String dir = getFilesDir().getAbsolutePath();
        File file = new File(dir,"cert");
        file.delete();
        certificate = null;
        deleteCertButton.setVisibility(View.INVISIBLE);
    }
}
