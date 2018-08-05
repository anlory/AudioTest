package com.anlory.audiotest;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaActivity extends AppCompatActivity {
    public final String MEDIA_FILE_NAME = "media.mp3";
    private final String TAG = "MediaActivity";

    public Button btn_play,record_view;
    public TextView msg_view;
    boolean isPlaying,isRecording;

    private ExecutorService mExecutorService;
    private MediaRecorder mMediaRecorder;
    private MediaPlayer mediaPlayer;
    private File mRecorderFile;
    private long startRecorderTime, stopRecorderTime;
    private  Handler mHanlder = new MyHandler();
    class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 0:
                    msg_view.setText("Record Failed !!!");
                    break;
                case 1:
                    msg_view.setText("Record Success !  "+ msg.arg1+"s");
                    break;
                case 2:
                    break;
                case 3:
                    break;
                case 4:
                    break;
                case 5:
                    break;
                default:
                    break;
            }
        }
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_main);



        initView();
        mExecutorService = Executors.newSingleThreadExecutor();

        //创建录音文件
        mRecorderFile = new File(Environment.getExternalStorageDirectory(),
                MEDIA_FILE_NAME);
        if (!mRecorderFile.getParentFile().exists()) mRecorderFile.getParentFile().mkdirs();
        try {
            mRecorderFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private  void initView(){
        btn_play = findViewById(R.id.btn_play1);
        record_view = findViewById(R.id.btn_record1);
        msg_view = findViewById(R.id.msg_view1);
        isPlaying = false;
        isRecording = false;

        btn_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isPlaying){
                    isPlaying = false;
                    stopPlayer();
                }
                else{
                    isPlaying = true;
                    playRecorder();
                }

            }
        });

        record_view.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        startRecorder();
                        break;
                    case MotionEvent.ACTION_UP:
                        stopRecorder();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    private void stopPlayer(){
        btn_play.setText("Start Play");
        isPlaying =false;
        if(mediaPlayer != null){
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer=null;
        }

    }

    private void doPlay(File audioFile) {
        try {
            //配置播放器 MediaPlayer
            mediaPlayer = new MediaPlayer();
            //设置声音文件
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            //配置音量,中等音量
            mediaPlayer.setVolume(1,1);
            //播放是否循环
            mediaPlayer.setLooping(false);

            //设置监听回调 播放完毕
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopPlayer();
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    stopPlayer();
                    Toast.makeText(MediaActivity.this,"播放失败",Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            //设置播放
            mediaPlayer.prepare();
            mediaPlayer.start();

            //异常处理，防止闪退

        } catch (Exception e) {
            e.printStackTrace();
            stopPlayer();
        }


    }

    private void playRecorder(){
        btn_play.setText("Stop Play");
        if (isPlaying) {
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mRecorderFile);
                }
            });

        } else {
            Toast.makeText(MediaActivity.this, "正在播放", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 释放上一次的录音
     */
    private void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    /**
     * 录音失败逻辑
     */

    private void recorderFail() {
        mRecorderFile = null;
        mHanlder.sendMessage(mHanlder.obtainMessage(0));
    }


    private boolean doStart() {

        try {
            //创建MediaRecorder
            mMediaRecorder = new MediaRecorder();



            //配置MediaRecorder

            //从麦克风采集
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);

            //保存文件为MP4格式
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            //所有android系统都支持的适中采样的频率
            mMediaRecorder.setAudioSamplingRate(44100);

            //通用的AAC编码格式
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            //设置音质频率
            mMediaRecorder.setAudioEncodingBitRate(96000);

            //设置文件录音的位置
            mMediaRecorder.setOutputFile(mRecorderFile.getPath());


            //开始录音
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            startRecorderTime = System.currentTimeMillis();

        } catch (Exception e) {
            Toast.makeText(MediaActivity.this, "录音失败，请重试", Toast.LENGTH_SHORT).show();
            return false;
        }


        //记录开始录音时间，用于统计时长，小于3秒中，录音不发送

        return true;
    }
    private void startRecorder(){
        record_view.setText("Speaking...");
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //释放上一次的录音
                releaseRecorder();

                //开始录音
                if (!doStart()) {
                    recorderFail();
                }
            }
        });
    }


    private boolean doStop() {
        try {
            mMediaRecorder.stop();
            stopRecorderTime = System.currentTimeMillis();
            final int second = (int) (stopRecorderTime - startRecorderTime) / 1000;
            //按住时间小于3秒钟，算作录取失败，不进行发送
            if (second < 1) return false;
            mHanlder.sendMessage(mHanlder.obtainMessage(1,second ,second));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private void stopRecorder(){
        record_view.setText("Start Record");
        //提交后台任务，停止录音
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                if (!doStop()) {
                    recorderFail();
                }
                releaseRecorder();

            }
        });
    }

    protected void onDestroy() {
        super.onDestroy();
        //当activity关闭时，停止这个线程，防止内存泄漏
        mExecutorService.shutdownNow();
        releaseRecorder();
    }
}
