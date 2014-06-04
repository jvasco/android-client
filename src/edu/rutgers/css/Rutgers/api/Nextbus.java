package edu.rutgers.css.Rutgers.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jdeferred.Deferred;
import org.jdeferred.DeferredManager;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;
import org.jdeferred.multiple.MultipleResults;
import org.jdeferred.multiple.OneReject;
import org.jdeferred.multiple.OneResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.location.Location;
import android.util.Log;

import com.androidquery.callback.AjaxStatus;
import com.androidquery.util.XmlDom;

import edu.rutgers.css.Rutgers.auxiliary.Prediction;

/**
 * Singleton class that provides access to the Nextbus API. Uses the JSON that nextbusjs generates to create requests
 * against the official Nextbus API.
 */
public class Nextbus {

	private static boolean isSetup = false;
	private static Promise<Object, Object, Object> configured;
	private static final String TAG = "Nextbus";
	
	private static JSONObject mNBConf;
	private static JSONObject mNWKConf;
	private static JSONObject mNBActive;
	private static JSONObject mNWKActive;
	
	private static long activeExpireTime = 1000 * 60 * 10; // active bus data cached ten minutes
	private static long configExpireTime = 1000 * 60 * 60; // config data cached one hour
	
	private static final String BASE_URL = "http://webservices.nextbus.com/service/publicXMLFeed?command=";
	
	public static final float NEARBY_MAX = 300.0f; // Within 300 meters is considered "nearby"
	
	/**
	 * Load JSON data on active buses & the entire bus config.
	 */
	private static void setup () {
		if (!isSetup) {
			isSetup = true;
			
			// This promise is used to notify the other objects that the object has been configured.
			final Deferred<Object, Object, Object> confd = new DeferredObject<Object, Object, Object>();
			configured = confd.promise();
			
			//final Promise promiseNBActive = Request.api("bus/active/nb", activeExpireTime);
			//final Promise promiseNWKActive = Request.api("bus/active/nwk", activeExpireTime);
			//final Promise promiseNBConf = Request.api("bus/config/nb", configExpireTime);
			//final Promise promiseNWKConf = Request.api("bus/config/nwk", configExpireTime);
			
			final Promise promiseNBActive = Request.json("https://rumobile.rutgers.edu/1/nbactivestops.txt", activeExpireTime);
			final Promise promiseNWKActive = Request.json("https://rumobile.rutgers.edu/1/nwkactivestops.txt", activeExpireTime);
			final Promise promiseNBConf = Request.json("https://rumobile.rutgers.edu/1/rutgersrouteconfig.txt", configExpireTime);
			final Promise promiseNWKConf = Request.json("https://rumobile.rutgers.edu/1/rutgers-newarkrouteconfig.txt", configExpireTime);
			
			DeferredManager dm = new DefaultDeferredManager();
			
			dm.when(promiseNBActive, promiseNBConf, promiseNWKActive, promiseNWKConf).done(new DoneCallback<MultipleResults>() {
				@Override
				public void onDone(MultipleResults res) {
					
					try {
					
						for (int i = 0; i < res.size(); i++) {
							OneResult r = res.get(i);
							
							if (r.getPromise() == promiseNBActive) mNBActive = (JSONObject) r.getResult();
							else if (r.getPromise() == promiseNWKActive) mNWKActive = (JSONObject) r.getResult();
							else if (r.getPromise() == promiseNBConf) mNBConf = (JSONObject) r.getResult();
							else if (r.getPromise() == promiseNWKConf) mNWKConf = (JSONObject) r.getResult();
						}
			
						confd.resolve(null);
					} catch (Exception e) {
						Log.e(TAG, Log.getStackTraceString(e));
						confd.reject(e);
					}
				}
			}).fail(new FailCallback<OneReject>() {
				@Override
				public void onFail(OneReject reject) {
					
					AjaxStatus r = (AjaxStatus) reject.getReject();
					
					Log.e(TAG, r.getCode() + ": " + r.getMessage());
				}
			});
		}
	}
	
