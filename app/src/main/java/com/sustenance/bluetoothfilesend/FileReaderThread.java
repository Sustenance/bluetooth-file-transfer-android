package com.sustenance.bluetoothfilesend;


import android.util.Log;
import android.os.Handler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class FileReaderThread extends Thread {
    protected static final int CHUNK_SIZE = 128 * 1024; //128kB

    private String fileName;
    private long numChunks;
    private long fileLength;
    private File file;
    private RandomAccessFile fileStream;


    public FileReaderThread(String fileName) {
        this.fileName = fileName;
        this.file = new File(this.fileName);
        try {
            this.fileLength = this.file.length();
            this.numChunks = this.fileLength / CHUNK_SIZE;
            if(this.fileLength % CHUNK_SIZE != 0){
                this.numChunks++;
            }
        } catch(SecurityException e){
            Log.e("ACCESS DENIED", e.getMessage());
        }
    }

    public String getFileNamePath() {
        return this.fileName;
    }

    public String getFileName() {
        return this.file.getName();
    }

    public long getFileLength() {
        return this.fileLength;
    }

    public long getNumChunks() {
        return this.numChunks;
    }

    public boolean isReady() {
        return (this.fileStream != null);
    }

    public JSONObject getMetadata() {
        JSONObject metadata = new JSONObject();
        if(this.file.canRead()) {
            try {
                metadata.put("name", this.getFileName());
                metadata.put("length", this.getFileLength());
                metadata.put("chunks", this.getNumChunks());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return metadata;
    }

    public byte[] read(long chunk) {
        byte[] buffer = new byte[CHUNK_SIZE];
        try {
            fileStream.seek(chunk * CHUNK_SIZE);
            fileStream.read(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    public void run() {
        try {
            fileStream = new RandomAccessFile(this.file, "r");
        } catch (FileNotFoundException e) {
            Log.e("File not found", e.getMessage());
            this.cancel();
        }
    }

    public void cancel() {
        interrupt();
    }
}
