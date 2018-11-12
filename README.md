# Mist android boilerplate
This is a empty android boilerpalete to start a mist android project. 

Please learn more about the Mist concepts on https://controlthings.atlassian.net/wiki/spaces/MIST/pages/655361/Identity-Based+Communication

## Licenses

The example source code is licensed under Apache 2.0. For the example code to be useful, you will need to have the MistApi aar libraries from ControlThings. These libraries require a valid license from ControlThings Oy Ab so that they can be used.

# Getting Started

## Prerequisites

* [Android studio](https://developer.android.com/studio/) 
* Credentials to Controlthings artifactory server. Please get in touch with ControlThings to obtain them: info@controlthings.fi or +358405166116

For the moment you will need acces to Controlthings Artifactory server, to get the MistApi.

##Credentials for Artifactory server

Create a `gradle.properties` fille in `HOME_DIR/.gradle` an add this lines:

```
integrator_username=YOUR_USERNAME
integrator_password=YOUR_PASSWORD
```

## Running

Open the project in android studio and it should be ready to go.

The example application does nothing else but starts Mist service, and queries Wish and Mist versions.

# How to use MistApi in existing projects
 
##Configure Gradle
 
In your **top level** `build.gradle` file add a reference to aritfactory:

```
allprojects {
    repositories {
        maven {
            url "http://foremost.cto.fi:8081/artifactory/libs-release-local"
            credentials {
                username = "${integrator_username}"
                password = "${integrator_password}"
            }
        }
    }
}        
``` 

In your **app** `build.gradle` import MistApi:

```
dependencies {
  implementation 'fi.ct.mist:MistApi:0.6.4'
}
```

## Use of ProcessLifecycle

The Mist IoT library is built on Wish, which is a software platform for distributed systems. Wish is the  responsible for opening and maintaining secure authenticated connections between the communicating peers. In Wish/Mist, the connections between peers are not request/response oriented, as in HTTP. Instead, the connections between peers are TCP socket connections, which allow for asynchronous communication between the peers. Currently, these connections are automatically managed, and the application programmer does not have to explicitly manage connections. 

On Android, in order to use Mist API, the Mist service must be started first. The Wish core is started automatically when Mist service is started. Likewise, Wish core is stopped when Mist service is stopped.

When ever a Mist/Wish Android app becomes the "foreground app" on the of the Android system, the Mist service should be started. When the service is running, Wish automatically attempts to maintain connections to the remote peer(s) that it knows of. 

As active TCP connections consume battery, the Wish core should be stopped when the app is no longer used by the user, i.e. it is no longer the foreground app. 

To be able to start the Wish core when app comes into foreground, and stop Wish core when the app goes to the background, its preferred to use the [ProcessLifecycleOwner](https://developer.android.com/reference/android/arch/lifecycle/ProcessLifecycleOwner) because it helps with tracking the state of the whole app.  

In your **app** `build.gradle` import:

```
dependencies {
  implementation 'android.arch.lifecycle:extensions:1.1.1'
}
```
In Launcher activity onCreate add a observer:

```
  @Override
    protected void onCreate(Bundle savedInstanceState) {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppLifecycleListener());
    }
```
Create a Lifecycle Listener Class:

```
class AppLifecycleListener implements LifecycleObserver {
  
  @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onMoveToForeground() {}

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onMoveToBackground() {}
}
```

### Start the Mist service

On Android, Mist runs on a android service and uses an AddonReceiver for connection and disconnection info.

Add the AddonReciver to the class:

```
public class MainActivity extends AppCompatActivity implements AddonReceiver.Receiver
``` 

In onCreate create a mist intent and add the receiver to it:

```
@Override
protected void onCreate(Bundle savedInstanceState) {

  ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppLifecycleListener());

  Intent mistService = new Intent(this, Service.class);
  AddonReceiver mistReceiver = new AddonReceiver(this);
  mistService.putExtra("receiver", mistReceiver);
}
```

Add onConnected (will be called when mist is ready and you can use the api):

```
@Override
    public void onConnected() {
    }
```

Add onDisconnected (Will be called when the mist Service is stoped):

```
    @Override
    public void onDisconnected() {
    }
```

Start the mist service in onMoveToForeground:
```
@OnLifecycleEvent(Lifecycle.Event.ON_START)
public void onMoveToForeground() {
  startService(mistService);
}
```


Stop the service in onMoveToBackground:
```
@OnLifecycleEvent(Lifecycle.Event.ON_STOP)
public void onMoveToBackground() {
  stopService(mistService);
}
```

## Making MistApi requests

(To be continued...)

