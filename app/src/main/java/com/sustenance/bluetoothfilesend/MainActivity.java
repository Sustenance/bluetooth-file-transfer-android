package com.sustenance.bluetoothfilesend;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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

import org.apache.commons.codec.android.binary.Hex;
import org.apache.commons.codec.android.digest.DigestUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    protected FileReaderThread mFileReader;
    protected ConsoleLoggerThread consoleLogger;
    protected Handler mHandler;
    protected BluetoothAdapter mBluetoothAdapter;
    protected PasswordManager mPasswordManager;
    protected boolean isBluetoothEnabled;
    protected String pass = "This is content";
    protected boolean isAuth;
    protected boolean isClientReady;
    protected long mNumChunks;
    protected long mStartTime;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.context = this;
        this.mStartTime = 0;
        this.isClientReady = false;
        this.mPasswordManager = new PasswordManager(this);
        filePath = "";
        final TextView consoleTextView = (TextView) findViewById(R.id.textView_console);
        if (consoleTextView != null) {
            consoleLogger = new ConsoleLoggerThread(consoleTextView, "Starting...");
            consoleLogger.start();
        }
        Button fileButton = (Button) findViewById(R.id.button_select_file);
        if (fileButton != null) {
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
                        consoleLogger.write("Requesting storage READ permission.");
                        ActivityCompat.requestPermissions(context,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                PERMISSIONS_REQUEST_READ_STORAGE);
                    }
                }
            });
        }
        Button sendButton = (Button) findViewById(R.id.button_send);
        if(sendButton != null) {
            sendButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if(isAuth && isClientReady){
                        if(mStartTime == 0){
                            mStartTime = new Date().getTime();
                        }
                        isClientReady = false;
                        refreshUI();
                        sendPacket(0);
                    }
                }
            });
        }
        int btPermissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH);
        if (btPermissionCheck == PackageManager.PERMISSION_GRANTED) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                consoleLogger.write("Device does not support Bluetooth.");
                Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_LONG).show();
            } else {
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivityForResult(discoverableIntent, REQUEST_ENABLE_BT);
            }
        } else {
            consoleLogger.write("Requesting Bluetooth permission.");
            ActivityCompat.requestPermissions(context,
                    new String[]{Manifest.permission.BLUETOOTH},
                    PERMISSIONS_REQUEST_BLUETOOTH);
        }

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                if (message.what == CONNECTED) {
                    consoleLogger.write("Connected to: " + message.obj.toString());
                } else if (message.what == MESSAGE_READ) {
                    if (mBTClient.isAlive()) {
                        byte[] received = (byte[]) message.obj;
                        String receivedString = new String(received);
                        try {
                            JSONObject receivedObj = new JSONObject(receivedString);
                            String status = receivedObj.getString("status");
                            switch (status) {
                                case "PASS":
                                    checkPassword(receivedObj.getString("pass"));
                                    //Log.d("PASS", receivedObj.getString("pass"));
                                    if (isAuth && mFileReader != null && mFileReader.isReady()) {
                                        JSONObject metadata = createMetadata();
                                        Log.d("META", metadata.toString(1));
                                        byte[] content = (metadata.toString()).getBytes();
                                        mBTClient.write(content);
                                    }
                                    break;
                                case "READY":
                                    try{
                                        long chunkNum = Long.parseLong(receivedObj.getString("lastChunk"));
                                        if(chunkNum == -1){ //is the first READY status, no chunks sent yet
                                            isClientReady = true;
                                            consoleLogger.write("Client ready to receive");
                                            refreshUI();
                                        } else {
                                            chunkNum++;
                                            sendPacket(chunkNum);
                                        }
                                    }catch (NumberFormatException e){
                                    }
                                    break;
                                case "RESEND":
                                    //resend requested chunk
                                    long chunkNum = Long.parseLong(receivedObj.getString("lastChunk"));
                                    sendPacket(chunkNum);
                                    break;
                                case "FINISHED":
                                    long endTime = new Date().getTime();
                                    consoleLogger.write("Finished file transfer in " +
                                            TimeUnit.MILLISECONDS.toMinutes(endTime-mStartTime) + "min, " +
                                            (TimeUnit.MILLISECONDS.toSeconds(endTime-mStartTime) -
                                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(endTime-mStartTime))) +
                                            "sec");
                                    mStartTime = 0;
                                    mBTClient.cancel();
                                    consoleLogger.write("Waiting for connection...");
                                    mBTServer = new AcceptThread();
                                    mBTServer.start();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
    }

    private void sendPacket(long chunkNum) {
        byte[] chunk = mFileReader.read(chunkNum);
        String chunkHexString = Hex.encodeHexString(chunk);
        String md5Hash = DigestUtils.md5Hex(chunkHexString);
        JSONObject packet = new JSONObject();
        try {
            packet.put("chunk", Long.toString(chunkNum));
            packet.put("payload", chunkHexString);
            packet.put("hash", md5Hash);
            //Log.d("Packet", packet.toString(1));
            if(mNumChunks != 0){
                NumberFormat nf = NumberFormat.getPercentInstance();
                consoleLogger.write("Sending " + nf.format((double)chunkNum / mNumChunks));
            }else {
                consoleLogger.write("Sending chunk #" + chunkNum);
            }
            mBTClient.write(packet.toString().getBytes());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private JSONObject createMetadata() {
        JSONObject metadata = this.mFileReader.getMetadata();
        try {
            this.mNumChunks = metadata.getInt("chunks");
            consoleLogger.write("File is " + metadata.getString("length") + " bytes");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return metadata;
    }

    private boolean manageFileReader() {
        if (this.filePath != null) {
            FileReaderThread thread = new FileReaderThread(this.filePath);
            consoleLogger.write("Starting file reader thread.");
            thread.start();
            while(!thread.isReady()){
                Log.d("FILE", "not yet ready");
            }
            this.mFileReader = thread;
            return true;
        } else {
            Log.d("No File", "Tried to start fileReader but not yet chosen file");
            return false;
        }
    }

    private boolean checkPassword(String receivedHash) {
        String pwHash = this.mPasswordManager.getExistingHash();
        if(receivedHash.equals(pwHash)){
            consoleLogger.write("Authenticated with client.");
            this.isAuth = true;
        }else {
            consoleLogger.write("Received incorrect password.");
            this.isAuth = false;
        }
        this.refreshUI();
        return this.isAuth;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    consoleLogger.write("Permission granted to READ storage.");
                    new MaterialFilePicker()
                            .withActivity(context)
                            .withFilter(Pattern.compile(".*$"))
                            .withRootPath("/")
                            .withRequestCode(FILE_CODE)
                            .withFilterDirectories(false)
                            .withHiddenFiles(true)
                            .start();
                } else {
                    consoleLogger.write("Permission denied to READ storage.");
                    Toast.makeText(this, "Must allow access to storage!", Toast.LENGTH_LONG).show();
                }
                return;
            }
            case PERMISSIONS_REQUEST_BLUETOOTH: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    consoleLogger.write("Permission granted to Bluetooth.");
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                } else {
                    consoleLogger.write("Permission denied to Bluetooth.");
                    Toast.makeText(this, "Must allow access to Bluetooth!", Toast.LENGTH_LONG).show();
                    this.finish();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CODE && resultCode == AppCompatActivity.RESULT_OK) {
            String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            if (filePath != null) {
                consoleLogger.write("Selected file: " + filePath);
                this.filePath = filePath;
                manageFileReader();
                if(isAuth && mFileReader != null && mFileReader.isReady()) {
                    JSONObject metadata = createMetadata();
                    try {
                        Log.d("META", metadata.toString(1));
                        byte[] content = (metadata.toString()).getBytes();
                        mBTClient.write(content);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e("FILE", "not ready");
                }
            }
        } else if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode != AppCompatActivity.RESULT_CANCELED) {
                consoleLogger.write("Waiting for connection...");
                isBluetoothEnabled = true;
                mBTServer = new AcceptThread();
                mBTServer.start();
                Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT).show();
            } else {
                isBluetoothEnabled = false;
                Toast.makeText(this, "Bluetooth Disabled", Toast.LENGTH_SHORT).show();
            }
        }

        this.refreshUI();
    }

    protected boolean askForBluetooth() {
        Log.e("BT Off", "Asking for Bluetooth enabled");
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        return true;
    }

    private void refreshUI() {
        TextView filePathView = (TextView) findViewById(R.id.textView_file_selected);
        if (filePathView != null) {
            filePathView.setText(this.filePath);
        }
        Button sendButton = (Button) findViewById(R.id.button_send);
        if (sendButton != null && this.isAuth && this.isClientReady && this.mFileReader != null &&
                this.mFileReader.isReady()) {
            sendButton.setTextColor(Color.GREEN);
            sendButton.setEnabled(true);
        } else if (sendButton != null){
            sendButton.setEnabled(false);
            sendButton.setTextColor(Color.RED);
        }
    }


    protected boolean manageConnectedSocket(BluetoothSocket socket) {
        this.isAuth = false;
        ConnectedThread thread = new ConnectedThread(socket);
        thread.start();
        String pwHash = this.mPasswordManager.getExistingHash();
        byte[] content = pwHash.getBytes();
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
            } catch (IOException e) {
            }
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

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
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
            } catch (IOException e) {
            }

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
            } catch (IOException e) {
            }
        }
    }

    public class ConsoleLoggerThread extends Thread {
        private String content;
        private TextView textView;

        public ConsoleLoggerThread(TextView textView) {
            this(textView, "");
        }

        public ConsoleLoggerThread(TextView textView, String content) {
            this.textView = textView;
            this.content = content;
        }

        public void run() {
            textView.setText(this.content);
        }

        public void write(String textContent) {
            this.content += "\n" + textContent;
            textView.setText(this.content);
        }

        public void cancel() {
            interrupt();
        }
    }

}
