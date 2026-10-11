package com.example.mylibrary.repository;

import com.example.mylibrary.dao.ReservationDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreReservationDao;
import com.example.mylibrary.models.Reservation;
import java.util.List;

public class ReservationRepository {
    private final ReservationDao reservationDao = new FirestoreReservationDao();

    public void addReservation(Reservation reservation, DataCallback<Reservation> callback) {
        reservationDao.addReservation(reservation, callback);
    }

    public void getReservationsByCustomerId(String customerId, DataCallback<List<Reservation>> callback) {
        reservationDao.getReservationsByCustomerId(customerId, callback);
    }

    public void getReservationsByBookId(String bookId, DataCallback<List<Reservation>> callback) {
        reservationDao.getReservationsByBookId(bookId, callback);
    }

    public void getActiveReservations(DataCallback<List<Reservation>> callback) {
        reservationDao.getActiveReservations(callback);
    }

    public void updateReservation(Reservation reservation, DataCallback<Void> callback) {
        reservationDao.updateReservation(reservation, callback);
    }

    public void cancelReservation(String id, DataCallback<Void> callback) {
        reservationDao.cancelReservation(id, callback);
    }
}
