package ar.edu.iessf.servife.common.formato;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formato de montos para textos de avisos: "$ 8.000" o "$ 8.000,50" (es-AR). */
public final class Dinero {

    private static final DecimalFormatSymbols SIMBOLOS = simbolos();

    private Dinero() {
    }

    /** Centavos a pesos, con separador de miles y decimales solo si hay centavos. */
    public static String pesos(long centavos) {
        BigDecimal pesos = BigDecimal.valueOf(centavos, 2);
        DecimalFormat formato = new DecimalFormat(centavos % 100 == 0 ? "#,##0" : "#,##0.00", SIMBOLOS);
        return "$ " + formato.format(pesos);
    }

    private static DecimalFormatSymbols simbolos() {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.forLanguageTag("es-AR"));
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return s;
    }
}
