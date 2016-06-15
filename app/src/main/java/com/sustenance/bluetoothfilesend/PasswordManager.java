package com.sustenance.bluetoothfilesend;

import android.content.Context;
import android.content.SharedPreferences;

import org.apache.commons.codec.android.digest.DigestUtils;

public class PasswordManager {
    private static final String PREFS_NAME = "com.sustenance.bluetoothfilesend";
    private static final String PWHASH = "pwhash";
    private Context mContext;
    private SharedPreferences mPrefs;

    public PasswordManager(Context context){
        this.mContext = context;
        this.mPrefs = context.getSharedPreferences(PREFS_NAME, 0);
    }

    public String getExistingHash() {
        String hash = mPrefs.getString(PWHASH, "");
        return hash;
    }

    public boolean setNewPassword(String password) {
        String hash = PasswordManager.getHashFromPassword(password);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(PWHASH, hash);
        editor.commit();
        return true;
    }

    public boolean clearPassword() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(PWHASH, "");
        editor.commit();
        return true;
    }

    public static String getHashFromPassword(String password) {
        String md5Hash = DigestUtils.md5Hex(password);
        return md5Hash;
    }
}
