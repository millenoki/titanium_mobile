/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.util.KrollAssetHelper;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.util.TiFileHelper2;

import android.content.Context;

public class TiResourceFile extends TiBaseFile
{
	private static final String TAG = "TiResourceFile";

	private String path;
	private boolean statsFetched = false;
	private boolean exists = false;

	public TiResourceFile(String path)
	{
		super(TYPE_RESOURCE);
		this.path = path;
	}

	@Override
	public boolean isDirectory()
	{
		if (statsFetched) {
			return this.exists && this.typeDir;
		}

		fetchStats();
		return this.exists && this.typeDir;
	}

	@Override
	public boolean isFile()
	{
		if (statsFetched) {
			return this.exists && this.typeFile;
		}

		fetchStats();
		return this.exists && this.typeFile;
	}

	@Override
	public TiBaseFile resolve()
	{
		return this;
	}

	@Override
	public InputStream getInputStream() throws IOException
	{
		Context context = TiApplication.getInstance();
		if (context != null) {
			String p = TiFileHelper2.joinSegments("Resources", path);
			return context.getAssets().open(p);
		}
		return null;
	}

	@Override
	public OutputStream getOutputStream() {
		return null; // read-only;
	}

	@Override
	public File getNativeFile() {
		return new File(toURL());
	}

	@Override
	public void write(Object data, boolean append) throws IOException
	{
		throw new IOException("read only");
	}

	@Override
	public void open(int mode, boolean binary) throws IOException {
		if (mode == MODE_READ) {
			InputStream in = getInputStream();
			if (in != null) {
				if (binary) {
					instream = new BufferedInputStream(in);
				} else {
					inreader = new BufferedReader(new InputStreamReader(in, "utf-8"));
				}
				opened = true;
			} else {
				throw new FileNotFoundException("File does not exist: " + path);
			}
		} else {
			throw new IOException("Resource file may not be written.");
		}
	}

	@Override
	public TiBlob read() throws IOException
	{
		if (KrollAssetHelper.fileExists(path))
			return TiBlob.blobFromObject(KrollAssetHelper.readAsset(path));
		return TiBlob.blobFromObject(this);
	}

	@Override
	public String readLine() throws IOException
	{
		if (!opened) {
			throw new IOException("Must open before calling readLine");
		}
		if (binary) {
			throw new IOException("File opened in binary mode, readLine not available.");
		}

		try {
			return inreader.readLine();
		} catch (IOException e) {
			Log.e(TAG, "Error reading a line from the file: ", e);
		}

		return null;
	}

	@Override
	public boolean exists()
	{
		if (statsFetched) {
			return this.exists;
		}

		fetchStats();
		return this.exists;
	}

	@Override
	public String name()
	{
		int idx = path.lastIndexOf("/");
		if (idx != -1)
		{
			return path.substring(idx + 1);
		}
		return path;
	}

	@Override
	public String extension()
	{
		if (!isFile()) {
			return null;
		}

		int idx = path.lastIndexOf(".");
		if (idx != -1)
		{
			return path.substring(idx + 1);
		}
		return null;
	}

	@Override
	public String nativePath()
	{
		return toURL();
	}

	@Override
	public long spaceAvailable()
	{
		return 0L;
	}

	public String toURL()
	{
		if (!path.isEmpty() && !path.endsWith("/") && isDirectory()) {
			path += "/";
		}
		return TiC.URL_ANDROID_ASSET_RESOURCES + path;
	}

	public long size()
	{
		if (!isFile()) {
			return 0L;
		}

		InputStream is = null;
		try {
			is = getInputStream();
			return is.available();
		} catch (IOException e) {
			Log.w(TAG, "Error while trying to determine file size: " + e.getMessage(), e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					Log.w(TAG, e.getMessage(), e, Log.DEBUG_MODE);
				}
			}
		}
		return 0L;
	}

	@Override
	public List<String> getDirectoryListing()
	{
		List<String> listing = new ArrayList<String>();
		KrollAssetHelper.getDirectoryListing(path, listing);
		Set<String> names = TiApplication.getTiAssets().list(path);
			if (names != null) {
            listing.addAll(names);
		}

		return listing;
	}

	public String toString()
	{
		return toURL();
	}

	private void fetchStats()
	{
        boolean exists = KrollAssetHelper.fileExists(path) || TiApplication.getTiAssets().exists(path);
		if (exists) {
			this.typeDir = false;
			this.typeFile = true;
			this.exists = true;

		} else {
			this.typeFile = false;

			if (!getDirectoryListing().isEmpty()) {
				this.typeDir = true;
				this.exists = true;

				// does not exist; neither file or directory
			} else {
				this.typeDir = false;
				this.exists = false;
			}
		}
		statsFetched = true;
	}
}
