package edu.rutgers.css.Rutgers.channels.feedback.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import edu.rutgers.css.Rutgers.Config;
import edu.rutgers.css.Rutgers.api.ChannelManager;
import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.channels.ruinfo.fragments.RUInfoMain;
import edu.rutgers.css.Rutgers.interfaces.ChannelManagerProvider;
import edu.rutgers.css.Rutgers.model.Channel;
import edu.rutgers.css.Rutgers.model.SpinnerAdapterImpl;
import edu.rutgers.css.Rutgers.utils.AppUtils;
import edu.rutgers.css.Rutgers.utils.RutgersUtils;
import edu.rutgers.css.Rutgers2.R;

public class FeedbackMain extends Fragment implements OnItemSelectedListener {

    private static final String TAG = "FeedbackMain";
    public static final String HANDLE = "feedback";
    //private static final String API = AppUtils.API_BASE + "feedback.php";
    private static final String API = "http://sauron.rutgers.edu/~jamchamb/feedback.php"; // TODO Replace
    
    private Spinner mSubjectSpinner;
    private Spinner mChannelSpinner;
    private EditText mMessageEditText;
    private EditText mEmailEditText;
    private SpinnerAdapterImpl<String> mChannelSpinnerAdapter;
    private boolean mLockSend;
    
    private AQuery aq;
    
    public FeedbackMain() {
        // Required empty public constructor
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        
        aq = new AQuery(getActivity().getApplicationContext());
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_feedback_main, container, false);
        Bundle args = getArguments();

        // Set title from JSON
        if(args.getString("title") != null) getActivity().setTitle(args.getString("title"));
        else getActivity().setTitle(R.string.feedback_title);
        
        mLockSend = false;

        mMessageEditText = (EditText) v.findViewById(R.id.messageEditText);
        mEmailEditText = (EditText) v.findViewById(R.id.emailEditText);

        mSubjectSpinner = (Spinner) v.findViewById(R.id.subjectSpinner);
        mSubjectSpinner.setOnItemSelectedListener(this);

        mChannelSpinnerAdapter = new SpinnerAdapterImpl<String>(getActivity(), android.R.layout.simple_spinner_item);
        mChannelSpinner = (Spinner) v.findViewById(R.id.channelSpinner);
        mChannelSpinner.setAdapter(mChannelSpinnerAdapter);

        final String homeCampus = RutgersUtils.getHomeCampus(getActivity());

        ChannelManager channelManager = ((ChannelManagerProvider) getActivity()).getChannelManager();
        for(Channel channel: channelManager.getChannels()) {
            mChannelSpinnerAdapter.add(channel.getTitle(homeCampus));
        }
        
        return v;
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.feedback_menu, menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        
        // Handle send button
        if(item.getItemId() == R.id.action_send) {
            if(!mLockSend) sendFeedback();
            return true;
        }
        
        return false;
    }
        
    /**
     * Submit the feedback
     */
    private void sendFeedback() {
        // Empty message - do nothing
        if(mMessageEditText.getText().toString().trim().isEmpty()) {
            return;
        }
        
        // Build POST request
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("subject", mSubjectSpinner.getSelectedItem());
        params.put("email", mEmailEditText.getText().toString());
        params.put("uuid", AppUtils.getUUID(getActivity().getApplicationContext()) + "@android");
        params.put("message", mMessageEditText.getText().toString().trim());
        params.put("wants_response", !mEmailEditText.getText().toString().isEmpty());
        // Post the selected channel if this is channel feedback
        if(mSubjectSpinner.getSelectedItem().equals(getString(R.string.feedback_channel_feedback))) {
            params.put("channel", mChannelSpinner.getSelectedItem());    
        }
        //params.put("debuglog", "");
        params.put("version", Config.VERSION);
        params.put("osname", Config.OSNAME);
        params.put("betamode", Config.BETAMODE);
        
        // Lock send button until POST request goes through
        mLockSend = true;

        final String feedbackErrorString = getString(R.string.feedback_error);
        final String feedbackSuccessString = getString(R.string.feedback_success);

        aq.ajax(API, params, JSONObject.class, new AjaxCallback<JSONObject>() {
            
            @Override
            public void callback(String url, JSONObject json, AjaxStatus status) {
                // Unlock send button
                mLockSend = false;
                
                // Check the response JSON
                if(json != null) {
                    // Errors - invalid input
                    if(json.optJSONArray("errors") != null) {
                        JSONArray response = json.optJSONArray("errors");
                        Toast.makeText(getActivity(), response.optString(0, feedbackErrorString), Toast.LENGTH_SHORT).show();
                    }
                    // Success - input went through
                    else if(!json.isNull("success")) {
                        String response = json.optString("success", feedbackSuccessString);
                        Toast.makeText(getActivity(), response, Toast.LENGTH_SHORT).show();
                        
                        // Only reset forms after message has gone through
                        resetForm();
                    }
                }
                // Didn't get JSON response
                else {
                    Log.w(TAG, AppUtils.formatAjaxStatus(status));
                    Toast toast = Toast.makeText(getActivity(), R.string.failed_load_short, Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.TOP, 0, 125);
                    toast.show();
                }
                
            }
            
        });

    }
    
    /**
     * Reset the feedback forms.
     */
    private void resetForm() {
        mSubjectSpinner.setSelection(0);
        mChannelSpinner.setSelection(0);
        mEmailEditText.setText("");
        mMessageEditText.setText("");

        // Close soft keyboard
        AppUtils.closeKeyboard(getActivity());
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(parent.getId() == R.id.subjectSpinner) {
            String selection = (String) parent.getItemAtPosition(position);
            
            // Channel feedback allows user to select a specific channel
            if(selection.equals(getString(R.string.feedback_channel_feedback))) {
                mChannelSpinner.setVisibility(View.VISIBLE);
            } else {
                mChannelSpinner.setVisibility(View.GONE);
            }
            
            // "General questions" boots you to RU-info. BURNNNN!!!
            if(selection.equals(getString(R.string.feedback_general))) {
                // Reset selection so that the user can hit back without getting booted right away
                // (this means general questions can never be the default option!)
                parent.setSelection(0);
                
                // Launch RU-info channel
                Bundle args = new Bundle();
                args.putString("component", RUInfoMain.HANDLE);
                ComponentFactory.getInstance().switchFragments(args);
            }

        }
        
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
        
    }
    
}