package com.jackz314.classregistrationhelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Course {
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
    private String major;//generated later on
    private String offeredTerms;
    private List<String> types;
    private List<String> days;
    private List<String> times;
    private List<String> locations;
    private List<String> periods;
    private String instructor;

    public Course(){
        //default constructor
    }

    public Course(String number, String title, String description, String crn, String totalSeats, String takenSeats, String availableSeats,
                  List<String> levelRestrictionYes, List<String> levelRestrictionNo, List<String> majorRestrictionYes, List<String> majorRestrictionNo,
                  String prerequisite, String units, String fee, String major, String offeredTerms, List<String> types, List<String> days, List<String> times, List<String> locations,
                  List<String> periods, String instructor) {
        this.setNumber(number);
        this.setTitle(title);
        this.setDescription(description);
        this.setCrn(crn);
        this.setTotalSeats(totalSeats);
        this.setTakenSeats(takenSeats);
        this.setAvailableSeats(availableSeats);
        this.setLevelRestrictionYes(levelRestrictionYes);
        this.setLevelRestrictionNo(levelRestrictionNo);
        this.setMajorRestrictionYes(majorRestrictionYes);
        this.setMajorRestrictionNo(majorRestrictionNo);
        this.setPrerequisite(prerequisite);
        this.setUnits(units);
        this.setFee(fee);
        this.setMajor(major);
        this.setOfferedTerms(offeredTerms);
        this.setTypes(types);
        this.setDays(days);
        this.setTimes(times);
        this.setLocations(locations);
        this.setPeriods(periods);
        this.setInstructor(instructor);
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
        //generate major automatically
        this.setMajor(number.substring(0, number.indexOf('-')));
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCrn() {
        return crn;
    }

    public void setCrn(String crn) {
        this.crn = crn;
    }

    public String getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(String totalSeats) {
        this.totalSeats = totalSeats;
    }

    public String getTakenSeats() {
        return takenSeats;
    }

    public void setTakenSeats(String takenSeats) {
        this.takenSeats = takenSeats;
    }

    public String getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(String availableSeats) {
        this.availableSeats = availableSeats;
    }

    public List<String> getLevelRestrictionYes() {
        return levelRestrictionYes;
    }

    public void setLevelRestrictionYes(List<String> levelRestrictionYes) {
        this.levelRestrictionYes = levelRestrictionYes;
    }

    public List<String> getLevelRestrictionNo() {
        return levelRestrictionNo;
    }

    public void setLevelRestrictionNo(List<String> levelRestrictionNo) {
        this.levelRestrictionNo = levelRestrictionNo;
    }

    public List<String> getMajorRestrictionYes() {
        return majorRestrictionYes;
    }

    public void setMajorRestrictionYes(List<String> majorRestrictionYes) {
        this.majorRestrictionYes = majorRestrictionYes;
    }

    public List<String> getMajorRestrictionNo() {
        return majorRestrictionNo;
    }

    public void setMajorRestrictionNo(List<String> majorRestrictionNo) {
        this.majorRestrictionNo = majorRestrictionNo;
    }


    public String getPrerequisite() {
        return prerequisite;
    }

    public void setPrerequisite(String prerequisite) {
        this.prerequisite = prerequisite;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public String getFee() {
        return fee;
    }

    public void setFee(String fee) {
        this.fee = fee;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    //get major from course number
    public String getMajor(String number) {
        return number.substring(0, number.indexOf('-'));
    }

    public String getOfferedTerms() {
        return offeredTerms;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public void addType(String type){
        if(types != null){
            types.add(type);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(type);
            setTypes(temp);
        }
    }

    public List<String> getDays() {
        return days;
    }

    public void setDays(List<String> days) {
        this.days = days;
    }

    public void addDays(String days){
        if(this.days != null){
            this.days.add(days);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(days);
            setDays(temp);
        }
    }

    public List<String> getTimes() {
        return times;
    }

    public void setTimes(List<String> times) {
        this.times = times;
    }

    public void addTime(String time){
        if(times != null){
            times.add(time);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(time);
            setTimes(temp);
        }
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public void addLocation(String location){
        if(locations != null){
            locations.add(location);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(location);
            setLocations(temp);
        }
    }

    public List<String> getPeriods() {
        return periods;
    }

    public void setPeriods(List<String> periods) {
        this.periods = periods;
    }

    public void addPeriod(String period){
        if(periods != null){
            periods.add(period);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(period);
            setPeriods(temp);
        }
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public void setOfferedTerms(String offeredTerms) {
        this.offeredTerms = offeredTerms;
    }

    public List<List<String>> getCourseInfoForDetail(){
        List<List<String>> courseInfo = new ArrayList<>();
        courseInfo.add(Collections.singletonList(getCrn()));
        courseInfo.add(Collections.singletonList(getNumber()));
        courseInfo.add(getTypes());
        courseInfo.add(getDays());
        courseInfo.add(getTimes());
        courseInfo.add(getLocations());
        courseInfo.add(getPeriods());
        courseInfo.add(Collections.singletonList(getInstructor()));
        return courseInfo;
    }

    @Override
    public String toString() {
        return "Course{" +
                "number='" + number + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", crn='" + crn + '\'' +
                ", totalSeats='" + totalSeats + '\'' +
                ", takenSeats='" + takenSeats + '\'' +
                ", availableSeats='" + availableSeats + '\'' +
                ", levelRestrictionYes=" + levelRestrictionYes +
                ", levelRestrictionNo=" + levelRestrictionNo +
                ", majorRestrictionYes=" + majorRestrictionYes +
                ", majorRestrictionNo=" + majorRestrictionNo +
                ", prerequisite='" + prerequisite + '\'' +
                ", units='" + units + '\'' +
                ", fee='" + fee + '\'' +
                ", major='" + major + '\'' +
                ", offeredTerms='" + offeredTerms + '\'' +
                ", types=" + types +
                ", days=" + days +
                ", times=" + times +
                ", locations=" + locations +
                ", periods=" + periods +
                ", instructor='" + instructor + '\'' +
                '}';
    }
}
