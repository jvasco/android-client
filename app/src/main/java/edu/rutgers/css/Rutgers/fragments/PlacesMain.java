package edu.rutgers.css.Rutgers.fragments;

import android.app.Activity;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.android.gms.common.GooglePlayServicesClient;

import org.jdeferred.Promise;
import org.jdeferred.android.AndroidAlwaysCallback;
import org.jdeferred.android.AndroidDoneCallback;
import org.jdeferred.android.AndroidExecutionScope;
import org.jdeferred.android.AndroidFailCallback;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import edu.rutgers.css.Rutgers.AppUtil;
import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.api.Places;
import edu.rutgers.css.Rutgers.api.Places.PlaceTuple;
import edu.rutgers.css.Rutgers.auxiliary.LocationClientProvider;
import edu.rutgers.css.Rutgers.auxiliary.RMenuAdapter;
import edu.rutgers.css.Rutgers.auxiliary.RMenuPart;
import edu.rutgers.css.Rutgers.auxiliary.SlideMenuHeader;
import edu.rutgers.css.Rutgers.auxiliary.SlideMenuItem;
import edu.rutgers.css.Rutgers2.R;

/**
 * Main Places component: displays a text field with auto-complete information from places database.
 * User enters a building name and selects from list; selection sent to place display component.
 */
public class PlacesMain extends Fragment implements GooglePlayServicesClient.ConnectionCallbacks {

	private static final String TAG = "PlacesMain";

	private ArrayList<PlaceTuple> mSearchList;
	private ArrayAdapter<PlaceTuple> mSearchAdapter;
	private ArrayList<RMenuPart> mData;
    private RMenuAdapter mAdapter;
    private ListView mListView;
    private ProgressBar mProgressCircle;
    private LocationClientProvider mLocationClientProvider;

	public PlacesMain() {
		// Required empty public constructor
	}

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.i(TAG, "Attaching to activity");
        try {
            mLocationClientProvider = (LocationClientProvider) activity;
            mLocationClientProvider.registerListener(this);
        } catch(ClassCastException e) {
            mLocationClientProvider = null;
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.i(TAG, "Detaching from activity");
        mLocationClientProvider = null;
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mSearchList = new ArrayList<PlaceTuple>();
		mSearchAdapter = new ArrayAdapter<PlaceTuple>(getActivity(), android.R.layout.simple_dropdown_item_1line, mSearchList);

        // Get nearby places & populate nearby places list
        mData = new ArrayList<RMenuPart>();
        mAdapter = new RMenuAdapter(getActivity(), R.layout.title_row, R.layout.basic_section_header, mData);

        // TODO This needs to be cancelled when the screen rotates
        // Populate search list & list of nearby places
		Places.getPlaces().done(new AndroidDoneCallback<JSONObject>() {

			@Override
			public void onDone(JSONObject json) {
				// Grab "all" field and add title from each object inside
                @SuppressWarnings("unchecked")
                Iterator<String> curKey = json.keys();
                while(curKey.hasNext()) {
                    String key = curKey.next();
                    try {
                        JSONObject curBuilding = json.getJSONObject(key);
                        PlaceTuple newPT = new PlaceTuple(key, curBuilding);
                        mSearchAdapter.add(newPT);
                    } catch (JSONException e) {
                        Log.w(TAG, "getPlaces().done: " + e.getMessage());
                    }
                }
                Collections.sort(mSearchList);

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
                AppUtil.showFailedLoadToast(getActivity());
            }

        });

    }
	
