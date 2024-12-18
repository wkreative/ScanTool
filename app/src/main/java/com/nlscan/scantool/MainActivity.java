package com.nlscan.scantool;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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

    private Button bnScanBarcode, bnGetDeviceInfo, bnRestartDevice, bnOpenDevice, bnScanEnable, bnSenseMode;
    private EditText editSerialname, editBaud;
    private Spinner spCommtype;
    private byte[] barcodeBuff = new byte[2*1024];
    private int barcodeLen = 0;

    private NLDeviceStream ds = new NLDevice(NLDeviceStream.DevClass.DEV_COMPOSITE);
    private String deviceInfo = null;
    static String TAG   = "NLCOMM-DEMO";
    private boolean usbOpenChecked = false;
    private boolean permissionsAreOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionsAreOk = checkPermissions();

        // Bind views manually
        bnScanBarcode = findViewById(R.id.bnScanBarcode);
        bnGetDeviceInfo = findViewById(R.id.bnGetDeviceInfo);
        bnRestartDevice = findViewById(R.id.bnRestartDevice);
        bnOpenDevice = findViewById(R.id.bnOpenDevice);
        bnScanEnable = findViewById(R.id.bnScanEnable);
        bnSenseMode = findViewById(R.id.bnSenseMode);
        editSerialname = findViewById(R.id.editSerialname);
        editBaud = findViewById(R.id.editBaud);
        spCommtype = findViewById(R.id.spCommtype);

        setEnable(false);
        setTitle("ScanTool SDK" + ds.GetSdkVersion());

        // Set listeners manually
        bnOpenDevice.setOnClickListener(v -> OnOpenCloseDevice());
        bnGetDeviceInfo.setOnClickListener(v -> OnGetDeviceInfo());
        bnRestartDevice.setOnClickListener(v -> OnRestartDevice());
        bnScanEnable.setOnClickListener(v -> onScanEnable());
        spCommtype.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                onCommselect(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Optionally handle if nothing is selected
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (ds != null)
            ds.close();
        super.onDestroy();
    }

    private void setEnable(boolean enable) {
        bnScanBarcode.setEnabled(enable);
        bnGetDeviceInfo.setEnabled(enable);
        bnRestartDevice.setEnabled(enable);
        bnScanEnable.setEnabled(enable);
        bnSenseMode.setEnabled(enable);
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
        switch (requestCode) {
            case 1:
            case 2:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsAreOk = checkPermissions();
                    if (permissionsAreOk) {
                        Log.i(TAG, "sdcard permission is ok.");
                    }
                } else {
                    finish();
                }
                return;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    Observable<Integer> observable = Observable.create(emitter -> emitter.onNext(1));

    Consumer<Integer> usbRecvObserver = integer -> {
        // Print the scanned barcode result
        String scannedData = new String(barcodeBuff, 0, barcodeLen);
        Log.d(TAG, "Scanned Barcode: " + scannedData);
    };

    Consumer<Integer> usbPlugObserver = integer -> {
        usbOpenChecked = false;
        bnOpenDevice.setText(R.string.TextOpen);
        setEnable(false);
    };

    void OnOpenCloseDevice() {
        deviceInfo = null;
        Log.d(TAG, "OnOpenCloseDevice  " + usbOpenChecked);
        if (usbOpenChecked) {
            ds.close();
            setEnable(false);
            usbOpenChecked = false;
            bnOpenDevice.setText(R.string.TextOpen);
            return;
        }

        if (ds.getDevObj().getClass().equals(NLUartStream.class)) {
            String serialName = editSerialname.getText().toString();
            String baudString = editBaud.getText().toString();
            int baud;

            if (serialName.isEmpty() || baudString.isEmpty() || !serialName.startsWith("/dev/tty")) {
                Log.e(TAG, "Serial name or baud is error!");
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
                bnOpenDevice.setText(R.string.TextOpen);
                usbOpenChecked = false;
                return;
            }
        } else {
            if (!ds.open(this, new NLDeviceStream.NLUsbListener() {
                @Override
                public void actionUsbPlug(int event) {
                    if (event == 1) {
                        // Device plugged in
                    } else {
                        ds.close();
                        observable.subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(usbPlugObserver);
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
                bnOpenDevice.setText(R.string.TextOpen);
                usbOpenChecked = false;
                return;
            }
        }
        bnOpenDevice.setText(R.string.TextClose);
        usbOpenChecked = true;
        setEnable(true);
    }

    void onCommselect(int position) {
        Log.d(TAG, "onCommselect: " + position);
        switch (position) {
            case 0:
                ds = new NLDevice(NLDeviceStream.DevClass.DEV_CDC);
                editSerialname.setEnabled(false);
                editBaud.setEnabled(false);
                break;
            case 1:
                ds = new NLDevice(NLDeviceStream.DevClass.DEV_POS);
                editSerialname.setEnabled(false);
                editBaud.setEnabled(false);
                break;
            case 2:
                ds = new NLDevice(NLDeviceStream.DevClass.DEV_COMPOSITE);
                editSerialname.setEnabled(false);
                editBaud.setEnabled(false);
                break;
            case 3:
                ds = new NLDevice(NLDeviceStream.DevClass.DEV_UART);
                editSerialname.setEnabled(true);
                editBaud.setEnabled(true);
                break;
        }
    }

    void OnGetDeviceInfo() {
        deviceInfo = ds.getDeviceInformation();
        msgbox((deviceInfo != null ? deviceInfo : ""), "Device Information");
    }

    void OnRestartDevice() {
        ds.restartDevice();
        bnOpenDevice.setText(R.string.TextOpen);
    }

    void onScanEnable() {
        if (!ds.setConfig("SCNENA1")) {
            // ShowToast(getString(R.string.TextConfigErr));
        }
    }

    void msgbox(String text, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(text);
        if (title != null) builder.setTitle(title);
        builder.setPositiveButton("OK", null);
        builder.setCancelable(true);
        builder.create().show();
    }
}










/*
package com.nlscan.scantool;
import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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

    private Button bnScanBarcode, bnGetDeviceInfo, bnRestartDevice, bnOpenDevice, bnScanEnable, bnSenseMode;
    private EditText editSerialname, editBaud;
    private Spinner spCommtype;
    private byte[] barcodeBuff = new byte[2*1024];
    private int barcodeLen = 0;

    private NLDeviceStream ds = new NLDevice(NLDeviceStream.DevClass.DEV_COMPOSITE);
    private String deviceInfo = null;
    static String TAG   = "NLCOMM-DEMO";
    private boolean usbOpenChecked = false;
    private boolean permissionsAreOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionsAreOk = checkPermissions();

        // Bind views manually
        bnScanBarcode = findViewById(R.id.bnScanBarcode);
        bnGetDeviceInfo = findViewById(R.id.bnGetDeviceInfo);
        bnRestartDevice = findViewById(R.id.bnRestartDevice);
        bnOpenDevice = findViewById(R.id.bnOpenDevice);
        bnScanEnable = findViewById(R.id.bnScanEnable);
        bnSenseMode = findViewById(R.id.bnSenseMode);
        editSerialname = findViewById(R.id.editSerialname);
        editBaud = findViewById(R.id.editBaud);
        spCommtype = findViewById(R.id.spCommtype);

        setEnable(false);
        setTitle("ScanTool SDK" + ds.GetSdkVersion());

        // Set listeners manually
        bnOpenDevice.setOnClickListener(v -> OnOpenCloseDevice());
        bnGetDeviceInfo.setOnClickListener(v -> OnGetDeviceInfo());
        bnRestartDevice.setOnClickListener(v -> OnRestartDevice());
        bnScanEnable.setOnClickListener(v -> onScanEnable());
        spCommtype.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                onCommselect(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Optionally handle if nothing is selected
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (ds != null)
            ds.close();
        super.onDestroy();
    }

    private void setEnable(boolean enable) {
        bnScanBarcode.setEnabled(enable);
        bnGetDeviceInfo.setEnabled(enable);
        bnRestartDevice.setEnabled(enable);
        bnScanEnable.setEnabled(enable);
        bnSenseMode.setEnabled(enable);
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
        switch (requestCode) {
            case 1:
            case 2:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsAreOk = checkPermissions();
                    if (permissionsAreOk) {
                        Log.i(TAG, "sdcard permission is ok.");
                    }
                } else {
                    finish();
                }
                return;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    Observable<Integer> observable = Observable.create(emitter -> emitter.onNext(1));

    Consumer<Integer> usbRecvObserver = integer -> {
    };

    Consumer<Integer> usbPlugObserver = integer -> {
      //  MainActivity.this.ShowToast(getString(R.string.TextInfoPlugout));
        usbOpenChecked = false;
        bnOpenDevice.setText(R.string.TextOpen);
        setEnable(false);
    };

    void OnOpenCloseDevice() {
        deviceInfo = null;
        Log.d(TAG, "OnOpenCloseDevice  " + usbOpenChecked);
        if (usbOpenChecked) {
            ds.close();
            setEnable(false);
            usbOpenChecked = false;
            bnOpenDevice.setText(R.string.TextOpen);
            return;
        }

        if (ds.getDevObj().getClass().equals(NLUartStream.class)) {
            String serialName = editSerialname.getText().toString();
            String baudString = editBaud.getText().toString();
            int baud;

            if (serialName.isEmpty() || baudString.isEmpty() || !serialName.startsWith("/dev/tty")) {
                Log.e(TAG, "Serial name or baud is error!");
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
                bnOpenDevice.setText(R.string.TextOpen);
                usbOpenChecked = false;
                return;
            }
        } else {
            if (!ds.open(this, new NLDeviceStream.NLUsbListener() {
                @Override
                public void actionUsbPlug(int event) {
                    if (event == 1) {
                       // MainActivity.this.ShowToast(getString(R.string.TextInfoPlugin));
                    } else {
                        ds.close();
                       // MainActivity.this.ShowToast(getString(R.string.TextInfoPlugout));
                        observable.subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(usbPlugObserver);
                    }
                }

                @Override
                public void actionUsbRecv(byte[] recvBuff, int len) {
                    barcodeLen = len;
                    if (usbOpenChecked) {
                        System.arraycopy(recvBuff, 0, barcodeBuff, 0, len);
                        String prefix = String.format("scanBarCode len:%s data: ", barcodeLen);
                        String str = new String(barcodeBuff, 0, barcodeLen);
                        runOnUiThread(() -> {
                            // showText(prefix, str);
                        });
                    }
                }

            })) {
                bnOpenDevice.setText(R.string.TextOpen);
                usbOpenChecked = false;
                return;
            }
        }
        bnOpenDevice.setText(R.string.TextClose);
        usbOpenChecked = true;
        setEnable(true);
    }

    void onCommselect(int position) {
        Log.d(TAG, "onCommselect: " + position);
        switch (position) {
            case 0:
                ds = new NLDevice(NLDeviceStream.DevClass.DEV_CDC);
                editSerialname.setEnabled(false);
                editBaud.setEnabled(false);
                break;
            case 1:
                ds = new NLDevice(NLDeviceStream.DevClass.DEV_POS);
                editSerialname.setEnabled(false);
                editBaud.setEnabled(false);
                break;
            case 2:
                ds = new NLDevice(NLDeviceStream.DevClass.DEV_COMPOSITE);
                editSerialname.setEnabled(false);
                editBaud.setEnabled(false);
                break;
            case 3:
                ds = new NLDevice(NLDeviceStream.DevClass.DEV_UART);
                editSerialname.setEnabled(true);
                editBaud.setEnabled(true);
                break;
        }
    }

    void OnGetDeviceInfo() {
        deviceInfo = ds.getDeviceInformation();
        msgbox((deviceInfo != null ? deviceInfo : ""), "Device Information");
    }

    void OnRestartDevice() {
        ds.restartDevice();
        bnOpenDevice.setText(R.string.TextOpen);
    }

    void onScanEnable() {
        if (!ds.setConfig("SCNENA1")) {
         //   ShowToast(getString(R.string.TextConfigErr));
        }
    }

    void msgbox(String text, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(text);
        if (title != null) builder.setTitle(title);
        builder.setPositiveButton("OK", null);
        builder.setCancelable(true);
        builder.create().show();
    }
}
*/
