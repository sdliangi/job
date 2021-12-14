package com.xxl.job.admin.core.util;

import org.springframework.lang.Nullable;

public class StringUtils {
    public static boolean isEmpty(@Nullable String str) {
        return str == null || str.trim().length() == 0;
    }
}
