/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package juw.fyp.navitalk.detection;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Adapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import juw.fyp.navitalk.Adapter.UserAdapter;
import juw.fyp.navitalk.CallActivity;
import juw.fyp.navitalk.CameraScreen;
import juw.fyp.navitalk.ConnectingActivity;
import juw.fyp.navitalk.R;
import juw.fyp.navitalk.RoleScreen;
import juw.fyp.navitalk.detection.env.ImageUtils;
import juw.fyp.navitalk.detection.env.Logger;
import juw.fyp.navitalk.models.Users;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

import static androidx.recyclerview.widget.RecyclerView.*;

public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener,
        Camera.PreviewCallback{
  private static final Logger LOGGER = new Logger();
  //FloatingActionButton logout;
  GoogleSignInClient signInClient;
  GoogleSignInOptions signInOptions;
  TextView code;
  FirebaseAuth auth;
  UserAdapter userAdapter;
  ArrayList<Users> Alist;
  RecyclerView rv;
  TextView vol;
  // Button call;
   RelativeLayout relativeLayout;
  SwipeListener swipeListener;
  TextToSpeech t1;

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private boolean debug = false;
  private Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;

  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior<LinearLayout> sheetBehavior;

  protected ImageView bottomSheetArrowImageView;

  DatabaseReference reference;
  String data,uid;
  Intent intent;

  ArrayList<String> vol_list = new ArrayList<String>();

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.tfe_od_activity_camera);


    auth = FirebaseAuth.getInstance();
    uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

    signInOptions= new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build();
    signInClient = GoogleSignIn.getClient(this,signInOptions);

    reference = FirebaseDatabase.getInstance().getReference().child("Users");

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }

    relativeLayout = findViewById(R.id.layout);
    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
    gestureLayout = findViewById(R.id.gesture_layout);
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
    bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);
    code = findViewById(R.id.code);
    rv = findViewById(R.id.list);
    Alist = new ArrayList<>();
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    rv.setLayoutManager(layoutManager);
    userAdapter = new UserAdapter(Alist,this);
    rv.setAdapter(userAdapter);
    vol = findViewById(R.id.vol);
 //   call = findViewById(R.id.btn_call);


