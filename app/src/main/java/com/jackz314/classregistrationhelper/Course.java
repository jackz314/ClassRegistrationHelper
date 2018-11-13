package com.jackz314.classregistrationhelper;

import java.util.Arrays;

public class Course {
    String number;
    String title;
    String description;
    String crn;
    String totalSeats, takenSeats, availableSeats;
    String[] levelRestriction;
    String prerequisite;
    String credit;
    String fee;
    String major;//generated later on

    public Course(){
        //default constructor
    }

    public Course(String number, String title, String description, String crn, String totalSeats, String takenSeats, String availableSeats, String[] levelRestriction, String prerequisite, String credit, String fee) {
        this.number = number;
        this.title = title;
        this.description = description;
        this.crn = crn;
        this.totalSeats = totalSeats;
        this.takenSeats = takenSeats;
        this.availableSeats = availableSeats;
        this.levelRestriction = levelRestriction;
        this.prerequisite = prerequisite;
        this.credit = credit;
        this.fee = fee;
        //generate major
        major = number.substring(0, number.indexOf('-'));
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
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

    public String[] getLevelRestriction() {
        return levelRestriction;
    }

    public void setLevelRestriction(String[] levelRestriction) {
        this.levelRestriction = levelRestriction;
    }

    public String getPrerequisite() {
        return prerequisite;
    }

    public void setPrerequisite(String prerequisite) {
        this.prerequisite = prerequisite;
    }

    public String getCredit() {
        return credit;
    }

    public void setCredit(String credit) {
        this.credit = credit;
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

    public String getMajor(String number) {
        return number.substring(0, number.indexOf('-'));
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
                ", levelRestriction=" + Arrays.toString(levelRestriction) +
                ", prerequisite='" + prerequisite + '\'' +
                ", credit='" + credit + '\'' +
                ", fee='" + fee + '\'' +
                ", major='" + major + '\'' +
                '}';
    }

}
