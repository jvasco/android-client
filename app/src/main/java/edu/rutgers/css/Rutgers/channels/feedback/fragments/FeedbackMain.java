package edu.rutgers.css.Rutgers.channels.feedback.fragments;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import edu.rutgers.css.Rutgers.Config;
import edu.rutgers.css.Rutgers.R;
import edu.rutgers.css.Rutgers.api.ChannelManager;
import edu.rutgers.css.Rutgers.api.ComponentFactory;
import edu.rutgers.css.Rutgers.channels.ruinfo.fragments.RUInfoMain;
import edu.rutgers.css.Rutgers.interfaces.ChannelManagerProvider;
import edu.rutgers.css.Rutgers.model.Channel;
import edu.rutgers.css.Rutgers.ui.fragments.BaseChannelFragment;
import edu.rutgers.css.Rutgers.utils.AppUtils;
import edu.rutgers.css.Rutgers.utils.RutgersUtils;

import static edu.rutgers.css.Rutgers.utils.LogUtils.LOGW;

/** Feedback form. */
public class FeedbackMain extends BaseChannelFragment implements OnItemSelectedListener {

    /* Log tag and component handle */
    private static final String TAG = "FeedbackMain";
    public static final String HANDLE = "feedback";

    private static final String API = Config.API_BASE + "feedback.php";
    //private static final String API = "http://sauron.rutgers.edu/~jamchamb/feedback.php"; // TODO Replace

    /* Argument bundle tags */
    private static final String ARG_TITLE_TAG       = ComponentFactory.ARG_TITLE_TAG;

    /* Member data */
    private AQuery mAQ;
    private boolean mLockSend;

    /* View references */
    private Spinner mSubjectSpinner;
    private Spinner mChannelSpinner;
    private TextView mChannelSpinnerText;
    private EditText mMessageEditText;
    private EditText mEmailEditText;

    public FeedbackMain() {
        // Required empty public constructor
    }

    public static Bundle createArgs(@NonNull String title) {
        Bundle bundle = new Bundle();
        bundle.putString(ComponentFactory.ARG_COMPONENT_TAG, FeedbackMain.HANDLE);
        bundle.putString(ARG_TITLE_TAG, title);
        return bundle;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        
        mAQ = new AQuery(getActivity());
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_feedback_main, container, false);
        final Bundle args = getArguments();

        // Set title from JSON
        if (args.getString(ARG_TITLE_TAG) != null) getActivity().setTitle(args.getString(ARG_TITLE_TAG));
        else getActivity().setTitle(R.string.feedback_title);
        
        mLockSend = false;

        mMessageEditText = (EditText) v.findViewById(R.id.messageEditText);
        mEmailEditText = (EditText) v.findViewById(R.id.emailEditText);

        mChannelSpinnerText = (TextView) v.findViewById(R.id.channel_spinner_text);

        mSubjectSpinner = (Spinner) v.findViewById(R.id.subjectSpinner);
        ArrayAdapter<CharSequence> subjectAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.feedback_subjects, R.layout.spinner);
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        mSubjectSpinner.setAdapter(subjectAdapter);
        mSubjectSpinner.setOnItemSelectedListener(this);

        ArrayAdapter<String> channelAdapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_spinner_item);
        channelAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        mChannelSpinner = (Spinner) v.findViewById(R.id.channelSpinner);
        mChannelSpinner.setAdapter(channelAdapter);

        final String homeCampus = RutgersUtils.getHomeCampus(getActivity());

        ChannelManager channelManager = ((ChannelManagerProvider) getActivity()).getChannelManager();
        for (Channel channel: channelManager.getChannels()) {
            channelAdapter.add(channel.getTitle(homeCampus));
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
        if (item.getItemId() == R.id.action_send) {
            if (!mLockSend) sendFeedback();
            return true;
        }
        
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Get rid of view references
        mSubjectSpinner = null;
        mChannelSpinner = null;
        mMessageEditText = null;
        mEmailEditText = null;
    }
        
    /**
     * Submit the feedback
     */
    private void sendFeedback() {
        // Empty message - do nothing
        if (StringUtils.isBlank(mMessageEditText.getText().toString())) {
            return;
        }

        // TODO Validate email address format
        
        // Build POST request
        Map<String, Object> params = new HashMap<>();
        params.put("subject", mSubjectSpinner.getSelectedItem());
        params.put("email", mEmailEditText.getText().toString().trim());
        params.put("uuid", AppUtils.getUUID(getActivity()));
        params.put("message", mMessageEditText.getText().toString().trim());
        params.put("wants_response", StringUtils.isNotBlank(mEmailEditText.getText().toString()));
        // Post the selected channel if this is channel feedback
        if (mSubjectSpinner.getSelectedItem().equals(getString(R.string.feedback_channel_feedback))) {
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

        mAQ.ajax(API, params, JSONObject.class, new AjaxCallback<JSONObject>() {

            @Override
            public void callback(String url, JSONObject json, AjaxStatus status) {
                // Unlock send button
                mLockSend = false;

                // Check the response JSON
                if (json != null) {
                    if (json.optJSONArray("errors") != null) {
                        JSONArray response = json.optJSONArray("errors");
                        Toast.makeText(getActivity(), response.optString(0, feedbackErrorString), Toast.LENGTH_SHORT).show();
                    } else if (!json.isNull("success")) {
                        String response = json.optString("success", feedbackSuccessString);
                        Toast.makeText(getActivity(), response, Toast.LENGTH_SHORT).show();

                        // Only reset forms after message has gone through
                        resetForm();
                    }
                } else {
                    // No response
                    LOGW(TAG, AppUtils.formatAjaxStatus(status));
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
        if (mSubjectSpinner != null) mSubjectSpinner.setSelection(0);
        if (mChannelSpinner != null) mChannelSpinner.setSelection(0);
        if (mEmailEditText != null) mEmailEditText.setText("");
        if (mMessageEditText != null) mMessageEditText.setText("");

        // Close soft keyboard
        AppUtils.closeKeyboard(getActivity());
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.subjectSpinner) {
            String selection = (String) parent.getItemAtPosition(position);
            
            // Channel feedback allows user to select a specific channel
            if (selection.equals(getString(R.string.feedback_channel_feedback))) {
                if (mChannelSpinner != null) mChannelSpinner.setVisibility(View.VISIBLE);
                if (mChannelSpinnerText != null) mChannelSpinnerText.setVisibility(View.VISIBLE);
            } else {
                if (mChannelSpinner != null) mChannelSpinner.setVisibility(View.GONE);
                if (mChannelSpinnerText != null) mChannelSpinnerText.setVisibility(View.GONE);
            }
            
            // "General questions" boots you to RU-info. BURNNNN!!!
            if (selection.equals(getString(R.string.feedback_general))) {
                // Reset selection so that the user can hit back without getting booted right away
                // (this means general questions can never be the default option!)
                parent.setSelection(0);
                
                // Launch RU-info channel
                switchFragments(RUInfoMain.createArgs(null));
            }

        }
        
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
    
}
