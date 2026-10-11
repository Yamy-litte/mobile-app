package com.example.mylibrary.dao;

import com.example.mylibrary.models.Location;
import java.util.List;

public interface LocationDao {
    void addLocation(Location location, DataCallback<Location> callback);
    void getLocations(DataCallback<List<Location>> callback);
    void getLocationById(String id, DataCallback<Location> callback);
}
