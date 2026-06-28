package com.rookie.common.util;


import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

@Component
public class SpringUtil {

    private static ConfigurableListableBeanFactory beanFactory;

    public SpringUtil(ConfigurableListableBeanFactory beanFactory) {
        SpringUtil.beanFactory=beanFactory;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name){
        return (T)beanFactory.getBean(name);
    }

    public static <T> T getBean(Class<T> tClass){
        return beanFactory.getBean(tClass);
    }

}
