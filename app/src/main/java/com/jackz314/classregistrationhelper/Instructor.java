package com.jackz314.classregistrationhelper;

import android.util.Pair;

import java.util.LinkedList;
import java.util.List;

public class Instructor {

    private String url = null;
    private String name = null;
    private String title = null;
    private String email = null;
    private String phone = null;
    private String office = null;
    private String education = null;
    private String discipline = null;
    private String researchInterest = null;
    private List<Pair<String, String>> otherInfo = null;

    public Instructor(){
        //default empty constructor
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOffice() {
        return office;
    }

    public void setOffice(String office) {
        this.office = office;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getDiscipline() {
        return discipline;
    }

    public void setDiscipline(String discipline) {
        this.discipline = discipline;
    }

    public String getResearchInterest() {
        return researchInterest;
    }

    public void setResearchInterest(String researchInterest) {
        this.researchInterest = researchInterest;
    }

    public List<Pair<String, String>> getOtherInfo() {
        return otherInfo;
    }

    private void setOtherInfo(List<Pair<String, String>> otherInfo) {
        this.otherInfo = otherInfo;
    }

    public void addOtherInfo(String otherLabel, String otherInfo){
        if(this.otherInfo == null){
            setOtherInfo(new LinkedList<>());
        }
        this.otherInfo.add(new Pair<>(otherLabel, otherInfo));
    }
}
