package ir.doorbash.lamp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.ybq.android.spinkit.SpinKitView;

import static ir.doorbash.lamp.Constants.REQUEST_ENABLE_BT;

public class MainActivity extends AppCompatActivity implements SerialListener {

    private enum ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public static final String DEVICE_ADDRESS = "20:16:03:08:32:85";
    public static final int STATUS_CHECK_INTERVAL = 5000;
    public static final int CONNECT_AGAIN_INTERVAL = 5000;

    private BluetoothAdapter bluetoothAdapter;
    private SerialSocket socket;
    private ConnectionStatus state = ConnectionStatus.DISCONNECTED;

    Handler handler;
    Button toggle;
    TextView status;
    SpinKitView loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();
        toggle = findViewById(R.id.toggle);
        status = findViewById(R.id.status);
        loading = findViewById(R.id.loading);

        toggle.setOnClickListener(v -> {
                    send("T");
                    if (toggle.getText().equals("Turn Off")) {
                        handler.post(() -> toggle.setText("Turn On"));
                    } else {
                        handler.post(() -> toggle.setText("Turn Off"));
                    }
                }
        );

        connect();
        setCheckStatusInterval();
    }

    private void setCheckStatusInterval() {
        handler.postDelayed(() -> {
            if (state == ConnectionStatus.CONNECTED) {
                send("S");
            }
            setCheckStatusInterval();
        }, STATUS_CHECK_INTERVAL);
    }

    private void connect() {
        System.out.println("connecting...");
        if (state != ConnectionStatus.DISCONNECTED) {
            System.out.println("connect: already connected");
            return;
        }
        state = ConnectionStatus.CONNECTING;
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) || bluetoothAdapter == null) {
            try {
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (!bluetoothAdapter.isEnabled()) {
                    status.setText("bluetooth is off");
                    handler.postDelayed(this::connect, CONNECT_AGAIN_INTERVAL);
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    return;
                }
                if (!deviceIsBond()) {
                    status.setText("device is not bond");
                    return;
                }
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
                String deviceName = device.getName() != null ? device.getName() : device.getAddress();
                status.setText("connecting to " + deviceName);
                handler.post(() -> {
                    loading.setVisibility(View.VISIBLE);
                    toggle.setVisibility(View.INVISIBLE);
                });
                socket = new SerialSocket();
                socket.connect(MainActivity.this, this, device);
            } catch (Exception e) {
                onSerialConnectError(e);
            }

        } else {
            status.setText("bluetooth is not supported");
        }
    }

    private void disconnect() {
        state = ConnectionStatus.DISCONNECTED;
        handler.post(() -> {
            loading.setVisibility(View.VISIBLE);
            toggle.setVisibility(View.INVISIBLE);
        });
        socket.disconnect();
        socket = null;
    }

    boolean deviceIsBond() {
        if (bluetoothAdapter != null) {
            System.out.println("bond devices list");
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
//                if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE)
//                    listItems.add(device);
                System.out.println(device.getName());
                System.out.println(device.getAddress());
                if (device.getAddress().equals(DEVICE_ADDRESS)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void send(String str) {
        System.out.println("sending " + str + " ...");
        if (state != ConnectionStatus.CONNECTED) {
            Toast.makeText(MainActivity.this, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] data = (str).getBytes();
            socket.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        System.out.println("data >> " + new String(data));
        if (data[0] == 'n') {
            handler.post(() -> toggle.setText("Turn On"));
        } else if (data[0] == 'y') {
            handler.post(() -> toggle.setText("Turn Off"));
        }
        handler.post(() -> {
            loading.setVisibility(View.INVISIBLE);
            toggle.setVisibility(View.VISIBLE);
        });
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        System.out.println("connected");
        handler.post(() -> status.setText("connected to device"));
        state = ConnectionStatus.CONNECTED;
        handler.postDelayed(() -> send("S"), 1000);
    }

    @Override
    public void onSerialConnectError(Exception e) {
        System.out.println("connection failed: " + e.getMessage());
        handler.post(() -> status.setText("connection failed: " + e.getMessage()));
        disconnect();
        handler.postDelayed(this::connect, CONNECT_AGAIN_INTERVAL);
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        System.out.println("connection lost: " + e.getMessage());
        disconnect();
        handler.postDelayed(this::connect, CONNECT_AGAIN_INTERVAL);
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            System.out.println("resultCode = " + resultCode);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
