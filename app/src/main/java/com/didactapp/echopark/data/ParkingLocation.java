package com.didactapp.echopark.data;

/**
 * Created by roman on 14/04/16.
 */
public class ParkingLocation {
    private String latitude;
    private String longitude;
    private String discoverytime;

    // Required default constructor for Firebase object mapping
    @SuppressWarnings("unused")
    private ParkingLocation() {
    }

    ParkingLocation(String latitude, String longitude, String discoveryTime) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.discoverytime = discoveryTime;
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public String getDiscoverytime(){ return discoverytime;}
}
