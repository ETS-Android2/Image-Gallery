package com.mca.imagegallery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;

public class EditProfile extends AppCompatActivity {

    private Button btnBack, btnUpdate;
    private ImageView ivProfile;
    private TextView tvChangeProfilePhoto;
    private EditText etName, etCity;
    private String id, name, city, profile_url;
    private String updatedName, updatedCity, updatedProfile;

    private ProgressDialog pd;
    private static AlertDialog dialog;
    private static File file;
    private static Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        btnBack = findViewById(R.id.btn_back);
        btnUpdate = findViewById(R.id.btn_update);
        ivProfile = findViewById(R.id.iv_profile);
        tvChangeProfilePhoto = findViewById(R.id.tv_change_profile_photo);
        etName = findViewById(R.id.et_name);
        etCity = findViewById(R.id.et_city);

        btnBack.setOnClickListener(view -> onBackPressed());

        SharedPreferences sp = getSharedPreferences(Utils.LOGIN_SHARED_FILE, MODE_PRIVATE);
        id = Utils.getID(sp.getString("email", null));
        name = sp.getString("name", null);
        city = sp.getString("city", null);
        profile_url = sp.getString("profile_url", null);
        etName.setText(name);
        etCity.setText(city);
        Picasso.get().load(profile_url).into(ivProfile);

        dialog = Utils.pickDialog(this);
        ivProfile.setOnClickListener(view -> dialog.show());
        tvChangeProfilePhoto.setOnClickListener(view -> dialog.show());

        btnUpdate.setOnClickListener(view -> updateProfile());
    }

    private boolean validateData(String name, String city) {
        if(name.isEmpty()) {
            etName.setError("Name is required!");
            etName.requestFocus();
            return false;
        }

        if(city.isEmpty()) {
            etCity.setError("City is required!");
            etCity.requestFocus();
            return false;
        }

        return true;
    }

    private void updateProfile() {

        updatedName = etName.getText().toString().trim();
        updatedCity = etCity.getText().toString().trim();

        if(validateData(updatedName, updatedCity) && (!updatedName.equals(name) || !updatedCity.equals(city) || updatedProfile != null)) {

            pd = Utils.progressDialog(this, "Updating...");
            pd.show();

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users")
                .document(id)
                .get()
                .addOnSuccessListener(document -> {
                    User user = document.toObject(User.class);
                    user.setName(updatedName);
                    user.setCity(updatedCity);
                    if(updatedProfile != null) {
                        uploadProfileToFirebaseStorage(user);
                    } else {
                        updateUserDataToFirestore(user);
                    }
                })
            .addOnFailureListener(ex -> {
                pd.dismiss();
                Utils.toast(this, ex.getMessage());
            });
        }
    }

    private void uploadProfileToFirebaseStorage(User user) {
        try {

            byte[] bytes = Utils.compressImage(this, imageUri);

            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference reference = storage.getReference().child("imagegallery-ks/" + id + "_profile");
            reference.putBytes(bytes)
                .addOnSuccessListener(snapshot -> {
                    reference.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            user.setProfile_url(uri.toString());
                            updateUserDataToFirestore(user);
                        })
                        .addOnFailureListener(ex -> Utils.toast(this, ex.getMessage()));
                })
                .addOnFailureListener(ex -> {
                    pd.dismiss();
                    Utils.toast(this, ex.getMessage());
                });
        }
        catch (IOException ex) {
            pd.dismiss();
            Utils.toast(this, ex.getMessage());
        }
    }

    private void updateUserDataToFirestore(User user) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
            .document(id)
            .set(user)
            .addOnSuccessListener(unused -> {
                Utils.toast(this, "Profile Updated...");
                updateSharedPreferences(user);
                finish();
            })
            .addOnFailureListener(ex -> {
                pd.dismiss();
                Utils.toast(this, ex.getMessage());
            });
    }

    private void updateSharedPreferences(User user) {
        SharedPreferences.Editor editor = getSharedPreferences(Utils.LOGIN_SHARED_FILE, MODE_PRIVATE).edit();
        editor.putString("name", user.getName());
        editor.putString("city", user.getCity());
        editor.putString("profile_url", user.getProfile_url());
        editor.apply();

        editor = getSharedPreferences(Utils.RECENT_USER_SHARED_FILE, MODE_PRIVATE).edit();
        editor.putString("name", user.getName());
        editor.putString("profile_url", user.getProfile_url());
        editor.apply();
    }

    // override method to perform action on permission grant and revoke
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Permissions.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // override method which will be called on when image is captured or selected
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        NavMenu.onActivityResult(requestCode, resultCode, data);
        imageUri = NavMenu.imageUri;
        file = NavMenu.file;
        if(imageUri != null) {
            updatedProfile = imageUri.toString();
            ivProfile.setImageURI(imageUri);
        } else {
            updatedProfile = null;
        }

        dialog.dismiss();
    }

    @Override
    public void onBackPressed() {
        if(!etName.getText().toString().equals(name) || !etCity.getText().toString().equals(city) || updatedProfile != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Unsaved changes?")
                    .setIcon(R.drawable.ic_launcher_background)
                    .setPositiveButton("Yes", (dialog, which) -> finish())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            finish();
        }
    }
}