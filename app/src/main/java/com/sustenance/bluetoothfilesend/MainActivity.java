package com.sustenance.bluetoothfilesend;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private final int PERMISSIONS_REQUEST_READ_STORAGE = 1;
    private final int PERMISSIONS_REQUEST_BLUETOOTH = 3;
    private final int REQUEST_ENABLE_BT = 4;
    private final int FILE_CODE = 2;
    private final int CONNECTED = 234;
    private final int MESSAGE_READ = 345;
    private AppCompatActivity context;
    private String filePath;
    protected AcceptThread mBTServer;
    protected ConnectedThread mBTClient;
    protected Handler mHandler;
    protected BluetoothAdapter mBluetoothAdapter;
    protected boolean isBluetoothEnabled;
    protected String pass = "This is content";
    protected boolean isAuth;


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
        int btPermissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH);
        if(btPermissionCheck == PackageManager.PERMISSION_GRANTED){
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_LONG).show();
            }else{
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivityForResult(discoverableIntent, REQUEST_ENABLE_BT);

//                isBluetoothEnabled = mBluetoothAdapter.isEnabled();
//                if (!isBluetoothEnabled) {
//                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
//                    startActivityForResult(discoverableIntent, REQUEST_ENABLE_BT);
//                }else {
//                    mBTServer = new AcceptThread();
//                    mBTServer.start();
//                }
            }
        } else {
            ActivityCompat.requestPermissions(context,
                    new String[]{Manifest.permission.BLUETOOTH},
                    PERMISSIONS_REQUEST_BLUETOOTH);
        }

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                if(message.what==CONNECTED){
                    Toast.makeText(context, message.obj.toString(), Toast.LENGTH_LONG).show();
                    //connectedTextView.setText(message.obj.toString());
                    //receiveData();
                }else if(message.what==MESSAGE_READ){
                    if(mBTClient.isAlive()){
                        byte[] received = (byte[])message.obj;
                        String receivedString = new String(received);
                        try {
                            JSONObject receivedObj = new JSONObject(receivedString);
                            String status = receivedObj.getString("status");
                            switch (status) {
                                case "PASS":
                                    Log.d("PASS", receivedObj.getString("pass"));
                                    if(checkPassword(receivedObj.getString("pass"))){
                                        //send metadata
                                        JSONObject metadata = new JSONObject();
                                        metadata.put("name", "testFile.txt");
                                        metadata.put("length", 10245);
                                        metadata.put("chunks", 456);

                                        byte[] content = (metadata.toString()).getBytes();
                                        mBTClient.write(content);
                                    }
                                    break;
                                case "READY":
                                    //send chunk
                                    break;
                                case "RESEND":
                                    //resend requested chunk
                                    break;

                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
    }

    private boolean checkPassword(String pass) {
        this.isAuth = pass.equals(this.pass);
        return this.isAuth;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch(requestCode) {
            case PERMISSIONS_REQUEST_READ_STORAGE: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    new MaterialFilePicker()
                            .withActivity(context)
                            .withFilter(Pattern.compile(".*$"))
                            .withRootPath("/")
                            .withRequestCode(FILE_CODE)
                            .withFilterDirectories(false)
                            .withHiddenFiles(true)
                            .start();
                }else {
                    Toast.makeText(this, "Must allow access to storage!", Toast.LENGTH_LONG).show();
                }
                return;
            }
            case PERMISSIONS_REQUEST_BLUETOOTH: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                } else {
                    Toast.makeText(this, "Must allow access to Bluetooth!", Toast.LENGTH_LONG).show();
                    this.finish();
                }
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
        } else if(requestCode == REQUEST_ENABLE_BT){
            if (resultCode != AppCompatActivity.RESULT_CANCELED){
                isBluetoothEnabled = true;
                mBTServer = new AcceptThread();
                mBTServer.start();
                Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT).show();
            }else{
                isBluetoothEnabled = false;
                Toast.makeText(this, "Bluetooth Disabled", Toast.LENGTH_SHORT).show();
            }
        }

        this.refreshUI();
    }

    protected boolean askForBluetooth(){
        Log.e("BT Off", "Asking for Bluetooth enabled");
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        return true;
    }

    private void refreshUI() {
        TextView filePathView = (TextView) findViewById(R.id.textView_file_selected);
        if(filePathView != null) {
            filePathView.setText(this.filePath);
        }
    }




    protected boolean manageConnectedSocket(BluetoothSocket socket){
        this.isAuth = false;
        ConnectedThread thread = new ConnectedThread(socket);
        thread.start();
        byte[] content = ("This is content").getBytes();
        thread.write(content);

        //Create ConnectedThread and run it
        return true;
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(getString(R.string.service_name),
                        UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                    this.cancel();
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) { }
        }
    }



    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
                /*
                if(mBTServer.isAlive()){
                    mBTServer.cancel();
                }
                */
                mBTClient = this;
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {

            Log.d("Connected", "Thread running");
            Message message = mHandler.obtainMessage(CONNECTED, mmSocket.getRemoteDevice().getName());
            message.sendToTarget();
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    //receiveData();

                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI activity

                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                mmOutStream.flush();
            } catch (IOException e) {
                Log.e("WriteFlush", e.getMessage());
            }

        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

}
