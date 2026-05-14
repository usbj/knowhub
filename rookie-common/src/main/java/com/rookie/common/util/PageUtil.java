package com.rookie.common.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.PageParameters;

import java.util.List;

public class PageUtil {

    private static final String PAGE_NUM_KEY="pageNum";

    private static final String PAGE_SIZE_KEY="pageSize";



    /**
     * 获取分页参数
     * */
    private static PageParameters getPageParameters() {
        String numParameter = ServletUtil.getParameter(PAGE_NUM_KEY);
        int num = (numParameter!=null)?Integer.parseInt(numParameter):1;
        String sizeParameter = ServletUtil.getParameter(PAGE_SIZE_KEY);
        int size = (sizeParameter!=null)?Integer.parseInt(sizeParameter):10;
        PageParameters parameters = new PageParameters();
        parameters.setPageSize(size);
        parameters.setPageNum(num);
        return parameters;
    }

    /**
     * 开启分页
     * */
    public static void startPage() {
        PageParameters pageParameters = getPageParameters();
        PageHelper.startPage(pageParameters.getPageNum(),pageParameters.getPageSize()).setReasonable(pageParameters.isReasonable());
    }

    /**
     * 将PageHelper劫持过的list(也就是分完页的)打包
     * */
    public static<T> PageInfo<T> packagedPageInfo(List<T> data) {
        PageInfo<T> pageInfo = new PageInfo<>(data);
        PageParameters pageParameters = getPageParameters();
        pageInfo.setPageNum(pageParameters.getPageNum());
        pageInfo.setPageSize(pageParameters.getPageSize());
        return pageInfo;
    }


    /**
     * 鉴于只能将PageHelper劫持过的list(也就是分完页的)存入PageInfo总数才对，
     * 故出此方法将entity转化为vo
     * */
    public static<T,E> PageInfo<T> copyPageInfo(PageInfo<E> data,Class<T> obj){
        PageInfo<T> pageInfo = new PageInfo<>();
        CopyOptions copyOptions = CopyOptions.create().setIgnoreProperties("list");
        BeanUtil.copyProperties(data,pageInfo,copyOptions);
        pageInfo.setList(BeanUtil.copyToList(data.getList(),obj));
        return pageInfo;

    }

}
