package mx.edu.cicese.alejandro.padme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.glass.content.Intents;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;
import mx.edu.cicese.alejandro.audio.record.AudioClipListener;
import mx.edu.cicese.alejandro.audio.record.OneDetectorManyObservers;

/**
 * An {@link Activity} showing a tuggable "Hello World!" card.
 * <p/>
 * The activity_main content view is composed of a one-card {@link CardScrollView} that provides tugging
 * feedback to the user when swipe gestures are detected.
 * If your Glassware intends to intercept swipe gestures, you should set the content view directly
 * and use a {@link com.google.android.glass.touchpad.GestureDetector}.
 *
 * @see <a href="https://developers.google.com/glass/develop/gdk/touch">GDK Developer Guide</a>
 */
public class MainActivity extends Activity {

    private static final String TAG = "ClapperPlay";
    /**
     * {@link CardScrollView} to use as the activity_main content view.
     */
    private CardScrollView mCardScroller;
    /**
     * "Hello World!" {@link View} generated by {@link #buildView()}.
     */
    private View mView;
    private RecordAudioTask recordAudioTask;

    private TextView log;

    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        EventBus.getDefault().register(this);
        //mView = buildView();
        setContentView(R.layout.activity_main);
        log = (TextView) findViewById(R.id.textView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
/*
        mCardScroller = new CardScrollView(this);
        mCardScroller.setAdapter(new CardScrollAdapter() {
            @Override
            public int getCount() {
                return 1;
            }

            @Override
            public Object getItem(int position) {
                return mView;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return mView;
            }

            @Override
            public int getPosition(Object item) {
                if (mView.equals(item)) {
                    return 0;
                }
                return AdapterView.INVALID_POSITION;
            }
        });

        // Handle the TAP event.
        mCardScroller.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Plays disallowed sound to indicate that TAP actions are not supported.
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.DISALLOWED);
            }
        });
        setContentView(mCardScroller);
                */
        startTask(createAudioLogger(), "Voice Tracker");
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
//        mCardScroller.activate();
    }

    @Override
    protected void onPause() {
   //     mCardScroller.deactivate();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopAll();
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    /**
     * Builds a Glass styled "Hello World!" view using the {@link CardBuilder} class.
     */
    private View buildView() {

        LayoutInflater inflater = (LayoutInflater) getBaseContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mView = inflater.inflate(R.layout.activity_main, null);
        return mView;
    }

    private void startTask(AudioClipListener detector, String name) {
        stopAll();

        recordAudioTask = new RecordAudioTask(MainActivity.this, log, progressBar, name);
        //wrap the detector to show some output
        List<AudioClipListener> observers = new ArrayList<>();
        observers.add(new AudioClipLogWrapper(log, progressBar,this));
        observers.add(new AudioClipLogFixedWrapper(this));
        OneDetectorManyObservers wrapped =
                new OneDetectorManyObservers(detector, observers);
        recordAudioTask.execute(wrapped);
    }

    private void stopAll() {
        Log.d(TAG, "stop record audio");
        shutDownTaskIfNecessary(recordAudioTask);
    }

    private void shutDownTaskIfNecessary(final AsyncTask task) {
        if ((task != null) && (!task.isCancelled())) {
            if ((task.getStatus().equals(AsyncTask.Status.RUNNING))
                    || (task.getStatus()
                    .equals(AsyncTask.Status.PENDING))) {
                Log.d(TAG, "CANCEL " + task.getClass().getSimpleName());
                task.cancel(true);
            } else {
                Log.d(TAG, "task not running");
            }
        }
    }

    private AudioClipListener createAudioLogger() {
        AudioClipListener audioLogger = new AudioClipListener() {
            @Override
            public boolean heard(short[] audioData, int sampleRate) {
                if (audioData == null || audioData.length == 0) {
                    return true;
                }

                // returning false means the recording won't be stopped
                // users have to manually stop it via the stop button
                return false;
            }
        };

        return audioLogger;
    }

    public void onEventMainThread(AudioClipLogWrapper event) {
        log.setText(event.toString());
    }

    public void onEvent(AudioClipLogWrapper event) {
        if (event.photo) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    takePicture();
                }
            });
        }
        event.photo = false;
    }

    private void takePicture() {
        Camera c = null;

        Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
            public void onShutter() {
                // Just do nothing.
            }
        };

        Camera.PictureCallback rawPictureCallback = new Camera.PictureCallback() {
            public void onPictureTaken(byte[] arg0, Camera arg1) {
                // Just do nothing.
            }
        };

        Camera.PictureCallback jpegPictureCallback = new Camera.PictureCallback() {
            public void onPictureTaken(byte[] data, Camera arg1) {
                // Save the picture.
                try {
                    Time now = new Time();

                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    FileOutputStream out = new FileOutputStream(new File(Intents.EXTRA_PICTURE_FILE_PATH + now.toString()));
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        try {
            c = Camera.open();
            SurfaceView view = new SurfaceView(this);
            c.setPreviewDisplay(view.getHolder());
            c.startPreview();
            c.takePicture(null, null, jpegPictureCallback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
