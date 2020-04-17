package com.example.sch.vote;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.IOException;

import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.RECEIVE_SMS;

public class MainActivity extends AppCompatActivity {
    public EditText editIP;
    public EditText editPort;
    public EditText editPassword;
    public Button connectButton;
    public Button resultButton;
    public TextView logText;
    public Thread thread;
    public Handler handler;
    private ConnectAdapter connectAdapter;
    private boolean connected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connected = false;
        editIP = (EditText) findViewById(R.id.editIP);
        editPassword = (EditText) findViewById(R.id.editPassword);
        editPort = (EditText) findViewById(R.id.editPort);
        connectButton = (Button) findViewById(R.id.connectButton);
        resultButton = (Button) findViewById(R.id.resultButton);
        logText = (TextView) findViewById(R.id.logText);
        editPassword.setText("123456");
        editPassword.setEnabled(false);
        if (checkSelfPermission(READ_SMS) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_SMS}, 1);
        }
        if (checkSelfPermission(RECEIVE_SMS) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.RECEIVE_SMS}, 1);
        }
        editIP.setText("10.215.217.212");
        editPort.setText("53217");

        connectAdapter = new ConnectAdapter();
        final View.OnClickListener connectListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String severAddress = editIP.getText().toString();
                final int Port = Integer.parseInt(editPort.getText().toString());
                if (!connected) {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                connectAdapter = new ConnectAdapter(severAddress, Port, handler);
                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                                    IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
                                    intentFilter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
                                    registerReceiver(connectAdapter, intentFilter);
                                }
                                Message m = Message.obtain();
                                m.what = 1;
                                m.obj = "Connecting";
                                handler.sendMessage(m);
                            } catch (IOException e) {
                                Message m = Message.obtain();
                                m.what = 1;
                                m.obj = e.getCause();
                                handler.sendMessage(m);
                            }
                        }
                    };
                    thread = new Thread(r);
                    thread.start();
                }
            }
        };
        connectButton.setOnClickListener(connectListener);
        logText.append("Listener Set.\n");
        //set Handler

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 1) {
                    //Normal Append Message
                    super.handleMessage(msg);
                    logText.append(msg.obj.toString() + "\n");
                } else if (msg.what == 2) {

                    connectButton.setText("Disconnect");
                }
                connectButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        thread.interrupt();
                        connectButton.setOnClickListener(connectListener);
                        connectButton.setText("Connect");
                        connectAdapter = null;
                        connected = false;
                    }
                });
                super.handleMessage(msg);
                logText.append(msg.obj.toString());
            }
        };
        //Set Click Listener for Connect Button




    }

}

