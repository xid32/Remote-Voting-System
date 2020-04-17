package com.example.sch.vote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class ConnectAdapter extends BroadcastReceiver {

    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    private String serverAddress;
    private int port;
    private Socket socket;
    public MsgEncoder msgEncoder;
    public MsgDecoder msgDecoder;
    public static Queue<KeyValueList> readyQueue;
    private Handler uiHandler;
    private ArrayList<Long> phoneList;
    private HashMap<String, Integer> voteResult;
    private String passcode;
    private int SecurityLevel;
    public ConnectAdapter() {

    }
    //OnReceive SMS message
    @Override
    public void onReceive(Context context, Intent intent) {
        Handler handler = new Handler();
        Log.e("SMS reciver", "Received Message");
        System.out.println("Received Message");
        if (intent.getAction().equals(SMS_RECEIVED_ACTION)) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                // get sms objects
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus.length == 0) {
                    return;
                }
                // large message might be broken into many
                SmsMessage[] messages = new SmsMessage[pdus.length];
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < pdus.length; i++) {
                    messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    sb.append(messages[i].getMessageBody());
                    KeyValueList temp = new KeyValueList();
                    temp.putPair("Scope", "VotingSystem");
                    temp.putPair("VoterPhoneNo", messages[i].getOriginatingAddress());
                    temp.putPair("MessageType", "Alert");
                    temp.putPair("Sender", "VotingSystem");
                    temp.putPair("Receiver", "VotingSystem");
                    temp.putPair("Name", "VotingSystem");
                    temp.putPair("MsgID", "701");
                    temp.putPair("CandidateID", messages[i].getMessageBody());
                    readyQueue.add(temp);
                    String t = new String("ID: " + messages[i].getMessageBody() + ", Num: " + messages[i].getOriginatingAddress());
                    Message msg = new Message();
                    msg.what = 1;
                    msg.obj = t;
                    handler.sendMessage(msg);
                    Toast.makeText(context, t, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
    public ConnectAdapter(String serverAddress, int port, Handler uiHandler) throws IOException {
        Log.e("ConnectADA",serverAddress+":"+port);
        readyQueue = new LinkedList<>();
        this.uiHandler = uiHandler;
        this.serverAddress = serverAddress;
        this.port = port;
        socket = new Socket(this.serverAddress, this.port);
        Log.e("ConnectADA",serverAddress+":"+port);
        msgDecoder = new MsgDecoder(socket.getInputStream());
        msgEncoder = new MsgEncoder(socket.getOutputStream());
        Message msg = Message.obtain();
        msg.what = 2;
        msg.obj = "Connected On Server:" + this.serverAddress + " : " + this.port + " .\n";
        uiHandler.sendMessage(msg);
        phoneList = new ArrayList<>();
        voteResult = new HashMap<>();
        sendInitMsg();
    }

    public void sendInitMsg() {
        try {
            KeyValueList k = new KeyValueList();
            // Send Register Message
            k.putPair("Scope", "VotingSystem");
            k.putPair("MessageType", "Register");
            k.putPair("Role", "Basic");
            k.putPair("Name", "VotingSystem");
            msgEncoder.sendMsg(k);
            //Send Connect Message;
            KeyValueList k2 = new KeyValueList();
            k2.putPair("Scope", "VotingSystem");
            k2.putPair("MessageType", "Connect");
            k2.putPair("Role", "Basic");
            k2.putPair("Name", "VotingSystem");
            msgEncoder.sendMsg(k2);
            //Settings
            KeyValueList k3 = new KeyValueList();
            k3.putPair("Scope", "VotingSystem");
            k3.putPair("MsgID", "21");
            k3.putPair("MessageType", "Setting");
            k3.putPair("Passcode", "123456");
            k3.putPair("SecurityLevel", "3");
            k3.putPair("Name", "VotingSystem");
            k3.putPair("Receiver", "VotingSystem");
            k3.putPair("InputMsgID 1", "701");
            k3.putPair("InputMsgID 2", "702");
            k3.putPair("InputMsgID 3", "703");
            k3.putPair("OutputMsgID 1", "711");
            k3.putPair("OutputMsgID 2", "712");
            k3.putPair("OutputMsgID 3", "726");
            msgEncoder.sendMsg(k3);
            Runnable runnable = new Runnable() {
                @Override
                @SuppressWarnings("InfiniteLoopStatement")
                public void run() {
                    getSISMessage();
                }
            };
            Thread tk = new Thread(runnable);
            tk.start();
            while (true) {
                if (readyQueue.peek() != null) {
                    msgEncoder.sendMsg(readyQueue.poll());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getSISMessage() {
        KeyValueList msg;
        while (true) {
            try {
                msg = msgDecoder.getMsg();
                anaylzeMsg(msg);
            } catch (Exception e) {
                Log.e("ConnectorAdapter", e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private void anaylzeMsg(KeyValueList msg) {
        // If message is not for voting, discard it.
        if (!msg.getValue("Scope").contains("VotingSystem")) {
            return;
        }
        Log.v("ConnectorAdapter", "Received SIS Message, Proceeding... ");
        Log.v("t", "Received SIS Message, MsgID =  "+ msg.getValue("MsgID"));
        int msgID = Integer.parseInt(msg.getValue("MsgID"));
        switch (msgID) {
            case 701:
                candiateType(msg);
                break;
            case 24:
                passcodeModify(msg);
                break;
            default:
                Message handlermsg = Message.obtain();
                handlermsg.what = 1;
                handlermsg.obj = "Received Message from:" + msg.getValue("Sender") + "\n";
                this.uiHandler.sendMessage(handlermsg);
        }
    }

    public void candiateType(KeyValueList msg) {
        long oritinalAddress = Long.parseLong(msg.getValue("VoterPhoneNo"));
        String msgContent = msg.getValue("CandidateID");
        if (oritinalAddress != 0) {
            System.out.println(oritinalAddress);
            if (!phoneList.contains(oritinalAddress)) {
                if (!voteResult.containsKey(msgContent)) {
                    phoneList.add(oritinalAddress);
                    voteResult.put(msgContent, 1);
                    String re = "Received message with candidate ID: " + msgContent + " by Phone number: " + oritinalAddress + "\n";
                    Message handlerMsg = Message.obtain();
                    handlerMsg.what = 1;
                    handlerMsg.obj = re;
                    uiHandler.sendMessage(handlerMsg);
                } else {
                    voteResult.put(msgContent, voteResult.get(msgContent) + 1);
                }
            }
        }
    }

    public void passcodeModify(KeyValueList msg) {
        if (!msg.getValue("Passcode").isEmpty() && !msg.getValue("SecurityLevel").isEmpty()) {
            passcode = msg.getValue("Passcode");
            SecurityLevel = Integer.parseInt(msg.getValue("SecurityLevel"));
        }
    }

}
