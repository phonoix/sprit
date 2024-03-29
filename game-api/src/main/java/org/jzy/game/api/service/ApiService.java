package org.jzy.game.api.service;


import com.jzy.javalib.base.script.ScriptManager;
import com.jzy.javalib.base.util.TimeUtil;
import com.jzy.javalib.network.grpc.RpcServerManager;
import com.jzy.javalib.network.io.handler.HandlerManager;
import com.jzy.javalib.network.scene.AbstractScene;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.UriSpec;
import org.jzy.game.common.config.server.ApiConfig;
import org.jzy.game.common.config.server.MongoConfig;
import org.jzy.game.common.config.server.ServiceConfig;
import org.jzy.game.common.constant.*;
import org.jzy.game.common.service.CommonServerService;
import org.jzy.game.common.service.KafkaProducerService;
import org.jzy.game.common.service.ZkClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;


/**
 * 服务器管理
 *
 * @author JiangZhiYong
 * @mail 359135103@qq.com
 */
@Service
public class ApiService extends AbstractScene {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiService.class);

    private ServiceInstance<ServiceConfig> serviceInstance;

    @Autowired
    private ApiConfig apiConfig;
    @Autowired
    private ApiExecutorService apiExecutorService;
    @Value("${config.mongodb.url}")
    private String mongoConfigUrl;
    @Value("${config.mongodb.database}")
    private String mongoConfigDatabase;
    @Value("${api.mongodb.url}")
    private String mongoApiUrl;
    @Value("${api.mongodb.database}")
    private String mongoApiDatabase;
    @Autowired
    private ZkClientService zkClientService;
    @Autowired
    private GlobalProperties globalProperties;
    @Autowired
    private KafkaProducerService kafkaProducerService;
    @Autowired
    private CommonServerService commonServerService;


    /**
     * 初始化
     */
    @PostConstruct
    public void init() {
        try {
            LOGGER.info("api server start：{}-->{} ", apiConfig.getId(), apiConfig.toString());
            //推送配置，数据库配置只在此推送
            zkClientService.pushConfig(ZKNode.ApiConfig.getKey(globalProperties.getProfile(), apiConfig.getId()), apiConfig);
            serviceInstance = ServiceInstance.<ServiceConfig>builder()
                    .id(String.valueOf(apiConfig.getId()))
                    .registrationTimeUTC(TimeUtil.currentTimeMillis())
                    .name(ServiceName.ApiRpc.name())
                    .address(apiConfig.getPrivateIp())
                    .payload(new ServiceConfig())
                    .port(apiConfig.getRpcPort())
                    .uriSpec(new UriSpec("{address}:{port}"))
                    .build();
            zkClientService.starService(ZKNode.ServicePath.getKey(globalProperties.getProfile()), serviceInstance);
            zkClientService.pushConfig(ZKNode.MongoExcelConfig.getKey(globalProperties.getProfile()), new MongoConfig(mongoConfigUrl, mongoConfigDatabase));

           // kafkaProducerService.connectLog(KafkaClientId.Api.getName() + apiConfig.getId());

            ScriptManager.getInstance().setHandlerLoader(HandlerManager.getInstance());
            ScriptManager.getInstance().init((str) -> {
                LOGGER.error("load scripts error:{}", str);
                System.exit(0);
            });
            RpcServerManager.getInstance().registerService(commonServerService);
            apiExecutorService.registerScene(ThreadType.server.toString(), this);
            RpcServerManager.getInstance().start(apiConfig.getRpcPort());
        } catch (Exception e) {
            LOGGER.error("login server start", e);
        }


    }


    /**
     * 销毁
     */
    @PreDestroy
    public void destroy() {
        zkClientService.unregisterService(serviceInstance);
        LOGGER.info("api server stop：{}-->{} ", apiConfig.getId(), apiConfig.toString());

    }


}
