package com.example.smart_login_conditions.models;

public class Condition {

    public enum ConditionType {
        ACTION_BUTTON,
        INPUT_FIELD,
        AUTOMATIC
    }

    private String name;
    private String status;
    private ConditionType type;
    private boolean isPassed;

    public Condition(String name, String status, ConditionType type, boolean isPassed) {
        this.name = name;
        this.status = status;
        this.type = type;
        this.isPassed = isPassed;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public ConditionType getType() {
        return type;
    }

    public boolean isPassed() {
        return isPassed;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPassed(boolean passed) {
        isPassed = passed;
    }
}