	/**
	 * Queries the Nextbus API for predictions for every stop in the given route.
	 * @param agency Agency (campus) to get bus data for
	 * @param route Route to get prediction data for
	 * @return Promise for prediction data for each stop on the route
	 */
	public static Promise<ArrayList<Prediction>, Exception, Double> routePredict (final String agency, final String route) {
		final Deferred<ArrayList<Prediction>, Exception, Double> d = new DeferredObject<ArrayList<Prediction>, Exception, Double>();
		setup();
		
		configured.then(new DoneCallback<Object>() {
			public void onDone(Object o) {
				Log.d("Nextbus", "routePredict: " + agency + ", " + route);
				JSONObject conf = agency.equals("nb") ? mNBConf : mNWKConf;
				
				String queryString = BASE_URL + "predictionsForMultiStops&a=" + (agency.equals("nb")? "rutgers" : "rutgers-newark");
				
				try {
				
					JSONObject routeData = conf.getJSONObject("routes").getJSONObject(route);
					JSONArray stops = routeData.getJSONArray("stops");
					for (int i = 0; i < stops.length(); i++) {
						queryString += "&stops=" + route + "|null|" + stops.getString(i);
					}
									
					Request.xml(queryString, -1).done(new DoneCallback<XmlDom>() {
						@Override
						public void onDone(XmlDom xml) {
							ArrayList<Prediction> results = new ArrayList<Prediction>();
							
							List<XmlDom> entries = xml.tags("predictions");

							for (int i = 0; i < entries.size(); i++) {
								XmlDom p = entries.get(i);
								
								Prediction oneresult = new Prediction(p.attr("stopTitle"), p.attr("stopTag"));
								
								List<XmlDom> xmlpredictions = p.tags("prediction");
								
								for (int j = 0; j < xmlpredictions.size(); j++) {
									oneresult.addPrediction(Integer.parseInt(xmlpredictions.get(j).attr("minutes")));
								}
								results.add(oneresult);
							}
							
							d.resolve(results);
						}
					}).fail(new FailCallback<AjaxStatus>() {
						@Override
						public void onFail(AjaxStatus e) {
							d.reject(new Exception(e.toString()));
						}
					});
	
				} catch (Exception e) {
					d.reject(e);
				}

			}
		});
		
		return d.promise();
	}
	
	/**
	 * Queries the Nextbus API for prediction data for every route going through given stop.
	 * @param agency Agency (campus) to get bus data for
	 * @param stop Stop to get prediction data for
	 * @return Promise for prediction data for each route arriving at the stop
	 */
	public static Promise<ArrayList<Prediction>, Exception, Double> stopPredict (final String agency, final String stop) {
		final Deferred<ArrayList<Prediction>, Exception, Double> d = new DeferredObject<ArrayList<Prediction>, Exception, Double>();
		setup();
		
		configured.then(new DoneCallback<Object>() {
			public void onDone(Object o) {
				Log.d("Nextbus", "stopPredict: " + agency + ", " + stop);
				JSONObject conf = agency.equals("nb") ? mNBConf : mNWKConf;
				
				String queryString = BASE_URL + "predictionsForMultiStops&a=" + (agency.equals("nb")? "rutgers" : "rutgers-newark");
				
				try {
				
					JSONObject routeData = conf.getJSONObject("stopsByTitle").getJSONObject(stop);
					JSONArray tags = routeData.getJSONArray("tags");
					for (int i = 0; i < tags.length(); i++) {
						JSONArray routes = conf.getJSONObject("stops").getJSONObject(tags.getString(i)).getJSONArray("routes");
						for (int j = 0; j < routes.length(); j++)
							queryString += "&stops=" + routes.getString(j) + "|null|" + tags.getString(i);
					}
					
					Request.xml(queryString, -1).done(new DoneCallback<XmlDom>() {
						@Override
						public void onDone(XmlDom xml) {
							ArrayList<Prediction> results = new ArrayList<Prediction>();
							
							List<XmlDom> entries = xml.tags("predictions");

							for (int i = 0; i < entries.size(); i++) {
								XmlDom p = entries.get(i);
								
								Prediction oneresult = new Prediction(p.attr("routeTitle"), p.attr("routeTag"));
								
								XmlDom dirtag = p.tag("direction");
								// If there's no dirtag then there are no predictions. Predictions always appear as children of a 'direction' tag
								if (dirtag != null) oneresult.setDirection(dirtag.attr("title"));
								else continue;

								List<XmlDom> xmlpredictions = p.tags("prediction");
								
								for (int j = 0; j < xmlpredictions.size(); j++) {
									oneresult.addPrediction(Integer.parseInt(xmlpredictions.get(j).attr("minutes")));
								}
								results.add(oneresult);
							}
							
							d.resolve(results);
						}
					}).fail(new FailCallback<AjaxStatus>() {
						@Override
						public void onFail(AjaxStatus e) {
							d.reject(new Exception(e.toString()));
						}
					});
	
				} catch (Exception e) {
					d.reject(e);
				}

			}
		});
		
		return d.promise();
	}
	
	/**
	 * Get all routes (preferably active) for given agency (campus).
	 * @param agency Agency (campus) to get routes for
	 * @return If there are active routes, a promise for a JSON array of active routes only. If there no active routes, a promise for a JSON array of all routes found in the agency config.
	 */
	public static Promise<JSONArray, Exception, Double> getRoutes (final String agency) {
		final Deferred<JSONArray, Exception, Double> d = new DeferredObject<JSONArray, Exception, Double>();
		setup();
		
		configured.then(new DoneCallback<Object>() {
			public void onDone(Object o) {
				try {
					JSONObject conf = agency.equals("nb") ? mNBConf : mNWKConf;
					JSONObject active = agency.equals("nb") ? mNBActive : mNWKActive;
					
					JSONArray routes;
					if (active != null) routes = active.getJSONArray("routes");
					else routes = conf.getJSONArray("sortedRoutes");
					
					d.resolve(routes);
				} catch (Exception e) {
					d.reject(e);
				}
			}
		});
		
		return d;
	}

