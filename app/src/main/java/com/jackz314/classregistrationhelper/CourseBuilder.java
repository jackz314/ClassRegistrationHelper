package com.jackz314.classregistrationhelper;

public class CourseBuilder {
    private String number;
    private String title;
    private String description;
    private String crn;
    private String totalSeats;
    private String takenSeats;
    private String availableSeats;
    private String[] levelRestriction;
    private String prerequisite;
    private String credit;
    private String fee;

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

    public CourseBuilder setLevelRestriction(String[] levelRestriction) {
        this.levelRestriction = levelRestriction;
        return this;
    }

    public CourseBuilder setPrerequisite(String prerequisite) {
        this.prerequisite = prerequisite;
        return this;
    }

    public CourseBuilder setCredit(String credit) {
        this.credit = credit;
        return this;
    }

    public CourseBuilder setFee(String fee) {
        this.fee = fee;
        return this;
    }

    public Course createCourse() {
        return new Course(number, title, description, crn, totalSeats, takenSeats, availableSeats, levelRestriction, prerequisite, credit, fee);
    }
}