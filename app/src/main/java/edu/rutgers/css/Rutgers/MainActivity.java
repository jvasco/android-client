package edu.rutgers.css.Rutgers;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.androidquery.callback.AjaxStatus;
import com.androidquery.util.AQUtility;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;

import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.android.AndroidDeferredManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import edu.rutgers.css.Rutgers.adapters.RMenuAdapter;
import edu.rutgers.css.Rutgers.api.Analytics;
import edu.rutgers.css.Rutgers.api.ChannelManager;
import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.api.Request;
import edu.rutgers.css.Rutgers.fragments.MainScreen;
import edu.rutgers.css.Rutgers.fragments.WebDisplay;
import edu.rutgers.css.Rutgers.interfaces.ChannelManagerProvider;
import edu.rutgers.css.Rutgers.interfaces.LocationClientProvider;
import edu.rutgers.css.Rutgers.items.RMenuItemRow;
import edu.rutgers.css.Rutgers.items.RMenuRow;
import edu.rutgers.css.Rutgers.utils.AppUtil;
import edu.rutgers.css.Rutgers.utils.LocationUtils;
import edu.rutgers.css.Rutgers.utils.RutgersUtil;
import edu.rutgers.css.Rutgers2.R;
import edu.rutgers.css.Rutgers2.SettingsActivity;

/**
 * RU Mobile main activity
 *
 */
