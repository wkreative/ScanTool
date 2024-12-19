package com.nlscan.scantool;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nlscan.nlsdk.NLDevice;
import com.nlscan.nlsdk.NLDeviceStream;
import com.nlscan.nlsdk.NLUartStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;


public class MainActivity extends AppCompatActivity {

    Button bnClearResult;
    Button bnScanBarcode;
    Button bnGetDeviceInfo;
    Button bnRestartDevice;

    EditText editConfig;
    Button bnQueCfg;
    Button bnOpenDevice;
    EditText etResult;
    Button bnUpdateFirmware;
    TextView txtFilePath;
    Button bnScanEnable;
    Button bnSenseMode;
    Button bnGetImg;
    Button bnUpdateConfig;

    TextView editSerialname;
    TextView editBaud;

    ProgressBar pbUpdate;
    Spinner spCommtype;

    private final byte[] barcodeBuff = new byte[2 * 1024];
    private int barcodeLen = 0;

    private NLDeviceStream ds = new NLDevice(NLDeviceStream.DevClass.DEV_COMPOSITE);
    private String deviceInfo = null;
    static String TAG = "NLCOMM-DEMO";
    private final int REQ_CODE_SELECT_FW = 0x1823;
    private boolean usbOpenChecked = false;
    private boolean permissionsAreOk = false;
    String newFwPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionsAreOk = checkPermissions();
        setContentView(R.layout.activity_main);

        bnClearResult = findViewById(R.id.bnClearResult);
        bnScanBarcode = findViewById(R.id.bnScanBarcode);
        bnGetDeviceInfo = findViewById(R.id.bnGetDeviceInfo);
        bnRestartDevice = findViewById(R.id.bnRestartDevice);
        editConfig = findViewById(R.id.editConfig);
        bnQueCfg = findViewById(R.id.bnQueCfg);
        bnOpenDevice = findViewById(R.id.bnOpenDevice);
        etResult = findViewById(R.id.etResult);
        bnUpdateFirmware = findViewById(R.id.bnUpdateFirmware);
        txtFilePath = findViewById(R.id.txtFilePath);
        bnScanEnable = findViewById(R.id.bnScanEnable);
        bnSenseMode = findViewById(R.id.bnSenseMode);
        bnGetImg = findViewById(R.id.bnGetImg);
        bnUpdateConfig = findViewById(R.id.bnUpdateConfig);
        editSerialname = findViewById(R.id.editSerialname);
        editBaud = findViewById(R.id.editBaud);
        pbUpdate = findViewById(R.id.pbUpdate);
        spCommtype = findViewById(R.id.spCommtype);

        bnOpenDevice.setOnClickListener(v -> {
            OnOpenCloseDevice();
        });

/*
        spCommtype.setOnItemClickListener((parent, view, position, id) -> onCommselect(position));
*/

        bnClearResult.setOnClickListener(v -> {
            etResult.setText("");

        });

        bnGetDeviceInfo.setOnClickListener(v -> {
            OnGetDeviceInfo();
        });
        bnRestartDevice.setOnClickListener(v -> {
            ds.restartDevice();
            //ds.close();
            bnOpenDevice.setText(R.string.TextOpen);
        });
        bnScanBarcode.setOnClickListener(v -> {
            if (!scanBarCode()) {
                showText("OnScanBarcode", "failed");
            }
        });
        bnQueCfg.setOnClickListener(v -> {
            String strCommand;
            String strCommandAck;
            strCommand = editConfig.getText().toString();
            strCommandAck = ds.getConfig(strCommand);
            if (strCommandAck != null) etResult.setText(strCommandAck);
        });
        bnScanEnable.setOnClickListener(v -> {
            if (!ds.setConfig("SCNENA1")) {
                ShowToast(getString(R.string.TextConfigErr));
            }
        });
        bnSenseMode.setOnClickListener(v -> {
            if (!ds.setConfig("SCNMOD2")) {
                ShowToast(getString(R.string.TextConfigErr));
            }
        });
        bnUpdateFirmware.setOnClickListener(v -> {
            OnUpdate();
        });
        bnUpdateConfig.setOnClickListener(v -> {
            onUpdateConfig();
        });
        bnGetImg.setOnClickListener(v -> {
            GetImg();
        });