	/**
	 * Get all stops (preferably active) for a given agency (campus).
	 * @param agency Agency (campus) to get stops for
	 * @return If there are active routes, a promise for a JSON array of active stops only. If there are no active routes, a promise for a JSON array of all stops found in the agency config.
	 */
	public static Promise<JSONArray, Exception, Double> getStops (final String agency) {
		final Deferred<JSONArray, Exception, Double> d = new DeferredObject<JSONArray, Exception, Double>();
		setup();
		
		configured.then(new DoneCallback<Object>() {
			@Override
			public void onDone(Object o) {
				try {
					JSONObject conf = agency.equals("nb") ? mNBConf : mNWKConf;
					JSONObject active = agency.equals("nb") ? mNBActive : mNWKActive;
					
					JSONArray stops;
					if (active != null) stops = active.getJSONArray("stops");
					else stops = conf.getJSONArray("sortedStops");
					
					d.resolve(stops);
				} catch (Exception e) {
					d.reject(e);
				}
			}
		});
		
		return d;
	}
	
	/**
	 * Get all bus stops (by title) near a specific location.
	 * @param sourceLat Latitude of location
	 * @param sourceLon Longitude of location
	 * @return stopByTitle JSON objects
	 */
	public static Promise<JSONObject, Exception, Double> getStopsByTitleNear(final String agency, final float sourceLat, final float sourceLon) {
		final Deferred<JSONObject, Exception, Double> d = new DeferredObject<JSONObject, Exception, Double>();
		setup();
		
		configured.then(new DoneCallback<Object>() {
			@Override
			public void onDone(Object o) {
				JSONObject nearStops = new JSONObject();
				
				try {
					JSONObject conf = agency.equals("nb") ? mNBConf : mNWKConf;
					JSONObject stopsByTitle = conf.getJSONObject("stopsByTitle");
					
					// Loop through stop titles
					Iterator<String> confIter = stopsByTitle.keys();
					while(confIter.hasNext()) {
						String curTitle = confIter.next();
						JSONObject curStopByTitle = stopsByTitle.getJSONObject(curTitle);
						JSONArray curStopTags = curStopByTitle.getJSONArray("tags");
						
						// Loop through tags for the stop -- if any are within range, list this stop
						for(int i = 0; i < curStopTags.length(); i++) {
							JSONObject curStop = conf.getJSONObject("stops").getJSONObject(curStopTags.getString(i));
							
							// Get distance between building and stop
							float endLatitude = Float.parseFloat(curStop.getString("lat"));
							float endLongitude = Float.parseFloat(curStop.getString("lon")); 
							float[] results = new float[3];
							Location.distanceBetween(sourceLat, sourceLon, endLatitude, endLongitude, results);
							
							// If the stop is within range, add it to the list
							if(results[0] < NEARBY_MAX) {
								nearStops.put(curTitle, curStopByTitle);
								break; // Skip to next stop title
							}
							
						}
						
					}
					
					d.resolve(nearStops);
				} catch(JSONException e) {
					Log.e(TAG, e.getMessage());
					d.reject(e);
					return;
				}
			}
		});
		
		return d;
	}
	
	public static Promise<JSONObject, Exception, Double> getActiveStopsByTitleNear(final String agency, final float sourceLat, final float sourceLon) {
		final Deferred<JSONObject, Exception, Double> d = new DeferredObject<JSONObject, Exception, Double>();
		Promise<JSONObject, Exception, Double> allNearStops = getStopsByTitleNear(agency, sourceLat, sourceLon);
		
		allNearStops.then(new DoneCallback<JSONObject>() {

			public void onDone(JSONObject stopsByTitle) {
				JSONObject conf = agency.equals("nb") ? mNBConf : mNWKConf;
				JSONObject active = agency.equals("nb") ? mNBActive : mNWKActive;
				
				JSONObject result = new JSONObject();
				
				try {
					JSONArray activeStops = active.getJSONArray("stops");
					
					// Loop through ALL nearby stops returned by earlier call
					Iterator<String> stopTitleIter = stopsByTitle.keys();
					while(stopTitleIter.hasNext()) {
						String curTitle = stopTitleIter.next();
						
						// Check to see if this stop is active
						for(int i = 0; i < activeStops.length(); i++) {
							JSONObject anActiveStop = activeStops.getJSONObject(i);
							if(anActiveStop.getString("title").equals(curTitle)) {
								result.put(curTitle, stopsByTitle.get(curTitle));	
							}
						}
					}
					
				} catch(JSONException e) {
					Log.e(TAG, e.getMessage());
				}
			}
			
		});
		
		return d;
	}
	
	private static JSONObject combineJSONObjs(JSONObject conf1, JSONObject conf2) {
		JSONObject result = new JSONObject();
		ArrayList<JSONObject> confs = new ArrayList<JSONObject>();
		confs.add(conf1);
		confs.add(conf2);
		
		for(JSONObject curConf: confs) {
			Iterator<String> confKeys = curConf.keys();
			while(confKeys.hasNext()) {
				try {
					String curKey = confKeys.next();
					Object curObj = curConf.get(curKey);
					result.put(curKey, curObj);
				} catch(JSONException e) {
					Log.e(TAG, e.getMessage());
					return null;
				}
			}
		}
		
		return result;
	}
}
