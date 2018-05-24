package ru.algotraide.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CalcUtils {

    public static BigDecimal toBigDec(String value) {
        return new BigDecimal(value);
    }

    public static BigDecimal multiply(String value1, String value2) {
        return new BigDecimal(value1).multiply(new BigDecimal(value2));
    }

    public static BigDecimal multiply(BigDecimal value1, BigDecimal value2) {
        return value1.multiply(value2);
    }

    public static BigDecimal multiply(BigDecimal value1, String value2) {
        return value1.multiply(new BigDecimal(value2));
    }

    public static BigDecimal divide(String value1, String value2) {
        return new BigDecimal(value1).divide(new BigDecimal(value2), 8, RoundingMode.DOWN);
    }

    public static BigDecimal divide(BigDecimal value1, BigDecimal value2) {
        return value1.divide(value2, 8, RoundingMode.DOWN);
    }

    public static BigDecimal subtract(String value1, String value2) {
        return new BigDecimal(value1).subtract(new BigDecimal(value2));
    }

    public static BigDecimal subtract(BigDecimal value1, BigDecimal value2) {
        return value1.subtract(value2);
    }

    public static BigDecimal add(String value1, String value2) {
        return new BigDecimal(value1).add(new BigDecimal(value2));
    }

    public static BigDecimal add(BigDecimal value1, BigDecimal value2) {
        return value1.add(value2);
    }

    public static BigDecimal add(BigDecimal value1, String value2) {
        return value1.add(new BigDecimal(value2));
    }
}
