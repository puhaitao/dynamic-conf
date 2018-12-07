package com.pht.dynconf.config;

import com.pht.dynconf.manager.ConfigManager;
import com.pht.dynconf.utils.StringHelper;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootExceptionReporter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ManagerProperties.class)
public class ApplicationConfig {
    @Autowired
    private ManagerProperties managerProperties;
    @Bean
    public ConfigManager configManager(){
        ConfigManager manager = null;
        try {
            manager = new ConfigManager(managerProperties.getBaseSleepTimeMs(),managerProperties.getMaxRetries(),managerProperties.getZkserver(), "/"+StringHelper.replaceDot(managerProperties.getPrefix(),"/"));
        } catch (Exception e) {
            throw new BeanCreationException("创建ConfigManager失败");
        }
        return manager;
    }

}
