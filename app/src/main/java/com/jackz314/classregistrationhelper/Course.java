package com.jackz314.classregistrationhelper;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Course implements Parcelable {
    private static final String TAG = "CourseObject";
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
    private Parcel in;

    Course(){
        //default constructor
    }

    Course(String number, String title, String description, String crn, String totalSeats, String takenSeats, String availableSeats,
           List<String> levelRestrictionYes, List<String> levelRestrictionNo, List<String> majorRestrictionYes, List<String> majorRestrictionNo, List<String> additionalRestrictions, List<String> collegeRestriction,
           String prerequisite, String units, String fee, String major, String offeredTerms, List<String> types, List<String> days, List<String> times, List<String> locations,
           List<String> periods, String instructor, String registerStatus) {
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
        this.setCollegeRestriction(collegeRestriction);
        this.setAdditionalRestrictions(additionalRestrictions);
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
        this.setRegisterStatus(registerStatus);
    }

    //DO NOT CHANGE THIS WITHOUT CHANGING THE CORRESPONDING WRITING PART AS WELL
    private Course(Parcel in) {
        setNumber(in.readString());
        setTitle(in.readString());
        setDescription(in.readString());
        setCrn(in.readString());
        setTotalSeats(in.readString());
        setTakenSeats(in.readString());
        setAvailableSeats(in.readString());
        setLevelRestrictionYes(in.createStringArrayList());
        setLevelRestrictionNo(in.createStringArrayList());
        setMajorRestrictionYes(in.createStringArrayList());
        setMajorRestrictionNo(in.createStringArrayList());
        setCollegeRestriction(in.createStringArrayList());
        setAdditionalRestrictions(in.createStringArrayList());
        setPrerequisite(in.readString());
        setUnits(in.readString());
        setFee(in.readString());
        setMajor(in.readString());
        setOfferedTerms(in.readString());
        setTypes(in.createStringArrayList());
        setDays(in.createStringArrayList());
        setTimes(in.createStringArrayList());
        setLocations(in.createStringArrayList());
        setPeriods(in.createStringArrayList());
        setInstructor(in.readString());
        setRegisterStatus(in.readString());
    }

    public static final Creator<Course> CREATOR = new Creator<Course>() {
        @Override
        public Course createFromParcel(Parcel in) {
            return new Course(in);
        }

        @Override
        public Course[] newArray(int size) {
            return new Course[size];
        }
    };

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
        if(number != null && !number.isEmpty()){
            //generate major automatically
            this.setMajor(number.substring(0, number.indexOf('-')));
        }
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

    public List<String> getMajorRestrictionNo() {
        return majorRestrictionNo;
    }

    public void setMajorRestrictionNo(List<String> majorRestrictionNo) {
        this.majorRestrictionNo = majorRestrictionNo;
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

    public List<String> getCollegeRestriction() {
        return collegeRestriction;
    }

    public void setCollegeRestriction(List<String> collegeRestriction) {
        this.collegeRestriction = collegeRestriction;
    }

    public List<String> getAdditionalRestrictions() {
        return additionalRestrictions;
    }

    public String getAdditionalRestrictionsString() {
        return (getAdditionalRestrictions() == null) ? null : Arrays.toString(getAdditionalRestrictions().toArray());
    }

    public void setAdditionalRestrictions(List<String> additionalRestrictions) {
        this.additionalRestrictions = additionalRestrictions;
    }

    public void addAdditionalRestriction(String additionalRestriction) {
        if(getAdditionalRestrictions() != null){
            getAdditionalRestrictions().add(additionalRestriction);
        }else {
            List<String> temp = new ArrayList<>();
            temp.add(additionalRestriction);
            setAdditionalRestrictions(temp);
        }
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

    public String getTypeLiteral(int index){
        if(index < 0) return null;
        String type = types.get(index);
        if(type.equals("LECT")) return "Lecture";
        if(type.equals("EXAM")) return "Exam";
        if(type.equals("DISC")) return "Discussion";
        if(type.equals("LAB")) return "Lab";
        if(type.equals("SEM")) return "Seminar";
        if(type.equals("STDO")) return "Studio";
        //if(type.equals("FLDW")) return "Future Light Digital Workshops";//not sure
        return type;//just in case none of them matches, return the original string
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public void addType(String type){
        if(getTypes() != null){
            getTypes().add(type);
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
        if(this.getDays() != null){
            this.getDays().add(days);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(days);
            setDays(temp);
        }
    }

    public String getDaysLiteral(int index){
        String days = getDays().get(index);
        if(days.equals("TBD")) return "TBD";
        days = days.replace("M", "Mon ")
                .replace("T", "Tue ")
                .replace("W", "Wed ")
                .replace("R", "Thu ")
                .replace("F", "Fri ")
                .replace("S", "Sat ");
        return days.substring(0, days.length() - 1);//remove the last space
    }

    public List<String> getTimes() {
        return times;
    }

    public void setTimes(List<String> times) {
        this.times = times;
    }

    public void addTime(String time){
        if(getTimes() != null){
            getTimes().add(time);
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
        if(getLocations() != null){
            getLocations().add(location);
        }else {//initialize types
            List<String> temp = new ArrayList<>();
            temp.add(location);
            setLocations(temp);
        }
    }

    public List<String> getPeriods() {
        if (periods != null) {
            Log.i(TAG, "Periods: " + Arrays.toString(periods.toArray()));
        }
        return periods;
    }

    public void setPeriods(List<String> periods) {
        this.periods = periods;
    }


    public String getPeriodLiteral(int index){
        //turn String like "11-JAN 25-MAY" to "Jan 11 - May 25"
        String periodsStr = getPeriods().get(index);
        Log.i(TAG, periodsStr);

        //separate two dates
        String periodStart = periodsStr.substring(0, periodsStr.indexOf(' '));
        String periodEnd = periodsStr.substring(periodsStr.indexOf(' ') + 1);

        //start date
        String startMonth = periodStart.substring(periodStart.indexOf('-') + 1);
        startMonth = startMonth.substring(0, 1).toUpperCase() + startMonth.substring(1).toLowerCase();
        String startDay = periodStart.substring(0, periodStart.indexOf('-'));

        if(periodStart.equals(periodEnd)){//one single date
            return startMonth + " " + startDay;
        }

        //end date
        String endMonth = periodEnd.substring(periodEnd.indexOf('-') + 1);
        endMonth = endMonth.substring(0, 1).toUpperCase() + endMonth.substring(1).toLowerCase();
        String endDay = periodEnd.substring(0, periodEnd.indexOf('-'));

        return startMonth + " " + startDay + " - " + endMonth + " " + endDay;
    }

    public void addPeriod(String period){
        if(getPeriods() != null){
            getPeriods().add(period);
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

    public String getRegisterStatus() {
        return registerStatus;
    }

    public void setRegisterStatus(String registerStatus){
        /*if(registerStatus != null){
            Log.i(TAG, "SET REGISTER STATUS for: " + getNumber() + ": " + getCrn() + ", STATUS: " + registerStatus);
        }*/
        this.registerStatus = registerStatus;
    }

    public void setOfferedTerms(String offeredTerms) {
        this.offeredTerms = offeredTerms;
    }

    /*public List<List<String>> getCourseInfoForDetail(){
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
    }*/

    public String getAcademicRestrictionCombinedText(){
        StringBuilder combinedText = new StringBuilder();
        boolean hasMajorYes = majorRestrictionYes != null && !majorRestrictionYes.isEmpty();
        boolean hasLevelYes = levelRestrictionYes != null && !levelRestrictionYes.isEmpty();
        boolean hasCollege = collegeRestriction != null && !collegeRestriction.isEmpty();
        boolean hasMajorNo = majorRestrictionNo != null && !majorRestrictionNo.isEmpty();
        boolean hasLevelNo = levelRestrictionNo != null && !levelRestrictionNo.isEmpty();
        if(!(hasMajorYes || hasCollege || hasLevelYes || hasMajorNo || hasLevelNo)){
            //no academic restrictions
            return null;
        }
        if(hasMajorYes || hasCollege || hasLevelYes){
            combinedText.append("You must be ");
        }
        if(hasMajorYes){
            if(isVowel(majorRestrictionYes.get(0).charAt(0))){
                combinedText.append("an ");
            }else {
                combinedText.append("a ");
            }
            for (int i = 0; i < majorRestrictionYes.size(); i++) {
                String restriction = majorRestrictionYes.get(i);
                combinedText.append(restriction);
                if(majorRestrictionYes.size() == 2){//if only has two elements, no commas needed
                    if(i == 0){
                        combinedText.append(" or ");
                    }
                }else if(majorRestrictionYes.size() > 2){
                    if(i > 0 && i != majorRestrictionYes.size() - 1){//append comma if more than two and isn't the last one
                        combinedText.append(", ");
                    }
                    if(i == majorRestrictionYes.size() - 2){//form the format: a, b, or c
                        combinedText.append(" or ");
                    }
                }
            }
            combinedText.append(" major");
        }
        if(hasLevelYes){
            if(hasMajorYes){
                combinedText.append("'s ");
            }else {
                if(isVowel(levelRestrictionYes.get(0).charAt(0))){
                    combinedText.append("an ");
                }else {
                    combinedText.append("a ");
                }
            }
            for (int i = 0; i < levelRestrictionYes.size(); i++) {
                String restriction = levelRestrictionYes.get(i);
                combinedText.append(restriction);
                if(levelRestrictionYes.size() == 2){//if only has two elements, no commas needed
                    if(i == 0){
                        combinedText.append(" or ");
                    }
                }else if(levelRestrictionYes.size() > 2){
                    if(i > 0 && i != levelRestrictionYes.size() - 1){//append comma if more than two and isn't the last one
                        combinedText.append(", ");
                    }
                    if(i == levelRestrictionYes.size() - 2){//form the format: a, b, or c
                        combinedText.append(" or ");
                    }
                }
            }
        }
        if (hasCollege){
            if(hasMajorYes && hasLevelYes){
                combinedText.append(", and ");
            }else {
                if(hasMajorYes || hasLevelYes){
                    combinedText.append(" and ");
                }
            }
            combinedText.append("in ");
            for (int i = 0; i < collegeRestriction.size(); i++) {
                String restriction = collegeRestriction.get(i);
                combinedText.append(restriction);
                if(collegeRestriction.size() == 2){//if only has two elements, no commas needed
                    if(i == 0){
                        combinedText.append(" or ");
                    }
                }else if(collegeRestriction.size() > 2){
                    if(i > 0 && i != collegeRestriction.size() - 1){//append comma if more than two and isn't the last one
                        combinedText.append(", ");
                    }
                    if(i == collegeRestriction.size() - 2){//form the format: a, b, or c
                        combinedText.append(" or ");
                    }
                }
            }
            combinedText.append(" college");
        }
        if(hasMajorNo || hasLevelNo){
            if(hasMajorYes || hasLevelYes || hasCollege){//end previous one
                combinedText.append(". ");
                combinedText.append("Can't be ");
            }else {
                combinedText.append("You can't be ");
            }
        }

        if(hasMajorNo){
            if(isVowel(majorRestrictionNo.get(0).charAt(0))){
                combinedText.append("an ");
            }else {
                combinedText.append("a ");
            }
            for (int i = 0; i < majorRestrictionNo.size(); i++) {
                String restriction = majorRestrictionNo.get(i);
                combinedText.append(restriction);
                if(majorRestrictionNo.size() == 2){//if only has two elements, no commas needed
                    if(i == 0){
                        combinedText.append(" or ");
                    }
                }else if(majorRestrictionNo.size() > 2){
                    if(i > 0 && i != majorRestrictionNo.size() - 1){//append comma if more than two and isn't the last one
                        combinedText.append(", ");
                    }
                    if(i == majorRestrictionNo.size() - 2){//form the format: a, b, or c
                        combinedText.append(" or ");
                    }
                }
            }
            combinedText.append(" major");
        }

        if(hasLevelNo){
            if(hasMajorNo){
                combinedText.append(", or be ");
            }
            if(isVowel(levelRestrictionNo.get(0).charAt(0))){
                combinedText.append("an ");
            }else {
                combinedText.append("a ");
            }
            for (int i = 0; i < levelRestrictionNo.size(); i++) {
                String restriction = levelRestrictionNo.get(i);
                combinedText.append(restriction);
                if(levelRestrictionNo.size() == 2){//if only has two elements, no commas needed
                    if(i == 0){
                        combinedText.append(" or ");
                    }
                }else if(levelRestrictionNo.size() > 2){
                    if(i > 0 && i != levelRestrictionNo.size() - 1){//append comma if more than two and isn't the last one
                        combinedText.append(", ");
                    }
                    if(i == levelRestrictionNo.size() - 2){//form the format: a, b, or c
                        combinedText.append(" or ");
                    }
                }
            }
            combinedText.append(" student");
        }
        return combinedText.toString();
    }

    public static boolean isVowel(char character){
        return character == 'a' || character == 'e' || character == 'i' || character == 'o' || character == 'u';
    }

    //todo continue here
    public String getAdditionalClassesCombinedText(){
        if(types != null && types.size() > 1){
            StringBuilder combinedText = new StringBuilder();
            for(int i = 1; i < types.size(); i++){//start from second one
                combinedText.append(getTypeLiteral(i)).append(" ");
                combinedText.append(getTimes().get(i)).append(" ");
                combinedText.append(getDaysLiteral(i)).append(" ");
                combinedText.append(getPeriodLiteral(i)).append(" ");
                combinedText.append("at ").append(getLocations().get(i));
                if(i != types.size() - 1){
                    combinedText.append("\n");
                }
            }
            return combinedText.toString();
        }else {
            return null;
        }
    }

    public String getOtherInfoCombinedText(){
        if((additionalRestrictions != null && !additionalRestrictions.isEmpty()) || (offeredTerms != null && !offeredTerms.isEmpty())){
            StringBuilder combinedText = new StringBuilder();
            if(offeredTerms != null && !offeredTerms.isEmpty()){
                combinedText.append(offeredTerms);
            }
            if(additionalRestrictions != null && !additionalRestrictions.isEmpty()){
                if(offeredTerms != null && !offeredTerms.isEmpty()){
                    combinedText.append("\n");
                }
                for (int i = 0; i < additionalRestrictions.size(); i++) {
                    String additionalRestriction = additionalRestrictions.get(i);
                    combinedText.append(Integer.toString(i)).append(". ");//1. xxxx 2. xxxx
                    combinedText.append(additionalRestriction);
                    if(additionalRestrictions.size() != 1 && i != additionalRestrictions.size() - 1){
                        combinedText.append('\n');
                    }
                }
            }
            return combinedText.toString();
        }else return null;
    }

    public CourseBuilder newBuilder(){
        CourseBuilder courseBuilder = new CourseBuilder();
        courseBuilder.setNumber(getNumber())
                .setTitle(getTitle())
                .setDescription(getDescription())
                .setCrn(getCrn())
                .setTotalSeats(getTotalSeats())
                .setTakenSeats(getTakenSeats())
                .setAvailableSeats(getAvailableSeats())
                .setLevelRestrictionYes(getLevelRestrictionYes())
                .setLevelRestrictionNo(getLevelRestrictionNo())
                .setMajorRestrictionYes(getMajorRestrictionYes())
                .setMajorRestrictionNo(getMajorRestrictionNo())
                .setCollegeRestriction(getCollegeRestriction())
                .setAdditionalRestrictions(getAdditionalRestrictions())
                .setPrerequisite(getPrerequisite())
                .setUnits(getUnits())
                .setFee(getFee())
                .setMajor(getMajor())
                .setOfferedTerms(getOfferedTerms())
                .setTypes(getTypes())
                .setDays(getDays())
                .setTimes(getTimes())
                .setLocations(getLocations())
                .setPeriods(getPeriods())
                .setInstructor(getInstructor())
                .setRegisterStatus(getRegisterStatus());
        return courseBuilder;
    }

    @Override
    public String toString() {
        return "Course{" +
                "number='" + getNumber() + '\n' +
                ", title='" + getTitle() + '\n' +
                ", description='" + getDescription() + '\n' +
                ", crn='" + getCrn() + '\n' +
                ", totalSeats='" + getTotalSeats() + '\n' +
                ", takenSeats='" + getTakenSeats() + '\n' +
                ", availableSeats='" + getAvailableSeats() + '\n' +
                ", prerequisite='" + getPrerequisite() + '\n' +
                ", units='" + getUnits() + '\n' +
                ", fee='" + getFee() + '\n' +
                ", major='" + getMajor() + '\n' +
                ", offeredTerms='" + getOfferedTerms() + '\n' +
                ", additionalInfo='" + getAdditionalRestrictionsString() + '\n' +
                ", instructor='" + getInstructor() + '\n' +
                ", registerStatus='" + getRegisterStatus() + '\n' +
                '}';
    }

    //todo continue here

    @Override
    public int describeContents() {
        return 0;
    }

    //DO NOT CHANGE THIS WITHOUT CHANGING THE CORRESPONDING READING PART AS WELL
    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(getNumber());
        parcel.writeString(getTitle());
        parcel.writeString(getDescription());
        parcel.writeString(getCrn());
        parcel.writeString(getTotalSeats());
        parcel.writeString(getTakenSeats());
        parcel.writeString(getAvailableSeats());
        parcel.writeStringList(getLevelRestrictionYes());
        parcel.writeStringList(getLevelRestrictionNo());
        parcel.writeStringList(getMajorRestrictionYes());
        parcel.writeStringList(getMajorRestrictionNo());
        parcel.writeStringList(getCollegeRestriction());
        parcel.writeStringList(getAdditionalRestrictions());
        parcel.writeString(getPrerequisite());
        parcel.writeString(getUnits());
        parcel.writeString(getFee());
        parcel.writeString(getMajor());
        parcel.writeString(getOfferedTerms());
        parcel.writeStringList(getTypes());
        parcel.writeStringList(getDays());
        parcel.writeStringList(getTimes());
        parcel.writeStringList(getLocations());
        parcel.writeStringList(getPeriods());
        parcel.writeString(getInstructor());
        parcel.writeString(getRegisterStatus());
    }

}