	@Override
	public View onCreateView (LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
		final View v = inflater.inflate(R.layout.fragment_places, parent, false);
		Bundle args = getArguments();

		getActivity().setTitle(getResources().getString(R.string.places_title));

        mProgressCircle = (ProgressBar) v.findViewById(R.id.progressCircle);

        mListView = (ListView) v.findViewById(R.id.listView);
        mListView.setAdapter(mAdapter);

		final AutoCompleteTextView autoComp = (AutoCompleteTextView) v.findViewById(R.id.buildingSearchField);
		autoComp.setAdapter(mSearchAdapter);

		// Item selected from auto-complete list
		autoComp.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// Launch Places display fragment
				Bundle args = new Bundle();
				PlaceTuple placeTuple = (PlaceTuple) parent.getAdapter().getItem(position);
				
				args.putString("component", "placesdisplay");
				args.putString("placeKey", placeTuple.getKey());
				args.putString("placeJSON", placeTuple.getPlaceJSON().toString());
				
				ComponentFactory.getInstance().switchFragments(args);
			}
			
		});
		
		// Text placed in field from soft-keyboard/autocomplete (may happen in landscape)
		autoComp.setOnEditorActionListener(new OnEditorActionListener() {

			@Override
			public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
				if(actionId == EditorInfo.IME_ACTION_GO) {
					return true;
				}
				
				return false;
			}
			
		});

        // Clear search bar
        ImageButton clearSearchButton = (ImageButton) v.findViewById(R.id.filterClearButton);
        clearSearchButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                autoComp.setText("");
            }

        });

        // Click listener for nearby places list
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SlideMenuItem clickedItem = (SlideMenuItem) parent.getItemAtPosition(position);
                ComponentFactory.getInstance().switchFragments(clickedItem.getArgs());
            }
        });

        return v;
	}

    @Override
    public void onResume() {
        super.onResume();

        // Reload nearby places
        loadNearbyPlaces();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected to services");

        // When location services are restored, retry loading nearby places.
        // Make sure this isn't called before the activity has been attached
        // or before onCreate() has ran.
        if(mData != null && isAdded()) loadNearbyPlaces();
    }

    @Override
    public void onDisconnected() {

    }

    private void loadNearbyPlaces() {
        final Resources res = getResources();

        // Check for location services
        if(mLocationClientProvider != null && mLocationClientProvider.servicesConnected() && mLocationClientProvider.getLocationClient().isConnected()) {

            // Get last location
            final Location lastLoc = mLocationClientProvider.getLocationClient().getLastLocation();
            if (lastLoc == null) {
                Log.w(TAG, "Couldn't get location");
                mAdapter.clear();
                mAdapter.add(new SlideMenuHeader(res.getString(R.string.places_nearby)));
                mAdapter.add(new SlideMenuItem(res.getString(R.string.failed_location)));
                return;
            }

            showProgressCircle();

            Places.getPlacesNear(lastLoc.getLatitude(), lastLoc.getLongitude()).done(new AndroidDoneCallback<List<PlaceTuple>>() {

                @Override
                public AndroidExecutionScope getExecutionScope() {
                    return AndroidExecutionScope.UI;
                }

                @Override
                public void onDone(List<PlaceTuple> result) {
                    mAdapter.clear();
                    mAdapter.add(new SlideMenuHeader(res.getString(R.string.places_nearby)));

                    if (result.isEmpty())
                        mAdapter.add(new SlideMenuItem(res.getString(R.string.places_none_nearby)));
                    else {
                        for (PlaceTuple placeTuple : result) {
                            Bundle args = new Bundle();
                            args.putString("title", placeTuple.getPlaceJSON().optString("title"));
                            args.putString("component", "placesdisplay");
                            args.putString("placeKey", placeTuple.getKey());
                            args.putString("placeJSON", placeTuple.getPlaceJSON().toString());
                            mAdapter.add(new SlideMenuItem(args));
                        }
                    }

                }

            }).fail(new AndroidFailCallback<Exception>() {

                @Override
                public AndroidExecutionScope getExecutionScope() {
                    return AndroidExecutionScope.UI;
                }

                @Override
                public void onFail(Exception result) {
                    mAdapter.clear();
                    mAdapter.add(new SlideMenuHeader(res.getString(R.string.places_nearby)));
                    mAdapter.add(new SlideMenuItem(getResources().getString(R.string.failed_load_short)));
                }

            }).always(new AndroidAlwaysCallback<List<PlaceTuple>, Exception>() {
                @Override
                public void onAlways(Promise.State state, List<PlaceTuple> resolved, Exception rejected) {
                    hideProgressCircle();
                }

                @Override
                public AndroidExecutionScope getExecutionScope() {
                    return AndroidExecutionScope.UI;
                }
            });

        }
        else {
            Log.w(TAG, "Location services not connected");

            // We're still waiting for location services to connect
            mAdapter.clear();
            mAdapter.add(new SlideMenuHeader(res.getString(R.string.places_nearby)));
            mAdapter.add(new SlideMenuItem(res.getString(R.string.location_connecting)));
        }

    }

    private void showProgressCircle() {
        if(mProgressCircle != null) mProgressCircle.setVisibility(View.VISIBLE);
    }

    private void hideProgressCircle() {
        if(mProgressCircle != null) mProgressCircle.setVisibility(View.GONE);
    }

}
