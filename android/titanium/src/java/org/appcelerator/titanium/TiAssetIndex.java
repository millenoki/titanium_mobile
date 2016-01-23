package org.appcelerator.titanium;

import java.util.HashMap;
import java.util.Set;

import org.appcelerator.kroll.util.KrollAssetHelper;

public final class TiAssetIndex {

    // ~ Instance fields
    // ------------------------------------------------------------------------------------------------

    private HashMap files;

    // ~ Constructors
    // ---------------------------------------------------------------------------------------------------

    public TiAssetIndex() {
        String string = KrollAssetHelper.readAsset("__assets_list__.index");
        
        if (string != null) {
            String[] array, parts;
            HashMap current, newValue;
            int i, j, length;
            String part;
            Object value;
            this.files = new HashMap();
            array = string.split("\\r?\\n");
            for (i = 0; i < array.length; i++) {
                current = this.files;
                parts = array[i].split("/");
                length = parts.length;
                for (j = 0; j < length - 1; j++) {
                    part = parts[j];
                    value = current.get(part);
                    if (value instanceof HashMap) {
                        current = (HashMap) value;
                    } else {
                        newValue = new HashMap<>();
                        current.put(part, newValue);
                        current = newValue;
                    }
                }
                current.put(parts[length - 1], null);
            }
        }
    }

    // ~ Methods
    // --------------------------------------------------------------------------------------------------------

    public boolean exists(final String path) {
        if (this.files == null) {
            return false;
        }
        String[] parts = path.split("/");
        HashMap current = this.files;
        for (int j = 0; j < parts.length; j++) {
            String part = parts[j];
            if (!current.containsKey(part)) {
                return false;
            }
            current = (HashMap) current.get(part);
            
        }
        return true;
    }


    public Set<String> list(String path) {
        if (this.files == null) {
            return null;
        }
        HashMap current = this.files;
        if (!path.isEmpty()) {
            String[] parts = path.split("/");
            for (int j = 0; j < parts.length; j++) {
                String part = parts[j];
                if (!current.containsKey(part)) {
                    return null;
                }
                current = (HashMap) current.get(part);
            }
        }
            
        return  current.keySet();
    }
}