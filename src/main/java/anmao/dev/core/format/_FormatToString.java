package anmao.dev.core.format;

import java.text.DecimalFormat;

public class _FormatToString {
    public static String numberToString(Object value){
        return formatValue(value,2);
    }
    public static String numberToString(Object value, int decimalPlaces) {
        double doubleValue;
        if (value instanceof Number) {
            doubleValue = ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                doubleValue = Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric string");
            }
        } else {
            throw new IllegalArgumentException("Unsupported data type");
        }
        String pattern = "0.";
        for (int i = 0; i < decimalPlaces; i++) {
            pattern += "0";
        }
        DecimalFormat decimalFormat = new DecimalFormat(pattern);
        return decimalFormat.format(doubleValue);
    }
    public static String formatValue(Object value, int decimalPlaces) {
        if (value instanceof Number) {
            double doubleValue = ((Number) value).doubleValue();
            return formatDecimal(doubleValue, decimalPlaces);
        } else if (value instanceof String) {
            try {
                double doubleValue = Double.parseDouble((String) value);
                return formatDecimal(doubleValue, decimalPlaces);
            } catch (NumberFormatException e) {
                return (String) value;
            }
        } else {
            throw new IllegalArgumentException("Unsupported data type");
        }
    }

    private static String formatDecimal(double value, int decimalPlaces) {
        DecimalFormat decimalFormat = new DecimalFormat("0." + "0".repeat(Math.max(0, decimalPlaces)));
        return decimalFormat.format(value);
    }
}
