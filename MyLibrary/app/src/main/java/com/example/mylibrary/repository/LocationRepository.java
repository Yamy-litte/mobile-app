package com.example.mylibrary.repository;

import com.example.mylibrary.dao.LocationDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreLocationDao;
import com.example.mylibrary.models.Location;
import java.util.List;

public class LocationRepository {
    private final LocationDao locationDao = new FirestoreLocationDao();

    public void addLocation(Location location, DataCallback<Location> callback) {
        locationDao.addLocation(location, callback);
    }

    public void getLocations(DataCallback<List<Location>> callback) {
        locationDao.getLocations(callback);
    }

    public void getLocationById(String id, DataCallback<Location> callback) {
        locationDao.getLocationById(id, callback);
    }
}
