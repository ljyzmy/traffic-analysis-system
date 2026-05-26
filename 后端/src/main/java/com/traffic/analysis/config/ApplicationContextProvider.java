package com.traffic.analysis.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文工具类，可以在非 Spring 管理的类中获取 Bean
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;
    }

    /**
     * 获取 ApplicationContext
     * @return ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 通过名称获取 Bean
     * @param name Bean 名称
     * @return Bean 实例
     */
    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }

    /**
     * 通过类型获取 Bean
     * @param <T> Bean 类型
     * @param clazz Bean 类
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    /**
     * 通过名称和类型获取 Bean
     * @param <T> Bean 类型
     * @param name Bean 名称
     * @param clazz Bean 类
     * @return Bean 实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }
} 