//    call.setOnClickListener(this);

    getCode();
    getVolunteer();

    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
              gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            } else {
              gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
            int height = gestureLayout.getMeasuredHeight();

            sheetBehavior.setPeekHeight(height);
          }
        });
    sheetBehavior.setHideable(false);

    sheetBehavior.setBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
              case BottomSheetBehavior.STATE_HIDDEN:
                break;
              case BottomSheetBehavior.STATE_EXPANDED:
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.arrow_down);
                }
                break;
              case BottomSheetBehavior.STATE_COLLAPSED:
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.arrow_up);
                }
                break;
              case BottomSheetBehavior.STATE_DRAGGING:
                break;
              case BottomSheetBehavior.STATE_SETTLING:
                bottomSheetArrowImageView.setImageResource(R.drawable.arrow_up);
                break;
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });

    swipeListener = new SwipeListener(relativeLayout);

    t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int status) {
        if (status != TextToSpeech.ERROR) {
          t1.setLanguage(Locale.US);
          t1.speak("You are on the detection Screen, swipe left to listen the features and swipe right to say something.",TextToSpeech.QUEUE_ADD, null);
        }
      }
    });

   intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

  }//end oncreate()

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
                  //textView.setText("swiped right");

                  t1.speak("Start speaking",TextToSpeech.QUEUE_ADD, null);
                  new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                      intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Start Speaking");
                      if(intent.resolveActivity(getPackageManager())!=null){
                        startActivityForResult(intent,10);
                      }else{
                        t1.speak("Your device does not support speech input", TextToSpeech.QUEUE_ADD, null);
                      }

                    }
                  }, 1000);

                }
                else{
             //     textView.setText("swiped left");
                  t1.speak("If you want to detect an object say detect and the object's name for example detect glass or detect chair. Anytime you require visual assistance, say video call. Simply say logout to get logged out from your account.", TextToSpeech.QUEUE_ADD, null);
                  t1.speak("Swipe left to listen again and swipe right to say something.", TextToSpeech.QUEUE_ADD, null);
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

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if(resultCode == RESULT_OK && data != null) {
      ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
    switch(requestCode){
        case 10:
          String cmd = result.get(0);
          switch (cmd) {
            case "log out":
              SignOut();
              break;

            case "video call":
              t1.speak("who do you want to call? Tell me the number for the respective volunteer when i say start speaking", TextToSpeech.QUEUE_ADD, null);
              //Toast.makeText(this, Alist.get(0).getUserName(), Toast.LENGTH_SHORT).show();
              for (Users vol : Alist) {
                int i = 1;
                t1.speak("say"+ i + "for" + vol.getUserName(), TextToSpeech.QUEUE_ADD, null);
                i++;
              }

              new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                  t1.speak("start speaking", TextToSpeech.QUEUE_ADD, null);
                  new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                      intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "tell the number");
                      if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(intent, 20);

                      } else {
                        t1.speak("Your device does not support speech input", TextToSpeech.QUEUE_ADD, null);
                      }
                    }
                  }, 2000);
                }
              }, 9000);


          }
    break;
        case 20:
          String cmd2 = result.get(0);
         int num = Integer.parseInt(cmd2);
         num--;
          Intent intent = new Intent(getApplicationContext(), ConnectingActivity.class);
          String id= (String) Alist.get(num).getUserId();
          t1.speak("Calling"+Alist.get(num).getUserName(), TextToSpeech.QUEUE_ADD, null);
          intent.putExtra("vol",id);
          startActivity(intent);
      }
    }

    else{
      t1.speak("Sorry, I didn't hear anything", TextToSpeech.QUEUE_ADD, null);
    }



  }
  private void SignOut() {
    signInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
      @Override
      public void onComplete(@NonNull Task<Void> task) {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(getApplicationContext(), RoleScreen.class);
        finishAffinity();
        startActivity(intent);

      }
    });
  }

  private void getCode() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Users/"+user.getUid()+"/code");
    reference.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        data = dataSnapshot.getValue(String.class);
        code.setText("ASSISTANCE CODE: "+data);
      }
      @Override
      public void onCancelled(@NonNull DatabaseError error) {
        Toast.makeText(getApplicationContext(), "canceled", Toast.LENGTH_SHORT).show();
      }
    });
  }

  private void getVolunteer() {

    DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Users");
    reference.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(@NonNull DataSnapshot snapshot) {
        Alist.clear();
        for(DataSnapshot dataSnapshot: snapshot.getChildren()){
        Users users = dataSnapshot.getValue(Users.class);

            if(users.getRole().equals("Volunteer") && users.getCode().equals(data)){
              vol.setText("VOLUNTEERS");
              Alist.add(users);


          }



        }
        userAdapter.notifyDataSetChanged();
      }


      @Override
      public void onCancelled(@NonNull DatabaseError error) {

      }
    });

    if(Alist.isEmpty()){
      vol.setText("No Volunteers Registered!");
    }

  }

//  public void onClick(View view){
//    Intent intent = new Intent(getApplicationContext(), ConnectingActivity.class);
//    String vol = vol_list.get(0);
//    intent.putExtra("volList",vol);
//    startActivity(intent);
//
//  }

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  protected int getLuminanceStride() {
    return yRowStride;
  }

  protected byte[] getLuminance() {
    return yuvBytes[0];
  }

  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    if (isProcessingFrame) {
      LOGGER.w("Dropping frame!");
      return;
    }

    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
        new Runnable() {
          @Override
          public void run() {
            ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
          }
        };

    postInferenceCallback =
        new Runnable() {
          @Override
          public void run() {
            camera.addCallbackBuffer(bytes);
            isProcessingFrame = false;
          }
        };
    processImage();
  }

  /** Callback for Camera2 API */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
          new Runnable() {
            @Override
            public void run() {
              ImageUtils.convertYUV420ToARGB8888(
                  yuvBytes[0],
                  yuvBytes[1],
                  yuvBytes[2],
                  previewWidth,
                  previewHeight,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes);
            }
          };

      postInferenceCallback =
          new Runnable() {
            @Override
            public void run() {
              image.close();
              isProcessingFrame = false;
            }
          };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();

  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
          final int requestCode, final String[] permissions, final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
               CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
            .show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
          CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;
  }

  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =
            (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        LOGGER.i("Camera API lv2?: %s", useCamera2API);
        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
      CameraConnectionFragment camera2Fragment =
          CameraConnectionFragment.newInstance(
              new CameraConnectionFragment.ConnectionCallback() {
                @Override
                public void onPreviewSizeChosen(final Size size, final int rotation) {
                  previewHeight = size.getHeight();
                  previewWidth = size.getWidth();
                  juw.fyp.navitalk.detection.CameraActivity.this.onPreviewSizeChosen(size, rotation);
                }
              },
              this,
              getLayoutId(),
              getDesiredPreviewFrameSize());

      camera2Fragment.setCamera(cameraId);
      fragment = camera2Fragment;
    } else {
      fragment =
          new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }


  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();


}