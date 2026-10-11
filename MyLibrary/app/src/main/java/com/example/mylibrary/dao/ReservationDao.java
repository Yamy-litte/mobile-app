package com.example.mylibrary.dao;

import com.example.mylibrary.models.Reservation;
import java.util.List;

public interface ReservationDao {
    void addReservation(Reservation reservation, DataCallback<Reservation> callback);
    void getReservationsByCustomerId(String customerId, DataCallback<List<Reservation>> callback);
    void getReservationsByBookId(String bookId, DataCallback<List<Reservation>> callback);
    void getActiveReservations(DataCallback<List<Reservation>> callback);
    void updateReservation(Reservation reservation, DataCallback<Void> callback);
    void cancelReservation(String id, DataCallback<Void> callback);
}
