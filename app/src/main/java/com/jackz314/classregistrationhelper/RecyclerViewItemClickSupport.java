package com.jackz314.classregistrationhelper;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by zhang on 2017/9/13.
 *
 * Remember to add "ids.xml" to values folder to use this support class
 *
 * Content in "ids.xml":
 *
 * <?xml version="1.0" encoding="utf-8"?>
 * <resources>
 *     <item name="item_click_support" type="id" />
 * </resources>
 *
 */

 class RecyclerViewItemClickSupport {
        private final RecyclerView mRecyclerView;
        private OnItemClickListener mOnItemClickListener;
        private OnItemLongClickListener mOnItemLongClickListener;
        private View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mOnItemClickListener != null) {
                    RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(view);
                    mOnItemClickListener.onItemClicked(mRecyclerView, holder.getAdapterPosition(), view);
                }
            }
        };
        private View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (mOnItemLongClickListener != null) {
                    RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(view);
                    return mOnItemLongClickListener.onItemLongClicked(mRecyclerView, holder.getAdapterPosition(), view);
                }
                return false;
            }
        };
        private RecyclerView.OnChildAttachStateChangeListener mAttachListener
                = new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                if (mOnItemClickListener != null) {
                    view.setOnClickListener(mOnClickListener);
                }
                if (mOnItemLongClickListener != null) {
                    view.setOnLongClickListener(mOnLongClickListener);
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {

            }
        };

        private RecyclerViewItemClickSupport(RecyclerView recyclerView) {
            mRecyclerView = recyclerView;
            mRecyclerView.setTag(R.id.item_click_support, this);
            mRecyclerView.addOnChildAttachStateChangeListener(mAttachListener);
        }

        public static RecyclerViewItemClickSupport addTo(RecyclerView view) {
            RecyclerViewItemClickSupport support = (RecyclerViewItemClickSupport) view.getTag(R.id.item_click_support);
            if (support == null) {
                support = new RecyclerViewItemClickSupport(view);
            }
            return support;
        }

        public static RecyclerViewItemClickSupport removeFrom(RecyclerView view) {
            RecyclerViewItemClickSupport support = (RecyclerViewItemClickSupport) view.getTag(R.id.item_click_support);
            if (support != null) {
                support.detach(view);
            }
            return support;
        }

        public RecyclerViewItemClickSupport setOnItemClickListener(OnItemClickListener listener) {
            mOnItemClickListener = listener;
            return this;
        }

        public RecyclerViewItemClickSupport setOnItemLongClickListener(OnItemLongClickListener listener) {
            mOnItemLongClickListener = listener;
            return this;
        }

        private void detach(RecyclerView view) {
            view.removeOnChildAttachStateChangeListener(mAttachListener);
            view.setTag(R.id.item_click_support, null);
        }

        public interface OnItemClickListener {

            void onItemClicked(RecyclerView recyclerView, int position, View v);
        }

        public interface OnItemLongClickListener {

            boolean onItemLongClicked(RecyclerView recyclerView, int position, View v);
        }
}
