package com.project.luo.heartratemon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.Arrays;

public class MonitorActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{

    String TAG = "VisionActivity";

    MonitorView mOpenCvCameraView;

    private final int GRAPH_BUFFER_SIZE = 200;

    private boolean isCounting = false;

    private Button startCounting;

    private XYPlot plot;

    private SimpleXYSeries seriesG;

    private long frameZeroTime;
    private long frameTenTime;

    private long avgFrameTime = 0;

    private int count = 0;
    private int rate = 0;
    private int iterations = 1;

    // The value of red reaches 255 when a finger is on camera
    // Set a threshold to 230 to indicate a finger is on camera
    private double rMax = 200;

    private TextView hrNumber;

    private final int WINDOW_SIZE = 200;
    private final int FILTER_SIZE = 7;

    private ArrayList<Double> dataNeedFilter = new ArrayList<Double>();

    //
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_monitor);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mOpenCvCameraView = (MonitorView) findViewById(R.id.MonitorView);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        startCounting = (Button) findViewById(R.id.startCounting);
        hrNumber = (TextView) findViewById(R.id.hrNumber);

        startCounting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isCounting){
                    isCounting = false;

                    mOpenCvCameraView.setupCameraFlashLight();
                    int listSize = dataNeedFilter.size();
                    if (listSize > FILTER_SIZE){ // click stop before iterations reach 5, list is not empty

                        // find the number of zero crossing
                        int crossing = zeroCrossing(medianFilter(deMean(dataNeedFilter)));

                        // get the time in the data set
                        long time = avgFrameTime * (listSize - 1) / 1000000;

                        // get the heart rate of this data set
                        int singleRate = (int) ((crossing * 60 * 1000) / time);

                        if (singleRate > 40){

                            // get the total heart rate
                            rate = rate + singleRate;

                            // get the average and display the result
                            displayResult(rate / iterations);
                        }
                    }
                    // clear and reset
                    dataNeedFilter.clear();
                    iterations = 1;
                    setButtonText("Start");
                }
                else{
                    isCounting = true;
                    mOpenCvCameraView.setupCameraFlashLight();
                    rate = 0;
                    displayResult(rate);
                    setButtonText("Stop");
                }
            }
        });

        plot = (XYPlot) findViewById(R.id.sensorXYPlot);

        Number[] series1Numbers = {};

        // Turn the above arrays into XYSeries':
        seriesG = new SimpleXYSeries(
                Arrays.asList(series1Numbers),          // SimpleXYSeries takes a List so turn our array into a List
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, // Y_VALS_ONLY means use the element index as the x value
                "G");

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter seriesGFormat = new LineAndPointFormatter(Color.GREEN, null, null, null);

        plot.addSeries(seriesG, seriesGFormat);

    }


    @Override
    public void onResume()
    {
        super.onResume();
        iterations = 1;
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
    }


    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_monitor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.info) {

            popUpAlert();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }
    //
    @Override
    public void onCameraViewStopped() {

    }
    //
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat currentFrame = inputFrame.rgba();
        Scalar rgbaScalar = Core.mean(currentFrame); //-> returns r,g,b,a;

        if (seriesG.size() > GRAPH_BUFFER_SIZE){

            seriesG.removeFirst();
        }

        double[] rgba = rgbaScalar.val;

        seriesG.addLast(null, rgba[1]);

        if (count == 0){
            frameZeroTime = System.nanoTime();
        }
        if (count == 10){
            frameTenTime = System.nanoTime();

            // Calculate Frame per second
            avgFrameTime = (frameTenTime - frameZeroTime) / 10;
            count ++;
        }

        plot.redraw();

        if (isCounting && avgFrameTime != 0 && rgba[0] >= rMax) {


            if (dataNeedFilter.size() == WINDOW_SIZE) {

                double[] filteredList = medianFilter(deMean(dataNeedFilter));

                // find the number of zero crossing in data set
                int crossing = zeroCrossing(filteredList);

                // Calculate the total time
                long time = avgFrameTime * (WINDOW_SIZE - 1) / 1000000;

                // Calculate the heart rate in a single data set
                int singleRate = (int) ((crossing * 60 * 1000) / time);

                if (singleRate > 30){

                    // get the total heart rate of all data set
                    rate = rate + singleRate;

                    Log.i(TAG, "The count is " + crossing);
                    Log.i(TAG, "The time is " + time);
                    Log.i(TAG, "The rate is " + rate / iterations);

                    // Average the heart rate and display the result
                    displayResult(rate / iterations);

                    iterations ++;
                }

                if (iterations == 5){
                    // Stop counting and reset when the iterations reach 5
                    iterations = 1;
                    rate = 0;
                    isCounting = false;
                    mOpenCvCameraView.setupCameraFlashLight();
                    setButtonText("Start");
                }

                dataNeedFilter.clear();

            }

            dataNeedFilter.add(rgba[1]);
        }

        if ( avgFrameTime == 0){

            count ++;
        }
        return currentFrame;
    }

    // Show More Information dialog
    private void popUpAlert(){
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MonitorActivity.this);

        alertBuilder.setTitle("More Information");
        alertBuilder.setMessage("It's an optical heart rate monitoring application.\n" +
                "Click Start/Stop button\n" +
                "Have a finger covered flash and camera\n" +
                "Heart Rate will be calculated");


        alertBuilder.setNegativeButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog alertDialog = alertBuilder.create();

        alertDialog.show();

    }

    /**
     * Implement medianFiltering on a list of numbers
     * Modify python codes from https://gist.github.com/bhawkins/3535131 to Java
     *
     * @param  fList   the list of numbers that need medianFiltering
     * @return         the array of numbers after medianFiltering
     */
    private double[] medianFilter(ArrayList<Double> fList){

        int length = fList.size();
        int k = (FILTER_SIZE - 1) / 2;
        int medianIndex = (FILTER_SIZE - 1) / 2;

        double[] result = new double[length];
        double[][] y = new double[length][FILTER_SIZE];

        for (int i = 0; i < length; i ++){
            y[i][k] = fList.get(i);
        }

        int j, l;
        for (int i = 0; i < k; i ++){
            j = k - i;
            l = 0;
            for (int m = j; m < length; m++){
                y[m][i] = fList.get(l);
                l ++;
            }
            for (int m = 0; m < j; m++){
                y[m][i] = fList.get(0);
            }
            l = j;
            for (int m = 0; m < length - j; m ++){
                y[m][FILTER_SIZE - (i+1)] = fList.get(l);
                l ++;
            }
            for (int m = length - j; m < length; m ++){
                y[m][FILTER_SIZE - (i+1)] = fList.get(length - 1);
            }

        }

        for (int i = 0; i < length; i ++){
            Arrays.sort(y[i]);
            result[i] = y[i][medianIndex];
        }

        return result;
    }

    /**
     * Implement deMean on a list of numbers
     *
     * @param  dList   the list of numbers that need deMean
     * @return         the list of numbers after deMean
     */
    private ArrayList<Double> deMean(ArrayList<Double> dList){

        double sum = 0;

        for( Double f : dList){
            sum = sum + f.floatValue();
        }

        double deMeanValue = (sum/(dList.size()));

        double afterDeMean;

        int length = dList.size();

        for( int i = 0; i < length; i ++){

            afterDeMean = dList.get(i) - deMeanValue;
            dList.set(i, afterDeMean);
        }

        return dList;
    }

    /**
     * Count how many numbers are zero crossing on a list of numbers and update step count accordingly
     *
     * @param  filteredNum   the list of numbers that need differentiation
     */
    private int zeroCrossing(double[] filteredNum){

        double previous = 0;
        int crossing = 0;

        for (double f : filteredNum){

            if (previous > 0 && f < 0 ){
                crossing ++;
            }

            previous = f;

        }

        return crossing;

    }

    // Display the result heart rate to the textview
    private void displayResult(final int result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (result == 0){
                    hrNumber.setText("");
                }
                else {
                    hrNumber.setText(String.valueOf(result) + "bpm");
                }
            }
        });
    }

    // Update the text in button
    private void setButtonText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startCounting.setText(text);
            }

        });
    }
}
