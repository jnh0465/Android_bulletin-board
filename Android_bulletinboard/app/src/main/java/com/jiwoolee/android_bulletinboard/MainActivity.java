package com.jiwoolee.android_bulletinboard;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends BaseActivity implements View.OnClickListener {
    private TextView mStatusTextView; //로그인여부 상태
    private EditText mEmailField;     //회원가입필드
    private EditText mPasswordField;

    private FirebaseAuth mAuth;       //구글로그인
    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;

    private RecyclerView mRecyclerView; //게시판
    private List<Board> mBoardList;
    private BoardAdapter mAdapter;

    private String UserName;
    private SwipeRefreshLayout swipe;

    private FirebaseFirestore mStore = FirebaseFirestore.getInstance(); //firestore 연결

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStatusTextView = findViewById(R.id.status); //버튼 참조
        mEmailField = findViewById(R.id.fieldEmail);
        mPasswordField = findViewById(R.id.fieldPassword);
        mRecyclerView = findViewById(R.id.recyclerview);
        swipe = findViewById(R.id.swipe);

        findViewById(R.id.emailSignInButton).setOnClickListener(this);  //리스너 연결
        findViewById(R.id.signOutButton).setOnClickListener(this);
        findViewById(R.id.signInButton).setOnClickListener(this);
        findViewById(R.id.floatingbutton).setOnClickListener(this);

        //구글 클라이언트 연결
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        mAuth = FirebaseAuth.getInstance(); //firebase 연결

        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() { //위로 당겨서 새로고침시
            @Override
            public void onRefresh() {
                UploadBoard();
                swipe.setRefreshing(false); //새로고침 종료
            }
        });
    }

    @Override
    public void onStart() { //시작시
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser(); //현재 로그인 되어있는지 확인
        updateUI(currentUser);                             //로그인 여부에 따라 UI 업데이트
        UploadBoard(); //실시간 업로드
    }

    private void UploadBoard(){ //게시판 실시간업로드
        mBoardList = new ArrayList<>();

        mStore.collection("board").orderBy("time", Query.Direction.DESCENDING) //작성시간 역순으로 정렬
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                for(DocumentChange dc : snapshot.getDocumentChanges()){
                    String id = (String) dc.getDocument().getData().get("id");
                    String title = (String) dc.getDocument().getData().get("title");
                    String content = (String) dc.getDocument().getData().get("content");
                    String name = (String) dc.getDocument().getData().get("name");

                    Board data = new Board(id, title, content, name);
                    mBoardList.add(data);
                }
                mAdapter = new BoardAdapter(mBoardList);
                mRecyclerView.setAdapter(mAdapter);
            }
        });
    }

    //로그인////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void signIn_google() { //구글로그인
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                mAuth.signInWithCredential(credential)
                        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    updateUI(user);
                                } else {
                                    updateUI(null);
                                }
                            }
                        });
            } catch (ApiException e) {
                updateUI(null);
            }
        }
    }

    private void signIn(String email, String password) { //로그인
        if (!validateForm()) {
            return;
        }

        showProgressDialog(); //프로그래스바

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                                @Override
                                public void onComplete(@NonNull Task<AuthResult> task) {
                                    if (task.isSuccessful()) {
                                        //Intent intent=new Intent(MainActivity.this,loginok.class);
                                        //startActivity(intent);
                                        FirebaseUser user = mAuth.getCurrentUser();
                                        updateUI(user);
                                    } else {
                                        updateUI(null);
                        }
                        if (!task.isSuccessful()) {
                            mStatusTextView.setText(R.string.auth_failed);
                        }
                        hideProgressDialog();
                    }
                });
    }

    private boolean validateForm() { //로그인 폼 채움 여부
        boolean valid = true;
        String email = mEmailField.getText().toString();
        if (TextUtils.isEmpty(email)) {
            mEmailField.setError("Required.");
            valid = false;
        } else {
            mEmailField.setError(null);
        }

        String password = mPasswordField.getText().toString();
        if (TextUtils.isEmpty(password)) {
            mPasswordField.setError("Required.");
            valid = false;
        } else {
            mPasswordField.setError(null);
        }

        return valid;
    }

    private void signOut() { //로그아웃
        mAuth.signOut();
        updateUI(null);
    }

    //회원가입//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void btn_createAccount(View view){     //커스텀 다이얼로그
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("회원 가입");   //제목
        builder.setIcon(R.mipmap.ic_launcher); //아이콘

        //다이얼로그를 통해 보여줄 뷰 설정
        LayoutInflater inflater = getLayoutInflater();
        View v1 = inflater.inflate(R.layout.custom_dialog, null);
        builder.setView(v1);

        DialogListener listener = new DialogListener();

        builder.setPositiveButton("확인",listener);
        builder.setNegativeButton("취소", listener);

        builder.show(); //띄우기
    }

    class DialogListener implements DialogInterface.OnClickListener{     //커스텀 다이얼로그 리스너
        @Override
        public void onClick(DialogInterface dialog, int which) {
            //AlertDialog가 가지고 있는 뷰를 가져옴
            AlertDialog alert = (AlertDialog)dialog;
            EditText edit1 = (EditText)alert.findViewById(R.id.editText);
            EditText edit2 = (EditText)alert.findViewById(R.id.editText2);

            String str1 = edit1.getText().toString(); //문자열가져옴
            String str2 = edit2.getText().toString();

            mEmailField.setText(str1); //폼에 채우기
            mPasswordField.setText(str2);

            createAccount(str1, str2);
        }
    }

    private void createAccount(String email, String password) { //이메일계정생성
        if (!validateForm()) { return; } //폼이 비었으면 return

        showProgressDialog();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUI(user);
                        } else {
                            updateUI(null);
                        }
                        hideProgressDialog();
                    }
                });
    }

    //UI////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void updateUI(FirebaseUser user) { //UI 업데이트
        hideProgressDialog();
        if (user != null) { //현재 로그인 상태 이면
            mStatusTextView.setText(getString(R.string.emailpassword_status_fmt,
                    user.getEmail(), user.isEmailVerified()));
            findViewById(R.id.loginFields).setVisibility(View.GONE);
            findViewById(R.id.userFields).setVisibility(View.VISIBLE);
            findViewById(R.id.signInButton).setVisibility(View.GONE);

            UserName = user.getEmail();
            //Toast.makeText(getApplicationContext(),UserName, Toast.LENGTH_SHORT).show();

        } else { //현재 로그인 상태가 아니면
            mStatusTextView.setText(R.string.signed_out);
            findViewById(R.id.loginFields).setVisibility(View.VISIBLE);
            findViewById(R.id.userFields).setVisibility(View.GONE);
            findViewById(R.id.signInButton).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View v) { //버튼클릭시
        int i = v.getId();
        if (i == R.id.emailSignInButton) {
            signIn(mEmailField.getText().toString(), mPasswordField.getText().toString());
        } else if (i == R.id.signOutButton) {
            signOut();
        } else if (i == R.id.signInButton) {
            signIn_google();
        }else if (i == R.id.floatingbutton) {
            Intent intent = new Intent(this, WriteActivity.class); //WriteActivity으로 UserName값 옮기기
                Bundle bundle = new Bundle();
                bundle.putString("name", UserName);

                mAdapter = new BoardAdapter(mBoardList);
                Integer bnum = mAdapter.getItemCount(); //글 개수를 받아와서 게시판넘버(bnum)에 담아 WriteActivity로 옮기기
                bundle.putInt("bunm", bnum);
            intent.putExtras(bundle);
            startActivity(intent);
        }
    }

    //Adapter///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private class BoardAdapter extends RecyclerView.Adapter<BoardAdapter.MainViewHolder>{ //게시판 어뎁터
        private List<Board> mBoardList;

        public BoardAdapter(List<Board> mBoardList) {
            this.mBoardList = mBoardList;
        }

        @NonNull
        @Override
        public MainViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new MainViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_main, viewGroup, false));
        }

        @Override
        public void onBindViewHolder(@NonNull MainViewHolder mainViewHolder, final int i) { //view\
            Board data = mBoardList.get(i);
            mainViewHolder.mTitleTextView.setText(data.getTitle());
            mainViewHolder.mNameTextView.setText(data.getName());
            mainViewHolder.mContentTextView.setText(data.getContent());

            mainViewHolder.itemView.setOnClickListener(new View.OnClickListener() { //리스트 클릭시 리스너
                @Override
                public void onClick(View v) { //클릭시 삭제
                    String id = mBoardList.get(i).getId(); //클릭한 인덱스의 아이디값 가져오기

                    CollectionReference cr = mStore.collection("board");
                    Query query = cr.whereEqualTo("id", id);
                    query.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if(task.isSuccessful()) {
                                for(QueryDocumentSnapshot dc : task.getResult()){
                                    String id = (String) dc.getData().get("id");
                                    Toast.makeText(getApplicationContext(), id, Toast.LENGTH_SHORT).show();

                                    mStore.collection("board").document(id)
                                            .delete()
                                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                @Override
                                                public void onSuccess(Void aVoid) {
                                                    Toast.makeText(getApplicationContext(), "삭제 완료", Toast.LENGTH_SHORT).show();
                                                    UploadBoard();
                                                }
                                            });
                                }
                            }
                        }
                    });
                }
            });
        }

        @Override
        public int getItemCount() {
            return mBoardList.size();
        }

        class MainViewHolder extends RecyclerView.ViewHolder{
            private TextView mTitleTextView;
            private TextView mNameTextView;
            private TextView mContentTextView;

            public MainViewHolder(@NonNull View itemView) {
                super(itemView);

                mTitleTextView = itemView.findViewById(R.id.item_title);
                mNameTextView = itemView.findViewById(R.id.item_name);
                mContentTextView = itemView.findViewById(R.id.item_content);
            }
        }
    }
}
