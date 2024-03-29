package juw.fyp.navitalk;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import java.util.Locale;
import juw.fyp.navitalk.detection.DetectorActivity;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class CallActivity extends AppCompatActivity
        implements Session.SessionListener,
        PublisherKit.PublisherListener {

    private static String API_KEY="47555231";
    private static String SESSION_ID = "2_MX40NzU1NTIzMX5-MTY3MTIwOTc1MzYwM35zSkgzSzBBQnB4eDltRkxEby8rODFMQ1F-fn4";
    private static String TOKEN ="T1==cGFydG5lcl9pZD00NzU1NTIzMSZzaWc9YjczNmIxZWIxYjczOTUzMGFmOTZhMzc4YWJlOThmMDYwNDFlNWUxZDpzZXNzaW9uX2lkPTJfTVg0ME56VTFOVEl6TVg1LU1UWTNNVEl3T1RjMU16WXdNMzV6U2tnelN6QkJRbkI0ZURsdFJreEVieThyT0RGTVExRi1mbjQmY3JlYXRlX3RpbWU9MTY3MTIwOTc3NCZub25jZT0wLjgzNDQyNzUxMDk5MzE3NDcmcm9sZT1wdWJsaXNoZXImZXhwaXJlX3RpbWU9MTY3MzgwMTc3NCZpbml0aWFsX2xheW91dF9jbGFzc19saXN0PQ==";
    private static final String LOG_TAG = CallActivity.class.getSimpleName();
    private static final int RC_VIDEO_APP_PERM=124;

    ImageView endCall,mic;
    TextView name;
    FrameLayout container1,container2;
    DatabaseReference ref;
    String userId="",volId="";
    Boolean isAudio=true;
    Session session;
    Publisher publisher;
    Subscriber subscriber;
    SwipeListener swipeListener;
    TextToSpeech t1;
    FrameLayout frameLayout;
    LinearLayout progess;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Get data
        ref = FirebaseDatabase.getInstance().getReference().child("Users");
        volId = getIntent().getStringExtra("volId");
        userId =  getIntent().getStringExtra("uid");

        // Resource ID
        endCall= findViewById(R.id.endCall);
        mic = findViewById(R.id.micBtn);
        name = findViewById(R.id.name);
        frameLayout = findViewById(R.id.frameLayout);
        progess = findViewById(R.id.progress);

        // Request mic and camera permission
        requestPermission();

        // Assigning the volunteer's name who the blind has called
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Users/"+volId+"/userName");
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String data = dataSnapshot.getValue(String.class);
                name.setText(data);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getApplicationContext(), "canceled", Toast.LENGTH_SHORT).show();
            }
        });

        // End call button code
        endCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                          cancelCall();
            }
        });

        // Initialize swipe gesture on layout
        swipeListener = new SwipeListener(frameLayout);


        // Speech to endcall
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                    t1.speak("when you want to end your call, swipe right. Swipe left to listen again",TextToSpeech.QUEUE_ADD, null);
                }
            }
        });

        // Mute and un-mute mic code
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (publisher != null) {
                    isAudio = !isAudio;
                    if (isAudio) {
                        mic.setImageResource(R.drawable.btn_unmute_normal);
                        publisher.setPublishAudio(true);
                    } else {
                        mic.setImageResource(R.drawable.btn_mute_pressed);
                        publisher.setPublishAudio(false);
                    }
                }
            }
        });

    }//end of onCreate()

    // Permission of camera and audio
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,CallActivity.this);

    }

    // After permission granted start video call
    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    private void requestPermission(){
        String[] perms = {Manifest.permission.INTERNET,Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO};
        if(EasyPermissions.hasPermissions(this,perms)){
            container1 = findViewById(R.id.cont1);
            container2 = findViewById(R.id.cont2);
            session =  new Session.Builder(this,API_KEY,SESSION_ID).build();
            session.setSessionListener(CallActivity.this);
            session.connect(TOKEN);
        }
        else{
            EasyPermissions.requestPermissions(this,"Camera & Audio Permission are Required!",RC_VIDEO_APP_PERM,perms);
        }
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {

    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {

    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {

    }

    //Connecting API Session for Video Call
    @Override
    public void onConnected(Session session) {
        Log.i(LOG_TAG,"Session Connected");

        publisher = new Publisher.Builder(this).build();
        publisher.setPublisherListener(CallActivity.this);
        publisher.setCameraId(0);
        publisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
        container2.addView(publisher.getView());

        if(publisher.getView() instanceof GLSurfaceView){

            ((GLSurfaceView) publisher.getView()).setZOrderOnTop(true);
        }

        session.publish(publisher);
        progess.setVisibility(View.GONE);

    }

    @Override
    public void onDisconnected(Session session) {

    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        Log.i(LOG_TAG,"Stream Received");

        if(subscriber == null){
            subscriber = new Subscriber.Builder(this,stream).build();;
            session.subscribe(subscriber);
            container1.addView(subscriber.getView());
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        Log.i(LOG_TAG,"Stream Dropped");

        session.unpublish(publisher);

        if(subscriber != null){
            subscriber = null;
            container1.removeAllViews();
        }

    }

    @Override
    public void onError(Session session, OpentokError opentokError) {

    }

    // Swipe gesture code
    private class SwipeListener implements View.OnTouchListener{
        GestureDetector gestureDetector;

        SwipeListener(View view){
            int threshold= 100;
            int velocity_threshold=100;

            GestureDetector.SimpleOnGestureListener listener = new GestureDetector.SimpleOnGestureListener(){
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    float xDiff = e2.getX() - e1.getX();
                    float yDiff = e2.getY() - e1.getY();
                    try {
                        if(Math.abs(xDiff) > Math.abs(yDiff)){
                            if(Math.abs(xDiff) > threshold && Math.abs(velocityX) > velocity_threshold){
                                if(xDiff>0){
                                    // Swipe Right
                                    cancelCall();
                                }
                                else{
                                    // Swipe Left
                                    t1.speak("when you want to end your call,Swipe right.",TextToSpeech.QUEUE_ADD, null);
                                }
                                return true;
                            }
                        }

                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    return false;
                }
            };

            gestureDetector = new GestureDetector(listener);
            view.setOnTouchListener(this);
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }

    }

    // End call code
    private void cancelCall() {
        ref.child(volId).child("Ringing").removeValue();
        ref.child(userId).child("Calling").removeValue();
        session.unpublish(publisher);
        startActivity(new Intent(getApplicationContext(), DetectorActivity.class));
        finish();
    }

}