public class MainActivity extends FragmentActivity  implements
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener,
		LocationClientProvider, ChannelManagerProvider {
	
	private static final String TAG = "MainActivity";
	private static final String SC_API = AppUtil.API_BASE + "shortcuts.txt";
    private static final String KEY_PREFS_FIRST_LAUNCH = "first_launch";

    private ChannelManager mChannelManager;
	private LocationClient mLocationClient;
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerListView;
	private ActionBarDrawerToggle mDrawerToggle;
	private RMenuAdapter mDrawerAdapter;
    private PopupWindow mLogoPopup;


    private ArrayList<GooglePlayServicesClient.ConnectionCallbacks> mLocationListeners;
	
	/**
	 * For providing the location client to fragments
	 */
	@Override
	public LocationClient getLocationClient() {
		return mLocationClient;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        // This will create the UUID if one does not yet exist
		Log.d(TAG, "UUID: " + AppUtil.getUUID(this));

        // Start Component Factory
        ComponentFactory.getInstance().setMainActivity(this);

        // This is usually created and populated before onCreate() is called so only initialize if
        // it's still null
        if(mLocationListeners == null) {
            mLocationListeners = new ArrayList<GooglePlayServicesClient.ConnectionCallbacks>(5);
        }

        /*
        if(BuildConfig.DEBUG) {
            getSupportFragmentManager().enableDebugLogging(true);
        }
        */

		/*
		 * Set default settings the first time the app is run
		 */
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);

        // Check if this is the first time the app is being launched
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean(KEY_PREFS_FIRST_LAUNCH, true)) {
            Log.i(TAG, "First launch");

            // First launch, create analytics event & show settings screen
            Analytics.queueEvent(this, Analytics.NEW_INSTALL, null);

            /*
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            */

            prefs.edit().putBoolean(KEY_PREFS_FIRST_LAUNCH, false).commit();
        }

		/*
		 * Connect to Google Play location services
		 */
		mLocationClient = new LocationClient(this, this, this);

        /*
         * Set up channel manager
         */
        mChannelManager = new ChannelManager();

		/*
		 * Set up nav drawer
		 */
		// Enable drawer icon
        if(getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }
        
        ArrayList<RMenuRow> menuArray = new ArrayList<RMenuRow>();
        mDrawerAdapter = new RMenuAdapter(this, R.layout.row_drawer_item, R.layout.row_drawer_header, menuArray);
        mDrawerListView = (ListView) findViewById(R.id.left_drawer);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        
        mDrawerToggle = new ActionBarDrawerToggle(        
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
                ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                //getActionBar().setTitle(mTitle);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                //getActionBar().setTitle(mDrawerTitle);
            }
        };
        
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        
        mDrawerListView.setAdapter(mDrawerAdapter);
        mDrawerListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RMenuRow clickedRow = (RMenuRow) parent.getAdapter().getItem(position);
                if(!(clickedRow instanceof RMenuItemRow)) return;

				Bundle clickedArgs = ((RMenuItemRow) clickedRow).getArgs();
				clickedArgs.putBoolean("topLevel", true); // This is a top level menu press
				
				// Launch component
				ComponentFactory.getInstance().switchFragments(clickedArgs);
				
				//mDrawerAdapter.setSelectedPos(position);
				mDrawerListView.invalidateViews();
				mDrawerLayout.closeDrawer(mDrawerListView); // Close menu after a click
			}

        });
        
        /*
         * Load nav drawer items
         */

        // Set up channel items in drawer
        loadChannels();
        
        // Set up web shortcut items in drawer
        loadWebShortcuts();
        
        /*
         * Set up main screen
         */
		FragmentManager fm = getSupportFragmentManager();
		if(fm.getBackStackEntryCount() == 0) {
			fm.beginTransaction()
				.replace(R.id.main_content_frame, new MainScreen(), "mainfrag")
				.commit();
		}

	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		// Connect to location services when activity becomes visible
        for(GooglePlayServicesClient.ConnectionCallbacks listener: mLocationListeners) {
            mLocationClient.registerConnectionCallbacks(listener);
        }
		mLocationClient.connect();

        showLogoOverlay(R.id.main_content_frame);
	}

	@Override
	protected void onStop() {
        super.onStop();

		// Disconnect from location services when activity is no longer visible
        for(GooglePlayServicesClient.ConnectionCallbacks listener: mLocationListeners) {
            mLocationClient.unregisterConnectionCallbacks(listener);
        }
		mLocationClient.disconnect();

        dismissLogoOverlay();

        // Attempt to flush analytics events to server
        Analytics.postEvents(this);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		// Clear AQuery cache on exit
		if(isTaskRoot()) {
			AQUtility.cleanCacheAsync(this);
		}
	}
	
	@Override
	public void onBackPressed() {
        Log.v(TAG, "Back button pressed. Leaving top component: " + ComponentFactory.getInstance().getTopHandle());

        // If drawer is open, intercept back press to close drawer
        if(mDrawerLayout.isDrawerOpen(mDrawerListView)) {
            mDrawerLayout.closeDrawer(mDrawerListView);
            return;
        }

		// If web display is active, send back button presses to it for navigating browser history
        if(WebDisplay.HANDLE.equalsIgnoreCase(ComponentFactory.getInstance().getTopHandle())) {
            Fragment webView = getSupportFragmentManager().findFragmentByTag(WebDisplay.HANDLE);
            if (webView != null && webView.isVisible()) {
                if(((WebDisplay) webView).backPress()) {
                    Log.d(TAG, "Triggered WebView back button");
                    return;
                }
            }
        }

        // Default back press behavior (go back in fragments, etc.)
        ComponentFactory.getInstance().popHandleStack();
        super.onBackPressed();
	}
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    	super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // See if ActionBarDrawerToggle will handle event
    	if (mDrawerToggle.onOptionsItemSelected(item)) {
        	return true;
        }
        
    	// Handle event here or pass it on
        switch(item.getItemId()) {
        
        	// Start the Settings activity
	        case R.id.action_settings:
	        	Intent intent = new Intent(this, SettingsActivity.class);
	        	startActivity(intent);
	        	return true;
	        
	        default:
	        	return super.onOptionsItemSelected(item);
	        	
        }
        
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    /**
     * Register a child fragment with the main activity's location client.
     * @param listener Fragment that uses the location client.
     */
    @Override
    public void registerListener(GooglePlayServicesClient.ConnectionCallbacks listener) {
        if(mLocationListeners == null) {
            mLocationListeners = new ArrayList<GooglePlayServicesClient.ConnectionCallbacks>(5);
        }

        mLocationListeners.add(listener);

        if(mLocationClient != null) {
            mLocationClient.registerConnectionCallbacks(listener);
            Log.d(TAG, "Registered location listener: " + listener.toString());
        } else {
            Log.w(TAG, "Failed to register listener: " + listener.toString());
        }
    }

    /**
     * Unregister a child fragment from the main activity's location client.
     * @param listener Play services Connection Callbacks listener
     */
    @Override
    public void unregisterListener(GooglePlayServicesClient.ConnectionCallbacks listener) {
        if(mLocationListeners != null) {
            mLocationListeners.remove(listener);
        }
        if(mLocationClient != null) {
            mLocationClient.unregisterConnectionCallbacks(listener);
            Log.d(TAG, "Unregistered location listener: " + listener.toString());
        }
    }

    /**
     * Play services connected
     * @param connectionHint Bundle of data provided to clients by Google Play services. May be null if no content is provided by the service.
     */
	@Override
	public void onConnected(Bundle connectionHint) {
		Log.i(TAG, "Connected to Google Play services.");
	}

    /**
     * Play services disconnected
     */
	@Override
	public void onDisconnected() {
		Log.i(TAG, "Disconnected from Google Play services");
	}
	
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.w(TAG, "Attempting to resolve Play Services connection failure");
		if(connectionResult.hasResolution()) {
			try {
				connectionResult.startResolutionForResult(this, LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);
			} catch (SendIntentException e) {
				Log.e(AppUtil.APPTAG, Log.getStackTraceString(e));
			}
		}
		else {
            LocationUtils.showErrorDialog(this, connectionResult.getErrorCode());
		}
	}

    /**
     * Handle results from Google Play Services
     */
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // Choose what to do based on the request code
        switch (requestCode) {

            // If the request code matches the code sent in onConnectionFailed
            case LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST :
                switch (resultCode) {
                    // If Google Play services resolved the problem
                    case Activity.RESULT_OK:
                        Log.w(AppUtil.APPTAG, "Connection failure resolved by Google Play");
                        break;

                    // If any other result was returned by Google Play services
                    default:
                        Log.w(AppUtil.APPTAG, "Connection failure not resolved by Google Play ("+resultCode+")");
                        break;
                }
                break;

            // If any other request code was received
            default:
               // Report that this Activity received an unknown requestCode
               Log.w(AppUtil.APPTAG, "Unknown request code: " + requestCode);
               break;
        }
    }

    /**
     * Check if Google Play Services are connected.
     * @return True if connected, false if not.
     */
    @Override
    public boolean servicesConnected() {
        return LocationUtils.servicesConnected(this);
    }

	/*
	 * Nav drawer helpers
	 */
	
	/**
	 * Add native channel items to the menu.
	 */
	private void loadChannels() {
        mChannelManager.loadChannelsFromResource(getResources(), R.raw.channels);
        addMenuSection(getString(R.string.drawer_channels), mChannelManager.getChannels("main"));
	}
	
	/**
	 * Grab web links and add them to the menu.
	 */
	private void loadWebShortcuts() {
        AndroidDeferredManager dm = new AndroidDeferredManager();
		dm.when(Request.jsonArray(SC_API, Request.CACHE_ONE_DAY)).done(new DoneCallback<JSONArray>() {

            @Override
            public void onDone(JSONArray shortcutsArray) {
                mChannelManager.loadChannelsFromJSONArray(shortcutsArray, "shortcuts");
                addMenuSection(getString(R.string.drawer_shortcuts), mChannelManager.getChannels("shortcuts"));
            }

        }).fail(new FailCallback<AjaxStatus>() {

            @Override
            public void onFail(AjaxStatus status) {
                Log.e(TAG, "loadWebShortcuts(): " + AppUtil.formatAjaxStatus(status));
            }

        });
		
	}

    /**
     * Create section header and load menu items from JSON.
     * @param category Section title
     * @param items Menu items JSON
     */
    private void addMenuSection(String category, JSONArray items) {
        //mDrawerAdapter.add(new RMenuHeaderRow(category));
        for(int i = 0; i < items.length(); i++) {
            try {
                // Create menu item
                JSONObject cur = items.getJSONObject(i);
                Bundle itemArgs = new Bundle();

                // Set title - may be a multi-title object
                itemArgs.putString("title", RutgersUtil.getLocalTitle(this, cur.get("title")));

                // Set component to launch. Default to WWW for web shortcuts
                if(cur.isNull("view") && !cur.isNull("url")) {
                    itemArgs.putString("component", WebDisplay.HANDLE);
                }
                else {
                    itemArgs.putString("component", cur.getString("view"));
                }

                // Set URL if available
                if(!cur.isNull("url")) itemArgs.putString("url", cur.getString("url"));

                // Set API if available
                if(!cur.isNull("api")) itemArgs.putString("api", cur.getString("api"));

                // Set data (JSON Array) if available
                if(cur.optJSONArray("data") != null) itemArgs.putString("data", cur.getJSONArray("data").toString());

                RMenuItemRow newSMI = new RMenuItemRow(itemArgs);
                // Try to find icon for this item and set it
                if(!cur.isNull("handle")) {
                    newSMI.setDrawable(AppUtil.getIcon(getResources(), cur.getString("handle")));
                }

                // Add the item to the drawer
                mDrawerAdapter.add(newSMI);
            } catch (JSONException e) {
                Log.w(TAG, "loadChannels(): " + e.getMessage());
            }
        }
    }

    @Override
    public ChannelManager getChannelManager() {
        return mChannelManager;
    }

    /**
     * Hack to display special logo over the action bar icon.
     * @param rootLayoutId Resource ID of the root layout for the main activity
     */
    private void showLogoOverlay(int rootLayoutId) {
        // Get a layout that just contains the logo to display
        final View logo = getLayoutInflater().inflate(R.layout.logo, null, false);
        logo.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // Get the the action bar, home button, and title text views
        final View home = findViewById(android.R.id.home);
        final View decorView = getWindow().getDecorView();
        final View actionBarView = decorView.findViewById(getResources().getIdentifier("action_bar_container", "id", "android"));
        final TextView actionBarTitle = (TextView) decorView.findViewById(getResources().getIdentifier("action_bar_title", "id", "android"));

        // Post an event so that after the layouts are drawn, a popup containing the logo displays
        // over the normal action bar icon. Then add padding to the title so that it's not obscured
        // by the logo popup.
        findViewById(rootLayoutId).post(new Runnable() {
            public void run() {
                int side = actionBarView.getHeight();
                mLogoPopup = new PopupWindow(logo, side+16, side+16, false);
                mLogoPopup.showAsDropDown(actionBarView, (int) home.getX(), -side);
                mLogoPopup.setTouchInterceptor(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        // Clicking the logo toggles drawer
                        if(mDrawerLayout.isDrawerOpen(mDrawerListView)) {
                            mDrawerLayout.closeDrawer(mDrawerListView);
                        } else {
                            mDrawerLayout.openDrawer(Gravity.START);
                        }
                        return true;
                    }
                });
                mLogoPopup.setTouchable(true);

                // Move the title text over so it's not obscured by the logo
                int diff = Math.abs(mLogoPopup.getWidth() - home.getWidth());
                actionBarTitle.setPadding(diff, 0, 0, 0);
            }
        });
    }

    /**
     * Dismiss the logo popup.
     */
    private void dismissLogoOverlay() {
        if(mLogoPopup != null) {
            mLogoPopup.dismiss();
            mLogoPopup = null;
        }
    }

}
