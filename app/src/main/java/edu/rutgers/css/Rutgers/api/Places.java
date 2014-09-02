package edu.rutgers.css.Rutgers.api;

import android.location.Location;
import android.util.Log;

import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.android.AndroidDeferredManager;
import org.jdeferred.android.AndroidDoneCallback;
import org.jdeferred.android.AndroidExecutionScope;
import org.jdeferred.android.AndroidFailCallback;
import org.jdeferred.impl.DeferredObject;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import edu.rutgers.css.Rutgers.utils.AppUtil;

/**
 * Helper for getting data from places API.
 * 
 */
public class Places {
	
	private static final String TAG = "PlacesAPI";

	private static Promise<Object, AjaxStatus, Object> configured;
	private static JSONObject mPlacesConf;

    /**
	 * Grab the places API data.
	 */
	private static synchronized void setup() {

		// Get places JSON from server
		final Deferred<Object, AjaxStatus, Object> confd = new DeferredObject<Object, AjaxStatus, Object>();
		configured = confd.promise();
		
		final Promise<AjaxCallback<JSONObject>, AjaxStatus, Double> promisePlaces = Request.apiWithStatus("places.txt", Request.CACHE_ONE_DAY * 7);
		
		AndroidDeferredManager dm = new AndroidDeferredManager();		
		dm.when(promisePlaces).done(new AndroidDoneCallback<AjaxCallback<JSONObject>>() {

			@Override
			public void onDone(AjaxCallback<JSONObject> res) {
                // If the result came from cache, skip new setup
                if(mPlacesConf != null && res.getStatus().getSource() != AjaxStatus.NETWORK) {
                    confd.resolve(null);
                    return;
                }

				try {
                    mPlacesConf = res.getResult().getJSONObject("all");
                    confd.resolve(null);
                } catch (JSONException e) {
                    Log.e(TAG, "setup(): " + e.getMessage());
                    confd.reject(res.getStatus());
                }
			}
			
			@Override
			public AndroidExecutionScope getExecutionScope() {
				return AndroidExecutionScope.BACKGROUND;
			}

		}).fail(new AndroidFailCallback<AjaxStatus>() {

			@Override
			public void onFail(AjaxStatus e) {
				Log.e(TAG, AppUtil.formatAjaxStatus(e));
				confd.reject(e);
			}
			
			@Override
			public AndroidExecutionScope getExecutionScope() {
				return AndroidExecutionScope.BACKGROUND;
			}

		});	
	}
	
	/**
	 * Get the JSON containing all of the place information
	 * @return JSONObject containing "all" field from Places API
	 */
	public static Promise<JSONObject, Exception, Double> getPlaces() {
		final Deferred<JSONObject, Exception, Double> d = new DeferredObject<JSONObject, Exception, Double>();
		setup();
		
		configured.then(new AndroidDoneCallback<Object>() {
			
			@Override
			public void onDone(Object o) {
				d.resolve(mPlacesConf);
			}
			
			@Override
			public AndroidExecutionScope getExecutionScope() {
				return AndroidExecutionScope.BACKGROUND;
			}
			
		}).fail(new AndroidFailCallback<AjaxStatus>() {
            @Override
            public AndroidExecutionScope getExecutionScope() {
                return AndroidExecutionScope.BACKGROUND;
            }

            @Override
            public void onFail(AjaxStatus status) {
                d.reject(new Exception(AppUtil.formatAjaxStatus(status)));
            }
        });
		
		return d.promise();
	}
	
	/**
	 * Get JSON for a specific place.
	 * @param placeKey Place key (NOT title)
	 * @return JSON for place
	 */
	public static Promise<JSONObject, Exception, Double> getPlace(final String placeKey) {
		final Deferred<JSONObject, Exception, Double> d = new DeferredObject<JSONObject, Exception, Double>();
		setup();
		
		configured.then(new AndroidDoneCallback<Object>() {
			
			@Override
			public void onDone(Object o) {
				try {
					JSONObject place = mPlacesConf.getJSONObject(placeKey);
					d.resolve(place);
				} catch (JSONException e) {
					Log.w(TAG, "getPlace(): " + e.getMessage());
					d.reject(e);
				}
			}
			
			@Override
			public AndroidExecutionScope getExecutionScope() {
				return AndroidExecutionScope.BACKGROUND;
			}
			
		}).fail(new AndroidFailCallback<AjaxStatus>() {
            @Override
            public AndroidExecutionScope getExecutionScope() {
                return AndroidExecutionScope.BACKGROUND;
            }

            @Override
            public void onFail(AjaxStatus status) {
                d.reject(new Exception(AppUtil.formatAjaxStatus(status)));
            }
        });
		
		return d.promise();
	}

