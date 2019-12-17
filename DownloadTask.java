package com.example.temp;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadTask extends AsyncTask<String, Integer, Integer> {

    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSED = 2;
    public static final int TYPE_CANCELED = 3;


    private static final String TAG = "SYSUMACH";

    private DownloadListener listener;

    private volatile static boolean isCanceled = false;

    private volatile static boolean isPaused = false;

    private int lastProgress;

    public DownloadTask(DownloadListener listener){
        this.listener = listener;
    }

    @Override
    protected Integer doInBackground(String... strings) {
        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;
        try{
            long downloadedLength = 0;
            String downloadUrl = strings[0];

            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
            String directory = Environment.getExternalStorageDirectory().getAbsolutePath();
            Log.e(TAG, directory);
            file = new File(directory + fileName);
            Log.e(TAG, file.getAbsolutePath());
            if(file.exists()){
                downloadedLength = file.length();
                Log.e(TAG, String.valueOf(downloadedLength));
            }

            long contentLength = getContentLength(downloadUrl);

            if(contentLength == 0) {
                Log.e(TAG, downloadUrl);
                return TYPE_FAILED;
            }
            else if(contentLength == downloadedLength){
                return TYPE_SUCCESS;
            }

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().addHeader("RANGE", "bytes=" + downloadedLength + "-")
                    .url(downloadUrl).build();
            Response response = client.newCall(request).execute();

            if(response != null){
                is = response.body().byteStream();
                savedFile = new RandomAccessFile(file, "rw");
                savedFile.seek(downloadedLength);
                byte[] b = new byte[1024];
                int total = 0;
                int len;

                while((len = is.read(b)) != -1){
                    if(isCanceled){
                        return TYPE_CANCELED;
                    }
                    else if(isPaused){
                        return TYPE_PAUSED;
                    }
                    else{
                        total += len;
                        savedFile.write(b, 0, len);
                        int progress = (int)((total + downloadedLength) * 100 / contentLength);
                        publishProgress(progress);
                    }
                }

                response.body().close();
                return TYPE_SUCCESS;
            }

        }catch (Exception e){

        }finally {
            try {
                if (is != null) {
                    is.close();
                }
                if (savedFile != null) {
                    savedFile.close();
                }
                if (isCanceled && file != null) {
                    file.delete();
                }
            } catch (Exception e) {

            }
        }
        return TYPE_FAILED;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];
        if(progress > lastProgress){
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    @Override
    protected void onPostExecute(Integer integer) {
        switch(integer){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
                break;
        }
    }

    public void pauseDownload(){
        isPaused = true;
        Log.e(TAG, String.valueOf(isPaused));
    }


    public void cancelDownload(){
        isCanceled = true;
    }

    private long getContentLength(String downloadUrl) {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder().url(downloadUrl).build();
        Response response;
        try{
            response = client.newCall(request).execute();
            if(response != null && response.isSuccessful()){
                long contentLength = response.body().contentLength();
                response.body().close();
                return contentLength;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return 0;
    }
}
