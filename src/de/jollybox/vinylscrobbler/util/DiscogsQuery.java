/**
 * Vinyl Scrobbler app by JollyBOX.de
 *
 * Copyright (c) 2011	Thomas Jollans
 * 
 * Refer to the file COPYING for copying permissions.
 */

package de.jollybox.vinylscrobbler.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import de.jollybox.vinylscrobbler.R;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;

public abstract class DiscogsQuery extends AsyncTask<String, Void, JSONObject> {
	
	protected Context mContext;
	protected String mError;
	protected ProgressDialog mProgress;
	protected boolean mUseCache;
	protected Resources res;
	
	public DiscogsQuery(Context context, boolean useCache) {
		mContext = context;
		mError = null;
		mUseCache = useCache;
		res = context.getResources();
	}
	
	public DiscogsQuery(Context context) {
		this(context, true);
	}
	
	protected abstract void errorMessage (String message);
	
	protected static Map<String, JSONObject> cCache = new HashMap<String, JSONObject>();
	protected static Map<String, ReentrantLock> cDownloading = new HashMap<String, ReentrantLock>();

	@Override
	protected void onPreExecute() {
		super.onPreExecute();

		mProgress = ProgressDialog.show(mContext, "", res.getString(R.string.querying_discogs), true);
	}
	
	@Override
	protected JSONObject doInBackground(String... args) {
		String query_string = args[0];
		JSONObject result;
		ReentrantLock lock = null;
		
		// caching magic:
		if (mUseCache) {
			// is it being downloaded atm?
			synchronized (cDownloading) {
				if ((lock = cDownloading.get(query_string)) != null) {
					// wait.
					lock.lock();
					// great!
				}
			}
		
			// check cache.
			synchronized (cCache) {
				if ((result = cCache.get(query_string)) != null) {
					return result;
				}
			}
		}
		
		// not cached. Announce in cDownloading, and fetch.
		synchronized (cDownloading) {
			lock = new ReentrantLock();
			lock.lock();
			cDownloading.put(query_string, lock);
		}
		try { // make sure the lock is unlocked in `finally'
		
			Resources res = mContext.getResources();
			String url = res.getString(R.string.discogs_api_root) + query_string;
	
			HttpUriRequest request = new HttpGet(url);		
		
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(Helper.doRequest(mContext, request), "UTF-8"));
			StringBuilder sb = new StringBuilder();

			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}

			try {
				String json_data = sb.toString();
				result = (JSONObject) (new JSONTokener(json_data)).nextValue();
				synchronized (cCache) {
					cCache.put(query_string, result);
				}
				return result;
			} catch (JSONException json_exc) {
				mError = res.getString(R.string.error_invalid_data);
				return null;
			} catch (ClassCastException cast_exc) {
				mError = res.getString(R.string.error_invalid_data);
				return null;
			}
		} catch (IOException io_exc) {
			mError = res.getString(R.string.error_discogs_io);
			return null;
		} finally {
			lock.unlock();
			cDownloading.remove(query_string);
		}
	}
	
	@Override
	protected void onPostExecute(JSONObject result) {
		super.onPostExecute(result);
		try {
			mProgress.dismiss();
		} catch (Exception exc) {
			// er...
			try {
				mProgress.hide();
			} catch (Exception exc2) {
				// what?!
			}
		}
		
		if (mError != null)
			errorMessage(mError);
		if (result != null)
			onResult(result);
	}
	
	protected void onResult(JSONObject result) {
	}
	
	public static abstract class WithAlertDialog extends DiscogsQuery {
		public WithAlertDialog(Context context) {
			super(context);
		}
		
		public WithAlertDialog(Context context, boolean useCache) {
			super (context, useCache);
		}
		
		@Override
		protected void errorMessage(String message) {
			(new AlertDialog.Builder(mContext))
				.setMessage(message)
				.setNeutralButton(android.R.string.ok, null).show();
		}
	}

}