    /**
     * Get places near a given location.
     * @param sourceLat Latitude
     * @param sourceLon Longitude
     * @return Promise for a list of place keys & JSON objects.
     */
    public static Promise<List<PlaceTuple>, Exception, Double> getPlacesNear(final double sourceLat, final double sourceLon) {
        final Deferred<List<PlaceTuple>, Exception, Double> d = new DeferredObject<List<PlaceTuple>, Exception, Double>();
        setup();

        configured.then(new AndroidDoneCallback<Object>() {

            @Override
            public void onDone(Object o) {
                try {
                    JSONObject allPlaces = mPlacesConf;

                    List<PlaceTuple> result = new ArrayList<PlaceTuple>();

                    Iterator<String> placesIter = allPlaces.keys();
                    while(placesIter.hasNext()) {
                        String curPlaceKey = placesIter.next();

                        JSONObject curPlace = allPlaces.getJSONObject(curPlaceKey);
                        if(curPlace.has("location")) {
                            JSONObject curPlaceLocation = curPlace.getJSONObject("location");
                            double placeLongitude = Double.parseDouble(curPlaceLocation.getString("longitude"));
                            double placeLatitude = Double.parseDouble(curPlaceLocation.getString("latitude"));

                            float[] results = new float[1];
                            Location.distanceBetween(sourceLat, sourceLon, placeLatitude, placeLongitude, results);

                            // If the place is within range, add it to the list
                            if (results[0] < AppUtil.NEARBY_RANGE) {
                                //Log.v(TAG, "Found nearby place " + curPlaceKey);
                                curPlace.put("distance", ""+results[0]);
                                result.add(new PlaceTuple(curPlaceKey, curPlace));
                            }
                        }
                    }

                    // Sort by distance
                    Collections.sort(result, new PTDistanceComparator());
                    d.resolve(result);
                }
                catch(JSONException e) {
                    Log.w(TAG, "getPlacesNear(): " + e.getMessage());
                    d.reject(e);
                }

            }

            @Override
            public AndroidExecutionScope getExecutionScope() {
                return AndroidExecutionScope.BACKGROUND;
            }

        }).fail(new AndroidFailCallback<AjaxStatus>() {
            @Override
            public AndroidExecutionScope getExecutionScope() {
                return AndroidExecutionScope.BACKGROUND;
            }

            @Override
            public void onFail(AjaxStatus status) {
                d.reject(new Exception(AppUtil.formatAjaxStatus(status)));
            }
        });

        return d.promise();
    }

    public static class PlaceTuple implements Comparable<PlaceTuple> {
        private String key;
        private JSONObject placeJson;

        public PlaceTuple(String key, JSONObject placeJson) {
            this.key = key;
            this.placeJson = placeJson;
        }

        public String getKey() {
            return this.key;
        }

        public JSONObject getPlaceJSON() {
            return this.placeJson;
        }

        @Override
        public String toString() {
            try {
                return placeJson.getString("title");
            } catch (JSONException e) {
                Log.w(TAG, "toString(): " + e.getMessage());
                return key;
            }
        }

        @Override
        public int compareTo(PlaceTuple another) {
            // Order by 'title' field alphabetically (or by key if getting title string fails)
            try {
                String thisTitle = getPlaceJSON().getString("title");
                String thatTitle = another.getPlaceJSON().getString("title");
                return thisTitle.compareTo(thatTitle);
            } catch (JSONException e) {
                Log.w(TAG, "compareTo(): " + e.getMessage());
                return getKey().compareTo(another.getKey());
            }
        }
    }

    private static class PTDistanceComparator implements Comparator<PlaceTuple> {
        @Override
        public int compare(PlaceTuple pt1, PlaceTuple pt2) {
            String distString1 = pt1.getPlaceJSON().optString("distance");
            String distString2 = pt2.getPlaceJSON().optString("distance");

            Float dist1 = Float.parseFloat(distString1);
            Float dist2 = Float.parseFloat(distString2);

            return dist1.compareTo(dist2);
        }
    }

}