        setEnable(false);
        etResult.setFocusable(false);
        etResult.setFocusableInTouchMode(false);
        setTitle("ScanTool SDK" + ds.GetSdkVersion());
    }

    @Override
    protected void onDestroy() {
        if (ds != null) ds.close();
        super.onDestroy();
    }

    private void setEnable(boolean enable) {
        bnScanBarcode.setEnabled(enable);
        bnGetDeviceInfo.setEnabled(enable);
        bnRestartDevice.setEnabled(enable);

        bnQueCfg.setEnabled(enable);
        editConfig.setEnabled(enable);
        bnUpdateFirmware.setEnabled(enable);

        bnScanEnable.setEnabled(enable);
        bnSenseMode.setEnabled(enable);
        bnGetImg.setEnabled(enable);
        bnUpdateConfig.setEnabled(enable);
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
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {    // authorized
                    permissionsAreOk = checkPermissions();
                    if (permissionsAreOk) {
                        Log.i(TAG, "sdcard permission is ok.");
                    }
                } else {                                                        //permission denied
                    finish();
                }
                return;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    private void ShowToast(CharSequence toastText) {
        @SuppressLint("InflateParams") View toastRoot = getLayoutInflater().inflate(R.layout.toast, null);
        TextView message = toastRoot.findViewById(R.id.toast_message);
        message.setText(toastText);

        Toast toastStart = new Toast(this);
        toastStart.setGravity(Gravity.CENTER, 0, 10);
        toastStart.setDuration(Toast.LENGTH_SHORT);
        toastStart.setView(toastRoot);
        toastStart.show();
    }

    Observable<Integer> observable = Observable.create(new ObservableOnSubscribe<Integer>() {
        @Override
        public void subscribe(ObservableEmitter<Integer> emitter) {
            emitter.onNext(1);
        }
    });

    Consumer<Integer> usbRecvObserver = new Consumer<Integer>() {
        @Override
        public void accept(Integer integer) {
            showText(String.format("scanBarCode len:%s data: ", barcodeLen), new String(barcodeBuff, 0, barcodeLen));
        }
    };

    Consumer<Integer> usbPlugObserver = new Consumer<Integer>() {
        @Override
        public void accept(Integer integer) {
            MainActivity.this.ShowToast(getString(R.string.TextInfoPlugout));
            //ds.close();
            usbOpenChecked = false;
            bnOpenDevice.setText(R.string.TextOpen);
            setEnable(false);
        }
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

            if (!ds.open(serialName, baud, new NLDeviceStream.NLUartListener() {
                @Override
                public void actionRecv(byte[] recvBuff, int len) {
                    barcodeLen = len;
                    if (usbOpenChecked) {
                        System.arraycopy(recvBuff, 0, barcodeBuff, 0, len);
                        observable.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(usbRecvObserver);
                    }
                }

            })) {
                bnOpenDevice.setText(R.string.TextOpen);
                usbOpenChecked = false;
                return;
            }
        } else {
            // Listen to the USB unplugging event, and notify the main_activity thread when the USB is unplugged
            if (!ds.open(this, new NLDeviceStream.NLUsbListener() {
                @Override
                public void actionUsbPlug(int event) {
                    if (event == 1) {
                        MainActivity.this.ShowToast(getString(R.string.TextInfoPlugin));
                    } else {
                        ds.close();
                        MainActivity.this.ShowToast(getString(R.string.TextInfoPlugout));
                        observable.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(usbPlugObserver);
                    }
                }

                @Override
                public void actionUsbRecv(byte[] recvBuff, int len) {
                    barcodeLen = len;
                    if (usbOpenChecked) {
                        System.arraycopy(recvBuff, 0, barcodeBuff, 0, len);
                        String prefix = String.format("scanBarCode len:%s data: ", barcodeLen);
                        String str = new String(barcodeBuff, 0, barcodeLen);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showText(prefix, str);
                            }
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


    void OnUpdate() {
        int len;

        if (newFwPath == null) return;

        File file = new File(newFwPath);
        if (file.exists() && file.isFile()) {
            len = (int) file.length();
        } else {
            Log.d(TAG, "file doesn't exist or is not a file");
            return;
        }
        byte[] firmware = new byte[len];
        readFile(firmware, file);
        pbUpdate.setVisibility(View.VISIBLE);
        class Update implements Runnable {
            public void run() {
                ds.updateFirmware(firmware, new NLDeviceStream.updateListner() {
                    @Override
                    public void curProgress(String type, NLDeviceStream.NLUpdateState state, int percent) {
                        Log.d(TAG, type + ":" + state + " " + percent);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // update
                                pbUpdate.setProgress(percent);
                                if (type.equals("END update")) {
                                    showText("Firmware:", "Update success!");
                                    pbUpdate.setVisibility(View.GONE);
                                }
                            }
                        });
                    }
                });
            }
        }
        Thread t = new Thread(new Update());
        t.start();
        bnOpenDevice.setText(R.string.TextOpen);
    }

    void onUpdateConfig() {
        if (newFwPath == null) return;

        File file = new File(newFwPath);
        if (!file.exists() || !file.isFile() || !newFwPath.toLowerCase().endsWith(".xml")) {
            Log.d(TAG, "file doesn't exist or is not a xml file");
            return;
        }
        class UpdateConfig implements Runnable {
            public void run() {
                int ret = ds.updateConfig(file);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ret > 0) {
                            showText("Config:", "xml update success!");
                        } else if (ret == 0) {
                            showText("Config:", "xml update success and change interface!");
                            ds.close();
                            setEnable(false);
                            usbOpenChecked = false;
                            bnOpenDevice.setText(R.string.TextOpen);
                        } else {
                            showText("Config:", "xml update fail!");
                        }
                    }
                });
            }
        }
        Thread t = new Thread(new UpdateConfig());
        t.start();
    }

