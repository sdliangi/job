package com.xxl.job.admin.core.util;

import java.util.Comparator;
import java.util.Date;

public class KeyCompareUtil implements Comparator<Date> {

    /**
     * 从小到大排序
     *
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    @Override
    public int compare(Date s1, Date s2) {
        return s1.compareTo(s2);
    }
}