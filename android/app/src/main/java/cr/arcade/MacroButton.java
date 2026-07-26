package com.cr.arcade;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;

public class MacroButton {
    private MainActivity activity;
    private WindowManager windowManager;
    private FrameLayout container;
    private WindowManager.LayoutParams params;
    private Button button;
    private String macroName;
    private ArrayList<RecordedAction> actions;
    private boolean isFixed = false;
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    private int color = 0xFF00FF00; // Зеленый по умолчанию
    private int size = 80;
    
    public interface OnClickCallback {
        void onMacroExecuted();
    }
    
    private OnClickCallback callback;
    
    public MacroButton(MainActivity activity, String name, ArrayList<RecordedAction> actions, int color, int size) {
        this.activity = activity;
        this.macroName = name;
        this.actions = actions;
        this.color = color;
        this.size = size;
        this.windowManager = (WindowManager) activity.getSystemService(WindowManager.class);
        createButton();
    }
    
    private void createButton() {
        container = new FrameLayout(activity);
        container.setBackgroundColor(0x00000000);
        
        button = new Button(activity);
        button.setText(macroName);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(color);
        bg.setStroke(3, Color.WHITE);
        button.setBackground(bg);
        
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(size, size);
        button.setLayoutParams(btnParams);
        
        button.setOnClickListener(v -> {
            if (callback != null) {
                callback.onMacroExecuted();
            }
            activity.executeMacro(actions);
        });
        
        container.addView(button);
        
        // Перетаскивание
        container.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isFixed) return false;
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        initialX = params.x;
                        initialY = params.y;
                        isDragging = false;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            params.x = initialX + (int) dx;
                            params.y = initialY + (int) dy;
                            if (windowManager != null) {
                                windowManager.updateViewLayout(container, params);
                            }
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        return isDragging;
                }
                return false;
            }
        });
        
        int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
        
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;
        
        windowManager.addView(container, params);
    }
    
    public void setCallback(OnClickCallback callback) {
        this.callback = callback;
    }
    
    public void setFixed(boolean fixed) {
        this.isFixed = fixed;
        if (fixed) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (windowManager != null && container != null) {
            windowManager.updateViewLayout(container, params);
        }
    }
    
    public void remove() {
        try {
            if (container != null && windowManager != null) {
                windowManager.removeView(container);
                container = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void setPosition(int x, int y) {
        params.x = x;
        params.y = y;
        if (windowManager != null && container != null) {
            windowManager.updateViewLayout(container, params);
        }
    }
    
    public String getMacroName() {
        return macroName;
    }
    
    public void setColor(int color) {
        this.color = color;
        android.graphics.drawable.GradientDrawable bg = (android.graphics.drawable.GradientDrawable) button.getBackground();
        bg.setColor(color);
    }
    
    public void setSize(int size) {
        this.size = size;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) button.getLayoutParams();
        params.width = size;
        params.height = size;
        button.setLayoutParams(params);
    }
  }
