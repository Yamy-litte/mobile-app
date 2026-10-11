package com.example.mylibrary.dao;

import com.example.mylibrary.models.Location;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirestoreLocationDao implements LocationDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "locations";

    @Override
    public void addLocation(Location location, DataCallback<Location> callback) {
        db.collection(COLLECTION).add(location)
                .addOnSuccessListener(doc -> {
                    location.setId(doc.getId());
                    callback.onSuccess(location);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getLocations(DataCallback<List<Location>> callback) {
        db.collection(COLLECTION).get()
                .addOnSuccessListener(query -> {
                    List<Location> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Location loc = doc.toObject(Location.class);
                        loc.setId(doc.getId());
                        list.add(loc);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getLocationById(String id, DataCallback<Location> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Location loc = doc.toObject(Location.class);
                        if (loc != null) loc.setId(doc.getId());
                        callback.onSuccess(loc);
                    } else {
                        callback.onFailure("Location not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
