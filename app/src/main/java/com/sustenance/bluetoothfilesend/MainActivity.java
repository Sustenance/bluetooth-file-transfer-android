package com.sustenance.bluetoothfilesend;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;

import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private final int PERMISSIONS_REQUEST_READ_STORAGE = 1;
    private final int FILE_CODE = 2;
    private AppCompatActivity context;
    private String filePath;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.context = this;
        filePath = "";
        Button fileButton = (Button) findViewById(R.id.button_select_file);
        if(fileButton != null) {
            fileButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        new MaterialFilePicker()
                                .withActivity(context)
                                .withFilter(Pattern.compile(".*$"))
                                .withRootPath("/")
                                .withRequestCode(FILE_CODE)
                                .withFilterDirectories(false)
                                .withHiddenFiles(true)
                                .start();
                    } else {
                        ActivityCompat.requestPermissions(context,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                PERMISSIONS_REQUEST_READ_STORAGE);
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch(requestCode) {
            case PERMISSIONS_REQUEST_READ_STORAGE: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                }else {
                    Toast.makeText(this, "Must allow access to storage!", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == FILE_CODE && resultCode == AppCompatActivity.RESULT_OK) {
            String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            if(filePath != null) {
                this.filePath = filePath;
            }
        }

        this.refreshUI();
    }

    private void refreshUI() {
        TextView filePathView = (TextView) findViewById(R.id.textView_file_selected);
        if(filePathView != null) {
            filePathView.setText(this.filePath);
        }
    }
}
