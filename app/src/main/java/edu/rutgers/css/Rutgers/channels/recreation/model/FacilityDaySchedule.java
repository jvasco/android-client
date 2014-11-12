package edu.rutgers.css.Rutgers.channels.recreation.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/**
 * Facility meeting area hours for a single day. Construct with GSON.
 */
public class FacilityDaySchedule implements Serializable {

    private String date;
    @SerializedName("meeting_area_hours") private List<MeetingAreaHours> areaHours;

    public String getDate() {
        return date;
    }

    public List<MeetingAreaHours> getAreaHours() {
        return areaHours;
    }

}