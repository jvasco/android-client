package edu.rutgers.css.Rutgers.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.rutgers.css.Rutgers2.R;

public class HourSwiperFragment extends Fragment {

	private static final String TAG = "HourSwiperFragment";
	
	public HourSwiperFragment() {
		// Required empty public constructor
	}
	
	public static HourSwiperFragment newInstance(String date, JSONObject hours) {
		HourSwiperFragment newFrag =  new HourSwiperFragment();
		Bundle args = new Bundle();
		args.putString("date", date);
		args.putString("hours", hours.toString());
		newFrag.setArguments(args);
		return newFrag;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.hour_swiper, container, false);
		Bundle args = getArguments();
		
		LinearLayout hourSwiperTableLayout = (LinearLayout) rootView.findViewById(R.id.hourSwiperTableLayout);
		
		// Add hours rows here
		try {
			JSONObject hours = new JSONObject(args.getString("hours"));
			Iterator<String> keys = hours.keys();
			while(keys.hasNext()) {
				String curKey = keys.next();
				TableRow newTR = (TableRow) inflater.inflate(R.layout.hour_row, container, false);

                TextView sublocTextView = (TextView) newTR.findViewById(R.id.sublocTextView);
                sublocTextView.setText(WordUtils.wrap(curKey,18));
                sublocTextView.setMaxLines(2);

                TextView hoursTextView = (TextView) newTR.findViewById(R.id.hoursTextView);

                // Sometimes these are comma separated, sometimes not  ¯\_(ツ)_/¯
                String hoursString = hours.getString(curKey);
                if(StringUtils.startsWithIgnoreCase(hoursString, "closed")) {
                    hoursTextView.setText(hoursString);
                    hoursTextView.setMaxLines(1);
                }
                else if(StringUtils.countMatches(hoursString, ",") > 0) {
                    hoursTextView.setText(hoursString.replace(",", "\n"));
                    hoursTextView.setMaxLines(1 + StringUtils.countMatches(hoursString, ","));
                }
                else {
                    StringBuilder builder = new StringBuilder();
                    int matches = 0;

                    // Here we go...
                    Pattern pattern = Pattern.compile("\\d{1,2}(\\:\\d{2})?\\s?((A|P)M)?\\s?-\\s?\\d{1,2}(\\:\\d{2})?\\s?(A|P)M"); // Why? Because "9:30 - 11:30AM/7 - 10PM"
                    Matcher matcher = pattern.matcher(hoursString);
                    while(matcher.find()) {
                        //Log.v(TAG, "Found " + matcher.group() + " at ("+matcher.start()+","+matcher.end()+")");
                        builder.append(matcher.group() + "\n");
                        matches++;
                    }

                    if(matches > 0) {
                        hoursTextView.setText(StringUtils.chomp(builder.toString()));
                        hoursTextView.setMaxLines(matches);
                    }
                    else {
                        // ಥ_ಥ
                        hoursTextView.setText(hoursString);
                    }
                }

				hourSwiperTableLayout.addView(newTR);
			}
		} catch(JSONException e) {
			Log.w(TAG, "onCreateView(): " + e.getMessage());
		}
		
		return rootView;
	}
	
}
