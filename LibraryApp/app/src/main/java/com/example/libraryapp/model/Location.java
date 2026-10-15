package com.example.libraryapp.model;

/**
 * Data model for the Location entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class Location {
    private int id;
    private String building;
    private String floor;
    private String shelfNumber;

    public Location() {
    }

    public Location(int id, String building, String floor, String shelfNumber) {
        this.id = id;
        this.building = building;
        this.floor = floor;
        this.shelfNumber = shelfNumber;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getShelfNumber() {
        return shelfNumber;
    }

    public void setShelfNumber(String shelfNumber) {
        this.shelfNumber = shelfNumber;
    }

}
