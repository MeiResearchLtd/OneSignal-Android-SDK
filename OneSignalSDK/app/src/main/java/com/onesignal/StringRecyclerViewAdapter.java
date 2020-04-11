package com.onesignal;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.onesignal.example.R;

import org.json.JSONArray;
import org.json.JSONException;

public class StringRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private LayoutInflater layoutInflater;

    private Context context;

    private JSONArray ids;

    public StringRecyclerViewAdapter(Context context, JSONArray ids) {
        this.context = context;

        this.ids = ids;

        layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
        View view = layoutInflater.inflate(com.onesignal.example.R.layout.string_recycler_view_child_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        try {
            ((ViewHolder) holder).setData(position, ids.getString(position));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return ids.length();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView stringTextView;

        private String id;

        ViewHolder(View itemView) {
            super(itemView);

            stringTextView = itemView.findViewById(R.id.string_recycler_view_child_text_view);
        }

        private void setData(int position, String id) {
            this.id = id;
            populateInterfaceElements(position);
        }

        private void populateInterfaceElements(final int position) {
            stringTextView.setText(id);
        }

    }

    public void setIds(JSONArray ids) {
        this.ids = ids;
        notifyDataSetChanged();
    }

}