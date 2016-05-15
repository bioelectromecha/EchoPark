package com.didactapp.echopark;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.widget.Toast;


import com.apkfuns.logutils.LogUtils;
import com.didactapp.echopark.data.EchoLocation;
import com.didactapp.echopark.data.ParkingLocation;
import com.didactapp.echopark.audio.WavAudioRecorder;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ServerValue;
import com.firebase.client.ValueEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        OnMapReadyCallback{


    //map constants
    private final int MAP_ZOOM = 17;
    private final int MAP_TILT = 65;
    private final int LOCATION_UPDATE_INTERVAL = 5000;

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private SupportMapFragment mMapFragment;
    private boolean mMapReady = false;
    private GoogleMap mGoogleMap;
    private Location mLastLocation = null;


    //firebase stuff
    private static final String FIREBASE_URL = "https://echopark.firebaseio.com/";
    private Firebase mFirebaseRef;
    private Firebase mFirebaseParkingSpotsRef;
    private boolean mTransmit = false;

    //audio recorder stuff
    private WavAudioRecorder myAudioRecorder;
    private final String AUDIO_FILE_NAME = "/recording.3gpp";
    private boolean mIsRecording = false;
    private boolean mReadyToTransmit = false;
    private boolean mHasPermission = false;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //initialize firebase
        Firebase.setAndroidContext(this);
        //set transmission and parking spot location child element
        mFirebaseRef = new Firebase(FIREBASE_URL).child("EchoLocation");
        mFirebaseParkingSpotsRef = new Firebase(FIREBASE_URL).child("ParkingSpotLocation");

        setContentView(R.layout.activity_main);

        // bind the google api client - for maps and user location
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //bind the google map fragment from xml
        mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mMapFragment.getMapAsync(this);

        //disable screen turn off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //initialize sound recorder and ask for permission if neccesary
        initializeMediaRecorder();
        findParking();
    }

    public void initializeMediaRecorder(){
        LogUtils.d("initializeMediaRecorder reached");
        requestAudioRecordingPermission();
        if(mHasPermission == false){
            return;
        }
        //initialize media recorder
        myAudioRecorder= WavAudioRecorder.getInstance();
        //append the filename to the internal storage path - this is where the output will go
        myAudioRecorder.setOutputFile(getFilesDir()+AUDIO_FILE_NAME);

        myAudioRecorder.prepare();
    }

    @Override
    public void onLocationChanged(Location location) {
        pointCameraAtLocation(location);
        mLastLocation=location;
        //if it can transmit, it will - otherwise will return
        transmitEcholocation();
        //if it can record, it will - otherwise will return
        recordEcho();
    }

    @Override
    public void onConnected(Bundle bundle) {

        requestLocationPermission();

        updateLastLocation();

        if (mLastLocation != null) {
            pointCameraAtLocation(mLastLocation);
        }

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(LOCATION_UPDATE_INTERVAL);

        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            //add user location marker to map
            mGoogleMap.setMyLocationEnabled(true);

        } catch (SecurityException e) {
            Toast.makeText(MainActivity.this, "Location Permission Not Granted", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(MainActivity.this, "onConnectionSuspended", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(MainActivity.this, "onConnectionFailed", Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMapReady = true;
        mGoogleMap = googleMap;
    }


    // don't forget onStart() and onStop() for any activity that uses google api services
    @Override
    protected void onStart() {
        //connect the api
        mGoogleApiClient.connect();
        super.onStart();
    }


    @Override
    protected void onStop() {
        // disconnect the api to avoid leaks
        mGoogleApiClient.disconnect();
        super.onStop();
    }


    public void requestLocationPermission() {
        //for the permission request callback code
        int REQUEST_CODE_ASK_PERMISSIONS = 123;

        //check if premission was granted, ask if not granted yet (or denied)
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //prompt user for location permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_ASK_PERMISSIONS);
        }
    }
    public void requestAudioRecordingPermission() {
        //for the permission request callback code
        int REQUEST_CODE_ASK_PERMISSIONS = 123;
        //check if audio premission was granted, ask if not granted yet (or denied)
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            //prompt user for location permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_ASK_PERMISSIONS);
        }
        mHasPermission=true;
    }

    public void updateLastLocation() {
        try {
            //get the last location of the device. This is mostly for before getting actual GPS position
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            //if fine location permission not granted
        } catch (SecurityException e) {
            Toast.makeText(MainActivity.this, "Location Permission Not Granted", Toast.LENGTH_SHORT).show();
        }
    }


    public void pointCameraAtLocation(Location location) {
        //if the map isn't ready - do nothing
        if (mMapReady != true) {
            return;
        }
        // copy the Location object values to a LatLong object values
        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
        //the target with all the parameters which specify where and how to move the camera
        CameraPosition target = CameraPosition.builder().target(userLocation).zoom(MAP_ZOOM).tilt(MAP_TILT).build();
        //move the map camera to target with a nice flying/gliding animation
        mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(target));
    }


    public void addLocationMarkerToMap(Location location,String title) {
        MarkerOptions lastLocationMarker = new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .title(title)
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher));

        //create the circle around marker
        CircleOptions lastLocationCircle = new CircleOptions()
                .center(new LatLng(location.getLatitude(), location.getLongitude()))
                .radius(40)
                .strokeColor(Color.GREEN)
                .strokeWidth(3)
                .fillColor(Color.argb(15, 00, 00, 255));

        //add both to the map
        if (mMapReady == true) {
            //add the marker
            mGoogleMap.addMarker(lastLocationMarker);
            //add the circle
            mGoogleMap.addCircle(lastLocationCircle);
        }
    }

    /**
     * Record device audio for set amount of time.
     */
    public void recordEcho() {
        LogUtils.d("recordEcho reached");

        if(mHasPermission==false){
            return;
        }
        if(mIsRecording == true){
            return;
        }
        if(mReadyToTransmit == true){
            return;
        }
        // try to start recording
        try {

            //call this because later stop() removes all it's settings
            initializeMediaRecorder();
            myAudioRecorder.start();
            mIsRecording = true;
            LogUtils.d("RECORDING STARTED");
            //record for 5 second and then stop
            new CountDownTimer(5000,1000){
                @Override
                public void onTick(long millisUntilFinished) {
                    //gets called at the end of every second
                }
                @Override
                public void onFinish() {
                    recordEchoStopHelper();
                }
            }.start();
        } catch (Exception e) {
            LogUtils.d("FAILED TO START RECORDING");
            return;
        }
    }

    /**
     * Used by recordEcho() to stop the recording.
     */
    private void recordEchoStopHelper(){
        LogUtils.d("recordEchoStopHelper reached");
        myAudioRecorder.stop();
        mIsRecording=false;
        mReadyToTransmit = true;
        LogUtils.d("RECORDING STOPPED");
    }

    /**
     * Get new parking spots when available.
     */
    public void findParking() {
        mFirebaseParkingSpotsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                LogUtils.d("There are " + snapshot.getChildrenCount() + " parking spots");

                //gets added to name of parking spot
                int i =0;

                //iterate through available parking spots
                for (DataSnapshot postSnapshot: snapshot.getChildren()) {
                    ParkingLocation ParkingLocation = postSnapshot.getValue(ParkingLocation.class);
                    Location tempLocation = new Location("");
                    tempLocation.setLatitude(Double.parseDouble(ParkingLocation.getLatitude()));
                    tempLocation.setLongitude(Double.parseDouble(ParkingLocation.getLongitude()));
                    addLocationMarkerToMap(tempLocation,"Parking Spot"+i);
                    i++;
                }
            }
            @Override
            public void onCancelled(FirebaseError firebaseError) {
                LogUtils.d("The read failed: " + firebaseError.getMessage());
            }
        });
    }

    private void transmitEcholocation(){
        LogUtils.d("transmitEcholocation reached");
        if(mLastLocation == null){
            return;
        }
        if(mIsRecording == true){
            return;
        }
        if(mReadyToTransmit==false){
            return;
        }
        //get file reference
        File file = new File(getFilesDir()+AUDIO_FILE_NAME);
        byte[] bytes;

        //encode file to bytes array
        try {
            bytes= Files.toByteArray(file);
        }catch (IOException e){
            LogUtils.d("FAILED TO CONVERT TO BYTES ARRAY");
            return;
        }

        LogUtils.d("TRANSMITTING");

        //encode bytes array to base64 string with Guava library
        String encoded = BaseEncoding.base64().encode(bytes);

        //get timestamp
        Object timeStamp = ServerValue.TIMESTAMP;

        // Create our 'model', an EchoLocation object
        EchoLocation echoLocation = new EchoLocation(String.valueOf(mLastLocation.getLatitude()),String.valueOf( mLastLocation.getLongitude()),encoded,timeStamp);
        // Create a new, auto-generated child of  location, and save our chat data there
        mFirebaseRef.push().setValue(echoLocation);
        mReadyToTransmit=false;
    }
}
