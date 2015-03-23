package edu.rutgers.css.Rutgers.channels.reader.fragments;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.androidquery.callback.AjaxStatus;
import com.androidquery.util.XmlDom;
import com.nhaarman.listviewanimations.appearance.simple.AlphaInAnimationAdapter;

import org.jdeferred.AlwaysCallback;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.jdeferred.android.AndroidDeferredManager;

import java.util.ArrayList;
import java.util.List;

import edu.rutgers.css.Rutgers.Config;
import edu.rutgers.css.Rutgers.R;
import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.api.Request;
import edu.rutgers.css.Rutgers.channels.reader.model.RSSAdapter;
import edu.rutgers.css.Rutgers.channels.reader.model.RSSItem;
import edu.rutgers.css.Rutgers.ui.fragments.BaseChannelFragment;
import edu.rutgers.css.Rutgers.ui.fragments.WebDisplay;
import edu.rutgers.css.Rutgers.utils.AppUtils;

import static edu.rutgers.css.Rutgers.utils.LogUtils.*;

/**
 * RSS feed reader
 */
public class RSSReader extends BaseChannelFragment {

    /* Log tag and component handle */
    private static final String TAG                 = "RSSReader";
    public static final String HANDLE               = "reader";

    /* Constants */
    private static final long EXPIRE                = Request.CACHE_ONE_MINUTE;

    /* Argument bundle tags */
    private static final String ARG_TITLE_TAG       = ComponentFactory.ARG_TITLE_TAG;
    private static final String ARG_URL_TAG         = ComponentFactory.ARG_URL_TAG;

    /* Saved instance state tags */
    private static final String SAVED_DATA_TAG      = Config.PACKAGE_NAME + ".reader.data";

    /* Member data */
    private ArrayList<RSSItem> mData;
    private RSSAdapter mAdapter;
    private boolean mLoading;

    public RSSReader() {
        // Required empty public constructor
    }

    /** Create arugment bundle for an RSS feed reader */
    public static Bundle createArgs(@NonNull String title, @NonNull String url) {
        Bundle bundle = new Bundle();
        bundle.putString(ComponentFactory.ARG_COMPONENT_TAG, RSSReader.HANDLE);
        bundle.putString(ARG_TITLE_TAG, title);
        bundle.putString(ARG_URL_TAG, url);
        return bundle;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
        
        mData = new ArrayList<>();
        mAdapter = new RSSAdapter(this.getActivity(), R.layout.row_rss, mData);

        if (savedInstanceState != null && savedInstanceState.getSerializable(SAVED_DATA_TAG) != null) {
            LOGD(TAG, "Restoring mData");
            mAdapter.addAll((ArrayList<RSSItem>) savedInstanceState.getSerializable(SAVED_DATA_TAG));
            return;
        }

        if (args.getString(ARG_URL_TAG) == null) {
            LOGE(TAG, "URL argument not set");
            Toast.makeText(getActivity(), R.string.failed_no_url, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get RSS feed XML and add items through the array adapter
        mLoading = true;
        AndroidDeferredManager dm = new AndroidDeferredManager();
        dm.when(Request.xml(args.getString(ARG_URL_TAG), RSSReader.EXPIRE)).done(new DoneCallback<XmlDom>() {
            
            @Override
            public void onDone(XmlDom xml) {
                List<XmlDom> items = xml.tags("item");
                
                for (XmlDom item: items) {
                    RSSItem newItem = new RSSItem(item);
                    mAdapter.add(newItem);
                }
            }

        }).fail(new FailCallback<AjaxStatus>() {

            @Override
            public void onFail(AjaxStatus e) {
                LOGE(TAG, AppUtils.formatAjaxStatus(e));
                AppUtils.showFailedLoadToast(getActivity());
            }

        }).always(new AlwaysCallback<XmlDom, AjaxStatus>() {
            @Override
            public void onAlways(Promise.State state, XmlDom resolved, AjaxStatus rejected) {
                mLoading = false;
                hideProgressCircle();
            }
        });
    }

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        final View v = super.createView(inflater, parent, savedInstanceState, R.layout.fragment_list_progress);

        if (mLoading) showProgressCircle();

        final Bundle args = getArguments();
        if (args.getString(ARG_TITLE_TAG) != null) {
            getActivity().setTitle(args.getString(ARG_TITLE_TAG));
        }

        final ListView listView = (ListView) v.findViewById(R.id.list);

        final AlphaInAnimationAdapter alphaInAnimationAdapter = new AlphaInAnimationAdapter(mAdapter);
        alphaInAnimationAdapter.setAbsListView(listView);
        assert alphaInAnimationAdapter.getViewAnimator() != null;
        alphaInAnimationAdapter.getViewAnimator().setInitialDelayMillis(100);
        alphaInAnimationAdapter.getViewAnimator().setAnimationDelayMillis(75);
        alphaInAnimationAdapter.getViewAnimator().setAnimationDurationMillis(200);

        listView.setAdapter(alphaInAnimationAdapter);

        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RSSItem item = mAdapter.getItem(position);

                if (item.getLink() != null) {
                    // Open web display fragment
                    switchFragments(WebDisplay.createArgs(args.getString(ARG_TITLE_TAG), item.getLink()));
                }
            }
        });
        
        return v;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mData.isEmpty()) outState.putSerializable(SAVED_DATA_TAG, mData);
    }

}
