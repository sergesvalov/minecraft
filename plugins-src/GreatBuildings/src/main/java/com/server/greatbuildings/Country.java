package com.server.greatbuildings;

/**
 * Country / region where the real-world great building is located.
 */
public enum Country {
    GEORGIA("georgia", "Грузия", "Georgia"),
    CYPRUS("cyprus", "Кипр", "Cyprus");

    private final String id;
    private final String titleRu;
    private final String titleEn;

    Country(String id, String titleRu, String titleEn) {
        this.id = id;
        this.titleRu = titleRu;
        this.titleEn = titleEn;
    }

    public String id() {
        return id;
    }

    public String titleRu() {
        return titleRu;
    }

    public String titleEn() {
        return titleEn;
    }

    public static Country fromString(String query) {
        if (query == null) {
            return null;
        }
        for (Country c : values()) {
            if (c.id.equalsIgnoreCase(query) || c.titleRu.equalsIgnoreCase(query) || c.titleEn.equalsIgnoreCase(query)) {
                return c;
            }
        }
        return null;
    }
}

