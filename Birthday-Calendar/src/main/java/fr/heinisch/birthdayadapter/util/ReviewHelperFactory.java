package fr.heinisch.birthdayadapter.util;

/**
 * Factory class to create instances of ReviewHelper.
 */
public class ReviewHelperFactory {
    /**
     * Creates and returns a new instance of ReviewHelperImpl.
     * The actual implementation depends on the build flavor.
     *
     * @return A ReviewHelper instance.
     */
    public static ReviewHelper create() {
        return new ReviewHelperImpl();
    }
}
