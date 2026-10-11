package com.example.mylibrary.models;

public class Location {
    private String id;
    private String building;
    private int floor;
    private String shelfNumber;

    public Location() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }
    public String getShelfNumber() { return shelfNumber; }
    public void setShelfNumber(String shelfNumber) { this.shelfNumber = shelfNumber; }
}
