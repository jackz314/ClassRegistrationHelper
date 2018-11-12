package com.jackz314.classregistrationhelper;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

class ListRecyclerAdapter extends RecyclerView.Adapter<ListRecyclerAdapter.ListRecyclerViewHolder> implements Filterable {

   private List<String[]> list;
   private List<String[]>  arrayListFiltered;

    /**
     * Creates new List Recycler Adapter
     *
     * @param arrayList list with each element being an array
     *                  array pos 0: course number
     *                  array pos 1: course description/title
     *                  array pos 2: course crn number
     *                  array pos 3: course available seats count
     */
    ListRecyclerAdapter(List<String[]> arrayList){
       this.list = arrayList;
       arrayListFiltered = arrayList;
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
        if(!arrayListFiltered.isEmpty() && arrayListFiltered.size() > position){
            final String classNumber = arrayListFiltered.get(position)[0];
            final String classDescription = arrayListFiltered.get(position)[1];
            final String crnNumber = arrayListFiltered.get(position)[2];
            final String availableSeats = arrayListFiltered.get(position)[3];
            holder.courseNumberTxt.setText(classNumber);
            holder.courseDescriptionTxt.setText(classDescription);
            holder.courseCrnTxt.setText(crnNumber);
            holder.courseAvailableSeatsTxt.setText(availableSeats);
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
       return list.size();
   }

   public int getFilteredItemCount(){
        return arrayListFiltered.size();
   }

   @Override
   public Filter getFilter(){
        //System.out.println("initial count"+ getItemCount());
       return new Filter() {
           @Override
           protected FilterResults performFiltering(CharSequence constraint) {
               String query = constraint.toString();
               List<String[]> arrTemp = new ArrayList<>();
               if (query.isEmpty()) {
                   arrTemp = list;
               } else {
                   //arrayListFiltered.clear();//clear previous stuff in it
                   for (String[] classInfo : list) {
                       //matching...
                       if (classInfo[0].contains(query) || //number
                               classInfo[2].contains(query) || //crn
                               classInfo[1].toLowerCase().contains(query.toLowerCase())) { //description
                           arrTemp.add(classInfo);
                       }
                   }
               }
               FilterResults filterResults = new FilterResults();
               filterResults.values = arrTemp;
               return filterResults;
           }

           @Override
           protected void publishResults(CharSequence constraint, FilterResults results) {
               arrayListFiltered = (List<String[]>) results.values;//it's fine, ignore the warning, I had to it this way, and the way that make sure the warning goes away slows down the performance, so no... maybe find out more about it in the future
               System.out.println(" Filtered Result Size: "+arrayListFiltered.size() + " Original Size: " + list.size());
               notifyDataSetChanged();
           }
       };
   }

   public String[] getItemAtPos(int position){
       return arrayListFiltered.get(position);
   }

   public void swapNewDataSet(List<String[]> newList)
   {
       if(newList == null || newList.isEmpty()) return;
       if (list != null && list.size()>0) list.clear();
       list = newList;
       notifyDataSetChanged();
   }

}
