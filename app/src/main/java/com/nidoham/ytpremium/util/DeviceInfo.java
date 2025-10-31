package com.nidoham.ytpremium.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Device information utility for BongoTube.
 */
public class DeviceInfo {
    
    private DeviceInfo() {}
    
    public static boolean isTelevision(Context context) {
        return isAndroidTV(context) || isFireTV(context) || isTVUiMode(context);
    }
    
    public static boolean isAndroidTV(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
    
    public static boolean isFireTV(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String model = Build.MODEL.toLowerCase();
        return manufacturer.equals("amazon") && (model.contains("aft") || model.contains("fire"));
    }
    
    public static boolean isTVUiMode(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return (config.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
    }
    
    public static boolean isMobile(Context context) {
        return !isTelevision(context);
    }
    
    public static boolean isTablet(Context context) {
        if (isTelevision(context)) return false;
        
        Configuration config = context.getResources().getConfiguration();
        double screenInches = getScreenSizeInches(context);
        boolean isLargeScreen = (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        
        return screenInches >= 7.0 && isLargeScreen;
    }
    
    public static boolean isPhone(Context context) {
        return isMobile(context) && !isTablet(context);
    }
    
    public static double getScreenSizeInches(Context context) {
        DisplayMetrics dm = getDisplayMetrics(context);
        double widthInches = dm.widthPixels / (double) dm.densityDpi;
        double heightInches = dm.heightPixels / (double) dm.densityDpi;
        return Math.sqrt(widthInches * widthInches + heightInches * heightInches);
    }
    
    public static int getScreenWidthPx(Context context) {
        return getDisplayMetrics(context).widthPixels;
    }
    
    public static int getScreenHeightPx(Context context) {
        return getDisplayMetrics(context).heightPixels;
    }
    
    public static int getScreenWidthDp(Context context) {
        DisplayMetrics dm = getDisplayMetrics(context);
        return (int) (dm.widthPixels / dm.density);
    }
    
    public static int getScreenHeightDp(Context context) {
        DisplayMetrics dm = getDisplayMetrics(context);
        return (int) (dm.heightPixels / dm.density);
    }
    
    public static float getScreenDensity(Context context) {
        return getDisplayMetrics(context).density;
    }
    
    public static int getScreenDensityDpi(Context context) {
        return getDisplayMetrics(context).densityDpi;
    }
    
    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
    
    public static boolean isPortrait(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }
    
    public static String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }
    
    public static String getDeviceModel() {
        return Build.MODEL;
    }
    
    public static String getAndroidVersion() {
        return Build.VERSION.RELEASE;
    }
    
    public static int getSdkVersion() {
        return Build.VERSION.SDK_INT;
    }
    
    public static String getDeviceType(Context context) {
        if (isFireTV(context)) return "Fire TV";
        if (isAndroidTV(context)) return "Android TV";
        if (isTVUiMode(context)) return "Television";
        if (isTablet(context)) return "Tablet";
        if (isPhone(context)) return "Phone";
        return "Mobile";
    }
    
    @Deprecated
    private static DisplayMetrics getDisplayMetrics(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getMetrics(dm);
        }
        return dm;
    }
}