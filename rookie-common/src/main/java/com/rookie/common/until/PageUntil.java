package com.rookie.common.until;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.PageParameters;

import java.util.List;

public class PageUntil {

    private static final String PAGE_NUM_KEY="pageNum";

    private static final String PAGE_SIZE_KEY="pageSize";



    private static PageParameters getPageParameters() {
        String numParameter = ServletUntil.getParameter(PAGE_NUM_KEY);
        int num = (numParameter!=null)?Integer.parseInt(numParameter):1;
        String sizeParameter = ServletUntil.getParameter(PAGE_SIZE_KEY);
        int size = (sizeParameter!=null)?Integer.parseInt(sizeParameter):10;
        PageParameters parameters = new PageParameters();
        parameters.setPageSize(size);
        parameters.setPageNum(num);
        return parameters;
    }

    public static void startPage() {
        PageParameters pageParameters = getPageParameters();
        PageHelper.startPage(pageParameters.getPageNum(),pageParameters.getPageSize()).setReasonable(pageParameters.isReasonable());
    }

    public static<T> PageInfo<T> packagedPageInfo(List<T> data) {
        PageInfo<T> pageInfo = new PageInfo<>(data);
        PageParameters pageParameters = getPageParameters();
        pageInfo.setPageNum(pageParameters.getPageNum());
        pageInfo.setPageSize(pageParameters.getPageSize());
        return pageInfo;
    }


}
