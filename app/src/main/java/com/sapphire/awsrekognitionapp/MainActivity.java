package com.sapphire.awsrekognitionapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Can't disable through xml
        ImageButton picButton = (ImageButton) findViewById(R.id.button);
        picButton.setEnabled(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA));
    }

    static final int REQUEST_IMAGE_CAPTURE = 1;

    public void takePicture(View view)
    {
        dispatchTakePictureIntent();
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");

            // send picture to AWS
            new AnalyzePictureTask().execute(imageBitmap);
        }
    }

    private class AnalyzePictureTask extends AsyncTask<Bitmap, String, List<DetectLabelsResult>> {
        protected List<DetectLabelsResult> doInBackground(Bitmap... bitmaps) {
            int count = bitmaps.length;
            List<DetectLabelsResult> results = new ArrayList<DetectLabelsResult>();

            CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                    getApplicationContext(),
                    getString(R.string.identityPoolId),
                    Regions.US_WEST_2
            );
            AmazonRekognition client = new AmazonRekognitionClient(credentialsProvider);

            for (int i = 0; i < count; i++) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmaps[i].compress(Bitmap.CompressFormat.JPEG,100,stream);

                ByteBuffer imageBytes = ByteBuffer.wrap(stream.toByteArray());
                DetectLabelsRequest request = new DetectLabelsRequest()
                        .withImage(new Image()
                                .withBytes(imageBytes))
                        .withMaxLabels(10)
                        .withMinConfidence(50F);

                try {
                    DetectLabelsResult result = client.detectLabels(request);
                    results.add(result);
                } catch (AmazonServiceException e) {
                    publishProgress(e.getErrorMessage());
                } catch (AmazonClientException e){
                    publishProgress(e.getMessage());
                }

                // Escape early if cancel() is called
                if (isCancelled()) break;
            }
            return results;
        }

        protected void onProgressUpdate(String... msg) {
            showError(msg[0]);
        }

        private void showError(String msg)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(msg).setTitle("Failure");
            builder.create().show();
        }

        protected void onPostExecute(List<DetectLabelsResult> results) {
            ArrayList<String> resultList = new ArrayList<String>();
            for (DetectLabelsResult result: results) {
                List<Label> labels = result.getLabels();
                for (Label label: labels) {
                    resultList.add(label.getName() + ": " + label.getConfidence().toString());
                }
            }

            ListView myListView = (ListView) MainActivity.this.findViewById(R.id.list_view);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, resultList);
            myListView.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        }
    }
}
