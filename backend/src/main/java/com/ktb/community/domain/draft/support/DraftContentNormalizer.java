package com.ktb.community.domain.draft.support;

public final class DraftContentNormalizer {

    private DraftContentNormalizer() {
    }

    public static String normalizeText(String value) {
        return value == null ? "" : value;
    }

    public static String normalizeImage(String value) {
        return value == null || value.isBlank()
                ? null
                : value;
    }

    public static boolean isEmpty(
            String title,
            String postBody,
            String postImage
    ) {
        return isBlank(title)
                && isBlank(postBody)
                && isBlank(postImage);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
