package puipui.wearvr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;


import android.widget.Toast;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;



/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer, SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mAccelerometer1;
    private float mAccelData0, mAccelData1;
    private float mratio1, mratio2;

    public float xMove, yMove;
    public float yMove1;

    private static final String TAG = "MainActivity";

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;

    private static final float CAMERA_Z = 0.01f;
    private static final float TIME_DELTA = 0.3f;

    private static final float YAW_LIMIT = 0.12f;
    private static final float PITCH_LIMIT = 0.12f;

    private static final int COORDS_PER_VERTEX = 3;

    // We keep the light always position just above the user.
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] { 0.0f, 2.0f, 0.0f, 1.0f };

    private final float[] lightPosInEyeSpace = new float[4];

    private FloatBuffer floorVertices;
    private FloatBuffer floorColors;
    private FloatBuffer floorNormals;

    private FloatBuffer cubeVertices1;
    private FloatBuffer cubeColors1;
    private FloatBuffer cubeFoundColors1;
    private FloatBuffer cubeNormals1;

    private int cubeProgram1;
    private int floorProgram;

    private int cubePositionParam1;
    private int cubeNormalParam1;
    private int cubeColorParam1;
    private int cubeModelParam1;
    private int cubeModelViewParam1;
    private int cubeModelViewProjectionParam1;
    private int cubeLightPosParam1;

    private int floorPositionParam;
    private int floorNormalParam;
    private int floorColorParam;
    private int floorModelParam;
    private int floorModelViewParam;
    private int floorModelViewProjectionParam;
    private int floorLightPosParam;

    private float[] modelCube1;
    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] modelViewProjection;
    private float[] modelView;
    private float[] modelFloor;

    private float floorDepth = 20f;

    private Vibrator vibrator;


    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param type The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The shader object handler.
     */
    private int loadGLShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param label Label to report in case of error.
     */
    private static void checkGLError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.round_activity_main);

        Toast.makeText(this, "\n\nTilt watch\nface down.\nCube goes\naround", Toast.LENGTH_LONG).show();


        setContentView(R.layout.common_ui);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);


        cardboardView.setVRModeEnabled(false);



        modelCube1 = new float[16];
        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];
        modelFloor = new float[16];
        headView = new float[16];

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer1 = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer1, SensorManager.SENSOR_DELAY_UI);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

    }



    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world.
     *
     * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
     * Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.


        ByteBuffer bbVertices1 = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COORDS1.length * 4);
        bbVertices1.order(ByteOrder.nativeOrder());
        cubeVertices1 = bbVertices1.asFloatBuffer();
        cubeVertices1.put(WorldLayoutData.CUBE_COORDS1);
        cubeVertices1.position(0);


        ByteBuffer bbColors1 = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COLORS1.length * 4);
        bbColors1.order(ByteOrder.nativeOrder());
        cubeColors1 = bbColors1.asFloatBuffer();
        cubeColors1.put(WorldLayoutData.CUBE_COLORS1);
        cubeColors1.position(0);


        ByteBuffer bbFoundColors1 = ByteBuffer.allocateDirect(
                WorldLayoutData.CUBE_FOUND_COLORS1.length * 4);
        bbFoundColors1.order(ByteOrder.nativeOrder());
        cubeFoundColors1 = bbFoundColors1.asFloatBuffer();
        cubeFoundColors1.put(WorldLayoutData.CUBE_FOUND_COLORS1);
        cubeFoundColors1.position(0);


        ByteBuffer bbNormals1 = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_NORMALS1.length * 4);
        bbNormals1.order(ByteOrder.nativeOrder());
        cubeNormals1 = bbNormals1.asFloatBuffer();
        cubeNormals1.put(WorldLayoutData.CUBE_NORMALS1);
        cubeNormals1.position(0);

        // make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        floorVertices = bbFloorVertices.asFloatBuffer();
        floorVertices.put(WorldLayoutData.FLOOR_COORDS);
        floorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        floorNormals = bbFloorNormals.asFloatBuffer();
        floorNormals.put(WorldLayoutData.FLOOR_NORMALS);
        floorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        floorColors = bbFloorColors.asFloatBuffer();
        floorColors.put(WorldLayoutData.FLOOR_COLORS);
        floorColors.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
        int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

        cubeProgram1 = GLES20.glCreateProgram();
        GLES20.glAttachShader(cubeProgram1, vertexShader);
        GLES20.glAttachShader(cubeProgram1, passthroughShader);
        GLES20.glLinkProgram(cubeProgram1);
        GLES20.glUseProgram(cubeProgram1);

        checkGLError("Cube program");

        cubePositionParam1 = GLES20.glGetAttribLocation(cubeProgram1, "a_Position");
        cubeNormalParam1 = GLES20.glGetAttribLocation(cubeProgram1, "a_Normal");
        cubeColorParam1 = GLES20.glGetAttribLocation(cubeProgram1, "a_Color");


        cubeModelParam1 = GLES20.glGetUniformLocation(cubeProgram1, "u_Model");
        cubeModelViewParam1 = GLES20.glGetUniformLocation(cubeProgram1, "u_MVMatrix");
        cubeModelViewProjectionParam1 = GLES20.glGetUniformLocation(cubeProgram1, "u_MVP");
        cubeLightPosParam1 = GLES20.glGetUniformLocation(cubeProgram1, "u_LightPos");

        GLES20.glEnableVertexAttribArray(cubePositionParam1);
        GLES20.glEnableVertexAttribArray(cubeNormalParam1);
        GLES20.glEnableVertexAttribArray(cubeColorParam1);

        checkGLError("Cube program params");

        floorProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(floorProgram, vertexShader);
        GLES20.glAttachShader(floorProgram, gridShader);
        GLES20.glLinkProgram(floorProgram);
        GLES20.glUseProgram(floorProgram);

        checkGLError("Floor program");

        floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model");
        floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix");
        floorModelViewProjectionParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP");
        floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos");

        floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position");
        floorNormalParam = GLES20.glGetAttribLocation(floorProgram, "a_Normal");
        floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color");

        GLES20.glEnableVertexAttribArray(floorPositionParam);
        GLES20.glEnableVertexAttribArray(floorNormalParam);
        GLES20.glEnableVertexAttribArray(floorColorParam);

        checkGLError("Floor program params");

        xMove=0f;
        yMove=0f;

        yMove1=0f;

        // Object first appears directly in front of user.
        Matrix.setIdentityM(modelCube1, 0);

        Matrix.translateM(modelCube1, 0, 2f, 0, -11f);

        Matrix.setIdentityM(modelFloor, 0);
        Matrix.translateM(modelFloor, 0, 0, -floorDepth, 0); // Floor appears below user.

        checkGLError("onSurfaceCreated");
    }

    /**
     * Converts a raw text file into a string.
     *
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The context of the text file, or null in case of error.
     */
    private String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        // Build the Model part of the ModelView matrix.

        Matrix.rotateM(modelCube1, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);

        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(headView, 0);

        checkGLError("onReadyToDraw");
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        checkGLError("colorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

        // Set the position of the light
        Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);



        Matrix.multiplyMM(modelView, 0, view, 0, modelCube1, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        drawCube1();

        // Set modelView for the floor, so we draw floor in the correct location
        Matrix.multiplyMM(modelView, 0, view, 0, modelFloor, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
                modelView, 0);
        drawFloor();
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }


    private int go1;



    /**
     * Draw the cube.
     *
     * <p>We've set all of our transformation matrices. Now we simply pass them into the shader.
     */

    private float xMove0=20f;

    private float xMove1=0f;
    private float xMove2=95f;
    private float zMove1=-60f;
    private float zMove2=60f;



    public void drawCube1() {
        GLES20.glUseProgram(cubeProgram1);

        GLES20.glUniform3fv(cubeLightPosParam1, 1, lightPosInEyeSpace, 0);

        // Set the Model in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelParam1, 1, false, modelCube1, 0);

        // Set the ModelView in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelViewParam1, 1, false, modelView, 0);

        // Set the position of the cube
        GLES20.glVertexAttribPointer(cubePositionParam1, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, cubeVertices1);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(cubeModelViewProjectionParam1, 1, false, modelViewProjection, 0);

        // Set the normal positions of the cube, again for shading
        GLES20.glVertexAttribPointer(cubeNormalParam1, 3, GLES20.GL_FLOAT, false, 0, cubeNormals1);
        GLES20.glVertexAttribPointer(cubeColorParam1, 4, GLES20.GL_FLOAT, false, 0,
                isLookingAtObject() ? cubeFoundColors1 : cubeColors1);


//move cube around
        //==================================================
        if(go1==1) {

            go1 = 0;

            if ((this.xMove0--) * 0.1f > -9.5f) {
                Matrix.setIdentityM(modelCube1, 0);
                Matrix.translateM(modelCube1, 0, (this.xMove0--)*0.1f, 0, -11f);
                vibrator.vibrate(100);
            }


            else if (zMove1++ * 0.1f <= 6f) {
                Matrix.setIdentityM(modelCube1, 0);
                 Matrix.translateM(modelCube1, 0, -9.5f, 0, zMove1++ * 0.1f);
                xMove1 = -95f;
                vibrator.vibrate(100);
            } else if (xMove1++ * 0.1f <= 9.5f) {
                Matrix.setIdentityM(modelCube1, 0);
                Matrix.translateM(modelCube1, 0, xMove1++ * 0.1f, 0, 6f);
                vibrator.vibrate(50);

            } else {
                Matrix.setIdentityM(modelCube1, 0);
                Matrix.translateM(modelCube1, 0, 9.5f, 0, 6f);

                if ((this.zMove2--) * 0.1f > -6f) {
                    Matrix.setIdentityM(modelCube1, 0);
                    Matrix.translateM(modelCube1, 0, 9.5f, 0, (this.zMove2--) * 0.1f);
                    vibrator.vibrate(100);

                } else {
                    Matrix.setIdentityM(modelCube1, 0);
                    Matrix.translateM(modelCube1, 0, 9.5f, 0, -11f);

                    if ((this.xMove2--) * 0.1f > -2.5f) {
                        Matrix.setIdentityM(modelCube1, 0);
                        Matrix.translateM(modelCube1, 0, (this.xMove2--) * 0.1f, 0, -11f);
                        vibrator.vibrate(100);
                    }
                    else{
                        Matrix.setIdentityM(modelCube1, 0);
                        Matrix.translateM(modelCube1, 0, -2.5f, 0, -11f);
                    }
                }


            }
        }
        //=========================

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
        checkGLError("Drawing cube");

    }

    /**
     * Draw the floor.
     *
     * <p>This feeds in data for the floor into the shader. Note that this doesn't feed in data about
     * position of the light, so if we rewrite our code to draw the floor first, the lighting might
     * look strange.
     */
    public void drawFloor() {
        GLES20.glUseProgram(floorProgram);

        // Set ModelView, MVP, position, normals, and color.
        GLES20.glUniform3fv(floorLightPosParam, 1, lightPosInEyeSpace, 0);
        GLES20.glUniformMatrix4fv(floorModelParam, 1, false, modelFloor, 0);
        GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(floorModelViewProjectionParam, 1, false,
                modelViewProjection, 0);
        GLES20.glVertexAttribPointer(floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, floorVertices);
        GLES20.glVertexAttribPointer(floorNormalParam, 3, GLES20.GL_FLOAT, false, 0,
                floorNormals);
        GLES20.glVertexAttribPointer(floorColorParam, 4, GLES20.GL_FLOAT, false, 0, floorColors);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        checkGLError("drawing floor");
    }

    /**(Disabled)
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        //was Log.i(TAG, "onCardboardTrigger");

        //was  if (isLookingAtObject()) {
        //was   score++;
        //was   overlayView.show3DToast("Found it! Look around for another one.\nScore = " + score);
        //was   hideObject();
        //was } else {
        //was  overlayView.show3DToast("Look around to find the object!");
        //was }

        // Always give user feedback.
        //was vibrator.vibrate(50);
    }



    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     *
     * @return true if the user is looking at the object.
     */
    private boolean isLookingAtObject() {
        float[] initVec = { 0, 0, 0, 1.0f };
        float[] objPositionVec = new float[4];

        // Convert object space to camera space. Use the headView from onNewFrame.
        Matrix.multiplyMM(modelView, 0, headView, 0, modelCube1, 0);
        Matrix.multiplyMV(objPositionVec, 0, modelView, 0, initVec, 0);

        float pitch = (float) Math.atan2(objPositionVec[1], -objPositionVec[2]);
        float yaw = (float) Math.atan2(objPositionVec[0], -objPositionVec[2]);

        return Math.abs(pitch) < PITCH_LIMIT && Math.abs(yaw) < YAW_LIMIT;
    }







    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}


    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            mAccelData0 = mAccelData0 + 0.25f * (event.values[1] - mAccelData0);
            mAccelData1 = mAccelData1 + 0.25f * (event.values[2] - mAccelData1);

            if (mAccelData1 != 0.0f) {
                mratio1 = Math.abs(mAccelData0 / mAccelData1);
            }
            if (mAccelData0 != 0.0f) {
                mratio2 = Math.abs(mAccelData1 / mAccelData0);
            }

        }


        if (mratio2 > mratio1) {

            if ((Math.abs(mAccelData1) > Math.abs(mAccelData0)) && (mAccelData1 > -2.0f)) {

                go1=0;

            }
            if ((Math.abs(mAccelData1) > Math.abs(mAccelData0)) && (mAccelData1 < 0.0f)) {

                go1=1;

            }
        }

    }


    @Override
    protected void onStop() {
        super.onStop();

        mSensorManager.unregisterListener(this);

        finish();

        System.exit(0);

    }



}
