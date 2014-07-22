package edu.rutgers.css.Rutgers.fragments;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import org.jdeferred.Promise;
import org.jdeferred.android.AndroidDeferredManager;
import org.jdeferred.android.AndroidDoneCallback;
import org.jdeferred.android.AndroidExecutionScope;
import org.jdeferred.android.AndroidFailCallback;
import org.jdeferred.multiple.MultipleResults;
import org.jdeferred.multiple.OneReject;
import org.jdeferred.multiple.OneResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import edu.rutgers.css.Rutgers.SettingsActivity;
import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.api.Nextbus;
import edu.rutgers.css.Rutgers.auxiliary.LocationClientProvider;
import edu.rutgers.css.Rutgers.auxiliary.LocationClientReceiver;
import edu.rutgers.css.Rutgers.auxiliary.RMenuAdapter;
import edu.rutgers.css.Rutgers.auxiliary.RMenuPart;
import edu.rutgers.css.Rutgers.auxiliary.SlideMenuHeader;
import edu.rutgers.css.Rutgers.auxiliary.SlideMenuItem;
import edu.rutgers.css.Rutgers2.R;

public class BusStops extends Fragment implements LocationClientReceiver {

	private static final String TAG = "BusStops";
	private static final int REFRESH_INTERVAL = 60 * 2; // nearby stop refresh interval in seconds
	
	private ListView mListView;
	private RMenuAdapter mAdapter;
	private ArrayList<RMenuPart> mData;
	private LocationClientProvider mLocationClientProvider;
	private int mNearbyRowCount; // Keep track of number of nearby stops displayed
	private Timer mUpdateTimer;
	private Handler mUpdateHandler;
    private String mCurrentCampus;
	
	public BusStops() {
		// Required empty public constructor
	}

