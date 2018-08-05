package com.anlory.audiotest;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioActivity extends AppCompatActivity implements View.OnClickListener{
    private final String TAG = "AudioActivity";
    private final String START_RECORD = "Start Record";
    private final String STOP_RECORD = "Stop Record";
    private final String START_PLAY = "Start Play";
    private final String STOP_PLAY = "Stop Play";
    private final String AUDIO_FILE_NAME = "/audiodemo/audio.pcm";


    public Button btn_play,btn_record;
    public TextView msg_view;
    private volatile boolean isRecordIng = false;
    private volatile boolean isPlayIng = false;
    private ExecutorService mExecutorService;

    private long startRecorderTime, stopRecorderTime;
    private AudioRecord mAudioRecord;
    private FileOutputStream mFileOutputStream;
    private File mAudioRecordFile;
    private byte[] mBuffer;
    private final int BUFFER_SIZE = 2048;
    private Handler mHandler = new myHanler();

    public  class myHanler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 0:
                    msg_view.setText("play Failed !!!");
                    break;
                case 1:
                    msg_view.setText("Record Failed !!!");
                    break;
                case 2:
                    msg_view.setText("Record Success! + : "+msg.arg1+"s");
                    break;
                case 3:
                    msg_view.setText("Recording ...");
                    break;
                case 4:
                    msg_view.setText("Play ...");
                    break;
                case 5:
                    msg_view.setText("Play End!");
                    btn_play.setText(START_PLAY);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_main);

        initView();
        mBuffer = new byte[BUFFER_SIZE];
        mExecutorService = Executors.newSingleThreadExecutor();

        //创建录音文件
        mAudioRecordFile = new File(Environment.getExternalStorageDirectory(),AUDIO_FILE_NAME);
        if(!mAudioRecordFile.getParentFile().exists()){
            boolean  ret = mAudioRecordFile.getParentFile().mkdirs();
            Log.d(TAG, "MAKE DIRS RET: "+ret);
        }
        Log.d(TAG, "mAudioRecordFile:"+ mAudioRecordFile.getPath());
        try {
            mAudioRecordFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private  void initView(){
        btn_play = findViewById(R.id.btn_play);
        btn_record = findViewById(R.id.btn_record);
        msg_view = findViewById(R.id.msg_view);
        btn_record.setOnClickListener(this);
        btn_play.setOnClickListener(this);
    }
    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_record:
                Log.d(TAG,"Audio Recorder is Press !");
                if(isRecordIng){
                    btn_record.setText(START_RECORD);
                    isRecordIng = false;
                }else{
                    btn_record.setText(STOP_RECORD);
                    isRecordIng = true;
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            startRecord();
                        }
                    });
                }
                break;
            case R.id.btn_play:
                if(isPlayIng){
                    isPlayIng = false;
                    btn_play.setText(START_PLAY);
                }else{
                    isPlayIng = true;
                    btn_play.setText(STOP_PLAY);
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            doPlay(mAudioRecordFile);
                        }
                    });

                }
                break;
        }
    }

    private void doPlay(File audioFile){
        if(audioFile != null){
            //配置播放器
            //音乐类型，扬声器播放
            int streamType = AudioManager.STREAM_MUSIC;

            //录音时采用的采样频率，所以播放时同样的采样频率
            int sampleRate = 44100;

            //单声道，和录音时设置的一样
            int channelConfig = AudioFormat.CHANNEL_OUT_MONO;

            //录音时使用16bit，所以播放时同样采用该方式
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

            //流模式
            int mode = AudioTrack.MODE_STREAM;
            //计算最小buffer大小
            //int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,channelConfig,audioFormat);
            //构造AudioTrack  不能小于AudioTrack的最低要求，也不能小于我们每次读的大小
            //AudioTrack mAudioTrack = new AudioTrack(streamType,sampleRate,channelConfig,audioFormat, Math.max(minBufferSize,BUFFER_SIZE),mode);
            AudioTrack mAudioTrack = new AudioTrack(streamType,sampleRate,channelConfig,audioFormat, BUFFER_SIZE,mode);

            FileInputStream inputStream = null;
            //从文件流读数据
            try {
                //循环读数据，写到播放器去播放
                inputStream = new FileInputStream(audioFile);
                int read;
                //Log.d(TAG,"Playing ...");
                mHandler.sendMessage(mHandler.obtainMessage(4));
                mAudioTrack.play();
                //只要没读完，循环播放
                while ((read = inputStream.read(mBuffer)) > 0 && isPlayIng){
                    int ret = mAudioTrack.write(mBuffer,0,read);
                    //Log.d(TAG,"Playing ... read :" + read + " ret :"+ret);
                    switch (ret){
                        case AudioTrack.ERROR_INVALID_OPERATION:
                        case AudioTrack.ERROR_BAD_VALUE:
                        case AudioTrack.ERROR_DEAD_OBJECT:
                            playFail();
                            return;
                        default:
                            break;

                    }
                }
                mHandler.sendMessage(mHandler.obtainMessage(5));
            } catch (Exception e) {
                e.printStackTrace();
                //读取失败
                playFail();
            }finally {
                isPlayIng = false;
                if(inputStream != null ){
                    //关闭文件输入流
                    closeStream(inputStream);
                }
                //播放器释放
                resetQuietly(mAudioTrack);
            }
        }
    }

    private void closeStream(FileInputStream in){
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void resetQuietly(AudioTrack at){
        at.stop();
        at.release();
    }
    private void playFail(){
        mHandler.sendMessage(mHandler.obtainMessage(0));
    }
    private void startRecord(){
        if(!doStart())
            recorderFail();
    }
    private void recorderFail(){
        mHandler.sendMessage(mHandler.obtainMessage(1));
    }
    //录音
    private boolean doStart(){
        try {
            //记录开始录音时间
            startRecorderTime = System.currentTimeMillis();

            //创建文件输出流
            mFileOutputStream = new FileOutputStream(mAudioRecordFile);

            //配置AudioRecord
            int audioSource = MediaRecorder.AudioSource.MIC;

            //所有android系统都支持，采样率
            int sampleRate = 44100;

            //单声道输入
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;

            //PCM_16是所有android系统都支持的
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

            //计算AudioRecord内部buffer最小
            //int miniBufferSize = getMinBufferSize(sampleRate,channelConfig,audioSource);

            //mAudioRecord = new AudioRecord(audioSource,sampleRate,channelConfig,audioFormat,Math.max(miniBufferSize,BUFFER_SIZE));
            mAudioRecord = new AudioRecord(audioSource,sampleRate,channelConfig,audioFormat,BUFFER_SIZE);

            //开始录音
            mAudioRecord.startRecording();
            mHandler.sendMessage(mHandler.obtainMessage(3));

            //循环读取数据，写入输出流中
            while (isRecordIng){
                Log.d(TAG,"Recording...");
                int read = mAudioRecord.read(mBuffer,0,BUFFER_SIZE);
                if(read < 0 ){
                    Toast.makeText(AudioActivity.this,"Audio Read Failed!",Toast.LENGTH_SHORT);
                    return false;
                }else {
                    mFileOutputStream.write(mBuffer,0,read);
                }
            }
            //退出循环，停止录音，释放资源
            stopRecorder();
        } catch (IOException e) {
            e.printStackTrace();
            return  false;
        }finally {
            if(mAudioRecord != null){
                mAudioRecord.release();
            }
        }
        return  true;

    }
    private void stopRecorder(){
        isRecordIng = false;
        if(!doStop()) recorderFail();
    }

    private boolean doStop(){
        //停止录音，关闭文件输出流
        mAudioRecord.stop();
        mAudioRecord.release();
        mAudioRecord = null;

        stopRecorderTime = System.currentTimeMillis();
        //记录结束时间，统计录音时长
        final int time = (int)(stopRecorderTime - startRecorderTime) /1000;
        if(time > 1){
            mHandler.sendMessage(mHandler.obtainMessage(2,time,time));
        }else{
            recorderFail();
            return false;
        }
        return  true;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mExecutorService != null){
            mExecutorService.shutdownNow();
        }
        if(mAudioRecord != null){
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }

    }
}
