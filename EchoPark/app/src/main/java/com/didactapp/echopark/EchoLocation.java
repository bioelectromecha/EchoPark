package com.didactapp.echopark;

/**
 * Created by roman on 14/04/16.
 */

public class EchoLocation {

    private String latitude;
    private String longitude;
    private String audio;
    private Object timestamp;

    // Required default constructor for Firebase object mapping
    @SuppressWarnings("unused")
    private EchoLocation() {
    }

    EchoLocation(String latitude, String longitude, String audio, Object timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.audio = audio;
        this.timestamp = timestamp;
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public String getAudio() {
        return audio;
    }

    public Object getTimestamp() {
        return timestamp;
    }

}