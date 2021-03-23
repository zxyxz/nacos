package com.alibaba.nacos.naming.core;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author wb-xy657181
 * @version $Id: PageResult.java, v 0.1 2020年02月17日 9:50 AM wb-xy657181 Exp $
 */
public class PageResult<T> {

    /**
     * 当前页
     */
    private int currentPage;

    /**
     * 总页数
     */
    private int pageSize;

    /**
     * 总条数
     */
    private long totalCount;
    /**
     * 数据
     */
    private List<T> list;

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public static <T>PageResult<T> success(int currentPage, int pageSize, long totalCount, List<T> data) {
        PageResult result = new PageResult();
        result.setCurrentPage(currentPage);
        result.setPageSize(pageSize);
        result.setTotalCount(totalCount);
        result.setList(data);
        return result;
    }

    public static <T>PageResult<T> result(List<T> data) {
        PageResult<T> result = new PageResult<>();
        result.setCurrentPage(-1);
        result.setPageSize(-1);
        result.setTotalCount(data.size());
        result.setList(data);
        return result;
    }

    public static <T>PageResult<T> emptyResult() {
        PageResult result = new PageResult();
        result.setList(Collections.emptyList());
        return result;
    }

    public static <T>PageResult<T> emptyResult(int currentPage, int pageSize, long totalCount) {
        PageResult result = new PageResult();
        result.setCurrentPage(currentPage);
        result.setPageSize(pageSize);
        result.setTotalCount(totalCount);
        result.setList(Collections.emptyList());
        return result;
    }
}
