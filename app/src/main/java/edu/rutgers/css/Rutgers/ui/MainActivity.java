package edu.rutgers.css.Rutgers.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.androidquery.callback.AjaxStatus;
import com.androidquery.util.AQUtility;

import org.apache.commons.lang3.StringUtils;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.android.AndroidDeferredManager;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import edu.rutgers.css.Rutgers.R;
import edu.rutgers.css.Rutgers.api.Analytics;
import edu.rutgers.css.Rutgers.api.ChannelManager;
import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.api.Request;
import edu.rutgers.css.Rutgers.interfaces.ChannelManagerProvider;
import edu.rutgers.css.Rutgers.model.Channel;
import edu.rutgers.css.Rutgers.model.rmenu.RMenuAdapter;
import edu.rutgers.css.Rutgers.model.rmenu.RMenuItemRow;
import edu.rutgers.css.Rutgers.model.rmenu.RMenuRow;
import edu.rutgers.css.Rutgers.ui.fragments.AboutDisplay;
import edu.rutgers.css.Rutgers.ui.fragments.MainScreen;
import edu.rutgers.css.Rutgers.ui.fragments.WebDisplay;
import edu.rutgers.css.Rutgers.utils.AppUtils;
import edu.rutgers.css.Rutgers.utils.ImageUtils;
import edu.rutgers.css.Rutgers.utils.PrefUtils;
import edu.rutgers.css.Rutgers.utils.RutgersUtils;

/**
 * RU Mobile main activity
 */
public class MainActivity extends LocationProviderActivity implements
        ChannelManagerProvider {

    /** Log tag */
    private static final String TAG = "MainActivity";

    /* Member data */
    private ChannelManager mChannelManager;
    private ActionBarDrawerToggle mDrawerToggle;
    private RMenuAdapter mDrawerAdapter;

    /* View references */
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerListView;

    @Override
    public ChannelManager getChannelManager() {
        return mChannelManager;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // This will create the UUID if one does not yet exist
        Log.d(TAG, "UUID: " + AppUtils.getUUID(this));

        // Start Component Factory
        ComponentFactory.getInstance().setMainActivity(this);

        /*
        if (BuildConfig.DEBUG) {
            getSupportFragmentManager().enableDebugLogging(true);
        }
        */

        // Set default settings the first time the app is run
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);

        // Check if this is the first time the app is being launched
        if (PrefUtils.isFirstLaunch(this)) {
            Log.i(TAG, "First launch");

            // First launch, create analytics event & show settings screen
            Analytics.queueEvent(this, Analytics.NEW_INSTALL, null);

            /*
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            */

            PrefUtils.markFirstLaunch(this);
        }

        // Set up channel manager
        mChannelManager = new ChannelManager();

        // Enable drawer icon
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }

        // Set up nav drawer
        ArrayList<RMenuRow> menuArray = new ArrayList<>();
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
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.LEFT);
        
        mDrawerListView.setAdapter(mDrawerAdapter);
        mDrawerListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RMenuRow clickedRow = (RMenuRow) parent.getAdapter().getItem(position);
                if (!(clickedRow instanceof RMenuItemRow)) return;

                Bundle clickedArgs = ((RMenuItemRow) clickedRow).getArgs();
                clickedArgs.putBoolean("topLevel", true); // This is a top level menu press
                
                // Launch component
                ComponentFactory.getInstance().switchFragments(clickedArgs);
                
                //mDrawerAdapter.setSelectedPos(position);
                mDrawerListView.invalidateViews();
                mDrawerLayout.closeDrawer(mDrawerListView); // Close menu after a click
            }

        });

        // Load nav drawer items
        loadChannels();
        loadWebShortcuts();
        
        // Set up initial fragment
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() == 0) {
            fm.beginTransaction()
                .replace(R.id.main_content_frame, new MainScreen(), MainScreen.HANDLE)
                .commit();
        }

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Attempt to flush analytics events to server
        Analytics.postEvents(this);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clear AQuery cache on exit
        if (isTaskRoot()) {
            AQUtility.cleanCacheAsync(this);
        }
    }
    
    @Override
    public void onBackPressed() {
        Log.v(TAG, "Back button pressed. Leaving top component: " + AppUtils.topHandle(this));

        // If drawer is open, intercept back press to close drawer
        if (mDrawerLayout.isDrawerOpen(mDrawerListView)) {
            mDrawerLayout.closeDrawer(mDrawerListView);
            return;
        }

        // If web display is active, send back button presses to it for navigating browser history
        if (AppUtils.isOnTop(this, WebDisplay.HANDLE)) {
            Fragment webView = getSupportFragmentManager().findFragmentByTag(WebDisplay.HANDLE);
            if (webView != null && webView.isVisible()) {
                if (((WebDisplay) webView).backPress()) {
                    Log.d(TAG, "Triggered WebView back button");
                    return;
                }
            }
        }

        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        mDrawerToggle.onConfigurationChanged(newConfig);
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
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.action_about:
                ComponentFactory.getInstance().switchFragments(AboutDisplay.createArgs());
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
     * Grab web channel links and add them to the menu.
     */
    private void loadWebShortcuts() {
        AndroidDeferredManager dm = new AndroidDeferredManager();
        dm.when(Request.apiArray("shortcuts.txt", Request.CACHE_ONE_DAY)).done(new DoneCallback<JSONArray>() {

            @Override
            public void onDone(JSONArray shortcutsArray) {
                mChannelManager.loadChannelsFromJSONArray(shortcutsArray, "shortcuts");
                addMenuSection(getString(R.string.drawer_shortcuts), mChannelManager.getChannels("shortcuts"));
            }

        }).fail(new FailCallback<AjaxStatus>() {

            @Override
            public void onFail(AjaxStatus status) {
                Log.e(TAG, "loadWebShortcuts(): " + AppUtils.formatAjaxStatus(status));
            }

        });
        
    }

    /**
     * Add channels to navigation drawer.
     * @param category Section title
     * @param channels Channels to add to nav drawer
     */
    private void addMenuSection(String category, List<Channel> channels) {
        //mDrawerAdapter.add(new RMenuHeaderRow(category))

        final String homeCampus = RutgersUtils.getHomeCampus(this);

        for (Channel channel: channels) {
            Bundle itemArgs = new Bundle();
            itemArgs.putString(ComponentFactory.ARG_TITLE_TAG, channel.getTitle(homeCampus));
            itemArgs.putString(ComponentFactory.ARG_COMPONENT_TAG, channel.getView());

            if (StringUtils.isNotBlank(channel.getApi())) {
                itemArgs.putString(ComponentFactory.ARG_API_TAG, channel.getApi());
            }

            if (StringUtils.isNotBlank(channel.getUrl())) {
                itemArgs.putString(ComponentFactory.ARG_URL_TAG, channel.getUrl());
            }

            if (channel.getData() != null) {
                itemArgs.putString(ComponentFactory.ARG_DATA_TAG, channel.getData().toString());
            }

            RMenuItemRow newSMI = new RMenuItemRow(itemArgs);
            // Try to find icon for this item and set it
            newSMI.setDrawable(ImageUtils.getIcon(getResources(), channel.getHandle()));

            // Add the item to the drawer
            mDrawerAdapter.add(newSMI);
        }
    }

}
