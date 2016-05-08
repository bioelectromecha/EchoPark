package com.didactapp.echopark;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;


import com.apkfuns.logutils.LogUtils;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseApp;
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
    private MediaRecorder myAudioRecorder;
    private String outputFile = null;
    private boolean mIsRecording = false;
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

        //initialize sound recorder and ask for permission if neccesary
        initializeMediaRecorder();

    }

    public void initializeMediaRecorder(){

        requestAudioRecordingPermission();

        if(mHasPermission == false){
            return;
        }
        //output file for audio recording
        outputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recording.3gpp";

        //initialize media recorder
        myAudioRecorder=new MediaRecorder();
        myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        myAudioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        myAudioRecorder.setOutputFile(outputFile);
    }


    @Override
    public void onLocationChanged(Location location) {
        pointCameraAtLocation(location);
        mLastLocation=location;
        if(mTransmit==true) {
            transmitEcholocation();
        }
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
        //check if premission was granted, ask if not granted yet (or denied)
        //TODO: add a checkSelfPermission WRITE_EXTERNAL_STORAGE also, instead of just RECORD_AUDIO
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
                .fillColor(Color.argb(15, 00, 255, 00));

        LogUtils.d(location.toString());
        //add both to the map
        if (mMapReady == true) {
            //add the marker
            mGoogleMap.addMarker(lastLocationMarker);
            //add the circle
            mGoogleMap.addCircle(lastLocationCircle);
        }

    }

    public void onClickTransmitEcho(){
        Firebase myFirebaseRef = new Firebase("https://echopark.firebaseio.com/");
    }


    //button listener
    public void onClickTransmitEcho(View view) {

        mTransmit=true;
        /*
        Button transmitButton = (Button) findViewById(R.id.transmit_echo_button);
        if(mHasPermission==false){
            return;
        }
        LogUtils.d("mIsRecording=="+mIsRecording);
        if (mIsRecording == false) {
            try {
                mIsRecording = true;
                transmitButton.setText("RECORDING...");
                myAudioRecorder.prepare();
                myAudioRecorder.start();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        if(mIsRecording == true){
            transmitButton.setText("STOPPED");
            mIsRecording= false;
            myAudioRecorder.stop();
            myAudioRecorder.release();
            myAudioRecorder = null;
            return;
        }
        */
        /*
        if(mTransmit==false) {
            Toast.makeText(MainActivity.this, "transmitting coordinates", Toast.LENGTH_SHORT).show();
            transmitButton.setText("Transmitting...");
            mTransmit = true;
        }else{
            Toast.makeText(MainActivity.this, "stopped transmission", Toast.LENGTH_SHORT).show();
            transmitButton.setText("TRANSMIT ECHO");
            mTransmit = false;
        }
        */
    }

    //button listener
    public void onClickFindParking(View view) {
        Toast.makeText(MainActivity.this, "marking coordinates", Toast.LENGTH_SHORT).show();
        mFirebaseParkingSpotsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                System.out.println("There are " + snapshot.getChildrenCount() + " parking spots");
                for (DataSnapshot postSnapshot: snapshot.getChildren()) {
                    ParkingLocation ParkingLocation = postSnapshot.getValue(ParkingLocation.class);
                    Location tempLocation = new Location("");
                    tempLocation.setLatitude(Double.parseDouble(ParkingLocation.getLatitude()));
                    tempLocation.setLongitude(Double.parseDouble(ParkingLocation.getLongitude()));
                    addLocationMarkerToMap(tempLocation,"Parking Spot");
                }
            }
            @Override
            public void onCancelled(FirebaseError firebaseError) {
                System.out.println("The read failed: " + firebaseError.getMessage());
            }
        });
    }
    private void transmitEcholocation(){
        if(mLastLocation == null){
            return;
        }
        Toast.makeText(MainActivity.this, "transmitting....", Toast.LENGTH_SHORT).show();

        //get file reference
        File file = new File(outputFile);
        byte[] bytes;

        //encode file to bytes array
        try {
            bytes= Files.toByteArray(file);
        }catch (IOException e){
            LogUtils.d("IOEXCEPTION in Files.toByteArray(file)");
            return;
        }

        //encode bytes array to base64 string with Guava library
        String encoded = BaseEncoding.base64().encode(bytes);

        //get timestamp
        Object timeStamp = ServerValue.TIMESTAMP;

        // Create our 'model', an EchoLocation object
        EchoLocation echoLocation = new EchoLocation(String.valueOf(mLastLocation.getLatitude()),String.valueOf( mLastLocation.getLongitude()),encoded,timeStamp);
        // Create a new, auto-generated child of  location, and save our chat data there
        mFirebaseRef.push().setValue(echoLocation);
    }
}
