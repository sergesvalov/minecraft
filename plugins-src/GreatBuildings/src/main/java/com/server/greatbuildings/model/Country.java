package com.server.greatbuildings.model;

public enum Country {
    GEORGIA("georgia", "Грузия", "Georgia"),
    CYPRUS("cyprus", "Кипр", "Cyprus");

    private final String id;
    private final String russian;
    private final String english;

    Country(String id, String russian, String english) {
        this.id = id;
        this.russian = russian;
        this.english = english;
    }

    public String id() {
        return id;
    }

    public String russian() {
        return russian;
    }

    public String english() {
        return english;
    }

    public static Country fromString(String s) {
        if (s == null) {
            return null;
        }
        String k = s.trim().toLowerCase();
        for (Country c : values()) {
            if (c.id.equals(k) || c.russian.toLowerCase().equals(k) || c.english.toLowerCase().equals(k)) {
                return c;
            }
        }
        return null;
    }
}

