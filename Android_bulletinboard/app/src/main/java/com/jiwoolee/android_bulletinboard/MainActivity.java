package com.jiwoolee.android_bulletinboard;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
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

    private String UserName;          //이메일 담을 변수
    private SwipeRefreshLayout swipe; //당겨서 새로고침

    private FirebaseFirestore mStore = FirebaseFirestore.getInstance(); //firestore 연결

    private CheckBox checkBox;           //checkbox
    private SharedPreferences CheckPre;  //checkbox값 저장할 SharedPreferences 선언
    private SharedPreferences.Editor CheckPreEdit;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        FirebaseApp.initializeApp(this);

        mStatusTextView = findViewById(R.id.status); //id값과 연결
        mEmailField = findViewById(R.id.fieldEmail);
        mPasswordField = findViewById(R.id.fieldPassword);
        mRecyclerView = findViewById(R.id.recyclerview);
        swipe = findViewById(R.id.swipe);

        findViewById(R.id.emailSignInButton).setOnClickListener(this);  //버튼 리스너 연결
        findViewById(R.id.signOutButton).setOnClickListener(this);
        findViewById(R.id.signInButton).setOnClickListener(this);
        findViewById(R.id.floatingbutton).setOnClickListener(this);
        findViewById(R.id.Testbutton).setOnClickListener(this);

        checkBox = findViewById(R.id.checkBox); //체크박스 리스너 연결
        checkBox.setOnCheckedChangeListener(onCheckedChangeListener);

        //구글 클라이언트 연결
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso); //firebase 연결
        mAuth = FirebaseAuth.getInstance();

        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() { //위로 당겨서 새로고침시
            @Override
            public void onRefresh() {
                UploadBoard();
                swipe.setRefreshing(false); //새로고침 종료
            }
        });

        CheckPre = getSharedPreferences("checkbox", 0);       //checkbox값 저장 위해 getSharedPreferences 사용
        CheckPreEdit = CheckPre.edit();
        if(CheckPre.getString("checkbox", "").equals("1")) {         //어플을 껐다 켰을 때 스위치 상태를 적용하기 위해 SharedPreferences 내용확인,
            checkBox.setChecked(true);                                       //1이면 체크
        } else {
            checkBox.setChecked(false);                                      //0이면 체크x
        }
    }

    @Override
    public void onStart() { //시작시
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser(); //현재 로그인 되어있는지 확인
        updateUI(currentUser);                             //로그인 여부에 따라 UI 업데이트
        UploadBoard();
    }

    private void UploadBoard(){ //게시판 쿼리
       mBoardList = new ArrayList<>();
        FirebaseUser user = mAuth.getCurrentUser();
        //Toast.makeText(MainActivity.this, name, Toast.LENGTH_LONG).show();

        if(CheckPre.getString("checkbox", "").equals("1")) { //체크박스에 체크가 되어있으면
            mStore.collection("board").whereEqualTo("name", UserName) //이메일로 쿼리
                    .orderBy("time", Query.Direction.DESCENDING).addSnapshotListener(new EventListener<QuerySnapshot>() { //작성시간 역순으로 정렬
                @Override
                public void onEvent(@Nullable QuerySnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                    for(DocumentChange dc : snapshot.getDocumentChanges()){
                        String id = (String) dc.getDocument().getData().get("id");
                        String title = (String) dc.getDocument().getData().get("title");
                        String content = (String) dc.getDocument().getData().get("content");
                        String name = (String) dc.getDocument().getData().get("name");
                        String time = (String) dc.getDocument().getData().get("time");

                        Board data = new Board(id, title, content, name, time);
                        mBoardList.add(data);
                    }
                    mAdapter = new BoardAdapter(mBoardList);
                    mRecyclerView.setAdapter(mAdapter);
                }
            });
        } else { //체크박스에 체크가 되어있지 않으면
            mStore.collection("board")
                    .orderBy("time", Query.Direction.DESCENDING).addSnapshotListener(new EventListener<QuerySnapshot>() { //작성시간 역순으로 정렬
                @Override
                public void onEvent(@Nullable QuerySnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                    for(DocumentChange dc : snapshot.getDocumentChanges()){
                        String id = (String) dc.getDocument().getData().get("id");
                        String title = (String) dc.getDocument().getData().get("title");
                        String content = (String) dc.getDocument().getData().get("content");
                        String name = (String) dc.getDocument().getData().get("name");
                        String time = (String) dc.getDocument().getData().get("time");

                        Board data = new Board(id, title, content, name, time);
                        mBoardList.add(data);
                    }
                    mAdapter = new BoardAdapter(mBoardList);
                    mRecyclerView.setAdapter(mAdapter);
                }
            });
        }
    }

    //로그인////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void signIn_google() { //구글 로그인
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

    private void signIn(String email, String password) { //이메일 로그인
        if (!validateForm()) { return; } //폼이 비어있으면 return
        showProgressDialog(); //프로그래스바 보이기
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() { //로그인성공시
                                @Override
                                public void onComplete(@NonNull Task<AuthResult> task) {
                                    if (task.isSuccessful()) {
                                        FirebaseUser user = mAuth.getCurrentUser();
                                        updateUI(user);
                                    } else {
                                        updateUI(null);
                        }
                        if (!task.isSuccessful()) { //로그인실패시
                            mStatusTextView.setText(R.string.auth_failed);
                        }
                        hideProgressDialog(); //프로그래스바 숨기기
                    }
                });
    }

    private void signIn_test() { //체험모드 로그인
        mEmailField = findViewById(R.id.fieldEmail);
        mPasswordField = findViewById(R.id.fieldPassword);

        mEmailField.setText(R.string.default_email);
        mPasswordField.setText(R.string.default_password);
        signIn(mEmailField.getText().toString(), mPasswordField.getText().toString());
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

        mEmailField.setText("");
        mPasswordField.setText("");
    }

    //회원가입//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void btn_createAccount(View view){
        AlertDialog.Builder builder = new AlertDialog.Builder(this); //커스텀 다이얼로그
        builder.setTitle("회원 가입");         //타이틀
        builder.setIcon(R.mipmap.ic_launcher); //아이콘

        LayoutInflater inflater = getLayoutInflater(); //다이얼로그 뷰 설정
        View v1 = inflater.inflate(R.layout.custom_dialog, null);
        builder.setView(v1);

        DialogListener listener = new DialogListener(); //리스너 연결
        builder.setPositiveButton("확인",listener);
        builder.setNegativeButton("취소", listener);

        builder.show(); //다이얼 로그 띄우기
    }

    class DialogListener implements DialogInterface.OnClickListener{ //커스텀 다이얼로그 리스너
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AlertDialog alert = (AlertDialog)dialog;
            EditText edit1 = (EditText)alert.findViewById(R.id.editText); //뷰가저옴
            EditText edit2 = (EditText)alert.findViewById(R.id.editText2);

            String str1 = edit1.getText().toString(); //문자열가져옴
            String str2 = edit2.getText().toString();

            mEmailField.setText(str1); //폼에 채우기
            mPasswordField.setText(str2);

            createAccount(str1, str2);
        }
    }

    private void createAccount(String email, String password) { //이메일계정생성
        if (!validateForm()) { return; } //폼이 비어있으면 return
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

    //게시판////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void updateUI(FirebaseUser user) { //UI 업데이트
        hideProgressDialog();
        if (user != null) { //현재 로그인 상태 이면
            mStatusTextView.setText(user.getEmail());
            findViewById(R.id.loginFields).setVisibility(View.GONE);
            findViewById(R.id.userFields).setVisibility(View.VISIBLE);
            findViewById(R.id.signInButton).setVisibility(View.GONE);
            UserName = user.getEmail();
        } else { //현재 로그인 상태가 아니면
            mStatusTextView.setText(R.string.signed_out);
            findViewById(R.id.loginFields).setVisibility(View.VISIBLE);
            findViewById(R.id.userFields).setVisibility(View.GONE);
            findViewById(R.id.signInButton).setVisibility(View.VISIBLE);
        }
    }

    private void uploadPost() { //게시글 작성
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this); //dialog 선언
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.edit_box, null, false);
        builder.setView(view);

        final EditText mWriteTitle = (EditText) view.findViewById(R.id.write_title); //참조
        final EditText mWriteContent = (EditText) view.findViewById(R.id.write_content);
        final TextView mWriteName = (TextView) view.findViewById(R.id.write_name);
        final TextView mWriteTime = (TextView) view.findViewById(R.id.write_time);
        mWriteName.setText(UserName); //mWriteName에 이메일 넣어주기

        final AlertDialog dialog = builder.create(); //dialog 생성

        Button ButtonSubmit = (Button) view.findViewById(R.id.write_upload); //업로드버튼 클릭시
        ButtonSubmit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String id = (String) mStore.collection("board").document().getId(); //board 테이블의 id값 받아서
                Map<String, Object> post = new HashMap<>();

                long now = System.currentTimeMillis();
                Date date = new Date(now);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                String getTime = sdf.format(date); //현재시간 받아오기

                mWriteTime.setText(getTime);

                post.put("id", id); //map 함수에 데이터 담기
                post.put("title", mWriteTitle.getText().toString());
                post.put("content", mWriteContent.getText().toString());
                post.put("name", mWriteName.getText().toString());
                post.put("time", mWriteTime.getText().toString());

                mStore.collection("board").document(id).set(post) //firsestore db에 업로드
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Toast.makeText(MainActivity.this, "업로드 완료", Toast.LENGTH_SHORT).show();
                                UploadBoard(); //새로고침
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(MainActivity.this, "업로드 실패", Toast.LENGTH_SHORT).show();
                            }
                        });
                dialog.dismiss(); //dialog 끄기
            }
        });
        dialog.show(); //dialog 보여주기
    }

    //listener//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onClick(View v) { //버튼클릭시
        int i = v.getId();
        if (i == R.id.signInButton) { //구글 로그인
            signIn_google();
        }else if (i == R.id.emailSignInButton) { //이메일 로그인
            signIn(mEmailField.getText().toString(), mPasswordField.getText().toString());
        }else if (i == R.id.Testbutton) { //체험모드 로그인
            signIn_test();
        } else if (i == R.id.signOutButton) { //로그아웃
            signOut();
        } else if (i == R.id.floatingbutton) { //게시글 작성
            uploadPost();
        }
    }

    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() { //체크박스 체크 클릭시
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked){ //체크시
                CheckPreEdit.putString("checkbox", "1");                // 1
                CheckPreEdit.commit();                                          // SharedPreferences에 저장
                //Toast.makeText(MainActivity.this, CheckPre.getString("checkbox", ""), Toast.LENGTH_LONG).show();

                UploadBoard();
            } else{
                CheckPreEdit.putString("checkbox", "0");                // 0
                CheckPreEdit.commit();                                          // SharedPreferences에 저장
                UploadBoard();
            }
        }
    };

    //Adapter///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private class BoardAdapter extends RecyclerView.Adapter<BoardAdapter.MainViewHolder>{ //게시판 어뎁터
        private List<Board> mBoardList;

        class MainViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
            private TextView mTitleTextView;
            private TextView mNameTextView;
            private TextView mContentTextView;
            FirebaseUser user = mAuth.getCurrentUser();

            public MainViewHolder(@NonNull View itemView) {
                super(itemView);
                mTitleTextView = itemView.findViewById(R.id.item_title);
                mNameTextView = itemView.findViewById(R.id.item_name);
                mContentTextView = itemView.findViewById(R.id.item_content);

                itemView.setOnCreateContextMenuListener(this); //리스너
            }

            @Override
            public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {// 컨텍스트 메뉴
                MenuItem Edit = contextMenu.add(Menu.NONE, 10, 1, "수정");
                MenuItem Delete = contextMenu.add(Menu.NONE, 20, 2, "삭제");
                Edit.setOnMenuItemClickListener(onEditMenu);
                Delete.setOnMenuItemClickListener(onEditMenu);
            }

            private final MenuItem.OnMenuItemClickListener onEditMenu = new MenuItem.OnMenuItemClickListener() { //컨텍스트 메뉴 클릭시
                @Override
                public boolean onMenuItemClick(MenuItem item){
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this); //dialog 선언
                    View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.edit_box, null, false);
                    builder.setView(view);

                    final EditText mWriteTitle = (EditText) view.findViewById(R.id.write_title); //참조
                    final EditText mWriteContent = (EditText) view.findViewById(R.id.write_content);
                    final TextView mWriteName = (TextView) view.findViewById(R.id.write_name);
                    final TextView mWriteTime = (TextView) view.findViewById(R.id.write_time);

                    mWriteTitle.setText(mBoardList.get(getAdapterPosition()).getTitle()); //선택한 포지션의 값 가져오기
                    mWriteContent.setText(mBoardList.get(getAdapterPosition()).getContent());
                    mWriteName.setText(mBoardList.get(getAdapterPosition()).getName());
                    mWriteTime.setText(mBoardList.get(getAdapterPosition()).getTime());

                    if(mWriteName.getText().toString().equals (user.getEmail())) { //로그인한 유저가 작성자와 동일인이라면{
                        switch (item.getItemId()) {
                            case 10: //수정
                                final AlertDialog dialog = builder.create(); //dialog 생성
                                Button ButtonSubmit = (Button) view.findViewById(R.id.write_upload); //업로드버튼 클릭시
                                ButtonSubmit.setOnClickListener(new View.OnClickListener() {
                                    public void onClick(View v) { String id = mBoardList.get(getAdapterPosition()).getId(); //클릭한 인덱스의 아이디값 가져오기
                                        Map<String, Object> post = new HashMap<>();

                                        long now = System.currentTimeMillis();
                                        Date date = new Date(now);
                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                                        String getTimeModi = sdf.format(date); //현재시간 받아오기

                                        post.put("id", id); //map 함수에 데이터 담기
                                        post.put("title", mWriteTitle.getText().toString());
                                        post.put("content", mWriteContent.getText().toString());
                                        post.put("name", mWriteName.getText().toString());
                                        post.put("time", mWriteTime.getText().toString());
                                        post.put("time_up", getTimeModi); //수정시간

                                        mStore.collection("board").document(id).set(post)
                                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                    @Override
                                                    public void onSuccess(Void aVoid) {
                                                        Toast.makeText(MainActivity.this, "수정 완료", Toast.LENGTH_SHORT).show();
                                                        UploadBoard();
                                                    }
                                                })
                                                .addOnFailureListener(new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(@NonNull Exception e) {
                                                        Toast.makeText(MainActivity.this, "수정 실패", Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                        dialog.dismiss();
                                    }
                                });
                                dialog.show();
                                break;

                            case 20: //삭제
                                String id = mBoardList.get(getAdapterPosition()).getId(); //클릭한 인덱스의 아이디값 가져오기
                                //Toast.makeText(MainActivity.this, id, Toast.LENGTH_SHORT).show();

                                mStore.collection("board").document(id).delete()
                                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void aVoid) {
                                                Toast.makeText(MainActivity.this, "삭제 완료", Toast.LENGTH_SHORT).show();
                                                UploadBoard(); //새로고침
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Toast.makeText(MainActivity.this, "삭제 실패", Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                break;
                        }
                    }else{
                        Toast.makeText(MainActivity.this, "작성자가 아닌 유저는 수정, 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            };
        }

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
        }
        @Override
        public int getItemCount() {
            return mBoardList.size();
        }
    }
}
