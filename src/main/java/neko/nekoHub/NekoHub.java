package neko.nekoHub;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Random;

@Plugin(
    id = "nekohub",
    name = "NekoHub",
    version = "1.0-SNAPSHOT",
    authors = {"不穿胖次の小奶猫"}
)
public class NekoHub implements SimpleCommand {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private List<String> hubList = List.of();
    private Random random = new Random();

    @Inject
    public NekoHub(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("插件启动成功");
        
        // 确保数据目录存在
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.error("无法创建数据目录", e);
        }
        
        // 释放默认配置文件
        saveDefaultConfig();
        
        // 注册命令
        CommandManager commandManager = server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("hub").build();
        commandManager.register(commandMeta, this);
        
        // 加载配置
        loadHubConfig();
    }

    @Subscribe(order = PostOrder.LAST)
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("插件已卸载");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        
        if (!(source instanceof Player)) {
            source.sendMessage(net.kyori.adventure.text.Component.text("只有玩家可以使用此命令"));
            return;
        }

        Player player = (Player) source;
        if (hubList.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.Component.text("没有配置任何大厅"));
            return;
        }

        String randomHub = hubList.get(random.nextInt(hubList.size()));
        //player.sendMessage(net.kyori.adventure.text.Component.text("传送到大厅: " + randomHub));
        
        // 实际的传送逻辑
        server.getServer(randomHub).ifPresentOrElse(
            registeredServer -> player.createConnectionRequest(registeredServer).connect(),
            () -> player.sendMessage(net.kyori.adventure.text.Component.text("未找到大厅服务器: " + randomHub))
        );
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of(); // 没有参数建议
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true; // 所有人都可以使用
    }

    private void saveDefaultConfig() {
        Path configFile = dataDirectory.resolve("config.yml");
        
        // 如果配置文件已存在，则不覆盖
        if (Files.exists(configFile)) {
            logger.info("配置文件已存在，跳过释放默认配置");
            return;
        }
        
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (inputStream == null) {
                logger.warn("未找到默认配置文件");
                return;
            }
            
            Files.copy(inputStream, configFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("默认配置文件已释放到: " + configFile.toString());
        } catch (IOException e) {
            logger.error("无法释放默认配置文件", e);
        }
    }

    private void loadHubConfig() {
        Path configFile = dataDirectory.resolve("config.yml");
        
        try {
            if (!Files.exists(configFile)) {
                logger.warn(" 配置文件不存在: " + configFile.toString());
                return;
            }
            
            String content = Files.readString(configFile);
            Yaml yaml = new Yaml();
            var config = yaml.load(content);
            
            if (config instanceof java.util.Map) {
                var map = (java.util.Map<String, Object>) config;
                if (map.containsKey("lobby") && map.get("lobby") instanceof List) {
                    hubList = (List<String>) map.get("lobby");
                    logger.info("已加载 " + hubList.size() + " 个大厅");
                } else {
                    logger.warn(" 配置文件格式错误");
                }
            }
        } catch (Exception e) {
            logger.error(" 读取配置文件时出错", e);
        }
    }
}
