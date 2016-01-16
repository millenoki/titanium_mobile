package org.appcelerator.titanium;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;


@Kroll.module(creatable=false)
public class ProtectedModule extends KrollModule {

    // Standard Debugging variables
    private static final String TAG = "AkylasCommonjsModule";
    


    public static class AeSimpleSHA1 {
        private static String convertToHex(byte[] data) {
            StringBuilder buf = new StringBuilder();
            for (byte b : data) {
                int halfbyte = (b >>> 4) & 0x0F;
                int two_halfs = 0;
                do {
                    buf.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte) : (char) ('a' + (halfbyte - 10)));
                    halfbyte = b & 0x0F;
                } while (two_halfs++ < 1);
            }
            return buf.toString();
        }

        public static String SHA1(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            byte[] sha1hash = md.digest();
            return convertToHex(sha1hash);
        }
        
        public static String hexToString(String txtInHex)
        {
            byte [] txtInByte = new byte [txtInHex.length() / 2];
            int j = 0;
            for (int i = 0; i < txtInHex.length(); i += 2)
            {
                    txtInByte[j++] = Byte.parseByte(txtInHex.substring(i, i + 2), 16);
            }
            return new String(txtInByte);
        }
    }

    public static void showError(final String message) {
        HashMap error = new HashMap();
        error.put("title", message);
        error.put("canContinue", false);
        TiApplication.getExceptionHandler().handleException(error);
    }
    
    private static final HashMap<String, String> sComputedKeys = new HashMap<>();

    protected static void verifyPassword(TiApplication app, final String passwordKey, final String password)
    {
        ITiAppInfo appInfo = app.getAppInfo();
        TiProperties appProperties = app.getAppProperties();
        String appId = appInfo.getId();
        if (appId.equals("com.akylas.titanium.ks")) {
            return;
        }
        String key = appProperties.getString(passwordKey, null);
        
        if (key == null) {
            showError("You need to set the \"" + passwordKey + "\"");
            return;
        }
        String result;
        try {
            String toCompute = String.format("%s%s",  appId, password);
            if (sComputedKeys.containsKey(toCompute)) {
                result = sComputedKeys.get(toCompute);
            } else {
                result = AeSimpleSHA1.SHA1(toCompute);
                sComputedKeys.put(toCompute, result);
            }
            if (!result.equalsIgnoreCase(key)) {
                showError("wrong \"" + passwordKey + "\" key!");
                return;
            }
        } catch (Exception e) {
            result = null;
        }
    }
}
