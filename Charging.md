# Biotope charging scenario

## Quick overview

Actors:

-The user(s) with their apps
-The registration server
-The charging provider(s), with their Mist interfaces.

Precondition: there is an existing trust relationship between
Registration server and Charging provider(s).

1. The app has the registration server's "contact info" pre-configured.
   This means that the contact info must be exported from the
   registration server as base64. This base64 is cut&pasted to Android
   source code. The Android code must be able to base64 decode the
   string.

   The registration server's identity is exported like this:

   ```
   wish> identity.export(result[0].uid)
   wish> BSON.serialize(result).toString("base64")
   ```

   When user starts application for the first time, it creates an
   identity for the user. Then a Wish friend request is sent to the
   registration service (at the location specified by contact card).
   In the following, `my_uid` is the uid of the local user's identity,
   `contact_base64` is the contact card as a String.

   ```
   contact=BSON.deserialize(Buffer.from(contact_base64, 'base64'))
   identity.friendRequest(my_uid, contact);
   ```

   The registration app allows the friend request, but it must flag this
   connection as "untrusted", as the server has not yet identified this user
   properly. (In the demo implementation this will be overlooked.)

   The app must save the registration server's uid, so that it can
   detect when the server becomes online.

2. Using the app, user supplies billing info. This is done by invoking a
   Mist endpoint on the registratin app.

   This means the app must be listening to `friendRequestAccepted` from
   Wish, and `peers` signals from MistApi, so that it
   knows that after friend request in step 1, when the registration
   server becomes online (`peer ruid === reg_server_uid`), the app can
   do the control.invoke request.

   ```
   mist.control.invoke(peer, 'requestCert', { cc_number:
   "4920123412341234", expiry: "09/20", cvc: "123" })
   ```

   The registration service creates account(s) on the participating
   charging provider(s), and adds billing info to them. 
   The charging provider returns somekind of credential(s) (api key).
   The API used for this depends on each charing provider, and is out 
   of scope for the time being.
   (This step will be omitted in the actual implementation)

   When the registration service has succeeded in creating the accounts,
   it knows that the user is legit as it has billing info for the user.
   The Registration service registers any credentials given by the
   charging provider to the Mist/OMI interface of the charging provider,
   associating Wish uid with proprietary credential.

3. Registration server issues certificate, and returns it as the
   return value of the invoke issued by app in step 2.

4. App saves certificate, so that it can use it later.

5. When user wants to charge, the user uses certificate to "friend
   request" the charging provider, providing certificate as credential.
   The user gets the "contact info" to the correct charging provider by
   e.g. reading a QR code or by reading NFC tag with telephone.
   The contact info includes, besides Wish contact details of the
   charging provider, also identifying information in the "meta" field.
   User makes friend request supplying the cert as metadata. Also the
   charging socket "meta" must be provided somehow (to identify which
   socket to enable).
8. The charging provider's Mist/OMI interface accepts the friend
   request, as the certificate checks out.
   The charging provider's Mist/OMI interface can map the Wish uid to the proprietary
   credentials, (registered in step 4).
9. When the user stops charging, billing is performed using the charging
   provider's own billing system.

## SW components in this repo

### Registration service

### Interface to the charging service provider

### Test application
