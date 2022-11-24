package android.cw.logbook;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.cw.logbook.databinding.ActivityMainBinding;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    FusedLocationProviderClient fusedLocationProviderClient;
    ActivityMainBinding binding;
    private final static int REQUEST_CODE = 100;
    private final static int REQUEST_CODE_CAMERA = 101;
    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference databaseRef;
    private List<ImageUploadInfo> list = new ArrayList<>();
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        databaseRef = FirebaseDatabase.getInstance().getReference("LogbookImage");

        binding.iconCamera.setOnClickListener(v -> CameraIntent());

        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    list.clear();
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        String url = dataSnapshot.child("imageURL").getValue(String.class);
                        String name = dataSnapshot.child("address").getValue(String.class);
                        ImageUploadInfo imageUploadInfo = new ImageUploadInfo(name, url);
                        list.add(imageUploadInfo);
                    }
                    Picasso.get().load(list.get(0).getImageURL()).into(binding.image);
                    binding.pageNumber.setText("1/" + list.size());
                    binding.txtAddress.setText(list.get(count).getAddress());
                    binding.url.setText(list.get(count).getImageURL());
                    count = 0;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        binding.btnPre.setOnClickListener(v -> {
            if (count > 0) {
                count--;
                Picasso.get().load(list.get(count).getImageURL()).into(binding.image);
                binding.pageNumber.setText((count + 1) + "/" + list.size());
                binding.txtAddress.setText(list.get(count).getAddress());
                binding.url.setText(list.get(count).getImageURL());
            }
        });
        binding.btnNext.setOnClickListener(v -> {
            if (count < list.size() - 1) {
                count++;
                Picasso.get().load(list.get(count).getImageURL()).into(binding.image);
                binding.pageNumber.setText((count + 1) + "/" + list.size());
                binding.txtAddress.setText(list.get(count).getAddress());
                binding.url.setText(list.get(count).getImageURL());
            }
        });
        binding.btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = binding.url.getText().toString();
                if (url.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter url", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!checkUrl(url)) {
                    Toast.makeText(MainActivity.this, "Please enter valid url", Toast.LENGTH_SHORT).show();
                    return;
                }
                Picasso.get().load(url).into(binding.image);
                getLastLocation();
                ImageUploadInfo imageUploadInfo = new ImageUploadInfo(binding.txtAddress.getText().toString(), url);
                databaseRef.push().setValue(imageUploadInfo);
                binding.url.setText("");
            }
        });
    }

    private boolean checkUrl(String url) {
        //check the url to see if it's a url or not
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return true;
        }
        return false;
    }


    private void getLastLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                                List<Address> addresses = null;
                                try {
                                    addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                                    String address = addresses.get(0).getAddressLine(0);
                                    binding.txtAddress.setText(address);
                                } catch (IOException e) {
                                    Log.d("TAG", "onSuccess: " + e.getMessage());
                                }
                            }

                        }
                    });
        } else {
            askPermission();
        }
    }

    private void askPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]
                {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
    }

    private void askCameraPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]
                {Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
    }

    private void CameraIntent() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, REQUEST_CODE_CAMERA);
        } else {
            askCameraPermission();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                Toast.makeText(this, "Required Permission", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CameraIntent();
            } else {
                Toast.makeText(this, "Required Permission", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (resultCode == RESULT_OK) {
                assert data != null;
                binding.iconCamera.setImageBitmap((Bitmap) data.getExtras().get("data"));
                getLastLocation();
                saveImage();
                uploadImage(bitmapToByte((Bitmap) data.getExtras().get("data")));
            }
        }
    }

    private void saveImage() {
        Bitmap bitmap = binding.iconCamera.getDrawingCache();
        File file = new File(Environment.getExternalStorageDirectory(), "image.jpg");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] bitmapToByte(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] bytes = stream.toByteArray();
        return bytes;
    }

    private void uploadImage(byte[] image) {

        String filepath = "Image/" + System.currentTimeMillis();
        StorageReference storageReference = FirebaseStorage.getInstance().getReference(filepath);
        storageReference.putBytes(image)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d("Upload", "onSuccess");
                        Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                        while (!uriTask.isSuccessful()) ;
                        String uploadImage = "" + uriTask.getResult();
                        Log.d("Upload", "onSuccess: " + uploadImage);
                        ImageUploadInfo imageUploadInfo = new ImageUploadInfo(binding.txtAddress.getText().toString(), uploadImage);
                        FirebaseDatabase.getInstance().getReference("LogbookImage")
                                .push().setValue(imageUploadInfo);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("Upload", "onFailure" + e.getMessage());
                    }
                });
    }

    class ImageUploadInfo {
        public String address;
        public String imageURL;

        public ImageUploadInfo(String address, String imageURL) {
            this.address = address;
            this.imageURL = imageURL;
        }

        public ImageUploadInfo() {
        }

        public String getAddress() {
            return address;
        }

        public String getImageURL() {
            return imageURL;
        }
    }

}