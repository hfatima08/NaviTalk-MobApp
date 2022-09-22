package juw.fyp.navitalk;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
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

import org.checkerframework.checker.units.qual.C;

import juw.fyp.navitalk.detection.DetectorActivity;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class CallActivity extends AppCompatActivity
        implements Session.SessionListener,
        PublisherKit.PublisherListener {

    private static String API_KEY="47555231";
    private static String SESSION_ID = "2_MX40NzU1NTIzMX5-MTY2MzY5NDE2NjA3NH5OR1ZkdWkwem5TYkR5SDBLbndick8yUyt-fg";
    private static String TOKEN ="T1==cGFydG5lcl9pZD00NzU1NTIzMSZzaWc9MzVlZWM4OTU1YzcxMDZhMmVjNmZlMmE1OTc1ZTMwMGE2ODMwOGY0ZjpzZXNzaW9uX2lkPTJfTVg0ME56VTFOVEl6TVg1LU1UWTJNelk1TkRFMk5qQTNOSDVPUjFaa2RXa3dlbTVUWWtSNVNEQkxibmRpY2s4eVV5dC1mZyZjcmVhdGVfdGltZT0xNjYzNjk0MTg2Jm5vbmNlPTAuODg4OTE4MzM2NzE2NjMyOSZyb2xlPXB1Ymxpc2hlciZleHBpcmVfdGltZT0xNjYzNjk3Nzg3JmluaXRpYWxfbGF5b3V0X2NsYXNzX2xpc3Q9";
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        ref = FirebaseDatabase.getInstance().getReference().child("Users");

        volId = getIntent().getStringExtra("volId");

        endCall= findViewById(R.id.endCall);
        mic = findViewById(R.id.micBtn);
        name = findViewById(R.id.name);

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

        endCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                            ref.child(volId).child("Ringing").removeValue();
                            ref.child(userId).child("Calling").removeValue();


                                session.unpublish(publisher);

                            startActivity(new Intent(getApplicationContext(), DetectorActivity.class));
                            finish();
            }
        });

        requestPermission();

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
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,CallActivity.this);

    }

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
            EasyPermissions.requestPermissions(this,"Need Camera & Mic Permissions ....",RC_VIDEO_APP_PERM,perms);
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

    @Override
    public void onConnected(Session session) {
        Log.i(LOG_TAG,"Session Coneected");

        publisher = new Publisher.Builder(this).build();
        publisher.setPublisherListener(CallActivity.this);
        publisher.setCameraId(0);
        container2.addView(publisher.getView());

        if(publisher.getView() instanceof GLSurfaceView){

            ((GLSurfaceView) publisher.getView()).setZOrderOnTop(true);
        }

        session.publish(publisher);
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
}