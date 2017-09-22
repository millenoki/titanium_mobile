/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package org.appcelerator.titanium.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

@SuppressLint("NewApi")
public class TitaniumBlob extends TiBaseFile
{
	protected String url;
	protected String name;
	protected String path;

	public TitaniumBlob(String url) {
		super(TYPE_BLOB);
		this.url = url;
		if (url != null) {
			init();
		}
	}

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access
	 * Framework Documents, as well as the _data field for the MediaStore and
	 * other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @author paulburke
	 */
	public static String getPath(final Context context, final Uri uri) {


	    // DocumentProvider
	    if (TiC.KIT_KAT_OR_GREATER && DocumentsContract.isDocumentUri(context, uri)) {
	        // ExternalStorageProvider
	        if (isExternalStorageDocument(uri)) {
	            final String docId = DocumentsContract.getDocumentId(uri);
	            final String[] split = docId.split(":");
	            final String type = split[0];

	            if ("primary".equalsIgnoreCase(type)) {
	                return Environment.getExternalStorageDirectory() + "/" + split[1];
	            }

	            // TODO handle non-primary volumes
	        }
	        // DownloadsProvider
	        else if (isDownloadsDocument(uri)) {

	            final String id = DocumentsContract.getDocumentId(uri);
	            final Uri contentUri = ContentUris.withAppendedId(
	                    Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

	            return getDataColumn(context, contentUri, null, null);
	        }
	        // MediaProvider
	        else if (isMediaDocument(uri)) {
	            final String docId = DocumentsContract.getDocumentId(uri);
	            final String[] split = docId.split(":");
	            final String type = split[0];

	            Uri contentUri = null;
	            if ("image".equals(type)) {
	                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
	            } else if ("video".equals(type)) {
	                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
	            } else if ("audio".equals(type)) {
	                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
	            }

	            final String selection = "_id=?";
	            final String[] selectionArgs = new String[] {
	                    split[1]
	            };

	            return getDataColumn(context, contentUri, selection, selectionArgs);
	        }
	    }
	    // MediaStore (and general)
	    else if ("content".equalsIgnoreCase(uri.getScheme())) {
	        return getDataColumn(context, uri, null, null);
	    }
	    // File
	    else if ("file".equalsIgnoreCase(uri.getScheme())) {
	        return uri.getPath();
	    }

	    return null;
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
	        String[] selectionArgs) {

	    Cursor cursor = null;
	    final String column = "_data";
	    final String[] projection = {
	            column
	    };

	    try {
	        cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
	                null);
	        if (cursor != null && cursor.moveToFirst()) {
	            final int column_index = cursor.getColumnIndexOrThrow(column);
	            return cursor.getString(column_index);
	        }
	        
	    } catch(Exception e) {
            e.printStackTrace();
        }finally {
	        if (cursor != null)
	            cursor.close();
	    }
	    return null;
	}


	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
	    return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri) {
	    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri) {
	    return "com.android.providers.media.documents".equals(uri.getAuthority());
	}
	protected void init() {
		String[] projection = {
			MediaStore.Images.ImageColumns.DISPLAY_NAME,
			MediaStore.Images.ImageColumns.DATA
		};
		Cursor c = null;

		if (url.startsWith("content://com.android.providers.media.documents")) {
			try {
				c = TiApplication.getInstance().getContentResolver().query(Uri.parse(url), null, null, null, null);
				c.moveToFirst();
				String id = c.getString(0);
				id = id.substring(id.lastIndexOf(":") + 1);
				c.close();

				c = TiApplication.getInstance().getContentResolver().query(
					android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					projection, MediaStore.Images.Media._ID + " = ? ", new String[]{id}, null);

				if (c.moveToNext()) {
					name = c.getString(0);
					path = c.getString(1);
				}
			} finally {
				if (c != null) {
					c.close();
				}
			}
		}  else if (url.startsWith("content://com.android.providers.downloads.documents")) {
			try {
				String id = DocumentsContract.getDocumentId(Uri.parse(url));
				Uri uri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
				c = TiApplication.getInstance().getContentResolver().query(uri, projection, null, null, null);

				if (c.moveToNext()) {
					name = c.getString(0);
					path = c.getString(1);
				}
			} finally {
				if (c != null) {
					c.close();
				}
			}
		} else {
			try {
				c = TiApplication.getInstance().getContentResolver().query(Uri.parse(url), projection, null, null, null);

				if (c.moveToNext()) {
					name = c.getString(0);
					path = c.getString(1);
				}
			} finally {
				if (c != null) {
					c.close();
				}
			}
		}
	}

	public void setUrl(String url) {
		this.url = url;
		if (url != null) {
			init();
		}
	}

	@Override
	public String nativePath() {
		return url;
	}

	public String toURL() {
		return url;
	}

	@Override
	public String name() {
		return name;
	}

	public File getFile() {
		return new File(path);
	}

	public String getContentType() {
		return TiApplication.getInstance().getContentResolver().getType(Uri.parse(url));
	}

	public InputStream getInputStream()
		throws IOException
	{
		return TiApplication.getInstance().getContentResolver().openInputStream(Uri.parse(url));
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return null;
	}

	@Override
	public File getNativeFile() {
		return new File(path);
	}

	public String getNativePath() {
		return path;
	}
}
