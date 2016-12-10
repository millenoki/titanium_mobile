/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 * 
 * Copied and modified from Apache's HTTPClient implementation (APL2 license):
 * org.apache.http.entity.StringEntity
 */


package ti.modules.titanium.network.httpurlconnection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.appcelerator.titanium.util.TiConvert;

public class JsonEntity extends Entity{

    protected final byte[] content;

    public JsonEntity(final Object data, String mimeType, String charset) throws UnsupportedEncodingException {
        super();
        if (data == null) {
            throw new IllegalArgumentException("Source JSON may not be null");
        }
        if (mimeType == null) {
            mimeType = HttpUrlConnectionUtils.JSON_TYPE;
        }
        if (charset == null) {
            charset = HttpUrlConnectionUtils.UTF_8;
        }
        this.content = TiConvert.toJSONString(data).getBytes(charset);
        setContentType(mimeType + HttpUrlConnectionUtils.CHARSET_PARAM + charset);
    }

    public JsonEntity(final Object data, String charset) throws UnsupportedEncodingException {
        this(data, null, charset);
    }

    public JsonEntity(final Object data) throws UnsupportedEncodingException {
        this(data, null);
    }

    public boolean isRepeatable() {
        return true;
    }

    public long getContentLength() {
        return this.content.length;
    }

    public InputStream getContent() throws IOException {
        return new ByteArrayInputStream(this.content);
    }

    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        outstream.write(this.content);
        outstream.flush();
    }
    
}
