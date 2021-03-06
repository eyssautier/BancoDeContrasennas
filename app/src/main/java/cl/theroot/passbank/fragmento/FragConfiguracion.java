package cl.theroot.passbank.fragmento;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import cl.theroot.passbank.CustomFragment;
import cl.theroot.passbank.CustomToast;
import cl.theroot.passbank.ExcepcionBancoContrasennas;
import cl.theroot.passbank.R;
import cl.theroot.passbank.adaptador.AdapParametros;
import cl.theroot.passbank.datos.CategoriaDAO;
import cl.theroot.passbank.datos.ParametroDAO;
import cl.theroot.passbank.datos.nombres.NombreParametro;
import cl.theroot.passbank.dominio.Parametro;
import cl.theroot.passbank.dominio.ParametroSeleccionable;


public class FragConfiguracion extends CustomFragment implements AlertDialogSiNoOk.iProcesarBotonSiNoOk {
    private static final String TAG = "BdC-FragConfiguracion";

    private AdapParametros adapter;
    private CategoriaDAO categoriaDAO;
    private ParametroDAO parametroDAO;

    @BindView(R.id.listConfiguration)
    ListView listView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragmento_configuracion, container, false);
        ButterKnife.bind(this, view);

        parametroDAO = new ParametroDAO(getApplicationContext());
        categoriaDAO = new CategoriaDAO(getApplicationContext());

        List<ParametroSeleccionable> parametros = new ArrayList<>();
        for (Parametro parametro : parametroDAO.seleccionarVisibles()) {
            parametros.add(new ParametroSeleccionable(parametro.getNombre(), parametro.getValor(), parametro.getPosicion(), parametro.getDescripcion(), parametro.getTipo(), parametro.getMinimo(), parametro.getMaximo(), false));
        }
        adapter = new AdapParametros(getActivity(), parametros);
        listView.setAdapter(adapter);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.sub_menu_configuracion, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu){
        super.onPrepareOptionsMenu(menu);
    }

    //Creación de la funcionalidad del submenu del fragmento
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sub_menu_configuration_change_master_key:
                return actividadPrincipal().cambiarFragmento(new FragCambioLlaveMaestra());
            case R.id.sub_menu_configuration_export_database:
                return actividadPrincipal().cambiarFragmento(new FragRespaldar());
            case R.id.sub_menu_configuration_save:
                try {
                    List<ParametroSeleccionable> parametros = adapter.getParametros();
                    String salida = parametrosValidos(parametros);
                    if (salida != null) {
                        throw new ExcepcionBancoContrasennas(getResources().getString(R.string.datosInvalidos), salida);
                    }

                    for (Parametro parametro : parametros) {
                        parametroDAO.actualizarUna(parametro);
                    }
                    CustomToast.Build(this, R.string.configuracionGrabada);

                    parametros = new ArrayList<>();
                    for (Parametro parametro : parametroDAO.seleccionarVisibles()) {
                        parametros.add(new ParametroSeleccionable(parametro.getNombre(), parametro.getValor(), parametro.getPosicion(), parametro.getDescripcion(), parametro.getTipo(), parametro.getMinimo(), parametro.getMaximo(), false));
                    }
                    adapter.updateParametros(parametros);

                    return true;
                } catch (ExcepcionBancoContrasennas ex) {
                    ex.alertDialog(this);
                }
                return super.onOptionsItemSelected(item);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private String parametrosValidos(List<ParametroSeleccionable> parametros) {
        for (Parametro parametro : parametros){
            if (parametro.getValor() == null) {
                return parametro.getNombre() + "\nNo puede ser nulo.";
            }

            // Se validan los tipos de los parámetros numéricos...
            if (parametro.getTipo() == 1) {
                try {
                    Integer.parseInt(parametro.getValor());
                } catch (Exception ex) {
                    return String.format("%s\nDebe contener solo caracteres numéricos.", parametro.getNombre());
                }
            }

            // Se validan los valores mínimos...
            if (parametro.getTipo() == 0 && parametro.getMinimo() != null) {
                if (parametro.getValor().length() < parametro.getMinimo()) {
                    return String.format("%s\nDebe tener un largo mínimo de %d caracter(es).", parametro.getNombre(), parametro.getMinimo());
                }
            } else if (parametro.getTipo() == 1 && parametro.getMinimo() != null) {
                int valorNumericoParametro = Integer.parseInt(parametro.getValor());
                if (valorNumericoParametro < parametro.getMinimo()) {
                    return String.format("%s\nDebe tener un valor mínimo de %d.", parametro.getNombre(), parametro.getMinimo());
                }
            }

            // Se validan los valores máximos...
            if (parametro.getTipo() == 0) {
                if (parametro.getMaximo() != null && parametro.getValor().length() > parametro.getMaximo()) {
                    return String.format("%s\nDebe tener un largo máximo de %d caracter(es).", parametro.getNombre(), parametro.getMaximo());
                }
            } else if (parametro.getTipo() == 1) {
                if (parametro.getMaximo() != null) {
                    int valorNumericoParametro = Integer.parseInt(parametro.getValor());
                    if (valorNumericoParametro > parametro.getMaximo()) {
                        return String.format("%s\nDebe tener un valor máximo de %d.", parametro.getNombre(), parametro.getMaximo());
                    }
                }
            }

            // Se hacen validaciones particulares...
            if (parametro.getNombre().equals(NombreParametro.NOMBRE_CATEGORIA_COMPLETA.toString())) {
                if (categoriaDAO.seleccionarUna(parametro.getValor()) != null) {
                    return String.format("%s\nDebe tener un nombre distinto al resto de categorías.", parametro.getNombre());
                }
            }
        }
        return null;
    }

    @Override
    public void procesarBotonSiNoOk(int boton) {

    }
}
