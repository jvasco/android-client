package edu.rutgers.css.Rutgers.fragments;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
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

import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.api.Nextbus;
import edu.rutgers.css.Rutgers.auxiliary.RMenuAdapter;
import edu.rutgers.css.Rutgers.auxiliary.RMenuPart;
import edu.rutgers.css.Rutgers.auxiliary.SlideMenuHeader;
import edu.rutgers.css.Rutgers.auxiliary.SlideMenuItem;
import edu.rutgers.css.Rutgers2.R;


public class BusAll extends Fragment {

	private static final String TAG = "BusAll";
	
	private ListView mListView;
	private RMenuAdapter mAdapter;
	private ArrayList<RMenuPart> mData;
    private EditText mFilterEditText;
	
	public BusAll() {
		// Required empty public constructor
	}
	
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        final Resources resources = getResources();

		mData = new ArrayList<RMenuPart>();
		mAdapter = new RMenuAdapter(getActivity(), R.layout.title_row, R.layout.basic_section_header, mData);

        // Get promises for all route & stop information
        final Promise nbRoutes = Nextbus.getAllRoutes("nb");
        final Promise nbStops = Nextbus.getAllStops("nb");
        final Promise nwkRoutes = Nextbus.getAllRoutes("nwk");
        final Promise nwkStops = Nextbus.getAllStops("nwk");

        // Synchronized load of all route & stop information
        AndroidDeferredManager dm = new AndroidDeferredManager();
        dm.when(nbRoutes, nbStops, nwkRoutes, nwkStops).done(new AndroidDoneCallback<MultipleResults>() {
            @Override
            public AndroidExecutionScope getExecutionScope() {
                return AndroidExecutionScope.UI;
            }

            @Override
            public void onDone(MultipleResults results) {
                Log.v(TAG, "# Results: " + results.size());

                for(OneResult result: results) {
                    if(result.getPromise() == nbRoutes) loadArray("nb", resources.getString(R.string.bus_nb_all_routes_header), "route", (JSONArray) result.getResult());
                    else if(result.getPromise() == nwkRoutes) loadArray("nwk", resources.getString(R.string.bus_nwk_all_routes_header), "route", (JSONArray)result.getResult());
                    else if(result.getPromise() == nbStops) loadArray("nb", resources.getString(R.string.bus_nb_all_stops_header), "stop", (JSONArray) result.getResult());
                    else if(result.getPromise() == nwkStops) loadArray("nwk", resources.getString(R.string.bus_nwk_all_stops_header), "stop", (JSONArray) result.getResult());
                }

                // Set filter after info is re-loaded
                if(savedInstanceState != null && savedInstanceState.getString("filter") != null) {
                    mAdapter.getFilter().filter(savedInstanceState.getString("filter"));
                }

            }
        }).fail(new AndroidFailCallback<OneReject>() {
            @Override
            public AndroidExecutionScope getExecutionScope() {
                return AndroidExecutionScope.UI;
            }

            @Override
            public void onFail(OneReject result) {
                Exception e = (Exception) result.getReject();
                Log.w(TAG, e.getMessage());
                Toast.makeText(getActivity(), R.string.failed_load, Toast.LENGTH_SHORT).show();
            }
        });
	}
	
	@Override
	public View onCreateView (LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_bus_all, parent, false);

        // Get the filter field and add a listener to it
        mFilterEditText = (EditText) v.findViewById(R.id.filterEditText);
        mFilterEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Set filter for list adapter
                mAdapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

        });

        // Get clear button and set listener
        ImageButton filterClearButton = (ImageButton) v.findViewById(R.id.filterClearButton);
        filterClearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFilterEditText.setText("");
            }
        });

		// Set up list to accept clicks on route or stop rows
		mListView = (ListView) v.findViewById(R.id.list);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				SlideMenuItem clickedItem = (SlideMenuItem) parent.getAdapter().getItem(position);
				Bundle clickedArgs = clickedItem.getArgs();
				
				try {
					JSONObject clickedJSON = new JSONObject(clickedArgs.getString("json"));
				
					Bundle args = new Bundle();
					args.putString("component", "busdisplay");
					args.putString("mode", clickedArgs.getString("mode"));
					args.putString("agency", clickedArgs.getString("agency"));
					args.putString("title", clickedJSON.getString("title"));
					if(clickedArgs.getString("mode").equalsIgnoreCase("route")) args.putString("tag", clickedJSON.getString("tag"));
					
					ComponentFactory.getInstance().switchFragments(args);
				} catch (JSONException e) {
					Log.e(TAG, "onCreateView()" + e.getMessage());
				}
				
			}
			
		});
		
		return v;
	}

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("filter", mFilterEditText.getText().toString());
    }

    /**
     * Load array of bus stop or route info into the list.
     * @param agencyTag Agency tag for API request
     * @param agencyHeader Header title that goes above these stops/routes
     * @param mode Specify "route" or "stop" mode
     * @param data JSON array of bus info
     */
    private void loadArray(final String agencyTag, final String agencyHeader, final String mode, final JSONArray data) {
        mAdapter.add(new SlideMenuHeader(agencyHeader));

        if(data == null) {
            mAdapter.add(new SlideMenuItem(getResources().getString(R.string.failed_load_short)));
            return;
        }
        else if(data.length() == 0) {
            if(mode.equalsIgnoreCase("stop")) mAdapter.add(new SlideMenuItem(getResources().getString(R.string.bus_no_configured_stops)));
            else if(mode.equalsIgnoreCase("route")) mAdapter.add(new SlideMenuItem(getResources().getString(R.string.bus_no_configured_routes)));
            return;
        }

        for(int i = 0; i < data.length(); i++) {
            try {
                loadItem(data.getJSONObject(i), agencyTag, mode);
            } catch (JSONException e) {
                Log.e(TAG, "loadAllStops() " + e.getMessage());
            }
        }
    }

    /**
     * Load a single JSON item into the list
     * @param jsonObj JSON object
     * @param agencyTag Agency tag for API request
     * @param mode Specify "route" or "stop" mode
     */
    private void loadItem(JSONObject jsonObj, String agencyTag, String mode) {
        try {
            Bundle menuBundle = new Bundle();
            menuBundle.putString("title", jsonObj.getString("title"));
            menuBundle.putString("mode", mode);
            menuBundle.putString("json", jsonObj.toString());
            menuBundle.putString("agency", agencyTag);
            SlideMenuItem newMenuItem = new SlideMenuItem(menuBundle);
            mAdapter.add(newMenuItem);
        } catch (JSONException e) {
            Log.w(TAG, "loadItem(): " + e.getMessage());
        }
    }
	
}
