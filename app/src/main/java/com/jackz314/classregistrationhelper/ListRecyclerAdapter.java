package com.jackz314.classregistrationhelper;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.graphics.Typeface.BOLD;

class ListRecyclerAdapter extends RecyclerView.Adapter<ListRecyclerAdapter.ListRecyclerViewHolder> implements Filterable {

   private List<String[]> originalList;
   private List<String[]> filteredList;
   private String query = null;
   private static final String TAG = "ListRecyclerAdapter";

    /**
     * Creates new List Recycler Adapter
     *
     * @param list originalList with each element being an array
     *                  array pos 0: course number
     *                  array pos 1: course description/title
     *                  array pos 2: course crn number
     *                  array pos 3: course available seats count
     */
    ListRecyclerAdapter(List<String[]> list){
       originalList = list;
       filteredList = list;
   }

   @NonNull
   @Override
   public ListRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
       //can override at any time to inflate different layouts
       Context context = parent.getContext();
       View view = LayoutInflater.from(context).inflate(R.layout.course_info_item, parent, false);
       return new ListRecyclerViewHolder(view);
   }

    @Override
    public void onBindViewHolder(@NonNull final ListRecyclerViewHolder holder, int position) {
        if(!filteredList.isEmpty() && filteredList.size() > position){
            final SpannableString classNumber = new SpannableString(filteredList.get(position)[0]);
            final SpannableString classDescription = new SpannableString(filteredList.get(position)[1]);
            final SpannableString crn = new SpannableString(filteredList.get(position)[2]);
            final SpannableString availableSeats = new SpannableString(filteredList.get(position)[3]);
            //Log.i(TAG, "Position: "+ Integer.toString(position) + " " + classNumber + " " + classDescription + " " + crnNumber + " " + availableSeats);
            if(query != null && !query.isEmpty()){
                //set bold span for the searched part
                //todo query for class numbers need further improvements
                query = query.toLowerCase().replace('-', ' ');
                String classNumberStr = classNumber.toString().toLowerCase().replace('-',' ');
                String classDescriptionStr = classDescription.toString().toLowerCase();
                String crnStr = crn.toString().toLowerCase();
                /*if(query.contains(" ") && query.contains("0")){//eg. CSE 030
                    classNumberStr = classNumberStr.replace("-0", " ");
                }else if(query.contains("0")){//e.g. CSE30
                    classNumberStr = classNumberStr.replace('-', '');
                }else if(query.contains(" ")){//e.g. CSE 30

                }*/
                if(classNumberStr.contains(query)){
                    int startPos = classNumberStr.indexOf(query);
                    while (startPos >= 0){
                        int endPos = Math.min(startPos + query.length(), classNumberStr.length());
                        classNumber.setSpan(new StyleSpan(BOLD), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        startPos = classNumberStr.indexOf(query,endPos);
                    }
                }
                if(classDescriptionStr.contains(query)){
                    int startPos = classDescriptionStr.indexOf(query);
                    while (startPos >= 0){
                        int endPos = Math.min(startPos + query.length(), classDescriptionStr.length());
                        classDescription.setSpan(new StyleSpan(BOLD), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        startPos = classDescriptionStr.indexOf(query,endPos);
                    }
                }
                if(crnStr.contains(query)){
                    int startPos = crnStr.indexOf(query);
                    while (startPos >= 0){
                        int endPos = Math.min(startPos + query.length(), crnStr.length());
                        crn.setSpan(new StyleSpan(BOLD), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        startPos = crnStr.indexOf(query,endPos);
                    }
                }
                holder.courseNumberTxt.setText(classNumber);
                holder.courseDescriptionTxt.setText(classDescription);
                holder.courseCrnTxt.setText(crn);
                holder.courseAvailableSeatsTxt.setText(availableSeats);
            }else {
                holder.courseNumberTxt.setText(classNumber);
                holder.courseDescriptionTxt.setText(classDescription);
                holder.courseCrnTxt.setText(crn);
                holder.courseAvailableSeatsTxt.setText(availableSeats);
            }

        }
    }

   static class ListRecyclerViewHolder extends RecyclerView.ViewHolder{
       TextView courseNumberTxt, courseDescriptionTxt, courseCrnTxt, courseAvailableSeatsTxt;
       ListRecyclerViewHolder(View itemView) {
           super(itemView);
           courseNumberTxt = itemView.findViewById(R.id.class_number_txt);
           courseDescriptionTxt = itemView.findViewById(R.id.class_description_txt);
           courseCrnTxt = itemView.findViewById(R.id.crn_no_txt);
           courseAvailableSeatsTxt = itemView.findViewById(R.id.avaliable_seat_count_txt);
       }
   }

   @Override
   public int getItemCount() {
       return filteredList.size();
   }

   public int getFilteredItemCount(){
        return filteredList.size();
   }

   @Override
   public Filter getFilter(){
        //System.out.println("initial count"+ getItemCount());
       return new Filter() {
           @Override
           protected FilterResults performFiltering(CharSequence constraint) {
               query = constraint.toString();
               List<String[]> arrTemp;
               if (query.isEmpty()) {
                   arrTemp = new ArrayList<>(originalList);
               } else {
                   arrTemp = getFilteredList(query);
               }
               FilterResults results = new FilterResults();
               results.values = arrTemp;
               Log.i(TAG, Arrays.deepToString(arrTemp.toArray()));
               return results;
           }

           @SuppressWarnings("unchecked")
           @Override
           protected void publishResults(CharSequence constraint, FilterResults results) {
               //filteredList.clear();
               filteredList = (List<String[]>) results.values;//it's fine, ignore the warning, I had to it this way, and the way that make sure the warning goes away slows down the performance, so no... maybe find out more about it in the future
               //System.out.println(" Filtered Result Size: "+ filteredList.size() + " Original Size: " + originalList.size());
               notifyDataSetChanged();
           }
       };
   }

   private List<String[]> getFilteredList(String query){
       //filteredList.clear();//clear previous stuff in it
       List<String[]> results = new ArrayList<>();
       for (String[] classInfo : originalList) {
           //matching...
           if (classInfo[0].toLowerCase().contains(query.toLowerCase()) || //course number
                   classInfo[2].contains(query) || //crn
                   classInfo[1].toLowerCase().contains(query.toLowerCase())) { //description
               results.add(classInfo);
           }
       }
       return results;
   }

   String[] getItemAtPos(int position){
       return filteredList.get(position);
   }

   void swapNewDataSet(List<String[]> newList)
   {
       if(newList == null || newList.isEmpty()) return;
       if (originalList != null && !originalList.isEmpty()) originalList.clear();
       originalList = newList;
       filteredList = newList;
       notifyDataSetChanged();
   }

}
