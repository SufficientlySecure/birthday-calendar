package fr.heinisch.birthdayadapter.util;

public class PurchaseHelperFactory {
    public static IPurchaseHelper create() {
        return new PurchaseHelperImpl();
    }
}
