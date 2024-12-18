package com.nlscan.scantool;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nlscan.nlsdk.NLDevice;
import com.nlscan.nlsdk.NLDeviceStream;
import com.nlscan.nlsdk.NLUartStream;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private Button bnOpenDevice;
    private EditText editSerialname, editBaud;
    TextView textss;
    private Spinner spCommtype;
    private byte[] barcodeBuff = new byte[2*1024];
    private int barcodeLen = 0;

    private NLDeviceStream ds = new NLDevice(NLDeviceStream.DevClass.DEV_COMPOSITE);
    private boolean usbOpenChecked = false;
    private boolean permissionsAreOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionsAreOk = checkPermissions();

        bnOpenDevice = findViewById(R.id.bnOpenDevice);
        editSerialname = findViewById(R.id.editSerialname);
        editBaud = findViewById(R.id.editBaud);
        textss = findViewById(R.id.textss);
        spCommtype = findViewById(R.id.spCommtype);

        bnOpenDevice.setOnClickListener(v -> OnOpenCloseDevice());
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionsAreOk = checkPermissions();
            if (permissionsAreOk) {
                Log.i("NLCOMM-DEMO", "sdcard permission is ok.");
            }
        } else {
            finish();
        }
    }

    Observable<Integer> observable = Observable.create(emitter -> emitter.onNext(1));

    Consumer<Integer> usbRecvObserver = integer -> {
        String scannedData = new String(barcodeBuff, 0, barcodeLen);
        Log.d("NLCOMM-DEMO", "Scanned Barcode: " + scannedData);
        textss.setText(scannedData);
    };

    void OnOpenCloseDevice() {
        if (usbOpenChecked) {
            ds.close();
            usbOpenChecked = false;
            bnOpenDevice.setText("Open Device");
            return;
        }

        if (ds.getDevObj().getClass().equals(NLUartStream.class)) {
            String serialName = editSerialname.getText().toString();
            String baudString = editBaud.getText().toString();
            int baud;

            if (serialName.isEmpty() || baudString.isEmpty() || !serialName.startsWith("/dev/tty")) {
                Log.e("NLCOMM-DEMO", "Serial name or baud is error!");
                return;
            }

            try {
                baud = Integer.parseInt(baudString);
            } catch (NumberFormatException e) {
                return;
            }

            if (!ds.open(serialName, baud, (recvBuff, len) -> {
                barcodeLen = len;
                if (usbOpenChecked) {
                    System.arraycopy(recvBuff, 0, barcodeBuff, 0, len);
                    observable.subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(usbRecvObserver);
                }
            })) {
                bnOpenDevice.setText("Open Device");
                usbOpenChecked = false;
                return;
            }
        } else {
            if (!ds.open(this, new NLDeviceStream.NLUsbListener() {
                @Override
                public void actionUsbPlug(int event) {
                    if (event != 1) {
                        ds.close();
                        observable.subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(integer -> {
                                    usbOpenChecked = false;
                                    bnOpenDevice.setText("Open Device");
                                });
                    }
                }

                @Override
                public void actionUsbRecv(byte[] recvBuff, int len) {
                    barcodeLen = len;
                    if (usbOpenChecked) {
                        System.arraycopy(recvBuff, 0, barcodeBuff, 0, len);
                        observable.subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(usbRecvObserver);
                    }
                }

            })) {
                bnOpenDevice.setText("Open Device");
                usbOpenChecked = false;
                return;
            }
        }
        bnOpenDevice.setText("Close Device");
        usbOpenChecked = true;
    }
}