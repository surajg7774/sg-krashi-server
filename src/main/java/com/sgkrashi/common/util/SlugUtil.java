package com.sgkrashi.common.util;

import java.util.function.Predicate;

/**
 * Shared slug generation for Module 15's four Admin catalog forms — one
 * utility instead of four near-identical copies. {@link #uniqueSlugFrom}
 * appends {@code -2}, {@code -3}, etc. until {@code existsCheck} reports no
 * collision, so a name typed twice by an Admin doesn't hit the unique
 * constraint each entity's {@code slug} column already has.
 */
public final class SlugUtil {

    private SlugUtil() {
    }

    public static String slugify(String value) {
        String slug = value.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "item" : slug;
    }

    public static String uniqueSlugFrom(String name, Predicate<String> slugExists) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (slugExists.test(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }
}
