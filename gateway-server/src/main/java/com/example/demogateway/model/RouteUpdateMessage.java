package com.example.demogateway.model;

import java.io.Serializable;

public class RouteUpdateMessage implements Serializable {
    private RouteAction action;
    private Object payload;
    private long timestamp;

    // Default constructor
    public RouteUpdateMessage() {
    }

    // Constructor with all fields
    public RouteUpdateMessage(RouteAction action, Object payload, long timestamp) {
        this.action = action;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public RouteAction getAction() {
        return action;
    }

    public void setAction(RouteAction action) {
        this.action = action;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}