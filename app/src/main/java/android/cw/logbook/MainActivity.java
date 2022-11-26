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
    private List<ImageUpload> list = new ArrayList<>();//create list to save
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());// map view binding for activity main layout
        setContentView(binding.getRoot());
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);//locating current location
        databaseRef = FirebaseDatabase.getInstance().getReference("LogbookImage");//use firebase to save pictures to folder LogBookImage

        binding.iconCamera.setOnClickListener(v -> CameraIntent());//click event camera button

        databaseRef.addValueEventListener(new ValueEventListener() {//add data to firebase
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {//if take a photo
                    list.clear();
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        String url = dataSnapshot.child("imageURL").getValue(String.class);//initialize to show link uploaded to form
                        String name = dataSnapshot.child("address").getValue(String.class);//initialize to show current address
                        ImageUpload ImageUpload = new ImageUpload(name, url);
                        list.add(ImageUpload);//add image to list image
                    }
                    Picasso.get().load(list.get(0).getImageURL()).into(binding.image);//load image
                    binding.pageNumber.setText("1/" + list.size());//get current and total page number
                    binding.txtAddress.setText(list.get(count).getAddress());// get address
                    binding.url.setText(list.get(count).getImageURL());//get link image
                    count = 0;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        binding.btnPre.setOnClickListener(v -> {//set click event previous button
            if (count > 0) {
                count--;
                Picasso.get().load(list.get(count).getImageURL()).into(binding.image);//load uploaded image
                binding.pageNumber.setText((count + 1) + "/" + list.size());//get current and total page number
                binding.txtAddress.setText(list.get(count).getAddress());// get address
                binding.url.setText(list.get(count).getImageURL());//get link image
            }
        });
        binding.btnNext.setOnClickListener(v -> {//set event click next button
            if (count < list.size() - 1) {
                count++;
                Picasso.get().load(list.get(count).getImageURL()).into(binding.image);//load uploaded image
                binding.pageNumber.setText((count + 1) + "/" + list.size());//get current and total page number
                binding.txtAddress.setText(list.get(count).getAddress());// get address
                binding.url.setText(list.get(count).getImageURL());//get link image
            }
        });
        binding.btnUpload.setOnClickListener(new View.OnClickListener() {//set event click upload button
            @Override
            public void onClick(View view) {
                String url = binding.url.getText().toString();
                if (url.isEmpty()) {//if link url is empty -> code run -> result show notification
                    Toast.makeText(MainActivity.this, "Please enter url", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!checkLinkUrl(url)) {//if there is a url link, it will check -> invalid -> code run -> result show notification
                    Toast.makeText(MainActivity.this, "Please enter valid url", Toast.LENGTH_SHORT).show();
                    return;
                }
                Picasso.get().load(url).into(binding.image);//load uploaded image
                getCurrentLocation();//get current location
                ImageUpload ImageUpload = new ImageUpload(binding.txtAddress.getText().toString(), url);
                databaseRef.push().setValue(ImageUpload);//push image to firebase
                binding.url.setText("");//show link url
            }
        });
    }

    private void saveImage() {//save image to firebase
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

    private void uploadImage(byte[] image) {//where the photo is stored

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
                        ImageUpload ImageUpload = new ImageUpload(binding.txtAddress.getText().toString(), uploadImage);
                        FirebaseDatabase.getInstance().getReference("LogbookImage")
                                .push().setValue(ImageUpload);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("Upload", "onFailure" + e.getMessage());
                    }
                });
    }

    class ImageUpload {
        public String address;//initialize address
        public String imageURL;//initialize imageURL

        public ImageUpload(String address, String imageURL) {
            this.address = address;
            this.imageURL = imageURL;
        }

        public ImageUpload() {
        }

        public String getAddress() {//get address
            return address;
        }

        public String getImageURL() {//get image url
            return imageURL;
        }
    }

    private boolean checkLinkUrl(String url) {
        //check the url to see if it's a url or not
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return true;
        }
        return false;
    }


    private void getCurrentLocation() {//get current location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {//if location is not null -> code run
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
            requestPermission();
        }
    }

    private void requestPermission() {//request Permission
        ActivityCompat.requestPermissions(MainActivity.this, new String[]
                {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
    }

    private void requestCameraPermission() {//when using the camera ask for permission
        ActivityCompat.requestPermissions(MainActivity.this, new String[]
                {Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
    }

    private void CameraIntent() {//function camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, REQUEST_CODE_CAMERA);
        } else {
            requestCameraPermission();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
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
                getCurrentLocation();
                saveImage();
                uploadImage(convertBitMapToByte((Bitmap) data.getExtras().get("data")));
            }
        }
    }



    private byte[] convertBitMapToByte(Bitmap bitmap) {//convert BitMap to Byte
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] bytes = stream.toByteArray();
        return bytes;
    }

}
