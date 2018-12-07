package com.pht.dynconf;

import com.pht.dynconf.manager.ConfigManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class DynamicConfApplicationTests {

    @Autowired
    private ConfigManager manager;

    @Test
    public void contextLoads() {
        System.out.println(manager.getConfig("bb.name"));
    }

}
