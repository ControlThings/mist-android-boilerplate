#Mist android boilerplate
This is a empty android boilerpalete to start a mist android project. 

Check out the difrent branches for examples.

##Getting Started

###Prerequisites

* [Android studio](https://developer.android.com/studio/) 
* Credentials to Controlthings artifactory server

Fore the moment you will need acces to Controlthings Artifactory server, to get the MistApi.

####Credentials for artifactory

Create a `gradle.properties` fille in `HOME_DIR/.gradle` an add this lines:

```
integrator_username=YOUR_OPTAINED_USERNAME
integrator_password=YOUR_OPTAINED_PASSWORD
```

##Running

Open the project in android studio and it should be ready to go.

##How to use MistApi in other projects
 
###Configure Gradle
 
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

###Use of ProcessLifecycle

To be able to stop and start mist when the app gos to the background its preferred to use the [ProcessLifecycleOwner](https://developer.android.com/reference/android/arch/lifecycle/ProcessLifecycleOwner) because if mist is runing in backround it will drain the battery. 

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

###Start the mist service

On android mist runs on a android service and uses an AddonReceiver for connection and disconnection info.

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