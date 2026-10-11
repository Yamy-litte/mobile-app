package com.example.mylibrary.dao;

import com.example.mylibrary.models.Location;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FirestoreLocationDao implements LocationDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "locations";

    @Override
    public void addLocation(Location location, DataCallback<Location> callback) {
        String id = location.getId() != null ? location.getId() : db.collection(COLLECTION).document().getId();
        location.setId(id);
        db.collection(COLLECTION).document(id).set(location)
                .addOnSuccessListener(unused -> callback.onSuccess(location))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getLocations(DataCallback<List<Location>> callback) {
        db.collection(COLLECTION).get()
                .addOnSuccessListener(snapshot -> {
                    List<Location> locations = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Location location = doc.toObject(Location.class);
                            if (location != null) {
                                location.setId(doc.getId());
                                locations.add(location);
                            }
                        }
                    }
                    callback.onSuccess(locations);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getLocationById(String id, DataCallback<Location> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Location location = doc.toObject(Location.class);
                        if (location != null) {
                            location.setId(doc.getId());
                            callback.onSuccess(location);
                        } else {
                            callback.onError(new Exception("Location is null"));
                        }
                    } else {
                        callback.onError(new Exception("Location not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateLocation(Location location, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(location.getId()).set(location)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void deleteLocation(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
