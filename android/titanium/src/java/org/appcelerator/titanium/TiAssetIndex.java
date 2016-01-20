package org.appcelerator.titanium;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.appcelerator.kroll.util.KrollAssetHelper;

public final class TiAssetIndex {

    // ~ Instance fields
    // ------------------------------------------------------------------------------------------------

    private List<String> files;

    // ~ Constructors
    // ---------------------------------------------------------------------------------------------------

    public TiAssetIndex() {
        String string = KrollAssetHelper
                .readAsset("__assets_list__.index");
        if (string != null) {
            this.files = Arrays.asList(string.split("\\r?\\n"));
        }
    }

    // ~ Methods
    // --------------------------------------------------------------------------------------------------------
    
    public boolean exists(final String path) {
        if (this.files == null) {
            return false;
        }
        for (final String file : this.files) {
            if (file.startsWith(path)) {
                return true;
            }
        }
        return false;
    }
    
    /* returns the number of files in a directory */
    public int numFiles(final String dir) {
        if (this.files == null) {
            return 0;
        }
        String directory = dir;
        if (directory.endsWith(File.separator)) {
            directory = directory.substring(0, directory.length() - 1);
        }

        int num = 0;
        for (final String file : this.files) {
            if (file.startsWith(directory)) {

                String rest = file.substring(directory.length());
                if (rest.charAt(0) == File.separatorChar) {
                    if (rest.indexOf(File.separator, 1) == -1) {
                        num = num + 1;
                    }
                }
            }
        }

        return num;
    }

    public Set<String> list(String path) {
        if (this.files == null) {
            return null;
        }
        String  realPath = path;
        if (realPath.length() > 0 && !realPath.endsWith("/")) {
          realPath = realPath + "/";
        }
        Set<String> result = new HashSet<String>();
        for (final String file : this.files) {
            if (file.startsWith(realPath)) {
                result.add(file.replace(realPath, "").split("/")[0]);
            }
        }
        return result;
    }
}