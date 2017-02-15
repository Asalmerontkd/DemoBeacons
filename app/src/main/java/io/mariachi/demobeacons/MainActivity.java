package io.mariachi.demobeacons;

import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Collection;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements BeaconConsumer  {
    @BindView(R.id.txtSalida)
    TextView txtSalida;

    BluetoothAdapter bluetoothAdapter;
    BeaconManager beaconManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter==null) // If null -> cerrar la aplicación, el dispositivo no tiene bluetooth
        {
            AlertDialog.Builder alerta = new AlertDialog.Builder(this);
            alerta.setMessage("Bluetooth no encontrado en el dispositivo.");
            alerta.setTitle("Bluetooth no encontrado.");
            alerta.setIcon(android.R.drawable.ic_dialog_alert);
            alerta.setCancelable(false);
            alerta.setPositiveButton("Salir", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    //Cerrar la aplicació por que no se encontró bluetooth.
                    finish();
                }
            });
            alerta.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!bluetoothAdapter.isEnabled()) //if Bluetooth no esta activo
        {
            AlertDialog.Builder activaBT = new AlertDialog.Builder(this);
            activaBT.setMessage("El bluetooth no se encuentra activo.\n¿Desea activarlo?");
            activaBT.setTitle("Bluetooth desactivado.");
            activaBT.setIcon(android.R.drawable.ic_dialog_alert);
            activaBT.setCancelable(false);
            activaBT.setPositiveButton("Activar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    //Activar el bluetooth
                    Intent bluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(bluetoothIntent, RESULT_OK);
                }
            });
            activaBT.setNegativeButton("Salir", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    //Si no se activa el bluetooth, salir de la aplicación
                    finish();
                }
            });
            activaBT.show();
        }

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));//AltBeacon
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));//AltBeacon
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=30,p:3-3:-41,i:4-11"));//Eddystone-EID
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19"));//Eddystone-UID
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15"));//Eddystone-TLM
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v"));//Eddystone-URL
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));//IBeacon
        //Comentar las siguientes dos lineas si quieres monitorear sin pausas
        beaconManager.setForegroundScanPeriod(10000l); //Tiempo de monitoreo 10000l = 10 segundos
        beaconManager.setForegroundBetweenScanPeriod(10000l); //Tiempo de espera entre monitoreos
        try {
            beaconManager.updateScanPeriods();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        beaconManager.bind(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    public void onBeaconServiceConnect() {
        Region region = new Region("myBeaons", null, null, null);

        //Monitorea en la region por beacons
        beaconManager.setMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                try {
                    beaconManager.startRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void didExitRegion(Region region) {
                try {
                    beaconManager.stopRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {

            }
        });

        //Cuando encuentra un beacon comienza a obtener sus datos como la distancia
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> collection, Region region) {
                for (final Beacon beacon : collection) {
                    final double precision;
                    double ratio = (beacon.getRssi() * 1.0) / beacon.getTxPower();
                    if (ratio < 1.0) {
                        precision = Math.pow(ratio, 10);
                    } else {
                        precision = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
                    }

                    Log.e("Beacon: "," MAC: "+beacon.getBluetoothAddress()+"\n BluetoothName: "+beacon.getBluetoothName()+"\n Distancia: "+precision);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String misDatos = " MAC: "+beacon.getBluetoothAddress()+"\n BluetoothName: "+beacon.getBluetoothName()+"\n Distancia: "+precision+" metros.";
                            setDatos(misDatos);
                        }
                    });
                }
            }
        });

        try {
            beaconManager.startMonitoringBeaconsInRegion(region);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    public void setDatos(String data)
    {
        txtSalida.setText(data);
    }
}
