package com.jackz314.classregistrationhelper;

import android.content.Context;
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

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import static android.graphics.Typeface.BOLD;

class ListRecyclerAdapter extends RecyclerView.Adapter<ListRecyclerAdapter.ListRecyclerViewHolder> implements Filterable {

   private List<Course> originalList;
   private List<Course> filteredList;
   private String query = null;
   private static final String TAG = "ListRecyclerAdapter";

   ListRecyclerAdapter(List<Course> list){
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
            try{
                final SpannableString classNumber = new SpannableString(filteredList.get(position).getNumber());
                final SpannableString classDescription = new SpannableString(filteredList.get(position).getTitle());
                final SpannableString crn = new SpannableString(filteredList.get(position).getCrn());
                final SpannableString availableSeats = new SpannableString(filteredList.get(position).getAvailableSeats());
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
                }
                holder.courseNumberTxt.setText(classNumber);
                holder.courseDescriptionTxt.setText(classDescription);
                holder.courseCrnTxt.setText(crn);
                holder.courseAvailableSeatsTxt.setText(availableSeats);

                String registerTime = filteredList.get(position).getRegisterStatus();
                if(registerTime != null && !registerTime.isEmpty()){
                    Log.i(TAG, "Registered course: " + crn + " POS: " + Integer.toString(position));
                    holder.registeredTimeTxt.setText(registerTime);
                    holder.registeredTimeTxt.setVisibility(View.VISIBLE);
                }else {// you have no idea how long it took me to realize that this part is missing. I kept getting random statuses, and this is the reason behind it. Fuc.
                    holder.registeredTimeTxt.setText("");
                    holder.registeredTimeTxt.setVisibility(View.GONE);
                }
            }catch (NullPointerException e){
                e.printStackTrace();
            }
        }
    }

   static class ListRecyclerViewHolder extends RecyclerView.ViewHolder{
       TextView courseNumberTxt, courseDescriptionTxt, courseCrnTxt, courseAvailableSeatsTxt, registeredTimeTxt;
       ListRecyclerViewHolder(View itemView) {
           super(itemView);
           courseNumberTxt = itemView.findViewById(R.id.class_number_txt);
           courseDescriptionTxt = itemView.findViewById(R.id.class_description_txt);
           courseCrnTxt = itemView.findViewById(R.id.crn_num_txt);
           courseAvailableSeatsTxt = itemView.findViewById(R.id.avaliable_seat_count_txt);
           registeredTimeTxt = itemView.findViewById(R.id.registered_time_txt);
       }
   }

   @Override
   public int getItemCount() {
       return filteredList.size();
   }

   @Override
   public Filter getFilter(){
        //System.out.println("initial count"+ getItemCount());
       return new Filter() {
           @Override
           protected FilterResults performFiltering(CharSequence constraint) {
               query = constraint.toString();
               List<Course> arrTemp;
               if (query.isEmpty()) {
                   arrTemp = new LinkedList<>(originalList);
               } else {
                   arrTemp = getFilteredList(query);
               }
               FilterResults results = new FilterResults();
               results.values = arrTemp;
               //Log.i(TAG, Arrays.deepToString(arrTemp.toArray()));
               return results;
           }

           @SuppressWarnings("unchecked")
           @Override
           protected void publishResults(CharSequence constraint, FilterResults results) {
               //filteredList.clear();
               filteredList = (List<Course>) results.values;//it's fine, ignore the warning, I had to it this way, and the way that make sure the warning goes away slows down the performance, so no... maybe find out more about it in the future
               //System.out.println(" Filtered Result Size: "+ filteredList.size() + " Original Size: " + originalList.size());
               notifyDataSetChanged();
           }
       };
   }

   public List<Course> getOriginalList(){
       return originalList;
   }

   private List<Course> getFilteredList(String query){
       //filteredList.clear();//clear previous stuff in it
       List<Course> results = new LinkedList<>();
       for (Course classInfo : originalList) {
           //matching...
           if (classInfo.getNumber().toLowerCase().replace('-', ' ').contains(query.toLowerCase().replace('-', ' ')) || //course number
                   classInfo.getCrn().contains(query) || //crn
                   classInfo.getTitle().toLowerCase().contains(query.toLowerCase())) { //description
               results.add(classInfo);
           }
       }
       return results;
   }

   String getQuery(){
       return query;
   }

   Course getItemAtPos(int position){
       return filteredList.get(position);
   }

   void swapNewDataSet(List<Course> newList) {
       if(newList == null || newList.isEmpty()) return;
       //if (originalList != null && !originalList.isEmpty()) originalList.clear();
       originalList = newList;
       filteredList = newList;
       notifyDataSetChanged();
   }

}
