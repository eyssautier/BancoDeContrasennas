package cl.theroot.passbank.adaptador;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cl.theroot.passbank.R;
import cl.theroot.passbank.dominio.Parametro;
import cl.theroot.passbank.dominio.ParametroSeleccionable;
import cl.theroot.passbank.fragmento.FragConfiguracion;

public class AdapParametros extends BaseAdapter{
    private LayoutInflater inflater;
    private FragConfiguracion fragConfiguracion;
    private List<ParametroSeleccionable> parametros;
    private Map<String, String> parametrosOriginales;

    private class ViewHolder {
        public TextView nombreParametro;
        public EditText valorParametro;
        public Integer referencia;
    }

    public AdapParametros(@NonNull Context context, @NonNull List<ParametroSeleccionable> parametros, @NonNull FragConfiguracion fragConfiguracion) {
        this.inflater = LayoutInflater.from(context);
        this.fragConfiguracion = fragConfiguracion;
        updateParametros(parametros);
    }

    @Override
    public int getCount() {
        return parametros.size();
    }

    @Override
    public ParametroSeleccionable getItem(int position) {
        return parametros.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public View getView(final int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = inflater.inflate(R.layout.lista_configuracion, null);
        }

        ViewHolder viewHolder = (ViewHolder) view.getTag();
        if (viewHolder == null) {
            viewHolder = new ViewHolder();
            viewHolder.nombreParametro = view.findViewById(R.id.TV_nombreParametro);
            viewHolder.valorParametro = view.findViewById(R.id.ET_valorParametro);
            view.setTag(viewHolder);
        }

        if (Arrays.asList(FragConfiguracion.PARAMETROS_NUMERICOS).contains(getItem(i).getNombre())) {
            viewHolder.valorParametro.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else {
            viewHolder.valorParametro.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }

        viewHolder.referencia = i;
        viewHolder.nombreParametro.setText(getItem(i).getNombre());
        viewHolder.valorParametro.setText(getItem(i).getValor());
        if (getItem(i).isSeleccionado()) {
            viewHolder.valorParametro.requestFocus();
            viewHolder.valorParametro.setSelection(viewHolder.valorParametro.getText().toString().length());
        } else {
            viewHolder.valorParametro.clearFocus();
        }

        final ViewHolder finalViewHolder = viewHolder;
        viewHolder.valorParametro.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                int i = finalViewHolder.referencia;
                getItem(i).setSeleccionado(hasFocus);
            }
        });
        viewHolder.valorParametro.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
            @Override
            public void afterTextChanged(Editable s) {
                int i = finalViewHolder.referencia;
                String texto = "";
                if (Arrays.asList(FragConfiguracion.PARAMETROS_NO_TRIMEABLES).contains(getItem(i).getNombre())) {
                    texto = s.toString();
                } else {
                    texto = s.toString().trim();
                }
                if (Arrays.asList(FragConfiguracion.PARAMETROS_NO_REPETIBLES).contains(getItem(i).getNombre())) {
                    String nuevoTexto = "";
                    for (int pos = 0; pos < texto.length(); pos++) {
                        if (!nuevoTexto.contains(Character.toString(texto.charAt(pos)))) {
                            nuevoTexto = nuevoTexto + texto.charAt(pos);
                        }
                    }
                    texto = nuevoTexto;
                }
                getItem(i).setValor(texto);
                habilitarCambios();
            }
        });

        return view;
    }

    public void updateParametros(List<ParametroSeleccionable> parametros) {
        this.parametros = parametros;
        parametrosOriginales = new HashMap<>();
        for (Parametro parametro : this.parametros) {
            parametrosOriginales.put(parametro.getNombre(), parametro.getValor());
        }
        fragConfiguracion.habilitarCambios(false);
        notifyDataSetChanged();
    }

    public List<ParametroSeleccionable> getParametros() {
        return parametros;
    }

    private void habilitarCambios() {
        boolean guardar = false;
        for (Parametro parametro : parametros) {
            if (!parametrosOriginales.get(parametro.getNombre()).equals(parametro.getValor())) {
                guardar = true;
            }
        }
        fragConfiguracion.habilitarCambios(guardar);
    }
}
