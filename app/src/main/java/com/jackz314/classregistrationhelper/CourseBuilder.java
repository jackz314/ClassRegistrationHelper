package com.jackz314.classregistrationhelper;

import java.util.ArrayList;
import java.util.List;

public class CourseBuilder {
    private static final String TAG = "CourseBuilder";
    private String number = null;
    private String title = null;
    private String description = null;
    private String crn = null;
    private String totalSeats = null;
    private String takenSeats = null;
    private String availableSeats = null;
    private List<String> levelRestrictionYes = null;
    private List<String> levelRestrictionNo = null;
    private List<String> majorRestrictionYes = null;
    private List<String> majorRestrictionNo = null;
    private List<String> collegeRestriction = null;
    private List<String> additionalRestrictions = null;
    private String prerequisite = null;
    private String units = null;
    private String fee = null;
    private String major = null;//generated later on
    private String offeredTerms = null;
    private List<String> types = null;
    private List<String> days = null;
    private List<String> times = null;
    private List<String> locations = null;
    private List<String> periods = null;
    private String instructor = null;
    private String registerStatus = null;

    public CourseBuilder setNumber(String number) {
        this.number = number;
        if(number != null && !number.isEmpty()){
            //generate major automatically
            this.setMajor(number.substring(0, number.indexOf('-')));
        }
        return this;
    }

    public CourseBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public CourseBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public CourseBuilder setCrn(String crn) {
        this.crn = crn;
        return this;
    }

    public CourseBuilder setTotalSeats(String totalSeats) {
        this.totalSeats = totalSeats;
        return this;
    }

    public CourseBuilder setTakenSeats(String takenSeats) {
        this.takenSeats = takenSeats;
        return this;
    }

    public CourseBuilder setAvailableSeats(String availableSeats) {
        this.availableSeats = availableSeats;
        return this;
    }

    public CourseBuilder setLevelRestrictionYes(List<String> levelRestrictionYes) {
        this.levelRestrictionYes = levelRestrictionYes;
        return this;
    }

    public CourseBuilder setLevelRestrictionNo(List<String> levelRestrictionNo) {
        this.levelRestrictionNo = levelRestrictionNo;
        return this;
    }

    public CourseBuilder setMajorRestrictionYes(List<String> majorRestrictionYes) {
        this.majorRestrictionYes = majorRestrictionYes;
        return this;
    }

    public CourseBuilder setMajorRestrictionNo(List<String> majorRestrictionNo) {
        this.majorRestrictionNo = majorRestrictionNo;
        return this;
    }

    public CourseBuilder setAdditionalRestrictions(List<String> additionalRestrictions) {
        this.additionalRestrictions = additionalRestrictions;
        return this;
    }

    public CourseBuilder addAdditionalRestriction(String additionalRestriction) {
        if(additionalRestrictions != null){
            additionalRestrictions.add(additionalRestriction);
        }else {
            List<String> temp = new ArrayList<>();
            temp.add(additionalRestriction);
            setAdditionalRestrictions(temp);
        }
        return this;
    }

    public CourseBuilder setCollegeRestriction(List<String> collegeRestriction) {
        this.collegeRestriction = collegeRestriction;
        return this;
    }

    public CourseBuilder setPrerequisite(String prerequisite) {
        this.prerequisite = prerequisite;
        return this;
    }

    public CourseBuilder setUnits(String units) {
        this.units = units;
        return this;
    }

    public CourseBuilder setFee(String fee) {
        this.fee = fee;
        return this;
    }

    public CourseBuilder setMajor(String major) {
        this.major = major;
        return this;
    }

    public CourseBuilder setOfferedTerms(String offeredTerms) {
        this.offeredTerms = offeredTerms;
        return this;
    }

    public CourseBuilder setTypes(List<String> types) {
        this.types = types;
        return this;
    }

    public CourseBuilder addType(String type){
        if(types != null){
            types.add(type);
            //Log.i(TAG, String.valueOf(types.size()));
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(type);
            setTypes(temp);
        }
        return this;
    }

    public CourseBuilder setDays(List<String> days) {
        this.days = days;
        return this;
    }

    public CourseBuilder addDays(String days){
        if(this.days!= null){
            this.days.add(days);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(days);
            setDays(temp);
        }
        return this;
    }

    public CourseBuilder setTimes(List<String> times) {
        this.times = times;
        return this;
    }

    public CourseBuilder addTime(String time){
        if(times != null){
            times.add(time);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(time);
            setTimes(temp);
        }
        return this;
    }

    public CourseBuilder setLocations(List<String> locations) {
        this.locations = locations;
        return this;
    }

    public CourseBuilder addLocation(String location){
        if(locations != null){
            locations.add(location);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(location);
            setLocations(temp);
        }
        return this;
    }

    public CourseBuilder setPeriods(List<String> periods) {
        this.periods = periods;
        return this;
    }

    public CourseBuilder addPeriod(String period){
        if(periods != null){
            periods.add(period);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(period);
            setPeriods(temp);
        }
        return this;
    }

    public CourseBuilder setInstructor(String instructor) {
        this.instructor = instructor;
        return this;
    }

    public CourseBuilder setRegisterStatus(String registerStatus){
        this.registerStatus = registerStatus;
        return this;
    }

    public Course buildCourse() {
        //Log.i(TAG, "Course Built");
        return new Course(number, title, description, crn, totalSeats, takenSeats, availableSeats, levelRestrictionYes, levelRestrictionNo, majorRestrictionYes, majorRestrictionNo, additionalRestrictions, collegeRestriction, prerequisite, units, fee, major, offeredTerms, types, days, times, locations, periods, instructor, registerStatus);
    }
}