/*    @SuppressLint("SdCardPath")
    @OnClick(R.id.txtFilePath)
    void OnSelectFile() {
        new LFilePicker().withActivity(MainActivity.this).withRequestCode(REQ_CODE_SELECT_FW).withStartPath("/sdcard").withFileFilter(new String[]{".bin", ".bin2", ".pak", ".xml"}).withMutilyMode(false).start();
    }*/


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_SELECT_FW && resultCode == RESULT_OK) {
            List<String> list = data.getStringArrayListExtra("paths");
            if (list != null && list.size() > 0) {
                newFwPath = list.get(0);
                txtFilePath.setText(newFwPath);
            }
        }
    }

    private void isDirPathExist(String dirPath) {
        File file = new File(dirPath);
        if (!file.exists()) {
            if (!file.mkdirs()) Toast.makeText(getApplicationContext(), R.string.TextCreateBmpPathFail, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImage(byte[] bitmap, int w, int h) {
        @SuppressLint("SdCardPath") String dirPath = "/sdcard/newland/saveImages";
        byte[] bmp_head = {(byte) 0x42, (byte) 0x4d, (byte) 0x36, (byte) 0xb4, (byte) 0x04, (byte) 0x00,    // 640*480+1078
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x36, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x28, (byte) 0x00, 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x02, (byte) 0x00, (byte) 0x00,    // 640
                (byte) 0xe0, (byte) 0x01, (byte) 0x00, (byte) 0x00,    // 480
                (byte) 0x01, (byte) 0x00, (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x82, (byte) 0x05, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x03, (byte) 0x03, (byte) 0x03, (byte) 0x00, (byte) 0x04, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x05, (byte) 0x05, (byte) 0x05, (byte) 0x00, (byte) 0x06, (byte) 0x06, (byte) 0x06, (byte) 0x00, (byte) 0x07, (byte) 0x07, (byte) 0x07, (byte) 0x00, (byte) 0x08, (byte) 0x08, (byte) 0x08, (byte) 0x00, (byte) 0x09, (byte) 0x09, (byte) 0x09, (byte) 0x00, (byte) 0x0a, (byte) 0x0a, (byte) 0x0a, (byte) 0x00, (byte) 0x0b, (byte) 0x0b, (byte) 0x0b, (byte) 0x00, (byte) 0x0c, (byte) 0x0c, (byte) 0x0c, (byte) 0x00, (byte) 0x0d, (byte) 0x0d, (byte) 0x0d, (byte) 0x00, (byte) 0x0e, (byte) 0x0e, (byte) 0x0e, (byte) 0x00, (byte) 0x0f, (byte) 0x0f, (byte) 0x0f, (byte) 0x00, (byte) 0x10, (byte) 0x10, (byte) 0x10, (byte) 0x00, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x00, (byte) 0x12, (byte) 0x12, (byte) 0x12, (byte) 0x00, (byte) 0x13, (byte) 0x13, (byte) 0x13, (byte) 0x00, (byte) 0x14, (byte) 0x14, (byte) 0x14, (byte) 0x00, (byte) 0x15, (byte) 0x15, (byte) 0x15, (byte) 0x00, (byte) 0x16, (byte) 0x16, (byte) 0x16, (byte) 0x00, (byte) 0x17, (byte) 0x17, (byte) 0x17, (byte) 0x00, (byte) 0x18, (byte) 0x18, (byte) 0x18, (byte) 0x00, (byte) 0x19, (byte) 0x19, (byte) 0x19, (byte) 0x00, (byte) 0x1a, (byte) 0x1a, (byte) 0x1a, (byte) 0x00, (byte) 0x1b, (byte) 0x1b, (byte) 0x1b, (byte) 0x00, (byte) 0x1c, (byte) 0x1c, (byte) 0x1c, (byte) 0x00, (byte) 0x1d, (byte) 0x1d, (byte) 0x1d, (byte) 0x00, (byte) 0x1e, (byte) 0x1e, (byte) 0x1e, (byte) 0x00, (byte) 0x1f, (byte) 0x1f, (byte) 0x1f, (byte) 0x00, (byte) 0x20, (byte) 0x20, (byte) 0x20, (byte) 0x00, (byte) 0x21, (byte) 0x21, (byte) 0x21, (byte) 0x00, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x00, (byte) 0x23, (byte) 0x23, (byte) 0x23, (byte) 0x00, (byte) 0x24, (byte) 0x24, (byte) 0x24, (byte) 0x00, (byte) 0x25, (byte) 0x25, (byte) 0x25, (byte) 0x00, (byte) 0x26, (byte) 0x26, (byte) 0x26, (byte) 0x00, (byte) 0x27, (byte) 0x27, (byte) 0x27, (byte) 0x00, (byte) 0x28, (byte) 0x28, (byte) 0x28, (byte) 0x00, (byte) 0x29, (byte) 0x29, (byte) 0x29, (byte) 0x00, (byte) 0x2a, (byte) 0x2a, (byte) 0x2a, (byte) 0x00, (byte) 0x2b, (byte) 0x2b, (byte) 0x2b, (byte) 0x00, (byte) 0x2c, (byte) 0x2c, (byte) 0x2c, (byte) 0x00, (byte) 0x2d, (byte) 0x2d, (byte) 0x2d, (byte) 0x00, (byte) 0x2e, (byte) 0x2e, (byte) 0x2e, (byte) 0x00, (byte) 0x2f, (byte) 0x2f, (byte) 0x2f, (byte) 0x00, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x00, (byte) 0x31, (byte) 0x31, (byte) 0x31, (byte) 0x00, (byte) 0x32, (byte) 0x32, (byte) 0x32, (byte) 0x00, (byte) 0x33, (byte) 0x33, (byte) 0x33, (byte) 0x00, (byte) 0x34, (byte) 0x34, (byte) 0x34, (byte) 0x00, (byte) 0x35, (byte) 0x35, (byte) 0x35, (byte) 0x00, (byte) 0x36, (byte) 0x36, (byte) 0x36, (byte) 0x00, (byte) 0x37, (byte) 0x37, (byte) 0x37, (byte) 0x00, (byte) 0x38, (byte) 0x38, (byte) 0x38, (byte) 0x00, (byte) 0x39, (byte) 0x39, (byte) 0x39, (byte) 0x00, (byte) 0x3a, (byte) 0x3a, (byte) 0x3a, (byte) 0x00, (byte) 0x3b, (byte) 0x3b, (byte) 0x3b, (byte) 0x00, (byte) 0x3c, (byte) 0x3c, (byte) 0x3c, (byte) 0x00, (byte) 0x3d, (byte) 0x3d, (byte) 0x3d, (byte) 0x00, (byte) 0x3e, (byte) 0x3e, (byte) 0x3e, (byte) 0x00, (byte) 0x3f, (byte) 0x3f, (byte) 0x3f, (byte) 0x00, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x00, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x00, (byte) 0x42, (byte) 0x42, (byte) 0x42, (byte) 0x00, (byte) 0x43, (byte) 0x43, (byte) 0x43, (byte) 0x00, (byte) 0x44, (byte) 0x44, (byte) 0x44, (byte) 0x00, (byte) 0x45, (byte) 0x45, (byte) 0x45, (byte) 0x00, (byte) 0x46, (byte) 0x46, (byte) 0x46, (byte) 0x00, (byte) 0x47, (byte) 0x47, (byte) 0x47, (byte) 0x00, (byte) 0x48, (byte) 0x48, (byte) 0x48, (byte) 0x00, (byte) 0x49, (byte) 0x49, (byte) 0x49, (byte) 0x00, (byte) 0x4a, (byte) 0x4a, (byte) 0x4a, (byte) 0x00, (byte) 0x4b, (byte) 0x4b, (byte) 0x4b, (byte) 0x00, (byte) 0x4c, (byte) 0x4c, (byte) 0x4c, (byte) 0x00, (byte) 0x4d, (byte) 0x4d, (byte) 0x4d, (byte) 0x00, (byte) 0x4e, (byte) 0x4e, (byte) 0x4e, (byte) 0x00, (byte) 0x4f, (byte) 0x4f, (byte) 0x4f, (byte) 0x00, (byte) 0x50, (byte) 0x50, (byte) 0x50, (byte) 0x00, (byte) 0x51, (byte) 0x51, (byte) 0x51, (byte) 0x00, (byte) 0x52, (byte) 0x52, (byte) 0x52, (byte) 0x00, (byte) 0x53, (byte) 0x53, (byte) 0x53, (byte) 0x00, (byte) 0x54, (byte) 0x54, (byte) 0x54, (byte) 0x00, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x00, (byte) 0x56, (byte) 0x56, (byte) 0x56, (byte) 0x00, (byte) 0x57, (byte) 0x57, (byte) 0x57, (byte) 0x00, (byte) 0x58, (byte) 0x58, (byte) 0x58, (byte) 0x00, (byte) 0x59, (byte) 0x59, (byte) 0x59, (byte) 0x00, (byte) 0x5a, (byte) 0x5a, (byte) 0x5a, (byte) 0x00, (byte) 0x5b, (byte) 0x5b, (byte) 0x5b, (byte) 0x00, (byte) 0x5c, (byte) 0x5c, (byte) 0x5c, (byte) 0x00, (byte) 0x5d, (byte) 0x5d, (byte) 0x5d, (byte) 0x00, (byte) 0x5e, (byte) 0x5e, (byte) 0x5e, (byte) 0x00, (byte) 0x5f, (byte) 0x5f, (byte) 0x5f, (byte) 0x00, (byte) 0x60, (byte) 0x60, (byte) 0x60, (byte) 0x00, (byte) 0x61, (byte) 0x61, (byte) 0x61, (byte) 0x00, (byte) 0x62, (byte) 0x62, (byte) 0x62, (byte) 0x00, (byte) 0x63, (byte) 0x63, (byte) 0x63, (byte) 0x00, (byte) 0x64, (byte) 0x64, (byte) 0x64, (byte) 0x00, (byte) 0x65, (byte) 0x65, (byte) 0x65, (byte) 0x00, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x00, (byte) 0x67, (byte) 0x67, (byte) 0x67, (byte) 0x00, (byte) 0x68, (byte) 0x68, (byte) 0x68, (byte) 0x00, (byte) 0x69, (byte) 0x69, (byte) 0x69, (byte) 0x00, (byte) 0x6a, (byte) 0x6a, (byte) 0x6a, (byte) 0x00, (byte) 0x6b, (byte) 0x6b, (byte) 0x6b, (byte) 0x00, (byte) 0x6c, (byte) 0x6c, (byte) 0x6c, (byte) 0x00, (byte) 0x6d, (byte) 0x6d, (byte) 0x6d, (byte) 0x00, (byte) 0x6e, (byte) 0x6e, (byte) 0x6e, (byte) 0x00, (byte) 0x6f, (byte) 0x6f, (byte) 0x6f, (byte) 0x00, (byte) 0x70, (byte) 0x70, (byte) 0x70, (byte) 0x00, (byte) 0x71, (byte) 0x71, (byte) 0x71, (byte) 0x00, (byte) 0x72, (byte) 0x72, (byte) 0x72, (byte) 0x00, (byte) 0x73, (byte) 0x73, (byte) 0x73, (byte) 0x00, (byte) 0x74, (byte) 0x74, (byte) 0x74, (byte) 0x00, (byte) 0x75, (byte) 0x75, (byte) 0x75, (byte) 0x00, (byte) 0x76, (byte) 0x76, (byte) 0x76, (byte) 0x00, (byte) 0x77, (byte) 0x77, (byte) 0x77, (byte) 0x00, (byte) 0x78, (byte) 0x78, (byte) 0x78, (byte) 0x00, (byte) 0x79, (byte) 0x79, (byte) 0x79, (byte) 0x00, (byte) 0x7a, (byte) 0x7a, (byte) 0x7a, (byte) 0x00, (byte) 0x7b, (byte) 0x7b, (byte) 0x7b, (byte) 0x00, (byte) 0x7c, (byte) 0x7c, (byte) 0x7c, (byte) 0x00, (byte) 0x7d, (byte) 0x7d, (byte) 0x7d, (byte) 0x00, (byte) 0x7e, (byte) 0x7e, (byte) 0x7e, (byte) 0x00, (byte) 0x7f, (byte) 0x7f, (byte) 0x7f, (byte) 0x00, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x00, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x00, (byte) 0x82, (byte) 0x82, (byte) 0x82, (byte) 0x00, (byte) 0x83, (byte) 0x83, (byte) 0x83, (byte) 0x00, (byte) 0x84, (byte) 0x84, (byte) 0x84, (byte) 0x00, (byte) 0x85, (byte) 0x85, (byte) 0x85, (byte) 0x00, (byte) 0x86, (byte) 0x86, (byte) 0x86, (byte) 0x00, (byte) 0x87, (byte) 0x87, (byte) 0x87, (byte) 0x00, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x00, (byte) 0x89, (byte) 0x89, (byte) 0x89, (byte) 0x00, (byte) 0x8a, (byte) 0x8a, (byte) 0x8a, (byte) 0x00, (byte) 0x8b, (byte) 0x8b, (byte) 0x8b, (byte) 0x00, (byte) 0x8c, (byte) 0x8c, (byte) 0x8c, (byte) 0x00, (byte) 0x8d, (byte) 0x8d, (byte) 0x8d, (byte) 0x00, (byte) 0x8e, (byte) 0x8e, (byte) 0x8e, (byte) 0x00, (byte) 0x8f, (byte) 0x8f, (byte) 0x8f, (byte) 0x00, (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte) 0x00, (byte) 0x91, (byte) 0x91, (byte) 0x91, (byte) 0x00, (byte) 0x92, (byte) 0x92, (byte) 0x92, (byte) 0x00, (byte) 0x93, (byte) 0x93, (byte) 0x93, (byte) 0x00, (byte) 0x94, (byte) 0x94, (byte) 0x94, (byte) 0x00, (byte) 0x95, (byte) 0x95, (byte) 0x95, (byte) 0x00, (byte) 0x96, (byte) 0x96, (byte) 0x96, (byte) 0x00, (byte) 0x97, (byte) 0x97, (byte) 0x97, (byte) 0x00, (byte) 0x98, (byte) 0x98, (byte) 0x98, (byte) 0x00, (byte) 0x99, (byte) 0x99, (byte) 0x99, (byte) 0x00, (byte) 0x9a, (byte) 0x9a, (byte) 0x9a, (byte) 0x00, (byte) 0x9b, (byte) 0x9b, (byte) 0x9b, (byte) 0x00, (byte) 0x9c, (byte) 0x9c, (byte) 0x9c, (byte) 0x00, (byte) 0x9d, (byte) 0x9d, (byte) 0x9d, (byte) 0x00, (byte) 0x9e, (byte) 0x9e, (byte) 0x9e, (byte) 0x00, (byte) 0x9f, (byte) 0x9f, (byte) 0x9f, (byte) 0x00, (byte) 0xa0, (byte) 0xa0, (byte) 0xa0, (byte) 0x00, (byte) 0xa1, (byte) 0xa1, (byte) 0xa1, (byte) 0x00, (byte) 0xa2, (byte) 0xa2, (byte) 0xa2, (byte) 0x00, (byte) 0xa3, (byte) 0xa3, (byte) 0xa3, (byte) 0x00, (byte) 0xa4, (byte) 0xa4, (byte) 0xa4, (byte) 0x00, (byte) 0xa5, (byte) 0xa5, (byte) 0xa5, (byte) 0x00, (byte) 0xa6, (byte) 0xa6, (byte) 0xa6, (byte) 0x00, (byte) 0xa7, (byte) 0xa7, (byte) 0xa7, (byte) 0x00, (byte) 0xa8, (byte) 0xa8, (byte) 0xa8, (byte) 0x00, (byte) 0xa9, (byte) 0xa9, (byte) 0xa9, (byte) 0x00, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0x00, (byte) 0xab, (byte) 0xab, (byte) 0xab, (byte) 0x00, (byte) 0xac, (byte) 0xac, (byte) 0xac, (byte) 0x00, (byte) 0xad, (byte) 0xad, (byte) 0xad, (byte) 0x00, (byte) 0xae, (byte) 0xae, (byte) 0xae, (byte) 0x00, (byte) 0xaf, (byte) 0xaf, (byte) 0xaf, (byte) 0x00, (byte) 0xb0, (byte) 0xb0, (byte) 0xb0, (byte) 0x00, (byte) 0xb1, (byte) 0xb1, (byte) 0xb1, (byte) 0x00, (byte) 0xb2, (byte) 0xb2, (byte) 0xb2, (byte) 0x00, (byte) 0xb3, (byte) 0xb3, (byte) 0xb3, (byte) 0x00, (byte) 0xb4, (byte) 0xb4, (byte) 0xb4, (byte) 0x00, (byte) 0xb5, (byte) 0xb5, (byte) 0xb5, (byte) 0x00, (byte) 0xb6, (byte) 0xb6, (byte) 0xb6, (byte) 0x00, (byte) 0xb7, (byte) 0xb7, (byte) 0xb7, (byte) 0x00, (byte) 0xb8, (byte) 0xb8, (byte) 0xb8, (byte) 0x00, (byte) 0xb9, (byte) 0xb9, (byte) 0xb9, (byte) 0x00, (byte) 0xba, (byte) 0xba, (byte) 0xba, (byte) 0x00, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0x00, (byte) 0xbc, (byte) 0xbc, (byte) 0xbc, (byte) 0x00, (byte) 0xbd, (byte) 0xbd, (byte) 0xbd, (byte) 0x00, (byte) 0xbe, (byte) 0xbe, (byte) 0xbe, (byte) 0x00, (byte) 0xbf, (byte) 0xbf, (byte) 0xbf, (byte) 0x00, (byte) 0xc0, (byte) 0xc0, (byte) 0xc0, (byte) 0x00, (byte) 0xc1, (byte) 0xc1, (byte) 0xc1, (byte) 0x00, (byte) 0xc2, (byte) 0xc2, (byte) 0xc2, (byte) 0x00, (byte) 0xc3, (byte) 0xc3, (byte) 0xc3, (byte) 0x00, (byte) 0xc4, (byte) 0xc4, (byte) 0xc4, (byte) 0x00, (byte) 0xc5, (byte) 0xc5, (byte) 0xc5, (byte) 0x00, (byte) 0xc6, (byte) 0xc6, (byte) 0xc6, (byte) 0x00, (byte) 0xc7, (byte) 0xc7, (byte) 0xc7, (byte) 0x00, (byte) 0xc8, (byte) 0xc8, (byte) 0xc8, (byte) 0x00, (byte) 0xc9, (byte) 0xc9, (byte) 0xc9, (byte) 0x00, (byte) 0xca, (byte) 0xca, (byte) 0xca, (byte) 0x00, (byte) 0xcb, (byte) 0xcb, (byte) 0xcb, (byte) 0x00, (byte) 0xcc, (byte) 0xcc, (byte) 0xcc, (byte) 0x00, (byte) 0xcd, (byte) 0xcd, (byte) 0xcd, (byte) 0x00, (byte) 0xce, (byte) 0xce, (byte) 0xce, (byte) 0x00, (byte) 0xcf, (byte) 0xcf, (byte) 0xcf, (byte) 0x00, (byte) 0xd0, (byte) 0xd0, (byte) 0xd0, (byte) 0x00, (byte) 0xd1, (byte) 0xd1, (byte) 0xd1, (byte) 0x00, (byte) 0xd2, (byte) 0xd2, (byte) 0xd2, (byte) 0x00, (byte) 0xd3, (byte) 0xd3, (byte) 0xd3, (byte) 0x00, (byte) 0xd4, (byte) 0xd4, (byte) 0xd4, (byte) 0x00, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0x00, (byte) 0xd6, (byte) 0xd6, (byte) 0xd6, (byte) 0x00, (byte) 0xd7, (byte) 0xd7, (byte) 0xd7, (byte) 0x00, (byte) 0xd8, (byte) 0xd8, (byte) 0xd8, (byte) 0x00, (byte) 0xd9, (byte) 0xd9, (byte) 0xd9, (byte) 0x00, (byte) 0xda, (byte) 0xda, (byte) 0xda, (byte) 0x00, (byte) 0xdb, (byte) 0xdb, (byte) 0xdb, (byte) 0x00, (byte) 0xdc, (byte) 0xdc, (byte) 0xdc, (byte) 0x00, (byte) 0xdd, (byte) 0xdd, (byte) 0xdd, (byte) 0x00, (byte) 0xde, (byte) 0xde, (byte) 0xde, (byte) 0x00, (byte) 0xdf, (byte) 0xdf, (byte) 0xdf, (byte) 0x00, (byte) 0xe0, (byte) 0xe0, (byte) 0xe0, (byte) 0x00, (byte) 0xe1, (byte) 0xe1, (byte) 0xe1, (byte) 0x00, (byte) 0xe2, (byte) 0xe2, (byte) 0xe2, (byte) 0x00, (byte) 0xe3, (byte) 0xe3, (byte) 0xe3, (byte) 0x00, (byte) 0xe4, (byte) 0xe4, (byte) 0xe4, (byte) 0x00, (byte) 0xe5, (byte) 0xe5, (byte) 0xe5, (byte) 0x00, (byte) 0xe6, (byte) 0xe6, (byte) 0xe6, (byte) 0x00, (byte) 0xe7, (byte) 0xe7, (byte) 0xe7, (byte) 0x00, (byte) 0xe8, (byte) 0xe8, (byte) 0xe8, (byte) 0x00, (byte) 0xe9, (byte) 0xe9, (byte) 0xe9, (byte) 0x00, (byte) 0xea, (byte) 0xea, (byte) 0xea, (byte) 0x00, (byte) 0xeb, (byte) 0xeb, (byte) 0xeb, (byte) 0x00, (byte) 0xec, (byte) 0xec, (byte) 0xec, (byte) 0x00, (byte) 0xed, (byte) 0xed, (byte) 0xed, (byte) 0x00, (byte) 0xee, (byte) 0xee, (byte) 0xee, (byte) 0x00, (byte) 0xef, (byte) 0xef, (byte) 0xef, (byte) 0x00, (byte) 0xf0, (byte) 0xf0, (byte) 0xf0, (byte) 0x00, (byte) 0xf1, (byte) 0xf1, (byte) 0xf1, (byte) 0x00, (byte) 0xf2, (byte) 0xf2, (byte) 0xf2, (byte) 0x00, (byte) 0xf3, (byte) 0xf3, (byte) 0xf3, (byte) 0x00, (byte) 0xf4, (byte) 0xf4, (byte) 0xf4, (byte) 0x00, (byte) 0xf5, (byte) 0xf5, (byte) 0xf5, (byte) 0x00, (byte) 0xf6, (byte) 0xf6, (byte) 0xf6, (byte) 0x00, (byte) 0xf7, (byte) 0xf7, (byte) 0xf7, (byte) 0x00, (byte) 0xf8, (byte) 0xf8, (byte) 0xf8, (byte) 0x00, (byte) 0xf9, (byte) 0xf9, (byte) 0xf9, (byte) 0x00, (byte) 0xfa, (byte) 0xfa, (byte) 0xfa, (byte) 0x00, (byte) 0xfb, (byte) 0xfb, (byte) 0xfb, (byte) 0x00, (byte) 0xfc, (byte) 0xfc, (byte) 0xfc, (byte) 0x00, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0x00, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00,};


        isDirPathExist(dirPath);

        //time as file name
        long timeStamp = System.currentTimeMillis();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
        String sd = sdf.format(new Date(timeStamp));
        String fileName = sd + ".bmp";

        File f = new File(dirPath, fileName);
        if (f.exists()) {
            if (!f.delete())
                //Toast.makeText(getApplicationContext(), R.string.TextDelBmpFail, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Del the same img file fail.");

        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            int imgSize = w * h + 1078;

            // Modify the length field of the bmp file header
            bmp_head[2] = (byte) (imgSize & 0xff);
            bmp_head[3] = (byte) ((imgSize >> 8) & 0xff);
            bmp_head[4] = (byte) ((imgSize >> 16) & 0xff);
            bmp_head[5] = (byte) ((imgSize >> 24) & 0xff);

            // Modify the length and width fields of the bmp file header
            bmp_head[18] = (byte) (w & 0xff);
            bmp_head[19] = (byte) ((w >> 8) & 0xff);
            bmp_head[20] = (byte) ((w >> 16) & 0xff);
            bmp_head[21] = (byte) ((w >> 24) & 0xff);

            bmp_head[22] = (byte) (h & 0xff);
            bmp_head[23] = (byte) ((h >> 8) & 0xff);
            bmp_head[24] = (byte) ((h >> 16) & 0xff);
            bmp_head[25] = (byte) ((h >> 24) & 0xff);

            out.write(bmp_head);    // write bmp header
            out.write(bitmap);      // write bitmap content
            out.close();
            //Toast.makeText(getApplicationContext(), R.string.common_save_success, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "save success");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    void GetImg() {
        int[] wh = ds.getImgSize();
        int w = wh[0];
        int h = wh[1];
        int imgSize = w * h;

        if (imgSize != 0) {
            byte[] imgBuf = new byte[imgSize];

            pbUpdate.setVisibility(View.VISIBLE);
            class GetImg implements Runnable {
                public void run() {
                    boolean ret = ds.getImgBuff(imgBuf, imgSize, new NLDeviceStream.transImgListner() {
                        @Override
                        public void curProgress(int percent) {
                            Log.d(TAG, "Img " + percent);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // update
                                    pbUpdate.setProgress(percent);
                                    if (percent == 100) {
                                        pbUpdate.setVisibility(View.GONE);
                                        showText("GetImg:", "Get image succ!");
                                    }
                                }
                            });
                        }
                    });
                    if (ret) {
                        saveImage(imgBuf, w, h);
                    } else {
                        Log.i(TAG, "Get img fail");
                    }
                }
            }
            Thread t = new Thread(new GetImg());
            t.start();

        }
    }

    boolean scanBarCode() {
        if (!ds.isOpen()) {
            return false;
        }
        return ds.startScan();
    }

    void showText(String prefix, String text) {
        etResult.append(prefix);
        etResult.append(text);
        etResult.append("\n");
    }

    void msgbox(String text, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(text);
        if (title != null) builder.setTitle(title);
        builder.setPositiveButton("OK", null);
        builder.setCancelable(true);
        builder.create().show();
    }


    private void readFile(byte[] firmware, File file) {
        int len;
        BufferedInputStream bis = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            len = bis.read(firmware);
            if (len != firmware.length) {
                Log.d(TAG, "Read length is wrong :" + len);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
