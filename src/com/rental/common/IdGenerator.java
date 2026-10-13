package com.rental.common;

import java.util.Collection;

public class IdGenerator {
    public static String next(String prefix, Collection<String> existingIds) {
        int max = 0;
        for (String id : existingIds) {
            if (id == null) continue;
            String digits = id.replaceAll("\\D", "");
            if (digits.isEmpty()) continue;
            try {
                max = Math.max(max, Integer.parseInt(digits));
            } catch (NumberFormatException ignored) { }
        }
        return prefix + String.format("%03d", max + 1);
    }
}
