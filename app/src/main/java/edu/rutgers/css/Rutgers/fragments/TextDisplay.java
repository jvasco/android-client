package edu.rutgers.css.Rutgers.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import edu.rutgers.css.Rutgers2.R;

public class TextDisplay extends Fragment {

    private static final String TAG = "TextDisplay";
    public static final String HANDLE = "text";

    public TextDisplay() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_text_display, parent, false);
        Bundle args = getArguments();

        TextView textView = (TextView) v.findViewById(R.id.text);

        if(args.getString("title") != null) getActivity().setTitle(args.getString("title"));

        if(args.getString("data") == null) {
            Log.w(TAG, "No text set");
            textView.setText("No text set");
        } else {
            textView.setText(Html.fromHtml(args.getString("data")));
        }

        return v;
    }
}