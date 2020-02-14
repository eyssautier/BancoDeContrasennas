package cl.theroot.passbank.fragmento;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.util.Collections;

import butterknife.BindView;
import butterknife.ButterKnife;
import cl.theroot.passbank.ActividadPrincipal;
import cl.theroot.passbank.Cifrador;
import cl.theroot.passbank.CustomFragment;
import cl.theroot.passbank.CustomToast;
import cl.theroot.passbank.DriveServiceHelper;
import cl.theroot.passbank.ExcepcionBancoContrasennas;
import cl.theroot.passbank.R;
import cl.theroot.passbank.datos.CategoriaCuentaDAO;
import cl.theroot.passbank.datos.CategoriaDAO;
import cl.theroot.passbank.datos.ContrasennaDAO;
import cl.theroot.passbank.datos.CuentaDAO;
import cl.theroot.passbank.datos.DBOpenHelper;
import cl.theroot.passbank.datos.DBOpenHelperDiccionario;
import cl.theroot.passbank.datos.ParametroDAO;
import cl.theroot.passbank.datos.nombres.NombreBD;
import cl.theroot.passbank.datos.nombres.NombreParametro;
import cl.theroot.passbank.dominio.Contrasenna;
import cl.theroot.passbank.dominio.Parametro;

import static android.app.Activity.RESULT_OK;


/**
 * A simple {@link Fragment} subclass.
 */
public class FragCrearLlaveMaestra extends CustomFragment implements View.OnClickListener {
    private static final String TAG = "BdC-FragCrearLlaveMa...";
    private static final int REQUEST_CODE_SIGN_IN = 1;

    @BindView(R.id.ET_newPassword)
    EditText ET_newPassword;
    @BindView(R.id.ET_newRepPassword)
    EditText ET_newRepPassword;
    @BindView(R.id.ET_cuenta_seleccionada)
    EditText ET_cuentaSeleccionada;
    @BindView(R.id.ET_respaldoLlaveMaestra)
    EditText ET_llaveMaestraRespaldo;
    @BindView(R.id.B_crear_llave_maestra)
    Button B_crear_llave_maestra;
    @BindView(R.id.IV_vaciar_usuario)
    ImageView IV_vaciarCuenta;

    private ProgressDialog mensajeProgreso;

    private DriveServiceHelper mDriveServiceHelper;
    private GoogleSignInClient mGoogleSignClient;

    private ParametroDAO parametroDAO;

