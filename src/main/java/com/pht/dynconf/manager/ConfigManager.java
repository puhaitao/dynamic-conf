package com.pht.dynconf.manager;

import com.pht.dynconf.exception.ZkConnectException;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理类，支持获取、更新配置等操作
 */
public class ConfigManager {
    private static Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private int baseSleepTimeMs;
    private int maxRetries;
    private String zkservers;
    private CuratorFramework client;
    private String rootPath;
    private volatile static Map<String, String> _CONFIG_MAP=new HashMap<>();

    public ConfigManager(int baseSleepTimeMs, int maxRetries, String zkservers, String rootPath) throws Exception {
        this.baseSleepTimeMs = baseSleepTimeMs;
        this.maxRetries = maxRetries;
        this.zkservers = zkservers;
        this.rootPath = rootPath;
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(this.baseSleepTimeMs, this.maxRetries);

        //1. 创建zk连接
        String[] zkServerArray = zkservers.split(",");
        for (String zkServer:zkServerArray){
            try {
                this.client = CuratorFrameworkFactory.newClient(zkServer, retryPolicy);
                this.client.start();
                if (this.client!=null&&this.client.getState()== CuratorFrameworkState.STARTED){
                    break;
                }
            }catch (Exception e){
                continue;
            }
        }

        if (this.client==null||this.client.getState()!= CuratorFrameworkState.STARTED){
            throw new ZkConnectException("连接zookeeper失败");
        }

        //创建根目录
        String realPath = this.rootPath + "/keep";
        if (this.client.checkExists().forPath(realPath) == null) {
            try {
                this.client.create().creatingParentsIfNeeded().forPath(realPath);
            } catch (Exception e) {

                logger.info("创建根目录{}失败", realPath);
                e.printStackTrace();
                throw e;
            }
        }

        //从zookeeper加载配置到内存
        loadAllConfig();

        PathChildrenCache pathChildrenCache = new PathChildrenCache(this.client, this.rootPath, true);
        pathChildrenCache.start();
        pathChildrenCache.getListenable().addListener((client,event)-> {

                switch ( event.getType() )
                {
                    case CHILD_ADDED:
                    {
                        _CONFIG_MAP.put(event.getData().getPath(), new String(event.getData().getData()));
                        break;
                    }
                    case CHILD_UPDATED:
                    {
                        _CONFIG_MAP.put(event.getData().getPath(), new String(event.getData().getData()));
                        break;
                    }
                    case CHILD_REMOVED:
                    {
                        _CONFIG_MAP.remove(event.getData().getPath());
                        break;
                    }
            }
        });
    }

    /**
     * 从zookeeper载入配置到内存
     */
    private void loadAllConfig() {
        List<String> properites = getChildren(this.rootPath);
        for (String prop:properites){
            String key = this.rootPath + "/" + prop;
            byte[] dataBytes = new byte[0];
            try {
                dataBytes = client.getData().forPath(key);
            } catch (Exception e) {
                logger.error("load error",e);
            }
            String value = new String(dataBytes);

            _CONFIG_MAP.put(this.rootPath + "/" + prop, value);
        }
    }

    public CuratorFramework getClient() {
        return this.client;
    }

    /**
     * 设置配置
     * @param key
     * @param value
     * @return
     */
    public boolean setConfig(String key, String value) {
        String newkey = rootPath + "/" + key.replace(".","/");
        if (client.getState() == CuratorFrameworkState.STOPPED) {
            client.start();
        }
        try {
            if (getConfig(key) == null || getConfig(key).equals("")) {
                 this.client.create().creatingParentsIfNeeded().forPath(newkey, value.getBytes(Charset.forName("UTF-8")));
            } else {
                 this.client.setData().forPath(newkey, value.getBytes(Charset.forName("UTF-8")));
            }
            _CONFIG_MAP.put(newkey, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置配置
     * @param key
     * @param value
     * @return
     */
    public boolean setEphemeralConfig(String key, String value) {
        String newkey = rootPath + "/" + key.replace(".","/");
        if (client.getState() == CuratorFrameworkState.STOPPED) {
            client.start();
        }
        try {
            if (getConfig(key) == null || getConfig(key).equals("")) {
                this.client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(newkey, value.getBytes(Charset.forName("UTF-8")));
            } else {
                this.client.setData().forPath(newkey, value.getBytes(Charset.forName("UTF-8")));
            }
            _CONFIG_MAP.put(newkey, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setConfigByFullPath(String fullpath,String value){
        if (client.getState() == CuratorFrameworkState.STOPPED) {
            client.start();
        }
        try {

            this.client.setData().forPath(fullpath, value.getBytes(Charset.forName("UTF-8")));

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取配置
     * @param key 配置key
     * @return
     */
    public String getConfig(String key) {
        if (key == null) {
            return null;
        }

        key = rootPath + "/" + key.replace(".","/");

        if (_CONFIG_MAP.get(key)!=null){
            logger.debug("read config from local");
            return _CONFIG_MAP.get(key);
        }

        logger.debug("read config from zk");

        if (client.getState() == CuratorFrameworkState.STOPPED) {
            client.start();
        }
        byte[] dataBytes = new byte[0];
        try {
            dataBytes = client.getData().forPath(key);
        } catch (Exception e) {
            return null;
        }
        String dataString = new String(dataBytes);
        return dataString;
    }

    /**
     * 获取子节点
     * @param path 路径
     * @return
     */
    public List<String> getChildren(String path) {
        if (client.getState() == CuratorFrameworkState.STOPPED) {
            client.start();
        }
        try {
            if (path.startsWith("/")){
                path = path.replace(".", "/");
            }else{
                path = "/" + path.replace(".", "/");
            }
            return client.getChildren().forPath(path);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 删除配置
     * @param key
     * @return
     */
    public boolean deleteConfig(String key){
        if (client.getState() == CuratorFrameworkState.STOPPED) {
            client.start();
        }
        String realKey = this.rootPath + "/" + key.replace(".","/");
        try {
            this.client.delete().forPath(realKey);
            _CONFIG_MAP.remove(realKey);
            return true;
        } catch (Exception e) {
            logger.error(String.format("删除%s失败", realKey), e);
            return false;
        }
    }

    public void createParetsIfNeed(String path) throws Exception {
        String realpath = rootPath + "/" + path.replace(".", "/");
        this.client.create().creatingParentsIfNeeded().forPath(realpath + "/keep");
        this.deleteConfig(path + ".keep");
    }

    public PathChildrenCache addPathChildrenCache(String path,boolean cacheData){
        String realpath = rootPath + "/" + path.replace(".", "/");
        PathChildrenCache pathChildrenCache = new PathChildrenCache(this.client, realpath, cacheData);
        try {
            pathChildrenCache.start();
            return pathChildrenCache;
        } catch (Exception e) {
            return null;
        }
    }
}
