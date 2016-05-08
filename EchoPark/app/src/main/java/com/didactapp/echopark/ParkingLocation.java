package com.didactapp.echopark;

/**
 * Created by roman on 14/04/16.
 */
public class ParkingLocation {
    private String latitude;
    private String longitude;

    // Required default constructor for Firebase object mapping
    @SuppressWarnings("unused")
    private ParkingLocation() {
    }

    ParkingLocation(String latitude, String longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }
}
