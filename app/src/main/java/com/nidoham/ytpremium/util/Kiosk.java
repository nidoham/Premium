package com.nidoham.ytpremium.util;

public class Kiosk {
    
    public static String getCategoryTitle(int position) {
        switch (position) {
            case 0:
                return "all";
            case 1:
                return "gaming";
            case 2:
                return "sports";
            case 3:
                return "music";
            case 4:
                return "news";
            default:
                return "all";
        }
    }
    
    public static int getCategoryPosition(String title) {
        switch (title.toLowerCase()) {
            case "all":
                return 0;
            case "gaming":
                return 1;
            case "sports":
                return 2;
            case "music":
                return 3;
            case "news":
                return 4;
            default:
                return 0;
        }
    }
}