package com.example.rgbcontrol;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.felhr.utils.HexData;
import com.rarepebble.colorpicker.ColorPickerView;

import java.lang.ref.WeakReference;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private UsbService usbService;
    private TextView display;
    private EditText editText;
    private MyHandler mHandler;
    private AlertDialog.Builder builder;
    private ColorPickerView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.button);
        view = (ColorPickerView) findViewById(R.id.picker);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                @ColorInt int i = 0xFFFFFFF;
                int c = view.getColor();
                System.out.println(Integer.toHexString(c));
                //view.setColor(i);

            }
        });
        builder = new AlertDialog.Builder(this);
        mHandler = new MyHandler(this);
        Button sendButton = (Button) findViewById(R.id.buttonSend);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (view.getColor()!=0) {
                    String data = convertRGB(view.getColor());
                    System.out.println(data);
                    if (usbService != null) { // if UsbService was correctly binded, Send data
                        System.out.println("Here");
                        usbService.write(("0x"+data).getBytes());
                    }
                }
            }
        });
    }

    private String convertRGB(int colorVal) {
        String color = Integer.toHexString(colorVal);
        float alpha = (Integer.decode("0x"+color.substring(color.length()-8,color.length()-6)))/(float)256;
        //System.out.println("alpha"+alpha);
        //System.out.println(Integer.decode("0x"+color.substring(color.length()-8,color.length()-6)));


        String r = color.substring(color.length()-6,color.length()-4);
        String g = color.substring(color.length()-4,color.length()-2);
        String b = color.substring(color.length()-2,color.length());
        //System.out.println("r: "+r+" g: "+g+" b: "+b);

        //System.out.println("r: "+Integer.decode("0x"+r)+" g: "+Integer.decode("0x"+g)+" b: "+Integer.decode("0x"+b));

        int conR = Math.round((1 - alpha) * 1 + alpha * Integer.decode("0x"+r));
        int conG = Math.round((1 - alpha) * 18 + alpha * Integer.decode("0x"+g));
        int conB = Math.round((1 - alpha) * 128 + alpha * Integer.decode("0x"+b));
        String hexR=Integer.toHexString(conR);
        if(conR<16)
            hexR = 0+hexR;
        String hexG=Integer.toHexString(conG);
        if(conG<16)
            hexG = 0+hexG;
        String hexB=Integer.toHexString(conB);
        if(conB<16)
            hexB = 0+hexB;

        //System.out.println("r: "+conR+" g: "+conG+" b: "+conB);

        return hexR+hexG+hexB;
    }

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }
    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    AlertDialog.Builder builder = mActivity.get().builder;
                    String data = (String) msg.obj;
                    if(data=="SUCCESS"){
                        builder.setMessage("The Color was set");
                    }
                    else{
                        builder.setMessage("An error occured");
                    }
                    //Creating dialog box
                    AlertDialog alert = builder.create();
                    //Setting the title manually
                    alert.setTitle("AlertDialogExample");
                    alert.show();
                    break;
                case UsbService.CTS_CHANGE:
                    Toast.makeText(mActivity.get(), "CTS_CHANGE",Toast.LENGTH_LONG).show();
                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(mActivity.get(), "DSR_CHANGE",Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
}
