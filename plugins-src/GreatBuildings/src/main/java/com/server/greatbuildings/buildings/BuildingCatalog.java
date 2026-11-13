package com.server.greatbuildings.buildings;

import com.server.greatbuildings.cyprus.Paphos;
import com.server.greatbuildings.georgia.Bagrati;
import com.server.greatbuildings.georgia.Svetitskhoveli;
import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.model.Country;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central catalog of all available 1:1 historical buildings.
 */
public final class BuildingCatalog {

    private final Map<String, Building> byId = new LinkedHashMap<>();

    public BuildingCatalog() {
        register(new Svetitskhoveli());
        register(new Bagrati());
        register(new Paphos());
    }

    private void register(Building building) {
        Building previous = byId.put(building.id().toLowerCase(), building);
        if (previous != null) {
            throw new IllegalStateException("Duplicate building id: " + building.id());
        }
    }

    public Building byId(String id) {
        if (id == null) {
            return null;
        }
        return byId.get(id.toLowerCase());
    }

    public Collection<Building> all() {
        return List.copyOf(byId.values());
    }

    public List<Building> byCountry(Country country) {
        return byId.values().stream()
                .filter(b -> b.country() == country)
                .toList();
    }

    public List<String> ids() {
        return List.copyOf(byId.keySet());
    }
}

