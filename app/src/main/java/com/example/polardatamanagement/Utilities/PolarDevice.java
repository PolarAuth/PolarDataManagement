package com.example.polardatamanagement.Utilities;

public class PolarDevice {

    private String id;
    private String address;
    private String rssi;
    private String name;
    private String isConnectable;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRssi() {
        return rssi;
    }

    public void setRssi(String rssi) {
        this.rssi = rssi;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIsConnectable() {
        return isConnectable;
    }

    public void setIsConnectable(String isConnectable) {
        this.isConnectable = isConnectable;
    }
}
