package com.jackz314.classregistrationhelper;

import java.util.List;

public class CourseBuilder {
    private String number;
    private String title;
    private String description;
    private String crn;
    private String totalSeats;
    private String takenSeats;
    private String availableSeats;
    private List<String> levelRestrictionYes;
    private List<String> levelRestrictionNo;
    private List<String> majorRestrictionYes;
    private List<String> majorRestrictionNo;
    private String prerequisite;
    private String units;
    private String fee;
    private String major;
    private String offeredTerms;
    private List<String> types;
    private List<String> days;
    private List<String> times;
    private List<String> locations;
    private List<String> periods;
    private String instructor;

    public CourseBuilder setNumber(String number) {
        this.number = number;
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

    public CourseBuilder setDays(List<String> days) {
        this.days = days;
        return this;
    }

    public CourseBuilder setTimes(List<String> times) {
        this.times = times;
        return this;
    }

    public CourseBuilder setLocations(List<String> locations) {
        this.locations = locations;
        return this;
    }

    public CourseBuilder setPeriods(List<String> periods) {
        this.periods = periods;
        return this;
    }

    public CourseBuilder setInstructor(String instructor) {
        this.instructor = instructor;
        return this;
    }

    public Course buildCourse() {
        Course course = new Course(number, title, description, crn, totalSeats, takenSeats, availableSeats, levelRestrictionYes, levelRestrictionNo, majorRestrictionYes, majorRestrictionNo, prerequisite, units, fee, major, offeredTerms, types, days, times, locations, periods, instructor);
        if(major != null){
            course.setMajor(major);
        }
        return course;
    }
}