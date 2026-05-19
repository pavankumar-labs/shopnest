package com.pavankumar.shopnestecommercebackend.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import java.util.HashMap;
import java.util.Map;

public class MDCTaskDecorator implements TaskDecorator {

    @Override
    public  Runnable decorate(Runnable runnable){
        Map<String,String> content= MDC.getCopyOfContextMap();
        return ()->{
            try{
                MDC.setContextMap(
                        content !=null ?content:  new HashMap<>());
                runnable.run();

            }
            finally {
                MDC.clear();
            }
        };
    }
}
