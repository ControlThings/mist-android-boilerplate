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

        /* Step 4: Get user's identity from Extras. Then we set up the UI.  */
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

        /* Step 5: Get the contact information for the registration server */
        regServerBson = getBsonContact(regServerBase64);
        regServerContact = getContact(regServerBson);

        /* Step 5b: Also get the charging server's contact info, this is just because in this app we don't actually get the information from a QR code or similar.
         *  */
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
    protected void onResume() {     /* Step 6: onResume is automatically called by the Android framework. We subscribe to wish and mist signals */
        super.onResume();
        forgetRegServerButton.setVisibility(View.GONE);
        forgetChargingButton.setVisibility(View.GONE);
        omiButton.setVisibility(View.GONE);

        certificate = readCert();
        if(certificate != null) {
            deleteCertButton.setVisibility(View.VISIBLE);
        }

        /* Step 6b: In order to speed up connections, we now ask the WIsh core to open connections to any contacts that is has in its database */
        wish.request.Connection.checkConnections(new Connection.CheckConnectionsCb() {
            @Override
            public void cb(boolean b) {}
        });

        /* Normally we proceed to next step here. */
        identityList();

        /* We also subscribe to signals (async events) from Wish core and Mist. This will cause other steps to be taken asynchronously, depending on the app state */
        wishSignals();
        mistSignals();
    }


    private void identityList() {
        /* Step 7: Make identity list, so that we can get hold of the Identity objects of the services that we are going to need later.
        Note that if we don't have access to regService identity, then we will make a friend request now explicitly. This is what happens the user uses the app for the first time. */

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
                    friendRequest(); //Proceed to next step, in case user uses app for the first time (registration server was not known). Else when using app later, registration service (and also charging service) will become online later and will be detected in mist signals as 'peers' event, see step 9
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
                    /* Step 8b: Friend request was accepted, ask local wish core to open connections. If we don't do this there could some delay until the wish core opens connection by itself */
                    wish.request.Connection.checkConnections(new Connection.CheckConnectionsCb() {
                        @Override
                        public void cb(boolean b) {}
                    });
                    /* Do the same thing as in step 7, as we now have access to Reg serice identity. When the regService connection is ready, a 'peers' mist signal is emitted, and the story continues there, as step 9 */
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
                   /* Step 9. We have a connection to a mist peer, but we don't know what. We need to list peers to find out what connections we have. */
                   listPeers();
               }
            }
        });
    }

    private void friendRequest() {
        /* Step 8: Send friend request to registration server, friend request is sent to a contact which is hard-coded into the app.
         * When the friend request is accepted by registration server, we will see friendRequesteeAccepted signal and which is handled in wishSignals, see step 8b */
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
        mist.api.request.Mist.listServices(new Mist.ListServicesCb() { // Note: Mist.listServices should be called listPeers. This will be rectified in a later release
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
                                              /* Step 10: show credit card input form. Proceed to next step. */
                                              activateCreditCardForm();
                                          } else {
                                              /* Step 10b, this is taken if we have the certificate already, in that case user can try to start charging by "reading" a QR code (simulated by push button in this case), go to step 13 */
                                              activateReadQRCodeButton();
                                          }
                                          if (mistSignalsId != 0) {
                                              mist.api.request.Mist.cancel(mistSignalsId);
                                          }
                                      }else if(data.equals("eu.biotope-project.charging-service") && chargingPeer == null) {
                                          /* Step 14, the charging service has become online, show the button which allows user to send OMI message, to start charing.  */
                                          chargingPeer = peer;
                                          activateForgetChargingButton();
                                          chargingButton.setVisibility(View.INVISIBLE);
                                          if (mistSignalsId != 0) {
                                              mist.api.request.Mist.cancel(mistSignalsId);
                                          }
                                          activateOmiButton();
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

    private void activateCreditCardForm() {
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
        /* Step 11: When user clicks on "save" on the credit card form, this is called. Note that all the input must be technically valid, to show the input button.
          * Test with: 4111 1111 1111 1111  */
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

                /* Step 12: Send the credit card info to registration server, and when the registration server responds, the cbDocument callback is activated and you get the certificate as parameter */
                mist.node.request.Control.invoke(regServerPeer, "getCertificate", bsonDocument ,new Control.InvokeCb() {
                    @Override
                    public void cbDocument(BsonDocument value) {
                        super.cbDocument(value);

                        certificate = value;
                        /* Save certificate to disk */
                        saveCert(certificate);
                        cardForm.setVisibility(View.INVISIBLE);
                        cardButton.setVisibility(View.INVISIBLE);
                        activateReadQRCodeButton();
                    }
                });
            }
        });
    }

    private void activateReadQRCodeButton() {
        chargingButton.setVisibility(View.VISIBLE);
        chargingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Step 13: User starts charging by "reading" a QR code (simulated by push button in this case)
                 The user "reads" QR code, and a friend request is sent to the charging service, with the certificate as a parameter.
                  (Note that in this case the app has the charging service's contact info hardcoded. In reality the information is obtained from the QR code. )
                  Next, when the charging service accepts the friend request, the next step is invoked when the charging service becomes online and Mist signal 'peers' is emitted. */
                mistSignals();
                wish.request.Identity.friendRequest(userIdentity.getUid(), chargingBson, certificate, new Identity.FriendRequestCb() {
                    @Override
                    public void cb(boolean b) {}
                });
            }
        });
    }

    private void activateOmiButton() {
        omiButton.setVisibility(View.VISIBLE);
        final String omiMsg = "<InfoItem name='ActivateCharging'></InfoItem>";
        omiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Step 15, the last step. User has pressed on the "send OMI message" button, send the OMI command to the charging service's OMI interface. */
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
                            Log.d(TAG, "OMI request successfully sent! ");
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

    private void activateForgetChargingButton() {
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
