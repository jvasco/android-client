package edu.rutgers.css.Rutgers.channels.soc.model;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.rutgers.css.Rutgers.utils.JsonUtils;

/**
 * SOC Index.
 */
public class SOCIndex {

    private static final String TAG = "SOCIndex";

    private static class IndexCourse {
        String course;
        String subj;
    }

    private static class IndexSubject {
        String id;
        String name;
        HashMap<String, String> courses;
    }

    private String semesterCode;
    private String campusCode;
    private String levelCode;

    private HashMap<String, String[]> mAbbreviations;
    private HashMap<String, IndexCourse> mCoursesByName;
    private HashMap<String, IndexSubject> mSubjectsByCode;
    private HashMap<String, String> mSubjectsByName;

    public SOCIndex(String campusCode, String levelCode, String semesterCode, JSONObject index) throws IllegalArgumentException {
        if (index.isNull("abbrevs") || index.isNull("courses") || index.isNull("ids") || index.isNull("names")) {
            throw new IllegalArgumentException("Invalid index, missing critical fields");
        }

        setSemesterCode(semesterCode);
        setCampusCode(campusCode);
        setLevelCode(levelCode);

        // Convert the JSON into native hashtables
        try {
            JSONObject abbrevs = index.getJSONObject("abbrevs"); // List of subject abbrevs->sub IDs
            JSONObject ids = index.getJSONObject("ids"); // List of subject IDs->contained courses
            JSONObject names = index.getJSONObject("names"); // List of subject names->sub IDs
            JSONObject courses = index.getJSONObject("courses"); // List of course names->sub/course IDs

            // Set up abbreviations hashtable
            mAbbreviations = new HashMap<>();
            for (Iterator<String> abbrevsIterator = abbrevs.keys(); abbrevsIterator.hasNext();) {
                String curAbbrev = abbrevsIterator.next();
                JSONArray curContents = abbrevs.getJSONArray(curAbbrev);
                String[] subIDStrings = JsonUtils.jsonToStringArray(curContents);
                mAbbreviations.put(curAbbrev, subIDStrings);
            }

            // Set up subject IDs hashtable
            mSubjectsByCode = new HashMap<>();
            for (Iterator<String> idsIterator = ids.keys(); idsIterator.hasNext();) {
                String curID = idsIterator.next();
                JSONObject curContents = ids.getJSONObject(curID);

                // Set up the list of CourseID:CourseName mappings for this Subject ID entry
                JSONObject curCourses = curContents.getJSONObject("courses");
                HashMap<String, String> courseMap = new HashMap<>();
                for (Iterator<String> courseIDIterator = curCourses.keys(); courseIDIterator.hasNext();) {
                    String curCourseID = courseIDIterator.next();
                    String curCourseName = curCourses.getString(curCourseID);
                    courseMap.put(curCourseID, curCourseName);
                }

                IndexSubject newSubject = new IndexSubject();
                newSubject.id = curID;
                newSubject.name = curContents.getString("name");
                newSubject.courses = courseMap;

                mSubjectsByCode.put(curID, newSubject);
            }

            // Set up subject names hashtable
            mSubjectsByName = new HashMap<>();
            for (Iterator<String> namesIterator = names.keys(); namesIterator.hasNext();) {
                String curName = namesIterator.next();
                String curContents = names.getString(curName);
                mSubjectsByName.put(curName, curContents);
            }

            // Set up course names
            mCoursesByName = new HashMap<>();
            for (Iterator<String> coursesIterator = courses.keys(); coursesIterator.hasNext();) {
                String curCourseName = coursesIterator.next();
                JSONObject curContents = courses.getJSONObject(curCourseName);
                IndexCourse newCourse = new IndexCourse();
                newCourse.course = curContents.getString("course");
                newCourse.subj = curContents.getString("subj");
                mCoursesByName.put(curCourseName, newCourse);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid index JSON: " + e.getMessage());
        }
    }

    public List<Subject> getSubjects() {
        List<Subject> results = new ArrayList<>();
        for (IndexSubject subject : mSubjectsByCode.values()) {
            results.add(new Subject(subject.name.toUpperCase(), subject.id));
        }
        return results;
    }

    /**
     * Get subjects by abbreviation
     * @param abbrev Abbreviation
     * @return List of subject JSON objects (empty if no results found)
     */
    public List<Subject> getSubjectsByAbbreviation(String abbrev) {
        List<Subject> results = new ArrayList<>();
        if (mAbbreviations.containsKey(abbrev.toUpperCase())) {
            String[] subjCodes = mAbbreviations.get(abbrev.toUpperCase());
            for (String subjCode: subjCodes) {
                IndexSubject curSubject = mSubjectsByCode.get(subjCode);
                if (curSubject != null) {
                    results.add(new Subject(curSubject.name.toUpperCase(), curSubject.id));
                }
            }
        }

        return results;
    }

    /**
     * Get subject by subject code
     * @param subjectCode Subject code
     * @return Subject JSON object
     */
    public Subject getSubjectByCode(String subjectCode) {
        IndexSubject subject = mSubjectsByCode.get(subjectCode);
        if (subject != null) {
            return new Subject(subject.name.toUpperCase(), subject.id);
        } else {
            return null;
        }
    }

    public List<Course> getCoursesInSubject(String subjectCode) {
        List<Course> results = new ArrayList<>();
        IndexSubject subject = mSubjectsByCode.get(subjectCode);
        if (subject != null) {
            Set<Map.Entry<String, String>> courseEntries = subject.courses.entrySet();
            for (Map.Entry<String, String> courseEntry : courseEntries) {
                String courseCode = courseEntry.getKey();
                String courseTitle = courseEntry.getValue();
                results.add(new Course(courseTitle, subjectCode, courseCode));
            }
        }
        return results;
    }

    public List<Course> getCoursesByCode(String queryCourseCode, String query) {
        List<Course> results = new ArrayList<>();
        Set<Map.Entry<String, IndexCourse>> courseEntries = mCoursesByName.entrySet();
        for (Map.Entry<String, IndexCourse> courseEntry : courseEntries) {
            String courseName = courseEntry.getKey();
            String courseCode = courseEntry.getValue().course;
            String subjectCode = courseEntry.getValue().subj;

            if (query == null || StringUtils.containsIgnoreCase(courseName, query)) {
                if (queryCourseCode == null || courseCode.contains(queryCourseCode)) {
                    results.add(new Course(courseName, subjectCode, courseCode));
                }
            }
        }
        return results;
    }

    public List<Course> getCoursesByCode(String courseCode) {
        return getCoursesByCode(courseCode, null);
    }

    public List<Course> getCoursesByQuery(String query) {
        return getCoursesByCode(null, query);
    }

    /**
     * Get course by subject & course code combination
     * @param subjectCode Subject code
     * @param courseCode Course code
     * @return Course-stub JSON object
     */
    public Course getCourseByCodeInSubject(String subjectCode, String courseCode) {
        IndexSubject subject = mSubjectsByCode.get(subjectCode);
        if (subject == null) return null;

        String title = subject.courses.get(courseCode);
        if (title != null) {
            return new Course(title.toUpperCase(), subjectCode, courseCode);
        } else {
            return null;
        }
    }

    public List<Course> getCoursesByCodeInSubject(String subjectCode, String courseCodeQuery) {
        IndexSubject subject = mSubjectsByCode.get(subjectCode);
        if (subject == null) return new ArrayList<>();

        List<Course> results = new ArrayList<>();

        for (Map.Entry<String, String> courseEntry : subject.courses.entrySet()) {
            String courseCode = courseEntry.getKey();
            String courseTitle = courseEntry.getValue();

            if (courseCode.contains(courseCodeQuery)) {
                results.add(new Course(courseTitle, subjectCode, courseCode));
            }
        }

        return results;
    }

    /**
     * Get courses by partial title matches on query in a subject
     * @param subjectCode subject to look for courses in
     * @param query Query string
     * @param cap Maximum number of results (cutoff point)
     * @return List of course-stub JSON objects (empty if no results found)
     */
    public List<Course> getCoursesByNameInSubject(String subjectCode, String query, int cap) {
        IndexSubject subject = mSubjectsByCode.get(subjectCode);
        if (subject == null) return new ArrayList<>();

        List<Course> results = new ArrayList<>();

        for (Map.Entry<String, String> courseEntry : subject.courses.entrySet()) {
            String courseCode = courseEntry.getKey();
            String courseTitle = courseEntry.getValue();

            if (StringUtils.containsIgnoreCase(courseTitle, query)) {
                results.add(new Course(courseTitle, subjectCode, courseCode));
            }
            if (results.size() >= cap) return results;
        }

        return results;
    }

    public void setSemesterCode(String semesterCode) {
        this.semesterCode = semesterCode;
    }

    public void setCampusCode(String campusCode) {
        this.campusCode = campusCode;
    }

    public void setLevelCode(String levelCode) {
        this.levelCode = levelCode;
    }

    public String getLevelCode() {
        return levelCode;
    }

    public String getSemesterCode() {
        return semesterCode;
    }

    public String getCampusCode() {
        return campusCode;
    }

}
