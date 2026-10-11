package com.example.mylibrary.dao;

public interface DataCallback<T> {
    void onSuccess(T result);
    void onFailure(String errorMessage);
}