    @Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mLocationClientProvider = (LocationClientProvider) activity;
            mLocationClientProvider.registerListener(this);
		} catch(ClassCastException e) {
			mLocationClientProvider = null;
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mNearbyRowCount = 0;
		mData = new ArrayList<RMenuPart>();
		mAdapter = new RMenuAdapter(getActivity(), R.layout.title_row, R.layout.basic_section_header, mData);
		
		// Setup the timer stuff for updating the nearby stops
		mUpdateTimer = new Timer();
		mUpdateHandler = new Handler();
	}
	
	@Override
	public View onCreateView (LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_bus_stops, parent, false);
		
		mListView = (ListView) v.findViewById(R.id.list);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {

			/**
			 * Clicking on one of the stops will launch the bus display in stop mode, which lists routes going through that stop.
			 */
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				SlideMenuItem clickedItem = (SlideMenuItem) parent.getAdapter().getItem(position);
				Bundle clickedArgs = clickedItem.getArgs();
						
				Bundle args = new Bundle();
				args.putString("component", "busdisplay");
				args.putString("mode", "stop");
				args.putString("title", clickedArgs.getString("title"));
				args.putString("agency", clickedArgs.getString("agency"));
				
				ComponentFactory.getInstance().switchFragments(args);
			}
			
		});
		
		return v;
	}
	
	@Override
	public void onResume() {
		super.onResume();

        // Get current campus tag (default to "nb")
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mCurrentCampus = sharedPref.getString(SettingsActivity.KEY_PREF_HOME_CAMPUS,
                getResources().getString(R.string.campus_nb_tag));

        mAdapter.clear();
        mNearbyRowCount = 0;

        // Add "nearby stops" header
        mAdapter.add(new SlideMenuHeader(getResources().getString(R.string.bus_nearby_active_stops_header)));

        // Start the update thread when screen is active
		mUpdateTimer = new Timer();
		mUpdateTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				mUpdateHandler.post(new Runnable() {
					@Override
					public void run() {
						loadNearbyStops(mCurrentCampus);
					}
				});
			}
		}, 0, 1000 * REFRESH_INTERVAL);

        // Load active stops
        final Promise nbActiveStops = Nextbus.getActiveStops("nb");
        final Promise nwkActiveStops = Nextbus.getActiveStops("nwk");

        AndroidDeferredManager dm = new AndroidDeferredManager();
        dm.when(nbActiveStops, nwkActiveStops).done(new AndroidDoneCallback<MultipleResults>() {

            @Override
            public AndroidExecutionScope getExecutionScope() {
                return AndroidExecutionScope.UI;
            }

            @Override
            public void onDone(MultipleResults results) {
                for(OneResult result: results) {
                    if(result.getPromise() == nbActiveStops) loadAgency("nb", getResources().getString(R.string.bus_nb_active_routes_header), (JSONArray) result.getResult());
                    else if(result.getPromise() == nwkActiveStops) loadAgency("nwk", getResources().getString(R.string.bus_nwk_active_routes_header), (JSONArray) result.getResult());
                }
            }

        }).fail(new AndroidFailCallback<OneReject>() {

            @Override
            public AndroidExecutionScope getExecutionScope() {
                return AndroidExecutionScope.UI;
            }

            @Override
            public void onFail(OneReject result) {
                Toast.makeText(getActivity(), R.string.failed_load, Toast.LENGTH_LONG).show();
            }

        });

	}
	
	@Override
	public void onPause() {
		super.onPause();

		// Stop the update thread from running when screen isn't active
		if(mUpdateTimer == null) return;
		
		mUpdateTimer.cancel();
		mUpdateTimer = null;
	}

    @Override
    public void onConnected(Bundle dataBundle) {
        Log.v(TAG, "Received onConnected");

        // Location services reconnected - retry loading nearby stops
        loadNearbyStops(mCurrentCampus);
    }

    @Override
    public void onDisconnected() {

    }

	/**
	 * Populate list with bus stops for agency, with a section header for that agency.
	 * Resulting JSON array looks like this:
	 * 	[{"geoHash":"dr5rb1qnk35gxur","title":"1 Washington Park"},{"geoHash":"dr5pzbvwhvpjst4","title":"225 Warren St"}]
	 * @param agencyTag Agency tag for API request
	 * @param agencyTitle Header title that goes above these stops
	 */
	private void loadAgency(final String agencyTag, final String agencyTitle, final JSONArray data) {
        mAdapter.add(new SlideMenuHeader(agencyTitle));

        if(data == null) {
            mAdapter.add(new SlideMenuItem(getResources().getString(R.string.failed_load_short)));
        }
		else if(data.length() == 0) {
            mAdapter.add(new SlideMenuItem(getResources().getString(R.string.bus_no_active_stops)));
            return;
        }

        // Create an item in the list for each stop from the array
        for(int i = 0; i < data.length(); i++) {
            try {
                JSONObject jsonObj = data.getJSONObject(i);
                Bundle menuBundle = new Bundle();
                menuBundle.putString("title", jsonObj.getString("title"));
                menuBundle.putString("agency", agencyTag);
                SlideMenuItem newMenuItem = new SlideMenuItem(menuBundle);
                mAdapter.add(newMenuItem);
            } catch (JSONException e) {
                Log.e(TAG, "loadAgency(): " + e.getMessage());
            }
        }

	}
	
	/**
	 * Remove rows related to nearby stops
	 */
	private void clearNearbyRows() {
		for(int i = 0; i < mNearbyRowCount; i++) {
			mData.remove(1);
			mAdapter.notifyDataSetChanged();
		}
		mNearbyRowCount = 0;
	}
	
	private void addNearbyRow(int pos, RMenuPart row) {
		mData.add(pos, row);
		mNearbyRowCount++;
		mAdapter.notifyDataSetChanged();
	}
	
	/**
	 * Populate list with active nearby stops for an agency
	 * @param agencyTag Agency tag for API request
	 */
	private void loadNearbyStops(final String agencyTag) {
        final Resources res = getResources();

		// Check for location services
		if(mLocationClientProvider != null && mLocationClientProvider.servicesConnected() && mLocationClientProvider.getLocationClient().isConnected()) {
			// Get last location
			Location lastLoc = mLocationClientProvider.getLocationClient().getLastLocation();
			if(lastLoc == null) {
				Log.w(TAG, "Could not get location");
				clearNearbyRows();
				addNearbyRow(1, new SlideMenuItem(res.getString(R.string.failed_location)));
				return;
			}
			Log.d(TAG, "Current location: " + lastLoc.toString());
			
			// Look up nearby active bus stops
			Nextbus.getActiveStopsByTitleNear(agencyTag, 
					(float) lastLoc.getLatitude(), 
					(float) lastLoc.getLongitude()
					).then(new AndroidDoneCallback<JSONObject>() {

				@Override
				public void onDone(JSONObject activeNearbyStops) {				
					// Clear previous rows
					clearNearbyRows();
					
					// If there aren't any results, put a "no stops nearby" message
					if(activeNearbyStops.length() == 0) {
						addNearbyRow(1, new SlideMenuItem(res.getString(R.string.bus_no_nearby_stops)));
					}
					// If there are new results, add them
					else {
						int j = 1;
						Iterator<String> stopTitleIter = activeNearbyStops.keys();
						while(stopTitleIter.hasNext()) {
							try {
								String curTitle = stopTitleIter.next();
								JSONObject curStop = activeNearbyStops.getJSONObject(curTitle);
															
								Bundle menuBundle = new Bundle();
								menuBundle.putString("title", curTitle);
								menuBundle.putString("agency", agencyTag);
								SlideMenuItem newMenuItem = new SlideMenuItem(menuBundle);
								
								addNearbyRow(j++, newMenuItem);
							} catch(JSONException e) {
								Log.e(TAG, "loadNearbyStops(): " + e.getMessage());
							}
						}
					}
				}
				
				@Override
				public AndroidExecutionScope getExecutionScope() {
					return AndroidExecutionScope.UI;
				}
				
			}).fail(new AndroidFailCallback<Exception>() {
                @Override
                public AndroidExecutionScope getExecutionScope() {
                    return AndroidExecutionScope.UI;
                }

                @Override
                public void onFail(Exception result) {
                    addNearbyRow(1, new SlideMenuItem(res.getString(R.string.failed_load_short)));
                }
            });
		}
		else {
			Log.w(TAG, "Could not get location provider, can't find nearby stops");
            addNearbyRow(1, new SlideMenuItem(res.getString(R.string.failed_location)));
		}

	}
	
}
