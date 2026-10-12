package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.cart;

import android.content.Context;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.MenuDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.CartItem;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory shopping cart. Cart contents are temporary until checkout.
 * Stock is checked against SQLite whenever an item is added or its quantity changes.
 */
public class CartManager {
    private final MenuDao menuDao;
    private final Map<Integer, CartItem> items = new LinkedHashMap<>();

    public CartManager(Context context) {
        menuDao = new MenuDao(context);
    }

    /**
     * Adds quantity of a menu item. If the item is already in the cart,
     * quantity is increased. Returns false for invalid IDs, quantities,
     * unavailable items, or quantities above current stock.
     */
    public synchronized boolean addItem(int menuId, int quantity) {
        if (menuId <= 0 || quantity <= 0) {
            return false;
        }

        Menu menu = menuDao.getMenuById(menuId);
        if (menu == null || menu.getStockQuantity() <= 0) {
            return false;
        }

        CartItem existing = items.get(menuId);
        int newQuantity = quantity + (existing == null ? 0 : existing.getQuantity());
        if (newQuantity > menu.getStockQuantity()) {
            return false;
        }

        if (existing == null) {
            items.put(menuId, new CartItem(menu, quantity));
        } else {
            existing.setQuantity(newQuantity);
        }
        return true;
    }

    /** Sets an existing cart line's quantity; quantity must be within current stock. */
    public synchronized boolean updateQuantity(int menuId, int quantity) {
        CartItem existing = items.get(menuId);
        if (existing == null || quantity <= 0) {
            return false;
        }

        Menu latestMenu = menuDao.getMenuById(menuId);
        if (latestMenu == null || quantity > latestMenu.getStockQuantity()) {
            return false;
        }

        existing.setQuantity(quantity);
        return true;
    }

    public synchronized boolean removeItem(int menuId) {
        return items.remove(menuId) != null;
    }

    public synchronized void clear() {
        items.clear();
    }

    /** Returns a snapshot list so callers cannot add/remove lines in the internal map. */
    public synchronized List<CartItem> getItems() {
        return new ArrayList<>(items.values());
    }

    public synchronized int getItemCount() {
        int count = 0;
        for (CartItem item : items.values()) {
            count += item.getQuantity();
        }
        return count;
    }

    public synchronized double getTotalAmount() {
        double total = 0;
        for (CartItem item : items.values()) {
            total += item.getSubtotal();
        }
        return total;
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    public void close() {
        menuDao.close();
    }
}
