package com.example.lungcanser;

import static java.security.AccessController.getContext;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.lungcanser.ml.ModelUnquant;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.model.Model;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 200;
    private static final int REQUEST_CODE_FOR_IMAGE_CHOOSER = 100;
    public static final int REQUEST_CODE_FOR_STORAGE_ACCSESS = 2;
    ImageView imageDisplay;
    Button scan;

    Bitmap image;

    int imageSize = 224;

    TextView results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scan = findViewById(R.id.scan);
        imageDisplay = findViewById(R.id.imageDisplay);
        results = findViewById(R.id.results);


        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if (checkPermission()) {
//                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//                    startActivityForResult(cameraIntent, REQUEST_CODE);
//
//                } else {
//                     //Permission is not granted, request it
//                    ActivityCompat.requestPermissions(MainActivity.this,
//                            new String[]{Manifest.permission.CAMERA},
//                            REQUEST_CODE);
//                }

                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    openGallarey();
                } else {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);

                }


            }
        });

    }

    private boolean checkPermission() {
        // Check if the permission is granted
        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, perform your operations
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, REQUEST_CODE);
            } else {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Allow permission")
                        .setMessage("To use the app you have to allow camera permission");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();

            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {

            image = (Bitmap) data.getExtras().get("data");
            int dimension = Math.min(image.getHeight(), image.getWidth());

            image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);

            Bitmap resized = Bitmap.createScaledBitmap(image, imageSize, imageSize, true);

            imageDisplay.setImageBitmap(image);

            try {
                classifyImage(resized);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else if (requestCode == 100) {
            Uri uri = data.getData();
            try {
                Bitmap image = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), uri);
                if (image != null) {
                    int dimension = Math.min(image.getHeight(), image.getWidth());
                    Bitmap thumNail = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
                    Bitmap resized = Bitmap.createScaledBitmap(image, imageSize, imageSize, true);
                    imageDisplay.setImageBitmap(thumNail);
                    classifyImage(resized);
                } else {
                    Log.e("The results", "Image is null");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void classifyImage(Bitmap image) throws IOException {
        ModelUnquant model = ModelUnquant.newInstance(getApplicationContext());
        // Creates inputs for reference.
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int image_pix[] = new int[imageSize * imageSize];

        image.getPixels(image_pix, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

        int pixels = 0;

        for (int i = 0; i < imageSize; i++) {
            for (int j = 0; j < imageSize; j++) {
                int value = image_pix[pixels++];
                byteBuffer.putFloat(((value >> 16) & 0xFF) * (1.f / 255.f));
                byteBuffer.putFloat(((value >> 8) & 0xFF) * (1.f / 255.f));
                byteBuffer.putFloat((value & 0xFF) * (1.f / 255.f));
            }
        }

        inputFeature0.loadBuffer(byteBuffer);

        // Runs model inference and gets result.
        ModelUnquant.Outputs outputs = model.process(inputFeature0);
        TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

        float[] confidence = outputFeature0.getFloatArray();
        int maxPos = 0;
        float maxConfidence = 0;

        for (int i = 0; i < confidence.length; i++) {
            if (confidence[i] > maxConfidence) {
                maxConfidence = confidence[i];
                maxPos = i;
            }
        }

        String[] classes = {"NORMAL","PNEUMONIA"};

        String s = "";
        for (int i = 0; i < classes.length; i++) {
            s += String.format("%s: %1f%%\n", classes[i], confidence[i] * 100);
        }

        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        results.setText(s);

        @SuppressLint("DefaultLocale") String formatted = String.format("%.1f", confidence[maxPos] * 100);

        //float check = (float) Double.parseDouble(formatted);

//        if (check < 65.5) {
//            Toast.makeText(getApplicationContext(), "Model error", Toast.LENGTH_SHORT).show();
//        } else {
//            switch (classes[maxPos]){
//                case "PNEUMONIA":
//                   // Toast.makeText(this, "PNEUMONIA DETECTED", Toast.LENGTH_SHORT).show();
//                    break;
//
//                case "NORMAL":
//                   // Toast.makeText(this, "NORMAL", Toast.LENGTH_SHORT).show();
//                    break;
//            }
//
//        }
        // Releases model resources if no longer used.
        model.close();

    }

    private void openGallarey() {
        Intent imageIntent = new Intent();
        imageIntent.setType("image/*");
        imageIntent.setAction(Intent.ACTION_PICK);
        startActivityForResult(Intent.createChooser(imageIntent, "Title"),
                100);
    }

}