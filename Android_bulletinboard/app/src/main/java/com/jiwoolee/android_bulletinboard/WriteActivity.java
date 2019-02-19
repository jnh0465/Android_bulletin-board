package com.jiwoolee.android_bulletinboard;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class WriteActivity extends AppCompatActivity implements View.OnClickListener {

    private FirebaseFirestore mStore = FirebaseFirestore.getInstance();

    private EditText mWriteTitle;
    private TextView mWriteName;
    private EditText mWriteContent;
    private String id;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write);

        mWriteTitle = findViewById(R.id.write_title);
        mWriteContent = findViewById(R.id.write_content);
        mWriteName = findViewById(R.id.write_name);

        findViewById(R.id.write_upload).setOnClickListener(this);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        String UserName = bundle.getString("name"); //MainActivity에서 UserName값 가져오기

        //Toast.makeText(getApplicationContext(),UserName, Toast.LENGTH_SHORT).show();
        mWriteName.setText(UserName);
    }

    @Override
    public void onClick(View view) {
        id = mStore.collection("board").document().getId();
        Map<String, Object> post = new HashMap<>();

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        Integer Bnum = bundle.getInt("bunm"); //MainActivity에서 bunm값 가져오기

        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String getTime = sdf.format(date);

        post.put("id", id);
        post.put("title", mWriteTitle.getText().toString());
        post.put("content", mWriteContent.getText().toString());
        post.put("name", mWriteName.getText().toString());
        post.put("bnum", Bnum);
        post.put("time", getTime);

        mStore.collection("board").document(id).set(post)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(WriteActivity.this, "업로드 완료", Toast.LENGTH_SHORT).show();
                        finish(); //글쓰기 버튼 클릭 후 액티비티 끄기
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(WriteActivity.this, "업로드 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