    public FragCrearLlaveMaestra() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragmento_crear_llave_maestra, container, false);
        ButterKnife.bind(this, view);

        parametroDAO = new ParametroDAO(getActivity().getApplicationContext());

        B_crear_llave_maestra.setOnClickListener(this);
        ET_cuentaSeleccionada.setOnClickListener(v -> {
            seleccionarCuenta();
        });
        IV_vaciarCuenta.setOnClickListener(v -> {
            ET_cuentaSeleccionada.setText("");
            mDriveServiceHelper = null;
        });

        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                .build();
        mGoogleSignClient = GoogleSignIn.getClient(getContext(), signInOptions);

        mensajeProgreso = new ProgressDialog(getActivity());
        mensajeProgreso.setTitle("Cargando Respaldo");
        mensajeProgreso.setMessage("Se está cargado su respaldo, favor esperar...");
        mensajeProgreso.setCancelable(false);

        getActivity().invalidateOptionsMenu();
        return view;
    }

    @Override
    public void onClick(View view) {
        try {
            String newPass = ET_newPassword.getText().toString();
            String newRepPass = ET_newRepPassword.getText().toString();
            String llaveMaestraRespaldo = ET_llaveMaestraRespaldo.getText().toString();
            if (newPass.isEmpty()) {
                ET_newPassword.requestFocus();
                throw new ExcepcionBancoContrasennas("Error - Campo Vacío", "La aplicación requiere de una Llave Maestra, favor de rellenar el campo de Nueva Llave Maestra.");
            }
            if (newPass.length() < Cifrador.LARGO_MINIMO_LLAVE_MAESTRA) {
                ET_newPassword.requestFocus();
                ET_newPassword.setSelection(0, ET_newPassword.getText().length());
                throw new ExcepcionBancoContrasennas("Error - Llave Maestra Muy Corta", "La Llave Maestra elegida es muy corta, debería tener al menos " + Cifrador.LARGO_MINIMO_LLAVE_MAESTRA + " caracteres.");
            }
            if (!newPass.equals(newRepPass)) {
                ET_newRepPassword.requestFocus();
                ET_newRepPassword.setSelection(0, ET_newRepPassword.getText().length());
                throw new ExcepcionBancoContrasennas("Error - No Coincidencia", "No coinciden las Llaves Maestras ingresadas, favor reingresar los datos.");
            }

            String email = ET_cuentaSeleccionada.getText().toString();
            if (!email.isEmpty()) {
                if (llaveMaestraRespaldo.isEmpty()) {
                    ET_llaveMaestraRespaldo.requestFocus();
                    throw new ExcepcionBancoContrasennas("Error - Campo Vacío", "Para cargar su respaldo, debe ingresar la Llave Maestra asociada a dichos datos.");
                }

                //Deshabilitar rotación de pantalla...
                if (getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else {
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }

                mensajeProgreso.show();

                // Se cierran conexiones abiertas...
                DBOpenHelper.getInstance(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS).cerrarConexiones();
                DBOpenHelper.getInstance(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO).cerrarConexiones();
                DBOpenHelperDiccionario.getInstance(getActivity().getApplicationContext()).cerrarConexiones();

                //Se crea, en el dispositivo, el archivo que almacenará la base de datos de respaldo momentáneamente
                DBOpenHelper.getInstance(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO);
                java.io.File dbFileRespaldo = getActivity().getApplicationContext().getDatabasePath(NombreBD.BANCO_CONTRASENNAS_RESPALDO.toString());
                //Log.i(TAG, "Tamaño del respaldo antes de la descarga: " + dbFileRespaldo.length());
                if (dbFileRespaldo != null && mDriveServiceHelper != null) {
                    //i(TAG, "Obteniendo listado de respaldos...");
                    mDriveServiceHelper.queryFiles()
                            .addOnSuccessListener(fileList -> {
                                //Log.i(TAG, "Listado de respaldos obtenido");

                                DateTime fechaMasNuevo = null;
                                String idMasNuevo = null;
                                for (File file : fileList.getFiles()) {
                                    if (fechaMasNuevo == null || fechaMasNuevo.getValue() < file.getCreatedTime().getValue()) {
                                        fechaMasNuevo = file.getCreatedTime();
                                        idMasNuevo = file.getId();
                                    }
                                }

                                //Log.i(TAG, "Respaldo Más Nuevo - ID: " + idMasNuevo);

                                if (idMasNuevo != null) {
                                    //Log.i(TAG, "Descargando respaldo de Google Drive...");
                                    mDriveServiceHelper.downloadFile(dbFileRespaldo, idMasNuevo)
                                            .addOnSuccessListener(salida -> {
                                                //Log.i(TAG, "Respaldo descargado exitosamente");
                                                //Log.i(TAG, "Tamaño del respaldo después de la descarga: " + dbFileRespaldo.length());

                                                //Validar que la contraseña maestra ingresada, para el respaldo, es correcta
                                                ParametroDAO parametroDAORespaldo = new ParametroDAO(getActivity().getApplicationContext(), true);
                                                Parametro parSaltHash = parametroDAORespaldo.seleccionarUno(NombreParametro.SAL_HASH);
                                                Parametro parResultHash = parametroDAORespaldo.seleccionarUno(NombreParametro.RESULTADO_HASH);
                                                String hash = Cifrador.genHashedPass(llaveMaestraRespaldo, parSaltHash.getValor())[1];

                                                //Log.i(TAG, "Hash Original:  " + parResultHash.getValor());
                                                //Log.i(TAG, "Hash Ingresado: " + hash);

                                                if (!parResultHash.getValor().equals(hash)) {
                                                    // Se cierran conexiones abiertas...
                                                    DBOpenHelper.getInstance(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO).cerrarConexiones();
                                                    DBOpenHelper.deleteBackup(getActivity().getApplicationContext());
                                                    Log.e(TAG, "La Llave Maestra del Respaldo no es correcta");
                                                    mensajeProgreso.dismiss();
                                                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                                                    CustomToast.Build(this, R.string.llaveMaestraRespIncorrecta);
                                                    ET_llaveMaestraRespaldo.requestFocus();
                                                    ET_llaveMaestraRespaldo.setSelection(0, ET_llaveMaestraRespaldo.getText().length());
                                                    return;
                                                }

                                                //Se obtiene la llave de encriptación del respaldo
                                                Parametro parSaltEncr = parametroDAORespaldo.seleccionarUno(NombreParametro.SAL_ENCRIPTACION);
                                                String antiguaLlaveEncr = Cifrador.genHashedPass(llaveMaestraRespaldo, parSaltEncr.getValor())[1];

                                                // Se cierran conexiones abiertas...
                                                DBOpenHelper.getInstance(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS).cerrarConexiones();

                                                //Recreamos el archivo original
                                                DBOpenHelper.deleteOriginal(getActivity().getApplicationContext());
                                                DBOpenHelper.getInstance(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS);

                                                //Se copian los datos del archivo local momentáneo, a la base de datos de la aplicación
                                                CategoriaDAO.cargarRespaldo(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO, NombreBD.BANCO_CONTRASENNAS);
                                                CuentaDAO.cargarRespaldo(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO, NombreBD.BANCO_CONTRASENNAS);
                                                ParametroDAO.cargarRespaldo(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO, NombreBD.BANCO_CONTRASENNAS);
                                                CategoriaCuentaDAO.cargarRespaldo(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO, NombreBD.BANCO_CONTRASENNAS);
                                                ContrasennaDAO.cargarRespaldo(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO, NombreBD.BANCO_CONTRASENNAS);

                                                // Se cierran conexiones abiertas...
                                                DBOpenHelper.getInstance(getActivity().getApplicationContext(), NombreBD.BANCO_CONTRASENNAS_RESPALDO).cerrarConexiones();

                                                //Se elimina el archivo local momentáneo
                                                DBOpenHelper.deleteBackup(getActivity().getApplicationContext());

                                                //Se crea la llave maestra
                                                String[] saltYHash = Cifrador.genHashedPass(newPass, null);
                                                String saltEncr = Cifrador.genSalt();

                                                //Se reencriptan las contraseñas
                                                String llaveEncrNueva = Cifrador.genHashedPass(newPass, saltEncr)[1];
                                                ContrasennaDAO contrasennaDAO = new ContrasennaDAO(getActivity().getApplicationContext());
                                                for (Contrasenna contrasenna : contrasennaDAO.seleccionarTodas()) {
                                                    String valorDesencriptado = Cifrador.desencriptar(contrasenna.getValor(), antiguaLlaveEncr);
                                                    String valorEncriptado = Cifrador.encriptar(valorDesencriptado, llaveEncrNueva);
                                                    contrasenna.setValor(valorEncriptado);
                                                    if (contrasennaDAO.actualizarUna(contrasenna) != 1) {
                                                        Log.e(TAG, "Contraseña ID: " + contrasenna.getId() + " no reencriptada!");
                                                    }
                                                }

                                                //Se guarda la llave maestra
                                                parSaltHash = new Parametro(NombreParametro.SAL_HASH, saltYHash[0], null);
                                                parResultHash = new Parametro(NombreParametro.RESULTADO_HASH, saltYHash[1], null);
                                                parSaltEncr = new Parametro(NombreParametro.SAL_ENCRIPTACION, saltEncr, null);

                                                parametroDAO = new ParametroDAO(getActivity().getApplicationContext());
                                                if (parametroDAO.actualizarUna(parSaltHash) > 0) {
                                                    if (parametroDAO.actualizarUna(parResultHash) > 0) {
                                                        if (parametroDAO.actualizarUna(parSaltEncr) > 0) {
                                                            mensajeProgreso.dismiss();
                                                            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                                                            CustomToast.Build(this, R.string.llaveCreadoYRespaldoCargado);
                                                            ((ActividadPrincipal) getActivity()).cambiarFragmento(new FragInicioSesion());
                                                        }
                                                    }
                                                }
                                            })
                                            .addOnFailureListener(exception -> {
                                                Log.e(TAG, "Error al descargar el respaldo", exception);
                                                mensajeProgreso.dismiss();
                                                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                                                CustomToast.Build(this, R.string.falloDescargaRespaldo);
                                            });
                                } else {
                                    Log.e(TAG, "No se encontró un respaldo en Google Drive");
                                    mensajeProgreso.dismiss();
                                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                                    CustomToast.Build(this, R.string.respaldoNoEncontrado);
                                }
                            })
                            .addOnFailureListener(exception -> {
                                Log.e(TAG, "Error al obtener listado de respaldos", exception);
                                mensajeProgreso.dismiss();
                                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                                CustomToast.Build(this, R.string.falloDescargaRespaldo);
                            });
                }
            } else {
                //Se crea la llave maestra, y se guarda en la base de datos
                String[] saltYHash = Cifrador.genHashedPass(newPass, null);
                String saltEncr = Cifrador.genSalt();

                Parametro parSalt = new Parametro(NombreParametro.SAL_HASH.toString(), saltYHash[0], null);
                Parametro parHash = new Parametro(NombreParametro.RESULTADO_HASH.toString(), saltYHash[1], null);
                Parametro parSaltEncr = new Parametro(NombreParametro.SAL_ENCRIPTACION.toString(), saltEncr, null);

                if (parametroDAO.actualizarUna(parSalt) > 0) {
                    if (parametroDAO.actualizarUna(parHash) > 0) {
                        if (parametroDAO.actualizarUna(parSaltEncr) > 0) {
                            CustomToast.Build(getActivity().getApplicationContext(), "Su Llave Maestra fue creada exitosamente.");
                            ((ActividadPrincipal) getActivity()).cambiarFragmento(new FragInicioSesion());
                        }
                    }
                }
            }
        } catch (ExcepcionBancoContrasennas ex) {
            AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
            alertDialog.setTitle(ex.getTitulo());
            alertDialog.setMessage(ex.getMensaje());
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", (dialog, which) -> dialog.dismiss());
            alertDialog.show();
        }
    }

    private void seleccionarCuenta() {
        mGoogleSignClient.signOut()
                .addOnCompleteListener(getActivity(), task -> {
                    ET_cuentaSeleccionada.setText("");
                    mDriveServiceHelper = null;
                    startActivityForResult(mGoogleSignClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
                });
    }

    public void onActivityResult(final int requestCode, final int resultCode, final Intent resultData) {
        if (requestCode == REQUEST_CODE_SIGN_IN) {
            if (resultCode == RESULT_OK && resultData != null) {
                GoogleSignIn.getSignedInAccountFromIntent(resultData)
                        .addOnSuccessListener(googleAccount -> {
                            ET_cuentaSeleccionada.setText(googleAccount.getEmail());
                            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(getContext(), Collections.singleton(DriveScopes.DRIVE_APPDATA));
                            credential.setSelectedAccount(googleAccount.getAccount());
                            Drive googleDriveService = new Drive.Builder(
                                    new NetHttpTransport(),
                                    new GsonFactory(),
                                    credential
                            ).build();
                            mDriveServiceHelper = new DriveServiceHelper(googleDriveService);
                        })
                        .addOnFailureListener(exception -> CustomToast.Build(this, R.string.inicioSesionDriveFallido));
            } else {
                CustomToast.Build(this, R.string.inicioSesionDriveFallido);
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData);
    }
}