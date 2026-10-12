package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.R;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Bill;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Bill> bills = new ArrayList<>();

    public OrderAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setBills(List<Bill> newBills) {
        bills.clear();
        if (newBills != null) bills.addAll(newBills);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return bills.size(); }
    @Override public Bill getItem(int position) { return bills.get(position); }
    @Override public long getItemId(int position) { return getItem(position).getBillId(); }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) view = inflater.inflate(R.layout.item_order, parent, false);

        Bill bill = getItem(position);
        TextView id = view.findViewById(R.id.textOrderId);
        TextView date = view.findViewById(R.id.textOrderDate);
        TextView status = view.findViewById(R.id.textOrderStatus);
        TextView total = view.findViewById(R.id.textOrderTotal);

        id.setText("Đơn hàng #" + bill.getBillId());
        date.setText("Ngày đặt: " + bill.getDateOfBill());
        status.setText("Trạng thái: " + bill.getStatus());
        total.setText("Tổng tiền: " + NumberFormat.getNumberInstance(new Locale("vi", "VN"))
                .format(bill.getTotalAmount()) + " VND");
        return view;
    }
}