package edu.rutgers.css.Rutgers.channels.bus.fragments;

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

import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.jdeferred.android.AndroidDeferredManager;
import org.jdeferred.multiple.MultipleResults;
import org.jdeferred.multiple.OneReject;
import org.jdeferred.multiple.OneResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.channels.bus.model.Nextbus;
import edu.rutgers.css.Rutgers.model.rmenu.RMenuAdapter;
import edu.rutgers.css.Rutgers.model.rmenu.RMenuHeaderRow;
import edu.rutgers.css.Rutgers.model.rmenu.RMenuItemRow;
import edu.rutgers.css.Rutgers.model.rmenu.RMenuRow;
import edu.rutgers.css.Rutgers.utils.AppUtils;
import edu.rutgers.css.Rutgers.utils.RutgersUtils;
import edu.rutgers.css.Rutgers2.R;


public class BusAll extends Fragment {

    private static final String TAG = "BusAll";
    public static final String HANDLE = "busall";

    private RMenuAdapter mAdapter;
    private ArrayList<RMenuRow> mData;
    private String mFilterString;
    private AndroidDeferredManager mDM;
    
    public BusAll() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDM = new AndroidDeferredManager();

        mData = new ArrayList<RMenuRow>();
        mAdapter = new RMenuAdapter(getActivity(), R.layout.row_title, R.layout.row_section_header, mData);

        // Restore filter
        if(savedInstanceState != null) {
            mFilterString = savedInstanceState.getString("filter");
        }

        // Get home campus for result ordering
        String userHome = RutgersUtils.getHomeCampus(getActivity());
        final boolean nbHome = userHome.equals(getString(R.string.campus_nb_full));

        // Get promises for all route & stop information
        final Promise nbRoutes = Nextbus.getAllRoutes("nb");
        final Promise nbStops = Nextbus.getAllStops("nb");
        final Promise nwkRoutes = Nextbus.getAllRoutes("nwk");
        final Promise nwkStops = Nextbus.getAllStops("nwk");

        final String nbRoutesString = getString(R.string.bus_nb_all_routes_header);
        final String nwkRoutesString = getString(R.string.bus_nwk_all_routes_header);
        final String nbStopsString = getString(R.string.bus_nb_all_stops_header);
        final String nwkStopsString = getString(R.string.bus_nwk_all_stops_header);

        // Synchronized load of all route & stop information
        mDM.when(nbRoutes, nbStops, nwkRoutes, nwkStops).done(new DoneCallback<MultipleResults>() {

            @Override
            public void onDone(MultipleResults results) {
                // Don't do anything if not attached to activity anymore
                if(!isAdded()) return;

                JSONArray nbRoutesResult = null, nbStopsResult = null, nwkRoutesResult = null, nwkStopsResult = null;

                for(OneResult result: results) {
                    if(result.getPromise() == nbRoutes) nbRoutesResult = (JSONArray) result.getResult();
                    else if(result.getPromise() == nwkRoutes) nwkRoutesResult = (JSONArray) result.getResult();
                    else if(result.getPromise() == nbStops) nbStopsResult = (JSONArray) result.getResult();
                    else if(result.getPromise() == nwkStops) nwkStopsResult = (JSONArray) result.getResult();
                }

                if(nbHome) {
                    loadArray("nb", nbRoutesString, "route", nbRoutesResult);
                    loadArray("nb", nbStopsString, "stop", nbStopsResult);
                    loadArray("nwk", nwkRoutesString, "route", nwkRoutesResult);
                    loadArray("nwk", nwkStopsString, "stop", nwkStopsResult);
                } else {
                    loadArray("nwk", nwkRoutesString, "route", nwkRoutesResult);
                    loadArray("nwk", nwkStopsString, "stop", nwkStopsResult);
                    loadArray("nb", nbRoutesString, "route", nbRoutesResult);
                    loadArray("nb", nbStopsString, "stop", nbStopsResult);
                }

                // Set filter after info is re-loaded
                if(mFilterString != null) {
                    mAdapter.getFilter().filter(mFilterString);
                }

            }

        }).fail(new FailCallback<OneReject>() {

            @Override
            public void onFail(OneReject result) {
                AppUtils.showFailedLoadToast(getActivity());
                Exception e = (Exception) result.getReject();
                Log.w(TAG, e.getMessage());
            }

        });
    }
    
    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_bus_all, parent, false);

        // Get the filter field and add a listener to it
        final EditText filterEditText = (EditText) v.findViewById(R.id.filterEditText);
        filterEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Set filter for list adapter
                mFilterString = s.toString().trim();
                mAdapter.getFilter().filter(mFilterString);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

        });
        filterEditText.requestFocus();

        // Get clear button and set listener
        ImageButton filterClearButton = (ImageButton) v.findViewById(R.id.filterClearButton);
        filterClearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                filterEditText.setText("");
            }
        });

        // Set up list to accept clicks on route or stop rows
        ListView listView = (ListView) v.findViewById(R.id.list);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RMenuItemRow clickedItem = (RMenuItemRow) parent.getAdapter().getItem(position);
                Bundle clickedArgs = clickedItem.getArgs();

                try {
                    JSONObject clickedJSON = new JSONObject(clickedArgs.getString("json"));

                    Bundle args = new Bundle();
                    args.putString("component", BusDisplay.HANDLE);
                    args.putString("mode", clickedArgs.getString("mode"));
                    args.putString("agency", clickedArgs.getString("agency"));
                    args.putString("title", clickedJSON.getString("title"));
                    if (clickedArgs.getString("mode").equalsIgnoreCase("route"))
                        args.putString("tag", clickedJSON.getString("tag"));

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
        if(mFilterString != null) outState.putString("filter", mFilterString);
    }

    /**
     * Load array of bus stop or route info into the list.
     * @param agencyTag Agency tag for API request
     * @param agencyHeader Header title that goes above these stops/routes
     * @param mode Specify "route" or "stop" mode
     * @param data JSON array of bus info
     */
    private void loadArray(final String agencyTag, final String agencyHeader, final String mode, final JSONArray data) {
        mAdapter.add(new RMenuHeaderRow(agencyHeader));

        if(data == null) {
            mAdapter.add(new RMenuItemRow(getString(R.string.failed_load_short)));
            return;
        } else if(data.length() == 0) {
            if(mode.equalsIgnoreCase("stop")) mAdapter.add(new RMenuItemRow(getString(R.string.bus_no_configured_stops)));
            else if(mode.equalsIgnoreCase("route")) mAdapter.add(new RMenuItemRow(getString(R.string.bus_no_configured_routes)));
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
            RMenuItemRow newMenuItem = new RMenuItemRow(menuBundle);
            mAdapter.add(newMenuItem);
        } catch (JSONException e) {
            Log.w(TAG, "loadItem(): " + e.getMessage());
        }
    }
    
}
